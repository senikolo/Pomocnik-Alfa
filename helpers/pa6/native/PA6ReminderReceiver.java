package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;

/** Explicit, nonexported reminder receiver; restores alarms after restart. */
public final class PA6ReminderReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context ctx,Intent intent){
  if(intent==null)return;
  if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())){PA6ReminderStore.restore(ctx);return;}
  if(!"com.senikolo.pa6tablet.REMIND".equals(intent.getAction()))return;
  String id=intent.getStringExtra("id");if(id==null)return;
  // Never display forged or cancelled alarms: check durable schedule first.
  JSONArray rows=PA6ReminderStore.list(ctx);JSONObject found=null;
  for(int i=0;i<rows.length();i++){
   JSONObject row=rows.optJSONObject(i);if(row!=null&&id.equals(row.optString("id"))){found=row;break;}
  }
  if(found==null)return;
  Intent open=new Intent(ctx,PA6RemindersActivity.class);
  PendingIntent tap=PendingIntent.getActivity(ctx,10,open,PendingIntent.FLAG_UPDATE_CURRENT);
  Notification n=new Notification.Builder(ctx)
   .setSmallIcon(android.R.drawable.ic_dialog_info)
   .setContentTitle("🔔 Pomocnik Alfa")
   .setContentText(found.optString("title","Przypomnienie"))
   .setAutoCancel(true).setDefaults(Notification.DEFAULT_SOUND|Notification.DEFAULT_LIGHTS)
   .setVibrate(PA6Haptics.reminder(ctx))
   .setContentIntent(tap).build();
  NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
  nm.notify(id.hashCode()&0x7fffffff,n);
  PA6ReminderStore.cancel(ctx,id);
 }
}