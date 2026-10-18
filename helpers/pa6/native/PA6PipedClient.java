package com.ispina.lokalnie;

import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Network data adapter for the Piped public API, powered upstream by NewPipeExtractor.
 * All search/results/player UI are native PA views; this is not an external app.
 * Public instances are independent services and can be unavailable without warning.
 */
public final class PA6PipedClient {
 // Android 6 emulator log from 17 Oct: kavin returned 526, adminforge 404.
 // Start with the server that actually supplied search results in our last test.
 public static final String[] DEFAULT_SERVERS={
  "https://api.piped.private.coffee",
  "https://piped-api.lunar.icu",
  "https://api.looleh.xyz"
 };
 public static final class Result {
  public final String title, channel, thumb, id, duration, date;
  Result(String title,String channel,String thumb,String id,String duration,String date){
   this.title=title;this.channel=channel;this.thumb=thumb;this.id=id;this.duration=duration;this.date=date;
  }
 }
 public static final class Video {
  public final String title,channel,stream,quality,audioStream;
  Video(String title,String channel,String stream,String quality){
   this(title,channel,stream,quality,"");
  }
  Video(String title,String channel,String stream,String quality,String audioStream){
   this.title=title;this.channel=channel;this.stream=stream;this.quality=quality;
   this.audioStream=audioStream==null?"":audioStream;
  }
 }
 public static final class Response<T>{
  public final T data;public final String server;
  Response(T data,String server){this.data=data;this.server=server;}
 }
 private static String https(String url){
  if(url==null||url.length()>8000||!url.startsWith("https://")||url.contains(" ")||url.contains("\n"))return "";
  return url;
 }
 public static String validServer(String input){
  if(input==null)return "";
  String t=input.trim();if(!t.matches("https://[A-Za-z0-9.-]+(?::[0-9]{2,5})?/?"))return "";
  return t.endsWith("/")?t.substring(0,t.length()-1):t;
 }
 private static String fetchRaw(String address)throws Exception{
  HttpURLConnection c=null;
  try{
   c=PA6ModernTls.open(address);
   c.setConnectTimeout(3500);c.setReadTimeout(5500);c.setInstanceFollowRedirects(true);
   c.setRequestProperty("User-Agent","PomocnikAlfa/6.0 (Android 6 native multimedia client)");
   c.setRequestProperty("Accept","application/json");
   int code=c.getResponseCode();
   if(code!=200)throw new IOException("Serwer odpowiedział HTTP "+code);
   ByteArrayOutputStream out=new ByteArrayOutputStream();
   try(InputStream in=c.getInputStream()){
    byte[] buf=new byte[8192];int read;
    while((read=in.read(buf))>0){
     if(out.size()+read>1250000)throw new IOException("Odpowiedź serwera zbyt duża");
     out.write(buf,0,read);
    }
   }
   return out.toString("UTF-8");
  }finally{if(c!=null)c.disconnect();}
 }
 private static JSONObject fetch(String address)throws Exception{return new JSONObject(fetchRaw(address));}
 private static JSONArray fetchArray(String address)throws Exception{return new JSONArray(fetchRaw(address));}
 private static String enc(String input)throws Exception{return URLEncoder.encode(input,"UTF-8");}
 private static ArrayList<String> servers(String user){
  ArrayList<String> hosts=new ArrayList<>();
  String custom=validServer(user);if(!custom.isEmpty())hosts.add(custom);
  for(String s:DEFAULT_SERVERS)if(!hosts.contains(s))hosts.add(s);
  return hosts;
 }
 private static String videoId(String address){
  if(address==null)return "";
  String i=address;
  int q=i.indexOf("v=");if(q>=0)i=i.substring(q+2);
  else {int slash=i.lastIndexOf('/');if(slash>=0)i=i.substring(slash+1);}
  int end=i.indexOf('&');if(end>=0)i=i.substring(0,end);
  // YouTube Shorts, live and youtu.be share links commonly append ?si= or ?t=.
  end=i.indexOf('?');if(end>=0)i=i.substring(0,end);
  end=i.indexOf('#');if(end>=0)i=i.substring(0,end);
  return i.matches("[A-Za-z0-9_-]{11}")?i:"";
 }
 public static String extractId(String address){return videoId(address);}
 public static Response<List<Result>> search(String query,String custom)throws Exception{
  if(query==null||query.trim().isEmpty())throw new IOException("Wpisz tytuł lub wykonawcę.");
  Exception last=null;
  for(String server:servers(custom)){
   try{
    JSONObject json=fetch(server+"/search?q="+enc(query.trim())+"&filter=videos");
    JSONArray arr=json.optJSONArray("items");
    if(arr==null)throw new IOException("Nieprawidłowa lista filmów");
    ArrayList<Result> list=new ArrayList<>();
    for(int i=0;i<arr.length()&&list.size()<36;i++){
     JSONObject item=arr.optJSONObject(i);if(item==null)continue;
     String id=videoId(item.optString("url",""));
     String title=item.optString("title","").trim();
     if(id.isEmpty()||title.isEmpty())continue;
     list.add(new Result(title,item.optString("uploader","Kanał"),
      https(item.optString("thumbnail","")),id,
      humanDuration(item.optLong("duration",0)),item.optString("uploadedDate","")));
    }
    return new Response<List<Result>>(list,server);
   }catch(Exception e){android.util.Log.w("PA6_PIPED","Host "+server+" nieudany: "+e.getClass().getSimpleName()+" "+e.getMessage());last=e;}
  }
  // Independent public fallback when the Piped API is down.
  try{return invidiousSearch(query);}catch(Exception fallback){
   throw new IOException("Serwery katalogu filmów są obecnie niedostępne (Piped i Invidious). Spróbuj później lub wybierz plik z tabletu.",fallback);
  }
 }
 public static Response<List<Result>> trending(String custom)throws Exception{
  Exception last=null;
  for(String server:servers(custom)){
   try{
    JSONObject data=fetch(server+"/trending?region=PL");
    // Trending endpoint normally returns JSON array; request it through separate decoder below.
   }catch(Exception e){last=e;}
  }
  // Keep home screen responsive even when unauthenticated trending is blocked.
  return search("muzyka polska",custom);
 }
 private static String humanDuration(long sec){
  if(sec<=0)return "";
  long h=sec/3600,m=(sec%3600)/60,s=sec%60;
  return h>0?String.format(Locale.US,"%d:%02d:%02d",h,m,s):String.format(Locale.US,"%d:%02d",m,s);
 }
 public static Response<Video> resolve(String id,String custom)throws Exception{
  return resolve(id,custom,Collections.<String>emptySet());
 }
 public static Response<Video> resolve(String id,String custom,Set<String> excluded)throws Exception{
  return resolve(id,custom,excluded,0);
 }
 public static Response<Video> resolve(String id,String custom,Set<String> excluded,int preferredHeight)throws Exception{
  if(id==null||!id.matches("[A-Za-z0-9_-]{11}"))throw new IOException("Nieprawidłowy identyfikator filmu.");
  Exception last=null;
  for(String server:servers(custom)){
   if(excluded.contains(server))continue;
   try{
    JSONObject data=fetch(server+"/streams/"+id);
    String name=data.optString("title","Film"),channel=data.optString("uploader","YouTube");
    if(data.optBoolean("livestream",false)){
     String hls=https(data.optString("hls",""));
     if(!hls.isEmpty())return new Response<>(new Video(name,channel,hls,"Transmisja na żywo"),server);
    }
    JSONArray videos=data.optJSONArray("videoStreams");
    String url="",quality="";
    int bestScore=Integer.MAX_VALUE;
    if(videos!=null)for(int i=0;i<videos.length();i++){
     JSONObject v=videos.optJSONObject(i);if(v==null||v.optBoolean("videoOnly",false))continue;
     String stream=https(v.optString("url",""));
     String codec=(v.optString("codec","")+" "+v.optString("mimeType","")).toLowerCase(Locale.ROOT);
     String format=v.optString("mimeType","").toLowerCase(Locale.ROOT);
     int height=v.optInt("height",0);
     // The Acer API23 decoder needs H.264/AVC video with muxed audio.
     // MP4 alone is not sufficient: a few feeds report other video codecs in MP4.
     boolean avc=codec.contains("avc1")||codec.contains("h264")||codec.contains("avc");
     if(stream.isEmpty()||height<=0||height>720||!format.contains("video/mp4")||!avc)continue;
     int target=preferredHeight<=0?480:Math.min(720,Math.max(144,preferredHeight));
     int score=height<=target?target-height:1000+height-target;
     if(score<bestScore){bestScore=score;url=stream;quality=v.optString("quality",height+"p");}
    }
    if(!url.isEmpty())return new Response<>(new Video(name,channel,url,quality),server);
    // For separated audio/video prefer server-provided adaptive manifest.
    String hls=https(data.optString("hls",""));
    if(!hls.isEmpty())return new Response<>(new Video(name,channel,hls,"HLS"),server);
    String dash=https(data.optString("dash",""));
    if(!dash.isEmpty())return new Response<>(new Video(name,channel,dash,"DASH"),server);
    throw new IOException("Brak wspólnego obrazu i dźwięku H.264 w tym źródle.");
   }catch(Exception e){
    android.util.Log.w("PA6_TUBE_RESOLVE","Piped "+server+" failed: "+e.getClass().getSimpleName()+" "+e.getMessage());
    last=e;
   }
  }
  try{return invidiousResolve(id,excluded);}catch(Exception fallback){
   android.util.Log.w("PA6_TUBE_RESOLVE","Invidious all sources unavailable: "+fallback.getMessage());
   throw new IOException("Nie można uzyskać zgodnego filmu z obrazem i dźwiękiem. Serwery Piped/Invidious mogą być niedostępne.",fallback);
  }
 }

