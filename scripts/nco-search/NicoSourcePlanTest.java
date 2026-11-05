package org.example.tverpatches.extension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class NicoSourcePlanTest {
    static void check(boolean good,String why) {
        if(!good)throw new AssertionError(why);
    }
    public static void main(String[] args) {
        List<String> two=Arrays.asList("jk7|2026-10-30 12:00|2026-10-30 12:30",
            "nico:sm9");
        List<String> selected=NicoSourcePlan.select(two);
        check(selected.size()==2&&selected.contains("nico:sm9"),"both sources");
        check(NicoSourcePlan.budget(0,2)==15000,"reserve room for video");
        check(NicoSourcePlan.budget(9000,1)==21000,"return unused capacity");

        List<String> full=new ArrayList<>();
        for(int i=0;i<32;i++)full.add("jk7|2026-01-"+String.format("%02d",i+1)+" 12:00|2026-01-01 12:30");
        for(int i=0;i<6;i++)full.add("nico:sm"+(i+10));
        selected=NicoSourcePlan.select(full);
        check(selected.size()==32,"source limit");
        for(int i=0;i<6;i++)check(selected.contains("nico:sm"+(i+10)),
            "NicoNico videos must not be dropped behind 32 broadcasts");
        check(selected.get(0).startsWith("jk7"),"keep archive order");
        int used=0;
        for(int i=0;i<selected.size();i++) {
            int limit=NicoSourcePlan.budget(used,selected.size()-i);
            check(limit>0,"each source receives capacity");
            used+=limit;
        }
        check(used==NicoSourcePlan.MAX_COMMENTS,"total capped");
        check(NicoSourcePlan.select(Arrays.asList("nico:sm9","nico:sm9")).size()==1,
            "deduplicate same video");
        System.out.println("Nico mixed-source selection and fair budgeting passed");
    }
}
