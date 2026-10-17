'use strict';
const assert=require('node:assert/strict'),fs=require('fs');global.PATransit=require('./transit-core.js');const {Assistant,parseTime,wallNow}=require('./conversation.js');const {Engine}=PATransit;let count=0;const test=async(name,fn)=>{await fn();console.log('PASS',name);count++};
const dir=process.argv[2]||'build/data',idx=JSON.parse(fs.readFileSync(dir+'/index_all.json'));let unavailable=false;const e=new Engine(idx,async id=>{if(unavailable)throw Error('offline missing');return JSON.parse(fs.readFileSync(dir+'/route_all_'+id+'.json'))});
const g=name=>{let r=e.resolve(name);assert.ok(r.group,'Missing stop '+name);return r.group};
const date=idx.generated,when=date+'T09:00';
(async()=>{
 await test('fixture: dojście przed pierwszym kursem bez fikcyjnego oczekiwania',async()=>{const index={days:['2026-10-15'],covered:[true],stops:[['a','Start','',52,21,[],'fixture'],['b','Wejście','',52.001,21,[0],'fixture'],['c','Cel','',52.03,21,[0],'fixture']],routes:[['r','X','',3,'fixture','fixture']],transfers:[]};const walk=new Engine(index,async()=>({patterns:[[[1,0,0,0,0],[2,1800,1800,0,0]]],runs:[[1,'Cel',0,34200,'fixture-trip','']]}));const rows=await walk.find(walk.resolve('Start').group,walk.resolve('Cel').group,'2026-10-15T09:00',{maxTransfers:0});assert(rows.length);const r=rows[0];assert.equal(r.legs[0].walk,true);assert.equal(r.dep,r.legs[0].dep);assert.equal(r.legs[0].arr,r.legs[1].dep);assert.equal(r.wait,0);assert.equal(r.duration,r.arr-r.legs[0].dep)});
 await test('pełny zakres: autobus, tramwaj, metro, SKM, WKD i kolej krajowa',()=>{for(const type of [0,1,2,3,900])assert(idx.routes.some(r=>r[3]===type));assert(idx.routes.some(r=>r[5]==='wkd'));assert(idx.routes.some(r=>r[5]==='polishrail'));assert(idx.routes.some(r=>r[4].includes('Mazowieckie')))});
 await test('jednakowe nazwy: Szwedzka w Warszawie i Krakowie oraz dwa Stanisławowy wymagają wyboru',()=>{const x=e.resolve('Szwedzka');assert.equal(x.group,null);assert(x.candidates.length>=2);const waw=x.candidates.find(g=>g.name.includes('Warszawa')),krk=x.candidates.find(g=>g.name.includes('Kraków'));assert(waw&&krk);assert(waw.stops.every(i=>idx.stops[i][6]==='warsaw'));assert(krk.stops.every(i=>idx.stops[i][6].startsWith('krakow')));assert.equal(e.resolve(waw.name).group,waw);assert.equal(e.resolve(krk.name).group,krk);const y=e.resolve('Stanisławów');assert.equal(y.group,null);assert(y.candidates.length>=2);assert.notEqual(y.candidates[0].name,y.candidates[1].name);for(const c of [waw,krk,...y.candidates])for(const i of c.stops)assert(PATransit.distance(idx.stops[c.stops[0]],idx.stops[i])<=800)});
 await test('Białołęka–centrum: wyłącznie rzeczywiste kursy i chronologia przesiadek',async()=>{const rows=await e.find(g('Białołęka-Ratusz'),g('Dw. Centralny'),when);assert(rows.length);for(const r of rows){assert(r.arr>=r.dep);for(let i=1;i<r.legs.length;i++)assert(r.legs[i].dep>=r.legs[i-1].arr);for(const l of r.legs.filter(l=>!l.walk))assert(l.trip&&l.serviceDay)}});
 await test('metro: częstotliwość zamiast fikcyjnych godzin',async()=>{assert.equal(g('Metro Centrum').key,'centrum');const rows=await e.departures(g('Młociny'),when,'M1');assert(rows.some(d=>d.headway>0&&d.frequencyEnd>d.dep));const routes=await e.find(g('Metro Młociny'),g('Metro Centrum'),when,{modes:[1],maxTransfers:0});for(const r of routes)assert(r.legs.every(l=>l.walk||!l.trip.includes('NdM')))});
 await test('tramwaj Tarchomin–Nowodwory',async()=>{const rows=await e.find(g('Tarchomin'),g('Nowodwory'),when,{modes:[0,900],maxTransfers:0});assert(rows.length)});
 await test('autobusy nocne występują w bazie i mają aktywne odjazdy',async()=>{const origin=g('Dw. Centralny');let found=[];for(const n of origin.routes){if(!/^N\d/.test(idx.routes[n][1]))continue;found=await e.departures(origin,date+'T23:00',idx.routes[n][1]);if(found.length)break}assert(found.length);assert(found.every(x=>x.dep>=23*3600&&/^N/.test(x.line)))});
 await test('Warszawa Centralna–Kraków Główny z krajowego GTFS',async()=>{const rows=await e.find(g('Warszawa Centralna'),g('Kraków Główny'),when,{maxTransfers:0});assert(rows.length);assert(rows.some(r=>r.legs.some(l=>l.source==='polishrail')))});
 await test('Ispina i Ispina Most: jeden zespół przystankowy',()=>assert.strictEqual(g('Ispina'),g('Ispina Most')));
 await test('Ispina Most–Bochnia: bezpośrednie MLD A4/A65',async()=>{const rows=await e.find(g('Ispina Most'),g('Bochnia Dworzec PKP'),when,{maxTransfers:0});assert(rows.length);assert(rows.some(r=>r.legs.some(l=>['A4','A65'].includes(l.line))))});
 await test('Leśniczówka: brak sfabrykowanego odjazdu A4, alternatywa Most ma kurs',async()=>{assert.equal((await e.departures(g('Ispina Leśniczówka'),when,'A4')).length,0);assert((await e.departures(g('Ispina Most'),when,'A4')).length)});
 await test('rozmowa: dane zachowane po dopytaniu, odmiana i godzina później',()=>{const a=new Assistant(e);let x=a.parse('Chcę jutro po 9 pojechać z Ispiny do Bochni');assert.equal(x.type,'clarify');assert.equal(a.state.asked,'to');assert(a.state.when.endsWith('T09:00'));x=a.parse('Bochnia Dworzec PKP');assert.equal(x.type,'route');const previous=x.when;x=a.parse('A godzinę później?');assert.equal(x.type,'route');assert(x.when.endsWith('T10:00'));assert.equal(x.when.slice(0,10),previous.slice(0,10));x=a.parse('Kiedy następna A4?');assert.equal(x.type,'departure');assert.equal(x.line,'A4');assert.equal(x.from.name,'Ispina Most');assert(x.direction);x=a.parse('Gdzie jest mój autobus?');assert.equal(x.type,'vehicle');assert.equal(x.line,'A4')});
 await test('rozmowa: kierunek i linia pytane oddzielnie bez utraty daty',()=>{const a=new Assistant(e);let x=a.parse('Gdzie jest mój autobus?');assert.equal(x.type,'clarify');assert.equal(a.state.asked,'from');x=a.parse('Tarchomin');assert.equal(x.type,'clarify');assert.equal(a.state.asked,'line');x=a.parse('186');assert.equal(x.type,'vehicle');assert.equal(x.line,'186')});
 await test('drugi porządek słów, brak pytania o znany czas i cel',()=>{const a=new Assistant(e);let x=a.parse('Chcę jechać do Bochni z Ispiny jutro po 9');assert.equal(x.type,'clarify');assert.equal(a.state.asked,'to');assert(a.state.when.endsWith('T09:00'));x=a.parse('Bochnia Dworzec PKP');assert.equal(x.type,'route')});
 await test('literówka Ispini, odmiana Leśniczówki, nowy region bez starej linii',()=>{const a=new Assistant(e);let x=a.parse('Z Ispini do Bochni Dworzec PKP jutro po 9');assert.equal(x.type,'route');assert.match(x.correction,/Ispiny/);x=a.parse('Z Ispiny Leśniczówki do Bochni Dworzec PKP');assert.equal(x.from.name,'Ispina Leśniczówka');x=a.parse('Jak dojadę z Tarchomina na Dworzec Centralny?');assert.equal(x.type,'route');assert.equal(x.from.name,'Tarchomin');assert.equal(x.to.name,'Dw. Centralny');assert.equal(x.line,null)});
 await test('pogoda, notatki i przypomnienia nie zmieniają trasy',()=>{const a=new Assistant(e);a.parse('Z Ispiny do Bochni Dworzec PKP jutro po 9');const before=a.state.from;let x=a.parse('Jaka będzie jutro pogoda w Warszawie?');assert.equal(x.type,'weather');assert.equal(x.city,'warszawa');assert.equal(a.state.from,before);assert.equal(a.parse('Zapisz notatkę: kupić mleko').text,'kupić mleko');x=a.parse('Przypomnij mi jutro o 15 o lekarzu');assert.equal(x.type,'reminder');assert(x.when.endsWith('T15:00'));assert.equal(a.parse('Co to jest Wisła?').type,'general')});
 await test('błędne daty i godziny odrzucane; zmiana czasu przechodzi przez północ',()=>{assert(parseTime('31.02.2026 o 10',when).error);assert(parseTime('jutro o 25',when).error);assert.equal(parseTime('godzinę później','2026-10-15T23:30').when,'2026-10-16T00:30')});
 await test('nieaktualny rozkład: odmowa zamiast powielania kursów',async()=>{await assert.rejects(()=>e.find(g('Ispina'),g('Bochnia Dworzec PKP'),'2035-01-01T09:00'),/Brak potwierdzonego/)});
 await test('offline: zapisane pliki działają bez połączenia; brak pliku nie jest pustą listą',async()=>{const offline=new Engine(idx,async id=>JSON.parse(fs.readFileSync(dir+'/route_all_'+id+'.json')));assert((await offline.find(g('Ispina'),g('Bochnia Dworzec PKP'),when,{maxTransfers:0})).length);const missing=new Engine(idx,async()=>{throw Error('offline missing')});await assert.rejects(()=>missing.find(g('Ispina'),g('Bochnia Dworzec PKP'),when,{maxTransfers:0}),/offline missing/)});
 await test('Planer usuwa kopie tego samego pociągu i niekorzystne przesiadki',async()=>{
  const monday=idx.days.find(d=>new Date(d+'T12:00:00Z').getUTCDay()===1);
  assert(monday,'No Monday in the rolling GTFS window');
  const routes=await e.find(g('Warszawa Centralna'),g('Kraków Główny'),monday+'T09:00',{maxTransfers:2});
  assert(routes.length,'No Warsaw–Kraków service');
  const fingerprints=routes.map(r=>r.legs.filter(l=>!l.walk).map(l=>[l.source,l.trip,l.serviceDay,l.dep,l.fromId,l.toId].join(':')).join('|'));
  assert.equal(new Set(fingerprints).size,fingerprints.length,'Different terminal footpaths duplicate the same train');
  for(let i=0;i<routes.length;i++)for(let j=0;j<routes.length;j++){
   if(i===j)continue;const a=routes[i],b=routes[j];
   assert(!(b.arr<=a.arr&&b.dep>=a.dep&&b.transfers<=a.transfers&&b.walkMetres<=a.walkMetres&&
    (b.arr<a.arr||b.dep>a.dep||b.transfers<a.transfers||b.walkMetres<a.walkMetres)),
    'A dominated itinerary was displayed: '+i+' vs '+j);
  }
 });
 await test('Ispina–Bochnia: bezpośredni autobus nie zostaje zastąpiony bezsensownym objazdem',async()=>{
  const monday=idx.days.find(d=>new Date(d+'T12:00:00Z').getUTCDay()===1);
  const routes=await e.find(g('Ispina Most'),g('Bochnia Dworzec PKP'),monday+'T09:00',{maxTransfers:2});
  assert(routes.some(r=>r.transfers===0&&r.legs.some(l=>l.line==='A65')));
  const direct=routes.find(r=>r.transfers===0&&r.legs.some(l=>l.line==='A65'));
  assert(routes.every(r=>!(r.transfers>0&&r.arr>=direct.arr&&r.dep<=direct.dep&&r.walkMetres>=direct.walkMetres)),
   'Nonsensical detour survives even though a direct bus is better');
 });
 await test('Tarchomin–Nowodwory: szybkie bezpośrednie tramwaje zamiast całonocnej wycieczki',async()=>{
  const monday=idx.days.find(d=>new Date(d+'T12:00:00Z').getUTCDay()===1);
  const rows=await e.find(g('Tarchomin'),g('Nowodwory'),monday+'T09:00',{maxTransfers:2});
  assert(rows.length>=3&&rows.slice(0,3).every(r=>r.transfers===0));
  assert(rows.every(r=>r.duration<45*60),'Overnight detour returned for a four-minute journey');
 });
 await test('sortowanie według przyjazdu, czasu i przesiadek',()=>{const rows=[{arr:500,duration:200,transfers:1},{arr:600,duration:100,transfers:0}];assert.equal(e.sort([...rows],'arrival')[0].arr,500);assert.equal(e.sort([...rows],'duration')[0].duration,100);assert.equal(e.sort([...rows],'transfers')[0].transfers,0)});
 console.log('TOTAL PASS',count);
})().catch(e=>{console.error('FAIL',e);process.exitCode=1});
