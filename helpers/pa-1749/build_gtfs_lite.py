#!/usr/bin/env python3
"""Build small GTFS archives from real WTP/MLD timetable sources.
Never fabricate stops or departures. Commit daily refresh via GitHub Actions.
"""
from __future__ import annotations
import csv,io,os,sys,time,zipfile,requests
from pathlib import Path
SOURCES={
 "warsaw":"https://mkuran.pl/gtfs/warsaw.zip",
 "mld":"https://www.kolejemalopolskie.com.pl/rozklady_jazdy/ald-gtfs.zip",
}
COLUMNS={
 "stops.txt":["stop_id","stop_name","stop_lat","stop_lon","location_type","parent_station"],
 "routes.txt":["route_id","route_short_name","route_long_name"],
 "trips.txt":["trip_id","route_id","service_id","trip_headsign"],
 "stop_times.txt":["trip_id","stop_id","stop_sequence","departure_time","pickup_type"],
 "calendar.txt":["service_id","monday","tuesday","wednesday","thursday","friday","saturday","sunday","start_date","end_date"],
 "calendar_dates.txt":["service_id","date","exception_type"],
}
out=Path("data/pa-gtfs");out.mkdir(parents=True,exist_ok=True)
for name,url in SOURCES.items():
 print("Downloading",name,url,flush=True)
 res=requests.get(url,timeout=100,headers={"User-Agent":"PomocnikAlfa/1.7.49 https://github.com/senikolo/Pomocnik-Alfa"})
 res.raise_for_status()
 assert res.content[:2]==b"PK",f"Not a GTFS ZIP: {url} {res.content[:80]!r}"
 assert len(res.content)<180_000_000
 path=out/f"{name}-lite.zip"
 with zipfile.ZipFile(io.BytesIO(res.content)) as src,zipfile.ZipFile(path,"w",compression=zipfile.ZIP_DEFLATED,compresslevel=8) as dest:
  names={Path(n).name:n for n in src.namelist()}
  for file,cols in COLUMNS.items():
   if file not in names:
    if file in ("calendar.txt","calendar_dates.txt"):continue
    raise RuntimeError(f"{name}: no {file}")
   data=src.read(names[file])
   reader=csv.DictReader(io.TextIOWrapper(io.BytesIO(data),encoding="utf-8-sig",newline=""))
   missing=set(cols)-set(reader.fieldnames or ())
   # Extra optional columns can be absent (e.g., pickup_type).
   required={"stop_id","stop_name","stop_lat","stop_lon"} if file=="stops.txt" else (
      {"trip_id","stop_id","stop_sequence","departure_time"} if file=="stop_times.txt" else
      {"trip_id","route_id","service_id"} if file=="trips.txt" else
      {"route_id"} if file=="routes.txt" else {"service_id"})
   if missing & required:raise RuntimeError(f"{name}: missing {file}: {missing & required}")
   buf=io.StringIO(newline="")
   writer=csv.DictWriter(buf,fieldnames=cols,lineterminator="\n")
   writer.writeheader()
   count=0
   for row in reader:
    if file=="stops.txt" and row.get("location_type") not in (None,"","0"):continue
    if file=="stop_times.txt" and row.get("pickup_type","")=="1":continue
    writer.writerow({c:row.get(c,"") or "" for c in cols})
    count+=1
   dest.writestr(file,buf.getvalue().encode("utf-8"))
   print(name,file,count,flush=True)
 print(name,"output_bytes",path.stat().st_size,flush=True)
 assert path.stat().st_size>5000 and path.stat().st_size<28_000_000
