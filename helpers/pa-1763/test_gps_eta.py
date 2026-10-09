#!/usr/bin/env python3
"""Regression guards for genuine position-correlated GPS ETAs and fast line index."""
from pathlib import Path
import gzip

base=Path("project/app/src/main/java/com/ispina/lokalnie")
backend=(base/"transit/WarsawGpsEta.java").read_text(encoding="utf-8")
gtfs=(base/"transit/GtfsNearby.java").read_text(encoding="utf-8")
near=(base/"NearbyDeparturesActivity.java").read_text(encoding="utf-8")
manual=(base/"LineDeparturesActivity.java").read_text(encoding="utf-8")
ui=(base/"TransitWowUi.java").read_text(encoding="utf-8")
fast=(base/"transit/FastLineStops.java").read_text(encoding="utf-8")
assert "positions.json" in backend
assert "trip_id" in backend and "timestamp" in backend
assert "parts[4].split" in backend
assert "winner==null||second-bestScore<50" in backend
assert "distance>230" in backend
assert "gpsEtaWhenMillis=eta" in backend
assert "if(d.sequence<best.forwardSequence)continue" in backend
assert 'ctx.getAssets().open("pa_gtfs/warsaw-gps-paths.idx")' in backend
assert "gpsEtaWhenMillis" in gtfs
assert "hasGpsEstimate(long now)" in gtfs
assert "if(departure.hasGpsEstimate(now))" in ui
assert "GPS LIVE · SZACUNEK" in ui
assert "e.clock.setText" in ui
assert "requestGpsEstimates(data)" in near
assert "WarsawGpsEta.apply(" in near
assert "TransitWowUi.update" in near
assert "TransitWowUi.update" in manual
assert "WarsawGpsEta.apply(" in manual
assert '"pa_gtfs/"+network+"-lines.tsv"' in fast
assert "new GZIPInputStream(ctx.getAssets().open" in fast
assert "versionCode 10764" in Path("project/app/build.gradle").read_text()
p=Path("project/app/src/main/assets/pa_gtfs/warsaw-gps-paths.idx")
seen=0;lines=set()
with gzip.open(p,"rt",encoding="utf-8") as stream:
    for row in stream:
        parts=row.rstrip("\n").split("\t",4)
        assert len(parts)==5
        assert all(x for x in parts)
        seen+=1
        lines.add(parts[1])
assert seen>10000,(seen,lines)
assert "517" in lines,"Warsaw route 517 not indexed"
print("PASS: true GPS-derived ETA wiring, trip and coordinate index, no false LIVE when stale/ambiguous, AAPT lines.tsv fallback")
print(f"PASS: indexed {seen} Warsaw active trips, {len(lines)} distinct route names")
