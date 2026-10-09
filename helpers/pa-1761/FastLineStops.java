package com.ispina.lokalnie.transit;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Offline index created from the same GTFS ZIP shipped with the APK.
 * The old ZIP scanner remains a fallback for missing/corrupt assets.
 */
public final class FastLineStops {
    private FastLineStops(){}
    private static final Map<String,Map<String,List<GtfsNearby.LineStop>>> CACHE=new HashMap<>();
    public static synchronized List<GtfsNearby.LineStop> forLine(Context ctx,String provider,String line)
            throws IOException {
        if(!"warsaw".equals(provider)&&!"mld".equals(provider))return null;
        Map<String,List<GtfsNearby.LineStop>> perLine=CACHE.get(provider);
        if(perLine==null){
            perLine=read(ctx,provider);
            CACHE.put(provider,perLine);
        }
        List<GtfsNearby.LineStop> found=perLine.get(line);
        return found==null?Collections.emptyList():new ArrayList<>(found);
    }
    private static Map<String,List<GtfsNearby.LineStop>> read(Context ctx,String network)throws IOException{
        Map<String,List<GtfsNearby.LineStop>> output=new HashMap<>();
        int rows=0;
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(
                new GZIPInputStream(ctx.getAssets().open("pa_gtfs/"+network+"-lines.tsv.gz")),
                StandardCharsets.UTF_8))){
            String line;
            while((line=reader.readLine())!=null){
                if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Anulowano");
                if(++rows>100000)throw new IOException("Niepoprawny indeks przystanków");
                String[] t=line.split("\t",-1);
                if(t.length!=5||!t[0].matches("[A-Z0-9]{1,8}")||t[1].isEmpty())continue;
                int seq;
                try{seq=Integer.parseInt(t[2]);}
                catch(NumberFormatException e){continue;}
                GtfsNearby.LineStop stop=new GtfsNearby.LineStop();
                stop.id=t[1];stop.sequence=seq;stop.name=t[3];stop.code=t[4];
                output.computeIfAbsent(t[0],k->new ArrayList<>()).add(stop);
            }
        }
        for(List<GtfsNearby.LineStop> stops:output.values()){
            stops.sort(Comparator.comparingInt((GtfsNearby.LineStop s)->s.sequence)
                .thenComparing(s->s.name.toLowerCase(Locale.ROOT))
                .thenComparing(s->s.code==null?"":s.code));
        }
        return output;
    }
}
