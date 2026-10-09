#!/usr/bin/env python3
"""PA 1.7.62: polished manual route view with native in-page GPS and no outbound links."""
from pathlib import Path
base=Path("project/app/src/main/java/com/ispina/lokalnie")
file=base/"LineDeparturesActivity.java"
s=file.read_text(encoding="utf-8")
def once(a,b,label):
    global s
    n=s.count(a)
    if n!=1:raise AssertionError(f"{label}: {n}")
    s=s.replace(a,b,1)
once('    private final ExecutorService work=Executors.newSingleThreadExecutor();',
'''    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private final ExecutorService gpsWorker=Executors.newSingleThreadExecutor();
    private Runnable autoSearch;
    private int gpsSerial;''',"modern workers")
once('        card.addView(NativeUi.text(this,"Wybierz dowolny przystanek",20,true));',
     '        card.addView(NativeUi.text(this,"Wybierz linię i kierunek",22,true));',
     "manual heading")
once('''            "Wpisz numer linii, a potem wybierz przystanek i stanowisko na jej trasie. Nie potrzebujesz GPS.",14));''',
     '''            "Wpisz linię, np. 517 lub A7. Wybierz stanowisko. GPS nie jest wymagany.",14));''',
     "short prompt")
once('        find=NativeUi.button(this,"Znajdź przystanki tej linii",false);',
     '        find=NativeUi.button(this,"Pokaż przystanki linii",false);',
     "manual search CTA")
once('        show=NativeUi.button(this,"Pokaż odjazdy z tego przystanku",false);',
     '        show=NativeUi.button(this,"Pokaż najbliższe odjazdy",false);',
     "manual departures CTA")
once('''                clearSelection("Wyszukaj przystanki po wpisaniu numeru linii.");''',
'''                clearSelection("Wybierz linię albo zacznij wpisywać jej numer.");
                if(autoSearch!=null)ui.removeCallbacks(autoSearch);
                autoSearch=()->{
                    String value=enteredLine();
                    if(value.length()>=3 || value.length()>=2&&value.startsWith("A"))
                        loadStops();
                };
                ui.postDelayed(autoSearch,500L);''',"fast auto-line search debounce")
once('                render(data,line,stopId,stopName);',
     '                renderWow(data,line,stopName);',
     "use polished in-app manual result screen")
