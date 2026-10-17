#!/usr/bin/env python3
"""Stream bounded archives; a failed provider does not discard the other feeds."""
import urllib.request,zipfile,concurrent.futures,time,sys,json
from pathlib import Path
from build_data import SOURCES
LIMIT=256*1024*1024
UNPACKED_LIMIT=2*1024*1024*1024

def validate(path):
 with zipfile.ZipFile(path) as z:
  names={Path(n).name for n in z.namelist()}
  if not {'stops.txt','routes.txt','trips.txt','stop_times.txt','agency.txt'}<=names:raise ValueError('Niekompletny GTFS')
  if not ({'calendar.txt','calendar_dates.txt'}&names):raise ValueError('Brak kalendarza')
  if sum(x.file_size for x in z.infolist())>UNPACKED_LIMIT:raise ValueError('Rozpakowany GTFS przekracza limit')
  if z.testzip():raise ValueError('Uszkodzona suma CRC')

def fetch(item,out):
 key,(url,*_)=item;target=out/(key+'.zip');tmp=out/(key+'.tmp');error=None
 for attempt in range(3):
  try:
   request=urllib.request.Request(url,headers={'User-Agent':'PomocnikAlfa/2.0 (+https://github.com/senikolo/Pomocnik-Alfa)'})
   size=0
   with urllib.request.urlopen(request,timeout=60) as response,tmp.open('wb') as stream:
    while True:
     chunk=response.read(1024*1024)
     if not chunk:break
     size+=len(chunk)
     if size>LIMIT:raise ValueError('GTFS przekracza 256 MiB')
     stream.write(chunk)
   validate(tmp);tmp.replace(target);return {'source':key,'bytes':size,'status':'downloaded'}
  except Exception as e:
   error=str(e);tmp.unlink(missing_ok=True)
   if attempt<2:time.sleep(attempt+1)
 if target.exists():
  try:validate(target);return {'source':key,'bytes':target.stat().st_size,'status':'cached','error':error}
  except Exception:target.unlink()
 return {'source':key,'status':'unavailable','error':error}
if __name__=='__main__':
 out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
 with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
  results=list(pool.map(lambda item:fetch(item,out),SOURCES.items()))
 for result in results:print(json.dumps(result,ensure_ascii=False),flush=True)
 (out/'download-status.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
 if all(x['status']=='unavailable' for x in results):raise SystemExit('Brak dostępnych rozkładów')
