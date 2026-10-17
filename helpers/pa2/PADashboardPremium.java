package com.ispina.lokalnie;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Premium visual refresh for the ORIGINAL Android home screen.
 *
 * Styles existing Views only, preserving IDs, onClick listeners, original modules,
 * cat launcher, GPS boards and user data. No replacement of the dashboard.
 */
public final class PADashboardPremium {
 private static final String TAG="PA2Premium";
 private PADashboardPremium(){}
 private static int dp(Context a,float n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
 private static GradientDrawable rounded(Context a,int fill,int stroke,int radius){
  GradientDrawable d=new GradientDrawable();
  d.setColor(fill);d.setCornerRadius(dp(a,radius));
  if(stroke!=0)d.setStroke(dp(a,1),stroke);
  return d;
 }
 private static int nativeColor(Context a,String name,int fallback){
  try{
   Class<?> cls=Class.forName("com.ispina.lokalnie.NativeUi");
   Method m=cls.getMethod(name,Context.class);
   return (Integer)m.invoke(null,a);
  }catch(Exception e){return fallback;}
 }
 private static int ink(Context a){return nativeColor(a,"ink",0xff292239);}
 private static int muted(Context a){return nativeColor(a,"muted",0xff645e77);}
 private static int surface(Context a){return nativeColor(a,"cardColor",0xffffffff);}
 private static int soft(Context a){return nativeColor(a,"soft",0xfff0eaf9);}
 private static int line(Context a){return nativeColor(a,"line",0xffe5ddec);}
 private static boolean dark(int color){
  return (Color.red(color)*299+Color.green(color)*587+Color.blue(color)*114)<130000;
 }
 private static int accent(Context a){return dark(surface(a))?0xffcdb6f4:0xff6846a4;}
 private static String key(String value){
  return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replace('ł','l');
 }

 /** This hook runs after the original MainActivity populated its native screen. */
 public static void install(Activity a){
  if(a==null)return;
  new Handler(Looper.getMainLooper()).postDelayed(()->{
   if(!a.isFinishing()&&!a.isDestroyed())try{apply(a);}catch(Throwable t){Log.w(TAG,"Original dashboard retained",t);}
  },450);
 }
 static void apply(Activity a)throws Exception {
  Field f=a.getClass().getDeclaredField("content");
  f.setAccessible(true);
  Object obj=f.get(a);
  if(!(obj instanceof LinearLayout))throw new IllegalStateException("Native home content not found");
  LinearLayout home=(LinearLayout)obj;
  styleHeader(a);
  for(int i=0;i<home.getChildCount();i++){
   View child=home.getChildAt(i);
   if(child==null||"pa-smart-boards".equals(child.getTag())||"pa-premium-header".equals(child.getTag()))continue;
   if(child instanceof TextView){styleSection(a,(TextView)child);continue;}
   if(!(child instanceof LinearLayout))continue;
   LinearLayout card=(LinearLayout)child;
   try{
    if(card.isClickable())styleAction(a,card);
    else if(hasPanelContent(card))styleInformation(a,card);
   }catch(Throwable t){
    // A bad resource must never prevent styling and navigation for later modules.
    Log.w(TAG,"Card "+i+" icon error",t);
   }
  }
 }
 private static boolean hasPanelContent(ViewGroup v){
  for(int i=0;i<v.getChildCount();i++){
   View x=v.getChildAt(i);
   if(x instanceof TextView&&((TextView)x).length()>3)return true;
   if(x instanceof ViewGroup&&hasPanelContent((ViewGroup)x))return true;
  }
  return false;
 }
 private static void styleSection(Context a,TextView label){
  String text=label.getText().toString();
  if(text.length()<3)return;
  label.setTextColor(accent(a));
  label.setTextSize(11);
  label.setLetterSpacing(.085f);
  label.setAllCaps(false);
  label.setPadding(dp(a,3),dp(a,17),dp(a,3),dp(a,7));
  label.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.BOLD));
 }
 private static TextView firstText(View v){
  if(v instanceof TextView)return(TextView)v;
  if(v instanceof ViewGroup){
   ViewGroup g=(ViewGroup)v;
   for(int i=0;i<g.getChildCount();i++){
    TextView t=firstText(g.getChildAt(i));
    if(t!=null)return t;
   }
  }
  return null;
 }

 /** Professional Lucide vectors, not improvised Canvas sketches or font emoji. */
 private static String iconFor(String title){
  String k=key(title);
  if(k.contains("planer")||k.contains("trasa")||k.contains("podroz"))return "route";
  if(k.contains("przystank")||k.contains("odjazd")||k.contains("autobus")||k.contains("pojazd")||k.contains("transport"))return "bus_front";
  if(k.contains("lotnisk")||k.contains("samolot")||k.contains("loty"))return "plane";
  if(k.contains("telewiz")||k.equals("tv"))return "tv";
  if(k.contains("radio"))return "radio";
  if(k.contains("spokoj")||k.contains("relaks")||k.contains("medytacj"))return "leaf";
  if(k.contains("czytnik")||k.contains("ksiaz")||k.contains("lektur"))return "book_open";
  if(k.contains("przypomn")||k.contains("powiadom")||k.contains("alarm"))return "bell_ring";
  if(k.contains("subskrypcj")||k.contains("kalendarz")||k.contains("termin"))return "calendar_days";
  if(k.contains("smart saver"))return "calculator";
  if(k.contains("oszczed")||k.contains("finans")||k.contains("wydatk"))return "piggy_bank";
  if(k.contains("promoc")||k.contains("zakup")||k.contains("gazetk")||k.contains("lowca"))return "shopping_bag";
  if(k.contains("notat")||k.contains("zapis"))return "notebook_pen";
  if(k.contains("ustawien")||k.contains("motyw")||k.contains("wyglad"))return "settings";
  if(k.contains("ispina")||k.contains("lokalnie")||k.contains("map"))return "map_pin";
  if(k.contains("film")||k.contains("tube")||k.contains("wideo"))return "clapperboard";
  return "grid_2x2";
 }
 private static int iconColor(Context a,String title){
  String k=key(title);
  if(dark(surface(a)))return 0xffd4bffc;
  if(k.contains("radio")||k.contains("telewiz")||k.contains("tube")||k.contains("film"))return 0xffa14d83;
  if(k.contains("notat")||k.contains("czytnik")||k.contains("ksiaz")||k.contains("przypomn")||k.contains("termin"))return 0xff4869a6;
  if(k.contains("relaks")||k.contains("spokoj")||k.contains("oszczed")||k.contains("smart saver"))return 0xff24836d;
  return 0xff6846a4;
 }
 private static int iconSoft(Context a,String title){
  if(dark(surface(a)))return 0xff3b3150;
  String k=key(title);
  if(k.contains("radio")||k.contains("telewiz")||k.contains("tube")||k.contains("film"))return 0xffffeff6;
  if(k.contains("notat")||k.contains("czytnik")||k.contains("ksiaz")||k.contains("przypomn")||k.contains("termin"))return 0xffedf3ff;
  if(k.contains("relaks")||k.contains("spokoj")||k.contains("oszczed")||k.contains("smart saver"))return 0xffe8f7f2;
  return 0xfff1eafa;
 }
 private static void styleAction(Activity a,LinearLayout card){
  if("pa-premium-card".equals(card.getTag()))return;
  if(card.getChildCount()<1||!(card.getChildAt(0) instanceof LinearLayout))return;
  LinearLayout body=(LinearLayout)card.getChildAt(0);
  if(body.getChildCount()<2||!(body.getChildAt(1) instanceof ViewGroup))return;
  ViewGroup textGroup=(ViewGroup)body.getChildAt(1);
  TextView title=firstText(textGroup);
  if(title==null)return;
  String label=title.getText().toString().trim();
  if(label.isEmpty())return;
  int fg=ink(a),muted=muted(a),purple=accent(a),soft=soft(a),fill=surface(a);
  card.setTag("pa-premium-card");
  card.setMinimumHeight(dp(a,78));
  card.setPadding(dp(a,12),dp(a,11),dp(a,12),dp(a,11));
  card.setElevation(dp(a,2));
  GradientDrawable shape=rounded(a,fill,line(a),19);
  card.setBackground(new RippleDrawable(ColorStateList.valueOf(dark(fill)?0x33ffffff:0x226846a4),shape,null));
  card.setContentDescription(null); // Original TextViews and Android navigation remain accessible.
  title.setTextSize(17);
  title.setTextColor(fg);
  title.setMaxLines(3);
  title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.BOLD));
  if(textGroup.getChildCount()>1&&textGroup.getChildAt(1) instanceof TextView){
   TextView summary=(TextView)textGroup.getChildAt(1);
   summary.setTextSize(12.5f);
   summary.setTextColor(muted);
   summary.setMaxLines(3);
   summary.setLineSpacing(0,1.16f);
  }
  View symbol=body.getChildAt(0);
  if(symbol instanceof TextView){
   // A TextView left compound drawable stays left-aligned even when the text
   // gravity is CENTER. Replace the icon cell with a real centered ImageView.
   // The old PA emoji is removed with the original TextView: no double icon.
   TextView original=(TextView)symbol;
   String name=iconFor(label);
   int res=a.getResources().getIdentifier("pa_premium_"+name,"drawable",a.getPackageName());
   if(res!=0){
    Drawable icon=a.getResources().getDrawable(res,a.getTheme()).mutate();
    icon.setTint(iconColor(a,label));
    ImageView centered=new ImageView(a);
    centered.setTag("pa-lucide:"+name);
    centered.setImageDrawable(icon);
    centered.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    centered.setPadding(dp(a,10),dp(a,10),dp(a,10),dp(a,10));
    centered.setBackground(rounded(a,iconSoft(a,label),0,14));
    centered.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    centered.setFocusable(false);
    centered.setClickable(false);
    ViewGroup.LayoutParams oldParams=original.getLayoutParams();
    LinearLayout.LayoutParams newParams=oldParams instanceof LinearLayout.LayoutParams?
      new LinearLayout.LayoutParams((LinearLayout.LayoutParams)oldParams):
      new LinearLayout.LayoutParams(dp(a,48),dp(a,48));
    newParams.width=dp(a,48);newParams.height=dp(a,48);
    body.removeViewAt(0);
    body.addView(centered,0,newParams);
   }else{
    // Leave the original icon untouched when a vector is unavailable.
    Log.w(TAG,"Vector not packaged: "+name+"; original glyph retained");
   }
  }else if(symbol instanceof ImageView){
   // Alfa Tube and Alfa Tube Kids have their own branded artwork.
   // Preserve that art and its original placement, without a second overlay.
  }
  if(body.getChildCount()>=3&&body.getChildAt(2) instanceof TextView){
   TextView arrow=(TextView)body.getChildAt(2);
   arrow.setTextColor(purple);
   arrow.setTextSize(24);
  }
 }
 private static void styleInformation(Activity a,LinearLayout card){
  int fill=surface(a);
  card.setBackground(rounded(a,fill,line(a),19));
  card.setElevation(dp(a,1));
  card.setPadding(dp(a,13),dp(a,13),dp(a,13),dp(a,13));
  // Controls and their handlers inside reminders, radio and shopping remain intact.
 }
 private static void styleHeader(Activity a){
  a.getWindow().setStatusBarColor(0xff4a3377);
  TextView label=search(a.getWindow().getDecorView(),"POMOCNIK ALFA 2.0");
  if(label==null)return;
  int purple=0xff4a3377,lavender=0xff7854b1;
  ViewParent p=label.getParent();
  if(p==null||p.getParent()==null||p.getParent().getParent()==null)return;
  View shell=(View)p.getParent().getParent();
  if(!(shell instanceof LinearLayout))return;
  LinearLayout header=(LinearLayout)shell;
  header.setTag("pa-premium-header"); // Native home header is not an ordinary content card.
  GradientDrawable gd=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{purple,0xff624494,lavender});
  gd.setCornerRadius(dp(a,19));
  header.setBackground(gd);
  header.setPadding(dp(a,12),dp(a,10),dp(a,12),dp(a,12));
  label.setTextSize(10.5f);
  label.setTextColor(0xffeee6ff);
  label.setLetterSpacing(.09f);
  // The greeting is rewritten by MainActivity ("Dzień dobry" / "Pomocnik Alfa").
  // Access the existing View by its field, not by its temporary text.
  try{
   Field greet=a.getClass().getDeclaredField("greeting");greet.setAccessible(true);
   Object gv=greet.get(a);
   if(gv instanceof TextView){TextView g=(TextView)gv;g.setTextSize(21);g.setTextColor(Color.WHITE);}
   Field df=a.getClass().getDeclaredField("headerDate");df.setAccessible(true);
   Object dv=df.get(a);
   if(dv instanceof TextView){TextView d=(TextView)dv;d.setTextSize(12);d.setTextColor(0xffebe4f7);}
  }catch(Exception e){Log.w(TAG,"Original header fields: "+e.getMessage());}
 }
 private static TextView search(View v,String txt){
  if(v instanceof TextView&&txt.equals(((TextView)v).getText().toString()))return(TextView)v;
  if(v instanceof ViewGroup){ViewGroup p=(ViewGroup)v;for(int i=0;i<p.getChildCount();i++){TextView t=search(p.getChildAt(i),txt);if(t!=null)return t;}}
  return null;
 }

 /** A single 24dp outline icon system, independent from device-specific emoji fonts. */
 static final class OutlineGlyph extends Drawable {
  private final String kind;
  private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
  OutlineGlyph(String title,int color,Context ctx){
   kind=key(title);
   pen.setColor(color);pen.setStrokeWidth(1.75f);pen.setStyle(Paint.Style.STROKE);pen.setStrokeCap(Paint.Cap.ROUND);pen.setStrokeJoin(Paint.Join.ROUND);
   fill.setColor(color);fill.setStyle(Paint.Style.FILL);
  }
  void line(Canvas c,float a,float b,float x,float y){c.drawLine(a,b,x,y,pen);}
  void round(Canvas c,float a,float b,float x,float y,float r){c.drawRoundRect(a,b,x,y,r,r,pen);}
  void circle(Canvas c,float x,float y,float r){c.drawCircle(x,y,r,pen);}
  void path(Canvas c,float... pts){Path p=new Path();p.moveTo(pts[0],pts[1]);for(int i=2;i<pts.length;i+=2)p.lineTo(pts[i],pts[i+1]);c.drawPath(p,pen);}
  @Override public int getIntrinsicWidth(){return 27;}
  @Override public int getIntrinsicHeight(){return 27;}
  @Override public void draw(Canvas c){
   Rect b=getBounds();if(b.width()==0||b.height()==0)return;
   c.save();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);
   if(has("planer","trasa","podroz")){circle(c,5,6,2);circle(c,19,18,2);path(c,7,6,15,6,15,12,9,12,9,18,17,18);}
   else if(has("przystank","odjazd","autobus","pojazd","transport")){round(c,5,3,19,20,3);line(c,5,12,19,12);line(c,9,7,15,7);line(c,8,20,7,22);line(c,16,20,17,22);circle(c,8,16,1);circle(c,16,16,1);}
   else if(has("alfa tube kids","dziec","bajki")){circle(c,12,13,7);circle(c,6,6,2);circle(c,18,6,2);circle(c,9,12,1);circle(c,15,12,1);path(c,10,16,12,17,14,16);}
   else if(has("tube","wideo","film")){round(c,3,5,21,19,3);path(c,10,8,16,12,10,16,10,8);}
   else if(has("radio")){round(c,3,8,21,20,2);line(c,5,8,16,3);line(c,7,12,14,12);circle(c,17,15,2);}
   else if(has("telewiz","tv")){round(c,3,5,21,18,2);line(c,12,18,12,21);line(c,8,21,16,21);}
   else if(has("spokoj","relaks","wycisz")){path(c,4,19,7,12,12,5,19,3,19,10,14,17,8,20,4,19);line(c,7,20,16,10);}
   else if(has("czytnik","książ","ksiaz","lektur")){path(c,12,6,8,4,3,4,3,19,8,19,12,21,16,19,21,19,21,4,16,4,12,6,12,21);}
   else if(has("przypomn","powiadom","alarm")){path(c,5,17,7,14,7,10,8,7,12,5,16,7,17,10,17,14,19,17,5,17);circle(c,12,20,1.5f);}
   else if(has("subskrypcj","kalendarz","termin")){round(c,4,5,20,21,2);line(c,4,10,20,10);line(c,8,3,8,7);line(c,16,3,16,7);circle(c,12,15,2.5f);}
   else if(has("smart saver","oszczedz","finans")){round(c,3,6,21,19,3);round(c,13,10,21,16,2);circle(c,17,13,0.8f);}
   else if(has("promoc","zakup","taniej","alerty")){path(c,3,5,6,5,9,17,19,17,21,8,7,8);circle(c,9,21,1);circle(c,18,21,1);}
   else if(has("notat","zapis")){round(c,5,3,19,21,2);line(c,9,9,16,9);line(c,9,13,16,13);line(c,9,17,14,17);}
   else if(has("lotnisk","nasluch","samolot")){path(c,12,2,14,10,21,14,21,16,13,14,13,20,16,22,8,22,11,20,11,14,3,16,3,14,10,10,12,2);}
   else if(has("ustawien","motyw","wyglad")){circle(c,12,12,3.5f);for(int i=0;i<8;i++){double r=i*Math.PI/4;line(c,(float)(12+7*Math.cos(r)),(float)(12+7*Math.sin(r)),(float)(12+10*Math.cos(r)),(float)(12+10*Math.sin(r)));}}
   else if(has("ispina","lokalnie")){path(c,3,19,6,15,9,18,14,8,19,11,21,6);line(c,3,21,21,21);}
   else {round(c,4,4,10,10,2);round(c,14,4,20,10,2);round(c,4,14,10,20,2);round(c,14,14,20,20,2);}
   c.restore();
  }
  private boolean has(String... xs){for(String x:xs)if(kind.contains(x))return true;return false;}
  @Override public void setAlpha(int alpha){pen.setAlpha(alpha);fill.setAlpha(alpha);}
  @Override public void setColorFilter(ColorFilter filter){pen.setColorFilter(filter);fill.setColorFilter(filter);}
  @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
}