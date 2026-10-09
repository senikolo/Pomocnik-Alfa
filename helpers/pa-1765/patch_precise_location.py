#!/usr/bin/env python3
"""PA 1.7.65: do not silently use approximate/old GPS for nearby departures."""
from pathlib import Path
file=Path("project/app/src/main/java/com/ispina/lokalnie/NearbyDeparturesActivity.java")
s=file.read_text(encoding="utf-8")
def once(a,b,label):
    global s
    n=s.count(a)
    if n!=1:raise AssertionError(f"{label}: {n}")
    s=s.replace(a,b,1)
once('    private boolean usingSavedFix;',
'''    private static final float GOOD_FIX_METRES=150f;
    private static final long MAX_POSITION_AGE=90000L;
    private static final long MAX_SAVED_FIX_AGE=45L*60000L;
    private boolean usingSavedFix;''',"gps quality limits")
once('        if(locating){timeout=this::finishFix;ui.postDelayed(timeout,6500);}',
     '        if(locating){timeout=this::finishFix;ui.postDelayed(timeout,14000);}',
     "longer precise acquisition rather than 6.5s")
once('        status.setText("Szukam Twojego położenia…");',
'''        status.setText("Ustalam dokładną pozycję. Zwykle pomaga włączona lokalizacja precyzyjna; pomiar może potrwać kilkanaście sekund.");''',
     "precision info")
once('''        if(fix.getTime()>0&&Math.abs(System.currentTimeMillis()-fix.getTime())>30L*60000L) return;
        if(best==null|| (fix.hasAccuracy() && (!best.hasAccuracy()||fix.getAccuracy()<best.getAccuracy())))best=new Location(fix);
        if(best.hasAccuracy()&&(best.getAccuracy()<120f ||
            (LocationManager.NETWORK_PROVIDER.equals(fix.getProvider())&&best.getAccuracy()<700f)))
             finishFix();''',
'''        if(fix.getTime()<=0||Math.abs(System.currentTimeMillis()-fix.getTime())>MAX_POSITION_AGE)return;
        if(!fix.hasAccuracy()||!Float.isFinite(fix.getAccuracy())||fix.getAccuracy()<0f)return;
        if(best==null || fix.getAccuracy()<best.getAccuracy() ||
            (Math.abs(fix.getAccuracy()-best.getAccuracy())<10f&&fix.getTime()>best.getTime()))
            best=new Location(fix);
        // Earlier versions accepted a 700 m network fix instantly; this could
        // displace passengers by kilometres. Accept only a high-confidence fix.
        boolean highConfidence=fix.getAccuracy()<=45f &&
            LocationManager.GPS_PROVIDER.equals(fix.getProvider());
        if(highConfidence)finishFix();''',
     "prefer freshly measured accurate position")
once('''        if(fix==null){fail("Brak nowej pozycji. Wybierz „Użyj ostatniej lokalizacji” albo wyszukaj linię bez GPS.");return;}
        lastFix=new Location(fix);''',
'''        if(fix==null){
            fail("Brak świeżego pomiaru GPS. Włącz lokalizację precyzyjną lub wybierz linię i przystanek bez GPS.");
            return;
        }
        if(!fix.hasAccuracy()||fix.getAccuracy()>GOOD_FIX_METRES){
            fail("Pozycja jest zbyt niedokładna (±"+Math.round(fix.getAccuracy())+
                " m). Nie pokazuję jej jako Twojej okolicy. Spróbuj ponownie przy oknie lub wyszukaj przystanek ręcznie.");
            return;
        }
        if(fix.getTime()<=0||System.currentTimeMillis()-fix.getTime()>MAX_POSITION_AGE){
            fail("Telefon zwrócił nieaktualną pozycję. Spróbuj ponownie lub wpisz przystanek.");
            return;
        }
        lastFix=new Location(fix);''',"reject low precision or stale on finish")
once('''        return at>0&&System.currentTimeMillis()-at<24L*3600000L;''',
'''        return at>0 && System.currentTimeMillis()-at<MAX_SAVED_FIX_AGE &&
            getSharedPreferences("pa_last_transit_fix",MODE_PRIVATE).getFloat("accuracy",999f)<=GOOD_FIX_METRES;''',
     "strict saved-fix validity")
