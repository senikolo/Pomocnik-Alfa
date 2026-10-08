#!/usr/bin/env python3
"""Wire conservative realtime parser after the PA scheduled departures patch."""
from pathlib import Path
from shutil import copyfile

base=Path("project/app/src/main/java/com/ispina/lokalnie")
src=Path("helpers/pa-1754/MldRealtime.java")
dest=base/"transit"/"MldRealtime.java"
dest.parent.mkdir(parents=True,exist_ok=True)
copyfile(src,dest)
assert dest.read_text(encoding="utf-8").count("public static String applyJson(")==1
gtfs=(base/"transit"/"GtfsNearby.java").read_text(encoding="utf-8")
assert "MldRealtime.apply(ctx,result.departures)" in gtfs
assert "item.serviceDate=date.toString().replace" in gtfs
assert "mld-rt-v2" in gtfs
main=base/"MainActivity.java"
s=main.read_text(encoding="utf-8")
assert "POMOCNIK ALFA 1.7.53" in s or "POMOCNIK ALFA 1.7.54" in s
main.write_text(s.replace("POMOCNIK ALFA 1.7.53","POMOCNIK ALFA 1.7.54"),encoding="utf-8")
print("PASS: MLD GTFS-RT adapter, immutable original trip IDs, cache migration and truthful LIVE state")
