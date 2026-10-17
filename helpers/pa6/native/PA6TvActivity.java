package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
 private String activeName="";
 private ScrollView channelList;
 private Button favoriteButton,historyButton,cinemaButton;
 private boolean cinema=false;
 private final int NIGHT=0xff0b1326, PANEL=0xff182742, TEXT=0xfff0f4ff, MUTED=0xffb3c2de, TEAL=0xff63ead7;
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
 private GradientDrawable shape(int bg,int stroke,int radius){
  GradientDrawable d=new GradientDrawable();d.setColor(bg);d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d;
 }
 private TextView txt(String text,int size,boolean bold,int color){
  TextView t=new TextView(this);t.setText(text);t.setTextColor(color);t.setTextSize(size);
  if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
  t.setGravity(Gravity.CENTER_VERTICAL);return t;
 }
 private Button control(String name,int back,int color){
  Button b=new Button(this);b.setText(name);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(color);
  b.setMinHeight(dp(54));b.setBackground(shape(back,0xff304563,14));return b;
 }
 private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
 private void space(LinearLayout p,int size){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(size)));}
 private void section(LinearLayout parent,String title,String caption){
  TextView t=txt(title,18,true,TEXT);parent.addView(t);
  TextView sub=txt(caption,12,false,MUTED);sub.setPadding(0,dp(3),0,dp(8));parent.addView(sub);
 }
 private void channel(LinearLayout parent,String logo,String name,String caption,int tint,Runnable callback){
  LinearLayout item=new LinearLayout(this);item.setGravity(Gravity.CENTER_VERTICAL);
  item.setPadding(dp(12),dp(10),dp(10),dp(10));item.setBackground(shape(PANEL,0xff2a3b59,16));
  TextView mark=txt(logo,20,true,tint);mark.setGravity(Gravity.CENTER);
  mark.setBackground(shape(0xff243451,0xff3f5471,12));
  item.addView(mark,new LinearLayout.LayoutParams(dp(66),dp(58)));
  LinearLayout copy=column();copy.setPadding(dp(12),0,0,0);
  TextView top=txt(name,16,true,TEXT);copy.addView(top);
  TextView bottom=txt(caption,12,false,MUTED);bottom.setMaxLines(2);copy.addView(bottom);
  item.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
  TextView chevron=txt("›",30,false,TEAL);item.addView(chevron);
  item.setContentDescription(name+", "+caption);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(79));lp.setMargins(0,0,0,dp(9));
  parent.addView(item,lp);
  item.setOnClickListener(v->callback.run());
 }
 private android.content.SharedPreferences prefs(){return getPreferences(0);}
 private void recentButtons(){
  if(historyButton==null||favoriteButton==null)return;
  String recently=prefs().getString("recent_name","");
  String favorite=prefs().getString("fav_name","");
  historyButton.setText(recently.isEmpty()?"↻  Ostatni kanał: brak":"↻  Ostatnio: "+recently);
  historyButton.setEnabled(!recently.isEmpty());
  favoriteButton.setText(favorite.isEmpty()?"☆  Zapisz bieżący kanał":"★  Ulubiony: "+favorite);
 }
 private void favorite(){
  String fav=prefs().getString("fav_url","");
  if(!fav.isEmpty()){
   play(prefs().getString("fav_name","Ulubiony"),fav);return;
  }
  if(!valid(activeUrl)){status.setText("Najpierw wybierz kanał do zapisania.");return;}
  prefs().edit().putString("fav_name",activeName).putString("fav_url",activeUrl).apply();
  status.setText("Zapisano ulubiony kanał: "+activeName);
  recentButtons();
 }
 private void cinemaMode(){
  cinema=!cinema;
  if(cinemaButton!=null)cinemaButton.setText(cinema?"▣  Wróć do listy kanałów":"⛶  Tryb kinowy (odtwarzacz systemowy)");
  if(channelList!=null)channelList.setVisibility(cinema?View.GONE:View.VISIBLE);
  if(video!=null){
   android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();
   int height=Math.max(230,(int)(metrics.heightPixels/metrics.density)-150);
   video.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(cinema?height:245)));
   if(cinema)video.setVisibility(View.VISIBLE);
   else if(useVlcWhenInstalled&&vlcPresent())video.setVisibility(View.GONE);
  }
 }
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  getWindow().setStatusBarColor(NIGHT);getWindow().setNavigationBarColor(NIGHT);
  LinearLayout page=column();page.setPadding(dp(14),dp(12),dp(14),dp(8));
  page.setBackgroundColor(NIGHT);setContentView(page);
  LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
  PA6GlyphView glyph=new PA6GlyphView(this,"tv",TEAL);
  heading.addView(glyph,new LinearLayout.LayoutParams(dp(48),dp(48)));
  LinearLayout intro=column();intro.setPadding(dp(9),0,0,0);
  intro.addView(txt("ALFA TV",25,true,TEXT));
  intro.addView(txt("Twoje kino na tablecie • Android 6",13,false,MUTED));
  heading.addView(intro,new LinearLayout.LayoutParams(0,-2,1));
  page.addView(heading);space(page,13);
  TextView chip=txt("●  KANAŁY INTERNETOWE   •   WYBIERZ I OGLĄDAJ",12,true,TEAL);
  chip.setPadding(dp(11),dp(9),dp(11),dp(9));chip.setBackground(shape(0xff193b48,0xff315e6c,12));
  page.addView(chip);
  video=new VideoView(this);
  video.setBackgroundColor(Color.BLACK);
  page.addView(video,new LinearLayout.LayoutParams(-1,dp(245)));
  video.setVisibility(vlcPresent()?View.GONE:View.VISIBLE);
  MediaController controller=new MediaController(this);controller.setAnchorView(video);video.setMediaController(controller);
  status=txt("Wybierz stację. VLC pozwala oglądać więcej formatów wideo.",14,false,0xffc9d6f2);
  status.setPadding(dp(13),dp(10),dp(13),dp(10));
  status.setBackground(shape(0xff1c304a,0xff304964,13));
  page.addView(status,new LinearLayout.LayoutParams(-1,-2));
  video.setOnPreparedListener(p->{
   p.setScreenOnWhilePlaying(true);
   p.setOnVideoSizeChangedListener((mp,w,h)->{
    if(w>0&&h>0)status.setText("Odtwarzam obraz "+w+" × "+h+" • "+activeName);
   });
   video.start();
   final int seq=generation;
   handler.postDelayed(()->{
    if(seq!=generation||video==null||isFinishing())return;
    if(p.getVideoWidth()==0||p.getVideoHeight()==0){
     status.setText("Słychać dźwięk bez obrazu. Spróbuj otworzyć kanał w VLC.");
    }
   },12000);
  });
  video.setOnErrorListener((p,what,extra)->{
   status.setText("Nie można odtworzyć obrazu (błąd "+what+"/"+extra+"). Otwórz w VLC lub sprawdź inny kanał.");
   return true;
  });
  cinemaButton=control("⛶  Tryb kinowy (odtwarzacz systemowy)",0xff213252,TEXT);
  LinearLayout.LayoutParams cine=new LinearLayout.LayoutParams(-1,dp(47));cine.setMargins(0,dp(7),0,dp(7));
  page.addView(cinemaButton,cine);cinemaButton.setOnClickListener(v->cinemaMode());
  channelList=new ScrollView(this);channelList.setFillViewport(false);
  page.addView(channelList,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout options=column();options.setPadding(0,dp(7),0,dp(24));
  channelList.addView(options);
  section(options,"Wybierz kanał","Szybki dostęp — dotknij, by uruchomić odtwarzanie");
  channel(options,"W","World Fashion","Moda, pokazy i styl",0xfffda5ce,
   ()->play("World Fashion","https://cdn.wfc.tv/cdn/stream-rus.m3u8"));
  channel(options,"N","NTV","Kanał internetowy",0xffa5cbff,
   ()->play("NTV","https://cdn.ntv.ru/vitrina/index.m3u8"));
  channel(options,"24","Rossija 24","Serwis informacyjny",0xffffca81,
   ()->play("Rossija 24","https://live.smotrim.ru/vgtrk/0/russia24-hd/index.m3u8"));
  channel(options,"TVP","TVP Info","Oficjalna strona nadawcy",TEAL,
   ()->openOfficial("https://www.tvp.info/na-zywo"));
  space(options,8);
  section(options,"Twoja telewizja","Wróć do ostatniego kanału lub zapisz ulubiony");
  historyButton=control("↻  Ostatnio oglądane",0xff1e344c,TEXT);
  options.addView(historyButton,new LinearLayout.LayoutParams(-1,dp(51)));
  historyButton.setOnClickListener(v->play(prefs().getString("recent_name","Kanał"),
    prefs().getString("recent_url","")));
  space(options,7);
  favoriteButton=control("☆  Zapisz bieżący kanał",0xff1e344c,TEAL);
  options.addView(favoriteButton,new LinearLayout.LayoutParams(-1,dp(51)));
  favoriteButton.setOnClickListener(v->favorite());
  Button replace=control("↺  Zmień ulubiony na aktualny kanał",0xff1a2840,MUTED);
  options.addView(replace,new LinearLayout.LayoutParams(-1,dp(49)));
  replace.setOnClickListener(v->{
   if(valid(activeUrl)){
    prefs().edit().putString("fav_url",activeUrl).putString("fav_name",activeName).apply();
    status.setText("Zmieniono ulubiony kanał.");recentButtons();
   }else status.setText("Najpierw uruchom kanał.");
  });
  space(options,14);
  section(options,"Odtwarzacz","Dopasuj sposób oglądania do możliwości Acera");
  CheckBox useVlc=new CheckBox(this);
  useVlc.setText("Używaj VLC, gdy jest zainstalowany");
  useVlc.setTextColor(TEXT);useVlc.setTextSize(15);
  useVlc.setChecked(prefs().getBoolean("prefer_vlc",true));
  useVlcWhenInstalled=useVlc.isChecked();
  useVlc.setOnCheckedChangeListener((button,checked)->{
   useVlcWhenInstalled=checked;prefs().edit().putBoolean("prefer_vlc",checked).apply();
   if(!cinema)video.setVisibility(checked&&vlcPresent()?View.GONE:View.VISIBLE);
  });
  options.addView(useVlc);
  Button external=control("▶  Otwórz bieżący kanał w VLC",0xff244b52,TEAL);
  options.addView(external,new LinearLayout.LayoutParams(-1,dp(55)));
  external.setOnClickListener(v->external());
  space(options,8);
  Button help=control(vlcPresent()?"✓ VLC jest dostępny":"Pobierz VLC dla Androida 6",0xff182943,TEXT);
  options.addView(help,new LinearLayout.LayoutParams(-1,dp(50)));
  help.setOnClickListener(v->{
   if(vlcPresent())status.setText("VLC jest zainstalowany. Wybierz kanał.");
   else openOfficial("https://f-droid.org/packages/org.videolan.vlc/");
  });
  space(options,14);
  section(options,"Własny kanał","Wklej adres strumienia HLS / MP4 (https://)");
  custom=new EditText(this);custom.setTextColor(TEXT);custom.setHintTextColor(MUTED);
  custom.setSingleLine(true);custom.setHint("https://.../playlist.m3u8");
  custom.setPadding(dp(14),dp(10),dp(14),dp(10));
  custom.setBackground(shape(0xff182942,0xff2c4361,14));
  options.addView(custom,new LinearLayout.LayoutParams(-1,dp(57)));
  space(options,8);
  Button go=control("▶  Uruchom własny kanał",0xff1d4b51,TEAL);
  options.addView(go,new LinearLayout.LayoutParams(-1,dp(56)));
  go.setOnClickListener(v->play("Własny kanał",custom.getText().toString().trim()));
  space(options,13);
  Button test=control("▶  Test obrazu H.264",0xff182943,MUTED);
  options.addView(test,new LinearLayout.LayoutParams(-1,dp(53)));
  test.setOnClickListener(v->{
   boolean prev=useVlcWhenInstalled;useVlcWhenInstalled=false;
   video.setVisibility(View.VISIBLE);
   play("Test obrazu H.264","https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8");
   useVlcWhenInstalled=prev;
  });
  space(options,10);
  TextView caveat=txt("Dostępność i format transmisji zależą od nadawców. Kanały z DRM lub ograniczeniami regionalnymi mogą się nie uruchomić.",12,false,MUTED);
  options.addView(caveat);
  recentButtons();
 }
 private boolean valid(String u){return u!=null&&u.length()<2000&&u.startsWith("https://")&&!u.contains(" ")&&!u.contains("\n")&&!u.contains("\r");}
 private void play(String name,String url){
  if(!valid(url)){status.setText("Podaj prawidłowy adres transmisji HTTPS.");return;}
  ++generation;final int seq=generation;activeUrl=url;activeName=name;
  if(!"Test obrazu H.264".equals(name)){
   prefs().edit().putString("recent_name",name).putString("recent_url",url).apply();
   recentButtons();
  }
  video.stopPlayback();
  if(useVlcWhenInstalled&&vlcPresent()){
   if(openWithVlc(url,name))return;
  }
  video.setVisibility(View.VISIBLE);
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
   status.setText("▶  "+name+" • otwarto w VLC. Miłego oglądania!");
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
