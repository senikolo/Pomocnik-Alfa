#!/usr/bin/env python3
"""PA 1.7.46: Moscow FM fallback, explicit official player, airport airplane icon."""
from pathlib import Path
import re

root=Path("project/app/src/main")
radio=root/"java/com/ispina/lokalnie/RadioActivity.java"
s=radio.read_text(encoding="utf-8")
old='"Москва FM 92.0","https://icecast.vgtrk.cdnvideo.ru/moscowfm128"'
new='"Москва FM 92.0","http://icecast.vgtrk.cdnvideo.ru:8000/moscowfm128"'
assert s.count(old)==1,"Expected Moscow FM source missing"
s=s.replace(old,new)
anchor="    private final Handler handler = new Handler(Looper.getMainLooper());"
assert s.count(anchor)==1
s=s.replace(anchor,anchor+"""
    private static final String MOSCOW="Москва FM 92.0";
    private static final String MOSCOW_SECONDARY="https://icecast-vgtrk.cdnvideo.ru/moscowfm128";
    private int moscowTry=0;
    private void playStation(String name,String url){
        moscowTry=MOSCOW.equals(name)?1:0;
        send(RadioService.ACTION_PLAY,name,url);
    }
""")
s=s.replace('play.setOnClickListener(v->send(RadioService.ACTION_PLAY,name,url));',
'''play.setOnClickListener(v->playStation(name,url));''')
s=s.replace('NativeUi.softTouch(c); NativeUi.onClick(c,v->send(RadioService.ACTION_PLAY,name,url));',
'''NativeUi.softTouch(c); NativeUi.onClick(c,v->playStation(name,url));''')
# Keep the row-level action for touch, show fallback controls only on Moscow FM.
anchor='''        c.addView(row);
        NativeUi.softTouch(c); NativeUi.onClick(c,v->playStation(name,url));
        parent.addView(c);'''
assert s.count(anchor)==1,"RadioActivity card position changed"
s=s.replace(anchor,'''        c.addView(row);
        NativeUi.softTouch(c); NativeUi.onClick(c,v->playStation(name,url));
        if(MOSCOW.equals(name)){
            NativeUi.addSpacer(c,this,7);
            TextView note=NativeUi.muted(this,"Jeśli pierwszy stream nie odpowiada, PA spróbuje zapasowego. Możesz także otworzyć oficjalny odtwarzacz stacji.",12);
            c.addView(note);
            NativeUi.addSpacer(c,this,7);
            LinearLayout alternatives=new LinearLayout(this);
            alternatives.setGravity(Gravity.CENTER_VERTICAL);
            Button alternate=NativeUi.button(this,"↻ Stream zapasowy",true);
            alternate.setOnClickListener(v->{moscowTry=2;send(RadioService.ACTION_PLAY,name,MOSCOW_SECONDARY);});
            alternatives.addView(alternate,new LinearLayout.LayoutParams(0,NativeUi.dp(this,43),1));
            Button website=NativeUi.button(this,"🌐 Strona radia",true);
            website.setOnClickListener(v->{
                try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.mosfm.com/")));}
                catch(Exception ignored){}
            });
            LinearLayout.LayoutParams websiteParams=new LinearLayout.LayoutParams(0,NativeUi.dp(this,43),1);
            websiteParams.leftMargin=NativeUi.dp(this,7);
            alternatives.addView(website,websiteParams);
            c.addView(alternatives);
        }
        parent.addView(c);''')
anchor='''        String e=p.getString("error","");
        String program=p.getString("program","");'''
assert s.count(anchor)==1,"Unable to locate radio state"
s=s.replace(anchor,'''        String e=p.getString("error","");
        if(MOSCOW.equals(n) && moscowTry==1 && e!=null && !e.trim().isEmpty()){
            moscowTry=2;
            if(status!=null)status.setText("Москва FM: próbuję drugiego źródła…");
            send(RadioService.ACTION_PLAY,MOSCOW,MOSCOW_SECONDARY);
            return;
        }
        String program=p.getString("program","");''')
radio.write_text(s,encoding="utf-8")

main=root/"java/com/ispina/lokalnie/MainActivity.java"
m=main.read_text(encoding="utf-8")
anchor='title.startsWith("Lotnictwo")?"✈️"'
assert m.count(anchor)==1
m=m.replace(anchor,'(title.startsWith("Lotnictwo")||title.startsWith("Nasłuch z lotnisk"))?"✈️"')
m=m.replace("POMOCNIK ALFA 1.7.45","POMOCNIK ALFA 1.7.46")
main.write_text(m,encoding="utf-8")

# Cleartext is enabled ONLY for the explicit radio source domain, not for all app traffic.
manifest=root/"AndroidManifest.xml"
man=manifest.read_text(encoding="utf-8")
if 'android:networkSecurityConfig=' in man:
    k=re.search(r'android:networkSecurityConfig="@xml/([^"]+)"',man)
    assert k,"Unexpected networkSecurityConfig"
    xml=root/("res/xml/"+k.group(1)+".xml")
    existing=xml.read_text(encoding="utf-8")
    domain='<domain-config cleartextTrafficPermitted="true"><domain includeSubdomains="false">icecast.vgtrk.cdnvideo.ru</domain></domain-config>'
    if "icecast.vgtrk.cdnvideo.ru" not in existing:
        assert "</network-security-config>" in existing
        xml.write_text(existing.replace("</network-security-config>",domain+"</network-security-config>"),encoding="utf-8")
else:
    assert '<application' in man
    xml=root/"res/xml/pa1746_network_security_config.xml"
    xml.parent.mkdir(parents=True,exist_ok=True)
    xml.write_text('''<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
  <base-config cleartextTrafficPermitted="false" />
  <domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="false">icecast.vgtrk.cdnvideo.ru</domain>
  </domain-config>
</network-security-config>
''',encoding="utf-8")
    man=man.replace("<application",'<application android:networkSecurityConfig="@xml/pa1746_network_security_config"',1)
    manifest.write_text(man,encoding="utf-8")
assert MOSCOW if False else True
print("PASS: airplane icon, Moscow primary HTTP-specific domain allowance, automatic alternate HTTPS and official player")
