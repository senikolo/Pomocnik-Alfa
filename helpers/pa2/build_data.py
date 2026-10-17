#!/usr/bin/env python3
"""Versioned, lossless GTFS passenger times. Never extrapolate service calendars.
Patterns share only identical stop/time/boarding arrays; run IDs remain original.
"""
import csv, io, json, zipfile, datetime as dt, hashlib, sys, os, re, unicodedata
from pathlib import Path
from collections import defaultdict
from zoneinfo import ZoneInfo
SOURCES = {
 'warsaw':('https://mkuran.pl/gtfs/warsaw.zip','Warszawski Transport Publiczny; GTFS: Mikołaj Kuranowski; OpenStreetMap contributors','https://mkuran.pl/gtfs/'),
 'mld':('https://www.kolejemalopolskie.com.pl/rozklady_jazdy/ald-gtfs.zip','Koleje Małopolskie – MLD','https://www.kolejemalopolskie.com.pl/pl/rozklady-jazdy/gtfs'),
 'krakow_bus':('https://gtfs.ztp.krakow.pl/GTFS_KRK_A.zip','Zarząd Transportu Publicznego w Krakowie – autobusy','https://ztp.krakow.pl/transport-publiczny/komunikacja-miejska/otwarte-dane'),
 'krakow_tram':('https://gtfs.ztp.krakow.pl/GTFS_KRK_T.zip','Zarząd Transportu Publicznego w Krakowie – tramwaje','https://gtfs.ztp.krakow.pl/'),
 'krakow_mobilis':('https://gtfs.ztp.krakow.pl/GTFS_KRK_M.zip','Zarząd Transportu Publicznego w Krakowie – Mobilis','https://gtfs.ztp.krakow.pl/'),
 'kmlrail':('https://www.kolejemalopolskie.com.pl/rozklady_jazdy/kml-ska-gtfs.zip','Koleje Małopolskie – kolej','https://www.kolejemalopolskie.com.pl/pl/rozklady-jazdy/gtfs'),
 'wkd':('https://mkuran.pl/gtfs/wkd.zip','Warszawska Kolej Dojazdowa; GTFS: Mikołaj Kuranowski (CC0)','https://mkuran.pl/gtfs/'),
 'polishrail':('https://mkuran.pl/gtfs/polish_trains.zip','PKP PLK, Koleje Mazowieckie; GTFS: Mikołaj Kuranowski; dane przetworzone','https://mkuran.pl/gtfs/'),
}
WEEK=('monday','tuesday','wednesday','thursday','friday','saturday','sunday')
def dump(p,v):p.write_text(json.dumps(v,ensure_ascii=False,separators=(',',':')),encoding='utf8')
def rows(z,name):
 names={Path(x).name:x for x in z.namelist()}
 return csv.DictReader(io.TextIOWrapper(z.open(names[name]),encoding='utf-8-sig',newline='')) if name in names else []
def seconds(x):
 a=list(map(int,x.split(':')));return a[0]*3600+a[1]*60+(a[2] if len(a)>2 else 0)
def mask_for(service,cal,exc,days):
 rule=cal.get(service,{});mask=0
 for i,day in enumerate(days):
  d=day.strftime('%Y%m%d');yes=rule.get('start_date','99999999')<=d<=rule.get('end_date','00000000') and rule.get(WEEK[day.weekday()])=='1'
  override=exc.get((service,d));yes=override=='1' if override else yes
  if yes:mask|=1<<i
 return mask

