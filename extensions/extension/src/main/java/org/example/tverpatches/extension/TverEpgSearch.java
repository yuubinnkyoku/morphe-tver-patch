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

    private static int score(String title, String episode, String epgTitle,
                             String epgEpisode) {
        String a = normalize(title), b = normalize(epgTitle);
        if (!alike(a,b)) return 0;
        int points = a.equals(b) ? 70 : 52;
        String e = normalize(episode), f = normalize(epgEpisode);
        // No episode title: there is not enough evidence for auto-loading.
        if (e.isEmpty() || f.isEmpty()) return points;
        if (e.equals(f)) return points + 30;
        if (alike(e,f)) return points + 15;
        // Generic episode title '第2話' may appear inside the EPG title.
        if (e.matches("第?[0-9]{1,3}(話|回|期|章).*")
            && f.contains(e)) return points + 15;
        // Mismatched nonempty episode titles: do not auto-select.
        return points - 10;
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
                    if (start <= 0 || end <= start || end-start > 21600) continue;
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
            if (unique.size() >= 12) break;
        }
        return new Result(unique, success);
    }

    /** Require an unambiguous, exact series + episode match. */
    static Candidate confident(List<Candidate> list) {
        if (list.size() != 1 || list.get(0).score < 100) return null;
        return list.get(0);
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
