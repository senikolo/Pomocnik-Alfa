/* Full stop view opened from the native home board, within the existing regional module. */
const homeBoard={stop:null,snapshot:null,inFlight:false,refreshed:0};
async function openHomeStop(id){
 if(!engine||!id)return;const sid=engine.idx.stops.findIndex(s=>s[0]===id);if(sid<0)return;const s=engine.idx.stops[sid];
 homeBoard.stop={name:s[1],stops:[sid],routes:new Set(s[5])};$('homeStopTitle').textContent=s[1]+(s[2]?' · '+s[2]:'');tab('stopBoard');
 PAMap.mount($('homeStopMap'),{lat:s[3],lon:s[4]},null);await refreshHomeStop();
}
async function refreshHomeStop(){
 if(!homeBoard.stop||homeBoard.inFlight||!appVisible||currentTab!=='stopBoard')return;homeBoard.inFlight=true;
 try{const departures=$('boardKind').value==='arrivals'?await engine.arrivals(homeBoard.stop,wallNow(),72):await engine.departures(homeBoard.stop,wallNow(),null,null,72,null,1800);homeBoard.snapshot={departures,live:null,checked:Date.now()/1000};renderHomeStop();
  let live=null;try{if($('boardKind').value!=='arrivals')live=await native('live',{departures:departures.filter(d=>!d.headway).slice(0,36)})}catch{}homeBoard.snapshot={departures,live,checked:live?.checkedAt||Date.now()/1000};homeBoard.refreshed=Date.now();renderHomeStop();
 }catch(e){$('homeStopStatus').textContent=e.message}finally{homeBoard.inFlight=false}
}
function renderHomeStop(){
 const snap=homeBoard.snapshot;if(!snap)return;
 const wanted=$('boardMode').value,operator=$('boardOperator').value,kind=$('boardKind').value;
 const operators=[...new Set(snap.departures.map(d=>d.operator).filter(Boolean))].sort();
 $('boardOperator').innerHTML='<option value="">Wszyscy przewoźnicy</option>'+operators.map(o=>'<option value="'+esc(o)+'">'+esc(o)+'</option>').join('');$('boardOperator').value=operators.includes(operator)?operator:'';
 const rows=snap.departures.filter(d=>(!wanted||PATransit.category(d.mode)===wanted)&&(!operator||d.operator===operator)).map(d=>{const actual=snap.live?.results?.find(a=>a.source===d.source&&a.trip===d.trip&&a.stopId===d.stopId&&a.serviceDay===d.serviceDay)||{};return{d,actual,status:PALiveState.assess(d,actual,snap.checked)}}).filter(x=>x.d.headway||x.status.cancelled||x.status.skipped||x.status.etaFresh||x.status.plannedMinutes>=0).slice(0,36);
 $('homeStopStatus').textContent=rows.length?'Godziny planowe · aktualizacje na żywo, gdy są dostępne':'Brak kolejnych '+(kind==='arrivals'?'przyjazdów':'odjazdów')+' w wybranym czasie i filtrze.';
 $('homeStopRows').innerHTML=rows.map(({d,actual,status})=>'<article class="card"><div class="row between wrap"><span class="pill">'+esc(mode(d.mode)+' · '+(d.operator||'')+' · '+(d.train?'Pociąg '+d.train:d.line))+'</span><strong class="board-time">'+(d.headway?'co ~'+Math.ceil(d.headway/60)+' min':clock(d.dep))+'</strong></div><p class="station">'+(kind==='arrivals'?'Z: ':'→ ')+esc(d.headsign)+'</p>'+busArrivalView(actual,d,snap.checked)+(status.gpsFresh?'<p class="small">Pozycja pojazdu dostępna · odjazd według rozkładu</p>':'')+(d.intermediate?.length?'<details><summary>Stacje pośrednie</summary>'+d.intermediate.map(p=>'<p class="small">'+clock(p.arrival)+' · '+esc(p.name)+'</p>').join('')+'</details>':'')+'<details><summary>Źródło informacji</summary><p class="small">'+esc(engine.idx.sources.find(s=>s.id===d.source)?.credit||d.source)+'</p></details></article>').join('');
}
for(const id of ['boardMode','boardOperator'])$(id).onchange=renderHomeStop;
$('boardKind').onchange=refreshHomeStop;
$('homeStopRefresh').onclick=refreshHomeStop;$('homeStopBack').onclick=()=>native('back').catch(()=>{});
const homeTimer=setInterval(()=>{if(!appVisible||document.hidden||currentTab!=='stopBoard')return;renderHomeStop();if(Date.now()-homeBoard.refreshed>=30000)refreshHomeStop()},5000);
window.addEventListener('pagehide',()=>{clearInterval(homeTimer);const m=$('homeStopMap');if(m.__mapCleanup)m.__mapCleanup()});
