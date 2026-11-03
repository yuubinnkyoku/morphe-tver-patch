package org.example.tverpatches.extension;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * TVer 7.2.1 experiment: draws offline test comments in an isolated WebView.
 * Does not access video pixels, bypass DRM, contact third-party servers,
 * or expose any JavascriptInterface to web content.
 */
@SuppressWarnings("unused")
public final class NicoOverlayBridge {
    private static final String TAG = "MorpheNicoOverlay";
    private static final String PERF_PREFIX = "MORPHE_NICO_PERF ";
    private static final String PREFS = "morphe.tver.settings";
    private static final String COMMENTS_VISIBLE = "nico_comments_visible";
    private static final String FPS_VISIBLE = "nico_fps_hud_visible";
    private static final String PREFER_120FPS = "nico_prefer_120fps";
    private static final String WINDOW_PREFER_120HZ = "nico_window_prefer_120hz";
    private static final String WINDOW_MODE_144HZ = "nico_window_mode_144hz";
    private static final String WINDOW_MIN_120HZ = "nico_window_min_120hz";
    private static final String VIDEO_SURFACE_RATE_OFF = "nico_video_surface_framerate_off";
    private static final String SAVED_FPS_REPORT = "nico_fps_last_report";
    private static final String JIKKYO_SOURCE = "nico_jikkyo_source";
    private static final String SHOW_DEMO = "nico_show_demo";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<ViewGroup> hostRef = new WeakReference<>(null);
    private static WeakReference<PassThroughWebView> webRef = new WeakReference<>(null);
    private static WeakReference<FrameLayout> overlayLayerRef = new WeakReference<>(null);
    private static Runnable pendingRelease;
    private static WeakReference<TextView> badgeRef = new WeakReference<>(null);
    private static WeakReference<TextView> sourceBadgeRef = new WeakReference<>(null);
    private static final Deque<String> perfHistory = new ArrayDeque<>();
    private static final int PERF_HISTORY_LIMIT = 12;
    private static String latestPerf;
    private static float displayHz;
    private static float requestedOverlayHz;
    private static WeakReference<Activity> windowActivityRef = new WeakReference<>(null);
    private static float previousWindowRefreshRate;
    private static boolean windowPreferenceApplied;
    private static WeakReference<Activity> modeActivityRef = new WeakReference<>(null);
    private static int previousWindowModeId;
    private static int requestedWindowModeId;
    private static float requestedWindowModeHz;
    private static WeakReference<Activity> minWindowActivityRef = new WeakReference<>(null);
    private static float previousWindowMinRefreshRate;
    private static boolean minWindowPreferenceApplied;
    private static String minWindowPreferenceStatus = "off";
    // Captured directly from Media3's VideoFrameReleaseHelper(Context) constructor.
    // Its Surface.setFrameRate hints may be sent before the overlay View is bound.
    private static volatile Context videoFrameRateContext;
    private static volatile float lastSurfaceFrameRateRequested = Float.NaN;
    private static volatile float lastSurfaceFrameRateSent = Float.NaN;
    private static volatile long surfaceFrameRateHintCount;
    private static volatile long surfaceFrameRateSuppressedCount;
    private static volatile String commentSourceStatus = "not-configured";
    private static volatile String commentSourceError = "";
    private static volatile int commentSourceCount;
    private static volatile int commentSourceGeneration;
    private static Object flow;
    private static boolean ready;
    private static boolean ticking;
    private static boolean loggedError;
    private static Class<?> stateClass;
    private static Method position, phase, rate, ad, seeking, generation;
    private static Method getFlowValue;

    private static final Runnable TICK = new Runnable() {
        @Override public void run() {
            ticking = false;
            ViewGroup host = hostRef.get();
            PassThroughWebView web = webRef.get();
            FrameLayout layer = overlayLayerRef.get();
            if (host == null || web == null || layer == null || web.getParent() != layer)
                return;
            // Compose can put the fullscreen controls *above* the AndroidView.
            // The comment renderer therefore lives in the Activity's decor
            // and follows the actual position of the video host.
            updateOverlayGeometry(host);
            bringOverlaysToFront(host);
            if (host.isAttachedToWindow()) {
                sendClock(web);
                NicoAutoSourceController.tick(host.getContext());
                TextView sourceBadge = sourceBadgeRef.get();
                if (sourceBadge != null) {
                    String text = NicoAutoSourceController.badgeLabel(commentSourceCount);
                    if (!text.contentEquals(sourceBadge.getText())) sourceBadge.setText(text);
                }
            }
            ticking = true;
            MAIN.postDelayed(this, 200);
        }
    };

    private NicoOverlayBridge() {}

    /** Called from the patched EpisodeViewModel.getEpisodeMeta() accessor. */
    public static void rememberEpisodeMetaFlow(Object metaFlow) {
        NicoAutoSourceController.rememberMetaFlow(metaFlow);
    }

    static void openEpisodeCommentSource(Context context, String spec, boolean notify) {
        loadJikkyoSource(context, spec, notify);
    }

    static void clearEpisodeComments() {
        commentSourceGeneration++;
        commentSourceCount = 0;
        commentSourceStatus = "not-configured";
        commentSourceError = "";
        PassThroughWebView current = webRef.get();
        if (ready && current != null) current.evaluateJavascript(
            "window.MorpheNico&&window.MorpheNico.setThreads([]);", null);
    }

    public static void bind(ViewGroup host, Object stateFlow) {
        if (host == null || stateFlow == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            host.post(() -> bind(host, stateFlow));
            return;
        }
        if (pendingRelease != null) {
            MAIN.removeCallbacks(pendingRelease);
            pendingRelease = null;
        }
        ViewGroup previous = hostRef.get();
        PassThroughWebView existing = webRef.get();
        FrameLayout currentLayer = overlayLayerRef.get();
        boolean samePlaybackHandoff = host != previous && previous != null
            && flow == stateFlow && existing != null && currentLayer != null
            && existing.getParent() == currentLayer && currentLayer.getParent() != null;
        if (samePlaybackHandoff) {
            // The AndroidView host changes during Compose fullscreen handoff.
            // Keep the same WebView, archive timeline and playback clock.
            if (currentLayer.getParent() == previous) {
                previous.removeView(currentLayer);
                host.addView(currentLayer, new FrameLayout.LayoutParams(-1, -1));
            }
            hostRef = new WeakReference<>(host);
            Log.i(TAG, "Reusing Nico renderer after player-host fullscreen handoff");
            updateOverlayGeometry(host);
        } else if (host != previous || existing == null || currentLayer == null
                || existing.getParent() != currentLayer) {
            if (previous != null) release();
            hostRef = new WeakReference<>(host);
            ready = false;
            loggedError = false;
            try {
                // A separate decor-level layer prevents Compose's fullscreen
                // layout/controls from obscuring comments rendered inside the
                // original EpisodePersistentPlayerHostView.
                Activity activity = findActivity(host.getContext());
                View decor = activity == null ? null : activity.getWindow().getDecorView();
                ViewGroup overlayParent = decor instanceof FrameLayout
                    ? (ViewGroup) decor : host;
                FrameLayout layer = new FrameLayout(host.getContext());
                layer.setBackgroundColor(Color.TRANSPARENT);
                layer.setClickable(false);
                layer.setFocusable(false);
                layer.setClipChildren(true);
                layer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                overlayLayerRef = new WeakReference<>(layer);
                FrameLayout.LayoutParams layerParams = new FrameLayout.LayoutParams(
                    overlayParent == host ? -1 : 1, overlayParent == host ? -1 : 1,
                    Gravity.TOP | Gravity.START);
                overlayParent.addView(layer, layerParams);
                PassThroughWebView web = makeWeb(host);
                webRef = new WeakReference<>(web);
                layer.addView(web, new FrameLayout.LayoutParams(-1, -1));
                TextView badge = makeFpsBadge(host);
                badgeRef = new WeakReference<>(badge);
                FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(-2, -2,
                    Gravity.TOP | Gravity.START);
                badgeParams.leftMargin = dp(host, 12);
                badgeParams.topMargin = dp(host, 52);
                layer.addView(badge, badgeParams);
                TextView sourceBadge = makeSourceBadge(host);
                sourceBadgeRef = new WeakReference<>(sourceBadge);
                // The episode player host is embedded inside Compose's
                // AndroidView. Its parent may intercept touches, so a visible
                // child badge there is not guaranteed to be tappable.
                // Add this *small* interactive control above Compose, at the
                // Activity Window decor level. All other player/overlay
                // pixels remain touch-transparent to TVer.
                ViewGroup sourceParent = decor instanceof FrameLayout ? (ViewGroup) decor : host;
                FrameLayout.LayoutParams sourceParams = new FrameLayout.LayoutParams(
                    -2, -2, Gravity.TOP | Gravity.END);
                sourceParams.rightMargin = dp(host, 12);
                sourceParams.topMargin = dp(host, 52);
                sourceParent.addView(sourceBadge, sourceParams);
                Log.i(TAG, "Comment source button attached to "
                    + (sourceParent == host ? "episode host (fallback)" : "Activity decor")
                    + ", clickable=" + sourceBadge.isClickable());
                updateOverlayGeometry(host);
                bringOverlaysToFront(host);
                web.loadDataWithBaseURL(
                    "https://appassets.androidplatform.net/", HTML, "text/html", "UTF-8", null
                );
                Log.i(TAG, "Attached local Nico test overlay");
            } catch (RuntimeException error) {
                Log.e(TAG, "WebView setup failed", error);
                release();
                return;
            }
        }
        if (flow != stateFlow) {
            flow = stateFlow;
            getFlowValue = null;
        }
        // Run AFTER Compose has called host.setSecure() on this update.
        host.post(() -> {
            if (hostRef.get() != host) return;
            updateOverlayGeometry(host);
            bringOverlaysToFront(host);
        });
        if (!ticking) {
            ticking = true;
            MAIN.post(TICK);
        }
    }

