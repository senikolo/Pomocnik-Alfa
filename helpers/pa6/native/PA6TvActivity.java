package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

/**
 * ALFA TV: always in-app 16:9 LibVLC window, live channels and favorites.
 * Only freely available public broadcaster/community streams are listed.
 * No paid, tokenized, DRM or unauthorized retransmissions.
 */
public final class PA6TvActivity extends Activity {
 private static final int WHITE=0xfff8fafc,INK=0xff182135,MUTED=0xff596675,RED=0xffea283b;
 private static final class Channel {
  final String name,kind,desc,url;
  Channel(String n,String k,String d,String u){name=n;kind=k;desc=d;url=u;}
 }
 private static final Channel[] CHANNELS={
  new Channel("Euronews Polska","Wiadomości","Informacje międzynarodowe • PL","https://cdn-euronews.akamaized.net/live/eds/euronews-pl/26382/index.m3u8"),
  new Channel("Echo24","Wiadomości","Wiadomości regionalne","https://echo24new.pl/LiveAppStreamECHO24/streams/GL0VksiIgQUS1672825288490.m3u8"),
  new Channel("BTB Info","Wiadomości","Regionalny kanał informacyjny","https://emisja2.btb.j00r.us/iptv/session/4/hls.m3u8"),
  new Channel("W Polsce PL","Wiadomości","Publiczna transmisja internetowa","https://str.onnetwork.tv/hls/onnet-358726_hi/index.m3u8"),
  new Channel("4 Fun TV","Muzyka","Teledyski • muzyka","https://stream.4fun.tv:8888/hls/4f.m3u8"),
  new Channel("4 Fun TV HD","Muzyka","Kanał muzyczny • wyższa jakość","https://stream.4fun.tv:8888/hls/4f_high/index.m3u8"),
  new Channel("4 Fun Kids","Muzyka","Teledyski i piosenki","https://stream.4fun.tv:8889/hls/4fk.m3u8"),
  new Channel("Radio Czwórka TV","Muzyka","Studio Polskiego Radia • obraz","https://stream14.polskieradio.pl/pr4_video/video_pr4.stream/playlist.m3u8"),
  new Channel("Radio RAM TV","Muzyka","Studio radiowe wideo","https://1888165924.rsc.cdn77.org/live/RadioRAM/playlist.m3u8"),
  new Channel("Red Carpet TV","Rozrywka","Filmy, kultura, gwiazdy","https://fast-rakuten.okast.tv/fa2e8c4385712f9a9076eaf728c751de/channels/1a6ecfaa-40c6-41b8-8634-595c424f856e/21f67fd1-4d21-43ba-8ad8-3afac9982c25/master.m3u8"),
  new Channel("World Fashion","Rozrywka","Moda • pokazy","https://cdn.wfc.tv/cdn/stream-rus.m3u8"),
  new Channel("Remonty TV","Rozrywka","Dom i wnętrza","https://prx-y5cf8.4vod.tv/playout/repl-abr67/index.m3u8"),
  new Channel("Telewizja Biznesowa","Rozrywka","Biznes i przedsiębiorczość","https://s-pl-01.mediatool.tv/playout/tbpl-abr/index.m3u8"),
  new Channel("ATM Rozrywka REWORK","Rozrywka","Internetowy kanał rozrywkowy","https://grupaatmrr.org.pl:8888/fallback/index.m3u8"),
  new Channel("Puls 2","Rozrywka","Otwarty przekaz HbbTV • może wymagać dostępu","https://edge01.cdn.emitel.pl/bpk-tv/Puls2_HD/hbbtv-hls/index.m3u8"),
  new Channel("BTB TV","Regiony","Regionalny kanał","https://emisja.btb.j00r.us/iptv/session/1/hls.m3u8"),
  new Channel("BTB 2","Regiony","Regionalny kanał","https://emisja.btb.j00r.us/iptv/session/2/hls.m3u8"),
  new Channel("BTB 3","Regiony","Regionalny kanał","https://emisja3.btb.j00r.us/iptv/session/3/hls.m3u8"),
  new Channel("TV Jasło","Regiony","Telewizja regionalna","https://cdn01.aztv.pl/live_tvjaslo/live_tvjaslo.stream/playlist.m3u8"),
  new Channel("Sfera TV","Regiony","Telewizja lokalna • nie zawsze 24/7","https://stream.sferatv.pl/sferatvlive/live.m3u8"),
  new Channel("TV ASTA","Regiony","Informacje z regionu","https://5cce8c6f0e84c.streamlock.net/LiveCast/smil:PlayStream9.smil/playlist.m3u8"),
  new Channel("TVT","Regiony","Telewizja regionalna","https://live.streamtvt.pl/LiveAppStreamTVT/streams/853271271313930867905724.m3u8"),
  new Channel("TVK Stella","Regiony","Telewizja lokalna","https://stream.tvkstella.pl/stella/stella.m3u8"),
  new Channel("Twoja.TV","Regiony","Telewizja internetowa","https://stream.twoja.tv/ttv_hls/live_720p/index.m3u8"),
  new Channel("Telewizja Relax","Regiony","Telewizja lokalna","https://srv1.streamingo.pl/memfs/be50f17e-6579-4bdf-86b1-31207aec85b8.m3u8")
 };
 private PA6EmbeddedPlayer player;
 private TextView status,active,listingCount;
 private LinearLayout results;
 private EditText search,custom;
 private String filter="Wszystkie",onAir="";
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
 private GradientDrawable box(int bg,int outline,int radius){
  GradientDrawable d=new GradientDrawable();d.setColor(bg);d.setCornerRadius(dp(radius));d.setStroke(dp(1),outline);return d;
 }
 private TextView text(String s,int sp,int col,boolean bold){
  TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(col);
  if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
 }
 private Button action(String value,boolean red){
  Button b=new Button(this);b.setAllCaps(false);b.setText(value);b.setTextSize(14);
  b.setTextColor(red?Color.WHITE:INK);b.setBackground(box(red?RED:WHITE,red?RED:0xffdde3eb,12));
  return b;
 }
 private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 private void space(LinearLayout l,int h){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
 private String favorites(){return getPreferences(0).getString("fav_tv","|");}
 private boolean favorite(String url){return favorites().contains("|"+url+"|");}
 private void toggle(Channel channel){
  String pref=favorites();
  String mark="|"+channel.url+"|";
  if(pref.contains(mark))pref=pref.replace(mark,"|");
  else pref+=channel.url+"|";
  getPreferences(0).edit().putString("fav_tv",pref).apply();
  showChannels();
 }
 private void play(Channel ch){
  onAir=ch.name;player.play(ch.name,ch.url);
  active.setText("▶  "+ch.name);
  status.setText("Odtwarzanie wewnątrz PA • "+ch.desc);
  getPreferences(0).edit().putString("tv_last",ch.url).putString("tv_name",ch.name).apply();
 }
 private void replay(){
  String last=getPreferences(0).getString("tv_last","");
  String name=getPreferences(0).getString("tv_name","Kanał");
  if(last.isEmpty())status.setText("Nie oglądano jeszcze żadnego kanału.");
  else play(new Channel(name,"Ostatnie","Ostatnio oglądany",last));
 }
 private void showChannels(){
  if(results==null)return;
  results.removeAllViews();
  String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);
  int found=0;
  for(Channel ch:CHANNELS){
   if(filter.equals("★")&&!favorite(ch.url))continue;
   if(!filter.equals("Wszystkie")&&!filter.equals("★")&&!filter.equals(ch.kind))continue;
   if(!query.isEmpty()&&!((ch.name+" "+ch.desc+" "+ch.kind).toLowerCase(Locale.ROOT).contains(query)))continue;
   found++;channelRow(ch);
  }
  listingCount.setText(found+" dostępnych pozycji katalogu • odbiór zależy od nadawcy");
  if(found==0){
   TextView none=text("Brak pozycji. Zmień kategorię lub wyczyść wyszukiwanie.",14,MUTED,false);
   none.setPadding(dp(11),dp(20),dp(11),dp(20));results.addView(none);
  }
 }
 private void channelRow(Channel channel){
  LinearLayout l=row();l.setPadding(dp(11),dp(10),dp(9),dp(10));
  l.setBackground(box(WHITE,0xffe0e6ee,15));
  TextView logo=text(channel.name.substring(0,Math.min(2,channel.name.length())).toUpperCase(Locale.ROOT),16,RED,true);
  logo.setGravity(Gravity.CENTER);logo.setBackground(box(0xffffeef0,0xffffdee2,12));
  l.addView(logo,new LinearLayout.LayoutParams(dp(57),dp(57)));
  LinearLayout texts=col();texts.setPadding(dp(11),0,0,0);
  TextView n=text(channel.name,16,INK,true);texts.addView(n);
  TextView label=text(channel.kind+" • "+channel.desc,12,MUTED,false);label.setMaxLines(2);texts.addView(label);
  l.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
  Button star=action(favorite(channel.url)?"★":"☆",false);
  star.setTextColor(favorite(channel.url)?RED:MUTED);
  l.addView(star,new LinearLayout.LayoutParams(dp(50),dp(45)));
  star.setOnClickListener(v->toggle(channel));
  l.setContentDescription(channel.name+" • "+channel.kind);
  l.setOnClickListener(v->play(channel));
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(81));p.setMargins(0,dp(4),0,dp(5));results.addView(l,p);
 }
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);getWindow().setStatusBarColor(WHITE);getWindow().setNavigationBarColor(INK);
  getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
  LinearLayout page=col();page.setBackgroundColor(WHITE);
  page.setPadding(dp(12),dp(10),dp(12),dp(5));setContentView(page);
  LinearLayout header=row();
  TextView red=text("●",28,RED,true);header.addView(red);
  TextView head=text("  ALFA TV",23,INK,true);header.addView(head,new LinearLayout.LayoutParams(0,-2,1));
  TextView live=text("NA ŻYWO",12,RED,true);header.addView(live);
  page.addView(header);space(page,6);
  player=new PA6EmbeddedPlayer(this);
  page.addView(player,new LinearLayout.LayoutParams(-1,-2));
  if(getIntent().getBooleanExtra("verify_libvlc",false)&&!player.verifyNative())
   throw new IllegalStateException("PA6 native player unavailable");
  active=text("Wybierz kanał z listy",17,INK,true);
  active.setPadding(dp(6),dp(6),dp(6),dp(4));page.addView(active);
  status=text("Odtwarzacz w okienku • obraz i dźwięk wewnątrz Pomocnika Alfa.",13,MUTED,false);
  status.setPadding(dp(6),dp(2),dp(6),dp(8));page.addView(status);
  LinearLayout tools=row();
  Button last=action("↻  Ostatnio",false);tools.addView(last,new LinearLayout.LayoutParams(0,dp(46),1));last.setOnClickListener(v->replay());
  Button stop=action("■  Stop",false);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(46),1);
  sp.setMargins(dp(7),0,0,0);tools.addView(stop,sp);stop.setOnClickListener(v->player.stop());
  page.addView(tools);space(page,10);
  ScrollView scroll=new ScrollView(this);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout content=col();scroll.addView(content);
  TextView sect=text("Kanały telewizyjne",20,INK,true);
  sect.setPadding(0,dp(11),0,dp(7));content.addView(sect);
  listingCount=text("Otwarty katalog TV",12,MUTED,false);content.addView(listingCount);
  search=new EditText(this);search.setSingleLine(true);search.setTextSize(15);
  search.setHint("Szukaj kanału…");search.setBackground(box(0xffedf0f5,0xffdfe4eb,12));
  search.setPadding(dp(12),dp(3),dp(12),dp(3));
  content.addView(search,new LinearLayout.LayoutParams(-1,dp(49)));
  search.addTextChangedListener(new android.text.TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){showChannels();}
   public void afterTextChanged(android.text.Editable e){}
  });
  space(content,9);
  LinearLayout tabs=row();
  for(String label:new String[]{"Wszystkie","Wiadomości","Muzyka","Regiony","Rozrywka","★"}){
   Button tab=action(label.equals("★")?"★":label,false);tab.setTextSize(11);
   LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);p.setMargins(dp(1),0,dp(1),0);tabs.addView(tab,p);
   tab.setOnClickListener(v->{filter=label;showChannels();status.setText("Kategoria: "+(label.equals("★")?"Ulubione":label));});
  }
  content.addView(tabs);space(content,8);
  results=col();content.addView(results);
  space(content,15);
  TextView own=text("Własny kanał",18,INK,true);content.addView(own);space(content,7);
  custom=new EditText(this);custom.setSingleLine(true);custom.setTextSize(13);custom.setHint("https://…/stream.m3u8");
  custom.setBackground(box(0xffedf0f5,0xffdfe4eb,12));custom.setPadding(dp(12),0,dp(12),0);
  content.addView(custom,new LinearLayout.LayoutParams(-1,dp(49)));
  Button add=action("▶  Odtwórz własny adres",true);content.addView(add,new LinearLayout.LayoutParams(-1,dp(52)));
  add.setOnClickListener(v->{
   String url=custom.getText().toString().trim();
   if(!url.startsWith("https://")||url.length()>2500||url.contains(" ")){status.setText("Wpisz prawidłowy adres HTTPS.");return;}
   play(new Channel("Własny kanał","Własne","Strumień dodany ręcznie",url));
  });
  space(content,9);
  Button demo=action("▶  Test wideo H.264 (materiał demonstracyjny)",false);
  content.addView(demo,new LinearLayout.LayoutParams(-1,dp(53)));
  demo.setOnClickListener(v->play(new Channel("Film kontrolny H.264","Test","Test odtwarzacza (nie kanał telewizji)",
   "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")));
  space(content,12);
  TextView disclaimer=text("Lista zawiera otwarte kanały internetowe i przekazy regionalne. Niektóre mogą być niedostępne poza regionem lub zmieniać adres. Nie oferujemy płatnych kanałów ani obchodzenia DRM.",12,MUTED,false);
  disclaimer.setPadding(0,dp(10),0,dp(20));content.addView(disclaimer);
  page.setFocusableInTouchMode(true);page.requestFocus();
  showChannels();
 }
 @Override protected void onDestroy(){if(player!=null)player.release();super.onDestroy();}
}
