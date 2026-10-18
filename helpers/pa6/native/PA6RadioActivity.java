package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Studio-quality radio directory with in-app station selection. Playback service is unchanged. */
public final class PA6RadioActivity extends Activity {
 private static final int WHITE=0xfff8fafc,INK=0xff1a2336,MUTED=0xff606b7b,RED=0xffe9263e;
 private LinearLayout list;
 private TextView state,playback;
 private EditText search,custom;
 private int serial=0;private boolean listening;
 private String category="Wszystkie",activeName="";
 private final BroadcastReceiver updates=new BroadcastReceiver(){
  @Override public void onReceive(Context c,Intent i){
   if(PA6RadioService.ACTION_STATE.equals(i.getAction())){
    String message=i.getStringExtra("message");
    if(message!=null&&playback!=null)playback.setText(message);
   }
  }
 };
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
 private GradientDrawable bg(int color,int stroke,int radius){
  GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d;
 }
 private TextView label(String value,int size,int color,boolean bold){
  TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
  if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
 }
 private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 private void gap(LinearLayout l,int h){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
 private Button button(String txt,boolean solid){
  Button b=new Button(this);b.setAllCaps(false);b.setText(txt);b.setTextSize(14);
  b.setTextColor(solid?Color.WHITE:INK);
  b.setBackground(bg(solid?RED:WHITE,solid?RED:0xffdce2eb,11));return b;
 }
 private boolean valid(String url){
  return url!=null&&(url.startsWith("https://")||url.startsWith("http://"))&&
   url.length()<2200&&!url.contains(" ")&&!url.contains("\n");
 }
 private String favorites(){return getPreferences(0).getString("radio_favorites","[]");}
 private JSONArray favoriteList(){
  try{return new JSONArray(favorites());}catch(Exception e){return new JSONArray();}
 }
 private boolean favorite(String url){
  JSONArray arr=favoriteList();
  for(int i=0;i<arr.length();i++)if(url.equals(arr.optJSONObject(i).optString("url")))return true;
  return false;
 }
 private void toggle(String name,String url){
  JSONArray previous=favoriteList(),next=new JSONArray();boolean found=false;
  for(int i=0;i<previous.length();i++){
   JSONObject o=previous.optJSONObject(i);
   if(o==null)continue;
   if(url.equals(o.optString("url"))){found=true;continue;}
   next.put(o);
  }
  if(!found&&next.length()<25){
   JSONObject o=new JSONObject();
   try{o.put("name",name);o.put("url",url);next.put(o);}catch(Exception ignored){}
  }
  getPreferences(0).edit().putString("radio_favorites",next.toString()).apply();
  if(category.equals("★ Ulubione"))loadFavorites();
  else { // Keep catalog without a network refresh: update visible star buttons.
   for(int j=0;j<list.getChildCount();j++){
    View item=list.getChildAt(j);
    if(item instanceof LinearLayout){
     LinearLayout row=(LinearLayout)item;
     if(url.equals(row.getTag())){
      View v=row.getChildAt(row.getChildCount()-1);
      if(v instanceof Button)((Button)v).setText(favorite(url)?"★":"☆");
     }
    }
   }
  }
 }
 private void play(String name,String url){
  if(!valid(url)){playback.setText("Nieprawidłowy adres stacji.");return;}
  activeName=name;playback.setText("Łączenie ze stacją: "+name+"…");
  getPreferences(0).edit().putString("radio_last_name",name).putString("radio_last_url",url).apply();
  try{
   Intent i=new Intent(this,PA6RadioService.class).setAction(PA6RadioService.ACTION_PLAY);
   i.putExtra("url",url);i.putExtra("name",name);startService(i);
  }catch(Exception e){playback.setText("Nie można uruchomić radia: "+e.getClass().getSimpleName());}
 }
 private void stop(){
  try{startService(new Intent(this,PA6RadioService.class).setAction(PA6RadioService.ACTION_STOP));}
  catch(Exception ignored){}playback.setText("Radio zatrzymane.");
 }
 private void station(LinearLayout dest,String name,String url){
  LinearLayout card=row();card.setPadding(dp(11),dp(10),dp(10),dp(10));
  card.setBackground(bg(WHITE,0xffe0e5ee,14));card.setTag(url);
  TextView logo=label(name.substring(0,Math.min(2,name.length())).toUpperCase(Locale.ROOT),17,RED,true);
  logo.setGravity(Gravity.CENTER);logo.setBackground(bg(0xffffeef1,0xffffe0e4,12));
  card.addView(logo,new LinearLayout.LayoutParams(dp(57),dp(57)));
  LinearLayout labels=col();labels.setPadding(dp(12),0,0,0);
  TextView headline=label(name,16,INK,true);headline.setMaxLines(2);labels.addView(headline);
  labels.addView(label("● RADIO ONLINE • DOTKNIJ, BY SŁUCHAĆ",11,MUTED,false));
  card.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
  Button star=button(favorite(url)?"★":"☆",false);star.setTextSize(19);star.setTextColor(RED);
  card.addView(star,new LinearLayout.LayoutParams(dp(50),dp(47)));
  star.setOnClickListener(v->toggle(name,url));
  card.setOnClickListener(v->play(name,url));
  card.setContentDescription("Słuchaj "+name);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(78));lp.setMargins(0,dp(3),0,dp(5));dest.addView(card,lp);
 }
 private void loadFavorites(){
  serial++;list.removeAllViews();
  JSONArray arr=favoriteList();state.setText("Ulubione stacje: "+arr.length());
  for(int i=0;i<arr.length();i++){JSONObject r=arr.optJSONObject(i);if(r!=null&&valid(r.optString("url")))station(list,r.optString("name","Radio"),r.optString("url"));}
  if(arr.length()==0)state.setText("Nie zapisano jeszcze ulubionych stacji. Użyj gwiazdki obok nazwy.");
 }
 private String encode(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){return "";}}
 private void load(String query){
  if(category.equals("★ Ulubione")){loadFavorites();return;}
  final int seq=++serial;
  list.removeAllViews();state.setText("Pobieram aktualną listę stacji…");
  final String group=category;
  new Thread(()->{
   ArrayList<String[]> stations=new ArrayList<>();String failure=null;
   String params="countrycode=PL&hidebroken=true&order=clickcount&reverse=true&limit=160";
   if(!query.isEmpty())params+="&name="+encode(query);
   if(group.equals("Rock"))params+="&tag=rock";
   if(group.equals("Pop"))params+="&tag=pop";
   if(group.equals("Jazz"))params+="&tag=jazz";
   if(group.equals("Relaks"))params+="&tag=relax";
   if(group.equals("Wiadomości"))params+="&tag=news";
   String req="/json/stations/search?"+params;
   for(String host:new String[]{"https://de1.api.radio-browser.info","https://all.api.radio-browser.info"}){
    HttpURLConnection c=null;
    try{
     // Android 6 may lack current CA roots. Use the bundled verified trust store.
     c=PA6ModernTls.open(host+req);
     c.setConnectTimeout(7500);c.setReadTimeout(12000);
     c.setRequestProperty("User-Agent","PomocnikAlfa/3.0 (Android 6 radio browser client)");
     c.setRequestProperty("Accept","application/json");
     int code=c.getResponseCode();if(code!=200)throw new IOException("HTTP "+code);
     ByteArrayOutputStream bytes=new ByteArrayOutputStream();
     try(InputStream in=c.getInputStream()){
      byte[] buffer=new byte[8192];int size;
      while((size=in.read(buffer))>0){if(bytes.size()+size>1900000)throw new IOException("Za duży katalog");bytes.write(buffer,0,size);}
     }
     JSONArray arr=new JSONArray(bytes.toString("UTF-8"));
     HashSet<String> urls=new HashSet<>();
     for(int n=0;n<arr.length()&&stations.size()<95;n++){
      JSONObject o=arr.optJSONObject(n);if(o==null)continue;
      String name=o.optString("name","").trim(),url=o.optString("url_resolved",o.optString("url","")).trim();
      String codec=o.optString("codec","").toUpperCase(Locale.ROOT);
      if(name.length()<2||name.length()>100||!valid(url)||(!codec.contains("MP3")&&!codec.contains("AAC")))continue;
      if(urls.add(url))stations.add(new String[]{name,url});
     }
     failure=null;break;
    }catch(Exception e){failure=e.getClass().getSimpleName()+": "+e.getMessage();}
    finally{if(c!=null)c.disconnect();}
   }
   final String error=failure;
   runOnUiThread(()->{
    if(isFinishing()||seq!=serial)return;
    state.setText(error!=null?"Katalog chwilowo niedostępny. Działają zapisane stacje i stacje szybkie.":
     stations.isEmpty()?"Nie znaleziono stacji. Zmień hasło lub kategorię.":"Stacje znalezione: "+stations.size()+" • wybierz do słuchania");
    for(String[] r:stations)station(list,r[0],r[1]);
   });
  }).start();
 }
 @Override public void onCreate(Bundle b){
  super.onCreate(b);PA6ModernTls.install(this);
  getWindow().setStatusBarColor(WHITE);getWindow().setNavigationBarColor(INK);
  getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(WHITE);
  LinearLayout page=col();page.setPadding(dp(13),dp(11),dp(13),dp(25));scroll.addView(page);setContentView(scroll);
  LinearLayout header=row();
  TextView dot=label("◉",31,RED,true);header.addView(dot);
  TextView heading=label("  ALFA RADIO",24,INK,true);
  header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
  header.addView(label("NA ŻYWO",12,RED,true));
  page.addView(header);gap(page,11);
  LinearLayout display=col();display.setBackground(bg(0xff18263e,0xff263953,19));display.setPadding(dp(17),dp(17),dp(17),dp(17));
  TextView cap=label("TERAZ SŁUCHASZ",12,0xffa8bddd,true);display.addView(cap);
  playback=label("Wybierz swoją stację radiową",18,Color.WHITE,true);
  playback.setPadding(0,dp(7),0,dp(14));display.addView(playback);
  LinearLayout controls=row();
  Button recent=button("▶ Ostatnio",true);controls.addView(recent,new LinearLayout.LayoutParams(0,dp(52),1));
  recent.setOnClickListener(v->{
   String url=getPreferences(0).getString("radio_last_url","");
   if(url.isEmpty())playback.setText("Brak ostatnio słuchanej stacji.");
   else play(getPreferences(0).getString("radio_last_name","Radio"),url);
  });
  Button stop=button("■ Zatrzymaj",false);
  LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(52),1);sp.setMargins(dp(8),0,0,0);
  controls.addView(stop,sp);stop.setOnClickListener(v->stop());
  display.addView(controls);page.addView(display);gap(page,17);
  page.addView(label("Szybkie stacje",19,INK,true));gap(page,5);
  LinearLayout presets=col();page.addView(presets);
  station(presets,"Radio ZET 2000","https://zt03.cdn.eurozet.pl/ZET200.mp3");
  station(presets,"Radio ZET Love","https://zt03.cdn.eurozet.pl/ZETLOV.mp3");
  station(presets,"RMF FM","https://rs103-krk.rmfstream.pl/RMFFM48");
  station(presets,"Radio ZET 2000 • HTTP","http://zet.cdn.eurozet.pl/ZET200.mp3");
  gap(page,10);
  TextView catalog=label("Wszystkie stacje",20,INK,true);page.addView(catalog);gap(page,8);
  search=new EditText(this);search.setSingleLine(true);search.setHint("Szukaj: Trójka, TOK FM, jazz…");
  search.setBackground(bg(0xffedf1f7,0xffdfe5ee,12));search.setPadding(dp(13),0,dp(13),0);
  page.addView(search,new LinearLayout.LayoutParams(-1,dp(50)));
  Button searchButton=button("Szukaj stacji",true);
  page.addView(searchButton,new LinearLayout.LayoutParams(-1,dp(49)));
  searchButton.setOnClickListener(v->load(search.getText().toString().trim()));gap(page,8);
  LinearLayout tabs=row();
  for(String label:new String[]{"Wszystkie","Pop","Rock","Jazz","Wiadomości","Relaks","★ Ulubione"}){
   // Horizontal scroll preserves readable button sizes on 10-inch tablets.
  }
  HorizontalScrollView tabsScroll=new HorizontalScrollView(this);tabsScroll.setHorizontalScrollBarEnabled(false);
  LinearLayout chips=row();
  for(String label:new String[]{"Wszystkie","Pop","Rock","Jazz","Wiadomości","Relaks","★ Ulubione"}){
   Button tab=button(label,false);tab.setTextSize(12);
   LinearLayout.LayoutParams t=new LinearLayout.LayoutParams(dp(label.length()>9?121:88),dp(47));
   t.setMargins(dp(2),0,dp(3),0);chips.addView(tab,t);
   tab.setOnClickListener(v->{category=label;load(search.getText().toString().trim());});
  }
  tabsScroll.addView(chips);page.addView(tabsScroll);
  state=label("Ładuję polskie stacje online…",13,MUTED,false);
  state.setPadding(dp(7),dp(13),dp(7),dp(8));page.addView(state);
  list=col();page.addView(list);gap(page,14);
  page.addView(label("Własny strumień radiowy",17,INK,true));gap(page,6);
  custom=new EditText(this);custom.setSingleLine(true);custom.setHint("https://…/stream.mp3");
  custom.setBackground(bg(0xffedf1f7,0xffdfe5ee,12));page.addView(custom,new LinearLayout.LayoutParams(-1,dp(51)));
  Button add=button("▶ Odtwórz własną stację",true);
  page.addView(add,new LinearLayout.LayoutParams(-1,dp(52)));
  add.setOnClickListener(v->play("Własna stacja",custom.getText().toString().trim()));
  gap(page,14);
  TextView note=label("Katalog Radio Browser jest aktualizowany przez społeczność. Stacje mogą zmieniać strumienie; radio działa w tle, a ulubione są zapisane na tablecie.",12,MUTED,false);
  note.setPadding(dp(5),dp(6),dp(5),dp(6));page.addView(note);
  page.setFocusableInTouchMode(true);page.requestFocus();
  load("");
 }
 @Override protected void onStart(){super.onStart();String url=getIntent().getStringExtra("station_url");if(url!=null){getIntent().removeExtra("station_url");play(getIntent().getStringExtra("station_name"),url);}if(!listening){registerReceiver(updates,new IntentFilter(PA6RadioService.ACTION_STATE));listening=true;}}
 @Override protected void onStop(){if(listening){unregisterReceiver(updates);listening=false;}super.onStop();}
 @Override protected void onDestroy(){serial++;super.onDestroy();}
}
