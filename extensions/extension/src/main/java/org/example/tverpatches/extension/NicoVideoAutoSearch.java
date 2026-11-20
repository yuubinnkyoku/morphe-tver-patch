package org.example.tverpatches.extension;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NCOverlay/nco-utils-inspired NicoNico Snapshot Search v2.
 *
 * Official, d-anime and SZBH candidates are classified using title, season,
 * and episode, with ambiguous results available only through manual choice.
 * Without the TVer player's known duration, official/d-anime auto-selection
 * requires unique matches in that category (max one per category).
 */
final class NicoVideoAutoSearch {
    private static final String TAG="MorpheNicoOverlay";
    private static final int MAX_RESPONSE_CHARS=1_200_000;
    static final String CATEGORY_OFFICIAL="公式";
    static final String CATEGORY_DANIME="dアニメ";
    static final String CATEGORY_SZBH="コメント専用";
    private static final long DANIME_CHANNEL_ID=2632720L;

    static final class Candidate {
        final String id,title,category;
        final long seconds;
        final int count,score;
        final boolean confidentlyMatched;
        Candidate(String id,String title,String category,
                  long seconds,int count,int score,boolean confident) {
            this.id=id;this.title=title;this.category=category;
            this.seconds=seconds;this.count=count;this.score=score;
            this.confidentlyMatched=confident;
        }
        String label() {
            return "["+category+"] "+title+"\n"+id+"・"+
                (seconds>0?seconds/60+"分・":"")+count+"コメント";
        }
    }
    static final class Result {
        final List<Candidate> candidates;
        final boolean available;
        Result(List<Candidate> candidates,boolean available) {
            this.candidates=candidates;this.available=available;
        }
    }

    private static JSONArray request(String query) {
        HttpURLConnection c=null;
        try {
            JSONObject filter=new JSONObject()
                .put("type","and")
                .put("filters",new JSONArray()
                    .put(new JSONObject().put("type","equal")
                        .put("field","contentType").put("value","long"))
                    .put(new JSONObject().put("type","or")
                        .put("filters",new JSONArray()
                            .put(new JSONObject().put("type","equal")
                                .put("field","genre.keyword").put("value","アニメ"))
                            .put(new JSONObject().put("type","equal")
                                .put("field","tagsExact").put("value","コメント専用動画"))
                            .put(new JSONObject().put("type","equal")
                                .put("field","tagsExact").put("value","SZBH方式")))));
            String u="https://snapshot.search.nicovideo.jp/api/v2/snapshot/video/contents/search"
                +"?q="+URLEncoder.encode(query,StandardCharsets.UTF_8)
                +"&targets=title"
                +"&fields=contentId,title,channelId,userId,lengthSeconds,commentCounter,tags"
                +"&_sort=-startTime&_limit=50&_context=MorpheTver"
                +"&jsonFilter="+URLEncoder.encode(filter.toString(),StandardCharsets.UTF_8);
            c=(HttpURLConnection)new URL(u).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(6500);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Accept","application/json");
            if(c.getResponseCode()!=200) {
                Log.w(TAG,"NicoNico search HTTP "+c.getResponseCode());
                return null;
            }
            StringBuilder out=new StringBuilder();
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(
                c.getInputStream(),StandardCharsets.UTF_8))) {
                char[] buf=new char[4096];
                int n;
                while((n=reader.read(buf))!=-1) {
                    if(out.length()+n>MAX_RESPONSE_CHARS)return null;
                    out.append(buf,0,n);
                }
            }
            JSONObject root=new JSONObject(out.toString());
            JSONObject meta=root.optJSONObject("meta");
            if(meta==null||meta.optInt("status")!=200)return null;
            return root.optJSONArray("data");
        }catch(Exception ex) {
            Log.w(TAG,"NicoNico Snapshot Search failed",ex);
            return null;
        }finally {
            if(c!=null)c.disconnect();
        }
    }

    private static Candidate evaluate(NcoSearchMatcher.Parsed wanted,JSONObject v) {
        String id=v.optString("contentId","");
        if(!NicoVideoTitleMatcher.videoId(id))return null;
        String title=v.optString("title","");
        NicoVideoTitleMatcher.Match match=NicoVideoTitleMatcher.match(wanted,title);
        if(match.score<85)return null;
        long channel=v.optLong("channelId",0);
        long user=v.optLong("userId",0);
        String category;
        if(channel==DANIME_CHANNEL_ID)category=CATEGORY_DANIME;
        else if(channel>0)category=CATEGORY_OFFICIAL;
        else if(user>0) {
            String tags=v.optString("tags","");
            if(!tags.contains("コメント専用動画")&&!tags.contains("SZBH方式"))
                return null;
            category=CATEGORY_SZBH;
        }else return null;
        int comments=v.optInt("commentCounter",0);
        if(comments<=0)return null;
        long seconds=v.optLong("lengthSeconds",0);
        if(seconds<=0||seconds>6*60*60)return null;
        return new Candidate(id,title,category,seconds,comments,
            match.score,match.autoSelect && !CATEGORY_SZBH.equals(category));
    }

    static Result search(String series,String episode) {
        NcoSearchMatcher.Parsed parsed=NcoSearchMatcher.parse(series,episode);
        List<String> queries=NicoVideoTitleMatcher.queries(parsed);
        if(queries.isEmpty())return new Result(Collections.emptyList(),true);
        Map<String,Candidate> found=new LinkedHashMap<>();
        boolean success=false;
        for(String query:queries) {
            JSONArray data=request(query);
            if(data==null)continue;
            success=true;
            for(int i=0;i<data.length()&&i<50;i++) {
                JSONObject item=data.optJSONObject(i);
                if(item==null)continue;
                Candidate video=evaluate(parsed,item);
                if(video==null)continue;
                Candidate older=found.get(video.id);
                if(older==null||video.score>older.score)found.put(video.id,video);
            }
            if(found.size()>=12)break;
        }
        List<Candidate> candidates=new ArrayList<>(found.values());
        candidates.sort(Comparator.comparingInt((Candidate c)->c.score)
            .reversed().thenComparing(Comparator.comparingInt(
                (Candidate c)->c.count).reversed()));
        if(candidates.size()>12)candidates=new ArrayList<>(candidates.subList(0,12));
        Log.i(TAG,"NicoNico automatic search: "+series+" / "+episode
            +" results="+candidates.size()+" available="+success);
        return new Result(candidates,success);
    }

    /** NCOverlay caps matched official/d-anime videos at three per category;
     * for automatic TVer importing, require one unambiguous exact match.
     */
    static List<String> automaticIds(List<Candidate> candidates) {
        List<String> chosen=new ArrayList<>();
        for(String type:new String[]{CATEGORY_OFFICIAL,CATEGORY_DANIME}) {
            Candidate only=null;
            int count=0;
            for(Candidate c:candidates) {
                if(!type.equals(c.category)||!c.confidentlyMatched)continue;
                count++;
                only=c;
            }
            if(count==1&&only!=null)chosen.add(only.id);
        }
        return chosen;
    }
    private NicoVideoAutoSearch(){}
}
