package com.ispina.lokalnie;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Alfa Tube Tablet / Android 6: entirely in-app search, thumbnails and video.
 * Uses public Piped/Invidious endpoints, never launches external applications
 * and never loads YouTube in the obsolete Android 6 system WebView.
 * Only a playable H264 MP4 stream with multiplexed audio is selected.
 */
public final class PA6TubeActivity extends Activity {
 private static final String[] PIPED={
  "https://pipedapi.kavin.rocks",
  "https://pipedapi.leptons.xyz",
  "https://pipedapi.nosebs.ru"
 };
 private static final String[] INVIDIOUS={
  "https://inv.nadeko.net",
  "https://invidious.tiekoetter.com",
  "https://invidious.nerdvpn.de"
 };
 private final int BG=0xff0b1221, CARD=0xff19263a, LINE=0xff32465f,
   FG=0xfff4f7fd, LIGHT=0xffacc0d9, RED=0xffff6375, MINT=0xff79dfd0;
 private VideoView video;
 private TextView state,playing,resultsLabel;
 private EditText query;
 private LinearLayout results;
 private boolean kids;
 private int request=0, playerRequest=0;
 private String activeVideo="";
 private final Handler main=new Handler(Looper.getMainLooper());
 private final LruCache<String,Bitmap> images=new LruCache<String,Bitmap>(4096){
  @Override protected int sizeOf(String key,Bitmap value){return Math.max(1,value.getByteCount()/1024);}
 };
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
 private GradientDrawable shape(int fill,int stroke,int r){
  GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(r));d.setStroke(dp(1),stroke);return d;
 }
 private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 private TextView text(String s,int pt,int color,boolean bold){
  TextView v=new TextView(this);v.setText(s);v.setTextColor(color);v.setTextSize(pt);
  if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
  v.setGravity(Gravity.CENTER_VERTICAL);return v;
 }
 private Button button(String s){
  Button b=new Button(this);b.setText(s);b.setAllCaps(false);
  b.setTextColor(FG);b.setTextSize(14);b.setMinHeight(dp(44));
  b.setBackground(shape(0xff293d58,LINE,12));return b;
 }
 private void message(String s){state.setText(s);}
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);kids=getIntent().getBooleanExtra("kids",false);
  getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
  getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
  LinearLayout page=vertical();page.setPadding(dp(14),dp(10),dp(14),dp(10));
  page.setBackgroundColor(BG);page.setFocusableInTouchMode(true);page.requestFocus();setContentView(page);
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
  PA6GlyphView icon=new PA6GlyphView(this,"tube",RED);
  header.addView(icon,new LinearLayout.LayoutParams(dp(42),dp(42)));
  LinearLayout names=vertical();names.setPadding(dp(11),0,0,0);
  names.addView(text(kids?"ALFA KIDS":"ALFA TUBE",24,FG,true));
  names.addView(text("Oglądaj w Pomocniku Alfa • bez przeglądarki",12,LIGHT,false));
  header.addView(names);page.addView(header);
  video=new VideoView(this);video.setBackgroundColor(Color.BLACK);
  int width=getResources().getDisplayMetrics().widthPixels-dp(28);
  int height=Math.max(dp(175),Math.min(dp(360),width*9/16));
  LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,height);
  vp.setMargins(0,dp(12),0,dp(5));page.addView(video,vp);
  MediaController controller=new MediaController(this);
  controller.setAnchorView(video);video.setMediaController(controller);
  video.setOnPreparedListener(mp->{
   mp.setScreenOnWhilePlaying(true);
   video.start();message("Odtwarzam wewnątrz Alfa Tube.");
   final int id=playerRequest;
   main.postDelayed(()->{
    if(id!=playerRequest||isFinishing()||video==null)return;
    if(mp.getVideoWidth()==0||mp.getVideoHeight()==0)
     message("Strumień nie przesyła obrazu na tym urządzeniu. Wybierz inny film.");
   },11000);
  });
  video.setOnErrorListener((mp,what,extra)->{
   message("Nie można odtworzyć tego formatu na Androidzie 6. Spróbuj innego filmu ("+what+").");
   return true;
  });
  playing=text("Wybierz film z wyników",16,FG,true);
  playing.setMaxLines(2);page.addView(playing);
  state=text("Ładuję prawdziwe propozycje filmów…",13,LIGHT,false);
  state.setPadding(dp(9),dp(7),dp(9),dp(7));
  state.setBackground(shape(CARD,LINE,11));page.addView(state);
  if(kids){
   TextView notice=text("Uwaga: wyszukiwanie nie ma certyfikowanych filtrów treści dla dzieci. Korzystaj pod opieką dorosłych.",12,0xffffc58e,false);
   notice.setPadding(0,dp(7),0,dp(5));page.addView(notice);
  }
  LinearLayout searchRow=new LinearLayout(this);searchRow.setGravity(Gravity.CENTER_VERTICAL);
  LinearLayout.LayoutParams sr=new LinearLayout.LayoutParams(-1,dp(51));sr.setMargins(0,dp(9),0,dp(5));page.addView(searchRow,sr);
  query=new EditText(this);query.setSingleLine(true);
  query.setTextColor(FG);query.setHintTextColor(LIGHT);query.setTextSize(15);
  query.setHint("Szukaj filmów, muzyki, kanałów");
  query.setBackground(shape(CARD,LINE,12));query.setPadding(dp(12),0,dp(10),0);
  searchRow.addView(query,new LinearLayout.LayoutParams(0,-1,1));
  Button go=button("Szukaj");
  LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(dp(96),-1);gp.setMargins(dp(7),0,0,0);
  searchRow.addView(go,gp);go.setOnClickListener(v->search(query.getText().toString().trim()));
  LinearLayout tabs=new LinearLayout(this);
  page.addView(tabs,new LinearLayout.LayoutParams(-1,dp(46)));
  String[] topics=kids?new String[]{"Bajki","Nauka","Piosenki"}:new String[]{"Polecane","Muzyka","Wiadomości","Technologia"};
  String[] searches=kids?new String[]{"bajki dla dzieci","nauka dla dzieci","piosenki dla dzieci"}:
    new String[]{"","muzyka Polska","wiadomości Polska","technologia"};
  for(int i=0;i<topics.length;i++){
   final String target=searches[i];
   Button tab=button(topics[i]);
   LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(0,dp(40),1);
   tl.setMargins(dp(2),0,dp(2),0);tabs.addView(tab,tl);
   tab.setOnClickListener(v->search(target));
  }
  resultsLabel=text("FILMY",12,MINT,true);page.addView(resultsLabel);
  ScrollView sc=new ScrollView(this);
  page.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
  results=vertical();results.setPadding(0,dp(5),0,dp(12));sc.addView(results);
  Button history=button("↺  Ostatnio oglądany film");
  page.addView(history,new LinearLayout.LayoutParams(-1,dp(45)));
  history.setOnClickListener(v->{
   String id=getPreferences(0).getString("last_id","");
   if(id.length()==11)play(id,getPreferences(0).getString("last_title","Poprzedni film"));
   else message("Nie ma jeszcze zapisanej historii odtwarzania.");
  });
  if(kids)search("bajki dla dzieci");
  else search("");
 }
 private String read(String address,int max)throws Exception{
  URL u=new URL(address);
  if(!"https".equalsIgnoreCase(u.getProtocol()))throw new Exception("Serwer wymaga HTTPS");
  HttpURLConnection c=PA6Https.open(this,address);
  try{
   c.setConnectTimeout(5500);c.setReadTimeout(6500);
   c.setRequestProperty("Accept","application/json");
   c.setRequestProperty("User-Agent","Mozilla/5.0 (compatible; PomocnikAlfa/6.0)");
   int status=c.getResponseCode();
   if(status!=200)throw new Exception("serwer HTTP "+status);
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();
   try(InputStream in=c.getInputStream()){
    byte[] buf=new byte[8192];int n;
    while((n=in.read(buf))>=0){if(bytes.size()+n>max)throw new Exception("Za duża odpowiedź");
     bytes.write(buf,0,n);}
   }
   return bytes.toString("UTF-8");
  }finally{c.disconnect();}
 }
 private static String encode(String q){
  try{return URLEncoder.encode(q,"UTF-8");}catch(Exception e){return q;}
 }
 private String idOf(String url){
  if(url==null)return "";
  if(url.matches("[a-zA-Z0-9_-]{11}"))return url;
  try{
   Uri u=Uri.parse(url.startsWith("/")?"https://youtube.com"+url:url);
   String v=u.getQueryParameter("v");
   if(v!=null&&v.matches("[a-zA-Z0-9_-]{11}"))return v;
   if("youtu.be".equals(u.getHost())){
    String last=u.getLastPathSegment();if(last!=null&&last.matches("[a-zA-Z0-9_-]{11}"))return last;
   }
   String path=u.getLastPathSegment();
   return path!=null&&path.matches("[a-zA-Z0-9_-]{11}")?path:"";
  }catch(Exception e){return "";}
 }
 private JSONArray videosFrom(JSONObject data){
  JSONArray items=data.optJSONArray("items");
  if(items==null)items=data.optJSONArray("relatedStreams");
  return items==null?new JSONArray():items;
 }
 private void search(String q){
  if(q.length()>120){message("Wpisz krótsze hasło wyszukiwania.");return;}
  int token=++request;
  message(q.isEmpty()?"Pobieram propozycje filmów…":"Wyszukuję: "+q+"…");
  resultsLabel.setText(q.isEmpty()?"POLECANE":"WYNIKI: "+q);
  results.removeAllViews();
  new Thread(()->{
   JSONArray entries=null;String error="Serwery filmów są niedostępne.";
   for(String host:PIPED){
    try{
     String path=q.isEmpty()?"/trending?region=PL":"/search?q="+encode(q)+"&filter=videos";
     String data=read(host+path,800000);
     JSONArray parsed=q.isEmpty()?new JSONArray(data):videosFrom(new JSONObject(data));
     if(parsed.length()==0)continue;
     entries=parsed;break;
    }catch(Exception e){error=e.getMessage();}
   }
   if(entries==null)for(String host:INVIDIOUS){
    try{
     String path=q.isEmpty()?"/api/v1/popular?region=PL":
       "/api/v1/search?q="+encode(q)+"&type=video";
     JSONArray parsed=new JSONArray(read(host+path,800000));
     if(parsed.length()==0)continue;
     entries=parsed;break;
    }catch(Exception e){error=e.getMessage();}
   }
   final JSONArray found=entries;final String problem=error;
   runOnUiThread(()->{
    if(isFinishing()||request!=token)return;
    if(found==null){message("Nie udało się pobrać filmów ("+problem+"). Sprawdź internet i spróbuj ponownie.");return;}
    int count=0;
    for(int i=0;i<found.length()&&count<22;i++){
     JSONObject item=found.optJSONObject(i);if(item==null)continue;
     if(item.optString("type","video").equals("channel"))continue;
     String id=idOf(item.optString("url",item.optString("videoId","")));
     if(id.isEmpty())continue;
     String title=item.optString("title","Film"),author=item.optString("uploader",item.optString("author",""));
     String thumb=item.optString("thumbnail","");
     JSONArray thumbs=item.optJSONArray("videoThumbnails");
     if(thumbs!=null&&thumbs.length()>0)thumb=thumbs.optJSONObject(Math.min(2,thumbs.length()-1)).optString("url",thumb);
     card(id,title,author,thumb);count++;
    }
    message(count>0?"Znaleziono "+count+" filmów. Otwórz wybrany, aby oglądać w PA.":"Brak filmów dla tego zapytania.");
   });
  }).start();
 }
 private void card(String id,String title,String author,String thumbnail){
  LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
  row.setPadding(dp(9),dp(7),dp(9),dp(7));
  row.setBackground(shape(CARD,LINE,13));
  LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(91));rp.setMargins(0,0,0,dp(8));
  results.addView(row,rp);
  ImageView image=new ImageView(this);image.setBackgroundColor(0xff2b364a);
  image.setScaleType(ImageView.ScaleType.CENTER_CROP);
  row.addView(image,new LinearLayout.LayoutParams(dp(122),dp(72)));
  thumbnail(image,thumbnail);
  LinearLayout info=vertical();info.setPadding(dp(11),0,0,0);
  TextView name=text(title,15,FG,true);name.setMaxLines(2);info.addView(name);
  TextView by=text(author,12,LIGHT,false);by.setMaxLines(1);info.addView(by);
  row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
  row.setOnClickListener(v->play(id,title));
 }
 private void thumbnail(ImageView view,String address){
  if(address==null||!address.startsWith("https://")||address.length()>1700)return;
  Bitmap cached=images.get(address);
  if(cached!=null){view.setImageBitmap(cached);return;}
  new Thread(()->{
   HttpURLConnection c=null;
   try{
    c=PA6Https.open(this,address);
    c.setConnectTimeout(4000);c.setReadTimeout(4500);
    if(c.getResponseCode()!=200||c.getContentLength()>260000) return;
    ByteArrayOutputStream data=new ByteArrayOutputStream();
    try(InputStream in=c.getInputStream()){byte[] b=new byte[4096];int n;
     while((n=in.read(b))!=-1){if(data.size()+n>260000)return;data.write(b,0,n);}
    }
    BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;
    Bitmap bitmap=BitmapFactory.decodeByteArray(data.toByteArray(),0,data.size(),opts);
    if(bitmap==null)return;images.put(address,bitmap);
    runOnUiThread(()->{if(!isFinishing())view.setImageBitmap(bitmap);});
   }catch(Exception ignored){}finally{if(c!=null)c.disconnect();}
  }).start();
 }
 private String playable(JSONObject response){
  JSONArray streams=response.optJSONArray("videoStreams");
  String best="";int bestScore=-1;
  if(streams!=null)for(int i=0;i<streams.length();i++){
   JSONObject s=streams.optJSONObject(i);if(s==null||s.optBoolean("videoOnly",true))continue;
   String type=s.optString("mimeType",""),codec=s.optString("codec",""),url=s.optString("url","");
   int height=s.optInt("height",0);
   if(!type.contains("mp4")||(!codec.isEmpty()&&!codec.startsWith("avc1"))||height>720)continue;
   if(url.startsWith("https://")&&url.length()<7000){
    int score=height>0&&height<=480?1000+height:height>0?height:1;
    if(score>bestScore){best=url;bestScore=score;}
   }
  }
  if(!best.isEmpty())return best;
  JSONArray formats=response.optJSONArray("formatStreams");
  if(formats!=null)for(int i=0;i<formats.length();i++){
   JSONObject f=formats.optJSONObject(i);if(f==null)continue;
   String url=f.optString("url","");
   String type=f.optString("type",f.optString("container",""));
   int quality=0;String label=f.optString("qualityLabel",f.optString("resolution",""));
   try{quality=Integer.parseInt(label.replaceAll("[^0-9]",""));}catch(Exception ignored){}
   if(url.startsWith("https://")&&url.length()<7000&&type.toLowerCase(Locale.ROOT).contains("mp4")&&quality<=720){
    int score=quality<=480?1000+quality:quality;
    if(score>bestScore){best=url;bestScore=score;}
   }
  }
  return best;
 }
 private void play(String id,String title){
  if(!id.matches("[a-zA-Z0-9_-]{11}")){message("Nieprawidłowy identyfikator filmu.");return;}
  int token=++playerRequest;activeVideo=id;
  playing.setText(title);message("Sprawdzam format wideo i ścieżkę dźwiękową…");
  video.stopPlayback();
  getPreferences(0).edit().putString("last_id",id).putString("last_title",title).apply();
  new Thread(()->{
   String stream="",problem="Nie udało się pobrać strumienia.";
   for(String host:PIPED){
    try{
     JSONObject data=new JSONObject(read(host+"/streams/"+id,700000));
     stream=playable(data);if(!stream.isEmpty())break;
     problem="Brak zgodnego wideo H.264 MP4 z dźwiękiem";
    }catch(Exception e){problem=e.getMessage();}
   }
   if(stream.isEmpty())for(String host:INVIDIOUS){
    try{
     JSONObject data=new JSONObject(read(host+"/api/v1/videos/"+id,700000));
     stream=playable(data);if(!stream.isEmpty())break;
     problem="Brak zgodnego formatu MP4";
    }catch(Exception e){problem=e.getMessage();}
   }
   final String url=stream,why=problem;
   runOnUiThread(()->{
    if(isFinishing()||token!=playerRequest)return;
    if(url.isEmpty()){message("Ten film nie jest dostępny w zgodnym formacie ("+why+"). Możesz wybrać inny.");return;}
    try{
     video.setVideoURI(Uri.parse(url));video.requestFocus();
     video.start();message("Buforuję film w oknie Alfa Tube…");
    }catch(Exception e){message("Nie mogę otworzyć strumienia na tym tablecie.");}
   });
  }).start();
 }
 @Override protected void onPause(){if(video!=null&&video.isPlaying())video.pause();super.onPause();}
 @Override protected void onDestroy(){request++;playerRequest++;if(video!=null)video.stopPlayback();super.onDestroy();}
}
