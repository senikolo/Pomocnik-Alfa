package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.*;

/** Shared schedule store: also receives reminders from Alfa conversation. */
public final class PA6ReminderStore {
 private PA6ReminderStore(){}
 static final String PREF="pa6_reminders";
 static JSONArray list(Context ctx){
  try{return new JSONArray(ctx.getSharedPreferences(PREF,0).getString("items","[]"));}
  catch(Exception e){return new JSONArray();}
 }
 static boolean persist(Context ctx,JSONArray entries){
  return ctx.getSharedPreferences(PREF,0).edit().putString("items",entries.toString()).commit();
 }
 public static boolean saveAndSchedule(Context ctx,String id,String title,String body,long at,String recurrence){
  try{
   if(at<=System.currentTimeMillis())return false;
   JSONArray source=list(ctx),items=new JSONArray();
   // Prune expired one-off reminders so long-lived tablets do not accumulate
   // stale alarms indefinitely; preserve all future items.
   for(int i=0;i<source.length();i++){
    JSONObject prior=source.optJSONObject(i);
    if(prior!=null&&prior.optLong("when",0)>System.currentTimeMillis())items.put(prior);
   }
   if(items.length()>=200)return false;
   JSONObject row=new JSONObject();row.put("id",id);row.put("title",title);row.put("body",body);row.put("when",at);
   items.put(row);
   // Schedule before persisting, and cancel if persistence fails. Never claim a
   // saved reminder if the OS rejected its alarm.
   schedule(ctx,row);
   if(!persist(ctx,items)){cancel(ctx,id);return false;}
   return true;
  }catch(Exception e){return false;}
 }
 static void schedule(Context context,JSONObject row)throws Exception{
  long when=row.getLong("when");if(when<System.currentTimeMillis())return;
  int code=row.getString("id").hashCode()&0x7fffffff;
  Intent intent=new Intent(context,PA6ReminderReceiver.class).setAction("com.senikolo.pa6tablet.REMIND");
  intent.putExtra("id",row.getString("id"));intent.putExtra("title",row.optString("title","Przypomnienie"));
  intent.putExtra("body",row.optString("body",""));
  PendingIntent pending=PendingIntent.getBroadcast(context,code,intent,PendingIntent.FLAG_UPDATE_CURRENT);
  AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
  if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pending);
  else am.setExact(AlarmManager.RTC_WAKEUP,when,pending);
 }
 static void cancel(Context ctx,String id){
  try{
   int code=id.hashCode()&0x7fffffff;
   Intent intent=new Intent(ctx,PA6ReminderReceiver.class).setAction("com.senikolo.pa6tablet.REMIND");
   PendingIntent pi=PendingIntent.getBroadcast(ctx,code,intent,PendingIntent.FLAG_NO_CREATE);
   if(pi!=null){((AlarmManager)ctx.getSystemService(Context.ALARM_SERVICE)).cancel(pi);pi.cancel();}
   JSONArray source=list(ctx),out=new JSONArray();
   for(int i=0;i<source.length();i++){JSONObject obj=source.optJSONObject(i);if(obj!=null&&!id.equals(obj.optString("id")))out.put(obj);}
   persist(ctx,out);
  }catch(Exception ignored){}
 }
 static void restore(Context ctx){
  JSONArray items=list(ctx);
  for(int i=0;i<items.length();i++)try{JSONObject row=items.getJSONObject(i);schedule(ctx,row);}catch(Exception ignored){}
 }
}