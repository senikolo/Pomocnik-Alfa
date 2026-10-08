#!/usr/bin/env python3
"""Opt-in, one-shot GPS weather for Pomocnik Alfa: no location on screen open."""
from pathlib import Path
import re

base=Path("project/app/src/main")
repo=base/"java/com/ispina/lokalnie/weather/WeatherRepository.java"
s=repo.read_text(encoding="utf-8")
anchor=" public static Place custom(Context c){"
assert s.count(anchor)==1
s=s.replace(anchor,
""" // GPS choice exists only in process memory; it is never captured automatically.
 private static volatile Place gpsPlace;
 public static void setGpsPlace(Place value){gpsPlace=value;}
 public static Place gpsPlace(){return gpsPlace;}
"""+anchor)
target="public static Place place(Context c,int index){if(index==2){"
assert s.count(target)==1
s=s.replace(target,"public static Place place(Context c,int index){if(index==3 && gpsPlace!=null)return gpsPlace;if(index==2){")
# Never persist sensitive coordinates in shared weather_cache.
target='c.getSharedPreferences("weather_cache",0).edit().putString(point.key(),saved.toString()).apply();return new Forecast(data,now,place,point);'
assert s.count(target)==1
s=s.replace(target,'if(place!=3)c.getSharedPreferences("weather_cache",0).edit().putString(point.key(),saved.toString()).apply();return new Forecast(data,now,place,point);')
target='public static Forecast cached(Context c,int place){try{'
assert s.count(target)==1
s=s.replace(target,'public static Forecast cached(Context c,int place){if(place==3)return null;try{')
repo.write_text(s,encoding="utf-8")

p=base/"java/com/ispina/lokalnie/WeatherActivity.java"
s=p.read_text(encoding="utf-8")
anchor=" private Forecast forecast;private Future<?> task;"
assert s.count(anchor)==1
s=s.replace(anchor,anchor+"""
 private Button gpsButton;
 private android.location.LocationManager gpsManager;
 private android.location.LocationListener gpsListener;
 private android.location.Location bestGps;
 private Runnable gpsTimeout;
 private boolean gpsListening;
 private static final int GPS_PERMISSION_REQUEST=8745;
""")
# Remember GPS on rotation within the process, but never locate again without tapping the GPS button.
anchor='place=place==2 && WeatherRepository.custom(this)!=null?2:place==1?1:0;'
assert s.count(anchor)==1
s=s.replace(anchor,
    'place=place==3 && WeatherRepository.gpsPlace()!=null?3:place==2 && WeatherRepository.custom(this)!=null?2:place==1?1:0;')
anchor='root.addView(places[2],new LinearLayout.LayoutParams(-1,-2));'
assert s.count(anchor)==1
s=s.replace(anchor,anchor+"""
  NativeUi.addSpacer(root,this,9);
  gpsButton=NativeUi.button(this,"📍 Moja lokalizacja GPS · dotknij, aby ustalić",true);
  gpsButton.setOnClickListener(v->startGpsWeather());
  root.addView(gpsButton,new LinearLayout.LayoutParams(-1,dp(56)));
""")
anchor='for(int i=0;i<3;i++)style(tabs[i],tab==i);}'
assert s.count(anchor)==1
s=s.replace(anchor,'if(gpsButton!=null)style(gpsButton,place==3);'+anchor)
# The forecast is grid-model based, not a promise of weather accuracy at street level.
s=s.replace("Prognoza: Open-Meteo · okolice wybranego miejsca",
            "Prognoza: Open-Meteo · najbliższa siatka modelu meteorologicznego")
