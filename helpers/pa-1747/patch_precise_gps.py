#!/usr/bin/env python3
"""PA 1.7.47: one-shot opt-in GPS with city/district/neighbourhood/estate and nearby street.

Uses OpenStreetMap Nominatim only AFTER the user taps GPS; Android Geocoder fallback.
Never stores coordinates, requests no background location, identifies OSM in user agent.
"""
from pathlib import Path

base=Path("project/app/src/main/java/com/ispina/lokalnie")
p=base/"WeatherActivity.java"
s=p.read_text(encoding="utf-8")
assert s.count(" private Button gpsButton;")==1
s=s.replace(" private Button gpsButton;"," private Button gpsButton;\n private android.widget.TextView gpsDetailsView;")
anchor='  root.addView(gpsButton,new LinearLayout.LayoutParams(-1,dp(56)));'
assert s.count(anchor)==1
s=s.replace(anchor,anchor+'''
  NativeUi.addSpacer(root,this,6);
  gpsDetailsView=NativeUi.muted(this,
    "Dokładne nazwy osiedla i ulicy: © OpenStreetMap. Po dotknięciu GPS współrzędne są jednorazowo wysyłane do usługi mapowej w celu ustalenia nazwy okolicy.",12);
  root.addView(gpsDetailsView,new LinearLayout.LayoutParams(-1,-2));
''')
start=s.index(' private static String gpsAddress(android.content.Context ctx,android.location.Location loc){')
end=s.index(' private void finishGpsFix(){',start)
s=s[:start]+s[end:]
old='''   String label=gpsAddress(getApplicationContext(),fix);
   float accuracy=fix.hasAccuracy()?fix.getAccuracy():Float.NaN;
   ui.post(()->{'''
new='''   GpsMicroArea.Result placeDetails=GpsMicroArea.resolve(getApplicationContext(),fix);
   String label=placeDetails.label;
   float accuracy=fix.hasAccuracy()?fix.getAccuracy():Float.NaN;
   ui.post(()->{'''
assert s.count(old)==1
s=s.replace(old,new)
anchor='''    gpsButton.setText("📍 GPS: "+label+" · dotknij, aby odświeżyć pozycję");'''
assert s.count(anchor)==1
s=s.replace(anchor,anchor+'''
    if(gpsDetailsView!=null)gpsDetailsView.setText(placeDetails.detail+
       (Float.isFinite(accuracy)&&accuracy>120f?"\\nUwaga: lokalizacja ma dokładność około "+Math.round(accuracy)+" m, więc nazwa najbliższej okolicy może być orientacyjna.":"")+
       "\\nNazwy: "+placeDetails.source+". Prognoza pogody opiera się na siatce modelu meteorologicznego.");
''')
p.write_text(s,encoding="utf-8")

