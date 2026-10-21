package app.template.extension.quality;

import android.app.Activity;
import android.app.AlertDialog;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.ArrayAdapter;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Runtime bridge for TVer 7.2.1's Compose quality button.
 *
 * Uses reflection deliberately: the extension is compiled independently from
 * the proprietary TVer APK, while the patch injects it into that APK.
 */
public final class VideoQualityPicker {
    private static final String TAG = "MorpheTVerQuality";
    private static final String ENGINE = "jp.play.tverplayer.player.core.engine.StreaksPlayerEngine";
    private static final String OVERRIDE = "jp.logiclogic.streaksplayer.model.STRSelectionOverride";
    private static volatile WeakReference<Activity> activity = new WeakReference<>(null);
    private static volatile WeakReference<Object> lastSdk = new WeakReference<>(null);
    private static volatile int requestedHeight = -1; // -1 = automatic
    private static volatile String lastMediaId;

    private VideoQualityPicker() {}

    /** Called from MainActivity.onWindowFocusChanged, including the no-PiP case. */
    public static void rememberActivity(Activity resumed) {
        if (resumed != null && !resumed.isFinishing()) {
            activity = new WeakReference<>(resumed);
        }
    }

    /**
     * Display our picker immediately, including while the media SDK is loading.
     * The stock picker is reserved for unsupported engines or absent Activities.
     * Video renditions are loaded asynchronously on the main thread and shown
     * in the same dialog once the SDK reports them.
     */
    public static boolean open(Object playerUiState) {
        final Activity host = activity.get();
        if (host == null || host.isFinishing() || host.isDestroyed()) return false;
        try {
            Object engine = member(playerUiState, "controller");
            if (engine != null && !ENGINE.equals(engine.getClass().getName())) {
                // Legacy players still use TVer's own quality selector.
                return false;
            }

            final ArrayList<Choice> choices = new ArrayList<>();
            final Object[] activeEngine = new Object[1];
            final Object[] activeSdk = new Object[1];
            final String[] activeMediaId = new String[1];
            final ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    host,
                    android.R.layout.simple_list_item_single_choice,
                    new ArrayList<>(Collections.singletonList("画質を取得中…")));
            final Handler handler = new Handler(Looper.getMainLooper());
            final Runnable[] refresh = new Runnable[1];

            final AlertDialog dialog = new AlertDialog.Builder(host)
                    .setTitle("現在の動画の画質 ・ 読み込み中")
                    .setSingleChoiceItems(adapter, -1, (d, position) -> {
                        // The temporary loading/error row is not selectable.
                        if (activeSdk[0] == null || choices.isEmpty()
                                || position < 0 || position > choices.size()) return;
                        try {
                            if (position == 0) {
                                setDesiredQuality(activeEngine[0], "AUTO");
                                applyAutomatic(activeSdk[0]);
                                requestedHeight = -1;
                            } else {
                                Choice chosen = choices.get(position - 1);
                                setDesiredQuality(activeEngine[0], "HIGH");
                                applyChoice(activeSdk[0], chosen);
                                requestedHeight = chosen.height;
                            }
                            lastSdk = new WeakReference<>(activeSdk[0]);
                            lastMediaId = activeMediaId[0];
                        } catch (Exception error) {
                            Log.e(TAG, "Unable to switch video track", error);
                            android.widget.Toast.makeText(host,
                                    "画質を変更できませんでした",
                                    android.widget.Toast.LENGTH_SHORT).show();
                        }
                        d.dismiss();
                    })
                    .setNegativeButton("閉じる", null)
                    .create();

            // Stop polling when the user dismisses the picker or navigates away.
            dialog.setOnDismissListener(d -> {
                if (refresh[0] != null) handler.removeCallbacks(refresh[0]);
            });
            dialog.show();

            final long startedAt = SystemClock.uptimeMillis();
            refresh[0] = () -> {
                if (!dialog.isShowing()) return;
                if (host.isFinishing() || host.isDestroyed()) {
                    dialog.dismiss();
                    return;
                }
                try {
                    Object currentEngine = member(playerUiState, "controller");
                    if (currentEngine != null
                            && ENGINE.equals(currentEngine.getClass().getName())) {
                        Object sdk = member(currentEngine, "sdk");
                        if (sdk != null) {
                            List<Choice> available = enumerate(sdk);
                            if (!available.isEmpty()) {
                                Object state = call(call(currentEngine, "getState"), "getValue");
                                String mediaId = (String) call(state, "getMediaId");
                                int width = number(call(state, "getVideoWidth"));
                                int height = number(call(state, "getVideoHeight"));
                                activeEngine[0] = currentEngine;
                                activeSdk[0] = sdk;
                                activeMediaId[0] = mediaId;
                                choices.clear();
                                choices.addAll(available);

                                adapter.clear();
                                adapter.add("自動");
                                for (Choice choice : choices) {
                                    adapter.add(choice.height + "p");
                                }
                                adapter.notifyDataSetChanged();
                                dialog.setTitle(height > 0
                                        ? "現在の動画の画質 ・ " + height
                                                + "p (" + width + "×" + height + ")"
                                        : "現在の動画の画質 ・ 読み込み中");

                                int checked = 0;
                                if (lastSdk.get() == sdk
                                        && java.util.Objects.equals(lastMediaId, mediaId)
                                        && requestedHeight >= 0) {
                                    for (int i = 0; i < choices.size(); i++) {
                                        if (choices.get(i).height == requestedHeight) {
                                            checked = i + 1;
                                            break;
                                        }
                                    }
                                }
                                dialog.getListView().setItemChecked(checked, true);
                                return; // Tracks are ready. No more polling.
                            }
                        }
                    }
                } catch (Exception error) {
                    // Startup can transiently fail before track groups exist.
                    // Keep the loading row and retry rather than opening TVer's menu.
                    Log.d(TAG, "Waiting for video tracks: " + error.getClass().getSimpleName());
                }

                if (SystemClock.uptimeMillis() - startedAt >= 15000L) {
                    adapter.clear();
                    adapter.add("画質を取得できませんでした。閉じて再試行してください");
                    adapter.notifyDataSetChanged();
                    dialog.setTitle("画質");
                    return;
                }
                handler.postDelayed(refresh[0], 300L);
            };
            refresh[0].run();
            return true;
        } catch (Exception error) {
            Log.w(TAG, "Unable to display resolution picker", error);
            return false;
        }
    }

    private static List<Choice> enumerate(Object sdk) throws Exception {
        Object[] renderers = (Object[]) call(sdk, "getSwitchableTracks");
        if (renderers == null) return Collections.emptyList();
        // A rendition may appear in more than one adaptive group; display one
        // item per vertical resolution and select the highest bitrate variant.
        Map<Integer, Choice> byHeight = new TreeMap<>(Collections.reverseOrder());
        for (Object renderer : renderers) {
            if (number(member(renderer, "rendererType")) != 1) continue;
            Object groupArray = member(renderer, "trackGroupArray");
            Object[] groups = (Object[]) member(groupArray, "trackGroups");
            for (Object group : groups) {
                Object trackGroup = member(group, "trackGroup");
                int length = number(member(trackGroup, "length"));
                for (int i = 0; i < length; i++) {
                    Object format = call(trackGroup, "getFormat", new Class<?>[]{int.class}, i);
                    int width = number(member(format, "width"));
                    int height = number(member(format, "height"));
                    int bitrate = number(member(format, "bitrate"));
                    if (width <= 0 || height <= 0) continue;
                    Choice previous = byHeight.get(height);
                    if (previous == null || bitrate > previous.bitrate) {
                        byHeight.put(height, new Choice(width, height, bitrate));
                    }
                }
            }
        }
        return new ArrayList<>(byHeight.values());
    }

    /** Keep TVer's own desired quality consistent across playback reapplication. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setDesiredQuality(Object engine, String name) throws Exception {
        Class<?> enumClass = Class.forName(
                "jp.play.tverplayer.player.contract.VideoQuality",
                false, engine.getClass().getClassLoader());
        Object value = Enum.valueOf((Class<? extends Enum>) enumClass, name);
        call(engine, "setQuality", new Class<?>[]{enumClass}, value);
    }

    private static void applyAutomatic(Object sdk) throws Exception {
        Object[] renderers = (Object[]) call(sdk, "getSwitchableTracks");
        if (renderers == null) throw new IllegalStateException("No tracks");
        for (Object renderer : renderers) {
            if (number(member(renderer, "rendererType")) != 1) continue;
            assign(renderer, "disable", false);
            call(renderer, "setSelection", new Class<?>[]{selectionArrayClass(sdk)}, new Object[]{null});
        }
        call(sdk, "setMaxVideoSize", new Class<?>[]{int.class, int.class}, 1920, 1080);
        update(sdk, renderers);
    }

    private static void applyChoice(Object sdk, Choice selected) throws Exception {
        // Resolve again when clicked, not against a potentially stale dialog snapshot.
        Object[] renderers = (Object[]) call(sdk, "getSwitchableTracks");
        if (renderers == null) throw new IllegalStateException("No tracks");
        Class<?> overrideClass = Class.forName(OVERRIDE, false, sdk.getClass().getClassLoader());
        Constructor<?> ctor = overrideClass.getConstructor(
                Class.forName("jp.logiclogic.streaksplayer.model.STRTrackGroup",
                        false, sdk.getClass().getClassLoader()), List.class);
        Class<?> overrideArray = Array.newInstance(overrideClass, 0).getClass();

        boolean found = false;
        for (Object renderer : renderers) {
            if (number(member(renderer, "rendererType")) != 1) continue;
            Object[] groups = (Object[]) member(member(renderer, "trackGroupArray"), "trackGroups");
            for (Object group : groups) {
                Object trackGroup = member(group, "trackGroup");
                int count = number(member(trackGroup, "length"));
                for (int i = 0; i < count; i++) {
                    Object format = call(trackGroup, "getFormat", new Class<?>[]{int.class}, i);
                    int width = number(member(format, "width"));
                    int height = number(member(format, "height"));
                    int bitrate = number(member(format, "bitrate"));
                    if (height == selected.height && width == selected.width
                            && bitrate == selected.bitrate) {
                        Object entry = ctor.newInstance(trackGroup, Collections.singletonList(i));
                        Object overrides = Array.newInstance(overrideClass, 1);
                        Array.set(overrides, 0, entry);
                        assign(renderer, "disable", false);
                        call(renderer, "setSelection", new Class<?>[]{overrideArray}, overrides);
                        found = true;
                        break;
                    }
                }
                if (found) break;
            }
            if (found) break;
        }
        if (!found) throw new IllegalStateException("Selected rendition disappeared");
        call(sdk, "setMaxVideoSize", new Class<?>[]{int.class, int.class},
                Math.max(1920, selected.width), Math.max(1080, selected.height));
        update(sdk, renderers);
    }

    private static Class<?> selectionArrayClass(Object sdk) throws Exception {
        return Array.newInstance(Class.forName(OVERRIDE, false, sdk.getClass().getClassLoader()), 0).getClass();
    }

    private static void update(Object sdk, Object[] renderers) throws Exception {
        call(sdk, "updateTrackSelection",
                new Class<?>[]{renderers.getClass()}, (Object) renderers);
    }

    private static int number(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : -1;
    }

    private static Field findField(Class<?> type, String name) throws Exception {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name + " on " + type);
    }

    private static Object member(Object obj, String field) throws Exception {
        return findField(obj.getClass(), field).get(obj);
    }

    private static void assign(Object obj, String field, Object value) throws Exception {
        findField(obj.getClass(), field).set(obj, value);
    }

    private static Object call(Object obj, String name) throws Exception {
        return call(obj, name, new Class<?>[0]);
    }

    private static Object call(Object obj, String name, Class<?>[] args, Object... values)
            throws Exception {
        return obj.getClass().getMethod(name, args).invoke(obj, values);
    }

    private static final class Choice {
        final int width;
        final int height;
        final int bitrate;
        Choice(int width, int height, int bitrate) {
            this.width = width;
            this.height = height;
            this.bitrate = bitrate;
        }
    }
}
