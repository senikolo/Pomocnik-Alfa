package com.ispina.lokalnie.transit;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Offline trip headsign -> ordered served platforms, no GPS or network needed. */
public final class FastLineDirections {
    private FastLineDirections(){}
    public static final class Direction {
        public final String name;
        public final Map<String,Integer> stops;
        Direction(String name,Map<String,Integer> stops){
            this.name=name;
            this.stops=Collections.unmodifiableMap(stops);
        }
    }
    public static List<Direction> forLine(Context ctx,String provider,String line)throws IOException{
        if(!"warsaw".equals(provider)&&!"mld".equals(provider))return Collections.emptyList();
        InputStream data;
        try{data=ctx.getAssets().open("pa_gtfs/"+provider+"-directions.tsv");}
        catch(IOException missing){
            data=new GZIPInputStream(ctx.getAssets().open(
                "pa_gtfs/"+provider+"-directions.tsv.gz"));
        }
        Map<String,Map<String,Integer>> grouped=new LinkedHashMap<>();
        try(BufferedReader reader=new BufferedReader(
                new InputStreamReader(data,StandardCharsets.UTF_8))){
            String row;int rows=0;
            while((row=reader.readLine())!=null){
                if((++rows&8191)==0&&Thread.currentThread().isInterrupted())
                    throw new InterruptedIOException("Przerwano odczyt kierunków");
                if(rows>500000)throw new IOException("Indeks kierunków przekroczył limit");
                if(!row.startsWith(line+"\t"))continue;
                String[] parts=row.split("\t",4);
                if(parts.length!=4||parts[1].isEmpty()||parts[2].isEmpty())continue;
                int sequence;
                try{sequence=Integer.parseInt(parts[3]);}
                catch(NumberFormatException ignored){continue;}
                Map<String,Integer> stops=grouped.computeIfAbsent(
                    parts[1],k->new LinkedHashMap<>());
                Integer previous=stops.get(parts[2]);
                if(previous==null||sequence<previous)stops.put(parts[2],sequence);
            }
        }
        List<Direction> result=new ArrayList<>();
        for(Map.Entry<String,Map<String,Integer>> item:grouped.entrySet())
            result.add(new Direction(item.getKey(),item.getValue()));
        result.sort(Comparator.comparing(d->d.name,java.text.Collator.getInstance(
            new Locale("pl","PL"))));
        return result;
    }
}
