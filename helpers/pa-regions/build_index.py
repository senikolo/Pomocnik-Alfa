#!/usr/bin/env python3
"""Prepare real, date-scoped Warsaw bus and MLD feeder-bus GTFS indexes.

No inferred trips, synthetic travel times, or simulated LIVE statuses.
Each source is usable only on dates supported by its calendar.
"""
import csv
import io
import json
import sys
import zipfile
import datetime as dt
from collections import defaultdict
from pathlib import Path
from zoneinfo import ZoneInfo

OUT = Path(sys.argv[1] if len(sys.argv) > 1 else "build/pa-regions")
OUT.mkdir(parents=True, exist_ok=True)
SOURCE = Path(sys.argv[2] if len(sys.argv) > 2 else "data/pa-gtfs")
TODAY = dt.datetime.now(ZoneInfo("Europe/Warsaw")).date()
DAYS = [TODAY + dt.timedelta(days=i) for i in range(4)]
WEEKDAYS = ("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")

def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, separators=(",",":")), encoding="utf8")

def date_mask(calendar, overrides, service):
    mask = 0
    for i, day in enumerate(DAYS):
        d = day.strftime("%Y%m%d")
        enabled = False
        rule = calendar.get(service)
        if rule is not None:
            enabled = (rule.get("start_date","99999999") <= d <= rule.get("end_date","00000000")
                and rule.get(WEEKDAYS[day.weekday()], "0") == "1")
        exception = overrides.get((service,d))
        if exception == "1": enabled = True
        if exception == "2": enabled = False
        if enabled: mask |= 1 << i
    return mask

def rows(z, name):
    if name not in z.namelist(): return []
    return csv.DictReader(io.TextIOWrapper(z.open(name), encoding="utf-8-sig", newline=""))

for provider in ("warsaw","mld"):
    with zipfile.ZipFile(SOURCE / (provider+"-lite.zip")) as z:
        available = set(z.namelist())
        calendar = {r["service_id"]:r for r in rows(z,"calendar.txt")}
        overrides = {(r["service_id"],r["date"]):r["exception_type"]
                     for r in rows(z,"calendar_dates.txt")}
        route_rows = [r for r in rows(z,"routes.txt") if r.get("route_type") == "3"]
        route_rows.sort(key=lambda r:(r.get("route_short_name",""),r["route_id"]))
        route_indexes = {r["route_id"]:i for i,r in enumerate(route_rows)}
        stops = []
        stop_idx = {}
        for r in rows(z,"stops.txt"):
            if r.get("stop_id") in stop_idx: continue
            try:
                lat=round(float(r["stop_lat"]),6); lon=round(float(r["stop_lon"]),6)
            except (ValueError,KeyError): lat=lon=0
            stop_idx[r["stop_id"]]=len(stops)
            stops.append([r["stop_id"],r.get("stop_name",""),r.get("stop_code",""),lat,lon,[]])
        trips = {}
        for r in rows(z,"trips.txt"):
            route = route_indexes.get(r.get("route_id",""))
            if route is None: continue
            mask = date_mask(calendar, overrides, r["service_id"])
            if mask:
                trips[r["trip_id"]]=(route,mask,r.get("trip_headsign",""))
        route_trips = defaultdict(dict)
        count = 0
        for r in rows(z,"stop_times.txt"):
            trip = r.get("trip_id")
            info = trips.get(trip)
            if info is None or r.get("stop_id") not in stop_idx: continue
            try:
                h,m,*secs = (int(x) for x in r["departure_time"].split(":"))
                minute = h*60+m
                if not (0 <= minute <= 2880): continue
                sequence=int(r["stop_sequence"])
            except (ValueError,KeyError):continue
            ri,mask,headsign=info
            item=route_trips[ri].setdefault(trip,[mask,headsign,[]])
            item[2].append((sequence,stop_idx[r["stop_id"]],minute))
            count+=1
        routes=[]
        lines_per_stop=[set() for _ in stops]
        for ri,r in enumerate(route_rows):
            listed=[]
            for mask,headsign,points in route_trips.get(ri,{}).values():
                points.sort(key=lambda s:s[0])
                pair=[]
                last=-1
                for _,sid,minute in points:
                    if minute < last: continue
                    pair.append([sid,minute])
                    last=minute
                    lines_per_stop[sid].add(ri)
                if len(pair)>=2: listed.append([mask,headsign,pair])
            write(OUT / ("route_%s_%d.json"%(provider,ri)),{"trips":listed})
            routes.append([r["route_id"],r.get("route_short_name",""),r.get("route_long_name","")])
        for i,s in enumerate(stops):s[5]=sorted(lines_per_stop[i])
        covered=[False]*len(DAYS)
        for v in trips.values():
            for i in range(len(DAYS)):
                if v[1] & (1<<i): covered[i]=True
        index={"source":provider,"generated":TODAY.isoformat(),
               "days":[d.isoformat() for d in DAYS],
               "covered":covered,
               "stops":stops,"routes":routes,
               "notice":"Rozkład planowy GTFS, nie pozycja GPS ani opóźnienia LIVE."}
        write(OUT/("index_%s.json"%provider),index)
        print("PASS",provider,"routes",len(routes),"stops",len(stops),"trips",len(trips),
              "stop_times",count,"covered",covered,flush=True)
        if not any(covered):raise SystemExit("No active dates in "+provider+" feed; refusing stale release")
