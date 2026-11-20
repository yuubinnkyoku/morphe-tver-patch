package org.example.tverpatches.extension;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Bounded plan for loading both Jikkyo archives and NicoNico videos.
 *
 * Old code fetched archives first, potentially consuming all 30,000 comments
 * or all 32 source slots before reaching a NicoNico video. Always reserve
 * source slots and comment capacity for all selected sources.
 */
final class NicoSourcePlan {
    static final int MAX_SOURCES=32;
    static final int MAX_COMMENTS=30000;

    static List<String> select(List<String> sources) {
        LinkedHashSet<String> unique=new LinkedHashSet<>(sources);
        List<String> archives=new ArrayList<>();
        List<String> videos=new ArrayList<>();
        for(String source:unique) {
            if(source.startsWith("nico:"))videos.add(source);
            else archives.add(source);
        }
        // Retain as many NicoNico sources as possible, up to six,
        // and use remaining slots for Jikkyo historical broadcasts.
        int videosToUse=Math.min(6,Math.min(videos.size(),MAX_SOURCES));
        int archivesToUse=Math.min(archives.size(),MAX_SOURCES-videosToUse);
        List<String> selected=new ArrayList<>(archivesToUse+videosToUse);
        selected.addAll(archives.subList(0,archivesToUse));
        selected.addAll(videos.subList(0,videosToUse));
        return selected;
    }

    static int budget(int used,int remainingSources) {
        if(remainingSources<=0||used>=MAX_COMMENTS)return 0;
        return Math.max(1,(MAX_COMMENTS-used)/remainingSources);
    }

    private NicoSourcePlan(){}
}