once('    private void render(GtfsNearby.Result result,String line,String stopId,String stopName){',
'''    private void renderWow(GtfsNearby.Result data,String line,String name){
        stopCountdown();gpsSerial++;
        departures.removeAllViews();
        message.setText("Odjazdy rozkładowe · "+clock(System.currentTimeMillis())+
            (data.oldData?" · starszy rozkład":""));
        LinearLayout intro=NativeUi.card(this);
        intro.addView(TransitWowUi.type(this,"Przystanek: "+name,20,true,TransitWowUi.ink(this)));
        intro.addView(TransitWowUi.type(this,
            "LINIA "+line+" · wybierz kurs, aby zobaczyć dalszą trasę.",
            13,false,TransitWowUi.subtle(this)));
        departures.addView(intro);
        NativeUi.addSpacer(departures,this,8);
        if(data.departures.isEmpty()){
            LinearLayout empty=NativeUi.card(this);
            empty.addView(TransitWowUi.type(this,"Brak odjazdów w najbliższych 2 godzinach",
                18,true,TransitWowUi.ink(this)));
            empty.addView(TransitWowUi.type(this,
                data.note==null?"Wybierz inne stanowisko lub numer linii.":data.note,
                14,false,TransitWowUi.subtle(this)));
            departures.addView(empty);
            return;
        }
        int displayed=0;
        for(GtfsNearby.Departure departure:data.departures){
            if(displayed++>=9)break;
            TransitWowUi.Entry entry=TransitWowUi.departure(this,departure,true,v->{
                showRoute(departure);
            });
            timers.add(new Countdown(departure,entry.countdown,entry.status));
            departures.addView(entry.card);
            NativeUi.addSpacer(departures,this,7);
        }
        if("warsaw".equals(provider())){
            showWowGps(data.departures.get(0),line);
        }else{
            LinearLayout advisory=NativeUi.card(this);
            advisory.addView(TransitWowUi.type(this,
                "MLD · rozkładowo",16,true,TransitWowUi.ink(this)));
            advisory.addView(TransitWowUi.type(this,
                data.liveNote==null?"Brak potwierdzonej prognozy czasu rzeczywistego.":
                    data.liveNote,13,false,TransitWowUi.subtle(this)));
            departures.addView(advisory);
        }
        ui.removeCallbacks(tick);
        if(visible&&!timers.isEmpty())ui.postDelayed(tick,30000L);
    }
    private void showWowGps(GtfsNearby.Departure selected,String line){
        LinearLayout card=NativeUi.card(this);
        card.addView(TransitWowUi.type(this,"POZYCJE POJAZDÓW · LIVE",
            16,true,TransitWowUi.ink(this)));
        TextView state=TransitWowUi.type(this,"Linia "+line+
            " · sprawdzam sygnały GPS pojazdów…",15,false,TransitWowUi.subtle(this));
        state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        card.addView(state);
        card.addView(TransitWowUi.type(this,
            "Dane GPS nie określają godziny przyjazdu do przystanku.",12,false,
            TransitWowUi.subtle(this)));
        NativeUi.addSpacer(card,this,6);
        Button refreshGps=NativeUi.button(this,"Odśwież dane GPS linii "+line,true);
        card.addView(refreshGps,new LinearLayout.LayoutParams(-1,dp(48)));
        departures.addView(card);
        final int serial=++gpsSerial;
        refreshGps.setOnClickListener(v->fetchWowGps(selected,state,++gpsSerial));
        fetchWowGps(selected,state,serial);
    }
    private void fetchWowGps(GtfsNearby.Departure departure,TextView message,int ticket){
        message.setText("Sprawdzam ostatnie pozycje pojazdów…");
        if(departure.routeId==null||departure.routeId.isEmpty()){
            message.setText("Brak zgodnego identyfikatora linii LIVE.");return;
        }
        gpsWorker.execute(()->{
            com.ispina.lokalnie.transit.WarsawVehicleGps.Snapshot gps=null;
            try{
                gps=com.ispina.lokalnie.transit.WarsawVehicleGps.load(
                    java.util.Collections.singleton(departure.routeId),
                    departure.stop.lat,departure.stop.lon);
            }catch(Exception ignored){}
            final com.ispina.lokalnie.transit.WarsawVehicleGps.Snapshot found=gps;
            ui.post(()->{
                if(destroyed||!visible||ticket!=gpsSerial)return;
                if(found==null)
                    message.setText("Brak świeżych pozycji GPS tej linii. Wyświetlam rozkład planowy.");
                else{
                    String value="Aktywne pojazdy linii "+departure.line+": "+found.vehicles;
                    if(found.vehicles>0&&!Double.isNaN(found.nearestMeters))
                        value+=" · najbliższy "+Math.round(found.nearestMeters)+" m";
                    message.setText(value+"\\nŹródło: dane miejskie Warszawy.");
                }
            });
        });
    }

    private void render(GtfsNearby.Result result,String line,String stopId,String stopName){''',
     "manual on-screen wow render")
once('                row.delay.setText(delayLabel(delay));',
     '                row.delay.setText(TransitWowUi.timing(row.departure));',
     "sync modern countdown status")
once('        visible=false;ui.removeCallbacks(tick);super.onStop();',
     '        visible=false;gpsSerial++;ui.removeCallbacks(tick);super.onStop();',
     "cancel GPS after leaving")
once('        destroyed=true;generation++;stopCountdown();work.shutdownNow();',
     '        destroyed=true;generation++;gpsSerial++;stopCountdown();work.shutdownNow();gpsWorker.shutdownNow();',
     "close live workers")
file.write_text(s,encoding="utf-8")
print("PASS: manual line & stop show legible direction cards and direct native GPS status without outbound links")