def operator(provider,route,agency):
 name=unicodedata.normalize('NFD',agency.lower()).encode('ascii','ignore').decode()
 # The nationwide GTFS bundles operators together. Respect its published agency IDs
 # (PR and IC) rather than guessing from train type, number or destination.
 if provider=='polishrail':
  agency_id=str(route.get('agency_id','')).strip().upper()
  if agency_id=='PR' or re.search(r'\bpol\s*regio\b',name):return 'POLREGIO'
  if agency_id=='IC' or re.search(r'\b(?:pkp\s+)?intercity\b',name):return 'PKP Intercity'
 if provider=='mld':return 'MLD'
 if provider=='kmlrail' or 'koleje maopolskie' in name:return 'KMŁ'
 if 'koleje mazowieckie' in name:return 'KM'
 if provider=='wkd':return 'WKD'
 if 'skm warszawa' in name or (provider=='warsaw' and route.get('route_type')=='2' and re.fullmatch(r'S(?:1|2|3|4|40)',route.get('route_short_name',''))):return 'SKM Warszawa'
 return agency

def build(source,out,today):
 out.mkdir(parents=True,exist_ok=True);days=[today+dt.timedelta(days=i) for i in range(-1,14)]
 stops=[];routes=[];sources=[];stop_routes=[];covered=[False]*len(days);transfers=[];summary={};preferred={}
 for provider,(url,credit,license_url) in SOURCES.items():
  path=source/(provider+'.zip')
  if not path.exists():print('MISSING',provider,flush=True);continue
  with zipfile.ZipFile(path) as z:
   cal={r['service_id']:r for r in rows(z,'calendar.txt')};exc={(r['service_id'],r['date']):r['exception_type'] for r in rows(z,'calendar_dates.txt')}
   masks={};tripmeta={};rr=list(rows(z,'routes.txt'));ri={r['route_id']:len(routes)+i for i,r in enumerate(rr)}
   agency={r.get('agency_id',''):r['agency_name'] for r in rows(z,'agency.txt')}
   for r in rr:
    an=agency.get(r.get('agency_id',''),next(iter(agency.values())) if len(agency)==1 else credit)
    routes.append([provider+':'+r['route_id'],r.get('route_short_name') or r.get('route_long_name') or r['route_id'],r.get('route_long_name',''),int(r['route_type']),an,provider,operator(provider,r,an)])
   frequency=defaultdict(list)
   for f in rows(z,'frequencies.txt'):
    try:
     start,end,headway=seconds(f['start_time']),seconds(f['end_time']),int(f['headway_secs'])
     if 0<=start<end<=172800 and headway>0:frequency[f['trip_id']].append((start,end,headway,f.get('exact_times','0')=='1'))
    except (ValueError,KeyError):continue
   si={}
   for s in rows(z,'stops.txt'):
    if s.get('location_type','0') not in ('','0'):continue
    try:lat=float(s['stop_lat']);lon=float(s['stop_lon'])
    except (ValueError,KeyError):continue
    sid=len(stops);si[s['stop_id']]=sid
    platform=s.get('platform_code','')
    if not platform and provider=='warsaw' and len(s['stop_id'])==6 and s['stop_id'].isdigit():platform=s['stop_id'][-2:]
    stops.append([provider+':'+s['stop_id'],s.get('stop_name',''),platform,round(lat,6),round(lon,6),[],provider,s.get('parent_station','')]);stop_routes.append(set())
   for t in rows(z,'trips.txt'):
    service=t['service_id']
    if service not in masks:masks[service]=mask_for(service,cal,exc,days)
    mask=masks[service]
    if mask and t['route_id'] in ri:tripmeta[t['trip_id']]=(ri[t['route_id']],mask,t.get('trip_headsign',''),t.get('trip_short_name',''))
   routepoints=defaultdict(dict);n=0
   for p in rows(z,'stop_times.txt'):
    info=tripmeta.get(p['trip_id']);sid=si.get(p['stop_id'])
    if info is None or sid is None:continue
    try:dep=seconds(p['departure_time']);arr=seconds(p['arrival_time']);seq=int(p['stop_sequence'])
    except (KeyError,ValueError):continue
    if dep<arr or not(0<=arr<=172800):continue
    routepoints[info[0]].setdefault(p['trip_id'],[]).append((seq,sid,dep,arr,int(p.get('pickup_type') or 0),int(p.get('drop_off_type') or 0)));n+=1
   runs=0;valid=[False]*len(days)
   for r in rr:
    rid=ri[r['route_id']];patterns=[];lookup={};entries=[]
    for tid,points in routepoints.get(rid,{}).items():
     points.sort();base=points[0][2]
     # Reject malformed chronology rather than silently deleting intermediate stops.
     if len(points)<2 or any(points[j][3]<points[j-1][2] for j in range(1,len(points))):continue
     key=tuple((p[1],p[2]-base,p[3]-base,p[4],p[5]) for p in points)
     if key not in lookup:lookup[key]=len(patterns);patterns.append(key)
     _,mask,head,short=tripmeta[tid]
     # Prefer dedicated WTP/SKA data only on service days they cover. No train-number fuzzy RT joins.
     op=routes[rid][6]
     if provider=='polishrail' and op in ('KMŁ','SKM Warszawa'):mask &= ~preferred.get(op,0)
     if not mask:continue
     for j in range(len(days)):
      if mask&(1<<j) and provider in ('warsaw','kmlrail') and op in ('KMŁ','SKM Warszawa'):preferred[op]=preferred.get(op,0)|(1<<j)
     entry=[mask,head or stops[points[-1][1]][1],lookup[key],base,tid,short]
     if tid in frequency:
      for start,end,headway,exact in frequency[tid]:
       if exact:
        for departure in range(start,end,headway):entries.append(entry[:3]+[departure,tid+'@'+str(departure),short])
       else:entries.append(entry[:3]+[start,tid,short,headway,end])
     else:entries.append(entry)
     for p in points:stop_routes[p[1]].add(rid)
     for j in range(len(days)):
      if mask&(1<<j):valid[j]=covered[j]=True
     runs+=1
    dump(out/f'route_all_{rid}.json',{'patterns':patterns,'runs':entries})
   # Explicit GTFS prohibited / timed transfers, consumed before proximity edges.
   for t in rows(z,'transfers.txt'):
    a=si.get(t.get('from_stop_id'));b=si.get(t.get('to_stop_id'))
    if a is not None and b is not None:transfers.append([a,b,int(t.get('transfer_type') or 0),int(t.get('min_transfer_time') or 0)])
   attrs=list(rows(z,'attributions.txt'));sources.append({'id':provider,'url':url,'credit':credit,'license':license_url,'attributions':attrs,'downloaded':dt.datetime.fromtimestamp(path.stat().st_mtime,ZoneInfo('Europe/Warsaw')).isoformat(),'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'covered':valid})
   summary[provider]={'routes':len(rr),'stops':len(si),'trips':runs,'stop_times':n,'covered':valid};print(provider,summary[provider],flush=True)
 for i,s in enumerate(stops):s[5]=sorted(stop_routes[i])
 index={'schema':2,'transportRevision':10,'source':'all','generated':today.isoformat(),'days':[d.isoformat() for d in days],'covered':covered,'stops':stops,'routes':routes,'sources':sources,'transfers':transfers,'notice':'Rozkład planowy GTFS. Dane rzeczywiste wymagają zgodnego kursu i świeżego pomiaru.'}
 dump(out/'index_all.json',index);dump(out/'summary.json',summary)
 files=[]
 for p in sorted(out.glob('*.json')):
  if p.name=='manifest.json':continue
  files.append({'name':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'size':p.stat().st_size})
 dump(out/'manifest.json',{'schema':2,'generated':today.isoformat(),'files':files})
 print('TOTAL',len(stops),len(routes),sum(p.stat().st_size for p in out.glob('*.json')),flush=True)
if __name__=='__main__':build(Path(sys.argv[1]),Path(sys.argv[2]),dt.date.fromisoformat(os.environ.get('PA_DATE',dt.datetime.now(ZoneInfo('Europe/Warsaw')).date().isoformat())))
