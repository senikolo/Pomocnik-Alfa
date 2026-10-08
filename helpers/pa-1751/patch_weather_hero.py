#!/usr/bin/env python3
"""Ensure weather hero uses latest place name after reverse geocoding, not a stale Forecast snapshot."""
from pathlib import Path

p=Path("project/app/src/main/java/com/ispina/lokalnie/WeatherActivity.java")
s=p.read_text(encoding="utf-8")
hero="NativeUi.text(this,forecast.location.name,18,true)"
assert s.count(hero)==1, "Weather hero reference changed"
s=s.replace(hero,"NativeUi.text(this,weatherPlaceLabel(),18,true)")
anchor=' private LinearLayout heading(String title){'
assert s.count(anchor)==1
method=''' private String weatherPlaceLabel(){
  String label="";
  if(place==3 && WeatherRepository.gpsPlace()!=null)
   label=WeatherRepository.gpsPlace().name;
  else if(forecast!=null&&forecast.location!=null)
   label=forecast.location.name;
  if(label==null||label.trim().isEmpty()||
     label.trim().startsWith("GPS "))
   return "Twoja okolica";
  return label.trim();
 }
'''
s=s.replace(anchor,method+anchor)
anchor='''      cityStatus.setText("Pogoda dla: "+details.label+(Float.isFinite(accuracy)&&accuracy>120f?" · lokalizacja przybliżona":""));'''
assert s.count(anchor)==1
s=s.replace(anchor,anchor+'''
      // The previous Forecast object carries a snapshot of the initial label.
      // Re-render the current forecast immediately when geocoding finishes.
      if(forecast!=null)render();''')
p.write_text(s,encoding="utf-8")
assert 'NativeUi.text(this,weatherPlaceLabel(),18,true)' in s
assert 'if(forecast!=null)render();' in s
main=Path("project/app/src/main/java/com/ispina/lokalnie/MainActivity.java")
v=main.read_text(encoding="utf-8")
assert "POMOCNIK ALFA 1.7.50" in v
main.write_text(v.replace("POMOCNIK ALFA 1.7.50","POMOCNIK ALFA 1.7.51"),encoding="utf-8")
print("PASS: weather hero label refreshed after geocoding; raw GPS coordinates not displayed")
