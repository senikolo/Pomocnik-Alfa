package com.ispina.lokalnie;
import android.content.Context;
import org.json.JSONObject;
/** Packaged source probe is evidence about a source, not a promise of live playback. */
final class PA6TvAudit {
 private static JSONObject audit;
 static String label(Context c,String name){
  if(audit==null)try(java.io.InputStream in=c.getAssets().open("pa6-tv-audit.json")){
   java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;
   while((n=in.read(b))!=-1){if(bytes.size()+n>200000)throw new java.io.IOException();bytes.write(b,0,n);}
   audit=new JSONObject(bytes.toString("UTF-8"));
  }catch(Exception ignored){audit=new JSONObject();}
  JSONObject channels=audit.optJSONObject("channels"),row=channels==null?null:channels.optJSONObject(name);
  String date=audit.optString("date","");
  if(row!=null&&row.optBoolean("media_accessible"))return "Obraz i dźwięk sprawdzono • "+date;
  return "Niezweryfikowany • dostęp może zależeć od regionu";
 }
}
