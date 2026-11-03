package org.example.tverpatches.extension;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict single-episode video-title matching inspired by NCOverlay/nco-utils.
 * Matching is deliberately separate from network operations so regression
 * tests can run with ordinary javac.
 */
final class NicoVideoTitleMatcher {
    private static final Pattern EPISODE=Pattern.compile(
        "(?i)(?:第\\s*[0-9一二三四五六七八九十]{1,4}\\s*(?:話|回|羽)"
        +"|[#＃]\\s*0*[0-9]{1,3}"
        +"|\\b(?:episode|ep)[ .:#\\-]*0*[0-9]{1,3}"
        +"|(?<![a-z0-9])0*[0-9]{1,3}\\s*話)");
    private static final Pattern MARATHON=Pattern.compile(
        "全話|一挙|イッキ見|一気見|総集編|まとめて放送|ダイジェスト");
    private static final Pattern VIDEO_ID=Pattern.compile("(?:sm|so|nm)[0-9]{1,12}");

    static final class Match {
        final int score;
        final boolean autoSelect;
        Match(int score,boolean autoSelect) {
            this.score=score;this.autoSelect=autoSelect;
        }
    }

    /** NCOverlay buildSearchQuery-style episode alternatives. */
    static List<String> queries(NcoSearchMatcher.Parsed wanted) {
        List<String> list=new ArrayList<>();
        if(wanted.episode==null||wanted.multipleEpisodes)return list;
        int n=wanted.episode;
        String title=wanted.baseTitle.replace("\"","");
        if(title.isEmpty())return list;
        String epis="("+n+"話 OR 第"+n+"話 OR #"+n+
            " OR #"+String.format(Locale.ROOT,"%02d",n)+
            " OR episode"+n+" OR ep"+n+")";
        list.add(title+" "+epis);
        String qualifier=(wanted.season!=null&&wanted.season>1
            ? " Season"+wanted.season:"");
        list.add(title+qualifier+" 第"+n+"話");
        if(!wanted.subtitle.isEmpty()) {
            String subtitle=NcoSearchMatcher.removeSymbols(wanted.subtitle);
            if(subtitle.length()>2)list.add(title+" \""+subtitle.replace("\"","")+"\"");
        }
        return list;
    }

    /**
     * Require an explicit episode marker. A video with only a franchise name
     * must never be silently selected (e.g. trailers and reaction compilations).
     */
    static Match match(NcoSearchMatcher.Parsed wanted,String videoTitle) {
        if(wanted.episode==null||wanted.multipleEpisodes||
            MARATHON.matcher(videoTitle).find())
            return new Match(0,false);
        String normal=NcoSearchMatcher.normalize(videoTitle);
        Matcher m=EPISODE.matcher(normal);
        Match best=new Match(0,false);
        while(m.find()) {
            // Extract the series before episode marker, and the text after it
            // for season / subtitle comparison, including surrounding quotes.
            String series=normal.substring(0,m.start()).trim();
            String episode=normal.substring(m.start()).trim();
            if(series.isEmpty())continue;
            NcoSearchMatcher.Parsed actual=NcoSearchMatcher.parse(series,episode);
            if(actual.multipleEpisodes||actual.episode==null
                ||!wanted.episode.equals(actual.episode))continue;
            int titleMatch=NcoSearchMatcher.titleMatch(wanted,series);
            if(titleMatch==0)continue;
            if(wanted.season!=null&&actual.season!=null
                &&!wanted.season.equals(actual.season))continue;
            boolean sameSeason=(wanted.season==null&&actual.season==null)
                ||(wanted.season!=null&&wanted.season.equals(actual.season));
            boolean hasBothSubtitles=
                NcoSearchMatcher.normalizeAll(wanted.subtitle).length()>=3
                &&NcoSearchMatcher.normalizeAll(actual.subtitle).length()>=3;
            boolean subtitleMatched=hasBothSubtitles&&
                NcoSearchMatcher.subtitleMatches(wanted.subtitle,actual.subtitle);
            if(hasBothSubtitles&&!subtitleMatched)continue;
            int score=titleMatch==2?95:75;
            if(sameSeason)score+=10;
            if(subtitleMatched)score+=15;
            // Auto-select only the identical single episode + identical
            // season evidence + sufficiently similar franchise title.
            boolean auto=titleMatch==2&&sameSeason&&score>=105;
            if(score>best.score)best=new Match(score,auto);
        }
        return best;
    }

    static boolean videoId(String id) {
        return id!=null&&VIDEO_ID.matcher(id).matches();
    }

    private NicoVideoTitleMatcher(){}
}
