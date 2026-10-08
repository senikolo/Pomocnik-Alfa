#!/usr/bin/env python3
"""PA 1.7.59: integrate an in-app, clearly attributed Czynaczas website viewer."""
from pathlib import Path
import shutil

base=Path("project/app/src/main/java/com/ispina/lokalnie")
shutil.copyfile("helpers/pa-1759/LiveMapActivity.java",base/"LiveMapActivity.java")

def replace_one(value, old, new, description):
    count=value.count(old)
    if count!=1:
        raise AssertionError(f"{description}: expected one match, got {count}")
    return value.replace(old,new,1)

manifest=Path("project/app/src/main/AndroidManifest.xml")
s=manifest.read_text(encoding="utf-8")
s=replace_one(s,
    '<activity android:name=".LineDeparturesActivity" android:exported="false" />',
    '<activity android:name=".LineDeparturesActivity" android:exported="false" />\n'
    '        <activity android:name=".LiveMapActivity" android:exported="false" />',
    "register in-app map activity")
manifest.write_text(s,encoding="utf-8")

line_file=base/"LineDeparturesActivity.java"
s=line_file.read_text(encoding="utf-8")
s=replace_one(s,'render(data,line);','render(data,line,stopId);',"pass selected stop")
s=replace_one(s,
    'private void render(GtfsNearby.Result result,String line){',
    'private void render(GtfsNearby.Result result,String line,String stopId){',
    "render stop signature")
s=replace_one(s,
    'String selectedStopId=result.stops.isEmpty()?null:result.stops.get(0).id;',
    'String selectedStopId=stopId;',
    "use explicitly selected platform ID, not first feed result")
s=replace_one(s,
    'Button liveMap=NativeUi.button(this,"Czynaczas · LIVE dla wybranego przystanku",false);\n'
    '            liveMap.setOnClickListener(v->{\n'
    '                try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(deepLink)));}\n'
    '                catch(Exception ex){message.setText("Nie można otworzyć Czynaczas.pl.");}\n'
    '            });',
    'Button liveMap=NativeUi.button(this,"Mapa LIVE · wewnątrz Pomocnika Alfa",false);\n'
    '            liveMap.setOnClickListener(v->{\n'
    '                try{startActivity(new Intent(this,LiveMapActivity.class)\n'
    '                    .putExtra(LiveMapActivity.EXTRA_STOP_ID,selectedStopId)\n'
    '                    .putExtra(LiveMapActivity.EXTRA_STOP_NAME,stations.isEmpty()?\n'
    '                        "Wybrany przystanek":stations.get(stops.getSelectedItemPosition()).name)\n'
    '                    .putExtra(LiveMapActivity.EXTRA_LINE,line));}\n'
    '                catch(Exception ex){message.setText("Nie można otworzyć mapy LIVE.");}\n'
    '            });',
    "open live map in PA with stop and line context")
s=replace_one(s,
    '"Otwieram niezależną mapę Czynaczas.pl, dopasowaną do numeru stanowiska. Opóźnienia oblicza serwis zewnętrzny — nie są jeszcze pobierane do PA."',
    '"Czynaczas.pl otwiera się w PA dla wybranego stanowiska. Dane LIVE pozostają własnością i odpowiedzialnością serwisu zewnętrznego; PA nie wylicza opóźnień."',
    "accurate live attribution")
s=replace_one(s,
    'String deepLink=CzynaczasLinks.forStop("warsaw",selectedStopId);\n',
    '',
    "remove unused deep link local variable")
# Avoid presenting directions for a newly selected platform against previous platform's results.
line_file.write_text(s,encoding="utf-8")

near_file=base/"NearbyDeparturesActivity.java"
near=near_file.read_text(encoding="utf-8")
network='        boolean warsaw="warsaw".equals(GtfsNearby.networkFor(fix.getLatitude(),fix.getLongitude()));\n'
near=replace_one(near,network,'',"move Warsaw network selection before nearest stops")
near=replace_one(near,'        LinearLayout stopCard=NativeUi.card(this);',
    network+'        LinearLayout stopCard=NativeUi.card(this);',
    "declare Warsaw before nearest-stop loop")
target='            stopCard.addView(stopPanel);'
button='''            if(warsaw && closest.id!=null && closest.id.matches("[0-9]{6}")){
                NativeUi.addSpacer(stopPanel,this,6);
                Button live=NativeUi.button(this,
                    "Mapa LIVE · "+(closest.code==null?"przystanek":("stan. "+closest.code)),true);
                live.setOnClickListener(v->startActivity(new Intent(this,LiveMapActivity.class)
                    .putExtra(LiveMapActivity.EXTRA_STOP_ID,closest.id)
                    .putExtra(LiveMapActivity.EXTRA_STOP_NAME,closest.name)));
                stopPanel.addView(live,new LinearLayout.LayoutParams(-1,dp(48)));
            }
            stopCard.addView(stopPanel);'''
near=replace_one(near,target,button,"nearest stops live button")
near_file.write_text(near,encoding="utf-8")

print("PASS: 1.7.59 in-app map manifest, exact selected stop, nearest-stop actions, safe LIVE attribution")
