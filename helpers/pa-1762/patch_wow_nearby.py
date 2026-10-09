#!/usr/bin/env python3
"""PA 1.7.62: genuinely simpler on-screen interaction; no external provider/application links."""
from pathlib import Path
from shutil import copyfile

base=Path("project/app/src/main/java/com/ispina/lokalnie")
copyfile("helpers/pa-1762/TransitWowUi.java",base/"TransitWowUi.java")
def replace_once(s,a,b,why):
    n=s.count(a)
    if n!=1:raise AssertionError(f"{why}: {n}")
    return s.replace(a,b,1)

near=base/"NearbyDeparturesActivity.java"
s=near.read_text(encoding="utf-8")
s=replace_once(s,'    private LocationManager manager;',
'''    private final ExecutorService gpsWorker=Executors.newSingleThreadExecutor();
    private final List<TransitWowUi.Entry> wowRows=new ArrayList<>();
    private GtfsNearby.Result wowData;
    private LinearLayout wowStops,wowList,wowGps;
    private EditText wowSearch;
    private String wowSelectedStop="",wowLiveRoute="";
    private boolean wowMode;
    private int wowLiveSerial;
    private LocationManager manager;''',"modern view state")
s=replace_once(s,'                render(found,fix);',
    '                 renderWow(found,fix);',"use wow view")
# keep legacy method available for audit/compatibility, but only new UI renders.
s=replace_once(s,'        status.setText("Nie mogę wyświetlić aktualnych odjazdów: "+problem);',
    '        status.setText("Nie udało się odczytać odjazdów: "+problem);',"error status")
s=replace_once(s,
'''        Button web=NativeUi.button(this,"Otwórz rozkład przewoźnika",true);
        web.setOnClickListener(v->open(GtfsNearby.networkFor(fix.getLatitude(),fix.getLongitude()).equals("warsaw")?
            "https://www.wtp.waw.pl/rozklady-jazdy/":"https://kolejemalopolskie.com.pl/pl/rozklad-jazdy/rozklady-autobusowe"));
        results.addView(web,new LinearLayout.LayoutParams(-1,dp(54)));''',
'''        results.addView(TransitWowUi.type(this,
            "Spróbuj ponownie albo wybierz linię ręcznie bez lokalizacji.",16,false,
            TransitWowUi.subtle(this)));
        Button again=NativeUi.button(this,"Spróbuj ponownie",true);
        again.setOnClickListener(v->begin());
        results.addView(again,new LinearLayout.LayoutParams(-1,dp(50)));''',"internal error recovery")
s=replace_once(s,
'''        if(lastFix!=null && System.currentTimeMillis()-lastFixAt<=FIX_REUSE_MS){
            searchTimetable(new Location(lastFix),true);
        }else begin(); // After five minutes, obtain a fresh opt-in GPS fix.''',
'''        if(lastFix!=null){
            searchTimetable(new Location(lastFix),true);
        }else if(savedAvailable())useSavedFix();
        else begin();''',"refresh should never demand fresh indoor GPS unnecessarily")
