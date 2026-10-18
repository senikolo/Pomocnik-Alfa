package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Alfa Tube: native, YouTube-inspired layout and NewPipe-style workflow.
 * Direct NewPipe search/metadata + bundled LibVLC. Piped/Invidious are backups.
 * No DRM bypass, user login or scraping in WebView.
 */
public final class PA6TubeActivity extends Activity {
 private static final int PICK_FILE=791, STORAGE_PERMISSION=792;
 private static final int WHITE=0xfff6f8fc,INK=0xff172036,MUTED=0xff596574,RED=0xffd92d48,PALE=0xffeef2f7;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService io=Executors.newFixedThreadPool(2);
 private final ThreadPoolExecutor thumbnailIo=(ThreadPoolExecutor)Executors.newFixedThreadPool(2);
 private final ExecutorService searchIo=Executors.newSingleThreadExecutor();
 private Future<?> searchTask,playTask;
 private PA6NewPipeClient.Work searchWork,playWork;
 private int searchGeneration=0;
 private volatile int thumbnailGeneration=0;
 private static final android.util.LruCache<String,Bitmap> THUMBS=new android.util.LruCache<String,Bitmap>(2*1024*1024){
  @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getByteCount();}
 };
 private static final class CachedSearch {
  final PA6PipedClient.Response<List<PA6PipedClient.Result>> response;
  final long at=SystemClock.elapsedRealtime();
  CachedSearch(PA6PipedClient.Response<List<PA6PipedClient.Result>> value){response=value;}
 }
 private static final android.util.LruCache<String,CachedSearch> SEARCHES=new android.util.LruCache<>(6);
 private void cancelSearch(){if(searchWork!=null)searchWork.cancel();if(searchTask!=null)searchTask.cancel(true);}
 private void cancelPlay(){if(playWork!=null)playWork.cancel();if(playTask!=null)playTask.cancel(true);}
 private void clearThumbnails(){thumbnailGeneration++;thumbnailIo.getQueue().clear();}

 private final PA6Fullscreen fullscreen=new PA6Fullscreen();
 private LinearLayout results,stageWrap,mediaArea,playerPane,feedPane;
 private PA6EmbeddedPlayer player;
 private WebView officialWeb;
 private Button officialButton,compatiblePlayerButton,qualityButton,downloadButton,videoModeButton,audioModeButton;
 private TextView status,title,subtitle,provider;
 private EditText search,link,serverField;
 private int request=0;
 private boolean closed=false,kids;
 private String activeId="",activeTitle="",currentProvider="",pendingDownloadId="";
 private final HashSet<String> failedProviders=new HashSet<>();
 private int playbackAttempts=0;
 private boolean playbackPending=false;
 private Runnable playbackWatchdog;
 private void cancelWatchdog(){
  if(playbackWatchdog!=null){main.removeCallbacks(playbackWatchdog);playbackWatchdog=null;}
 }
 private void armWatchdog(final int seq){
  cancelWatchdog();
  // A stalled HTTP stream may never produce a VLC error event. Retry instead
  // of displaying a permanent black frame or endless "Buffering" state.
  playbackWatchdog=()->{
   if(closed||seq!=request||playbackPending||activeId.isEmpty())return;
   playbackWatchdog=null;
   feedback("To źródło nie rozpoczęło odtwarzania. Sprawdzam następne…");
   player.stop();
   onVideoPlaybackError();
  };
  main.postDelayed(playbackWatchdog,25000);
 }
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
 private GradientDrawable panel(int color,int border,int r){
  GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(r));d.setStroke(dp(1),border);return d;
 }
 private TextView text(String s,int size,int color,boolean bold){
  TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);
  if(bold)t.setTypeface(null,1);return t;
 }
 private Button button(String value,int color,boolean emphasized){
  Button b=new Button(this);b.setAllCaps(false);b.setText(value);b.setTextSize(14);b.setMinWidth(0);b.setMinimumWidth(0);b.setMaxLines(2);
  b.setTextColor(emphasized?WHITE:INK);b.setMinHeight(dp(48));b.setPadding(dp(8),dp(6),dp(8),dp(6));
  b.setBackground(panel(emphasized?color:WHITE,emphasized?color:0xffdce2ed,12));return b;
 }
 private void pad(View v,int x,int y){v.setPadding(dp(x),dp(y),dp(x),dp(y));}
 private void gap(LinearLayout l,int h){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
 private LinearLayout line(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 private String server(){return getPreferences(0).getString("piped_server","");}
 private int selectedQuality(){return getPreferences(0).getInt("tube_quality",0);}
 private String layoutMode(){return getSharedPreferences("pa6_display",MODE_PRIVATE).getString("ui_mode","Automatyczny");}
 private void useLocalVideo(){stopService(new Intent(this,PA6AudioService.class));getPreferences(0).edit().putBoolean("tube_audio_only",false).apply();player.setAudioOnly(false);updateQualityButton();}
 private boolean audioOnly(){return getPreferences(0).getBoolean("tube_audio_only",false);}
 private void chooseMode(boolean audio){
  getPreferences(0).edit().putBoolean("tube_audio_only",audio).apply();updateQualityButton();
  if(!activeId.isEmpty()){player.stop();playVideo(activeId);}
  else feedback(audio?"Tryb Samo audio. Wybierz film, którego chcesz posłuchać.":"Tryb Wideo. Wybierz film z listy.");
 }
 private void styleMode(Button button,boolean selected,boolean audio){
  if(button==null)return;int color=audio?0xff0f766e:RED;
  button.setTextColor(selected?0xffffffff:INK);button.setBackground(panel(selected?color:0xffffffff,selected?color:0xffdce3ee,14));
 }
 private String qualityLabel(){int q=selectedQuality();return q==0?"Auto":q+"p";}
 private static final int[] QUALITY_VALUES={0,240,360,480,720};
 private static final String[] QUALITY_OPTIONS={"Automatyczna","240p (oszczędna)","360p","480p","720p (jeśli dostępna)"};
 private int[] availableQualities={0};
 private void updateQualityButton(){
  if(qualityButton!=null)qualityButton.setText("Jakość obrazu • "+qualityLabel());
  styleMode(videoModeButton,!audioOnly(),false);styleMode(audioModeButton,audioOnly(),true);
  if(downloadButton!=null){downloadButton.setEnabled(!audioOnly()&&!activeId.isEmpty());downloadButton.setAlpha(audioOnly()?.45f:1f);}

 }
 private void chooseQuality(){
  final int[] values=activeId.isEmpty()?QUALITY_VALUES:availableQualities;
  String[] labels=new String[values.length];
  for(int i=0;i<values.length;i++)labels[i]=values[i]==0?"Auto":values[i]+"p";
  int selected=0;
  for(int i=0;i<values.length;i++)if(values[i]==selectedQuality())selected=i;
  new AlertDialog.Builder(this).setTitle(activeId.isEmpty()?"Domyślna jakość wideo":"Dostępne jakości filmu")
   .setSingleChoiceItems(labels,selected,(dialog,which)->{
    getPreferences(0).edit().putInt("tube_quality",values[which]).putBoolean("tube_audio_only",false).apply();
    updateQualityButton();dialog.dismiss();
    if(!activeId.isEmpty()){
     feedback("Ustawiam jakość "+qualityLabel()+".");
     playVideo(activeId);
    }
   }).setNegativeButton("Anuluj",null).show();
 }
 private void applyLayoutMode(){
  if(mediaArea==null||playerPane==null||feedPane==null)return;
  if(playerPane.getParent() instanceof ViewGroup)((ViewGroup)playerPane.getParent()).removeView(playerPane);
  if(feedPane.getParent() instanceof ViewGroup)((ViewGroup)feedPane.getParent()).removeView(feedPane);
  mediaArea.removeAllViews();
  float widthDp=getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density;
  boolean wide=widthDp>=("Tablet".equals(layoutMode())?600:660) && !"Smartfon".equals(layoutMode());
  android.util.Log.i("PA6_PERF","LAYOUT_MODE="+layoutMode()+" WIDE="+wide);
  mediaArea.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
  mediaArea.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
  if(wide){
   LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,-1,4);
   pp.setMargins(0,0,dp(13),0);
   ScrollView playerScroll=new ScrollView(this);playerScroll.addView(playerPane);mediaArea.addView(playerScroll,pp);
   mediaArea.addView(feedPane,new LinearLayout.LayoutParams(0,-1,5));
  }else{
   LinearLayout stack=column();
   int max=(int)Math.min(widthDp-24, "Smartfon".equals(layoutMode())?460:widthDp);
   ScrollView all=new ScrollView(this);all.setFillViewport(true);
   all.addView(stack,new ScrollView.LayoutParams(-1,-2));
   mediaArea.addView(all,new LinearLayout.LayoutParams(dp(max),-1));
   stack.addView(playerPane,new LinearLayout.LayoutParams(-1,-2));
   stack.addView(feedPane,new LinearLayout.LayoutParams(-1,dp(650)));
  }
 }
 private void showTubeSettings(){
  final String[] modes={"Automatyczny","Tablet","Smartfon"};
  LinearLayout panel=column();pad(panel,19,11);
  TextView note=text("Personalizacja Alfa Tube i Tube Kids. Ustawienia zostaną zapamiętane.",13,MUTED,false);
  panel.addView(note);gap(panel,12);
  TextView modeTitle=text("Układ ekranu",15,INK,true);panel.addView(modeTitle);
  Spinner mode=new Spinner(this);
  ArrayAdapter<String> modeAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,modes);
  modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
  mode.setAdapter(modeAdapter);
  for(int i=0;i<modes.length;i++)if(modes[i].equals(layoutMode()))mode.setSelection(i);
  panel.addView(mode,new LinearLayout.LayoutParams(-1,-2));gap(panel,12);
  TextView qualityTitle=text("Domyślna jakość filmu",15,INK,true);panel.addView(qualityTitle);
  Spinner quality=new Spinner(this);
  ArrayAdapter<String> qualityAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,QUALITY_OPTIONS);
  qualityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
  quality.setAdapter(qualityAdapter);
  for(int i=0;i<QUALITY_VALUES.length;i++)if(QUALITY_VALUES[i]==selectedQuality())quality.setSelection(i);
  panel.addView(quality,new LinearLayout.LayoutParams(-1,-2));gap(panel,10);
  CheckBox wifi=new CheckBox(this);
  wifi.setText("Pobieraj tylko przez Wi-Fi");
  wifi.setTextColor(INK);
  wifi.setChecked(getPreferences(0).getBoolean("tube_wifi_only",true));
  panel.addView(wifi);
  TextView hint=text("Pobrane filmy MP4 znajdziesz w folderze Pobrane. Pobieranie jest możliwe tylko, gdy źródło oferuje plik z obrazem i dźwiękiem bez zabezpieczeń.",12,MUTED,false);
  pad(hint,2,9);panel.addView(hint);gap(panel,12);
  Button clear=button("Wyczyść historię Alfa Tube",RED,false);
  panel.addView(clear,new LinearLayout.LayoutParams(-1,-2));
  clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Usunąć historię?")
   .setMessage("Lista ostatnio oglądanych filmów zostanie wyczyszczona.")
   .setPositiveButton("Usuń",(dialog,which)->{
    getPreferences(0).edit().remove("recent_ids").apply();
    feedback("Historia została wyczyszczona.");
   }).setNegativeButton("Anuluj",null).show());
  ScrollView scroll=new ScrollView(this);scroll.addView(panel);
  new AlertDialog.Builder(this).setTitle("Ustawienia Alfa Tube")
   .setView(scroll).setPositiveButton("Zapisz",(dialog,which)->{
     getPreferences(0).edit()
      .putInt("tube_quality",QUALITY_VALUES[quality.getSelectedItemPosition()])
      .putBoolean("tube_wifi_only",wifi.isChecked()).apply();
     getSharedPreferences("pa6_display",MODE_PRIVATE).edit()
      .putString("ui_mode",modes[mode.getSelectedItemPosition()]).apply();
     updateQualityButton();applyLayoutMode();
     feedback("Ustawienia zapisane. Jakość zadziała od kolejnego filmu.");
   }).setNegativeButton("Anuluj",null).show();
 }
 private void downloadCurrent(){
  if(audioOnly()){feedback("Pobieranie MP4 jest dostępne w trybie Wideo.");return;}
  if(activeId==null||!activeId.matches("[A-Za-z0-9_-]{11}")){
   feedback("Wybierz film, który chcesz pobrać.");return;
  }
  final String id=activeId;
  new AlertDialog.Builder(this).setTitle("Pobierz film")
   .setMessage("Zapiszę dostępny plik MP4 z obrazem i dźwiękiem do folderu Pobrane. Wybrana jakość jest limitem; nie każdy film ma taką wersję. Pobieraj tylko materiały, na których zapis masz zgodę.")
   .setPositiveButton("Pobierz",(dialog,which)->{
    if(Build.VERSION.SDK_INT>=23 &&
      checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
      pendingDownloadId=id;
      requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE,android.Manifest.permission.READ_EXTERNAL_STORAGE},STORAGE_PERMISSION);
    }else startDownload(id);
   }).setNegativeButton("Anuluj",null).show();
 }
 private void startDownload(final String id){
  Intent job=new Intent(this,PA6DownloadService.class).putExtra("id",id)
   .putExtra("quality",selectedQuality()).putExtra("wifi",getPreferences(0).getBoolean("tube_wifi_only",true));
  startService(job);
 }
 private TextView downloadStatus;
 private final Runnable downloadMonitor=new Runnable(){public void run(){
  if(closed)return;
  if(downloadStatus!=null){String value=getSharedPreferences("pa6_download",0).getString("status","");
   if(!value.contentEquals(downloadStatus.getText()))downloadStatus.setText(value);
   downloadStatus.setVisibility(value.isEmpty()?View.GONE:View.VISIBLE);
  }
  main.postDelayed(this,1000);
 }};
 @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] granted){
  super.onRequestPermissionsResult(code,permissions,granted);
  if(code!=STORAGE_PERMISSION)return;
  String id=pendingDownloadId;pendingDownloadId="";
  if(granted.length>0&&granted[0]==PackageManager.PERMISSION_GRANTED&&!id.isEmpty())
   startDownload(id);
  else feedback("Nie przyznano dostępu do pamięci. Pobieranie anulowane.");
 }
 private void feedback(String message){status.setText(message);}
 private void disableKeyboard(){
  try{((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);}catch(Exception ignored){}
 }
 private void playVideo(final String id){
  if(id==null||!id.matches("[A-Za-z0-9_-]{11}")){feedback("Nieprawidłowy adres filmu.");return;}
  cancelSearch();cancelPlay();cancelWatchdog();switchBackToNative();
  if(officialButton!=null)officialButton.setVisibility(View.GONE);
  ++request;activeId=id;availableQualities=new int[]{0};playbackAttempts=0;failedProviders.clear();currentProvider="";
  if(compatiblePlayerButton!=null)compatiblePlayerButton.setVisibility(View.GONE);
  updateQualityButton();
  resolveAndPlay(id,request);
 }
 private void resolveAndPlay(final String id,final int seq){
  playbackPending=true;
  feedback(playbackAttempts==0?"Sprawdzam film bezpośrednio przez NewPipe Extractor…":
    "Pierwsze źródło nie odtworzyło filmu. Sprawdzam serwer zapasowy ("+playbackAttempts+"/6)…");
  final HashSet<String> ignored=new HashSet<>(failedProviders);
  cancelPlay();final PA6NewPipeClient.Work work=new PA6NewPipeClient.Work();playWork=work;
  final boolean audio=audioOnly();
  final int preferredQuality=selectedQuality();final String preferredServer=server();
  playTask=io.submit(()->{
   PA6NewPipeClient.enterWork(work);
   try{
    PA6PipedClient.Response<PA6PipedClient.Video> response=null;
    if(!ignored.contains("NewPipe Extractor")){
     try{
      response=audio?PA6NewPipeClient.resolveAudio(id):PA6NewPipeClient.resolve(id,preferredQuality);
     }catch(Throwable npe){
      if(work.cancelled||Thread.currentThread().isInterrupted())return;
      if(audio)throw new IOException("Nie udało się pobrać samego dźwięku. Spróbuj ponownie albo wybierz Wideo.",npe);
      android.util.Log.w("PA6_NEWPIPE","Native extractor not available: "+npe.getClass().getSimpleName()+" "+npe.getMessage());
      final String type=npe.getClass().getSimpleName();
      final String reason=type.contains("SignInConfirmNotBot")
          ?"YouTube odrzucił połączenie i żąda potwierdzenia, że użytkownik nie jest botem."
          :"NewPipe nie pobrał strumienia ("+type+").";
      main.post(()->{
       if(!closed&&seq==request){
        feedback(reason+" Sprawdzam źródła zapasowe…");
        if(compatiblePlayerButton!=null)compatiblePlayerButton.setVisibility(View.VISIBLE);
       }
      });
     }
    }
    if(work.cancelled)return;
    if(response==null){if(audio)throw new IOException("Źródło samego dźwięku jest niedostępne. Wybierz Wideo.");response=PA6PipedClient.resolve(id,preferredServer,ignored,preferredQuality);}
    final PA6PipedClient.Response<PA6PipedClient.Video> selected=response;
    int[] heights=new int[]{0};
    if("NewPipe Extractor".equals(selected.server))try{heights=PA6NewPipeClient.availableQualities(id);}catch(Exception ignoredQuality){}
    final int[] options=heights;
    main.post(()->{
     if(closed||seq!=request)return;
     playbackPending=false;availableQualities=options;
     PA6PipedClient.Video v=selected.data;
     currentProvider=selected.server;
     activeTitle=v.title;title.setText(v.title);
     subtitle.setText(v.channel+" • Faktyczny format: "+v.quality);
     provider.setText(audio?"Słuchasz bez pobierania obrazu":"Odtwarzanie wewnątrz Alfa Tube");
     player.setAudioOnly(audio);
     if(audio){cancelWatchdog();player.backgroundAudio(v.title);PA6AudioService.start(this,id,v.title,v.stream);}
     else{stopService(new Intent(this,PA6AudioService.class));armWatchdog(seq);player.play(v.title,v.stream,v.audioStream);}
     updateQualityButton();
     if(v.audioStream!=null&&!v.audioStream.isEmpty()){
      android.util.Log.i("PA6_TUBE","Adaptive playback requested (separate video/audio)");
     }
     feedback("Łączenie z filmem. Jeśli ten serwer zawiedzie, aplikacja wybierze następny.");
     String old=getPreferences(0).getString("recent_ids","");
     String next=id+","+old.replace(id+",","");
     if(next.length()>360)next=next.substring(0,360);
     getPreferences(0).edit().putString("recent_ids",next).putString("recent_title_"+id,v.title).apply();
    });
   }catch(Exception e){
    if(work.cancelled||Thread.currentThread().isInterrupted())return;
    final String why=e.getMessage();
    main.post(()->{
     if(closed||seq!=request)return;
     playbackPending=false;
     feedback(audio?"Nie udało się odtworzyć samego dźwięku. Wybierz Wideo lub spróbuj innego filmu.":
      "Nie udało się uruchomić filmu. "+why);
     if(officialButton!=null)officialButton.setVisibility(View.VISIBLE);
    });
   }finally{PA6NewPipeClient.leaveWork();}
  });
 }
 /**
  * A user-selected compatibility fallback, not an embedded player.
  * Android 6 does not allow displaying another app's Activity inside PA6.
  */
 private void openInstalledCompatiblePlayer(){
  if(activeId==null||!activeId.matches("[A-Za-z0-9_-]{11}")){
   feedback("Najpierw wybierz film.");return;
  }
  Uri video=Uri.parse("https://www.youtube.com/watch?v="+activeId);
  String[] packages={"org.schabi.newpipe","free.rm.skytube.oss","free.rm.skytube.extra"};
  String[] labels={"NewPipe","SkyTube","SkyTube Extra"};
  final ArrayList<Intent> available=new ArrayList<>();
  final ArrayList<String> names=new ArrayList<>();
  for(int i=0;i<packages.length;i++){
   try{
    getPackageManager().getPackageInfo(packages[i],0);
    Intent intent=new Intent(Intent.ACTION_VIEW,video);
    intent.addCategory(Intent.CATEGORY_BROWSABLE);
    intent.setPackage(packages[i]);
    if(intent.resolveActivity(getPackageManager())!=null){
     available.add(intent);names.add(labels[i]);
    }
   }catch(android.content.pm.PackageManager.NameNotFoundException ignored){
   }catch(SecurityException e){
    android.util.Log.w("PA6_BRIDGE","Odtwarzacz zablokowany",e);
   }
  }
  if(available.isEmpty()){
   new AlertDialog.Builder(this).setTitle("Brak NewPipe lub SkyTube")
    .setMessage("Zainstaluj jeden z odtwarzaczy na tablecie. PA6 nie uruchomi przeglądarki bez Twojej zgody.")
    .setPositiveButton("OK",null).show();
   return;
  }
  if(available.size()==1){
   try{startActivity(available.get(0));}
   catch(ActivityNotFoundException | SecurityException e){feedback("Nie udało się uruchomić zainstalowanego odtwarzacza.");}
   return;
  }
  new AlertDialog.Builder(this)
   .setTitle("Wybierz działający odtwarzacz")
   .setItems(names.toArray(new String[0]),(dialog,choice)->{
    try{startActivity(available.get(choice));}
    catch(ActivityNotFoundException | SecurityException e){feedback("Nie udało się uruchomić wybranego odtwarzacza.");}
   }).setNegativeButton("Anuluj",null).show();
 }
 private void showOfficialEmbedded(){
  if(activeId==null||!activeId.matches("[A-Za-z0-9_-]{11}")){
   feedback("Najpierw wybierz film z wyników albo wklej link YouTube.");return;
  }
  cancelWatchdog();++request;playbackPending=false;
  player.stop();player.setVisibility(View.GONE);
  if(officialWeb==null){
   officialWeb=new WebView(this);
   officialWeb.setBackgroundColor(0xff101827);
   officialWeb.getSettings().setJavaScriptEnabled(true);
   officialWeb.getSettings().setDomStorageEnabled(true);
   officialWeb.getSettings().setMediaPlaybackRequiresUserGesture(true);
   officialWeb.getSettings().setAllowFileAccess(false);
   officialWeb.getSettings().setAllowContentAccess(false);
   officialWeb.setWebViewClient(new WebViewClient(){
    @Override public boolean shouldOverrideUrlLoading(WebView view,String url){
     return !(url.startsWith("https://www.youtube-nocookie.com/")||
              url.startsWith("https://www.youtube.com/")||
              url.startsWith("https://accounts.google.com/"));
    }
   });
   officialWeb.setWebChromeClient(new WebChromeClient());
   stageWrap.addView(officialWeb,0,new LinearLayout.LayoutParams(-1,dp(300)));
  }
  officialWeb.setVisibility(View.VISIBLE);
  officialWeb.onResume();
  String url="https://www.youtube-nocookie.com/embed/"+activeId+"?playsinline=1&rel=0";
  officialWeb.loadUrl(url);
  if(officialButton!=null)officialButton.setVisibility(View.GONE);
  feedback("Tryb awaryjny wewnątrz PA • oficjalny YouTube może wyświetlać reklamy i wymagać aktualizacji Android System WebView.");
 }
 private void switchBackToNative(){
  if(officialWeb!=null){officialWeb.stopLoading();officialWeb.loadUrl("about:blank");
   officialWeb.onPause();officialWeb.setVisibility(View.GONE);}
  player.setVisibility(View.VISIBLE);
 }
 private void onVideoPlaybackError(){
  if(audioOnly()){
   cancelWatchdog();playbackPending=false;feedback("Nie udało się odtworzyć dźwięku. Spróbuj ponownie lub przełącz na Wideo.");return;
  }
  if(closed||activeId.isEmpty()||playbackPending)return;
  cancelWatchdog();
  if(!currentProvider.isEmpty())failedProviders.add(currentProvider);
  if(++playbackAttempts>6){
   feedback("Wewnętrzny odtwarzacz nie uruchomił filmu. Wybierz NewPipe lub SkyTube (osobna aplikacja).");
   if(officialButton!=null)officialButton.setVisibility(View.VISIBLE);
   return;
  }
  resolveAndPlay(activeId,request);
 }
 private void showSearchResults(final PA6PipedClient.Response<List<PA6PipedClient.Result>> response,final int seq){
  if(closed||seq!=searchGeneration)return;
  results.removeAllViews();clearThumbnails();
  provider.setText("Wyniki filmów • "+response.server.replace("https://",""));
  feedback(response.data.isEmpty()?"Brak wyników. Spróbuj innego hasła.":"Znaleziono filmów: "+response.data.size()+" • dotknij filmu, aby oglądać.");
  appendResults(response.data,0,seq);
 }
 private void appendResults(final List<PA6PipedClient.Result> list,final int start,final int seq){
  if(closed||seq!=searchGeneration)return;
  int end=Math.min(list.size(),start+4);
  for(int i=start;i<end;i++)movieCard(list.get(i));
  if(end<list.size()&&end%12!=0){final int next=end;main.postDelayed(()->appendResults(list,next,seq),16);}
  else if(end<list.size()){
   final int next=end;Button more=button("Pokaż kolejne filmy",RED,false);
   results.addView(more,new LinearLayout.LayoutParams(-1,-2));
   more.setOnClickListener(v->{results.removeView(more);appendResults(list,next,seq);});
  }
 }
 private void searchVideos(final String query){
  if(query.trim().isEmpty()){feedback("Wpisz nazwę filmu, kanału lub wykonawcy.");return;}
  disableKeyboard();cancelSearch();final int seq=++searchGeneration;
  clearThumbnails();results.removeAllViews();
  final String terms=kids?(query.trim()+" bajki dla dzieci"):query.trim();
  final String preferredServer=server(),cacheKey=preferredServer+"|"+terms.toLowerCase(Locale.ROOT);
  CachedSearch cached=SEARCHES.get(cacheKey);
  if(cached!=null&&SystemClock.elapsedRealtime()-cached.at<300000){
   android.util.Log.i("PA6_PERF","SEARCH_CACHE_HIT");showSearchResults(cached.response,seq);return;
  }
  feedback("Wyszukiwanie: "+query+"…");
  final long started=SystemClock.elapsedRealtime();
  final PA6NewPipeClient.Work work=new PA6NewPipeClient.Work();searchWork=work;
  searchTask=searchIo.submit(()->{
   PA6NewPipeClient.enterWork(work);
   try{
    PA6PipedClient.Response<List<PA6PipedClient.Result>> selected;
    try{selected=PA6NewPipeClient.search(terms);}
    catch(Exception | LinkageError nativeFailure){
     if(work.cancelled||Thread.currentThread().isInterrupted())return;
     selected=PA6PipedClient.search(terms,preferredServer);
    }
    if(work.cancelled)return;
    final PA6PipedClient.Response<List<PA6PipedClient.Result>> response=selected;
    main.post(()->{
     if(closed||work.cancelled||seq!=searchGeneration)return;
     SEARCHES.put(cacheKey,new CachedSearch(response));showSearchResults(response,seq);
     android.util.Log.i("PA6_PERF","SEARCH_READY_MS="+(SystemClock.elapsedRealtime()-started));
    });
   }catch(Exception error){
    if(work.cancelled||Thread.currentThread().isInterrupted())return;
    main.post(()->{if(closed||seq!=searchGeneration)return;
     feedback("Nie udało się wyszukać filmów. Sprawdź połączenie i spróbuj ponownie.");
    });
   }finally{PA6NewPipeClient.leaveWork();}
  });
 }
 private void thumb(ImageView image,String suggested,String videoId){
  if(videoId==null||!videoId.matches("[A-Za-z0-9_-]{11}"))return;
  // Prefer Google's compact 320px thumbnail; old tablet has only 2 GB RAM.
  final String direct="https://i.ytimg.com/vi/"+videoId+"/mqdefault.jpg";
  Bitmap cached=THUMBS.get(videoId);if(cached!=null){image.setImageBitmap(cached);return;}
  final int generation=thumbnailGeneration;
  thumbnailIo.execute(()->{
   if(closed||generation!=thumbnailGeneration)return;
   String[] urls={direct,suggested};
   for(String url:urls){
    if(closed||generation!=thumbnailGeneration)return;
    if(url==null||!url.startsWith("https://")||url.length()>2400)continue;
    HttpURLConnection c=null;
    try{
     c=PA6ModernTls.open(url);c.setConnectTimeout(3500);c.setReadTimeout(4500);
     c.setRequestProperty("User-Agent","Mozilla/5.0 (Android 6; Alfa Tube)");
     if(c.getResponseCode()!=200||c.getContentLength()>300000)continue;
     ByteArrayOutputStream out=new ByteArrayOutputStream(26000);
     try(InputStream in=c.getInputStream()){
      byte[] buf=new byte[4096];int n;
      while((n=in.read(buf))>0){if(closed||generation!=thumbnailGeneration)return;if(out.size()+n>300000)break;out.write(buf,0,n);}
     }
     byte[] data=out.toByteArray();
     if(data.length==0||data.length>300000)continue;
     BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;
     o.inPreferredConfig=Bitmap.Config.RGB_565;
     Bitmap result=BitmapFactory.decodeByteArray(data,0,data.length,o);
     if(result!=null){
      THUMBS.put(videoId,result);
      main.post(()->{if(!closed&&!isFinishing()&&generation==thumbnailGeneration)image.setImageBitmap(result);});
      return;
     }
    }catch(Exception e){
     android.util.Log.w("PA6_THUMB","Miniatura chwilowo niedostępna: "+e.getClass().getSimpleName());
    }finally{if(c!=null)c.disconnect();}
   }
  });
 }
 private void movieCard(PA6PipedClient.Result video){
  LinearLayout card=line();card.setPadding(dp(9),dp(9),dp(9),dp(9));
  card.setBackground(panel(0xffffffff,0xffe3e9f2,16));
  ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
  poster.setBackground(panel(0xffe6ebf3,0xffe6ebf3,9));
  FrameLayout imageFrame=new FrameLayout(this);
  imageFrame.addView(poster,new FrameLayout.LayoutParams(-1,-1));
  TextView playBadge=text("▶",23,WHITE,true);
  playBadge.setGravity(Gravity.CENTER);playBadge.setBackground(panel(0xcced263b,0xcced263b,24));
  FrameLayout.LayoutParams badgeParams=new FrameLayout.LayoutParams(dp(39),dp(39),Gravity.CENTER);
  imageFrame.addView(playBadge,badgeParams);
  float screenDp=getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density;
  int posterWidth=screenDp<430?112:144;
  card.addView(imageFrame,new LinearLayout.LayoutParams(dp(posterWidth),dp(posterWidth*9/16)));
  thumb(poster,video.thumb,video.id);
  LinearLayout copy=column();copy.setPadding(dp(13),0,0,0);
  TextView h=text(video.title,15,INK,true);h.setMaxLines(2);copy.addView(h);
  TextView c=text(video.channel,12,MUTED,false);c.setMaxLines(1);copy.addView(c);
  TextView d=text(video.duration+"   "+video.date,12,MUTED,false);d.setMaxLines(1);copy.addView(d);
  card.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(4),0,dp(6));
  results.addView(card,lp);
  card.setContentDescription(video.title+", "+video.channel);
  card.setOnClickListener(v->playVideo(video.id));
 }
 private void history(){savedFilms("recent_ids","Ostatnio oglądane");}
 private void savedFilms(String key,String caption){
  cancelSearch();++searchGeneration;clearThumbnails();results.removeAllViews();String raw=getPreferences(0).getString(key,"");
  int shown=0;
  for(String id:raw.split(",")){
   if(!id.matches("[A-Za-z0-9_-]{11}")||shown++>=12)continue;
   String name=getPreferences(0).getString("recent_title_"+id,"Film");
   movieCard(new PA6PipedClient.Result(name,caption,"",""+id,"",""));
  }
  feedback(shown==0?"Lista jest pusta.":caption);
 }
 private void openLink(){
  String raw=link.getText().toString().trim();String id=PA6PipedClient.extractId(raw);
  if(!id.isEmpty()){playVideo(id);return;}
  if(raw.startsWith("https://")&&raw.length()<4000&&(raw.contains(".mp4")||raw.contains(".m3u8"))){
   cancelWatchdog();++request;activeId="";activeTitle="Własny film";
   title.setText(activeTitle);subtitle.setText("Bezpośredni strumień • w PA");
   useLocalVideo();player.play("Wideo internetowe",raw);feedback("Odtwarzanie w aplikacji.");return;
  }
  feedback("Wklej link do YouTube albo bezpośredniego filmu MP4/HLS.");
 }
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  String verifyLayout=getIntent().getStringExtra("verify_layout");
  if("Smartfon".equals(verifyLayout)||"Tablet".equals(verifyLayout))getSharedPreferences("pa6_display",0).edit().putString("ui_mode",verifyLayout).apply();
  kids=getIntent().getBooleanExtra("kids",false);PA6ModernTls.install(this);
  getWindow().setStatusBarColor(0xff17263b);getWindow().setNavigationBarColor(0xff151d30);
  getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
  LinearLayout page=column();page.setBackgroundColor(WHITE);pad(page,16,12);setContentView(page);
  LinearLayout heading=line();
  TextView logo=text("▶",20,WHITE,true);logo.setGravity(Gravity.CENTER);
  logo.setBackground(panel(RED,RED,11));heading.addView(logo,new LinearLayout.LayoutParams(dp(47),-2));
  LinearLayout brand=column();brand.setPadding(dp(12),0,dp(8),0);
  TextView name=text(kids?"Tube Kids":"Alfa Tube",24,INK,true);brand.addView(name);
  TextView tagline=text("Filmy i muzyka • po Twojemu",12,MUTED,false);brand.addView(tagline);
  heading.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
  Button settings=button("⚙",RED,false);settings.setTextSize(22);settings.setContentDescription("Ustawienia Alfa Tube");
  heading.addView(settings,new LinearLayout.LayoutParams(dp(50),dp(50)));
  settings.setOnClickListener(v->showTubeSettings());
  page.addView(heading);gap(page,12);
  LinearLayout searchBar=line();
  search=new EditText(this);search.setSingleLine(true);search.setHint("Szukaj filmów i muzyki…");
  search.setTextSize(15);search.setBackground(panel(PALE,0xffe1e5ec,11));pad(search,13,4);
  searchBar.addView(search,new LinearLayout.LayoutParams(0,-2,1));
  Button go=button("Szukaj",RED,true);go.setOnClickListener(v->searchVideos(search.getText().toString().trim()));
  LinearLayout.LayoutParams gb=new LinearLayout.LayoutParams(dp(92),-2);gb.setMargins(dp(8),0,0,0);searchBar.addView(go,gb);
  page.addView(searchBar);gap(page,9);
  LinearLayout quick=line();
  for(String q:(kids?new String[]{"Bajki","Piosenki dla dzieci","Zwierzęta","Nauka"}:
     new String[]{"Muzyka","Wiadomości","Podróże","Technologia"})){
   Button tab=button(q,RED,false);tab.setTextSize(12);
   LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);
   p.setMargins(dp(2),0,dp(5),0);quick.addView(tab,p);
   tab.setOnClickListener(v->{search.setText(q);searchVideos(q);});
  }
  HorizontalScrollView topics=new HorizontalScrollView(this);
  topics.setHorizontalScrollBarEnabled(false);
  topics.addView(quick,new android.widget.FrameLayout.LayoutParams(-2,-2));
  page.addView(topics);gap(page,9);
  mediaArea=column();page.addView(mediaArea,new LinearLayout.LayoutParams(-1,0,1));
  playerPane=column();feedPane=column();
  applyLayoutMode();
  stageWrap=column();stageWrap.setBackground(panel(0xff101827,0xff101827,14));
  player=new PA6EmbeddedPlayer(this);player.setFullscreenListener(()->fullscreen.toggle(this,player));player.setPlaybackErrorListener(()->onVideoPlaybackError());
  player.setPlaybackStartListener(()->{
   cancelWatchdog();
   if(!closed&&!activeId.isEmpty())feedback("Film jest odtwarzany wewnątrz Alfa Tube.");
  });
  stageWrap.addView(player,new LinearLayout.LayoutParams(-1,-2));
  title=text("Wybierz film z listy",16,WHITE,true);pad(title,12,4);stageWrap.addView(title);
  subtitle=text("Film pojawi się tutaj, bez opuszczania Pomocnika Alfa",12,0xffc6cbd7,false);pad(subtitle,12,5);stageWrap.addView(subtitle);
  gap(stageWrap,7);
  playerPane.addView(stageWrap,new LinearLayout.LayoutParams(-1,-2));gap(playerPane,12);
  LinearLayout modes=line();
  videoModeButton=button("▶  Wideo",RED,true);audioModeButton=button("♫  Samo audio",0xff0f766e,false);
  modes.addView(videoModeButton,new LinearLayout.LayoutParams(0,dp(52),1));
  LinearLayout.LayoutParams am=new LinearLayout.LayoutParams(0,dp(52),1);am.setMargins(dp(8),0,0,0);modes.addView(audioModeButton,am);
  videoModeButton.setOnClickListener(v->chooseMode(false));audioModeButton.setOnClickListener(v->chooseMode(true));
  playerPane.addView(modes);gap(playerPane,8);
  LinearLayout actions=line();
  qualityButton=button("Jakość obrazu • "+qualityLabel(),RED,false);
  qualityButton.setOnClickListener(v->chooseQuality());
  actions.addView(qualityButton,new LinearLayout.LayoutParams(0,-2,1));
  downloadButton=button("↓  Pobierz MP4",RED,false);
  downloadButton.setEnabled(false);
  LinearLayout.LayoutParams downloadParams=new LinearLayout.LayoutParams(0,-2,1);
  downloadParams.setMargins(dp(7),0,0,0);
  actions.addView(downloadButton,downloadParams);
  downloadButton.setOnClickListener(v->downloadCurrent());
  playerPane.addView(actions,new LinearLayout.LayoutParams(-1,-2));
  downloadStatus=text("",13,INK,true);pad(downloadStatus,12,10);downloadStatus.setBackground(panel(0xffe8f1f5,0xffd5e5ed,12));downloadStatus.setVisibility(View.GONE);playerPane.addView(downloadStatus,0);

  status=text("Wybierz film lub posłuchaj samego dźwięku.",13,MUTED,false);
  pad(status,12,10);status.setBackground(panel(0xffeaf0f7,0xffeaf0f7,12));playerPane.addView(status);gap(playerPane,8);
  provider=text("Odtwarzanie wewnątrz aplikacji",11,MUTED,false);
  playerPane.addView(provider);gap(playerPane,6);
  LinearLayout links=line();
  Button recent=button("◷ Historia",RED,false);links.addView(recent,new LinearLayout.LayoutParams(0,-2,1));
  recent.setOnClickListener(v->history());
  Button favorites=button("★ Dodaj / usuń",RED,false);
  LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,-2,1);fp.setMargins(dp(7),0,0,0);links.addView(favorites,fp);
  favorites.setOnClickListener(v->{if(activeId.isEmpty()){feedback("Najpierw wybierz film.");return;}String all=getPreferences(0).getString("favorite_ids","");String token=activeId+",";boolean had=all.contains(token);all=all.replace(token,"");if(!had)all=token+all;if(all.length()>360)all=all.substring(0,360);getPreferences(0).edit().putString("favorite_ids",all).putString("recent_title_"+activeId,activeTitle).apply();feedback(had?"Usunięto z ulubionych.":"Dodano do ulubionych.");});
  playerPane.addView(links);
  LinearLayout saved=line();Button stars=button("★ Ulubione",RED,false);saved.addView(stars,new LinearLayout.LayoutParams(0,-2,1));stars.setOnClickListener(v->savedFilms("favorite_ids","Ulubione filmy"));Button downloads=button("↓ Pobrane",RED,false);saved.addView(downloads,new LinearLayout.LayoutParams(0,-2,1));downloads.setOnClickListener(v->startActivity(new Intent(this,PA6DownloadsActivity.class)));playerPane.addView(saved);
  Button catalog=button("Zwiń / rozwiń listę filmów",RED,false);playerPane.addView(catalog);catalog.setOnClickListener(v->feedPane.setVisibility(feedPane.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
  compatiblePlayerButton=button("▶ Otwórz w NewPipe / SkyTube (osobna aplikacja)",RED,false);
  compatiblePlayerButton.setVisibility(View.GONE);
  LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
  cp.setMargins(0,dp(7),0,dp(5));
  playerPane.addView(compatiblePlayerButton,cp);
  compatiblePlayerButton.setOnClickListener(v->openInstalledCompatiblePlayer());
  ScrollView sc=new ScrollView(this);sc.setFillViewport(false);
  feedPane.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout below=column();sc.addView(below);
  TextView section=text("Odkrywaj",21,INK,true);pad(section,7,15);below.addView(section);
  results=column();below.addView(results);
  gap(below,16);
  Button advancedToggle=button("＋  Linki, pliki i źródła",RED,false);below.addView(advancedToggle,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout tools=column();tools.setVisibility(View.GONE);below.addView(tools);
  advancedToggle.setOnClickListener(v->{boolean show=tools.getVisibility()!=View.VISIBLE;tools.setVisibility(show?View.VISIBLE:View.GONE);advancedToggle.setText(show?"−  Zwiń dodatkowe opcje":"＋  Linki, pliki i źródła");});
  link=new EditText(this);link.setSingleLine(true);link.setTextSize(14);
  link.setHint("Wklej link YouTube / MP4 / HLS");link.setBackground(panel(PALE,0xffdde2eb,11));pad(link,12,6);
  tools.addView(link,new LinearLayout.LayoutParams(-1,-2));
  Button open=button("▶ Odtwórz link wewnątrz PA",RED,true);tools.addView(open,new LinearLayout.LayoutParams(-1,-2));
  open.setOnClickListener(v->openLink());gap(tools,8);
  Button demo=button("▶ Uruchom film kontrolny w Alfa Tube",RED,false);
  tools.addView(demo,new LinearLayout.LayoutParams(-1,-2));
  demo.setOnClickListener(v->{
   cancelWatchdog();++request;activeId="";title.setText("Film kontrolny H.264");
   subtitle.setText("Test zgodności tabletu • obraz + dźwięk");
   useLocalVideo();player.play("Film kontrolny","https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8");
   feedback("Materiał testowy, nie YouTube. Odtwarzanie odbywa się wewnątrz PA.");
  });
  officialButton=button("Awaryjnie: oglądaj w PA przez YouTube (możliwe reklamy)",RED,false);
  tools.addView(officialButton,new LinearLayout.LayoutParams(-1,-2));
  officialButton.setVisibility(View.GONE);
  officialButton.setOnClickListener(v->showOfficialEmbedded());
  Button file=button("Wybierz film zapisany na tablecie",RED,false);tools.addView(file,new LinearLayout.LayoutParams(-1,-2));
  file.setOnClickListener(v->{
   try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/*").addCategory(Intent.CATEGORY_OPENABLE),PICK_FILE);}
   catch(Exception e){feedback("Nie udało się wybrać pliku.");}
  });
  if(kids){TextView caution=text("Wyszukiwanie kieruje do treści dziecięcych, ale to nie YouTube Kids: nie zapewnia pełnej filtracji ani nadzoru rodzicielskiego.",12,RED,true);pad(caution,8,9);tools.addView(caution);}
  TextView serverTitle=text("Serwer katalogu Piped (opcjonalnie)",15,INK,true);pad(serverTitle,8,12);tools.addView(serverTitle);
  serverField=new EditText(this);serverField.setSingleLine(true);serverField.setHint("https://pipedapi…");
  serverField.setText(getPreferences(0).getString("piped_server",""));
  serverField.setBackground(panel(PALE,0xffdde2eb,11));pad(serverField,12,6);
  tools.addView(serverField,new LinearLayout.LayoutParams(-1,-2));
  Button save=button("Zapisz serwer",RED,false);tools.addView(save,new LinearLayout.LayoutParams(-1,-2));
  save.setOnClickListener(v->{
   String value=serverField.getText().toString().trim();
   if(value.isEmpty()||!PA6PipedClient.validServer(value).isEmpty()){
    getPreferences(0).edit().putString("piped_server",value).apply();
    feedback("Serwer zapisany. Wyszukaj ponownie film.");
   }else feedback("Wpisz poprawny adres serwera HTTPS.");
  });
  gap(tools,18);
  TextView foot=text("Wewnętrzny VLC nadal działa w PA. Jeśli YouTube zablokuje strumień, możesz świadomie otworzyć film w osobno zainstalowanym NewPipe lub SkyTube. To tryb zgodności, nie odtwarzanie wewnątrz PA.",12,MUTED,false);pad(foot,9,12);tools.addView(foot);
  updateQualityButton();
  page.setFocusableInTouchMode(true);page.requestFocus();
  if(getIntent().getBooleanExtra("verify_libvlc",false)&&!player.verifyNative())
   throw new IllegalStateException("PA6 Alfa Tube native player unavailable");
  // First screen shows meaningful content without pretending to have a working feed.
  if(getIntent().getBooleanExtra("verify_audio_only",false)){
   getPreferences(0).edit().putBoolean("tube_audio_only",true).apply();updateQualityButton();
   player.setAudioOnly(true);title.setText("Test samego audio");subtitle.setText("AAC • bez dekodowania wideo");
   player.backgroundAudio("Test samego audio");PA6AudioService.start(this,"","Test samego audio",Uri.fromFile(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"pa6-audio.m4a")).toString());
   feedback("Samo audio • słuchasz bez obrazu");
  }else if(getIntent().getBooleanExtra("verify_quality_menu",false)){
   getPreferences(0).edit().putBoolean("tube_audio_only",false).apply();updateQualityButton();chooseQuality();
  }else if(getIntent().getBooleanExtra("verify_performance",false)){
   verifyPerformance();
  }else if(getIntent().getBooleanExtra("verify_download_validation",false)){
   io.execute(()->{
    try{
     File folder=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
     PA6DownloadService.verifyComplete(new File(folder,"pa6-av.mp4"));
     boolean rejected=false;
     try{PA6DownloadService.verifyComplete(new File(folder,"pa6-silent.mp4"));}catch(IOException expected){rejected=true;}
     if(!rejected)throw new IOException("Silent MP4 accepted");
     android.util.Log.i("PA6_DOWNLOAD_TEST","PASS complete AV accepted; silent MP4 rejected");
    }catch(Exception error){android.util.Log.e("PA6_DOWNLOAD_TEST","FAIL MP4 validation",error);}
   });
  }else if(getIntent().getBooleanExtra("verify_download_failure",false)){
   startDownload("invalid");
  }else if(getIntent().getBooleanExtra("verify_official_embed",false)){
   activeId="jNQXAC9IVRw";title.setText("YouTube • film demonstracyjny");
   showOfficialEmbedded();
  }else if(getIntent().getBooleanExtra("verify_real_youtube",false)){
   playVideo("jNQXAC9IVRw");
  }else if(getIntent().getBooleanExtra("verify_video_stream",false)){
   title.setText("Film kontrolny H.264");
   subtitle.setText("Test obrazu i dźwięku na Androidzie 6");
   useLocalVideo();player.play("Film kontrolny H.264","https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8");
   feedback("Sprawdzam odtwarzanie materiału kontrolnego…");
  }else if(!PA6AudioService.active&&!getIntent().hasExtra("offline_path")&&!getIntent().hasExtra("play_id")&&!getIntent().getBooleanExtra("verify_controls",false)){search.setText(kids?"Bajki":"muzyka");searchVideos(kids?"Bajki":"muzyka");}
  if(getIntent().getBooleanExtra("verify_controls",false)){useLocalVideo();player.setAudioOnly(false);player.playUri("Test sterowania",Uri.fromFile(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"pa6-av.mp4")));}
  String shortcut=getIntent().getStringExtra("play_id");if(shortcut!=null&&shortcut.matches("[A-Za-z0-9_-]{11}"))playVideo(shortcut);
  String offline=getIntent().getStringExtra("offline_path");if(offline!=null)try{File f=new File(offline).getCanonicalFile();File downloads=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getCanonicalFile();if(f.isFile()&&f.getParentFile().equals(downloads)&&f.getName().endsWith(".mp4")){useLocalVideo();stopService(new Intent(this,PA6AudioService.class));player.setAudioOnly(false);player.playUri(f.getName(),Uri.fromFile(f));title.setText(f.getName());subtitle.setText("Plik z pamięci • offline");}}catch(Exception e){feedback("Nie można otworzyć pliku.");}
 }
 private void verifyPerformance(){
  final long started=SystemClock.elapsedRealtime();
  ArrayList<PA6PipedClient.Result> first=new ArrayList<>(),second=new ArrayList<>();
  for(int i=0;i<36;i++)first.add(new PA6PipedClient.Result("Film "+i,"Kontrola płynności","","","",""));
  second.addAll(first.subList(0,3));
  final PA6PipedClient.Response<List<PA6PipedClient.Result>> old=new PA6PipedClient.Response<List<PA6PipedClient.Result>>(first,"Pamięć testowa");
  SEARCHES.put(server()+"|pa6 performance",new CachedSearch(old));
  SEARCHES.put(server()+"|pa6 replacement",new CachedSearch(new PA6PipedClient.Response<List<PA6PipedClient.Result>>(second,"Pamięć testowa")));
  searchVideos("pa6 performance");final int stale=searchGeneration;
  main.postDelayed(new Runnable(){public void run(){
   if(closed)return;
   if(results.getChildCount()!=13&&SystemClock.elapsedRealtime()-started<5000){main.postDelayed(this,50);return;}
   try{
    if(results.getChildCount()!=13)throw new IllegalStateException("Result pagination failed");
    searchVideos("pa6 replacement");
    showSearchResults(old,stale);
    if(results.getChildCount()!=3)throw new IllegalStateException("Stale search replaced current results");
    PA6NewPipeClient.Work work=new PA6NewPipeClient.Work();
    okhttp3.OkHttpClient client=new okhttp3.OkHttpClient();
    okhttp3.Call obsolete=client.newCall(new okhttp3.Request.Builder().url("https://example.com/").build());
    okhttp3.Call independent=client.newCall(new okhttp3.Request.Builder().url("https://example.com/").build());
    work.add(obsolete);work.cancel();
    if(!obsolete.isCanceled()||independent.isCanceled())throw new IllegalStateException("Cancellation isolation failed");
    android.util.Log.i("PA6_PERF","PASS cached search, 12-item page, stale-result rejection, isolated HTTP cancellation; UI_MS="+(SystemClock.elapsedRealtime()-started));
   }catch(Exception error){android.util.Log.e("PA6_PERF","FAIL performance regression",error);}
  }},50);


 }
 @Override protected void onActivityResult(int requestCode,int result,Intent data){
  super.onActivityResult(requestCode,result,data);
  if(requestCode!=PICK_FILE||result!=RESULT_OK||data==null||data.getData()==null)return;
  cancelWatchdog();++request;activeId="";activeTitle="Film z tabletu";
  title.setText(activeTitle);subtitle.setText("Lokalny plik • offline");
  player.setAudioOnly(audioOnly());player.playUri(activeTitle,data.getData());feedback("Odtwarzanie pliku z pamięci urządzenia.");
 }
 @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);applyLayoutMode();}
 @Override protected void onStart(){super.onStart();if(PA6AudioService.active&&activeId.isEmpty()&&!getIntent().hasExtra("offline_path")&&!getIntent().getBooleanExtra("verify_controls",false)){activeId=PA6AudioService.videoId;activeTitle=PA6AudioService.title;title.setText(activeTitle);player.backgroundAudio(activeTitle);}main.removeCallbacks(downloadMonitor);main.post(downloadMonitor);}
 @Override protected void onStop(){
  main.removeCallbacks(downloadMonitor);cancelSearch();cancelPlay();++searchGeneration;clearThumbnails();
  // Never leave hidden video decoding when the user switches to other PA modules.
  cancelWatchdog();++request;activeId="";playbackPending=false;
  if(officialWeb!=null)officialWeb.onPause();
  if(player!=null&&!PA6AudioService.active)player.stop();
  super.onStop();
 }
 @Override protected void onDestroy(){
  fullscreen.close();closed=true;main.removeCallbacks(downloadMonitor);cancelWatchdog();request++;io.shutdownNow();searchIo.shutdownNow();thumbnailIo.shutdownNow();
  if(officialWeb!=null){officialWeb.stopLoading();officialWeb.destroy();officialWeb=null;}
  if(player!=null)player.release();
  super.onDestroy();
 }
}