    public static void unbind(ViewGroup host) {
        if (host == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            host.post(() -> unbind(host));
            return;
        }
        if (hostRef.get() == host) {
            // Fullscreen handoff may dispose one Compose AndroidView just
            // before constructing its replacement. Allow it to rebind and
            // reuse the renderer without dropping already loaded comments.
            if (pendingRelease != null) MAIN.removeCallbacks(pendingRelease);
            final Runnable cleanup = () -> {
                if (hostRef.get() == host) release();
            };
            pendingRelease = cleanup;
            MAIN.postDelayed(cleanup, 700);
        }
    }

    private static void release() {
        if (pendingRelease != null) {
            MAIN.removeCallbacks(pendingRelease);
            pendingRelease = null;
        }
        MAIN.removeCallbacks(TICK);
        ticking = false;
        ready = false;
        flow = null;
        getFlowValue = null;
        stateClass = null;
        position = phase = rate = ad = seeking = generation = null;
        // Invalidate pending network loads and WebView deliveries on episode release.
        commentSourceGeneration++;
        NicoAutoSourceController.resetOnRelease();
        // Keep FPS history across playback -> settings navigation.
        restoreMinimumDisplayRefreshRatePreference();
        restoreDisplayModePreference();
        restoreWindowPreference();
        requestedOverlayHz = 0f;
        TextView badge = badgeRef.get();
        if (badge != null) {
            badge.setOnClickListener(null);
            if (badge.getParent() instanceof ViewGroup) {
                ((ViewGroup) badge.getParent()).removeView(badge);
            }
        }
        badgeRef = new WeakReference<>(null);
        TextView sourceBadge = sourceBadgeRef.get();
        if (sourceBadge != null) {
            sourceBadge.setOnClickListener(null);
            if (sourceBadge.getParent() instanceof ViewGroup)
                ((ViewGroup) sourceBadge.getParent()).removeView(sourceBadge);
        }
        sourceBadgeRef = new WeakReference<>(null);
        PassThroughWebView web = webRef.get();
        if (web != null) {
            if (web.getParent() instanceof ViewGroup) {
                ((ViewGroup) web.getParent()).removeView(web);
            }
            web.stopLoading();
            web.destroy();
        }
        webRef = new WeakReference<>(null);
        FrameLayout layer = overlayLayerRef.get();
        if (layer != null && layer.getParent() instanceof ViewGroup)
            ((ViewGroup) layer.getParent()).removeView(layer);
        overlayLayerRef = new WeakReference<>(null);
        hostRef = new WeakReference<>(null);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private static PassThroughWebView makeWeb(ViewGroup host) {
        PassThroughWebView web = new PassThroughWebView(host.getContext());
        web.setBackgroundColor(Color.TRANSPARENT);
        web.setClickable(false);
        web.setFocusable(false);
        web.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setBlockNetworkLoads(true);
        web.getSettings().setDomStorageEnabled(false);
        // All content is bundled locally; forward only our 5-second FPS summaries.
        // No JavascriptInterface and no per-frame Java/JS calls are needed.
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage message) {
                String text = message.message();
                if (text != null && text.startsWith(PERF_PREFIX)) {
                    String stats = text.substring(PERF_PREFIX.length());
                    Log.i(TAG, stats);
                    if (webRef.get() == web) {
                        if (Looper.myLooper() == Looper.getMainLooper()) {
                            onPerfStats(web, stats);
                        } else {
                            web.post(() -> onPerfStats(web, stats));
                        }
                    }
                    return true;
                }
                return super.onConsoleMessage(message);
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (webRef.get() == web) {
                    ready = true;
                    Display display = web.getDisplay();
                    displayHz = display == null ? 0f : display.getRefreshRate();
                    Log.i(TAG, String.format(Locale.ROOT,
                        "Renderer ready; reportedDisplayHz=%.1f (WebView rAF FPS measured separately)",
                        displayHz));
                    updateFpsBadge();
                    applyOverlayPreferences();
                    sendClock(web);
                    NicoAutoSourceController.tick(web.getContext());
                }
            }
        });
        return web;
    }

    private static TextView makeSourceBadge(ViewGroup host) {
        TextView label = new TextView(host.getContext());
        label.setText("💬 コメント");
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setTextColor(Color.WHITE);
        label.setGravity(Gravity.CENTER);
        label.setPadding(dp(host, 10), dp(host, 7), dp(host, 10), dp(host, 7));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(220, 18, 24, 36));
        background.setCornerRadius(dp(host, 9));
        label.setBackground(background);
        label.setElevation(dp(host, 4));
        label.setContentDescription("実況コメントの候補と時刻調整を開く");
        // Minimum 48dp touch target, independent from text width and density.
        label.setMinHeight(dp(host, 48));
        label.setMinWidth(dp(host, 96));
        label.setClickable(true);
        label.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
                Log.i(TAG, "Comment source button received ACTION_DOWN");
            return false; // Let TextView's click handling see DOWN and UP.
        });
        label.setOnClickListener(v -> {
            Log.i(TAG, "Comment source button clicked");
            Activity activity = findActivity(host.getContext());
            if (activity != null) NicoAutoSourceController.showPicker(activity);
            else Log.w(TAG, "Cannot open comment source picker: Activity not found");
        });
        return label;
    }

    // This HUD is a sibling of the WebView, so only its small bounds consume taps.
    // The fullscreen comment WebView remains touch-transparent.
    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private static TextView makeFpsBadge(ViewGroup host) {
        TextView badge = new TextView(host.getContext());
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(host, 9), dp(host, 6), dp(host, 9), dp(host, 6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(205, 18, 24, 36));
        bg.setCornerRadius(dp(host, 8));
        badge.setBackground(bg);
        badge.setElevation(dp(host, 4));
        badge.setText("FPS --  |  measuring...");
        badge.setContentDescription("Nico overlay FPS diagnostics, tap for details and copy");
        badge.setOnClickListener(v -> showPerfDialog(host.getContext()));
        badge.setOnLongClickListener(v -> {
            copyPerfReport(host.getContext());
            return true;
        });
        return badge;
    }

    /**
     * The WebView's containing layer belongs to the Activity decor rather than
     * to Compose's inline video host. Position it exactly over the video host
     * as the layout changes between portrait inline and fullscreen.
     *
     * This does not modify the video Surface, Window security flags, or player.
     */
    private static void updateOverlayGeometry(ViewGroup host) {
        FrameLayout layer = overlayLayerRef.get();
        if (host == null || layer == null || !(layer.getParent() instanceof ViewGroup))
            return;
        ViewGroup parent = (ViewGroup) layer.getParent();
        boolean visible = host.isAttachedToWindow() && host.isShown()
            && host.getWidth() > 0 && host.getHeight() > 0;
        if (!visible) {
            if (layer.getVisibility() != View.INVISIBLE)
                layer.setVisibility(View.INVISIBLE);
            return;
        }
        if (layer.getParent() != host) {
            int[] hostLocation = new int[2], parentLocation = new int[2];
            host.getLocationOnScreen(hostLocation);
            parent.getLocationOnScreen(parentLocation);
            int x = hostLocation[0] - parentLocation[0];
            int y = hostLocation[1] - parentLocation[1];
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)
                layer.getLayoutParams();
            if (params.width != host.getWidth() || params.height != host.getHeight()
                    || params.leftMargin != x || params.topMargin != y) {
                params.width = host.getWidth();
                params.height = host.getHeight();
                params.leftMargin = x;
                params.topMargin = y;
                layer.setLayoutParams(params);
                Log.d(TAG, "Comment overlay geometry x=" + x + " y=" + y
                    + " w=" + params.width + " h=" + params.height);
            }
        }
        if (layer.getVisibility() != View.VISIBLE)
            layer.setVisibility(View.VISIBLE);
    }

    private static void bringOverlaysToFront(ViewGroup host) {
        FrameLayout layer = overlayLayerRef.get();
        TextView sourceBadge = sourceBadgeRef.get();
        if (layer != null && layer.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) layer.getParent();
            // A sibling of the Activity's ComposeView, not a child of it.
            // Do not cause endless reordering when the button is already last.
            int targetIndex = parent.getChildCount() - 1
                - (sourceBadge != null && sourceBadge.getParent() == parent ? 1 : 0);
            if (parent.indexOfChild(layer) != targetIndex) layer.bringToFront();
        }
        if (sourceBadge != null && sourceBadge.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) sourceBadge.getParent();
            if (parent.indexOfChild(sourceBadge) != parent.getChildCount() - 1)
                sourceBadge.bringToFront();
        }
    }

    private static String metric(String sample, String key) {
        for (String field : sample.split(" ")) {
            if (field.startsWith(key + "=")) return field.substring(key.length() + 1);
        }
        return "--";
    }

    private static void onPerfStats(PassThroughWebView web, String stats) {
        ViewGroup host = hostRef.get();
        if (webRef.get() != web || host == null || stats.length() > 1200) return;
        if (!stats.startsWith("fps=") || !stats.contains("p95ms=")) return;
        Display displayNow = web.getDisplay();
        float refreshNow = displayNow == null ? 0f : displayNow.getRefreshRate();
        float modeNow = displayNow == null || displayNow.getMode() == null ? 0f
            : displayNow.getMode().getRefreshRate();
        Activity activity = findActivity(host.getContext());
        WindowManager.LayoutParams windowAttrs = activity == null ? null
            : activity.getWindow().getAttributes();
        float windowHz = windowAttrs == null ? 0f : windowAttrs.preferredRefreshRate;
        int windowModeId = windowAttrs == null ? 0 : windowAttrs.preferredDisplayModeId;
        float minimumWindowHz = getMinimumWindowRefreshRate(windowAttrs);
        boolean minWindowRequested = minWindowPreferenceApplied && activity != null
            && activity == minWindowActivityRef.get()
            && Float.compare(minimumWindowHz, 120f) == 0;
        boolean windowRequested = windowPreferenceApplied && activity != null
            && activity == windowActivityRef.get() && windowHz == 120f;
        boolean mode144Requested = activity != null && activity == modeActivityRef.get()
            && requestedWindowModeId != 0 && windowModeId == requestedWindowModeId;
        int activeDisplayModeId = displayNow == null || displayNow.getMode() == null ? 0
            : displayNow.getMode().getModeId();
        boolean surfaceHintOff = host.getContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(VIDEO_SURFACE_RATE_OFF, false);
        String sample = stats + String.format(Locale.ROOT,
            " request120=%s requestedHz=%.1f displayHzNow=%.1f displayModeHz=%.1f"
            + " window120=%s windowPreferredHz=%.1f windowModeId=%d"
            + " mode144=%s requestedModeId=%d requestedModeHz=%.1f displayModeIdNow=%d"
            + " min120=%s min120Applied=%s minWindowHz=%.1f min120Status=%s"
            + " surfaceHintOff=%s surfaceFpsWanted=%.2f surfaceFpsSent=%.2f"
            + " surfaceHintCalls=%d surfaceHintSuppressed=%d"
            + " commentSource=%s loadedComments=%d autoSearch=%s epgCandidates=%d",
            requestedOverlayHz == 120f, requestedOverlayHz, refreshNow, modeNow,
            windowRequested, windowHz, windowModeId,
            mode144Requested, requestedWindowModeId, requestedWindowModeHz,
            activeDisplayModeId,
            host.getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(WINDOW_MIN_120HZ, false), minWindowRequested,
            minimumWindowHz, minWindowPreferenceStatus, surfaceHintOff,
            lastSurfaceFrameRateRequested, lastSurfaceFrameRateSent,
            surfaceFrameRateHintCount, surfaceFrameRateSuppressedCount,
            commentSourceStatus, commentSourceCount,
            NicoAutoSourceController.status(), NicoAutoSourceController.candidateCount());
        latestPerf = sample;
        perfHistory.addLast(sample);
        while (perfHistory.size() > PERF_HISTORY_LIMIT) perfHistory.removeFirst();
        updateFpsBadge();
        // Save one compact report every five seconds. It remains available
        // from Morphe settings after TVer's playback view has been released.
        host.getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(SAVED_FPS_REPORT, perfReport(host.getContext())).apply();
    }

    private static void updateFpsBadge() {
        TextView badge = badgeRef.get();
        if (badge == null) return;
        String fps = latestPerf == null ? "--" : metric(latestPerf, "fps");
        String p95 = latestPerf == null ? "--" : metric(latestPerf, "p95ms");
        String hz = displayHz > 0 ? String.format(Locale.ROOT, "%.0f", displayHz) : "--";
        badge.setText("FPS " + fps + " / " + hz + "Hz\np95 " + p95 + "ms  ·  Tap to copy");
        ViewGroup host = hostRef.get();
        if (host != null) updateSettingBadgeVisibility(host.getContext(), badge);
    }

    private static void applyOverlayPreferences() {
        ViewGroup host = hostRef.get();
        PassThroughWebView web = webRef.get();
        if (host == null) return;
        boolean showComments = host.getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(COMMENTS_VISIBLE, true);
        // preferredDisplayModeId overrides preferredMinDisplayRefreshRate.
        // Give the opt-in minimum-rate experiment priority over the older
        // 144Hz mode-ID and 120Hz preferredRefreshRate experiments.
        updateDisplayModePreference(host.getContext());
        updateWindowPreference(host.getContext());
        updateMinimumDisplayRefreshRatePreference(host.getContext());
        if (web != null && web.getParent() == overlayLayerRef.get()) {
            boolean demoOn = host.getContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(SHOW_DEMO, false);
            if (ready) web.evaluateJavascript(
                "window.MorpheNico&&window.MorpheNico.setDemoMode(" + demoOn + ");",
                null);
            // API 35+: per-View refresh preference, not an FPS guarantee.
            // Request the faster display only while comments are enabled.
            boolean request120 = showComments && host.getContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREFER_120FPS, false);
            updateRequestedFrameRate(web, request120);
            if (ready) {
                web.evaluateJavascript(
                    "window.MorpheNico&&window.MorpheNico.setVisible(" +
                    (showComments ? "true" : "false") + ");", null);
            }
        }
        updateFpsBadge();
    }

    /**
     * Called by the patched Media3 VideoFrameReleaseHelper constructor, before
     * any video frame-rate hints are sent. No TVer SDK classes are referenced.
     */
    public static void rememberVideoFrameRateContext(Context context) {
        if (context != null) videoFrameRateContext = context.getApplicationContext();
    }

    /**
     * Called from VideoFrameReleaseHelper.Api30.setSurfaceFrameRate(), immediately
     * before Android's Surface.setFrameRate(). A zero request clears an existing
     * frame-rate hint, including the 29.97Hz fixed-source hint seen in dumpsys.
     * Keeping the upstream helper state unchanged makes the experiment reversible
     * when the next player session starts (reopen playback after toggling).
     */
    public static float filterVideoSurfaceFrameRate(float requestedHz) {
        Context context = videoFrameRateContext;
        boolean disabled = context != null && context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(VIDEO_SURFACE_RATE_OFF, false);
        float appliedHz = disabled ? 0f : requestedHz;
        lastSurfaceFrameRateRequested = requestedHz;
        lastSurfaceFrameRateSent = appliedHz;
        surfaceFrameRateHintCount++;
        if (disabled && requestedHz > 0f) {
            surfaceFrameRateSuppressedCount++;
            Log.i(TAG, String.format(Locale.ROOT,
                "Suppressed video Surface.setFrameRate(%.3fHz); requested 0Hz instead",
                requestedHz));
        }
        return appliedHz;
    }

    private static void updateRequestedFrameRate(PassThroughWebView web, boolean prefer120) {
        if (Build.VERSION.SDK_INT < 35) {
            requestedOverlayHz = 0f;
            return;
        }
        float rate = prefer120 ? 120f : View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT;
        if (web.getRequestedFrameRate() == rate) {
            requestedOverlayHz = prefer120 ? 120f : 0f;
            return;
        }
        web.setRequestedFrameRate(rate);
        requestedOverlayHz = prefer120 ? 120f : 0f;
        Log.i(TAG, String.format(Locale.ROOT,
            "Nico frame-rate preference %s (requested=%.1fHz, actual frame rate not guaranteed)",
            prefer120 ? "ON" : "OFF", web.getRequestedFrameRate()));
    }

    /**
     * Experimental 144Hz mode request for TVer's video window.
     * Only choose a supported 144Hz mode at the active physical resolution.
     * Preserve TVer's original preferredDisplayModeId, restoring it on OFF
     * and when the playback view is released. This does NOT override the
     * system's developer-option setting or guarantee that Android obeys.
     */
    private static void updateDisplayModePreference(Context context) {
        boolean enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(WINDOW_MODE_144HZ, false)
            && !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(WINDOW_MIN_120HZ, false);
        Activity activity = findActivity(context);
        if (!enabled || activity == null || activity.isFinishing() || activity.isDestroyed()) {
            restoreDisplayModePreference();
            return;
        }
        if (requestedWindowModeId != 0 && modeActivityRef.get() != activity) {
            restoreDisplayModePreference();
        }
        try {
            ViewGroup host = hostRef.get();
            Display display = host == null ? null : host.getDisplay();
            if (display == null) display = activity.getDisplay();
            if (display == null || display.getMode() == null) {
                Log.i(TAG, "144Hz display-mode request skipped: no attached display");
                return;
            }
            Display.Mode current = display.getMode();
            Display.Mode target = null;
            for (Display.Mode mode : display.getSupportedModes()) {
                if (mode.getPhysicalWidth() != current.getPhysicalWidth()
                    || mode.getPhysicalHeight() != current.getPhysicalHeight()) continue;
                float diff = Math.abs(mode.getRefreshRate() - 144f);
                if (diff <= 1f && (target == null
                    || diff < Math.abs(target.getRefreshRate() - 144f))) target = mode;
            }
            if (target == null) {
                restoreDisplayModePreference();
                Log.i(TAG, String.format(Locale.ROOT,
                    "144Hz display-mode request skipped: no supported 144Hz mode at %dx%d",
                    current.getPhysicalWidth(), current.getPhysicalHeight()));
                return;
            }
            Window window = activity.getWindow();
            if (window == null) return;
            WindowManager.LayoutParams attrs = window.getAttributes();
            int targetId = target.getModeId();
            if (requestedWindowModeId == 0) {
                previousWindowModeId = attrs.preferredDisplayModeId;
                modeActivityRef = new WeakReference<>(activity);
            } else if (attrs.preferredDisplayModeId != requestedWindowModeId) {
                // Another component changed this Window while the experiment
                // was active. Do not overwrite its more recent preference.
                Log.i(TAG, "144Hz mode request displaced by another Window preference");
                modeActivityRef = new WeakReference<>(null);
                previousWindowModeId = 0;
                requestedWindowModeId = 0;
                requestedWindowModeHz = 0f;
                return;
            }
            if (attrs.preferredDisplayModeId != targetId) {
                attrs.preferredDisplayModeId = targetId;
                window.setAttributes(attrs);
            }
            if (requestedWindowModeId != targetId) {
                Log.i(TAG, String.format(Locale.ROOT,
                    "Window preferredDisplayModeId requested=%d (%.1fHz %dx%d); previous=%d",
                    targetId, target.getRefreshRate(), target.getPhysicalWidth(),
                    target.getPhysicalHeight(), previousWindowModeId));
            }
            requestedWindowModeId = targetId;
            requestedWindowModeHz = target.getRefreshRate();
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not request Window 144Hz display mode", error);
        }
    }

    private static void restoreDisplayModePreference() {
        Activity activity = modeActivityRef.get();
        if (requestedWindowModeId != 0 && activity != null) {
            try {
                Window window = activity.getWindow();
                if (window != null) {
                    WindowManager.LayoutParams attrs = window.getAttributes();
                    // Do not undo a newer preference from TVer or another patch.
                    if (attrs.preferredDisplayModeId == requestedWindowModeId) {
                        attrs.preferredDisplayModeId = previousWindowModeId;
                        window.setAttributes(attrs);
                        Log.i(TAG, "Restored previous Window preferredDisplayModeId="
                            + previousWindowModeId);
                    }
                }
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not restore Window display mode", error);
            }
        }
        modeActivityRef = new WeakReference<>(null);
        previousWindowModeId = 0;
        requestedWindowModeId = 0;
        requestedWindowModeHz = 0f;
    }

    /**
     * Request 120Hz at the Activity Window level, independently of the
     * WebView's per-View hint. This is a preference, not a forced mode.
     * Preserve a pre-existing app preference and restore it on release.
     */
    private static void updateWindowPreference(Context context) {
        boolean enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(WINDOW_PREFER_120HZ, false)
            && !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(WINDOW_MIN_120HZ, false);
        Activity activity = findActivity(context);
        if (!enabled || activity == null || activity.isFinishing() || activity.isDestroyed()) {
            restoreWindowPreference();
            return;
        }
        if (windowPreferenceApplied && windowActivityRef.get() != activity) {
            restoreWindowPreference();
        }
        try {
            Window window = activity.getWindow();
            if (window == null) return;
            WindowManager.LayoutParams attrs = window.getAttributes();
            // A mode ID overrides preferredRefreshRate. Do not overwrite the
            // host application's display-mode preference in this experiment.
            if (attrs.preferredDisplayModeId != 0) {
                Log.i(TAG, "Window 120Hz hint skipped: host has preferredDisplayModeId="
                    + attrs.preferredDisplayModeId);
                return;
            }
            if (!windowPreferenceApplied) {
                previousWindowRefreshRate = attrs.preferredRefreshRate;
                windowActivityRef = new WeakReference<>(activity);
                windowPreferenceApplied = true;
            }
            if (Float.compare(attrs.preferredRefreshRate, 120f) != 0) {
                attrs.preferredRefreshRate = 120f;
                window.setAttributes(attrs);
                Log.i(TAG, String.format(Locale.ROOT,
                    "Window preferredRefreshRate requested=120Hz previous=%.1fHz",
                    previousWindowRefreshRate));
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not set Window 120Hz preference", error);
        }
    }

    private static void restoreWindowPreference() {
        Activity activity = windowActivityRef.get();
        if (windowPreferenceApplied && activity != null) {
            try {
                Window window = activity.getWindow();
                if (window != null) {
                    WindowManager.LayoutParams attrs = window.getAttributes();
                    // Avoid clobbering a later setting from TVer or another patch.
                    if (Float.compare(attrs.preferredRefreshRate, 120f) == 0) {
                        attrs.preferredRefreshRate = previousWindowRefreshRate;
                        window.setAttributes(attrs);
                        Log.i(TAG, String.format(Locale.ROOT,
                            "Restored previous Window preferredRefreshRate=%.1fHz",
                            previousWindowRefreshRate));
                    }
                }
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not restore Window refresh preference", error);
            }
        }
        windowActivityRef = new WeakReference<>(null);
        previousWindowRefreshRate = 0f;
        windowPreferenceApplied = false;
    }

    /**
     * Experimental hidden @TestApi on Android 16. It is ignored by Android
     * whenever preferredDisplayModeId is nonzero. We access it reflectively
     * so this extension does not need a non-public Android SDK at compile time.
     * A vendor hidden-API restriction may reject the call; surface this in
     * diagnostics rather than claiming the request was honored.
     */
    private static Field minimumRefreshRateField() throws NoSuchFieldException {
        return WindowManager.LayoutParams.class.getField("preferredMinDisplayRefreshRate");
    }

    private static float getMinimumWindowRefreshRate(WindowManager.LayoutParams attrs) {
        if (attrs == null) return Float.NaN;
        try {
            return minimumRefreshRateField().getFloat(attrs);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return Float.NaN;
        }
    }

    private static void updateMinimumDisplayRefreshRatePreference(Context context) {
        boolean enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(WINDOW_MIN_120HZ, false);
        Activity activity = findActivity(context);
        if (!enabled || activity == null || activity.isFinishing() || activity.isDestroyed()) {
            restoreMinimumDisplayRefreshRatePreference();
            minWindowPreferenceStatus = enabled ? "no-activity" : "off";
            return;
        }
        if (minWindowPreferenceApplied && minWindowActivityRef.get() != activity) {
            restoreMinimumDisplayRefreshRatePreference();
        }
        try {
            Window window = activity.getWindow();
            if (window == null) {
                minWindowPreferenceStatus = "no-window";
                return;
            }
            WindowManager.LayoutParams attrs = window.getAttributes();
            // Never clear a mode ID belonging to TVer or some other patch.
            // Morphe's own 144Hz mode is restored by updateDisplayModePreference()
            // before reaching this method, even if both switches are ON.
            if (attrs.preferredDisplayModeId != 0) {
                minWindowPreferenceStatus = "blocked-by-mode-id";
                return;
            }
            Field field = minimumRefreshRateField();
            float current = field.getFloat(attrs);
            if (minWindowPreferenceApplied && Float.compare(current, 120f) != 0) {
                // TVer changed the field; do not clobber a newer setting.
                minWindowPreferenceApplied = false;
                minWindowActivityRef = new WeakReference<>(null);
                minWindowPreferenceStatus = "overridden";
                return;
            }
            if (!minWindowPreferenceApplied) {
                previousWindowMinRefreshRate = current;
                minWindowActivityRef = new WeakReference<>(activity);
                minWindowPreferenceApplied = true;
            }
            if (Float.compare(current, 120f) != 0) {
                field.setFloat(attrs, 120f);
                window.setAttributes(attrs);
                Log.i(TAG, String.format(Locale.ROOT,
                    "Experimental Window preferredMinDisplayRefreshRate=120Hz previous=%.1f",
                    previousWindowMinRefreshRate));
            }
            minWindowPreferenceStatus = "requested";
        } catch (ReflectiveOperationException | RuntimeException error) {
            minWindowPreferenceStatus = "unavailable";
            Log.w(TAG, "Window minimum 120Hz @TestApi unavailable on this Android build", error);
        }
    }

    private static void restoreMinimumDisplayRefreshRatePreference() {
        Activity activity = minWindowActivityRef.get();
        if (minWindowPreferenceApplied && activity != null) {
            try {
                Window window = activity.getWindow();
                if (window != null) {
                    WindowManager.LayoutParams attrs = window.getAttributes();
                    Field field = minimumRefreshRateField();
                    if (Float.compare(field.getFloat(attrs), 120f) == 0) {
                        field.setFloat(attrs, previousWindowMinRefreshRate);
                        window.setAttributes(attrs);
                        Log.i(TAG, "Restored original Window minimum refresh rate="
                            + previousWindowMinRefreshRate);
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                Log.w(TAG, "Could not restore original Window minimum refresh rate", error);
            }
        }
        minWindowActivityRef = new WeakReference<>(null);
        previousWindowMinRefreshRate = 0f;
        minWindowPreferenceApplied = false;
    }

    /**
     * Read the chosen broadcaster archive off the main thread, then stream
     * bounded JSON arrays to the local, network-blocked WebView. The generation
     * guard prevents stale episodes from receiving data after navigation.
     */
    private static void loadJikkyoSource(Context context, String spec, boolean showToast) {
        PassThroughWebView web = webRef.get();
        if (web == null || !ready) {
            commentSourceStatus = "open-playback";
            if (showToast) Toast.makeText(context,
                "保存しました。動画を開くと読み込みます", Toast.LENGTH_LONG).show();
            return;
        }
        final int generation = ++commentSourceGeneration;
        commentSourceStatus = "loading";
        commentSourceError = "";
        commentSourceCount = 0;
        new Thread(() -> {
            try {
                JikkyoCommentSource.Result result = JikkyoCommentSource.fetch(spec);
                MAIN.post(() -> {
                    if (generation != commentSourceGeneration || webRef.get() != web) return;
                    web.evaluateJavascript(
                        "window.MorpheNico&&window.MorpheNico.beginImport();", null);
                    deliverJikkyoBatches(web, result.batches, 0, generation,
                        result.count, showToast);
                });
            } catch (Exception error) {
                Log.w(TAG, "Jikkyo archive fetch failed for " + spec, error);
                String message = error.getMessage();
                final String reason = message == null || message.trim().isEmpty()
                    ? error.getClass().getSimpleName()
                    : message.length() > 150 ? message.substring(0, 150) : message;
                MAIN.post(() -> {
                    if (generation != commentSourceGeneration || webRef.get() != web) return;
                    commentSourceStatus = "error";
                    commentSourceError = reason;
                    if (showToast) {
                        Activity activity = findActivity(context);
                        if (activity != null && !activity.isFinishing()
                            && !activity.isDestroyed()) {
                            new AlertDialog.Builder(activity)
                                .setTitle("実況過去ログを取得できませんでした")
                                .setMessage(reason
                                    + "\n\n放送日時を確認してから再度お試しください。")
                                .setPositiveButton("OK", null)
                                .show();
                        } else {
                            Toast.makeText(context, reason, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }, "Morphe-Jikkyo").start();
    }

    private static void deliverJikkyoBatches(PassThroughWebView web, List<String> batches,
                                              int index, int generation, int count,
                                              boolean showToast) {
        if (generation != commentSourceGeneration || webRef.get() != web || !ready)
            return;
        if (index < batches.size()) {
            web.evaluateJavascript(
                "window.MorpheNico&&window.MorpheNico.appendComments("
                    + batches.get(index) + ");", null);
            MAIN.postDelayed(() -> deliverJikkyoBatches(web, batches, index + 1,
                generation, count, showToast), 20);
            return;
        }
        web.evaluateJavascript("window.MorpheNico&&window.MorpheNico.endImport();", null);
        commentSourceCount = count;
        commentSourceError = "";
        commentSourceStatus = count == 0 ? "empty" : "loaded";
        Log.i(TAG, "Loaded Jikkyo comments=" + count);
        if (showToast) Toast.makeText(web.getContext(),
            "実況コメントを" + count + "件読み込みました", Toast.LENGTH_LONG).show();
    }

    private static String perfReport(Context context) {
        if (perfHistory.isEmpty()) {
            String stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(SAVED_FPS_REPORT, null);
            if (stored != null && !stored.isEmpty()) return stored;
        }
        StringBuilder report = new StringBuilder("TVer Morphe Nico Overlay FPS diagnostics\n");
        report.append("Device: ").append(Build.MANUFACTURER).append(" ")
            .append(Build.MODEL).append(" / Android ").append(Build.VERSION.RELEASE)
            .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        report.append("Display reported refresh: ")
            .append(String.format(Locale.ROOT, "%.1f Hz", displayHz)).append("\n");
        report.append("Measurement: WebView requestAnimationFrame cadence, not physical presented FPS.\n");
        report.append("Windows: five seconds each; oldest first; maximum 12.\n");
        if (!commentSourceError.isEmpty()) {
            report.append("Last comment source error: ")
                .append(commentSourceError).append("\n");
        }
        report.append("\n");
        if (perfHistory.isEmpty()) {
            report.append("Collecting first five seconds of frame data...");
        } else {
            int window = 1;
            for (String sample : perfHistory) {
                report.append(window++).append(": ").append(sample).append("\n");
            }
        }
        return report.toString();
    }

    private static void updateSettingBadgeVisibility(Context context, TextView badge) {
        boolean enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(FPS_VISIBLE, true);
        badge.setVisibility(enabled ? View.VISIBLE : View.GONE);
    }

    private static Activity findActivity(Context source) {
        Context context = source;
        for (int i = 0; i < 12 && context != null; i++) {
            if (context instanceof Activity) return (Activity) context;
            if (!(context instanceof ContextWrapper)) break;
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == context) break;
            context = next;
        }
        return null;
    }

    private static void copyPerfReport(Context context) {
        ClipboardManager clipboard = (ClipboardManager) context
            .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(context, "Clipboard unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Morphe Nico FPS report", perfReport(context)));
        Toast.makeText(context, "FPS report copied", Toast.LENGTH_SHORT).show();
    }

    private static void showPerfDialog(Context context) {
        Activity activity = findActivity(context);
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            copyPerfReport(context);
            return;
        }
        new AlertDialog.Builder(activity)
            .setTitle("Nico comment overlay · FPS")
            .setMessage(perfReport(activity))
            .setPositiveButton("Copy report", (dialog, which) -> copyPerfReport(activity))
            .setNegativeButton("Close", null)
            .show();
    }

    /**
     * Extend the PiP patch's Morphe settings panel before it is attached to
     * the AlertDialog. Scrolling keeps existing settings usable on phones.
     */
    public static View enhanceSettings(Activity activity, LinearLayout panel) {
        if (activity == null || panel == null) return panel;
        TextView heading = new TextView(activity);
        heading.setText("ニコニコ風コメント・FPS計測");
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        heading.setTextColor(Color.DKGRAY);
        heading.setPadding(dp(panel, 20), dp(panel, 18), dp(panel, 12), dp(panel, 4));
        panel.addView(heading);

        panel.addView(makeSettingSwitch(activity, "コメント表示を有効にする",
            COMMENTS_VISIBLE, true));
        panel.addView(makeSettingSwitch(activity, "デモコメントを表示（通常はOFF）",
            SHOW_DEMO, false));
        panel.addView(makeSettingSwitch(activity,
            "番組情報から実況コメントを自動検索（完全一致時のみ自動読込）",
            NicoAutoSourceController.AUTO_ENABLED, true));
        android.widget.Button selectSource = new android.widget.Button(activity);
        selectSource.setText("現在の番組の実況コメントを探す・選ぶ");
        selectSource.setAllCaps(false);
        selectSource.setOnClickListener(v -> NicoAutoSourceController.showPicker(activity));
        panel.addView(selectSource);
        panel.addView(makeSettingSwitch(activity, "動画上にFPSを表示する",
            FPS_VISIBLE, true));
        // Keep the normal comment controls visible; hide unsuccessful
        // high-refresh-rate experiments in an optional technical section.
        android.widget.Button showExperiments = new android.widget.Button(activity);
        showExperiments.setText("高Hz実験の詳細を表示");
        showExperiments.setAllCaps(false);
        panel.addView(showExperiments);
        LinearLayout experiments = new LinearLayout(activity);
        experiments.setOrientation(LinearLayout.VERTICAL);
        experiments.setVisibility(View.GONE);
        panel.addView(experiments);
        showExperiments.setOnClickListener(v -> {
            boolean expand = experiments.getVisibility() == View.GONE;
            experiments.setVisibility(expand ? View.VISIBLE : View.GONE);
            showExperiments.setText(expand
                ? "高Hz実験の詳細を閉じる" : "高Hz実験の詳細を表示");
        });
        experiments.addView(makeSettingSwitch(activity, "コメント描画を120fps優先（実験・電池消費増の可能性）",
            PREFER_120FPS, false));
        experiments.addView(makeSettingSwitch(activity, "動画再生中はWindow全体に120Hz要求（実験）",
            WINDOW_PREFER_120HZ, false));
        experiments.addView(makeSettingSwitch(activity, "動画再生中に144Hz表示モードを優先（実験・電池消費増）",
            WINDOW_MODE_144HZ, false));
        experiments.addView(makeSettingSwitch(activity,
            "動画再生中にWindowの最小120Hzを要求（実験・144Hzモード指定より優先・非公開API）",
            WINDOW_MIN_120HZ, false));
        experiments.addView(makeSettingSwitch(activity, "動画Surfaceのフレームレート要求を解除（実験・再生画面を開き直して反映）",
            VIDEO_SURFACE_RATE_OFF, false));

        android.widget.Button resetHz = new android.widget.Button(activity);
        resetHz.setAllCaps(false);
        resetHz.setText("高Hz実験をすべてOFF（60fps通常運用）");
        resetHz.setOnClickListener(v -> {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(PREFER_120FPS, false)
                .putBoolean(WINDOW_PREFER_120HZ, false)
                .putBoolean(WINDOW_MODE_144HZ, false)
                .putBoolean(WINDOW_MIN_120HZ, false)
                .putBoolean(VIDEO_SURFACE_RATE_OFF, false)
                .apply();
            applyOverlayPreferences();
            Toast.makeText(activity,
                "高Hz実験を解除しました。設定画面と再生画面を開き直すと完全に反映されます",
                Toast.LENGTH_LONG).show();
        });
        panel.addView(resetHz);

        TextView sourceHeading = new TextView(activity);
        sourceHeading.setText("ニコニコ実況過去ログ（手動指定・自動検索できない場合）");
        sourceHeading.setTextColor(Color.DKGRAY);
        sourceHeading.setPadding(dp(panel, 24), dp(panel, 14), dp(panel, 24), dp(panel, 4));
        panel.addView(sourceHeading);
        TextView sourceHelp = new TextView(activity);
        sourceHelp.setText("チャンネル | 開始日時(JST) | 終了日時(JST) | ずれ補正(秒)\n"
            + "例: jk1|2026-10-23 21:00|2026-10-23 22:00|0\n"
            + "自動検索に候補がなければ、こちらから指定できます。");
        sourceHelp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sourceHelp.setTextColor(Color.DKGRAY);
        sourceHelp.setPadding(dp(panel, 24), dp(panel, 4), dp(panel, 24), dp(panel, 8));
        panel.addView(sourceHelp);
        EditText sourceField = new EditText(activity);
        sourceField.setSingleLine(false);
        sourceField.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        sourceField.setHint("jk1|2026-10-23 21:00|2026-10-23 22:00|0");
        String episodeSpecific = NicoAutoSourceController.sourceForCurrent(activity);
        sourceField.setText(episodeSpecific.isEmpty()
            ? activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(JIKKYO_SOURCE, "") : episodeSpecific);
        panel.addView(sourceField);
        android.widget.Button sourceLoad = new android.widget.Button(activity);
        sourceLoad.setAllCaps(false);
        sourceLoad.setText("実況コメントを読み込む（空欄で解除）");
        sourceLoad.setOnClickListener(v -> {
            String spec = sourceField.getText().toString().trim();
            if (spec.isEmpty()) {
                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .remove(JIKKYO_SOURCE).apply();
                NicoAutoSourceController.setManual(activity, "");
                clearEpisodeComments();
                Toast.makeText(activity, "現在の番組のコメントを解除しました",
                    Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                JikkyoCommentSource.validate(spec);
            } catch (IllegalArgumentException error) {
                Toast.makeText(activity, "指定が不正です: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
                return;
            }
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(JIKKYO_SOURCE, spec).putBoolean(SHOW_DEMO, false).apply();
            NicoAutoSourceController.setManual(activity, spec);
            applyOverlayPreferences();
            loadJikkyoSource(activity, spec, true);
        });
        panel.addView(sourceLoad);

        android.widget.Button copy = new android.widget.Button(activity);
        copy.setText("FPS計測ログを表示・コピー");
        copy.setAllCaps(false);
        copy.setOnClickListener(v -> showPerfDialog(activity));
        panel.addView(copy);

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.addView(panel, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private static Switch makeSettingSwitch(Activity activity, String title,
                                            String key, boolean defaultValue) {
        Switch toggle = new Switch(activity);
        toggle.setText(title);
        toggle.setPadding(dp(toggle, 24), dp(toggle, 12), dp(toggle, 24), dp(toggle, 12));
        toggle.setChecked(activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener((button, enabled) -> {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(key, enabled).apply();
            applyOverlayPreferences();
        });
        return toggle;
    }

    private static void sendClock(PassThroughWebView web) {
        if (flow == null || !ready) return;
        try {
            if (getFlowValue == null) {
                Class<?> flowInterface = Class.forName(
                    "kotlinx.coroutines.flow.StateFlow", false, flow.getClass().getClassLoader()
                );
                getFlowValue = flowInterface.getMethod("getValue");
            }
            Object state = getFlowValue.invoke(flow);
            if (state == null) return;
            if (stateClass != state.getClass()) {
                stateClass = state.getClass();
                position = stateClass.getMethod("getPositionMs");
                phase = stateClass.getMethod("getPhase");
                rate = stateClass.getMethod("getRate");
                ad = stateClass.getMethod("isAd");
                seeking = stateClass.getMethod("isSeeking");
                generation = stateClass.getMethod("getSeekGeneration");
            }
            long ms = Math.max(0, ((Number) position.invoke(state)).longValue());
            float speed = ((Number) rate.invoke(state)).floatValue();
            if (!Float.isFinite(speed) || speed <= 0) speed = 1f;
            boolean playing = "PLAYING".equals(String.valueOf(phase.invoke(state)));
            boolean isAd = (Boolean) ad.invoke(state);
            boolean isSeeking = (Boolean) seeking.invoke(state);
            long seekGeneration = ((Number) generation.invoke(state)).longValue();
            String command = String.format(Locale.ROOT,
                "window.MorpheNico&&window.MorpheNico.update(%d,%s,%.4f,%s,%s,%d);",
                ms, playing, speed, isAd, isSeeking, seekGeneration
            );
            web.evaluateJavascript(command, null);
        } catch (Exception error) {
            if (!loggedError) {
                loggedError = true;
                Log.e(TAG, "Could not read PlaybackState", error);
            }
        }
    }

    // Returning false allows touch dispatch to continue to TVer's controls.
    private static final class PassThroughWebView extends WebView {
        PassThroughWebView(android.content.Context context) { super(context); }
        @Override public boolean dispatchTouchEvent(MotionEvent event) { return false; }
        @Override public boolean onTouchEvent(MotionEvent event) { return false; }
    }

    /*
     * A standalone test renderer. This intentionally does not claim to fully
     * reproduce Nico comment art. An optional v1-shaped thread array may later
     * be provided through MorpheNico.setThreads(threads).
     */
    private static final String HTML = """
        <!doctype html><html><head><meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'">
        <style>
        html,body{margin:0;width:100%;height:100%;background:transparent;overflow:hidden}
        canvas{width:100%;height:100%;object-fit:contain;display:block;pointer-events:none}
        </style></head><body><canvas id="screen" width="1920" height="1080"></canvas>
        <script>
        (function(){
            'use strict';

            const cvs=document.getElementById('screen'),ctx=cvs.getContext('2d');
            const W=1920,H=1080,scrollMs=6500,fixedMs=4000;
            const palette={white:'#fff',red:'#f33',pink:'#f8a',orange:'#f90',
                yellow:'#ff0',green:'#0f0',cyan:'#0ff',blue:'#44f',purple:'#c7f',black:'#000'};
            const demo=[
                {vposMs:2000,body:'Morphe × TVer テストコメント',commands:['white']},
                {vposMs:3700,body:'8888888888',commands:['yellow']},
                {vposMs:5200,body:'上固定コメント',commands:['ue','cyan']},
                {vposMs:7000,body:'下固定コメント',commands:['shita','red']},
                {vposMs:9200,body:'特殊コメントは次の段階で対応',commands:['small','green']},
                {vposMs:12500,body:'改行テスト'+String.fromCharCode(10)+'２行目',commands:['big']}
            ];
            let comments=[],importBuffer=[],demoPrepared=[],demoMode=false;
            let commentsEnabled=true;
            let clock={ms:0,stamp:performance.now(),playing:false,rate:1,ad:false,seeking:false};
            let lastReportedMs=null,lastSeekGeneration=null;

            // Always follow the video position. Repeated stale StateFlow
            // samples must not re-anchor the clock every 200ms.
            function interpolatedMs(now){
                if(!clock.playing||clock.ad||clock.seeking)return clock.ms;
                return clock.ms+Math.max(0,now-clock.stamp)*clock.rate;
            }
            function update(ms,playing,rate,ad,seeking,generation){
                if(!Number.isFinite(ms))return;
                const now=performance.now();
                ms=Math.max(0,ms);
                const running=!!playing&&!ad&&!seeking;
                const nextRate=Number.isFinite(rate)&&rate>0?rate:1;
                const predicted=interpolatedMs(now);
                const fresh=lastReportedMs===null||ms!==lastReportedMs;
                const seekChanged=lastSeekGeneration!==null&&generation!==lastSeekGeneration;
                const seekEnded=clock.seeking&&!seeking;
                let next=predicted;
                if(lastReportedMs===null||seekChanged||seekEnded){
                    next=ms;
                }else if(fresh){
                    const drift=ms-predicted;
                    if(ms<lastReportedMs-900||Math.abs(drift)>2400){
                        next=ms;
                    }else if(running&&clock.playing){
                        next=predicted+Math.min(30,Math.max(0,drift)*0.12);
                    }else{
                        next=ms;
                    }
                }
                lastReportedMs=ms;
                lastSeekGeneration=generation;
                clock={ms:next,stamp:now,playing:running,rate:nextRate,ad:!!ad,seeking:!!seeking};
            }
            function setVisible(enabled){commentsEnabled=!!enabled;}
            function setDemoMode(enabled){demoMode=!!enabled;}

            // Precompute text metrics and non-overlapping lanes ONCE per
            // imported timeline, rather than measureText() on every frame.
            // Skip unplaceable comments instead of piling them on each other.
            function prepare(input){
                const result=[];
                for(const c of input){
                    if(!c||!Number.isFinite(Number(c.vposMs)))continue;
                    const body=String(c.body??'').slice(0,400);
                    if(!body)continue;
                    const commands=Array.isArray(c.commands)?c.commands.map(String):[];
                    const size=commands.includes('big')?64:commands.includes('small')?30:48;
                    const lines=body.split(String.fromCharCode(10)).slice(0,10);
                    ctx.font='bold '+size+'px sans-serif';
                    const width=Math.max(1,...lines.map(line=>ctx.measureText(line).width));
                    const height=size*1.16*lines.length;
                    let color='#fff';
                    for(const cmd of commands){
                        if(palette[cmd])color=palette[cmd];
                        else if(/^#[0-9a-fA-F]{6}$/.test(cmd))color=cmd;
                    }
                    result.push({vposMs:Number(c.vposMs),lines,size,width,height,color,
                        type:commands.includes('ue')?'top':commands.includes('shita')?'bottom':'scroll',
                        lane:-1,velocity:(W+width)/scrollMs});
                }
                result.sort((a,b)=>a.vposMs-b.vposMs);
                const scrollLanes=Array(14).fill(null);
                const topUntil=Array(4).fill(-Infinity),bottomUntil=Array(4).fill(-Infinity);
                for(const c of result){
                    if(c.type!=='scroll'){
                        const until=c.type==='top'?topUntil:bottomUntil;
                        for(let lane=0;lane<until.length;lane++){
                            if(c.vposMs>=until[lane]){
                                c.lane=lane;
                                until[lane]=c.vposMs+fixedMs+80;
                                break;
                            }
                        }
                        continue;
                    }
                    for(let lane=0;lane<scrollLanes.length;lane++){
                        const previous=scrollLanes[lane];
                        // Ensure the previous comment has cleared the right
                        // edge; also prevent a faster, wider newcomer catching
                        // up before the previous comment leaves on the left.
                        const earliest=!previous?-Infinity:previous.vposMs+Math.max(
                            (previous.width+50)/previous.velocity,
                            scrollMs-(W-50)/c.velocity
                        );
                        if(c.vposMs>=earliest){
                            c.lane=lane;
                            scrollLanes[lane]=c;
                            break;
                        }
                    }
                }
                return result.filter(c=>c.lane>=0);
            }
            demoPrepared=prepare(demo);
            function setThreads(input){
                const flattened=[];
                if(Array.isArray(input)){
                    for(const thread of input){
                        for(const c of (thread?.comments||[]))flattened.push(c);
                    }
                }
                comments=prepare(flattened);
                demoMode=false;
            }
            function beginImport(){importBuffer=[];comments=[];demoMode=false;}
            function appendComments(batch){
                if(Array.isArray(batch)&&importBuffer.length<30000){
                    for(const c of batch.slice(0,30000-importBuffer.length))importBuffer.push(c);
                }
            }
            function endImport(){
                comments=prepare(importBuffer);
                importBuffer=[];
            }
            function lowerBound(a,time){
                let l=0,r=a.length;
                while(l<r){
                    const m=(l+r)>>>1;
                    if(a[m].vposMs<time)l=m+1;
                    else r=m;
                }
                return l;
            }
            function paint(comment,age){
                const duration=comment.type==='scroll'?scrollMs:fixedMs;
                if(age<0||age>=duration)return false;
                const {size,lines,width,height,lane,type}=comment;
                const x=type==='scroll'?W-age*comment.velocity:(W-width)/2;
                const y=type==='top'?20+lane*190:
                    type==='bottom'?H-20-height-lane*190:35+lane*72;
                ctx.font='bold '+size+'px sans-serif';
                ctx.textBaseline='top';
                ctx.lineJoin='round';
                ctx.lineWidth=Math.max(3,size/10);
                ctx.strokeStyle='rgba(0,0,0,.9)';
                ctx.fillStyle=comment.color;
                for(let i=0;i<lines.length;i++){
                    const lineY=y+i*size*1.16;
                    ctx.strokeText(lines[i],x,lineY);
                    ctx.fillText(lines[i],x,lineY);
                }
                return true;
            }
            // JS requestAnimationFrame callback cadence is the measurement.
            // It may differ from the actual physical screen/compositor FPS.
            // Collect enough samples for a useful p95 without per-frame logging.
            const perfWindowMs=5000;
            let perfStart=null,prevFrameTime=null;
            let frameIntervals=[],drawCostSum=0,drawCostMax=0,drawnSum=0;
            function recordPerf(start,drawCost,drawn){
                if(perfStart===null){
                    perfStart=start;
                    prevFrameTime=start;
                    return;
                }
                const interval=start-prevFrameTime;
                prevFrameTime=start;
                if(interval>0){
                    frameIntervals.push(interval);
                    drawCostSum+=drawCost;
                    drawCostMax=Math.max(drawCostMax,drawCost);
                    drawnSum+=drawn;
                }
                const elapsed=start-perfStart;
                if(elapsed<perfWindowMs)return;
                const count=frameIntervals.length;
                if(count){
                    const sorted=frameIntervals.slice().sort((a,b)=>a-b);
                    const pct=q=>sorted[Math.min(count-1,Math.max(0,Math.ceil(count*q)-1))];
                    const over17=sorted.filter(v=>v>16.7).length;
                    const over25=sorted.filter(v=>v>25).length;
                    const over33=sorted.filter(v=>v>33.3).length;
                    console.log('MORPHE_NICO_PERF '+
                        'fps='+(1000*count/elapsed).toFixed(1)+
                        ' p50ms='+pct(0.50).toFixed(1)+
                        ' p95ms='+pct(0.95).toFixed(1)+
                        ' maxMs='+sorted[count-1].toFixed(1)+
                        ' gt16ms='+over17+'/'+count+
                        ' gt25ms='+over25+'/'+count+
                        ' gt33ms='+over33+'/'+count+
                        ' drawAvgMs='+(drawCostSum/count).toFixed(2)+
                        ' drawMaxMs='+drawCostMax.toFixed(2)+
                        ' commentsAvg='+(drawnSum/count).toFixed(1)+
                        ' playing='+clock.playing+
                        ' rate='+clock.rate+
                        ' ad='+clock.ad);
                }
                perfStart=start;
                frameIntervals=[];
                drawCostSum=0;
                drawCostMax=0;
                drawnSum=0;
            }

            function frame(){
                const frameStart=performance.now();
                let drawn=0;
                ctx.clearRect(0,0,W,H);
                if(commentsEnabled&&!clock.ad&&!clock.seeking){
                    const ms=interpolatedMs(frameStart);
                    const active=demoMode?demoPrepared:comments;
                    const timeline=demoMode?ms%16000:ms;
                    const from=lowerBound(active,timeline-scrollMs);
                    for(let i=from;i<active.length;i++){
                        const c=active[i],age=timeline-c.vposMs;
                        if(age<0)break;
                        if(paint(c,age))drawn++;
                        if(drawn>=150)break;
                    }
                    if(demoMode){
                        ctx.font='20px sans-serif';
                        ctx.fillStyle='rgba(255,255,255,.7)';
                        ctx.fillText('Morphe Nico Overlay — TEST MODE',18,H-34);
                    }
                }
                recordPerf(frameStart,performance.now()-frameStart,drawn);
                requestAnimationFrame(frame);
            }
            window.MorpheNico={update,setThreads,setVisible,setDemoMode,
                beginImport,appendComments,endImport};
            requestAnimationFrame(frame);
        })();
        </script></body></html>
        """;
}
