#!/usr/bin/env python3
"""Preserve all existing modules. Replace only the isolated regional planner Activity."""
import sys,re,shutil
from pathlib import Path
root,data=map(Path,sys.argv[1:3]);here=Path(__file__).parent
assets=root/'assets'/'pa_regions'
if assets.exists():shutil.rmtree(assets)
assets.mkdir(parents=True)
for p in data.glob('*.json'):shutil.copyfile(p,assets/p.name)
for name in ('app.html','app.js','transit-core.js','conversation.js','osm-map.js','live-state.js','radio_catalog.json'):shutil.copyfile(here/name,assets/name)
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
 # The legacy nearby-stop screen is a different function from the regional planner.
 # Keep its original click listener, but label it by its actual purpose.
 legacy=anchor.replace('    invoke-direct {p0, v1, v2, v0}', '    const-string v1, "Przystanki w pobliżu"' + chr(10) + chr(10) + '    invoke-direct {p0, v1, v2, v0}',1)
 s=s.replace(anchor,legacy+addition)
s=s.replace('Warszawa: autobusy. Małopolska: MLD. Trasy, przesiadki i rozmowa po polsku.','Warszawa, Małopolska i kolej. Trasy, przesiadki, pogoda i Twoje notatki.')
s=s.replace('Pomocnik Alfa 1.7.66','Pomocnik Alfa 2.0')
# Correct every dashboard function glyph, including Planner, Tube and Kids.
method=s.index('.method private addActionCard(')
a=s.index('    const-string p2, "Pogoda"',method)
b=s.index('    const/high16 v3, 0x41b00000',a)
s=s[:a]+'    invoke-static {p1}, Lcom/ispina/lokalnie/PAIcons;->glyph(Ljava/lang/String;)Ljava/lang/String;\n\n    move-result-object p2\n\n'+s[b:]
s=re.sub(r'POMOCNIK ALFA 1[^"\n]*','POMOCNIK ALFA 2.0',s)
s=s.replace('Planer, Autobus LIVE i Alfa','Planer i Alfa').replace('Warszawa, Małopolska i kolej. Planer, autobus LIVE i asystent Alfa.','Trasy, autobusy i tramwaje na mapie. Warszawa, Małopolska i kolej.')
assert s.count('const-string v1, "Przystanki w pobliżu"')==1, 'Duplicate or missing nearby-stop tile'
assert s.count('const-string v1, "Planer i Alfa"')==1, 'Duplicate or missing PA2 regional planner'
main.write_text(s)
p=root/'AndroidManifest.xml';m=p.read_text()
m=m.replace('com.senikolo.pomocniknowy.regionspreview','com.senikolo.pomocniknowy')
assert 'package="com.senikolo.pomocniknowy"' in m
m=m.replace('com.senikolo.pomocniknowy','com.senikolo.pomocniknowy.pa2preview')
if 'android:name="com.ispina.lokalnie.PARegionsActivity"' not in m:
 m=m.replace('<application ', '<application ',1).replace('        <activity ', '        <activity android:exported="false" android:name="com.ispina.lokalnie.PARegionsActivity" android:windowSoftInputMode="adjustResize"/>\n        <activity ',1)
m=re.sub(r'android:label="(?:Pomocnik Alfa 1\.7\.66|Pomocnik PA Regiony TEST)"','android:label="Pomocnik Alfa ULTRASMART 10"',m)
# Restore the original Alfa cat, preserving the base APK's existing adaptive launcher.
# ic_launcher_foreground.xml uses ic_launcher_cat_foreground.png from the original PA.
m=re.sub(r'android:icon="[^"]+"','android:icon="@mipmap/ic_launcher"',m,count=1)
m=re.sub(r'android:roundIcon="[^"]+"','android:roundIcon="@mipmap/ic_launcher_round"',m,count=1)
m=re.sub(r'android:label="(?:Pomocnik Alfa ULTRASMART 10|Pomocnik Alfa 1\\.7\\.66|Pomocnik PA Regiony TEST)"','android:label="Pomocnik Alfa ULTRASMART 10"',m)
p.write_text(m)
p=root/'apktool.yml';s=p.read_text();s=re.sub(r'versionCode:.*',"versionCode: '20014'",s,count=1);s=re.sub(r'versionName:.*','versionName: 2.0.0-alpha.14-economy-search',s,count=1);p.write_text(s)
# Install professional vector drawables into the ORIGINAL Android application's resources.
# No network is required at build time; sources are vendored with ISC attribution.
import subprocess
subprocess.run([sys.executable,str(here/"prepare_premium_icons.py"),str(root),str(here/"premium-lucide-icons.json")],check=True)
shutil.copyfile(here/"THIRD-PARTY-LUCIDE-LICENSE.txt",assets/"THIRD-PARTY-LUCIDE-LICENSE.txt")
print('PASS: isolated module replaced; all other activities/services/receivers/assets preserved; minSdk 29 unchanged')

# Integrate actual boards in the existing dashboard card.
import subprocess
subprocess.run([sys.executable,str(here/"nearby_patch.py"),str(root)],check=True)

# Passenger-only string replacements in all preserved native modules.
for folder in root.glob('smali*'):
 for path in folder.rglob('*.smali'):
  before=path.read_text();after=before.replace('Brak ETA','Nie mam teraz informacji na żywo').replace('brak ETA','brak informacji na żywo').replace('brak potwierdzonego ETA','odjazd według rozkładu')
  if path.name.startswith(('GtfsNearby','NearbyDeparturesActivity','TransitWowUi')):
   after=after.replace('"skm"','"rail"').replace('Pociągi SKM','Pociągi').replace('"SKM"','"Kolej"')
   # Historical compact feed is now larger than 29 MiB; keep a bounded 80 MiB cap.
   if path.name.startswith('GtfsNearby'):after=after.replace('0x1d00000','0x5000000')
  if after!=before:path.write_text(after)
