package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/** Android 6 lightweight TV: fixed video surface, lower HLS variants and external fallback. */
public final class PA6TvActivity extends Activity {
 private VideoView video;
 private TextView status;
 private EditText custom;
 private String activeUrl="";
 private boolean useVlcWhenInstalled=true;
 private boolean vlcPresent(){try{getPackageManager().getPackageInfo("org.videolan.vlc",0);return true;}catch(Exception e){return false;}}
 private int generation=0;
 private final int violet=0xff593d89;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
 private Button action(String label){
  Button b=new Button(this);b.setText(label);b.setTextSize(15);
  b.setAllCaps(false);b.setTextColor(violet);b.setMinHeight(dp(53));return b;
 }
 private TextView label(String text,int size){
  TextView t=new TextView(this);t.setText(text);t.setTextSize(size);
  t.setTextColor(0xff30243f);t.setPadding(dp(6),dp(7),dp(6),dp(7));return t;
 }
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  LinearLayout page=new LinearLayout(this);
  page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(0xfff7f3fa);
  page.setPadding(dp(12),dp(8),dp(12),dp(5));setContentView(page);
  TextView head=label("📺  Telewizja • Pomocnik Alfa",23);
  head.setTypeface(null,1);page.addView(head);
  // Critical: SurfaceView/VideoView must not be nested in a moving ScrollView.
  video=new VideoView(this);
  video.setBackgroundColor(Color.BLACK);
  page.addView(video,new LinearLayout.LayoutParams(-1,dp(245)));
  MediaController controller=new MediaController(this);
  controller.setAnchorView(video);video.setMediaController(controller);
  status=label("Wybierz kanał. Na starszym Acerze transmisje HLS najlepiej odtwarzać przez VLC.",14);
  page.addView(status);
  video.setOnPreparedListener(p->{
   p.setScreenOnWhilePlaying(true);
   p.setOnVideoSizeChangedListener((mp,w,h)->{
    if(w>0&&h>0)status.setText("Obraz i dźwięk: "+w+"×"+h+". Dotknij obrazu, by pokazać sterowanie.");
   });
   video.start();
   final int seq=generation;
   handler.postDelayed(()->{
    if(seq!=generation||video==null||isFinishing())return;
    if(p.getVideoWidth()==0||p.getVideoHeight()==0){
     status.setText("Słychać dźwięk, ale brak obrazu. Ten wariant transmisji może być niezgodny z kodekiem w tablecie. Użyj odtwarzacza zewnętrznego poniżej.");
    }
   },12000);
  });
  video.setOnErrorListener((p,what,extra)->{
   status.setText("Brak obrazu. Nie udało się odtworzyć transmisji na Androidzie 6 (błąd "+what+"/"+extra+"). Możesz otworzyć ją w innym odtwarzaczu.");
   return true;
  });
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
  page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout options=new LinearLayout(this);options.setOrientation(1);
  options.setPadding(dp(4),0,dp(4),dp(24));scroll.addView(options);
  Button stop=action("■  Zatrzymaj telewizję");options.addView(stop);
  stop.setOnClickListener(v->{++generation;video.stopPlayback();status.setText("Zatrzymano telewizję.");});
  Button external=action("▶  Otwórz w innym odtwarzaczu (np. VLC)");options.addView(external);
  external.setOnClickListener(v->external());
  final CheckBox useVlc=new CheckBox(this);
  useVlc.setText("Odtwarzaj przez VLC, jeżeli jest zainstalowany (zalecane)");
  useVlc.setChecked(true);
  useVlc.setOnCheckedChangeListener((view,checked)->useVlcWhenInstalled=checked);
  options.addView(useVlc);
  Button help=action(vlcPresent()?"✓ VLC wykryty na tablecie":"Pobierz zgodny odtwarzacz VLC");
  options.addView(help);
  help.setOnClickListener(v->{
   if(vlcPresent())status.setText("VLC jest zainstalowany. Wybierz kanał, by uruchomić pełny obraz.");
   else openOfficial("https://f-droid.org/packages/org.videolan.vlc/");
  });
  Button test=action("🎞 TEST obrazu — film kontrolny H.264 (to nie kanał TV)");
  options.addView(test);
  test.setOnClickListener(v->{
   boolean before=useVlcWhenInstalled;
   useVlcWhenInstalled=false;
   play("Test obrazu H.264","https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8");
   useVlcWhenInstalled=before;
  });
  options.addView(label("Jeżeli film kontrolny ma obraz, a kanały go nie mają, prawdopodobną przyczyną jest źródło lub kodek transmisji. Gdy test ma sam dźwięk, spróbuj VLC.",13));
  options.addView(label("Transmisje kanałów mogą się zmieniać; dostępność internetowa nie jest gwarantowana. Nie obsługujemy kanałów z DRM ani płatnych usług.",13));
  options.addView(label("Kanały internetowe:",16));
  Button tvp=action("TVP Info • oficjalna transmisja na stronie");options.addView(tvp);
  tvp.setOnClickListener(v->openOfficial("https://www.tvp.info/na-zywo"));
  Button world=action("▶  World Fashion Channel");options.addView(world);
  world.setOnClickListener(v->play("World Fashion","https://cdn.wfc.tv/cdn/stream-rus.m3u8"));
  Button ntv=action("▶  NTV");options.addView(ntv);
  ntv.setOnClickListener(v->play("NTV","https://cdn.ntv.ru/vitrina/index.m3u8"));
  Button r24=action("▶  Rossija 24");options.addView(r24);
  r24.setOnClickListener(v->play("Rossija 24","https://live.smotrim.ru/vgtrk/0/russia24-hd/index.m3u8"));
  options.addView(label("Własny adres HTTPS (HLS .m3u8 lub MP4):",14));
  custom=new EditText(this);custom.setSingleLine(true);
  custom.setHint("https://.../playlist.m3u8");
  options.addView(custom,new LinearLayout.LayoutParams(-1,dp(57)));
  Button go=action("▶  Odtwórz adres");options.addView(go);
  go.setOnClickListener(v->play("Podana transmisja",custom.getText().toString().trim()));
  options.addView(label("Nie każda telewizja internetowa nadaje w kodeku wspieranym przez Acer Android 6. Treści DRM oraz kanały wymagające konta mogą nie działać. Przycisk zewnętrzny otwiera wyłącznie aktualny adres strumienia.",13));
 }
 private boolean valid(String u){return u!=null&&u.length()<2000&&u.startsWith("https://")&&!u.contains(" ")&&!u.contains("\n")&&!u.contains("\r");}
 private void play(String name,String url){
  if(!valid(url)){status.setText("Podaj prawidłowy adres transmisji HTTPS.");return;}
  ++generation;final int seq=generation;activeUrl=url;
  video.stopPlayback();
  if(useVlcWhenInstalled&&vlcPresent()){
   if(openWithVlc(url,name))return;
  }
  status.setText("Łączenie z kanałem "+name+"…");
  new Thread(()->{
   String lower=chooseLowHls(url);
   runOnUiThread(()->{
    if(isFinishing()||seq!=generation)return;
    activeUrl=lower;
    try{
     video.setVideoURI(Uri.parse(lower));
     video.requestFocus();video.start();
     status.setText(lower.equals(url)?"Buforuję transmisję…":"Wybrano lżejszy wariant transmisji. Buforuję obraz…");
    }catch(Exception e){status.setText("Nie mogę otworzyć tego kanału. Wypróbuj inny odtwarzacz.");}
   });
  }).start();
 }
 /**
  * For an HLS master list, choose a low-bandwidth H.264 video rendition.
  * If it's a media list, requires DRM, or network fails, return the original URL.
  * Never download segments into application memory.
  */
 private String chooseLowHls(String address){
  if(!address.toLowerCase(Locale.ROOT).contains(".m3u8"))return address;
  HttpURLConnection c=null;
  try{
   URL base=new URL(address);
   c=(HttpURLConnection)base.openConnection();
   c.setConnectTimeout(6000);c.setReadTimeout(6000);
   c.setRequestProperty("User-Agent","PomocnikAlfa/6.0");
   c.setRequestProperty("Accept","application/vnd.apple.mpegurl, application/x-mpegURL, */*");
   if(c.getResponseCode()!=200)return address;
   BufferedReader rd=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));
   ArrayList<String> lines=new ArrayList<>();
   String line;int sum=0;
   while(lines.size()<500&&(line=rd.readLine())!=null){
    sum+=line.length();if(sum>110000)break;
    lines.add(line.trim());
   }
   String best=null;long bestScore=Long.MAX_VALUE;
   for(int i=0;i<lines.size();i++){
    String info=lines.get(i);
    if(!info.startsWith("#EXT-X-STREAM-INF:"))continue;
    String lc=info.toLowerCase(Locale.ROOT);
    if(lc.contains("hev1")||lc.contains("hvc1")||lc.contains("av01")||lc.contains("vp09"))continue;
    if(lc.contains("codecs=")&&!lc.contains("avc1"))continue; // skip audio-only variants
    Matcher m=Pattern.compile("BANDWIDTH=([0-9]+)").matcher(info);
    long bw=m.find()?Long.parseLong(m.group(1)):2000000L;
    Matcher res=Pattern.compile("RESOLUTION=([0-9]+)x([0-9]+)").matcher(info);
    if(res.find()&&(Integer.parseInt(res.group(1))>1280||Integer.parseInt(res.group(2))>720))continue;
    String target=null;
    for(int j=i+1;j<lines.size();j++){
     if(lines.get(j).isEmpty())continue;
     if(lines.get(j).startsWith("#EXT-X-STREAM-INF:"))break;
     if(!lines.get(j).startsWith("#"))target=lines.get(j);
     break;
    }
    if(target==null)continue;
    URL rendition=new URL(base,target);
    if(!valid(rendition.toString()))continue;
    long score=bw>1400000L?bw+10000000L:bw;
    if(score<bestScore){best=rendition.toString();bestScore=score;}
   }
   return best==null?address:best;
  }catch(Exception ignored){return address;}
  finally{if(c!=null)c.disconnect();}
 }
 private boolean openWithVlc(String url,String name){
  if(!valid(url))return false;
  try{
   Intent i=new Intent(Intent.ACTION_VIEW);
   i.setDataAndType(Uri.parse(url),"video/*");
   i.setPackage("org.videolan.vlc");
   i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
   startActivity(i);
   status.setText("Kanał "+name+" przekazany do VLC. Odtwarzanie odbywa się w VLC.");
   return true;
  }catch(Exception e){
   status.setText("Nie udało się uruchomić VLC, spróbuję odtwarzacza systemowego.");
   return false;
  }
 }
 private void external(){
  if(!valid(activeUrl)){status.setText("Najpierw wybierz kanał.");return;}
  if(vlcPresent()&&openWithVlc(activeUrl,"wybrany"))return;
  try{
   Intent i=new Intent(Intent.ACTION_VIEW);
   i.setDataAndType(Uri.parse(activeUrl),activeUrl.toLowerCase(Locale.ROOT).contains(".m3u8")?"application/vnd.apple.mpegurl":"video/*");
   i.addCategory(Intent.CATEGORY_DEFAULT);
   startActivity(Intent.createChooser(i,"Wybierz odtwarzacz wideo"));
  }catch(Exception e){status.setText("Brak odtwarzacza strumieni. Zainstaluj wersję VLC zgodną z Androidem 6.");}
 }
 private void openOfficial(String address){
  try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(address)));}
  catch(Exception e){status.setText("Nie znaleziono przeglądarki do otwarcia strony telewizji.");}
 }
 @Override protected void onPause(){if(video!=null)video.pause();super.onPause();}
 @Override protected void onDestroy(){++generation;if(video!=null)video.stopPlayback();super.onDestroy();}
}
