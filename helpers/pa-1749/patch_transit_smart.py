#!/usr/bin/env python3
"""Apply PA 1.7.49 nearby scheduled departures + Smart Oferta + human-friendly GPS."""
from pathlib import Path
import re,shutil

root=Path("project/app/src/main")
pkg=root/"java/com/ispina/lokalnie"
for file in ("NearbyDeparturesActivity.java","GtfsNearby.java","SmartOffer.java"):
    src=Path("helpers/pa-1749")/file
    dest=(pkg/"transit"/file) if file=="GtfsNearby.java" else (
        (pkg/"deals"/file) if file=="SmartOffer.java" else pkg/file)
    dest.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(src,dest)
# Ensure Java regex string contains exactly two source backslashes for one regex backslash.
smart=pkg/"deals/SmartOffer.java"
x=smart.read_text()
x=x.replace("\\\\\\\\","\\\\")
smart.write_text(x)
assert 'Pattern.compile("(?i)' in x

main=pkg/"MainActivity.java"
s=main.read_text()
anchor="    private void buildContent(){\n"
assert s.count(anchor)==1
s=s.replace(anchor,anchor+
'''        addActionCard("Odjazdy stąd","Najbliższe przystanki, planowe odjazdy, kierunki i prosta trasa. GPS tylko po dotknięciu.",v->startActivity(new Intent(this,NearbyDeparturesActivity.class)));
''')
s=s.replace('title.equals("Radio")?"📻"', 'title.equals("Odjazdy stąd")?"🚌":title.equals("Radio")?"📻"')
assert "Odjazdy stąd" in s
assert "POMOCNIK ALFA 1.7.48" in s
s=s.replace("POMOCNIK ALFA 1.7.48","POMOCNIK ALFA 1.7.49")
main.write_text(s)

manifest=root/"AndroidManifest.xml"
s=manifest.read_text()
anchor='<activity android:name=".WeatherActivity" android:exported="false" />'
assert s.count(anchor)==1
s=s.replace(anchor,anchor+'\n        <activity android:name=".NearbyDeparturesActivity" android:exported="false" />')
manifest.write_text(s)

deals=pkg/"deals/DealsRepository.java"
s=deals.read_text()
anchor='String preferred=preferredUnit(query);result.offers.sort('
assert s.count(anchor)==1
s=s.replace(anchor,
  'for(Deal deal:result.offers)SmartOffer.enrichCount(deal);\n  '+anchor)
a=s.index(' public static String summary(List<Deal> list){')
b=s.index('\n }',a)+3
s=s[:a]+''' public static String summary(List<Deal> list){
  if(list.isEmpty())return "Brak ofert z ceną w dostępnych źródłach.";
  SmartOffer.Pick recommendation=SmartOffer.choose(list,"");
  return recommendation.reasoning;
 }'''+s[b:]
# Weight vs volume preference: toothpaste measured in ml or g should not be forced to litres if only kg facts.
s=s.replace('||n.equals("pasta do zebow")||n.equals("pasty do zebow")','')
deals.write_text(s)

# Respect multipacks: 2 x 250 g is 500 g, not 250 g. Unknown multi-buy pricing
# remains unranked when conditions cannot be determined reliably.
parser=pkg/"deals/DealsParser.java"
ss=parser.read_text()
mark='  for(int i=0;i<words.length;i++)if(n.contains("przy zakupie "+words[i]))d.minimum=i+2;'
assert ss.count(mark)==1
ss=ss.replace(mark,mark+"""
  Matcher multipack=Pattern.compile("(?i)(\\\\d{1,2})\\\\s*[x×]\\\\s*(\\\\d+(?:[.,]\\\\d+)?)\\\\s*(kg|g|ml|l)\\\\b").matcher(text);
  if(multipack.find()){
      int boxes=Integer.parseInt(multipack.group(1));
      double each=Double.parseDouble(multipack.group(2).replace(',','.'));
      String suffix=multipack.group(3).toLowerCase(Locale.ROOT);
      if(boxes>1&&boxes<=24&&each>0&&each<=10000){
          double amount=boxes*each;
          if(suffix.equals("g")||suffix.equals("ml"))amount/=1000.0;
          d.unit=(suffix.equals("g")||suffix.equals("kg"))?"kg":"l";
          if(amount>0)d.unitPrice=d.price/amount;
      }
  }
  if(n.contains("drugi za")||n.contains("2 1 gratis")||n.contains("3 w cenie 2")
     ||n.contains("co drugi")||n.contains("multirabat")||n.contains("gratis przy zakupie"))
      d.details=false; // Price attribution across units uncertain. Do not crown winner.
""")
parser.write_text(ss)


