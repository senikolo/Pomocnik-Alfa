#!/usr/bin/env python3
"""Patch the decoded full PA 1.7.66 without replacing its many existing modules."""
from pathlib import Path
import re, hashlib, sys

root=Path(sys.argv[1])
assets=root/"assets"
assets.mkdir(exist_ok=True)
parts=sorted((Path("ports/il52")).glob("main_ui.part-*.txt"))
assert len(parts)==18, f"Expected 18 exact IL UI parts, got {len(parts)}"
full=b"".join(p.read_bytes() for p in parts)
expected="067906d9e11ca6874920a156b12feb549e181061f68ed15ceb18a61438540d50"
assert hashlib.sha256(full).hexdigest()==expected, "IL52 UI bytes differ from original; abort"
(assets/"pa_il52_main_ui.js").write_bytes(full)
(assets/"pa_il52_shell.html").write_bytes(Path("helpers/pa-il52/pa_il52_shell.html").read_bytes())

matches=list(root.glob("smali*/com/ispina/lokalnie/MainActivity.smali"))
assert len(matches)==1, matches
p=matches[0]
source=p.read_text(encoding="utf-8")
needle=('    const-string v2, "Najbli\\u017csze przystanki, planowe odjazdy, kierunki i prosta trasa. GPS tylko po dotkni\\u0119ciu."\n\n'
        '    invoke-direct {p0, v1, v2, v0}, Lcom/ispina/lokalnie/MainActivity;->addActionCard(Ljava/lang/String;Ljava/lang/String;Landroid/view/View$OnClickListener;)V')
assert source.count(needle)==1, f"Unique native card anchor not found: {source.count(needle)}"
addition='''
    # IL 52 bus route planner: same JS UI, separate native screen.
    new-instance v0, Lcom/ispina/lokalnie/ILBusClick;

    invoke-direct {v0, p0}, Lcom/ispina/lokalnie/ILBusClick;-><init>(Landroid/content/Context;)V

    const-string v1, "Planer autobusów jak w IL"

    const-string v2, "Wybierz start, cel, datę i godzinę. Te same podpowiedzi przystanków i przesiadki co w IL."

    invoke-direct {p0, v1, v2, v0}, Lcom/ispina/lokalnie/MainActivity;->addActionCard(Ljava/lang/String;Ljava/lang/String;Landroid/view/View$OnClickListener;)V
'''
source=source.replace(needle,needle+"\n"+addition,1)
p.write_text(source,encoding="utf-8")

manifest=root/"AndroidManifest.xml"
s=manifest.read_text(encoding="utf-8")
n='        <activity android:exported="false" android:name="com.ispina.lokalnie.WeatherActivity"/>'
assert s.count(n)==1
s=s.replace(n,'        <activity android:exported="false" android:name="com.ispina.lokalnie.ILBusPlannerActivity"/>\n'+n)
# Isolated preview installs alongside a user's production PA and cannot destroy
# their notes or reminders by forcing an uninstall for signature mismatch.
assert 'package="com.senikolo.pomocniknowy"' in s
s=s.replace('package="com.senikolo.pomocniknowy"','package="com.senikolo.pomocniknowy.ilbuspreview"')
s=s.replace('com.senikolo.pomocniknowy.fileprovider','com.senikolo.pomocniknowy.ilbuspreview.fileprovider')
s=s.replace('com.senikolo.pomocniknowy.androidx-startup','com.senikolo.pomocniknowy.ilbuspreview.androidx-startup')
s=s.replace('com.senikolo.pomocniknowy.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION',
            'com.senikolo.pomocniknowy.ilbuspreview.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION')
s=s.replace('android:label="Pomocnik Alfa 1.7.66"','android:label="Pomocnik Alfa - IL Bus TEST"')
manifest.write_text(s,encoding="utf-8")

meta=root/"apktool.yml"
ms=meta.read_text(encoding="utf-8")
assert re.search(r"versionCode:\s*['\"]?10767",ms),ms[:500]
ms=re.sub(r"versionCode:\s*['\"]?10767['\"]?",'versionCode: \'10768\'',ms,count=1)
ms=re.sub(r"versionName: .+",'versionName: 1.7.67-IL52-PREVIEW',ms,count=1)
meta.write_text(ms,encoding="utf-8")
print("PASS: IL52 SHA-256",expected,"bytes",len(full),"part count",len(parts))
print("PASS: main PA card, extra activity and isolated preview package")
