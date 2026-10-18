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

/** Modern studio-inspired home screen; native Android API23, no WebView on dashboard. */
public final class PA6DashboardActivity extends Activity {
 private static final int BG=0xfff6f8fc,WHITE=Color.WHITE,INK=0xff182338,MUTED=0xff647287,RED=0xffed283d;
 private LinearLayout root;private boolean senior;
 private String displayMode(){
  return getSharedPreferences("pa6_display",MODE_PRIVATE).getString("ui_mode","Automatyczny");
 }
 private void chooseDisplayMode(){
  final String[] modes={"Automatyczny","Tablet","Smartfon"};
  int current=0;
  for(int i=0;i<modes.length;i++)if(modes[i].equals(displayMode()))current=i;
  new AlertDialog.Builder(this).setTitle("Układ Pomocnika Alfa")
   .setSingleChoiceItems(modes,current,(dialog,which)->{
    getSharedPreferences("pa6_display",MODE_PRIVATE).edit()
     .putString("ui_mode",modes[which]).apply();
    dialog.dismiss();render();
   }).setNegativeButton("Anuluj",null).show();
 }
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
 private GradientDrawable shape(int fill,int stroke,int radius){
  GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setStroke(dp(1),stroke);g.setCornerRadius(dp(radius));return g;
 }
 private TextView text(String s,int size,boolean bold,int color){
  TextView t=new TextView(this);t.setText(s);t.setTextSize(size+(senior?2:0));t.setTextColor(color);
  if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
 }
 private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 private void gap(LinearLayout x,int h){x.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
 private View tile(String type,String label,String desc,String key,int tint){
  LinearLayout card=col();card.setGravity(Gravity.CENTER);card.setPadding(dp(12),dp(8),dp(12),dp(8));
  card.setBackground(shape(WHITE,0xffdfe6ee,18));
  LinearLayout badge=row();badge.setGravity(Gravity.CENTER);
  badge.setBackground(shape(0xfff3f5fa,0xffe8edf3,12));
  PA6GlyphView glyph=new PA6GlyphView(this,type,tint);
  badge.addView(glyph,new LinearLayout.LayoutParams(dp(40),dp(40)));
  card.addView(badge,new LinearLayout.LayoutParams(dp(58),dp(56)));
  gap(card,12);
  TextView caption=text(label,15,true,INK);caption.setGravity(Gravity.CENTER);caption.setMaxLines(2);card.addView(caption);
  TextView sub=text(desc,12,false,MUTED);sub.setGravity(Gravity.CENTER);sub.setMaxLines(2);card.addView(sub);
  card.setContentDescription(label+". "+desc);
  card.setOnClickListener(v->open(key));
  return card;
 }
 private void category(String title,String sub,String[][] options){
  gap(root,24);
  TextView h=text(title,20,true,INK);h.setPadding(dp(5),0,0,dp(5));root.addView(h);
  TextView hint=text(sub,13,false,MUTED);hint.setPadding(dp(5),0,0,dp(11));root.addView(hint);
  DisplayMetrics dm=getResources().getDisplayMetrics();
  int width=(int)(dm.widthPixels/dm.density);
  int cols="Smartfon".equals(displayMode())?2:width>=840?4:width>=550?3:2;
  for(int pos=0;pos<options.length;pos+=cols){
   LinearLayout line=row();line.setGravity(Gravity.TOP);root.addView(line);
   for(int j=0;j<cols;j++){
    if(pos+j>=options.length){line.addView(new View(this),new LinearLayout.LayoutParams(0,dp(3),1));continue;}
    String[] entry=options[pos+j];
    View card=tile(entry[0],entry[1],entry[2],entry[3],(int)Long.parseLong(entry[4],16));
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(senior?155:140),1);
    p.setMargins(dp(4),dp(4),dp(4),dp(4));line.addView(card,p);
   }
  }
 }
 private void quick(LinearLayout row,String key,String type,String title,String desc,int tint){
  LinearLayout card=row();card.setPadding(dp(13),dp(11),dp(11),dp(11));
  card.setBackground(shape(0xff172c48,0xff172c48,15));
  PA6GlyphView icon=new PA6GlyphView(this,type,tint);
  card.addView(icon,new LinearLayout.LayoutParams(dp(41),dp(41)));
  LinearLayout column=col();column.setPadding(dp(9),0,0,0);
  column.addView(text(title,15,true,WHITE));
  column.addView(text(desc,11,false,0xffc5d7eb));
  card.addView(column,new LinearLayout.LayoutParams(0,-2,1));
  boolean stacked=row.getOrientation()==LinearLayout.VERTICAL;
  LinearLayout.LayoutParams params=stacked?
   new LinearLayout.LayoutParams(-1,dp(senior?81:75)):
   new LinearLayout.LayoutParams(0,dp(senior?81:75),1);
  params.setMargins(dp(4),0,dp(4),0);row.addView(card,params);
  card.setOnClickListener(v->open(key));
 }
 private void open(String key){
  try{
   Intent i;
   switch(key){
    case "planner":case "bus":case "assistant":case "favorites":
     i=new Intent(this,PARegionsActivity.class).putExtra("pa_tab",key);break;
    case "tube":case "kids":i=new Intent(this,PA6TubeActivity.class).putExtra("kids",key.equals("kids"));break;
    case "tv":i=new Intent(this,PA6TvActivity.class);break;
    case "radio":i=new Intent(this,PA6RadioActivity.class);break;
    case "sounds":i=new Intent(this,PA6SoundsActivity.class);break;
    case "weather":i=new Intent(this,PA6WeatherActivity.class);break;
    case "notes":i=new Intent(this,PA6NotesActivity.class);break;
    case "reminders":i=new Intent(this,PA6RemindersActivity.class);break;
    case "reader":i=new Intent(this,PA6ReaderActivity.class);break;
    default:Toast.makeText(this,"Moduł niedostępny",Toast.LENGTH_SHORT).show();return;
   }
   getPreferences(0).edit().putString("last",key).apply();
   startActivity(i);
  }catch(Exception e){Toast.makeText(this,"Nie udało się otworzyć funkcji.",Toast.LENGTH_LONG).show();}
 }
 private String last(String target){
  switch(target){
   case "tv":return "Telewizja";case "radio":return "Radio";case "tube":return "Alfa Tube";
   case "assistant":return "Pomocnik Alfa";case "planner":return "Planer podróży";
   case "bus":return "Gdzie jest autobus?";case "sounds":return "Dźwięki spokoju";
   case "favorites":return "Moje przystanki";default:return "Ostatnio używane";
  }
 }
 private void render(){
  ScrollView sc=new ScrollView(this);sc.setBackgroundColor(BG);sc.setFillViewport(true);
  root=col();root.setPadding(dp(12),dp(11),dp(12),dp(25));
  // Phone layout remains readable on a 10-inch tablet instead of stretching cards.
  FrameLayout holder=new FrameLayout(this);
  sc.addView(holder,new ScrollView.LayoutParams(-1,-2));
  float widthDp=getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density;
  int contentWidth="Smartfon".equals(displayMode())?(int)Math.min(widthDp,480):-1;
  holder.addView(root,new FrameLayout.LayoutParams(contentWidth<0?-1:dp(contentWidth),-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL));
  setContentView(sc);
  LinearLayout brand=row();brand.setPadding(dp(12),dp(11),dp(12),dp(11));
  brand.setBackground(shape(WHITE,0xffe3e8f0,16));
  ImageView cat=new ImageView(this);
  cat.setImageResource(getResources().getIdentifier("ic_cat","drawable",getPackageName()));
  cat.setScaleType(ImageView.ScaleType.FIT_CENTER);
  brand.addView(cat,new LinearLayout.LayoutParams(dp(59),dp(59)));
  LinearLayout caption=col();caption.setPadding(dp(12),0,0,0);
  caption.addView(text("POMOCNIK ALFA",23,true,INK));
  caption.addView(text("UltraSmart • Android 6 • "+displayMode(),12,false,MUTED));
  brand.addView(caption,new LinearLayout.LayoutParams(0,-2,1));
  Button switchLayout=new Button(this);switchLayout.setText("Układ");
  switchLayout.setAllCaps(false);switchLayout.setTextSize(12);
  switchLayout.setTextColor(INK);switchLayout.setBackground(shape(0xfff1f4f8,0xffdfe6ef,11));
  brand.addView(switchLayout,new LinearLayout.LayoutParams(dp(75),dp(45)));
  switchLayout.setOnClickListener(v->chooseDisplayMode());
  root.addView(brand);gap(root,15);
  LinearLayout highlight=col();highlight.setPadding(dp(18),dp(16),dp(18),dp(17));
  highlight.setBackground(shape(0xffeaf0fb,0xffdce5f5,19));
  highlight.addView(text("Wszystko w jednym miejscu",23,true,INK));
  gap(highlight,6);
  highlight.addView(text("Włącz telewizję, posłuchaj radia, obejrzyj film lub zaplanuj podróż.",14,false,0xff4b627e));
  gap(highlight,15);
  boolean phone="Smartfon".equals(displayMode())||
   ("Automatyczny".equals(displayMode())&&widthDp<520);
  LinearLayout shortcuts=phone?col():row();
  quick(shortcuts,"tv","tv","ALFA TV","PL + rosyjskojęzyczne",0xff65f1d8);
  quick(shortcuts,"tube","tube","ALFA TUBE","Filmy i muzyka",0xffff939e);
  quick(shortcuts,"radio","radio","RADIO","Polskie stacje",0xffffc783);
  highlight.addView(shortcuts);root.addView(highlight);
  String previous=getPreferences(0).getString("last","");
  if(!previous.isEmpty()&&!previous.equals("tv")&&!previous.equals("tube")&&!previous.equals("radio")){
   TextView recent=text("↗  Ostatnio: "+last(previous)+"  •  otwórz",14,true,RED);
   recent.setPadding(dp(14),dp(13),dp(12),dp(6));root.addView(recent);
   recent.setOnClickListener(v->open(previous));
  }
  category("Multimedia","Oglądaj i słuchaj bez przełączania do innych programów",new String[][]{
   {"tv","Telewizja","Kanały w okienku","tv","ffe13349"},
   {"tube","Alfa Tube","Wyszukuj filmy","tube","ffee283d"},
   {"radio","Radio","Stacje i ulubione","radio","ffbf6a37"},
   {"sounds","Dźwięki spokoju","Relaks offline","sounds","ff5872bd"},
   {"kids","Tube Kids","Filmy dla dzieci","kids","ffb85d96"}
  });
  category("Transport i pomoc","Warszawa i Małopolska",new String[][]{
   {"planner","Planer podróży","Trasy i odjazdy","planner","ff3c8b98"},
   {"bus","Gdzie jest autobus?","Sprawdź kurs","bus","ff3c8b98"},
   {"assistant","Porozmawiaj z Alfą","Zapytaj asystenta","assistant","ff7b6cba"},
   {"favorites","Moje przystanki","Szybki dostęp","favorites","ffb78735"}
  });
  category("Na co dzień","Praktyczne narzędzia",new String[][]{
   {"weather","Pogoda","Sprawdź prognozę","weather","ff6096bc"},
   {"notes","Notatnik","Twoje zapiski","notes","ff7e74ba"},
   {"reminders","Przypomnienia","Nie zapomnij","reminders","ffb78735"},
   {"reader","Czytnik Alfa","Dokumenty PDF/TXT","reader","ff3c8b98"}
  });
  gap(root,17);
  Button size=new Button(this);size.setAllCaps(false);size.setTextSize(15);
  size.setText(senior?"Standardowe kafelki":"Powiększ kafelki");
  size.setTextColor(RED);size.setBackground(shape(WHITE,0xffe1e6ed,13));
  root.addView(size,new LinearLayout.LayoutParams(-1,dp(52)));
  size.setOnClickListener(v->{senior=!senior;getPreferences(0).edit().putBoolean("senior",senior).apply();render();});
  gap(root,13);
  TextView footer=text("PA6 Tube Plus • 2.2.1 TEST • kompilacja 2002001",12,false,MUTED);
  footer.setGravity(Gravity.CENTER);root.addView(footer);
 }
 @Override public void onCreate(Bundle b){
  super.onCreate(b);senior=getPreferences(0).getBoolean("senior",false);
  getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(INK);render();
 }
}
