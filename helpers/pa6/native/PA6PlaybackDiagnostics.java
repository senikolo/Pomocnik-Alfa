package com.ispina.lokalnie;

import android.content.Context;
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Small, local diagnostic record. No network, URLs, searches or user content. */
final class PA6PlaybackDiagnostics {
 private static final Handler main=new Handler(Looper.getMainLooper());
 private static ScheduledExecutorService timer;
 private static int clients;
 private static volatile long lastBeat;
 private static final AtomicBoolean pending=new AtomicBoolean();
 static synchronized void start(Context context){
  if(++clients>1)return;
  final Context app=context.getApplicationContext();lastBeat=SystemClock.elapsedRealtime();pending.set(false);
  timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"pa6-ui-diagnostic");t.setDaemon(true);return t;});
  timer.scheduleWithFixedDelay(new Runnable(){boolean recorded;
   public void run(){
    long delay=SystemClock.elapsedRealtime()-lastBeat;
    if(delay>5000&&!recorded){
     recorded=true;StringBuilder trace=new StringBuilder("Brak odpowiedzi interfejsu przez co najmniej ").append(delay).append(" ms\n");
     for(StackTraceElement frame:Looper.getMainLooper().getThread().getStackTrace()){trace.append(frame.toString()).append('\n');if(trace.length()>6000)break;}
     app.getSharedPreferences("pa6_player_diagnostics",0).edit().putString("last_stall",trace.toString()).putLong("stall_time",System.currentTimeMillis()).apply();
     android.util.Log.w("PA6_UI",trace.toString());
    }else if(delay<2000)recorded=false;
    if(pending.compareAndSet(false,true))main.post(()->{lastBeat=SystemClock.elapsedRealtime();pending.set(false);});
   }
  },1,1,TimeUnit.SECONDS);
 }
 static synchronized void stop(){if(clients>0&&--clients==0&&timer!=null){timer.shutdownNow();timer=null;}}
 static String report(Context context,String failure){
  String version="";try{version=context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName;}catch(Exception ignored){}
  String report="Pomocnik Alfa 6 • "+version+"\n"+Build.MANUFACTURER+" "+Build.MODEL+" • Android "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")\n\n"+(failure.isEmpty()?"Brak zapisanego błędu odtwarzacza.":failure);
  String stall=context.getSharedPreferences("pa6_player_diagnostics",0).getString("last_stall","");
  return report+(stall.isEmpty()?"":"\n\nOstatnie zatrzymanie interfejsu:\n"+stall);
 }
}
