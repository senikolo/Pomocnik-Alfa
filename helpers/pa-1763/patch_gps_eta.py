#!/usr/bin/env python3
"""PA 1.7.63: actual, conservative GPS-derived arrival predictions directly on cards."""
from pathlib import Path
from shutil import copyfile

root=Path("project/app/src/main/java/com/ispina/lokalnie")
transit=root/"transit"
copyfile("helpers/pa-1763/WarsawGpsEta.java",transit/"WarsawGpsEta.java")
def change(path,old,new,label):
    s=path.read_text(encoding="utf-8")
    n=s.count(old)
    if n!=1:raise AssertionError(f"{label}: expected one match, found {n}")
    path.write_text(s.replace(old,new,1),encoding="utf-8")

gtfs=transit/"GtfsNearby.java"
change(gtfs,'        public Integer liveDelaySeconds;',
'''        // GPS-derived ETA is an ESTIMATE, not provider-confirmed TripUpdate.
        public Long gpsEtaWhenMillis;
        public long gpsEtaSeenAt;
        public boolean hasGpsEstimate(long now){
            return gpsEtaWhenMillis!=null && gpsEtaSeenAt>0 &&
                gpsEtaSeenAt<=now+30000L && now-gpsEtaSeenAt<=120000L;
        }
        public Integer liveDelaySeconds;''',"GPS ETA fields")
fast=transit/"FastLineStops.java"
change(fast,
    '''        try(BufferedReader reader=new BufferedReader(new InputStreamReader(
                new GZIPInputStream(ctx.getAssets().open("pa_gtfs/"+network+"-lines.tsv.gz")),
                StandardCharsets.UTF_8))){''',
    '''        // Android Asset Packaging Tool inflates .gz assets and strips the suffix.
        // Read the actual packaged plain TSV first; retain old gzip fallback.
        InputStream source;
        try{source=ctx.getAssets().open("pa_gtfs/"+network+"-lines.tsv");}
        catch(IOException missing){
            source=new GZIPInputStream(ctx.getAssets().open("pa_gtfs/"+network+"-lines.tsv.gz"));
        }
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(
                source,StandardCharsets.UTF_8))){''',"ACTUAL packaged index naming")
change(fast,'import java.io.InputStreamReader;',
    'import java.io.InputStreamReader;\nimport java.io.InputStream;',
    "stream import for plain Android asset")
ui=root/"TransitWowUi.java"
change(ui,'        public final TextView status;',
'''        public final TextView status;
        public final TextView clock;''',"ETA clock target")
change(ui,
'''        Entry(LinearLayout c,TextView t,TextView s){card=c;countdown=t;status=s;}''',
'''        Entry(LinearLayout c,TextView t,TextView s,TextView w){
            card=c;countdown=t;status=s;clock=w;
        }''',"ETA entry constructor")
change(ui,
'''    public static String timing(GtfsNearby.Departure departure){''',
'''    public static String countdown(GtfsNearby.Departure departure,long now){
        if(departure.confirmedDelayMinutes(now)!=null)
            return DepartureCountdown.label(departure.when,now,
                departure.confirmedDelayMinutes(now));
        if(departure.hasGpsEstimate(now)){
            long seconds=(departure.gpsEtaWhenMillis-now)/1000L;
            if(seconds<=20)return "TERAZ · GPS";
            return "za "+((seconds+59L)/60L)+" min · GPS";
        }
        return DepartureCountdown.label(departure.when,now,null);
    }
    public static void update(Entry e,GtfsNearby.Departure d){
        long now=System.currentTimeMillis();
        e.countdown.setText(countdown(d,now));
        e.status.setText(timing(d));
        e.clock.setText(clock(d.hasGpsEstimate(now)?d.gpsEtaWhenMillis:d.when));
        e.clock.setContentDescription(d.hasGpsEstimate(now)?
            "Szacowany odjazd GPS "+clock(d.gpsEtaWhenMillis):
            "Planowy odjazd "+clock(d.when));
    }
    public static String timing(GtfsNearby.Departure departure){''',"true live countdown and clock")
