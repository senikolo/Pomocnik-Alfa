package com.ispina.lokalnie;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.os.Vibrator;

/** API23 uses duration/pattern only. No amplitude API, no background click polling. */
public final class PA6Haptics {
 private PA6Haptics(){}
 private static long last;
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("pa6_comfort",0);}
 public static boolean available(Context c){
  try{Vibrator v=(Vibrator)c.getSystemService(Context.VIBRATOR_SERVICE);return v!=null&&v.hasVibrator();}
  catch(RuntimeException e){return false;}
 }
 static boolean enabled(Context c){return available(c)&&prefs(c).getBoolean("vibration",true);}
 static long[] pattern(int strength,String event){
  int level=Math.max(0,Math.min(2,strength));
  long tap=new long[]{12,24,40}[level];
  if("success".equals(event))return new long[]{0,tap,65,tap+8};
  if("error".equals(event))return new long[]{0,tap+20,90,tap+20,90,tap+20};
  if("reminder".equals(event))return new long[]{0,90+level*60,140,90+level*60,180,140+level*60};
  return new long[]{0,tap};
 }
 static long[] reminder(Context c){return enabled(c)&&prefs(c).getBoolean("reminder_vibration",true)?pattern(prefs(c).getInt("strength",0),"reminder"):new long[]{0};}
 public static void emit(Context c,String event){
  if(!enabled(c))return;
  if(!"tap".equals(event)&&!"test".equals(event)&&!prefs(c).getBoolean("signals",true))return;
  long now=SystemClock.elapsedRealtime();if("tap".equals(event)&&now-last<85)return;last=now;
  try{((Vibrator)c.getSystemService(Context.VIBRATOR_SERVICE)).vibrate(pattern(prefs(c).getInt("strength",0),event),-1);}
  catch(RuntimeException ignored){}
 }
}
