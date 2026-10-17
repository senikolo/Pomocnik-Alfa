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

/** Low-memory, native dashboard for an Android 6 tablet. Modules start on demand. */
public final class PA6DashboardActivity extends Activity {
 private LinearLayout root;
 private int ink=0xff322546,accent=0xff6946a4,background=0xfff5f2fa;
 private boolean senior;
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
 private GradientDrawable bg(int fill,int stroke,int radius){
  GradientDrawable b=new GradientDrawable();b.setColor(fill);b.setCornerRadius(dp(radius));
  b.setStroke(dp(1),stroke);return b;
 }
 private TextView text(String s,int sp,boolean bold,int color){
  TextView t=new TextView(this);t.setText(s);t.setTextSize(sp+(senior?2:0));t.setTextColor(color);
  if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
 }
 private void addHeader(){
  LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(16),dp(12),dp(16),dp(12));
  bar.setBackground(bg(accent,accent,17));
  ImageView cat=new ImageView(this);cat.setImageResource(getResources().getIdentifier("ic_cat","drawable",getPackageName()));
  cat.setScaleType(ImageView.ScaleType.FIT_CENTER);bar.addView(cat,new LinearLayout.LayoutParams(dp(62),dp(62)));
  LinearLayout copy=new LinearLayout(this);copy.setOrientation(1);copy.setPadding(dp(12),0,0,0);
  copy.addView(text("POMOCNIK ALFA",23,true,Color.WHITE));
  copy.addView(text("UltraSmart • Tablet Edition • Android 6",14,false,0xffece1fb));
  bar.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
  root.addView(bar);
  TextView intro=text("Witaj! Co chcesz dzisiaj zrobić?",19,true,ink);
  intro.setPadding(dp(4),dp(20),0,dp(3));root.addView(intro);
  TextView secondary=text("Twój transport, multimedia i codzienne sprawy — w jednym miejscu.",14,false,0xff6b607c);
  secondary.setPadding(dp(4),0,0,dp(16));root.addView(secondary);
 }
 private void section(String title,String[][] items){
  TextView head=text(title,19,true,accent);head.setPadding(dp(3),dp(18),0,dp(6));root.addView(head);
  DisplayMetrics dm=getResources().getDisplayMetrics();
  int width=(int)(dm.widthPixels/dm.density);
  int columns=width>=860?4:(width>=550?3:2);
  int i=0;
  while(i<items.length){
   LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
   root.addView(row,new LinearLayout.LayoutParams(-1,-2));
   for(int col=0;col<columns;col++){
    if(i>=items.length){View blank=new View(this);row.addView(blank,new LinearLayout.LayoutParams(0,dp(6),1));continue;}
    String[] entry=items[i++];
    LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER);
    card.setPadding(dp(7),dp(9),dp(7),dp(9));card.setBackground(bg(Color.WHITE,0xffe5dced,15));
    TextView emoji=text(entry[0],29,false,accent);emoji.setGravity(Gravity.CENTER);card.addView(emoji);
    TextView label=text(entry[1],senior?17:15,true,ink);label.setGravity(Gravity.CENTER);label.setMaxLines(2);
    card.addView(label,new LinearLayout.LayoutParams(-1,-2));
    card.setContentDescription(entry[1]);
    card.setOnClickListener(v->open(entry[2]));
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(senior?113:101),1);p.setMargins(dp(4),dp(4),dp(4),dp(4));
    row.addView(card,p);
   }
  }
 }
 private void open(String target){
  try{
   Class<?> cl;
   Intent i;
   switch(target){
    case "planner":case "bus":case "assistant":case "favorites":
     cl=PARegionsActivity.class;i=new Intent(this,cl);i.putExtra("pa_tab",target);break;
    case "radio":cl=PA6RadioActivity.class;i=new Intent(this,cl);break;
    case "tv":cl=PA6TvActivity.class;i=new Intent(this,cl);break;
    case "tube":case "kids":cl=PA6TubeActivity.class;i=new Intent(this,cl).putExtra("kids",target.equals("kids"));break;
    case "weather":cl=PA6WeatherActivity.class;i=new Intent(this,cl);break;
    case "notes":cl=PA6NotesActivity.class;i=new Intent(this,cl);break;
    case "reminders":cl=PA6RemindersActivity.class;i=new Intent(this,cl);break;
    case "reader":cl=PA6ReaderActivity.class;i=new Intent(this,cl);break;
    case "sounds":cl=PA6SoundsActivity.class;i=new Intent(this,cl);break;
    default:Toast.makeText(this,"Moduł jeszcze niedostępny",Toast.LENGTH_SHORT).show();return;
   }
   startActivity(i);
  }catch(Exception e){Toast.makeText(this,"Nie udało się otworzyć modułu",Toast.LENGTH_LONG).show();}
 }
 @Override public void onCreate(Bundle b){super.onCreate(b);senior=getPreferences(0).getBoolean("senior",false);render();}
 private void render(){
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(background);
  root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(12),dp(12),dp(12),dp(26));
  scroll.addView(root);setContentView(scroll);
  addHeader();
  section("🚌 Transport i Alfa",new String[][]{
   {"🧭","Planer podróży","planner"},{"🚌","Gdzie mój autobus?","bus"},
   {"💬","Porozmawiaj z Alfą","assistant"},{"⭐","Ulubione przystanki","favorites"}});
  section("▶ Multimedia",new String[][]{
   {"▶","Alfa Tube","tube"},{"📻","Radio","radio"},{"📺","Telewizja","tv"},{"🧸","Alfa Kids","kids"},{"🎵","Dźwięki spokoju","sounds"}});
  section("☀ Na co dzień",new String[][]{
   {"🌤","Pogoda","weather"},{"📝","Notatnik","notes"},{"🔔","Przypomnienia","reminders"},{"📖","Czytnik Alfa","reader"}});
  Button mode=new Button(this);mode.setAllCaps(false);mode.setText(senior?"Wyłącz duże elementy":"Włącz większe elementy");
  mode.setTextColor(accent);mode.setBackground(bg(0xffede5f7,0xffd8cbea,14));
  LinearLayout.LayoutParams m=new LinearLayout.LayoutParams(-1,dp(55));m.setMargins(dp(4),dp(19),dp(4),0);
  root.addView(mode,m);
  mode.setOnClickListener(v->{senior=!senior;getPreferences(0).edit().putBoolean("senior",senior).apply();render();});
  TextView info=text("Wersja lekka dla 2 GB RAM • moduły włączają się dopiero po otwarciu • transmisje i LIVE wymagają internetu oraz dostępnego źródła.",12,false,0xff716782);
  info.setGravity(Gravity.CENTER);info.setPadding(dp(12),dp(15),dp(12),0);root.addView(info);
 }
}