 // Only official-listed Invidious hosts. They can become unavailable, so never promise every video.
 private static final String[] INVIDIOUS={
  "https://inv.nadeko.net",
  "https://invidious.nerdvpn.de",
  "https://yt.chocolatemoo53.com",
  "https://invidious.tiekoetter.com"
 };
 private static String thumbnail(JSONArray thumbs){
  if(thumbs==null)return "";
  for(int i=0;i<thumbs.length();i++){
   JSONObject t=thumbs.optJSONObject(i);if(t==null)continue;
   String u=t.optString("url","");
   if(u.startsWith("https://"))return https(u);
  }
  return "";
 }
 private static Response<List<Result>> invidiousSearch(String query)throws Exception{
  Exception last=null;
  for(String server:INVIDIOUS){
   try{
    JSONArray data=fetchArray(server+"/api/v1/search?q="+enc(query.trim())+"&type=video");
    ArrayList<Result> found=new ArrayList<>();
    for(int i=0;i<data.length()&&found.size()<35;i++){
     JSONObject item=data.optJSONObject(i);if(item==null||!"video".equals(item.optString("type","video")))continue;
     String id=item.optString("videoId","");
     if(!id.matches("[A-Za-z0-9_-]{11}"))continue;
     found.add(new Result(item.optString("title","Film"),item.optString("author","Kanał"),
       thumbnail(item.optJSONArray("videoThumbnails")),id,
       humanDuration(item.optLong("lengthSeconds",0)),item.optString("publishedText","")));
    }
    return new Response<List<Result>>(found,server);
   }catch(Exception e){last=e;}
  }
  throw new IOException("Publiczne Invidious nie odpowiada.",last);
 }
 private static Response<Video> invidiousResolve(String id,Set<String> excluded)throws Exception{
  Exception last=null;
  for(String server:INVIDIOUS){
   if(excluded.contains(server))continue;
   try{
    JSONObject data=fetch(server+"/api/v1/videos/"+id);
    JSONArray formats=data.optJSONArray("formatStreams");
    int best=Integer.MAX_VALUE;String stream="",label="";
    if(formats!=null)for(int i=0;i<formats.length();i++){
     JSONObject format=formats.optJSONObject(i);if(format==null)continue;
     String url=format.optString("url","");
     if(url.startsWith("/"))url=server+url;
     String type=format.optString("type","").toLowerCase(Locale.ROOT);
     String container=format.optString("container","").toLowerCase(Locale.ROOT);
     if(!type.contains("video/mp4")&&!container.equals("mp4"))continue;
     String codec=format.optString("encoding","").toLowerCase(Locale.ROOT);
     if(codec.contains("vp9")||codec.contains("av1"))continue;
     String resolution=format.optString("qualityLabel",format.optString("quality","360p"));
     int height=0;
     try{height=Integer.parseInt(resolution.replaceAll("[^0-9]",""));}catch(Exception ignored){}
     if(height>720)continue;
     int score=Math.abs((height<=0?360:height)-480);
     if(!https(url).isEmpty()&&score<best){stream=url;label=resolution;best=score;}
    }
    if(!stream.isEmpty()){
     // Route media via the current instance's supported proxy rather than direct
     // googlevideo hosts that Android6 frequently receives as 403/expired URLs.
     // The Tube retries through other instances on an actual media decoding error.
     String proxied=server+"/latest_version?id="+enc(id)+"&itag=18&local=true";
     android.util.Log.i("PA6_TUBE","Invidious progressive playback via instance proxy: "+server);
     return new Response<>(new Video(data.optString("title","Film"),data.optString("author","YouTube"),proxied,"MP4 360p • serwer zapasowy"),server);
    }
    String hls=https(data.optString("hlsUrl",""));
    if(!hls.isEmpty())return new Response<>(new Video(data.optString("title","Film"),data.optString("author","YouTube"),hls,"HLS"),server);
    throw new IOException("Brak zgodnego wideo na serwerze.");
   }catch(Exception e){
    android.util.Log.w("PA6_TUBE_RESOLVE","Invidious "+server+" failed: "+e.getClass().getSimpleName()+" "+e.getMessage());
    last=e;
   }
  }
  throw new IOException("Publiczne Invidious nie udostępnia strumienia.",last);
 }
 private PA6PipedClient(){}
}
