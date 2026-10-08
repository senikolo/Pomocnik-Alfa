#!/usr/bin/env python3
"""PA 1.7.60: verify calm chip palette, both themes, and safe GPS UI wiring."""
from pathlib import Path

base=Path("project/app/src/main/java/com/ispina/lokalnie")
near=(base/"NearbyDeparturesActivity.java").read_text(encoding="utf-8")
manual=(base/"LineDeparturesActivity.java").read_text(encoding="utf-8")
gps=(base/"GpsLiveActivity.java").read_text(encoding="utf-8")
parser=(base/"transit/WarsawVehicleGps.java").read_text(encoding="utf-8")
manifest=Path("project/app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
gtfs=(base/"transit/GtfsNearby.java").read_text(encoding="utf-8")
sets={
 "linia":("EBE7F4","4B3C72","312C44","E6DEF7"),
 "kierunek":("E9F0F6","284A64","263B4B","E4F1FA"),
 "przystanek":("EAF1ED","305A49","2D443B","E5F6EC"),
 "stanowisko":("F3F0E8","625134","443C30","F7EBD5"),
 "godzina":("E4EFED","145C58","294945","DFF7F0"),
 "rozkład":("F0F1F3","4A5260","3A414B","EDF1F4"),
 "opóźnienie":("F9EDE3","80502E","49392F","FFE9D9"),
 "aktualizacja":("EBF3EC","2C6244","294334","E3F6E7")
}
def luma(s):
    rgb=[int(s[i:i+2],16)/255 for i in (0,2,4)]
    linear=[x/12.92 if x<=0.04045 else ((x+.055)/1.055)**2.4 for x in rgb]
    return .2126*linear[0]+.7152*linear[1]+.0722*linear[2]
def contrast(a,b):
    x,y=sorted((luma(a),luma(b)),reverse=True)
    return (x+.05)/(y+.05)
for label,(lb,lf,db,df) in sets.items():
    for name,bg,fg in (("jasny",lb,lf),("ciemny",db,df)):
        score=contrast(bg,fg)
        assert score>=4.5,(label,name,score)
    for field in (lb,lf,db,df):
        assert "0xFF"+field in near,label
        assert "0xFF"+field in manual,label
assert '"📍 GPS LIVE · pojazdy linii "+d.line' in near
assert 'GpsLiveActivity.class' in manual
assert 'android:name=".GpsLiveActivity" android:exported="false"' in manifest
assert 'stop.lat=decimal(rows.s(row,"stop_lat"))' in gtfs
assert "FRESH_MS = 120000L" in parser
assert "Miasto Stołeczne Warszawa" in gps
assert "NIE prognoza przyjazdu" in gps
assert "Double.isNaN(snapshot.nearestMeters)" in gps
assert "No phone location or API token" in parser
assert "versionCode 10761" in Path("project/app/build.gradle").read_text()
print("PASS: 8 muted WCAG-AA light/dark palettes, native GPS display, accuracy/attribution guards")
