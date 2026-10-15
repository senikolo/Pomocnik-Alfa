package com.ispina.lokalnie;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.Manifest;
import android.content.*;
import android.net.Uri;
import android.webkit.*;
import android.view.WindowManager;
import android.widget.Toast;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Local-only WebView. All internet access is allowlisted here, never arbitrary page code. */
public final class PARegionsActivity extends Activity {
 private WebView web;private volatile boolean destroyed;private volatile File active;
 private final ExecutorService jobs=Executors.newFixedThreadPool(3);
 private final String HOST="pa-bus.invalid";
 private static final String DATA_URL="https://github.com/senikolo/Pomocnik-Alfa/releases/download/pa2-data/pa2-data.zip";
 @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);getWindow().setStatusBarColor(0xff493075);getWindow().setNavigationBarColor(0xff1b142a);getWindow().getDecorView().setSystemUiVisibility(0);
  String folder=PADataStore.preferences(this).getString("data_folder","");if(folder.matches("data-[0-9]+")){File f=new File(getFilesDir(),folder);if(new File(f,"index_all.json").isFile())active=f;}
  web=new WebView(this);web.setBackgroundColor(0xfff3f7fc);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setMediaPlaybackRequiresUserGesture(true);if(Build.VERSION.SDK_INT>=26)s.setSafeBrowsingEnabled(true);
  web.setWebChromeClient(new WebChromeClient());web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return !local(r.getUrl());}
   @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
    Uri u=r.getUrl();if(!local(u))return error();String path=u.getPath();if(path==null)return error();String name=path.substring(1);if(path.startsWith("/osm/"))return PAOSMTiles.load(PARegionsActivity.this,path);
    if(!name.matches("(?:app\\.html|app\\.js|transit-core\\.js|conversation\\.js|osm-map\\.js|live-state\\.js|index_all\\.json|route_all_[0-9]+\\.json|manifest\\.json|summary\\.json)"))return error();
    String mime=name.endsWith(".html")?"text/html":name.endsWith(".js")?"application/javascript":"application/json";
    try{InputStream in;File current=active;if(name.endsWith(".json")&&current!=null)in=new FileInputStream(new File(current,name));else in=getAssets().open("pa_regions/"+name);
     Map<String,String> h=new HashMap<>();h.put("Cache-Control","no-store");h.put("X-Content-Type-Options","nosniff");h.put("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; object-src 'none'; frame-src 'none'; base-uri 'none'; form-action 'none'");return new WebResourceResponse(mime,"UTF-8",200,"OK",h,in);
    }catch(Exception e){return error();}
   }
  });web.addJavascriptInterface(new Bridge(),"PADevice");setContentView(web);web.loadUrl("https://"+HOST+"/app.html");
 }
 private boolean local(Uri u){return "https".equals(u.getScheme())&&HOST.equals(u.getHost())&&u.getPort()==-1;}
 private WebResourceResponse error(){return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",Collections.<String,String>emptyMap(),new ByteArrayInputStream(new byte[0]));}
 private void reply(final String id,final JSONObject result){runOnUiThread(()->{if(!destroyed&&web!=null)web.evaluateJavascript("window.PANativeReply("+JSONObject.quote(id)+","+result.toString()+")",null);});}
 private JSONObject ok(Object data)throws JSONException{return new JSONObject().put("ok",true).put("data",data);}
 private JSONObject fail(String message){try{return new JSONObject().put("ok",false).put("message",message);}catch(Exception e){return new JSONObject();}}
 public final class Bridge {
  @JavascriptInterface public void request(String raw){if(destroyed||raw==null||raw.length()>80000)return;try{final JSONObject p=new JSONObject(raw);final String id=p.getString("id"),type=p.getString("type");if(!id.matches("[0-9]{1,12}"))return;jobs.submit(()->{try{JSONObject result=dispatch(type,p.optJSONObject("payload"));reply(id,ok(result));}catch(Exception e){String message=e.getMessage();if(e instanceof java.net.UnknownHostException||e instanceof java.net.ConnectException||e instanceof java.net.SocketTimeoutException)message="Nie mogę teraz połączyć się z internetem. Rozkłady zapisane w aplikacji nadal działają.";else if(e instanceof javax.net.ssl.SSLException)message="Nie udało się bezpiecznie połączyć ze źródłem. Spróbuj później.";else if(e instanceof org.json.JSONException)message="Źródło zwróciło niepełne dane. Spróbuj ponownie później.";reply(id,fail(message==null?"Nie udało się wykonać tej czynności.":message));}});}catch(Exception ignored){}}
 }
 private JSONObject dispatch(String type,JSONObject p)throws Exception {
  if(p==null)p=new JSONObject();final JSONObject data=p;
  if(type.equals("copy")){final String text=p.optString("text").substring(0,Math.min(p.optString("text").length(),20000));runOnUiThread(()->{android.content.ClipboardManager c=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);c.setPrimaryClip(ClipData.newPlainText("Podróż z Alfą",text));Toast.makeText(this,"Skopiowano",Toast.LENGTH_SHORT).show();});return new JSONObject().put("saved",true);}
  if(type.equals("note")){String text=p.optString("text").trim();if(text.isEmpty()||text.length()>8000)throw new IOException("Notatka jest pusta albo zbyt długa.");android.content.SharedPreferences pref=getSharedPreferences("native_notes",0);String old=pref.getString("note","");if(old.length()+text.length()>200000)throw new IOException("Notatnik jest pełny. Zrób kopię i uporządkuj notatki.");if(!pref.edit().putString("note",old+(old.isEmpty()?"":"\n\n")+text).commit())throw new IOException("Nie udało się zapisać notatki.");return new JSONObject().put("saved",true);}
  if(type.equals("reminder")){SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm",Locale.ROOT);fmt.setLenient(false);fmt.setTimeZone(TimeZone.getTimeZone("Europe/Warsaw"));long at=fmt.parse(p.getString("when")).getTime();if(at<=System.currentTimeMillis())throw new IOException("Ta godzina już minęła. Podaj przyszły termin.");if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){runOnUiThread(()->requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},400));throw new IOException("Włącz powiadomienia i ponownie zapisz przypomnienie.");}
   android.app.NotificationManager nm=(android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(!nm.areNotificationsEnabled())throw new IOException("Powiadomienia PA są wyłączone. Włącz je w ustawieniach telefonu.");
   Class<?> cl=Class.forName("com.ispina.lokalnie.reminders.ReminderScheduler");boolean saved=(Boolean)cl.getMethod("saveAndSchedule",Context.class,String.class,String.class,String.class,long.class,String.class).invoke(null,this,"alfa-"+UUID.randomUUID(),p.optString("text","Przypomnienie"),p.optString("text"),at,"once");if(!saved)throw new IOException("Nie udało się ustawić alarmu. Sprawdź uprawnienie dokładnych alarmów w module Przypomnienia.");return new JSONObject().put("saved",true).put("message","Przypomnienie zapisane w PA. Termin realizacji zależy również od uprawnień alarmów i ustawień baterii telefonu.");}
  if(type.equals("weather"))return weather(p);
  if(type.equals("search"))return search(p.optString("query"));
  if(type.equals("homeStop"))return new JSONObject().put("stopId",getIntent().getStringExtra("pa_stop_id"));
  if(type.equals("live"))return PALive.lookup(p,(u,max)->read(u,max));
  if(type.equals("update"))return update();
  if(type.equals("back")){runOnUiThread(()->finish());return new JSONObject();}
  if(type.equals("module")){final String name=p.optString("name");final Map<String,String> names=new HashMap<>();names.put("notes","NotesActivity");names.put("reminders","RemindersActivity");names.put("weather","WeatherActivity");names.put("radio","RadioActivity");names.put("tv","TvActivity");if(!names.containsKey(name))throw new IOException("Nieznany moduł.");runOnUiThread(()->{try{startActivity(new Intent(this,Class.forName("com.ispina.lokalnie."+names.get(name))));}catch(Exception e){Toast.makeText(this,"Moduł niedostępny",Toast.LENGTH_SHORT).show();}});return new JSONObject();}
  if(type.equals("official")){final String url=p.optString("url");if(!Arrays.asList("https://portalpasazera.pl/","https://www.wtp.waw.pl/","https://ztp.krakow.pl/","https://www.kolejemalopolskie.com.pl/").contains(url))throw new IOException("Nieznany serwis.");runOnUiThread(()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))));return new JSONObject();}
  throw new IOException("Nieobsługiwana czynność.");
 }
 private JSONObject weather(JSONObject p)throws Exception{
  String city=p.optString("city").trim();if(city.isEmpty()||city.length()>140)throw new IOException("Podaj miejscowość dla pogody.");int offset=Math.max(0,Math.min(6,p.optInt("offset")));
  // Public noncommercial Open-Meteo endpoints, attribution shown with the result.
  JSONObject place;
  if(p.has("latitude")&&p.has("longitude")){double lat=p.getDouble("latitude"),lon=p.getDouble("longitude");if(lat<48||lat>56||lon<13||lon>25)throw new IOException("Przystanek jest poza obszarem tej prognozy.");place=new JSONObject().put("name",city).put("latitude",lat).put("longitude",lon);}
  else{JSONObject geo=new JSONObject(new String(read("https://geocoding-api.open-meteo.com/v1/search?name="+enc(city)+"&count=4&language=pl&format=json&countryCode=PL",300000),StandardCharsets.UTF_8));JSONArray rows=geo.optJSONArray("results");if(rows==null||rows.length()==0)throw new IOException("Nie odnaleziono miejscowości „"+city+"”. Podaj nazwę miasta.");place=rows.getJSONObject(0);}
  JSONObject forecast=new JSONObject(new String(read("https://api.open-meteo.com/v1/forecast?latitude="+place.getDouble("latitude")+"&longitude="+place.getDouble("longitude")+"&daily=weather_code,temperature_2m_min,temperature_2m_max,precipitation_probability_max&timezone=Europe%2FWarsaw&forecast_days=7",300000),StandardCharsets.UTF_8));JSONObject d=forecast.getJSONObject("daily");return new JSONObject().put("city",place.getString("name")).put("region",place.optString("admin1")).put("date",d.getJSONArray("time").getString(offset)).put("min",d.getJSONArray("temperature_2m_min").get(offset)).put("max",d.getJSONArray("temperature_2m_max").get(offset)).put("rain",d.getJSONArray("precipitation_probability_max").get(offset)).put("code",d.getJSONArray("weather_code").get(offset)).put("source","Open-Meteo • CC BY 4.0").put("url","https://open-meteo.com/").put("checkedAt",System.currentTimeMillis());
 }
 private JSONObject search(String query)throws Exception{
  if(query.isEmpty()||query.length()>500)throw new IOException("Wpisz krótsze pytanie.");
  // Wikipedia retrieval, explicitly never presented as general web/news coverage or LLM reasoning.
  JSONObject r=new JSONObject(new String(read("https://pl.wikipedia.org/w/api.php?action=query&format=json&generator=search&gsrsearch="+enc(query)+"&gsrlimit=3&prop=extracts%7Cinfo&exintro=1&explaintext=1&exsentences=3&inprop=url",500000),StandardCharsets.UTF_8));JSONArray out=new JSONArray();JSONObject pages=r.optJSONObject("query");pages=pages==null?null:pages.optJSONObject("pages");if(pages!=null){Iterator<String> keys=pages.keys();while(keys.hasNext()){JSONObject x=pages.getJSONObject(keys.next());String extract=x.optString("extract");out.put(new JSONObject().put("title",x.optString("title")).put("text",extract.substring(0,Math.min(extract.length(),1600))).put("url",x.optString("fullurl")));}}
  return new JSONObject().put("results",out).put("source","Wikipedia • wyszukiwanie encyklopedyczne, bez modelu AI").put("checkedAt",System.currentTimeMillis());
 }
 private synchronized JSONObject update()throws Exception{
  File stage=new File(getFilesDir(),"data-"+System.currentTimeMillis());stage.mkdirs();boolean installed=false;
  try{byte[] zip=read(DATA_URL,80*1024*1024);int count=0;long size=0;try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(zip))){ZipEntry entry;while((entry=in.getNextEntry())!=null){String name=entry.getName();if(!name.matches("(?:index_all|route_all_[0-9]+|manifest|summary)\\.json")||entry.isDirectory())throw new IOException("Nieprawidłowa zawartość paczki rozkładów.");byte[] bytes=bounded(in,24*1024*1024);size+=bytes.length;if(size>180*1024*1024||++count>4000)throw new IOException("Paczka rozkładów jest zbyt duża.");try(FileOutputStream out=new FileOutputStream(new File(stage,name))){out.write(bytes);}}}
   JSONObject manifest=new JSONObject(new String(fileBytes(new File(stage,"manifest.json"),2*1024*1024),StandardCharsets.UTF_8));if(manifest.getInt("schema")!=2)throw new IOException("Niezgodna wersja rozkładów.");JSONArray files=manifest.getJSONArray("files");for(int i=0;i<files.length();i++){JSONObject f=files.getJSONObject(i);String n=f.getString("name");if(!n.matches("(?:index_all|route_all_[0-9]+|summary)\\.json"))throw new IOException("Niedozwolony plik.");byte[] b=fileBytes(new File(stage,n),24*1024*1024);if(b.length!=f.getLong("size")||!hex(MessageDigest.getInstance("SHA-256").digest(b)).equals(f.getString("sha256")))throw new IOException("Uszkodzona paczka danych.");}
   JSONObject idx=new JSONObject(new String(fileBytes(new File(stage,"index_all.json"),24*1024*1024),StandardCharsets.UTF_8));SimpleDateFormat day=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);day.setTimeZone(TimeZone.getTimeZone("Europe/Warsaw"));String today=day.format(new Date());JSONArray days=idx.getJSONArray("days"),covered=idx.getJSONArray("covered");boolean current=false;for(int i=0;i<days.length();i++)if(today.equals(days.getString(i))&&covered.getBoolean(i))current=true;if(!current)throw new IOException("Paczka nie zawiera potwierdzonych kursów na dziś. Zachowano dotychczasową bazę.");
   String previous=PADataStore.preferences(this).getString("data_folder","");if(!PADataStore.preferences(this).edit().putString("data_folder",stage.getName()).commit())throw new IOException("Nie udało się zapisać nowej bazy.");active=stage;installed=true;
   // Keep previous generation until next process start; in-flight readers remain valid.
   return new JSONObject().put("generated",idx.getString("generated")).put("saved",true);
  }finally{if(!installed)remove(stage);}
 }
 private static void remove(File f){if(f.isDirectory())for(File c:f.listFiles())remove(c);f.delete();}
 private static byte[] fileBytes(File f,int max)throws Exception{try(InputStream in=new FileInputStream(f)){return bounded(in,max);}}
 private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();}
 private static String enc(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}
 private static boolean hostOK(String host){return Arrays.asList("tile.openstreetmap.org","geocoding-api.open-meteo.com","api.open-meteo.com","pl.wikipedia.org","raw.githubusercontent.com","github.com","release-assets.githubusercontent.com","objects.githubusercontent.com","mkuran.pl","gtfs.ztp.krakow.pl").contains(host);}
 static byte[] read(String address,int max)throws Exception{
  String current=address;for(int redirect=0;redirect<5;redirect++){URL u=new URL(current);if(!"https".equals(u.getProtocol())||!hostOK(u.getHost())||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443))throw new IOException("Niedozwolone źródło danych.");HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(25000);c.setRequestProperty("User-Agent","PomocnikAlfa/2.0 (+https://github.com/senikolo/Pomocnik-Alfa)");try{int code=c.getResponseCode();if(code>=300&&code<400){current=new URL(u,c.getHeaderField("Location")).toString();continue;}if(code!=200)throw new IOException(code==404?"Usługa lub paczka danych nie jest jeszcze dostępna. Zachowano dane zapisane w aplikacji.":"Źródło jest niedostępne (HTTP "+code+"). Spróbuj później.");if(c.getContentLengthLong()>max)throw new IOException("Odpowiedź źródła jest zbyt duża.");try(InputStream in=c.getInputStream()){return bounded(in,max);}}finally{c.disconnect();}}throw new IOException("Zbyt wiele przekierowań.");
 }
 static byte[] bounded(InputStream in,int max)throws Exception{if(in==null)throw new IOException("Pusta odpowiedź źródła.");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[16384];int n,total=0;while((n=in.read(buffer))!=-1){total+=n;if(total>max)throw new IOException("Przekroczono limit odpowiedzi źródła.");out.write(buffer,0,n);}return out.toByteArray();}
 @Override public void onBackPressed(){if(web!=null){web.evaluateJavascript("window.PABack?window.PABack():false",null);}else super.onBackPressed();}
 @Override protected void onPause(){super.onPause();if(web!=null)web.evaluateJavascript("window.PAVisibility&&window.PAVisibility(false)",null);}
 @Override protected void onResume(){super.onResume();if(web!=null)web.evaluateJavascript("window.PAVisibility&&window.PAVisibility(true)",null);}
 @Override protected void onDestroy(){destroyed=true;jobs.shutdownNow();if(web!=null){web.removeJavascriptInterface("PADevice");web.stopLoading();web.destroy();web=null;}super.onDestroy();}
}
