package com.ispina.lokalnie;
import android.content.*;
/** One active, validated timetable generation shared by dashboard and planner. */
final class PADataStore {
 static synchronized SharedPreferences preferences(Context c){SharedPreferences target=c.getSharedPreferences("pa2_data",0);if(!target.contains("data_folder"))for(String name:new String[]{"com.ispina.lokalnie.PARegionsActivity","PARegionsActivity"}){String old=c.getSharedPreferences(name,0).getString("data_folder","");if(old.matches("data-[0-9]+")&&new java.io.File(c.getFilesDir(),old+"/index_all.json").isFile()){target.edit().putString("data_folder",old).commit();break;}}if(target.getInt("transport_revision",0)<11){target.edit().remove("data_folder").putInt("transport_revision",11).commit();}return target;}
}
