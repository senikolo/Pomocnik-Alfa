#!/usr/bin/env python3
"""Prepare the real PA regional transport activity for Android API 23.

This is a separate application, not a minSdk manipulation of the Android 10 APK.
The timetable, live-join and OSM sources are reused from helpers/pa2.
"""
from pathlib import Path
import shutil,sys

repo=Path(__file__).resolve().parents[2]
dest=Path(sys.argv[1])
dest.mkdir(parents=True,exist_ok=True)
src=repo/'helpers'/'pa2'
for name in ('PARegionsActivity.java','PALive.java','PADataStore.java','PAOSMTiles.java'):
    code=(src/name).read_text(encoding='utf-8')
    if name=='PARegionsActivity.java':
        code=code.replace('extends Activity', 'extends PA6BaseActivity')
        code=code.replace('public final class Bridge {', 'public final class Bridge { @JavascriptInterface public void tap(){if(!destroyed)runOnUiThread(()->PA6Haptics.emit(PARegionsActivity.this,"tap"));} @JavascriptInterface public String premiumMode(){return PA6Premium.mode(PARegionsActivity.this);}')
        old='if(!nm.areNotificationsEnabled())'
        assert old in code
        code=code.replace(old,'if(Build.VERSION.SDK_INT>=24&&!nm.areNotificationsEnabled())')
        # Android 6 supports a complete WebView interception API, but older WebViews
        # do not support newer JS syntax; scripts are transpiled separately.
        anchor='s.setMediaPlaybackRequiresUserGesture(true);'
        assert anchor in code
        code=code.replace(anchor,anchor+'s.setGeolocationEnabled(true);')
        old='web.setWebChromeClient(new WebChromeClient());'
        assert old in code
        code=code.replace(old,'''web.setWebChromeClient(new WebChromeClient(){
   @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback callback){
    if(!("https://"+HOST).equals(origin)){callback.invoke(origin,false,false);return;}
    if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
     requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},610);
     callback.invoke(origin,false,false);return;
    }
    callback.invoke(origin,true,false);
   }
  });''')
        # API23 UIAutomator cannot expose the DOM of older Chromium. Inspect the
        # actual loaded page with a diagnostic-only evaluateJavascript callback.
        web_client='web.setWebViewClient(new WebViewClient(){'
        assert web_client in code
        code=code.replace(web_client,web_client+'''
   @Override public void onPageFinished(WebView v,String url){
    if(!getIntent().getBooleanExtra("verify_web",false)||!local(Uri.parse(url)))return;
    v.postDelayed(()->{if(destroyed)return;
     final String tab=getIntent().getStringExtra("pa_tab");final String expected="bus".equals(tab)?"liveBus":"assistant".equals(tab)?"assistant":"favorites".equals(tab)?"favorites":"planner";
     v.evaluateJavascript("(function(){var p=document.querySelector('.panel.active');return (p?p.id:'missing')+'|'+typeof window.PANativeReply;})()",value->{
      if(value!=null&&value.contains(expected+"|function"))android.util.Log.i("PA6_WEB_TEST","PASS active="+expected+" bridge=ready");
      else android.util.Log.e("PA6_WEB_TEST","FAIL expected="+expected+" result="+value);
     });
    },1500);
   }
''')
        # The same bridge and per-request allowlist as in the new PA.
        anchor='app\\\\.html|app\\\\.js'
        # The source's literal string contains two slashes to survive Java/regex:
        needle='app\\\\.html|app\\\\.js'
        assert needle in code
        code=code.replace(needle,'app\\\\.html|tablet-legacy\\\\.css|tablet-compat\\\\.js|media-ui\\\\.js|app\\\\.js')
        old='String mime=name.endsWith(".html")?"text/html":name.endsWith(".js")?"application/javascript":"application/json";'
        assert old in code
        code=code.replace(old,'String mime=name.endsWith(".html")?"text/html":name.endsWith(".css")?"text/css":name.endsWith(".js")?"application/javascript":"application/json";')
        # Reuse original dispatch module API and launch API23 native players.
        assert 'names.put("radio","RadioActivity")' in code
        assert 'names.put("tv","TvActivity")' in code
        code=code.replace('names.put("radio","RadioActivity")','names.put("radio","PA6RadioActivity")')
        code=code.replace('names.put("tv","TvActivity")','names.put("tv","PA6TvActivity")')
        code=code.replace('names.put("notes","NotesActivity")','names.put("notes","PA6NotesActivity")')
        code=code.replace('names.put("reminders","RemindersActivity")','names.put("reminders","PA6RemindersActivity")')
        code=code.replace('names.put("weather","WeatherActivity")','names.put("weather","PA6WeatherActivity")')
        code=code.replace('Class.forName("com.ispina.lokalnie.reminders.ReminderScheduler")','Class.forName("com.ispina.lokalnie.PA6ReminderStore")')
        old_load='web.loadUrl("https://"+HOST+"/app.html");'
        assert old_load in code
        code=code.replace(old_load,'''String tab=getIntent().getStringExtra("pa_tab");
  if(!Arrays.asList("planner","bus","assistant","favorites").contains(tab))tab="planner";
  web.loadUrl("https://"+HOST+"/app.html?tab="+tab);''')
    elif name=='PALive.java':
        a='fields.computeIfAbsent(key,k->new ArrayList<>()).add(f);'
        assert a in code
        code=code.replace(a,'List<Field> cur=fields.get(key);if(cur==null){cur=new ArrayList<>();fields.put(key,cur);}cur.add(f);')
        a='fields.getOrDefault(k,Collections.emptyList())'
        assert a in code
        code=code.replace(a,'(fields.containsKey(k)?fields.get(k):Collections.<Field>emptyList())')
        a='fetched.getOrDefault(url,0L)'
        assert a in code
        code=code.replace(a,'(fetched.containsKey(url)?fetched.get(url):0L)')
        # Do not pull PANearbyRules.java from Android 11: it uses java.time
        # unavailable on Android 6. Keep the exact noon-anchored Warsaw
        # service-day epoch semantics of the original GTFS calculations.
        assert 'PANearbyRules.epoch(' in code
        code=code.replace('PANearbyRules.epoch(', 'PA6CompatEpoch.epoch(')
    (dest/name).write_text(code,encoding='utf-8')
(dest/'PA6CompatEpoch.java').write_text("""
package com.ispina.lokalnie;
final class PA6CompatEpoch {
 static long epoch(String date,int seconds)throws Exception{
  if(date==null||!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new IllegalArgumentException("GTFS day");
  java.text.SimpleDateFormat fmt=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.ROOT);
  fmt.setLenient(false);
  fmt.setTimeZone(java.util.TimeZone.getTimeZone("Europe/Warsaw"));
  java.util.Date day=fmt.parse(date);
  java.util.Calendar c=java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Warsaw"),java.util.Locale.ROOT);
  c.setTime(day);c.set(java.util.Calendar.HOUR_OF_DAY,12);
  long noon=c.getTimeInMillis()/1000L;
  return noon-43200L+seconds;
 }
}
""",encoding='utf-8')
for source in (repo/'helpers'/'pa6'/'native').glob('*.java'):
 shutil.copyfile(source,dest/source.name)
print('PASS original PA regions, GTFS-RT, data store and OSM reader API23 compatibility edits')
