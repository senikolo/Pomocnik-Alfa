#!/usr/bin/env python3
"""PA 1.7.61: offline GTFS, four useful stops, indoor fallback, compact true GPS status."""
from pathlib import Path
from shutil import copyfile

base=Path("project/app/src/main/java/com/ispina/lokalnie")
gtfs=base/"transit/GtfsNearby.java"
near=base/"NearbyDeparturesActivity.java"
manual=base/"LineDeparturesActivity.java"

def once(s,a,b,label):
    n=s.count(a)
    assert n==1,(label,n)
    return s.replace(a,b,1)

copyfile("helpers/pa-1761/FastLineStops.java",base/"transit/FastLineStops.java")

s=gtfs.read_text(encoding="utf-8")
s=once(s,'private static final long FRESH=21L*3600000L;',
    'private static final long FRESH=48L*3600000L;',"cache freshness")
s=once(s,
    '        if(dest.isFile()&&dest.length()>10000&&System.currentTimeMillis()-dest.lastModified()<FRESH)return dest;',
    '''        // First use: install the bundled, already-downloaded schedule (no network wait).
        if(!dest.isFile()||dest.length()<=10000){
            File packaged=new File(dir,provider+".packaged");
            try(InputStream input=ctx.getAssets().open("pa_gtfs/"+provider+"-lite.zip");
                OutputStream output=new BufferedOutputStream(new FileOutputStream(packaged))){
                byte[] bytes=new byte[32768];int n;long total=0;
                while((n=input.read(bytes))!=-1){
                    total+=n;if(total>MAX_BYTES)throw new IOException("Za duży rozkład offline");
                    output.write(bytes,0,n);
                }
                output.flush();
                try(ZipFile check=new ZipFile(packaged)){
                    if(check.getEntry("stops.txt")==null||check.getEntry("stop_times.txt")==null)
                        throw new IOException("Niepełny rozkład offline");
                }
                if(dest.exists()&&!dest.delete())throw new IOException("Stary plik");
                if(!packaged.renameTo(dest))throw new IOException("Nie mogę zapisać rozkładu offline");
            }catch(Exception ignored){if(packaged.exists())packaged.delete();}
        }
        out.downloadedAt=dest.lastModified();
        if(dest.isFile()&&dest.length()>10000&&System.currentTimeMillis()-dest.lastModified()<FRESH)return dest;''',
    "install packaged feed")
s=once(s,'System.currentTimeMillis()-dest.lastModified()<72L*3600000L',
       'System.currentTimeMillis()-dest.lastModified()<14L*24L*3600000L',
       "longer verified cache fallback")
s=once(s,
    '        Result data=new Result();\n        List<LineStop> output=new ArrayList<>();',
    '''        // A small indexed asset makes line search near-instantaneous and offline.
        try {
            List<LineStop> indexed=FastLineStops.forLine(ctx,provider,line);
            if(indexed!=null)return indexed;
        }catch(IOException ignored){ /* legacy ZIP scanner below */ }
        Result data=new Result();
        List<LineStop> output=new ArrayList<>();''',
    "fast line lookup")
s=once(s,
    '            // Five nearby named stop complexes, up to four platforms within each.',
    '            // Examine eight candidates and up to two platforms each; keep four best after departures are known.',
    "top stops comment")
s=once(s,'if(railStations>=4||stop.distance>4300)continue;',
       'if(railStations>=2||stop.distance>2300)continue;',
       "rail limit")
s=once(s,'if(posts>=4 || (posts==0 && platformsByName.size()>=5))continue;',
       'if(posts>=2 || (posts==0 && platformsByName.size()>=8))continue;',
       "candidate groups")
s=once(s,
    '            result.departures.sort(Comparator.comparingLong(d->d.when));',
    '''            result.departures.sort(Comparator.comparingLong(d->d.when));
            chooseBestFour(result,moment);''',
    "rank top stops")
