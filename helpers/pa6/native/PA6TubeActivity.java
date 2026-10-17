package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Android 6 Alfa Tube: honest handoff to a compatible YouTube client,
 * direct video link routing, and local-video preview.
 * The old system WebView is NOT a modern YouTube playback engine.
 */
public final class PA6TubeActivity extends Activity {
 private static final int OPEN_VIDEO=772;
 private static final String NEWPIPE="org.schabi.newpipe";
 private VideoView video;
 private TextView status;
 private EditText search,videoLink;
 private boolean kids;
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
 private Button button(String label){
  Button b=new Button(this);b.setAllCaps(false);b.setText(label);
  b.setTextSize(16);b.setTextColor(0xff624397);b.setMinHeight(dp(56));return b;
 }
 private TextView description(LinearLayout root,String value,int size){
  TextView t=new TextView(this);t.setText(value);t.setTextSize(size);
  t.setTextColor(0xff534669);t.setPadding(dp(5),dp(8),dp(5),dp(8));root.addView(t);return t;
 }
 private boolean hasNewPipe(){
  try{getPackageManager().getPackageInfo(NEWPIPE,0);return true;}
  catch(Exception e){return false;}
 }
 private void openBrowser(String url){
  try{
   startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));
  }catch(Exception e){
   status.setText("Nie znalazłem przeglądarki obsługującej ten adres. Możesz skorzystać z NewPipe dla Androida 6.");
  }
 }
 private void openNewPipe(String query){
  Intent launch=getPackageManager().getLaunchIntentForPackage(NEWPIPE);
  if(launch==null){
   status.setText("Nie ma NewPipe. Zainstaluj wydanie dla Androida 6 z oficjalnego F-Droid lub otwórz wideo w obsługiwanej przeglądarce.");
   return;
  }
  if(!query.isEmpty()){
   try{
    ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE))
     .setPrimaryClip(ClipData.newPlainText("Alfa Tube — wyszukiwanie",query));
   }catch(Exception ignored){}
  }
  try{
   startActivity(launch);
   if(!query.isEmpty())Toast.makeText(this,"Hasło skopiowane. Otwórz wyszukiwanie NewPipe i wybierz Wklej.",Toast.LENGTH_LONG).show();
   status.setText("NewPipe uruchomiony."+(!query.isEmpty()?" Hasło wyszukiwania skopiowano do schowka.":""));
  }catch(Exception e){status.setText("Nie udało się otworzyć NewPipe. Spróbuj uruchomić aplikację ręcznie.");}
 }
 private boolean validYouTube(Uri uri){
  if(uri==null||uri.getScheme()==null||uri.getHost()==null)return false;
  String scheme=uri.getScheme().toLowerCase(Locale.ROOT),host=uri.getHost().toLowerCase(Locale.ROOT);
  if(!(scheme.equals("https")||scheme.equals("http")))return false;
  return host.equals("youtu.be")||host.equals("youtube.com")||
   host.equals("m.youtube.com")||host.equals("www.youtube.com")||host.equals("music.youtube.com");
 }
 private void openVideoLink(String url){
  Uri uri;
  try{uri=Uri.parse(url);}catch(Exception e){status.setText("Nieprawidłowy adres filmu.");return;}
  if(url.length()>2000||!validYouTube(uri)){
   status.setText("Wklej prawidłowy link YouTube (youtube.com/watch albo youtu.be/…).");
   return;
  }
  if(hasNewPipe()){
   try{
    Intent i=new Intent(Intent.ACTION_VIEW,uri);
    i.setPackage(NEWPIPE);
    i.addCategory(Intent.CATEGORY_DEFAULT);
    startActivity(i);status.setText("Przekazano film do NewPipe.");return;
   }catch(Exception ignored){}
  }
  openBrowser(url);
 }
 @Override public void onCreate(Bundle b){
  super.onCreate(b);kids=getIntent().getBooleanExtra("kids",false);
  ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(0xfff6f3fa);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(14),dp(10),dp(14),dp(26));
  sc.addView(root);setContentView(sc);
  TextView head=description(root,kids?"🧸  Alfa Kids":"▶  Alfa Tube",25);
  head.setTextColor(0xff543684);head.setTypeface(null,1);
  status=description(root,kids?"Treści dla dzieci otwierają się w zewnętrznej aplikacji lub przeglądarce.":
   "Na Androidzie 6 YouTube może nie działać w starej przeglądarce. Użyj zgodnej aplikacji NewPipe (Android 6+).",15);
  if(kids){
   description(root,"YouTube Kids wymaga obsługiwanej przeglądarki. Nie zastępuje to kontroli rodzicielskiej.",15);
   Button kidsBrowser=button("▶ Otwórz oficjalne YouTube Kids");root.addView(kidsBrowser);
   kidsBrowser.setOnClickListener(v->openBrowser("https://www.youtubekids.com/"));
   Button parent=button("▶ Przeglądaj filmy w NewPipe (bez filtrów Kids)");root.addView(parent);
   parent.setOnClickListener(v->openNewPipe(""));
   description(root,"Uwaga: NewPipe nie ma filtrów YouTube Kids. Z tego powodu nie jest równoważną aplikacją dziecięcą.",13);
   return;
  }
  description(root,hasNewPipe()?"✓ Zgodny odtwarzacz NewPipe jest zainstalowany.":
   "NewPipe nie jest zainstalowany. To bezpłatny odtwarzacz filmów z YouTube obsługujący Androida 6.",16);
  Button start=button("▶ Otwórz filmy w NewPipe");root.addView(start);
  start.setOnClickListener(v->openNewPipe(""));
  Button install=button("Pobierz NewPipe — oficjalna strona F-Droid");root.addView(install);
  install.setOnClickListener(v->openBrowser("https://f-droid.org/packages/org.schabi.newpipe/"));
  description(root,"Wyszukaj film lub teledysk:",16);
  search=new EditText(this);search.setSingleLine(true);
  search.setText(getPreferences(0).getString("last_query",""));
  search.setHint("Wykonawca, piosenka, nazwa kanału");
  root.addView(search,new LinearLayout.LayoutParams(-1,dp(57)));
  Button find=button("🔎 Szukaj w NewPipe");root.addView(find);
  find.setOnClickListener(v->{
   String q=search.getText().toString().trim();
   if(q.isEmpty()){status.setText("Wpisz tytuł filmu lub wykonawcę.");return;}
   getPreferences(0).edit().putString("last_query",q).apply();
   openNewPipe(q);
  });
  description(root,"Wyszukiwanie: Alfa przekazuje hasło do schowka i otwiera NewPipe. W NewPipe użyj lupy i Wklej.",13);
  Button browser=button("🌐 Szukaj na oficjalnej stronie YouTube");root.addView(browser);
  browser.setOnClickListener(v->{
   String q=search.getText().toString().trim();
   if(q.isEmpty()){status.setText("Wpisz tytuł filmu.");return;}
   try{openBrowser("https://m.youtube.com/results?search_query="+URLEncoder.encode(q,"UTF-8"));}
   catch(Exception e){status.setText("Nie mogę przygotować zapytania.");}
  });
  description(root,"Otwórz konkretny film z linku YouTube:",16);
  videoLink=new EditText(this);videoLink.setSingleLine(true);
  videoLink.setHint("https://www.youtube.com/watch?v=…");
  videoLink.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
  root.addView(videoLink,new LinearLayout.LayoutParams(-1,dp(58)));
  Button link=button("▶ Otwórz film");root.addView(link);
  link.setOnClickListener(v->openVideoLink(videoLink.getText().toString().trim()));
  description(root,"Film zapisany na tablecie:",16);
  video=new VideoView(this);video.setBackgroundColor(Color.BLACK);
  root.addView(video,new LinearLayout.LayoutParams(-1,dp(232)));
  MediaController controls=new MediaController(this);controls.setAnchorView(video);video.setMediaController(controls);
  video.setOnErrorListener((mp,what,extra)->{
   status.setText("Ten plik nie jest obsługiwany przez systemowy odtwarzacz Android 6. Wypróbuj format MP4 / H.264.");
   return true;
  });
  Button file=button("▶ Otwórz lokalne MP4");root.addView(file);
  file.setOnClickListener(v->{
   try{
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.setType("video/*");i.addCategory(Intent.CATEGORY_OPENABLE);
    startActivityForResult(i,OPEN_VIDEO);
   }catch(Exception e){status.setText("Nie znaleziono aplikacji do wyboru plików.");}
  });
  Button stop=button("■ Zatrzymaj film");root.addView(stop);
  stop.setOnClickListener(v->{video.stopPlayback();status.setText("Zatrzymano odtwarzanie filmu.");});
  description(root,"Alfa Tube korzysta z NewPipe lub przeglądarki przy filmach online, a z systemowego odtwarzacza przy własnych plikach. Filmy online nie są odtwarzane wewnątrz ekranu PA.",13);
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request!=OPEN_VIDEO||result!=RESULT_OK||data==null||video==null||data.getData()==null)return;
  try{
   video.stopPlayback();video.setVideoURI(data.getData());
   video.setOnPreparedListener(p->{video.start();status.setText("Odtwarzam film z tabletu.");});
   video.start();status.setText("Otwieram film z tabletu…");
  }catch(Exception e){status.setText("Nie można otworzyć pliku.");}
 }
 @Override protected void onStop(){if(video!=null){try{video.stopPlayback();}catch(Exception ignored){}}super.onStop();}
}
