package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Android 6 native HLS/MP4 TV screen; only public broadcaster streams. */
public final class PA6TvActivity extends Activity {
 private VideoView video;
 private TextView status;
 private EditText custom;
 private int violet=0xff593d89;
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
 private Button action(String label){Button b=new Button(this);b.setText(label);b.setTextSize(15);b.setAllCaps(false);b.setTextColor(violet);b.setMinHeight(dp(53));return b;}
 private TextView label(String text,int size){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(0xff30243f);t.setPadding(dp(6),dp(7),dp(6),dp(7));return t;}
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  ScrollView page=new ScrollView(this);page.setFillViewport(true);page.setBackgroundColor(0xfff7f3fa);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(15),dp(10),dp(15),dp(24));page.addView(root);setContentView(page);
  TextView head=label("📺  Telewizja • Pomocnik Alfa",24);head.setTypeface(null,1);root.addView(head);
  root.addView(label("Odtwarzacz Android 6: tylko transmisje dostępne bez DRM i obsługiwane przez kodeki tabletu.",14));
  video=new VideoView(this);video.setBackgroundColor(Color.BLACK);
  root.addView(video,new LinearLayout.LayoutParams(-1,dp(245)));
  MediaController mc=new MediaController(this);mc.setAnchorView(video);video.setMediaController(mc);
  status=label("Wybierz kanał lub wklej legalny adres transmisji.",14);root.addView(status);
  video.setOnPreparedListener(p->{status.setText("Odtwarzanie rozpoczęte");p.setOnInfoListener((mp,what,extra)->false);});
  video.setOnErrorListener((p,what,extra)->{status.setText("Ten strumień jest niedostępny lub nieobsługiwany przez Androida 6. Wypróbuj inny kanał.");return true;});
  Button stop=action("■  Zatrzymaj obraz");root.addView(stop);stop.setOnClickListener(v->{video.stopPlayback();status.setText("Zatrzymano telewizję.");});
  Button tvp=action("TVP Info • oficjalna transmisja w przeglądarce");root.addView(tvp);
  tvp.setOnClickListener(v->openOfficial("https://www.tvp.info/na-zywo"));
  Button world=action("▶  World Fashion Channel");root.addView(world);
  world.setOnClickListener(v->play("World Fashion","https://cdn.wfc.tv/cdn/stream-rus.m3u8"));
  Button ntv=action("▶  NTV");root.addView(ntv);
  ntv.setOnClickListener(v->play("NTV","https://cdn.ntv.ru/vitrina/index.m3u8"));
  Button r24=action("▶  Rossija 24");root.addView(r24);
  r24.setOnClickListener(v->play("Rossija 24","https://live.smotrim.ru/vgtrk/0/russia24-hd/index.m3u8"));
  root.addView(label("Własny adres transmisji (HTTPS, HLS .m3u8 lub MP4):",14));
  custom=new EditText(this);custom.setSingleLine(true);custom.setHint("https://.../playlist.m3u8");
  root.addView(custom,new LinearLayout.LayoutParams(-1,dp(57)));
  Button go=action("▶  Odtwórz adres");root.addView(go);
  go.setOnClickListener(v->play("Podana transmisja",custom.getText().toString().trim()));
  root.addView(label("Niektóre kanały mogą być niedostępne ze względów geograficznych, zmian adresu, certyfikatu TLS lub DRM. Dostępność zależy od nadawcy.",13));
 }
 private void play(String name,String url){
  if(!url.startsWith("https://")||url.length()>2000||url.contains(" ")||url.contains("\n")){
   status.setText("Podaj prawidłowy adres transmisji HTTPS.");return;
  }
  video.stopPlayback();
  status.setText("Łączenie z kanałem "+name+"…");
  try{video.setVideoURI(Uri.parse(url));video.requestFocus();video.start();}
  catch(Exception e){status.setText("Nie udało się uruchomić odtwarzania na tym tablecie.");}
 }
 private void openOfficial(String address){
  try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(address)));}
  catch(Exception e){status.setText("Na tablecie nie znaleziono przeglądarki do otwarcia TVP Info.");}
 }
 @Override protected void onPause(){if(video!=null)video.pause();super.onPause();}
 @Override protected void onDestroy(){if(video!=null)video.stopPlayback();super.onDestroy();}
}
