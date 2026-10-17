package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Android 6 native radio directory and player, with foreground service. */
public final class PA6RadioActivity extends Activity {
 private LinearLayout list;
 private TextView state;
 private EditText search;
 private int serial;
 private int violet=Color.rgb(92,61,143);
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
 private TextView title(String s,int size){
  TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(0xff30243f);t.setPadding(dp(10),dp(8),dp(10),dp(8));return t;
 }
 private Button button(String s){
  Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(16);b.setMinHeight(dp(55));b.setTextColor(violet);return b;
 }
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xfff6f3fa);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(16),dp(12),dp(16),dp(20));
  scroll.addView(root);setContentView(scroll);
  TextView head=title("📻  Radio • Pomocnik Alfa",24);head.setTypeface(null,1);root.addView(head);
  root.addView(title("Radio gra w tle. Do zatrzymania służy przycisk poniżej albo powiadomienie.",14));
  Button stop=button("■  Zatrzymaj radio");root.addView(stop);
  stop.setOnClickListener(v->startService(new Intent(this,PA6RadioService.class).setAction(PA6RadioService.ACTION_STOP)));
  search=new EditText(this);search.setSingleLine(true);search.setHint("Wyszukaj stację, np. Trójka, RMF, TOK FM");
  search.setInputType(InputType.TYPE_CLASS_TEXT);root.addView(search,new LinearLayout.LayoutParams(-1,dp(55)));
  Button find=button("Szukaj stacji");root.addView(find);
  find.setOnClickListener(v->load(search.getText().toString().trim()));
  state=title("Ładuję popularne stacje z Polski…",14);root.addView(state);
  list=new LinearLayout(this);list.setOrientation(1);root.addView(list);
  load("");
 }
 private void load(String query){
  int task=++serial;state.setText("Szukam stacji…");list.removeAllViews();
  new Thread(()->{
   ArrayList<String[]> stations=new ArrayList<>();String error=null;
   HttpURLConnection c=null;
   try{
    String params=query.isEmpty()?"countrycode=PL":"name="+URLEncoder.encode(query,"UTF-8");
    URL u=new URL("https://de1.api.radio-browser.info/json/stations/search?"+params+"&hidebroken=true&is_https=true&order=clickcount&reverse=true&limit=70");
    c=(HttpURLConnection)u.openConnection();c.setConnectTimeout(12000);c.setReadTimeout(16000);
    c.setRequestProperty("User-Agent","PomocnikAlfaTablet/2.1 Android6 (open radio directory)");
    if(c.getResponseCode()!=200)throw new IOException("Katalog radia jest niedostępny: "+c.getResponseCode());
    ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){
     byte[] buf=new byte[8192];int count,total=0;
     while((count=in.read(buf))!=-1){total+=count;if(total>1024*1024)throw new IOException("Lista stacji jest zbyt długa");out.write(buf,0,count);}
    }
    JSONArray rows=new JSONArray(out.toString("UTF-8"));
    for(int n=0;n<rows.length()&&stations.size()<35;n++){
     JSONObject item=rows.optJSONObject(n);if(item==null)continue;
     String url=item.optString("url_resolved",item.optString("url"));
     String name=item.optString("name","Radio").trim();
     String codec=item.optString("codec").toUpperCase(Locale.ROOT);
     if(!url.startsWith("https://")||url.length()>2000||name.length()<2||!(codec.contains("MP3")||codec.contains("AAC")))continue;
     stations.add(new String[]{name,url,codec});
    }
   }catch(Exception e){error=e.getMessage();}finally{if(c!=null)c.disconnect();}
   final String msg=error;runOnUiThread(()->{
    if(isFinishing()||task!=serial)return;
    state.setText(msg!=null?"Nie udało się pobrać stacji: "+msg:stations.isEmpty()?"Brak kompatybilnych stacji. Spróbuj innego hasła.":"Wybierz stację • "+stations.size()+" wyników");
    for(String[] row:stations){
     Button play=button("▶  "+row[0]);
     play.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
     list.addView(play,new LinearLayout.LayoutParams(-1,dp(60)));
     play.setOnClickListener(v->{
      state.setText("Uruchamiam: "+row[0]);
      Intent intent=new Intent(this,PA6RadioService.class).setAction(PA6RadioService.ACTION_PLAY);
      intent.putExtra("url",row[1]);intent.putExtra("name",row[0]);startService(intent);
     });
    }
   });
  }).start();
 }
 @Override protected void onDestroy(){serial++;super.onDestroy();}
}
