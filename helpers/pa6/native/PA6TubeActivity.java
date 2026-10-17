package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.net.URLEncoder;

/** Lightweight Alfa Tube: local videos + official YouTube search in browser.
 * Deliberately no scraping, unauthorized downloads or unsupported video codecs.
 */
public final class PA6TubeActivity extends Activity {
 private static final int OPEN_VIDEO=772;
 private VideoView video;
 private TextView status;
 private EditText search;
 private boolean kids;
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
 private Button button(String title){
  Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setTextSize(16);b.setTextColor(0xff624397);b.setMinHeight(dp(55));return b;
 }
 private void description(LinearLayout root,String value,int size){
  TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(0xff5b506a);t.setPadding(dp(3),dp(7),dp(3),dp(7));root.addView(t);
 }
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);kids=getIntent().getBooleanExtra("kids",false);
  ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(0xfff6f3fa);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(16),dp(12),dp(16),dp(27));sc.addView(root);setContentView(sc);
  TextView head=new TextView(this);head.setText(kids?"🧸  Alfa Kids":"▶  Alfa Tube");
  head.setTextSize(26);head.setTextColor(0xff543684);root.addView(head);
  description(root,kids?"Kącik dziecięcy. Materiały w internecie otwierają się na oficjalnej stronie YouTube Kids. Kontrola rodzicielska zależy od serwisu.":"Filmy i muzyka: wyszukuj na oficjalnej stronie YouTube albo odtwarzaj własne pliki bez przeciążania pamięci tabletu.",15);
  if(!kids){
   video=new VideoView(this);video.setBackgroundColor(Color.BLACK);
   root.addView(video,new LinearLayout.LayoutParams(-1,dp(250)));
   MediaController controls=new MediaController(this);controls.setAnchorView(video);video.setMediaController(controls);
   video.setOnErrorListener((mp,what,extra)->{status.setText("Tego pliku nie można odtworzyć na Androidzie 6 — sprawdź format i kodek.");return true;});
   status=new TextView(this);status.setText("Odtwarzacz jest gotowy.");status.setTextSize(14);root.addView(status);
   Button file=button("▶  Otwórz film z tabletu");root.addView(file);
   file.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,OPEN_VIDEO);});
   Button stop=button("■  Zatrzymaj odtwarzanie");root.addView(stop);stop.setOnClickListener(v->{video.stopPlayback();status.setText("Zatrzymano.");});
  }
  description(root,kids?"Otwórz YouTube Kids:":"Wyszukaj film, teledysk lub kanał YouTube:",16);
  if(!kids){
   search=new EditText(this);search.setSingleLine(true);search.setText(getPreferences(0).getString("last_query",""));
   search.setHint("Tytuł filmu lub wykonawca");
   root.addView(search,new LinearLayout.LayoutParams(-1,dp(60)));
   Button find=button("🔎  Szukaj na YouTube");root.addView(find);
   find.setOnClickListener(v->{String q=search.getText().toString().trim();if(q.isEmpty()){Toast.makeText(this,"Wpisz tytuł lub wykonawcę",Toast.LENGTH_SHORT).show();return;}
    getPreferences(0).edit().putString("last_query",q).apply();
    try{launch("https://m.youtube.com/results?search_query="+URLEncoder.encode(q,"UTF-8"));}
    catch(Exception e){Toast.makeText(this,"Nie udało się wyszukać filmu",Toast.LENGTH_SHORT).show();}
   });
  }else{
   Button yt=button("▶  Otwórz YouTube Kids");root.addView(yt);
   yt.setOnClickListener(v->launch("https://www.youtubekids.com/"));
  }
  description(root,"Uwaga: współczesne strony YouTube mogą nie obsługiwać starego systemowego WebView. W takim przypadku potrzebna jest kompatybilna przeglądarka. Aplikacja nie obiecuje odtwarzania filmów chronionych DRM.",13);
 }
 private void launch(String url){
  try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
  catch(Exception e){Toast.makeText(this,"Zainstaluj przeglądarkę obsługującą wybrany serwis",Toast.LENGTH_LONG).show();}
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request!=OPEN_VIDEO||result!=RESULT_OK||data==null||video==null||data.getData()==null)return;
  try{
   video.stopPlayback();video.setVideoURI(data.getData());video.start();status.setText("Otwieram film z tabletu…");
  }catch(Exception e){status.setText("Nie można odczytać wybranego pliku.");}
 }
 @Override protected void onStop(){if(video!=null){try{video.stopPlayback();}catch(Exception ignored){}}super.onStop();}
}