/* Full stop view opened from the native home board, within the existing regional module. */
const homeBoard={stop:null,snapshot:null,inFlight:false,refreshed:0};
async function openHomeStop(id){
 if(!engine||!id)return;const sid=engine.idx.stops.findIndex(s=>s[0]===id);if(sid<0)return;const s=engine.idx.stops[sid];
 homeBoard.stop={name:s[1],stops:[sid],routes:new Set(s[5])};$('homeStopTitle').textContent=s[1]+(s[2]?' · '+s[2]:'');tab('stopBoard');
 PAMap.mount($('homeStopMap'),{lat:s[3],lon:s[4]},null);await refreshHomeStop();
}
async function refreshHomeStop(){
 if(!homeBoard.stop||homeBoard.inFlight||!appVisible||currentTab!=='stopBoard')return;homeBoard.inFlight=true;
 try{const departures=await engine.departures(homeBoard.stop,wallNow(),null,null,36,null,1800);homeBoard.snapshot={departures,live:null,checked:Date.now()/1000};renderHomeStop();
  let live=null;try{live=await native('live',{departures:departures.slice(0,36)})}catch{}homeBoard.snapshot={departures,live,checked:live?.checkedAt||Date.now()/1000};homeBoard.refreshed=Date.now();renderHomeStop();
 }catch(e){$('homeStopStatus').textContent=e.message}finally{homeBoard.inFlight=false}
}
function renderHomeStop(){
 const snap=homeBoard.snapshot;if(!snap)return;
 const rows=snap.departures.map(d=>{const actual=snap.live?.results?.find(a=>a.source===d.source&&a.trip===d.trip&&a.stopId===d.stopId&&a.serviceDay===d.serviceDay)||{};return{d,actual,status:PALiveState.assess(d,actual,snap.checked)}}).filter(x=>x.status.cancelled||x.status.skipped||x.status.etaFresh||x.status.plannedMinutes>=0).slice(0,16);
 $('homeStopStatus').textContent=rows.length?'Rozkład planowy · prognozy LIVE tylko po potwierdzeniu':'Brak odjazdów w ciągu 24 godzin. Sprawdź ważność bazy w ustawieniach.';
 $('homeStopRows').innerHTML=rows.map(({d,actual,status})=>'<article class="card"><div class="row wrap"><span class="pill">'+esc(mode(d.mode)+' '+d.line)+'</span><strong>'+clock(d.dep)+'</strong></div><p class="station">→ '+esc(d.headsign)+'</p>'+busArrivalView(actual,d,snap.checked)+(status.gpsFresh?'<p class="small">Pozycja GPS LIVE · brak potwierdzonego ETA</p>':'')+'</article>').join('');
}
$('homeStopRefresh').onclick=refreshHomeStop;$('homeStopBack').onclick=()=>native('back').catch(()=>{});
const homeTimer=setInterval(()=>{if(!appVisible||document.hidden||currentTab!=='stopBoard')return;renderHomeStop();if(Date.now()-homeBoard.refreshed>=30000)refreshHomeStop()},5000);
window.addEventListener('pagehide',()=>{clearInterval(homeTimer);const m=$('homeStopMap');if(m.__mapCleanup)m.__mapCleanup()});
