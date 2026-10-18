package com.ispina.lokalnie;
import android.app.*;
import android.view.*;
import android.widget.*;
/** Moves the existing surface; never starts a second decoder. */
final class PA6Fullscreen {
 private Dialog dialog;
 void toggle(Activity activity,PA6EmbeddedPlayer player){
  if(dialog!=null){dialog.dismiss();return;}
  final ViewGroup parent=(ViewGroup)player.getParent();final int index=parent.indexOfChild(player);final ViewGroup.LayoutParams old=player.getLayoutParams();
  player.detachSurface();parent.removeView(player);player.setFullscreen(true);
  Dialog d=new Dialog(activity,android.R.style.Theme_Material_NoActionBar_Fullscreen);dialog=d;
  LinearLayout host=new LinearLayout(activity);host.setOrientation(1);host.setBackgroundColor(0xff000000);
  host.addView(player,new LinearLayout.LayoutParams(-1,-1));d.setContentView(host);
  d.setOnDismissListener(x->{player.detachSurface();host.removeView(player);player.setFullscreen(false);parent.addView(player,index,old);player.attachSurface();dialog=null;});
  d.show();d.getWindow().setLayout(-1,-1);d.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);player.attachSurface();
 }
 void close(){if(dialog!=null)dialog.dismiss();}
}
