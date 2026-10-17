package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.text.TextUtils;

/** Small Android 6 foreground audio player. No WebView media dependency. */
public final class PA6RadioService extends Service implements AudioManager.OnAudioFocusChangeListener {
 public static final String ACTION_PLAY="com.senikolo.pa6.PLAY";
 public static final String ACTION_STOP="com.senikolo.pa6.STOP";
 private MediaPlayer player;
 private AudioManager audio;
 private int generation=0;
 @Override public android.os.IBinder onBind(Intent i){return null;}
 @Override public int onStartCommand(Intent i,int flags,int startId){
  if(i==null)return START_NOT_STICKY;
  if(ACTION_STOP.equals(i.getAction())){stopSelf();return START_NOT_STICKY;}
  if(!ACTION_PLAY.equals(i.getAction()))return START_NOT_STICKY;
  final String url=i.getStringExtra("url"),name=i.getStringExtra("name");
  if(url==null||!url.startsWith("https://")||url.length()>2000){stopSelf();return START_NOT_STICKY;}
  final int seq=++generation;
  release();
  audio=(AudioManager)getSystemService(AUDIO_SERVICE);
  if(audio.requestAudioFocus(this,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){stopSelf();return START_NOT_STICKY;}
  showNotification(name==null?"Radio":name,"Łączenie...");
  try{
   player=new MediaPlayer();
   player.setAudioStreamType(AudioManager.STREAM_MUSIC);
   player.setOnPreparedListener(p->{if(seq!=generation)return;p.start();showNotification(name,"Odtwarzanie");});
   player.setOnErrorListener((p,what,extra)->{if(seq==generation){showNotification(name,"Strumień niedostępny");stopSelf();}return true;});
   player.setDataSource(this,Uri.parse(url));
   player.prepareAsync();
  }catch(Exception e){stopSelf();}
  return START_NOT_STICKY;
 }
 private void showNotification(String name,String status){
  Intent stop=new Intent(this,PA6RadioService.class).setAction(ACTION_STOP);
  PendingIntent pi=PendingIntent.getService(this,4,stop,PendingIntent.FLAG_UPDATE_CURRENT);
  Intent open=new Intent(this,PA6RadioActivity.class);
  PendingIntent activity=PendingIntent.getActivity(this,5,open,PendingIntent.FLAG_UPDATE_CURRENT);
  Notification n=new Notification.Builder(this).setSmallIcon(android.R.drawable.ic_media_play)
   .setContentTitle(TextUtils.isEmpty(name)?"Radio • Pomocnik Alfa":name)
   .setContentText(status+" · Dotknij, aby wrócić do radia")
   .setContentIntent(activity).setOngoing(true)
   .addAction(android.R.drawable.ic_menu_close_clear_cancel,"Zatrzymaj",pi).build();
  startForeground(2306,n);
 }
 private void release(){
  if(player!=null){try{player.reset();player.release();}catch(Exception ignored){}player=null;}
  if(audio!=null){audio.abandonAudioFocus(this);audio=null;}
 }
 @Override public void onAudioFocusChange(int focus){if(focus==AudioManager.AUDIOFOCUS_LOSS)stopSelf();else if(player!=null){try{if(focus==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)player.pause();else if(focus==AudioManager.AUDIOFOCUS_GAIN&&!player.isPlaying())player.start();}catch(Exception ignored){}}}
 @Override public void onDestroy(){++generation;release();stopForeground(true);super.onDestroy();}
}
