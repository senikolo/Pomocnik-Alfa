package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.media.AudioManager;
import android.media.session.*;
import android.os.*;
import org.videolan.libvlc.*;
import java.util.ArrayList;

/** Foreground audio, independent of the activity and its video surface. */
public final class PA6AudioService extends Service {
 static volatile boolean active,paused;
 static volatile String title="",videoId="",status="";
 static volatile long position,duration;
 private LibVLC vlc;private MediaPlayer player;private MediaSession session;
 private AudioManager audio;private PowerManager.WakeLock wake;private boolean focus,resume;
 private final Handler main=new Handler();
 private final BroadcastReceiver noisy=new BroadcastReceiver(){public void onReceive(Context c,Intent i){pause();}};
 private final AudioManager.OnAudioFocusChangeListener focusListener=change->{
  if(player==null)return;
  if(change==AudioManager.AUDIOFOCUS_GAIN){if(resume){resume=false;play();}}
  else {resume=change!=AudioManager.AUDIOFOCUS_LOSS&&!paused;pause();if(change==AudioManager.AUDIOFOCUS_LOSS)focus=false;}
 };
 static void command(Context c,String action){c.startService(new Intent(c,PA6AudioService.class).setAction(action));}
 static void start(Context c,String id,String name,String url){c.startService(new Intent(c,PA6AudioService.class).setAction("PLAY").putExtra("id",id).putExtra("title",name).putExtra("url",url));}
 static void seek(Context c,long time){c.startService(new Intent(c,PA6AudioService.class).setAction("SEEK").putExtra("time",time));}
 @Override public void onCreate(){
  super.onCreate();registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));audio=(AudioManager)getSystemService(AUDIO_SERVICE);
  wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"PA6:audio");wake.setReferenceCounted(false);
  session=new MediaSession(this,"Alfa Tube");session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
  session.setCallback(new MediaSession.Callback(){public void onPlay(){play();}public void onPause(){pause();}public void onStop(){stopSelf();}public void onSeekTo(long t){seekTo(t);}});session.setActive(true);
 }
 private PendingIntent action(String a,int n){return PendingIntent.getService(this,n,new Intent(this,PA6AudioService.class).setAction(a),PendingIntent.FLAG_UPDATE_CURRENT);}
 private Notification notice(){
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,PA6TubeActivity.class),PendingIntent.FLAG_UPDATE_CURRENT);
  return new Notification.Builder(this).setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title).setContentText(status)
   .setContentIntent(open).setOngoing(!paused).setShowWhen(false)
   .addAction(paused?android.R.drawable.ic_media_play:android.R.drawable.ic_media_pause,paused?"Wznów":"Pauza",action("TOGGLE",1))
   .addAction(android.R.drawable.ic_menu_close_clear_cancel,"Zatrzymaj",action("STOP",2))
   .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1)).build();
 }
 private void publish(){
  if(!active)return;
  if(player!=null){position=Math.max(0,player.getTime());duration=Math.max(0,player.getLength());}
  session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_STOP|PlaybackState.ACTION_SEEK_TO)
   .setState(paused?PlaybackState.STATE_PAUSED:PlaybackState.STATE_PLAYING,position,paused?0:1).build());
  ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(6202,notice());
 }
 private final Runnable tick=new Runnable(){public void run(){publish();if(active)main.postDelayed(this,1000);}};
 private void acquire(){if(!wake.isHeld())wake.acquire();}
 private void pause(){if(player!=null){player.pause();paused=true;android.util.Log.i("PA6_AUDIO","PAUSED");status="Wstrzymano • samo audio";if(wake.isHeld())wake.release();publish();}}
 private void play(){if(player==null)return;if(!focus){focus=audio.requestAudioFocus(focusListener,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;}if(!focus){status="Dźwięk zajmuje inna funkcja";publish();return;}paused=false;status="Słuchasz w tle • samo audio";acquire();player.play();publish();}
 private void seekTo(long t){if(player!=null&&player.isSeekable()&&player.getLength()>0){player.setTime(Math.max(0,Math.min(t,player.getLength()-1)));android.util.Log.i("PA6_AUDIO","SEEK_REQUEST="+t);}publish();}
 @Override public int onStartCommand(Intent i,int flags,int startId){
  if(i==null){stopSelf();return START_NOT_STICKY;}String a=i.getAction();
  if("STOP".equals(a)){stopSelf();return START_NOT_STICKY;}
  if("PLAY".equals(a)&&i.hasExtra("url")){
   String url=i.getStringExtra("url");if(url==null||!(url.startsWith("https://")||url.startsWith("file://"))){stopSelf();return START_NOT_STICKY;}
   title=i.getStringExtra("title");videoId=i.getStringExtra("id");active=true;paused=false;status="Łączenie • samo audio";position=duration=0;
   session.setMetadata(new android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE,title).putString(android.media.MediaMetadata.METADATA_KEY_ARTIST,"Alfa Tube").build());
   startForeground(6202,notice());
   try{
    if(player!=null){player.setEventListener(null);player.stop();player.release();vlc.release();}
    vlc=new LibVLC(this,new ArrayList<String>());player=new MediaPlayer(vlc);
    final MediaPlayer selected=player;player.setEventListener(e->main.post(()->{if(!active||player!=selected)return;
     if(e.type==MediaPlayer.Event.Playing){status="Słuchasz w tle • samo audio";android.util.Log.i("PA6_AUDIO","BACKGROUND_PLAYING");publish();}
     else if(e.type==MediaPlayer.Event.EncounteredError){status="Nie można odtworzyć audio. Otwórz Alfa Tube i spróbuj ponownie.";pause();status="Błąd źródła audio • spróbuj ponownie";publish();}
     else if(e.type==MediaPlayer.Event.EndReached){stopSelf();}
    }));
    Media m=new Media(vlc,android.net.Uri.parse(url));m.addOption(":no-video");m.addOption(":network-caching=1500");m.addOption(":http-reconnect=false");m.addOption(":avcodec-hw=none");player.setMedia(m);m.release();play();
    main.removeCallbacks(tick);main.post(tick);
   }catch(Throwable e){android.util.Log.e("PA6_AUDIO","START_FAILED",e);stopSelf();}
  }else if(!active){stopSelf();}
  else if("TOGGLE".equals(a)){if(paused)play();else pause();}
  else if("PAUSE".equals(a))pause();else if("RESUME".equals(a))play();else if("SEEK".equals(a))seekTo(i.getLongExtra("time",0));
  return START_NOT_STICKY;
 }
 @Override public void onDestroy(){unregisterReceiver(noisy);active=false;paused=false;videoId="";main.removeCallbacksAndMessages(null);if(player!=null){player.setEventListener(null);player.stop();player.release();player=null;}if(vlc!=null)vlc.release();if(focus)audio.abandonAudioFocus(focusListener);if(wake.isHeld())wake.release();session.release();stopForeground(true);super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
