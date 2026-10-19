package com.ispina.lokalnie;
import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import org.json.*;
import java.io.File;

public final class PA6DownloadsActivity extends PA6BaseActivity {
 private final Handler main=new Handler();private LinearLayout list;private ScrollView scroll;private String shown="";
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 private void button(LinearLayout box,String text,Runnable action){Button b=PA6Premium.button(this,text,false);box.addView(b,new LinearLayout.LayoutParams(-1,dp(50)));b.setOnClickListener(v->action.run());}
 private void retry(JSONObject r){if(checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},11);Toast.makeText(this,"Po przyznaniu dostępu ponownie wybierz Ponów.",Toast.LENGTH_LONG).show();return;}startService(new Intent(this,PA6DownloadService.class).putExtra("id",r.optString("id")).putExtra("quality",r.optInt("quality")).putExtra("wifi",r.optBoolean("wifi",false)));}
 private void render(){JSONArray records=PA6DownloadStore.list(this);String raw=records.toString();if(raw.equals(shown))return;shown=raw;int y=scroll.getScrollY();list.removeAllViews();
  if(records.length()==0){TextView empty=new TextView(this);empty.setText("Brak pobrań. W Alfa Tube wybierz film i Pobierz MP4.");empty.setTextSize(17);list.addView(empty);}
  for(int i=0;i<records.length();i++){final JSONObject r=records.optJSONObject(i);if(r==null)continue;LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(14),dp(14),dp(14),dp(14));box.setBackground(PA6Premium.shape(this,PA6Premium.SURFACE,0xffe0e8ef,20));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(12));list.addView(box,p);
   TextView title=new TextView(this);title.setText(r.optString("title","Film"));title.setTextColor(PA6Premium.INK);title.setTextSize(18);box.addView(title);
   TextView message=new TextView(this);message.setText(r.optString("message"));message.setTextSize(14);message.setPadding(0,dp(8),0,dp(8));box.addView(message);
   String state=r.optString("state");if("running".equals(state)){ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(r.optInt("percent",-1)<0);progress.setProgress(r.optInt("percent"));box.addView(progress);button(box,"Anuluj pobieranie",()->startService(new Intent(this,PA6DownloadService.class).setAction("CANCEL").putExtra("key",r.optString("key"))));}
   else if("complete".equals(state)){button(box,"▶ Odtwórz z pamięci",()->{if(!new File(r.optString("path")).isFile()){Toast.makeText(this,"Plik został usunięty lub przeniesiony.",Toast.LENGTH_LONG).show();return;}startActivity(new Intent(this,PA6TubeActivity.class).putExtra("offline_path",r.optString("path")));});}
   else button(box,"Ponów pobieranie",()->retry(r));
  }scroll.post(()->scroll.scrollTo(0,y));
 }
 private final Runnable tick=new Runnable(){public void run(){render();main.postDelayed(this,1000);}};
 @Override public void onCreate(Bundle b){super.onCreate(b);LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setPadding(dp(16),dp(12),dp(16),0);page.setBackgroundColor(PA6Premium.BG);TextView title=new TextView(this);title.setText("Pobrane filmy");title.setTextSize(25);title.setTextColor(PA6Premium.INK);page.addView(title);button(page,"← Wróć",()->finish());scroll=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(1);scroll.addView(list);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(page);}
 @Override protected void onStart(){super.onStart();main.post(tick);}
 @Override protected void onStop(){main.removeCallbacks(tick);super.onStop();}
}