s=once(s,'    /** A stop/platform on a selected line; supports discovery without GPS. */',
'''    /** Prefer stops with imminent service, then short walking distance.
     * Four different named stop complexes maximum, counting any nearby rail stop.
     */
    private static void chooseBestFour(Result result,long now){
        Map<String,Double> distance=new HashMap<>();
        Map<String,Integer> service=new HashMap<>();
        for(Stop stop:result.stops){
            String key=stop.name.toLowerCase(Locale.ROOT);
            distance.put(key,Math.min(distance.getOrDefault(key,Double.MAX_VALUE),stop.distance));
        }
        for(Departure dep:result.departures){
            if(dep.when<now-60000L||dep.when>now+60L*60000L)continue;
            String key=dep.stop.name.toLowerCase(Locale.ROOT);
            service.put(key,service.getOrDefault(key,0)+1);
        }
        List<String> names=new ArrayList<>(distance.keySet());
        names.sort((a,b)->{
            double scoreA=distance.get(a)+(service.getOrDefault(a,0)==0?550:0)
                -Math.min(8,service.getOrDefault(a,0))*40;
            double scoreB=distance.get(b)+(service.getOrDefault(b,0)==0?550:0)
                -Math.min(8,service.getOrDefault(b,0))*40;
            int cmp=Double.compare(scoreA,scoreB);
            return cmp==0?a.compareTo(b):cmp;
        });
        Set<String> keep=new HashSet<>(names.subList(0,Math.min(4,names.size())));
        result.stops.removeIf(stop->!keep.contains(stop.name.toLowerCase(Locale.ROOT)));
        result.departures.removeIf(dep->!keep.contains(dep.stop.name.toLowerCase(Locale.ROOT)));
        result.stops.sort(Comparator.comparingDouble(stop->stop.distance));
    }

    /** A stop/platform on a selected line; supports discovery without GPS. */''',"best stop utility")
gtfs.write_text(s,encoding="utf-8")

s=near.read_text(encoding="utf-8")
s=once(s,'    private Button locate,refresh;',
    '    private Button locate,refresh,previousLocation;', "stored location button field")
s=once(s,'    private Location lastFix;',
    '    private boolean usingSavedFix;\n    private Location lastFix;', "saved status field")
s=once(s,'        locate.setOnClickListener(v->begin());',
    '        locate.setOnClickListener(v->begin());',"locate original marker")
s=once(s,'        card.addView(locate,new LinearLayout.LayoutParams(-1,dp(55)));',
    '''        card.addView(locate,new LinearLayout.LayoutParams(-1,dp(55)));
        NativeUi.addSpacer(card,this,8);
        previousLocation=NativeUi.button(this,"⌖ Użyj ostatniej lokalizacji (wewnątrz)",true);
        previousLocation.setOnClickListener(v->useSavedFix());
        card.addView(previousLocation,new LinearLayout.LayoutParams(-1,dp(52)));''',"indoor option")
s=once(s,
    '        status=NativeUi.muted(this,"Dotknij przycisku. Lokalizacja nie działa w tle.",14);',
    '        status=NativeUi.muted(this,"Wewnątrz budynku możesz użyć lokalizacji sieciowej lub ostatniej zapisanej pozycji.",14);',
    "indoor friendly hint")
s=once(s,'        NativeUi.addSpacer(root,this,9);\n        LinearLayout manual=NativeUi.card(this);',
    '''        updateSavedAction();
        NativeUi.addSpacer(root,this,9);
        LinearLayout manual=NativeUi.card(this);''',"update action")
s=once(s,'        stopGps();locating=true;best=null;generation++;',
       '        usingSavedFix=false;stopGps();locating=true;best=null;generation++;',
       "clear manual fix flag")
# First occurrence belongs to requestFix; keep the lifecycle teardown intact.
s=s.replace('        lastFix=null;lastFixAt=0L;\n        if(refresh!=null)refresh.setEnabled(false);',
            '        if(refresh!=null)refresh.setEnabled(false);',1)
s=once(s,'        if(locating){timeout=this::finishFix;ui.postDelayed(timeout,16000);}',
       '        if(locating){timeout=this::finishFix;ui.postDelayed(timeout,6500);}',
       "short location wait")
s=once(s,'if(fix.getTime()>0&&Math.abs(System.currentTimeMillis()-fix.getTime())>150000) return;',
       'if(fix.getTime()>0&&Math.abs(System.currentTimeMillis()-fix.getTime())>30L*60000L) return;',
       "allow nearby indoor network last-known")
s=once(s,'        if(best.hasAccuracy()&&best.getAccuracy()<70f)finishFix();',
       '''        if(best.hasAccuracy()&&(best.getAccuracy()<120f ||
           (LocationManager.NETWORK_PROVIDER.equals(fix.getProvider())&&best.getAccuracy()<700f)))
            finishFix();''',"network quick fix")
