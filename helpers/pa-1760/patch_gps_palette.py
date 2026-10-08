#!/usr/bin/env python3
"""Apply PA 1.7.60 muted departure palette and true public GPS VehiclePositions viewer."""
from pathlib import Path
from shutil import copyfile
import re

base=Path("project/app/src/main/java/com/ispina/lokalnie")
transit=base/"transit"
transit.mkdir(parents=True,exist_ok=True)
copyfile("helpers/pa-1760/WarsawVehicleGps.java",transit/"WarsawVehicleGps.java")
copyfile("helpers/pa-1760/GpsLiveActivity.java",base/"GpsLiveActivity.java")

def once(value,old,new,why):
    count=value.count(old)
    if count!=1:
        raise AssertionError(f"{why}: expected 1 match, got {count}")
    return value.replace(old,new,1)

manifest=Path("project/app/src/main/AndroidManifest.xml")
s=manifest.read_text(encoding="utf-8")
s=once(s,
    '<activity android:name=".LiveMapActivity" android:exported="false" />',
    '<activity android:name=".LiveMapActivity" android:exported="false" />\n'
    '        <activity android:name=".GpsLiveActivity" android:exported="false" />',
    "register native GPS screen")
manifest.write_text(s,encoding="utf-8")

# Manual stop searches must expose coordinates: they are used LOCALLY for
# distances. No user geolocation is requested, transmitted, or retained.
gtfs=transit/"GtfsNearby.java"
s=gtfs.read_text(encoding="utf-8")
s=once(s,
    'stop.code=passengerStopNumber(id,rows.s(row,"stop_code"),provider);\n                all.put(id,stop);',
    'stop.code=passengerStopNumber(id,rows.s(row,"stop_code"),provider);\n'
    '                stop.lat=decimal(rows.s(row,"stop_lat"));\n'
    '                stop.lon=decimal(rows.s(row,"stop_lon"));\n'
    '                all.put(id,stop);',
    "read manual stop coordinates")
gtfs.write_text(s,encoding="utf-8")

# Background and foreground pairs chosen for subdued WCAG-AA contrast in both themes.
colors={
    "0xFF5836A5":"0xFFEBE7F4", "0xFFFFFFFF":"0xFF4B3C72",
    "0xFF714DC0":"0xFF312C44",
    # The other original white foregrounds are assigned explicitly below
}
pairs={
    ("0xFF5836A5","0xFFFFFFFF","0xFF714DC0","0xFFFFFFFF"):
        ("0xFFEBE7F4","0xFF4B3C72","0xFF312C44","0xFFE6DEF7"),
    ("0xFFE6F0FF","0xFF174783","0xFF1C3A61","0xFFF0F6FF"):
        ("0xFFE9F0F6","0xFF284A64","0xFF263B4B","0xFFE4F1FA"),
    ("0xFFE0F4E9","0xFF075B46","0xFF173F34","0xFFE3FFF0"):
        ("0xFFEAF1ED","0xFF305A49","0xFF2D443B","0xFFE5F6EC"),
    ("0xFFFFEDD0","0xFF794200","0xFF52391D","0xFFFFEAC0"):
        ("0xFFF3F0E8","0xFF625134","0xFF443C30","0xFFF7EBD5"),
    ("0xFF125E7B","0xFFFFFFFF","0xFF17678B","0xFFFFFFFF"):
        ("0xFFE4EFED","0xFF145C58","0xFF294945","0xFFDFF7F0"),
    ("0xFFF0F3F6","0xFF46515E","0xFF353D48","0xFFEFF3F9"):
        ("0xFFF0F1F3","0xFF4A5260","0xFF3A414B","0xFFEDF1F4"),
    ("0xFFFFE6D0","0xFF8C3706","0xFF5A321C","0xFFFFECD9"):
        ("0xFFF9EDE3","0xFF80502E","0xFF49392F","0xFFFFE9D9"),
    ("0xFFDEF6E5","0xFF126035","0xFF19442C","0xFFE5FFED"):
        ("0xFFEBF3EC","0xFF2C6244","0xFF294334","0xFFE3F6E7"),
}
def palette(text):
    for old,new in pairs.items():
        a=",".join(old)
        b=",".join(new)
        text=text.replace(a,b)
    return text

near=base/"NearbyDeparturesActivity.java"
s=near.read_text(encoding="utf-8")
s=palette(s)
s=s.replace("Route number: bold white on a saturated purple badge (light/dark accessible).",
            "Route number: calm lavender surface and readable ink in both themes.")
s=s.replace("Departure clock: strong deep teal badge (distinct from direction blue).",
            "Departure clock: soft teal surface and readable deep teal ink.")