change(ui,
'''        Integer delay=departure.confirmedDelayMinutes(System.currentTimeMillis());
        return delay==null?"ROZKŁADOWO":delay==0?"LIVE · bez opóźnienia":
            "LIVE · "+(delay>0?"+":"")+delay+" min";''',
'''        long now=System.currentTimeMillis();
        Integer confirmed=departure.confirmedDelayMinutes(now);
        if(confirmed!=null)
            return confirmed==0?"LIVE · potwierdzone":
                "LIVE · "+(confirmed>0?"+":"")+confirmed+" min";
        if(departure.hasGpsEstimate(now))return "GPS LIVE · SZACUNEK";
        return "ROZKŁADOWO";''',"live status precision")
change(ui,
'''        TextView countdown=type(a,DepartureCountdown.label(d.when,System.currentTimeMillis(),
            d.confirmedDelayMinutes(System.currentTimeMillis())),16,true,ink(a));''',
'''        TextView countdown=type(a,countdown(d,System.currentTimeMillis()),
            16,true,ink(a));''',"ETA countdown initial")
change(ui,'        return new Entry(panel,countdown,live);',
          '        return new Entry(panel,countdown,live,tm);',"clock update reference")

near=root/"NearbyDeparturesActivity.java"
change(near,'    private GtfsNearby.Result wowData;',
'''    private GtfsNearby.Result wowData;
    private int gpsEtaGeneration;
    private final Runnable etaRepeat=new Runnable(){
        @Override public void run(){
            if(screenVisible && wowMode && wowData!=null)
                requestGpsEstimates(wowData);
        }
    };''',"recurring GPS ETA")
change(near,
    '''        refreshWowRows();
    }
    private void addWowStopTabs(''',
    '''        refreshWowRows();
        requestGpsEstimates(data);
    }
    private void requestGpsEstimates(GtfsNearby.Result data){
        if(!screenVisible||destroyed||!wowMode)return;
        final int call=++gpsEtaGeneration;
        ui.removeCallbacks(etaRepeat);
        if(!"warsaw".equals(GtfsNearby.networkFor(data.stops.isEmpty()?0:data.stops.get(0).lat,
                              data.stops.isEmpty()?0:data.stops.get(0).lon)))
            return;
        gpsWorker.execute(()->{
            com.ispina.lokalnie.transit.WarsawGpsEta.Result estimate=null;
            try{estimate=com.ispina.lokalnie.transit.WarsawGpsEta.apply(
                getApplicationContext(),data.departures);
            }catch(Exception ignored){}
            final com.ispina.lokalnie.transit.WarsawGpsEta.Result finished=estimate;
            ui.post(()->{
                if(!screenVisible||destroyed||call!=gpsEtaGeneration||data!=wowData)return;
                for(int i=0;i<countdownLabels.size()&&i<wowRows.size();i++)
                    TransitWowUi.update(wowRows.get(i),countdownLabels.get(i).departure);
                if(wowGps!=null){
                    wowGps.removeAllViews();
                    LinearLayout note=NativeUi.card(this);
                    note.addView(TransitWowUi.type(this,"ODJAZDY W CZASIE RZECZYWISTYM",
                        16,true,TransitWowUi.ink(this)));
                    note.addView(TransitWowUi.type(this,
                        finished==null?"Brak świeżych danych GPS. Pokazuję godziny rozkładowe.":
                            finished.note,
                        14,false,TransitWowUi.subtle(this)));
                    note.addView(TransitWowUi.type(this,
                        "GPS LIVE to prognoza na podstawie aktualnej pozycji autobusu, nie gwarantowany czas przyjazdu.",
                        12,false,TransitWowUi.subtle(this)));
                    wowGps.addView(note);
                }
                ui.postDelayed(etaRepeat,60000L);
            });
        });
    }
    private void addWowStopTabs(''',"live request in passenger view")
