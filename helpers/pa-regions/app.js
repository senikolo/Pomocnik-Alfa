(function(){
"use strict";
const $=id=>document.getElementById(id);
const norm=s=>String(s||"").toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g,"").replace(/ł/g,"l").replace(/[^a-z0-9]+/g," ").trim().replace(/\s+/g," ");
const pad=n=>String(n).padStart(2,"0");
const localInput=date=>date.getFullYear()+"-"+pad(date.getMonth()+1)+"-"+pad(date.getDate())+"T"+pad(date.getHours())+":"+pad(date.getMinutes());
const minuteFormat=m=>pad(Math.floor(m/60)%24)+":"+pad(m%60)+(m>=1440?" (+1 dzień)":"");
const html=s=>String(s).replace(/[&<>"]/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c]));
const DATA={};let network="warsaw",start=null,end=null,generation=0,lastAnswer="";
$("departAt").value=localInput(new Date());
const aliases={mld:{"ispina":"ispina most","ispiny":"ispina most","ispini":"ispina most","nowe brzesko":"nowe brzesko rynek","bochnia pkp":"bochnia dworzec pkp","podleze pkp":"pkp podleze"},warsaw:{"dw centralny":"dw centralny","metro mlociny":"metro mlociny"}};
function canonical(n){let k=norm(n);return aliases[network]?.[k]||k}
function tab(name){const assistant=name==="assistant";$("tabPlanner").classList.toggle("active",!assistant);$("tabAssistant").classList.toggle("active",assistant);$("panelPlanner").classList.toggle("active",!assistant);$("panelAssistant").classList.toggle("active",assistant);$("tabPlanner").setAttribute("aria-selected",String(!assistant));$("tabAssistant").setAttribute("aria-selected",String(assistant));}
$("tabPlanner").onclick=()=>tab("planner");$("tabAssistant").onclick=()=>tab("assistant");
function groupIndex(idx){
 let map=new Map();
 idx.stops.forEach((s,i)=>{const k=norm(s[1]);if(!k)return;let g=map.get(k);if(!g){g={key:k,name:s[1],stops:[],routes:new Set()};map.set(k,g)}g.stops.push(i);(s[5]||[]).forEach(v=>g.routes.add(v))});
 if(idx.source==="mld" && (map.has("ispina")||map.has("ispina most"))){
   const most=map.get("ispina")||map.get("ispina most");
   const other=map.get("ispina most");
   // Merge both physical GTFS stop IDs. These names describe the same stop.
   if(other && other!==most){
     for(const sid of other.stops) if(!most.stops.includes(sid)) most.stops.push(sid);
     for(const line of other.routes) most.routes.add(line);
   }
   most.name="Ispina Most";
   for(const alias of ["ispina","ispina most","ispiny most","ispiny","ispini"])map.set(alias,most);
 }
 for(const [alias,to] of Object.entries(aliases[idx.source]||{})){if(!map.has(alias)&&map.has(to))map.set(alias,map.get(to))}
 idx.groups=map;return idx;
}
async function getData(provider){
 if(DATA[provider])return DATA[provider];
 const response=await fetch("/index_"+provider+".json",{cache:"no-store"});
 if(!response.ok)throw new Error("Brak danych rozkładowych: "+provider);
 const idx=groupIndex(await response.json());
 idx.routeCache=new Map();DATA[provider]=idx;return idx;
}
async function getRoute(idx,routeId){
 if(idx.routeCache.has(routeId))return idx.routeCache.get(routeId);
 const promise=fetch("/route_"+idx.source+"_"+routeId+".json").then(r=>{if(!r.ok)throw new Error("Brakuje danych linii "+routeId);return r.json()}).then(o=>o.trips||[]).catch(e=>{idx.routeCache.delete(routeId);throw e;});
 idx.routeCache.set(routeId,promise);
 return promise;
}
function matching(q){
 const idx=DATA[network];if(!idx)return[];
 const k=canonical(q);if(!k)return[];
 const uniq=new Map();for(const [key,g] of idx.groups){
 if(key.includes(k)&&!uniq.has(g.key))uniq.set(g.key,g);
 }
 return [...uniq.values()].sort((a,b)=>Number(!a.key.startsWith(k))-Number(!b.key.startsWith(k))||a.name.localeCompare(b.name,"pl")).slice(0,12);
}
function suggestions(input,list){
 const q=$(input).value;if(norm(q).length<2){$(list).classList.remove("open");return}
 const rows=matching(q);$(list).innerHTML=rows.map(g=>'<button type="button" data-stop="'+html(g.name)+'">'+html(g.name)+'</button>').join("")||"<p class='small'>Nie znaleziono przystanku w wybranej sieci.</p>";
 $(list).classList.add("open");
 $(list).querySelectorAll("button").forEach(b=>b.onclick=()=>{$(input).value=b.dataset.stop;$(list).classList.remove("open");$(input).focus()});
}
function onEdited(which){
 if(which==="from")start=null;else end=null;
 $("status"+(which==="from"?"From":"To")).textContent="";
 $("results").innerHTML="";
}
for(const [input,list] of [["from","suggestFrom"],["to","suggestTo"]]){
 $(input).oninput=()=>{onEdited(input);suggestions(input,list)};
 $(input).onfocus=()=>suggestions(input,list);
}
function resolved(text){
 const idx=DATA[network];if(!idx)return null;
 let key=canonical(text),g=idx.groups.get(key);
 if(g)return g;
 const candidates=matching(text);return candidates.length===1?candidates[0]:null;
}
function confirm(which){
 const field=$(which),out=$("status"+(which==="from"?"From":"To"));
 const group=resolved(field.value);
 if(!group){out.textContent="Wybierz pełną nazwę przystanku z podpowiedzi.";out.style.color="#ad3535";return;}
 field.value=group.name;out.textContent="✓ "+group.name+" – zatwierdzono";out.style.color="#096750";
 if(which==="from")start=group;else end=group;
 $("suggest"+(which==="from"?"From":"To")).classList.remove("open");
 if(start&&end)search();
}
$("confirmFrom").onclick=()=>confirm("from");$("confirmTo").onclick=()=>confirm("to");
$("changeFrom").onclick=()=>{$("from").readOnly=false;onEdited("from");$("from").focus()};
$("changeTo").onclick=()=>{$("to").readOnly=false;onEdited("to");$("to").focus()};
$("swap").onclick=()=>{const v=$("from").value;$("from").value=$("to").value;$("to").value=v;const tmp=start;start=end;end=tmp;$("statusFrom").textContent=start?"✓ "+start.name+" – zatwierdzono":"";$("statusTo").textContent=end?"✓ "+end.name+" – zatwierdzono":"";if(start&&end)search()};
$("departAt").onchange=()=>{if(start&&end)search()};
$("search").onclick=search;
$("network").onchange=()=>{network=$("network").value;start=end=null;generation++;for(const id of ["from","to","statusFrom","statusTo"])$(id).value!==undefined?$(id).value="":$(id).textContent="";$("results").innerHTML="";initialize()};
function prepare(date,idx){
 if(!date||isNaN(date.getTime()))throw new Error("Wybierz poprawną datę i godzinę.");
 const day=date.getFullYear()+"-"+pad(date.getMonth()+1)+"-"+pad(date.getDate());
 const di=idx.days.indexOf(day);
 if(di<0||!idx.covered[di])throw new Error("Brak potwierdzonych danych rozkładu na "+day+". Wybierz dzień objęty aktualną bazą. Nie wyświetlam niezweryfikowanych kursów.");
 return {day:di,minute:date.getHours()*60+date.getMinutes(),date};
}
const groupOf=(index,sid)=>norm(index.stops[sid]?.[1]);
function oneTrip(points,src,dst,minDepart){
 let board=null;
 for(let i=0;i<points.length;i++){
  const s=points[i];if(src.has(s[0])&&s[1]>=minDepart&&(board===null||s[1]<board[1])) board=[i,s[1],s[0]];
  if(board&&i>board[0]&&dst.has(s[0])&&s[1]>=board[1])
   return {dep:board[1],arr:s[1],from:board[2],to:s[0]};
 }
 return null;
}
function appendFirst(idx,grp,rec){let rows=grp.get(rec.key);if(!rows)grp.set(rec.key,rows=[]);
 rows.push(rec);rows.sort((a,b)=>a.arr-b.arr);if(rows.length>16)rows.length=16;
}
async function findTrips(idx,from,to,when){
 const valid=prepare(when,idx);
 if(from.key===to.key)throw new Error("Początek i cel podróży są takie same.");
 const aSet=new Set(from.stops),bSet=new Set(to.stops),mask=1<<valid.day;
 const startLines=[...from.routes],endLines=[...to.routes];
 if(!startLines.length||!endLines.length)return [];
 const needed=new Set([...startLines,...endLines]);
 const loaded=new Map(await Promise.all([...needed].map(async line=>[line,await getRoute(idx,line)])));
 const direct=[],first=new Map(),matches=[];
 // A candidate first leg must board at the selected origin after the selected time.
 for(const line of startLines){
  for(const [days,headsign,points] of loaded.get(line)||[]){
   if(!(days&mask))continue;
   let board=-1;
   for(let i=0;i<points.length;i++){
    const [sid,minute]=points[i];
    if(board<0 && aSet.has(sid) && minute>=valid.minute && minute<=valid.minute+180){board=i;continue}
    if(board>=0&&i>board){
     let [originSid,dep]=points[board];
     if(bSet.has(sid)&&minute>=dep){
      direct.push({kind:"direct",legs:[{line:idx.routes[line][1],headsign,dep,arr:minute,from:idx.stops[originSid][1],to:idx.stops[sid][1]}],dep,arr:minute});break;
     }
     if(minute>dep+120)break;
     if(minute>=dep+3){
      const key=groupOf(idx,sid);
      appendFirst(idx,first,{key,line,headsign,dep,arr:minute,from:idx.stops[originSid][1],to:idx.stops[sid][1]});
     }
    }
   }
  }
 }
 // A transfer must occur at a documented GTFS stop with the same stop name.
 // Five minutes is the minimum connection; no imaginary cross-city connection.
 for(const line of endLines){
  for(const [days,headsign,points] of loaded.get(line)||[]){
   if(!(days&mask))continue;
   let last=-1;
   for(let j=0;j<points.length;j++){
    if(bSet.has(points[j][0])){last=j;break}
   }
   if(last<1)continue;
   const destArr=points[last][1];if(destArr<=valid.minute||destArr>valid.minute+360)continue;
   for(let i=0;i<last;i++){
    const [sid,dep2]=points[i], key=groupOf(idx,sid),incoming=first.get(key);
    if(!incoming)continue;
    for(const leg of incoming){
     if(leg.line===line)continue;
     const wait=dep2-leg.arr;
     if(wait<5||wait>60)continue;
     matches.push({kind:"transfer",wait,dep:leg.dep,arr:destArr,legs:[
      {line:idx.routes[leg.line][1],headsign:leg.headsign,dep:leg.dep,arr:leg.arr,from:leg.from,to:leg.to},
      {line:idx.routes[line][1],headsign,dep:dep2,arr:destArr,from:idx.stops[sid][1],to:idx.stops[points[last][0]][1]}
     ]});
    }
   }
  }
 }
 const seen=new Set();return direct.concat(matches).filter(x=>{
  const key=x.legs.map(l=>[l.line,l.dep,l.arr,l.from,l.to].join("|")).join("/");
  if(seen.has(key))return false;seen.add(key);return true;
 }).sort((a,b)=>Number(a.kind==="transfer")-Number(b.kind==="transfer")||a.arr-b.arr||a.dep-b.dep).slice(0,24);
}
function resultCard(r){
 const legs=r.legs;
 const steps=legs.map((l,i)=>'<div><span class="chip">Linia '+html(l.line)+'</span> <span class="time">'+minuteFormat(l.dep)+' → '+minuteFormat(l.arr)+'</span><div class="small">'+html(l.from)+' → '+html(l.to)+' · kierunek '+html(l.headsign||"według rozkładu")+'</div></div>').join(r.kind==="transfer"?'<p class="small">⇄ Przesiadka: '+r.wait+' min (ten sam zespół przystankowy; sprawdź stanowisko).</p>':"");
 return '<article class="trip '+(r.kind==="transfer"?"transfer":"")+'"><b>'+(r.kind==="direct"?"Bez przesiadki":"1 przesiadka")+'</b><p class="small">Podróż '+(r.arr-r.dep)+' min · przyjazd '+minuteFormat(r.arr)+'</p>'+steps+'</article>';
}
async function search(){
 const seq=++generation;
 if(!start||!end){$("progress").textContent="Zatwierdź początek i cel przyciskiem Akceptuj.";return;}
 $("progress").textContent="Sprawdzam rzeczywiste kursy…";$("results").innerHTML="";
 try{
  const idx=await getData(network),trips=await findTrips(idx,start,end,new Date($("departAt").value));
  if(seq!==generation)return;
  $("progress").textContent=trips.length?"Znaleziono "+trips.length+" połączeń · wyłącznie rozkład planowy.":"Brak potwierdzonych kursów w tym oknie czasowym.";
  $("results").innerHTML=trips.map(resultCard).join("");
 }catch(e){if(seq===generation){$("progress").textContent=e.message;$("results").innerHTML="";}}
}
async function initialize(){
 $("coverage").textContent="Odczytuję rozkład z pakietu…";
 try{
 const idx=await getData(network);
 $("coverage").textContent="Baza: "+(network==="warsaw"?"autobusy Warszawy":"Małopolskie Linie Dowozowe")+
 " · ważne daty: "+idx.days.filter((v,i)=>idx.covered[i]).join(", ")+". Tylko godziny z rozkładu, bez fikcyjnego LIVE.";
 }catch(e){$("coverage").textContent="Nie udało się odczytać rozkładu: "+e.message}
}
function bubble(content,who){let div=document.createElement("div");div.className="bubble "+who;div.textContent=content;$("messages").append(div);$("messages").scrollTop=$("messages").scrollHeight;}
function extractStop(idx,text){
 const raw=norm(text),options=[...new Set([...idx.groups.values()])].sort((a,b)=>b.key.length-a.key.length);
 // Check longer stop names first and match word boundaries.
 for(const g of options){if(g.key.length<4)continue;
  if((" "+raw+" ").includes(" "+g.key+" "))return g;
 }
 return null;
}
const dialogue={from:null,to:null,when:null,fromHint:"",toHint:""};
function segmentKey(value){
 let k=norm(value);
 k=k.replace(/^ispiny\b/,"ispina").replace(/^ispini\b/,"ispina");
 k=k.replace(/^bochni\b/,"bochnia").replace(/^warszawy\b/,"warszawa");
 k=k.replace(/^krakowa\b/,"krakow").replace(/^niepolomic\b/,"niepolomice");
 k=k.replace(/^nowego brzeska\b/,"nowe brzesko");
 return k;
}
function identifyStop(idx,text){
 const k=segmentKey(text),canonicalKey=canonical(k);
 let g=idx.groups.get(canonicalKey)||idx.groups.get(k);
 if(g)return {group:g,candidates:[]};
 const options=[...new Set(idx.groups.values())].filter(x=>x.key.startsWith(k+" ")||x.key===k);
 if(options.length===1)return {group:options[0],candidates:[]};
 return {group:null,candidates:options.slice(0,8).map(x=>x.name)};
}
function parseRequestDate(query){
 const lower=norm(query);
 let date=dialogue.when?new Date(dialogue.when):new Date($("departAt").value);
 if(/\bjutro\b/.test(lower)){date=new Date();date.setDate(date.getDate()+1);}
 else if(/\b(dzis|dzisiaj)\b/.test(lower))date=new Date();
 const m=query.match(/(?:\bpo\b|\bo\b|\bokoło\b|\bokolo\b)\s*(\d{1,2})(?:[:.](\d{2}))?/i);
 if(m){let h=Number(m[1]),min=Number(m[2]||0);if(h<=23&&min<=59)date.setHours(h,min,0,0)}
 return date;
}
async function assistant(){
 const question=$("chatInput").value.trim();if(!question)return;
 bubble(question,"user");$("chatInput").value="";
 const lower=norm(question);
 if(/^(hej|czesc|dzien dobry|dobry wieczor|witaj)[!. ]*$/.test(lower)){
  lastAnswer="Cześć! W czym mogę pomóc z przejazdem? Podaj trasę albo zapytaj o konkretny przystanek.";
  bubble(lastAnswer,"assistant");return;
 }
 if(/^(dzieki|dziekuje|super|ok|okej)[!. ]*$/.test(lower)){
  lastAnswer="Nie ma za co! Gdy zechcesz sprawdzić kolejną trasę, napisz mi skąd, dokąd i kiedy jedziemy.";
  bubble(lastAnswer,"assistant");return;
 }
 const isMld=/\b(mld|malopols\w*|krakow\w*|ispin\w*|bochn\w*|niepolomic\w*|a4|a7|a65)\b/.test(lower);
 const isWarsaw=/\b(warszaw\w*|ztm|z(p|t)m|m[12]|waw)\b/.test(lower);
 if(isMld&&!isWarsaw&&network!=="mld"){$("network").value="mld";$("network").onchange();dialogue.from=dialogue.to=null}
 else if(isWarsaw&&!isMld&&network!=="warsaw"){$("network").value="warsaw";$("network").onchange();dialogue.from=dialogue.to=null}
 const date=parseRequestDate(question);dialogue.when=date;
 let idx;
 try{idx=await getData(network)}catch(e){lastAnswer="Nie udało się wczytać rozkładów: "+e.message;bubble(lastAnswer,"assistant");return}
 if(/gdzie.*(autobus|bus)|pozycja.*(autobus|bus)|gps/.test(lower)){
  lastAnswer="W tym Planerze pokazuję godziny rozkładowe. Lokalizację LIVE mogę potwierdzić tylko, gdy dostępny jest świeży pomiar GPS konkretnego autobusu. Napisz, o którą linię, kierunek i przystanek chodzi.";
  bubble(lastAnswer,"assistant");return;
 }
 let from=null,to=null;
 const timeEnding="(?=\\s+(?:jutro|dzisiaj|dziś|dzis|po\\s+\\d|o\\s+\\d|około\\s+\\d|okolo\\s+\\d)|[.!?]|$)";
 const pair=question.match(new RegExp("(?:^|\\s)(?:z|ze)\\s+(.+?)\\s+(?:do|na)\\s+(.+?)"+timeEnding,"i"));
 const reverse=pair?null:question.match(new RegExp("(?:^|\\s)(?:do|na)\\s+(.+?)\\s+(?:z|ze)\\s+(.+?)"+timeEnding,"i"));
 if(pair||reverse){
  const origin=pair?pair[1]:reverse[2],destination=pair?pair[2]:reverse[1];
  const a=identifyStop(idx,origin),b=identifyStop(idx,destination);
  dialogue.fromHint=origin;dialogue.toHint=destination;
  dialogue.from=a.group;dialogue.to=b.group;
  dialogue.fromCandidates=a.candidates;dialogue.toCandidates=b.candidates;
 }else{
  // A follow-up may supply only one of the two missing stops.
  const fromPart=question.match(new RegExp("(?:^|\\s)(?:z|ze)\\s+(.+?)"+timeEnding,"i"));
  const toPart=question.match(new RegExp("(?:^|\\s)(?:do|na)\\s+(.+?)"+timeEnding,"i"));
  if(fromPart&&!toPart){const a=identifyStop(idx,fromPart[1]);dialogue.from=a.group;dialogue.fromHint=fromPart[1];dialogue.fromCandidates=a.candidates;}
  else if(toPart&&!fromPart){const b=identifyStop(idx,toPart[1]);dialogue.to=b.group;dialogue.toHint=toPart[1];dialogue.toCandidates=b.candidates;}
  else{
   const identified=identifyStop(idx,question);
   if(identified.group){
    if(!dialogue.from)dialogue.from=identified.group;
    else if(!dialogue.to)dialogue.to=identified.group;
   }
  }
 }
 from=dialogue.from||(!dialogue.fromHint?start:null);
 to=dialogue.to||(!dialogue.toHint?end:null);
 if(!from||!to){
  let lines=[];
  if(!from)lines.push("Podaj konkretny przystanek początkowy"+(dialogue.fromHint?" dla „"+dialogue.fromHint+"”":"")+".");
  if(!to)lines.push("Podaj konkretny przystanek docelowy"+(dialogue.toHint?" dla „"+dialogue.toHint+"”":"")+".");
  const suggestions=[...new Set([...(dialogue.fromCandidates||[]),...(dialogue.toCandidates||[])])].slice(0,8);
  if(suggestions.length)lines.push("Dostępne przystanki: "+suggestions.join(", ")+".");
  if(dialogue.when)lines.push("Datę i godzinę już zapamiętałem: "+localInput(dialogue.when).replace("T"," ")+".");
  lastAnswer=lines.join("\n");bubble(lastAnswer,"assistant");return;
 }
 start=from;end=to;$("from").value=from.name;$("to").value=to.name;
 $("statusFrom").textContent="✓ "+from.name+" – zatwierdzono";$("statusTo").textContent="✓ "+to.name+" – zatwierdzono";
 $("departAt").value=localInput(date);
 try{
  const results=await findTrips(idx,from,to,date);
  const describe=r=>r.legs.map(l=>"linia "+l.line+" ("+minuteFormat(l.dep)+"–"+minuteFormat(l.arr)+", "+l.from+" → "+l.to+")").join("; ");
  lastAnswer=results.length
   ?"Z "+from.name+" do "+to.name+" od "+pad(date.getHours())+":"+pad(date.getMinutes())+":\n"+results.slice(0,3).map((r,i)=>(i+1)+". "+describe(r)+(r.kind==="transfer"?", przesiadka "+r.wait+" min":" bez przesiadki")).join("\n")+"\nTo godziny planowe GTFS. Nie są to odjazdy GPS LIVE."
   :"Nie znalazłem potwierdzonego kursu między tymi przystankami w wybranym czasie. Sprawdź inną datę lub pobliski przystanek.";
  bubble(lastAnswer,"assistant");
 }catch(e){lastAnswer=e.message;bubble(lastAnswer,"assistant")}
}
$("ask").onclick=assistant;
$("copy").onclick=()=>{if(!lastAnswer)return; if(navigator.clipboard&&navigator.clipboard.writeText)navigator.clipboard.writeText(lastAnswer).catch(()=>{});};
document.querySelectorAll(".prompt").forEach(b=>b.onclick=()=>{$("chatInput").value=b.dataset.query;});
initialize();
})();