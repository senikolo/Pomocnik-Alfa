package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.text.TextUtils;

/** Native, small-memory Android 6 radio playback with visible error reporting. */
public final class PA6RadioService extends Service implements AudioManager.OnAudioFocusChangeListener {
 public static final String ACTION_PLAY="com.senikolo.pa6.PLAY";
 public static final String ACTION_STOP="com.senikolo.pa6.STOP";
 public static final String ACTION_STATE="com.senikolo.pa6.RADIO_STATE";
 private MediaPlayer player;
 private AudioManager audio;
 private int generation=0;
 private String station="Radio";
 @Override public IBinder onBind(Intent intent){return null;}
 private boolean valid(String url){return url!=null&&url.length()<2000
    &&(url.startsWith("https://")||url.startsWith("http://"))
    &&!url.contains(" ")&&!url.contains("\n")&&!url.contains("\r");}
 private void report(String message){
  Intent b=new Intent(ACTION_STATE);
  b.setPackage(getPackageName());
  b.putExtra("message",message);
  sendBroadcast(b);
 }
 @Override public int onStartCommand(Intent i,int flags,int startId){
  if(i==null)return START_NOT_STICKY;
  if(ACTION_STOP.equals(i.getAction())){report("Zatrzymano radio.");stopSelf();return START_NOT_STICKY;}
  if(!ACTION_PLAY.equals(i.getAction()))return START_NOT_STICKY;
  String url=i.getStringExtra("url");
  station=i.getStringExtra("name");
  if(TextUtils.isEmpty(station))station="Radio";
  if(!valid(url)){report("Nieprawidłowy adres stacji.");stopSelf();return START_NOT_STICKY;}
  final int seq=++generation;
  release();
  audio=(AudioManager)getSystemService(AUDIO_SERVICE);
  if(audio==null||audio.requestAudioFocus(this,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
   report("Inna aplikacja używa dźwięku. Zatrzymaj ją i spróbuj ponownie.");stopSelf();return START_NOT_STICKY;
  }
  report("Łączenie z "+station+"…");
  showNotification(station,"Łączenie…");
  try{
   player=new MediaPlayer();
   player.setAudioStreamType(AudioManager.STREAM_MUSIC);
   player.setOnPreparedListener(p->{
    if(seq!=generation)return;
    try{p.start();report("Teraz gra: "+station);showNotification(station,"Odtwarzanie");}
    catch(Exception ex){report("Nie udało się uruchomić dźwięku tej stacji.");stopSelf();}
   });
   player.setOnErrorListener((p,what,extra)->{
    if(seq==generation){report("Ta stacja nie odtwarza się na tablecie (błąd "+what+"/"+extra+"). Wybierz inną.");stopSelf();}
    return true;
   });
   player.setOnInfoListener((p,what,extra)->{
    if(what==MediaPlayer.MEDIA_INFO_BUFFERING_START)report("Buforuję: "+station+"…");
    else if(what==MediaPlayer.MEDIA_INFO_BUFFERING_END)report("Teraz gra: "+station);
    return false;
   });
   player.setDataSource(this,Uri.parse(url));
   player.prepareAsync();
  }catch(Exception e){
   report("Nie można połączyć ze stacją. Sprawdź internet, godzinę systemową lub wybierz inną stację.");
   stopSelf();
  }
  return START_NOT_STICKY;
 }
 private void showNotification(String name,String status){
  PendingIntent stop=PendingIntent.getService(this,4,new Intent(this,PA6RadioService.class).setAction(ACTION_STOP),PendingIntent.FLAG_UPDATE_CURRENT);
  PendingIntent open=PendingIntent.getActivity(this,5,new Intent(this,PA6RadioActivity.class),PendingIntent.FLAG_UPDATE_CURRENT);
  Notification n=new Notification.Builder(this)
   .setSmallIcon(android.R.drawable.ic_media_play)
   .setContentTitle(name).setContentText(status+" · Dotknij, aby wrócić do radia")
   .setContentIntent(open).setOngoing(true)
   .addAction(android.R.drawable.ic_menu_close_clear_cancel,"Zatrzymaj",stop).build();
  startForeground(2306,n);
 }
 private void release(){
  if(player!=null){try{player.reset();player.release();}catch(Exception ignored){}player=null;}
  if(audio!=null){audio.abandonAudioFocus(this);audio=null;}
 }
 @Override public void onAudioFocusChange(int focus){
  if(focus==AudioManager.AUDIOFOCUS_LOSS){report("Radio zatrzymano — inna aplikacja przejęła dźwięk.");stopSelf();}
  else if(player!=null){
   try{
    if(focus==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)player.pause();
    else if(focus==AudioManager.AUDIOFOCUS_GAIN&&!player.isPlaying())player.start();
   }catch(Exception ignored){}
  }
 }
 @Override public void onDestroy(){++generation;release();stopForeground(true);super.onDestroy();}
}
