/* Pure timetable engine. No AI calls or generated departures. Seconds since service midnight. */
(function(root){'use strict';
const norm=s=>String(s||'').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/ł/g,'l').replace(/[^a-z0-9]+/g,' ').trim().replace(/\s+/g,' ');
const dayOf=d=>d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');
function distance(a,b){if(!a[3]||!b[3])return Infinity;const rad=Math.PI/180,x=(a[4]-b[4])*rad*Math.cos((a[3]+b[3])*rad/2),y=(a[3]-b[3])*rad;return 6371000*Math.hypot(x,y)}
const category=t=>t===1||t>=400&&t<500?'metro':t===0||t>=900&&t<1000?'tram':t===2||t>=100&&t<200?'rail':'bus';
const canonical=s=>norm(s).replace(/^ispin[yi]\b/,'ispina').replace(/^bochni\b/,'bochnia').replace(/^warszaw(?:y|ie)\b/,'warszawa').replace(/^krakow(?:a|ie)\b/,'krakow').replace(/^tarchomin(?:a|ie)\b/,'tarchomin').replace(/^nowodwor(?:ow|ach)\b/,'nowodwory').replace(/^winnic(?:y|e)\b/,'winnica').replace(/^szczesliwic(?:ach|e)?\b/,'szczesliwice').replace(/^grobli\b/,'grobla').replace(/^proszowic\b/,'proszowice').replace(/^niepolomic\b/,'niepolomice').replace(/^podleza\b/,'podleze').replace(/^nowego brzeska\b/,'nowe brzesko').replace(/^wieliczki\b/,'wieliczka').replace(/^legionowa\b/,'legionowo').replace(/^tarnowa\b/,'tarnow').replace(/\bglownego\b/,'glowny').replace(/\bwschodniej\b/,'wschodnia').replace(/\bzachodniej\b/,'zachodnia').replace(/\blotniska\b/,'lotnisko').replace(/lesniczowki\b/,'lesniczowka').replace(/dworzec centralny|dworca centralnego|dworzec centralnego/g,'dw centralny').replace(/dworzec pkp bochnia/,'bochnia dworzec pkp');
class Engine{
 constructor(idx,loader){this.idx=idx;this.loader=loader;this.cache=new Map();this.groups=new Map();this.grid=new Map();this.walkCache=new Map();this.explicit=new Map();
  const buckets=new Map(),network=s=>s==='warsaw'?'Warszawa':s.startsWith('krakow')?'Kraków':s==='mld'?'MLD':s==='kmlrail'?'Koleje Małopolskie':s==='wkd'?'WKD':'kolej';
  idx.stops.forEach((s,i)=>{const base=canonical(s[1])==='ispina'&&s[6]==='mld'?'ispina most':canonical(s[1]);let list=buckets.get(base);if(!list){list=[];buckets.set(base,list)}let g=list.find(x=>distance(idx.stops[x.stops[0]],s)<=800);if(!g){g={key:base,name:base==='ispina most'?'Ispina Most':s[1],stops:[],routes:new Set()};list.push(g)}g.stops.push(i);s[5].forEach(r=>g.routes.add(r));
   const k=this.cell(s[3],s[4]);if(!this.grid.has(k))this.grid.set(k,[]);this.grid.get(k).push(i);
  });
  this.ambiguous=new Map();
  const rail=idx.stops.filter(s=>s[6]==='polishrail');
  for(const [base,list] of buckets){const areas=list.map(g=>network(idx.stops[g.stops[0]][6]));for(let n=0;n<list.length;n++){const g=list[n],stop=idx.stops[g.stops[0]];
   if(list.length>1){let label=areas[n];if(areas.filter(x=>x===label).length>1){let near=null,metres=Infinity;for(const station of rail){if(canonical(station[1])===base)continue;const d=distance(stop,station);if(d<metres){metres=d;near=station}}label+=near&&metres<25000?' · rejon stacji '+near[1]:' · '+stop[3].toFixed(3)+', '+stop[4].toFixed(3)}g.name+=' · '+label;g.key=canonical(g.name)}
   if(this.groups.has(g.key)){g.name+=' · '+stop[0];g.key=canonical(g.name)}this.groups.set(g.key,g);
  }if(list.length>1)this.ambiguous.set(base,list)}
  if(this.groups.has('ispina most'))this.groups.set('ispina',this.groups.get('ispina most'));
  for(const [a,b] of [['bochnia pkp','bochnia dworzec pkp'],['podleze pkp','pkp podleze'],['warszawa centralna','warszawa centralna'],['dw centralny','dw centralny'],['metro centrum','centrum']])if(this.groups.has(b))this.groups.set(a,this.groups.get(b));
  (idx.transfers||[]).forEach(t=>this.explicit.set(t[0]+':'+t[1],t));
  this.routeStops=idx.routes.map(()=>new Set());idx.stops.forEach((s,i)=>s[5].forEach(r=>this.routeStops[r].add(i)));
 }
 cell(lat,lon){return Math.floor(lat/.004)+':'+Math.floor(lon/.006)}
 nearby(sid,max=400){const a=this.idx.stops[sid],key=sid+':'+max;if(this.walkCache.has(key))return this.walkCache.get(key);const x=Math.floor(a[3]/.004),y=Math.floor(a[4]/.006),out=[];
  for(let dx=-2;dx<=2;dx++)for(let dy=-2;dy<=2;dy++)for(const j of this.grid.get((x+dx)+':'+(y+dy))||[]){if(j===sid)continue;const b=this.idx.stops[j],d=distance(a,b),rule=this.explicit.get(sid+':'+j);if(rule&&rule[2]===3)continue;if(d<=max){const sec=Math.max(rule?rule[3]:0,Math.ceil(d*1.35/1.15)+60);out.push([j,sec,Math.round(d*1.35)])}}
  this.walkCache.set(key,out);return out;
 }
 match(q,limit=16){const k=canonical(q);if(k.length<3)return[];const unique=[...new Set(this.groups.values())];return unique.filter(g=>canonical(g.name).includes(k)||g.key.includes(k)).sort((a,b)=>Number(!a.key.startsWith(k))-Number(!b.key.startsWith(k))||a.name.localeCompare(b.name,'pl')).slice(0,limit)}
 resolve(q){let k=canonical(q).replace(/^(?:przystanek|przystanku|stacja|stacji)\s+/,'');if(k==='ispina')k='ispina most';if(k==='metro centrum'&&this.groups.has('centrum'))k='centrum';const exact=this.groups.get(k);if(exact)return{group:exact,candidates:[]};if(this.ambiguous.has(k))return{group:null,candidates:this.ambiguous.get(k)};
  // Station aliases are allowed only if they lead to exactly one indexed group.
  const aliases={'dw centralny':'warszawa centralna','bochnia pkp':'bochnia dworzec pkp','podleze pkp':'pkp podleze'};
  if(aliases[k]&&this.groups.has(aliases[k]))return{group:this.groups.get(aliases[k]),candidates:[]};
  const c=this.match(k,20);if(c.length===1)return{group:c[0],candidates:[]};
  if(!c.length&&k.length>=6){const terms=k.split(' ');const fuzzy=[...new Set(this.groups.values())].filter(g=>{const n=g.key;if(Math.abs(n.length-k.length)>2)return false;let a=0,b=0,e=0;while(a<n.length&&b<k.length){if(n[a]===k[b]){a++;b++;continue}if(++e>1)return false;if(n.length>k.length)a++;else if(k.length>n.length)b++;else{a++;b++}}return e+(n.length-a)+(k.length-b)<=1});return{group:null,candidates:fuzzy.slice(0,6),correction:true}}
  return{group:null,candidates:c};
 }
 async route(id){if(this.cache.has(id))return this.cache.get(id);const p=this.loader(id).catch(e=>{this.cache.delete(id);throw e});this.cache.set(id,p);if(this.cache.size>70)this.cache.delete(this.cache.keys().next().value);return p}
 valid(when){const date=when.slice(0,10),day=this.idx.days.indexOf(date);if(day<0||!this.idx.covered[day])throw Error('Brak potwierdzonego rozkładu na '+date+'. Odśwież dane lub wybierz dzień objęty bazą.');const [h,m]=when.slice(11).split(':').map(Number);if(!(h>=0&&h<=23&&m>=0&&m<=59))throw Error('Wybierz poprawną godzinę.');return{day,time:h*3600+m*60}}
 runs(data,day,min,max){const out=[];for(const r of data.runs||[]){for(const shift of [-1,0,1]){const d=day+shift;if(d<0||d>=this.idx.days.length||!(r[0]&(1<<d)))continue;const pattern=data.patterns[r[2]],base=r[3]+shift*86400;if(!pattern.length||(r[6]?r[7]+shift*86400:base)+pattern.at(-1)[2]<min||base>max)continue;out.push({id:r[4],headsign:r[1],short:r[5],headway:r[6]||0,frequencyEnd:r[7]?r[7]+shift*86400:0,base,pattern,day:this.idx.days[d]})}}return out.sort((a,b)=>a.base-b.base)}
 add(map,sid,label){let list=map.get(sid)||[];if(list.some(x=>x.arr<=label.arr&&x.dep>=label.dep))return false;list=list.filter(x=>!(label.arr<=x.arr&&label.dep>=x.dep));list.push(label);list.sort((a,b)=>a.arr-b.arr);if(list.length>12)list.length=12;map.set(sid,list);return list.includes(label)}
 walk(map,max){const result=new Map([...map].map(([k,v])=>[k,[...v]]));for(const [sid,labels] of map)for(const [to,sec,metres] of this.nearby(sid,max))for(const l of labels){if(l.walked+metres>max*1.5)continue;this.add(result,to,{...l,arr:l.arr+sec,walked:l.walked+metres,path:l.path.concat({walk:true,fromId:sid,toId:to,dep:l.arr,arr:l.arr+sec,metres,estimated:true})})}return result}
 async find(from,to,when,opts={}){
  const {day,time}=this.valid(when);if(from.key===to.key)throw Error('Początek i cel są takie same.');const minWait=Math.max(60,Math.min(3600,(opts.minTransfer||5)*60)),maxWalk=opts.walk===false?0:400,maxRounds=Math.min(4,(opts.maxTransfers??2)+1),horizon=time+24*3600;
  let prev=new Map();for(const sid of from.stops)prev.set(sid,[{arr:time,dep:time,path:[],walked:0}]);if(maxWalk)prev=this.walk(prev,maxWalk);
  const targets=new Set(to.stops),found=[];let cancelled=()=>opts.cancelled&&opts.cancelled();
  // Reverse topological reachability prunes lines that cannot reach destination in remaining rounds.
  const allowed=[new Set()];for(const sid of to.stops){for(const r of this.idx.stops[sid][5])allowed[0].add(r);if(maxWalk)for(const [j]of this.nearby(sid,maxWalk))this.idx.stops[j][5].forEach(r=>allowed[0].add(r))}
  for(let i=1;i<maxRounds;i++){const set=new Set(allowed[i-1]);for(const rid of allowed[i-1])for(const sid of this.routeStops[rid]){this.idx.stops[sid][5].forEach(r=>set.add(r));if(maxWalk)for(const [j]of this.nearby(sid,maxWalk))this.idx.stops[j][5].forEach(r=>set.add(r))}allowed.push(set)}
  for(let round=0;round<maxRounds;round++){
   if(cancelled())return[];const eligible=allowed[maxRounds-round-1],routes=new Set();for(const sid of prev.keys())this.idx.stops[sid][5].forEach(r=>{if(eligible.has(r)&&(!opts.modes||opts.modes.includes(this.idx.routes[r][3])))routes.add(r)});
   const next=new Map(),ids=[...routes];let at=0;
   while(at<ids.length){const batch=ids.slice(at,at+5);at+=5;const loaded=await Promise.all(batch.map(async id=>[id,await this.route(id)]));if(cancelled())return[];
    for(const [rid,data]of loaded)for(const run of this.runs(data,day,time,horizon)){
     if(run.headway)continue; // Frequency-only service cannot promise a particular connection.
     let boarded=[];
     for(let pi=0;pi<run.pattern.length;pi++){
      const p=run.pattern[pi],sid=p[0],arr=run.base+p[2],dep=run.base+p[1];
      if(p[4]!==1)for(const b of boarded){if(pi<=b.pi||arr<b.departure)continue;const leg={line:this.idx.routes[rid][1],mode:this.idx.routes[rid][3],agency:this.idx.routes[rid][4],source:this.idx.routes[rid][5],operator:this.idx.routes[rid][6]||this.idx.routes[rid][4],trip:run.id,serviceDay:run.day,headsign:run.headsign,train:run.short,fromId:b.sid,toId:sid,dep:b.departure,arr,from:this.idx.stops[b.sid][1],to:this.idx.stops[sid][1],platformFrom:this.idx.stops[b.sid][2],platformTo:this.idx.stops[sid][2],pickupType:b.pickupType,dropOffType:p[4]};this.add(next,sid,{arr,dep:b.previous.path.some(x=>!x.walk)?b.previous.dep:b.departure-b.previous.path.reduce((sum,x)=>sum+x.arr-x.dep,0),walked:b.previous.walked,path:b.previous.path.concat(leg)})}
      if(p[3]===1)continue;
      for(const l of prev.get(sid)||[]){const rides=l.path.filter(x=>!x.walk),last=rides.at(-1);if(last&&last.trip===run.id&&last.source===this.idx.routes[rid][5])continue;let wait=rides.length?minWait:0;const lastStep=l.path.at(-1);if(lastStep&&lastStep.walk&&rides.length)wait=Math.max(0,minWait-(lastStep.arr-lastStep.dep));if((!rides.length||dep-last.arr<=7200)&&dep>=l.arr+wait&&dep<=horizon){const b={pi,sid,departure:dep,previous:l,pickupType:p[3]};if(!boarded.some(x=>x.previous.dep>=l.dep&&x.departure>=dep))boarded.push(b);if(boarded.length>5)boarded.shift()}}
     }
    }
    if(opts.progress)opts.progress(round+1,Math.min(100,Math.round(at/Math.max(ids.length,1)*100)));
    await new Promise(r=>setTimeout(r,0));
   }
   const reached=maxWalk?this.walk(next,maxWalk):next;
   for(const sid of targets)for(const l of reached.get(sid)||[]){const legs=l.path.filter(x=>!x.walk);if(!legs.length)continue;const path=l.path.map(x=>({...x})),firstRide=path.findIndex(x=>!x.walk),access=path.slice(0,firstRide).reduce((sum,x)=>sum+x.arr-x.dep,0),dep=legs[0].dep-access;let cursor=dep;for(let i=0;i<firstRide;i++){const span=path[i].arr-path[i].dep;path[i].dep=cursor;path[i].arr=cursor+span;cursor+=span}found.push({legs:path,dep,arr:l.arr,transfers:legs.length-1,duration:l.arr-dep,wait:path.reduce((a,x,i,p)=>a+(i?Math.max(0,x.dep-p[i-1].arr):0),0),date:when.slice(0,10)})}
   prev=reached;if(!prev.size)break;
  }

  // A different footpath between platforms must not create another copy of the same train.
  // Keep real alternatives (different vehicles/departure times), not inflated walk variants.
  const unique=new Map();
  for(const r of found){
   const rides=r.legs.filter(l=>!l.walk);
   if(!rides.length||r.arr<r.dep||r.dep<time||r.legs.some((l,i)=>l.arr<l.dep||(i&&l.dep<r.legs[i-1].arr)))continue;
   if(opts.mustLine&&!rides.some(l=>norm(l.line)===norm(opts.mustLine)))continue;
   const key=rides.map(l=>l.source+':'+l.trip+':'+l.serviceDay+':'+l.dep+':'+l.fromId+':'+l.toId).join('|');
   r.walkMetres=r.legs.filter(l=>l.walk).reduce((n,l)=>n+l.metres,0);
   const previous=unique.get(key);
   if(!previous||r.arr<previous.arr||(r.arr===previous.arr&&r.walkMetres<previous.walkMetres))unique.set(key,r);
  }
  const choices=[...unique.values()];
  // Pareto: never offer an earlier start, later arrival, more changes and more walking
  // when a strictly better actual itinerary exists.
  const relevant=choices.filter((r,i)=>!choices.some((other,j)=>i!==j&&other.arr<=r.arr&&other.dep>=r.dep&&other.transfers<=r.transfers&&other.walkMetres<=r.walkMetres&&(other.arr<r.arr||other.dep>r.dep||other.transfers<r.transfers||other.walkMetres<r.walkMetres)));
  const fastest=Math.min(...relevant.map(r=>r.duration));
  // A connection taking many times longer than a valid nearby alternative is not useful;
  // retain legitimate longer direct railway services with a relative + absolute allowance.
  const maxUseful=fastest===Infinity?Infinity:Math.max(fastest*3,fastest+45*60);
  const useful=relevant.filter(r=>r.duration<=maxUseful);
  const direct=useful.filter(r=>r.transfers===0).sort((a,b)=>a.dep-b.dep);
  const frequentDirect=direct.length>=3&&direct[2].dep-direct[0].dep<=45*60;
  const shortestDirect=direct.length?Math.min(...direct.map(r=>r.duration)):Infinity;
  return this.sort(useful.filter(r=>!frequentDirect||r.transfers===0||r.duration<=shortestDirect+10*60),opts.sort).slice(0,24)
 }
 sort(rows,sort='arrival'){return rows.sort((a,b)=>sort==='duration'?a.duration-b.duration||a.arr-b.arr:sort==='transfers'?a.transfers-b.transfers||a.arr-b.arr:a.arr-b.arr||a.transfers-b.transfers||b.dep-a.dep)}
 async arrivals(origin,when,limit=36){const {day,time}=this.valid(when),out=[],selected=new Set(origin.stops);
  for(const id of origin.routes){const data=await this.route(id),meta=this.idx.routes[id];for(const r of this.runs(data,day,time,time+86400)){if(r.headway)continue;for(let i=1;i<r.pattern.length;i++){const p=r.pattern[i],arr=r.base+p[2];if(!selected.has(p[0])||p[4]===1||arr<time||arr>time+86400)continue;out.push({line:meta[1],headsign:this.idx.stops[r.pattern[0][0]][1],dep:arr,trip:r.id,source:meta[5],serviceDay:r.day,stopId:this.idx.stops[p[0]][0],mode:meta[3],operator:meta[6]||meta[4],train:r.short,queryDay:when.slice(0,10),arrival:true});}}}
  return out.sort((a,b)=>a.dep-b.dep).slice(0,limit);
 }
 async departures(origin,when,line,direction,limit=12,routeIds=null,lookback=0){const {day,time}=this.valid(when),out=[],earliest=time-Math.max(0,Math.min(3600,Number(lookback)||0));const selected=new Set(origin.stops),allowed=routeIds?new Set(routeIds):null,rids=[...origin.routes].filter(id=>(!allowed||allowed.has(id))&&(!line||norm(this.idx.routes[id][1])===norm(line)));for(const id of rids){const data=await this.route(id);for(const r of this.runs(data,day,earliest,time+86400)){if(direction&&!norm(r.headsign).includes(canonical(direction)))continue;for(const p of r.pattern){const dep=r.base+p[1];if(selected.has(p[0])&&p[3]!==1&&(dep>=earliest||r.headway&&r.frequencyEnd+p[1]>=earliest)&&dep<=time+86400){out.push({line:this.idx.routes[id][1],headsign:r.headsign,dep,trip:r.id,source:this.idx.routes[id][5],serviceDay:r.day,stopId:this.idx.stops[p[0]][0],stop:this.idx.stops[p[0]][1],platform:this.idx.stops[p[0]][2],pickupType:p[3],mode:this.idx.routes[id][3],operator:this.idx.routes[id][6]||this.idx.routes[id][4],train:r.short,headway:r.headway,frequencyEnd:r.frequencyEnd+p[1],intermediate:r.pattern.slice(r.pattern.indexOf(p)+1).map(x=>({name:this.idx.stops[x[0]][1],arrival:r.base+x[2]})),queryDay:when.slice(0,10)});break}}}}return out.sort((a,b)=>a.dep-b.dep).slice(0,Math.max(1,Math.min(72,limit)))}
}
root.PATransit={Engine,norm,canonical,distance,dayOf,category};if(typeof module!=='undefined')module.exports=root.PATransit;
})(typeof globalThis!=='undefined'?globalThis:this);
