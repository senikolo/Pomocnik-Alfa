#!/usr/bin/env python3
"""PA 1.7.65 — line -> travel direction -> correct stops -> matching departures."""
from pathlib import Path
from shutil import copyfile
base=Path("project/app/src/main/java/com/ispina/lokalnie")
copyfile("helpers/pa-1765/FastLineDirections.java",base/"transit"/"FastLineDirections.java")
p=base/"LineDeparturesActivity.java"
s=p.read_text(encoding="utf-8")
def once(a,b,why):
    global s
    n=s.count(a)
    if n!=1:raise AssertionError(f"{why}: found {n}")
    s=s.replace(a,b,1)
once("import com.ispina.lokalnie.transit.GtfsNearby;",
'''import com.ispina.lokalnie.transit.GtfsNearby;
import com.ispina.lokalnie.transit.FastLineDirections;''',"direction index import")
once("    private final List<GtfsNearby.LineStop> stations=new ArrayList<>();",
'''    private final List<GtfsNearby.LineStop> stations=new ArrayList<>();
    private final List<GtfsNearby.LineStop> allStations=new ArrayList<>();
    private final List<FastLineDirections.Direction> directionOptions=new ArrayList<>();
    private Spinner directions;''',"direction state")
once('''        NativeUi.addSpacer(card,this,8);
        card.addView(NativeUi.text(this,"Przystanek i stanowisko",15,true));''',
'''        NativeUi.addSpacer(card,this,8);
        card.addView(TransitWowUi.type(this,"KIERUNEK JAZDY →",16,true,
            TransitWowUi.ink(this)));
        directions=new Spinner(this);
        directions.setEnabled(false);
        directions.setContentDescription("Wybierz kierunek linii. Lista przystanków zależy od kierunku.");
        directions.setAdapter(new ArrayAdapter<String>(this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[]{"Najpierw wyszukaj linię"}));
        directions.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                                                  View view,int pos,long id){
                if(pos>=0 && pos<directionOptions.size())applyDirection(pos);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        card.addView(directions,new LinearLayout.LayoutParams(-1,dp(58)));
        NativeUi.addSpacer(card,this,8);
        card.addView(TransitWowUi.type(this,"PRZYSTANEK I STANOWISKO",15,true,
            TransitWowUi.ink(this)));''',"direction spinner between line and stops")
once('''        stations.clear();
        if(stops!=null){''',
'''        stations.clear();
        allStations.clear();
        directionOptions.clear();
        if(directions!=null){
            directions.setEnabled(false);
            directions.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Najpierw wyszukaj linię"}));
        }
        if(stops!=null){''',"clear direction on line change")
once('''        stops.setEnabled(!on&&!stations.isEmpty());''',
'''        stops.setEnabled(!on&&!stations.isEmpty());
        if(directions!=null)directions.setEnabled(!on&&!directionOptions.isEmpty());''',
     "busy direction control")
once('''            List<GtfsNearby.LineStop> found=null;String error=null;
            try{found=GtfsNearby.lineStops(getApplicationContext(),provider,line);}
            catch(Exception e){error=e.getMessage();}
            final List<GtfsNearby.LineStop> data=found;''',
'''            List<GtfsNearby.LineStop> found=null;
            List<FastLineDirections.Direction> headings=null;
            String error=null;
            try{
                found=GtfsNearby.lineStops(getApplicationContext(),provider,line);
                headings=FastLineDirections.forLine(getApplicationContext(),provider,line);
            }catch(Exception e){error=e.getMessage();}
            final List<GtfsNearby.LineStop> data=found;
            final List<FastLineDirections.Direction> matching=headings;''',
     "load headings with line stops in same async worker")
