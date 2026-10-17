package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import java.util.*;

/**
 * Low-memory synthesized background sound generator for Android 6 (API 23).
 * No recordings, network connection, user tracking, or heavyweight audio engines.
 * White/pink/brown noises are synthetic; rain/fan/waves are simple synthetic textures.
 */
public final class PA6SoundsService extends Service implements AudioManager.OnAudioFocusChangeListener {
 public static final String PLAY="com.senikolo.pa6.SOUNDS_PLAY";
 public static final String STOP="com.senikolo.pa6.SOUNDS_STOP";
 public static final String PREF="pa6_sounds";
 public static final int RATE=22050;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private AudioManager audioManager;
 private volatile AudioTrack currentTrack;
 private volatile int generation=0;
 private Thread thread;

 @Override public IBinder onBind(Intent intent){return null;}

 @Override public int onStartCommand(Intent intent,int flags,int startId) {
  if(intent==null)return START_NOT_STICKY;
  if(STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
  if(!PLAY.equals(intent.getAction()))return START_NOT_STICKY;
  String mode=intent.getStringExtra("mode");
  if(!Arrays.asList("white","pink","brown","rain","fan","waves").contains(mode))mode="white";
  int volume=Math.max(1,Math.min(100,intent.getIntExtra("volume",45)));
  int minutes=intent.getIntExtra("minutes",30);
  if(minutes!=0&&minutes!=15&&minutes!=30&&minutes!=60&&minutes!=120)minutes=30;
  stopWorker();
  audioManager=(AudioManager)getSystemService(AUDIO_SERVICE);
  if(audioManager==null||audioManager.requestAudioFocus(this,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
   stopSelf();return START_NOT_STICKY;
  }
  final String selected=mode;
  final int level=volume,deadlineMinutes=minutes,sequence=generation;
  getSharedPreferences(PREF,0).edit().putString("mode",mode).putInt("volume",volume)
   .putInt("minutes",minutes).putBoolean("playing",true).apply();
  showNotification(label(mode),minutes);
  thread=new Thread(()->synthesize(selected,level,deadlineMinutes,sequence),"pa6-sounds");
  thread.setPriority(Thread.NORM_PRIORITY-1);
  thread.start();
  return START_NOT_STICKY;
 }

 public static String label(String mode){
  if("pink".equals(mode))return "Różowy szum";
  if("brown".equals(mode))return "Brązowy szum";
  if("rain".equals(mode))return "Szum deszczu";
  if("fan".equals(mode))return "Szum wentylatora";
  if("waves".equals(mode))return "Fale morskie";
  return "Biały szum";
 }

 private void showNotification(String name,int minutes){
  Intent open=new Intent(this,PA6SoundsActivity.class);
  PendingIntent back=PendingIntent.getActivity(this,201,open,PendingIntent.FLAG_UPDATE_CURRENT);
  Intent stop=new Intent(this,PA6SoundsService.class).setAction(STOP);
  PendingIntent stopPi=PendingIntent.getService(this,202,stop,PendingIntent.FLAG_UPDATE_CURRENT);
  String text=minutes==0?"Odtwarzanie bez limitu":"Wyłącznik: "+minutes+" min";
  Notification notification=new Notification.Builder(this)
   .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
   .setContentTitle("Alfa • "+name)
   .setContentText(text).setContentIntent(back).setOngoing(true)
   .addAction(android.R.drawable.ic_media_pause,"Zatrzymaj",stopPi).build();
  startForeground(2307,notification);
 }

 private void synthesize(String mode,int volume,int minutes,int seq){
  AudioTrack track=null;
  long until=minutes<=0?Long.MAX_VALUE:SystemClock.elapsedRealtime()+minutes*60000L;
  Random rng=new Random();
  double brown=0,pinkA=0,pinkB=0,pinkC=0,phase=0;
  long sample=0;
  final int min=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
  if(min<=0){handler.post(()->{if(seq==generation)stopSelf();});return;}
  final int frames=2048;
  short[] block=new short[frames];
  try{
   track=new AudioTrack(AudioManager.STREAM_MUSIC,RATE,AudioFormat.CHANNEL_OUT_MONO,
     AudioFormat.ENCODING_PCM_16BIT,Math.max(min*2,frames*4),AudioTrack.MODE_STREAM);
   if(track.getState()!=AudioTrack.STATE_INITIALIZED)throw new IllegalStateException("AudioTrack nie działa");
   currentTrack=track;
   track.play();
   final double gain=0.6*(volume/100.0);
   while(seq==generation&&!Thread.currentThread().isInterrupted()&&SystemClock.elapsedRealtime()<until){
    for(int i=0;i<frames;i++){
     double white=rng.nextDouble()*2-1;
     pinkA=0.997*pinkA+0.003*white;
     pinkB=0.94*pinkB+0.06*white;
     pinkC=0.63*pinkC+0.37*white;
     brown=(brown+0.032*white)/1.032;
     double pink=0.55*pinkB+0.25*pinkC+0.20*pinkA;
     double value;
     if("pink".equals(mode))value=pink*1.5;
     else if("brown".equals(mode))value=brown*2.4;
     else if("rain".equals(mode)){
      // Synthetic rain-like hiss and irregular soft droplets.
      double drop=(rng.nextDouble()>0.9997)?0.65*rng.nextDouble():0;
      value=0.45*white+0.6*pink+drop;
     }else if("fan".equals(mode)){
      phase+=2*Math.PI*75/RATE;
      if(phase>2*Math.PI)phase-=2*Math.PI;
      value=0.52*pink+0.15*Math.sin(phase)+0.12*Math.sin(2*phase);
     }else if("waves".equals(mode)){
      // Smooth, slow amplitude envelope: the waves rise and fall.
      double cycle=(sample++%(RATE*11L))/(RATE*11.0);
      double swell=0.13+0.78*Math.pow((1-Math.cos(2*Math.PI*cycle))/2,1.7);
      value=swell*(0.45*white+0.95*pink);
     }else value=white*0.60;
     double out=value*gain;
     if(out>0.92)out=0.92;if(out< -0.92)out=-0.92;
     block[i]=(short)(out*32767.0);
    }
    int offset=0;
    while(offset<frames&&seq==generation&&!Thread.currentThread().isInterrupted()){
     int count=track.write(block,offset,frames-offset);
     if(count<=0)throw new IllegalStateException("Urządzenie przerwało odtwarzanie");
     offset+=count;
    }
   }
  }catch(Exception ignored) {
  }finally{
   if(track!=null){try{track.pause();track.flush();track.release();}catch(Exception ignored){}}
   if(currentTrack==track)currentTrack=null;
   handler.post(()->{if(seq==generation)stopSelf();});
  }
 }

 private void stopWorker(){
  generation++;
  Thread previous=thread;thread=null;
  if(previous!=null)previous.interrupt();
  AudioTrack old=currentTrack;currentTrack=null;
  if(old!=null)try{old.pause();old.flush();}catch(Exception ignored){}
  getSharedPreferences(PREF,0).edit().putBoolean("playing",false).apply();
 }
 @Override public void onAudioFocusChange(int focus){
  if(focus==AudioManager.AUDIOFOCUS_LOSS)stopSelf();
  else if(focus==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
   AudioTrack t=currentTrack;if(t!=null)try{t.pause();}catch(Exception ignored){}
  }else if(focus==AudioManager.AUDIOFOCUS_GAIN){
   AudioTrack t=currentTrack;if(t!=null)try{t.play();}catch(Exception ignored){}
  }
 }
 @Override public void onDestroy(){
  stopWorker();
  if(audioManager!=null){audioManager.abandonAudioFocus(this);audioManager=null;}
  stopForeground(true);
  super.onDestroy();
 }
}