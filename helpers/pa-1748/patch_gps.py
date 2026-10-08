#!/usr/bin/env python3
"""PA 1.7.48 GPS: forecast first, network geocode asynchronous, cache-first, optional refine."""
from pathlib import Path
p=Path("project/app/src/main/java/com/ispina/lokalnie/WeatherActivity.java")
s=p.read_text(encoding="utf-8")
def change(before,after,n=1):
 global s
 assert s.count(before)==n,(before[:80],s.count(before))
 s=s.replace(before,after)
change(" private android.widget.TextView gpsDetailsView;",
       " private android.widget.TextView gpsDetailsView;\n private Button gpsRefineButton;\n private boolean gpsRefineMode;\n private int gpsResultVersion;")
change("""  root.addView(gpsDetailsView,new LinearLayout.LayoutParams(-1,-2));""",
"""  root.addView(gpsDetailsView,new LinearLayout.LayoutParams(-1,-2));
  gpsRefineButton=NativeUi.button(this,"🎯 Doprecyzuj lokalizację (cel: 25 m)",true);
  gpsRefineButton.setVisibility(View.GONE);
  gpsRefineButton.setOnClickListener(v->{gpsRefineMode=true;startGpsWeather();});
  root.addView(gpsRefineButton,new LinearLayout.LayoutParams(-1,dp(48)));""")
change("""  if(gpsListening)return;
  if(!hasGpsPermission()){""",
"""  if(gpsListening)return;
  if(!hasGpsPermission()){""")
change("""  gpsListening=true;
  bestGps=null;""",
"""  gpsListening=true;
  ++gpsResultVersion;
  bestGps=null;""")
change("""  if(gpsListening){
   gpsTimeout=()->finishGpsFix();
   ui.postDelayed(gpsTimeout,18000);
  }""",
"""  if(gpsListening){
   gpsTimeout=()->finishGpsFix();
   ui.postDelayed(gpsTimeout,gpsRefineMode?27000:18000);
  }""")
change("""  if(bestGps!=null && bestGps.hasAccuracy() && bestGps.getAccuracy()<=55f)finishGpsFix();""",
"""  if(bestGps!=null && bestGps.hasAccuracy() &&
     bestGps.getAccuracy()<=(gpsRefineMode?25f:55f))finishGpsFix();""")
start=s.index(' private void finishGpsFix(){')
end=s.index(' @Override protected void onStop()',start)
s=s[:start]+''' private void finishGpsFix(){
  if(!gpsListening)return;
  android.location.Location found=bestGps;
  stopGpsUpdates();
  if(found==null){
   cityStatus.setText("Brak aktualnej pozycji GPS. Spróbuj przy oknie albo włącz lokalizację.");
   return;
  }
  final android.location.Location fix=new android.location.Location(found);
  final int searchId=gpsResultVersion;
  float accuracy=fix.hasAccuracy()?fix.getAccuracy():Float.NaN;
  // Forecast starts immediately, without waiting for reverse geocoding.
  String quick=String.format(java.util.Locale.US,"GPS %.4f, %.4f",fix.getLatitude(),fix.getLongitude());
  WeatherRepository.setGpsPlace(new WeatherRepository.Place(quick,"Pozycja GPS",fix.getLatitude(),fix.getLongitude()));
  place=3;selectedDay=-1;forecast=null;
  gpsButton.setText("📍 GPS: ustalam nazwę okolicy…");
  gpsDetailsView.setText("Wyszukuję dzielnicę, osiedle i najbliższą ulicę. Prognoza jest już pobierana.");
  gpsRefineButton.setVisibility(View.VISIBLE);
  cityStatus.setText("Pobrano pozycję"+
    (Float.isFinite(accuracy)?" · dokładność około "+Math.round(accuracy)+" m":" · nieznana dokładność")+
    ". Pobieram prognozę niezależnie od wyszukiwania nazwy.");
  load(true);
  executor.submit(()->{
    GpsMicroArea.Result details=GpsMicroArea.resolve(getApplicationContext(),fix);
    ui.post(()->{
      if(destroyed || searchId!=gpsResultVersion || place!=3)return;
      WeatherRepository.setGpsPlace(new WeatherRepository.Place(details.label,"Pozycja GPS",
         fix.getLatitude(),fix.getLongitude()));
      gpsButton.setText("📍 GPS: "+details.label+" · odśwież");
      String certainty=(!Float.isFinite(accuracy))?"Nieznana dokładność GPS":
        accuracy<=25f?"Pozycja dokładna ("+Math.round(accuracy)+" m)":
        accuracy<=80f?"Pozycja przybliżona ("+Math.round(accuracy)+" m)":
        "Uwaga: mała dokładność GPS ("+Math.round(accuracy)+" m) - osiedle może być sąsiednie";
      gpsDetailsView.setText(details.detail+"\\n"+certainty+
          "\\nNazwy mapowe: "+details.source+
          ". Prognoza opiera się na modelu obszarowym.");
      cityStatus.setText("Twoja okolica: "+details.label+" · "+certainty);
    });
  });
 }
'''+s[end:]
change(" @Override protected void onStop(){stopGpsUpdates();super.onStop();}",
""" @Override protected void onStop(){stopGpsUpdates();gpsRefineMode=false;super.onStop();}""")
change("@Override protected void onDestroy(){stopGpsUpdates();destroyed=true;",
       "@Override protected void onDestroy(){stopGpsUpdates();++gpsResultVersion;destroyed=true;")
