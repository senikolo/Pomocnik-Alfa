package com.ispina.lokalnie;

import org.json.*;
import java.io.*;
import java.net.*;

/** Resolve current public TVP playlists on the viewer's device, never cache expiring URLs. */
public final class PA6TvSources {
 private PA6TvSources(){}
 static String tvpPlaylist(String json)throws Exception{
  JSONObject root=new JSONObject(json);
  JSONObject drm=root.optJSONObject("drm");
  if(drm!=null&&drm.length()>0)throw new IOException("Ten program wymaga odtwarzacza TVP obsługującego zabezpieczenia transmisji.");
  JSONObject sources=root.optJSONObject("sources");
  JSONArray streams=sources==null?null:sources.optJSONArray("HLS");
  if(streams!=null)for(int i=0;i<streams.length();i++){
   JSONObject stream=streams.optJSONObject(i);if(stream==null)continue;
   String url=stream.optString("src","");
   if(url.startsWith("https://")&&url.length()<8000&&!url.contains("\n"))return url;
  }
  throw new IOException("TVP nie udostępniła obecnie strumienia tego programu. Spróbuj później.");
 }
 public static String resolve(String source)throws Exception{
  if(!source.startsWith("tvp:"))return source;
  String id=source.substring(4);
  if(!id.equals("399697")&&!id.equals("399746"))throw new IOException("Nieprawidłowy kanał TVP.");
  HttpURLConnection connection=PA6ModernTls.open("https://vod.tvp.pl/api/products/"+id+"/videos/playlist?platform=BROWSER&videoType=LIVE");
  try{
   connection.setConnectTimeout(5000);connection.setReadTimeout(7000);
   connection.setRequestProperty("User-Agent","Mozilla/5.0");
   connection.setRequestProperty("Referer","https://vod.tvp.pl/");
   connection.setRequestProperty("Accept","application/json");
   int code=connection.getResponseCode();
   InputStream stream=code>=400?connection.getErrorStream():connection.getInputStream();
   if(stream==null)throw new IOException("TVP nie odpowiedziała. Spróbuj ponownie.");
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();
   try(InputStream input=stream){
    byte[] buffer=new byte[4096];int n;
    while((n=input.read(buffer))!=-1){if(bytes.size()+n>300000)throw new IOException("Nieprawidłowa odpowiedź TVP.");bytes.write(buffer,0,n);}
   }
   String json=bytes.toString("UTF-8");
   if(json.contains("GEOIP_FILTER_FAILED"))throw new IOException("TVP ogranicza tę transmisję do Polski. Sprawdź połączenie i spróbuj ponownie.");
   if(code>=400)throw new IOException("TVP nie udostępnia teraz tej transmisji ("+code+"). Spróbuj później.");
   return tvpPlaylist(json);
  }finally{connection.disconnect();}
 }
}
