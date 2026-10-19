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
 private FrameLayout videoFrame;
 private TextView state;
 private final TextView placeholder;
 private LibVLC vlc;
 private MediaPlayer engine;
 private final Handler ui=new Handler(Looper.getMainLooper());
 private String active="";
 private boolean audioOnly=false,backgroundAudio=false,fullscreen=false;
 private Runnable fullscreenListener;private android.widget.SeekBar timeline;private TextView clock;
 public long playbackPosition(){return time();}
 public boolean isPlaying(){return backgroundAudio?PA6AudioService.active&&!PA6AudioService.paused:engine!=null&&engine.isPlaying();}
 public void setFullscreenListener(Runnable r){fullscreenListener=r;}
 public void setFullscreen(boolean value){fullscreen=value;if(videoFrame!=null)videoFrame.setLayoutParams(new LinearLayout.LayoutParams(-1,value?0:-2,value?1:0));requestLayout();}
 public void detachSurface(){try{if(engine!=null)engine.getVLCVout().detachViews();}catch(Throwable ignored){}}
 public void attachSurface(){try{if(engine!=null&&!engine.getVLCVout().areViewsAttached()){engine.getVLCVout().setVideoView(screen);engine.getVLCVout().attachViews();applyVideoGeometry();}}catch(Throwable ignored){}}
 public void showMessage(String message){placeholder.setText(message);placeholder.setTextSize(17);placeholder.setVisibility(VISIBLE);}
 public void backgroundAudio(String name){releaseLocal();backgroundAudio=true;active=name;setAudioOnly(true);state.setText("Samo audio • działa również po wygaszeniu ekranu");}
 private long time(){return backgroundAudio?PA6AudioService.position:engine==null?0:Math.max(0,engine.getTime());}
 private long length(){return backgroundAudio?PA6AudioService.duration:engine==null?0:Math.max(0,engine.getLength());}
 private void seek(long t){long len=length();if(len<=0)return;t=Math.max(0,Math.min(t,len-1));if(backgroundAudio)PA6AudioService.seek(getContext(),t);else if(engine!=null&&engine.isSeekable())engine.setTime(t);}
 private static String stamp(long ms){long sec=ms/1000;return String.format(java.util.Locale.ROOT,"%d:%02d",sec/60,sec%60);}
 private final Runnable tick=new Runnable(){public void run(){screen.setKeepScreenOn(!audioOnly&&!backgroundAudio&&engine!=null&&engine.isPlaying());long len=length(),pos=time();timeline.setEnabled(len>0);if(!timeline.isPressed())timeline.setProgress(len>0?(int)(pos*1000/len):0);clock.setText(stamp(pos)+" / "+(len>0?stamp(len):"na żywo"));if(backgroundAudio)state.setText(PA6AudioService.active?PA6AudioService.status:"Audio zatrzymane");ui.postDelayed(this,1000);}};
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();ui.removeCallbacks(tick);ui.post(tick);}
 @Override protected void onDetachedFromWindow(){ui.removeCallbacks(tick);super.onDetachedFromWindow();}

 public void setAudioOnly(boolean value){
  audioOnly=value;screen.setKeepScreenOn(!value && engine!=null && engine.isPlaying());
  if(value){placeholder.setText("♫\nSamo audio\nSłuchaj bez obrazu");placeholder.setTextSize(22);placeholder.setBackgroundColor(0xff123b45);placeholder.setVisibility(VISIBLE);}
  else{placeholder.setText("▶  Wybierz film");placeholder.setTextSize(18);placeholder.setBackgroundColor(0xff111827);}
  requestLayout();
 }

 private Runnable playbackErrorListener,playbackStartListener;
 public void setPlaybackErrorListener(Runnable listener){playbackErrorListener=listener;}
 public void setPlaybackStartListener(Runnable listener){playbackStartListener=listener;}
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
    if(fullscreen){super.onMeasure(widthMeasureSpec,heightMeasureSpec);return;}
    int width=MeasureSpec.getSize(widthMeasureSpec);
    int videoHeight=fullscreen?Math.max(dp(100),getResources().getDisplayMetrics().heightPixels-dp(180)):audioOnly?dp(180):Math.max(dp(130),Math.round(width*9f/16f));
    super.onMeasure(widthMeasureSpec,MeasureSpec.makeMeasureSpec(videoHeight,MeasureSpec.EXACTLY));
   }
  };
  videoFrame=frame;frame.setBackgroundColor(Color.BLACK);
  screen=new SurfaceView(context);screen.setKeepScreenOn(false);
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
  state.setText("Gotowy do odtwarzania");state.setPadding(dp(12),0,dp(8),0);
  controls.addView(state,new LinearLayout.LayoutParams(0,-2,1));
  Button pause=new Button(context);pause.setText("Ⅱ / ▶");pause.setTextSize(14);pause.setContentDescription("Pauza lub wznów odtwarzanie");pause.setTextColor(0xfff4f6fc);
  pause.setBackgroundColor(0xff31476a);
  pause.setOnClickListener(v->{if(backgroundAudio){PA6AudioService.command(getContext(),"TOGGLE");}else if(engine!=null){try{if(engine.isPlaying())engine.pause();else engine.play();}catch(Exception ignored){}}});
  controls.addView(pause,new LinearLayout.LayoutParams(dp(60),dp(50)));
  Button stop=new Button(context);stop.setText("■");stop.setContentDescription("Zatrzymaj odtwarzanie");stop.setTextColor(0xfff4f6fc);
  stop.setBackgroundColor(0xff31476a);
  stop.setOnClickListener(v->stop());
  controls.addView(stop,new LinearLayout.LayoutParams(dp(56),dp(50)));
  addView(controls,new LinearLayout.LayoutParams(-1,dp(60)));
  LinearLayout transport=new LinearLayout(context);transport.setGravity(Gravity.CENTER_VERTICAL);
  Button back=new Button(context);back.setText("−10 s");back.setAllCaps(false);back.setOnClickListener(v->seek(time()-10000));transport.addView(back,new LinearLayout.LayoutParams(dp(76),dp(48)));
  clock=new TextView(context);clock.setTextColor(0xfff4f6fc);clock.setTextSize(12);clock.setGravity(Gravity.CENTER);transport.addView(clock,new LinearLayout.LayoutParams(0,dp(48),1));
  Button next=new Button(context);next.setText("+10 s");next.setAllCaps(false);next.setOnClickListener(v->seek(time()+10000));transport.addView(next,new LinearLayout.LayoutParams(dp(76),dp(48)));
  Button full=new Button(context);full.setText("⛶");full.setContentDescription("Pełny ekran / powrót");full.setOnClickListener(v->{if(fullscreenListener!=null)fullscreenListener.run();});transport.addView(full,new LinearLayout.LayoutParams(dp(52),dp(48)));addView(transport);
  timeline=new android.widget.SeekBar(context);timeline.setMax(1000);timeline.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(android.widget.SeekBar b){}public void onProgressChanged(android.widget.SeekBar b,int p,boolean user){}public void onStopTrackingTouch(android.widget.SeekBar b){seek(length()*b.getProgress()/1000);}});addView(timeline,new LinearLayout.LayoutParams(-1,dp(40)));
  for(Button b:new Button[]{pause,stop,back,next,full}){b.setAllCaps(false);b.setTextColor(0xffe8f2fa);b.setPadding(0,0,0,0);b.setStateListAnimator(null);b.setBackground(PA6Premium.shape(context,0xff294563,0xff355372,12));PA6Premium.touch(b);}
  timeline.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff88bfe1));timeline.setThumbTintList(android.content.res.ColorStateList.valueOf(0xffbddff2));

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
    android.util.Log.i("PA6_PLAYER","PLAYING "+active);screen.setKeepScreenOn(!audioOnly);
    state.setText("▶ Odtwarzanie: "+active);
    if(playbackStartListener!=null)playbackStartListener.run();
   }
   else if(event.type==MediaPlayer.Event.Buffering&&event.getBuffering()<99f)state.setText("Buforowanie • "+active);
   else if(event.type==MediaPlayer.Event.EncounteredError){
    android.util.Log.w("PA6_PLAYER","PLAYBACK_ERROR "+active);screen.setKeepScreenOn(false);
    state.setText("Błąd transmisji. Sprawdzam inne źródło, jeśli jest dostępne.");
    placeholder.setVisibility(VISIBLE);
    if(playbackErrorListener!=null)playbackErrorListener.run();
   }
   else if(event.type==MediaPlayer.Event.EndReached){screen.setKeepScreenOn(false);state.setText("Zakończono odtwarzanie.");placeholder.setVisibility(VISIBLE);}
  }));
  ready=true;
 }
 public void play(String name,String address){
  play(name,address,"");
 }
 /**
  * Video-only MP4 and its M4A audio stream are played by bundled LibVLC.
  */
 public void play(String name,String address,String audioAddress){
  if(!validUrl(address)){state.setText("Nieprawidłowy adres materiału.");return;}
  if(audioAddress!=null&&!audioAddress.isEmpty()&&!validUrl(audioAddress)){
   state.setText("Nieprawidłowy adres ścieżki dźwiękowej.");return;
  }
  playUriWithAudio(name,Uri.parse(address),audioAddress);
 }
 private boolean validUrl(String address){
  return address!=null&&address.length()<8000
   &&(address.startsWith("https://")||address.startsWith("http://"))
   &&!address.contains("\n")&&!address.contains(" ");
 }
 public void playUri(String name,Uri address){
  playUriWithAudio(name,address,"");
 }
 private void playUriWithAudio(String name,Uri address,String audioAddress){
  backgroundAudio=false;active=name;state.setText("Łączenie • "+name);
  try{
   init();
   if(audio==null)audio=(AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);
   if(audio!=null&&!hasFocus){
    int gain=audio.requestAudioFocus(focusListener,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);
    hasFocus=(gain==AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    if(!hasFocus){state.setText("Inne audio zajmuje dźwięk. Zatrzymaj radio.");return;}
   }
   engine.stop();
   placeholder.setVisibility(audioOnly?VISIBLE:GONE);
   Media media=new Media(vlc,address);
   media.addOption(":network-caching=1500");
   if(audioOnly){media.addOption(":no-video");android.util.Log.i("PA6_PLAYER","AUDIO_ONLY_MODE=true");}
   media.addOption(":avcodec-hw=none"); // Acer Android 6: avoid audio-only hardware decoding failures.
   media.addOption(":http-reconnect=false");
   media.addOption(":http-user-agent=Mozilla/5.0 (Android 6; Pomocnik Alfa)");
   // Attach the separate AAC/M4A track before playback. This is still in-app.
   if(audioAddress!=null&&!audioAddress.isEmpty()){
    media.addOption(":input-slave="+audioAddress);
    android.util.Log.i("PA6_PLAYER","ADAPTIVE_AUDIO_SLAVE_ATTACHED");
   }
   engine.setMedia(media);
   media.release();
   engine.play();
  }catch(Throwable e){
   android.util.Log.e("PA6_PLAYER","PLAYBACK_START_ERROR",e);
   placeholder.setVisibility(VISIBLE);
   state.setText("Nie udało się uruchomić odtwarzacza: "+e.getClass().getSimpleName());
   // A synchronous LibVLC failure must also activate the next-source fallback.
   if(playbackErrorListener!=null)playbackErrorListener.run();
  }
 }
 public boolean verifyNative(){
  try{init();android.util.Log.i("PA6_LIBVLC","NATIVE_ENGINE_READY");state.setText("Silnik wideo jest gotowy.");return true;}
  catch(Throwable e){android.util.Log.e("PA6_LIBVLC","INIT_FAILED",e);state.setText("Błąd inicjalizacji silnika wideo: "+e.getClass().getSimpleName());return false;}
 }
 public void stop(){
  screen.setKeepScreenOn(false);
  if(backgroundAudio){PA6AudioService.command(getContext(),"STOP");backgroundAudio=false;}
  try{if(engine!=null)engine.stop();}catch(Throwable ignored){}
  state.setText("Zatrzymano.");
  placeholder.setVisibility(VISIBLE);
  if(audio!=null&&hasFocus){try{audio.abandonAudioFocus(focusListener);}catch(Exception ignored){}hasFocus=false;}
 }
 public void release(){
  playbackErrorListener=null;playbackStartListener=null;
  releaseLocal();
 }
 private void releaseLocal(){
  screen.setKeepScreenOn(false);backgroundAudio=false;
  try{if(engine!=null)engine.stop();}catch(Throwable ignored){}
  if(audio!=null&&hasFocus){try{audio.abandonAudioFocus(focusListener);}catch(Exception ignored){}hasFocus=false;}
  try{if(engine!=null){engine.getVLCVout().detachViews();engine.release();}}catch(Throwable ignored){}
  try{if(vlc!=null)vlc.release();}catch(Throwable ignored){}
  engine=null;vlc=null;ready=false;audio=null;
 }
}
