#!/usr/bin/env python3
"""Manual line+platform: direct LIVE ETAs, delayed bus visibility, no old scheduled trips."""
from pathlib import Path
p=Path("project/app/src/main/java/com/ispina/lokalnie/LineDeparturesActivity.java")
s=p.read_text(encoding="utf-8")
def once(old,new,reason):
    global s
    n=s.count(old)
    if n!=1:raise AssertionError(f"{reason}: {n}")
    s=s.replace(old,new,1)
once('    private GtfsNearby.Result latestResult;',
'''    private GtfsNearby.Result latestResult;
    private String wowLastLine="",wowLastStop="";
    private String wowGpsStatus="Sprawdzam dane pojazdów…";''',"manual refresh state")

start=s.index('    private void renderWow(GtfsNearby.Result data,String line,String name){')
end=s.index('    private void showWowGps(',start)
replace='''    private void renderWow(GtfsNearby.Result data,String line,String name){
        latestResult=data;wowLastLine=line;wowLastStop=name;
        wowGpsStatus="Sprawdzam rzeczywiste czasy odjazdów…";
        gpsSerial++;
        renderWow(data,line,name,false);
    }
    private void renderWow(GtfsNearby.Result data,String line,String name,boolean updated){
        stopCountdown();
        gpsEntries.clear();
        departures.removeAllViews();
        message.setText("Odjazdy · sprawdzono "+clock(System.currentTimeMillis())+
            (data.oldData?" · starszy rozkład":""));
        LinearLayout intro=NativeUi.card(this);
        intro.addView(TransitWowUi.type(this,
            "PRZYSTANEK  "+name.toUpperCase(new Locale("pl","PL")),
            19,true,TransitWowUi.ink(this)));
        intro.addView(TransitWowUi.type(this,
            "LINIA "+line+" · kierunek, stanowisko, godzina i aktualny status LIVE.",
            13,false,TransitWowUi.subtle(this)));
        departures.addView(intro);
        NativeUi.addSpacer(departures,this,8);
        int shown=0;
        long now=System.currentTimeMillis();
        List<GtfsNearby.Departure> sorted=new ArrayList<>(data.departures);
        sorted.sort(java.util.Comparator.comparingLong(d->TransitWowUi.expected(d,now)));
        for(GtfsNearby.Departure departure:sorted){
            if(!TransitWowUi.shouldDisplay(departure,now,false))continue;
            if(shown++>=9)break;
            TransitWowUi.Entry entry=TransitWowUi.departure(this,departure,true,v->{
                showRoute(departure);
            });
            timers.add(new Countdown(departure,entry.countdown,entry.status));
            gpsEntries.add(entry);
            departures.addView(entry.card);
            NativeUi.addSpacer(departures,this,7);
        }
        if(shown==0){
            LinearLayout empty=NativeUi.card(this);
            empty.addView(TransitWowUi.type(this,
                data.departures.isEmpty()?
                    "Brak kursów w najbliższych dwóch godzinach":
                    "Brak nadchodzących odjazdów",
                17,true,TransitWowUi.ink(this)));
            empty.addView(TransitWowUi.type(this,
                data.departures.isEmpty()?
                    "Zmień linię lub stanowisko.":
                    "Minione kursy ukryte. Zaległe pokażą się tylko przy świeżej prognozie LIVE.",
                13,false,TransitWowUi.subtle(this)));
            departures.addView(empty);
        }
        if("warsaw".equals(provider())&&!data.departures.isEmpty()){
            showWowGps(data.departures.get(0),line,!updated);
        }else if("mld".equals(provider())){
            LinearLayout advisory=NativeUi.card(this);
            advisory.addView(TransitWowUi.type(this,
                "MLD · status odjazdów",16,true,TransitWowUi.ink(this)));
            advisory.addView(TransitWowUi.type(this,
                data.liveNote==null?"Brak świeżej, zweryfikowanej prognozy dla tego kursu.":
                    data.liveNote,13,false,TransitWowUi.subtle(this)));
            departures.addView(advisory);
        }
        ui.removeCallbacks(tick);
        if(visible&&!timers.isEmpty())ui.postDelayed(tick,30000L);
    }
'''
s=s[:start]+replace+s[end:]
once('    private void showWowGps(GtfsNearby.Departure selected,String line){',
     '    private void showWowGps(GtfsNearby.Departure selected,String line,boolean auto){',
     "GPS UI automatic initial fetch")
once('"POZYCJE POJAZDÓW · LIVE"', '"ODJAZDY · GPS LIVE"', "actual arrival screen label")
once('''        card.addView(TransitWowUi.type(this,
            "Dane GPS nie określają godziny przyjazdu do przystanku.",12,false,
            TransitWowUi.subtle(this)));''',
'''        card.addView(TransitWowUi.type(this,
            "GPS LIVE oznacza szacowany czas przyjazdu, nie gwarancję punktualności.",
            12,false,TransitWowUi.subtle(this)));''',"GPS estimate description")
once('''        refreshGps.setOnClickListener(v->fetchWowGps(selected,state,++gpsSerial));
        fetchWowGps(selected,state,serial);''',
'''        refreshGps.setOnClickListener(v->fetchWowGps(selected,state,++gpsSerial));
        if(auto)fetchWowGps(selected,state,serial);
        else state.setText(wowGpsStatus);''',"manual refresh loop guard")
once('''                message.setText(result==null?
                    "Brak świeżych prognoz GPS. Pozostają godziny rozkładowe.":
                    result.note+"\\nDane: Miasto Stołeczne Warszawa · mkuran.pl.");
                for(int i=0;i<gpsEntries.size()&&i<timers.size();i++)
                    TransitWowUi.update(gpsEntries.get(i),timers.get(i).departure);''',
'''                wowGpsStatus=result==null?
                    "Brak świeżego GPS. Pokazuję wyłącznie nadchodzące odjazdy rozkładowe.":
                    result.note+"\\nDane miejskie Warszawy.";
                // Rebuild from verified live estimates: a bus delayed past scheduled
                // time may now appear. No schedule-only departure survives its time.
                renderWow(loaded,wowLastLine,wowLastStop,true);''',
     "recreate cards once with delayed live")
once('''            for(Countdown row:timers){
                Integer delay=row.departure.confirmedDelayMinutes(now);
                row.text.setText(TransitWowUi.countdown(row.departure,now));
                row.delay.setText(TransitWowUi.timing(row.departure));
            }''',
'''            for(int i=0;i<gpsEntries.size()&&i<timers.size();i++)
                TransitWowUi.update(gpsEntries.get(i),timers.get(i).departure);''',
     "expiry and live style every tick")
p.write_text(s,encoding="utf-8")
print("PASS: manual results display only forthcoming departures or genuine live-confirmed late buses")
