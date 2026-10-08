#!/usr/bin/env python3
"""Daily compact rolling GTFS: preserve rider-facing stop codes and route types."""
from __future__ import annotations
import csv,io,zipfile,requests,datetime
from pathlib import Path

SOURCES={
    "warsaw":"https://mkuran.pl/gtfs/warsaw.zip",
    "mld":"https://www.kolejemalopolskie.com.pl/rozklady_jazdy/ald-gtfs.zip",
}
OUT=Path("data/pa-gtfs");OUT.mkdir(parents=True,exist_ok=True)
now=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=2))).date()
dates={(now+datetime.timedelta(days=d)).strftime("%Y%m%d") for d in (-1,0,1,2)}
HEADERS={
 "stops.txt":["stop_id","stop_code","stop_name","stop_lat","stop_lon"],
 "routes.txt":["route_id","route_short_name","route_long_name","route_type"],
 "trips.txt":["trip_id","route_id","service_id","trip_headsign"],
 "stop_times.txt":["trip_id","stop_id","stop_sequence","departure_time"],
 "calendar.txt":["service_id","monday","tuesday","wednesday","thursday","friday","saturday","sunday","start_date","end_date"],
 "calendar_dates.txt":["service_id","date","exception_type"],
}
def read_rows(z,name):
    if name not in z.namelist():return iter(())
    raw=io.TextIOWrapper(z.open(name),encoding="utf-8-sig",newline="")
    return csv.DictReader(raw)
for provider,url in SOURCES.items():
    print("FETCH",provider,url,flush=True)
    response=requests.get(url,timeout=105,headers={"User-Agent":"PomocnikAlfa/1.7.49 (+https://github.com/senikolo/Pomocnik-Alfa)"})
    response.raise_for_status()
    assert response.content[:2]==b"PK",(provider,response.content[:100])
    with zipfile.ZipFile(io.BytesIO(response.content)) as src:
        available={Path(x).name:x for x in src.namelist()}
        assert set(["stops.txt","routes.txt","trips.txt","stop_times.txt"]).issubset(available)
        calendarrows=list(read_rows(src,available["calendar.txt"])) if "calendar.txt" in available else []
        daterows=list(read_rows(src,available["calendar_dates.txt"])) if "calendar_dates.txt" in available else []
        active=set()
        for row in calendarrows:
            if row.get("start_date","99999999")<=max(dates) and row.get("end_date","00000000")>=min(dates):
                active.add(row["service_id"])
        for row in daterows:
            if row.get("date") in dates and row.get("exception_type")=="1":active.add(row["service_id"])
        trips={}
        rows={}
        # Train-only source missing days must never create fake dates.
        for file in ("stops.txt","routes.txt","calendar.txt","calendar_dates.txt"):
            if file in available:
                rows[file]=list(read_rows(src,available[file]))
        for idx,trip in enumerate(read_rows(src,available["trips.txt"])):
            if trip.get("service_id") not in active:continue
            trips[trip["trip_id"]]=str(len(trips)+1)
        print(provider,"filtered_trips",len(trips),flush=True)
        # Stream output ZIP to prevent multi-million-row data in RAM
        out=OUT/(provider+"-lite.zip")
        with zipfile.ZipFile(out,"w",compression=zipfile.ZIP_DEFLATED,compresslevel=8,allowZip64=True) as target:
            for file in ("stops.txt","routes.txt","calendar.txt","calendar_dates.txt"):
                if file not in available:continue
                buf=io.StringIO(newline="");w=csv.DictWriter(buf,fieldnames=HEADERS[file],lineterminator="\n")
                w.writeheader()
                for row in rows[file]:
                    if file=="stops.txt" and row.get("location_type","") not in ("","0"):continue
                    if file=="calendar_dates.txt" and row.get("date") not in dates:continue
                    w.writerow({c:row.get(c,"") for c in HEADERS[file]})
                target.writestr(file,buf.getvalue().encode("utf-8"))
            with target.open("trips.txt","w",force_zip64=True) as dst:
                dst.write((",".join(HEADERS["trips.txt"])+"\n").encode())
                for trip in read_rows(src,available["trips.txt"]):
                    numeric=trips.get(trip.get("trip_id",""))
                    if numeric is None:continue
                    line=[numeric,trip.get("route_id",""),trip.get("service_id",""),trip.get("trip_headsign","")]
                    buf=io.StringIO(newline="");csv.writer(buf,lineterminator="\n").writerow(line)
                    dst.write(buf.getvalue().encode("utf-8"))
            with target.open("stop_times.txt","w",force_zip64=True) as dst:
                dst.write((",".join(HEADERS["stop_times.txt"])+"\n").encode())
                n=0
                for stop in read_rows(src,available["stop_times.txt"]):
                    numeric=trips.get(stop.get("trip_id",""))
                    if numeric is None or stop.get("pickup_type","")=="1":continue
                    line=[numeric,stop.get("stop_id",""),stop.get("stop_sequence",""),stop.get("departure_time","")]
                    dst.write((",".join(line)+"\n").encode("utf-8"))
                    n+=1
                    if n%1000000==0:print(provider,"stop_times_kept",n,flush=True)
        print(provider,"selected_stop_times",n,"compressed_bytes",out.stat().st_size,flush=True)
        assert out.stat().st_size>5000 and out.stat().st_size<29_000_000,(provider,out.stat().st_size)
