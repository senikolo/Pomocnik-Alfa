package com.ispina.lokalnie;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/**
 * Android 6: select a modest H.264 rendition in simple HLS master playlists.
 * This is best-effort only. Keep the master manifest if it declares separate audio.
 * Never claim a channel exists solely because its URL appears in a catalog.
 */
public final class PA6HlsQuality {
 private static final Pattern HEIGHT=Pattern.compile("RESOLUTION=[0-9]+x([0-9]+)",Pattern.CASE_INSENSITIVE);
 private static final Pattern BANDWIDTH=Pattern.compile("BANDWIDTH=([0-9]+)",Pattern.CASE_INSENSITIVE);
 private PA6HlsQuality(){}
 private static int number(Pattern pattern,String line,int otherwise){
  Matcher m=pattern.matcher(line);
  if(!m.find())return otherwise;
  try{return Integer.parseInt(m.group(1));}catch(Exception ex){return otherwise;}
 }
 public static String preferLow(String input){
  if(input==null||!input.startsWith("https://")||!input.contains(".m3u8"))return input;
  HttpURLConnection connection=null;
  try{
   connection=PA6ModernTls.open(input);
   connection.setConnectTimeout(3400);connection.setReadTimeout(4200);
   connection.setRequestProperty("Accept","application/vnd.apple.mpegurl,application/x-mpegURL,*/*");
   if(connection.getResponseCode()!=200)return input;
   ByteArrayOutputStream bytes=new ByteArrayOutputStream(8000);
   try(InputStream stream=connection.getInputStream()){
    byte[] part=new byte[2048];int n;
    while((n=stream.read(part))!=-1){
     if(bytes.size()+n>100000)return input;
     bytes.write(part,0,n);
    }
   }
   String playlist=bytes.toString("UTF-8");
   if(!playlist.startsWith("#EXTM3U")||!playlist.contains("#EXT-X-STREAM-INF"))return input;
   // An audio group must be passed to LibVLC via the complete master playlist.
   if(playlist.contains("#EXT-X-MEDIA:TYPE=AUDIO"))return input;
   String[] lines=playlist.split("\\r?\\n");
   String best="",candidateMeta="";
   int bestScore=Integer.MAX_VALUE;
   for(String raw:lines){
    String line=raw.trim();
    if(line.startsWith("#EXT-X-STREAM-INF:")){
     candidateMeta=line;continue;
    }
    if(candidateMeta.isEmpty()||line.isEmpty()||line.startsWith("#"))continue;
    String info=candidateMeta.toLowerCase(Locale.ROOT);
    candidateMeta="";
    if(info.contains("hev1")||info.contains("hvc1")||info.contains("av01")||info.contains("vp09"))continue;
    int height=number(HEIGHT,info,0),bps=number(BANDWIDTH,info,2000000);
    // Favor 360p and 480p H.264; avoid high-res CPU-heavy streams on 2GB tablets.
    int score=(height>0&&height<=480?0:height==0?10000000:20000000)
      +Math.abs((height==0?360:height)-360)*10000+Math.min(bps/1000,9999);
    String resolved=new URL(new URL(input),line).toString();
    if((resolved.startsWith("https://")||resolved.startsWith("http://"))&&score<bestScore){
     best=resolved;bestScore=score;
    }
   }
   if(!best.isEmpty()){
    android.util.Log.i("PA6_HLS","Selected low-resolution HLS variant for Android 6");
    return best;
   }
  }catch(Exception e){
   android.util.Log.w("PA6_HLS","Cannot inspect master playlist, using original",e);
  }finally{if(connection!=null)connection.disconnect();}
  return input;
 }
}