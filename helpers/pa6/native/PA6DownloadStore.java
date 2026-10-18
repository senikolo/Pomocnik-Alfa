package com.ispina.lokalnie;
import android.content.*;
import org.json.*;
/** Bounded persistent metadata; never caches media or expiring stream URLs. */
final class PA6DownloadStore {
 static synchronized JSONArray list(Context c){try{return new JSONArray(c.getSharedPreferences("pa6_download",0).getString("items","[]"));}catch(Exception e){return new JSONArray();}}
 static synchronized void put(Context c,JSONObject record){JSONArray old=list(c),next=new JSONArray();next.put(record);String key=record.optString("key");for(int i=0;i<old.length()&&next.length()<30;i++){JSONObject r=old.optJSONObject(i);if(r!=null&&!key.equals(r.optString("key")))next.put(r);}c.getSharedPreferences("pa6_download",0).edit().putString("items",next.toString()).apply();}
}
