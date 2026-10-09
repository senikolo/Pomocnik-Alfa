#!/usr/bin/env python3
"""Smoke audit modern screen: UI semantics and no buttons linking to competing apps."""
from pathlib import Path
p=Path("project/app/src/main/java/com/ispina/lokalnie")
cards=(p/"TransitWowUi.java").read_text(encoding="utf-8")
near=(p/"NearbyDeparturesActivity.java").read_text(encoding="utf-8")
manual=(p/"LineDeparturesActivity.java").read_text(encoding="utf-8")
gd=(p/"transit/GtfsNearby.java").read_text(encoding="utf-8")
assert 'KIERUNEK  →' in cards
assert 'setMaxLines(3)' in cards
assert 'd.headsign' in cards
assert 'd.line' in cards
assert '"ODJAZD"' in cards
assert 'DepartureCountdown.label(' in cards
assert 'ROZKŁADOWO' in cards
assert 'TextUtils.TruncateAt.END' in cards
assert 'setContentDescription("Kierunek "' in cards
assert "renderWow(found,fix)" in near
assert "renderWow(data,line,stopName)" in manual
assert "addWowStopTabs" in near
assert "if(unique.size()==4)break" in near
assert "refreshWowRows" in near
assert "gpsWorker.execute" in near
assert "WarsawVehicleGps.load" in near
assert "getSharedPreferences(\"pa_last_transit_fix\"" in near
assert "FastLineStops.forLine" in gd
assert "showWowGps" in manual
assert "fetchWowGps" in manual
near_active=near.split("private void renderWow(",1)[1].split("private void render(GtfsNearby.Result",1)[0]
manual_active=manual.split("private void renderWow(",1)[1].split("private void render(GtfsNearby.Result",1)[0]
for name,active in [("nearby",near_active),("manual",manual_active)]:
    assert "ACTION_VIEW" not in active,(name,"outbound VIEW")
    assert "time4bus" not in active,(name,"outbound")
    assert "Czynaczas" not in active,(name,"external")
    assert "https://" not in active,(name,"links")
assert "versionCode 10763" in Path("project/app/build.gradle").read_text(encoding="utf-8")
print("PASS: PA 1.7.62 passenger-first cards, 3-line directions, 4 stops, integrated LIVE without 3rd-party app links")
