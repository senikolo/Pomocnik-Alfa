package com.ispina.lokalnie;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.EditText;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputFilter;
import android.widget.TextView;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.text.TextUtils;
import android.util.TypedValue;

import com.ispina.lokalnie.transit.GtfsNearby;
import com.ispina.lokalnie.transit.DepartureCountdown;
import com.ispina.lokalnie.transit.DepartureQuickFilter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NearbyDeparturesActivity extends ThemedActivity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private LocationManager manager;
    private LocationListener listener;
    private Location best;
    private Runnable timeout;
    private TextView status;
    private LinearLayout results;
    private Button locate,refresh;
    private ProgressBar progress;
    private final Map<String,LinearLayout> modeSections=new LinkedHashMap<>();
    private final Map<String,TextView> modeFilters=new LinkedHashMap<>();
    private String activeMode="all";
    private String searchQuery="";
    private final Map<String,List<DepartureCard>> modeCards=new LinkedHashMap<>();
    private TextView noMatches,quickLine,quickStop,quickTime;
    private LinearLayout nearestPanel;
    private GtfsNearby.Departure nearestDeparture;
    private static final class DepartureCard {
        final GtfsNearby.Departure departure;
        final LinearLayout view;
        DepartureCard(GtfsNearby.Departure d,LinearLayout panel){
            departure=d;view=panel;
        }
    }
    private Location lastFix;
    private long lastFixAt;
    private boolean screenVisible;
    private static final long FIX_REUSE_MS=5L*60L*1000L;
    private final List<CountdownLabel> countdownLabels=new ArrayList<>();
    private static class CountdownLabel {
        final GtfsNearby.Departure departure;
        final TextView view,liveState;
        CountdownLabel(GtfsNearby.Departure d,TextView v,TextView live){
            departure=d;view=v;liveState=live;
        }
    }
    private final Runnable countdownTick=new Runnable(){
        @Override public void run(){
            updateCountdowns();
            if(screenVisible && !destroyed && !countdownLabels.isEmpty())
                ui.postDelayed(this,30000L);
        }
    };
    private int generation;
    private boolean destroyed,locating;
    private static final int GPS_PERMISSION=1749;

    private int dp(int v){return NativeUi.dp(this,v);}
    @Override protected void onCreate(Bundle b){super.onCreate(b);build();}

    private void build(){
        ScrollView page=new ScrollView(this);page.setFillViewport(true);page.setBackgroundColor(NativeUi.bg(this));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(15),dp(18),dp(15),dp(30));page.addView(root);setContentView(page);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        Button back=NativeUi.button(this,"‹",true);back.setOnClickListener(v->finish());
        head.addView(back,new LinearLayout.LayoutParams(dp(49),dp(48)));
        TextView title=NativeUi.text(this,"🚌 Odjazdy stąd",24,true);head.addView(title,new LinearLayout.LayoutParams(-1,-2));
        root.addView(head);
        NativeUi.addSpacer(root,this,14);
        LinearLayout card=NativeUi.card(this);
        card.addView(NativeUi.text(this,"Najbliższe przystanki",21,true));
        card.addView(NativeUi.muted(this,"Wybierzesz lokalizację tylko teraz. Pokażę przystanki, kierunki, odjazdy planowe i prostą listę kolejnych przystanków.",14));
        NativeUi.addSpacer(card,this,10);
        locate=NativeUi.button(this,"📍 Znajdź odjazdy z mojej okolicy",false);
        locate.setOnClickListener(v->begin());
        card.addView(locate,new LinearLayout.LayoutParams(-1,dp(55)));
        NativeUi.addSpacer(card,this,8);
        refresh=NativeUi.button(this,"↻ Odśwież odjazdy",true);
        refresh.setOnClickListener(v->refreshDepartures());
        refresh.setEnabled(false);
        card.addView(refresh,new LinearLayout.LayoutParams(-1,dp(52)));
        NativeUi.addSpacer(card,this,6);
        status=NativeUi.muted(this,"Dotknij przycisku. Lokalizacja nie działa w tle.",14);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);card.addView(status);root.addView(card);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);progress.setVisibility(View.GONE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));
        results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);root.addView(results);
        NativeUi.addSpacer(root,this,15);
        LinearLayout info=NativeUi.card(this);
        info.addView(NativeUi.text(this,"Dane i dokładność",17,true));
        info.addView(NativeUi.muted(this,"Warszawa: rozkłady WTP/WarsawGTFS; Małopolska: MLD. Brak oznaczenia live nie oznacza punktualności. Opóźnienia będą widoczne wyłącznie po dopasowaniu świeżej informacji o konkretnym kursie i stanowisku. Sama pozycja GPS pojazdu nie daje pewnego opóźnienia. Odległości są w linii prostej. Dane: ZTM Warszawa, Mikołaj Kuranowski i © OpenStreetMap contributors.",13));
        root.addView(info);
    }
    private boolean permitted(){
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
               checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
    }
    private void begin(){
        if(locating)return;
        if(!permitted()){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},GPS_PERMISSION);
            return;
        }
        requestFix();
    }
    @Override public void onRequestPermissionsResult(int code,String[] perms,int[] grants){
        super.onRequestPermissionsResult(code,perms,grants);
        if(code==GPS_PERMISSION){
            if(permitted())requestFix();
            else status.setText("Bez zgody na lokalizację nie mogę wskazać przystanków w pobliżu.");
        }
    }
    private void requestFix(){
        stopGps();locating=true;best=null;generation++;
        stopCountdowns();
        lastFix=null;lastFixAt=0L;
        if(refresh!=null)refresh.setEnabled(false);
        results.removeAllViews();progress.setVisibility(View.VISIBLE);locate.setEnabled(false);
        status.setText("Szukam Twojego położenia…");
        manager=(LocationManager)getSystemService(LOCATION_SERVICE);
        if(manager==null){fail("Urządzenie nie udostępnia lokalizacji.");return;}
        listener=new LocationListener(){
            @Override public void onLocationChanged(Location fix){consider(fix);}
            @Override public void onProviderEnabled(String provider){}
            @Override public void onProviderDisabled(String provider){}
            @Override public void onStatusChanged(String provider,int st,Bundle b){}
        };
        boolean available=false;
        for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER}){
            try{
                if(!manager.isProviderEnabled(provider))continue;
                available=true;
                consider(manager.getLastKnownLocation(provider));
                if(locating)manager.requestLocationUpdates(provider,1000,0,listener,Looper.getMainLooper());
            }catch(SecurityException ignored){}catch(IllegalArgumentException ignored){}
        }
        if(!available){fail("Włącz lokalizację w ustawieniach Androida.");return;}
        if(locating){timeout=this::finishFix;ui.postDelayed(timeout,16000);}
    }
    private void consider(Location fix){
        if(!locating||fix==null||!Double.isFinite(fix.getLatitude())||!Double.isFinite(fix.getLongitude()))return;
        if(fix.getTime()>0&&Math.abs(System.currentTimeMillis()-fix.getTime())>150000) return;
        if(best==null|| (fix.hasAccuracy() && (!best.hasAccuracy()||fix.getAccuracy()<best.getAccuracy())))best=new Location(fix);
        if(best.hasAccuracy()&&best.getAccuracy()<70f)finishFix();
    }
    private void stopGps(){
        if(timeout!=null){ui.removeCallbacks(timeout);timeout=null;}
        if(manager!=null&&listener!=null)try{manager.removeUpdates(listener);}catch(Exception ignored){}
        listener=null;locating=false;
    }
    private void finishFix(){
        if(!locating)return;
        Location fix=best;stopGps();
        if(fix==null){fail("Nie udało się pobrać aktualnej pozycji. Spróbuj przy oknie.");return;}
        lastFix=new Location(fix);
        lastFixAt=System.currentTimeMillis();
        searchTimetable(fix,false);
    }
    private void refreshDepartures(){
        if(locating || progress.getVisibility()==View.VISIBLE)return;
        if(lastFix!=null && System.currentTimeMillis()-lastFixAt<=FIX_REUSE_MS){
            searchTimetable(new Location(lastFix),true);
        }else begin(); // After five minutes, obtain a fresh opt-in GPS fix.
    }
    private void searchTimetable(Location fix,boolean preserveResults){
        final int requestId=++generation;
        progress.setVisibility(View.VISIBLE);
        locate.setEnabled(false);
        refresh.setEnabled(false);
        if(!preserveResults)results.removeAllViews();
        status.setText("Odświeżam pobliskie odjazdy i aktualność rozkładów…");
        work.execute(()->{
            GtfsNearby.Result value=null;String error=null;
            try{value=GtfsNearby.search(getApplicationContext(),fix.getLatitude(),fix.getLongitude());}
            catch(Exception e){error=e.getMessage();}
            GtfsNearby.Result found=value;String problem=error;
            ui.post(()->{
                if(destroyed||requestId!=generation || !screenVisible)return;
                locate.setEnabled(true);
                refresh.setEnabled(lastFix!=null);
                progress.setVisibility(View.GONE);
                if(problem!=null){
                    if(preserveResults && results.getChildCount()>0)
                        status.setText("Odświeżenie nieudane: "+problem+". Pokazuję poprzednie wyniki.");
                    else renderError(problem,fix);
                    return;
                }
                render(found,fix);
            });
        });
    }
    private void fail(String message){
        stopGps();progress.setVisibility(View.GONE);locate.setEnabled(true);
        if(refresh!=null)refresh.setEnabled(lastFix!=null);
        status.setText(message);
    }
    private String time(long value){
        SimpleDateFormat f=new SimpleDateFormat("HH:mm",new Locale("pl","PL"));
        f.setTimeZone(java.util.TimeZone.getTimeZone("Europe/Warsaw"));
        return f.format(new Date(value));
    }
    private void renderError(String problem,Location fix){
        status.setText("Nie mogę wyświetlić aktualnych odjazdów: "+problem);
        results.removeAllViews();
        Button web=NativeUi.button(this,"Otwórz rozkład przewoźnika",true);
        web.setOnClickListener(v->open(GtfsNearby.networkFor(fix.getLatitude(),fix.getLongitude()).equals("warsaw")?
            "https://www.wtp.waw.pl/rozklady-jazdy/":"https://kolejemalopolskie.com.pl/pl/rozklad-jazdy/rozklady-autobusowe"));
        results.addView(web,new LinearLayout.LayoutParams(-1,dp(54)));
    }
    private String stopLabel(GtfsNearby.Stop stop){
        if(stop==null)return "Nieznany przystanek";
        String name=stop.name==null?"Przystanek":stop.name;
        String code=stop.code==null?"":stop.code.trim();
        if(code.isEmpty())return name;
        return name+" · stanowisko "+code;
    }
    private void render(GtfsNearby.Result value,Location fix){
        stopCountdowns();
        modeSections.clear();
        modeFilters.clear();
        modeCards.clear();
        nearestDeparture=null;
        results.removeAllViews();
        SimpleDateFormat stamp=new SimpleDateFormat("dd.MM, HH:mm",new Locale("pl","PL"));
        stamp.setTimeZone(java.util.TimeZone.getTimeZone("Europe/Warsaw"));
        String age=stamp.format(new Date(value.downloadedAt));
        status.setText(value.feedName+" · rozkład z "+age+
            " · sprawdzono "+time(System.currentTimeMillis())+
            (value.oldData?" · UWAGA: starszy rozkład":""));
        NativeUi.addSpacer(results,this,9);
        LinearLayout liveState=NativeUi.card(this);
        liveState.addView(NativeUi.text(this,"Stan informacji LIVE",17,true));
        liveState.addView(NativeUi.muted(this,value.liveNote==null?
            "Brak zweryfikowanych aktualizacji; godziny są planowe.":value.liveNote,14));
        results.addView(liveState);
        NativeUi.addSpacer(results,this,8);
        LinearLayout stopCard=NativeUi.card(this);
        stopCard.addView(NativeUi.text(this,"🚏 5 najbliższych przystanków",19,true));
        // Show a maximum of five named stop complexes, not eight platform entries.
        Map<String,List<GtfsNearby.Stop>> groups=new LinkedHashMap<>();
        for(GtfsNearby.Stop stop:value.stops){
            if(stop.id.matches("[0-9]{4}"))continue; // SKM appears in its own section
            String key=stop.name.toLowerCase(Locale.ROOT);
            if(!groups.containsKey(key) && groups.size()>=5)continue;
            List<GtfsNearby.Stop> platforms=groups.get(key);
            if(platforms==null){platforms=new ArrayList<>();groups.put(key,platforms);}
            platforms.add(stop);
        }
        for(List<GtfsNearby.Stop> platforms:groups.values()){
            GtfsNearby.Stop closest=platforms.get(0);
            LinkedHashSet<String> numbers=new LinkedHashSet<>();
            for(GtfsNearby.Stop st:platforms)
                if(st.code!=null&&!st.code.trim().isEmpty())numbers.add(st.code.trim());
            LinearLayout stopPanel=NativeUi.card(this);
            // The nearest-stop summary uses exactly the same stop/platform palette
            // as each departure row. Names are allowed two lines; directions are not.
            TextView stopName=stopChip(closest.name);
            stopName.setSingleLine(false);
            stopName.setMaxLines(2);
            stopName.setEllipsize(TextUtils.TruncateAt.END);
            stopName.setTextSize(18);
            stopPanel.addView(stopName);
            NativeUi.addSpacer(stopPanel,this,6);
            String posts=numbers.isEmpty()?"Numer stanowiska: brak w danych":
                "Stanowiska: "+android.text.TextUtils.join(", ",numbers);
            if(numbers.isEmpty()){
                stopPanel.addView(NativeUi.muted(this,posts,14));
            }else{
                HorizontalScrollView scroller=new HorizontalScrollView(this);
                scroller.setHorizontalScrollBarEnabled(false);
                LinearLayout row=new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                int p=0;
                for(String number:numbers){
                    if(p++>0)horizontalGap(row,6);
                    TextView chip=platformChip("Stan. "+number);
                    row.addView(chip,new LinearLayout.LayoutParams(-2,-2));
                }
                scroller.addView(row);
                stopPanel.addView(scroller);
            }
            NativeUi.addSpacer(stopPanel,this,4);
            stopPanel.addView(NativeUi.muted(this,Math.round(closest.distance)+" m w linii prostej",14));
            stopPanel.setContentDescription(closest.name+". "+posts+". "+Math.round(closest.distance)+" metrów.");
            stopCard.addView(stopPanel);
        }
        if(groups.isEmpty())stopCard.addView(NativeUi.muted(this,"Nie znaleziono przystanków autobusowych ani tramwajowych w pobliżu.",14));
        results.addView(stopCard);
        boolean warsaw="warsaw".equals(GtfsNearby.networkFor(fix.getLatitude(),fix.getLongitude()));
        if(!warsaw && !activeMode.equals("all") && !activeMode.equals("bus"))
            activeMode="all";
        addModeFilters(warsaw);
        addLineSearch();
        addNearestPanel();
        addDepartureSection(value,"bus","🚌 Autobusy",true);
        if(warsaw){
            addDepartureSection(value,"tram","🚋 Tramwaje",true);
            addDepartureSection(value,"skm","🚆 Pociągi SKM",true);
            addDepartureSection(value,"metro","🚇 Metro",true);
            LinearLayout km=NativeUi.card(this);
            km.addView(NativeUi.text(this,"🚆 Koleje Mazowieckie (KM)",18,true));
            km.addView(NativeUi.muted(this,
                "Pociągi KM nie są zawarte w aktualnym rozkładzie WTP w tej aplikacji. Nie podaję niezweryfikowanych godzin.",13));
            NativeUi.addSpacer(km,this,8);
            Button site=NativeUi.button(this,"Sprawdź rozkład KM u przewoźnika",true);
            site.setOnClickListener(v->open("https://www.mazowieckie.com.pl/pl"));
            km.addView(site,new LinearLayout.LayoutParams(-1,dp(52)));
            results.addView(km);
        }
        LinearLayout service=NativeUi.card(this);
        service.addView(NativeUi.text(this,"Informacje bieżące u przewoźnika",18,true));
        service.addView(NativeUi.muted(this,
            "Odjazdy PA są rozkładowe. Na razie nie ma potwierdzonych korekt minutowych dla konkretnych kursów. Możesz sprawdzić bieżące utrudnienia u przewoźnika.",14));
        Button disruptions=NativeUi.button(this,"Sprawdź bieżące utrudnienia",true);
        disruptions.setOnClickListener(v->open(warsaw?
            "https://www.wtp.waw.pl/utrudnienia/":"https://kolejemalopolskie.com.pl/pl/utrudnienia"));
        service.addView(disruptions,new LinearLayout.LayoutParams(-1,dp(52)));
        NativeUi.addSpacer(service,this,7);
        Button externalLive=NativeUi.button(this,
            warsaw?"Sprawdź odjazdy LIVE w Time4BUS":"Sprawdź LIVE w KiedyPrzyjedzie",true);
        externalLive.setOnClickListener(v->open(warsaw?
            "https://time4bus.com/":"https://kolejemalopolskie.kiedyprzyjedzie.pl/"));
        service.addView(externalLive,new LinearLayout.LayoutParams(-1,dp(52)));
        service.addView(NativeUi.muted(this,
            "To zewnętrzny serwis z własnymi danymi na żywo. PA nie pobiera jeszcze jego prognoz.",12));
        results.addView(service);
        applyModeFilter();
        startCountdowns();
    }
    private void addModeFilters(boolean warsaw){
        LinearLayout wrapper=NativeUi.card(this);
        wrapper.addView(NativeUi.text(this,"Pokaż odjazdy",18,true));
        NativeUi.addSpacer(wrapper,this,7);
        HorizontalScrollView scroller=new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        LinearLayout chips=new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        String[] keys=warsaw?new String[]{"all","bus","tram","skm","metro"}:
            new String[]{"all","bus"};
        String[] names=warsaw?new String[]{"Wszystkie","Autobusy","Tramwaje","SKM","Metro"}:
            new String[]{"Wszystkie","Autobusy"};
        for(int i=0;i<keys.length;i++){
            final String mode=keys[i];
            TextView chip=NativeUi.text(this,names[i],15,true);
            chip.setPadding(dp(13),dp(10),dp(13),dp(10));
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(44));
            chip.setClickable(true);
            chip.setFocusable(true);
            chip.setContentDescription("Filtruj odjazdy: "+names[i]);
            chip.setOnClickListener(v->{
                activeMode=mode;
                applyModeFilter();
            });
            modeFilters.put(mode,chip);
            if(i>0)horizontalGap(chips,7);
            chips.addView(chip,new LinearLayout.LayoutParams(-2,-2));
        }
        scroller.addView(chips);
        wrapper.addView(scroller);
        results.addView(wrapper);
    }
    private void addLineSearch(){
        LinearLayout card=NativeUi.card(this);
        card.addView(NativeUi.text(this,"Znajdź konkretną linię lub kierunek",17,true));
        NativeUi.addSpacer(card,this,7);
        EditText input=new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setHint("Linia, kierunek lub przystanek…");
        input.setContentDescription("Wyszukaj linię, kierunek lub przystanek w pobranych odjazdach");
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(48)});
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setText(searchQuery);
        input.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                searchQuery=s.toString().trim();
                applyModeFilter();
            }
            @Override public void afterTextChanged(Editable s){}
        });
        card.addView(input,new LinearLayout.LayoutParams(-1,dp(52)));
        noMatches=NativeUi.muted(this,"Nie znaleziono takich odjazdów w pobranym rozkładzie.",14);
        noMatches.setVisibility(View.GONE);
        card.addView(noMatches);
        results.addView(card);
    }
    private void addNearestPanel(){
        nearestPanel=NativeUi.card(this);
        nearestPanel.addView(NativeUi.text(this,"⏱ Najbliższy odjazd z wyświetlonych",18,true));
        NativeUi.addSpacer(nearestPanel,this,6);
        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        quickLine=lineChip("–");
        quickLine.setGravity(Gravity.CENTER);
        top.addView(quickLine,new LinearLayout.LayoutParams(dp(65),-2));
        horizontalGap(top,6);
        quickTime=destinationChip("Czekam na odjazdy…");
        top.addView(quickTime,new LinearLayout.LayoutParams(0,-2,1));
        nearestPanel.addView(top);
        NativeUi.addSpacer(nearestPanel,this,5);
        quickStop=stopChip("Przystanek");
        nearestPanel.addView(quickStop,new LinearLayout.LayoutParams(-1,-2));
        nearestPanel.setClickable(true);
        nearestPanel.setFocusable(true);
        nearestPanel.setOnClickListener(v->{
            if(nearestDeparture!=null)route(nearestDeparture);
        });
        results.addView(nearestPanel);
    }
    private void applyModeFilter(){
        long now=System.currentTimeMillis();
        boolean searching=!searchQuery.isEmpty();
        int visible=0;
        GtfsNearby.Departure soonest=null;
        long earliest=Long.MAX_VALUE;
        for(Map.Entry<String,LinearLayout> item:modeSections.entrySet()){
            String mode=item.getKey();
            boolean modeSelected=activeMode.equals("all") || activeMode.equals(mode);
            List<DepartureCard> cards=modeCards.get(mode);
            if(cards==null)cards=new ArrayList<>();
            int localVisible=0;
            for(DepartureCard card:cards){
                boolean matched=modeSelected &&
                    DepartureQuickFilter.matches(card.departure,searchQuery);
                // Keep first 12 rows per mode in the default view; a text search
                // can find any of the 30 locally cached entries without a new GPS call.
                boolean show=matched && (searching || localVisible<12);
                card.view.setVisibility(show?View.VISIBLE:View.GONE);
                if(!show)continue;
                localVisible++;
                visible++;
                long estimated=DepartureQuickFilter.expectedTime(card.departure,now);
                if(estimated>=now-60000L && (estimated<earliest ||
                   estimated==earliest && soonest!=null &&
                   card.departure.stop.distance<soonest.stop.distance)){
                    earliest=estimated;
                    soonest=card.departure;
                }
            }
            // Empty mode sections show their original explanatory text,
            // unless the user typed a specific search.
            item.getValue().setVisibility(modeSelected &&
                (localVisible>0 || cards.isEmpty() && !searching)?
                View.VISIBLE:View.GONE);
        }
        if(noMatches!=null)noMatches.setVisibility(visible==0?View.VISIBLE:View.GONE);
        nearestDeparture=soonest;
        refreshNearestPanel(now);
        boolean dark=(getResources().getConfiguration().uiMode&
            Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        for(Map.Entry<String,TextView> item:modeFilters.entrySet()){
            boolean selected=item.getKey().equals(activeMode);
            TextView chip=item.getValue();
            int background=selected?(dark?0xFF387BC0:0xFF20609C):
                (dark?0xFF303944:0xFFEAF0F5);
            int foreground=selected?0xFFFFFFFF:(dark?0xFFE9F2FD:0xFF284561);
            GradientDrawable drawable=new GradientDrawable();
            drawable.setColor(background);
            drawable.setCornerRadius(dp(16));
            chip.setBackground(drawable);
            chip.setTextColor(foreground);
            chip.setSelected(selected);
        }
    }
    private void refreshNearestPanel(long now){
        if(nearestPanel==null || quickLine==null || quickStop==null || quickTime==null)return;
        GtfsNearby.Departure d=nearestDeparture;
        if(d==null){
            nearestPanel.setVisibility(View.GONE);
            return;
        }
        nearestPanel.setVisibility(View.VISIBLE);
        Integer delay=d.confirmedDelayMinutes(now);
        quickLine.setText(d.line);
        quickTime.setText(d.headsign+" · "+DepartureCountdown.label(d.when,now,delay));
        quickTime.setContentDescription("Kierunek "+d.headsign+". "+
            DepartureCountdown.label(d.when,now,delay));
        quickStop.setText(stopLabel(d.stop));
        nearestPanel.setContentDescription("Najbliższy odjazd. Linia "+d.line+
            ". Kierunek "+d.headsign+". Przystanek "+stopLabel(d.stop)+". "+
            DepartureCountdown.label(d.when,now,delay)+". Dotknij, aby poznać trasę.");
    }
    private void horizontalGap(LinearLayout layout,int dps){
        layout.addView(new View(this),new LinearLayout.LayoutParams(dp(dps),1));
    }
    /** Consistent meaning for each color, with separate high-contrast night variants. */
    private TextView transitChip(String label, int sp, int lightBackground, int lightText,
                                 int darkBackground, int darkText, boolean oneLine){
        TextView view=NativeUi.text(this,label,sp,true);
        boolean dark=(getResources().getConfiguration().uiMode&
                Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        view.setTextColor(dark?darkText:lightText);
        GradientDrawable background=new GradientDrawable();
        background.setColor(dark?darkBackground:lightBackground);
        background.setCornerRadius(dp(10));
        view.setBackground(background);
        view.setPadding(dp(10),dp(9),dp(10),dp(9));
        view.setMinHeight(dp(41));
        view.setGravity(Gravity.CENTER_VERTICAL);
        if(oneLine){
            view.setSingleLine(true);
            view.setEllipsize(TextUtils.TruncateAt.END);
            view.setMinWidth(0);
            view.setContentDescription(label);
        }
        return view;
    }
    private TextView lineChip(String label){
        // Route number: bold white on a saturated purple badge (light/dark accessible).
        return transitChip(label,20,0xFF5836A5,0xFFFFFFFF,0xFF714DC0,0xFFFFFFFF,true);
    }
    private TextView destinationChip(String label){
        // Direction: calm blue, distinct from route and stop.
        TextView v=transitChip(label,17,0xFFE6F0FF,0xFF174783,0xFF1C3A61,0xFFF0F6FF,true);
        // Use the available width before shortening long termini to an ellipsis.
        v.setAutoSizeTextTypeUniformWithConfiguration(12,17,1,TypedValue.COMPLEX_UNIT_SP);
        return v;
    }
    private TextView stopChip(String label){
        // Stop name: green/teal with high-contrast dark text.
        TextView v=transitChip(label,16,0xFFE0F4E9,0xFF075B46,0xFF173F34,0xFFE3FFF0,true);
        v.setAutoSizeTextTypeUniformWithConfiguration(12,15,1,TypedValue.COMPLEX_UNIT_SP);
        return v;
    }
    private TextView platformChip(String label){
        // Boarding position is always amber, NEVER the same purple as the route.
        return transitChip(label,15,0xFFFFEDD0,0xFF794200,0xFF52391D,0xFFFFEAC0,true);
    }
    private TextView timeChip(String label){
        // Departure clock: strong deep teal badge (distinct from direction blue).
        return transitChip(label,21,0xFF125E7B,0xFFFFFFFF,0xFF17678B,0xFFFFFFFF,true);
    }
    private TextView liveChip(String label,Integer delay){
        if(delay==null)return transitChip(label,13,0xFFF0F3F6,0xFF46515E,0xFF353D48,0xFFEFF3F9,true);
        if(delay>0)return transitChip(label,14,0xFFFFE6D0,0xFF8C3706,0xFF5A321C,0xFFFFECD9,true);
        return transitChip(label,14,0xFFDEF6E5,0xFF126035,0xFF19442C,0xFFE5FFED,true);
    }
    private void addDepartureSection(GtfsNearby.Result value,String mode,String heading,boolean alwaysVisible){
        List<GtfsNearby.Departure> selected=new ArrayList<>();
        for(GtfsNearby.Departure d:value.departures)if(mode.equals(d.mode))selected.add(d);
        if(selected.isEmpty()&&!alwaysVisible)return;
        LinearLayout section=NativeUi.card(this);
        modeSections.put(mode,section);
        List<DepartureCard> rows=new ArrayList<>();
        modeCards.put(mode,rows);
        section.addView(NativeUi.text(this,heading+" · odjazdy",19,true));
        if(selected.isEmpty()){
            section.addView(NativeUi.muted(this,
                "Nie znaleziono potwierdzonych odjazdów w najbliższych dwóch godzinach z pobliskich przystanków tej kategorii.",14));
            results.addView(section);return;
        }
        section.addView(NativeUi.muted(this,
            "Fiolet: linia · niebieski: kierunek · zielony: przystanek · bursztyn: stanowisko · morski: godzina",12));
        NativeUi.addSpacer(section,this,9);
        int count=0;
        for(GtfsNearby.Departure d:selected){
            if(count++>=30)break;
            LinearLayout panel=NativeUi.card(this);
            // Line and destination: one horizontal row; no broken destination text.
            LinearLayout top=new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView number=lineChip(d.line);
            number.setGravity(Gravity.CENTER);
            number.setContentDescription("Linia "+d.line);
            top.addView(number,new LinearLayout.LayoutParams(dp(65),-2));
            horizontalGap(top ,5);
            TextView direction=destinationChip(d.headsign);
            direction.setContentDescription("Kierunek: "+d.headsign);
            top.addView(direction,new LinearLayout.LayoutParams(0,-2,1));
            panel.addView(top);
            NativeUi.addSpacer(panel,this,7);

            LinearLayout middle=new LinearLayout(this);
            middle.setOrientation(LinearLayout.HORIZONTAL);
            middle.setGravity(Gravity.CENTER_VERTICAL);
            TextView name=stopChip(d.stop.name);
            name.setContentDescription("Przystanek: "+d.stop.name);
            middle.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            horizontalGap(middle ,5);
            String post=d.stop.code==null?"":d.stop.code.trim();
            TextView platform=platformChip(post.isEmpty()?"Nr —":"Stan. "+post);
            platform.setContentDescription(post.isEmpty()?"Numer stanowiska niedostępny":"Stanowisko "+post);
            platform.setGravity(Gravity.CENTER);
            middle.addView(platform,new LinearLayout.LayoutParams(dp(85),-2));
            panel.addView(middle);
            NativeUi.addSpacer(panel,this,7);

            LinearLayout bottom=new LinearLayout(this);
            bottom.setOrientation(LinearLayout.HORIZONTAL);
            bottom.setGravity(Gravity.CENTER_VERTICAL);
            TextView clock=timeChip(time(d.when));
            clock.setContentDescription("Odjazd planowy "+time(d.when));
            clock.setGravity(Gravity.CENTER);
            bottom.addView(clock,new LinearLayout.LayoutParams(dp(92),-2));
            horizontalGap(bottom ,7);
            Integer delay=d.confirmedDelayMinutes(System.currentTimeMillis());
            String liveLabel=delay==null?"Rozkładowo":
                    delay>0?"+"+delay+" min · LIVE":
                    delay<0?delay+" min · LIVE":"Bez opóźnienia · LIVE";
            TextView status=liveChip(liveLabel,delay);
            status.setGravity(Gravity.CENTER);
            bottom.addView(status,new LinearLayout.LayoutParams(0,-2,1));
            panel.addView(bottom);
            NativeUi.addSpacer(panel,this,5);
            TextView countdown=liveChip(DepartureCountdown.label(d.when,System.currentTimeMillis(),delay),delay);
            countdown.setGravity(Gravity.CENTER);
            countdown.setTextSize(16);
            panel.addView(countdown,new LinearLayout.LayoutParams(-1,-2));
            countdownLabels.add(new CountdownLabel(d,countdown,status));
            panel.setContentDescription("Linia "+d.line+". Kierunek "+d.headsign+
                ". Przystanek "+stopLabel(d.stop)+". Odjazd "+time(d.when)+". "+liveLabel);
            panel.setClickable(true);
            panel.setFocusable(true);
            NativeUi.onClick(panel,v->route(d));
            rows.add(new DepartureCard(d,panel));
            section.addView(panel);
        }
        results.addView(section);
    }
    private void updateCountdowns(){
        long now=System.currentTimeMillis();
        for(CountdownLabel label:countdownLabels){
            Integer delay=label.departure.confirmedDelayMinutes(now);
            label.view.setText(DepartureCountdown.label(label.departure.when,now,delay));
            applyLivePalette(label.view,delay);
            applyLivePalette(label.liveState,delay);
            String description=delay==null?"Rozkładowo":
                delay>0?"+"+delay+" min · LIVE":
                delay<0?delay+" min · LIVE":"Bez opóźnienia · LIVE";
            label.liveState.setText(description);
        }
        // Recompute the hero when departure times pass or an update expires.
        refreshNearestFromVisible(now);
    }
    private void refreshNearestFromVisible(long now){
        GtfsNearby.Departure best=null;long earliest=Long.MAX_VALUE;
        for(List<DepartureCard> cards:modeCards.values())for(DepartureCard card:cards){
            if(card.view.getVisibility()!=View.VISIBLE)continue;
            long expected=DepartureQuickFilter.expectedTime(card.departure,now);
            if(expected<now-60000L)continue;
            if(expected<earliest){earliest=expected;best=card.departure;}
        }
        nearestDeparture=best;
        refreshNearestPanel(now);
    }
    private void applyLivePalette(TextView view,Integer delay){
        boolean dark=(getResources().getConfiguration().uiMode&
            Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        int background=delay==null?(dark?0xFF353D48:0xFFF0F3F6):
            delay>0?(dark?0xFF5A321C:0xFFFFE6D0):
            (dark?0xFF19442C:0xFFDEF6E5);
        int foreground=delay==null?(dark?0xFFEFF3F9:0xFF46515E):
            delay>0?(dark?0xFFFFECD9:0xFF8C3706):
            (dark?0xFFE5FFED:0xFF126035);
        GradientDrawable drawable=new GradientDrawable();
        drawable.setColor(background);
        drawable.setCornerRadius(dp(10));
        view.setBackground(drawable);
        view.setTextColor(foreground);
    }
    private void stopCountdowns(){
        ui.removeCallbacks(countdownTick);
        countdownLabels.clear();
    }
    private void startCountdowns(){
        ui.removeCallbacks(countdownTick);
        updateCountdowns();
        if(screenVisible && !destroyed && !countdownLabels.isEmpty())
            ui.postDelayed(countdownTick,30000L);
    }
    @Override protected void onStart(){
        super.onStart();
        screenVisible=true;
        startCountdowns();
    }
    private void route(GtfsNearby.Departure d){
        StringBuilder text=new StringBuilder();
        text.append("Odjazd planowy: ").append(time(d.when)).append("\nPrzystanek: ").append(stopLabel(d.stop))
            .append("\nLinia ").append(d.line).append(" · ").append(d.headsign).append("\n\n");
        if(d.following.isEmpty())text.append("Brak dostępnej listy kolejnych przystanków w pobranych danych.");
        else{
            text.append("Uproszczona trasa (kolejne przystanki):\n");
            int i=0;for(String stop:d.following){text.append(i++==0?"● ":"│  ").append(stop).append("\n");}
        }
        new android.app.AlertDialog.Builder(this).setTitle("Trasa linii "+d.line)
            .setMessage(text.toString()).setPositiveButton("Zamknij",null).show();
    }
    private void open(String url){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception e){status.setText("Nie ma przeglądarki do otwarcia rozkładu.");}
    }
    @Override protected void onStop(){
        screenVisible=false;
        ui.removeCallbacks(countdownTick);
        lastFix=null;lastFixAt=0L;
        if(refresh!=null)refresh.setEnabled(false);
        boolean searching=locating || (progress!=null && progress.getVisibility()==View.VISIBLE);
        stopGps();
        if(searching){
            generation++;
            if(locate!=null)locate.setEnabled(true);
            if(progress!=null)progress.setVisibility(View.GONE);
            if(status!=null)status.setText("Wyszukiwanie przerwane po opuszczeniu ekranu. Dotknij przycisku, aby spróbować ponownie.");
        }
        super.onStop();
    }
    @Override protected void onDestroy(){destroyed=true;generation++;stopCountdowns();stopGps();ui.removeCallbacksAndMessages(null);work.shutdownNow();super.onDestroy();}
}
