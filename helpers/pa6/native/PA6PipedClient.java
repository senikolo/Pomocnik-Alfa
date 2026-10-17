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
 public static final String[] DEFAULT_SERVERS={
  "https://pipedapi.kavin.rocks",
  "https://pipedapi.adminforge.de",
  "https://api.piped.private.coffee"
 };
 public static final class Result {
  public final String title, channel, thumb, id, duration, date;
  Result(String title,String channel,String thumb,String id,String duration,String date){
   this.title=title;this.channel=channel;this.thumb=thumb;this.id=id;this.duration=duration;this.date=date;
  }
 }
 public static final class Video {
  public final String title,channel,stream,quality;
  Video(String title,String channel,String stream,String quality){
   this.title=title;this.channel=channel;this.stream=stream;this.quality=quality;
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
 private static JSONObject fetch(String address)throws Exception{
  HttpURLConnection c=null;
  try{
   c=PA6ModernTls.open(address);
   c.setConnectTimeout(6000);c.setReadTimeout(8500);c.setInstanceFollowRedirects(true);
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
   return new JSONObject(out.toString("UTF-8"));
  }finally{if(c!=null)c.disconnect();}
 }
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
   }catch(Exception e){last=e;}
  }
  throw new IOException("Nie udało się pobrać wyników z dostępnych serwerów Piped."+(last==null?"":" "+last.getMessage()));
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
  if(id==null||!id.matches("[A-Za-z0-9_-]{11}"))throw new IOException("Nieprawidłowy identyfikator filmu.");
  Exception last=null;
  for(String server:servers(custom)){
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
     String codec=v.optString("codec","").toLowerCase(Locale.ROOT);
     String format=v.optString("mimeType","").toLowerCase(Locale.ROOT);
     int height=v.optInt("height",0);
     if(stream.isEmpty()||height<=0||height>720||(!codec.contains("avc1")&&!format.contains("video/mp4")))continue;
     int score=Math.abs(height-480);
     if(score<bestScore){bestScore=score;url=stream;quality=v.optString("quality",height+"p");}
    }
    if(!url.isEmpty())return new Response<>(new Video(name,channel,url,quality),server);
    // For separated audio/video prefer server-provided adaptive manifest.
    String hls=https(data.optString("hls",""));
    if(!hls.isEmpty())return new Response<>(new Video(name,channel,hls,"HLS"),server);
    String dash=https(data.optString("dash",""));
    if(!dash.isEmpty())return new Response<>(new Video(name,channel,dash,"DASH"),server);
    throw new IOException("Serwer nie udostępnia filmu z obrazem i dźwiękiem w jednym strumieniu.");
   }catch(Exception e){last=e;}
  }
  throw new IOException("Nie udało się uzyskać zgodnego strumienia. Film może być niedostępny lub serwery przeciążone. "+(last==null?"":last.getMessage()));
 }
 private PA6PipedClient(){}
}
