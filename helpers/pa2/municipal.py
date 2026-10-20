"""Importuj rzeczywisty rozkład gminny z modelu Ispina Lokalnie.
Nie twórz fikcyjnych lokalizacji: przystanek musi mieć odpowiednik w źródłowym GTFS.
Interpolowane godziny są oznaczane jako szacunkowe, nigdy jako GPS LIVE.
"""
import datetime as dt
import hashlib
import json
import re
import unicodedata
from pathlib import Path

HERE=Path(__file__).parent
ALIASES={
 "ispina most":"ispina",
 "nowe brzesko":"nowe brzesko rynek",
 "gawlowek dom ludowy":"gawlowek",
 "rondo kocmyrzowskie":"rondo kocmyrzowskie im ks gorzelanego",
 "plac centralny":"plac centralny im r reagana",
 "brama 5":"brama nr 5",
 "szymanskiego":"szymanskiego 01",
 "wyciaska":"wyciaska 01",
 "wyciaze polnoc":"wyciaze polnoc 01",
 "pobiednik wielki iii komora":"pobiednik wielki komora",
 "pobiednik wielki ii":"pobiednik wielki kolonia",
 "pobiednik wielki i brzostek":"pobiednik wielki brzostek",
 "wawrzenczyce":"wawrzenczyce cmentarz",
 "wawrzenczyce ii":"wawrzenczyce centrum",
 "wawrzenczyce iii":"wawrzenczyce wygon",
 "wawrzenczyce iv":"wawrzenczyce zabudowanie",
 "wawrzenczyce v":"wawrzenczyce granica",
 "zielona":"zielona dw965",
}

def key(value):
 text=unicodedata.normalize("NFD",str(value).lower())
 text="".join(ch for ch in text if unicodedata.category(ch)!="Mn").replace("ł","l")
 text=re.sub(r"[^a-z0-9]+"," ",text).strip()
 return re.sub(r"\s+"," ",re.sub(r"^krakow ","",text))

def seconds(value):
 h,m=map(int,value.split(":"))
 if not 0<=m<60 or not 0<=h<48:raise ValueError("Nieprawidłowa godzina gminna")
 return 3600*h+60*m

def add_municipal(out,days,stops,routes,sources,stop_routes,covered,summary):
 path=HERE/"linia-gminna.json"
 data=json.loads(path.read_text(encoding="utf-8"))
 assert data["line"]["id"]=="GMINNA" and len(data["serviceGroups"])==4
 if dt.date.fromisoformat(data["validFrom"])>days[-1]:
  return
 # Wykorzystujemy WYŁĄCZNIE współrzędne już opublikowane w pobranych GTFS.
 indexed={}
 for s in stops:
  if s[6] not in ("mld","krakow_bus","krakow_mobilis","krakow_tram"):continue
  indexed.setdefault(key(s[1]),[]).append(s)
 aliases={}
 for item in data["stops"]:
  lookup=ALIASES.get(key(item["name"]),key(item["name"]))
  options=indexed.get(lookup,[])
  # Najpierw przewoźnik MLD (lokalne przystanki), potem miejski GTFS.
  options.sort(key=lambda s:(s[6]!="mld",s[0]))
  if options:aliases[item["id"]]=options[0]
 important=("ispina_ii","ispina_iii","ispina_iv")
 found=[s for s in important if s in aliases]
 if not found:
  print("GMINNA pominięta: brak lokalnego GTFS ze współrzędnymi Ispiny",flush=True)
  return
 if len(found)!=len(important):
  raise ValueError("GMINNA: niekompletna geolokalizacja trzech przystanków Ispiny")
 rid=len(routes)
 routes.append(["gminna:1","GMINNA",data["line"]["name"],3,data["line"]["operator"],"gminna","Linia gminna"])
 ids={}
 for item in data["stops"]:
  base=aliases.get(item["id"])
  if base is None:continue
  sid=len(stops)
  ids[item["id"]]=sid
  # Opublikowana nazwa z IL, lokalizacja z oryginalnego GTFS.
  stops.append(["gminna:"+item["id"],item["name"],"",base[3],base[4],[],"gminna",""])
  stop_routes.append(set([rid]))
 patterns=[]
 entries=[]
 valid=[False]*len(days)
 for group_idx,group in enumerate(data["serviceGroups"]):
  anchors=set(group["timingQuality"]["officialAnchors"])
  points=[]
  assert len(group["route"])==len(group["offsets"])
  last=-1
  for stop_id,offset in zip(group["route"],group["offsets"]):
   value=int(offset)*60
   if value<last:raise ValueError("GMINNA: cofnięcie czasu między przystankami")
   last=value
   if stop_id in ids:
    # Szóste pole: 1 = szacunek z modelu IL; 0 = godzina źródłowa.
    points.append([ids[stop_id],value,value,0,0,0 if stop_id in anchors else 1])
  if len(points)<2:raise ValueError("GMINNA: za mało potwierdzonych przystanków")
  pattern_id=len(patterns)
  patterns.append(points)
  mask=0
  for i,day in enumerate(days):
   weekday=day.weekday()
   active=day>=dt.date.fromisoformat(data["validFrom"]) and (
    group["dayType"]=="WEEKDAY" and weekday<5 or
    group["dayType"]=="SATURDAY" and weekday==5)
   if active:mask|=1<<i;valid[i]=covered[i]=True
  head="Kraków Czyżyny" if group["direction"]=="KRAKOW" else "Mikluszowice"
  for n,start in enumerate(group["departures"]):
   entries.append([mask,head,pattern_id,seconds(start),"gminna:"+str(group_idx)+":"+str(n),""])
 for i,s in enumerate(stops):
  if i>=len(stops)-len(ids):s[5]=[rid]
 out.joinpath("route_all_"+str(rid)+".json").write_text(json.dumps({"patterns":patterns,"runs":entries},ensure_ascii=False,separators=(",",":")),encoding="utf-8")
 stamp=dt.datetime.fromisoformat(data["updatedAt"])
 missing=[item["name"] for item in data["stops"] if item["id"] not in ids]
 credit="MARK TRAVEL · linia gminna wg Ispina Lokalnie (niektóre przystanki: godziny szacunkowe)"
 sources.append({"id":"gminna","url":"https://github.com/senikolo/Ispina-Lokalnie-/blob/main/planner/test-data/linia-gminna.json","credit":credit,"license":"","attributions":[],"downloaded":stamp.isoformat(),"sha256":hashlib.sha256(path.read_bytes()).hexdigest(),"covered":valid})
 summary["gminna"]={"routes":1,"stops":len(ids),"trips":len(entries),"missed_stops":missing,"covered":valid}
 print("GMINNA",summary["gminna"],flush=True)
