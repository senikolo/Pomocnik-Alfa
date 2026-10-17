package com.ispina.lokalnie;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Tiny native vector icon library, consistent on Android 6 without emoji fonts or bitmap loading. */
public final class PA6GlyphView extends View {
 private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final String kind;
 private final int color;
 public PA6GlyphView(Context c,String glyph,int tint){super(c);kind=glyph;color=tint;setLayerType(View.LAYER_TYPE_NONE,null);}
 private void line(Canvas c,float...xy){
  Path path=new Path();path.moveTo(xy[0],xy[1]);
  for(int i=2;i+1<xy.length;i+=2)path.lineTo(xy[i],xy[i+1]);c.drawPath(path,p);
 }
 private void box(Canvas c,float l,float t,float r,float b,float radius){c.drawRoundRect(l,t,r,b,radius,radius,p);}
 private void circle(Canvas c,float x,float y,float radius){c.drawCircle(x,y,radius,p);}
 @Override protected void onDraw(Canvas original){
  super.onDraw(original);
  int size=Math.min(getWidth(),getHeight());if(size<=0)return;
  Canvas c=original;int save=c.save();
  c.translate((getWidth()-size)/2f,(getHeight()-size)/2f);c.scale(size/100f,size/100f);
  p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(6);
  p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
  switch(kind){
   case "tv":
    box(c,13,24,87,75,10);line(c,36,88,64,88);line(c,50,75,50,88);
    p.setStrokeWidth(4);line(c,38,12,50,23,63,12);break;
   case "tube":
    box(c,12,22,88,78,16);
    p.setStyle(Paint.Style.FILL);Path play=new Path();play.moveTo(43,36);play.lineTo(67,50);play.lineTo(43,64);play.close();c.drawPath(play,p);break;
   case "radio":
    box(c,13,29,87,78,10);line(c,22,27,72,12);circle(c,35,55,11);
    line(c,60,49,75,49);line(c,60,60,75,60);break;
   case "planner":
    circle(c,20,28,8);circle(c,80,73,8);
    line(c,20,42,20,64,39,64,39,27,61,27,61,73,66,73);break;
   case "bus":
    box(c,17,23,83,73,9);line(c,20,48,80,48);line(c,50,26,50,48);
    circle(c,32,77,7);circle(c,68,77,7);break;
   case "assistant":
    box(c,13,17,87,72,13);line(c,32,72,27,87,49,73);
    p.setStyle(Paint.Style.FILL);circle(c,33,44,4);circle(c,50,44,4);circle(c,67,44,4);break;
   case "favorites":
    Path star=new Path();for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float r=i%2==0?37:17;float x=50+(float)Math.cos(a)*r,y=50+(float)Math.sin(a)*r;if(i==0)star.moveTo(x,y);else star.lineTo(x,y);}star.close();c.drawPath(star,p);break;
   case "kids":
    circle(c,50,50,35);circle(c,37,42,2);circle(c,63,42,2);
    c.drawArc(34,40,66,71,15,150,false,p);break;
   case "sounds":
    line(c,15,50,24,50,30,32,40,74,48,19,57,81,67,36,74,50,85,50);break;
   case "weather":
    circle(c,33,35,13);line(c,33,13,33,9);line(c,15,35,10,35);
    c.drawArc(23,38,80,88,180,180,false,p);line(c,23,66,77,66);break;
   case "notes":
    box(c,22,13,78,87,7);line(c,34,37,66,37);line(c,34,51,66,51);line(c,34,65,58,65);break;
   case "reminders":
    c.drawArc(25,22,75,79,195,150,false,p);line(c,24,73,76,73);circle(c,50,81,5);break;
   case "reader":
    line(c,50,24,50,80);line(c,50,28,21,18,15,21,15,74,46,83,50,80,54,83,85,74,85,21,79,18,50,28);break;
   default:circle(c,50,50,30);break;
  }
  c.restoreToCount(save);
 }
}