s=once(s,'        if(fix==null){fail("Nie udało się pobrać aktualnej pozycji. Spróbuj przy oknie.");return;}',
       '        if(fix==null){fail("Brak nowej pozycji. Wybierz „Użyj ostatniej lokalizacji” albo wyszukaj linię bez GPS.");return;}',
       "GPS fail message")
s=once(s,'        lastFixAt=System.currentTimeMillis();\n        searchTimetable(fix,false);',
    '''        lastFixAt=System.currentTimeMillis();
        getSharedPreferences("pa_last_transit_fix",MODE_PRIVATE).edit()
            .putString("lat",Double.toString(fix.getLatitude()))
            .putString("lon",Double.toString(fix.getLongitude()))
            .putFloat("accuracy",fix.hasAccuracy()?fix.getAccuracy():999f)
            .putLong("at",lastFixAt).apply();
        updateSavedAction();
        searchTimetable(fix,false);''',
    "persist opted-in successful fix")
s=once(s,'    private void refreshDepartures(){',
'''    private boolean savedAvailable(){
        long at=getSharedPreferences("pa_last_transit_fix",MODE_PRIVATE).getLong("at",0L);
        return at>0&&System.currentTimeMillis()-at<24L*3600000L;
    }
    private void updateSavedAction(){
        if(previousLocation!=null)previousLocation.setEnabled(savedAvailable());
    }
    private void useSavedFix(){
        if(!savedAvailable()){
            status.setText("Brak lokalizacji z ostatnich 24 godzin. Skorzystaj z numeru linii.");
            updateSavedAction();return;
        }
        android.content.SharedPreferences saved=getSharedPreferences("pa_last_transit_fix",MODE_PRIVATE);
        try{
            double lat=Double.parseDouble(saved.getString("lat",""));
            double lon=Double.parseDouble(saved.getString("lon",""));
            if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)
                throw new IllegalArgumentException("współrzędne");
            stopGps();usingSavedFix=true;
            Location fix=new Location("saved");
            fix.setLatitude(lat);fix.setLongitude(lon);fix.setTime(saved.getLong("at",0L));
            fix.setAccuracy(saved.getFloat("accuracy",999f));
            lastFix=fix;lastFixAt=System.currentTimeMillis();
            searchTimetable(fix,false);
        }catch(Exception ex){status.setText("Nie można użyć zapisanej lokalizacji.");}
    }

    private void refreshDepartures(){''',"indoor last fix handlers")
s=once(s,
   '        status.setText("Odświeżam pobliskie odjazdy i aktualność rozkładów…");',
   '        status.setText(usingSavedFix?"Lokalizacja orientacyjna · szukam czterech przydatnych przystanków…":\n            "Wybieram cztery najlepsze przystanki i sprawdzam odjazdy…");',
   "refined progress text")
s=once(s,'        stopCard.addView(NativeUi.text(this,"🚏 5 najbliższych przystanków",19,true));',
    '''        stopCard.addView(NativeUi.text(this,"🚏 Cztery przydatne przystanki",19,true));
        stopCard.addView(NativeUi.muted(this,
            "Wybór według odległości i najbliższych kursów. Dwa stanowiska z każdej lokalizacji.",13));''',
    "four-stop title")
s=once(s,'            if(stop.id.matches("[0-9]{4}"))continue; // SKM appears in its own section\n',
       '',"show rail stops in top 4")
s=once(s,'            if(!groups.containsKey(key) && groups.size()>=5)continue;',
       '            if(!groups.containsKey(key) && groups.size()>=4)continue;',
       "display four stops")
s=once(s,'        addNearestPanel();',
    '''        addNearestPanel();
        if(warsaw)showInlineVehicleLive(value);''',
    "in-screen real vehicle GPS")
