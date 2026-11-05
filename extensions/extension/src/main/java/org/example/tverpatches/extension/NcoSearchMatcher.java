package org.example.tverpatches.extension;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Focused Java port of NCOverlay / @midra/nco-utils search preparation.
 *
 * Upstream references (MIT):
 * src/parse/{index,libs/extract/season,libs/extract/episode}.ts
 * src/search/services/syobocal.ts
 *
 * Search: parsed.titleStripped -> removeSymbols -> upper-case.
 * Matching: normalized title similarity >= 0.85, episode/season evidence,
 * then subtitle verification when needed.
 *
 * This is a targeted Android adaptation, not a full port of every nco-utils
 * anime-title parsing rule.
 */
final class NcoSearchMatcher {
    private static final Pattern SEASON = Pattern.compile(
        "(?i)\\s*(?:season\\s*([0-9]{1,2})|シーズン\\s*([0-9]{1,2})"
        + "|第\\s*([0-9]{1,2})\\s*(?:期|シーズン|シリーズ)"
        + "|([0-9]{1,2})(?:st|nd|rd|th)\\s+season"
        + "|\\(([0-9]{1,2})\\))\\s*$");
    private static final Pattern EP_NUMERIC = Pattern.compile(
        "(?i)(?:第\\s*([0-9]{1,3})\\s*(?:話|回|羽|章|幕)"
        + "|(?:#|＃)\\s*([0-9]{1,3})"
        + "|\\b(?:episode|ep|chapter)\\s*[.#:\\-]?\\s*([0-9]{1,3})"
        + "|(?<![a-z0-9])([0-9]{1,3})\\s*話)");
    private static final Pattern EP_KANJI = Pattern.compile(
        "第\\s*([一二三四五六七八九十]+)\\s*(?:話|回|羽)");
    private static final Pattern MULTIPLE_EP = Pattern.compile(
        "(?:第\\s*\\d+\\s*話|#\\s*\\d+)\\s*[〜~\\-/]\\s*(?:第\\s*)?(?:#\\s*)?\\d+\\s*(?:話)?");
    private static final Pattern CLEAN_SUFFIX = Pattern.compile(
        "(?i)(?:\\s*(?:字幕|吹替|吹き替え|見逃し配信|無料配信|完全版)\\s*(?:版)?\\s*)$");
    private static final Pattern SYMBOLS = Pattern.compile("[\\p{S}\\p{P}]");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    static final class Parsed {
        final String fullSeries, baseTitle, searchWord, subtitle;
        final Integer season, episode;
        final boolean multipleEpisodes;

        Parsed(String fullSeries, String baseTitle, String searchWord,
               String subtitle, Integer season, Integer episode,
               boolean multipleEpisodes) {
            this.fullSeries = fullSeries;
            this.baseTitle = baseTitle;
            this.searchWord = searchWord;
            this.subtitle = subtitle;
            this.season = season;
            this.episode = episode;
            this.multipleEpisodes = multipleEpisodes;
        }
    }

    static String normalize(String raw) {
        if (raw == null) return "";
        return SPACES.matcher(Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .replace("\u200b","").replace("\u2060","").replace("\ufeff","")
            .replace('\n',' ')).replaceAll(" ").trim();
    }

    static String removeSymbols(String raw) {
        return SPACES.matcher(SYMBOLS.matcher(normalize(raw))
            .replaceAll(" ")).replaceAll(" ").trim();
    }

    static String normalizeAll(String raw) {
        return removeSymbols(raw).replace(" ","").toUpperCase(Locale.ROOT);
    }

    private static String cleanSeries(String series) {
        String result=normalize(series);
        result=result.replaceFirst("(?i)^(?:TV|テレビ)?アニメ(?:ーション)?\\s*[「『](.+?)[」』]\\s*", "$1 ");
        result=result.replaceFirst("(?i)^(?:TV|テレビ)?アニメ(?:ーション)?\\s+", "");
        result=result.replaceFirst("(?i)\\s+the\\s+animation$", "");
        return CLEAN_SUFFIX.matcher(result).replaceAll("").trim();
    }

    private static Integer seasonNumber(String raw) {
        Matcher match=SEASON.matcher(normalize(raw));
        if (!match.find()) return null;
        for (int group=1;group<=5;group++)
            if (match.group(group)!=null) return Integer.valueOf(match.group(group));
        return null;
    }

    private static String seasonFreeTitle(String series) {
        String cleaned=cleanSeries(series);
        Matcher match=SEASON.matcher(cleaned);
        return match.find()?cleaned.substring(0,match.start()).trim():cleaned;
    }

