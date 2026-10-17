const assert=require('assert/strict'),fs=require('fs');
const {Engine,category}=require('./transit-core.js'),live=require('./live-state.js');
const path=process.argv[2],idx=JSON.parse(fs.readFileSync(path+'/index_all.json'));
const engine=new Engine(idx,async id=>JSON.parse(fs.readFileSync(path+'/route_all_'+id+'.json')));
(async()=>{
 for(const [mode,expected] of [[2,'rail'],[106,'rail'],[403,'metro'],[901,'tram'],[704,'bus']])assert.equal(category(mode),expected);
 const op=new Set(idx.routes.map(r=>r[6]));for(const name of ['KM','KMŁ','SKM Warszawa','MLD'])assert(op.has(name),name);
 for(const line of ['S1','S2','S3','S4','S40'])assert(idx.routes.some(r=>r[1]===line&&r[6]==='SKM Warszawa'));
 for(const [from,to,operator] of [['Kraków Główny','Wieliczka Rynek-Kopalnia','KMŁ'],['Kraków Główny','Kraków Lotnisko','KMŁ'],['Kraków Główny','Tarnów','KMŁ'],['Warszawa Wschodnia','Legionowo','KM']]){
  const a=engine.resolve(from).group,b=engine.resolve(to).group;assert(a&&b,from+' / '+to);
  let found=false;
  for(const day of idx.days.slice(1,4)){const trips=await engine.find(a,b,day+'T05:00',{maxTransfers:0});if(trips.some(t=>t.legs.some(l=>l.operator===operator))){found=true;break}}
  assert(found,'confirmed route '+from+' → '+to+' / '+operator);console.log('PASS real GTFS',from,'→',to,operator);
 }
 const station=engine.resolve('Warszawa Wschodnia').group;
 const ds=await engine.departures(station,idx.generated+'T08:00');assert(ds.length);assert(ds.some(d=>d.train));assert(ds.some(d=>d.intermediate.length));
 assert((await engine.arrivals(station,idx.generated+'T08:00')).length);
 assert.equal(live.epoch('2026-10-25',12*3600),Date.parse('2026-10-25T11:00:00Z')/1000);
 assert.equal(live.epoch('2026-03-29',12*3600),Date.parse('2026-03-29T10:00:00Z')/1000);
 assert.equal(live.epoch('2026-10-25',3600),live.epoch('2026-10-25',0)+3600);
 const now=Date.now()/1000,d={queryDay:idx.generated,dep:36000};
 assert(!live.assess(d,{live:true,eta:now+300,liveTimestamp:now-121},now,now).etaFresh);
 assert(!live.assess(d,{cancelled:true,liveTimestamp:now-121},now,now).cancelled);
 console.log('PASS rail operators, stations, arrivals, train numbers, intermediate stops, SKM lines, DST and stale LIVE');
})().catch(e=>{console.error(e);process.exitCode=1});
