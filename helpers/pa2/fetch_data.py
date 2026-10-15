#!/usr/bin/env python3
import urllib.request,zipfile,io,concurrent.futures,time,sys
from pathlib import Path
from build_data import SOURCES
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
def fetch(item):
 key,(url,*_)=item
 error=None
 for attempt in range(3):
  try:
   request=urllib.request.Request(url+('?pa=2' if '?' not in url else '&pa=2'),headers={'User-Agent':'PomocnikAlfa/2.0 (+https://github.com/senikolo/Pomocnik-Alfa)'})
   b=urllib.request.urlopen(request,timeout=90).read()
   with zipfile.ZipFile(io.BytesIO(b)) as z:
    assert {'stops.txt','routes.txt','trips.txt','stop_times.txt'}.issubset(z.namelist())
   tmp=out/(key+'.tmp');tmp.write_bytes(b);tmp.replace(out/(key+'.zip'));return key,len(b)
  except Exception as e:error=e;time.sleep(attempt*2)
 if (out/(key+'.zip')).exists():return key,'cached; '+str(error)
 raise RuntimeError(key+': '+str(error))
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
 for r in pool.map(fetch,SOURCES.items()):print(r,flush=True)
