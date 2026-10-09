package com.ispina.lokalnie.transit;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Warsaw GPS-derived ETA: live position + matching scheduled trip + along-route
 * interpolation. This is NOT an operator-confirmed GTFS-RT TripUpdate.
 * Each estimate is explicitly marked "szacunek GPS LIVE" in passenger UI.
 * Fail closed on ambiguous routes, stale positions or off-route locations.
 */
public final class WarsawGpsEta {
    private WarsawGpsEta(){}
    public static final String URL_POSITIONS="https://mkuran.pl/gtfs/warsaw/positions.json";
    private static final long FRESH_MS=120000L;
    private static final int MAX_FEED=2*1024*1024;
    private static final int MAX_INDEX_ROWS=100000;
    private static final ZoneId ZONE=ZoneId.of("Europe/Warsaw");

    private static final class Point {
        int sequence,seconds;
        double lat,lon;
    }
    private static final class Path {
        String trip,line,date,start;
        final List<Point> points=new ArrayList<>();
    }
    public static final class Result {
        public int estimated;
        public int candidates;
        public int freshVehicles;
        public String note;
        public long fetchedAt;
    }
    /** Numeric geometry kept side-effect free for JVM unit tests. */
    public static final class Match {
        public final double distance;
        public final long plannedTimeAtPosition;
        public final int forwardSequence;
        Match(double d,long t,int s){distance=d;plannedTimeAtPosition=t;forwardSequence=s;}
    }
    public static Match locate(List<double[]> points,double lat,double lon,
                               long midnightMillis,long gpsMillis){
        Match winner=null;double bestScore=Double.MAX_VALUE;
        for(int i=1;i<points.size();i++){
            double[] a=points.get(i-1),b=points.get(i);
            if(a.length<4||b.length<4)continue;
            // Local equirectangular meters around the GPS location.
            double cos=Math.cos(Math.toRadians(lat));
            double ax=(a[1]-lon)*111320*cos,ay=(a[0]-lat)*111320;
            double bx=(b[1]-lon)*111320*cos,by=(b[0]-lat)*111320;
            double vx=bx-ax,vy=by-ay;
            double len2=vx*vx+vy*vy;
            if(len2<1)continue;
            double frac=Math.max(0,Math.min(1,-(ax*vx+ay*vy)/len2));
            double x=ax+frac*vx,y=ay+frac*vy;
            double distance=Math.hypot(x,y);
            if(distance>230)continue; // prevent wrong-side-road false matches
            long when=midnightMillis+Math.round((a[2]+frac*(b[2]-a[2]))*1000.0);
            long delta=gpsMillis-when;
            if(delta< -12*60000L||delta> 30*60000L)continue;
            // Minor schedule proximity tie-break prevents wrong leg on circular lines.
            double score=distance+Math.min(300,Math.abs(delta)/60000.0*1.5);
            if(score<bestScore){
                bestScore=score;
                winner=new Match(distance,when,(int)Math.round(b[3]));
            }
        }
        return winner;
    }
    private static Map<String,List<Path>> loadPaths(Context ctx,List<GtfsNearby.Departure> departures)
            throws IOException{
        Set<String> wanted=new HashSet<>();
        for(GtfsNearby.Departure d:departures)
            if(d.tripId!=null && !d.tripId.isEmpty())wanted.add(d.tripId);
        Map<String,List<Path>> out=new HashMap<>();
        if(wanted.isEmpty())return out;
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(
                new GZIPInputStream(ctx.getAssets().open("pa_gtfs/warsaw-gps-paths.idx")),
                StandardCharsets.UTF_8))){
            String line;int rows=0;
            while((line=reader.readLine())!=null){
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Anulowano odczyt");
                if(++rows>MAX_INDEX_ROWS)throw new IOException("Indeks GPS przekroczył limit");
                String[] parts=line.split("\t",5);
                if(parts.length!=5 || !wanted.contains(parts[0]))continue;
                Path path=new Path();
                path.trip=parts[0];path.line=parts[1];path.date=parts[2];path.start=parts[3];
                try{
                    for(String chunk:parts[4].split("\\|")){
                        String[] p=chunk.split(",");
                        if(p.length!=5)continue;
                        Point point=new Point();
                        point.sequence=Integer.parseInt(p[0]);
                        point.lat=Double.parseDouble(p[1]);
                        point.lon=Double.parseDouble(p[2]);
                        point.seconds=Integer.parseInt(p[3]);
                        path.points.add(point);
                    }
                }catch(NumberFormatException ignored){continue;}
                if(path.points.size()<2)continue;
                String key=path.date+"|"+path.line+"|"+path.start;
                out.computeIfAbsent(key,k->new ArrayList<>()).add(path);
            }
        }
        return out;
    }
    private static byte[] fetch()throws IOException{
        HttpURLConnection c=(HttpURLConnection)new URL(URL_POSITIONS).openConnection();
        c.setConnectTimeout(6000);c.setReadTimeout(8000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("User-Agent","PomocnikAlfa/1.7.63 GPS-ETA");
        try{
            if(c.getResponseCode()!=200)throw new IOException("Źródło pozycji GPS HTTP "+c.getResponseCode());
            if(c.getContentLengthLong()>MAX_FEED)throw new IOException("Zbyt duży plik pozycji GPS");
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){
                byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))>=0){
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Anulowano GPS");
                    if(out.size()+n>MAX_FEED)throw new IOException("Przekroczony limit danych GPS");
                    out.write(buffer,0,n);
                }
            }
            return out.toByteArray();
        }finally{c.disconnect();}
    }
    private static long millis(String text){
        try{return OffsetDateTime.parse(text).toInstant().toEpochMilli();}
        catch(Exception ignored){return 0;}
    }
    private static boolean fresh(long ts,long now){
        return ts>0&&ts<=now+30000L&&now-ts<=FRESH_MS;
    }
    /**
     * Populates gpsEtaWhenMillis ONLY if live trip matches one unique physical
     * schedule, map distance within 230m and the vehicle has not passed the stop.
     */
    public static Result apply(Context ctx,List<GtfsNearby.Departure> departures)throws Exception{
        Result result=new Result();result.fetchedAt=System.currentTimeMillis();
        if(departures.isEmpty()){result.note="Brak odjazdów.";return result;}
        Map<String,List<Path>> paths=loadPaths(ctx,departures);
        if(paths.isEmpty()){result.note="Brak dopasowanych kursów w rozkładzie.";return result;}
        return applyJson(new String(fetch(),StandardCharsets.UTF_8),paths,departures,
                System.currentTimeMillis());
    }
    private static Result applyJson(String json,Map<String,List<Path>> paths,
                                   List<GtfsNearby.Departure> departures,long now)throws Exception{
        JSONObject root=new JSONObject(json);
        long feedAt=millis(root.optString("time",""));
        if(!fresh(feedAt,now))throw new IOException("Brak świeżego źródła GPS");
        JSONArray positions=root.optJSONArray("positions");
        if(positions==null)throw new IOException("Brak listy pojazdów");
        Result result=new Result();result.fetchedAt=feedAt;
        Map<String,List<GtfsNearby.Departure>> grouped=new HashMap<>();
        for(GtfsNearby.Departure d:departures)
            if(d.tripId!=null)grouped.computeIfAbsent(d.tripId,k->new ArrayList<>()).add(d);
        for(int i=0;i<positions.length();i++){
            JSONObject v=positions.optJSONObject(i);
            if(v==null)continue;
            long stamp=millis(v.optString("timestamp",""));
            if(!fresh(stamp,now))continue;
            result.freshVehicles++;
            String ref=v.optString("trip_id","");
            String[] tokens=ref.split(":");
            if(tokens.length<5)continue;
            String date=tokens[0],line=tokens[1],start=tokens[tokens.length-1];
            if(!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")||
               !start.matches("[0-9]{4}"))continue;
            List<Path> candidates=paths.get(date+"|"+line+"|"+start);
            if(candidates==null||candidates.isEmpty())continue;
            double lat=v.optDouble("lat",Double.NaN),lon=v.optDouble("lon",Double.NaN);
            if(!Double.isFinite(lat)||!Double.isFinite(lon)||lat<51.5||lat>53||lon<20||lon>22)
                continue;
            long midnight=LocalDate.parse(date).atStartOfDay(ZONE).toInstant().toEpochMilli();
            Path winner=null;Match best=null;double bestScore=Double.MAX_VALUE,second=Double.MAX_VALUE;
            for(Path path:candidates){
                List<double[]> linePoints=new ArrayList<>();
                for(Point p:path.points)
                    linePoints.add(new double[]{p.lat,p.lon,p.seconds,p.sequence});
                Match candidate=locate(linePoints,lat,lon,midnight,stamp);
                if(candidate==null)continue;
                double distance=candidate.distance;
                if(distance<bestScore){
                    second=bestScore;winner=path;best=candidate;bestScore=distance;
                }else second=Math.min(second,distance);
            }
            // Multiple runs with indistinguishable geometry cannot be assigned.
            if(winner==null||second-bestScore<50)continue;
            List<GtfsNearby.Departure> matched=grouped.get(winner.trip);
            if(matched==null)continue;
            result.candidates++;
            long delay=stamp-best.plannedTimeAtPosition;
            for(GtfsNearby.Departure d:matched){
                if(d.sequence<best.forwardSequence)continue;
                long eta=d.when+delay;
                if(eta<now-20000L || eta>now+90L*60000L)continue;
                if(d.gpsEtaSeenAt>=stamp)continue;
                d.gpsEtaWhenMillis=eta;
                d.gpsEtaSeenAt=stamp;
                result.estimated++;
            }
        }
        result.note=result.estimated>0?
            "GPS LIVE: "+result.estimated+" szacowanych odjazdów na podstawie pozycji pojazdów.":
            "Brak pewnego dopasowania pojazdu do konkretnego kursu; pozostaje rozkład.";
        return result;
    }
}
