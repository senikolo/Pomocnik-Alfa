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
const groupOf=(index,sid)=>{const key=norm(index.stops[sid]?.[1]);return index.groups.get(key)?.key||key;};
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
const dialogue={from:null,to:null,when:null,fromHint:"",toHint:"",fromCandidates:[],toCandidates:[],asked:"",line:null,lastResults:[],intent:"route"};
function segmentKey(value){
 let k=norm(value).replace(/^(?:przystanek|przystanku|stacja|stacji|z|ze|do|na)\s+/,"");
 for(const [a,b] of [
  [/^ispiny\b/,"ispina"],[/^ispini\b/,"ispina"],[/^bochni\b/,"bochnia"],
  [/^warszawy\b/,"warszawa"],[/^krakowa\b/,"krakow"],[/^niepolomic\b/,"niepolomice"],
  [/^nowego brzeska\b/,"nowe brzesko"],[/^proszowic\b/,"proszowice"],
  [/^grobli\b/,"grobla"],[/^podleza\b/,"podleze"]])k=k.replace(a,b);
 return k;
}
function identifyStop(idx,text){
 const k=segmentKey(text);if(!k)return {group:null,candidates:[]};
 const aliasesFor=aliases[idx.source]||{};
 let g=idx.groups.get(aliasesFor[k]||k)||idx.groups.get(k);
 if(g)return {group:g,candidates:[]};
 const unique=[...new Set(idx.groups.values())];
 // Never choose an arbitrary platform when a town has several stops.
 const options=unique.filter(x=>x.key.startsWith(k+" ")||x.key===k);
 if(options.length===1)return {group:options[0],candidates:[]};
 return {group:null,candidates:options.slice(0,8).map(x=>x.name)};
}
function inferNetwork(lower){
 const ma=/\b(mld|malopolsk\w*|krakow\w*|ispin\w*|bochn\w*|niepolomic\w*|nowe brzesko|proszowic\w*|podlez\w*|grobli|a4|a7|a65|a50|a57)\b/.test(lower);
 const wa=/\b(warszaw\w*|ztm|z(p|t)m|metro|mlocin\w*|centraln\w*|waw)\b/.test(lower);
 return ma&&!wa?"mld":wa&&!ma?"warsaw":null;
}
function parseRequestDate(query){
 const lower=norm(query),now=new Date();
 let date=dialogue.when?new Date(dialogue.when):new Date($("departAt").value||now);
 if(Number.isNaN(date.getTime()))date=new Date(now);
 let explicit=false;
 if(/\bpojutrze\b/.test(lower)){date=new Date(now);date.setDate(date.getDate()+2);explicit=true;}
 else if(/\bjutro\b/.test(lower)){date=new Date(now);date.setDate(date.getDate()+1);explicit=true;}
 else if(/\b(dzis|dzisiaj)\b/.test(lower)){date=new Date(now);explicit=true;}
 const weekdayMatch=lower.match(/\b(?:w|we)\s+(poniedzialek|wtorek|srode|sroda|czwartek|piatek|sobote|sobota|niedziele|niedziela)\b/);
 if(weekdayMatch){
  const target={poniedzialek:1,wtorek:2,srode:3,sroda:3,czwartek:4,piatek:5,sobote:6,sobota:6,niedziele:0,niedziela:0}[weekdayMatch[1]];
  date=new Date(now);date.setDate(date.getDate()+((target-date.getDay()+7)%7));explicit=true;
 }
 const dateMatch=query.match(/\b(\d{1,2})[./-](\d{1,2})(?:[./-](\d{4}))?\b/);
 if(dateMatch){
  const year=dateMatch[3]?Number(dateMatch[3]):now.getFullYear();
  const candidate=new Date(year,Number(dateMatch[2])-1,Number(dateMatch[1]),date.getHours(),date.getMinutes());
  if(candidate.getDate()===Number(dateMatch[1])&&candidate.getMonth()===Number(dateMatch[2])-1){
   date=candidate;explicit=true;
  }
 }
 const hours=query.match(/(?:^|\s)(?:po|o|okolo|około|ok|od|na|godzina|godziny|godz)\s+(\d{1,2})(?:[:.](\d{1,2}))?(?=\s|[.,!?]|$)/i)
       ||query.match(/(?:^|\s)(\d{1,2})[:.](\d{2})(?=\s|[.,!?]|$)/);
 if(hours){
  const h=Number(hours[1]),m=Number(hours[2]||0);
  if(h<=23&&m<=59){date.setHours(h,m,0,0);explicit=true;}
 }
 const shift=lower.match(/\b(?:za|o)\s+(\d{1,3})\s*(minut|min)\b/);
 if(shift){const amount=Number(shift[1]);date=new Date(now.getTime()+amount*60000);explicit=true;}
 const later=lower.match(/\b(?:pozniej|do przodu)\s+(?:o\s+)?(\d{1,3})\s*(minut|min)\b/);
 const earlier=lower.match(/\b(?:wczesniej)\s+(?:o\s+)?(\d{1,3})\s*(minut|min)\b/);
 if(later||earlier){date=new Date(date.getTime()+(later?1:-1)*Number((later||earlier)[1])*60000);explicit=true;}
 return {date,explicit};
}
function parseLocations(question){
 const end="(?=\\s+(?:jutro|pojutrze|dzisiaj|dziś|dzis|po\\s+\\d|o\\s+\\d|od\\s+\\d|około\\s+\\d|okolo\\s+\\d|w\\s+(?:poniedziałek|wtorek|środę|czwartek|piątek|sobotę|niedzielę))|[.!?]|$)";
 const fromTo=question.match(new RegExp("(?:^|\\s)(?:z|ze)\\s+(.+?)\\s+(?:do|na)\\s+(.+?)"+end,"i"));
 if(fromTo)return {from:fromTo[1],to:fromTo[2]};
 const toFrom=question.match(new RegExp("(?:^|\\s)(?:do|na)\\s+(.+?)\\s+(?:z|ze)\\s+(.+?)"+end,"i"));
 if(toFrom)return {from:toFrom[2],to:toFrom[1]};
 const split=question.match(new RegExp("(?:^|\\s)(?:z|ze|od)\\s+(.+?)\\s+(?:w kierunku|w stronę|w strone)\\s+(.+?)"+end,"i"));
 if(split)return {from:split[1],to:split[2]};
 const singleFrom=question.match(new RegExp("(?:^|\\s)(?:z|ze)\\s+(.+?)"+end,"i"));
 const singleTo=question.match(new RegExp("(?:^|\\s)(?:do)\\s+(.+?)"+end,"i"));
 return {from:singleFrom?.[1]||null,to:singleTo?.[1]||null};
}
function answer(message){lastAnswer=message;bubble(message,"assistant");}
function showPending(date){
 const need=dialogue.from?"to":"from";
 dialogue.asked=need;
 const prefix=need==="from"?"Z którego dokładnie przystanku chcesz wyruszyć?":"Na którym dokładnie przystanku chcesz zakończyć podróż?";
 const hint=need==="from"?dialogue.fromHint:dialogue.toHint;
 const options=(need==="from"?dialogue.fromCandidates:dialogue.toCandidates)||[];
 answer(prefix+(hint?" („"+hint.trim()+"”)":"")+
    (options.length?"\nMożliwości: "+options.join(", ")+".":"")+
    "\nDatę i godzinę mam: "+localInput(date).replace("T"," ")+".");
}
function displayStop(idx,stopName){return idx.source==="mld"&&norm(stopName)==="ispina"?"Ispina Most":stopName}
async function departures(idx,origin,when,line){
 const valid=prepare(when,idx);
 const routeIds=[...origin.routes].filter(x=>!line||norm(idx.routes[x]?.[1])===norm(line));
 if(line&&!routeIds.length){
  return {message:"Linia "+line+" nie obsługuje przystanku "+origin.name+" według tej bazy GTFS.",found:[]};
 }
 const found=[],source=new Set(origin.stops),mask=1<<valid.day;
 const loaded=await Promise.all(routeIds.map(async lineId=>({lineId,trips:await getRoute(idx,lineId)})));
 for(const {lineId,trips} of loaded){
  for(const [days,headsign,points] of trips){
   if(!(days&mask))continue;
   for(const [sid,minute] of points){
    if(source.has(sid)&&minute>=valid.minute&&minute<valid.minute+360){
     found.push({line:idx.routes[lineId][1],headsign,time:minute});break;
    }
   }
  }
 }
 found.sort((a,b)=>a.time-b.time);
 return {found:found.slice(0,5)};
}
function nearbyIdeas(idx,from,to,line){
 if(idx.source!=="mld")return "";
 const town=norm(from.name).split(" ")[0];
 const same=[...new Set(idx.groups.values())].filter(g=>g!==from&&norm(g.name).startsWith(town+" ")&&g.routes.size>0);
 const targets=same.filter(g=>[...g.routes].some(ri=>[...to.routes].includes(ri)&&(!line||norm(idx.routes[ri][1])===norm(line))));
 if(!targets.length)return "";
 return "\nW tej miejscowości warto sprawdzić również: "+targets.slice(0,4).map(g=>g.name).join(", ")+". To propozycje z bazy przystanków, nie potwierdzenie wygodnego dojścia.";
}
function summarizeTrips(idx,from,to,when,results){
 const day=localInput(when).slice(0,10),txt=results.slice(0,3).map((r,i)=>{
  const legs=r.legs.map(l=>"linia "+l.line+" "+minuteFormat(l.dep)+"–"+minuteFormat(l.arr)+
    " ("+displayStop(idx,l.from)+" → "+displayStop(idx,l.to)+")").join("; ");
  return (i+1)+". "+legs+", przyjazd "+minuteFormat(r.arr)+
    (r.kind==="transfer"?", przesiadka "+r.wait+" min":" bez przesiadki");
 });
 return "Z "+from.name+" do "+to.name+" • "+day+" od "+pad(when.getHours())+":"+pad(when.getMinutes())+
  "\n"+txt.join("\n")+"\nGodziny planowe GTFS, nie GPS LIVE.";
}
async function assistant(){
 const question=$("chatInput").value.trim();if(!question)return;
 bubble(question,"user");$("chatInput").value="";
 const lower=norm(question);
 if(/^(hej|czesc|dzien dobry|dobry wieczor|witaj)[!. ]*$/.test(lower)){
  answer("Cześć! Znajdziemy autobus? Możesz zapytać na przykład: „Z Ispiny do Bochni jutro po 9” albo „Kiedy następna A4 z Ispiny Most?”");return;
 }
 if(/^(dzieki|dziekuje|super|ok|okej)[!. ]*$/.test(lower)){
  answer("Nie ma za co! Jeśli chcesz, mogę sprawdzić późniejszy kurs lub inną trasę.");return;
 }
 if(/\b(od nowa|nowa trasa|wyczysc rozmowe|reset)\b/.test(lower)){
  dialogue.from=dialogue.to=dialogue.when=null;dialogue.fromHint=dialogue.toHint="";
  dialogue.fromCandidates=dialogue.toCandidates=[];dialogue.asked="";dialogue.line=null;dialogue.lastResults=[];dialogue.intent="route";
  start=end=null;$("from").value=$("to").value="";
  answer("Jasne, zaczynamy nową trasę. Skąd chcesz jechać?");return;
 }
 if(/\b(gdzie.*(?:autobus|bus)|gps|pozycja.*(?:autobus|bus))\b/.test(lower)){
  answer("Mogę potwierdzić planowe godziny odjazdów, ale w tym module nie mam potwierdzonej bieżącej pozycji GPS autobusu. Nie będę zgadywać jego lokalizacji.");return;
 }
 if(/\b(pogoda|temperatura|deszcz|co u ciebie|opowiedz|wiadomosci|news)\b/.test(lower)){
  answer("W tej części PA pomagam z autobusami i trasami. Nie mam tu modelu AI do swobodnej rozmowy ani aktualnej pogody. Mogę za to sprawdzić konkretny odjazd z rozkładu.");return;
 }
 const proposed=inferNetwork(lower);
 if(proposed&&proposed!==network){
  $("network").value=proposed;$("network").onchange();
  dialogue.from=dialogue.to=null;dialogue.fromHint=dialogue.toHint="";
  dialogue.fromCandidates=dialogue.toCandidates=[];dialogue.asked="";dialogue.line=null;dialogue.intent="route";
 }
 const parsed=parseRequestDate(question),date=parsed.date;
 dialogue.when=date;
 let idx;
 try{idx=await getData(network)}catch(e){answer("Nie mogę otworzyć bazy rozkładów: "+e.message);return;}
 const lineMention=lower.match(/\b(?:linia|linie|linii|linia numer|linia nr|linią|autobus|bus|numer)\s*(a\d{1,3}|\d{2,3})\b/)||
  lower.match(/\b(a(?:4|7|50|57|65))\b/);
 if(lineMention)dialogue.line=lineMention[1].toUpperCase();
 const loc=parseLocations(question);
 const departureRequest=/\b(kiedy|najblizsz\w*|nastepn\w*|odjazd\w*|odjezdz\w*|o ktorej|rozklad\w*)\b/.test(lower);
 if(departureRequest && !loc.to){dialogue.intent="departure";dialogue.to=null;dialogue.toHint="";}
 else if(loc.from&&loc.to){dialogue.intent="route";if(!lineMention)dialogue.line=null;}
 if(loc.from||loc.to){
  if(loc.from){const f=identifyStop(idx,loc.from);dialogue.from=f.group;dialogue.fromHint=loc.from;dialogue.fromCandidates=f.candidates;}
  if(loc.to){const t=identifyStop(idx,loc.to);dialogue.to=t.group;dialogue.toHint=loc.to;dialogue.toCandidates=t.candidates;}
 }else{
  const alone=identifyStop(idx,question);
  if(alone.group){
   const which=dialogue.asked||(!dialogue.from?"from":!dialogue.to?"to":null);
   if(which){dialogue[which]=alone.group;dialogue[which+"Hint"]="";dialogue[which+"Candidates"]=[];}
  }else if(/^\s*(zamien|zamien miejscami|w druga strone|powrot)\b/.test(lower)&&dialogue.from&&dialogue.to){
   const temp=dialogue.from;dialogue.from=dialogue.to;dialogue.to=temp;
   dialogue.line=null;
  }
 }
 // A standalone "A4 z Ispiny" is a departure request, not an incomplete journey.
 const asksDepartures=dialogue.intent==="departure"||departureRequest||
   (!!dialogue.line&&!dialogue.to&&!!dialogue.from);
 if(asksDepartures&&!dialogue.to){
  if(!dialogue.from){dialogue.asked="from";answer("Z którego przystanku sprawdzić najbliższe odjazdy?");return;}
  try{
   const r=await departures(idx,dialogue.from,date,dialogue.line);
   if(r.message){dialogue.intent="route";answer(r.message);return;}
   const list=r.found.map(x=>minuteFormat(x.time)+" • "+x.line+" → "+(x.headsign||"kierunek nieopisany"));
   dialogue.intent="route";dialogue.asked="";
   answer(list.length?"Planowe odjazdy z "+dialogue.from.name+" od "+pad(date.getHours())+":"+pad(date.getMinutes())+
     ":\n"+list.join("\n")+"\nBez danych o rzeczywistym opóźnieniu.":"Brak potwierdzonych odjazdów z "+dialogue.from.name+" w kolejnych 6 godzinach. Sprawdź inną godzinę lub linię.");
  }catch(e){answer(e.message);}
  return;
 }
 if(!dialogue.from||!dialogue.to){showPending(date);return;}
 const from=dialogue.from,to=dialogue.to;
 dialogue.asked="";
 start=from;end=to;$("from").value=from.name;$("to").value=to.name;
 $("statusFrom").textContent="✓ "+from.name+" – zatwierdzono";
 $("statusTo").textContent="✓ "+to.name+" – zatwierdzono";
 $("departAt").value=localInput(date);
 try{
  let results=await findTrips(idx,from,to,date);
  if(dialogue.line)results=results.filter(x=>x.legs.some(l=>norm(l.line)===norm(dialogue.line)));
  dialogue.lastResults=results;
  if(results.length){answer(summarizeTrips(idx,from,to,date,results));return;}
  const note=nearbyIdeas(idx,from,to,dialogue.line);
  answer("Nie znalazłem potwierdzonego połączenia"+(dialogue.line?" linią "+dialogue.line:"")+
   " z "+from.name+" do "+to.name+" na "+localInput(date).replace("T"," ")+
   ". Nie będę podawać zmyślonej godziny."+note);
 }catch(e){answer(e.message);}
}

$("ask").onclick=assistant;
$("copy").onclick=()=>{if(!lastAnswer)return; if(navigator.clipboard&&navigator.clipboard.writeText)navigator.clipboard.writeText(lastAnswer).catch(()=>{});};
document.querySelectorAll(".prompt").forEach(b=>b.onclick=()=>{$("chatInput").value=b.dataset.query;});
initialize();
})();