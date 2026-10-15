import datetime as dt
from build_data import mask_for
# Published GTFS weekday rules plus explicit holidays override each other in this order.
days=[dt.date(2026,10,16)+dt.timedelta(days=i) for i in range(4)]
cal={'s':{'start_date':'20261001','end_date':'20261031','monday':'1','tuesday':'1','wednesday':'1','thursday':'1','friday':'1','saturday':'0','sunday':'0'}}
assert mask_for('s',cal,{},days)==0b1001
assert mask_for('s',cal,{('s','20261016'):'2',('s','20261018'):'1'},days)==0b1100
assert mask_for('missing',{}, {('missing','20261017'):'1'},days)==0b0010
assert mask_for('s',cal,{},[dt.date(2027,1,1)])==0
print('PASS GTFS weekday/weekend, holiday exclusions/additions, exception-only calendars and expiry')

# A public stop number is not a platform number: verify the exported passenger data.
import tempfile,zipfile,json
from pathlib import Path
from build_data import build
with tempfile.TemporaryDirectory() as folder:
 root=Path(folder);feeds=root/'feeds';feeds.mkdir();out=root/'out'
 files={
 'agency.txt':'agency_id,agency_name\n1,Fixture\n',
 'routes.txt':'route_id,route_short_name,route_type,agency_id\nx,A4,3,1\n',
 'stops.txt':'stop_id,stop_name,stop_lat,stop_lon,stop_code,platform_code\na,Ispina,50.1,20.3,4372,\nb,Cel,50.2,20.4,4373,2\n',
 'trips.txt':'route_id,service_id,trip_id,trip_headsign\nx,s,t,Cel\n',
 'stop_times.txt':'trip_id,arrival_time,departure_time,stop_id,stop_sequence\nt,09:00:00,09:00:00,a,1\nt,09:20:00,09:20:00,b,2\n',
 'calendar_dates.txt':'service_id,date,exception_type\ns,20261015,1\n'}
 with zipfile.ZipFile(feeds/'mld.zip','w') as z:
  for name,contents in files.items():z.writestr(name,contents)
 build(feeds,out,dt.date(2026,10,15))
 exported=json.loads((out/'index_all.json').read_text())['stops']
 assert exported[0][0]=='mld:a' and exported[0][2]==''
 assert exported[1][2]=='2'
print('PASS passenger platform labels do not use public stop numbers')
