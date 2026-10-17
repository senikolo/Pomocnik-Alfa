package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Android 6 radio: usable without directory, with mirror retries and diagnostics. */
public final class PA6RadioActivity extends Activity {
 private LinearLayout list;
 private TextView state, playback;
 private EditText search, custom;
 private int serial;
 private boolean listening;
 private final int violet=0xff6bdfd1;
 private final int ink=0xffeef5ff, muted=0xffb0c3dd;
 private android.content.SharedPreferences prefs(){return getPreferences(0);}
 private android.graphics.drawable.GradientDrawable surface(int bg){
  android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();
  d.setColor(bg);d.setCornerRadius(dp(13));d.setStroke(dp(1),0xff38516c);return d;
 }
 private final BroadcastReceiver updates=new BroadcastReceiver(){
  @Override public void onReceive(Context ctx,Intent intent){
   if(PA6RadioService.ACTION_STATE.equals(intent.getAction())){
    String message=intent.getStringExtra("message");
    if(message!=null&&playback!=null)playback.setText(message);
   }
  }
 };
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
 private TextView title(String s,int size){
  TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(ink);
  t.setPadding(dp(10),dp(8),dp(10),dp(8));return t;
 }
 private Button button(String s){
  Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(16);
  b.setMinHeight(dp(55));b.setTextColor(ink);
  b.setBackground(surface(0xff233954));return b;
 }
 private void play(String name,String url){
  if(url==null||!(url.startsWith("https://")||url.startsWith("http://"))||url.contains(" ")||url.length()>2000){
   playback.setText("Wpisz pełny adres internetowy transmisji (http:// lub https://).");return;
  }
  playback.setText("Łączenie z "+name+"…");
  try{
   Intent intent=new Intent(this,PA6RadioService.class).setAction(PA6RadioService.ACTION_PLAY);
   intent.putExtra("url",url);intent.putExtra("name",name);
   startService(intent);
  }catch(Exception e){playback.setText("Nie mogę uruchomić odtwarzacza. "+e.getClass().getSimpleName());}
 }
 private void station(LinearLayout parent,String name,String url){
  LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);
  LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(-1,dp(60));outer.setMargins(0,dp(3),0,dp(5));parent.addView(card,outer);
  Button play=button("▶  "+name);play.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
  card.addView(play,new LinearLayout.LayoutParams(0,-1,1));
  play.setOnClickListener(v->play(name,url));
  Button fav=button("☆");
  LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(dp(56),-1);fp.setMargins(dp(5),0,0,0);
  card.addView(fav,fp);fav.setOnClickListener(v->{
   prefs().edit().putString("fav_radio",name).putString("fav_url",url).apply();
   playback.setText("Dodano do ulubionych: "+name);
  });
 }
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xff0b1425);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(16),dp(12),dp(16),dp(20));
  root.setFocusableInTouchMode(true);root.requestFocus();scroll.addView(root);setContentView(scroll);
  TextView head=title("ALFA RADIO  •  NA ŻYWO",25);head.setTypeface(null,1);root.addView(head);
  playback=title("Wybierz stację. Muzyka gra także po wyjściu z ekranu.",16);
  playback.setTextColor(0xff8de0ce);playback.setBackground(surface(0xff1c3047));root.addView(playback);
  Button favorites=button("★  Odtwórz zapisaną ulubioną stację");root.addView(favorites);
  favorites.setOnClickListener(v->{
   String url=prefs().getString("fav_url",""),name=prefs().getString("fav_radio","");
   if(url.isEmpty())playback.setText("Nie masz jeszcze ulubionej stacji. Wybierz gwiazdkę przy nazwie.");
   else play(name,url);
  });
  Button stop=button("■  Zatrzymaj radio");root.addView(stop);
  stop.setOnClickListener(v->{
   playback.setText("Zatrzymano radio.");
   startService(new Intent(this,PA6RadioService.class).setAction(PA6RadioService.ACTION_STOP));
  });
  root.addView(title("SZYBKIE STACJE  •  JEDNO DOTKNIĘCIE",17));
  // Public direct broadcast streams; individual providers may update their URLs.
  station(root,"Radio ZET 2000","https://zt03.cdn.eurozet.pl/ZET200.mp3");
  station(root,"Radio ZET Love","https://zt03.cdn.eurozet.pl/ZETLOV.mp3");
  station(root,"Radio ZET 2000 • zapasowy adres","http://zet.cdn.eurozet.pl/ZET200.mp3");
  station(root,"RMF FM (wersja 48 kb/s)","https://rs103-krk.rmfstream.pl/RMFFM48");
  root.addView(title("ODKRYWAJ RADIO  •  STACJE Z KATALOGU",17));
  LinearLayout genres=new LinearLayout(this);genres.setGravity(Gravity.CENTER_VERTICAL);root.addView(genres);
  String[] genresNames={"Polskie","Rock","Jazz","Klasyczna"};
  String[] genresQueries={"","rock","jazz","classical"};
  for(int i=0;i<genresNames.length;i++){
   final String key=genresQueries[i];Button chip=button(genresNames[i]);
   LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.setMargins(dp(2),0,dp(2),0);
   genres.addView(chip,p);chip.setTextSize(12);chip.setOnClickListener(v->load(key));
  }
  search=new EditText(this);search.setSingleLine(true);
  search.setHint("Np. Trójka, RMF, TOK FM");search.setInputType(InputType.TYPE_CLASS_TEXT);
  search.setTextColor(ink);search.setHintTextColor(muted);search.setPadding(dp(12),0,dp(12),0);search.setBackground(surface(0xff1d2d44));root.addView(search,new LinearLayout.LayoutParams(-1,dp(55)));
  Button find=button("Szukaj stacji");root.addView(find);
  find.setOnClickListener(v->load(search.getText().toString().trim()));
  state=title("Ładuję katalog radia…",14);state.setTextColor(muted);root.addView(state);
  list=new LinearLayout(this);list.setOrientation(1);root.addView(list);
  root.addView(title("Masz własny adres transmisji?",16));
  custom=new EditText(this);custom.setSingleLine(true);
  custom.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
  custom.setHint("https://radio.example/stream.mp3");custom.setTextColor(ink);custom.setHintTextColor(muted);custom.setBackground(surface(0xff1d2d44));root.addView(custom,new LinearLayout.LayoutParams(-1,dp(55)));
  Button own=button("▶  Odtwórz własny adres");root.addView(own);
  own.setOnClickListener(v->play("Własna stacja",custom.getText().toString().trim()));
  root.addView(title("Gdy stacja nie gra, zobacz komunikat u góry. Przy problemach z HTTPS sprawdź także datę i godzinę tabletu.",13));
  load("");
 }
 private void load(String query){
  final int task=++serial;
  state.setText("Ładuję stacje…");list.removeAllViews();
  new Thread(()->{
   ArrayList<String[]> stations=new ArrayList<>();String error=null;
   String params=query.isEmpty()?"countrycode=PL":"name="+encode(query);
   String request="/json/stations/search?"+params+"&hidebroken=true&order=clickcount&reverse=true&limit=90";
   for(String host:new String[]{"https://de1.api.radio-browser.info","https://all.api.radio-browser.info"}){
    HttpURLConnection c=null;
    try{
     c=(HttpURLConnection)new URL(host+request).openConnection();
     c.setConnectTimeout(9000);c.setReadTimeout(12000);
     c.setRequestProperty("User-Agent","PomocnikAlfaTablet/2.2 (Android 6; radio search)");
     c.setRequestProperty("Accept","application/json");
     int code=c.getResponseCode();
     if(code!=200)throw new IOException("serwer katalogu: HTTP "+code);
     ByteArrayOutputStream out=new ByteArrayOutputStream();
     try(InputStream in=c.getInputStream()){
      byte[] buf=new byte[8192];int count,total=0;
      while((count=in.read(buf))!=-1){total+=count;if(total>1200000)throw new IOException("Zbyt duża lista");out.write(buf,0,count);}
     }
     JSONArray rows=new JSONArray(out.toString("UTF-8"));
     for(int n=0;n<rows.length()&&stations.size()<75;n++){
      JSONObject item=rows.optJSONObject(n);if(item==null)continue;
      String url=item.optString("url_resolved",item.optString("url")).trim();
      String name=item.optString("name","Radio").trim();
      String codec=item.optString("codec","").toUpperCase(Locale.ROOT);
      if(!((url.startsWith("http://")||url.startsWith("https://"))&&url.length()<2000
        &&name.length()>1&&!url.contains(" ")&&(codec.contains("MP3")||codec.contains("AAC"))))continue;
      stations.add(new String[]{name,url});
     }
     error=null;break;
    }catch(Exception e){error=e.getClass().getSimpleName()+": "+e.getMessage();}
    finally{if(c!=null)c.disconnect();}
   }
   final String msg=error;
   runOnUiThread(()->{
    if(isFinishing()||task!=serial)return;
    state.setText(msg!=null?"Katalog chwilowo niedostępny ("+msg+"). Użyj szybkich stacji u góry.":
     stations.isEmpty()?"Nie znaleziono pasujących stacji. Spróbuj innego hasła.":"Stacje z katalogu: "+stations.size()+" • dotknij, aby słuchać");
    for(String[] row:stations)station(list,row[0],row[1]);
   });
  }).start();
 }
 private static String encode(String q){try{return URLEncoder.encode(q,"UTF-8");}catch(Exception e){return "";}}
 @Override protected void onStart(){super.onStart();if(!listening){registerReceiver(updates,new IntentFilter(PA6RadioService.ACTION_STATE));listening=true;}}
 @Override protected void onStop(){if(listening){unregisterReceiver(updates);listening=false;}super.onStop();}
 @Override protected void onDestroy(){serial++;super.onDestroy();}
}