# Avoid stale geocode update when switch to a named place; already gated by place index.
p.write_text(s,encoding="utf-8")

p=Path("project/app/src/main/java/com/ispina/lokalnie/GpsMicroArea.java")
s=p.read_text(encoding="utf-8")
a=s.index('    private static Result systemFallback(')
b=s.index('    static synchronized Result resolve(',a)
s=s[:a]+'''    private static Result systemFallback(Context ctx,Location location){
        String coordinates=String.format(Locale.US,"GPS %.4f, %.4f",location.getLatitude(),location.getLongitude());
        return new Result(coordinates,
            "Usługa mapowa nie odpowiedziała; nie przypisuję osiedla na podstawie domysłów. Możesz spróbować ponownie.",
            "współrzędne GPS");
    }

'''+s[b:]
change1='''        Result fallback=systemFallback(ctx,location);
        long now=SystemClock.elapsedRealtime();
        if(cachedResult!=null && now-cachedAtMs<120000L && Double.isFinite(cachedLat)){'''
assert s.count(change1)==1
s=s.replace(change1,'''        long now=SystemClock.elapsedRealtime();
        // Check cache before any network call, to avoid redundant OSM traffic.
        if(cachedResult!=null && now-cachedAtMs<1800000L && Double.isFinite(cachedLat)){''')
anchor='''        // No more than one public OSM request per second; repeated taps return Android fallback.
        if(lastRequestTimeMs>0 && now-lastRequestTimeMs<1300L)return fallback;
        lastRequestTimeMs=now;'''
assert s.count(anchor)==1
s=s.replace(anchor,'''        Result fallback=systemFallback(ctx,location);
        // Single app instance: max one request per 3 seconds, 30-minute local cache.
        if(lastRequestTimeMs>0 && now-lastRequestTimeMs<3000L)return fallback;
        lastRequestTimeMs=now;''')
s=s.replace("connection.setConnectTimeout(4800);","connection.setConnectTimeout(3200);").replace("connection.setReadTimeout(5800);","connection.setReadTimeout(3800);")
s=s.replace('PomocnikAlfa/1.7.47','PomocnikAlfa/1.7.48')
s=s.replace('geokodera urządzenia','mapy OpenStreetMap')
p.write_text(s,encoding="utf-8")
main=Path("project/app/src/main/java/com/ispina/lokalnie/MainActivity.java")
s=main.read_text(encoding="utf-8")
assert "POMOCNIK ALFA 1.7.47" in s
main.write_text(s.replace("POMOCNIK ALFA 1.7.47","POMOCNIK ALFA 1.7.48"),encoding="utf-8")
print("PASS GPS: immediate forecast, 25m refinement, 3s throttle, 30m privacy-preserving cache, no sync Geocoder")