once('''            status.setText("Brak lokalizacji z ostatnich 24 godzin. Skorzystaj z numeru linii.");''',
'''            status.setText("Brak dokładnej lokalizacji z ostatnich 45 minut. Wyszukaj linię i przystanek bez GPS.");''',
     "stale saved-fix notice")
once('''        if(lastFix!=null){
            searchTimetable(new Location(lastFix),true);
        }else if(savedAvailable())useSavedFix();
        else begin();''',
'''        if(lastFix!=null && lastFix.hasAccuracy() &&
            lastFix.getAccuracy()<=GOOD_FIX_METRES &&
            lastFix.getTime()>0 &&
            System.currentTimeMillis()-lastFix.getTime()<MAX_SAVED_FIX_AGE){
            searchTimetable(new Location(lastFix),true);
        }else begin();''',
     "never re-fetch entire station list using old saved approximate fix")
once('''        status.setText(usingSavedFix?"Lokalizacja orientacyjna · szukam czterech przydatnych przystanków…":
            "Wybieram cztery najlepsze przystanki i sprawdzam odjazdy…");''',
'''        status.setText("Lokalizacja "+(usingSavedFix?"zapisana, ":"świeża, ")+
            "dokładność ±"+Math.round(fix.getAccuracy())+
            " m · wybieram najbliższe przystanki…");''',
     "show accuracy when resolving departures")
once('''        status.setText((usingSavedFix?"Pozycja orientacyjna · ":"")+
            "Rozkład: "+format.format(new Date(data.downloadedAt))+
            (data.oldData?" · dane starsze":""));''',
'''        long locationAgeMinutes=fix.getTime()>0?
            Math.max(0,(System.currentTimeMillis()-fix.getTime())/60000L):0;
        status.setText("Twoja lokalizacja: ±"+Math.round(fix.getAccuracy())+" m"+
            (usingSavedFix?" · zapisana "+locationAgeMinutes+" min temu":" · świeży pomiar")+
            " · rozkład "+format.format(new Date(data.downloadedAt))+
            (data.oldData?" · dane starsze":""));''',
     "accurate location / cached age disclosure")
once('''        for(GtfsNearby.Stop stop:data.stops){
            String key=stop.name.toLowerCase(Locale.ROOT);''',
'''        final boolean inWarsaw="warsaw".equals(GtfsNearby.networkFor(
            fix.getLatitude(),fix.getLongitude()));
        for(GtfsNearby.Stop stop:data.stops){
            // Urban stop suggestions over 1.2 km away are not usable as "nearby".
            // MLD rural areas can legitimately have fewer stops.
            if(inWarsaw && stop.distance>1200)continue;
            String key=stop.name.toLowerCase(Locale.ROOT);''',
     "cap Warsaw nearest-stop radius")
once('''                "Możesz wyszukać numer linii bez GPS w polu powyżej.",''',
'''                "Nie ma przystanku w bliskim zasięgu. Wyszukaj linię i przystanek ręcznie bez GPS.",''',
     "bounded nearest search empty state")
once('''        previousLocation=NativeUi.button(this,"⌖ Użyj ostatniej lokalizacji (wewnątrz)",true);''',
'''        previousLocation=NativeUi.button(this,"⌖ Ostatnia dokładna pozycja (maks. 45 min)",true);''',
     "clarify saved fallback to user")
once('''        status=NativeUi.muted(this,"Wewnątrz budynku możesz użyć lokalizacji sieciowej lub ostatniej zapisanej pozycji.",14);''',
'''        status=NativeUi.muted(this,"Przystanki wyświetlam tylko przy dokładnej lokalizacji (do ±150 m). Gdy telefon zna pozycję jedynie orientacyjnie, użyj wyszukiwarki bez GPS.",14);''',
     "explain accuracy gate")
file.write_text(s,encoding="utf-8")
print("PASS: reject stale/coarse fix, visibly disclose accuracy, 1.2 km Warsaw stop suggestions, safe manual fallback")
