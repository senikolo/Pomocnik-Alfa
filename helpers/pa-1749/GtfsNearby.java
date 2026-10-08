package com.ispina.lokalnie.transit;

import android.content.Context;
import android.location.Location;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * On-demand GTFS stop search and SCHEDULED departures. No background GPS.
 * Data from public / producer GTFS, no inferred departure times or live ETAs.
 */
public final class GtfsNearby {
    private GtfsNearby(){}
    public static final class Stop {
        public String id,name,code;
        public double lat,lon,distance;
    }
    public static final class Departure {
        public Stop stop;
        public String line,headsign,tripId,routeId,mode,serviceDate;
        public long when;
        public int sequence;
        // Populated only when a trustworthy GTFS-RT TripUpdate matches exactly this trip and stop.
        // Never infer punctuality or delays from GPS position alone.
        public Integer liveDelaySeconds;
        public long liveReportedAtMillis;
        public Integer confirmedDelayMinutes(long nowMillis){
            if(liveDelaySeconds==null || liveReportedAtMillis<=0 ||
               liveReportedAtMillis>nowMillis+30000L ||
               nowMillis-liveReportedAtMillis>120000L ||
               Math.abs(liveDelaySeconds)>7200)return null;
            return (int)Math.round(liveDelaySeconds/60.0);
        }
        public final List<String> following=new ArrayList<>();
    }
    public static final class Result {
        public final List<Stop> stops=new ArrayList<>();
        public final List<Departure> departures=new ArrayList<>();
        public String source,feedName,note,liveNote;
        public boolean oldData;
        public long downloadedAt;
    }
    private static class Trip {
        String route,headsign,service;
        Trip(String r,String h,String s){route=r;headsign=h;service=s;}
    }
    private static class Route {
        String name, mode;
        Route(String n,String m){name=n;mode=m;}
    }
    /** GTFS route_type drives grouping. Prefix fallback supports older cached lite feeds. */
    public static String modeFor(String type, String line, String provider){
        String l=line==null?"":line.trim().toUpperCase(Locale.ROOT);
        int t=parseInt(type,-1);
        if(t==0 || (t>=900&&t<=906))return "tram";
        if(t==1 || (t>=400&&t<=405))return "metro";
        if(t==2 || (t>=100&&t<=117))return "skm";
        if(t==3 || (t>=700&&t<=716))return "bus";
        if("mld".equals(provider))return "bus";
        if(l.matches("S(1|2|3|4|40)"))return "skm";
        if(l.matches("M[12]"))return "metro";
        if(l.matches("([1-9]|[1-9][0-9])"))return "tram";
        return "bus";
    }
    public static String passengerStopNumber(String stopId,String publishedCode,String provider){
        String code=clean(publishedCode);
        if(!code.isEmpty())return code;
        // Warsaw bus/tram: 4-digit stop complex + 2-digit platform (e.g. 700901 -> 01).
        if("warsaw".equals(provider)&&stopId!=null&&stopId.matches("[0-9]{6}"))
            return stopId.substring(4);
        return "";
    }
    private static final ZoneId ZONE=ZoneId.of("Europe/Warsaw");
    private static final String BASE="https://raw.githubusercontent.com/senikolo/Pomocnik-Alfa/main/data/pa-gtfs/";
    private static final long MAX_BYTES=29L*1024L*1024L;
    private static final long FRESH=21L*3600000L;
    private static String clean(String s){return s==null?"":s.trim().replace("\ufeff","");}
    public static String networkFor(double lat,double lon){
        if(lat>=51.9&&lat<=52.65&&lon>=20.45&&lon<=21.6)return "warsaw";
        if(lat>=48.9&&lat<=50.85&&lon>=18.8&&lon<=21.85)return "mld";
        return "";
    }
    private static File feed(Context ctx,String provider,Result out)throws Exception{
        File dir=new File(ctx.getCacheDir(),"pa_gtfs");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Brak miejsca na rozkłady");
        File dest=new File(dir,(provider.equals("mld")?"mld-rt-v2":provider)+"-lite.zip");
        out.downloadedAt=dest.lastModified();
        if(dest.isFile()&&dest.length()>10000&&System.currentTimeMillis()-dest.lastModified()<FRESH)return dest;
        File temp=new File(dir,provider+".download");
        HttpURLConnection c=null;
        try{
            URL url=new URL(BASE+provider+"-lite.zip");
            c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(9000);c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent","PomocnikAlfa/1.7.49 (+https://github.com/senikolo/Pomocnik-Alfa)");
            if(c.getResponseCode()!=200)throw new IOException("Rozkład chwilowo niedostępny (HTTP "+c.getResponseCode()+")");
            long count=0;
            try(InputStream in=new BufferedInputStream(c.getInputStream());OutputStream os=new BufferedOutputStream(new FileOutputStream(temp))){
                byte[] buf=new byte[32768];int n;
                while((n=in.read(buf))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Pobieranie anulowane");
                    count+=n;if(count>MAX_BYTES)throw new IOException("Rozkład przekroczył limit");
                    os.write(buf,0,n);
                }
            }
            try(ZipFile z=new ZipFile(temp)){
                if(z.getEntry("stops.txt")==null||z.getEntry("trips.txt")==null||z.getEntry("stop_times.txt")==null)
                    throw new IOException("Brak wymaganych tablic rozkładu");
            }
            if(dest.exists()&&!dest.delete())throw new IOException("Nie można odświeżyć rozkładu");
            if(!temp.renameTo(dest))throw new IOException("Nie można zapisać rozkładu");
            out.downloadedAt=System.currentTimeMillis();
            return dest;
        }catch(Exception e){
            if(dest.isFile()&&dest.length()>10000&&System.currentTimeMillis()-dest.lastModified()<72L*3600000L){
                out.oldData=true;out.downloadedAt=dest.lastModified();return dest;
            }
            throw e;
        }finally{if(c!=null)c.disconnect();if(temp.exists())temp.delete();}
    }
    private static List<String> csv(String line){
        ArrayList<String> out=new ArrayList<>();StringBuilder b=new StringBuilder();boolean quoted=false;
        for(int i=0;i<line.length();i++){
            char ch=line.charAt(i);
            if(ch=='"'){if(quoted&&i+1<line.length()&&line.charAt(i+1)=='"'){b.append('"');i++;}else quoted=!quoted;}
            else if(ch==','&&!quoted){out.add(b.toString());b.setLength(0);}
            else b.append(ch);
        }
        out.add(b.toString());return out;
    }
    private static class Rows implements Closeable {
        BufferedReader r;Map<String,Integer> col=new HashMap<>();
        Rows(ZipFile zip,String file)throws IOException{
            ZipEntry e=zip.getEntry(file);if(e==null)return;
            r=new BufferedReader(new InputStreamReader(zip.getInputStream(e),StandardCharsets.UTF_8),32768);
            String head=r.readLine();if(head==null)return;
            List<String> names=csv(head);
            for(int i=0;i<names.size();i++)col.put(clean(names.get(i)),i);
        }
        List<String> next()throws IOException{if(r==null)return null;String line=r.readLine();return line==null?null:csv(line);}
        String s(List<String> values,String key){
            Integer i=col.get(key);return i!=null&&i<values.size()?clean(values.get(i)):"";
        }
        @Override public void close()throws IOException{if(r!=null)r.close();}
    }
    private static int parseInt(String s,int fallback){try{return Integer.parseInt(s);}catch(Exception e){return fallback;}}
    private static double decimal(String s){try{return Double.parseDouble(s);}catch(Exception e){return Double.NaN;}}
    private static int time(String time){
        String[] a=time.split(":");if(a.length<2)return -1;
        int h=parseInt(a[0],-1),m=parseInt(a[1],-1),s=a.length>=3?parseInt(a[2],0):0;
        if(h<0||h>47||m<0||m>59||s<0||s>59)return -1;
        return h*3600+m*60+s;
    }
    private static boolean activeByWeek(String[] row,Rows csv,LocalDate date){
        String start=csv.s(Arrays.asList(row),"start_date"),end=csv.s(Arrays.asList(row),"end_date");
        String d=date.toString().replace("-","");
        if(!start.isEmpty()&&d.compareTo(start)<0)return false;
        if(!end.isEmpty()&&d.compareTo(end)>0)return false;
        String key=date.getDayOfWeek().name().toLowerCase(Locale.ROOT);
        return "1".equals(csv.s(Arrays.asList(row),key));
    }
    private static Set<String> active(ZipFile z,LocalDate day)throws IOException{
        Set<String> result=new HashSet<>();
        String date=day.toString().replace("-","");
        try(Rows csv=new Rows(z,"calendar.txt")){
            List<String> r;while((r=csv.next())!=null){
                if(activeByWeek(r.toArray(new String[0]),csv,day))result.add(csv.s(r,"service_id"));
            }
        }
        try(Rows csv=new Rows(z,"calendar_dates.txt")){
            List<String> r;while((r=csv.next())!=null){
                if(!date.equals(csv.s(r,"date")))continue;
                String sid=csv.s(r,"service_id");
                if("1".equals(csv.s(r,"exception_type")))result.add(sid);
                if("2".equals(csv.s(r,"exception_type")))result.remove(sid);
            }
        }
        return result;
    }
    private static void checkCancelled()throws InterruptedIOException{
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Sprawdzanie rozkładu anulowane");
    }
    private static void collectRoutes(ZipFile z,Map<String,Route> routes,String provider)throws IOException{
        try(Rows csv=new Rows(z,"routes.txt")){
            List<String> r;while((r=csv.next())!=null){
                String id=csv.s(r,"route_id"),name=csv.s(r,"route_short_name");
                if(name.isEmpty())name=csv.s(r,"route_long_name");
                if(name.isEmpty())name=id;
                routes.put(id,new Route(name,modeFor(csv.s(r,"route_type"),name,provider)));
            }
        }
    }
    public static Result search(Context ctx,double lat,double lon)throws Exception{
        String provider=networkFor(lat,lon);
        if(provider.isEmpty())throw new IOException("Obszar poza obsługiwanymi rozkładami. Obsługiwane sieci: Warszawa WTP i Małopolskie Linie Dowozowe.");
        Result result=new Result();
        result.feedName=provider.equals("warsaw")?"Warszawski Transport Publiczny":"Małopolskie Linie Dowozowe";
        result.source=provider.equals("warsaw")?"WarsawGTFS · dane z WTP/ZTM":"Koleje Małopolskie · GTFS MLD";
        File archive=feed(ctx,provider,result);
        ZonedDateTime now=ZonedDateTime.now(ZONE);
        LocalDate today=now.toLocalDate(),previous=today.minusDays(1);
        long moment=now.toInstant().toEpochMilli();
        Map<String,Stop> allStops=new HashMap<>();
        Map<String,String> stopNames=new HashMap<>();
        try(ZipFile z=new ZipFile(archive)){
            try(Rows csv=new Rows(z,"stops.txt")){
                List<String> r;int lines=0;
                while((r=csv.next())!=null){
                    if((++lines&8191)==0)checkCancelled();
                    String id=csv.s(r,"stop_id"),name=csv.s(r,"stop_name");
                    double slat=decimal(csv.s(r,"stop_lat")),slon=decimal(csv.s(r,"stop_lon"));
                    if(id.isEmpty()||name.isEmpty()||!Double.isFinite(slat)||!Double.isFinite(slon))continue;
                    stopNames.put(id,name);
                    float[] distance=new float[1];
                    Location.distanceBetween(lat,lon,slat,slon,distance);
                    boolean railNode=provider.equals("warsaw")&&id.matches("[0-9]{4}");
                    if(distance[0]>(railNode?4300:1900))continue;
                    Stop stop=new Stop();stop.id=id;stop.name=name;stop.lat=slat;stop.lon=slon;stop.distance=distance[0];
                    stop.code=passengerStopNumber(id,csv.s(r,"stop_code"),provider);
                    allStops.put(id,stop);
                }
            }
            List<Stop> near=new ArrayList<>(allStops.values());
            near.sort(Comparator.comparingDouble(s->s.distance));
            // Five nearby named stop complexes, up to four platforms within each.
            // Rail stations are retained separately to keep SKM available.
            Map<String,Integer> platformsByName=new LinkedHashMap<>();
            int railStations=0;
            for(Stop stop:near){
                boolean railNode=provider.equals("warsaw")&&stop.id.matches("[0-9]{4}");
                if(railNode){
                    if(railStations>=4||stop.distance>4300)continue;
                    railStations++;
                    result.stops.add(stop);
                    continue;
                }
                if(stop.distance>1900)continue;
                String key=stop.name.toLowerCase(Locale.ROOT);
                int posts=platformsByName.getOrDefault(key,0);
                if(posts>=4 || (posts==0 && platformsByName.size()>=5))continue;
                platformsByName.put(key,posts+1);
                result.stops.add(stop);
            }
            if(result.stops.isEmpty()){result.note="Nie znaleziono przystanków tej sieci w promieniu 1,9 km.";return result;}
            Map<String,Stop> chosen=new HashMap<>();
            for(Stop stop:result.stops)chosen.put(stop.id,stop);
            Set<String> dayServices=active(z,today),previousServices=active(z,previous),nextServices=active(z,today.plusDays(1));
            Set<String> services=new HashSet<>(dayServices);services.addAll(previousServices);services.addAll(nextServices);
            Map<String,Route> routes=new HashMap<>();collectRoutes(z,routes,provider);
            Map<String,Trip> trips=new HashMap<>();
            try(Rows csv=new Rows(z,"trips.txt")){
                List<String> r;int lines=0;
                while((r=csv.next())!=null){
                    if((++lines&16383)==0)checkCancelled();
                    String service=csv.s(r,"service_id");
                    if(services.contains(service))trips.put(csv.s(r,"trip_id"),
                         new Trip(csv.s(r,"route_id"),csv.s(r,"trip_headsign"),service));
                }
            }
            if(services.isEmpty()){result.note="Brak potwierdzonych kursów na dzisiejszy dzień w pobranych danych GTFS.";return result;}
            try(Rows csv=new Rows(z,"stop_times.txt")){
                List<String> r;int lines=0;
                while((r=csv.next())!=null){
                    if((++lines&32767)==0)checkCancelled();
                    Stop stop=chosen.get(csv.s(r,"stop_id"));if(stop==null)continue;
                    String tid=csv.s(r,"trip_id");Trip trip=trips.get(tid);if(trip==null)continue;
                    int seconds=time(csv.s(r,"departure_time"));if(seconds<0)continue;
                    for(int dayOffset=-1;dayOffset<=1;dayOffset++){
                        if(dayOffset==0&&!dayServices.contains(trip.service))continue;
                        if(dayOffset==-1&&!previousServices.contains(trip.service))continue;
                        if(dayOffset==1&&!nextServices.contains(trip.service))continue;
                        LocalDate date=today.plusDays(dayOffset);
                        long departure=date.atStartOfDay(ZONE).plusSeconds(seconds).toInstant().toEpochMilli();
                        if(departure<moment-120000L||departure>moment+120L*60000L)continue;
                        Departure item=new Departure();
                        Route route=routes.get(trip.route);
                        item.stop=stop;item.tripId=tid;item.routeId=trip.route;
                        item.line=route==null?trip.route:route.name;
                        item.mode=route==null?"bus":route.mode;
                        item.headsign=trip.headsign.isEmpty()?"Kierunek według rozkładu":trip.headsign;
                        item.when=departure;item.serviceDate=date.toString().replace("-","");
                        item.sequence=parseInt(csv.s(r,"stop_sequence"),-1);
                        result.departures.add(item);
                    }
                }
            }
            result.departures.sort(Comparator.comparingLong(d->d.when));
            // Do not allow plentiful bus courses to hide the tram/SKM sections.
            Map<String,Integer> byMode=new HashMap<>();
            Iterator<Departure> cursor=result.departures.iterator();
            while(cursor.hasNext()){
                Departure d=cursor.next();
                int n=byMode.getOrDefault(d.mode,0);
                if(n>=30)cursor.remove(); else byMode.put(d.mode,n+1);
            }
            // One extra pass through stop_times for short, genuinely scheduled stop lists.
            Map<String,List<Departure>> wanted=new HashMap<>();
            for(Departure d:result.departures){
                if(wanted.size()>28)break;
                List<Departure> ls=wanted.get(d.tripId);
                if(ls==null){ls=new ArrayList<>();wanted.put(d.tripId,ls);}
                ls.add(d);
            }
            try(Rows csv=new Rows(z,"stop_times.txt")){
                List<String> r;int lines=0;
                while((r=csv.next())!=null){
                    if((++lines&32767)==0)checkCancelled();
                    List<Departure> ls=wanted.get(csv.s(r,"trip_id"));if(ls==null)continue;
                    int sequence=parseInt(csv.s(r,"stop_sequence"),-1);
                    String id=csv.s(r,"stop_id");
                    String next=stopNames.get(id);
                    if(next==null)continue;
                    for(Departure d:ls)if(sequence>=d.sequence && d.following.size()<8)d.following.add(next);
                }
            }
        }
        if(provider.equals("mld")){
            // Network request only after explicit nearby-departures tap on the worker thread.
            result.liveNote=MldRealtime.apply(ctx,result.departures);
        }else result.liveNote="WTP: brak zweryfikowanego źródła prognoz minutowych dla tych kursów.";
        if(result.departures.isEmpty())result.note="Brak kursów w ciągu najbliższych 120 minut na wybranych przystankach. To odjazdy planowe.";
        else result.note="Godziny planowe, nie na żywo. Upewnij się, że rozkład nadal obowiązuje.";
        return result;
    }

    /** A stop/platform on a selected line; supports discovery without GPS. */
    public static final class LineStop {
        public String id,name,code;
        public int sequence;
        public String label(){
            return name+(code==null||code.isEmpty()?"":" · stan. "+code);
        }
    }
    private static boolean manualProvider(String provider){
        return "warsaw".equals(provider)||"mld".equals(provider);
    }
    private static String manualLine(String text)throws IOException{
        String line=clean(text).toUpperCase(Locale.ROOT);
        if(!line.matches("[A-Z0-9]{1,8}"))
            throw new IOException("Wpisz prawidłowy numer linii, np. 517, A7, S1.");
        return line;
    }
    private static Set<String> routeIdsForLine(ZipFile z,String provider,String line)throws Exception{
        Set<String> found=new HashSet<>();
        try(Rows rows=new Rows(z,"routes.txt")){
            List<String> row;
            while((row=rows.next())!=null){
                String name=rows.s(row,"route_short_name");
                if(name.isEmpty())name=rows.s(row,"route_long_name");
                if(line.equalsIgnoreCase(name))found.add(rows.s(row,"route_id"));
            }
        }
        return found;
    }
    private static Map<String,Stop> loadManualStops(ZipFile z,String provider)throws Exception{
        Map<String,Stop> all=new HashMap<>();
        try(Rows rows=new Rows(z,"stops.txt")){
            List<String> row;int n=0;
            while((row=rows.next())!=null){
                if((++n&8191)==0)checkCancelled();
                String id=rows.s(row,"stop_id"),name=rows.s(row,"stop_name");
                if(id.isEmpty()||name.isEmpty())continue;
                Stop stop=new Stop();
                stop.id=id;stop.name=name;
                stop.code=passengerStopNumber(id,rows.s(row,"stop_code"),provider);
                all.put(id,stop);
            }
        }
        return all;
    }
    public static List<LineStop> lineStops(Context ctx,String provider,String entered)throws Exception{
        if(!manualProvider(provider))throw new IOException("Wybierz Warszawę albo MLD.");
        String line=manualLine(entered);
        Result data=new Result();
        List<LineStop> output=new ArrayList<>();
        try(ZipFile z=new ZipFile(feed(ctx,provider,data))){
            Set<String> routeIds=routeIdsForLine(z,provider,line);
            if(routeIds.isEmpty())return output;
            Set<String> trips=new HashSet<>();
            try(Rows rows=new Rows(z,"trips.txt")){
                List<String> row;int n=0;
                while((row=rows.next())!=null){
                    if((++n&16383)==0)checkCancelled();
                    if(routeIds.contains(rows.s(row,"route_id")))
                        trips.add(rows.s(row,"trip_id"));
                }
            }
            if(trips.isEmpty())return output;
            Map<String,Integer> positions=new HashMap<>();
            try(Rows rows=new Rows(z,"stop_times.txt")){
                List<String> row;int n=0;
                while((row=rows.next())!=null){
                    if((++n&32767)==0)checkCancelled();
                    if(!trips.contains(rows.s(row,"trip_id")))continue;
                    String id=rows.s(row,"stop_id");
                    int seq=parseInt(rows.s(row,"stop_sequence"),Integer.MAX_VALUE);
                    Integer old=positions.get(id);
                    if(old==null||seq<old)positions.put(id,seq);
                }
            }
            Map<String,Stop> all=loadManualStops(z,provider);
            for(Map.Entry<String,Integer> entry:positions.entrySet()){
                Stop stop=all.get(entry.getKey());if(stop==null)continue;
                LineStop item=new LineStop();
                item.id=stop.id;item.name=stop.name;item.code=stop.code;
                item.sequence=entry.getValue();
                output.add(item);
            }
        }
        output.sort(Comparator.comparingInt((LineStop d)->d.sequence)
            .thenComparing(d->d.name,java.text.Collator.getInstance(new Locale("pl","PL")))
            .thenComparing(d->d.code==null?"":d.code));
        return output;
    }
    /** All upcoming scheduled departures for an explicitly selected line and platform.
     * Includes service calendars, overnight trips and verified LIVE only if available.
     */
    public static Result lineStopDepartures(Context ctx,String provider,String entered,String stopId)
            throws Exception{
        if(!manualProvider(provider))throw new IOException("Wybierz Warszawę albo MLD.");
        String line=manualLine(entered);
        if(stopId==null||stopId.isEmpty())throw new IOException("Wybierz przystanek.");
        Result out=new Result();
        out.feedName="warsaw".equals(provider)?"Warszawski Transport Publiczny":"Małopolskie Linie Dowozowe";
        out.source=provider.equals("warsaw")?"WarsawGTFS · ZTM/WTP":"GTFS MLD · Koleje Małopolskie";
        File file=feed(ctx,provider,out);
        ZonedDateTime now=ZonedDateTime.now(ZONE);
        LocalDate today=now.toLocalDate(),previous=today.minusDays(1),next=today.plusDays(1);
        long at=now.toInstant().toEpochMilli();
        try(ZipFile z=new ZipFile(file)){
            Set<String> routes=routeIdsForLine(z,provider,line);
            if(routes.isEmpty()){out.note="Linia nie występuje w pobranym rozkładzie.";return out;}
            Map<String,Stop> allStops=loadManualStops(z,provider);
            Stop stop=allStops.get(stopId);
            if(stop==null){out.note="Wybrany przystanek nie występuje w rozkładzie.";return out;}
            out.stops.add(stop);
            Set<String> todayServices=active(z,today);
            Set<String> previousServices=active(z,previous);
            Set<String> nextServices=active(z,next);
            Set<String> services=new HashSet<>(todayServices);
            services.addAll(previousServices);services.addAll(nextServices);
            Map<String,Trip> trips=new HashMap<>();
            try(Rows rows=new Rows(z,"trips.txt")){
                List<String> row;int n=0;
                while((row=rows.next())!=null){
                    if((++n&16383)==0)checkCancelled();
                    String route=rows.s(row,"route_id");
                    String service=rows.s(row,"service_id");
                    if(!routes.contains(route)||!services.contains(service))continue;
                    trips.put(rows.s(row,"trip_id"),
                        new Trip(route,rows.s(row,"trip_headsign"),service));
                }
            }
            Map<String,Route> routeInfo=new HashMap<>();
            collectRoutes(z,routeInfo,provider);
            try(Rows rows=new Rows(z,"stop_times.txt")){
                List<String> row;int n=0;
                while((row=rows.next())!=null){
                    if((++n&32767)==0)checkCancelled();
                    if(!stopId.equals(rows.s(row,"stop_id")))continue;
                    String tripId=rows.s(row,"trip_id");
                    Trip trip=trips.get(tripId);if(trip==null)continue;
                    int seconds=time(rows.s(row,"departure_time"));if(seconds<0)continue;
                    for(int offset=-1;offset<=1;offset++){
                        if(offset==-1&&!previousServices.contains(trip.service))continue;
                        if(offset==0&&!todayServices.contains(trip.service))continue;
                        if(offset==1&&!nextServices.contains(trip.service))continue;
                        LocalDate day=today.plusDays(offset);
                        long when=day.atStartOfDay(ZONE).plusSeconds(seconds).toInstant().toEpochMilli();
                        if(when<at-60000L||when>at+120L*60000L)continue;
                        Departure d=new Departure();
                        Route route=routeInfo.get(trip.route);
                        d.stop=stop;d.tripId=tripId;d.routeId=trip.route;
                        d.line=line;
                        d.headsign=trip.headsign.isEmpty()?"Kierunek według rozkładu":trip.headsign;
                        d.mode=route==null?"bus":route.mode;
                        d.when=when;d.serviceDate=day.toString().replace("-","");
                        d.sequence=parseInt(rows.s(row,"stop_sequence"),-1);
                        out.departures.add(d);
                    }
                }
            }
            out.departures.sort(Comparator.comparingLong(d->d.when));
            if(out.departures.size()>30)
                out.departures.subList(30,out.departures.size()).clear();
            if(!out.departures.isEmpty()){
                Map<String,List<Departure>> wanted=new HashMap<>();
                for(Departure d:out.departures)
                    wanted.computeIfAbsent(d.tripId,k->new ArrayList<>()).add(d);
                try(Rows rows=new Rows(z,"stop_times.txt")){
                    List<String> row;int n=0;
                    while((row=rows.next())!=null){
                        if((++n&32767)==0)checkCancelled();
                        List<Departure> matching=wanted.get(rows.s(row,"trip_id"));
                        if(matching==null)continue;
                        String name=allStops.containsKey(rows.s(row,"stop_id"))?
                            allStops.get(rows.s(row,"stop_id")).name:"";
                        int seq=parseInt(rows.s(row,"stop_sequence"),-1);
                        if(name.isEmpty())continue;
                        for(Departure d:matching)
                            if(seq>=d.sequence&&d.following.size()<8)d.following.add(name);
                    }
                }
            }
        }
        if(provider.equals("mld"))out.liveNote=MldRealtime.apply(ctx,out.departures);
        else out.liveNote="WTP: godziny planowe; brak zweryfikowanego źródła prognoz dla tej linii.";
        if(out.departures.isEmpty())
            out.note="Brak najbliższych kursów tej linii z wybranego stanowiska w ciągu dwóch godzin.";
        else out.note="Wyniki pochodzą z rozkładu GTFS. Aktualizacje LIVE tylko po potwierdzeniu źródła.";
        return out;
    }
}
