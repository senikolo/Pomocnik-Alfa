package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.text.*;
import java.util.*;

/** Pomocnik Alfa Tablet: native, API23-compatible dashboard and multimedia. */
public class HomeActivity extends Activity {
  static final int INK=Color.rgb(39,36,57), PURPLE=Color.rgb(86,56,135), BG=Color.rgb(247,245,251);
  static int dp(Context c,int x){return (int)(x*c.getResources().getDisplayMetrics().density+.5f);}
  static GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
  static TextView text(Context c,String s,int sp,boolean bold,int color){
    TextView t=new TextView(c);t.setText(s);t.setTextSize(sp);t.setTextColor(color);
    if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setGravity(Gravity.CENTER_VERTICAL);
    return t;
  }
  static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(1);return l;}
  static void pad(View v,int a,int b){v.setPadding(dp(v.getContext(),a),dp(v.getContext(),b),dp(v.getContext(),a),dp(v.getContext(),b));}
  static void margin(LinearLayout parent,View child,int bottom){
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(parent.getContext(),bottom);parent.addView(child,lp);
  }
  static LinearLayout screen(Activity a,String title,String intro){
    LinearLayout root=column(a);root.setBackgroundColor(BG);pad(root,18,18);
    TextView heading=text(a,title,25,true,PURPLE);margin(root,heading,8);
    if(intro!=null){TextView sub=text(a,intro,15,false,INK);margin(root,sub,16);}
    return root;
  }
  static void show(Activity a,LinearLayout root){ScrollView sc=new ScrollView(a);sc.setFillViewport(true);sc.addView(root);a.setContentView(sc);}
  static Button action(Context c,String s){Button b=new Button(c);b.setText(s);b.setTextSize(16);b.setAllCaps(false);b.setMinHeight(dp(c,52));return b;}
  static void start(Activity a,Class<?> cls){a.startActivity(new Intent(a,cls));}
  static void external(Activity a,String url){
    try{a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){Toast.makeText(a,"Brak zgodnej przeglądarki.",Toast.LENGTH_LONG).show();}
  }
  static void planner(Activity a,String tab){
    Intent i=new Intent(a,PARegionsActivity.class);i.putExtra("pa_tab",tab);a.startActivity(i);
  }
  @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(PURPLE);getWindow().setNavigationBarColor(Color.rgb(45,30,71));build();}
  private void tile(LinearLayout row,String symbol,String title,String subtitle,Runnable run){
    LinearLayout box=column(this);box.setGravity(Gravity.CENTER);box.setBackground(bg(Color.WHITE,dp(this,18)));
    box.setElevation(dp(this,2));pad(box,10,11);
    TextView icon=text(this,symbol,29,true,PURPLE);icon.setGravity(Gravity.CENTER);
    TextView name=text(this,title,16,true,INK);name.setGravity(Gravity.CENTER);
    TextView caption=text(this,subtitle,12,false,Color.rgb(102,94,120));caption.setGravity(Gravity.CENTER);
    box.addView(icon,new LinearLayout.LayoutParams(-1,dp(this,45)));
    box.addView(name,new LinearLayout.LayoutParams(-1,dp(this,31)));
    box.addView(caption,new LinearLayout.LayoutParams(-1,dp(this,39)));
    box.setContentDescription(title+" — "+subtitle);
    box.setOnClickListener(v->run.run());
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(this,144),1);p.setMargins(dp(this,4),dp(this,4),dp(this,4),dp(this,7));
    row.addView(box,p);
  }
  private void build(){
    LinearLayout root=screen(this,"Pomocnik Alfa 2.0","UltraSmart • Tablet Edition • Android 6.0");
    TextView hint=text(this,"Twój Pomocnik Alfa: transport, radio, telewizja i codzienne sprawy.",15,false,INK);
    margin(root,hint,15);
    LinearLayout row1=new LinearLayout(this);root.addView(row1);
    tile(row1,"➜","Planer","Warszawa i Małopolska",()->planner(this,"planner"));
    tile(row1,"▣","Moje przystanki","Odjazdy i ulubione",()->planner(this,"favorites"));
    LinearLayout row2=new LinearLayout(this);root.addView(row2);
    tile(row2,"⌖","Gdzie jest pojazd?","Mapa i kursy",()->planner(this,"liveBus"));
    tile(row2,"✦","Pomocnik Alfa","Rozmowa i porady",()->planner(this,"assistant"));
    LinearLayout row3=new LinearLayout(this);root.addView(row3);
    tile(row3,"♫","Radio","Słuchaj także w tle",()->start(this,Radio.class));
    tile(row3,"▣","Telewizja","Wideo i oficjalne kanały",()->start(this,Tv.class));
    LinearLayout row4=new LinearLayout(this);root.addView(row4);
    tile(row4,"☀","Pogoda","Prognoza dla miejscowości",()->start(this,Weather.class));
    tile(row4,"✎","Notatnik","Zapisz ważne informacje",()->start(this,Notes.class));
    LinearLayout row5=new LinearLayout(this);root.addView(row5);
    tile(row5,"◷","Przypomnienia","Alarmy w tablecie",()->start(this,Reminders.class));
    tile(row5,"☆","Ulubione trasy","Zapisane połączenia",()->planner(this,"favorites"));
    TextView foot=text(this,"Wersja tabletowa: część danych rozkładowych jest planowa. LIVE tylko ze sprawdzonych źródeł. Obsługa wybranych kanałów TV zależy od dostępności transmisji i kodeków tabletu.",12,false,Color.DKGRAY);
    margin(root,foot,12);show(this,root);
  }

  /** Background radio via Android MediaPlayer, no WebView dependency. */
  public static class RadioService extends Service {
    static final String PLAY="pa6.radio.PLAY",STOP="pa6.radio.STOP";
    private MediaPlayer player;
    private AudioManager am;
    private final AudioManager.OnAudioFocusChangeListener focus=change->{
      if(change<=AudioManager.AUDIOFOCUS_LOSS_TRANSIENT && player!=null)try{player.pause();}catch(Exception ignored){}
    };
    @Override public void onCreate(){super.onCreate();am=(AudioManager)getSystemService(AUDIO_SERVICE);}
    private void release(){if(player!=null){try{player.reset();player.release();}catch(Exception ignored){}player=null;}}
    @Override public int onStartCommand(Intent intent,int flags,int id){
      if(intent==null)return START_NOT_STICKY;
      if(STOP.equals(intent.getAction())){release();stopForeground(true);stopSelf();return START_NOT_STICKY;}
      String url=intent.getStringExtra("url"),name=intent.getStringExtra("name");
      if(!PLAY.equals(intent.getAction())||url==null||!url.startsWith("https://"))return START_NOT_STICKY;
      release();am.requestAudioFocus(focus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);
      Intent stop=new Intent(this,RadioService.class).setAction(STOP);
      PendingIntent pi=PendingIntent.getService(this,10,stop,PendingIntent.FLAG_UPDATE_CURRENT);
      Intent open=new Intent(this,Radio.class);
      PendingIntent content=PendingIntent.getActivity(this,11,open,PendingIntent.FLAG_UPDATE_CURRENT);
      Notification n=new Notification.Builder(this).setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle(name==null?"Radio":name).setContentText("Odtwarzanie lub łączenie…")
        .setContentIntent(content).setOngoing(true)
        .addAction(android.R.drawable.ic_media_pause,"Zatrzymaj",pi).build();
      startForeground(610,n);
      getSharedPreferences("pa6radio",0).edit().putString("name",name).putString("url",url).apply();
      try{
        player=new MediaPlayer();player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        player.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK);
        player.setOnPreparedListener(mp->{try{mp.start();}catch(Exception ignored){}});
        player.setOnErrorListener((mp,what,extra)->{Toast.makeText(this,"Nie udało się odtworzyć stacji. Spróbuj innego strumienia.",Toast.LENGTH_LONG).show();stopSelf();return true;});
        player.setDataSource(url);player.prepareAsync();
      }catch(Exception e){release();stopForeground(true);stopSelf();Toast.makeText(this,"Błąd połączenia ze stacją",Toast.LENGTH_SHORT).show();}
      return START_NOT_STICKY;
    }
    @Override public void onDestroy(){release();if(am!=null)am.abandonAudioFocus(focus);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
  }

  public static class Radio extends Activity {
    private static final String[][] STATIONS={
      {"Polskie Radio Jedynka","https://stream11.polskieradio.pl/pr1/pr1.sdp/playlist.m3u8"},
      {"Polskie Radio Dwójka","https://stream12.polskieradio.pl/pr2/pr2.sdp/playlist.m3u8"},
      {"Polskie Radio Trójka","https://stream13.polskieradio.pl/pr3/pr3.sdp/playlist.m3u8"},
      {"Polskie Radio Czwórka","https://stream14.polskieradio.pl/pr4/pr4.sdp/playlist.m3u8"},
      {"Polskie Radio 24","https://stream15.polskieradio.pl/pr24/pr24.sdp/playlist.m3u8"},
      {"Radio Dzieciom","https://stream85.polskieradio.pl/live/radio_dzieciom.sdp/playlist.m3u8"}
    };
    void play(String name,String url){startService(new Intent(this,RadioService.class).setAction(RadioService.PLAY).putExtra("name",name).putExtra("url",url));Toast.makeText(this,"Łączenie: "+name,Toast.LENGTH_SHORT).show();}
    @Override public void onCreate(Bundle b){super.onCreate(b);LinearLayout root=screen(this,"Radio","Stacje Polskiego Radia. Odtwarzanie działa w tle, również po przejściu do Planera.");
      String last=getSharedPreferences("pa6radio",0).getString("name","");
      if(!last.isEmpty())margin(root,text(this,"Ostatnio wybrano: "+last,14,false,INK),10);
      for(String[] s:STATIONS){Button btn=action(this,"▶  "+s[0]);margin(root,btn,7);btn.setOnClickListener(v->play(s[0],s[1]));}
      Button stop=action(this,"■  Zatrzymaj radio");margin(root,stop,14);stop.setOnClickListener(v->startService(new Intent(this,RadioService.class).setAction(RadioService.STOP)));
      margin(root,text(this,"Uwaga: dostępność internetowych strumieni i zgodność HLS z Androidem 6 wymagają testu na tablecie.",13,false,Color.DKGRAY),4);
      show(this,root);
    }
  }

  public static class Tv extends Activity {
    private VideoView video;private TextView status;private EditText custom;
    void play(String url){
      if(!url.startsWith("https://")||!(url.contains(".m3u8")||url.contains(".mp4"))){status.setText("Podaj bezpieczny adres HTTPS transmisji HLS (.m3u8) lub MP4.");return;}
      status.setText("Łączenie z transmisją…");
      video.setVideoURI(Uri.parse(url));video.setOnPreparedListener(mp->{status.setText("Odtwarzanie");mp.setScreenOnWhilePlaying(true);video.start();});
      video.setOnErrorListener((mp,what,extra)->{status.setText("Ten kanał lub format nie jest obsługiwany na Androidzie 6. Spróbuj otworzyć go w przeglądarce.");return true;});
      video.requestFocus();
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      LinearLayout root=screen(this,"Telewizja","Natywny odtwarzacz dla zgodnych transmisji bez DRM oraz oficjalne strony nadawców.");
      video=new VideoView(this);video.setBackgroundColor(Color.BLACK);
      LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,dp(this,320));root.addView(video,vp);
      MediaController c=new MediaController(this);c.setAnchorView(video);video.setMediaController(c);
      status=text(this,"Wybierz transmisję poniżej.",14,false,INK);margin(root,status,9);
      Button pr=action(this,"▶  Polskie Radio Czwórka – wideo (HLS)");margin(root,pr,7);pr.setOnClickListener(v->play("https://stream14.polskieradio.pl/pr4_video/video_pr4.stream/playlist.m3u8"));
      Button t1=action(this,"↗  TVP1 – oficjalny serwis");margin(root,t1,7);t1.setOnClickListener(v->external(this,"https://vod.tvp.pl/live"));
      Button ti=action(this,"↗  TVP Info – oficjalny serwis");margin(root,ti,7);ti.setOnClickListener(v->external(this,"https://www.tvp.info/"));
      Button tvp=action(this,"↗  TVP Sport – oficjalny serwis");margin(root,tvp,7);tvp.setOnClickListener(v->external(this,"https://sport.tvp.pl/"));
      custom=new EditText(this);custom.setSingleLine(true);custom.setInputType(17);custom.setHint("Wklej legalny adres HLS HTTPS (.m3u8)");margin(root,custom,6);
      Button go=action(this,"▶  Odtwórz podany strumień");margin(root,go,10);go.setOnClickListener(v->play(custom.getText().toString().trim()));
      margin(root,text(this,"Niektóre kanały wymagają nowego WebView, DRM albo wygasających tokenów. Otwarcie strony nadawcy nie gwarantuje odtwarzania na tym tablecie.",13,false,Color.DKGRAY),5);
      show(this,root);
    }
    @Override protected void onPause(){if(video!=null)video.stopPlayback();super.onPause();}
  }

  public static class Notes extends Activity {
    @Override public void onCreate(Bundle b){super.onCreate(b);LinearLayout root=screen(this,"Notatnik","Twoje notatki są przechowywane lokalnie w tablecie.");
      EditText ed=new EditText(this);ed.setGravity(Gravity.TOP);ed.setMinLines(10);ed.setText(getSharedPreferences("pa6notes",0).getString("note",""));ed.setHint("Zapisz swoje informacje…");margin(root,ed,10);
      Button save=action(this,"Zapisz notatkę");margin(root,save,12);
      save.setOnClickListener(v->{getSharedPreferences("pa6notes",0).edit().putString("note",ed.getText().toString()).apply();Toast.makeText(this,"Notatka zapisana na tablecie",Toast.LENGTH_SHORT).show();});show(this,root);
    }
  }

  public static class Reminders extends Activity {
    long trigger;TextView when;EditText label;
    @Override public void onCreate(Bundle b){super.onCreate(b);LinearLayout root=screen(this,"Przypomnienia","Tablet przypomni o wybranej godzinie, nawet jeśli aplikacja jest zamknięta.");
      label=new EditText(this);label.setSingleLine(true);label.setHint("O czym przypomnieć?");margin(root,label,10);
      when=text(this,"Wybierz termin przypomnienia",17,true,INK);margin(root,when,9);
      Button date=action(this,"Wybierz datę i godzinę");margin(root,date,7);
      date.setOnClickListener(v->{Calendar c=Calendar.getInstance();
        new DatePickerDialog(this,(view,y,m,d)->{
          new TimePickerDialog(this,(tv,h,min)->{
            Calendar chosen=Calendar.getInstance();chosen.set(y,m,d,h,min,0);chosen.set(Calendar.MILLISECOND,0);trigger=chosen.getTimeInMillis();
            when.setText(new SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(chosen.getTime()));
          },c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),true).show();
        },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
      });
      Button save=action(this,"Zapisz przypomnienie");margin(root,save,11);save.setOnClickListener(v->{
        if(trigger<=System.currentTimeMillis()||label.getText().toString().trim().isEmpty()){Toast.makeText(this,"Podaj treść oraz przyszły termin.",Toast.LENGTH_LONG).show();return;}
        int id=(int)(System.currentTimeMillis()%1000000);
        Intent i=new Intent(this,ReminderReceiver.class).putExtra("text",label.getText().toString()).putExtra("rid",id);
        PendingIntent pi=PendingIntent.getBroadcast(this,id,i,PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pi);
        Toast.makeText(this,"Przypomnienie ustawione",Toast.LENGTH_LONG).show();
        label.setText("");trigger=0;when.setText("Wybierz następny termin");
      });show(this,root);
    }
  }

  public static class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
      String message=intent.getStringExtra("text");int id=intent.getIntExtra("rid",611);
      PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,HomeActivity.class),PendingIntent.FLAG_UPDATE_CURRENT);
      Notification n=new Notification.Builder(c).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle("Pomocnik Alfa przypomina").setContentText(message)
        .setStyle(new Notification.BigTextStyle().bigText(message)).setContentIntent(open)
        .setAutoCancel(true).setDefaults(Notification.DEFAULT_ALL).build();
      ((NotificationManager)c.getSystemService(NOTIFICATION_SERVICE)).notify(id,n);
    }
  }

  public static class Weather extends Activity {
    private TextView output;private EditText city;
    @Override public void onCreate(Bundle b){super.onCreate(b);LinearLayout root=screen(this,"Pogoda","Aktualne warunki pogodowe z Open-Meteo. Wymagany internet.");
      city=new EditText(this);city.setSingleLine(true);city.setText(getSharedPreferences("pa6weather",0).getString("city","Warszawa"));city.setHint("Miejscowość");margin(root,city,8);
      Button lookup=action(this,"Sprawdź pogodę");margin(root,lookup,10);
      output=text(this,"Wprowadź miejscowość, a następnie wybierz „Sprawdź pogodę”.",16,false,INK);margin(root,output,8);
      lookup.setOnClickListener(v->fetch(city.getText().toString().trim()));show(this,root);
    }
    static JSONObject get(String url)throws Exception{
      HttpURLConnection h=(HttpURLConnection)new URL(url).openConnection();h.setConnectTimeout(12000);h.setReadTimeout(12000);h.setRequestProperty("User-Agent","PA-Tablet-Android6/1.0");
      try{if(h.getResponseCode()!=200)throw new IOException("HTTP "+h.getResponseCode());
        ByteArrayOutputStream out=new ByteArrayOutputStream();InputStream in=h.getInputStream();
        try{byte[] buf=new byte[4096];int n,total=0;while((n=in.read(buf))!=-1){total+=n;if(total>150000)throw new IOException("Zbyt duża odpowiedź");out.write(buf,0,n);}}finally{in.close();}
        return new JSONObject(out.toString("UTF-8"));
      }finally{h.disconnect();}
    }
    void fetch(String c){
      if(c.length()<2||c.length()>100){output.setText("Wpisz nazwę miejscowości.");return;}
      output.setText("Sprawdzam pogodę…");
      getSharedPreferences("pa6weather",0).edit().putString("city",c).apply();
      new Thread(()->{String result;
        try{
          JSONObject geo=get("https://geocoding-api.open-meteo.com/v1/search?name="+URLEncoder.encode(c,"UTF-8")+"&count=1&language=pl&format=json");
          JSONArray a=geo.optJSONArray("results");if(a==null||a.length()==0)throw new IOException("Nie znaleziono miejscowości");
          JSONObject p=a.getJSONObject(0);double lat=p.getDouble("latitude"),lon=p.getDouble("longitude");
          String url="https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon+"&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&timezone=auto";
          JSONObject current=get(url).getJSONObject("current");
          result=p.getString("name")+"\nTemperatura: "+current.getDouble("temperature_2m")+" °C\nWilgotność: "+current.getInt("relative_humidity_2m")+"%\nWiatr: "+current.getDouble("wind_speed_10m")+" km/h\nKod pogody: "+current.getInt("weather_code")+"\nDane: Open-Meteo";
        }catch(Exception e){result="Nie mogę pobrać pogody. Sprawdź internet i spróbuj ponownie.\n"+e.getMessage();}
        final String answer=result;runOnUiThread(()->output.setText(answer));
      },"PA6-Weather").start();
    }
  }
}
