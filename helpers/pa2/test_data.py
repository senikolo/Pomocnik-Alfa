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

from build_data import operator
assert operator('polishrail',{'route_type':'2'},'Koleje Mazowieckie')=='KM'
assert operator('polishrail',{'route_type':'2'},'Koleje Małopolskie')=='KMŁ'
assert operator('mld',{'route_type':'3'},'Koleje Małopolskie')=='MLD'
assert operator('warsaw',{'route_type':'2','route_short_name':'S40'},'WTP')=='SKM Warszawa'
assert operator('other',{'route_type':'2','route_short_name':'S1'},'Inna kolej')=='Inna kolej'
print('PASS rail type does not identify the operator; MLD remains bus')

# The nationwide rail feed publishes PR and IC as distinct agencies.
assert operator('polishrail',{'route_type':'2','agency_id':'PR'},'POLREGIO')=='POLREGIO'
assert operator('polishrail',{'route_type':'2','agency_id':'IC'},'PKP Intercity')=='PKP Intercity'
assert operator('polishrail',{'route_type':'2'},'PolRegio Sp. z o.o.')=='POLREGIO'
assert operator('polishrail',{'route_type':'2'},'PKP INTERCITY S.A.')=='PKP Intercity'
assert operator('polishrail',{'route_type':'2','agency_id':'XYZ'},'Inna kolej')=='Inna kolej'
assert operator('other',{'route_type':'2','agency_id':'IC'},'Inna kolej')=='Inna kolej'
print('PASS POLREGIO and PKP Intercity agency IDs, names and no route-type false positives')

# End-to-end: each operator must retain distinct scheduled trains, not merely a label.
with tempfile.TemporaryDirectory() as folder:
 root=Path(folder);feeds=root/'feeds';feeds.mkdir();out=root/'out'
 files={
  'agency.txt':'agency_id,agency_name\nPR,POLREGIO\nIC,PKP Intercity\n',
  'routes.txt':'route_id,route_short_name,route_type,agency_id\np,REG,2,PR\ni,IC,2,IC\n',
  'stops.txt':'stop_id,stop_name,stop_lat,stop_lon\na,Start,50.1,20.3\nb,Przesiadka,50.2,20.4\nc,Cel,50.3,20.5\n',
  'trips.txt':'route_id,service_id,trip_id,trip_headsign,trip_short_name\np,s,pr-trip,Przesiadka,30301\ni,s,ic-trip,Cel,5310\n',
  'stop_times.txt':'trip_id,arrival_time,departure_time,stop_id,stop_sequence\npr-trip,09:00:00,09:00:00,a,1\npr-trip,09:20:00,09:20:00,b,2\nic-trip,10:00:00,10:00:00,b,1\nic-trip,10:40:00,10:40:00,c,2\n',
  'calendar_dates.txt':'service_id,date,exception_type\ns,20261017,1\n'
 }
 with zipfile.ZipFile(feeds/'polishrail.zip','w') as z:
  for name,contents in files.items():z.writestr(name,contents)
 build(feeds,out,dt.date(2026,10,17))
 index=json.loads((out/'index_all.json').read_text())
 ops={r[6]:i for i,r in enumerate(index['routes'])}
 assert 'POLREGIO' in ops and 'PKP Intercity' in ops
 for op,trip,number in [('POLREGIO','pr-trip','30301'),('PKP Intercity','ic-trip','5310')]:
  runs=json.loads((out/('route_all_'+str(ops[op])+'.json')).read_text())['runs']
  assert any(r[4]==trip and r[5]==number and r[0]>0 for r in runs),(op,runs)
print('PASS two operator-specific schedules and train numbers survive the GTFS build')
