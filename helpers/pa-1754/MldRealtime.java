package com.ispina.lokalnie.transit;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Strict on-demand GTFS-RT JSON adapter. Never estimates delays from bus position.
 * The publicly controlled manifest must contain an authorized provider endpoint.
 * Until such an endpoint is confirmed, no delays are displayed as LIVE.
 */
public final class MldRealtime {
    private MldRealtime(){}
    private static final String CONFIG =
        "https://raw.githubusercontent.com/senikolo/Pomocnik-Alfa/main/data/pa-gtfs/live.json";
    private static final int MAX_CONFIG_BYTES=4096;
    private static final int MAX_FEED_BYTES=6*1024*1024;
    private static final long MAX_AGE_MS=120000L;

    private static byte[] read(String url,int maxBytes,boolean trustedProvider)throws Exception{
        URL uri=new URL(url);
        if(!"https".equals(uri.getProtocol()) || uri.getUserInfo()!=null || uri.getPort()!=-1)throw new java.io.IOException("Niedozwolony adres");
        String host=uri.getHost().toLowerCase(java.util.Locale.ROOT);
        if(trustedProvider){
            if(!host.equals("kolejemalopolskie.com.pl")&&!host.endsWith(".kolejemalopolskie.com.pl"))
                throw new java.io.IOException("Źródło spoza oficjalnej domeny przewoźnika");
        }else if(!"raw.githubusercontent.com".equals(host))
            throw new java.io.IOException("Nieprawidłowy plik konfiguracji");
        HttpURLConnection http=(HttpURLConnection)uri.openConnection();
        http.setInstanceFollowRedirects(false);
        http.setConnectTimeout(6500);
        http.setReadTimeout(10000);
        http.setRequestProperty("User-Agent","PomocnikAlfa/1.7.54 (GTFS-RT compatibility)");
        http.setRequestProperty("Accept","application/json");
        try {
            if(http.getResponseCode()!=200)throw new java.io.IOException("Źródło HTTP "+http.getResponseCode());
            if(http.getContentLengthLong()>maxBytes)throw new java.io.IOException("Zbyt duży plik LIVE");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(4096);
            try(InputStream input=http.getInputStream()){
                byte[] buffer=new byte[8192];int n,total=0;
                while((n=input.read(buffer))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Przerwano");
                    total+=n;
                    if(total>maxBytes)throw new java.io.IOException("Przekroczono limit danych LIVE");
                    bytes.write(buffer,0,n);
                }
            }
            return bytes.toByteArray();
        }finally{http.disconnect();}
    }
    public static String apply(Context context,List<GtfsNearby.Departure> departures){
        if(departures.isEmpty())return "Brak kursów, dla których można sprawdzić LIVE.";
        String source;
        try{
            JSONObject manifest=new JSONObject(new String(read(CONFIG,MAX_CONFIG_BYTES,false),StandardCharsets.UTF_8));
            if(manifest.optInt("schema",0)!=1)return "Źródło LIVE: niezgodna konfiguracja.";
            source=manifest.optString("mld_gtfs_rt_json","").trim();
        }catch(Exception e){return "Źródło LIVE: konfiguracja chwilowo niedostępna.";}
        if(source.isEmpty())return "MLD LIVE: oczekuje na potwierdzony dostęp do danych przewoźnika.";
        try{
            String payload=new String(read(source,MAX_FEED_BYTES,true),StandardCharsets.UTF_8);
            return applyJson(payload,departures,System.currentTimeMillis());
        }catch(Exception e){
            return "MLD LIVE: brak dostępnych aktualizacji. Wyświetlam odjazdy planowe.";
        }
    }
    /** Accept official GTFS-RT JSON camelCase or snake_case; never approximate trip matching. */
    public static String applyJson(String payload,List<GtfsNearby.Departure> departures,long now)throws Exception{
        JSONObject feed=new JSONObject(payload);
        JSONObject header=feed.optJSONObject("header");
        if(header==null)return "MLD LIVE: brak znacznika aktualności źródła.";
        long headerSecs=header.optLong("timestamp",0L);
        if(!fresh(headerSecs,now))return "MLD LIVE: dane zbyt stare, pokazuję rozkład planowy.";
        JSONArray entities=feed.optJSONArray("entity");
        if(entities==null)return "MLD LIVE: brak zgodnych aktualizacji GTFS-RT.";
        Map<String,List<GtfsNearby.Departure>> byTrip=new HashMap<>();
        for(GtfsNearby.Departure d:departures)
            if(d.tripId!=null && d.serviceDate!=null)
                byTrip.computeIfAbsent(d.tripId+"|"+d.serviceDate,k->new ArrayList<>()).add(d);
        int matches=0;
        for(int i=0;i<entities.length();i++){
            JSONObject e=entities.optJSONObject(i);
            if(e==null||e.optBoolean("isDeleted",false))continue;
            JSONObject update=object(e,"tripUpdate","trip_update");
            if(update==null)continue;
            JSONObject trip=update.optJSONObject("trip");
            if(trip==null)continue;
            String tid=string(trip,"tripId","trip_id"),date=string(trip,"startDate","start_date");
            if(tid.isEmpty()||!date.matches("[0-9]{8}"))continue;
            String rel=string(trip,"scheduleRelationship","schedule_relationship");
            if("CANCELED".equalsIgnoreCase(rel)||"SKIPPED".equalsIgnoreCase(rel))continue;
            List<GtfsNearby.Departure> targets=byTrip.get(tid+"|"+date);
            if(targets==null)continue;
            long updateSecs=update.optLong("timestamp",headerSecs);
            if(!fresh(updateSecs,now))continue;
            JSONArray at=update.optJSONArray("stopTimeUpdate");
            if(at==null)at=update.optJSONArray("stop_time_update");
            if(at==null)continue;
            String route=string(trip,"routeId","route_id");
            for(int j=0;j<at.length();j++){
                JSONObject stop=at.optJSONObject(j);if(stop==null)continue;
                String relation=string(stop,"scheduleRelationship","schedule_relationship");
                if("NO_DATA".equalsIgnoreCase(relation)||"SKIPPED".equalsIgnoreCase(relation))continue;
                JSONObject departure=stop.optJSONObject("departure");
                if(departure==null||!departure.has("delay")||departure.isNull("delay"))continue;
                int seconds=departure.optInt("delay",Integer.MAX_VALUE);
                if(Math.abs((long)seconds)>7200L)continue;
                String stopId=string(stop,"stopId","stop_id");
                int seq=stop.has("stopSequence")?stop.optInt("stopSequence",-1):stop.optInt("stop_sequence",-1);
                for(GtfsNearby.Departure d:targets){
                    if(!route.isEmpty()&&!route.equals(d.routeId))continue;
                    // If the provider supplies both stop_id and stop_sequence, both
                    // must match. Matching only the sequence could put a delay on
                    // another platform when the feed itself names a different stop.
                    if(stopId.isEmpty()&&seq<=0)continue;
                    if(!stopId.isEmpty()&&!stopId.equals(d.stop.id))continue;
                    if(seq>0&&seq!=d.sequence)continue;
                    // Keep the newest correction when a feed includes duplicate
                    // updates for one exact trip/date/stop.
                    if(d.liveReportedAtMillis>=updateSecs*1000L)continue;
                    d.liveDelaySeconds=seconds;
                    d.liveReportedAtMillis=updateSecs*1000L;
                    matches++;
                }
            }
        }
        return matches==0?"MLD LIVE: nie ma potwierdzonych aktualizacji dla pobliskich kursów.":
            "MLD LIVE: "+matches+" odjazdów z potwierdzonymi aktualizacjami.";
    }
    private static boolean fresh(long seconds,long now){
        if(seconds<=0)return false;
        long delta=now-seconds*1000L;
        return delta>=-30000L&&delta<=MAX_AGE_MS;
    }
    private static JSONObject object(JSONObject item,String a,String b){
        JSONObject value=item.optJSONObject(a);
        return value!=null?value:item.optJSONObject(b);
    }
    private static String string(JSONObject o,String a,String b){
        String first=o.optString(a,"");
        return first.isEmpty()?o.optString(b,""):first;
    }
}
