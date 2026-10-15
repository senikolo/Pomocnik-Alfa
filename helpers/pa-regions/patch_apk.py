#!/usr/bin/env python3
"""Patch PA 1.7.66 code only to add in-app regional bus planner and assistant.
Uses a separate package for safe side-by-side testing, preserving original PA APK.
"""
from pathlib import Path
import shutil
import re
import sys
import json

root=Path(sys.argv[1])
data=Path(sys.argv[2])
assets=root/"assets"/"pa_regions"
assets.mkdir(parents=True,exist_ok=True)
for source in data.glob("*.json"):
    shutil.copyfile(source,assets/source.name)
for name in ("app.html","app.js"):
    shutil.copyfile(Path("helpers/pa-regions")/name,assets/name)
assert (assets/"index_warsaw.json").exists()
assert (assets/"index_mld.json").exists()
assert sum(1 for _ in assets.glob("route_warsaw_*.json"))>100
assert sum(1 for _ in assets.glob("route_mld_*.json"))>30

matches=list(root.glob("smali*/com/ispina/lokalnie/MainActivity.smali"))
assert len(matches)==1,matches
p=matches[0]
s=p.read_text(encoding="utf-8")
needle=('    const-string v2, "Najbli\\u017csze przystanki, planowe odjazdy, kierunki i prosta trasa. GPS tylko po dotkni\\u0119ciu."\n\n'
        '    invoke-direct {p0, v1, v2, v0}, Lcom/ispina/lokalnie/MainActivity;->addActionCard(Ljava/lang/String;Ljava/lang/String;Landroid/view/View$OnClickListener;)V')
assert s.count(needle)==1,"Original PA bus-card anchor changed, not risking patch."
addition='''

    # Regional network-aware bus planner and PA assistant (Warsaw / MLD)
    new-instance v0, Lcom/ispina/lokalnie/PARegionsClick;

    invoke-direct {v0, p0}, Lcom/ispina/lokalnie/PARegionsClick;-><init>(Landroid/content/Context;)V

    const-string v1, "Planer i Asystent PA"

    const-string v2, "Warszawa: autobusy. Małopolska: MLD. Trasy, przesiadki i rozmowa po polsku."

    invoke-direct {p0, v1, v2, v0}, Lcom/ispina/lokalnie/MainActivity;->addActionCard(Ljava/lang/String;Ljava/lang/String;Landroid/view/View$OnClickListener;)V
'''
p.write_text(s.replace(needle,needle+addition,1),encoding="utf-8")
manifest=root/"AndroidManifest.xml"
m=manifest.read_text(encoding="utf-8")
needle='        <activity android:exported="false" android:name="com.ispina.lokalnie.WeatherActivity"/>'
assert m.count(needle)==1
m=m.replace(needle,'        <activity android:exported="false" android:name="com.ispina.lokalnie.PARegionsActivity"/>\n'+needle)
assert 'package="com.senikolo.pomocniknowy"' in m
m=m.replace('package="com.senikolo.pomocniknowy"','package="com.senikolo.pomocniknowy.regionspreview"')
for tail in ('fileprovider','androidx-startup','DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'):
    m=m.replace("com.senikolo.pomocniknowy."+tail,"com.senikolo.pomocniknowy.regionspreview."+tail)
m=m.replace('android:label="Pomocnik Alfa 1.7.66"','android:label="Pomocnik PA Regiony TEST"')
manifest.write_text(m,encoding="utf-8")

meta=root/"apktool.yml"
text=meta.read_text(encoding="utf-8")
assert re.search(r"versionCode:\s*['\"]?10767",text)
text=re.sub(r"versionCode:\s*['\"]?10767['\"]?","versionCode: '10769'",text,count=1)
text=re.sub(r"versionName: .+","versionName: 1.7.69-REGIONY-TEST",text,count=1)
meta.write_text(text,encoding="utf-8")
print("PASS: existing PA functionality preserved; dedicated Warsaw/MLD route and assistant card")
print("PASS: regional data files",len(list(assets.glob("*.json"))))