s=replace_once(s,'    private void render(GtfsNearby.Result value,Location fix){',
'''    /** Passenger-first layout. Exactly four nearby named stops, then their next departures. */
    private void renderWow(GtfsNearby.Result data,Location fix){
        wowMode=true;wowLiveSerial++;wowLiveRoute="";
        wowData=data;stopCountdowns();results.removeAllViews();
        wowRows.clear();wowSelectedStop="";
        SimpleDateFormat format=new SimpleDateFormat("dd.MM HH:mm",new Locale("pl","PL"));
        format.setTimeZone(java.util.TimeZone.getTimeZone("Europe/Warsaw"));
        status.setText((usingSavedFix?"Pozycja orientacyjna · ":"")+
            "Rozkład: "+format.format(new Date(data.downloadedAt))+
            (data.oldData?" · dane starsze":""));
        LinearLayout introduction=NativeUi.card(this);
        introduction.addView(TransitWowUi.type(this,"Twoje najbliższe przystanki",
            22,true,TransitWowUi.ink(this)));
        NativeUi.addSpacer(introduction,this,4);
        introduction.addView(TransitWowUi.type(this,
            "Wybierz przystanek. Zobaczysz tylko jego najbliższe odjazdy.",
            14,false,TransitWowUi.subtle(this)));
        results.addView(introduction);
        NativeUi.addSpacer(results,this,10);

        wowStops=new LinearLayout(this);
        wowStops.setOrientation(LinearLayout.HORIZONTAL);
        android.widget.HorizontalScrollView strip=new android.widget.HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        strip.addView(wowStops);
        results.addView(strip);

        LinkedHashMap<String,GtfsNearby.Stop> unique=new LinkedHashMap<>();
        for(GtfsNearby.Stop stop:data.stops){
            String key=stop.name.toLowerCase(Locale.ROOT);
            if(!unique.containsKey(key))unique.put(key,stop);
            if(unique.size()==4)break;
        }
        if(unique.isEmpty()){
            LinearLayout none=NativeUi.card(this);
            none.addView(TransitWowUi.type(this,"Brak przystanków w zasięgu",
                18,true,TransitWowUi.ink(this)));
            none.addView(TransitWowUi.type(this,
                "Możesz wyszukać numer linii bez GPS w polu powyżej.",
                14,false,TransitWowUi.subtle(this)));
            results.addView(none);
            return;
        }
        wowSelectedStop=unique.values().iterator().next().name.toLowerCase(Locale.ROOT);
        addWowStopTabs(unique);

        LinearLayout search=NativeUi.card(this);
        search.addView(TransitWowUi.type(this,"Szukaj w odjazdach",15,true,TransitWowUi.ink(this)));
        wowSearch=new EditText(this);
        wowSearch.setSingleLine(true);
        wowSearch.setTextSize(18);
        wowSearch.setHint("Numer linii lub kierunek…");
        wowSearch.setContentDescription("Filtr numeru linii i kierunku na wybranym przystanku");
        wowSearch.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence t,int start,int count,int after){}
            @Override public void afterTextChanged(Editable t){}
            @Override public void onTextChanged(CharSequence t,int start,int before,int count){
                refreshWowRows();
            }
        });
        search.addView(wowSearch,new LinearLayout.LayoutParams(-1,dp(52)));
        results.addView(search);
        NativeUi.addSpacer(results,this,9);
        wowList=new LinearLayout(this);wowList.setOrientation(LinearLayout.VERTICAL);
        results.addView(wowList);
        NativeUi.addSpacer(results,this,9);
        wowGps=new LinearLayout(this);wowGps.setOrientation(LinearLayout.VERTICAL);
        results.addView(wowGps);
        refreshWowRows();
    }
    private void addWowStopTabs(LinkedHashMap<String,GtfsNearby.Stop> stops){
        wowStops.removeAllViews();
        int n=0;
        boolean dark=(getResources().getConfiguration().uiMode&
            Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        for(GtfsNearby.Stop stop:stops.values()){
            final String key=stop.name.toLowerCase(Locale.ROOT);
            boolean selected=key.equals(wowSelectedStop);
            LinearLayout tab=new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setPadding(dp(13),dp(13),dp(13),dp(12));
            tab.setBackground(TransitWowUi.round(this,
                selected?(dark?0xFF344254:0xFFDEE8F0):(dark?0xFF222B36:0xFFFFFFFF),
                selected?(dark?0xFF859DB4:0xFF728DA7):(dark?0xFF3A4654:0xFFDAE2E9),14));
            tab.addView(TransitWowUi.type(this,(n+1)+". "+stop.name,16,true,TransitWowUi.ink(this)));
            tab.addView(TransitWowUi.type(this,
                Math.round(stop.distance)+" m · w linii prostej",12,false,TransitWowUi.subtle(this)));
            tab.setClickable(true);tab.setFocusable(true);
            tab.setContentDescription("Wybierz przystanek "+stop.name+
                ". Odległość w linii prostej "+Math.round(stop.distance)+" metrów.");
            tab.setOnClickListener(v->{
                if(!wowSelectedStop.equals(key)){
                    wowSelectedStop=key;wowLiveRoute="";wowLiveSerial++;
                    addWowStopTabs(stops);refreshWowRows();
                }
            });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(178),-2);
            if(n++>0)lp.leftMargin=dp(7);
            wowStops.addView(tab,lp);
        }
    }
    private void refreshWowRows(){
        if(wowList==null||wowSearch==null||wowData==null)return;
        wowList.removeAllViews();wowRows.clear();countdownLabels.clear();
        String q=wowSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        String selectedName="";
        for(GtfsNearby.Stop stop:wowData.stops){
            if(stop.name.toLowerCase(Locale.ROOT).equals(wowSelectedStop)){
                selectedName=stop.name;break;
            }
        }
        wowList.addView(TransitWowUi.type(this,
            "ODJAZDY  ·  "+selectedName,17,true,TransitWowUi.ink(this)));
        NativeUi.addSpacer(wowList,this,10);
        int shown=0;
        GtfsNearby.Departure first=null;
        for(GtfsNearby.Departure d:wowData.departures){
            if(!d.stop.name.toLowerCase(Locale.ROOT).equals(wowSelectedStop))continue;
            if(!q.isEmpty()&&!d.line.toLowerCase(Locale.ROOT).contains(q)&&
               !d.headsign.toLowerCase(Locale.ROOT).contains(q))continue;
            if(shown++==0)first=d;
            if(shown>9)break;
            TransitWowUi.Entry e=TransitWowUi.departure(this,d,true,v->{
                route(d);
                selectWowLive(d,true);
            });
            wowRows.add(e);
            countdownLabels.add(new CountdownLabel(d,e.countdown,e.status));
            wowList.addView(e.card);
            NativeUi.addSpacer(wowList,this,7);
        }
        if(shown==0)wowList.addView(TransitWowUi.type(this,
            q.isEmpty()?"Brak najbliższych odjazdów z wybranego przystanku.":
                "Nie znaleziono kursów tej linii lub kierunku.",
            15,false,TransitWowUi.subtle(this)));
        startCountdowns();
        if(first!=null)selectWowLive(first,false);
        else if(wowGps!=null){wowGps.removeAllViews();wowLiveRoute="";wowLiveSerial++;}
    }
    private void selectWowLive(GtfsNearby.Departure dep,boolean force){
        if(wowGps==null)return;
        if(!"warsaw".equals(GtfsNearby.networkFor(dep.stop.lat,dep.stop.lon))||
            !"bus".equals(dep.mode)&&!"tram".equals(dep.mode)){
            wowGps.removeAllViews();
            wowGps.addView(TransitWowUi.type(this,
                "Odjazdy planowe · przewoźnik nie udostępnił zgodnych prognoz LIVE.",
                13,false,TransitWowUi.subtle(this)));
            wowLiveRoute="";wowLiveSerial++;return;
        }
        if(!force&&dep.routeId!=null&&dep.routeId.equals(wowLiveRoute))return;
        final int ticket=++wowLiveSerial;
        wowLiveRoute=dep.routeId;
        wowGps.removeAllViews();
        LinearLayout frame=NativeUi.card(this);
        frame.addView(TransitWowUi.type(this,"POZYCJE POJAZDÓW · LIVE",
            15,true,TransitWowUi.ink(this)));
        TextView state=TransitWowUi.type(this,"Linia "+dep.line+
            " · sprawdzam sygnały GPS…",15,false,TransitWowUi.subtle(this));
        state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        frame.addView(state);
        frame.addView(TransitWowUi.type(this,
            "Pozycja pojazdu nie jest prognozą przyjazdu. Bez zewnętrznych aplikacji.",
            12,false,TransitWowUi.subtle(this)));
        wowGps.addView(frame);
        if(dep.routeId==null||dep.routeId.isEmpty()){state.setText("Brak identyfikatora linii LIVE.");return;}
        gpsWorker.execute(()->{
            com.ispina.lokalnie.transit.WarsawVehicleGps.Snapshot snap=null;
            try{
                snap=com.ispina.lokalnie.transit.WarsawVehicleGps.load(
                    java.util.Collections.singleton(dep.routeId),dep.stop.lat,dep.stop.lon);
            }catch(Exception ignored){}
            final com.ispina.lokalnie.transit.WarsawVehicleGps.Snapshot got=snap;
            ui.post(()->{
                if(destroyed||!screenVisible||ticket!=wowLiveSerial)return;
                if(got==null){
                    state.setText("Brak aktualnego sygnału GPS linii "+dep.line+
                        ". Godziny odjazdów są rozkładowe.");
                }else{
                    String text="Linia "+dep.line+" · pojazdy z aktualnym GPS: "+got.vehicles;
                    if(got.vehicles>0&&!Double.isNaN(got.nearestMeters))
                        text+=" · najbliższy "+Math.round(got.nearestMeters)+" m";
                    state.setText(text+"\nDane miasta Warszawy · aktualne pozycje pojazdów.");
                }
            });
        });
    }

    private void render(GtfsNearby.Result value,Location fix){''',"new passenger-first render")