    private static int kanjiNumber(String text) {
        String digits="一二三四五六七八九";
        if ("十".equals(text))return 10;
        int ten=text.indexOf('十');
        if (ten>=0) {
            String before=text.substring(0,ten);
            String after=text.substring(ten+1);
            int tens=before.isEmpty()?1:digits.indexOf(before.charAt(0))+1;
            int units=after.isEmpty()?0:digits.indexOf(after.charAt(0))+1;
            return tens>=1&&units>=0?tens*10+units:-1;
        }
        if (text.length()==1)return digits.indexOf(text.charAt(0))+1;
        return -1;
    }

    static Parsed parse(String series, String episodeTitle) {
        String cleaned=cleanSeries(series);
        String title=seasonFreeTitle(cleaned);
        Integer season=seasonNumber(cleaned);
        String episodeText=normalize(episodeTitle);
        boolean multiple=MULTIPLE_EP.matcher(episodeText).find();
        Integer number=null;
        int textEnd=0;
        Matcher numeric=EP_NUMERIC.matcher(episodeText);
        while(numeric.find()) {
            int value=-1;
            for(int group=1;group<=4;group++) {
                if(numeric.group(group)!=null) {
                    value=Integer.parseInt(numeric.group(group));break;
                }
            }
            if(value<0)continue;
            if(number!=null&&number!=value) multiple=true;
            if(number==null) {number=value;textEnd=numeric.end();}
        }
        Matcher kanji=EP_KANJI.matcher(episodeText);
        while(kanji.find()) {
            int value=kanjiNumber(kanji.group(1));
            if(value<0)continue;
            if(number!=null&&number!=value)multiple=true;
            if(number==null){number=value;textEnd=kanji.end();}
        }
        if(multiple)number=null;
        String subtitle=(textEnd>0 ? episodeText.substring(textEnd) : episodeText)
            .replaceFirst("^[\\s:：・/\\-〜~#]+","").trim();
        if(subtitle.length()>=2 &&
            ((subtitle.startsWith("「")&&subtitle.endsWith("」"))
            ||(subtitle.startsWith("『")&&subtitle.endsWith("』"))))
            subtitle=subtitle.substring(1,subtitle.length()-1).trim();
        String search=removeSymbols(title).toUpperCase(Locale.ROOT);
        return new Parsed(cleaned,title,search,subtitle,season,number,multiple);
    }

    /** NCOverlay's 0.85 similarity threshold on stripped titles. */
    static double similarity(String a,String b) {
        a=normalizeAll(a);b=normalizeAll(b);
        int m=a.length(),n=b.length();
        if(m==0&&n==0)return 1;
        if(m==0||n==0)return 0;
        int[] previous=new int[n+1],current=new int[n+1];
        for(int j=0;j<=n;j++)previous[j]=j;
        for(int i=1;i<=m;i++) {
            current[0]=i;
            for(int j=1;j<=n;j++)
                current[j]=Math.min(Math.min(previous[j]+1,current[j-1]+1),
                    previous[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));
            int[] temp=previous;previous=current;current=temp;
        }
        return 1.0-previous[n]/(double)Math.max(m,n);
    }

    /**
     * 2: similar stripped title, 1: NCOverlay-style partial title match,
     * 0: reject. Explicitly conflicting seasons are never auto-matched.
     */
    static int titleMatch(Parsed requested,String candidate) {
        Parsed actual=parse(candidate,"");
        String expected=normalizeAll(requested.baseTitle);
        String received=normalizeAll(actual.baseTitle);
        if(expected.isEmpty()||received.isEmpty())return 0;
        if(requested.season!=null&&actual.season!=null
            &&!requested.season.equals(actual.season))return 0;
        if(requested.episode!=null
            ? similarity(requested.baseTitle,actual.baseTitle)>=0.85
            : expected.equals(received))return 2;
        if(requested.episode!=null&&Math.min(expected.length(),received.length())>=4
            &&(expected.contains(received)||received.contains(expected)))return 1;
        return 0;
    }

    static boolean subtitleMatches(String expected,String actual) {
        String a=normalizeAll(expected),b=normalizeAll(actual);
        if(a.length()<3||b.length()<3)return false;
        return a.equals(b)||similarity(a,b)>=0.85||
            (Math.min(a.length(),b.length())>=6&&
                (a.contains(b)||b.contains(a)));
    }

    static List<String> searchTerms(Parsed parsed) {
        LinkedHashSet<String> terms=new LinkedHashSet<>();
        if(!parsed.searchWord.isEmpty())terms.add(parsed.searchWord);
        // Backup terms for incomplete metadata; only exact/fuzzy matched
        // results can be used afterwards.
        String source=removeSymbols(parsed.fullSeries).toUpperCase(Locale.ROOT);
        if(!source.isEmpty())terms.add(source);
        return new ArrayList<>(terms);
    }

    private NcoSearchMatcher() {}
}
