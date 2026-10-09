#!/usr/bin/env python3
"""Regression checks: fixed PA Transit colors, platform 02, live-only, no ghost buses."""
from pathlib import Path
root=Path("project/app/src/main/java/com/ispina/lokalnie")
ui=(root/"TransitWowUi.java").read_text(encoding="utf-8")
near=(root/"NearbyDeparturesActivity.java").read_text(encoding="utf-8")
manual=(root/"LineDeparturesActivity.java").read_text(encoding="utf-8")
gtfs=(root/"transit/GtfsNearby.java").read_text(encoding="utf-8")
gradle=Path("project/app/build.gradle").read_text(encoding="utf-8")
assert "BUS=0xFF2463A6" in ui
assert "TRAM=0xFFB94E48" in ui
assert "RAIL=0xFF704FA0" in ui
assert "METRO=0xFF087E79" in ui
assert "GREEN=0xFF15805B" in ui
assert "AMBER=0xFFB86A18" in ui
assert '"AUTOBUS"' in ui and '"TRAMWAJ"' in ui and '"KOLEJ"' in ui and '"METRO"' in ui
assert '"🚌"' in ui and '"🚋"' in ui and '"🚆"' in ui and '"🚇"' in ui
assert 'stop.id.substring(4)' in ui
assert 'name+(number.isEmpty()?"":" "+number)' in ui
assert '" · stan. "' not in gtfs.split("public static final class LineStop",1)[1].split("private static boolean manualProvider",1)[0]
assert 'matches("[0-9]{6}")?id.substring(4)' in gtfs
assert "public static boolean shouldDisplay(" in ui
assert 'd.when<now-10000L && !live(d,now)' in ui
assert 'estimate<now-10000L' in ui
assert 'd.confirmedDelayMinutes(now)' in ui
assert 'd.hasGpsEstimate(now)' in ui
assert 'if(!TransitWowUi.shouldDisplay(d,now,wowOnlyLive))continue;' in near
assert 'sorted.sort(Comparator.comparingLong(d->TransitWowUi.expected(d,now)))' in near
assert "wowOnlyLive" in near and "Tylko LIVE" in near
assert "pa_transit_favourite" in near
assert "requestGpsEstimates(data)" in near
assert "WarsawGpsEta.apply(" in near
assert 'TransitWowUi.shouldDisplay(departure,now,false)' in manual
assert 'renderWow(loaded,wowLastLine,wowLastStop,true)' in manual
assert 'if(auto)fetchWowGps(selected,state,serial);' in manual
assert 'if(when<at-25L*60000L' in gtfs
assert 'if(departure<moment-25L*60000L' in gtfs
assert 'versionCode 10765' in gradle
assert "versionName '1.7.64-A10'" in gradle
print("PASS: PA Transit four fixed colors, green GPS LIVE, amber late, full 02, sorted fresh/live only, favorite, expired departure hidden")
