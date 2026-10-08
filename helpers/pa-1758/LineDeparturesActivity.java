package com.ispina.lokalnie;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.net.Uri;
import android.graphics.drawable.GradientDrawable;
import android.content.res.Configuration;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import com.ispina.lokalnie.transit.GtfsNearby;
import com.ispina.lokalnie.transit.DepartureCountdown;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Manual line + stop platform lookup. No GPS required. */
public final class LineDeparturesActivity extends ThemedActivity {
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final List<GtfsNearby.LineStop> stations=new ArrayList<>();
    private final List<Countdown> timers=new ArrayList<>();
    private Spinner network,stops;
    private EditText lineInput;
    private Button find,show;
    private ProgressBar progress;
    private TextView message;
    private LinearLayout departures;
    private int generation=0;
    private boolean destroyed=false,visible=false;
    private static final class Countdown {
        final GtfsNearby.Departure departure;
        final TextView text,delay;
        Countdown(GtfsNearby.Departure d,TextView t,TextView live){
            departure=d;text=t;delay=live;
        }
    }
    private final Runnable tick=new Runnable(){
        @Override public void run(){
            long now=System.currentTimeMillis();
            for(Countdown row:timers){
                Integer delay=row.departure.confirmedDelayMinutes(now);
                row.text.setText(DepartureCountdown.label(row.departure.when,now,delay));
                row.delay.setText(delayLabel(delay));
            }
            if(visible&&!destroyed&&!timers.isEmpty())ui.postDelayed(this,30000L);
        }
    };
    private int dp(int size){return NativeUi.dp(this,size);}
    @Override protected void onCreate(Bundle state){super.onCreate(state);build();}
    private void build(){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(NativeUi.bg(this));
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(15),dp(18),dp(15),dp(30));
        scroll.addView(root);
        setContentView(scroll);
        LinearLayout head=new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        Button back=NativeUi.button(this,"‹",true);
        back.setOnClickListener(v->finish());
        head.addView(back,new LinearLayout.LayoutParams(dp(49),dp(48)));
        head.addView(NativeUi.text(this,"Linia i przystanek",23,true));
        root.addView(head);
        NativeUi.addSpacer(root,this,12);
        LinearLayout card=NativeUi.card(this);
        card.addView(NativeUi.text(this,"Wybierz dowolny przystanek",20,true));
        card.addView(NativeUi.muted(this,
            "Wpisz numer linii, a potem wybierz przystanek i stanowisko na jej trasie. Nie potrzebujesz GPS.",14));
        NativeUi.addSpacer(card,this,10);
        card.addView(NativeUi.text(this,"Sieć komunikacyjna",15,true));
        network=new Spinner(this);
        ArrayAdapter<String> providers=new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[]{"Warszawa (WTP)","Małopolskie Linie Dowozowe (MLD)"});
        network.setAdapter(providers);
        network.setContentDescription("Wybierz sieć: Warszawa albo MLD");
        card.addView(network,new LinearLayout.LayoutParams(-1,dp(52)));
        NativeUi.addSpacer(card,this,8);
        card.addView(NativeUi.text(this,"Numer linii",15,true));
        lineInput=new EditText(this);
        lineInput.setSingleLine(true);
        lineInput.setTextSize(19);
        lineInput.setHint("np. 517, 190, A7, A4, S1");
        lineInput.setContentDescription("Wpisz numer linii autobusowej, tramwajowej lub kolejowej");
        lineInput.setFilters(new android.text.InputFilter[]{
            new android.text.InputFilter.LengthFilter(8)});
        lineInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT|
            android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        card.addView(lineInput,new LinearLayout.LayoutParams(-1,dp(55)));
        NativeUi.addSpacer(card,this,9);
        find=NativeUi.button(this,"Znajdź przystanki tej linii",false);
        find.setOnClickListener(v->loadStops());
        card.addView(find,new LinearLayout.LayoutParams(-1,dp(55)));
        NativeUi.addSpacer(card,this,8);
        card.addView(NativeUi.text(this,"Przystanek i stanowisko",15,true));
        stops=new Spinner(this);
        stops.setContentDescription("Wybierz przystanek na trasie linii");
        stops.setEnabled(false);
        stops.setAdapter(new ArrayAdapter<String>(this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[]{"Najpierw wyszukaj linię"}));
        card.addView(stops,new LinearLayout.LayoutParams(-1,dp(52)));
        NativeUi.addSpacer(card,this,9);
        show=NativeUi.button(this,"Pokaż odjazdy z tego przystanku",false);
        show.setEnabled(false);
        show.setOnClickListener(v->loadDepartures());
        card.addView(show,new LinearLayout.LayoutParams(-1,dp(55)));
        NativeUi.addSpacer(card,this,7);
        message=NativeUi.muted(this,"Wybierz sieć i wpisz numer linii.",14);
        message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        card.addView(message);
        root.addView(card);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        root.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));
        departures=new LinearLayout(this);
        departures.setOrientation(LinearLayout.VERTICAL);
        root.addView(departures);
        NativeUi.addSpacer(root,this,10);
        LinearLayout hint=NativeUi.card(this);
        hint.addView(NativeUi.text(this,"Planowo a LIVE",17,true));
        hint.addView(NativeUi.muted(this,"Nie wyświetlamy opóźnień na podstawie domysłów. LIVE pojawi się tylko wtedy, gdy przewoźnik udostępni świeżą, zgodną aktualizację dla konkretnego kursu i przystanku.",13));
        root.addView(hint);
        network.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){
                clearSelection("Zmieniono sieć. Wyszukaj przystanki wybranej linii.");
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        lineInput.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                clearSelection("Wyszukaj przystanki po wpisaniu numeru linii.");
            }
            @Override public void afterTextChanged(Editable s){}
        });
    }
    private String provider(){return network.getSelectedItemPosition()==1?"mld":"warsaw";}
    private String enteredLine(){return lineInput.getText().toString().trim().toUpperCase(Locale.ROOT);}
    private void clearSelection(String text){
        generation++;
        stations.clear();
        if(stops!=null){
            stops.setEnabled(false);
            stops.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Najpierw wyszukaj linię"}));
        }
        if(show!=null)show.setEnabled(false);
        if(departures!=null)departures.removeAllViews();
        stopCountdown();
        if(message!=null)message.setText(text);
    }
    private boolean valid(){
        if(!enteredLine().matches("[A-Z0-9]{1,8}")){
            message.setText("Podaj numer linii, np. 517, 190, A4, A7 lub S1.");
            return false;
        }
        return true;
    }
    private void busy(boolean on){
        progress.setVisibility(on?View.VISIBLE:View.GONE);
        find.setEnabled(!on);
        show.setEnabled(!on&&!stations.isEmpty());
        network.setEnabled(!on);
        lineInput.setEnabled(!on);
        stops.setEnabled(!on&&!stations.isEmpty());
    }
    private void dismissKeyboard(){
        try{
            InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if(imm!=null)imm.hideSoftInputFromWindow(lineInput.getWindowToken(),0);
        }catch(Exception ignored){}
        lineInput.clearFocus();
    }
    private void loadStops(){
        if(!valid()||progress.getVisibility()==View.VISIBLE)return;
        dismissKeyboard();clearSelection("Szukam przystanków linii "+enteredLine()+"…");
        final String provider=provider(),line=enteredLine();
        final int id=++generation;
        busy(true);
        work.execute(()->{
            List<GtfsNearby.LineStop> found=null;String error=null;
            try{found=GtfsNearby.lineStops(getApplicationContext(),provider,line);}
            catch(Exception e){error=e.getMessage();}
            final List<GtfsNearby.LineStop> data=found;
            final String problem=error;
            ui.post(()->{
                if(destroyed||id!=generation)return;
                busy(false);
                if(problem!=null){message.setText("Nie można pobrać przystanków: "+problem);return;}
                if(data==null||data.isEmpty()){
                    message.setText("Nie znaleziono tej linii w rozkładzie wybranej sieci.");
                    return;
                }
                stations.addAll(data);
                List<String> labels=new ArrayList<>();
                for(GtfsNearby.LineStop stop:stations)labels.add(stop.label());
                stops.setAdapter(new ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item,labels));
                stops.setEnabled(true);
                show.setEnabled(true);
                message.setText("Znaleziono "+stations.size()+" stanowisk. Wybierz właściwy przystanek.");
            });
        });
    }
    private void loadDepartures(){
        int position=stops.getSelectedItemPosition();
        if(position<0||position>=stations.size()||progress.getVisibility()==View.VISIBLE)return;
        final String provider=provider(),line=enteredLine(),stopId=stations.get(position).id;
        final int id=++generation;
        dismissKeyboard();
        departures.removeAllViews();
        stopCountdown();
        message.setText("Pobieram odjazdy z wybranego przystanku…");
        busy(true);
        work.execute(()->{
            GtfsNearby.Result result=null;String error=null;
            try{result=GtfsNearby.lineStopDepartures(getApplicationContext(),provider,line,stopId);}
            catch(Exception e){error=e.getMessage();}
            final GtfsNearby.Result data=result;final String problem=error;
            ui.post(()->{
                if(destroyed||id!=generation)return;
                busy(false);
                if(problem!=null){message.setText("Nie mogę pobrać odjazdów: "+problem);return;}
                render(data,line);
            });
        });
    }
    private String clock(long stamp){
        SimpleDateFormat fmt=new SimpleDateFormat("HH:mm",new Locale("pl","PL"));
        fmt.setTimeZone(java.util.TimeZone.getTimeZone("Europe/Warsaw"));
        return fmt.format(new Date(stamp));
    }
    private TextView chip(String value,int size,int lightBg,int lightFg,int darkBg,int darkFg){
        TextView chip=NativeUi.text(this,value,size,true);
        boolean dark=(getResources().getConfiguration().uiMode&
            Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        chip.setTextColor(dark?darkFg:lightFg);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(dark?darkBg:lightBg);
        bg.setCornerRadius(dp(10));
        chip.setBackground(bg);
        chip.setPadding(dp(10),dp(9),dp(10),dp(9));
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setMinHeight(dp(41));
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        return chip;
    }
    private TextView routeChip(String label){
        return chip(label,20,0xFF5836A5,0xFFFFFFFF,0xFF714DC0,0xFFFFFFFF);
    }
    private TextView destinationChip(String label){
        TextView result=chip(label,17,0xFFE6F0FF,0xFF174783,0xFF1C3A61,0xFFF0F6FF);
        result.setAutoSizeTextTypeUniformWithConfiguration(12,17,1,TypedValue.COMPLEX_UNIT_SP);
        result.setContentDescription("Kierunek: "+label);
        return result;
    }
    private TextView stopChip(String label){
        TextView result=chip(label,15,0xFFE0F4E9,0xFF075B46,0xFF173F34,0xFFE3FFF0);
        result.setContentDescription("Przystanek: "+label);
        return result;
    }
    private TextView clockChip(String label){
        return chip(label,21,0xFF125E7B,0xFFFFFFFF,0xFF17678B,0xFFFFFFFF);
    }
    private TextView delayChip(String label,Integer delay){
        if(delay==null)return chip(label,13,0xFFF0F3F6,0xFF46515E,0xFF353D48,0xFFEFF3F9);
        if(delay>0)return chip(label,13,0xFFFFE6D0,0xFF8C3706,0xFF5A321C,0xFFFFECD9);
        return chip(label,13,0xFFDEF6E5,0xFF126035,0xFF19442C,0xFFE5FFED);
    }
    private String delayLabel(Integer delay){
        return delay==null?"Rozkładowo":delay>0?"+"+delay+" min · LIVE":
            delay<0?delay+" min · LIVE":"Bez opóźnienia · LIVE";
    }
    private void spaceHorizontal(LinearLayout row,int px){
        row.addView(new View(this),new LinearLayout.LayoutParams(dp(px),1));
    }
    private void render(GtfsNearby.Result result,String line){
        message.setText(result.feedName+" · "+(result.oldData?"starszy rozkład":"rozkład planowy")+
            " · sprawdzono "+clock(System.currentTimeMillis()));
        departures.removeAllViews();
        LinearLayout state=NativeUi.card(this);
        state.addView(NativeUi.text(this,"Stan informacji LIVE",17,true));
        state.addView(NativeUi.muted(this,result.liveNote==null?
            "Brak potwierdzonych opóźnień; pokazuję godziny planowe.":result.liveNote,14));
        departures.addView(state);
        NativeUi.addSpacer(departures,this,8);
        if(result.departures.isEmpty()){
            LinearLayout empty=NativeUi.card(this);
            empty.addView(NativeUi.text(this,"Brak odjazdów w najbliższych 2 godzinach",18,true));
            empty.addView(NativeUi.muted(this,result.note==null?"Wybierz inny przystanek.":result.note,14));
            departures.addView(empty);
        }
        int count=0;
        for(GtfsNearby.Departure d:result.departures){
            if(count++>=15)break;
            LinearLayout card=NativeUi.card(this);
            LinearLayout head=new LinearLayout(this);
            head.setGravity(Gravity.CENTER_VERTICAL);
            TextView route=routeChip(d.line);
            route.setGravity(Gravity.CENTER);
            head.addView(route,new LinearLayout.LayoutParams(dp(65),-2));
            spaceHorizontal(head,6);
            head.addView(destinationChip(d.headsign),new LinearLayout.LayoutParams(0,-2,1));
            card.addView(head);
            NativeUi.addSpacer(card,this,6);
            LinearLayout middle=new LinearLayout(this);
            middle.setGravity(Gravity.CENTER_VERTICAL);
            middle.addView(stopChip(d.stop.name),new LinearLayout.LayoutParams(0,-2,1));
            spaceHorizontal(middle,6);
            TextView platform=chip(d.stop.code==null||d.stop.code.isEmpty()?"Stan. —":
                "Stan. "+d.stop.code,14,0xFFFFEDD0,0xFF794200,0xFF52391D,0xFFFFEAC0);
            middle.addView(platform,new LinearLayout.LayoutParams(dp(85),-2));
            card.addView(middle);
            NativeUi.addSpacer(card,this,6);
            LinearLayout bottom=new LinearLayout(this);
            bottom.setGravity(Gravity.CENTER_VERTICAL);
            TextView when=clockChip(clock(d.when));
            when.setGravity(Gravity.CENTER);
            bottom.addView(when,new LinearLayout.LayoutParams(dp(92),-2));
            spaceHorizontal(bottom,7);
            Integer delay=d.confirmedDelayMinutes(System.currentTimeMillis());
            TextView live=delayChip(delayLabel(delay),delay);
            bottom.addView(live,new LinearLayout.LayoutParams(0,-2,1));
            card.addView(bottom);
            NativeUi.addSpacer(card,this,5);
            TextView countdown=delayChip(
                DepartureCountdown.label(d.when,System.currentTimeMillis(),delay),delay);
            card.addView(countdown,new LinearLayout.LayoutParams(-1,-2));
            timers.add(new Countdown(d,countdown,live));
            card.setClickable(true);card.setFocusable(true);
            card.setContentDescription("Linia "+d.line+", kierunek "+d.headsign+
                ", przystanek "+d.stop.name+", odjazd "+clock(d.when)+", "+delayLabel(delay));
            card.setOnClickListener(v->showRoute(d));
            departures.addView(card);
        }
        LinearLayout external=NativeUi.card(this);
        external.addView(NativeUi.text(this,"Informacje u przewoźnika",17,true));
        Button web=NativeUi.button(this,"Sprawdź zewnętrzne informacje LIVE",true);
        web.setOnClickListener(v->{
            String url=provider().equals("warsaw")?"https://time4bus.com/":
                "https://kolejemalopolskie.kiedyprzyjedzie.pl/";
            try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
            catch(Exception ex){message.setText("Nie można otworzyć serwisu LIVE.");}
        });
        external.addView(web,new LinearLayout.LayoutParams(-1,dp(52)));
        departures.addView(external);
        ui.removeCallbacks(tick);
        if(visible&&!timers.isEmpty())ui.postDelayed(tick,30000L);
    }
    private void showRoute(GtfsNearby.Departure d){
        StringBuilder text=new StringBuilder();
        text.append("Odjazd: ").append(clock(d.when)).append("\n")
            .append("Kierunek: ").append(d.headsign).append("\n")
            .append("Przystanek: ").append(d.stop.name).append("\n\n");
        if(d.following.isEmpty())text.append("Dalsza trasa nie jest dostępna.");
        else{
            text.append("Kolejne przystanki:\n");
            for(String name:d.following)text.append("• ").append(name).append("\n");
        }
        new android.app.AlertDialog.Builder(this).setTitle("Trasa linii "+d.line)
            .setMessage(text.toString()).setPositiveButton("Zamknij",null).show();
    }
    private void stopCountdown(){ui.removeCallbacks(tick);timers.clear();}
    @Override protected void onStart(){
        super.onStart();visible=true;
        ui.removeCallbacks(tick);
        if(!timers.isEmpty())ui.post(tick);
    }
    @Override protected void onStop(){
        visible=false;ui.removeCallbacks(tick);super.onStop();
    }
    @Override protected void onDestroy(){
        destroyed=true;generation++;stopCountdown();work.shutdownNow();
        ui.removeCallbacksAndMessages(null);super.onDestroy();
    }
}