s=once(s,'    private void addModeFilters(boolean warsaw){',
'''    /** Real city vehicle positions (not arrival estimates) displayed where passengers look. */
    private void showInlineVehicleLive(GtfsNearby.Result value){
        GtfsNearby.Departure sample=null;
        for(GtfsNearby.Departure d:value.departures){
            if(("bus".equals(d.mode)||"tram".equals(d.mode))&&d.routeId!=null&&
                 !d.routeId.isEmpty()&&d.stop!=null){sample=d;break;}
        }
        if(sample==null)return;
        final GtfsNearby.Departure route=sample;
        LinearLayout card=NativeUi.card(this);
        card.addView(NativeUi.text(this,"● Pozycje pojazdów · LIVE",18,true));
        TextView live=NativeUi.muted(this,
            "Linia "+route.line+" · pobieram bieżące sygnały pojazdów…",14);
        live.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        card.addView(live);
        card.addView(NativeUi.muted(this,
            "To pozycje GPS, nie prognozy przyjazdu. Nie zastępują godziny rozkładowej.",12));
        NativeUi.addSpacer(card,this,7);
        Button open=NativeUi.button(this,"Zobacz szczegóły LIVE · "+route.line,true);
        open.setOnClickListener(v->startActivity(new Intent(this,GpsLiveActivity.class)
            .putExtra(GpsLiveActivity.EXTRA_ROUTE,route.routeId)
            .putExtra(GpsLiveActivity.EXTRA_LINE,route.line)
            .putExtra(GpsLiveActivity.EXTRA_STOP,route.stop.name)
            .putExtra(GpsLiveActivity.EXTRA_STOP_ID,route.stop.id)
            .putExtra(GpsLiveActivity.EXTRA_LAT,route.stop.lat)
            .putExtra(GpsLiveActivity.EXTRA_LON,route.stop.lon)));
        card.addView(open,new LinearLayout.LayoutParams(-1,dp(45)));
        results.addView(card);
        final int id=generation;
        work.execute(()->{
            com.ispina.lokalnie.transit.WarsawVehicleGps.Snapshot gps=null;
            try{
                gps=com.ispina.lokalnie.transit.WarsawVehicleGps.load(
                    java.util.Collections.singleton(route.routeId),route.stop.lat,route.stop.lon);
            }catch(Exception ignored){}
            final com.ispina.lokalnie.transit.WarsawVehicleGps.Snapshot snapshot=gps;
            ui.post(()->{
                if(destroyed||!screenVisible||id!=generation)return;
                if(snapshot==null){
                    live.setText("Brak świeżych pozycji GPS dla linii "+route.line+
                        ". Godziny poniżej pozostają rozkładowe.");
                }else{
                    String label="Linia "+route.line+" · aktywne pojazdy: "+snapshot.vehicles;
                    if(snapshot.vehicles>0&&!Double.isNaN(snapshot.nearestMeters))
                        label+=" · najbliższy "+Math.round(snapshot.nearestMeters)+" m w linii prostej";
                    live.setText(label+"\nŹródło: Miasto Stołeczne Warszawa · mkuran.pl.");
                }
            });
        });
    }

    private void addModeFilters(boolean warsaw){''',"inline GPS method")
# Remove repeated bright/large GPS button from every departure row; one summarized view above.
s=once(s,
'''            if(d.stop!=null && d.stop.id!=null &&
                    d.stop.id.matches("[0-9]{6}") &&
                    ("bus".equals(d.mode)||"tram".equals(d.mode)) &&
                    d.routeId!=null&&!d.routeId.isEmpty()){
                NativeUi.addSpacer(panel,this,5);
                Button gps=NativeUi.button(this,"📍 GPS LIVE · pojazdy linii "+d.line,true);
                gps.setOnClickListener(v->startActivity(new Intent(this,GpsLiveActivity.class)
                    .putExtra(GpsLiveActivity.EXTRA_ROUTE,d.routeId)
                    .putExtra(GpsLiveActivity.EXTRA_LINE,d.line)
                    .putExtra(GpsLiveActivity.EXTRA_STOP,d.stop.name)
                    .putExtra(GpsLiveActivity.EXTRA_STOP_ID,d.stop.id)
                    .putExtra(GpsLiveActivity.EXTRA_LAT,d.stop.lat)
                    .putExtra(GpsLiveActivity.EXTRA_LON,d.stop.lon)));
                panel.addView(gps,new LinearLayout.LayoutParams(-1,dp(45)));
            }''','',"remove redundant row buttons")
near.write_text(s,encoding="utf-8")

s=manual.read_text(encoding="utf-8")
s=once(s,'    private final List<GtfsNearby.LineStop> stations=new ArrayList<>();',
    '''    private final List<GtfsNearby.LineStop> stations=new ArrayList<>();
    private final List<GtfsNearby.LineStop> visibleStations=new ArrayList<>();
    private EditText stopFilter;''',"filtered stations")
