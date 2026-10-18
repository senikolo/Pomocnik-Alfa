package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.media.*;
import java.io.*;

/** One streamed transfer at a time; no movie is buffered in RAM. */
public final class PA6DownloadService extends Service {
 private volatile boolean running,stopped;
 private static final int NOTICE=6201;
 private void report(String message,int percent,boolean done){
  getSharedPreferences("pa6_download",0).edit().putString("status",message).apply();
  Notification.Builder n=new Notification.Builder(this).setSmallIcon(android.R.drawable.stat_sys_download)
   .setContentTitle("Alfa Tube • pobieranie MP4").setContentText(message)
   .setStyle(new Notification.BigTextStyle().bigText(message)).setOngoing(!done);
  if(!done)n.setProgress(100,Math.max(0,percent),percent<0);
  ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTICE,n.build());
 }
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  if(intent==null){stopSelf();return START_NOT_STICKY;}
  if(running)return START_NOT_STICKY;
  running=true;
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
  File partial=null;String message;
  try{
   PA6ModernTls.install(this);checkNetwork(wifi);report("Przygotowuję MP4 z obrazem i dźwiękiem…",-1,false);
   PA6PipedClient.Video video=PA6NewPipeClient.resolveDownload(id,quality).data;
   File folder=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
   if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Nie można otworzyć folderu Pobrane.");
   File target=new File(folder,"AlfaTube_"+id+"_"+System.currentTimeMillis()+".mp4");
   partial=new File(target.getPath()+".part");
   okhttp3.Request request=new okhttp3.Request.Builder().url(video.stream)
    .header("User-Agent","Mozilla/5.0").header("Accept-Encoding","identity").build();
   try(okhttp3.Response response=PA6NewPipeClient.downloadClient().newCall(request).execute()){
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
   if(!partial.renameTo(target))throw new IOException("Nie można zapisać gotowego pliku. Sprawdź wolne miejsce.");
   MediaScannerConnection.scanFile(this,new String[]{target.getAbsolutePath()},new String[]{"video/mp4"},null);
   message="Pobieranie zakończone. Pobrane/"+target.getName();
  }catch(Throwable e){
   android.util.Log.w("PA6_DOWNLOAD","Download failed",e);
   message="Nie można pobrać filmu. "+(e instanceof IOException?e.getMessage():"Sprawdź połączenie, wolne miejsce i dostęp do pamięci.");
  }finally{
   if(partial!=null&&partial.exists())partial.delete();
  }
  stopForeground(false);report(message,100,true);running=false;stopSelf();
 }
 static void verifyComplete(File file)throws IOException{
   MediaExtractor extractor=new MediaExtractor();boolean audio=false,videoTrack=false;
   try{
    extractor.setDataSource(file.getAbsolutePath());
    for(int i=0;i<extractor.getTrackCount();i++){
     String mime=extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
     audio|=mime!=null&&mime.startsWith("audio/");videoTrack|=mime!=null&&mime.startsWith("video/");
    }
   }finally{extractor.release();}
   if(!audio||!videoTrack)throw new IOException("Źródło nie dostarczyło kompletnego obrazu i dźwięku. Plik nie został zapisany jako MP4.");
 }
 @Override public void onDestroy(){stopped=true;super.onDestroy();}
 @Override public IBinder onBind(Intent intent){return null;}
}
