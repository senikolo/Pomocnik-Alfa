package com.ispina.lokalnie;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.*;
import android.widget.*;

/** Lightweight native design tokens shared by every screen. No bitmap shadows or UI libraries. */
final class PA6Premium {
 private static final int STYLED=0x7f0f2420;
 static final int BG=0xfff6f7f9,SURFACE=0xfffffefd,INK=0xff182e4b,MUTED=0xff61748b,BLUE=0xff367cad,PALE=0xffeaf2f8;
 static int dp(Context c,int n){return (int)(c.getResources().getDisplayMetrics().density*n+.5f);}
 static GradientDrawable shape(Context c,int color,int border,int radius){
  GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));
  if(border!=0)d.setStroke(dp(c,1),border);return d;
 }
 static GradientDrawable gradient(Context c,int start,int end,int radius){
  GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{start,end});d.setCornerRadius(dp(c,radius));return d;
 }
 static void touch(View view){
  view.setHapticFeedbackEnabled(false);view.setTag(STYLED,Boolean.TRUE);
  if(view.getBackground()!=null&&!(view.getBackground() instanceof RippleDrawable))
   view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x19367cad),view.getBackground(),null));
 }
 static Button button(Context c,String title,boolean primary){
  Button b=new Button(c);b.setText(title);b.setAllCaps(false);b.setTextSize(15);b.setGravity(Gravity.CENTER);
  b.setTextColor(primary?Color.WHITE:INK);b.setMinHeight(dp(c,50));b.setMinimumWidth(0);b.setMinWidth(0);
  b.setPadding(dp(c,14),dp(c,8),dp(c,14),dp(c,8));
  b.setBackground(shape(c,primary?BLUE:SURFACE,primary?0:0xffdce6ef,16));touch(b);return b;
 }
 static TextView text(Context c,String title,int size,int color,boolean strong){
  TextView t=new TextView(c);t.setText(title);t.setTextSize(size);t.setTextColor(color);
  t.setTypeface(Typeface.create(strong?"sans-serif-medium":"sans-serif",Typeface.NORMAL));t.setLineSpacing(dp(c,2),1f);return t;
 }
 static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(1);return l;}
 static void space(LinearLayout l,int n){l.addView(new View(l.getContext()),new LinearLayout.LayoutParams(1,dp(l.getContext(),n)));}
 static String mode(Context c){return c.getSharedPreferences("pa6_display",0).getString("ui_mode","Automatyczny");}
 static boolean wide(Context c){float w=c.getResources().getDisplayMetrics().widthPixels/c.getResources().getDisplayMetrics().density;return !"Smartfon".equals(mode(c))&&w>=("Tablet".equals(mode(c))?600:660);}
 static void field(EditText e){
  e.setTextColor(INK);e.setHintTextColor(MUTED);e.setTextSize(16);
  e.setBackground(shape(e.getContext(),SURFACE,0xffd4e1ec,16));
  e.setPadding(dp(e.getContext(),14),dp(e.getContext(),10),dp(e.getContext(),14),dp(e.getContext(),10));
  e.setMinimumHeight(dp(e.getContext(),50));
 }
 static void polish(View v){
  // Only restyle platform-default widgets; custom player surfaces retain their contrast.
  if(v instanceof EditText){field((EditText)v);return;}
  if(v instanceof Button){
   Button b=(Button)v;b.setAllCaps(false);b.setHapticFeedbackEnabled(false);
   b.setTypeface(Typeface.create("sans-serif-medium",0));b.setStateListAnimator(null);
   if(!(b instanceof CompoundButton)&&!Boolean.TRUE.equals(b.getTag(STYLED))){
    b.setTextColor(INK);b.setBackground(shape(v.getContext(),SURFACE,0xffdce6ef,16));
   }
   if(b instanceof CompoundButton){
    ColorStateList tint=new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xffc2ccd5,BLUE,MUTED});
    ((CompoundButton)b).setButtonTintList(tint);
    if(b instanceof Switch){((Switch)b).setThumbTintList(tint);((Switch)b).setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xffbdd7ea,0xffd9e0e6}));}
   }else touch(b);
  }else if(v instanceof TextView){TextView t=(TextView)v;t.setLineSpacing(dp(v.getContext(),2),1f);}
  if(v instanceof SeekBar){((SeekBar)v).setProgressTintList(ColorStateList.valueOf(BLUE));((SeekBar)v).setThumbTintList(ColorStateList.valueOf(BLUE));}
  if(v instanceof ScrollView){((ScrollView)v).setClipToPadding(false);v.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);}
  if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)polish(g.getChildAt(i));}
 }
}
