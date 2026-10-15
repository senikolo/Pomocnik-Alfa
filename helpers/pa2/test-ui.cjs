const fs=require('fs'),path=require('path');
const {JSDOM,VirtualConsole}=require(process.env.PA_JSDOM_PATH||'jsdom');
const root=__dirname,data=path.resolve(process.argv[2]);const assert=require('node:assert/strict');
let clock=Date.parse('2026-10-15T13:00:00Z');
const vc=new VirtualConsole(),errors=[];vc.on('jsdomError',e=>errors.push(e.message));
const d=new JSDOM(fs.readFileSync(root+'/app.html','utf8'),{url:'https://pa-bus.invalid/app.html',runScripts:'outside-only',pretendToBeVisual:true,virtualConsole:vc});
const w=d.window,doc=w.document,OriginalDate=w.Date;w.Date=class extends OriginalDate{constructor(...args){super(...(args.length?args:[clock]))}static now(){return clock}};
w.matchMedia=()=>({matches:false,addEventListener(){},addListener(){}});w.scrollTo=()=>{};w.HTMLElement.prototype.scrollTo=function(){};w.HTMLElement.prototype.scrollIntoView=function(){};
Object.defineProperty(w.HTMLElement.prototype,'innerText',{get(){return this.textContent},set(s){this.textContent=s},configurable:true});
w.fetch=async url=>{try{let b=fs.readFileSync(data+new URL(url,'https://pa-bus.invalid').pathname);return {ok:true,json:async()=>JSON.parse(b)}}catch{return {ok:false}}};
let liveEnabled=true;const requests=[];w.PADevice={request(raw){let x=JSON.parse(raw);requests.push(x);let result;
 if(x.type==='live')result={checkedAt:Math.floor(clock/1000),results:x.payload.departures.map(c=>({...c,live:liveEnabled,...(liveEnabled?{position:{latitude:52.32,longitude:20.97,timestamp:Math.floor(clock/1000),age:0}}:{} )}))};
 else if(x.type==='weather')result={city:'Bochnia',date:'2026-10-16',min:7,max:14,rain:40,source:'fixture'};
 else result={saved:true,results:[]};
 setTimeout(()=>w.PANativeReply(x.id,{ok:true,data:result}),1);
}};
const resizing=new Set(),add=w.addEventListener.bind(w),remove=w.removeEventListener.bind(w);w.addEventListener=(name,fn,...rest)=>{if(name==='resize')resizing.add(fn);return add(name,fn,...rest)};w.removeEventListener=(name,fn,...rest)=>{if(name==='resize')resizing.delete(fn);return remove(name,fn,...rest)};
for(const n of ['transit-core.js','conversation.js','osm-map.js','live-state.js','app.js'])w.eval(fs.readFileSync(root+'/'+n,'utf8'));
const $=id=>doc.getElementById(id),delay=ms=>new Promise(r=>setTimeout(r,ms));
async function until(check){for(let i=0;i<400;i++){if(check())return;await delay(10)}throw Error('UI wait timeout')}
const out=[];function record(name,data){out.push({name,...data})}
async function chooseStop(name){$('busStop').value=name;$('busStop').dispatchEvent(new w.Event('input'));let b=[...doc.querySelectorAll('#busStops button')].find(b=>b.textContent.startsWith(name));if(!b)throw Error('No stop '+name);b.click();}
async function chooseLine(number){let o=[...$('busLine').options].find(x=>x.textContent.endsWith(' '+number));if(!o)throw Error('No line '+number);$('busLine').value=o.value;$('busLine').dispatchEvent(new w.Event('change'));await until(()=>$('busDirection').options.length>1&&!$('busDirection').disabled);}
async function check(){const old=requests.length;$('checkBus').click();await until(()=>requests.length>old&&!$('checkBus').disabled&&$('busResult').textContent.includes('pozycja GPS'));}
async function ask(q){$('chatInput').value=q;$('ask').click();await until(()=>!$('ask').disabled);}
(async()=>{
 await until(()=>$('coverage').textContent.includes('offline'));
 $('busQuick').click();await chooseStop('Tarchomin');
 record('Tarchomin bus/tram picker',{lines:[...$('busLine').options].map(x=>x.textContent)});
 await chooseLine('186');let o=[...$('busDirection').options].find(x=>x.textContent==='Szczęśliwice');$('busDirection').value=o.value;$('busDirection').dispatchEvent(new w.Event('change'));await check();
 const initial=$('busResult').textContent,req=requests.filter(r=>r.type==='live').length;
 liveEnabled=false;clock+=181000;w.PAVisibility(true);assert(!$('busResult').textContent.includes('Świeża pozycja GPS'));record('stale GPS expires',{pass:true});await delay(50);liveEnabled=true;
 record('GPS display after 181 seconds without refresh',{nativeCallsBefore:req,nativeCallsAfter:requests.filter(r=>r.type==='live').length,unchangedText:initial===$('busResult').textContent,stillSaysAgeZero:$('busResult').textContent.includes('sprzed 0 s'),stillConfirmed:$('busResult').textContent.includes('Pozycja GPS potwierdzona')});
 for(let i=0;i<3;i++)await check();assert.equal(resizing.size,2);record('map resize listeners bounded',{pass:true,count:resizing.size});
 await chooseStop('Rondo ONZ');await chooseLine('17').catch(async()=>{let o=[...$('busLine').options].find(x=>x.textContent.includes('Tramwaj'));$('busLine').value=o.value;$('busLine').dispatchEvent(new w.Event('change'));await until(()=>$('busDirection').options.length>1)});
 $('busDirection').selectedIndex=1;$('busDirection').dispatchEvent(new w.Event('change'));await check();record('tram map language',{selected:$('busLine').selectedOptions[0].textContent,mapTitle:doc.querySelector('.osm-toolbar strong')?.textContent,marker:doc.querySelector('.osm-bus')?.textContent});
 assert(doc.querySelector('.osm-toolbar strong').textContent.startsWith('Tramwaj'));assert(doc.querySelector('.osm-bus use').getAttribute('href')==='#pa-tram');
 $('tabAssistant').click();await ask('Z Ispiny do Bochni Dworzec PKP jutro o 9');
 const firstMessage=$('messages').lastElementChild,oldButton=[...firstMessage.querySelectorAll('button')].find(b=>b.textContent.includes('Wybieram'));
 if(!oldButton)throw Error('No first trip selection '+firstMessage.textContent);
 await ask('Z Tarchomina do Nowodwory jutro o 10');
 oldButton.click();record('historical chat journey selection',{oldMessage:firstMessage.textContent.slice(0,400),note:$('note').textContent.slice(0,700),correctOldRoute:$('note').textContent.includes('Start: Ispina Most')});
 assert($('note').textContent.includes('Start: Ispina Most'));assert($('note').textContent.includes('Cel: Bochnia Dworzec PKP'));assert(!doc.getElementById('aiOnline'));const ids=new Set([...doc.querySelectorAll('symbol')].map(x=>x.id));for(const u of doc.querySelectorAll('use'))assert(ids.has(u.getAttribute('href').slice(1)));assert.equal(errors.length,0);record('runtime errors',{errors});console.log(JSON.stringify(out,null,2));console.log('TOTAL PASS DOM immutable trips, LIVE expiry, map cleanup, tram icons, no inactive AI');d.window.close();
})().catch(e=>{console.error(e);d.window.close();process.exitCode=1});