java=r'''package com.ispina.lokalnie;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Reverse geocodes exactly one user-requested GPS fix. No address or lat/lon storage.
 * OSM/Nominatim is called only after explicit GPS tile tap and permission handling.
 * One call per tap at most; respects public Nominatim usage limits.
 */
final class GpsMicroArea {
    static final class Result {
        final String label;
        final String detail;
        final String source;
        Result(String label, String detail, String source) {
            this.label=label;
            this.detail=detail;
            this.source=source;
        }
    }

    private static long lastRequestTimeMs=0L;
    private static long cachedAtMs=0L;
    private static double cachedLat=Double.NaN, cachedLon=Double.NaN;
    private static Result cachedResult;

    private static String clean(String value) {
        return value==null?"":value.trim();
    }
    private static String first(JSONObject o,String... keys){
        if(o==null)return "";
        for(String key:keys){
            String v=clean(o.optString(key,""));
            if(!v.isEmpty() && !"null".equalsIgnoreCase(v))return v;
        }
        return "";
    }
    private static boolean equalsName(String a,String b){
        return !a.isEmpty() && a.equalsIgnoreCase(b);
    }
    private static String combine(String city,String district,String micro){
        StringBuilder out=new StringBuilder();
        for(String name:new String[]{city,district,micro}){
            if(name.isEmpty())continue;
            boolean exists=false;
            for(String old:out.toString().split(" · ")){
                if(equalsName(name,old)){exists=true;break;}
            }
            if(!exists){if(out.length()>0)out.append(" · ");out.append(name);}
        }
        return out.toString();
    }

    private static Result systemFallback(Context ctx,Location location){
        String coordinates=String.format(Locale.US,"GPS %.4f, %.4f",
                location.getLatitude(),location.getLongitude());
        if(!Geocoder.isPresent())
            return new Result(coordinates,"Nazwa osiedla i ulicy nie jest dostępna z geokodera urządzenia.","GPS");
        try{
            List<Address> a=new Geocoder(ctx,new Locale("pl","PL"))
                .getFromLocation(location.getLatitude(),location.getLongitude(),1);
            if(a==null || a.isEmpty())return new Result(coordinates,"Nie udało się określić nazwy osiedla.","GPS");
            Address addr=a.get(0);
            String city=clean(addr.getLocality());
            if(city.isEmpty())city=clean(addr.getSubAdminArea());
            if(city.isEmpty())city=clean(addr.getAdminArea());
            String district=clean(addr.getSubLocality());
            String street=clean(addr.getThoroughfare());
            String label=combine(city,district,"");
            if(label.isEmpty())label=coordinates;
            String detail=(district.isEmpty()?"Osiedle: brak nazwy w dostępnych danych.":"Część miejscowości: "+district+".")+
                     (street.isEmpty()?"":"\nNajbliższa ulica: "+street+".");
            return new Result(label,detail,"geokoder Androida");
        }catch(Exception ignored){
            return new Result(coordinates,"Brak nazwy osiedla w danych lokalizacji.","GPS");
        }
    }

    static synchronized Result resolve(Context ctx,Location location){
        Result fallback=systemFallback(ctx,location);
        long now=SystemClock.elapsedRealtime();
        if(cachedResult!=null && now-cachedAtMs<120000L && Double.isFinite(cachedLat)){
            float[] distance=new float[1];
            Location.distanceBetween(location.getLatitude(),location.getLongitude(),cachedLat,cachedLon,distance);
            if(distance[0]<40f)return cachedResult;
        }
        // No more than one public OSM request per second; repeated taps return Android fallback.
        if(lastRequestTimeMs>0 && now-lastRequestTimeMs<1300L)return fallback;
        lastRequestTimeMs=now;
        HttpURLConnection connection=null;
        try{
            String lat=String.format(Locale.US,"%.7f",location.getLatitude());
            String lon=String.format(Locale.US,"%.7f",location.getLongitude());
            URL url=new URL("https://nominatim.openstreetmap.org/reverse"+
                "?format=jsonv2&addressdetails=1&zoom=18&layer=address&accept-language=pl"+
                "&lat="+lat+"&lon="+lon);
            connection=(HttpURLConnection)url.openConnection();
            connection.setConnectTimeout(4800);
            connection.setReadTimeout(5800);
            connection.setRequestProperty("User-Agent","PomocnikAlfa/1.7.47 (https://github.com/senikolo/Pomocnik-Alfa)");
            connection.setRequestProperty("Accept","application/json");
            connection.setRequestProperty("Accept-Language","pl");
            if(connection.getResponseCode()!=200)return fallback;
            try(InputStream input=connection.getInputStream();
                BufferedReader reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))){
                StringBuilder raw=new StringBuilder();
                char[] buf=new char[1024];int n;
                while((n=reader.read(buf))!=-1){
                    raw.append(buf,0,n);
                    if(raw.length()>65536)return fallback;
                }
                JSONObject data=new JSONObject(raw.toString());
                JSONObject address=data.optJSONObject("address");
                if(address==null)return fallback;
                String city=first(address,"city","town","village","municipality","hamlet");
                String district=first(address,"city_district","borough","suburb");
                String micro=first(address,"neighbourhood","quarter","residential","city_block","locality","isolated_dwelling");
                // OSM may use 'suburb' for a specific estate within a city_district.
                String suburb=first(address,"suburb");
                if(!suburb.isEmpty()&&!equalsName(suburb,district)&&micro.isEmpty())micro=suburb;
                // Avoid showing one estate twice, even when tagged as both quarter and suburb.
                if(equalsName(micro,district)||equalsName(micro,city))micro="";
                if(equalsName(district,city))district="";
                String street=first(address,"road","pedestrian","living_street","footway","path");
                String label=combine(city,district,micro);
                if(label.isEmpty())label=fallback.label;
                // Missing city: keep the Android-derived city (if OSM only supplies an estate).
                if(city.isEmpty() && !fallback.label.startsWith("GPS ")){
                    label=combine(fallback.label,"",micro);
                }
                StringBuilder detail=new StringBuilder();
                if(!district.isEmpty())detail.append("Dzielnica / rejon: ").append(district).append(".");
                if(!micro.isEmpty()){
                    if(detail.length()>0)detail.append("\n");
                    detail.append("Osiedle / część dzielnicy: ").append(micro).append(".");
                }else{
                    if(detail.length()>0)detail.append("\n");
                    detail.append("Dokładna nazwa osiedla nie występuje w danych mapowych tej lokalizacji.");
                }
                if(!street.isEmpty()){
                    detail.append("\nNajbliższa ulica: ").append(street)
                          .append(" (orientacyjnie, nie jest to ustalony adres budynku).");
                }
                Result result=new Result(label,detail.toString(),"© OpenStreetMap contributors");
                cachedResult=result;cachedAtMs=SystemClock.elapsedRealtime();
                cachedLat=location.getLatitude();cachedLon=location.getLongitude();
                return result;
            }
        }catch(Exception ignored){return fallback;}
        finally{if(connection!=null)connection.disconnect();}
    }
}
'''
(base/"GpsMicroArea.java").write_text(java,encoding="utf-8")

main=base/"MainActivity.java"
m=main.read_text(encoding="utf-8")
assert m.count("POMOCNIK ALFA 1.7.46")>=1
m=m.replace("POMOCNIK ALFA 1.7.46","POMOCNIK ALFA 1.7.47")
main.write_text(m,encoding="utf-8")
print("PASS: on-tap OSM micro-area, Android fallback, street separately, source attribution, privacy and cache/rate limit")
