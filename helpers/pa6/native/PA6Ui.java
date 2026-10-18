package com.ispina.lokalnie;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/** Shared lightweight native design system. No AppCompat or WebView dependency. */
public final class PA6Ui {
 public static final int BG=0xfff4f7fb, WHITE=0xffffffff, INK=0xff192b45,
   MUTED=0xff546980, BORDER=0xffdce5ef, PRIMARY=0xff174875, ACCENT=0xffe92e49;
 private PA6Ui(){}
 public static int dp(Context c,int v){return (int)(v*c.getResources().getDisplayMetrics().density+.5f);}
 public static GradientDrawable surface(Context c,int fill,int stroke,int radius){
  GradientDrawable d=new GradientDrawable();
  d.setColor(fill);d.setStroke(dp(c,1),stroke);d.setCornerRadius(dp(c,radius));return d;
 }
 public static TextView text(Context c,String title,int sp,boolean bold){
  TextView v=new TextView(c);
  v.setText(title);v.setTextColor(INK);v.setTextSize(sp);
  if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
  v.setPadding(dp(c,8),dp(c,9),dp(c,8),dp(c,9));
  return v;
 }
 public static Button button(Context c,String title,boolean primary){
  Button b=new Button(c);b.setText(title);b.setAllCaps(false);b.setTextSize(16);
  b.setMinHeight(dp(c,52));b.setTextColor(primary?WHITE:PRIMARY);
  b.setBackground(surface(c,primary?PRIMARY:WHITE,primary?PRIMARY:BORDER,13));
  return b;
 }
 public static void input(Context c,EditText field){
  field.setTextSize(17);field.setTextColor(INK);field.setHintTextColor(MUTED);
  field.setBackground(surface(c,WHITE,BORDER,13));
  field.setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10));
 }
}