package org.example.tverpatches.extension;

public final class NcoSearchMatcherTest {
    private static void check(boolean cond,String message){
        if(!cond)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        NcoSearchMatcher.Parsed drama=NcoSearchMatcher.parse(
            "孤独のグルメ Season4",
            "第2話 中央区銀座の 韓国風天ぷらと参鶏湯ラーメン");
        check(drama.season!=null&&drama.season==4,"Season4");
        check(drama.episode!=null&&drama.episode==2,"episode #2");
        check("孤独のグルメ".equals(drama.searchWord),"NCOverlay-style series query");
        check(NcoSearchMatcher.titleMatch(drama,"孤独のグルメ Season4")==2,"same series");
        check(NcoSearchMatcher.titleMatch(drama,"孤独のグルメ Season3")==0,
            "must not mix Season3 reruns");
        check(NcoSearchMatcher.subtitleMatches(drama.subtitle,
            "中央区銀座の韓国風天ぷらと参鶏湯ラーメン"),"subtitle normalized");
        NcoSearchMatcher.Parsed anime=NcoSearchMatcher.parse(
            "鬼滅の刃 遊郭編","第3話「何者？」");
        check(anime.episode!=null&&anime.episode==3,"number from episode");
        check("鬼滅の刃 遊郭編".equals(anime.searchWord),"anime title query");
        check(NcoSearchMatcher.titleMatch(anime,"鬼滅の刃 遊郭編")==2,
            "exact spinoff");
        check(NcoSearchMatcher.titleMatch(anime,"鬼滅の刃 柱稽古編")==0,
            "different arc must not be matched");
        NcoSearchMatcher.Parsed noNumber=NcoSearchMatcher.parse(
            "ぼっち・ざ・ろっく!","君の家まで");
        check(noNumber.episode==null,"episode missing");
        check(NcoSearchMatcher.subtitleMatches(noNumber.subtitle,"君の家まで"),
            "subtitle search");
        NcoSearchMatcher.Parsed kanji=NcoSearchMatcher.parse(
            "あいうえお 第2期","第十二話 『衝撃』");
        check(kanji.season!=null&&kanji.season==2,"season Japanese");
        check(kanji.episode!=null&&kanji.episode==12,"Kanji episode number");
        NcoSearchMatcher.Parsed multi=NcoSearchMatcher.parse(
            "アニメ番組","#1〜#4 一挙放送");
        check(multi.multipleEpisodes && multi.episode==null,"no bulk-series auto match");
        System.out.println("NCOverlay-style title and episode parsing regression passed");
    }
}