once('''                stations.addAll(data);
                List<String> labels=new ArrayList<>();
                for(GtfsNearby.LineStop stop:stations)labels.add(stop.label());
                stops.setAdapter(new ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item,labels));
                stops.setEnabled(true);
                show.setEnabled(true);
                message.setText("Znaleziono "+stations.size()+" stanowisk. Wybierz właściwy przystanek.");''',
'''                allStations.addAll(data);
                if(matching!=null)directionOptions.addAll(matching);
                if(directionOptions.isEmpty()){
                    stations.addAll(allStations);
                    List<String> names=new ArrayList<>();
                    for(GtfsNearby.LineStop stop:stations)names.add(stop.label());
                    stops.setAdapter(new ArrayAdapter<String>(this,
                        android.R.layout.simple_spinner_dropdown_item,names));
                    stops.setEnabled(true);show.setEnabled(true);
                    directions.setEnabled(false);
                    directions.setAdapter(new ArrayAdapter<String>(this,
                        android.R.layout.simple_spinner_dropdown_item,
                        new String[]{"Kierunki niedostępne w rozkładzie"}));
                    message.setText("Wybierz przystanek. Dostawca nie podał kierunków tej linii.");
                    return;
                }
                List<String> labels=new ArrayList<>();
                for(FastLineDirections.Direction dir:directionOptions)
                    labels.add("→ "+dir.name);
                directions.setAdapter(new ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item,labels));
                directions.setEnabled(true);
                applyDirection(0);''',"choose direction before stops")
once('    private void loadDepartures(){',
'''    private String selectedDirection(){
        int pos=directions==null?-1:directions.getSelectedItemPosition();
        return pos>=0&&pos<directionOptions.size()?directionOptions.get(pos).name:"";
    }
    private void applyDirection(int index){
        if(index<0||index>=directionOptions.size())return;
        FastLineDirections.Direction choice=directionOptions.get(index);
        stations.clear();
        for(GtfsNearby.LineStop candidate:allStations)
            if(choice.stops.containsKey(candidate.id))stations.add(candidate);
        stations.sort(java.util.Comparator
            .comparingInt((GtfsNearby.LineStop st)->choice.stops.get(st.id))
            .thenComparing(st->st.name,java.text.Collator.getInstance(new Locale("pl","PL"))));
        List<String> names=new ArrayList<>();
        for(GtfsNearby.LineStop st:stations)names.add(st.label());
        stops.setAdapter(new ArrayAdapter<String>(this,
            android.R.layout.simple_spinner_dropdown_item,
            names.isEmpty()?java.util.Collections.singletonList("Brak przystanków w tym kierunku"):names));
        stops.setEnabled(!stations.isEmpty());
        show.setEnabled(!stations.isEmpty());
        departures.removeAllViews();
        stopCountdown();
        message.setText("Kierunek → "+choice.name+" · "+stations.size()+
            " stanowisk na trasie. Wybierz przystanek.");
    }
    private void loadDepartures(){''',"direction choice selects only valid stops")
once('''        final String provider=provider(),line=enteredLine(),stopId=stations.get(position).id;''',
'''        final String provider=provider(),line=enteredLine(),stopId=stations.get(position).id,
            direction=selectedDirection();''',"snapshot selected direction")
once('''            try{result=GtfsNearby.lineStopDepartures(getApplicationContext(),provider,line,stopId);}
            catch(Exception e){error=e.getMessage();}''',
'''            try{
                result=GtfsNearby.lineStopDepartures(getApplicationContext(),provider,line,stopId);
                if(result!=null&&!direction.isEmpty()){
                    result.departures.removeIf(d->
                        !direction.equalsIgnoreCase(d.headsign));
                }
            }catch(Exception e){error=e.getMessage();}''',"filter arrivals by selected exact direction")
# Header for initial/manual live redraw; wowLastStop currently only name.
once('''        message.setText("Odjazdy · sprawdzono "+clock(System.currentTimeMillis())+''',
'''        message.setText("Kierunek → "+selectedDirection()+" · sprawdzono "+
            clock(System.currentTimeMillis())+''',"explicit manual result heading")
p.write_text(s,encoding="utf-8")
print("PASS: chosen line exposes travel directions, stop list is direction-specific and arrivals filtered")