s=replace_once(s,
'''        for(CountdownLabel label:countdownLabels){
            Integer delay=label.departure.confirmedDelayMinutes(now);''',
'''        for(CountdownLabel label:countdownLabels){
            Integer delay=label.departure.confirmedDelayMinutes(now);
            if(wowMode){
                label.view.setText(DepartureCountdown.label(label.departure.when,now,delay));
                label.liveState.setText(TransitWowUi.timing(label.departure));
                continue;
            }''',"modern countdown")
s=replace_once(s,
'''        // Recompute the hero when departure times pass or an update expires.
        refreshNearestFromVisible(now);''',
'''        if(wowMode)return;
        // Recompute the hero when departure times pass or an update expires.
        refreshNearestFromVisible(now);''',"modern skip legacy hero")
s=replace_once(s,'    @Override protected void onStop(){',
    '''    @Override protected void onStop(){
        wowLiveSerial++;''',"cancel live when backgrounded")
s=replace_once(s,'ui.removeCallbacksAndMessages(null);work.shutdownNow();super.onDestroy();',
    'ui.removeCallbacksAndMessages(null);work.shutdownNow();gpsWorker.shutdownNow();super.onDestroy();',
    "close live executor")
near.write_text(s,encoding="utf-8")
print("PASS: modern passenger-first nearby screen wired")
