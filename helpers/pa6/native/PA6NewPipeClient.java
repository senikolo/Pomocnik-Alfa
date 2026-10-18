package com.ispina.lokalnie;

import android.util.Log;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * In-app NewPipe Extractor adapter for API23, GPLv3 compatible with PA.
 * Stream retrieval is performed locally; Piped/Invidious remain optional backups.
 * This is not guaranteed to work for videos requiring sign-in, DRM or YouTube PoToken.
 */
public final class PA6NewPipeClient {
 private static boolean initialized;
 private static okhttp3.OkHttpClient httpClient;
 private static synchronized void init(){
  if(initialized)return;
  try{
   // Use the same OkHttp transport family as NewPipe itself, with the existing
   // verified Android 6 TLS roots. HttpURLConnection mishandles some response
   // encodings and content negotiation required by modern YouTube.
   okhttp3.OkHttpClient.Builder builder=new okhttp3.OkHttpClient.Builder()
    .connectTimeout(9000,java.util.concurrent.TimeUnit.MILLISECONDS)
    .readTimeout(30000,java.util.concurrent.TimeUnit.MILLISECONDS)
    .followRedirects(true).retryOnConnectionFailure(true);
   builder.sslSocketFactory(PA6ModernTls.okHttpSocketFactory(),PA6ModernTls.okHttpTrustManager());
   httpClient=builder.build();
  }catch(Exception e){
   throw new IllegalStateException("Nie można skonfigurować bezpiecznego HTTPS w PA6",e);
  }
  NewPipe.init(new Downloader(){
   @Override public Response execute(Request request)throws IOException{
    String address=request.url();
    if(address==null||!address.startsWith("https://"))
     throw new IOException("Only HTTPS sources are accepted.");
    okhttp3.Request.Builder req=new okhttp3.Request.Builder().url(address)
     .header("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0");
    for(Map.Entry<String,List<String>> h:request.headers().entrySet()){
     if(h.getKey()==null)continue;
     req.removeHeader(h.getKey());
     for(String v:h.getValue())if(v!=null)req.addHeader(h.getKey(),v);
    }
    // Let OkHttp negotiate gzip and transparently decode it. Do not force identity
    // or forward Brotli Accept-Encoding without a Brotli decoder on Android 6.
    req.removeHeader("Accept-Encoding");
    byte[] data=request.dataToSend();
    okhttp3.RequestBody body=null;
    if(data!=null)body=okhttp3.RequestBody.create(data);
    else if("POST".equalsIgnoreCase(request.httpMethod())||"PUT".equalsIgnoreCase(request.httpMethod()))
     body=okhttp3.RequestBody.create(new byte[0]);
    req.method(request.httpMethod(),body);
    try(okhttp3.Response http=httpClient.newCall(req.build()).execute()){
     okhttp3.ResponseBody payload=http.body();
     String content=payload==null?"":payload.string();
     if(content.length()>8000000)throw new IOException("YouTube response too large.");
     int code=http.code();
     if(code>=400)Log.w("PA6_NEWPIPE","Extractor HTTP "+code+" from "+http.request().url().host());
     return new Response(code,http.message(),http.headers().toMultimap(),
      content,http.request().url().toString());
    }
   }
  });
  initialized=true;
 }
 /**
  * Search YouTube directly on the device using NewPipeExtractor.
  * This deliberately avoids depending on a public Piped/Invidious search API.
  * Must run on the caller's background executor, never the Android UI thread.
  */
 public static PA6PipedClient.Response<List<PA6PipedClient.Result>> search(String query)throws Exception{
  if(query==null||query.trim().isEmpty())throw new IOException("Wpisz nazwę filmu.");
  init();
  SearchInfo info=SearchInfo.getInfo(ServiceList.YouTube,
      ServiceList.YouTube.getSearchQHFactory().fromQuery(query.trim()));
  ArrayList<PA6PipedClient.Result> found=new ArrayList<>();
  for(InfoItem entry:info.getRelatedItems()){
   if(!(entry instanceof StreamInfoItem))continue;
   StreamInfoItem video=(StreamInfoItem)entry;
   String id=PA6PipedClient.extractId(video.getUrl());
   if(id.isEmpty()||video.getName()==null||video.getName().trim().isEmpty())continue;
   long seconds=video.getDuration();
   String duration="";
   if(seconds>0){
    long hours=seconds/3600,minutes=(seconds%3600)/60,remain=seconds%60;
    duration=hours>0?String.format(Locale.US,"%d:%02d:%02d",hours,minutes,remain):
        String.format(Locale.US,"%d:%02d",minutes,remain);
   }
   String uploader=video.getUploaderName();
   String date=video.getTextualUploadDate();
   found.add(new PA6PipedClient.Result(video.getName(),
       uploader==null||uploader.trim().isEmpty()?"Kanał":uploader,
       "https://i.ytimg.com/vi/"+id+"/mqdefault.jpg",id,duration,date==null?"":date));
   if(found.size()>=36)break;
  }
  Log.i("PA6_NEWPIPE","SEARCH_RESULTS count="+found.size());
  return new PA6PipedClient.Response<List<PA6PipedClient.Result>>(found,"NewPipe Extractor");
 }
 public static PA6PipedClient.Response<PA6PipedClient.Video> resolve(String id)throws Exception{
  if(id==null||!id.matches("[A-Za-z0-9_-]{11}"))throw new IOException("Nieprawidłowy identyfikator");
  init();
  StreamInfo info=StreamInfo.getInfo("https://www.youtube.com/watch?v="+id);
  String bestUrl="",bestLabel="";
  int bestScore=Integer.MAX_VALUE;
  for(VideoStream v:info.getVideoStreams()){
   if(v==null||v.isVideoOnly()||!v.isUrl()||v.getFormat()!=MediaFormat.MPEG_4)continue;
   String url=v.getContent();
   if(!url.startsWith("https://")||url.length()>8000)continue;
   String quality=v.getResolution();
   int height=0;
   try{height=Integer.parseInt(quality.replaceAll("[^0-9]",""));}catch(Exception ignored){}
   if(height>720)continue;
   int score=Math.abs((height==0?360:height)-480);
   if(score<bestScore){bestScore=score;bestUrl=url;bestLabel=quality;}
  }
  if(!bestUrl.isEmpty()){
   Log.i("PA6_NEWPIPE","Progressive MP4 for "+id+" "+bestLabel);
   return new PA6PipedClient.Response<>(new PA6PipedClient.Video(info.getName(),info.getUploaderName(),bestUrl,bestLabel+" • NewPipe"),"NewPipe Extractor");
  }
  // YouTube commonly exposes video-only MP4 and audio-only M4A streams.
  // Prior PA6 releases ignored those streams even when the extraction worked.
  // Prefer a modest H.264-compatible MP4 video with AAC/M4A sound on Android 6.
  String videoOnlyUrl="",videoQuality="",audioUrl="";
  int videoScore=Integer.MAX_VALUE,audioScore=Integer.MAX_VALUE;
  for(VideoStream v:info.getVideoOnlyStreams()){
   if(v==null||!v.isUrl()||v.getFormat()!=MediaFormat.MPEG_4)continue;
   String url=v.getContent();
   if(url==null||!url.startsWith("https://")||url.length()>8000)continue;
   int height=0;
   String quality=v.getResolution();
   try{height=Integer.parseInt(quality.replaceAll("[^0-9]",""));}catch(Exception ignored){}
   if(height<=0||height>720)continue;
   int score=Math.abs(height-360);
   if(score<videoScore){videoScore=score;videoOnlyUrl=url;videoQuality=quality;}
  }
  for(AudioStream a:info.getAudioStreams()){
   if(a==null||!a.isUrl()||a.getFormat()!=MediaFormat.M4A)continue;
   String url=a.getContent();
   if(url==null||!url.startsWith("https://")||url.length()>8000)continue;
   int bitrate=a.getAverageBitrate();
   int score=Math.abs((bitrate<=0?128:bitrate)-128);
   if(score<audioScore){audioScore=score;audioUrl=url;}
  }
  if(!videoOnlyUrl.isEmpty()&&!audioUrl.isEmpty()){
   Log.i("PA6_NEWPIPE","Selected DASH video-only MP4 "+videoQuality+" with separate M4A audio");
   return new PA6PipedClient.Response<>(new PA6PipedClient.Video(
      info.getName(),info.getUploaderName(),videoOnlyUrl,
      videoQuality+" + AAC • NewPipe",audioUrl),"NewPipe Extractor");
  }
  String hls=info.getHlsUrl();
  if(hls!=null&&hls.startsWith("https://")){
   return new PA6PipedClient.Response<>(new PA6PipedClient.Video(info.getName(),info.getUploaderName(),hls,"HLS • NewPipe"),"NewPipe Extractor");
  }
  String dash=info.getDashMpdUrl();
  if(dash!=null&&dash.startsWith("https://")){
   return new PA6PipedClient.Response<>(new PA6PipedClient.Video(info.getName(),info.getUploaderName(),dash,"DASH • NewPipe"),"NewPipe Extractor");
  }
  throw new IOException("NewPipe nie udostępnił zgodnego obrazu i dźwięku dla tego filmu.");
 }
 private PA6NewPipeClient(){}
}
