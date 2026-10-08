package com.ispina.lokalnie.transit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Public GTFS-RT VehiclePositions for Warsaw, NOT a TripUpdates/ETA feed.
 * Source: mkuran.pl/gtfs/warsaw/vehicles.pb (City of Warsaw open data).
 * No phone location or API token is sent to the provider.
 */
public final class WarsawVehicleGps {
    private WarsawVehicleGps() {}
    public static final String SOURCE = "https://mkuran.pl/gtfs/warsaw/vehicles.pb";
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final long FRESH_MS = 120000L;

    public static final class Snapshot {
        public int vehicles;
        public double nearestMeters = Double.NaN;
        public long feedTimeMillis;
        public long latestVehicleMillis;
        public String sourceName = "Miasto Stołeczne Warszawa • mkuran.pl";
    }
    private static final class Position {
        String route = "";
        float lat = Float.NaN, lon = Float.NaN;
        long timestampMillis;
    }
    private static final class Reader {
        final byte[] bytes;
        int p, end;
        Reader(byte[] bytes, int start, int end) { this.bytes=bytes; this.p=start; this.end=end; }
        boolean hasNext(){return p<end;}
        long varint() throws IOException {
            long value=0;
            for(int shift=0;shift<64;shift+=7){
                if(p>=end)throw new IOException("Uszkodzony strumień GTFS");
                int b=bytes[p++] & 255;
                value|=(long)(b&127)<<shift;
                if((b&128)==0)return value;
            }
            throw new IOException("Niepoprawny varint");
        }
        Reader child() throws IOException {
            long length=varint();
            if(length<0 || length>end-p)throw new IOException("Zbyt długa wiadomość GTFS");
            Reader r=new Reader(bytes,p,p+(int)length);
            p+=(int)length;
            return r;
        }
        int fixed32() throws IOException {
            if(end-p<4)throw new IOException("Niepoprawny float");
            int v=(bytes[p]&255)|((bytes[p+1]&255)<<8)|((bytes[p+2]&255)<<16)|((bytes[p+3]&255)<<24);
            p+=4;return v;
        }
        String string() throws IOException {
            Reader c=child();
            return new String(bytes,c.p,c.end-c.p,java.nio.charset.StandardCharsets.UTF_8);
        }
        void skip(int wire) throws IOException {
            switch(wire){
                case 0:varint();return;
                case 1:if(end-p<8)throw new IOException("Niepoprawny field64");p+=8;return;
                case 2:child();return;
                case 5:fixed32();return;
                default:throw new IOException("Nieznany typ pola GTFS");
            }
        }
    }
    private static long header(Reader reader) throws IOException {
        long stamp=0;
        while(reader.hasNext()){
            long tag=reader.varint();int f=(int)(tag>>>3),w=(int)(tag&7);
            if(f==3 && w==0)stamp=reader.varint();
            else reader.skip(w);
        }
        return stamp*1000L;
    }
    private static void trip(Reader reader,Position out) throws IOException {
        while(reader.hasNext()){
            long tag=reader.varint();int f=(int)(tag>>>3),w=(int)(tag&7);
            if(f==5 && w==2)out.route=reader.string();
            else reader.skip(w);
        }
    }
    private static void coord(Reader reader,Position out) throws IOException {
        while(reader.hasNext()){
            long tag=reader.varint();int f=(int)(tag>>>3),w=(int)(tag&7);
            if(f==1 && w==5)out.lat=Float.intBitsToFloat(reader.fixed32());
            else if(f==2 && w==5)out.lon=Float.intBitsToFloat(reader.fixed32());
            else reader.skip(w);
        }
    }
    private static Position vehicle(Reader reader) throws IOException {
        Position out=new Position();
        while(reader.hasNext()){
            long tag=reader.varint();int f=(int)(tag>>>3),w=(int)(tag&7);
            if(f==1 && w==2)trip(reader.child(),out);
            else if(f==2 && w==2)coord(reader.child(),out);
            else if(f==5 && w==0)out.timestampMillis=reader.varint()*1000L;
            else reader.skip(w);
        }
        return out;
    }
    private static Position entity(Reader reader) throws IOException {
        Position pos=null;
        while(reader.hasNext()){
            long tag=reader.varint();int f=(int)(tag>>>3),w=(int)(tag&7);
            if(f==4 && w==2)pos=vehicle(reader.child());
            else reader.skip(w);
        }
        return pos;
    }
    private static boolean fresh(long stamp,long now) {
        return stamp>0 && stamp<=now+30000L && now-stamp<=FRESH_MS;
    }
    private static double haversine(double a,double b,double c,double d){
        double dlat=Math.toRadians(c-a),dlon=Math.toRadians(d-b);
        double h=Math.pow(Math.sin(dlat/2),2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*Math.pow(Math.sin(dlon/2),2);
        return 12742000.0*Math.asin(Math.min(1,Math.sqrt(h)));
    }
    public static Snapshot parse(byte[] data,Set<String> routes,double stopLat,double stopLon,long now) throws IOException {
        if(data==null||data.length==0||data.length>MAX_BYTES)throw new IOException("Nieprawidłowy rozmiar źródła");
        Reader feed=new Reader(data,0,data.length);
        Snapshot result=new Snapshot();
        List<Position> positions=new ArrayList<>();
        while(feed.hasNext()){
            long tag=feed.varint();int field=(int)(tag>>>3),wire=(int)(tag&7);
            if(field==1 && wire==2)result.feedTimeMillis=header(feed.child());
            else if(field==2 && wire==2){
                Position pos=entity(feed.child());
                if(pos!=null)positions.add(pos);
            } else feed.skip(wire);
        }
        if(!fresh(result.feedTimeMillis,now))throw new IOException("Źródło GPS nie ma świeżej aktualizacji");
        Set<String> accepted=new HashSet<>();
        if(routes!=null)for(String route:routes)if(route!=null&&!route.isEmpty())accepted.add(route);
        if(accepted.isEmpty())throw new IOException("Brak identyfikatora linii w rozkładzie");
        boolean validStop=Double.isFinite(stopLat)&&Double.isFinite(stopLon)&&
            Math.abs(stopLat)<=90&&Math.abs(stopLon)<=180&&!(stopLat==0&&stopLon==0);
        for(Position pos:positions){
            if(!accepted.contains(pos.route) || !fresh(pos.timestampMillis,now) ||
               !Float.isFinite(pos.lat)||!Float.isFinite(pos.lon) ||
               Math.abs(pos.lat)>90||Math.abs(pos.lon)>180)continue;
            result.vehicles++;
            result.latestVehicleMillis=Math.max(result.latestVehicleMillis,pos.timestampMillis);
            if(validStop){
                double dist=haversine(stopLat,stopLon,pos.lat,pos.lon);
                if(Double.isNaN(result.nearestMeters)||dist<result.nearestMeters)result.nearestMeters=dist;
            }
        }
        return result;
    }
    public static Snapshot load(Set<String> routes,double stopLat,double stopLon) throws IOException {
        HttpURLConnection http=(HttpURLConnection)new URL(SOURCE).openConnection();
        http.setConnectTimeout(6500);
        http.setReadTimeout(12000);
        http.setInstanceFollowRedirects(false);
        http.setRequestProperty("Accept","application/x-protobuf");
        http.setRequestProperty("User-Agent","PomocnikAlfa/1.7.60");
        try {
            if(http.getResponseCode()!=200)throw new IOException("Serwer GPS HTTP "+http.getResponseCode());
            if(http.getContentLengthLong()>MAX_BYTES)throw new IOException("Zbyt duży plik GPS");
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=http.getInputStream()){
                byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new IOException("Anulowano odświeżanie");
                    if(out.size()+n>MAX_BYTES)throw new IOException("Limit transferu GPS");
                    out.write(buffer,0,n);
                }
            }
            return parse(out.toByteArray(),routes,stopLat,stopLon,System.currentTimeMillis());
        } finally {http.disconnect();}
    }
}
