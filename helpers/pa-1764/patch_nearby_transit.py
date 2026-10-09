#!/usr/bin/env python3
"""Passenger-first nearby UI: favorite stops, live-only filter, no departed rows."""
from pathlib import Path
p=Path("project/app/src/main/java/com/ispina/lokalnie/NearbyDeparturesActivity.java")
s=p.read_text(encoding="utf-8")
def once(a,b,label):
    global s
    count=s.count(a)
    if count!=1:raise AssertionError(f"{label}: {count}")
    s=s.replace(a,b,1)

once('    private GtfsNearby.Result wowData;',
'''    private GtfsNearby.Result wowData;
    private boolean wowOnlyLive;
    private TextView wowFavouriteButton;
    private LinearLayout wowToggles;''',"new state")

once('''        wowSelectedStop=unique.values().iterator().next().name.toLowerCase(Locale.ROOT);
        addWowStopTabs(unique);''',
'''        wowSelectedStop=unique.values().iterator().next().name.toLowerCase(Locale.ROOT);
        String saved=getSharedPreferences("pa_transit_favourite",MODE_PRIVATE)
            .getString("stop","");
        if(unique.containsKey(saved))wowSelectedStop=saved;
        addWowStopTabs(unique);
        buildWowActions();''',"persistent default stop")

once('''                    addWowStopTabs(stops);refreshWowRows();''',
'''                    addWowStopTabs(stops);refreshWowFavourite();refreshWowRows();''',
    "keep favorite control in sync")

once('    private void refreshWowRows(){',
'''    private void buildWowActions(){
        LinearLayout controls=NativeUi.card(this);
        wowFavouriteButton=TransitWowUi.type(this,"☆ Zapisz przystanek",
            15,true,TransitWowUi.ink(this));
        wowFavouriteButton.setGravity(Gravity.CENTER_VERTICAL);
        wowFavouriteButton.setPadding(dp(12),dp(9),dp(12),dp(9));
        wowFavouriteButton.setMinHeight(dp(44));
        wowFavouriteButton.setBackground(TransitWowUi.round(this,
            0x00000000,0xFF8899AA,12));
        wowFavouriteButton.setClickable(true);wowFavouriteButton.setFocusable(true);
        wowFavouriteButton.setOnClickListener(v->{
            android.content.SharedPreferences prefs=getSharedPreferences(
                "pa_transit_favourite",MODE_PRIVATE);
            String stored=prefs.getString("stop","");
            prefs.edit().putString("stop",stored.equals(wowSelectedStop)?"":wowSelectedStop)
                .apply();
            refreshWowFavourite();
        });
        controls.addView(wowFavouriteButton);
        refreshWowFavourite();
        NativeUi.addSpacer(controls,this,7);
        controls.addView(TransitWowUi.type(this,"Pokaż",13,false,
            TransitWowUi.subtle(this)));
        NativeUi.addSpacer(controls,this,6);
        wowToggles=new LinearLayout(this);
        wowToggles.setOrientation(LinearLayout.HORIZONTAL);
        controls.addView(wowToggles);
        buildWowToggles();
        results.addView(controls);
    }
    private void refreshWowFavourite(){
        if(wowFavouriteButton==null)return;
        boolean stored=wowSelectedStop.equals(getSharedPreferences(
            "pa_transit_favourite",MODE_PRIVATE).getString("stop",""));
        wowFavouriteButton.setText(stored?"★ Ulubiony przystanek":"☆ Zapisz przystanek");
        wowFavouriteButton.setContentDescription(stored?
            "Usuń przystanek z ulubionych":"Dodaj przystanek do ulubionych");
    }
    private void buildWowToggles(){
        if(wowToggles==null)return;
        wowToggles.removeAllViews();
        for(int i=0;i<2;i++){
            final boolean only=i==1;
            boolean selected=wowOnlyLive==only;
            TextView label=TransitWowUi.type(this,only?"Tylko LIVE":"Wszystkie",
                15,true,selected?0xFFFFFFFF:TransitWowUi.ink(this));
            label.setGravity(Gravity.CENTER);
            label.setMinHeight(dp(44));
            label.setPadding(dp(11),dp(9),dp(11),dp(9));
            boolean dark=(getResources().getConfiguration().uiMode&
                Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
            label.setBackground(TransitWowUi.round(this,
                selected?0xFF15805B:(dark?0xFF293645:0xFFF1F4F7),
                selected?0xFF15805B:(dark?0xFF4D5D6B:0xFFDAE2E9),12));
            label.setClickable(true);label.setFocusable(true);
            label.setContentDescription("Filtr odjazdów: "+
                (only?"tylko z aktualnym potwierdzeniem LIVE":"wszystkie nadchodzące"));
            label.setOnClickListener(v->{
                wowOnlyLive=only;
                buildWowToggles();
                refreshWowRows();
            });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(46),1f);
            if(i>0)lp.leftMargin=dp(7);
            wowToggles.addView(label,lp);
        }
    }

    private void refreshWowRows(){''',"toggle and favorites controls")

once('''        int shown=0;
        GtfsNearby.Departure first=null;
        for(GtfsNearby.Departure d:wowData.departures){''',
'''        int shown=0;
        GtfsNearby.Departure first=null;
        long now=System.currentTimeMillis();
        List<GtfsNearby.Departure> sorted=new ArrayList<>(wowData.departures);
        sorted.sort(Comparator.comparingLong(d->TransitWowUi.expected(d,now)));
        for(GtfsNearby.Departure d:sorted){''',"sort by estimated real departure")

once('''            if(!q.isEmpty()&&!d.line.toLowerCase(Locale.ROOT).contains(q)&&
               !d.headsign.toLowerCase(Locale.ROOT).contains(q))continue;
            if(shown++==0)first=d;''',
'''            if(!q.isEmpty()&&!d.line.toLowerCase(Locale.ROOT).contains(q)&&
               !d.headsign.toLowerCase(Locale.ROOT).contains(q))continue;
            if(!TransitWowUi.shouldDisplay(d,now,wowOnlyLive))continue;
            if(shown++==0)first=d;''',"actual finished-departure visibility")

once('''            q.isEmpty()?"Brak najbliższych odjazdów z wybranego przystanku.":
                "Nie znaleziono kursów tej linii lub kierunku.",''',
'''            wowOnlyLive?"Brak świeżych prognoz LIVE dla tego przystanku.":
                (q.isEmpty()?"Brak nadchodzących odjazdów z wybranego przystanku.":
                "Nie znaleziono nadchodzących kursów tej linii lub kierunku."),''',
    "empty state with active-only mode")

once('''                for(int i=0;i<countdownLabels.size()&&i<wowRows.size();i++)
                    TransitWowUi.update(wowRows.get(i),countdownLabels.get(i).departure);
                if(wowGps!=null){''',
'''                // Rebuild the sorted list after a live feed: only fresh GPS can keep
                // already scheduled departures visible when legitimately late.
                refreshWowRows();
                if(wowGps!=null){''',"new delayed arrivals appear in cards")

once('''        if(wowMode)return;
        // Recompute the hero when departure times pass or an update expires.''',
'''        if(wowMode){
            for(CountdownLabel label:countdownLabels){
                if(!TransitWowUi.shouldDisplay(label.departure,now,wowOnlyLive)){
                    refreshWowRows();
                    break;
                }
            }
            return;
        }
        // Recompute the hero when departure times pass or an update expires.''',
    "remove departed cards automatically on countdown tick")

p.write_text(s,encoding="utf-8")
print("PASS: real-time sorted departures, past ones hidden, favorite stop and LIVE-only mode")
