package com.ispina.lokalnie;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ispina.lokalnie.transit.CzynaczasLinks;
import com.ispina.lokalnie.transit.WarsawVehicleGps;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native, opt-in GPS LIVE for Warsaw. Never represents positions as predicted departures. */
public final class GpsLiveActivity extends ThemedActivity {
    public static final String EXTRA_ROUTE="gps_route_id";
    public static final String EXTRA_LINE="gps_line";
    public static final String EXTRA_STOP="gps_stop";
    public static final String EXTRA_STOP_ID="gps_stop_id";
    public static final String EXTRA_LAT="gps_lat";
    public static final String EXTRA_LON="gps_lon";

    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private TextView status,attribution;
    private Button refresh;
    private int serial;
    private boolean visible,destroyed;
    private final Runnable periodic=new Runnable(){
        @Override public void run(){
            if(visible&&!destroyed)load();
        }
    };
    private int dp(int value){return NativeUi.dp(this,value);}
    private String time(long timestamp){
        SimpleDateFormat f=new SimpleDateFormat("HH:mm:ss",new Locale("pl","PL"));
        f.setTimeZone(java.util.TimeZone.getTimeZone("Europe/Warsaw"));
        return f.format(new Date(timestamp));
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        Intent args=getIntent();
        String line=args.getStringExtra(EXTRA_LINE);
        if(line==null)line="—";
        final String displayLine=line;
        String stop=args.getStringExtra(EXTRA_STOP);
        if(stop==null||stop.trim().isEmpty())stop="Wybrany przystanek";
        String stopId=args.getStringExtra(EXTRA_STOP_ID);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(15),dp(18),dp(15),dp(26));
        root.setBackgroundColor(NativeUi.bg(this));
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);setContentView(scroll);

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back=NativeUi.button(this,"‹",true);
        back.setOnClickListener(v->finish());
        header.addView(back,new LinearLayout.LayoutParams(dp(50),dp(47)));
        TextView title=NativeUi.text(this,"GPS LIVE · linia "+displayLine,22,true);
        title.setPadding(dp(9),0,0,0);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(header);
        NativeUi.addSpacer(root,this,10);
        LinearLayout card=NativeUi.card(this);
        card.addView(NativeUi.text(this,stop,18,true));
        NativeUi.addSpacer(card,this,8);
        status=NativeUi.text(this,"Odczytuję aktualne pozycje pojazdów…",17,true);
        status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        card.addView(status);
        NativeUi.addSpacer(card,this,7);
        card.addView(NativeUi.muted(this,
            "To rzeczywiste zgłoszenia GPS pojazdów tej linii, a NIE prognoza przyjazdu. "+
            "Nie znamy kierunku ani czasu dojazdu na podstawie samej pozycji. "+
            "Odległość oznacza dystans w linii prostej od wybranego przystanku.",13));
        root.addView(card);
        NativeUi.addSpacer(root,this,8);
        refresh=NativeUi.button(this,"↻ Odśwież pozycje GPS",false);
        refresh.setOnClickListener(v->load());
        root.addView(refresh,new LinearLayout.LayoutParams(-1,dp(52)));

        if(stopId!=null&&stopId.matches("[0-9]{6}")){
            NativeUi.addSpacer(root,this,8);
            Button map=NativeUi.button(this,"Otwórz mapę i odjazdy LIVE",true);
            final String savedStop=stopId,savedName=stop;
            map.setOnClickListener(v->startActivity(new Intent(this,LiveMapActivity.class)
                .putExtra(LiveMapActivity.EXTRA_STOP_ID,savedStop)
                .putExtra(LiveMapActivity.EXTRA_STOP_NAME,savedName)
                .putExtra(LiveMapActivity.EXTRA_LINE,displayLine)));
            root.addView(map,new LinearLayout.LayoutParams(-1,dp(50)));
        }
        NativeUi.addSpacer(root,this,9);
        LinearLayout credits=NativeUi.card(this);
        credits.addView(NativeUi.text(this,"Źródło i aktualność",16,true));
        attribution=NativeUi.muted(this,
            "Pozycje: Miasto Stołeczne Warszawa, poprzez mkuran.pl (GTFS-RT). "+
            "Źródło oficjalnych danych: api.um.warszawa.pl.",13);
        credits.addView(attribution);
        Button source=NativeUi.button(this,"Źródło danych miasta",true);
        source.setOnClickListener(v->{
            try{startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://api.um.warszawa.pl/")));}
            catch(Exception ignored){}
        });
        credits.addView(source,new LinearLayout.LayoutParams(-1,dp(45)));
        root.addView(credits);
    }
    private void load(){
        if(destroyed||!visible)return;
        final int ticket=++serial;
        final String route=getIntent().getStringExtra(EXTRA_ROUTE);
        final double lat=getIntent().getDoubleExtra(EXTRA_LAT,Double.NaN);
        final double lon=getIntent().getDoubleExtra(EXTRA_LON,Double.NaN);
        refresh.setEnabled(false);
        status.setText("Sprawdzam świeże sygnały GPS…");
        ui.removeCallbacks(periodic);
        worker.execute(()->{
            WarsawVehicleGps.Snapshot data=null;
            String problem=null;
            try{
                Set<String> ids=(route==null||route.trim().isEmpty())?
                    Collections.emptySet():Collections.singleton(route);
                data=WarsawVehicleGps.load(ids,lat,lon);
            }catch(Exception ex){problem=ex.getMessage();}
            final WarsawVehicleGps.Snapshot snapshot=data;
            final String error=problem;
            ui.post(()->{
                if(destroyed||!visible||ticket!=serial)return;
                refresh.setEnabled(true);
                if(error!=null){
                    status.setText("Brak aktualnych danych GPS. "+
                        "Możesz otworzyć mapę LIVE lub spróbować ponownie.");
                    attribution.setText("Błąd odczytu: "+error+". "+
                        "Nie pokazuję niezweryfikowanych pozycji.");
                }else if(snapshot!=null){
                    String result=snapshot.vehicles==0?
                        "Brak świeżych sygnałów GPS dla tej linii. Nie oznacza to braku kursów.":
                        "Pojazdy tej linii zgłaszające GPS: "+snapshot.vehicles;
                    if(!Double.isNaN(snapshot.nearestMeters))
                        result+="\nNajbliższy sygnał: "+Math.round(snapshot.nearestMeters)+" m w linii prostej";
                    if(snapshot.latestVehicleMillis>0)
                        result+="\nOstatni sygnał pojazdu: "+time(snapshot.latestVehicleMillis);
                    status.setText(result);
                    attribution.setText("Miasto Stołeczne Warszawa • mkuran.pl. "+
                        "Aktualizacja źródła: "+time(snapshot.feedTimeMillis)+". "+
                        "Oficjalne dane: api.um.warszawa.pl. "+
                        "Pozycje nie są obliczeniem czasu do odjazdu.");
                }
                ui.postDelayed(periodic,45000L);
            });
        });
    }
    @Override protected void onStart(){super.onStart();visible=true;load();}
    @Override protected void onStop(){
        visible=false;serial++;ui.removeCallbacks(periodic);
        super.onStop();
    }
    @Override protected void onDestroy(){
        destroyed=true;serial++;ui.removeCallbacksAndMessages(null);
        worker.shutdownNow();super.onDestroy();
    }
}
