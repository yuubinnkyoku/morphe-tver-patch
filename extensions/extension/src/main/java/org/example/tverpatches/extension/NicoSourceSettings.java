package org.example.tverpatches.extension;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Per-source visibility and relative offsets. Jikkyo broadcast offset
 * encoded in the saved source spec remains separate from the setting here.
 */
final class NicoSourceSettings {
    private static final String PREFS="morphe.tver.settings";
    private static final String PREFIX="nico_source_settings_";

    private static String key(String source) {
        if(!source.startsWith("nico:")) {
            String[] fields=source.split("\\|",-1);
            if(fields.length>=3)
                source=fields[0]+"|"+fields[1]+"|"+fields[2];
        }
        String combined=NicoAutoSourceController.seriesName()+"\u0000"
            +NicoAutoSourceController.episodeName()+"\u0000"+source;
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256")
                .digest(combined.getBytes(StandardCharsets.UTF_8));
            StringBuilder id=new StringBuilder(PREFIX);
            for(int i=0;i<12;i++)
                id.append(String.format(Locale.ROOT,"%02x",bytes[i]&0xff));
            return id.toString();
        }catch(Exception ex) {
            return PREFIX+Integer.toHexString(combined.hashCode());
        }
    }

    static boolean enabled(Context context,String source) {
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getBoolean(key(source)+"_enabled",true);
    }

    static int offsetSeconds(Context context,String source) {
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getInt(key(source)+"_seconds",0);
    }

    private static void enable(Context context,String source,boolean enabled) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putBoolean(key(source)+"_enabled",enabled).apply();
    }

    private static void offset(Context context,String source,int seconds) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putInt(key(source)+"_seconds",seconds).apply();
    }

    static void show(Activity activity) {
        if(activity==null||activity.isFinishing()||activity.isDestroyed())return;
        final String episode=NicoAutoSourceController.episodeName();
        List<String> sources=new ArrayList<>(NicoAutoSourceController.currentSources(activity));
        if(sources.isEmpty()) {
            new AlertDialog.Builder(activity).setTitle("コメント元がありません")
                .setMessage("実況検索やニコニコ動画の追加を行ってください")
                .setPositiveButton("閉じる",null).show();
            return;
        }
        String[] labels=new String[sources.size()];
        for(int i=0;i<sources.size();i++) {
            String src=sources.get(i);
            String name;
            if(src.startsWith("nico:")) {
                name="ニコニコ動画 "+src.substring(5);
            }else {
                String[] parts=src.split("\\|",-1);
                name="実況 "+parts[0]+(parts.length>=2?" "+parts[1]:"");
            }
            labels[i]=(enabled(activity,src)?"☑ ":"☐ ")+name
                +"  （補正 "+offsetSeconds(activity,src)+"秒）";
        }
        new AlertDialog.Builder(activity)
            .setTitle("コメント元の表示・時刻補正")
            .setItems(labels,(dialog,which)->{
                if(episode.equals(NicoAutoSourceController.episodeName()))
                    showOptions(activity,sources.get(which));
            })
            .setNegativeButton("閉じる",null).show();
    }

    private static void showOptions(Activity activity,String source) {
        final String episode=NicoAutoSourceController.episodeName();
        boolean visible=enabled(activity,source);
        boolean isVideo=source.startsWith("nico:");
        new AlertDialog.Builder(activity)
            .setTitle(isVideo?source:"実況 "+source.split("\\|",-1)[0])
            .setItems(new String[]{
                visible?"表示をオフにする":"表示をオンにする",
                "このコメント元だけ時刻補正する",
                isVideo?"動画を一覧から削除する":"一覧へ戻る"
            },(dialog,which)->{
                if(!episode.equals(NicoAutoSourceController.episodeName()))return;
                if(which==0) {
                    enable(activity,source,!visible);
                    NicoAutoSourceController.reloadAllSources(activity,true);
                    show(activity);
                }else if(which==1) {
                    showOffset(activity,source);
                }else if(which==2&&isVideo) {
                    NicoAutoSourceController.removeNicoVideo(activity,source.substring(5));
                    show(activity);
                }else show(activity);
            })
            .setNegativeButton("戻る",(d,w)->show(activity)).show();
    }

    private static void showOffset(Activity activity,String source) {
        final String episode=NicoAutoSourceController.episodeName();
        EditText input=new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(Integer.toString(offsetSeconds(activity,source)));
        LinearLayout layout=new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(16*activity.getResources().getDisplayMetrics().density);
        layout.setPadding(pad,pad/2,pad,pad/2);
        TextView help=new TextView(activity);
        help.setText("このコメント元だけを±3600秒以内で補正します。"
            +"\n正の数で遅らせ、負の数で早めます。");
        layout.addView(help);
        layout.addView(input);
        new AlertDialog.Builder(activity)
            .setTitle("コメント元ごとの時刻補正")
            .setView(layout)
            .setPositiveButton("保存して再読込",(d,w)->{
                if(!episode.equals(NicoAutoSourceController.episodeName()))return;
                try {
                    int value=Integer.parseInt(input.getText().toString().trim());
                    if(Math.abs(value)>3600)throw new NumberFormatException();
                    offset(activity,source,value);
                    NicoAutoSourceController.reloadAllSources(activity,true);
                }catch(NumberFormatException ex) {
                    Toast.makeText(activity,"補正は±3600秒以内の整数です",
                        Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton("戻る",(d,w)->show(activity)).show();
    }

    private NicoSourceSettings(){}
}
