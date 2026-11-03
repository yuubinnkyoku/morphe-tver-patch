package org.example.tverpatches.extension;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Search NicoNico video title/episode matches independently of EPG discovery. */
final class NicoVideoSearchController {
    static final String AUTO_ENABLED="nico_video_auto_search";
    private static final String PREFS="morphe.tver.settings";
    private static final String TAG="MorpheNicoOverlay";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());

    private static int generation;
    private static String episodeKey="";
    private static String searchStatus="not-searched";
    private static List<NicoVideoAutoSearch.Candidate> candidates=new ArrayList<>();

    static void reset() {
        generation++;
        episodeKey="";
        candidates=new ArrayList<>();
        searchStatus="not-searched";
    }

    static void onEpisodeIdentified(Context context,String series,String episode) {
        reset();
        episodeKey=series+"\u0000"+episode;
        if(context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getBoolean(AUTO_ENABLED,true))search(context,true);
    }

    static int candidateCount(){return candidates.size();}

    static void search(Context context,boolean automatic) {
        String series=NicoAutoSourceController.seriesName();
        String episode=NicoAutoSourceController.episodeName();
        if(series.isEmpty()||episode.isEmpty()) {
            if(!automatic)
                Toast.makeText(context,"動画を開いてから検索してください",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        final String key=series+"\u0000"+episode;
        final int run=++generation;
        searchStatus="searching";
        new Thread(()->{
            NicoVideoAutoSearch.Result found;
            try {
                found=NicoVideoAutoSearch.search(series,episode);
            }catch(Exception error) {
                Log.w(TAG,"NicoNico video search failed",error);
                found=new NicoVideoAutoSearch.Result(Collections.emptyList(),false);
            }
            final NicoVideoAutoSearch.Result finished=found;
            MAIN.post(()->{
                if(run!=generation||!key.equals(episodeKey))return;
                candidates=new ArrayList<>(finished.candidates);
                searchStatus=!finished.available?"error":
                    candidates.isEmpty()?"empty":"found";
                if(automatic) {
                    List<String> ids=NicoVideoAutoSearch.automaticIds(candidates);
                    boolean changed=false;
                    for(String id:ids) {
                        if(NicoAutoSourceController.addNicoVideo(context,id,false))
                            changed=true;
                    }
                    if(changed)
                        NicoAutoSourceController.reloadAllSources(context,false);
                    if(changed)
                        Log.i(TAG,"Automatically selected "+ids+" NicoNico videos");
                }else {
                    showResults(context);
                }
            });
        },"Morphe-NicoSearch").start();
    }

    static void showPicker(Activity activity) {
        if(activity==null||activity.isFinishing()||activity.isDestroyed())return;
        if("searching".equals(searchStatus)) {
            Toast.makeText(activity,"ニコニコ動画を検索しています",
                Toast.LENGTH_SHORT).show();
            return;
        }
        if("not-searched".equals(searchStatus)) {
            search(activity,false);
            Toast.makeText(activity,"ニコニコ動画の検索を開始しました",
                Toast.LENGTH_SHORT).show();
            return;
        }
        showResults(activity);
    }

    private static void showResults(Context context) {
        if(!(context instanceof Activity))return;
        Activity activity=(Activity)context;
        if(activity.isFinishing()||activity.isDestroyed())return;
        String key=episodeKey;
        List<NicoVideoAutoSearch.Candidate> snapshot=new ArrayList<>(candidates);
        String[] options=new String[snapshot.size()+1];
        options[0]="動画ID・URLを手入力";
        for(int i=0;i<snapshot.size();i++)options[i+1]=snapshot.get(i).label();
        new AlertDialog.Builder(activity)
            .setTitle("ニコニコ動画の検索結果（"+snapshot.size()+"件）")
            .setItems(options,(dialog,which)->{
                if(!key.equals(episodeKey))return;
                if(which==0)
                    NicoAutoSourceController.showNicoVideoEditor(activity);
                else {
                    String id=snapshot.get(which-1).id;
                    if(NicoAutoSourceController.addNicoVideo(activity,id,false)) {
                        NicoAutoSourceController.reloadAllSources(activity,true);
                    }else {
                        Toast.makeText(activity,
                            "登録済み、または追加上限に達しています",
                            Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setPositiveButton("再検索",(d,w)->search(activity,false))
            .setNegativeButton("閉じる",null)
            .show();
    }

    private NicoVideoSearchController(){}
}