s=once(s,
    '"Fiolet: linia · niebieski: kierunek · zielony: przystanek · bursztyn: stanowisko · morski: godzina"',
    '"Kolory pomagają rozróżnić linię, kierunek, przystanek, stanowisko i godzinę"',
    "quiet legend")
s=once(s,
    '            countdownLabels.add(new CountdownLabel(d,countdown,status));',
    '''            countdownLabels.add(new CountdownLabel(d,countdown,status));
            if(d.stop!=null && d.stop.id!=null &&
                    d.stop.id.matches("[0-9]{6}") &&
                    ("bus".equals(d.mode)||"tram".equals(d.mode)) &&
                    d.routeId!=null&&!d.routeId.isEmpty()){
                NativeUi.addSpacer(panel,this,5);
                Button gps=NativeUi.button(this,"📍 GPS LIVE · pojazdy linii "+d.line,true);
                gps.setOnClickListener(v->startActivity(new Intent(this,GpsLiveActivity.class)
                    .putExtra(GpsLiveActivity.EXTRA_ROUTE,d.routeId)
                    .putExtra(GpsLiveActivity.EXTRA_LINE,d.line)
                    .putExtra(GpsLiveActivity.EXTRA_STOP,d.stop.name)
                    .putExtra(GpsLiveActivity.EXTRA_STOP_ID,d.stop.id)
                    .putExtra(GpsLiveActivity.EXTRA_LAT,d.stop.lat)
                    .putExtra(GpsLiveActivity.EXTRA_LON,d.stop.lon)));
                panel.addView(gps,new LinearLayout.LayoutParams(-1,dp(45)));
            }''',
    "GPS action on Warsaw departures")
# Dynamic countdown badge must use the same quiet palette as the initial chip.
replacements={
    "0xFF353D48:0xFFF0F3F6":"0xFF3A414B:0xFFF0F1F3",
    "0xFF5A321C:0xFFFFE6D0":"0xFF49392F:0xFFF9EDE3",
    "0xFF19442C:0xFFDEF6E5":"0xFF294334:0xFFEBF3EC",
    "0xFFEFF3F9:0xFF46515E":"0xFFEDF1F4:0xFF4A5260",
    "0xFFFFECD9:0xFF8C3706":"0xFFFFE9D9:0xFF80502E",
    "0xFFE5FFED:0xFF126035":"0xFFE3F6E7:0xFF2C6244"
}
for old,new in replacements.items():
    s=once(s,old,new,"dynamic palette "+old)
near.write_text(s,encoding="utf-8")

line=base/"LineDeparturesActivity.java"
s=palette(line.read_text(encoding="utf-8"))
s=once(s,
    '            external.addView(liveMap,new LinearLayout.LayoutParams(-1,dp(55)));',
    '''            external.addView(liveMap,new LinearLayout.LayoutParams(-1,dp(55)));
            if(!result.departures.isEmpty()){
                GtfsNearby.Departure selected=result.departures.get(0);
                NativeUi.addSpacer(external,this,7);
                Button gps=NativeUi.button(this,"GPS LIVE · pojazdy linii "+line,true);
                gps.setOnClickListener(v->startActivity(new Intent(this,GpsLiveActivity.class)
                    .putExtra(GpsLiveActivity.EXTRA_ROUTE,selected.routeId)
                    .putExtra(GpsLiveActivity.EXTRA_LINE,line)
                    .putExtra(GpsLiveActivity.EXTRA_STOP,stopName)
                    .putExtra(GpsLiveActivity.EXTRA_STOP_ID,selectedStopId)
                    .putExtra(GpsLiveActivity.EXTRA_LAT,selected.stop.lat)
                    .putExtra(GpsLiveActivity.EXTRA_LON,selected.stop.lon)));
                external.addView(gps,new LinearLayout.LayoutParams(-1,dp(50)));
                external.addView(NativeUi.muted(this,
                    "Świeże pozycje GPS bez prognozy minut. Opóźnienia pozostają rozkładowe, jeśli brak potwierdzonego źródła TripUpdates.",12));
            }''',
    "manual line real GPS action")
line.write_text(s,encoding="utf-8")

main=base/"MainActivity.java"
s=main.read_text(encoding="utf-8")
s=once(s,"POMOCNIK ALFA 1.7.59","POMOCNIK ALFA 1.7.60","increment splash version")
main.write_text(s,encoding="utf-8")

gradle=Path("project/app/build.gradle")
s=gradle.read_text(encoding="utf-8")
s=once(s,"versionCode 10760","versionCode 10761","increment Android versionCode")
s=once(s,"versionName '1.7.59-A10'","versionName '1.7.60-A10'","increment Android versionName")
gradle.write_text(s,encoding="utf-8")

print("PASS: subdued palette, native fresh Warsaw vehicle GPS, stop coordinates, source credit, build version")
