package com.ispina.lokalnie;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Shared navigation and haptics; no recurring view scans or global layout listeners. */
public abstract class PA6BaseActivity extends Activity {
 private String activeMode;
 private float downX,downY;private boolean moved;private View tapTarget;
 @Override protected void onCreate(Bundle b){super.onCreate(b);activeMode=PA6Premium.mode(this);getWindow().setStatusBarColor(PA6Premium.INK);getWindow().setNavigationBarColor(PA6Premium.INK);}
 @Override protected void onResume(){super.onResume();getWindow().setStatusBarColor(PA6Premium.INK);getWindow().setNavigationBarColor(PA6Premium.INK);if(!(this instanceof PA6DashboardActivity)&&!(this instanceof PA6SettingsActivity)&&!PA6Premium.mode(this).equals(activeMode)){activeMode=PA6Premium.mode(this);recreate();}}
 @Override public void setContentView(View content){
  if(content instanceof ScrollView && !(this instanceof PA6DashboardActivity) && !(this instanceof PA6SettingsActivity)){
   ScrollView scroll=(ScrollView)content;
   if(scroll.getChildCount()==1){View child=scroll.getChildAt(0);scroll.removeView(child);FrameLayout center=new FrameLayout(this);
    int width=(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);
    int max=Math.min(width,"Smartfon".equals(PA6Premium.mode(this))?480:960);
    center.addView(child,new FrameLayout.LayoutParams(PA6Premium.dp(this,max),-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL));scroll.addView(center);}
  }
  if(this instanceof PA6DashboardActivity){super.setContentView(content);content.post(()->PA6Premium.polish(content));return;}
  LinearLayout shell=PA6Premium.column(this);shell.setBackgroundColor(PA6Premium.BG);
  LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(PA6Premium.dp(this,12),PA6Premium.dp(this,5),PA6Premium.dp(this,12),PA6Premium.dp(this,5));
  Button back=PA6Premium.button(this,"‹  Główna",false);back.setContentDescription("Wróć do ekranu głównego");
  bar.addView(back,new LinearLayout.LayoutParams(PA6Premium.dp(this,114),PA6Premium.dp(this,48)));
  back.setOnClickListener(v->{startActivity(new Intent(this,PA6DashboardActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();});
  TextView brand=PA6Premium.text(this,"ALFA",12,PA6Premium.MUTED,true);brand.setGravity(Gravity.CENTER);bar.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
  if(!(this instanceof PA6SettingsActivity)){
   Button settings=PA6Premium.button(this,"Ustawienia",false);bar.addView(settings,new LinearLayout.LayoutParams(PA6Premium.dp(this,116),PA6Premium.dp(this,48)));
   settings.setOnClickListener(v->startActivity(new Intent(this,PA6SettingsActivity.class)));
  }
  shell.addView(bar);shell.addView(content,new LinearLayout.LayoutParams(-1,0,1));super.setContentView(shell);
  content.post(()->PA6Premium.polish(content));
  if(PA6Haptics.prefs(this).getBoolean("motion",true)){content.setAlpha(.85f);content.animate().alpha(1f).setDuration(160).start();}
 }
 private View clickable(View v,float x,float y){
  if(v.getVisibility()!=View.VISIBLE||v instanceof android.webkit.WebView)return null;
  int[] location=new int[2];v.getLocationOnScreen(location);
  if(x<location[0]||y<location[1]||x>=location[0]+v.getWidth()||y>=location[1]+v.getHeight())return null;
  if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=g.getChildCount()-1;i>=0;i--){View found=clickable(g.getChildAt(i),x,y);if(found!=null)return found;}}
  return v.isEnabled()&&v.isClickable()&&!(v instanceof EditText)?v:null;
 }
 @Override public boolean dispatchTouchEvent(MotionEvent e){
  int a=e.getActionMasked();
  if(a==MotionEvent.ACTION_DOWN){downX=e.getRawX();downY=e.getRawY();moved=false;tapTarget=clickable(getWindow().getDecorView(),downX,downY);}
  if(a==MotionEvent.ACTION_MOVE&&(Math.abs(e.getRawX()-downX)>PA6Premium.dp(this,12)||Math.abs(e.getRawY()-downY)>PA6Premium.dp(this,12)))moved=true;
  if(a==MotionEvent.ACTION_UP){if(!moved&&tapTarget!=null&&tapTarget==clickable(getWindow().getDecorView(),e.getRawX(),e.getRawY()))PA6Haptics.emit(this,"tap");tapTarget=null;}
  if(a==MotionEvent.ACTION_CANCEL)tapTarget=null;
  return super.dispatchTouchEvent(e);
 }
}