s=once(s,'        stops=new Spinner(this);',
    '''        stopFilter=new EditText(this);
        stopFilter.setSingleLine(true);
        stopFilter.setTextSize(17);
        stopFilter.setHint("Zawęź po nazwie, np. Berensona");
        stopFilter.setContentDescription("Wyszukaj nazwę przystanku na trasie");
        card.addView(stopFilter,new LinearLayout.LayoutParams(-1,dp(52)));
        NativeUi.addSpacer(card,this,6);
        stops=new Spinner(this);''',"inline platform name search")
s=once(s,'        lineInput.addTextChangedListener(new TextWatcher(){',
    '''        stopFilter.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void afterTextChanged(Editable s){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                filterStops();
            }
        });
        lineInput.addTextChangedListener(new TextWatcher(){''',"filter listener")
s=once(s,'        stations.clear();',
    '''        stations.clear();visibleStations.clear();
        if(stopFilter!=null&&!stopFilter.getText().toString().isEmpty())
            stopFilter.setText("");''',"clear filtered results")
s=once(s,'        show.setEnabled(!on&&!stations.isEmpty());',
    '        show.setEnabled(!on&&!visibleStations.isEmpty());',"enable show correctly")
s=once(s,'        stops.setEnabled(!on&&!stations.isEmpty());',
    '        stops.setEnabled(!on&&!visibleStations.isEmpty());',"enable stops correctly")
s=once(s,
'''                stations.addAll(data);
                List<String> labels=new ArrayList<>();
                for(GtfsNearby.LineStop stop:stations)labels.add(stop.label());
                stops.setAdapter(new ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item,labels));
                stops.setEnabled(true);
                show.setEnabled(true);
                message.setText("Znaleziono "+stations.size()+" stanowisk. Wybierz właściwy przystanek.");''',
    '''                stations.addAll(data);
                filterStops();''',"visible filtered stations")
s=once(s,'    private void loadDepartures(){',
'''    private static String folded(String name){
        String s=java.text.Normalizer.normalize(name==null?"":name,
            java.text.Normalizer.Form.NFD);
        return s.replaceAll("\\\\p{M}+","").toLowerCase(Locale.ROOT).replace("ł","l");
    }
    private void filterStops(){
        if(stops==null||stopFilter==null)return;
        String q=folded(stopFilter.getText().toString().trim());
        visibleStations.clear();
        for(GtfsNearby.LineStop stop:stations){
            if(!q.isEmpty()&&!folded(stop.label()).contains(q))continue;
            visibleStations.add(stop);
            if(visibleStations.size()>=70)break;
        }
        List<String> labels=new ArrayList<>();
        for(GtfsNearby.LineStop stop:visibleStations)labels.add(stop.label());
        if(labels.isEmpty())labels.add("Brak pasującego przystanku");
        stops.setAdapter(new ArrayAdapter<String>(this,
            android.R.layout.simple_spinner_dropdown_item,labels));
        stops.setEnabled(!visibleStations.isEmpty());
        show.setEnabled(!visibleStations.isEmpty());
        if(stations.isEmpty())return;
        message.setText(visibleStations.isEmpty()?"Nie znaleziono przystanku. Zmień nazwę.":
            "Linia ma "+stations.size()+" stanowisk. Wyświetlam "+visibleStations.size()+
            ". Wpisz fragment nazwy, aby zawęzić wybór.");
    }

    private void loadDepartures(){''',"manual stop filtering")
s=once(s,'position>=stations.size()','position>=visibleStations.size()',"manual choice bounds")
s=once(s,'stations.get(position).id,stopName=stations.get(position).name',
       'visibleStations.get(position).id,stopName=visibleStations.get(position).name',
       "selected filtered platform")
manual.write_text(s,encoding="utf-8")

main=base/"MainActivity.java"
s=main.read_text(encoding="utf-8")
s=once(s,"POMOCNIK ALFA 1.7.60","POMOCNIK ALFA 1.7.61","version label")
main.write_text(s,encoding="utf-8")
gradle=Path("project/app/build.gradle")
s=gradle.read_text(encoding="utf-8")
s=once(s,"versionCode 10761","versionCode 10762","version code")
s=once(s,"versionName '1.7.60-A10'","versionName '1.7.61-A10'","version name")
gradle.write_text(s,encoding="utf-8")
print("PASS: PA 1.7.61 performance, 4 ranked stops, indoor fallback and inline verified GPS")
