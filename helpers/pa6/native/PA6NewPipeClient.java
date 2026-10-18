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
 /** Request-scoped cancellation never aborts a separate download or player task. */
 public static final class Work {
  public volatile boolean cancelled;
  private final Set<okhttp3.Call> calls=new HashSet<>();
  public synchronized void cancel(){cancelled=true;for(okhttp3.Call call:calls)call.cancel();calls.clear();}
  synchronized void add(okhttp3.Call call)throws IOException{if(cancelled){call.cancel();throw new InterruptedIOException("Cancelled");}calls.add(call);}
  synchronized void remove(okhttp3.Call call){calls.remove(call);}
 }
 private static final ThreadLocal<Work> currentWork=new ThreadLocal<>();
 public static void enterWork(Work work){currentWork.set(work);}
 public static void leaveWork(){currentWork.remove();}
 public static void checkWork()throws InterruptedIOException{
  Work w=currentWork.get();if(Thread.currentThread().isInterrupted()||(w!=null&&w.cancelled))throw new InterruptedIOException("Cancelled");
 }
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
    checkWork();
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
    okhttp3.Call call=httpClient.newCall(req.build());
    Work work=currentWork.get();if(work!=null)work.add(call);
    try(okhttp3.Response http=call.execute()){
     okhttp3.ResponseBody payload=http.body();
     String content=payload==null?"":payload.string();
     if(content.length()>8000000)throw new IOException("YouTube response too large.");
     int code=http.code();
     if(code>=400)Log.w("PA6_NEWPIPE","Extractor HTTP "+code+" from "+http.request().url().host());
     return new Response(code,http.message(),http.headers().toMultimap(),
      content,http.request().url().toString());
    }finally{if(work!=null)work.remove(call);}
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
  checkWork();init();
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
 private static boolean isAvc(String codec){
  if(codec==null)return false;
  String c=codec.toLowerCase(Locale.ROOT);
  return c.contains("avc1")||c.contains("h264")||c.contains("h.264")||c.contains("avc");
 }
 private static boolean isAac(String codec){
  if(codec==null)return false;
  String c=codec.toLowerCase(Locale.ROOT);
  return c.contains("mp4a")||c.contains("aac");
 }
 private static int height(String label){
  if(label==null)return 0;
  try{
   String digits=label.split("p",2)[0].replaceAll("[^0-9]","");
   return digits.isEmpty()?0:Integer.parseInt(digits);
  }catch(Exception e){return 0;}
 }
 private static int score(int height,int target){
  if(height<=0||height>720)return Integer.MAX_VALUE;
  // Do not silently request a resolution above the user's quality limit
  // unless there is no lower-quality playable stream at all.
  int preferred=target<=0?360:Math.min(720,Math.max(144,target));
  return height<=preferred?preferred-height:1000+height-preferred;
 }
 private static String cachedId="";
 private static StreamInfo cachedInfo;
 private static long cachedAt;
 static okhttp3.OkHttpClient downloadClient(){init();return httpClient;}
 private static synchronized StreamInfo load(String id)throws Exception{
  if(id==null||!id.matches("[A-Za-z0-9_-]{11}"))throw new IOException("Nieprawidłowy identyfikator filmu");
  checkWork();init();
  long now=android.os.SystemClock.elapsedRealtime();
  if(id.equals(cachedId)&&cachedInfo!=null&&now-cachedAt<120000)return cachedInfo;
  StreamInfo info=StreamInfo.getInfo("https://www.youtube.com/watch?v="+id);
  cachedId=id;cachedInfo=info;cachedAt=now;return info;
 }
 private static PA6PipedClient.Video progressive(StreamInfo info,int preferred){
  VideoStream best=null;int bestScore=Integer.MAX_VALUE;
  for(VideoStream v:info.getVideoStreams()){
   if(v==null||v.isVideoOnly()||!v.isUrl()||v.getFormat()!=MediaFormat.MPEG_4||!isAvc(v.getCodec()))continue;
   String address=v.getContent();
   if(address==null||!address.startsWith("https://")||address.length()>8000)continue;
   int points=score(height(v.getResolution()),preferred);
   if(points<bestScore){best=v;bestScore=points;}
  }
  if(best==null)return null;
  return new PA6PipedClient.Video(info.getName(),info.getUploaderName(),
   best.getContent(),best.getResolution()+" • MP4 H.264 + audio");
 }
 private static PA6PipedClient.Video adaptive(StreamInfo info,int preferred){
  VideoStream video=null;int bestScore=Integer.MAX_VALUE;
  for(VideoStream v:info.getVideoOnlyStreams()){
   if(v==null||!v.isUrl()||v.getFormat()!=MediaFormat.MPEG_4||!isAvc(v.getCodec()))continue;
   String address=v.getContent();
   if(address==null||!address.startsWith("https://")||address.length()>8000)continue;
   int points=score(height(v.getResolution()),preferred);
   if(points<bestScore){video=v;bestScore=points;}
  }
  AudioStream audio=null;int audioScore=Integer.MAX_VALUE;
  for(AudioStream a:info.getAudioStreams()){
   if(a==null||!a.isUrl()||a.getFormat()!=MediaFormat.M4A||!isAac(a.getCodec()))continue;
   String address=a.getContent();
   if(address==null||!address.startsWith("https://")||address.length()>8000)continue;
   int bitrate=a.getAverageBitrate();
   int points=Math.abs((bitrate<=0?128:bitrate)-128);
   if(points<audioScore){audioScore=points;audio=a;}
  }
  if(video==null||audio==null)return null;
  return new PA6PipedClient.Video(info.getName(),info.getUploaderName(),
   video.getContent(),video.getResolution()+" • H.264 + osobne AAC",audio.getContent());
 }
 public static int[] availableQualities(String id)throws Exception{
  StreamInfo info=load(id);java.util.TreeSet<Integer> heights=new java.util.TreeSet<>();
  for(int h:new int[]{240,360,480,720}){
   PA6PipedClient.Video single=progressive(info,h),split=adaptive(info,h);
   if((single!=null&&height(single.quality)==h)||(split!=null&&height(split.quality)==h))heights.add(h);
  }
  int[] result=new int[heights.size()+1];int i=1;for(int h:heights)result[i++]=h;
  return result;
 }
 public static PA6PipedClient.Response<PA6PipedClient.Video> resolve(String id)throws Exception{
  return resolve(id,0);
 }
 public static PA6PipedClient.Response<PA6PipedClient.Video> resolve(String id,int preferredHeight)throws Exception{
  StreamInfo info=load(id);
  PA6PipedClient.Video single=progressive(info,preferredHeight);
  PA6PipedClient.Video split=adaptive(info,preferredHeight);
  PA6PipedClient.Video chosen=single!=null?single:split;
  if(split!=null&&
    (single==null||score(height(split.quality),preferredHeight)<score(height(single.quality),preferredHeight)))
   chosen=split;
  if(chosen!=null){
   Log.i("PA6_NEWPIPE","Selected quality "+chosen.quality+" for "+id);
   return new PA6PipedClient.Response<>(chosen,"NewPipe Extractor");
  }
  String hls=info.getHlsUrl();
  if(hls!=null&&hls.startsWith("https://"))
   return new PA6PipedClient.Response<>(new PA6PipedClient.Video(info.getName(),info.getUploaderName(),hls,"HLS • automatyczna"),"NewPipe Extractor");
  String dash=info.getDashMpdUrl();
  if(dash!=null&&dash.startsWith("https://"))
   return new PA6PipedClient.Response<>(new PA6PipedClient.Video(info.getName(),info.getUploaderName(),dash,"DASH • automatyczna"),"NewPipe Extractor");
  throw new IOException("Brak formatu wideo obsługiwanego przez Androida 6");
 }
 /** Offline downloads only accept a single MP4 with both image and sound.
  * Separate DASH tracks must not be saved as a misleading silent .mp4 file.
  * Only download content the user has the right to save. */
 public static PA6PipedClient.Response<PA6PipedClient.Video> resolveDownload(String id,int preferredHeight)throws Exception{
  PA6PipedClient.Video file=progressive(load(id),preferredHeight);
  if(file==null)throw new IOException("Ten film nie udostępnia połączonego obrazu i dźwięku MP4 do pobrania");
  return new PA6PipedClient.Response<>(file,"NewPipe Extractor");
 }
 private PA6NewPipeClient(){}
}
