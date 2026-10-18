package com.ispina.lokalnie;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import java.util.ArrayList;

/** Embedded LibVLC. Decoding and controls remain INSIDE Pomocnik Alfa. */
public final class PA6EmbeddedPlayer extends LinearLayout {
 private final SurfaceView screen;
 private TextView state;
 private final TextView placeholder;
 private LibVLC vlc;
 private MediaPlayer engine;
 private final Handler ui=new Handler(Looper.getMainLooper());
 private String active="";
 private Runnable playbackErrorListener;
 public void setPlaybackErrorListener(Runnable listener){playbackErrorListener=listener;}
 private boolean ready=false;
 private AudioManager audio;
 private boolean hasFocus=false;
 private final AudioManager.OnAudioFocusChangeListener focusListener=change->{
  ui.post(()->{
   if(engine==null)return;
   if(change==AudioManager.AUDIOFOCUS_LOSS || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){
    try{engine.pause();}catch(Exception ignored){}
    state.setText("Odtwarzanie wstrzymane — dźwięk przejęła inna funkcja.");
    if(change==AudioManager.AUDIOFOCUS_LOSS)hasFocus=false;
   }
  });
 };
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
 public PA6EmbeddedPlayer(Context context){
  super(context);setOrientation(VERTICAL);
  setBackgroundColor(0xff101b31);
  // Keep the video genuinely 16:9 on 10-inch landscape tablets.
  // A fixed 218dp frame looked panoramic and wasted decoder output on Acer.
  FrameLayout frame=new FrameLayout(context){
   @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec){
    int width=MeasureSpec.getSize(widthMeasureSpec);
    int videoHeight=Math.max(dp(130),Math.round(width*9f/16f));
    super.onMeasure(widthMeasureSpec,MeasureSpec.makeMeasureSpec(videoHeight,MeasureSpec.EXACTLY));
   }
  };
  frame.setBackgroundColor(Color.BLACK);
  screen=new SurfaceView(context);screen.setKeepScreenOn(true);
  frame.addView(screen,new FrameLayout.LayoutParams(-1,-1));
  screen.addOnLayoutChangeListener((v,l,t,r,b,oldL,oldT,oldR,oldB)->applyVideoGeometry());
  screen.getHolder().addCallback(new android.view.SurfaceHolder.Callback(){
   @Override public void surfaceCreated(android.view.SurfaceHolder holder){}
   @Override public void surfaceChanged(android.view.SurfaceHolder holder,int format,int w,int h){applyVideoGeometry();}
   @Override public void surfaceDestroyed(android.view.SurfaceHolder holder){}
  });
  placeholder=new TextView(context);placeholder.setText("▶  ALFA • wybierz materiał");
  placeholder.setGravity(Gravity.CENTER);placeholder.setTextSize(18);placeholder.setTextColor(0xffc6d2e7);
  placeholder.setBackgroundColor(0xff111827);
  frame.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
  addView(frame,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout controls=new LinearLayout(context);
  controls.setGravity(Gravity.CENTER_VERTICAL);
  state=new TextView(context);state.setTextColor(0xfff4f6fc);state.setTextSize(13);
  state.setText("Odtwarzacz wbudowany. Wybierz materiał.");
  controls.addView(state,new LinearLayout.LayoutParams(0,-2,1));
  Button pause=new Button(context);pause.setText("⏯");pause.setTextColor(0xfff4f6fc);
  pause.setBackgroundColor(0xff31476a);
  pause.setOnClickListener(v->{if(engine!=null){try{engine.pause();}catch(Exception ignored){}}});
  controls.addView(pause,new LinearLayout.LayoutParams(dp(60),dp(50)));
  Button stop=new Button(context);stop.setText("■");stop.setTextColor(0xfff4f6fc);
  stop.setBackgroundColor(0xff31476a);
  stop.setOnClickListener(v->stop());
  controls.addView(stop,new LinearLayout.LayoutParams(dp(56),dp(50)));
  addView(controls,new LinearLayout.LayoutParams(-1,dp(60)));
 }
 private void applyVideoGeometry(){
  if(engine==null)return;
  int width=screen.getWidth(),height=screen.getHeight();
  if(width<=0||height<=0)return;
  try{
   engine.getVLCVout().setWindowSize(width,height);
   engine.setAspectRatio(null);
   engine.setScale(0f);
  }catch(Throwable e){android.util.Log.w("PA6_PLAYER","Video geometry pending",e);}
 }
 private void init(){
  if(ready)return;
  ArrayList<String> options=new ArrayList<>();
  options.add("--no-video-title-show");
  options.add("--network-caching=1200");
  vlc=new LibVLC(getContext(),options);
  engine=new MediaPlayer(vlc);
  engine.getVLCVout().setVideoView(screen);
  engine.getVLCVout().attachViews();
  applyVideoGeometry();
  engine.setEventListener(event->ui.post(()->{
   if(event.type==MediaPlayer.Event.Playing){
    applyVideoGeometry();
    android.util.Log.i("PA6_PLAYER","PLAYING "+active);
    state.setText("▶ Odtwarzanie: "+active);
   }
   else if(event.type==MediaPlayer.Event.Buffering)state.setText("Buforowanie • "+active);
   else if(event.type==MediaPlayer.Event.EncounteredError){
    android.util.Log.w("PA6_PLAYER","PLAYBACK_ERROR "+active);
    state.setText("Błąd transmisji. Sprawdzam inne źródło, jeśli jest dostępne.");
    placeholder.setVisibility(VISIBLE);
    if(playbackErrorListener!=null)playbackErrorListener.run();
   }
   else if(event.type==MediaPlayer.Event.EndReached){state.setText("Zakończono odtwarzanie.");placeholder.setVisibility(VISIBLE);}
  }));
  ready=true;
 }
 public void play(String name,String address){
  if(address==null||address.length()>8000||!(address.startsWith("https://")||address.startsWith("http://"))
      ||address.contains("\n")||address.contains(" ")){state.setText("Nieprawidłowy adres materiału.");return;}
  playUri(name,Uri.parse(address));
 }
 public void playUri(String name,Uri address){
  active=name;state.setText("Łączenie • "+name);
  try{
   init();
   if(audio==null)audio=(AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);
   if(audio!=null&&!hasFocus){
    int gain=audio.requestAudioFocus(focusListener,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);
    hasFocus=(gain==AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    if(!hasFocus){state.setText("Inne audio zajmuje dźwięk. Zatrzymaj radio.");return;}
   }
   engine.stop();
   placeholder.setVisibility(GONE);
   Media media=new Media(vlc,address);
   media.addOption(":network-caching=1500");
   media.addOption(":avcodec-hw=none"); // Acer Android 6: avoid audio-only hardware decoding failures.
   media.addOption(":http-reconnect=true");
   engine.setMedia(media);
   media.release();
   engine.play();
  }catch(Throwable e){
   android.util.Log.e("PA6_PLAYER","PLAYBACK_START_ERROR",e);
   placeholder.setVisibility(VISIBLE);
   state.setText("Nie udało się uruchomić odtwarzacza: "+e.getClass().getSimpleName());
  }
 }
 public boolean verifyNative(){
  try{init();android.util.Log.i("PA6_LIBVLC","NATIVE_ENGINE_READY");state.setText("Silnik wideo jest gotowy.");return true;}
  catch(Throwable e){android.util.Log.e("PA6_LIBVLC","INIT_FAILED",e);state.setText("Błąd inicjalizacji silnika wideo: "+e.getClass().getSimpleName());return false;}
 }
 public void stop(){
  try{if(engine!=null)engine.stop();}catch(Throwable ignored){}
  state.setText("Zatrzymano.");
  placeholder.setVisibility(VISIBLE);
  if(audio!=null&&hasFocus){try{audio.abandonAudioFocus(focusListener);}catch(Exception ignored){}hasFocus=false;}
 }
 public void release(){
  playbackErrorListener=null;
  stop();
  if(audio!=null&&hasFocus){try{audio.abandonAudioFocus(focusListener);}catch(Exception ignored){}hasFocus=false;}
  try{if(engine!=null){engine.getVLCVout().detachViews();engine.release();}}catch(Throwable ignored){}
  try{if(vlc!=null)vlc.release();}catch(Throwable ignored){}
  engine=null;vlc=null;ready=false;audio=null;
 }
}
