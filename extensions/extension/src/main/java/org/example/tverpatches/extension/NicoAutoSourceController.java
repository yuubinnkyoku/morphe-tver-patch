package org.example.tverpatches.extension;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import org.json.JSONArray;
import java.util.Locale;

/**
 * Per-episode comment source manager:
 * TVer's EpisodePlayerMeta -> TVer broadcast EPG -> candidates -> Jikkyo archive.
 * All public calls besides rememberMetaFlow() run on the main thread.
 * No candidate is silently applied unless the match is exact and unique.
 */
final class NicoAutoSourceController {
    static final String AUTO_ENABLED = "nico_epg_auto_search";
    private static final String PREFS = "morphe.tver.settings";
    private static final String SOURCE_PREFIX = "nico_episode_source_";
    private static final String AUTO_GROUP_PREFIX = "nico_episode_auto_group_";
    private static final String PENDING_SOURCE = "nico_source_for_next_episode";
    private static final String TAG = "MorpheNicoOverlay";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile WeakReference<Object> metaRef = new WeakReference<>(null);
    private static String program = "", episode = "", episodeKey = "";
    private static String searchStatus = "waiting-title";
    private static List<TverEpgSearch.Candidate> candidates = new ArrayList<>();
    private static int searchGeneration;
    private static String loadedSpec = "";
    private static boolean bsSearch;

    static void rememberMetaFlow(Object flow) {
        if (flow != null) metaRef = new WeakReference<>(flow);
    }

    private static String preferenceKey() {
        if (episodeKey.isEmpty()) return "";
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(episodeKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder(SOURCE_PREFIX);
            for (int i = 0; i < 12; i++) key.append(String.format(Locale.ROOT,"%02x",bytes[i] & 0xff));
            return key.toString();
        } catch (Exception ex) {
            return SOURCE_PREFIX + Integer.toHexString(episodeKey.hashCode());
        }
    }


    private static List<String> autoGroupForCurrent(Context context) {
        String key = preferenceKey();
        if (key.isEmpty()) return Collections.emptyList();
        String raw = context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getString(AUTO_GROUP_PREFIX + key, "");
        if (raw.isEmpty()) return Collections.emptyList();
        try {
            JSONArray array = new JSONArray(raw);
            LinkedHashSet<String> unique = new LinkedHashSet<>();
            for (int i = 0; i < array.length() && unique.size() < 32; i++) {
                String item = array.optString(i, "");
                if (item.isEmpty()) continue;
                JikkyoCommentSource.validate(item);
                unique.add(item);
            }
            return new ArrayList<>(unique);
        } catch (Exception invalid) {
            Log.w(TAG,"Saved archive group is invalid",invalid);
            return Collections.emptyList();
        }
    }

    private static void setAutoGroup(Context context, List<String> sources) {
        String key = preferenceKey();
        if (key.isEmpty() || sources.isEmpty()) return;
        JSONArray array = new JSONArray();
        for (String item : sources) array.put(item);
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .remove(key)
            .putString(AUTO_GROUP_PREFIX + key, array.toString())
            .remove(PENDING_SOURCE).apply();
        loadedSpec = "auto-group";
    }

    static String sourceForCurrent(Context context) {
        String key = preferenceKey();
        if (key.isEmpty()) return "";
        String manual = context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getString(key,"");
        if (!manual.isEmpty()) return manual;
        List<String> group = autoGroupForCurrent(context);
        return group.isEmpty() ? "" : group.get(0);
    }

    static void setManual(Context context, String spec) {
        String key = preferenceKey();
        SharedPreferences.Editor edit =
            context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit();
        if (!key.isEmpty()) edit.remove(AUTO_GROUP_PREFIX + key);
        if (spec == null || spec.isEmpty()) {
            if (!key.isEmpty()) edit.remove(key);
            edit.remove(PENDING_SOURCE);
            loadedSpec = "";
        } else if (!key.isEmpty()) {
            edit.putString(key,spec).remove(PENDING_SOURCE);
            loadedSpec = spec;
        } else {
            // Morphe settings can be opened without the episode player.
            // Apply this explicit manual selection ONLY to the next episode.
            edit.putString(PENDING_SOURCE,spec);
        }
        edit.apply();
    }

