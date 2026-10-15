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
