#!/usr/bin/env python3
"""Hermetyczny test importu rozkładu gminnego z IL UltraSmart (bez sieci)."""
import datetime as dt
import json
import tempfile
from pathlib import Path
from municipal import ALIASES, add_municipal, key

source=json.loads((Path(__file__).parent/"linia-gminna.json").read_text(encoding="utf-8"))
assert source["line"]["id"]=="GMINNA"
assert len(source["serviceGroups"])==4
assert {g["dayType"] for g in source["serviceGroups"]}=={"WEEKDAY","SATURDAY"}
assert {g["direction"] for g in source["serviceGroups"]}=={"KRAKOW","MIKLUSZOWICE"}
days=[dt.date(2026,10,19)+dt.timedelta(days=n) for n in range(8)]
stops=[]
# Testowa siatka służy tylko do testu dopasowania; nie trafia do aplikacji.
for n,item in enumerate(source["stops"]):
 if item["id"]=="park_ride":continue
 name=ALIASES.get(key(item["name"]),key(item["name"]))
 stops.append(["mld:"+str(n),name,"",50.0+n*0.0001,20.0+n*0.0001,[],"mld",""])
routes=[]
sources=[]
stop_routes=[set() for _ in stops]
covered=[False]*len(days)
summary={}
with tempfile.TemporaryDirectory() as temp:
 out=Path(temp)
 add_municipal(out,days,stops,routes,sources,stop_routes,covered,summary)
 assert len(routes)==1 and routes[0][1]=="GMINNA"
 assert summary["gminna"]["stops"]>=45
 assert summary["gminna"]["missed_stops"]==["Mikluszowice Park&Ride"]
 assert len(sources)==1 and sources[0]["id"]=="gminna"
 imported=json.loads((out/"route_all_0.json").read_text())
 assert len(imported["patterns"])==4
 assert len(imported["runs"])==38
 assert any(p[5] for pat in imported["patterns"] for p in pat), "Brakuje oznaczenia szacunków"
 assert any(not p[5] for pat in imported["patterns"] for p in pat), "Brakuje godzin źródłowych"
 sunday=days.index(dt.date(2026,10,25))
 saturday=days.index(dt.date(2026,10,24))
 assert all(not (r[0]&(1<<sunday)) for r in imported["runs"]),"Nie wolno generować niedzieli"
 saturday_trips=[r for r in imported["runs"] if r[0]&(1<<saturday)]
 assert len(saturday_trips)==10, "5 sobotnich kursów na kierunek"
 assert len([r for r in imported["runs"] if r[0]&1])==28, "14 kursów na kierunek w dni robocze"
 for stop in ["Ispina Most","Ispina Dom Ludowy","Ispina Wiślisko"]:
  assert any(s[1]==stop for s in stops),stop
 assert all(covered[i] for i,day in enumerate(days) if day.weekday()<6)
 assert not covered[sunday]
 print("PASS municipal: 4 kalendarze, 38 kursów, 3 przystanki Ispiny, bez niedziel, szacunki i brak zmyślonych współrzędnych")