    static void resetOnRelease() {
        searchGeneration++;
        metaRef = new WeakReference<>(null);
        episodeKey = "";
        program = episode = "";
        loadedSpec = "";
        candidates = new ArrayList<>();
        searchStatus = "waiting-title";
    }

    /** Called every 200ms by NicoOverlayBridge while playing. */
    static void tick(Context context) {
        Object flow = metaRef.get();
        if (flow == null) return;
        try {
            Class<?> stateFlowType = Class.forName(
                "kotlinx.coroutines.flow.StateFlow", false, flow.getClass().getClassLoader());
            Object metadata = stateFlowType.getMethod("getValue").invoke(flow);
            if (metadata == null) return;
            Method getProgram = metadata.getClass().getMethod("getProgramTitle");
            Method getEpisode = metadata.getClass().getMethod("getEpisodeTitle");
            Object a = getProgram.invoke(metadata), b = getEpisode.invoke(metadata);
            String currentProgram = a == null ? "" : a.toString().trim();
            String currentEpisode = b == null ? "" : b.toString().trim();
            if (currentProgram.isEmpty() || currentEpisode.isEmpty()) return;
            String key = currentProgram + "\u0000" + currentEpisode;
            if (key.equals(episodeKey)) return;
            // New title: never keep the previous episode's comments.
            searchGeneration++;
            program = currentProgram;
            episode = currentEpisode;
            episodeKey = key;
            bsSearch = false;
            candidates = new ArrayList<>();
            loadedSpec = "";
            searchStatus = "identified";
            NicoOverlayBridge.clearEpisodeComments();
            Log.i(TAG,"TVer episode identified: "+program+" / "+episode);
            List<String> savedGroup = autoGroupForCurrent(context);
            if (!savedGroup.isEmpty()) {
                loadedSpec = "auto-group";
                searchStatus = "saved-auto-group";
                NicoOverlayBridge.openEpisodeCommentSources(context,savedGroup,false);
                return;
            }
            String saved = sourceForCurrent(context);
            if (saved.isEmpty()) {
                String pending = context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                    .getString(PENDING_SOURCE,"");
                if (!pending.isEmpty()) {
                    JikkyoCommentSource.validate(pending);
                    setManual(context,pending);
                    saved = pending;
                }
            }
            if (!saved.isEmpty()) {
                loadedSpec = saved;
                searchStatus = "saved-source";
                NicoOverlayBridge.openEpisodeCommentSource(context,saved,false);
            } else if (context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .getBoolean(AUTO_ENABLED,true)) {
                search(context,false);
            } else {
                searchStatus = "disabled";
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            // This is experimental metadata extraction; never break playback.
            if (!"metadata-error".equals(searchStatus)) {
                searchStatus = "metadata-error";
                Log.w(TAG,"Could not read TVer EpisodePlayerMeta",error);
            }
        }
    }

    static void search(Context context, boolean bs) {
        if (program.isEmpty() || episode.isEmpty()) {
            searchStatus = "waiting-title";
            Toast.makeText(context, "動画を開いて番組名を取得してください",
                Toast.LENGTH_SHORT).show();
            return;
        }
        final String key = episodeKey;
        final String series = program, subtitle = episode;
        final int generation = ++searchGeneration;
        bsSearch = bs;
        searchStatus = "searching";
        candidates = new ArrayList<>();
        new Thread(() -> {
            try {
                java.util.concurrent.ExecutorService executor =
                    java.util.concurrent.Executors.newFixedThreadPool(bs ? 1 : 3);
                List<TverEpgSearch.Candidate> found = new ArrayList<>();
                int success = 0;
                try {
                    List<java.util.concurrent.Callable<TverEpgSearch.Result>> tasks =
                        new ArrayList<>();
                    if (!bs) {
                        tasks.add(() -> TverEpgSearch.search(series,subtitle,false));
                        tasks.add(() -> SyobocalSearch.search(series,subtitle));
                    }
                    tasks.add(() -> TverEpgSearch.search(series,subtitle,true));
                    List<java.util.concurrent.Future<TverEpgSearch.Result>> futures =
                        executor.invokeAll(tasks, 90, java.util.concurrent.TimeUnit.SECONDS);
                    for (java.util.concurrent.Future<TverEpgSearch.Result> future : futures) {
                        if (future.isCancelled()) continue;
                        try {
                            TverEpgSearch.Result result = future.get();
                            success += result.successfulDays;
                            found = TverEpgSearch.merge(found,result.candidates);
                        } catch (java.util.concurrent.ExecutionException error) {
                            Log.w(TAG,"One archive discovery source failed",error);
                        }
                    }
                } finally {
                    executor.shutdownNow();
                }
                final List<TverEpgSearch.Candidate> results = found;
                final int available = success;
                MAIN.post(() -> {
                    if (generation != searchGeneration || !key.equals(episodeKey)) return;
                    candidates = results;
                    searchStatus = available == 0 ? "epg-error" :
                        candidates.isEmpty() ? "no-candidates" : "candidates";
                    // NCOverlay-style: all verified past broadcasts of the
                    // current episode are loaded as one synchronized group.
                    List<TverEpgSearch.Candidate> group =
                        TverEpgSearch.automaticGroup(series,subtitle,candidates);
                    if (!group.isEmpty() && loadedSpec.isEmpty() &&
                        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                            .getBoolean(AUTO_ENABLED,true)) {
                        List<String> specifications = new ArrayList<>();
                        for (TverEpgSearch.Candidate item : group)
                            specifications.add(item.specification(0));
                        setAutoGroup(context,specifications);
                        NicoOverlayBridge.openEpisodeCommentSources(
                            context,specifications,false);
                        searchStatus = "auto-selected";
                        Log.i(TAG,"Auto loading " + specifications.size()
                            + " broadcasts for " + series + " / " + subtitle);
                    }
                });
            } catch (Exception error) {
                MAIN.post(() -> {
                    if (generation != searchGeneration || !key.equals(episodeKey)) return;
                    searchStatus = "epg-error";
                    Log.w(TAG,"Archive discovery failed",error);
                });
            }
        },"Morphe-EPG").start();
    }

    private static void select(Context context, TverEpgSearch.Candidate chosen) {
        String spec = chosen.specification(0);
        setManual(context,spec);
        NicoOverlayBridge.openEpisodeCommentSource(context,spec,true);
        searchStatus = "selected";
    }

    static String badgeLabel(int loadedCount) {
        if (program.isEmpty()) return "💬 コメント";
        if ("searching".equals(searchStatus)) return "💬 検索中";
        if (loadedCount > 0) return "💬 "+loadedCount+"件";
        if (!candidates.isEmpty()) return "💬 候補"+candidates.size();
        if ("epg-error".equals(searchStatus)) return "💬 検索失敗";
        if ("no-candidates".equals(searchStatus)) return "💬 候補なし";
        return "💬 コメント";
    }

    static String status() { return searchStatus; }
    static int candidateCount() { return candidates.size(); }
    static String seriesName() { return program; }
    static String episodeName() { return episode; }

    static void showPicker(Activity activity) {
        if (activity == null || activity.isDestroyed() || activity.isFinishing()) return;
        if (program.isEmpty()) {
            new AlertDialog.Builder(activity).setTitle("コメントを選択")
                .setMessage("番組情報を取得中です。動画を開いてから再度お試しください。")
                .setPositiveButton("閉じる",null).show();
            return;
        }
        final String keyAtOpen = episodeKey;
        String current = sourceForCurrent(activity);
        List<TverEpgSearch.Candidate> options = new ArrayList<>(candidates);
        String[] labels = new String[options.size() + 2];
        List<String> group = autoGroupForCurrent(activity);
        labels[0] = "現在のコメントを調整 / 解除\n"
            + (group.isEmpty() ? (current.isEmpty() ? "未選択" : current)
                : group.size() + "放送分を重ね合わせ")
            + "　（"+searchStatus+"）";
        labels[1] = "放送局・放送日時を手動で指定";
        for (int i=0;i<options.size();i++) labels[i+2]=options.get(i).label();
        new AlertDialog.Builder(activity)
            .setTitle("💬 "+program+" / "+episode)
            .setItems(labels,(dialog,which)->{
                if (!keyAtOpen.equals(episodeKey)) return;
                if (which==0) showSourceControls(activity);
                else if (which==1) showManualSourceEditor(activity);
                else select(activity,options.get(which-2));
            })
            .setPositiveButton("地デジ・履歴検索",(d,w)->search(activity,false))
            .setNeutralButton("BS検索",(d,w)->search(activity,true))
            .setNegativeButton("閉じる",null)
            .show();
    }


    private static void showManualSourceEditor(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        String keyAtOpen = episodeKey;
        EditText input = new EditText(activity);
        input.setSingleLine(false);
        input.setTextSize(14);
        input.setHint("jk7|2026-10-30 23:00|2026-10-30 23:55|0");
        input.setText(sourceForCurrent(activity));
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, pad / 2);
        TextView instructions = new TextView(activity);
        instructions.setText("実況チャンネル | 元の放送開始 (日本時間) | 終了 | 補正秒\n"
            + "例: テレビ東京は jk7。放送日が違うとコメントも一致しません。");
        layout.addView(instructions);
        layout.addView(input);
        new AlertDialog.Builder(activity)
            .setTitle("実況過去ログを手動で指定")
            .setView(layout)
            .setPositiveButton("読み込む", (dialog, which) -> {
                if (!keyAtOpen.equals(episodeKey)) return;
                String spec = input.getText().toString().trim();
                try {
                    JikkyoCommentSource.validate(spec);
                    setManual(activity, spec);
                    NicoOverlayBridge.openEpisodeCommentSource(activity, spec, true);
                    searchStatus = "manual-source";
                } catch (IllegalArgumentException ex) {
                    Toast.makeText(activity, "指定形式を確認してください: " + ex.getMessage(),
                        Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton("戻る", null)
            .show();
    }

    static void showCurrentSourceControls(Activity activity) {
        showSourceControls(activity);
    }

    private static void showSourceControls(Activity activity) {
        List<String> group = autoGroupForCurrent(activity);
        String current = sourceForCurrent(activity);
        if (current.isEmpty()) {
            new AlertDialog.Builder(activity).setTitle("コメント未選択")
                .setMessage("候補を選んでから、時刻のずれを補正できます。")
                .setPositiveButton("閉じる",null).show();
            return;
        }
        String[] parts = current.split("\\|",-1);
        if (parts.length < 3) return;
        final String keyAtOpen = episodeKey;
        int original = 0;
        if (parts.length>=4) try {original=Integer.parseInt(parts[3]);}
            catch(NumberFormatException ignored){}
        final EditText offset = new EditText(activity);
        offset.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);
        offset.setSingleLine(true);
        offset.setText(Integer.toString(original));
        offset.setSelectAllOnFocus(true);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32,8,32,4);
        TextView help = new TextView(activity);
        help.setText("コメントの時刻補正（秒）\n正の値で遅らせ、負の値で早めます。"
            + (group.isEmpty() ? "" : "\n" + group.size()
                + "放送分に一括適用します。"));
        layout.addView(help);
        layout.addView(offset);
        new AlertDialog.Builder(activity)
            .setTitle("コメントの時刻を調整")
            .setView(layout)
            .setPositiveButton("保存して再読込",(d,w)->{
                if (!keyAtOpen.equals(episodeKey)) return;
                try {
                    int seconds = Integer.parseInt(offset.getText().toString().trim());
                    if (Math.abs(seconds)>3600) throw new NumberFormatException();
                    String spec = parts[0]+"|"+parts[1]+"|"+parts[2]+"|"+seconds;
                    if (group.isEmpty()) {
                        setManual(activity,spec);
                        NicoOverlayBridge.openEpisodeCommentSource(activity,spec,true);
                    } else {
                        List<String> changed = new ArrayList<>();
                        for (String item : group) {
                            String[] f = item.split("\\|",-1);
                            changed.add(f[0]+"|"+f[1]+"|"+f[2]+"|"+seconds);
                        }
                        setAutoGroup(activity,changed);
                        NicoOverlayBridge.openEpisodeCommentSources(activity,changed,true);
                    }
                } catch(NumberFormatException error) {
                    Toast.makeText(activity,"補正は±3600秒以内です",
                        Toast.LENGTH_LONG).show();
                }
            })
            .setNeutralButton("コメントを解除",(d,w)->{
                if (!keyAtOpen.equals(episodeKey)) return;
                setManual(activity,"");
                NicoOverlayBridge.clearEpisodeComments();
                searchStatus = "cleared";
            })
            .setNegativeButton("戻る",null)
            .show();
    }
    private NicoAutoSourceController(){}
}