activity=pkg/"DealsActivity.java"
s=activity.read_text()
old='''  if(!r.offers.isEmpty() && "Wszystkie".equals(store)){LinearLayout win=NativeUi.card(this);win.setBackground(NativeUi.roundedStroke(Color.rgb(252,244,220),Color.rgb(236,218,167),22,this));win.addView(NativeUi.text(this,r.stale?"Zapisane oferty · odśwież przed zakupem":DealsRepository.summary(r.offers),18,true));if(r.partial)win.addView(NativeUi.muted(this,"Porównanie częściowe: nie wszystkie źródła odpowiedziały.",13));results.addView(win);}'''
assert s.count(old)==1
new='''  if(!r.offers.isEmpty()){
   List<Deal> visible=new ArrayList<>();
   for(Deal item:r.offers)if("Wszystkie".equals(store)||store.equals(item.store))visible.add(item);
   SmartOffer.Pick pick=SmartOffer.choose(visible,active);
   LinearLayout win=NativeUi.card(this);
   win.setBackground(NativeUi.roundedStroke(NativeUi.softStrong(this),NativeUi.line(this),22,this));
   win.addView(NativeUi.text(this,r.stale?"Zapisane oferty · odśwież przed zakupem":"🏆 Smart oferta",21,true));
   win.addView(NativeUi.muted(this,pick.reasoning,15));
   if(pick.best!=null && !r.stale){
    NativeUi.addSpacer(win,this,8);
    win.addView(NativeUi.text(this,pick.best.store+" · "+pick.best.name,17,true));
    Button go=NativeUi.button(this,"Sprawdź najlepszą znalezioną ofertę",true);
    go.setOnClickListener(v->open(pick.best.url));win.addView(go);
    if(pick.easier!=null){
     win.addView(NativeUi.muted(this,"Łatwiejszy zakup (1 szt., bez karty): "+
       pick.easier.store+" · "+pick.easier.money(pick.easier.unitPrice)+" / "+pick.easier.unit,13));
    }
   }
   if(r.partial)win.addView(NativeUi.muted(this,"Porównanie częściowe. Nie wszystkie źródła odpowiedziały.",13));
   win.addView(NativeUi.muted(this,"Ranking nie uwzględnia kosztów dojazdu, dostawy ani cen nieudostępnionych przez sklepy. Przed zakupem sprawdź warunki.",12));
   results.addView(win);
  }'''
s=s.replace(old,new)
activity.write_text(s)

# Human-facing weather description (weather calculation remains unchanged).
weather=pkg/"WeatherActivity.java"
s=weather.read_text()
start=s.find('      gpsDetailsView.setText(details.detail+')
if start>=0:
    end=s.find('      cityStatus.setText(',start)
    assert end>start
    s=s[:start]+'''      gpsDetailsView.setText("Okolica: "+details.label+
          (Float.isFinite(accuracy)&&accuracy>120f?"\\nUwaga: przy słabym sygnale GPS osiedle może być sąsiednie.":"")+
          "\\nNazwy na podstawie © OpenStreetMap. Podgląd szczegółów i pozycji jest dostępny po odświeżeniu GPS.");
'''+s[end:]
# Keep the main weather presentation conversational; precision remains in GPS details.
s=s.replace('      cityStatus.setText("Twoja okolica: "+details.label+" · "+certainty);',
            '      cityStatus.setText("Pogoda dla: "+details.label+(Float.isFinite(accuracy)&&accuracy>120f?" · lokalizacja przybliżona":""));')
s=s.replace('🎯 Doprecyzuj lokalizację (cel: 25 m)','🎯 Doprecyzuj okolicę')
weather.write_text(s)

gps=pkg/"GpsMicroArea.java"
s=gps.read_text()
anchor='''                String label=combine(city,district,micro);
                if(label.isEmpty())label=fallback.label;'''
assert s.count(anchor)==1
s=s.replace(anchor,'''                // Name the smallest verified place first, without raw coordinate jargon.
                String human=!micro.isEmpty()?micro:(!district.isEmpty()?district:(!street.isEmpty()?"ul. "+street:""));
                String label=combine(human,city,"");
                if(label.isEmpty())label=fallback.label;''')
gps.write_text(s)
print("PASS: home departures tile, verified GTFS activities, Smart Oferta comparison and simplified GPS labels")
