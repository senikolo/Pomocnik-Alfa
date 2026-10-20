'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs');
global.PATransit=require('./transit-core.js');
const {Engine}=global.PATransit;
const {Assistant}=require('./conversation.js');
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
 for(const name of ['Ispina Most','Ispina Dom Ludowy','Ispina Wiślisko']){
  const stop=idx.stops.find(s=>s[1]===name&&s[6]==='gminna');
  assert(idx.stops.some(s=>s[6]==='mld'&&s[3]===stop[3]&&s[4]===stop[4]),'Coordinates must come from MLD: '+name);
  const group=engine.resolve(name).group;
  const board=await engine.departures(group,weekday+'T00:00','GMINNA',null,72);
  assert(board.some(d=>d.headsign.includes('Kraków'))&&board.some(d=>d.headsign.includes('Mikluszowice')),name+' both directions');
 }
 const destination=engine.resolve('Grobla Błonie').group;
 for(const [from,to] of [[origin,destination],[destination,origin]]){
  const trips=await engine.find(from,to,weekday+'T04:00',{maxTransfers:0,mustLine:'GMINNA',walk:false});
  assert(trips.length&&trips.every(t=>t.legs.some(l=>l.line==='GMINNA')),'Planner municipal journey in both directions');
 }
 const assistant=new Assistant(engine);
 const request=assistant.parse('Kiedy odjeżdża autobus linii gminnej z Ispina Most w kierunku Kraków '+weekday+' o 04:00?');
 assert.equal(request.type,'departure');assert.equal(request.line,'GMINNA');assert.equal(request.from,origin);
 assert((await engine.departures(request.from,request.when,request.line,request.direction)).length,'Assistant departure request');
 const route=new Assistant(engine).parse('Linia gminna z Ispina Most do Grobla Błonie '+weekday+' o 04:00');
 assert.equal(route.type,'route');assert.equal(route.line,'GMINNA');
 assert((await engine.find(route.from,route.to,route.when,{mustLine:route.line,maxTransfers:0})).length,'Assistant planner request');
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
