#!/usr/bin/env python3
"""Build tiny, indexed and offline stop lists from the exact packaged GTFS feeds.

Run during CI, never on the phone. Each line is a route name, platform stop ID,
minimum stop sequence, passenger name and platform label.
"""
from pathlib import Path
import csv
import gzip
import io
import re
import zipfile
from collections import defaultdict

SOURCE=Path("data/pa-gtfs")
OUT=Path("project/app/src/main/assets/pa_gtfs")
OUT.mkdir(parents=True,exist_ok=True)
def rows(z,name):
    with z.open(name) as stream:
        reader=csv.DictReader(io.TextIOWrapper(stream, encoding="utf-8-sig",newline=""))
        for row in reader:
            yield row

def clean(v):
    return (v or "").replace("\t"," ").replace("\r"," ").replace("\n"," ").strip()

for provider in ("warsaw","mld"):
    archive=SOURCE/f"{provider}-lite.zip"
    assert archive.is_file(),archive
    with zipfile.ZipFile(archive) as z:
        route_names=defaultdict(set)
        for r in rows(z,"routes.txt"):
            rid=clean(r.get("route_id"))
            name=clean(r.get("route_short_name")) or clean(r.get("route_long_name"))
            if rid and name and re.fullmatch(r"[A-Za-z0-9]{1,8}",name):
                route_names[rid].add(name.upper())
        by_trip={}
        for t in rows(z,"trips.txt"):
            rid=clean(t.get("route_id"))
            if rid in route_names:
                by_trip[clean(t.get("trip_id"))]=rid
        stop_min={}
        for t in rows(z,"stop_times.txt"):
            rid=by_trip.get(clean(t.get("trip_id")))
            if rid is None:continue
            sid=clean(t.get("stop_id"))
            if not sid:continue
            seq=int(t.get("stop_sequence") or 999999)
            for name in route_names[rid]:
                key=(name,sid)
                if seq<stop_min.get(key,999999):
                    stop_min[key]=seq
        ids={sid for _,sid in stop_min}
        stops={}
        for s in rows(z,"stops.txt"):
            sid=clean(s.get("stop_id"))
            if sid not in ids:continue
            name=clean(s.get("stop_name"))
            if not name:continue
            code=clean(s.get("stop_code"))
            if not code and provider=="warsaw" and re.fullmatch("[0-9]{6}",sid):
                code=sid[-2:]
            stops[sid]=(name,code)
    records=[]
    for (line,sid),seq in stop_min.items():
        if sid in stops:
            label,code=stops[sid]
            records.append((line,seq,label,code,sid))
    records.sort(key=lambda r:(r[0],r[1],r[2].casefold(),r[3],r[4]))
    target=OUT/f"{provider}-lines.tsv.gz"
    with gzip.open(target,"wt",encoding="utf-8",newline="\n",compresslevel=9) as f:
        for line,seq,name,code,sid in records:
            f.write(f"{line}\t{sid}\t{seq}\t{name}\t{code}\n")
    copied=OUT/f"{provider}-lite.zip"
    copied.write_bytes(archive.read_bytes())
    print(f"{provider}: {len(records)} indexed stops; {target.stat().st_size:,} B index, {copied.stat().st_size:,} B offline GTFS")
