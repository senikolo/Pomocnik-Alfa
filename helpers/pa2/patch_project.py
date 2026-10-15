#!/usr/bin/env python3
"""Preserve all existing modules. Replace only the isolated regional planner Activity."""
import sys,re,shutil
from pathlib import Path
root,data=map(Path,sys.argv[1:3]);here=Path(__file__).parent
assets=root/'assets'/'pa_regions'
if assets.exists():shutil.rmtree(assets)
assets.mkdir(parents=True)
for p in data.glob('*.json'):shutil.copyfile(p,assets/p.name)
for name in ('app.html','app.js','transit-core.js','conversation.js'):shutil.copyfile(here/name,assets/name)
for folder in root.glob('smali*'):
 for p in (folder/'com/ispina/lokalnie').glob('PARegions*.smali'):p.unlink()
 if not list(folder.rglob('*.smali')):shutil.rmtree(folder)
main=next(root.glob('smali*/com/ispina/lokalnie/MainActivity.smali'));s=main.read_text()
if 'Lcom/ispina/lokalnie/PARegionsClick;' not in s:
 anchor=('    const-string v2, "Najbli\\u017csze przystanki, planowe odjazdy, kierunki i prosta trasa. GPS tylko po dotkni\\u0119ciu."\n\n'
 '    invoke-direct {p0, v1, v2, v0}, Lcom/ispina/lokalnie/MainActivity;->addActionCard(Ljava/lang/String;Ljava/lang/String;Landroid/view/View$OnClickListener;)V')
 assert s.count(anchor)==1,'Unknown main card layout; refusing risky patch'
 addition='''

    new-instance v0, Lcom/ispina/lokalnie/PARegionsClick;
    invoke-direct {v0, p0}, Lcom/ispina/lokalnie/PARegionsClick;-><init>(Landroid/content/Context;)V
    const-string v1, "Planer, Autobus LIVE i Alfa"
    const-string v2, "Warszawa, Małopolska i kolej. Planer, autobus LIVE i asystent Alfa."
    invoke-direct {p0, v1, v2, v0}, Lcom/ispina/lokalnie/MainActivity;->addActionCard(Ljava/lang/String;Ljava/lang/String;Landroid/view/View$OnClickListener;)V
'''
 s=s.replace(anchor,anchor+addition)
s=s.replace('Warszawa: autobusy. Małopolska: MLD. Trasy, przesiadki i rozmowa po polsku.','Warszawa, Małopolska i kolej. Trasy, przesiadki, pogoda i Twoje notatki.')
s=s.replace('Pomocnik Alfa 1.7.66','Pomocnik Alfa 2.0')
main.write_text(s)
p=root/'AndroidManifest.xml';m=p.read_text()
m=m.replace('com.senikolo.pomocniknowy.regionspreview','com.senikolo.pomocniknowy')
assert 'package="com.senikolo.pomocniknowy"' in m
m=m.replace('com.senikolo.pomocniknowy','com.senikolo.pomocniknowy.pa2buspreview')
if 'android:name="com.ispina.lokalnie.PARegionsActivity"' not in m:
 m=m.replace('<application ', '<application ',1).replace('        <activity ', '        <activity android:exported="false" android:name="com.ispina.lokalnie.PARegionsActivity" android:windowSoftInputMode="adjustResize"/>\n        <activity ',1)
m=re.sub(r'android:label="(?:Pomocnik Alfa 1\.7\.66|Pomocnik PA Regiony TEST)"','android:label="Pomocnik Alfa 2.0 TEST"',m)
# Separate Alpha 3 preview launcher emblem: adaptive violet Alfa identity + bus pictogram.
drawable=root/'res'/'drawable';drawable.mkdir(parents=True,exist_ok=True)
(drawable/'pa2_live_bg.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
  <solid android:color="#54358F"/>
</shape>''')
(drawable/'pa2_live_fg.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
 android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
 <path android:fillColor="#FFFFFF" android:pathData="M35,32 C31,32 29,36 29,40 L29,70 C29,75 32,78 37,78 L71,78 C76,78 79,75 79,70 L79,40 C79,36 77,32 73,32 Z"/>
 <path android:fillColor="#54358F" android:pathData="M36,39 L72,39 L72,55 L36,55 Z"/>
 <path android:fillColor="#54358F" android:pathData="M38,78 L44,78 L44,83 L38,83 Z M64,78 L70,78 L70,83 L64,83 Z"/>
 <path android:fillColor="#FFFFFF" android:pathData="M46,44 L62,44 L62,48 L46,48 Z"/>
 <path android:fillColor="#54358F" android:pathData="M38,65 L45,65 L45,69 L38,69 Z M63,65 L70,65 L70,69 L63,69 Z"/>
</vector>''')
adaptive=root/'res'/'mipmap-anydpi-v26';adaptive.mkdir(parents=True,exist_ok=True)
(adaptive/'pa2_live_icon.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
 <background android:drawable="@drawable/pa2_live_bg"/>
 <foreground android:drawable="@drawable/pa2_live_fg"/>
</adaptive-icon>''')
if 'android:icon=' in m:
 m=re.sub(r'android:icon="[^"]+"','android:icon="@mipmap/pa2_live_icon"',m,count=1)
else:
 m=m.replace('<application ','<application android:icon="@mipmap/pa2_live_icon" ',1)
m=re.sub(r'android:label="(?:Pomocnik Alfa 2.0 TEST|Pomocnik Alfa 1\.7\.66|Pomocnik PA Regiony TEST)"','android:label="PA 2.0 Autobus LIVE TEST"',m)

p.write_text(m)
p=root/'apktool.yml';s=p.read_text();s=re.sub(r'versionCode:.*',"versionCode: '20003'",s,count=1);s=re.sub(r'versionName:.*','versionName: 2.0.0-alpha.3',s,count=1);p.write_text(s)
print('PASS: isolated module replaced; all other activities/services/receivers/assets preserved; minSdk 29 unchanged')
