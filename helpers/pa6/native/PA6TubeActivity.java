package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
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
 private static final int PICK_FILE=791;
 private static final int WHITE=0xfff8fafc,INK=0xff172036,MUTED=0xff596574,RED=0xffed263b,PALE=0xffedf0f5;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService io=Executors.newFixedThreadPool(2);
 private final ExecutorService thumbnailIo=Executors.newFixedThreadPool(2);
 private LinearLayout results,stageWrap;
 private PA6EmbeddedPlayer player;
 private WebView officialWeb;
 private Button officialButton,compatiblePlayerButton;
 private TextView status,title,subtitle,provider;
 private EditText search,link,serverField;
 private int request=0;
 private boolean closed=false,kids;
 private String activeId="",activeTitle="",currentProvider="";
 private final HashSet<String> failedProviders=new HashSet<>();
 private int playbackAttempts=0;
 private boolean playbackPending=false;
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
 private GradientDrawable panel(int color,int border,int r){
  GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(r));d.setStroke(dp(1),border);return d;
 }
 private TextView text(String s,int size,int color,boolean bold){
  TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);
  if(bold)t.setTypeface(null,1);return t;
 }
 private Button button(String value,int color,boolean emphasized){
  Button b=new Button(this);b.setAllCaps(false);b.setText(value);b.setTextSize(14);
  b.setTextColor(emphasized?WHITE:INK);b.setMinHeight(dp(47));
  b.setBackground(panel(emphasized?color:WHITE,emphasized?color:0xffdce2ed,12));return b;
 }
 private void pad(View v,int x,int y){v.setPadding(dp(x),dp(y),dp(x),dp(y));}
 private void gap(LinearLayout l,int h){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
 private LinearLayout line(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 private String server(){return getPreferences(0).getString("piped_server","");}
 private void feedback(String message){status.setText(message);}
 private void disableKeyboard(){
  try{((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);}catch(Exception ignored){}
 }
 private void playVideo(final String id){
  if(id==null||!id.matches("[A-Za-z0-9_-]{11}")){feedback("Nieprawidłowy adres filmu.");return;}
  switchBackToNative();
  if(officialButton!=null)officialButton.setVisibility(View.GONE);
  ++request;activeId=id;playbackAttempts=0;failedProviders.clear();currentProvider="";
  if(compatiblePlayerButton!=null)compatiblePlayerButton.setVisibility(View.VISIBLE);
  resolveAndPlay(id,request);
 }
 private void resolveAndPlay(final String id,final int seq){
  playbackPending=true;
  feedback(playbackAttempts==0?"Sprawdzam film bezpośrednio przez NewPipe Extractor…":
    "Pierwsze źródło nie odtworzyło filmu. Sprawdzam serwer zapasowy ("+playbackAttempts+"/6)…");
  final HashSet<String> ignored=new HashSet<>(failedProviders);
  io.execute(()->{
   try{
    PA6PipedClient.Response<PA6PipedClient.Video> response=null;
    if(!ignored.contains("NewPipe Extractor")){
     try{
      response=PA6NewPipeClient.resolve(id);
     }catch(Throwable npe){
      android.util.Log.w("PA6_NEWPIPE","Native extractor not available: "+npe.getClass().getSimpleName()+" "+npe.getMessage());
     }
    }
    if(response==null)response=PA6PipedClient.resolve(id,server(),ignored);
    final PA6PipedClient.Response<PA6PipedClient.Video> selected=response;
    main.post(()->{
     if(closed||seq!=request)return;
     playbackPending=false;
     PA6PipedClient.Video v=selected.data;
     currentProvider=selected.server;
     activeTitle=v.title;title.setText(v.title);
     subtitle.setText(v.channel+" • "+v.quality);
     provider.setText("Źródło filmu: "+selected.server.replace("https://",""));
     player.play(v.title,v.stream);
     feedback("Łączenie z filmem. Jeśli ten serwer zawiedzie, aplikacja wybierze następny.");
     String old=getPreferences(0).getString("recent_ids","");
     String next=id+","+old.replace(id+",","");
     if(next.length()>360)next=next.substring(0,360);
     getPreferences(0).edit().putString("recent_ids",next).putString("recent_title_"+id,v.title).apply();
    });
   }catch(Exception e){
    final String why=e.getMessage();
    main.post(()->{
     if(closed||seq!=request)return;
     playbackPending=false;
     feedback("Wewnętrzny odtwarzacz nie pobrał filmu. "+why+
      " Możesz otworzyć go w NewPipe lub SkyTube.");
     if(officialButton!=null)officialButton.setVisibility(View.VISIBLE);
    });
   }
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
  for(String name:packages){
   try{
    getPackageManager().getPackageInfo(name,0);
    Intent intent=new Intent(Intent.ACTION_VIEW,video);
    intent.addCategory(Intent.CATEGORY_BROWSABLE);
    intent.setPackage(name);
    if(intent.resolveActivity(getPackageManager())==null)continue;
    startActivity(intent);return;
   }catch(android.content.pm.PackageManager.NameNotFoundException ignored){
   }catch(ActivityNotFoundException e){
    android.util.Log.w("PA6_BRIDGE","Nie udało się otworzyć "+name,e);
   }catch(SecurityException e){
    android.util.Log.w("PA6_BRIDGE","Odtwarzacz jest niedostępny "+name,e);
   }
  }
  new AlertDialog.Builder(this).setTitle("Brak NewPipe lub SkyTube")
   .setMessage("Zainstaluj zgodny odtwarzacz na tablecie. Nie otwieram przeglądarki bez Twojej zgody.")
   .setPositiveButton("OK",null).show();
 }
 private void showOfficialEmbedded(){
  if(activeId==null||!activeId.matches("[A-Za-z0-9_-]{11}")){
   feedback("Najpierw wybierz film z wyników albo wklej link YouTube.");return;
  }
  ++request;playbackPending=false;
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
  if(closed||activeId.isEmpty()||playbackPending)return;
  if(!currentProvider.isEmpty())failedProviders.add(currentProvider);
  if(++playbackAttempts>6){
   feedback("Wewnętrzny odtwarzacz nie uruchomił filmu. Wybierz NewPipe lub SkyTube (osobna aplikacja).");
   if(officialButton!=null)officialButton.setVisibility(View.VISIBLE);
   return;
  }
  resolveAndPlay(activeId,request);
 }
 private void searchVideos(final String query){
  if(query.isEmpty()){feedback("Wpisz nazwę filmu, kanału lub wykonawcy.");return;}
  disableKeyboard();int seq=++request;
  feedback("Wyszukiwanie: "+query+"…");
  results.removeAllViews();
  io.execute(()->{
   try{
    String terms=kids?(query+" bajki dla dzieci"):query;
    PA6PipedClient.Response<java.util.List<PA6PipedClient.Result>> selected;
    try{
     selected=PA6NewPipeClient.search(terms);
    }catch(Exception | LinkageError nativeFailure){
     android.util.Log.w("PA6_NEWPIPE","Direct search unavailable; trying backup catalogs",nativeFailure);
     selected=PA6PipedClient.search(terms,server());
    }
    final PA6PipedClient.Response<java.util.List<PA6PipedClient.Result>> response=selected;
    main.post(()->{
     if(closed||seq!=request)return;
     java.util.List<PA6PipedClient.Result> list=response.data;
     provider.setText("Wyniki filmów • "+response.server.replace("https://",""));
     feedback(list.isEmpty()?"Brak wyników. Spróbuj innego hasła.":"Znaleziono filmów: "+list.size()+" • dotknij miniatury, aby oglądać tutaj.");
     for(PA6PipedClient.Result v:list)movieCard(v);
    });
   }catch(Exception e){
    final String reason=e.getMessage();
    main.post(()->{if(closed||seq!=request)return;
     feedback(reason+" Możesz spróbować ponownie lub zmienić serwer.");
     TextView empty=text("Nie udało się wyszukać filmów przez NewPipe ani serwery zapasowe. Sprawdź internet lub odtwórz plik z tabletu.",15,MUTED,false);
     pad(empty,14,17);results.addView(empty);
    });
   }
  });
 }
 private void thumb(ImageView image,String suggested,String videoId){
  if(videoId==null||!videoId.matches("[A-Za-z0-9_-]{11}"))return;
  // Prefer Google's compact 320px thumbnail; old tablet has only 2 GB RAM.
  final String direct="https://i.ytimg.com/vi/"+videoId+"/mqdefault.jpg";
  thumbnailIo.execute(()->{
   String[] urls={direct,suggested};
   for(String url:urls){
    if(url==null||!url.startsWith("https://")||url.length()>2400)continue;
    HttpURLConnection c=null;
    try{
     c=PA6ModernTls.open(url);c.setConnectTimeout(3500);c.setReadTimeout(4500);
     c.setRequestProperty("User-Agent","Mozilla/5.0 (Android 6; Alfa Tube)");
     if(c.getResponseCode()!=200||c.getContentLength()>300000)continue;
     ByteArrayOutputStream out=new ByteArrayOutputStream(26000);
     try(InputStream in=c.getInputStream()){
      byte[] buf=new byte[4096];int n;
      while((n=in.read(buf))>0){if(out.size()+n>300000)break;out.write(buf,0,n);}
     }
     byte[] data=out.toByteArray();
     if(data.length==0||data.length>300000)continue;
     BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;
     o.inPreferredConfig=Bitmap.Config.RGB_565;
     Bitmap result=BitmapFactory.decodeByteArray(data,0,data.length,o);
     if(result!=null){
      main.post(()->{if(!closed&&!isFinishing())image.setImageBitmap(result);});
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
  card.setBackground(panel(WHITE,0xffe0e5ed,14));
  ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
  poster.setBackground(panel(0xffe6ebf3,0xffe6ebf3,9));
  FrameLayout imageFrame=new FrameLayout(this);
  imageFrame.addView(poster,new FrameLayout.LayoutParams(-1,-1));
  TextView playBadge=text("▶",23,WHITE,true);
  playBadge.setGravity(Gravity.CENTER);playBadge.setBackground(panel(0xcced263b,0xcced263b,24));
  FrameLayout.LayoutParams badgeParams=new FrameLayout.LayoutParams(dp(39),dp(39),Gravity.CENTER);
  imageFrame.addView(playBadge,badgeParams);
  card.addView(imageFrame,new LinearLayout.LayoutParams(dp(157),dp(91)));
  thumb(poster,video.thumb,video.id);
  LinearLayout copy=column();copy.setPadding(dp(13),0,0,0);
  TextView h=text(video.title,15,INK,true);h.setMaxLines(2);copy.addView(h);
  TextView c=text(video.channel,13,MUTED,false);c.setMaxLines(1);copy.addView(c);
  TextView d=text(video.duration+"   "+video.date,12,MUTED,false);d.setMaxLines(1);copy.addView(d);
  card.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(4),0,dp(6));
  results.addView(card,lp);
  card.setContentDescription(video.title+", "+video.channel);
  card.setOnClickListener(v->playVideo(video.id));
 }
 private void history(){
  results.removeAllViews();String raw=getPreferences(0).getString("recent_ids","");
  int shown=0;
  for(String id:raw.split(",")){
   if(!id.matches("[A-Za-z0-9_-]{11}")||shown++>=12)continue;
   String name=getPreferences(0).getString("recent_title_"+id,"Film");
   movieCard(new PA6PipedClient.Result(name,"Ostatnio oglądane","",""+id,"",""));
  }
  feedback(shown==0?"Historia jest pusta.":"Twoja historia oglądania.");
 }
 private void openLink(){
  String raw=link.getText().toString().trim();String id=PA6PipedClient.extractId(raw);
  if(!id.isEmpty()){playVideo(id);return;}
  if(raw.startsWith("https://")&&raw.length()<4000&&(raw.contains(".mp4")||raw.contains(".m3u8"))){
   ++request;activeId="";activeTitle="Własny film";
   title.setText(activeTitle);subtitle.setText("Bezpośredni strumień • w PA");
   player.play("Wideo internetowe",raw);feedback("Odtwarzanie w aplikacji.");return;
  }
  feedback("Wklej link do YouTube albo bezpośredniego filmu MP4/HLS.");
 }
 @Override public void onCreate(Bundle b){
  super.onCreate(b);kids=getIntent().getBooleanExtra("kids",false);PA6ModernTls.install(this);
  getWindow().setStatusBarColor(0xfff8fafc);getWindow().setNavigationBarColor(0xff151d30);
  getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
  LinearLayout page=column();page.setBackgroundColor(WHITE);pad(page,12,8);setContentView(page);
  LinearLayout heading=line();
  TextView logo=text("▶",20,WHITE,true);logo.setGravity(Gravity.CENTER);
  logo.setBackground(panel(RED,RED,11));heading.addView(logo,new LinearLayout.LayoutParams(dp(47),dp(42)));
  TextView name=text(kids?"Tube Kids":"Alfa Tube",23,INK,true);name.setPadding(dp(9),0,0,0);
  heading.addView(name,new LinearLayout.LayoutParams(0,-2,1));
  TextView tag=text("ANDROID 6",11,MUTED,true);heading.addView(tag);
  page.addView(heading);gap(page,12);
  LinearLayout searchBar=line();
  search=new EditText(this);search.setSingleLine(true);search.setHint("Szukaj filmów i muzyki…");
  search.setTextSize(15);search.setBackground(panel(PALE,0xffe1e5ec,11));pad(search,13,4);
  searchBar.addView(search,new LinearLayout.LayoutParams(0,dp(49),1));
  Button go=button("Szukaj",RED,true);go.setOnClickListener(v->searchVideos(search.getText().toString().trim()));
  LinearLayout.LayoutParams gb=new LinearLayout.LayoutParams(dp(92),dp(49));gb.setMargins(dp(8),0,0,0);searchBar.addView(go,gb);
  page.addView(searchBar);gap(page,9);
  LinearLayout quick=line();
  for(String q:(kids?new String[]{"Bajki","Piosenki dla dzieci","Zwierzęta","Nauka"}:
     new String[]{"Muzyka","Wiadomości","Podróże","Technologia"})){
   Button tab=button(q,RED,false);tab.setTextSize(12);
   LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(42),1);p.setMargins(dp(2),0,dp(2),0);quick.addView(tab,p);
   tab.setOnClickListener(v->{search.setText(q);searchVideos(q);});
  }
  page.addView(quick);gap(page,9);
  float widthDp=getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density;
  boolean wide=widthDp>=660;
  LinearLayout mediaArea=column();
  mediaArea.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
  page.addView(mediaArea,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout playerPane=column(),feedPane=column();
  if(wide){
   LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,-1,4);pp.setMargins(0,0,dp(13),0);
   mediaArea.addView(playerPane,pp);
   mediaArea.addView(feedPane,new LinearLayout.LayoutParams(0,-1,5));
  }else{
   mediaArea.addView(playerPane,new LinearLayout.LayoutParams(-1,-2));
   mediaArea.addView(feedPane,new LinearLayout.LayoutParams(-1,0,1));
  }
  stageWrap=column();stageWrap.setBackground(panel(0xff101827,0xff101827,14));
  player=new PA6EmbeddedPlayer(this);player.setPlaybackErrorListener(()->onVideoPlaybackError());
  stageWrap.addView(player,new LinearLayout.LayoutParams(-1,-2));
  title=text("Wybierz film z listy",16,WHITE,true);pad(title,12,4);stageWrap.addView(title);
  subtitle=text("Film pojawi się tutaj, bez opuszczania Pomocnika Alfa",12,0xffc6cbd7,false);pad(subtitle,12,5);stageWrap.addView(subtitle);
  gap(stageWrap,7);
  playerPane.addView(stageWrap,new LinearLayout.LayoutParams(-1,-2));
  status=text("Alfa Tube wyszukuje przez NewPipe. Odtwarzacz działa wewnątrz PA.",13,MUTED,false);
  pad(status,5,8);playerPane.addView(status);
  provider=text("Wbudowany VLC • osobny NewPipe/SkyTube tylko na Twoje życzenie",11,MUTED,false);
  playerPane.addView(provider);gap(playerPane,6);
  LinearLayout links=line();
  Button recent=button("◷ Historia",RED,false);links.addView(recent,new LinearLayout.LayoutParams(0,dp(46),1));
  recent.setOnClickListener(v->history());
  Button favorites=button("★ Obejrzyj ponownie",RED,false);
  LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,dp(46),1);fp.setMargins(dp(7),0,0,0);links.addView(favorites,fp);
  favorites.setOnClickListener(v->{if(activeId.isEmpty())feedback("Najpierw wybierz film.");else playVideo(activeId);});
  playerPane.addView(links);
  compatiblePlayerButton=button("▶ Otwórz w NewPipe / SkyTube (osobna aplikacja)",RED,false);
  compatiblePlayerButton.setVisibility(View.GONE);
  LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(54));
  cp.setMargins(0,dp(7),0,dp(5));
  playerPane.addView(compatiblePlayerButton,cp);
  compatiblePlayerButton.setOnClickListener(v->openInstalledCompatiblePlayer());
  ScrollView sc=new ScrollView(this);sc.setFillViewport(false);
  feedPane.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout below=column();sc.addView(below);
  TextView section=text("Propozycje filmów",19,INK,true);pad(section,7,15);below.addView(section);
  results=column();below.addView(results);
  TextView advanced=text("Własne filmy i ustawienia",17,INK,true);pad(advanced,7,12);below.addView(advanced);
  link=new EditText(this);link.setSingleLine(true);link.setTextSize(14);
  link.setHint("Wklej link YouTube / MP4 / HLS");link.setBackground(panel(PALE,0xffdde2eb,11));pad(link,12,6);
  below.addView(link,new LinearLayout.LayoutParams(-1,dp(53)));
  Button open=button("▶ Odtwórz link wewnątrz PA",RED,true);below.addView(open,new LinearLayout.LayoutParams(-1,dp(51)));
  open.setOnClickListener(v->openLink());gap(below,8);
  Button demo=button("▶ Uruchom film kontrolny w Alfa Tube",RED,false);
  below.addView(demo,new LinearLayout.LayoutParams(-1,dp(50)));
  demo.setOnClickListener(v->{
   ++request;activeId="";title.setText("Film kontrolny H.264");
   subtitle.setText("Test zgodności tabletu • obraz + dźwięk");
   player.play("Film kontrolny","https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8");
   feedback("Materiał testowy, nie YouTube. Odtwarzanie odbywa się wewnątrz PA.");
  });
  officialButton=button("Awaryjnie: oglądaj w PA przez YouTube (możliwe reklamy)",RED,false);
  below.addView(officialButton,new LinearLayout.LayoutParams(-1,dp(52)));
  officialButton.setVisibility(View.GONE);
  officialButton.setOnClickListener(v->showOfficialEmbedded());
  Button file=button("Wybierz film zapisany na tablecie",RED,false);below.addView(file,new LinearLayout.LayoutParams(-1,dp(51)));
  file.setOnClickListener(v->{
   try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/*").addCategory(Intent.CATEGORY_OPENABLE),PICK_FILE);}
   catch(Exception e){feedback("Nie udało się wybrać pliku.");}
  });
  if(kids){TextView caution=text("Wyszukiwanie kieruje do treści dziecięcych, ale to nie YouTube Kids: nie zapewnia pełnej filtracji ani nadzoru rodzicielskiego.",12,RED,true);pad(caution,8,9);below.addView(caution);}
  TextView serverTitle=text("Serwer katalogu Piped (opcjonalnie)",15,INK,true);pad(serverTitle,8,12);below.addView(serverTitle);
  serverField=new EditText(this);serverField.setSingleLine(true);serverField.setHint("https://pipedapi…");
  serverField.setText(getPreferences(0).getString("piped_server",""));
  serverField.setBackground(panel(PALE,0xffdde2eb,11));pad(serverField,12,6);
  below.addView(serverField,new LinearLayout.LayoutParams(-1,dp(52)));
  Button save=button("Zapisz serwer",RED,false);below.addView(save,new LinearLayout.LayoutParams(-1,dp(48)));
  save.setOnClickListener(v->{
   String value=serverField.getText().toString().trim();
   if(value.isEmpty()||!PA6PipedClient.validServer(value).isEmpty()){
    getPreferences(0).edit().putString("piped_server",value).apply();
    feedback("Serwer zapisany. Wyszukaj ponownie film.");
   }else feedback("Wpisz poprawny adres serwera HTTPS.");
  });
  gap(below,18);
  TextView foot=text("Wewnętrzny VLC nadal działa w PA. Jeśli YouTube zablokuje strumień, możesz świadomie otworzyć film w osobno zainstalowanym NewPipe lub SkyTube. To tryb zgodności, nie odtwarzanie wewnątrz PA.",12,MUTED,false);pad(foot,9,12);below.addView(foot);
  page.setFocusableInTouchMode(true);page.requestFocus();
  if(getIntent().getBooleanExtra("verify_libvlc",false)&&!player.verifyNative())
   throw new IllegalStateException("PA6 Alfa Tube native player unavailable");
  // First screen shows meaningful content without pretending to have a working feed.
  if(getIntent().getBooleanExtra("verify_official_embed",false)){
   activeId="jNQXAC9IVRw";title.setText("YouTube • film demonstracyjny");
   showOfficialEmbedded();
  }else if(getIntent().getBooleanExtra("verify_real_youtube",false)){
   playVideo("jNQXAC9IVRw");
  }else if(getIntent().getBooleanExtra("verify_video_stream",false)){
   title.setText("Film kontrolny H.264");
   subtitle.setText("Test obrazu i dźwięku na Androidzie 6");
   player.play("Film kontrolny H.264","https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8");
   feedback("Sprawdzam odtwarzanie materiału kontrolnego…");
  }else{search.setText(kids?"Bajki":"muzyka");searchVideos(kids?"Bajki":"muzyka");}
 }
 @Override protected void onActivityResult(int requestCode,int result,Intent data){
  super.onActivityResult(requestCode,result,data);
  if(requestCode!=PICK_FILE||result!=RESULT_OK||data==null||data.getData()==null)return;
  ++request;activeId="";activeTitle="Film z tabletu";
  title.setText(activeTitle);subtitle.setText("Lokalny plik • offline");
  player.playUri(activeTitle,data.getData());feedback("Odtwarzanie pliku z pamięci urządzenia.");
 }
 @Override protected void onStop(){
  // Never leave hidden video decoding when the user switches to other PA modules.
  ++request;activeId="";playbackPending=false;
  if(officialWeb!=null)officialWeb.onPause();
  if(player!=null)player.stop();
  super.onStop();
 }
 @Override protected void onDestroy(){
  closed=true;request++;io.shutdownNow();thumbnailIo.shutdownNow();
  if(officialWeb!=null){officialWeb.stopLoading();officialWeb.destroy();officialWeb=null;}
  if(player!=null)player.release();
  super.onDestroy();
 }
}
