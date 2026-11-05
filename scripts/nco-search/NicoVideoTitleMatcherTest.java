package org.example.tverpatches.extension;

public final class NicoVideoTitleMatcherTest {
    static void ok(boolean value,String message) {
        if(!value)throw new AssertionError(message);
    }
    public static void main(String[] argv) {
        NcoSearchMatcher.Parsed drama=NcoSearchMatcher.parse(
            "孤独のグルメ Season4","第2話 中央区銀座の 韓国風天ぷらと参鶏湯ラーメン");
        ok(NicoVideoTitleMatcher.queries(drama).get(0).contains("第2話"),
            "NCOverlay episode keyword");
        ok(NicoVideoTitleMatcher.match(drama,
            "孤独のグルメ Season4 第2話 中央区銀座の韓国風天ぷらと参鶏湯ラーメン")
            .autoSelect,"same drama episode");
        ok(NicoVideoTitleMatcher.match(drama,
            "孤独のグルメ Season3 第2話 中央区銀座の韓国風天ぷらと参鶏湯ラーメン")
            .score==0,"different season");
        ok(NicoVideoTitleMatcher.match(drama,
            "孤独のグルメ Season4 第12話 中央区銀座の韓国風天ぷらと参鶏湯ラーメン")
            .score==0,"12 vs 2");
        ok(NicoVideoTitleMatcher.match(drama,
            "孤独のグルメ Season4 第1話〜第2話 一挙放送")
            .score==0,"episode bundle");
        NcoSearchMatcher.Parsed anime=NcoSearchMatcher.parse(
            "鬼滅の刃 遊郭編","第3話");
        ok(NicoVideoTitleMatcher.match(anime,
            "鬼滅の刃 遊郭編 第3話").autoSelect,"exact anime episode");
        ok(NicoVideoTitleMatcher.match(anime,
            "鬼滅の刃 無限列車編 第3話").score==0,"wrong anime arc");
        ok(NicoVideoTitleMatcher.match(anime,
            "鬼滅の刃 遊郭編 第13話").score==0,"wrong number");
        ok(NicoVideoTitleMatcher.match(anime,
            "鬼滅の刃 遊郭編 予告映像").score==0,"no episode evidence");
        System.out.println("NicoNico video source title matching tests passed");
    }
}
