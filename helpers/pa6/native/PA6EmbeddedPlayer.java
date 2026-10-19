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
 private final Object engineLock=new Object();
 private volatile LibVLC vlc;
 private volatile MediaPlayer engine;
 private volatile int generation;
 private volatile boolean disposed,cachedPlaying;
 private volatile long cachedPosition,cachedDuration;
 private volatile String lastFailure="";
 private volatile PA6StreamRelay relay;
 private Runnable whenSurfaceReady;
 private final java.util.concurrent.ScheduledThreadPoolExecutor core=new java.util.concurrent.ScheduledThreadPoolExecutor(1,r->{Thread t=new Thread(r,"pa6-decoder");t.setDaemon(true);return t;});
 public String lastError(){if(!lastFailure.isEmpty())return lastFailure;PA6StreamRelay r=relay;return r==null?"":r.lastError();}
 public void recordError(String error){lastFailure=error==null?"":error;}
 private boolean current(int seq){return !disposed&&seq==generation;}
 private void submit(Runnable r){if(!core.isShutdown())try{core.execute(r);}catch(java.util.concurrent.RejectedExecutionException ignored){}}
 private void refreshSnapshot(){MediaPlayer p=engine;if(p==null)return;try{cachedPosition=Math.max(0,p.getTime());cachedDuration=Math.max(0,p.getLength());cachedPlaying=p.isPlaying();}catch(Throwable ignored){}}

 private final Handler ui=new Handler(Looper.getMainLooper());
 private String active="";
 private boolean audioOnly=false,backgroundAudio=false,fullscreen=false;
 private Runnable fullscreenListener;private android.widget.SeekBar timeline;private TextView clock;
 public long playbackPosition(){return time();}
 public boolean isPlaying(){return backgroundAudio?PA6AudioService.active&&!PA6AudioService.paused:cachedPlaying;}
 public void setFullscreenListener(Runnable r){fullscreenListener=r;}
 public void setFullscreen(boolean value){fullscreen=value;if(videoFrame!=null)videoFrame.setLayoutParams(new LinearLayout.LayoutParams(-1,value?0:-2,value?1:0));requestLayout();}
 public void detachSurface(){try{if(engine!=null)engine.getVLCVout().detachViews();}catch(Throwable ignored){}}
 public void attachSurface(){try{if(engine!=null&&!engine.getVLCVout().areViewsAttached()){engine.getVLCVout().setVideoView(screen);engine.getVLCVout().attachViews();applyVideoGeometry();}}catch(Throwable ignored){}}
 public void showMessage(String message){placeholder.setText(message);placeholder.setTextSize(17);placeholder.setVisibility(VISIBLE);}
 public void backgroundAudio(String name){releaseLocal();backgroundAudio=true;active=name;setAudioOnly(true);state.setText("Samo audio • działa również po wygaszeniu ekranu");}
 private long time(){return backgroundAudio?PA6AudioService.position:cachedPosition;}
 private long length(){return backgroundAudio?PA6AudioService.duration:cachedDuration;}
 private void seek(long t){long len=length();if(len<=0)return;t=Math.max(0,Math.min(t,len-1));if(backgroundAudio)PA6AudioService.seek(getContext(),t);else {final long point=t;submit(()->{MediaPlayer p=engine;if(p!=null&&p.isSeekable())p.setTime(point);});}}
 private static String stamp(long ms){long sec=ms/1000;return String.format(java.util.Locale.ROOT,"%d:%02d",sec/60,sec%60);}
 private final Runnable tick=new Runnable(){public void run(){screen.setKeepScreenOn(!audioOnly&&!backgroundAudio&&cachedPlaying);long len=length(),pos=time();timeline.setEnabled(len>0);if(!timeline.isPressed())timeline.setProgress(len>0?(int)(pos*1000/len):0);clock.setText(stamp(pos)+" / "+(len>0?stamp(len):"na żywo"));if(backgroundAudio)state.setText(PA6AudioService.active?PA6AudioService.status:"Audio zatrzymane");ui.postDelayed(this,1000);}};
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();ui.removeCallbacks(tick);ui.post(tick);}
 @Override protected void onDetachedFromWindow(){ui.removeCallbacks(tick);super.onDetachedFromWindow();}

 public void setAudioOnly(boolean value){
  audioOnly=value;screen.setKeepScreenOn(!value && cachedPlaying);
  if(value){placeholder.setText("♫\nSamo audio\nSłuchaj bez obrazu");placeholder.setTextSize(22);placeholder.setBackgroundColor(0xff123b45);placeholder.setVisibility(VISIBLE);}
  else{placeholder.setText("▶  Wybierz film");placeholder.setTextSize(18);placeholder.setBackgroundColor(0xff111827);}
  requestLayout();
 }

 private Runnable playbackErrorListener,playbackStartListener;
 public void setPlaybackErrorListener(Runnable listener){playbackErrorListener=listener;}
 public void setPlaybackStartListener(Runnable listener){playbackStartListener=listener;}
 private volatile boolean ready=false;
 private AudioManager audio;
 private boolean hasFocus=false;
 private final AudioManager.OnAudioFocusChangeListener focusListener=change->{
  ui.post(()->{
   if(engine==null)return;
   if(change==AudioManager.AUDIOFOCUS_LOSS || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){
    submit(()->{MediaPlayer p=engine;if(p!=null)try{p.pause();}catch(Throwable ignored){}});
    state.setText("Odtwarzanie wstrzymane — dźwięk przejęła inna funkcja.");
    if(change==AudioManager.AUDIOFOCUS_LOSS)hasFocus=false;
   }
  });
 };
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
 public PA6EmbeddedPlayer(Context context){
  super(context);setOrientation(VERTICAL);
  PA6PlaybackDiagnostics.start(context);
  setBackgroundColor(0xff101b31);
  // Playback events maintain the snapshot; no recurring JNI polling or decoder locks.
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
   @Override public void surfaceCreated(android.view.SurfaceHolder holder){if(whenSurfaceReady!=null){Runnable r=whenSurfaceReady;whenSurfaceReady=null;r.run();}}
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
  pause.setOnClickListener(v->{if(backgroundAudio){PA6AudioService.command(getContext(),"TOGGLE");}else submit(()->{MediaPlayer p=engine;if(p!=null)try{if(p.isPlaying())p.pause();else p.play();}catch(Throwable ignored){}});});
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
  Button details=new Button(context);details.setAllCaps(false);details.setText("Informacje o błędzie");details.setTextSize(12);details.setTextColor(0xffd8e5f4);details.setBackgroundColor(0xff20344d);
  details.setOnClickListener(v->{final String report=PA6PlaybackDiagnostics.report(getContext(),lastError());new android.app.AlertDialog.Builder(getContext()).setTitle("Informacje o odtwarzaniu").setMessage(report).setPositiveButton("Kopiuj",(dialog,which)->{((android.content.ClipboardManager)getContext().getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("PA6 — informacje o błędzie",report));android.widget.Toast.makeText(getContext(),"Skopiowano informacje o błędzie.",android.widget.Toast.LENGTH_SHORT).show();}).setNegativeButton("Zamknij",null).show();});
  addView(details,new LinearLayout.LayoutParams(-1,dp(48)));

 }
 private void applyVideoGeometry(){
  final MediaPlayer p=engine;final int w=screen.getWidth(),h=screen.getHeight();
  if(p==null||w<=0||h<=0)return;
  submit(()->{if(engine!=p)return;try{p.getVLCVout().setWindowSize(w,h);p.setAspectRatio(null);p.setScale(0f);}catch(Throwable ignored){}});
 }
 private void fail(final int seq,final String message,Throwable cause){
  if(cause!=null)android.util.Log.e("PA6_PLAYER","START_FAILED "+message,cause);
  PA6StreamRelay r=relay;final String detail=r==null?"":r.lastError();
  ui.post(()->{if(!current(seq))return;cachedPlaying=false;lastFailure=message+(detail.isEmpty()?"":"\nPołączenie: "+detail);state.setText(message);placeholder.setText(message);placeholder.setVisibility(VISIBLE);screen.setKeepScreenOn(false);if(playbackErrorListener!=null)playbackErrorListener.run();});
 }
 /** Only the surface binding runs on main; native construction/stop/decoding never do. */
 private void prepare(final int seq,final Runnable afterReady){
  submit(()->{
   if(!current(seq))return;
   try{
    if(engine==null){
     ArrayList<String> opts=new ArrayList<>();opts.add("--no-video-title-show");opts.add("--network-caching=1500");
     PA6ModernTls.configureVlc(getContext().getApplicationContext(),opts);
     LibVLC created=new LibVLC(getContext().getApplicationContext(),opts);
     MediaPlayer p=new GuardedPlayer(created);
     boolean accepted;
     synchronized(engineLock){accepted=current(seq);if(accepted){vlc=created;engine=p;}}
     if(!accepted){p.release();created.release();return;}
    }
    final MediaPlayer selected=engine;
    ui.post(()->{
     if(!current(seq)||engine!=selected)return;
     try{
      if(!selected.getVLCVout().areViewsAttached()){selected.getVLCVout().setVideoView(screen);selected.getVLCVout().attachViews();}
      ready=true;applyVideoGeometry();
      Runnable start=()->submit(()->{if(current(seq)&&engine==selected)afterReady.run();});
      if(audioOnly||screen.getHolder().getSurface().isValid())start.run();else whenSurfaceReady=start;
     }catch(Throwable error){fail(seq,"Nie udało się przygotować obrazu. Otwórz kanał ponownie.",error);}
    });
   }catch(Throwable error){fail(seq,"Nie udało się uruchomić odtwarzacza ("+error.getClass().getSimpleName()+").",error);}
  });
 }
 private void listen(final MediaPlayer selected,final int seq){
  final boolean[] announced={false};
  selected.setEventListener(event->{
   if(!current(seq)||engine!=selected)return;
   if(event.type==MediaPlayer.Event.TimeChanged){cachedPosition=Math.max(0,event.getTimeChanged());return;}
   if(event.type==MediaPlayer.Event.LengthChanged){cachedDuration=Math.max(0,event.getLengthChanged());return;}
   if(event.type==MediaPlayer.Event.Paused||event.type==MediaPlayer.Event.Stopped){cachedPlaying=false;return;}
   if(event.type==MediaPlayer.Event.Playing){
    cachedPlaying=true;state.setText("Odtwarzanie • "+active);screen.setKeepScreenOn(!audioOnly);
    android.util.Log.i("PA6_PLAYER","PLAYING "+active);
   }else if(event.type==MediaPlayer.Event.Buffering&&event.getBuffering()<99f)state.setText("Buforowanie • "+active);
   else if(event.type==MediaPlayer.Event.EncounteredError){fail(seq,"Nie można odczytać transmisji. Spróbuj ponownie lub wybierz inny materiał.",null);}
   else if(event.type==MediaPlayer.Event.EndReached){cachedPlaying=false;state.setText("Zakończono odtwarzanie.");placeholder.setVisibility(VISIBLE);screen.setKeepScreenOn(false);}
   boolean videoReady=event.type==MediaPlayer.Event.Vout&&event.getVoutCount()>0;
   if(!announced[0]&&(videoReady||(audioOnly&&event.type==MediaPlayer.Event.Playing))){
    announced[0]=true;if(!audioOnly)placeholder.setVisibility(GONE);
    android.util.Log.i("PA6_PLAYER",videoReady?"VIDEO_OUTPUT_READY":"AUDIO_OUTPUT_READY");
    if(playbackStartListener!=null)playbackStartListener.run();
   }
  });
 }
 /** AWindow invokes these virtual methods from Android surface callbacks. LibVLC's
  * default implementation can synchronously wait for a video output to disappear.
  */
 private final class GuardedPlayer extends MediaPlayer {
  GuardedPlayer(LibVLC library){super(library);}
  @Override public void play(){final int seq=generation;if(Looper.myLooper()==Looper.getMainLooper())submit(()->{if(engine==this&&current(seq))super.play();});else super.play();}
  @Override public void setVideoTrackEnabled(boolean enabled){final int seq=generation;if(Looper.myLooper()==Looper.getMainLooper())submit(()->{if(engine==this&&current(seq))super.setVideoTrackEnabled(enabled);});else super.setVideoTrackEnabled(enabled);}
 }
 private Uri localTransport(Uri address)throws Exception {
  PA6StreamRelay transport=relay;
  if(transport==null){PA6ModernTls.install(getContext());transport=new PA6StreamRelay(PA6ModernTls::open);relay=transport;}
  if("https".equals(address.getScheme())||"http".equals(address.getScheme()))return Uri.parse(transport.route(address.toString()));
  // The quality selector generates a tiny local master to retain separate audio.
  // Route its remote references too, without exposing arbitrary local files over HTTP.
  if("file".equals(address.getScheme())&&address.getPath()!=null&&address.getPath().endsWith(".m3u8")){
   java.io.File file=new java.io.File(address.getPath()).getCanonicalFile();
   if(file.getParentFile().equals(getContext().getCacheDir().getCanonicalFile())&&file.getName().startsWith("pa6-tv-")&&file.length()<=2000000){
    java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
    try(java.io.InputStream in=new java.io.FileInputStream(file)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>2000000)throw new java.io.IOException("Lista transmisji jest za duża.");bytes.write(buffer,0,n);}}
    return Uri.parse(transport.playlist(bytes.toString("UTF-8")));
   }
  }
  return address;
 }
 public void play(String name,String address){play(name,address,"");}
 public void play(String name,String address,String audioAddress){
  if(!validUrl(address)||(audioAddress!=null&&!audioAddress.isEmpty()&&!validUrl(audioAddress))){lastFailure="Nieprawidłowy adres materiału.";showMessage(lastFailure);if(playbackErrorListener!=null)playbackErrorListener.run();return;}
  playUriWithAudio(name,Uri.parse(address),audioAddress);
 }
 private boolean validUrl(String s){return s!=null&&s.length()<16000&&(s.startsWith("https://")||s.startsWith("http://"))&&!s.contains("\n")&&!s.contains(" ");}
 public void playUri(String name,Uri address){playUriWithAudio(name,address,"");}
 private void playUriWithAudio(String name,Uri address,String audioAddress){
  final int seq=++generation;whenSurfaceReady=null;backgroundAudio=false;active=name;lastFailure="";cachedPlaying=false;cachedPosition=cachedDuration=0;
  state.setText("Łączenie • "+name);placeholder.setText("Przygotowuję obraz i dźwięk…");placeholder.setVisibility(VISIBLE);
  if(audio==null)audio=(AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);
  if(audio!=null&&!hasFocus){hasFocus=audio.requestAudioFocus(focusListener,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;if(!hasFocus){fail(seq,"Dźwięk jest zajęty. Zatrzymaj radio i spróbuj ponownie.",null);return;}}
  final boolean onlyAudio=audioOnly;
  prepare(seq,()->{
   MediaPlayer p=engine;if(p==null||!current(seq))return;
   try{
    PA6StreamRelay old=relay;relay=null;if(old!=null)old.close();
    p.setEventListener(null);p.stop();if(!current(seq))return;
    Media media=new Media(vlc,localTransport(address));
    media.addOption(":network-caching=1500");media.addOption(":avcodec-threads=2");media.addOption(":http-reconnect=false");media.addOption(":http-user-agent=Mozilla/5.0");
    if(!onlyAudio)media.setHWDecoderEnabled(true,false);
    if(onlyAudio)media.addOption(":no-video");
    if(audioAddress!=null&&!audioAddress.isEmpty())media.addOption(":input-slave="+localTransport(Uri.parse(audioAddress)));
    listen(p,seq);p.setMedia(media);media.release();if(current(seq))p.play();else {PA6StreamRelay cancelled=relay;relay=null;if(cancelled!=null)cancelled.close();}
   }catch(Throwable error){fail(seq,"Nie udało się rozpocząć transmisji ("+error.getClass().getSimpleName()+").",error);}
  });
 }
 public boolean verifyNative(){
  if(disposed)return false;
  final int seq=generation;
  prepare(seq,()->android.util.Log.i("PA6_LIBVLC","NATIVE_ENGINE_READY"));
  return true; // Requested asynchronously; QA must wait for NATIVE_ENGINE_READY or START_FAILED.
 }
 public void stop(){
  ++generation;whenSurfaceReady=null;cachedPlaying=false;cachedPosition=cachedDuration=0;screen.setKeepScreenOn(false);
  if(backgroundAudio){PA6AudioService.command(getContext(),"STOP");backgroundAudio=false;}
  final MediaPlayer selected=engine;
  PA6StreamRelay stoppedRelay=relay;relay=null;if(stoppedRelay!=null)stoppedRelay.close();
  submit(()->{if(selected!=null)try{selected.setEventListener(null);selected.stop();}catch(Throwable ignored){}});
  state.setText("Zatrzymano.");placeholder.setVisibility(VISIBLE);abandonFocus();
 }
 private void abandonFocus(){if(audio!=null&&hasFocus){try{audio.abandonAudioFocus(focusListener);}catch(Throwable ignored){}hasFocus=false;}}
 public void release(){
  if(disposed)return;PA6PlaybackDiagnostics.stop();
  ++generation;disposed=true;whenSurfaceReady=null;playbackErrorListener=null;playbackStartListener=null;ui.removeCallbacks(tick);releaseLocal();core.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);core.shutdown();
 }
 private void releaseLocal(){
  ++generation;whenSurfaceReady=null;
  screen.setKeepScreenOn(false);backgroundAudio=false;cachedPlaying=false;abandonFocus();
  final MediaPlayer old;final LibVLC library;
  synchronized(engineLock){old=engine;library=vlc;engine=null;vlc=null;}ready=false;
  PA6StreamRelay stoppedRelay=relay;relay=null;if(stoppedRelay!=null)stoppedRelay.close();
  if(old!=null)try{old.getVLCVout().detachViews();}catch(Throwable ignored){}
  submit(()->{try{if(old!=null){old.setEventListener(null);old.stop();old.release();}}catch(Throwable ignored){}try{if(library!=null)library.release();}catch(Throwable ignored){}});
 }
}
