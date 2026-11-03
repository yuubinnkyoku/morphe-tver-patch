package org.example.tverpatches.extension;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Secondary automatic archive lookup for older episodic programmes.
 * The Syoboi Calendar database provides the original broadcast date, episode
 * number and broadcaster. It is never used to guess dates from VOD metadata.
 *
 * API documentation: https://docs.cal.syoboi.jp/spec/json.php/
 * Channel map: Midra429/nco-utils CHANNEL_IDS_JIKKYO_SYOBOCAL (MIT).
 */
final class SyobocalSearch {
    private static final Map<String,String> CHANNELS = new HashMap<>();
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter DATE_SECONDS =
        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_MINUTES =
        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");

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
        for (String[] pair : pairs) CHANNELS.put(pair[0], pair[1]);
    }

    private static JSONObject request(String query) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://cal.syoboi.jp/json.php?" + query);
            connection = (HttpURLConnection)url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(7000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-User-Agent", "MorpheTVerNicoOverlay/1.2");
            if (connection.getResponseCode() != 200) return null;
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buffer = new char[4096];
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    if (body.length() + n > 1_500_000) return null;
                    body.append(buffer, 0, n);
                }
            }
            return new JSONObject(body.toString());
        } catch (Exception error) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static long epoch(String raw) {
        try {
            String normalized = raw.trim().replace('T', ' ');
            if (normalized.length() >= 19)
                return LocalDateTime.parse(normalized.substring(0,19),DATE_SECONDS)
                    .atZone(JST).toEpochSecond();
            if (normalized.length() >= 16)
                return LocalDateTime.parse(normalized.substring(0,16),DATE_MINUTES)
                    .atZone(JST).toEpochSecond();
        } catch (Exception ignored) { }
        return 0;
    }

    private static String cleanSeries(String input) {
        // Compare a season-labelled programme with that same season, not with
        // a similarly titled but different series or season.
        return TverEpgSearch.normalize(input)
            .replaceAll("シーズン([0-9]+)", "season$1")
            .replaceAll("第([0-9]+)期", "season$1");
    }

    static TverEpgSearch.Result search(String title, String episode) {
        String expected = cleanSeries(title);
        if (expected.length() < 3) return new TverEpgSearch.Result(
            Collections.emptyList(), 0);
        int requestedNumber = TverEpgSearch.episodeNumber(episode);
        String requestedSubtitle = TverEpgSearch.normalize(episode)
            .replaceFirst("^(?:第)?[0-9]{1,3}(?:話|回)", "");
        if (requestedNumber < 0 && requestedSubtitle.length() < 5) {
            return new TverEpgSearch.Result(Collections.emptyList(), 0);
        }
        // Search by the series name. The database is primarily for anime;
        // ordinary dramas may have no corresponding entry.
        JSONObject titles = request("Req=TitleSearch&Limit=15&Search="
            + URLEncoder.encode(title, StandardCharsets.UTF_8));
        if (titles == null) return new TverEpgSearch.Result(
            Collections.emptyList(), 0);
        JSONObject values = titles.optJSONObject("Titles");
        if (values == null) return new TverEpgSearch.Result(
            Collections.emptyList(), 1);
        List<TverEpgSearch.Candidate> candidates = new ArrayList<>();
        long finishedBy = Instant.now().getEpochSecond() - 600;
        int tried = 0;
        java.util.Iterator<String> keys = values.keys();
        while (keys.hasNext() && tried < 4) {
            String key = keys.next();
            JSONObject entry = values.optJSONObject(key);
            if (entry == null) continue;
            String series = entry.optString("Title", "");
            if (!expected.equals(cleanSeries(series))) continue;
            String tid = entry.optString("TID", key);
            if (!tid.matches("[0-9]{1,8}")) continue;
            tried++;

            int count = requestedNumber;
            String subtitle = "";
            if (count < 0) {
                JSONObject subs = request("Req=SubTitles&TID=" + tid);
                JSONObject all = subs == null ? null : subs.optJSONObject("SubTitles");
                JSONObject byNumber = all == null ? null : all.optJSONObject(tid);
                if (byNumber == null) continue;
                java.util.Iterator<String> numbers = byNumber.keys();
                int matched = -1;
                while (numbers.hasNext()) {
                    String n = numbers.next();
                    String description = byNumber.optString(n, "");
                    String cleaned = TverEpgSearch.normalize(description);
                    if (cleaned.length() >= 5 &&
                        (cleaned.equals(requestedSubtitle) ||
                        (cleaned.length() >= 8 && requestedSubtitle.contains(cleaned)))) {
                        int parsed;
                        try { parsed = Integer.parseInt(n); }
                        catch (NumberFormatException ignored) { continue; }
                        if (matched != -1 && parsed != matched) {
                            matched = -1;
                            break;
                        }
                        matched = parsed;
                        subtitle = description;
                    }
                }
                count = matched;
            }
            if (count < 0 || count > 999) continue;
            JSONObject programmes = request("Req=ProgramByCount&TID=" + tid
                + "&Count=" + count);
            JSONObject records = programmes == null ? null :
                programmes.optJSONObject("Programs");
            if (records == null) continue;
            java.util.Iterator<String> ids = records.keys();
            while (ids.hasNext()) {
                JSONObject programme = records.optJSONObject(ids.next());
                if (programme == null) continue;
                String channel = CHANNELS.get(programme.optString("ChID", ""));
                if (channel == null) continue;
                long start = epoch(programme.optString("StTime", ""));
                long end = epoch(programme.optString("EdTime", ""));
                if (start <= 0 || end <= start || end-start > 21600 ||
                    end > finishedBy) continue;
                String epLabel = "第" + count + "話";
                if (!subtitle.isEmpty()) epLabel += " " + subtitle;
                candidates.add(new TverEpgSearch.Candidate(series, epLabel,
                    programme.optString("ChName",channel),channel,start,end,110));
            }
        }
        candidates.sort(Comparator.comparingLong(
            (TverEpgSearch.Candidate item) -> item.start).reversed());
        if (candidates.size() > 12)
            candidates = new ArrayList<>(candidates.subList(0,12));
        return new TverEpgSearch.Result(candidates, 1);
    }

    private SyobocalSearch() {}
}
