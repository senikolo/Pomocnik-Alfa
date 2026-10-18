package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.media.*;
import java.io.*;

/** One streamed transfer at a time; no movie is buffered in RAM. */
public final class PA6DownloadService extends Service {
 private volatile boolean running,stopped;private volatile okhttp3.Call call;
 private volatile PA6NewPipeClient.Work work;
 private org.json.JSONObject record;private String key;
 @Override public void onCreate(){super.onCreate();org.json.JSONArray old=PA6DownloadStore.list(this);for(int i=0;i<old.length();i++){org.json.JSONObject r=old.optJSONObject(i);if(r!=null&&"running".equals(r.optString("state")))try{r.put("state","failed");r.put("message","Pobieranie przerwane. Możesz ponowić.");PA6DownloadStore.put(this,r);}catch(Exception ignored){}}}
 private void save(String state,String message,int percent){if(record==null)return;try{record.put("state",state);record.put("message",message);record.put("percent",percent);PA6DownloadStore.put(this,record);}catch(Exception ignored){}}

 private static final int NOTICE=6201;
 private void report(String message,int percent,boolean done){
  getSharedPreferences("pa6_download",0).edit().putString("status",message).apply();
  Notification.Builder n=new Notification.Builder(this).setSmallIcon(android.R.drawable.stat_sys_download)
   .setContentTitle("Alfa Tube • pobieranie MP4").setContentText(message)
   .setStyle(new Notification.BigTextStyle().bigText(message)).setOngoing(!done);
  n.setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,PA6DownloadsActivity.class),PendingIntent.FLAG_UPDATE_CURRENT));
  if(!done)n.addAction(android.R.drawable.ic_menu_close_clear_cancel,"Anuluj",PendingIntent.getService(this,0,new Intent(this,PA6DownloadService.class).setAction("CANCEL").putExtra("key",key),PendingIntent.FLAG_UPDATE_CURRENT));
  if(!done){save("running",message,percent);}
  if(!done)n.setProgress(100,Math.max(0,percent),percent<0);
  ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTICE,n.build());
 }
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  if(intent==null){stopSelf();return START_NOT_STICKY;}
  if("CANCEL".equals(intent.getAction())){if(running&&key.equals(intent.getStringExtra("key"))){stopped=true;if(work!=null)work.cancel();if(call!=null)call.cancel();}else if(!running)stopSelf();return START_NOT_STICKY;}
  if(running){getSharedPreferences("pa6_download",0).edit().putString("status","Trwa inne pobieranie. Poczekaj lub anuluj je na liście Pobrane.").apply();return START_NOT_STICKY;}
  running=true;stopped=false;work=new PA6NewPipeClient.Work();key=Long.toString(System.currentTimeMillis());
  record=new org.json.JSONObject();try{record.put("key",key);record.put("id",intent.getStringExtra("id"));record.put("quality",intent.getIntExtra("quality",0));record.put("wifi",intent.getBooleanExtra("wifi",true));record.put("title","Film "+intent.getStringExtra("id"));}catch(Exception ignored){}
  save("running","Przygotowuję pobieranie…",-1);
  startForeground(NOTICE,new Notification.Builder(this).setSmallIcon(android.R.drawable.stat_sys_download)
   .setContentTitle("Alfa Tube").setContentText("Przygotowuję pobieranie…").build());
  String id=intent.getStringExtra("id");int quality=intent.getIntExtra("quality",0);
  boolean wifi=intent.getBooleanExtra("wifi",true);
  new Thread(()->transfer(id,quality,wifi),"PA6-MP4-download").start();
  return START_NOT_STICKY;
 }
 private void checkNetwork(boolean wifi)throws IOException{
  NetworkInfo n=((ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
  if(n==null||!n.isConnected())throw new IOException("Brak połączenia z internetem. Spróbuj ponownie po połączeniu.");
  if(wifi&&n.getType()!=ConnectivityManager.TYPE_WIFI)throw new IOException("Włącz Wi-Fi lub wyłącz opcję „Pobieraj tylko przez Wi-Fi” w ustawieniach Alfa Tube.");
 }
 private void transfer(String id,int quality,boolean wifi){
  File partial=null;String message;PA6NewPipeClient.enterWork(work);
  try{
   PA6ModernTls.install(this);checkNetwork(wifi);report("Przygotowuję MP4 z obrazem i dźwiękiem…",-1,false);
   PA6PipedClient.Video video=PA6NewPipeClient.resolveDownload(id,quality).data;
   if(stopped)throw new IOException("Anulowano pobieranie.");
   record.put("title",video.title);
   File folder=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
   if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Nie można otworzyć folderu Pobrane.");
   File target=new File(folder,"AlfaTube_"+id+"_"+System.currentTimeMillis()+".mp4");
   partial=new File(target.getPath()+".part");
   okhttp3.Request request=new okhttp3.Request.Builder().url(video.stream)
    .header("User-Agent","Mozilla/5.0").header("Accept-Encoding","identity").build();
   call=PA6NewPipeClient.downloadClient().newCall(request);
   if(stopped)throw new IOException("Anulowano pobieranie.");
   try(okhttp3.Response response=call.execute()){
    if(!response.isSuccessful()||response.body()==null)throw new IOException("Serwer odmówił pobrania (HTTP "+response.code()+"). Wybierz film ponownie i spróbuj jeszcze raz.");
    long expected=response.body().contentLength(),read=0,last=0;
    try(InputStream in=response.body().byteStream();FileOutputStream out=new FileOutputStream(partial)){
     byte[] buffer=new byte[32768];int count;
     while((count=in.read(buffer))!=-1){
      if(stopped)throw new IOException("Pobieranie przerwane. Spróbuj ponownie.");
      out.write(buffer,0,count);read+=count;
      long now=SystemClock.elapsedRealtime();
      if(now-last>1000){checkNetwork(wifi);last=now;
       int percent=expected>0?(int)Math.min(99,read*100/expected):-1;
       report("Pobieranie: "+(percent>=0?percent+"% • ":"")+read/1048576+" MB",percent,false);
      }
     }
    }
    if(read==0||(expected>=0&&read!=expected))throw new IOException("Plik jest niekompletny. Spróbuj ponownie.");
   }
   report("Sprawdzam obraz i dźwięk MP4…",-1,false);
   verifyComplete(partial);
   if(stopped)throw new IOException("Anulowano pobieranie.");
   if(!partial.renameTo(target))throw new IOException("Nie można zapisać gotowego pliku. Sprawdź wolne miejsce.");
   MediaScannerConnection.scanFile(this,new String[]{target.getAbsolutePath()},new String[]{"video/mp4"},null);
   record.put("path",target.getAbsolutePath());save("complete","Pobieranie zakończone",100);
   message="Pobieranie zakończone. Pobrane/"+target.getName();
  }catch(Throwable e){
   android.util.Log.w("PA6_DOWNLOAD","Download failed",e);
   message="Nie można pobrać filmu. "+(e instanceof IOException?e.getMessage():"Sprawdź połączenie, wolne miejsce i dostęp do pamięci.");
   if(stopped)message="Anulowano pobieranie. Możesz ponowić.";
   save(stopped?"cancelled":"failed",message,0);
  }finally{
   PA6NewPipeClient.leaveWork();if(partial!=null&&partial.exists())partial.delete();
  }
  final String done=message;new Handler(Looper.getMainLooper()).post(()->{stopForeground(false);report(done,100,true);running=false;call=null;work=null;stopSelf();});
 }
 static void verifyComplete(File file)throws IOException{
   MediaExtractor extractor=new MediaExtractor();boolean audio=false,videoTrack=false;
   try(FileInputStream input=new FileInputStream(file)){
    extractor.setDataSource(input.getFD());
    for(int i=0;i<extractor.getTrackCount();i++){
     String mime=extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
     audio|=mime!=null&&mime.startsWith("audio/");videoTrack|=mime!=null&&mime.startsWith("video/");
    }
   }finally{extractor.release();}
   if(!audio||!videoTrack)throw new IOException("Źródło nie dostarczyło kompletnego obrazu i dźwięku. Plik nie został zapisany jako MP4.");
 }
 @Override public void onDestroy(){stopped=true;if(work!=null)work.cancel();if(call!=null)call.cancel();super.onDestroy();}
 @Override public IBinder onBind(Intent intent){return null;}
}
