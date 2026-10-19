package com.ispina.lokalnie;
import android.app.*;
import android.view.*;
import android.widget.*;
/** Same decoder and SurfaceView in full screen or a movable-in-layout in-app window. API23 has no system PiP. */
final class PA6Fullscreen {
 private Dialog dialog;
 void toggle(Activity activity,PA6EmbeddedPlayer player){if(dialog!=null){dialog.dismiss();return;}show(activity,player,false);}
 void mini(Activity activity,PA6EmbeddedPlayer player){if(dialog!=null)dialog.dismiss();show(activity,player,true);}
 private void show(Activity activity,PA6EmbeddedPlayer player,boolean mini){
  if(!(player.getParent() instanceof ViewGroup)||activity.isFinishing())return;
  final ViewGroup parent=(ViewGroup)player.getParent();final int index=parent.indexOfChild(player);final ViewGroup.LayoutParams old=player.getLayoutParams();
  player.detachSurface();parent.removeView(player);player.setFullscreen(true);
  Dialog d=new Dialog(activity,android.R.style.Theme_Material_NoActionBar_Fullscreen){
   private float x,y;
   @Override public boolean dispatchTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){x=e.getX();y=e.getY();}if(e.getActionMasked()==MotionEvent.ACTION_UP&&Math.abs(e.getX()-x)<12&&Math.abs(e.getY()-y)<12)PA6Haptics.emit(activity,"tap");return super.dispatchTouchEvent(e);}
  };dialog=d;
  LinearLayout host=new LinearLayout(activity);host.setOrientation(1);host.setBackgroundColor(0xff101b31);
  if(mini){LinearLayout bar=new LinearLayout(activity);bar.setGravity(Gravity.CENTER_VERTICAL);TextView label=PA6Premium.text(activity,"  Odtwarzanie w okienku",12,0xffe8f2fa,true);bar.addView(label,new LinearLayout.LayoutParams(0,-2,1));Button close=PA6Premium.button(activity,"Wróć",false);bar.addView(close,new LinearLayout.LayoutParams(PA6Premium.dp(activity,80),PA6Premium.dp(activity,48)));close.setOnClickListener(v->d.dismiss());host.addView(bar);}
  host.addView(player,new LinearLayout.LayoutParams(-1,0,1));d.setContentView(host);
  d.setOnDismissListener(x->{player.detachSurface();host.removeView(player);player.setFullscreen(false);parent.addView(player,Math.min(index,parent.getChildCount()),old);player.attachSurface();dialog=null;});
  d.show();Window w=d.getWindow();
  if(mini){
   int available=activity.getResources().getDisplayMetrics().widthPixels;
   int width=Math.min(PA6Premium.dp(activity,420),available-PA6Premium.dp(activity,24));
   w.setLayout(width,(int)(width*9f/16)+PA6Premium.dp(activity,232));w.setGravity(Gravity.BOTTOM|Gravity.RIGHT);
   w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
  }else{w.setLayout(-1,-1);w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
  player.attachSurface();
 }
 void close(){if(dialog!=null)dialog.dismiss();}
}
