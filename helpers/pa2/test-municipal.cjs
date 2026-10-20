'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs');
const {Engine}=require('./transit-core.js');
const dir=process.argv[2];
assert(dir,'Podaj katalog z indeksami GTFS');
const idx=JSON.parse(fs.readFileSync(dir+'/index_all.json','utf8'));
const rid=idx.routes.findIndex(r=>r[1]==='GMINNA'&&r[5]==='gminna');
assert(rid>=0,'Missing municipal line in transport index');
const data=JSON.parse(fs.readFileSync(dir+'/route_all_'+rid+'.json','utf8'));
assert.equal(data.runs.length,38);
assert.equal(data.patterns.length,4);
for(const name of ['Ispina Most','Ispina Dom Ludowy','Ispina Wiślisko'])assert(idx.stops.some(s=>s[1]===name&&s[6]==='gminna'),name);
const weekday=idx.days.find(d=>new Date(d+'T12:00:00Z').getUTCDay()>=1&&new Date(d+'T12:00:00Z').getUTCDay()<=5);
const sunday=idx.days.find(d=>new Date(d+'T12:00:00Z').getUTCDay()===0);
assert(weekday,'No weekday in GTFS index');
const engine=new Engine(idx,async id=>JSON.parse(fs.readFileSync(dir+'/route_all_'+id+'.json')));
(async()=>{
 const origin=engine.resolve('Ispina Most').group;
 assert(origin,'Ispina Most not searchable');
 assert(origin.routes.has(rid),'Ispina Most not connected to municipal line');
 const departures=await engine.departures(origin,weekday+'T00:00','GMINNA',null,72);
 assert(departures.some(d=>d.headsign.includes('Kraków')),'No direction Kraków');
 assert(departures.some(d=>d.headsign.includes('Mikluszowice')),'No direction Mikluszowice');
 const estimatedGroup=engine.resolve('Grobla Błonie').group;
 assert(estimatedGroup,'Grobla Błonie not searchable');
 const estimated=await engine.departures(estimatedGroup,weekday+'T00:00','GMINNA',null,72);
 assert(estimated.some(d=>d.estimated),'Estimated time not propagated to departure board');
 if(sunday){
  const sundayTrips=await engine.departures(origin,sunday+'T00:00','GMINNA',null,72);
  assert(sundayTrips.every(d=>d.serviceDay!==sunday),'Municipal route shows Sunday service');
 }
 console.log('PASS PA11 GMINNA source, 3 stops, both directions, marked estimated times, Sunday calendar');
})().catch(e=>{console.error(e);process.exitCode=1});
