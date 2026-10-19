package com.ispina.lokalnie;
import android.app.*;
import android.view.*;
import android.widget.*;
/** API23 window modes share one Dialog and one decoder. Restore is synchronous/idempotent. */
final class PA6Fullscreen {
 private Dialog dialog;private boolean small;
 private ViewGroup originalParent;private int originalIndex;private ViewGroup.LayoutParams originalParams;
 private LinearLayout host,miniBar;private PA6EmbeddedPlayer currentPlayer;
 void toggle(Activity activity,PA6EmbeddedPlayer player){
  if(dialog!=null){if(small)resize(activity,false);else close();return;}
  show(activity,player,false);
 }
 void mini(Activity activity,PA6EmbeddedPlayer player){
  if(dialog!=null){resize(activity,true);return;}show(activity,player,true);
 }
 private void resize(Activity activity,boolean mini){
  if(dialog==null)return;small=mini;miniBar.setVisibility(mini?View.VISIBLE:View.GONE);
  Window w=dialog.getWindow();
  if(mini){
   int available=activity.getResources().getDisplayMetrics().widthPixels;
   int width=Math.min(PA6Premium.dp(activity,420),available-PA6Premium.dp(activity,24));
   w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
   w.setLayout(width,(int)(width*9f/16)+PA6Premium.dp(activity,232));w.setGravity(Gravity.BOTTOM|Gravity.RIGHT);
   w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
  }else{
   w.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_DIM_BEHIND);
   w.setGravity(Gravity.CENTER);w.setLayout(-1,-1);
   w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  }
 }
 private void show(Activity activity,PA6EmbeddedPlayer player,boolean mini){
  if(!(player.getParent() instanceof ViewGroup)||activity.isFinishing())return;
  originalParent=(ViewGroup)player.getParent();originalIndex=originalParent.indexOfChild(player);originalParams=player.getLayoutParams();currentPlayer=player;
  player.detachSurface();originalParent.removeView(player);player.setFullscreen(true);
  final Dialog d=new Dialog(activity,android.R.style.Theme_Material_NoActionBar_Fullscreen){
   private float x,y;
   @Override public boolean dispatchTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){x=e.getX();y=e.getY();}if(e.getActionMasked()==MotionEvent.ACTION_UP&&Math.abs(e.getX()-x)<12&&Math.abs(e.getY()-y)<12)PA6Haptics.emit(activity,"tap");return super.dispatchTouchEvent(e);}
  };dialog=d;
  host=new LinearLayout(activity);host.setOrientation(1);host.setBackgroundColor(0xff101b31);
  miniBar=new LinearLayout(activity);miniBar.setGravity(Gravity.CENTER_VERTICAL);
  TextView label=PA6Premium.text(activity,"  Odtwarzanie w okienku",12,0xffe8f2fa,true);miniBar.addView(label,new LinearLayout.LayoutParams(0,-2,1));
  Button back=PA6Premium.button(activity,"Wróć",false);miniBar.addView(back,new LinearLayout.LayoutParams(PA6Premium.dp(activity,80),PA6Premium.dp(activity,48)));back.setOnClickListener(v->close());
  host.addView(miniBar);host.addView(player,new LinearLayout.LayoutParams(-1,0,1));d.setContentView(host);
  d.setOnDismissListener(x->{if(dialog==d)restore(d,false);});
  d.show();resize(activity,mini);player.post(()->{if(dialog==d)player.attachSurface();});
 }
 private void restore(Dialog expected,boolean dismiss){
  if(dialog!=expected)return;
  // onDismiss is dispatched through a Handler on API23. Clear ownership BEFORE
  // dismiss, so a late callback cannot reparent a view already in a new window.
  dialog=null;expected.setOnDismissListener(null);
  PA6EmbeddedPlayer player=currentPlayer;ViewGroup parent=originalParent;
  currentPlayer=null;originalParent=null;
  if(player!=null&&parent!=null){
   player.detachSurface();if(player.getParent() instanceof ViewGroup)((ViewGroup)player.getParent()).removeView(player);
   player.setFullscreen(false);parent.addView(player,Math.min(originalIndex,parent.getChildCount()),originalParams);
   player.post(player::attachSurface);
  }
  host=null;miniBar=null;
  if(dismiss)expected.dismiss();
 }
 void close(){if(dialog!=null)restore(dialog,true);}
}
