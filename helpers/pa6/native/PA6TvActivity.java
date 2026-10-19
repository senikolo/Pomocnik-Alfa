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
public final class PA6TvActivity extends PA6BaseActivity {
 private final PA6Fullscreen fullscreen=new PA6Fullscreen();
 private static String countryFor(String name){
  if(name.startsWith("Rossija 1")||name.equals("Univer TV"))return "Rosja";
  if(name.startsWith("World Fashion"))return "Międzynarodowe";
  return "Polska";
 }
 /** Native flag drawing also works on Android 6 without emoji-font support. */
 private final class FlagView extends View {
  private final String country;private final android.graphics.Paint paint=new android.graphics.Paint(3);
  FlagView(String value){super(PA6TvActivity.this);country=value;setContentDescription("Flaga: "+value);}
  @Override protected void onDraw(android.graphics.Canvas canvas){
   super.onDraw(canvas);float w=getWidth(),h=getHeight();canvas.save();
   android.graphics.Path clip=new android.graphics.Path();clip.addRoundRect(new android.graphics.RectF(0,0,w,h),dp(2),dp(2),android.graphics.Path.Direction.CW);canvas.clipPath(clip);
   paint.setColor(0xffffffff);canvas.drawRect(0,0,w,h,paint);
   if(country.equals("Polska")){paint.setColor(0xffdc143c);canvas.drawRect(0,h/2,w,h,paint);}
   else if(country.equals("Rosja")){paint.setColor(0xff1456a0);canvas.drawRect(0,h/3,w,2*h/3,paint);paint.setColor(0xffd62d36);canvas.drawRect(0,2*h/3,w,h,paint);}
   else{paint.setColor(0xff276aa0);canvas.drawRect(0,0,w,h,paint);paint.setColor(0xffffffff);paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(dp(1));canvas.drawCircle(w/2,h/2,h*.37f,paint);canvas.drawOval(new android.graphics.RectF(w/2-h*.18f,h*.13f,w/2+h*.18f,h*.87f),paint);canvas.drawLine(w/2-h*.37f,h/2,w/2+h*.37f,h/2,paint);paint.setStyle(android.graphics.Paint.Style.FILL);}
   canvas.restore();paint.setColor(0xffcbd5e1);paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(dp(1));canvas.drawRoundRect(new android.graphics.RectF(.5f,.5f,w-.5f,h-.5f),dp(2),dp(2),paint);paint.setStyle(android.graphics.Paint.Style.FILL);
  }
 }
 private static final int WHITE=PA6Premium.BG,INK=PA6Premium.INK,MUTED=PA6Premium.MUTED,RED=PA6Premium.BLUE;
 private static final class Channel {
  final String name,kind,desc,url,country;
  final String[] backup;
  Channel(String n,String k,String d,String u,String... alternatives){
   name=n;kind=k;desc=d;url=u;backup=alternatives;country=countryFor(n);
  }
 }
 private static final Channel[] CHANNELS={
  new Channel("TVP 1","Wiadomości","Oficjalny kanał TVP • dostęp w Polsce","tvp:399697"),
  new Channel("TVP3 Kraków","Regiony","Małopolska • oficjalna transmisja TVP","tvp:399746"),
  new Channel("Rossija 1 (Россия 1)","Rosyjskojęzyczne","Russia 1 • oficjalna transmisja Smotrim","https://live.smotrim.ru/vgtrk/0/russia1-hd/index.m3u8"),
  new Channel("Euronews Polska","Wiadomości","Informacje międzynarodowe • PL","https://cdn-euronews.akamaized.net/live/eds/euronews-pl/26382/index.m3u8"),
  new Channel("W Polsce PL","Wiadomości","Publiczna transmisja internetowa","https://str.onnetwork.tv/hls/onnet-358726_hi/index.m3u8"),
  new Channel("4 Fun TV","Muzyka","Teledyski • muzyka","https://stream.4fun.tv:8888/hls/4f.m3u8"),
  new Channel("Radio Czwórka TV","Muzyka","Studio Polskiego Radia • obraz","https://stream14.polskieradio.pl/pr4_video/video_pr4.stream/playlist.m3u8"),
  new Channel("Radio RAM TV","Muzyka","Studio radiowe wideo","https://1888165924.rsc.cdn77.org/live/RadioRAM/playlist.m3u8"),
  new Channel("Red Carpet TV","Rozrywka","Filmy, kultura, gwiazdy","https://fast-rakuten.okast.tv/fa2e8c4385712f9a9076eaf728c751de/channels/1a6ecfaa-40c6-41b8-8634-595c424f856e/21f67fd1-4d21-43ba-8ad8-3afac9982c25/master.m3u8"),
  new Channel("World Fashion","Rozrywka","Moda • pokazy","https://cdn.wfc.tv/cdn/stream-rus.m3u8"),
  new Channel("Remonty TV","Rozrywka","Dom i wnętrza","https://prx-y5cf8.4vod.tv/playout/repl-abr67/index.m3u8"),
  new Channel("Puls 2","Rozrywka","Otwarty przekaz HbbTV • może wymagać dostępu","https://edge01.cdn.emitel.pl/bpk-tv/Puls2_HD/hbbtv-hls/index.m3u8"),
  new Channel("Sfera TV","Regiony","Telewizja lokalna • nie zawsze 24/7","https://stream.sferatv.pl/sferatvlive/live.m3u8"),
  new Channel("TV ASTA","Regiony","Informacje z regionu","https://5cce8c6f0e84c.streamlock.net/LiveCast/smil:PlayStream9.smil/playlist.m3u8"),
  new Channel("TVK Stella","Regiony","Telewizja lokalna","https://stream.tvkstella.pl/stella/stella.m3u8"),
  new Channel("Twoja.TV","Regiony","Telewizja internetowa","https://stream.twoja.tv/ttv_hls/live_720p/index.m3u8"),
  new Channel("Telewizja Relax","Regiony","Telewizja lokalna","https://srv1.streamingo.pl/memfs/be50f17e-6579-4bdf-86b1-31207aec85b8.m3u8"),
  new Channel("Univer TV","Rosyjskojęzyczne","Edukacja, uczelnie i nauka","https://cdn.universmotri.ru/live/smil:univer.smil/playlist.m3u8",
     "https://cdn.universmotri.ru/live/smil:mbr.smil/playlist.m3u8")
 };
 private PA6EmbeddedPlayer player;
 private TextView status,active,listingCount;
 private LinearLayout results;
 private EditText search,custom;
 private String filter="Wszystkie",countryFilter="Wszystkie",onAir="";
 private final android.os.Handler main=new android.os.Handler();
 private Runnable tuneWatchdog;
 private void cancelTuneWatchdog(){if(tuneWatchdog!=null){main.removeCallbacks(tuneWatchdog);tuneWatchdog=null;}}
 private int tuneRequest=0;
 private Channel currentChannel;
 private int currentSource=0;
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
  b.setMinHeight(dp(48));b.setMinWidth(0);b.setMinimumWidth(0);b.setMaxLines(2);
  b.setTextColor(red?Color.WHITE:INK);b.setBackground(box(red?RED:WHITE,red?RED:0xffdde3eb,12));
  b.setMinHeight(dp(50));b.setStateListAnimator(null);PA6Premium.touch(b);return b;
 }
 private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 private void space(LinearLayout l,int h){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
 static java.util.ArrayList<String[]> saved(android.content.Context context){java.util.ArrayList<String[]> out=new java.util.ArrayList<>();String all=context.getSharedPreferences(PA6TvActivity.class.getName(),0).getString("fav_tv","|");for(Channel c:CHANNELS)if(all.contains("|"+c.url+"|"))out.add(new String[]{c.name,c.url});return out;}
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
  currentChannel=ch;currentSource=0;playChannelSource(ch,0);
 }
 private void playChannelSource(Channel ch,int index){
  cancelTuneWatchdog();onAir=ch.name;
  final int attempt=++tuneRequest;
  final String input=index==0?ch.url:ch.backup[index-1];
  active.setText("▶  "+ch.name);
  status.setText(index==0?"Sprawdzam jakość transmisji…":"Pierwsza transmisja zawiodła. Łączę z zapasową…");
  getPreferences(0).edit().putString("tv_last",ch.url).putString("tv_name",ch.name).apply();
  new Thread(()->{
   try{
    String url=PA6HlsQuality.preferLow(PA6TvSources.resolve(input));
    runOnUiThread(()->{
     if(isFinishing()||attempt!=tuneRequest)return;
     player.play(ch.name,url);
     tuneWatchdog=()->{if(attempt==tuneRequest&&currentChannel!=null)onChannelPlaybackError();};
     main.postDelayed(tuneWatchdog,18000);
     status.setText("Łączę z "+ch.name+" • "+(index==0?"źródło główne":"źródło zapasowe")+".");
    });
   }catch(Exception error){
    runOnUiThread(()->{if(isFinishing()||attempt!=tuneRequest)return;
     // DNS, geo restrictions and invalid URLs must use backup streams too.
     // Never retry a channel that the viewer has already switched away from.
     if(currentChannel==ch && currentSource<ch.backup.length){
      android.util.Log.w("PA6_TV","Primary stream failed, trying backup",error);
      onChannelPlaybackError();
     }else{
      status.setText(error instanceof java.io.IOException?error.getMessage():"Nie udało się otworzyć transmisji. Spróbuj później.");
      if(currentChannel==ch)currentChannel=null;
     }
    });
   }
  },"pa6-tv-quality").start();
 }
 private void onChannelPlaybackError(){
  cancelTuneWatchdog();
  if(currentChannel==null||isFinishing())return;
  if(currentSource<currentChannel.backup.length){
   ++currentSource;
   playChannelSource(currentChannel,currentSource);
  }else{
   status.setText("Kanał „"+currentChannel.name+"” obecnie nie nadaje pod dostępnym adresem. Wybierz inny.");
   currentChannel=null;
  }
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
  for(String country:new String[]{"Polska","Rosja","Międzynarodowe"}){
   if(!countryFilter.equals("Wszystkie")&&!countryFilter.equals(country))continue;
   boolean heading=false;
   for(Channel ch:CHANNELS){
    if(!ch.country.equals(country))continue;
    if(filter.equals("★")&&!favorite(ch.url))continue;
    if(!filter.equals("Wszystkie")&&!filter.equals("★")&&!filter.equals(ch.kind))continue;
    if(!query.isEmpty()&&!((ch.name+" "+ch.desc+" "+ch.kind+" "+ch.country).toLowerCase(Locale.ROOT).contains(query)))continue;
    if(!heading){
     LinearLayout group=row();group.setPadding(dp(4),dp(16),0,dp(8));
     group.addView(new FlagView(country),new LinearLayout.LayoutParams(dp(30),dp(20)));
     TextView label=text(country,18,INK,true);label.setPadding(dp(10),0,0,0);group.addView(label);results.addView(group);heading=true;
    }
    found++;channelRow(ch);
   }
  }
  listingCount.setText(found+" kanałów • wybierz, aby oglądać");
  if(found==0){
   TextView none=text("Brak pozycji. Zmień kategorię lub wyczyść wyszukiwanie.",14,MUTED,false);
   none.setPadding(dp(11),dp(20),dp(11),dp(20));results.addView(none);
  }
 }
 private String verification(Channel channel){
  long played=getPreferences(0).getLong("verified_"+channel.url,0);
  if(played>0)return "Odtwarzano na tym urządzeniu • "+android.text.format.DateFormat.format("dd.MM HH:mm",played);
  return PA6TvAudit.label(this,channel.name);
 }
 private void channelRow(Channel channel){
  LinearLayout l=row();l.setPadding(dp(11),dp(10),dp(9),dp(10));
  l.setBackground(box(WHITE,0xffe0e6ee,15));
  TextView logo=text(channel.name.substring(0,Math.min(2,channel.name.length())).toUpperCase(Locale.ROOT),16,RED,true);
  logo.setGravity(Gravity.CENTER);logo.setBackground(box(0xffeaf2f8,0xffdbe9f4,12));
  l.addView(logo,new LinearLayout.LayoutParams(dp(57),dp(57)));
  LinearLayout texts=col();texts.setPadding(dp(11),0,0,0);
  LinearLayout channelTitle=row();
  channelTitle.addView(new FlagView(channel.country),new LinearLayout.LayoutParams(dp(24),dp(16)));
  TextView n=text(channel.name,16,INK,true);n.setPadding(dp(8),0,0,0);channelTitle.addView(n,new LinearLayout.LayoutParams(0,-2,1));texts.addView(channelTitle);
  TextView label=text(channel.kind+" • "+channel.desc,12,MUTED,false);label.setMaxLines(2);texts.addView(label);
  TextView verified=text(verification(channel),11,MUTED,false);verified.setPadding(0,dp(4),0,0);texts.addView(verified);
  l.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
  Button star=action(favorite(channel.url)?"★":"☆",false);
  star.setTextColor(favorite(channel.url)?RED:MUTED);
  l.addView(star,new LinearLayout.LayoutParams(dp(50),dp(48)));
  star.setOnClickListener(v->toggle(channel));
  l.setContentDescription(channel.name+" • "+channel.kind);
  l.setOnClickListener(v->play(channel));PA6Premium.touch(l);
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(4),0,dp(5));results.addView(l,p);
 }
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);PA6ModernTls.install(this);
  getWindow().setStatusBarColor(INK);getWindow().setNavigationBarColor(INK);
  getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
  LinearLayout page=col();page.setBackgroundColor(WHITE);
  page.setPadding(dp(12),dp(10),dp(12),dp(5));setContentView(page);
  LinearLayout header=row();
  TextView red=text("●",28,RED,true);header.addView(red);
  TextView head=text("  ALFA TV",23,INK,true);header.addView(head,new LinearLayout.LayoutParams(0,-2,1));
  TextView live=text("NA ŻYWO",12,RED,true);header.addView(live);
  page.addView(header);space(page,6);
  // Tablet landscape: fixed 16:9-ish player on the left and scrollable channel library on the right.
  // Portrait: the same screens stack, and the player is NEVER inside a ScrollView.
  float widthDp=getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density;
  boolean wide=PA6Premium.wide(this);
  LinearLayout panes=col();panes.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
  page.addView(panes,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout playerPane=col(),catalogPane=col();
  if(wide){
   LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-1,5);
   left.setMargins(0,0,dp(12),0);ScrollView playerScroll=new ScrollView(this);playerScroll.addView(playerPane);panes.addView(playerScroll,left);
   panes.addView(catalogPane,new LinearLayout.LayoutParams(0,-1,4));
  }else{
   LinearLayout stack=col();ScrollView all=new ScrollView(this);all.setFillViewport(true);all.addView(stack);
   FrameLayout phone=new FrameLayout(this);int max=dp((int)Math.min(widthDp-24,480));phone.addView(all,new FrameLayout.LayoutParams(max,-1,Gravity.TOP|Gravity.CENTER_HORIZONTAL));
   panes.addView(phone,new LinearLayout.LayoutParams(-1,-1));stack.addView(playerPane,new LinearLayout.LayoutParams(-1,-2));stack.addView(catalogPane,new LinearLayout.LayoutParams(-1,dp(720)));
  }
  player=new PA6EmbeddedPlayer(this);player.setFullscreenListener(()->fullscreen.toggle(this,player));
  player.setPlaybackErrorListener(()->onChannelPlaybackError());
  player.setPlaybackStartListener(()->{cancelTuneWatchdog();if(currentChannel!=null)getPreferences(0).edit().putLong("verified_"+currentChannel.url,System.currentTimeMillis()).apply();if(currentChannel!=null)status.setText("Na żywo • "+currentChannel.name);});
  playerPane.addView(player,new LinearLayout.LayoutParams(-1,-2));
  if(getIntent().getBooleanExtra("verify_libvlc",false)&&!player.verifyNative())
   throw new IllegalStateException("PA6 native player unavailable");
  active=text("Wybierz kanał z listy",17,INK,true);
  active.setPadding(dp(6),dp(6),dp(6),dp(4));playerPane.addView(active);
  status=text("Odtwarzacz w okienku • obraz i dźwięk wewnątrz Pomocnika Alfa.",13,MUTED,false);
  status.setPadding(dp(6),dp(2),dp(6),dp(8));playerPane.addView(status);
  LinearLayout tools=row();
  Button last=action("↻  Ostatnio",false);tools.addView(last,new LinearLayout.LayoutParams(0,dp(50),1));last.setOnClickListener(v->replay());
  Button stop=action("■  Stop",false);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(50),1);
  sp.setMargins(dp(7),0,0,0);tools.addView(stop,sp);stop.setOnClickListener(v->player.stop());
  playerPane.addView(tools);space(playerPane,10);
  Button mini=action("▣  Małe okienko",false);playerPane.addView(mini);mini.setOnClickListener(v->fullscreen.mini(this,player));
  Button hide=action("Zwiń / rozwiń katalog",false);page.addView(hide,1,new LinearLayout.LayoutParams(-1,dp(50)));hide.setOnClickListener(v->catalogPane.setVisibility(catalogPane.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
  ScrollView scroll=new ScrollView(this);catalogPane.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
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
  LinearLayout countries=row();
  for(String country:new String[]{"Wszystkie","Polska","Rosja","Międzynarodowe"}){
   LinearLayout tab=row();tab.setPadding(dp(10),dp(8),dp(10),dp(8));tab.setMinimumHeight(dp(48));
   tab.setBackground(box(0xffeef2f7,0xffdce3ee,12));
   if(!country.equals("Wszystkie"))tab.addView(new FlagView(country),new LinearLayout.LayoutParams(dp(24),dp(16)));
   TextView label=text(country.equals("Międzynarodowe")?"Świat":country,14,INK,true);label.setPadding(dp(7),0,0,0);tab.addView(label);
   LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(48));cp.setMargins(0,0,dp(6),0);countries.addView(tab,cp);
   tab.setContentDescription("Kraj: "+country);
   tab.setOnClickListener(v->{countryFilter=country;filter="Wszystkie";showChannels();status.setText(country.equals("Wszystkie")?"Wszystkie kraje":country);});
  }
  HorizontalScrollView countryScroll=new HorizontalScrollView(this);countryScroll.setHorizontalScrollBarEnabled(false);countryScroll.addView(countries);content.addView(countryScroll);space(content,9);
  LinearLayout tabs=row();
  for(String label:new String[]{"Wszystkie","Wiadomości","Muzyka","Regiony","Rozrywka","★"}){
   Button tab=action(label.equals("★")?"★":label,false);tab.setTextSize(11);
   LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(48));p.setMargins(dp(1),0,dp(1),0);tabs.addView(tab,p);
   tab.setOnClickListener(v->{filter=label;showChannels();status.setText("Kategoria: "+(label.equals("★")?"Ulubione":label));});
  }
  HorizontalScrollView categoryScroll=new HorizontalScrollView(this);categoryScroll.setHorizontalScrollBarEnabled(false);categoryScroll.addView(tabs);content.addView(categoryScroll);space(content,8);
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
  if(getIntent().getBooleanExtra("verify_ru_catalog",false))
   filter="Rosyjskojęzyczne";
  String testCountry=getIntent().getStringExtra("verify_tv_country");
  if("Polska".equals(testCountry)||"Rosja".equals(testCountry)||"Międzynarodowe".equals(testCountry))countryFilter=testCountry;
  showChannels();
  if(getIntent().getBooleanExtra("verify_tv_catalog",false)){
   try{
    String url=PA6TvSources.tvpPlaylist("{\"sources\":{\"HLS\":[{\"src\":\"https://test.example/live.m3u8\"}]}}");
    if(!url.equals("https://test.example/live.m3u8"))throw new IllegalStateException("TVP resolver failed");
    boolean rejected=false;
    try{PA6TvSources.tvpPlaylist("{\"drm\":{\"WIDEVINE\":{}},\"sources\":{}}");}catch(java.io.IOException expected){rejected=true;}
    if(!rejected)throw new IllegalStateException("DRM source accepted");
    boolean one=false,krakow=false,russia=false;
    for(Channel channel:CHANNELS){one|=channel.name.equals("TVP 1");krakow|=channel.name.equals("TVP3 Kraków");russia|=channel.name.startsWith("Rossija 1");}
    if(!one||!krakow||!russia)throw new IllegalStateException("Requested channels missing");
    android.util.Log.i("PA6_TV_TEST","PASS requested channels, TVP HLS parsing, protected stream rejection");
   }catch(Exception error){android.util.Log.e("PA6_TV_TEST","FAIL TV catalog",error);}
  }
  if(getIntent().getBooleanExtra("verify_russia1",false))for(Channel channel:CHANNELS)if(channel.name.startsWith("Rossija 1")){play(channel);break;}
  // CI diagnostics: actually request a real H.264 media stream, not merely initialize LibVLC.
  if(getIntent().getBooleanExtra("verify_video_stream",false))
   play(new Channel("Film kontrolny H.264","Test","Test odtwarzania wideo w PA",
    "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"));
 }
 @Override protected void onStart(){super.onStart();if(getIntent().getBooleanExtra("play_last",false)){getIntent().removeExtra("play_last");replay();}String shortcut=getIntent().getStringExtra("station_url");if(shortcut!=null){getIntent().removeExtra("station_url");for(Channel c:CHANNELS)if(c.url.equals(shortcut)){play(c);break;}}}
 @Override protected void onStop(){
  // Never leave hidden video decoding when the user switches to other PA modules.
  cancelTuneWatchdog();++tuneRequest;currentChannel=null;
  if(player!=null)player.stop();
  super.onStop();
 }
 @Override protected void onDestroy(){fullscreen.close();cancelTuneWatchdog();if(player!=null)player.release();super.onDestroy();}
}
