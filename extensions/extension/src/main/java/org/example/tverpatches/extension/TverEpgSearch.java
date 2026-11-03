package org.example.tverpatches.extension;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Narrow, opt-in matching for the actual TVer broadcast EPG, inspired by
 * NCOverlay's TVer metadata extraction and EPG source selection.
 *
 * Deliberately does not infer an archive timestamp from the watch date.
 * Only broadcast items retrieved from TVer's EPG may become candidates.
 * A candidate is auto-selected only when series AND episode titles agree.
 */
final class TverEpgSearch {
    static final class Candidate {
        final String title, episode, broadcaster, channel;
        final long start, end;
        final int score;
        Candidate(String title, String episode, String broadcaster, String channel,
                  long start, long end, int score) {
            this.title = title;
            this.episode = episode;
            this.broadcaster = broadcaster;
            this.channel = channel;
            this.start = start;
            this.end = end;
            this.score = score;
        }
        String specification(int offsetSec) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
                .withZone(ZoneId.of("Asia/Tokyo"));
            return channel + "|" + fmt.format(java.time.Instant.ofEpochSecond(start))
                + "|" + fmt.format(java.time.Instant.ofEpochSecond(end))
                + "|" + offsetSec;
        }
        String label() {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd HH:mm")
                .withZone(ZoneId.of("Asia/Tokyo"));
            String ep = episode.isEmpty() ? "" : " / " + episode;
            return fmt.format(java.time.Instant.ofEpochSecond(start)) + " "
                + broadcaster + "\n" + title + ep;
        }
    }
    static final class Result {
        final List<Candidate> candidates;
        final int successfulDays;
        Result(List<Candidate> candidates, int successfulDays) {
            this.candidates = candidates;
            this.successfulDays = successfulDays;
        }
    }

    private static final Map<String,String> CHANNELS = new HashMap<>();
    private static final Map<String,JSONObject> CACHE = new HashMap<>();
    private static final Map<String,Long> CACHE_UNTIL = new HashMap<>();
    static {
        // TVer channel identifiers matched to Nico Jikkyo by nco-utils.
        String[][] pairs = {
            {"120","jk1"},{"124","jk2"},{"128","jk4"},{"138","jk5"},
            {"131","jk6"},{"142","jk7"},{"134","jk8"},{"399","jk9"},
            {"426","jk10"},{"404","jk11"},{"417","jk12"},{"593","jk13"},
            {"588","jk14"},{"1","jk101"},{"5","jk141"},{"8","jk151"},
            {"11","jk161"},{"14","jk171"},{"17","jk181"},{"20","jk191"},
            {"21","jk192"},{"22","jk193"},{"23","jk200"},{"24","jk201"},
            {"26","jk211"},{"27","jk222"},{"31","jk236"},{"39","jk252"},
            {"260","jk260"},{"263","jk263"},{"265","jk265"}
        };
        for (String[] pair : pairs) CHANNELS.put(pair[0], pair[1]);
    }

    static String normalize(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[\\s\\p{P}\\p{S}　]+", "")
            .replaceAll("(見逃し配信|無料配信|字幕版|完全版)$", "");
    }

    private static boolean alike(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        return Math.min(a.length(), b.length()) >= 6
            && (a.contains(b) || b.contains(a))
            && Math.min(a.length(), b.length()) * 10 >= Math.max(a.length(), b.length()) * 8;
    }

    // A series-wide marathon is not the selected VOD episode: matching only
    // "孤独のグルメ Season4" would load unrelated comments.
    private static final Pattern MARATHON = Pattern.compile(
        "全話|イッキ見|一挙|一気見|総集編|ダイジェスト|傑作選|まとめて放送");
    private static final Pattern EPISODE_NUMBER = Pattern.compile(
        "(?:第\\s*([0-9]{1,3})\\s*[話回]|[#＃]\\s*([0-9]{1,3})|([0-9]{1,3})\\s*話)");

    static int episodeNumber(String input) {
        Matcher match = EPISODE_NUMBER.matcher(
            Normalizer.normalize(input, Normalizer.Form.NFKC));
        if (!match.find()) return -1;
        for (int i = 1; i <= 3; i++) {
            if (match.group(i) != null) return Integer.parseInt(match.group(i));
        }
        return -1;
    }

    private static String episodeDescription(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC);
        return normalize(EPISODE_NUMBER.matcher(normalized).replaceFirst(""));
    }

    private static int score(String title, String episode, String epgTitle,
                             String epgEpisode) {
        String a = normalize(title), b = normalize(epgTitle);
        if (!alike(a,b)) return 0;
        int base = a.equals(b) ? 70 : 52;
        String e = normalize(episode), f = normalize(epgEpisode);
        // Without episode-level evidence we cannot assert this archive belongs
        // to the particular TVer episode. Do not offer a misleading candidate.
        if (e.isEmpty() || f.isEmpty()) return 0;
        if (MARATHON.matcher(epgEpisode).find()) return 0;
        int expectedNumber = episodeNumber(episode);
        int broadcastNumber = episodeNumber(epgEpisode);
        if (expectedNumber >= 0 && broadcastNumber >= 0
            && expectedNumber != broadcastNumber) return 0;
        if (e.equals(f)) return base + 30;
        if (alike(e,f)) return base + 25;

        String description = episodeDescription(episode);
        // Textual episode subtitle agreement is stronger than episode number.
        if (description.length() >= 8 && f.contains(description))
            return base + 25;

        // Number-only EPG labels ("第2話" or "#2") are acceptable candidates,
        // but NOT confident enough to be silently auto-selected.
        String broadcasterDescription = episodeDescription(epgEpisode);
        if (expectedNumber >= 0 && broadcastNumber == expectedNumber
            && broadcasterDescription.length() <= 2) return base + 10;
        // Do not treat a series name or unrelated episode description as a hit.
        return 0;
    }

    /**
     * Network operation. Search the previous 8 complete broadcast dates
     * plus today. Use the original broadcast listing, not the VOD date.
     * The default is terrestrial; BS may be requested manually.
     */
    static Result search(String title, String episode, boolean bs) throws Exception {
        if (normalize(title).length() < 3) return new Result(Collections.emptyList(),0);
        ZoneId zone = ZoneId.of("Asia/Tokyo");
        LocalDate today = LocalDate.now(zone);
        long archiveCutoff = Instant.now().getEpochSecond() - 10 * 60;
        List<Candidate> found = new ArrayList<>();
        int success = 0;
        for (int daysBack = 0; daysBack <= 8; daysBack++) {
            LocalDate day = today.minusDays(daysBack);
            String date = day.format(DateTimeFormatter.ofPattern("uuuu/MM/dd"));
            JSONObject api = fetchEpg(date, bs ? "bs" : "ota");
            if (api == null) continue;
            success++;
            JSONArray contents = api.optJSONArray("contents");
            if (contents == null) continue;
            for (int i = 0; i < contents.length(); i++) {
                JSONObject content = contents.optJSONObject(i);
                if (content == null) continue;
                JSONObject broadcaster = content.optJSONObject("broadcaster");
                if (broadcaster == null) continue;
                String channel = CHANNELS.get(broadcaster.optString("id"));
                if (channel == null) continue;
                String broadcasterName = broadcaster.optString("name", channel);
                JSONArray programs = content.optJSONArray("programs");
                if (programs == null) continue;
                for (int j = 0; j < programs.length(); j++) {
                    JSONObject program = programs.optJSONObject(j);
                    if (program == null) continue;
                    String series = program.optString("seriesTitle", "");
                    String caption = program.optString("title", "");
                    String show = series.isEmpty() ? caption : series;
                    int rank = score(title, episode, show, caption);
                    if (rank < 52) continue;
                    long start = program.optLong("startAt", 0),end = program.optLong("endAt",0);
                    // The archive is historical and updated periodically.
                    // Never show a future/ongoing broadcast as a past log.
                    if (start <= 0 || end <= start || end-start > 21600
                        || end > archiveCutoff) continue;
                    found.add(new Candidate(show, caption, broadcasterName, channel,
                        start, end, rank));
                }
            }
        }
        found.sort(Comparator.<Candidate>comparingInt(c -> c.score).reversed()
            .thenComparing(Comparator.comparingLong((Candidate c) -> c.start).reversed()));
        // Deduplicate EPG lists and keep the UI bounded.
        List<Candidate> unique = new ArrayList<>();
        for (Candidate candidate : found) {
            boolean duplicate = false;
            for (Candidate already : unique) {
                if (already.channel.equals(candidate.channel)
                        && already.start == candidate.start) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) unique.add(candidate);
            if (unique.size() >= 32) break;
        }
        return new Result(unique, success);
    }

    /**
     * Choose a well-identified episode, not merely a similarly named series.
     * Repeat broadcasts of the very same episode are safe to group together:
     * prefer the latest completed one so we can select automatically.
     * Divergent high-confidence episode descriptions remain manual choices.
     */
    static Candidate confident(List<Candidate> list) {
        if (list.isEmpty()) return null;
        List<Candidate> ranked = new ArrayList<>(list);
        ranked.sort(Comparator.<Candidate>comparingInt(c -> c.score).reversed()
            .thenComparing(Comparator.comparingLong((Candidate c) -> c.start).reversed()));
        Candidate top = ranked.get(0);
        if (top.score < 95) return null;
        for (int i = 1; i < ranked.size(); i++) {
            Candidate other = ranked.get(i);
            if (other.score < top.score) break;
            // Do not silently choose between different episodes or editions
            // when they score equally well.
            if (!normalize(top.title).equals(normalize(other.title))
                || !normalize(top.episode).equals(normalize(other.episode))) return null;
        }
        return top;
    }


    /** Select every verified airing of the SAME TVer episode, including reruns. */
    static List<Candidate> automaticGroup(String series, String episode,
                                          List<Candidate> choices) {
        int expectedNo = episodeNumber(episode);
        String expectedText = episodeDescription(episode);
        List<Candidate> group = new ArrayList<>();
        for (Candidate c : choices) {
            if (c.score < 100 || MARATHON.matcher(c.episode).find()) continue;
            int actualNo = episodeNumber(c.episode);
            if (expectedNo >= 0 && actualNo >= 0 && expectedNo != actualNo)
                continue;
            String actualText = episodeDescription(c.episode);
            if (expectedText.length() >= 6 && actualText.length() >= 6
                && !expectedText.equals(actualText)
                && !expectedText.contains(actualText)
                && !actualText.contains(expectedText)) continue;
            group.add(c);
        }
        group.sort(Comparator.comparingLong((Candidate c) -> c.start).reversed());
        return merge(group, Collections.emptyList());
    }

    /** Merge different broadcaster schedules without duplicate time slots. */
    static List<Candidate> merge(List<Candidate> first, List<Candidate> second) {
        List<Candidate> all = new ArrayList<>(first);
        all.addAll(second);
        all.sort(Comparator.<Candidate>comparingInt(c -> c.score).reversed()
            .thenComparing(Comparator.comparingLong((Candidate c) -> c.start).reversed()));
        List<Candidate> unique = new ArrayList<>();
        for (Candidate next : all) {
            boolean seen = false;
            for (Candidate prev : unique) {
                if (prev.channel.equals(next.channel) && prev.start == next.start) {
                    seen = true;
                    break;
                }
            }
            if (!seen) unique.add(next);
            if (unique.size() >= 32) break;
        }
        return unique;
    }

    private static JSONObject fetchEpg(String date, String type) {
        String cacheKey = date + "|" + type;
        synchronized (CACHE) {
            if (CACHE_UNTIL.getOrDefault(cacheKey,0L) > System.currentTimeMillis())
                return CACHE.get(cacheKey);
        }
        HttpURLConnection c = null;
        try {
            String url = "https://service-api.tver.jp/api/v1/callEPGv2?date="
                + date.replace("/", "%2F") + "&area=23&type=" + type;
            c = (HttpURLConnection)new URL(url).openConnection();
            c.setConnectTimeout(4000);
            c.setReadTimeout(4000);
            c.setRequestProperty("x-tver-platform-type","web");
            c.setRequestProperty("Accept","application/json");
            c.setInstanceFollowRedirects(false);
            if (c.getResponseCode() != 200) return null;
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    c.getInputStream(),StandardCharsets.UTF_8))) {
                char[] chunk = new char[4096];
                int n;
                while ((n=reader.read(chunk))!=-1) {
                    if (body.length()+n>2_000_000) return null;
                    body.append(chunk,0,n);
                }
            }
            JSONObject result = new JSONObject(body.toString()).optJSONObject("result");
            if (result != null) {
                synchronized (CACHE) {
                    if (CACHE.size() > 28) {
                        CACHE.clear();
                        CACHE_UNTIL.clear();
                    }
                    CACHE.put(cacheKey,result);
                    CACHE_UNTIL.put(cacheKey,System.currentTimeMillis()+10*60*1000);
                }
            }
            return result;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private TverEpgSearch() {}
}
