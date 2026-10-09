#!/usr/bin/env python3
"""PA 1.7.61 regression: off-line indexes, four ranked stops, indoor fallback and LIVE."""
from pathlib import Path
import gzip,zipfile
base=Path("project/app/src/main/java/com/ispina/lokalnie")
near=(base/"NearbyDeparturesActivity.java").read_text(encoding="utf-8")
gtfs=(base/"transit/GtfsNearby.java").read_text(encoding="utf-8")
line=(base/"LineDeparturesActivity.java").read_text(encoding="utf-8")
fast=(base/"transit/FastLineStops.java").read_text(encoding="utf-8")
assert "chooseBestFour(result,moment)" in gtfs
assert "Math.min(4,names.size())" in gtfs
assert "platformsByName.size()>=8" in gtfs
assert "result.departures.removeIf" in gtfs
assert "groups.size()>=4" in near
assert "Cztery przydatne przystanki" in near
assert "getSharedPreferences(\"pa_last_transit_fix\"" in near
assert "useSavedFix()" in near
assert "ui.postDelayed(timeout,6500)" in near
assert "LocationManager.NETWORK_PROVIDER.equals" in near
assert "showInlineVehicleLive(value)" in near
assert "WarsawVehicleGps.load" in near
assert "Nie zastępują godziny rozkładowej" in near
assert 'GPS LIVE · pojazdy linii "+d.line' not in near
assert "FastLineStops.forLine(ctx,provider,line)" in gtfs
assert "ctx.getAssets().open(\"pa_gtfs/\"+provider+\"-lite.zip\")" in gtfs
assert 'ctx.getAssets().open("pa_gtfs/"+network+"-lines.tsv.gz")' in fast
assert "private void filterStops()" in line and "visibleStations" in line
assert "visibleStations.get(position).id" in line
assert 's.replaceAll("\\\\p{M}+"' in line
assert "versionCode 10762" in Path("project/app/build.gradle").read_text(encoding="utf-8")
for name in ("warsaw","mld"):
    p=Path(f"project/app/src/main/assets/pa_gtfs/{name}-lines.tsv.gz")
    with gzip.open(p,"rt",encoding="utf-8") as handle:
        data=[x.rstrip("\n").split("\t") for x in handle]
    assert len(data)>50,(name,len(data))
    assert all(len(x)==5 for x in data)
    assert all(x[0] and x[1] and x[3] for x in data)
    with zipfile.ZipFile(f"project/app/src/main/assets/pa_gtfs/{name}-lite.zip") as z:
        assert {"routes.txt","stops.txt","stop_times.txt","trips.txt"}.issubset(z.namelist())
    print(f"PASS: {name} offline index, {len(data)} line-stop records")
print("PASS: 4 useful stops, indoor fallback, fast manual lookup, compact on-screen city vehicle LIVE")
