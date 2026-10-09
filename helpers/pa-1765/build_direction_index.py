#!/usr/bin/env python3
"""Build direction (trip_headsign) -> served platforms for PA manual lookup.

The resulting asset is bundled in the APK and fast to search offline.
"""
from pathlib import Path
from collections import defaultdict
import csv,gzip,io,re,zipfile
out=Path("project/app/src/main/assets/pa_gtfs")
out.mkdir(parents=True,exist_ok=True)
def rows(z,name):
    with z.open(name) as f:
        yield from csv.DictReader(io.TextIOWrapper(f,encoding="utf-8-sig",newline=""))
def clean(value):
    return " ".join((value or "").replace("\t"," ").replace("\n"," ").split())
for provider in ("warsaw","mld"):
    with zipfile.ZipFile(Path("data/pa-gtfs")/(provider+"-lite.zip")) as z:
        routes={}
        for r in rows(z,"routes.txt"):
            name=clean(r.get("route_short_name")) or clean(r.get("route_long_name"))
            if re.fullmatch("[A-Za-z0-9]{1,8}",name):
                routes[r.get("route_id")]=name.upper()
        trip={}
        for t in rows(z,"trips.txt"):
            line=routes.get(t.get("route_id"))
            headsign=clean(t.get("trip_headsign"))
            if line and headsign and clean(t.get("trip_id")):
                trip[t["trip_id"]]=(line,headsign)
        mapping={}
        for r in rows(z,"stop_times.txt"):
            pair=trip.get(r.get("trip_id"))
            if not pair:continue
            sid=clean(r.get("stop_id"))
            if not sid:continue
            try:seq=int(r.get("stop_sequence") or 9999)
            except ValueError:continue
            key=(*pair,sid)
            if seq<mapping.get(key,999999):mapping[key]=seq
    assert len(mapping)>500,(provider,len(mapping))
    target=out/(provider+"-directions.tsv.gz")
    with gzip.open(target,"wt",encoding="utf-8",newline="\n",compresslevel=9) as f:
        for (line,direction,sid),seq in sorted(mapping.items()):
            f.write(f"{line}\t{direction}\t{sid}\t{seq}\n")
    print("PASS:",provider,len(mapping),"direction/stop entries,",target.stat().st_size,"compressed bytes")
