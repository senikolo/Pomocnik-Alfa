package com.ispina.lokalnie.radio;
import android.app.Activity;import android.content.Intent;import android.net.Uri;import android.os.Build;import android.os.Bundle;
public class RadioCommandActivity extends Activity {
 @Override protected void onCreate(Bundle b){super.onCreate(b);handle(getIntent());finish();}
 @Override protected void onNewIntent(Intent i){super.onNewIntent(i);handle(i);finish();}
 private void handle(Intent incoming){Uri d=incoming==null?null:incoming.getData();if(d==null)return;String cmd=d.getHost();if(cmd==null)cmd="";Intent s=new Intent(this,RadioService.class);if("play".equalsIgnoreCase(cmd)){s.setAction(RadioService.ACTION_PLAY);s.putExtra("name",d.getQueryParameter("name"));s.putExtra("url",d.getQueryParameter("url"));}else if("toggle".equalsIgnoreCase(cmd))s.setAction(RadioService.ACTION_TOGGLE);else if("stop".equalsIgnoreCase(cmd))s.setAction(RadioService.ACTION_STOP);else return;try{if(Build.VERSION.SDK_INT>=26&&!RadioService.ACTION_STOP.equals(s.getAction()))startForegroundService(s);else startService(s);}catch(Throwable ignored){}}
}
