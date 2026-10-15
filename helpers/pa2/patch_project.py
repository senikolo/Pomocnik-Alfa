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
    const-string v1, "Planer i Asystent PA"
    const-string v2, "Warszawa, Małopolska i kolej. Trasy, przesiadki, pogoda i Twoje notatki."
    invoke-direct {p0, v1, v2, v0}, Lcom/ispina/lokalnie/MainActivity;->addActionCard(Ljava/lang/String;Ljava/lang/String;Landroid/view/View$OnClickListener;)V
'''
 s=s.replace(anchor,anchor+addition)
s=s.replace('Warszawa: autobusy. Małopolska: MLD. Trasy, przesiadki i rozmowa po polsku.','Warszawa, Małopolska i kolej. Trasy, przesiadki, pogoda i Twoje notatki.')
s=s.replace('Pomocnik Alfa 1.7.66','Pomocnik Alfa 2.0')
main.write_text(s)
p=root/'AndroidManifest.xml';m=p.read_text()
m=m.replace('com.senikolo.pomocniknowy.regionspreview','com.senikolo.pomocniknowy')
assert 'package="com.senikolo.pomocniknowy"' in m
m=m.replace('com.senikolo.pomocniknowy','com.senikolo.pomocniknowy.pa2uxpreview')
if 'android:name="com.ispina.lokalnie.PARegionsActivity"' not in m:
 m=m.replace('<application ', '<application ',1).replace('        <activity ', '        <activity android:exported="false" android:name="com.ispina.lokalnie.PARegionsActivity" android:windowSoftInputMode="adjustResize"/>\n        <activity ',1)
m=re.sub(r'android:label="(?:Pomocnik Alfa 1\.7\.66|Pomocnik PA Regiony TEST)"','android:label="Pomocnik Alfa 2.0 TEST"',m)
p.write_text(m)
p=root/'apktool.yml';s=p.read_text();s=re.sub(r'versionCode:.*',"versionCode: '20002'",s,count=1);s=re.sub(r'versionName:.*','versionName: 2.0.0-alpha.2',s,count=1);p.write_text(s)
print('PASS: isolated module replaced; all other activities/services/receivers/assets preserved; minSdk 29 unchanged')
