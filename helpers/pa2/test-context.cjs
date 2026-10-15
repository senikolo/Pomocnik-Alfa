'use strict';
const assert=require('node:assert/strict');
global.PATransit=require('./transit-core.js');
const {Assistant,parseTime}=require('./conversation.js');
const {canonical}=PATransit;
const names={'tarchomin':'Tarchomin','nowodwory':'Nowodwory','ispina most':'Ispina Most','ispina':'Ispina Most','bochnia dworzec pkp':'Bochnia Dworzec PKP'};
const engine={
 idx:{routes:[['route186','186'],['route17','17']]},
 resolve(q){const key=canonical(q).replace(/^przystanku? /,'');const name=names[key];return {group:name?{key,name,routes:new Set([0,1])}:null,candidates:[]}},
 match(){return []}
};
let passed=0;
function test(label,fn){fn();passed++;console.log('PASS',label)}
test('naturalny przystanek i kierunek w pytaniu LIVE',()=>{
 const a=new Assistant(engine);
 const r=a.parse('Gdzie jest autobus 186 na przystanku Tarchomin w kierunku Szczęśliwice?');
 assert.equal(r.type,'vehicle');assert.equal(r.from.name,'Tarchomin');
 assert.equal(r.line,'186');assert.equal(r.direction,'Szczęśliwice');
});
test('nowa linia nie odziedziczy dawnego kierunku',()=>{
 const a=new Assistant(engine);
 a.parse('Gdzie jest autobus 186 z Tarchomina w kierunku Szczęśliwice?');
 const r=a.parse('A gdzie jest tramwaj 17?');
 assert.equal(r.type,'vehicle');assert.equal(r.line,'17');
 assert.equal(r.direction,null);assert.equal(r.from.name,'Tarchomin');
});
test('z pojazdu LIVE wraca do nowej trasy bez starego numeru',()=>{
 const a=new Assistant(engine);
 a.parse('Gdzie jest autobus 186 z Tarchomina w kierunku Szczęśliwice?');
 const r=a.parse('A do Nowodworów?');
 assert.equal(r.type,'route');assert.equal(r.to.name,'Nowodwory');
 assert.equal(r.line,null);assert.equal(r.from.name,'Tarchomin');
});
test('czytelne dopytanie, zamiast zgadywania lokalizacji i linii',()=>{
 const a=new Assistant(engine);
 const first=a.parse('Gdzie jest mój autobus?');
 assert.equal(first.type,'clarify');assert.equal(a.state.asked,'from');
 const second=a.parse('Tarchomin');
 assert.equal(second.type,'clarify');assert.equal(a.state.asked,'line');
 const third=a.parse('186');
 assert.equal(third.type,'vehicle');assert.equal(third.line,'186');
});
test('pół i półtorej godziny to czas względny',()=>{
 assert.equal(parseTime('Pół godziny później','2026-10-15T10:00','2026-10-15T10:00').when,'2026-10-15T10:30');
 assert.equal(parseTime('Półtorej godziny wcześniej','2026-10-15T10:00','2026-10-15T10:00').when,'2026-10-15T08:30');
});
test('nowy cel zachowuje już podaną datę i godzinę',()=>{
 const a=new Assistant(engine);
 const r=a.parse('Z Ispiny do Bochni Dworzec PKP jutro po 9');
 assert.equal(r.type,'route');assert(r.when.endsWith('T09:00'));
 const changed=a.parse('A do Nowodworów?');
 assert.equal(changed.type,'route');assert.equal(changed.when,r.when);
});
console.log('TOTAL PASS context',passed);
