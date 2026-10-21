package org.example.tverpatches.extension;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
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
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<ViewGroup> hostRef = new WeakReference<>(null);
    private static WeakReference<PassThroughWebView> webRef = new WeakReference<>(null);
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
                if (host.getChildAt(host.getChildCount() - 1) != web) web.bringToFront();
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
                web.bringToFront();
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
            PassThroughWebView web = webRef.get();
            if (web != null && web.getParent() == host) web.bringToFront();
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
                    Log.i(TAG, text.substring(PERF_PREFIX.length()));
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
                    float displayHz = display == null ? 0f : display.getRefreshRate();
                    Log.i(TAG, String.format(Locale.ROOT,
                        "Renderer ready; reportedDisplayHz=%.1f (WebView rAF FPS measured separately)",
                        displayHz));

                    sendClock(web);
                }
            }
        });
        return web;
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
                if(!clock.ad&&!clock.seeking){
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
            window.MorpheNico={update,setThreads};
            requestAnimationFrame(frame);
        })();
        </script></body></html>
        """;
}
