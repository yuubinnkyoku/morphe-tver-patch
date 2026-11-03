package org.example.tverpatches.extension;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fetch public NicoNico video comments using its watch v4 response and nvComment
 * v1 API. No credentials or cookies are copied from the user's NicoNico app.
 *
 * Matches the API sequence used by NCOverlay's nco-utils; a private, paid or
 * members-only video may not be readable without a logged-in session.
 */
final class NicoVideoCommentSource {
    static final int MAX_COMMENTS = 30000;
    private static final int MAX_RESPONSE_CHARS = 12_000_000;
    private static final int BATCH_SIZE = 250;

    static String videoId(String input) {
        String value=input==null?"":input.trim();
        // Accept video ID, canonical URL, or an internal "nico:" source key.
        if(value.startsWith("nico:"))value=value.substring(5);
        value=value.replaceFirst("(?i)^https?://(?:www\\.)?nicovideo\\.jp/watch/","");
        int end=value.indexOf('?');
        if(end>=0)value=value.substring(0,end);
        end=value.indexOf('#');
        if(end>=0)value=value.substring(0,end);
        if(!value.matches("(?:sm|so|nm)[0-9]{1,12}"))
            throw new IllegalArgumentException("ニコニコ動画のID（sm〜/so〜/nm〜）を指定してください");
        return value;
    }

    static String specification(String input) {
        return "nico:"+videoId(input);
    }

    private static String read(HttpURLConnection connection) throws IOException {
        int status=connection.getResponseCode();
        if(status!=200)
            throw new IOException("ニコニコ動画APIがHTTP "+status+" を返しました（会員限定動画などは取得できません）");
        StringBuilder out=new StringBuilder();
        try(InputStream stream=connection.getInputStream();
            BufferedReader reader=new BufferedReader(new InputStreamReader(
                stream,StandardCharsets.UTF_8))) {
            char[] buffer=new char[4096];
            int n;
            while((n=reader.read(buffer))!=-1) {
                if(out.length()+n>MAX_RESPONSE_CHARS)
                    throw new IOException("ニコニコ動画のコメント取得結果が容量上限を超えました");
                out.append(buffer,0,n);
            }
        }
        return out.toString();
    }

    private static JSONObject watch(String id) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(
            "https://www.nicovideo.jp/watch/"+id
                +"?responseType=json&nvpcClientVersion=1").openConnection();
        try {
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Accept","application/json");
            conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 "
                +"(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36");
            JSONObject root=new JSONObject(read(conn));
            if(root.optJSONObject("meta")==null||
                root.optJSONObject("meta").optInt("status")!=200)
                throw new IOException("動画情報が取得できません（削除・非公開・会員限定の可能性）");
            JSONObject data=root.optJSONObject("data");
            JSONObject response=data==null?null:data.optJSONObject("response");
            JSONObject watch=response==null?null:response.optJSONObject("$watchV4");
            JSONObject content=watch==null?null:watch.optJSONObject("data");
            if(content==null)throw new IOException("動画情報の形式が対応外です");
            return content;
        }finally {conn.disconnect();}
    }

    static JikkyoCommentSource.Result fetch(String source,int maxCount) throws Exception {
        String id=videoId(source);
        int limit=Math.min(MAX_COMMENTS,Math.max(1,maxCount));
        JSONObject video=watch(id);
        JSONObject comment=video.optJSONObject("comment");
        JSONObject nv=comment==null?null:comment.optJSONObject("nvComment");
        if(nv==null)throw new IOException("この動画には取得可能なコメント情報がありません");
        String host=nv.optString("server","");
        // The watch response supplies an endpoint; allow only official
        // nvComment hosts to avoid untrusted arbitrary outbound requests.
        URL serverUrl=new URL(host);
        String domain=serverUrl.getHost().toLowerCase(Locale.ROOT);
        if(!"https".equalsIgnoreCase(serverUrl.getProtocol())
            ||(!domain.equals("nvcomment.nicovideo.jp")
            && !domain.endsWith(".nvcomment.nicovideo.jp")))
            throw new IOException("コメント取得先が正規のドメインではありません");
        String key=nv.optString("threadKey","");
        JSONObject params=nv.optJSONObject("params");
        if(key.isEmpty()||params==null)
            throw new IOException("この動画のコメント取得用認証情報がありません");
        JSONObject payload=new JSONObject()
            .put("params",params)
            .put("threadKey",key)
            .put("additionals",new JSONObject());
        String target="https://"+domain+"/v1/threads";
        HttpURLConnection conn=(HttpURLConnection)new URL(target).openConnection();
        JSONObject root;
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Content-Type","application/json");
            conn.setRequestProperty("Accept","application/json");
            conn.setRequestProperty("X-Frontend-Id","6");
            conn.setRequestProperty("X-Frontend-Version","0");
            conn.setRequestProperty("X-Client-Os-Type","others");
            byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream os=conn.getOutputStream()){os.write(bytes);}
            root=new JSONObject(read(conn));
        }finally {conn.disconnect();}
        JSONObject meta=root.optJSONObject("meta");
        if(meta==null||meta.optInt("status")!=200) {
            String error=meta==null?"unknown":meta.optString("errorCode","unknown");
            throw new IOException("ニコニココメントAPIから取得できません: "+error);
        }
        JSONObject data=root.optJSONObject("data");
        JSONArray threads=data==null?null:data.optJSONArray("threads");
        if(threads==null)throw new IOException("コメントスレッドが返されませんでした");
        List<String> batches=new ArrayList<>();
        JSONArray batch=new JSONArray();
        int count=0;
        for(int i=0;i<threads.length()&&count<limit;i++) {
            JSONObject thread=threads.optJSONObject(i);
            JSONArray comments=thread==null?null:thread.optJSONArray("comments");
            if(comments==null)continue;
            for(int j=0;j<comments.length()&&count<limit;j++) {
                JSONObject item=comments.optJSONObject(j);
                if(item==null)continue;
                String body=item.optString("body","");
                if(body.isEmpty()||body.length()>400||body.startsWith("/"))continue;
                long position=item.optLong("vposMs",-1);
                if(position<0||position>24L*60*60*1000)continue;
                JSONArray commands=item.optJSONArray("commands");
                if(commands==null)commands=new JSONArray();
                JSONArray safeCommands=new JSONArray();
                for(int k=0;k<commands.length()&&k<15;k++) {
                    String command=commands.optString(k,"");
                    if(!command.isEmpty()&&command.length()<=24)
                        safeCommands.put(command);
                }
                // Only pass display fields, never user IDs, to local WebView.
                batch.put(new JSONObject().put("vposMs",position)
                    .put("body",body).put("commands",safeCommands));
                count++;
                if(batch.length()>=BATCH_SIZE) {
                    batches.add(batch.toString());
                    batch=new JSONArray();
                }
            }
        }
        if(batch.length()>0)batches.add(batch.toString());
        return new JikkyoCommentSource.Result(batches,count);
    }
    private NicoVideoCommentSource(){}
}