# Selected GPS station can be ephemeral, still refresh forecast on demand.
methods=r'''
 private boolean hasGpsPermission(){
  if(android.os.Build.VERSION.SDK_INT<23)return true;
  return checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED ||
         checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED;
 }
 private void startGpsWeather(){
  if(gpsListening)return;
  if(!hasGpsPermission()){
   if(android.os.Build.VERSION.SDK_INT>=23){
    requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION},GPS_PERMISSION_REQUEST);
   }
   return;
  }
  acquireGps();
 }
 @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){
  super.onRequestPermissionsResult(code,permissions,grants);
  if(code!=GPS_PERMISSION_REQUEST)return;
  if(hasGpsPermission())acquireGps();
  else cityStatus.setText("Bez zgody na lokalizację nadal możesz korzystać z Ispiny, Warszawy i wyszukiwania miejscowości.");
 }
 private void acquireGps(){
  gpsManager=(android.location.LocationManager)getSystemService(LOCATION_SERVICE);
  if(gpsManager==null){cityStatus.setText("GPS nie jest dostępny na tym urządzeniu.");return;}
  gpsListening=true;
  bestGps=null;
  gpsButton.setEnabled(false);
  cityStatus.setText("Ustalam lokalizację tylko teraz. Może to potrwać kilkanaście sekund…");
  gpsListener=new android.location.LocationListener(){
   @Override public void onLocationChanged(android.location.Location location){considerGpsFix(location);}
   @Override public void onStatusChanged(String provider,int status,android.os.Bundle extras){}
   @Override public void onProviderEnabled(String provider){}
   @Override public void onProviderDisabled(String provider){}
  };
  boolean any=false;
  for(String provider:new String[]{android.location.LocationManager.GPS_PROVIDER,
                                  android.location.LocationManager.NETWORK_PROVIDER}){
   try{
    if(!gpsManager.isProviderEnabled(provider))continue;
    any=true;
    android.location.Location old=gpsManager.getLastKnownLocation(provider);
    considerGpsFix(old);
    if(gpsListening)gpsManager.requestLocationUpdates(provider,1000,0f,gpsListener,android.os.Looper.getMainLooper());
   }catch(SecurityException ignored){}catch(IllegalArgumentException ignored){}
  }
  if(!any){
   stopGpsUpdates();
   cityStatus.setText("Włącz lokalizację w systemie Android i dotknij kafelka GPS ponownie.");
   return;
  }
  if(gpsListening){
   gpsTimeout=()->finishGpsFix();
   ui.postDelayed(gpsTimeout,18000);
  }
 }
 private void considerGpsFix(android.location.Location loc){
  if(!gpsListening || loc==null)return;
  if(!Double.isFinite(loc.getLatitude()) || !Double.isFinite(loc.getLongitude()))return;
  // Avoid stale results; accuracy comes from Android, not from the weather provider.
  if(loc.getTime()>0 && Math.abs(System.currentTimeMillis()-loc.getTime())>180000L)return;
  if(bestGps==null || (loc.hasAccuracy() && (!bestGps.hasAccuracy() ||
       loc.getAccuracy()<bestGps.getAccuracy())))bestGps=loc;
  if(bestGps!=null && bestGps.hasAccuracy() && bestGps.getAccuracy()<=55f)finishGpsFix();
 }
 private void stopGpsUpdates(){
  if(gpsTimeout!=null){ui.removeCallbacks(gpsTimeout);gpsTimeout=null;}
  if(gpsManager!=null && gpsListener!=null){
   try{gpsManager.removeUpdates(gpsListener);}catch(Exception ignored){}
  }
  gpsListener=null;gpsListening=false;
  if(gpsButton!=null)gpsButton.setEnabled(true);
 }
 private static String nonBlank(String a,String b){
  return a!=null && !a.trim().isEmpty()?a.trim():(b==null?"":b.trim());
 }
 private static String gpsAddress(android.content.Context ctx,android.location.Location loc){
  String fallback=String.format(java.util.Locale.US,"GPS %.4f, %.4f",loc.getLatitude(),loc.getLongitude());
  if(!android.location.Geocoder.isPresent())return fallback;
  try{
   java.util.List<android.location.Address> addresses=
    new android.location.Geocoder(ctx,new java.util.Locale("pl","PL"))
     .getFromLocation(loc.getLatitude(),loc.getLongitude(),1);
   if(addresses==null || addresses.isEmpty())return fallback;
   android.location.Address a=addresses.get(0);
   String town=nonBlank(a.getLocality(),nonBlank(a.getSubAdminArea(),a.getAdminArea()));
   String district=nonBlank(a.getSubLocality(),"");
   if(district.isEmpty())district=nonBlank(a.getThoroughfare(),"");
   if(town.isEmpty())return district.isEmpty()?fallback:district;
   return !district.isEmpty() && !town.equalsIgnoreCase(district)?town+" · "+district:town;
  }catch(Exception ignored){return fallback;}
 }
 private void finishGpsFix(){
  if(!gpsListening)return;
  android.location.Location found=bestGps;
  stopGpsUpdates();
  if(found==null){
   cityStatus.setText("Nie udało się uzyskać aktualnej pozycji. Spróbuj przy oknie albo włącz lokalizację w telefonie.");
   return;
  }
  cityStatus.setText("Pobrano pozycję. Ustalam nazwę okolicy…");
  final android.location.Location fix=new android.location.Location(found);
  executor.submit(()->{
   String label=gpsAddress(getApplicationContext(),fix);
   float accuracy=fix.hasAccuracy()?fix.getAccuracy():Float.NaN;
   ui.post(()->{
    if(destroyed)return;
    WeatherRepository.setGpsPlace(new WeatherRepository.Place(label,"Pozycja GPS",fix.getLatitude(),fix.getLongitude()));
    place=3;selectedDay=-1;forecast=null;
    gpsButton.setText("📍 GPS: "+label+" · dotknij, aby odświeżyć pozycję");
    cityStatus.setText("Pozycja GPS: "+label+
      (Float.isFinite(accuracy)?" · dokładność położenia około "+Math.round(accuracy)+" m":" · dokładność nieznana")+
      ". Pogoda pochodzi z modelu obszarowego, a nie z czujnika w tej okolicy.");
    load(true);
   });
  });
 }
 @Override protected void onStop(){stopGpsUpdates();super.onStop();}
'''
anchor=' @Override protected void onSaveInstanceState(Bundle b){'
assert s.count(anchor)==1
s=s.replace(anchor,methods+"\n"+anchor)
anchor='@Override protected void onDestroy(){destroyed=true;'
assert s.count(anchor)==1
s=s.replace(anchor,'@Override protected void onDestroy(){stopGpsUpdates();destroyed=true;')
p.write_text(s,encoding="utf-8")

manifest=base/"AndroidManifest.xml"
s=manifest.read_text(encoding="utf-8")
anchor='<uses-permission android:name="android.permission.INTERNET" />'
assert s.count(anchor)==1
s=s.replace(anchor,anchor+'''
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />''')
manifest.write_text(s,encoding="utf-8")
print("PASS: per-tap permission, one-shot 18s GPS/network fix, reverse geocode, nonpersistent coordinates")
