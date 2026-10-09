#!/usr/bin/env python3
"""PA 1.7.65 regression: direction chooser, high contrast, accurate GPS selection."""
from pathlib import Path
import gzip

root=Path("project/app/src/main/java/com/ispina/lokalnie")
near=(root/"NearbyDeparturesActivity.java").read_text(encoding="utf-8")
manual=(root/"LineDeparturesActivity.java").read_text(encoding="utf-8")
ui=(root/"TransitWowUi.java").read_text(encoding="utf-8")
cls=(root/"transit/FastLineDirections.java").read_text(encoding="utf-8")
assert "GOOD_FIX_METRES=150f" in near
assert "MAX_POSITION_AGE=90000L" in near
assert "MAX_SAVED_FIX_AGE=45L*60000L" in near
assert 'fix.getAccuracy()<700f' not in near.split("private void consider(Location fix)",1)[1].split("private void stopGps()",1)[0]
assert "fix.getAccuracy()>GOOD_FIX_METRES" in near
assert "if(inWarsaw && stop.distance>1200)continue;" in near
assert "Twoja lokalizacja: ±" in near
assert "Wyszukaj linię" in near
assert '"pa_last_transit_fix"' in near
assert "KIERUNEK JAZDY →" in manual
assert "FastLineDirections.forLine(" in manual
assert "applyDirection(int index)" in manual
assert "choice.stops.containsKey(candidate.id)" in manual
assert "!direction.equalsIgnoreCase(d.headsign)" in manual
assert "Kierunek → "+ " +choice.name" not in manual  # phrasing check below
assert "Kierunek → " in manual
assert 'provider+"-directions.tsv"' in cls
assert "split" in cls
assert '0xFFF7FAFD' in ui and '0xFFFFFAF9' in ui
assert '0xFFFBF9FF' in ui and '0xFFF5FCFB' in ui
assert "GREEN_DARK=0xFF80F0C3" in ui
assert 'type(a,stopName(d.stop),16,true,ink(a))' in ui
assert "versionCode 10766" in Path("project/app/build.gradle").read_text()
for p in ("warsaw","mld"):
    index=Path("project/app/src/main/assets/pa_gtfs")/(p+"-directions.tsv.gz")
    assert index.exists()
    names=set();count=0
    with gzip.open(index,"rt",encoding="utf-8") as f:
        for row in f:
            cols=row.rstrip("\n").split("\t")
            assert len(cols)==4 and cols[2] and cols[1]
            names.add(cols[0]);count+=1
    assert count>1000,(p,count)
    if p=="warsaw":assert "517" in names,(p,len(names))
    if p=="mld":assert "A7" in names,(p,len(names))
    print(f"PASS: {p} indexed directional platforms {count}; lines {len(names)}")
print("PASS: GPS precision filter, high contrast, mode colors, direction -> stops -> matching departures")
