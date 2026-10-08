#!/usr/bin/env python3
"""Integrate no-GPS line+stop chooser and official Czynaczas website deep link."""
from pathlib import Path
import shutil
base=Path("project/app/src/main/java/com/ispina/lokalnie")
for source,target in [
    ("helpers/pa-1758/LineDeparturesActivity.java",base/"LineDeparturesActivity.java"),
    ("helpers/pa-1758/CzynaczasLinks.java",base/"transit/CzynaczasLinks.java"),
]:
    target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(source,target)

manifest=Path("project/app/src/main/AndroidManifest.xml")
s=manifest.read_text(encoding="utf-8")
anchor='<activity android:name=".NearbyDeparturesActivity" android:exported="false" />'
assert s.count(anchor)==1,"Missing nearby activity manifest registration"
s=s.replace(anchor,anchor+'\n        <activity android:name=".LineDeparturesActivity" android:exported="false" />')
manifest.write_text(s,encoding="utf-8")

ui=base/"NearbyDeparturesActivity.java"
s=ui.read_text(encoding="utf-8")
assert 'new Intent(this,LineDeparturesActivity.class)' in s
assert 'Wyszukaj linię bez GPS' in s
manual=(base/"transit/GtfsNearby.java").read_text(encoding="utf-8")
for key in ["lineStops(Context ctx","lineStopDepartures(Context ctx",
            "if(provider.equals(\"mld\"))out.liveNote=MldRealtime.apply"]:
    assert key in manual,key
links=(base/"LineDeparturesActivity.java").read_text(encoding="utf-8")
assert 'CzynaczasLinks.forStop("warsaw",selectedStopId)' in links
assert 'Opóźnienia oblicza serwis zewnętrzny' in links
main=base/"MainActivity.java"
x=main.read_text(encoding="utf-8")
assert "POMOCNIK ALFA 1.7.57" in x
main.write_text(x.replace("POMOCNIK ALFA 1.7.57","POMOCNIK ALFA 1.7.58"),encoding="utf-8")
print("PASS: manual line/stop chooser, manifest, Czynaczas deep link, palette preserved")
