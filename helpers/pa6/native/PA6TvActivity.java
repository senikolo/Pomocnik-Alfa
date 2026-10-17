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
 private final ArrayList<String[]> channelData=new ArrayList<>();
 private LinearLayout catalog;
 private EditText channelFilter;
 private int catalogLoad=0;
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
  if(cinemaButton!=null)cinemaButton.setText(cinema?"▣  Wróć do listy kanałów":"⛶  Powiększ odtwarzacz");
  if(channelList!=null)channelList.setVisibility(cinema?View.GONE:View.VISIBLE);
  if(video!=null){
   android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();
   int height=Math.max(230,(int)(metrics.heightPixels/metrics.density)-150);
   video.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(cinema?height:245)));
   video.setVisibility(View.VISIBLE);
  }
 }
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  getWindow().setStatusBarColor(NIGHT);getWindow().setNavigationBarColor(NIGHT);
  LinearLayout page=column();page.setPadding(dp(14),dp(12),dp(14),dp(8));
  page.setBackgroundColor(NIGHT);page.setFocusableInTouchMode(true);page.requestFocus();setContentView(page);
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
  video.setVisibility(View.VISIBLE);
  MediaController controller=new MediaController(this);controller.setAnchorView(video);video.setMediaController(controller);
  status=txt("Wybierz kanał. Obraz i dźwięk odtwarzane są w oknie PA.",14,false,0xffc9d6f2);
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
     status.setText("Słychać dźwięk bez obrazu. Ten kodek nie działa na Androidzie 6; wybierz inny kanał.");
    }
   },12000);
  });
  video.setOnErrorListener((p,what,extra)->{
   status.setText("Ten kanał nie odtwarza się na Androidzie 6 (błąd "+what+"/"+extra+"). Wybierz inny.");
   return true;
  });
  cinemaButton=control("⛶  Powiększ odtwarzacz",0xff213252,TEXT);
  LinearLayout.LayoutParams cine=new LinearLayout.LayoutParams(-1,dp(47));cine.setMargins(0,dp(7),0,dp(7));
  page.addView(cinemaButton,cine);cinemaButton.setOnClickListener(v->cinemaMode());
  channelList=new ScrollView(this);channelList.setFillViewport(false);
  page.addView(channelList,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout options=column();options.setPadding(0,dp(7),0,dp(24));
  channelList.addView(options);
  section(options,"Kanały na żywo","Lista polskich stacji pobierana ze społecznościowego katalogu IPTV-org");
  channelFilter=new EditText(this);channelFilter.setTextColor(TEXT);channelFilter.setHintTextColor(MUTED);
  channelFilter.setSingleLine(true);channelFilter.setHint("Znajdź kanał, np. TV, muzyka, info");
  channelFilter.setBackground(shape(PANEL,0xff354962,13));
  channelFilter.setPadding(dp(12),0,dp(12),0);
  options.addView(channelFilter,new LinearLayout.LayoutParams(-1,dp(54)));
  space(options,8);
  Button refresh=control("↻  Odśwież kanały",0xff193d47,TEAL);
  options.addView(refresh,new LinearLayout.LayoutParams(-1,dp(48)));
  refresh.setOnClickListener(v->loadCatalog());
  catalog=column();options.addView(catalog);
  channelFilter.addTextChangedListener(new android.text.TextWatcher(){
   @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   @Override public void onTextChanged(CharSequence s,int start,int before,int count){renderCatalog();}
   @Override public void afterTextChanged(android.text.Editable e){}
  });
  section(options,"Kanał demonstracyjny","Wybierz, aby sprawdzić odbiór transmisji HLS");
  channel(options,"WF","World Fashion","Ogólnodostępny strumień; dostępność zależy od nadawcy",0xfffda5ce,
   ()->play("World Fashion","https://cdn.wfc.tv/cdn/stream-rus.m3u8"));
  loadCatalog();
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
  section(options,"Odtwarzacz wewnętrzny","Obraz zawsze pozostaje w Pomocniku Alfa");
  TextView internal=txt("Wbudowany odtwarzacz wideo • brak uruchamiania VLC, przeglądarki i innych aplikacji",13,false,TEAL);
  options.addView(internal);
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
   video.setVisibility(View.VISIBLE);
   play("Test obrazu H.264","https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8");
  });
  space(options,10);
  TextView caveat=txt("Dostępność i format transmisji zależą od nadawców. Kanały z DRM lub ograniczeniami regionalnymi mogą się nie uruchomić.",12,false,MUTED);
  options.addView(caveat);
  recentButtons();
 }
 private boolean valid(String u){return u!=null&&u.length()<2000&&(u.startsWith("https://")||u.startsWith("http://"))&&!u.contains(" ")&&!u.contains("\n")&&!u.contains("\r");}
 private void play(String name,String url){
  if(!valid(url)){status.setText("Podaj prawidłowy adres transmisji HTTP lub HTTPS.");return;}
  ++generation;final int seq=generation;activeUrl=url;activeName=name;
  if(!"Test obrazu H.264".equals(name)){
   prefs().edit().putString("recent_name",name).putString("recent_url",url).apply();
   recentButtons();
  }
  video.stopPlayback();
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
    }catch(Exception e){status.setText("Nie mogę otworzyć tego kanału. Spróbuj inny z listy.");}
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
 /** Load free-to-air playlist. No hardcoded promises about working channels. */
 private void loadCatalog(){
  final int token=++catalogLoad;
  catalog.removeAllViews();
  catalog.addView(txt("Ładuję polskie kanały internetowe…",14,false,MUTED));
  new Thread(()->{
   ArrayList<String[]> fresh=new ArrayList<>();String error="";
   HttpURLConnection c=null;
   try{
    c=(HttpURLConnection)new URL("https://iptv-org.github.io/iptv/countries/pl.m3u").openConnection();
    c.setConnectTimeout(9000);c.setReadTimeout(12000);
    c.setRequestProperty("User-Agent","PomocnikAlfaAndroid6/2.3");
    if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());
    BufferedReader rd=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));
    String line,name="";int bytes=0;HashSet<String> seen=new HashSet<>();
    while((line=rd.readLine())!=null&&fresh.size()<90){
     bytes+=line.length();if(bytes>400000)throw new IOException("Lista kanałów jest za duża");
     line=line.trim();
     if(line.startsWith("#EXTINF:")){
      int comma=line.lastIndexOf(',');name=comma>=0?line.substring(comma+1).trim():"";
      if(name.length()>75)name=name.substring(0,75);
     }else if(!line.startsWith("#")&&!line.isEmpty()&&!name.isEmpty()){
      if(valid(line)&&!seen.contains(name+"|"+line)&&!line.toLowerCase(Locale.ROOT).contains(".mpd")){
       seen.add(name+"|"+line);fresh.add(new String[]{name,line});
      }
      name="";
     }
    }
   }catch(Exception e){error=e.getMessage();}
   finally{if(c!=null)c.disconnect();}
   final String failure=error;
   runOnUiThread(()->{
    if(isFinishing()||token!=catalogLoad)return;
    channelData.clear();channelData.addAll(fresh);
    renderCatalog();
    if(fresh.isEmpty())status.setText("Katalog stacji chwilowo niedostępny: "+failure+". Nadal możesz podać własny adres HLS.");
   });
  }).start();
 }
 private void renderCatalog(){
  if(catalog==null)return;
  catalog.removeAllViews();String filter=channelFilter==null?"":channelFilter.getText().toString().toLowerCase(Locale.ROOT).trim();
  int shown=0;
  for(String[] channel:channelData){
   if(!channel[0].toLowerCase(Locale.ROOT).contains(filter))continue;
   final String name=channel[0],url=channel[1];
   channel(catalog,"TV",name,"Oglądaj w okienku • transmisja internetowa",TEAL,()->play(name,url));
   if(++shown>=48)break;
  }
  if(shown==0)catalog.addView(txt(channelData.isEmpty()?"Ładuję kanały lub serwis jest niedostępny.":"Brak kanałów dla wpisanej nazwy.",14,false,MUTED));
  else catalog.addView(txt("Pokazuję "+shown+" z "+channelData.size()+" stacji. Niektóre mogą wymagać kodeków lub uprawnień nadawcy.",12,false,MUTED));
 }
 @Override protected void onPause(){if(video!=null)video.pause();super.onPause();}
 @Override protected void onDestroy(){++generation;if(video!=null)video.stopPlayback();super.onDestroy();}
}
