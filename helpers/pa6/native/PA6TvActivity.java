package com.ispina.lokalnie;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** All live TV plays in our own LibVLC screen, never launches VLC. */
public final class PA6TvActivity extends Activity {
 private PA6EmbeddedPlayer player;
 private TextView info;
 private EditText custom;
 private LinearLayout root;
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
 private TextView text(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(0xfff2f4fe);v.setPadding(dp(9),dp(8),dp(9),dp(8));return v;}
 private Button button(String title,Runnable action){
  Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setTextSize(16);b.setMinHeight(dp(56));
  b.setOnClickListener(v->action.run());root.addView(b,new LinearLayout.LayoutParams(-1,dp(56)));return b;
 }
 private void launch(String name,String url){
  if(!url.startsWith("https://")||url.contains(" ")||url.length()>2000){info.setText("Podaj poprawny adres HTTPS.");return;}
  player.play(name,url);
  getPreferences(0).edit().putString("last_name",name).putString("last_url",url).apply();
  info.setText("Oglądasz w Pomocniku Alfa: "+name);
 }
 @Override public void onCreate(Bundle state){
  super.onCreate(state);getWindow().setStatusBarColor(0xff101b31);
  LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setPadding(dp(12),dp(9),dp(12),dp(5));page.setBackgroundColor(0xff101b31);
  setContentView(page);
  page.addView(text("📺  ALFA TV",24));
  page.addView(text("Transmisje odtwarzane w aplikacji • bez VLC i bez przeglądarki",13));
  player=new PA6EmbeddedPlayer(this);
  page.addView(player,new LinearLayout.LayoutParams(-1,-2));
  info=text("Wybierz kanał. Obraz może ładować się kilka sekund.",14);page.addView(info);
  ScrollView list=new ScrollView(this);page.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  root=new LinearLayout(this);root.setOrientation(1);list.addView(root);
  button("▶  Ostatnio oglądany kanał",()->{
    String url=getPreferences(0).getString("last_url","");
    if(url.isEmpty())info.setText("Nie masz jeszcze ostatnio oglądanego kanału.");
    else launch(getPreferences(0).getString("last_name","Kanał"),url);
  });
  root.addView(text("Kanały internetowe",19));
  button("▶  World Fashion Channel",()->launch("World Fashion","https://cdn.wfc.tv/cdn/stream-rus.m3u8"));
  button("▶  NTV",()->launch("NTV","https://cdn.ntv.ru/vitrina/index.m3u8"));
  button("▶  Rossija 24",()->launch("Rossija 24","https://live.smotrim.ru/vgtrk/0/russia24-hd/index.m3u8"));
  root.addView(text("Własna transmisja HLS / MP4",18));
  custom=new EditText(this);custom.setSingleLine(true);custom.setHint("https://.../stream.m3u8");custom.setTextColor(Color.WHITE);custom.setHintTextColor(0xff9fadc8);
  root.addView(custom,new LinearLayout.LayoutParams(-1,dp(58)));
  button("▶  Odtwórz wpisany adres",()->launch("Własny kanał",custom.getText().toString().trim()));
  root.addView(text("Kanały z DRM lub ograniczeniami dostępu mogą wymagać technologii, których tablet z Androidem 6 nie obsługuje. Nie otwieramy nic poza PA.",13));
 }
 @Override protected void onDestroy(){if(player!=null)player.release();super.onDestroy();}
}
