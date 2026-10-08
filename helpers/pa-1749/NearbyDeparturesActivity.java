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
import android.widget.TextView;
import android.net.Uri;

import com.ispina.lokalnie.transit.GtfsNearby;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
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
    private Button locate;
    private ProgressBar progress;
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
        NativeUi.addSpacer(card,this,6);
        status=NativeUi.muted(this,"Dotknij przycisku. Lokalizacja nie działa w tle.",14);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);card.addView(status);root.addView(card);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);progress.setVisibility(View.GONE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));
        results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);root.addView(results);
        NativeUi.addSpacer(root,this,15);
        LinearLayout info=NativeUi.card(this);
        info.addView(NativeUi.text(this,"Dane i dokładność",17,true));
        info.addView(NativeUi.muted(this,"Warszawa: WTP (autobusy, tramwaje, metro i SKM) przez WarsawGTFS; pociągi Kolei Mazowieckich nie są częścią tej bazy. Małopolska: rozkłady autobusów MLD. Godziny są planowe, bez opóźnień na żywo. Odległości podano w linii prostej. Dane WTP: ZTM Warszawa, Mikołaj Kuranowski i © OpenStreetMap contributors.",13));
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
        final int requestId=generation;
        progress.setVisibility(View.VISIBLE);
        status.setText("Szukam pobliskich przystanków i sprawdzam aktualny rozkład…");
        work.execute(()->{
            GtfsNearby.Result value=null;String error=null;
            try{value=GtfsNearby.search(getApplicationContext(),fix.getLatitude(),fix.getLongitude());}
            catch(Exception e){error=e.getMessage();}
            GtfsNearby.Result found=value;String problem=error;
            ui.post(()->{
                if(destroyed||requestId!=generation)return;
                locate.setEnabled(true);progress.setVisibility(View.GONE);
                if(problem!=null){renderError(problem,fix);return;}
                render(found,fix);
            });
        });
    }
    private void fail(String message){
        stopGps();progress.setVisibility(View.GONE);locate.setEnabled(true);status.setText(message);
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
        if(code.isEmpty() || name.endsWith(" "+code))return name;
        return name+" "+code;
    }
    private void render(GtfsNearby.Result value,Location fix){
        results.removeAllViews();
        SimpleDateFormat stamp=new SimpleDateFormat("dd.MM, HH:mm",new Locale("pl","PL"));
        stamp.setTimeZone(java.util.TimeZone.getTimeZone("Europe/Warsaw"));
        String age=stamp.format(new Date(value.downloadedAt));
        status.setText(value.feedName+" · dane "+age+(value.oldData?" · UWAGA: starszy rozkład":""));
        NativeUi.addSpacer(results,this,9);
        LinearLayout stopCard=NativeUi.card(this);
        stopCard.addView(NativeUi.text(this,"🚏 Najbliższe przystanki",19,true));
        int limit=0;
        for(GtfsNearby.Stop stop:value.stops){
            if(limit++>=8)break;
            TextView txt=NativeUi.text(this,stopLabel(stop)+" · "+Math.round(stop.distance)+" m w linii prostej",15,false);
            txt.setPadding(0,dp(6),0,dp(6));stopCard.addView(txt);
        }
        if(value.stops.isEmpty())stopCard.addView(NativeUi.muted(this,value.note,14));
        results.addView(stopCard);
        boolean warsaw="warsaw".equals(GtfsNearby.networkFor(fix.getLatitude(),fix.getLongitude()));
        addDepartureSection(value,"bus","🚌 Autobusy",false);
        if(warsaw){
            addDepartureSection(value,"tram","🚋 Tramwaje",false);
            addDepartureSection(value,"skm","🚆 Pociągi SKM",true);
            addDepartureSection(value,"metro","🚇 Metro",false);
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
    }
    private void addDepartureSection(GtfsNearby.Result value,String mode,String heading,boolean alwaysVisible){
        List<GtfsNearby.Departure> selected=new ArrayList<>();
        for(GtfsNearby.Departure d:value.departures)if(mode.equals(d.mode))selected.add(d);
        if(selected.isEmpty()&&!alwaysVisible)return;
        LinearLayout section=NativeUi.card(this);
        section.addView(NativeUi.text(this,heading+" · planowo",19,true));
        if(selected.isEmpty()){
            section.addView(NativeUi.muted(this,
                "Nie znaleziono potwierdzonych odjazdów w najbliższych dwóch godzinach z pobliskich przystanków tej kategorii.",14));
            results.addView(section);return;
        }
        Set<String> stopKeys=new HashSet<>();
        StringBuilder near=new StringBuilder();
        for(GtfsNearby.Departure d:selected){
            if(stopKeys.size()>=4)break;
            if(!stopKeys.add(d.stop.id))continue;
            if(near.length()>0)near.append(" • ");
            near.append(stopLabel(d.stop));
        }
        section.addView(NativeUi.muted(this,"Przystanki: "+near+" · odjazdy planowe, nie na żywo",12));
        NativeUi.addSpacer(section,this,8);
        int count=0;
        for(GtfsNearby.Departure d:selected){
            if(count++>=12)break;
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
            TextView line=NativeUi.text(this,d.line,18,true);
            row.addView(line,new LinearLayout.LayoutParams(dp(56),-2));
            LinearLayout description=new LinearLayout(this);description.setOrientation(LinearLayout.VERTICAL);
            description.addView(NativeUi.text(this,d.headsign,15,true));
            description.addView(NativeUi.muted(this,stopLabel(d.stop)+" · "+Math.round(d.stop.distance)+" m",12));
            row.addView(description,new LinearLayout.LayoutParams(0,-2,1));
            TextView clock=NativeUi.text(this,time(d.when),18,true);
            row.addView(clock);
            LinearLayout panel=NativeUi.card(this);
            panel.addView(row);
            panel.setClickable(true);panel.setFocusable(true);
            NativeUi.onClick(panel,v->route(d));
            section.addView(panel);
        }
        results.addView(section);
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
    @Override protected void onDestroy(){destroyed=true;generation++;stopGps();ui.removeCallbacksAndMessages(null);work.shutdownNow();super.onDestroy();}
}
