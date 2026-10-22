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
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
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
    private static final String SAVED_FPS_REPORT = "nico_fps_last_report";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<ViewGroup> hostRef = new WeakReference<>(null);
    private static WeakReference<PassThroughWebView> webRef = new WeakReference<>(null);
    private static WeakReference<TextView> badgeRef = new WeakReference<>(null);
    private static final Deque<String> perfHistory = new ArrayDeque<>();
    private static final int PERF_HISTORY_LIMIT = 12;
    private static String latestPerf;
    private static float displayHz;
    private static float requestedOverlayHz;
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
            if (host == null || web == null || web.getParent() != host) return;
            if (host.isAttachedToWindow()) {
                // TVer's setSecure() may remove and re-add its SurfaceView.
                // Keep the WebView below our tappable FPS badge, not above it.
                bringOverlaysToFront(host);
                sendClock(web);
            }
            ticking = true;
            MAIN.postDelayed(this, 200);
        }
    };

    private NicoOverlayBridge() {}

    public static void bind(ViewGroup host, Object stateFlow) {
        if (host == null || stateFlow == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            host.post(() -> bind(host, stateFlow));
            return;
        }
        ViewGroup previous = hostRef.get();
        PassThroughWebView existing = webRef.get();
        if (host != previous || existing == null || existing.getParent() != host) {
            if (previous != null) release();
            hostRef = new WeakReference<>(host);
            ready = false;
            loggedError = false;
            try {
                PassThroughWebView web = makeWeb(host);
                webRef = new WeakReference<>(web);
                host.addView(web, new FrameLayout.LayoutParams(-1, -1));
                TextView badge = makeFpsBadge(host);
                badgeRef = new WeakReference<>(badge);
                FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(-2, -2,
                    Gravity.TOP | Gravity.START);
                badgeParams.leftMargin = dp(host, 12);
                badgeParams.topMargin = dp(host, 52);
                host.addView(badge, badgeParams);
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
        if (hostRef.get() == host) release();
    }

    private static void release() {
        MAIN.removeCallbacks(TICK);
        ticking = false;
        ready = false;
        flow = null;
        getFlowValue = null;
        stateClass = null;
        position = phase = rate = ad = seeking = generation = null;
        // Keep FPS history across playback -> settings navigation.
        requestedOverlayHz = 0f;
        TextView badge = badgeRef.get();
        if (badge != null) {
            badge.setOnClickListener(null);
            if (badge.getParent() instanceof ViewGroup) {
                ((ViewGroup) badge.getParent()).removeView(badge);
            }
        }
        badgeRef = new WeakReference<>(null);
        PassThroughWebView web = webRef.get();
        if (web != null) {
            if (web.getParent() instanceof ViewGroup) {
                ((ViewGroup) web.getParent()).removeView(web);
            }
            web.stopLoading();
            web.destroy();
        }
        webRef = new WeakReference<>(null);
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
                }
            }
        });
        return web;
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

    private static void bringOverlaysToFront(ViewGroup host) {
        PassThroughWebView web = webRef.get();
        TextView badge = badgeRef.get();
        boolean hasBadge = badge != null && badge.getParent() == host;
        if (web != null && web.getParent() == host) {
            int expected = host.getChildCount() - (hasBadge ? 2 : 1);
            if (host.indexOfChild(web) != expected) web.bringToFront();
        }
        if (hasBadge && host.indexOfChild(badge) != host.getChildCount() - 1) {
            badge.bringToFront();
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
        String sample = stats + String.format(Locale.ROOT,
            " request120=%s requestedHz=%.1f displayHzNow=%.1f displayModeHz=%.1f",
            requestedOverlayHz == 120f, requestedOverlayHz, refreshNow, modeNow);
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
        if (web != null && web.getParent() == host) {
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
        report.append("Windows: five seconds each; oldest first; maximum 12.\n\n");
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
        panel.addView(makeSettingSwitch(activity, "動画上にFPSを表示する",
            FPS_VISIBLE, true));
        panel.addView(makeSettingSwitch(activity, "コメント描画を120fps優先（実験・電池消費増の可能性）",
            PREFER_120FPS, false));

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
            let threads=null;
            let commentsEnabled=true;
            let clock={ms:0,stamp:performance.now(),playing:false,rate:1,ad:false,seeking:false};
            let lastReportedMs=null, lastSeekGeneration=null;

            // PlaybackState positionMs can update much less often than our 200ms
            // sampling interval. Re-anchoring to the same stale value causes
            // comments to bounce right, then jump left at the next real update.
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
                    // First sample or an explicit discontinuity: snap to TVer.
                    next=ms;
                }else if(fresh){
                    const drift=ms-predicted;
                    if(ms<lastReportedMs-900||Math.abs(drift)>2400){
                        // Handle a seek even if the seek flag was not observed.
                        next=ms;
                    }else if(running&&clock.playing){
                        // Preserve forward motion. Small positive drift is
                        // corrected gradually; stale/rounded samples never
                        // push scrolling comments backwards.
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
            function setThreads(input){
                if(!Array.isArray(input)){threads=null;return;}
                const result=[];
                for(const thread of input)for(const comment of (thread.comments||[])){
                    if(Number.isFinite(Number(comment.vposMs))){
                        result.push({vposMs:Number(comment.vposMs),body:String(comment.body||''),
                            commands:Array.isArray(comment.commands)?comment.commands:[]});
                    }
                }
                threads=result.sort((a,b)=>a.vposMs-b.vposMs);
            }
            function paint(comment,age,index){
                const commands=comment.commands||[];
                const top=commands.includes('ue'),bottom=commands.includes('shita');
                const duration=top||bottom?fixedMs:scrollMs;
                if(age<0||age>=duration)return;
                const size=commands.includes('big')?64:commands.includes('small')?30:48;
                let color='#fff';
                for(const cmd of commands){
                    if(palette[cmd])color=palette[cmd];
                    else if(/^#[0-9a-fA-F]{6}$/.test(cmd))color=cmd;
                }
                ctx.font='bold '+size+'px sans-serif';
                ctx.textBaseline='top';
                ctx.lineJoin='round';
                ctx.lineWidth=Math.max(3,size/10);
                const lines=String(comment.body).split(String.fromCharCode(10));
                const width=Math.max(1,...lines.map(s=>ctx.measureText(s).width));
                const height=size*1.16*lines.length;
                const x=(top||bottom)?(W-width)/2:W-age/duration*(W+width);
                const y=top?20+index%3*height:bottom?H-20-height-index%3*height:35+index%14*70;
                ctx.strokeStyle='rgba(0,0,0,.9)';
                ctx.fillStyle=color;
                for(let i=0;i<lines.length;i++){
                    ctx.strokeText(lines[i],x,y+i*size*1.16);
                    ctx.fillText(lines[i],x,y+i*size*1.16);
                }
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
                    const ms=clock.ms+(clock.playing?Math.max(0,performance.now()-clock.stamp)*clock.rate:0);
                    const comments=threads===null?demo:threads;
                    const timeline=threads===null?ms%16000:ms;
                    for(let i=0;i<comments.length;i++){
                        const age=timeline-comments[i].vposMs;
                        if(age>=0&&age<scrollMs){
                            paint(comments[i],age,i);
                            if(++drawn>=150)break;
                        }
                    }
                    if(threads===null){
                        ctx.font='20px sans-serif';
                        ctx.fillStyle='rgba(255,255,255,.7)';
                        ctx.fillText('Morphe Nico Overlay — TEST MODE',18,H-34);
                    }
                }
                recordPerf(frameStart,performance.now()-frameStart,drawn);
                requestAnimationFrame(frame);
            }
            window.MorpheNico={update,setThreads,setVisible};
            requestAnimationFrame(frame);
        })();
        </script></body></html>
        """;
}
