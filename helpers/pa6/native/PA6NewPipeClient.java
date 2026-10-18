package com.ispina.lokalnie;

import android.util.Log;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
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
 private static synchronized void init(){
  if(initialized)return;
  NewPipe.init(new Downloader(){
   @Override public Response execute(Request request)throws IOException{
    String address=request.url();
    if(!address.startsWith("https://"))throw new IOException("Unsupported protocol");
    HttpURLConnection c=null;
    try{
     try{c=PA6ModernTls.open(address);}catch(Exception tls){throw new IOException("TLS YouTube",tls);}
     c.setConnectTimeout(9000);c.setReadTimeout(30000);
     c.setInstanceFollowRedirects(true);
     c.setRequestMethod(request.httpMethod());
     c.setRequestProperty("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0");
     for(Map.Entry<String,List<String>> h:request.headers().entrySet()){
      if(h.getKey()==null)continue;
      for(String value:h.getValue())c.addRequestProperty(h.getKey(),value);
     }
     // HttpURLConnection on API23 lacks NewPipe OkHttp Brotli support. Request plain responses.
     c.setRequestProperty("Accept-Encoding","identity");
     if(request.dataToSend()!=null){
      c.setDoOutput(true);
      try(OutputStream out=c.getOutputStream()){out.write(request.dataToSend());}
     }
     int code=c.getResponseCode();
     ByteArrayOutputStream bytes=new ByteArrayOutputStream(65536);
     InputStream body=code>=400?c.getErrorStream():c.getInputStream();
     if(body!=null)try(InputStream in=body){
      byte[] buffer=new byte[8192];int n;
      while((n=in.read(buffer))!=-1){
       if(bytes.size()+n>8_000_000)throw new IOException("Too many YouTube bytes");
       bytes.write(buffer,0,n);
      }
     }
     return new Response(code,c.getResponseMessage(),c.getHeaderFields(),bytes.toString("UTF-8"),c.getURL().toString());
    }finally{if(c!=null)c.disconnect();}
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
  String hls=info.getHlsUrl();
  if(hls!=null&&hls.startsWith("https://")){
   return new PA6PipedClient.Response<>(new PA6PipedClient.Video(info.getName(),info.getUploaderName(),hls,"HLS • NewPipe"),"NewPipe Extractor");
  }
  String dash=info.getDashMpdUrl();
  if(dash!=null&&dash.startsWith("https://")){
   return new PA6PipedClient.Response<>(new PA6PipedClient.Video(info.getName(),info.getUploaderName(),dash,"DASH • NewPipe"),"NewPipe Extractor");
  }
  throw new IOException("NewPipe nie uzyskał pliku MP4 z dźwiękiem dla tego materiału.");
 }
 private PA6NewPipeClient(){}
}
