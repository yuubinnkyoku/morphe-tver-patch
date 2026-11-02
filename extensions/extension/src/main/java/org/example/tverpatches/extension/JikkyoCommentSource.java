package org.example.tverpatches.extension;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Native, opt-in reader for https://jikkyo.tsukumijima.net/ archive comments.
 * No Niconico authentication, site cookies, or WebView networking required.
 * Channel and broadcast timestamps are chosen manually; automatic show/title
 * matching is deliberately out of scope.
 */
final class JikkyoCommentSource {
    private static final int MAX_RESPONSE_CHARS = 8_000_000;
    private static final int MAX_COMMENTS = 30_000;
    private static final int BATCH_SIZE = 250;

    static final class Result {
        final List<String> batches;
        final int count;
        Result(List<String> batches, int count) {
            this.batches = batches;
            this.count = count;
        }
    }

    private static final class Spec {
        final String channel;
        final long start, end, offsetMs;
        Spec(String channel, long start, long end, long offsetMs) {
            this.channel = channel;
            this.start = start;
            this.end = end;
            this.offsetMs = offsetMs;
        }
    }

    static void validate(String raw) {
        parse(raw);
    }

    private static Spec parse(String raw) {
        String[] fields = raw.trim().split("\\|", -1);
        if (fields.length < 3 || fields.length > 4)
            throw new IllegalArgumentException("区切りには | を使用してください");
        String channel = fields[0].trim().toLowerCase(Locale.ROOT);
        if (!channel.matches("jk[0-9]{1,3}"))
            throw new IllegalArgumentException("チャンネルは jk1 などで指定");
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");
            ZoneId zone = ZoneId.of("Asia/Tokyo");
            long start = LocalDateTime.parse(fields[1].trim(), fmt)
                .atZone(zone).toEpochSecond();
            long end = LocalDateTime.parse(fields[2].trim(), fmt)
                .atZone(zone).toEpochSecond();
            long offsetMs = fields.length == 4 && !fields[3].trim().isEmpty()
                ? Long.parseLong(fields[3].trim()) * 1000 : 0;
            if (start <= 0 || end <= start || end - start > 21600
                || Math.abs(offsetMs) > 3600000)
                throw new IllegalArgumentException("期間は6時間以内、補正は±3600秒以内");
            return new Spec(channel, start, end, offsetMs);
        } catch (DateTimeException | NumberFormatException ex) {
            throw new IllegalArgumentException("日時形式は 2026-10-23 21:00", ex);
        }
    }

    // MUST run on a background thread. Caller handles lifecycle and delivery.
    static Result fetch(String raw) throws Exception {
        Spec spec = parse(raw);
        String address = "https://jikkyo.tsukumijima.net/api/kakolog/"
            + spec.channel + "?starttime=" + spec.start + "&endtime=" + spec.end
            + "&format=json";
        HttpURLConnection connection = (HttpURLConnection) new URL(address)
            .openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int code = connection.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    if (response.length() + read > MAX_RESPONSE_CHARS)
                        throw new IOException("取得データが8MBを超えています");
                    response.append(buffer, 0, read);
                }
            }
            JSONObject root = new JSONObject(response.toString());
            if (root.has("error"))
                throw new IOException("実況API: " + root.optString("error"));
            JSONArray packet = root.optJSONArray("packet");
            if (packet == null) throw new IOException("実況APIにpacketがありません");
            List<String> batches = new ArrayList<>();
            JSONArray batch = new JSONArray();
            int count = 0;
            for (int i = 0; i < packet.length() && count < MAX_COMMENTS; i++) {
                JSONObject entry = packet.optJSONObject(i);
                JSONObject chat = entry == null ? null : entry.optJSONObject("chat");
                if (chat == null) continue;
                long seconds = chat.optLong("date", -1);
                if (seconds < spec.start || seconds >= spec.end) continue;
                String body = chat.optString("content", "");
                if (body.isEmpty() || body.length() > 400 || body.startsWith("/"))
                    continue;
                long micros = Math.min(999999, Math.max(0,
                    chat.optLong("date_usec", 0)));
                long position = (seconds - spec.start) * 1000 + micros / 1000
                    + spec.offsetMs;
                if (position < 0) continue;
                JSONArray commands = new JSONArray();
                for (String command : chat.optString("mail", "").split("\\s+")) {
                    if (!command.isEmpty() && command.length() <= 24)
                        commands.put(command);
                }
                // Keep only display data; never send user IDs to the WebView.
                batch.put(new JSONObject().put("vposMs", position)
                    .put("body", body).put("commands", commands));
                count++;
                if (batch.length() == BATCH_SIZE) {
                    batches.add(batch.toString());
                    batch = new JSONArray();
                }
            }
            if (batch.length() != 0) batches.add(batch.toString());
            return new Result(batches, count);
        } finally {
            connection.disconnect();
        }
    }

    private JikkyoCommentSource() {}
}
