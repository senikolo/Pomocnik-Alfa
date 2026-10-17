package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;

/**
 * PA6 WOW: lightweight custom Android 6 dashboard.
 * All shapes/vectors are native, no large images, third-party UI or WebView on Home.
 */
public final class PA6DashboardActivity extends Activity {
 private final int NIGHT=0xff0c1428, PANEL=0xff15213a, INK=0xfff3f6ff, MUTED=0xffafbdd9, TEAL=0xff60eddf;
 private LinearLayout root;
 private boolean senior;
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
 private GradientDrawable shape(int fill,int radius){
  GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));return d;
 }
 private GradientDrawable outlined(int fill,int stroke,int radius){
  GradientDrawable d=shape(fill,radius);d.setStroke(dp(1),stroke);return d;
 }
 private GradientDrawable gradient(int first,int second,int radius){
  GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{first,second});
  d.setCornerRadius(dp(radius));return d;
 }
 private TextView label(String txt,int size,boolean bold,int color){
  TextView t=new TextView(this);t.setText(txt);t.setTextSize(size+(senior?2:0));
  t.setTextColor(color);
  if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
  t.setGravity(Gravity.CENTER_VERTICAL);
  return t;
 }
 private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
 private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 private void space(LinearLayout into,int height){View v=new View(this);into.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
 private String lastName(String target){
  switch(target){case "tv":return "Telewizja";case "tube":return "Alfa Tube";case "radio":return "Radio";
   case "planner":return "Planer podróży";case "assistant":return "Pomocnik Alfa";
   case "sounds":return "Dźwięki spokoju";case "bus":return "Gdzie jest autobus?";
   default:return "Ostatnio używany moduł";}
 }
 private void hero(){
  LinearLayout hero=column();hero.setPadding(dp(20),dp(18),dp(20),dp(20));
  hero.setBackground(gradient(0xff273f83,0xff162443,24));
  root.addView(hero,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout strip=row();
  ImageView cat=new ImageView(this);
  cat.setImageResource(getResources().getIdentifier("ic_cat","drawable",getPackageName()));
  cat.setScaleType(ImageView.ScaleType.FIT_CENTER);
  strip.addView(cat,new LinearLayout.LayoutParams(dp(52),dp(52)));
  LinearLayout heading=column();heading.setPadding(dp(13),0,0,0);
  heading.addView(label("POMOCNIK ALFA",24,true,INK));
  heading.addView(label("UltraSmart  •  Tablet Edition",13,false,0xffb9d6ff));
  strip.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
  hero.addView(strip);
  space(hero,18);
  TextView welcome=label("Dobrze, że jesteś!",26,true,Color.WHITE);hero.addView(welcome);
  space(hero,6);
  hero.addView(label("Podróże, telewizja, muzyka i codzienna pomoc. Wszystko pod ręką.",15,false,0xffd6e5ff));
  space(hero,19);
  LinearLayout shortcuts=row();
  shortcuts.addView(quick("tv","TELEWIZJA","Oglądaj teraz",TEAL),new LinearLayout.LayoutParams(0,dp(senior?85:75),1));
  View gap=new View(this);shortcuts.addView(gap,new LinearLayout.LayoutParams(dp(10),1));
  shortcuts.addView(quick("radio","RADIO","Słuchaj na żywo",0xffffcf83),new LinearLayout.LayoutParams(0,dp(senior?85:75),1));
  hero.addView(shortcuts);
  String last=getPreferences(0).getString("last_section","");
  if(!last.isEmpty()&&!last.equals("tv")&&!last.equals("radio")){
   space(hero,13);
   TextView recent=label("↗  Ostatnio: "+lastName(last)+"   ·   OTWÓRZ",13,true,0xffc9ebff);
   recent.setPadding(dp(4),dp(8),dp(4),dp(8));hero.addView(recent);
   recent.setOnClickListener(v->open(last));
  }
 }
 private View quick(String target,String caption,String sub,int tint){
  LinearLayout l=row();l.setPadding(dp(12),dp(8),dp(12),dp(8));
  l.setBackground(outlined(0xff263a62,0xff52719f,15));
  PA6GlyphView icon=new PA6GlyphView(this,target,tint);
  l.addView(icon,new LinearLayout.LayoutParams(dp(37),dp(37)));
  LinearLayout copy=column();copy.setPadding(dp(10),0,0,0);
  copy.addView(label(caption,14,true,Color.WHITE));
  copy.addView(label(sub,11,false,0xffb5cdee));
  l.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
  l.setContentDescription(caption+". "+sub);
  l.setOnClickListener(v->open(target));return l;
 }
 private void section(String name,String detail,String[][] entries){
  space(root,22);
  LinearLayout heading=row();heading.setPadding(dp(5),0,dp(5),0);
  TextView title=label(name,21,true,INK);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  root.addView(heading);
  TextView subtitle=label(detail,13,false,MUTED);
  subtitle.setPadding(dp(6),dp(5),0,dp(10));root.addView(subtitle);
  DisplayMetrics dm=getResources().getDisplayMetrics();
  int width=(int)(dm.widthPixels/dm.density);
  int cols=width>=840?4:width>=590?3:2;
  for(int i=0;i<entries.length;i+=cols){
   LinearLayout r=row();r.setGravity(Gravity.TOP);root.addView(r,new LinearLayout.LayoutParams(-1,-2));
   for(int j=0;j<cols;j++){
    if(i+j>=entries.length){r.addView(new View(this),new LinearLayout.LayoutParams(0,dp(2),1));continue;}
    String[] e=entries[i+j];View item=tile(e[0],e[1],e[2],e[3],Integer.parseInt(e[4]));
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(senior?164:148),1);
    p.setMargins(dp(4),dp(4),dp(4),dp(4));r.addView(item,p);
   }
  }
 }
 private View tile(String kind,String name,String desc,String target,int accent){
  LinearLayout tile=column();tile.setGravity(Gravity.TOP);
  tile.setPadding(dp(12),dp(12),dp(10),dp(10));
  tile.setBackground(outlined(PANEL,0xff293952,19));
  LinearLayout iconFrame=row();iconFrame.setGravity(Gravity.CENTER);
  iconFrame.setBackground(shape(0xff23314a,13));
  PA6GlyphView icon=new PA6GlyphView(this,kind,accent);
  iconFrame.addView(icon,new LinearLayout.LayoutParams(dp(37),dp(37)));
  tile.addView(iconFrame,new LinearLayout.LayoutParams(dp(54),dp(54)));
  space(tile,12);
  TextView label=label(name,15,true,INK);label.setMaxLines(2);
  tile.addView(label);
  TextView sub=label(desc,11,false,MUTED);sub.setMaxLines(2);
  tile.addView(sub);
  tile.setContentDescription(name+". "+desc);
  tile.setOnClickListener(v->{
   tile.animate().scaleX(.97f).scaleY(.97f).setDuration(90).withEndAction(()->{
    tile.setScaleX(1);tile.setScaleY(1);open(target);
   }).start();
  });
  return tile;
 }
 private void open(String target){
  try{
   Intent i;
   switch(target){
    case "planner":case "bus":case "assistant":case "favorites":
     i=new Intent(this,PARegionsActivity.class).putExtra("pa_tab",target);break;
    case "radio":i=new Intent(this,PA6RadioActivity.class);break;
    case "tv":i=new Intent(this,PA6TvActivity.class);break;
    case "tube":case "kids":i=new Intent(this,PA6TubeActivity.class).putExtra("kids",target.equals("kids"));break;
    case "weather":i=new Intent(this,PA6WeatherActivity.class);break;
    case "notes":i=new Intent(this,PA6NotesActivity.class);break;
    case "reminders":i=new Intent(this,PA6RemindersActivity.class);break;
    case "reader":i=new Intent(this,PA6ReaderActivity.class);break;
    case "sounds":i=new Intent(this,PA6SoundsActivity.class);break;
    default:Toast.makeText(this,"Funkcja chwilowo niedostępna",Toast.LENGTH_SHORT).show();return;
   }
   startActivity(i);
   getPreferences(0).edit().putString("last_section",target).apply();
  }catch(Exception e){Toast.makeText(this,"Nie udało się otworzyć tej funkcji",Toast.LENGTH_LONG).show();}
 }
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  getWindow().setStatusBarColor(NIGHT);getWindow().setNavigationBarColor(NIGHT);
  senior=getPreferences(0).getBoolean("senior",false);
  render();
 }
 @Override protected void onResume(){super.onResume();} // No polling/background work on dashboard.
 private void render(){
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(NIGHT);
  scroll.setClipToPadding(false);
  root=column();root.setPadding(dp(12),dp(12),dp(12),dp(25));
  scroll.addView(root);setContentView(scroll);
  hero();
  section("Twoje multimedia","Wybierz i korzystaj bez zbędnych kroków",new String[][]{
   {"tv","Telewizja","Kanały internetowe","tv","6352351"},
   {"tube","Alfa Tube","Filmy i teledyski","tube","16767957"},
   {"radio","Radio","Stacje online","radio","16763779"},
   {"sounds","Dźwięki spokoju","Relaks offline","sounds","12558335"},
   {"kids","Alfa Kids","Kącik dziecięcy","kids","16753397"}
  });
  section("Transport i Pomocnik","Warszawa i Małopolska",new String[][]{
   {"planner","Planer podróży","Trasy i odjazdy","planner","6352351"},
   {"bus","Gdzie autobus?","Informacje o kursie","bus","16763779"},
   {"assistant","Porozmawiaj z Alfą","Asystent podróży","assistant","16767957"},
   {"favorites","Moje przystanki","Szybki dostęp","favorites","16753397"}
  });
  section("Na co dzień","Przydatne narzędzia",new String[][]{
   {"weather","Pogoda","Sprawdź prognozę","weather","6352351"},
   {"notes","Notatnik","Zapisz pomysł","notes","16763779"},
   {"reminders","Przypomnienia","Nie zapomnij","reminders","16767957"},
   {"reader","Czytnik Alfa","Twoje dokumenty","reader","12558335"}
  });
  space(root,14);
  TextView mode=label(senior?"◉  Rozmiar standardowy":"◉  Większe elementy",15,true,TEAL);
  mode.setPadding(dp(18),dp(16),dp(18),dp(16));mode.setGravity(Gravity.CENTER);
  mode.setBackground(outlined(0xff1e3048,0xff3c5669,15));
  root.addView(mode,new LinearLayout.LayoutParams(-1,-2));
  mode.setOnClickListener(v->{senior=!senior;getPreferences(0).edit().putBoolean("senior",senior).apply();render();});
  space(root,14);
  TextView foot=label("PA UltraSmart • Android 6 • lekki interfejs dla 2 GB RAM",12,false,MUTED);
  foot.setGravity(Gravity.CENTER);root.addView(foot);
  root.setAlpha(0f);root.animate().alpha(1f).setDuration(240).start();
 }
}
