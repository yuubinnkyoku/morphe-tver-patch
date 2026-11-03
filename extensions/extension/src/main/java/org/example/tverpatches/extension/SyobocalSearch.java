package org.example.tverpatches.extension;

import android.util.Log;
import android.util.Xml;

import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * NCOverlay-aligned historic Jikkyo search via Syoboi Calendar.
 *
 * NCOverlay's nco-utils syobocal search:
 *   parse(TVer title + episode) -> titleStripped ->
 *   TitleSearch(removeSymbols(titleStripped).toUpperCase(), Limit=15) ->
 *   similar series (>= .85), or partial matches with known episode ->
 *   SubTitles (when episode number is unknown) ->
 *   db.php ProgLookup(TID, Count, ChID, JOIN=SubTitles).
 *
 * This native Java adaptation preserves the same search order and program
 * lookup endpoint while keeping strict cross-season and multi-episode safety.
 * Upstream: https://github.com/Midra429/nco-utils (MIT).
 */
final class SyobocalSearch {
    private static final String TAG = "MorpheNicoOverlay";
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter DATE_SECONDS =
        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_MINUTES =
        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");
    private static final Map<String,String> CHANNELS = new HashMap<>();
    static {
        String[][] pairs = {
            {"1","jk1"},{"2","jk2"},{"4","jk4"},{"6","jk5"},
            {"5","jk6"},{"7","jk7"},{"3","jk8"},{"19","jk9"},
            {"14","jk10"},{"8","jk11"},{"13","jk12"},
            {"58","jk13"},{"66","jk14"},{"9","jk101"},
            {"179","jk103"},{"71","jk141"},{"18","jk151"},
            {"16","jk161"},{"15","jk171"},{"17","jk181"},
            {"204","jk191"},{"97","jk192"},{"76","jk193"},
            {"285","jk200"},{"286","jk201"},{"128","jk211"},
            {"129","jk222"},{"197","jk236"},{"251","jk252"},
            {"271","jk260"},{"273","jk263"},{"272","jk265"},
            {"20","jk333"}
        };
        for(String[] pair:pairs)CHANNELS.put(pair[0],pair[1]);
    }