# Avoid loading vehicle positions twice and re-rendering the summary.
change(near,
'''        if(first!=null)selectWowLive(first,false);
        else if(wowGps!=null){wowGps.removeAllViews();wowLiveRoute="";wowLiveSerial++;}''',
'''        if(first==null && wowGps!=null)wowGps.removeAllViews();
        for(int i=0;i<countdownLabels.size()&&i<wowRows.size();i++)
            TransitWowUi.update(wowRows.get(i),countdownLabels.get(i).departure);''',
"no separate vehicles-only feed")
change(near,
'''            if(wowMode){
                label.view.setText(DepartureCountdown.label(label.departure.when,now,delay));
                label.liveState.setText(TransitWowUi.timing(label.departure));
                continue;
            }''',
'''            if(wowMode){
                label.view.setText(TransitWowUi.countdown(label.departure,now));
                label.liveState.setText(TransitWowUi.timing(label.departure));
                continue;
            }''',"live countdown tick")
change(near,'        wowLiveSerial++;\n        screenVisible=false;',
'''        wowLiveSerial++;
        gpsEtaGeneration++;ui.removeCallbacks(etaRepeat);
        screenVisible=false;''',"background stop")
# WOW screens use real GPS estimates, old vehicle location helper remains in source but isn't invoked.

manual=root/"LineDeparturesActivity.java"
change(manual,'    private int gpsSerial;',
'''    private final List<TransitWowUi.Entry> gpsEntries=new ArrayList<>();
    private GtfsNearby.Result latestResult;
    private int gpsSerial;''',"manual realtime state")
change(manual,
'''                row.text.setText(DepartureCountdown.label(row.departure.when,now,delay));''',
'''                row.text.setText(TransitWowUi.countdown(row.departure,now));''',
"manual countdown")
change(manual,
'''        stopCountdown();gpsSerial++;
        departures.removeAllViews();''',
'''        stopCountdown();gpsSerial++;latestResult=data;gpsEntries.clear();
        departures.removeAllViews();''',"manual result state")
change(manual,
'''            timers.add(new Countdown(departure,entry.countdown,entry.status));''',
'''            timers.add(new Countdown(departure,entry.countdown,entry.status));
            gpsEntries.add(entry);''',"live UI entries manual")
change(manual,
'''        gpsWorker.execute(()->{
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
        });''',
'''        final GtfsNearby.Result loaded=latestResult;
        if(loaded==null)return;
        gpsWorker.execute(()->{
            com.ispina.lokalnie.transit.WarsawGpsEta.Result estimate=null;
            try{
                estimate=com.ispina.lokalnie.transit.WarsawGpsEta.apply(
                    getApplicationContext(),loaded.departures);
            }catch(Exception ignored){}
            final com.ispina.lokalnie.transit.WarsawGpsEta.Result result=estimate;
            ui.post(()->{
                if(destroyed||!visible||ticket!=gpsSerial||loaded!=latestResult)return;
                message.setText(result==null?
                    "Brak świeżych prognoz GPS. Pozostają godziny rozkładowe.":
                    result.note+"\\nDane: Miasto Stołeczne Warszawa · mkuran.pl.");
                for(int i=0;i<gpsEntries.size()&&i<timers.size();i++)
                    TransitWowUi.update(gpsEntries.get(i),timers.get(i).departure);
            });
        });''',"replace raw vehicle count with true GPS departure ETAs")
change(manual,'        visible=false;gpsSerial++;ui.removeCallbacks(tick);super.onStop();',
    '        visible=false;gpsSerial++;ui.removeCallbacks(tick);super.onStop();',
    "preserve stop lifecycle")
# Version bump.
main=root/"MainActivity.java"
change(main,"POMOCNIK ALFA 1.7.62","POMOCNIK ALFA 1.7.63","app visible version")
gradle=Path("project/app/build.gradle")
change(gradle,"versionCode 10763","versionCode 10764","APK versionCode")
change(gradle,"versionName '1.7.62-A10'","versionName '1.7.63-A10'","APK versionName")
print("PASS: PA 1.7.63 GPS-derived live ETAs, accurate asset load, freshness gates and in-page status")
