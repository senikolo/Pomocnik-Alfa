"""Record availability and actual feed header age. Availability alone is not proof of LIVE."""
import urllib.request,datetime,json,concurrent.futures,time
URLS=['https://mkuran.pl/gtfs/warsaw/vehicles.pb']+['https://gtfs.ztp.krakow.pl/'+kind+'_'+x+'.pb' for x in 'ATM' for kind in ('VehiclePositions','TripUpdates')]+['https://mkuran.pl/gtfs/wkd.pb','https://mkuran.pl/gtfs/polish_trains/updates.pb']
def var(b,p):
 n=0;s=0
 while p<len(b):
  v=b[p];p+=1;n|=(v&127)<<s
  if v<128:return n,p
  s+=7
 raise ValueError('truncated varint')
def fields(b):
 p=0
 while p<len(b):
  tag,p=var(b,p);wire=tag&7
  if wire==0:v,p=var(b,p)
  elif wire==2:n,p=var(b,p);v=b[p:p+n];p+=n
  elif wire in (1,5):n=8 if wire==1 else 4;v=b[p:p+n];p+=n
  else:raise ValueError('wire')
  yield tag>>3,v

def probe(u):
 r={'url':u,'checked_utc':datetime.datetime.now(datetime.timezone.utc).isoformat()}
 try:
  with urllib.request.urlopen(urllib.request.Request(u,headers={'User-Agent':'PomocnikAlfa/2.0'}),timeout=25) as response:
   b=response.read(12*1024*1024);r.update(status=response.status,bytes=len(b))
  header=next(v for k,v in fields(b) if k==1);stamp=next(v for k,v in fields(header) if k==3);r.update(timestamp=stamp,age_seconds=round(time.time()-stamp),fresh=-20<=time.time()-stamp<=120)
 except Exception as e:r['error']=str(e)
 return r
if __name__=='__main__':
 with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:print(json.dumps(list(pool.map(probe,URLS)),indent=2))