    private static String networkText(String address) {
        HttpURLConnection c=null;
        try {
            c=(HttpURLConnection)new URL(address).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(8000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Accept","application/json, application/xml, text/xml");
            c.setRequestProperty("X-User-Agent","MorpheTVerNicoOverlay/1.2");
            int status=c.getResponseCode();
            if(status!=200) {
                Log.w(TAG,"Syoboi HTTP "+status+" from "+address);
                return null;
            }
            StringBuilder text=new StringBuilder();
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(
                c.getInputStream(),StandardCharsets.UTF_8))) {
                char[] chars=new char[4096];
                int n;
                while((n=reader.read(chars))!=-1) {
                    if(text.length()+n>2_000_000) {
                        Log.w(TAG,"Syoboi response too large");
                        return null;
                    }
                    text.append(chars,0,n);
                }
            }
            return text.toString();
        }catch(Exception ex) {
            Log.w(TAG,"Syoboi network request failed: "+address,ex);
            return null;
        }finally {
            if(c!=null)c.disconnect();
        }
    }

    private static JSONObject json(String query) {
        try {
            String text=networkText("https://cal.syoboi.jp/json.php?"+query);
            return text==null?null:new JSONObject(text);
        }catch(Exception ex) {
            Log.w(TAG,"Invalid Syoboi JSON",ex);
            return null;
        }
    }

    private static long epoch(String raw) {
        try {
            String s=raw.trim().replace('T',' ');
            if(s.matches("[0-9]{10,13}")) {
                long val=Long.parseLong(s);
                return s.length()==13?val/1000:val;
            }
            if(s.length()>=19)
                return LocalDateTime.parse(s.substring(0,19),DATE_SECONDS)
                    .atZone(JST).toEpochSecond();
            if(s.length()>=16)
                return LocalDateTime.parse(s.substring(0,16),DATE_MINUTES)
                    .atZone(JST).toEpochSecond();
        }catch(Exception ignored) {}
        return 0;
    }

    private static final class Title {
        final String tid,name;
        final int match;
        Title(String tid,String name,int match) {
            this.tid=tid;this.name=name;this.match=match;
        }
    }

    private static List<Title> titles(NcoSearchMatcher.Parsed parsed) {
        LinkedHashMap<String,Title> matches=new LinkedHashMap<>();
        for(String term:NcoSearchMatcher.searchTerms(parsed)) {
            JSONObject result=json("Req=TitleSearch&Limit=15&Search="
                +URLEncoder.encode(term,StandardCharsets.UTF_8));
            JSONObject titles=result==null?null:result.optJSONObject("Titles");
            if(titles==null)continue;
            java.util.Iterator<String> ids=titles.keys();
            while(ids.hasNext()) {
                String key=ids.next();
                JSONObject item=titles.optJSONObject(key);
                if(item==null)continue;
                String tid=item.optString("TID",key);
                String title=item.optString("Title","");
                if(!tid.matches("[0-9]{1,8}"))continue;
                int quality=NcoSearchMatcher.titleMatch(parsed,title);
                if(quality==0)continue;
                Title previous=matches.get(tid);
                if(previous==null||quality>previous.match)
                    matches.put(tid,new Title(tid,title,quality));
            }
            Log.i(TAG,"NCOverlay-style TitleSearch '"+term+"' found "
                +matches.size()+" matched titles");
            if(!matches.isEmpty())break;
        }
        List<Title> list=new ArrayList<>(matches.values());
        list.sort(Comparator.comparingInt((Title v)->v.match).reversed());
        return list;
    }

    private static final class EpisodeMatch {
        final int count;
        final String subtitle;
        EpisodeMatch(int count,String subtitle){this.count=count;this.subtitle=subtitle;}
    }

    /** NCOverlay derives an unknown episode number from SubTitles. */
    private static EpisodeMatch matchSubtitle(String tid,String subtitle) {
        if(NcoSearchMatcher.normalizeAll(subtitle).length()<3)return null;
        JSONObject response=json("Req=SubTitles&TID="+tid);
        JSONObject outer=response==null?null:response.optJSONObject("SubTitles");
        JSONObject entries=outer==null?null:outer.optJSONObject(tid);
        if(entries==null)return null;
        EpisodeMatch found=null;
        java.util.Iterator<String> numbers=entries.keys();
        while(numbers.hasNext()) {
            String n=numbers.next();
            String text=entries.optString(n,"");
            if(!NcoSearchMatcher.subtitleMatches(subtitle,text))continue;
            try {
                int count=Integer.parseInt(n);
                if(count<0||count>999)return null;
                if(found!=null && found.count!=count)return null;
                found=new EpisodeMatch(count,text);
            }catch(NumberFormatException ignored){}
        }
        return found;
    }

    /** All subtitles are optional for the exact-series episode-number path. */
    private static String subtitleFor(String tid,int count) {
        JSONObject response=json("Req=SubTitles&TID="+tid);
        JSONObject outer=response==null?null:response.optJSONObject("SubTitles");
        JSONObject byCount=outer==null?null:outer.optJSONObject(tid);
        return byCount==null?"":byCount.optString(Integer.toString(count),"");
    }

    /**
     * This is NCOverlay's ProgLookup endpoint, not ProgramByCount.
     * JOIN=SubTitles returns STSubTitle for reliable cross-series filtering.
     */
    private static List<JSONObject> programmeLookup(String tid,int count) {
        List<JSONObject> records=new ArrayList<>();
        String xmlText=networkText("https://cal.syoboi.jp/db.php?Command=ProgLookup&TID="
            +tid+"&Count="+count+"&JOIN=SubTitles");
        if(xmlText!=null) {
            try {
                XmlPullParser parser=Xml.newPullParser();
                // Reject DTDs and avoid external entities.
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,false);
                parser.setInput(new StringReader(xmlText));
                JSONObject item=null;
                int event=parser.getEventType();
                while(event!=XmlPullParser.END_DOCUMENT) {
                    if(event==XmlPullParser.DOCDECL)
                        throw new IllegalArgumentException("Unexpected Syoboi DTD");
                    if(event==XmlPullParser.START_TAG) {
                        String name=parser.getName();
                        if("ProgItem".equals(name))item=new JSONObject();
                        else if(item!=null&&("TID".equals(name)||"ChID".equals(name)
                            ||"ChName".equals(name)||"StTime".equals(name)
                            ||"EdTime".equals(name)||"STSubTitle".equals(name)
                            ||"SubTitle".equals(name)||"Count".equals(name)
                            ||"Flag".equals(name))) {
                            item.put(name,parser.nextText());
                        }
                    }else if(event==XmlPullParser.END_TAG &&
                        "ProgItem".equals(parser.getName())&&item!=null) {
                        records.add(item);
                        item=null;
                        if(records.size()>=256)break;
                    }
                    event=parser.next();
                }
            }catch(Exception ex) {
                records.clear();
                Log.w(TAG,"Syoboi ProgLookup XML parse error",ex);
            }
        }
        if(!records.isEmpty())return records;
        // Fallback to the older JSON API when db.php is blocked or unavailable.
        JSONObject json=json("Req=ProgramByCount&TID="+tid+"&Count="+count);
        JSONObject byId=json==null?null:json.optJSONObject("Programs");
        if(byId!=null) {
            java.util.Iterator<String> keys=byId.keys();
            while(keys.hasNext()&&records.size()<256) {
                JSONObject entry=byId.optJSONObject(keys.next());
                if(entry!=null)records.add(entry);
            }
        }
        return records;
    }

    static TverEpgSearch.Result search(String title,String episode) {
        NcoSearchMatcher.Parsed parsed=NcoSearchMatcher.parse(title,episode);
        if(parsed.multipleEpisodes||parsed.searchWord.length()<3)
            return new TverEpgSearch.Result(Collections.emptyList(),0);
        if(parsed.episode==null&&NcoSearchMatcher.normalizeAll(parsed.subtitle).length()<3)
            return new TverEpgSearch.Result(Collections.emptyList(),0);
        List<Title> titles=titles(parsed);
        if(titles.isEmpty())return new TverEpgSearch.Result(Collections.emptyList(),1);
        List<TverEpgSearch.Candidate> candidates=new ArrayList<>();
        long archiveCutoff=Instant.now().getEpochSecond()-600;

        int exactCount=0;
        for(Title t:titles)if(t.match==2)exactCount++;
        boolean ambiguous=exactCount>1;
        for(int i=0;i<titles.size()&&i<8;i++) {
            Title t=titles.get(i);
            int episodeNo=parsed.episode==null?-1:parsed.episode;
            String subtitle="";
            if(episodeNo<0) {
                EpisodeMatch found=matchSubtitle(t.tid,parsed.subtitle);
                if(found==null)continue;
                episodeNo=found.count;
                subtitle=found.subtitle;
            }else if(!parsed.subtitle.isEmpty()) {
                // NCOverlay checks subtitles to resolve multiple series. For
                // exact, unambiguous titles it still trusts the episode count.
                subtitle=subtitleFor(t.tid,episodeNo);
                boolean matches=subtitle.isEmpty()||
                    NcoSearchMatcher.subtitleMatches(parsed.subtitle,subtitle);
                if(ambiguous && !matches)continue;
            }
            if(episodeNo<0||episodeNo>999)continue;
            List<JSONObject> shows=programmeLookup(t.tid,episodeNo);
            for(JSONObject show:shows) {
                String channel=CHANNELS.get(show.optString("ChID",""));
                if(channel==null)continue;
                if(!t.tid.equals(show.optString("TID",t.tid)))continue;
                int actualCount=show.optInt("Count",-1);
                if(actualCount>=0&&actualCount!=episodeNo)continue;
                long start=epoch(show.optString("StTime",""));
                long end=epoch(show.optString("EdTime",""));
                if(start<=0||end<=start||end-start>21600||end>archiveCutoff)
                    continue;
                String actualSubtitle=show.optString("STSubTitle",
                    show.optString("SubTitle",subtitle));
                String finalSubtitle=actualSubtitle.isEmpty()?subtitle:actualSubtitle;
                boolean verifiedSubtitle=parsed.subtitle.length()>=3&&
                    NcoSearchMatcher.subtitleMatches(parsed.subtitle,finalSubtitle);
                if(ambiguous&&!verifiedSubtitle)continue;
                // Only well-supported matches may be loaded automatically.
                int score=t.match==2&&(!ambiguous||verifiedSubtitle)?110:
                    verifiedSubtitle?103:80;
                String label="第"+episodeNo+"話";
                if(!finalSubtitle.isEmpty())label+=" "+finalSubtitle;
                candidates.add(new TverEpgSearch.Candidate(t.name,label,
                    show.optString("ChName",channel),channel,start,end,score));
            }
        }
        candidates.sort(Comparator.<TverEpgSearch.Candidate>
            comparingInt(c->c.score).reversed()
            .thenComparing(Comparator.comparingLong(
                (TverEpgSearch.Candidate c)->c.start).reversed()));
        List<TverEpgSearch.Candidate> unique=
            TverEpgSearch.merge(candidates,Collections.emptyList());
        Log.i(TAG,"NCOverlay-style Syoboi query: "+parsed.searchWord+
            ", season="+parsed.season+", episode="+parsed.episode+
            ", titles="+titles.size()+", broadcasts="+unique.size());
        return new TverEpgSearch.Result(unique,1);
    }

    private SyobocalSearch(){}
}
