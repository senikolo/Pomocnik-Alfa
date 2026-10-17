package com.ispina.lokalnie;

import android.app.*;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Simple non-tracking weather view backed by Open-Meteo. */
public final class PA6WeatherActivity extends Activity {
 private LinearLayout details;
 private EditText city;
 private TextView info;
 private int request=0;
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
 private TextView label(String text,int size){
  TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(0xff463365);t.setPadding(dp(5),dp(7),dp(5),dp(7));return t;
 }
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  ScrollView sc=new ScrollView(this);sc.setBackgroundColor(0xfff7f4fa);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(15),dp(13),dp(15),dp(26));sc.addView(root);setContentView(sc);
  root.addView(label("🌤  Pogoda z Alfą",25));
  root.addView(label("Podaj miejscowość. Nie zakładam, gdzie jesteś; lokalizacja GPS nie jest wymagana.",15));
  city=new EditText(this);city.setSingleLine(true);city.setHint("Np. Warszawa, Kraków, Ispina");
  city.setText(getPreferences(0).getString("city",""));
  root.addView(city,new LinearLayout.LayoutParams(-1,dp(57)));
  Button go=new Button(this);go.setText("Pokaż prognozę");go.setTextColor(0xff634299);
  go.setAllCaps(false);go.setTextSize(17);root.addView(go,new LinearLayout.LayoutParams(-1,dp(56)));
  go.setOnClickListener(v->lookup(city.getText().toString().trim()));
  info=label("Prognoza 3 dni • Open-Meteo",14);root.addView(info);
  details=new LinearLayout(this);details.setOrientation(1);root.addView(details);
  String previous=city.getText().toString().trim();if(!previous.isEmpty())lookup(previous);
 }
 private static String load(String address)throws Exception{
  HttpURLConnection c=null;
  try{
   c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(16000);
   c.setRequestProperty("User-Agent","PA6Tablet/1.0");
   if(c.getResponseCode()!=200)throw new IOException("Serwis pogody nie odpowiedział ("+c.getResponseCode()+")");
   ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){
    byte[] buf=new byte[4096];int n,total=0;while((n=in.read(buf))>0){total+=n;if(total>300000)throw new IOException("Prognoza jest zbyt duża");out.write(buf,0,n);}
   }
   return out.toString("UTF-8");
  }finally{if(c!=null)c.disconnect();}
 }
 private String icon(int code){
  if(code==0)return "☀️";if(code<=3)return "⛅";if(code<=48)return "🌫";
  if(code<=67)return "🌧";if(code<=77)return "❄";if(code<=82)return "🌦";if(code<=86)return "🌨";return "⛈";
 }
 private void lookup(String place){
  if(place.isEmpty()){info.setText("Najpierw podaj miejscowość.");return;}
  if(place.length()>100){info.setText("Nazwa miejscowości jest zbyt długa.");return;}
  getPreferences(0).edit().putString("city",place).apply();
  int id=++request;info.setText("Pobieram prognozę dla: "+place+"…");details.removeAllViews();
  new Thread(()->{
   String error=null,cityName="";ArrayList<String> forecast=new ArrayList<>();
   try{
    JSONObject g=new JSONObject(load("https://geocoding-api.open-meteo.com/v1/search?name="+URLEncoder.encode(place,"UTF-8")+"&count=1&language=pl&format=json&countryCode=PL"));
    JSONArray loc=g.optJSONArray("results");
    if(loc==null||loc.length()==0)throw new IOException("Nie znaleziono miejscowości. Spróbuj większego miasta lub pobliskiej miejscowości.");
    JSONObject found=loc.getJSONObject(0);cityName=found.optString("name",place)+" • "+found.optString("admin1","");
    String lat=String.valueOf(found.getDouble("latitude")),lon=String.valueOf(found.getDouble("longitude"));
    JSONObject w=new JSONObject(load("https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon+"&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&current=temperature_2m,wind_speed_10m&timezone=Europe%2FWarsaw&forecast_days=3"));
    JSONObject current=w.getJSONObject("current");
    forecast.add("Teraz: "+current.optString("temperature_2m","–")+" °C • Wiatr "+current.optString("wind_speed_10m","–")+" km/h");
    JSONObject daily=w.getJSONObject("daily");JSONArray days=daily.getJSONArray("time"),codes=daily.getJSONArray("weather_code"),max=daily.getJSONArray("temperature_2m_max"),min=daily.getJSONArray("temperature_2m_min"),rain=daily.getJSONArray("precipitation_probability_max");
    for(int i=0;i<days.length();i++)forecast.add(icon(codes.optInt(i))+"  "+days.optString(i)+"\n"+min.optString(i)+"–"+max.optString(i)+" °C  •  prawdopodobieństwo opadów "+rain.optString(i)+"%");
   }catch(Exception e){error=e.getMessage();}
   final String message=error,foundCity=cityName;
   runOnUiThread(()->{
    if(isFinishing()||id!=request)return;
    if(message!=null){info.setText(message);return;}
    info.setText("Prognoza: "+foundCity+" • Open-Meteo");
    for(String row:forecast){
     TextView t=label(row,18);t.setBackgroundColor(Color.WHITE);
     LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));
     details.addView(t,p);
    }
    details.addView(label("Źródło danych: Open-Meteo (CC BY 4.0). Prognoza może się zmieniać.",12));
   });
  }).start();
 }
 @Override protected void onDestroy(){request++;super.onDestroy();}
}