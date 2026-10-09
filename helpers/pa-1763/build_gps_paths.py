#!/usr/bin/env python3
"""Create a bounded compact GTFS vehicle/stop matcher from the bundled Warsaw timetable.

No network/key. Live data always fetched afresh on phone. This index only holds
public scheduled trip paths and coordinates for matching live trip starts.
"""
from pathlib import Path
import csv, gzip, io, re, zipfile

archive=Path("project/app/src/main/assets/pa_gtfs/warsaw-lite.zip")
dest=Path("project/app/src/main/assets/pa_gtfs/warsaw-gps-paths.idx")
dest.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(archive) as z:
    def rows(name):
        with z.open(name) as stream:
            yield from csv.DictReader(io.TextIOWrapper(stream,encoding="utf-8-sig",newline=""))
    routes={r["route_id"]:r.get("route_short_name","") for r in rows("routes.txt")}
    coords={r["stop_id"]:(r.get("stop_lat",""),r.get("stop_lon",""))
            for r in rows("stops.txt")}
    trips={r["trip_id"]:(routes.get(r.get("route_id",""),""),r.get("service_id","")[:10])
           for r in rows("trips.txt")}
    count=0
    with gzip.open(dest,"wt",encoding="utf-8",compresslevel=7,newline="\n") as out:
        trip_id=None
        points=[]
        def flush():
            global count
            if len(points)<2:return
            line,date=trips.get(trip_id,("",""))
            if not line or not re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}",date):return
            seconds=int(points[0].split(",")[3])
            hour=seconds//3600
            if hour>=24:return
            start=f"{hour:02d}{(seconds//60)%60:02d}"
            out.write(f"{trip_id}\t{line}\t{date}\t{start}\t{'|'.join(points)}\n")
            count+=1
        for row in rows("stop_times.txt"):
            tid=row["trip_id"]
            if trip_id!=tid:
                if trip_id is not None:flush()
                trip_id=tid;points=[]
            position=coords.get(row.get("stop_id",""))
            if position is None:continue
            time=row.get("departure_time","").split(":")
            if len(time)!=3:continue
            try:
                secs=int(time[0])*3600+int(time[1])*60+int(time[2])
                seq=int(row.get("stop_sequence","-1"))
                float(position[0]);float(position[1])
            except ValueError:continue
            points.append(f"{seq},{position[0]},{position[1]},{secs},{row['stop_id']}")
        if trip_id is not None:flush()
assert count>10000,f"Incomplete GPS path index: {count}"
print(f"PASS: {count} Warsaw scheduled paths, gzip index {dest.stat().st_size} B")
