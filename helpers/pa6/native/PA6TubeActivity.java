package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Locale;

/** Alfa Tube stays inside PA: YouTube official embedded player and native stream player. */
public final class PA6TubeActivity extends Activity {
 private static final int PICK_FILE=791;
 private FrameLayout outer,fullscreen;
 private View fullscreenChild;
 private WebChromeClient.CustomViewCallback fullscreenCallback;
 private WebView browser;
 private PA6EmbeddedPlayer media;
 private TextView status;
 private EditText search,link;
 private boolean kids;
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
 private TextView text(String value,int sp){
  TextView v=new TextView(this);v.setText(value);v.setTextColor(0xffedf2ff);v.setTextSize(sp);
  v.setPadding(dp(8),dp(7),dp(8),dp(7));return v;
 }
 private Button button(LinearLayout root,String title,Runnable a){
  Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(15);
  root.addView(b,new LinearLayout.LayoutParams(-1,dp(52)));b.setOnClickListener(v->a.run());return b;
 }
 private boolean youtube(Uri uri){
  if(uri==null||uri.getHost()==null||uri.getScheme()==null)return false;
  String host=uri.getHost().toLowerCase(Locale.ROOT);
  return "https".equals(uri.getScheme())&&(host.equals("youtube.com")||host.endsWith(".youtube.com")||host.equals("youtu.be"));
 }
 private String youtubeId(String address){
  try{
   Uri u=Uri.parse(address.trim());
   if(!youtube(u))return null;
   String host=u.getHost().toLowerCase(Locale.ROOT),id;
   if(host.equals("youtu.be"))id=u.getLastPathSegment();
   else if(u.getPath()!=null&&u.getPath().startsWith("/shorts/"))id=u.getPathSegments().size()>1?u.getPathSegments().get(1):null;
   else if(u.getPath()!=null&&u.getPath().startsWith("/embed/"))id=u.getPathSegments().size()>1?u.getPathSegments().get(1):null;
   else id=u.getQueryParameter("v");
   return id!=null&&id.matches("[A-Za-z0-9_-]{11}")?id:null;
  }catch(Exception e){return null;}
 }
 private void youtubeEmbed(String id){
  media.stop();media.setVisibility(View.GONE);browser.setVisibility(View.VISIBLE);
  status.setText("Ładuję odtwarzacz YouTube wewnątrz Alfa Tube…");
  // Official embed: app referrer is required by YouTube for Android WebView players.
  String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1.0'></head>"
   +"<body style='margin:0;background:#111827;color:white'><iframe style='border:0;width:100%;height:95vh' "
   +"allow='autoplay; encrypted-media; picture-in-picture; fullscreen' allowfullscreen "
   +"src='https://www.youtube.com/embed/"+id+"?playsinline=1&rel=0'></iframe></body></html>";
  browser.loadDataWithBaseURL("https://com.senikolo.pa6internal.example/",html,"text/html","UTF-8",null);
 }
 private void browseSearch(String query){
  if(query.isEmpty()){status.setText("Wpisz tytuł lub wykonawcę.");return;}
  media.stop();media.setVisibility(View.GONE);browser.setVisibility(View.VISIBLE);
  try{
   status.setText("Wyszukiwanie wewnątrz PA. Wybierz film z listy YouTube.");
   browser.loadUrl("https://m.youtube.com/results?search_query="+URLEncoder.encode(query,"UTF-8"));
  }catch(Exception e){status.setText("Nie udało się otworzyć wyników YouTube.");}
 }
 private void openLink(String input){
  String url=input.trim();
  String id=youtubeId(url);
  if(id!=null){youtubeEmbed(id);return;}
  if(url.startsWith("https://")&&(url.toLowerCase(Locale.ROOT).contains(".m3u8")
     ||url.toLowerCase(Locale.ROOT).contains(".mp4"))){
   browser.setVisibility(View.GONE);media.setVisibility(View.VISIBLE);
   media.play("Wideo internetowe",url);status.setText("Film odtwarzany wewnątrz Pomocnika Alfa.");return;
  }
  status.setText("Podaj link YouTube lub bezpośredni adres MP4/HLS. Materiały nie opuszczają PA.");
 }
 @Override public void onCreate(Bundle b){
  super.onCreate(b);kids=getIntent().getBooleanExtra("kids",false);
  getWindow().setStatusBarColor(0xff101b31);
  outer=new FrameLayout(this);
  LinearLayout body=new LinearLayout(this);body.setOrientation(1);
  body.setBackgroundColor(0xff101b31);body.setPadding(dp(11),dp(10),dp(11),dp(6));
  outer.addView(body);setContentView(outer);
  body.addView(text(kids?"🧸  Alfa Kids":"▶  Alfa Tube",24));
  status=text("Filmy oglądasz tutaj — bez przeskakiwania do innej aplikacji.",14);
  body.addView(status);
  if(kids)body.addView(text("Uwaga: YouTube wbudowany nie zapewnia kontroli rodzicielskiej YouTube Kids.",13));
  LinearLayout actions=new LinearLayout(this);actions.setOrientation(1);
  search=new EditText(this);search.setSingleLine(true);search.setTextColor(Color.WHITE);
  search.setHintTextColor(0xff9ba9c7);search.setHint("Tytuł, wykonawca lub kanał");
  actions.addView(search,new LinearLayout.LayoutParams(-1,dp(50)));
  button(actions,"🔎  Szukaj w Alfa Tube",()->browseSearch(search.getText().toString().trim()));
  link=new EditText(this);link.setSingleLine(true);link.setTextColor(Color.WHITE);
  link.setHintTextColor(0xff9ba9c7);link.setHint("Link YouTube, MP4 lub HLS");
  actions.addView(link,new LinearLayout.LayoutParams(-1,dp(50)));
  button(actions,"▶  Odtwórz link w Alfa Tube",()->openLink(link.getText().toString()));
  ScrollView controls=new ScrollView(this);controls.addView(actions);
  body.addView(controls,new LinearLayout.LayoutParams(-1,dp(215)));
  FrameLayout stage=new FrameLayout(this);
  body.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
  media=new PA6EmbeddedPlayer(this);stage.addView(media,new FrameLayout.LayoutParams(-1,-2));media.setVisibility(View.GONE);
  browser=new WebView(this);stage.addView(browser,new FrameLayout.LayoutParams(-1,-1));
  WebSettings settings=browser.getSettings();
  settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
  settings.setMediaPlaybackRequiresUserGesture(true);
  settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
  settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  browser.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView view,String url){
    String id=youtubeId(url);
    if(id!=null){youtubeEmbed(id);return true;}
    Uri u=Uri.parse(url);
    if(youtube(u)){return false;}
    // External host links from search may only be opened by explicit user action,
    // but remain blocked inside our embedded Youtube browser.
    status.setText("To odnośnik poza YouTube — zostajemy w Alfa Tube.");
    return true;
   }
   @Override public void onReceivedError(WebView view,int code,String message,String failingUrl){
    status.setText("Nie udało się wczytać YouTube w starym WebView. Sprawdź jego aktualizację w systemie.");
   }
  });
  browser.setWebChromeClient(new WebChromeClient(){
   @Override public void onShowCustomView(View view,CustomViewCallback callback){
    if(fullscreenChild!=null){callback.onCustomViewHidden();return;}
    fullscreenChild=view;fullscreenCallback=callback;
    fullscreen=new FrameLayout(PA6TubeActivity.this);
    fullscreen.setBackgroundColor(Color.BLACK);
    fullscreen.addView(view,new FrameLayout.LayoutParams(-1,-1));
    outer.addView(fullscreen,new FrameLayout.LayoutParams(-1,-1));
   }
   @Override public void onHideCustomView(){closeFullscreen();}
  });
  browser.loadDataWithBaseURL("https://com.senikolo.pa6internal.example/",
   "<html><body style='background:#101b31;color:#eee;font-family:sans-serif'><h3>ALFA TUBE</h3>"
   +"<p>Wpisz tytuł filmu lub wklej link YouTube. Odtwarzacz pozostanie w PA.</p></body></html>","text/html","UTF-8",null);
  button(body,"🎞  Wybierz film z tabletu",()->{
   try{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/*").addCategory(Intent.CATEGORY_OPENABLE);
    startActivityForResult(intent,PICK_FILE);
   }catch(Exception e){status.setText("Nie udało się otworzyć plików.");}
  });
 }
 private void closeFullscreen(){
  if(fullscreen!=null){outer.removeView(fullscreen);fullscreen.removeAllViews();fullscreen=null;}
  if(fullscreenCallback!=null){fullscreenCallback.onCustomViewHidden();fullscreenCallback=null;}
  fullscreenChild=null;
 }
 @Override public void onBackPressed(){
  if(fullscreen!=null){closeFullscreen();return;}
  if(browser!=null&&browser.canGoBack()){browser.goBack();return;}
  super.onBackPressed();
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request!=PICK_FILE||result!=RESULT_OK||data==null||data.getData()==null)return;
  browser.setVisibility(View.GONE);media.setVisibility(View.VISIBLE);
  media.playUri("Film z tabletu",data.getData());
  status.setText("Odtwarzanie lokalnego filmu w Alfa Tube.");
 }
 @Override protected void onDestroy(){
  closeFullscreen();
  if(browser!=null){browser.stopLoading();browser.destroy();}
  if(media!=null)media.release();
  super.onDestroy();
 }
}
