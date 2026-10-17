(function(){'use strict';
const $=id=>document.getElementById(id),{norm,canonical}=PATransit,{wallNow,parseTime,localDate,format}=PAConversation;
let activeSearch=0,updating=false;let engine,assistant,start=null,end=null,results=[],generation=0,currentTab='planner',selected=null,noteText='',noteWeather='',busy=false,requestNo=0,manualTime=false;const callbacks=new Map();
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const clock=sec=>{const d=Math.floor(sec/86400),x=((sec%86400)+86400)%86400;return String(Math.floor(x/3600)).padStart(2,'0')+':'+String(Math.floor(x%3600/60)).padStart(2,'0')+(d>0?' (+'+d+' dzień)':'')};
const minutes=s=>Math.ceil(s/60);const duration=sec=>{const m=minutes(sec);return m>=60?Math.floor(m/60)+' godz. '+(m%60?m%60+' min':''):m+' min'};
const icon=name=>'<svg class="vicon" aria-hidden="true" viewBox="0 0 24 24"><use href="#pa-'+name+'"></use></svg>';
const mode=t=>({metro:'Metro',tram:'Tramwaj',rail:'Pociąg',bus:'Autobus'})[PATransit.category(t)];
const load=(key,fallback)=>{try{return JSON.parse(localStorage.getItem(key))||fallback}catch{return fallback}};
function save(key,value){try{localStorage.setItem(key,JSON.stringify(value))}catch{toast('Nie udało się zapisać danych. Sprawdź wolne miejsce.')}}
let toastTimer;function toast(text){clearTimeout(toastTimer);$('toast').textContent=text;$('toast').classList.remove('hidden');toastTimer=setTimeout(()=>$('toast').classList.add('hidden'),3200)}
window.PANativeReply=(id,r)=>{const c=callbacks.get(id);if(!c)return;callbacks.delete(id);clearTimeout(c.timer);r.ok?c.resolve(r.data):c.reject(Error(r.message||'Czynność nie powiodła się.'))};
function native(type,payload={}){return new Promise((resolve,reject)=>{if(!window.PADevice){if(type==='copy'&&navigator.clipboard){navigator.clipboard.writeText(payload.text).then(()=>resolve({saved:true}),reject);return}reject(Error('Ta funkcja działa w zainstalowanej aplikacji PA.'));return}const id=String(++requestNo),timer=setTimeout(()=>{callbacks.delete(id);reject(Error('Upłynął czas oczekiwania. Spróbuj ponownie.'))},type==='update'?180000:50000);callbacks.set(id,{resolve,reject,timer});PADevice.request(JSON.stringify({id,type,payload}))})}
function tab(name){if(name!=='stopBoard'){const m=$('homeStopMap');if(m&&m.__mapCleanup)m.__mapCleanup()}currentTab=name;document.body.classList.remove('keyboard-open');if(document.activeElement?.tagName==='TEXTAREA')document.activeElement.blur();document.querySelectorAll('.panel').forEach(x=>x.classList.toggle('active',x.id===name));document.querySelectorAll('nav button').forEach(x=>{x.classList.toggle('active',x.dataset.tab===name);x.setAttribute('aria-current',x.dataset.tab===name?'page':'false')});if(name==='favorites')renderFavorites();window.scrollTo({top:0,behavior:'auto'});if(name==='assistant')scrollChat()}
window.PABack=()=>{if(!$('settings').classList.contains('hidden')){$('settings').classList.add('hidden');layoutChat();return}if(currentTab!=='planner'){tab('planner');return}native('back').catch(()=>{})};
$('back').onclick=window.PABack;document.querySelectorAll('nav button').forEach(b=>b.onclick=()=>tab(b.dataset.tab));$('settingsToggle').onclick=()=>{$('settings').classList.toggle('hidden');layoutChat()};
function appearance(){const theme=$('theme').value,dark=theme==='dark'||theme==='auto'&&window.matchMedia('(prefers-color-scheme:dark)').matches;document.documentElement.dataset.theme=dark?'dark':'light';document.body.classList.toggle('senior',$('senior').value==='large');save('pa2-appearance',{theme,senior:$('senior').value});requestAnimationFrame(layoutChat)}
const style=load('pa2-appearance',{theme:'auto',senior:'normal'});$('theme').value=style.theme;$('senior').value=style.senior;$('theme').onchange=$('senior').onchange=appearance;appearance();const media=window.matchMedia('(prefers-color-scheme:dark)');if(media.addEventListener)media.addEventListener('change',appearance);else if(media.addListener)media.addListener(appearance);
function updateDateLabel(){const raw=$('departAt').value;if(!raw||!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(raw)){$('dateDisplay').textContent='Wybierz datę i godzinę';return}const [d,t]=raw.split('T'),[yy,mm,dd]=d.split('-');const today=wallNow().slice(0,10);$('dateDisplay').textContent=(d===today?'Dzisiaj · ':'')+dd+'.'+mm+'.'+yy+' · '+t.slice(0,5)}
$('departAt').value=wallNow();updateDateLabel();$('departAt').onchange=()=>{manualTime=true;updateDateLabel();if(start&&end)search()};$('now').onclick=()=>{manualTime=false;$('departAt').value=wallNow();updateDateLabel();if(start&&end)search()};
function sourceNotice(idx){const dates=idx.days.filter((d,i)=>idx.covered[i]);$('coverage').textContent=idx.routes.length+' linii i relacji · rozkład '+dates[0]+' – '+dates.at(-1)+'. Dostępny offline.';$('sources').innerHTML=idx.sources.map(s=>'<p><b>'+esc(s.credit)+'</b><br>'+esc(s.url)+'<br>Pobrano '+esc(s.downloaded.slice(0,16).replace('T',' '))+'. Przetworzono do Planera PA.'+(s.attributions||[]).map(a=>'<br>'+esc(a.organization_name)+' '+esc(a.attribution_url||'')).join('')+'</p>').join('')}
async function init(){try{const r=await fetch('/index_all.json',{cache:'no-store'});if(!r.ok)throw Error('Nie można odczytać bazy.');const idx=await r.json();if(idx.schema!==2)throw Error('Nieobsługiwana wersja rozkładu.');engine=new PATransit.Engine(idx,async id=>{const r=await fetch('/route_all_'+id+'.json',{cache:'no-store'});if(!r.ok)throw Error('Brakuje pliku rozkładu linii. Odśwież dane.');return r.json()});assistant=new PAConversation.Assistant(engine);sourceNotice(idx);restoreConversation();renderFavorites();native("homeStop").then(r=>openHomeStop(r.stopId)).catch(()=>{});}catch(e){$('coverage').textContent=e.message;$('progress').textContent='Nie udało się uruchomić Planera. Spróbuj ponownie otworzyć ten moduł.'}}
function groupSummary(g){const sources=[...new Set(g.stops.map(i=>engine.idx.stops[i][6]))],names={warsaw:'WTP Warszawa',mld:'MLD',krakow_bus:'Kraków',krakow_tram:'Kraków',krakow_mobilis:'Kraków',kmlrail:'Koleje Małopolskie',wkd:'WKD',polishrail:'kolej'};return [...new Set(sources.map(s=>names[s]||s))].join(' · ')}
function suggest(which){const text=$(which).value,box=$('suggest'+(which==='from'?'From':'To'));box.innerHTML='';$(which).setAttribute('aria-expanded','false');if(!engine||norm(text).length<3){box.classList.remove('open');return}const groups=engine.match(text);box.classList.add('open');$(which).setAttribute('aria-expanded','true');if(!groups.length){box.innerHTML='<p class="small" style="padding:12px">Nie znaleziono przystanku. Spróbuj innej pisowni.</p>';return}groups.slice(0,12).forEach(g=>{const b=document.createElement('button');b.type='button';b.setAttribute('role','option');b.innerHTML=esc(g.name)+'<small>'+esc(groupSummary(g))+'</small>';b.onclick=()=>{$(which).value=g.name;box.classList.remove('open');$(which).setAttribute('aria-expanded','false');$('confirm'+(which==='from'?'From':'To')).focus()};box.appendChild(b)});if(groups.length>12){const more=document.createElement('p');more.className='small';more.style.padding='8px 12px';more.textContent='Wpisz dokładniejszą nazwę, aby zawęzić listę.';box.appendChild(more)}}
function edit(which){if(which==='from')start=null;else end=null;$(which).setAttribute('aria-expanded','false');const suffix=which==='from'?'From':'To';$('status'+suffix).textContent='';$('confirm'+suffix).textContent='Akceptuj';$(which).readOnly=false;generation++;$('results').innerHTML=''}
function accept(which){if(!engine)return;const suffix=which==='from'?'From':'To';if($(which).readOnly){edit(which);$(which).focus();return}const r=engine.resolve($(which).value);if(!r.group){$('status'+suffix).textContent='Wybierz pełną nazwę z podpowiedzi.';suggest(which);return}if(which==='from')start=r.group;else end=r.group;$(which).value=r.group.name;$(which).readOnly=true;$(which).setAttribute('aria-expanded','false');$('status'+suffix).textContent='✓ '+r.group.name+' · wybrano';$('status'+suffix).classList.remove('pop');void $('status'+suffix).offsetWidth;$('status'+suffix).classList.add('pop');$('confirm'+suffix).textContent='Zmień';$('suggest'+suffix).classList.remove('open');if(start&&end){if(!manualTime)$('departAt').value=wallNow();updateDateLabel();search()}}
for(const which of ['from','to']){$(which).oninput=()=>{edit(which);suggest(which)};$(which).onfocus=()=>{if(!$(which).readOnly)suggest(which)};$('confirm'+(which==='from'?'From':'To')).onclick=()=>accept(which)}
function setPoints(a,b,when){start=a;end=b;for(const [id,g]of [['from',a],['to',b]]){const suffix=id==='from'?'From':'To';$(id).value=g?g.name:'';$(id).readOnly=!!g;$('confirm'+suffix).textContent=g?'Zmień':'Akceptuj';$('status'+suffix).textContent=g?'✓ '+g.name+' · wybrano':''}if(when){$('departAt').value=when;manualTime=true}updateDateLabel()}
$('swap').onclick=()=>{setPoints(end,start);if(start&&end)search()};
function options(){const modes={bus:[3,700,701,702,704,714],tram:[0,900],metro:[1],rail:[2,100,101,102,103,106,109]};return{minTransfer:Number($('transfer').value)||5,maxTransfers:Number($('changes').value),walk:$('walk').checked,sort:$('sort').value,modes:modes[$('modes').value]}}
for(const id of ['sort','transfer','changes','modes','walk'])$(id).onchange=()=>{if(start&&end)search()};
function renderTrip(r,i){const rides=r.legs.filter(l=>!l.walk);const needsFareCheck=rides.some(l=>['PKP Intercity','POLREGIO'].includes(l.operator));let steps='';r.legs.forEach((l,j)=>{const previous=r.legs[j-1];if(previous&&!l.walk){const wait=l.dep-previous.arr;if(wait>0)steps+='<div class="risk '+(wait<300?'tight':'')+'">Przesiadka · '+minutes(wait)+' min oczekiwania</div>'}if(l.walk){steps+='<div class="leg"><p>Pieszo · ok. '+l.metres+' m · '+duration(l.arr-l.dep)+'</p><p class="small">'+esc(engine.idx.stops[l.fromId][1])+' → '+esc(engine.idx.stops[l.toId][1])+'</p><p class="small">Dojście orientacyjne; sprawdź przejście.</p></div>';return}steps+='<div class="leg"><div class="row wrap"><span class="pill">'+esc(mode(l.mode)+' '+(l.operator||l.agency)+' · '+(l.train||l.line))+'</span><span class="small">'+clock(l.dep)+' → '+clock(l.arr)+'</span></div><p class="station">'+esc(l.from)+' → '+esc(l.to)+'</p><p class="small">Kierunek '+esc(l.headsign)+(l.platformFrom?' · stanowisko/peron '+esc(l.platformFrom):'')+'</p></div>'});
return '<article class="card trip"><div class="trip-head"><div><div class="trip-time">'+clock(r.dep)+' <small>→</small> '+clock(r.arr)+'</div><span class="small">'+(r.transfers?r.transfers+' '+(r.transfers===1?'przesiadka':'przesiadki'):'Bez przesiadek')+'</span></div><div class="duration"><strong>'+duration(r.duration)+'</strong><br>Rozkład planowy</div></div><div class="route-strip">'+rides.map(l=>'<span class="pill">'+esc(l.train||l.line)+'</span>').join('<span class="small">→</span>')+'</div><details><summary>Przebieg podróży</summary><div class="timeline">'+steps+'</div></details><button class="btn primary full chooseTrip" data-index="'+i+'">Wybieram · pokaż notatkę</button><p class="footnote">'+esc([...new Set(rides.map(l=>l.agency))].join(' · '))+'</p>'+(needsFareCheck?'<p class="footnote">Bilet miejski nie musi obejmować tego pociągu. Sprawdź taryfę i ewentualną rezerwację u przewoźnika.</p>':'')+'</article>'}
function showResults(rows){results=rows.map(r=>({...r,fromName:start.name,toName:end.name}));rows=results;$('results').innerHTML=rows.map(renderTrip).join('');document.querySelectorAll('.chooseTrip').forEach(b=>b.onclick=()=>selectTrip(rows[Number(b.dataset.index)]))}
function revealResults(){const target=$('progress');if(!target)return;const reduce=window.matchMedia?.('(prefers-reduced-motion:reduce)').matches;const top=target.getBoundingClientRect().top+window.scrollY-114;window.scrollTo({top:Math.max(0,top),behavior:reduce?'auto':'smooth'})}
async function search(){if(!engine||updating)return;const seq=++generation;activeSearch=seq;if(!start||!end){$('progress').textContent='Najpierw zaakceptuj początek i cel.';return}$('progress').textContent='Sprawdzam kursy i przesiadki…';$('results').innerHTML='';$('search').disabled=true;const when=$('departAt').value;updateDateLabel();assistant.sync(start,end,when);
 try{const rows=await engine.find(start,end,when,{...options(),cancelled:()=>seq!==generation,progress:(round,percent)=>{if(seq===generation)$('progress').textContent='Sprawdzam połączenia · etap '+round+' · '+percent+'%'}});if(seq!==generation)return;showResults(rows);$('progress').textContent=rows.length?'Znalazłem '+rows.length+' połączeń na '+when.slice(0,10)+'. Wybierz najwygodniejsze.':'Brak połączeń spełniających te ustawienia w kolejnych 24 godzinach. Sprawdź inny przystanek lub zwiększ liczbę przesiadek.';persistConversation();if(rows.length)setTimeout(()=>{if(seq===generation&&currentTab==='planner')revealResults()},90);}
 catch(e){if(seq===generation){$('progress').textContent=e.message;$('results').innerHTML=''}}finally{if(activeSearch===seq)$('search').disabled=false}}
$('search').onclick=search;
function noteFor(r){let lines=['🧭 Twoja podróż z Alfą',r.date,'','Start: '+r.fromName,'Cel: '+r.toName,'Wyjazd: '+clock(r.dep)+' · przyjazd: '+clock(r.arr),'Łącznie: '+duration(r.duration)+' · '+(r.transfers?r.transfers+' przesiadki':'bez przesiadek'),''];r.legs.forEach((l,i)=>{if(l.walk){lines.push('🚶 Dojście orientacyjne: ok. '+l.metres+' m, '+duration(l.arr-l.dep)+'.');return}if(i&&l.dep>r.legs[i-1].arr)lines.push('Przesiadka: '+minutes(l.dep-r.legs[i-1].arr)+' min oczekiwania.');lines.push(mode(l.mode)+' '+(l.train||l.line)+' → '+l.headsign,clock(l.dep)+' '+l.from+(l.platformFrom?' (stanowisko/peron '+l.platformFrom+')':''),clock(l.arr)+' '+l.to,'')});if(noteWeather)lines.push('🌤 '+noteWeather,'');lines.push('Godziny planowe z GTFS. Opóźnienia i zmiany nie są w tej notatce potwierdzone.','Wskazówka: przyjdź kilka minut wcześniej i sprawdź kierunek oraz stanowisko.');if(r.legs.some(l=>l.source==='polishrail'))lines.push('Przed podróżą kolejową potwierdź rozkład i bilet w Portalu Pasażera. Bilet miejski może nie obejmować POLREGIO ani PKP Intercity.');return lines.join('\n')}
function selectTrip(trip){if(!trip){toast('Wyszukaj to połączenie ponownie.');return}selected=JSON.parse(JSON.stringify(trip));noteWeather='';noteText=noteFor(selected);$('note').textContent=noteText;$('noteStatus').textContent='';tab('notePanel')}
$('returnResults').onclick=()=>tab('planner');$('copyNote').onclick=async()=>{try{await native('copy',{text:noteText});toast('Notatka skopiowana')}catch(e){$('noteStatus').textContent=e.message}};$('saveNote').onclick=async()=>{try{await native('note',{text:noteText});$('noteStatus').textContent='Zapisano w notatniku PA.'}catch(e){$('noteStatus').textContent=e.message}};
function weatherText(w){return w.city+' · '+w.date+': '+w.min+'–'+w.max+'°C, szansa opadów '+w.rain+'%.'+(w.rain>=50?' Warto zabrać parasol.':'')}
$('noteWeather').onclick=async()=>{if(!selected)return;$('noteStatus').textContent='Sprawdzam prognozę…';try{const city=selected.toName.replace(/\s+(?:Dworzec|Most|PKP|Dom Ludowy|Główny|Centralna).*$/i,'');const diff=Math.round((localDate(selected.date+'T12:00')-localDate(wallNow().slice(0,10)+'T12:00'))/86400000);if(diff<0||diff>6)throw Error('Prognoza jest dostępna na 7 dni od dzisiaj.');const stop=engine.idx.stops[selected.legs.at(-1).toId];const w=await native('weather',{city:selected.toName,latitude:stop[3],longitude:stop[4],offset:diff});noteWeather=weatherText(w)+' Źródło: '+w.source;noteText=noteFor(selected);$('note').textContent=noteText;$('noteStatus').textContent='Dodano prognozę.'}catch(e){$('noteStatus').textContent=e.message}};
let favorites=load('pa2-favorites',[]);
function addFavorite(value){if(favorites.some(x=>x.from===value.from&&x.to===value.to)){toast('Już jest w ulubionych');return}favorites.push(value);save('pa2-favorites',favorites);toast('Zapisano w ulubionych')}
$('saveRoute').onclick=()=>{if(!start){toast('Najpierw zaakceptuj przystanek początkowy');return}addFavorite({from:start.name,to:end?end.name:null})};
function renderFavorites(){const box=$('favoriteList');box.innerHTML='';if(!favorites.length){box.innerHTML='<div class="card"><h3>Tu będą Twoje stałe trasy</h3><p class="small">Zaakceptuj przystanek lub oba punkty podróży i wybierz „Zapisz trasę”.</p></div>';return}favorites.forEach((f,i)=>{const div=document.createElement('div');div.className='card';div.innerHTML='<h3>'+esc(f.from)+(f.to?' → '+esc(f.to):'')+'</h3><div class="row"><button class="btn primary use">'+(f.to?'Sprawdź teraz':'Wybierz przystanek')+'</button><button class="btn quiet remove">Usuń</button></div>';div.querySelector('.use').onclick=()=>{if(!engine)return;const a=engine.resolve(f.from).group,b=f.to?engine.resolve(f.to).group:null;if(!a||f.to&&!b){toast('Zapisanej nazwy nie ma w nowej bazie. Wybierz przystanek ponownie.');return}manualTime=false;setPoints(a,b,wallNow());manualTime=false;tab('planner');if(a&&b)search()};div.querySelector('.remove').onclick=()=>{favorites.splice(i,1);save('pa2-favorites',favorites);renderFavorites()};box.appendChild(div)})}
function layoutChat(){if(currentTab!=='assistant')return;const viewport=window.visualViewport?window.visualViewport.height:window.innerHeight;document.body.classList.toggle('compact-chat',viewport<540);const panel=$('assistant'),nav=document.querySelector('nav'),visible=getComputedStyle(nav).display!=='none',bottom=visible?Math.min(viewport,nav.getBoundingClientRect().top):viewport;panel.style.height=Math.max(180,bottom-panel.getBoundingClientRect().top-9)+'px'}
function scrollChat(){layoutChat();requestAnimationFrame(()=>{$('messages').scrollTop=$('messages').scrollHeight;requestAnimationFrame(()=>{$('messages').scrollTop=$('messages').scrollHeight})})}
let history=[];
function bubble(text,who='assistant',opts=[]){const b=document.createElement('div');b.className='bubble '+who;const body=document.createElement('div');body.textContent=text;b.appendChild(body);if(opts.length){const list=document.createElement('div');list.className='options';function addOption(option,parent){const x=document.createElement('button');x.className='btn'+(option.detail?' chat-trip-choice':'');
x.textContent=option.label||option;
if(option.detail){const sub=document.createElement('small');sub.className='chat-trip-detail';sub.textContent=option.detail;x.appendChild(sub)}
x.onclick=()=>option.action?option.action():ask(String(option));parent.appendChild(x)}
opts.slice(0,3).forEach(x=>addOption(x,list));if(opts.length>3){const more=document.createElement('div');more.className='options more-options hidden';opts.slice(3,12).forEach(x=>addOption(x,more));const toggle=document.createElement('button');toggle.className='btn quiet';toggle.textContent='Więcej propozycji ('+(Math.min(12,opts.length)-3)+')';toggle.setAttribute('aria-expanded','false');toggle.onclick=()=>{const open=more.classList.toggle('hidden');toggle.textContent=open?'Więcej propozycji ('+(Math.min(12,opts.length)-3)+')':'Zwiń propozycje';toggle.setAttribute('aria-expanded',String(!open));scrollChat()};list.appendChild(toggle);list.appendChild(more)}b.appendChild(list)}$('messages').appendChild(b);scrollChat();return b}
function remember(role,content){history.push({role,content:String(content).slice(0,3500)});history=history.slice(-20);persistConversation()}
function say(text,opts=[]){bubble(text,'assistant',opts);remember('assistant',text)}
function persistConversation(){if(!assistant)return;const s=assistant.state;try{sessionStorage.setItem('pa2-session',JSON.stringify({history,state:{...s,from:s.from?.name,to:s.to?.name,candidates:[],fromCandidates:[],toCandidates:[]}}))}catch{}}
function restoreConversation(){let last;try{last=JSON.parse(sessionStorage.getItem('pa2-session'))}catch{}if(last?.history?.length){history=last.history.slice(-20);history.forEach(m=>bubble(m.content,m.role));const s=last.state||{};assistant.state={...assistant.state,...s,from:s.from?engine.resolve(s.from).group:null,to:s.to?engine.resolve(s.to).group:null};if(assistant.state.from&&assistant.state.to)setPoints(assistant.state.from,assistant.state.to,assistant.state.when)}else{bubble((Number(wallNow().slice(11,13))>=18?'Dobry wieczór!':'Dzień dobry!')+' Jestem Alfa. Pomogę Ci zaplanować podróż, sprawdzić pogodę albo zapisać coś na później.\n\nDokąd się wybierasz?')}}
function setBusy(on,label='Sprawdzam…'){busy=on;$('ask').disabled=on;$('clearChat').disabled=on;$('typing').textContent=on?label:'';$('typing').classList.toggle('busy',on)}
function summarize(rows){return rows.slice(0,3).map((r,i)=>{const rides=r.legs.filter(l=>!l.walk);return(i+1)+'. '+clock(r.dep)+' → '+clock(r.arr)+' · '+rides.map(l=>l.train||l.line).join(' → ')+' · '+duration(r.duration)+(r.transfers?' · przesiadki: '+r.transfers:' · bez przesiadek')}).join('\n')}
async function routeAction(a){setPoints(a.from,a.to,a.when);$('typing').textContent='Sprawdzam rozkłady i przesiadki…';const rows=await engine.find(a.from,a.to,a.when,{...options(),mustLine:a.line});showResults(rows);if(rows.length){
 const note=(a.correction?a.correction+'\n':'')+(!rows.some(r=>r.transfers===0)?'Nie znalazłem bezpośredniego kursu. Poniżej są połączenia z przesiadką.\n':'')+
 'Z '+a.from.name+' do '+a.to.name+' · '+a.when.slice(0,10)+'.\nWybierz konkretny kurs poniżej. Godziny według rozkładu.';
 say(note,results.slice(0,3).map(r=>{const rides=r.legs.filter(l=>!l.walk);
  const label='Wybieram · '+clock(r.dep)+' → '+clock(r.arr)+' · '+rides.map(l=>l.train||l.line).join(' + ');
  const detail=duration(r.duration)+' · '+(r.transfers?(r.transfers===1?'1 przesiadka':r.transfers+' przesiadki'):'bez przesiadek');
  return{label,detail,action:()=>selectTrip(r)}}))
 }else{
 let alternatives=[];const sourceName=canonical(a.from.name).split(' ')[0],endRoutes=new Set(a.to.routes);
 for(const g of new Set(engine.groups.values()))if(g!==a.from&&canonical(g.name).startsWith(sourceName+' ')&&[...g.routes].some(r=>endRoutes.has(r))){const nearest=Math.min(...a.from.stops.flatMap(i=>g.stops.map(j=>PATransit.distance(engine.idx.stops[i],engine.idx.stops[j]))));if(nearest<=2500)alternatives.push({g,nearest})}
 alternatives.sort((x,y)=>x.nearest-y.nearest);const checked=[];for(const item of alternatives.slice(0,4)){const r=await engine.find(item.g,a.to,a.when,{...options(),maxTransfers:0});if(r.length)checked.push({...item,result:r[0]})}
 const specific=!a.from.routes.size?'Ten przystanek nie ma czynnych kursów w wybranej bazie. ':'';
 say(specific+'Nie znalazłem połączenia z '+a.from.name+' do '+a.to.name+' spełniającego te ustawienia.'+(checked.length?'\nSprawdziłem rozkład dla innych przystanków:\n'+checked.map(x=>x.g.name+': '+clock(x.result.dep)+' → '+clock(x.result.arr)+' · '+x.result.legs.filter(l=>!l.walk).map(l=>l.line).join(', ')).join('\n')+'\nDojście trzeba sprawdzić osobno; nie potwierdzam dostępności drogi pieszej.':''),checked.map(x=>({label:x.g.name,action:()=>ask('Z '+x.g.name+' do '+a.to.name+' '+a.when.slice(0,10)+' o '+a.when.slice(11))})))
}}
async function departureAction(a){
 if(a.line&&![...a.from.routes].some(r=>norm(engine.idx.routes[r][1])===norm(a.line))){say('Linia '+a.line+' nie obsługuje przystanku '+a.from.name+' według tej bazy rozkładów. Wybierz inny przystanek lub linię.');assistant.state.asked='from';return}
 const routeIds=a.operator?[...a.from.routes].filter(id=>(engine.idx.routes[id][6]||engine.idx.routes[id][4])===a.operator):null;
 const departures=await engine.departures(a.from,a.type==='vehicle'?wallNow():a.when,a.line,a.direction,72,routeIds,a.type==='vehicle'?1800:0);
 if(!departures.length&&a.direction){const other=await engine.departures(a.from,a.when,a.line,null);if(other.length){assistant.state.direction=null;assistant.state.asked='direction';say('Wcześniejszy cel nie wskazuje jednoznacznie kierunku tej linii. W którym kierunku jedziesz?',[...new Set(other.map(d=>d.headsign))]);return}}
 if(!departures.length){say((a.line&&!a.from.routes.size?'Linia nie ma tu potwierdzonych kursów. ':'')+'Brak pasujących odjazdów z '+a.from.name+' w kolejnych 24 godzinach. Spróbuj innej godziny albo kierunku.');return}
 const directions=[...new Set(departures.map(d=>d.headsign))];if(!a.direction&&directions.length>1){assistant.state.asked='direction';say('Z przystanku '+a.from.name+' mam kilka kierunków. Który wybierasz?',directions);return}
 if(a.type==='vehicle'){
  $('typing').textContent='Sprawdzam dostępne dane rzeczywiste…';let live;try{live=await native('live',{departures:departures.slice(0,12)})}catch(e){say('Nie mogę teraz pobrać danych rzeczywistych. '+e.message+'\n\nRozkład planowy:\n'+departures.slice(0,3).map(d=>(d.headway?'Co około '+Math.ceil(d.headway/60)+' min':clock(d.dep))+' · '+(d.operator||'')+' '+(d.train||d.line)+' → '+d.headsign).join('\n'));return}
  const fresh=live.results.filter(x=>x.live&&!x.cancelled&&!x.skipped);const first=fresh.find(x=>x.eta>Date.now()/1000)||fresh.find(x=>x.position)||live.results.find(x=>x.dep>=Number(wallNow().slice(11,13))*3600+Number(wallNow().slice(14,16))*60)||live.results[0];if(!first){say('Nie znalazłem aktualnego kursu. Spróbuj innego kierunku.');return}if(first.cancelled){say('Potwierdzono odwołanie kursu '+first.line+' → '+first.headsign+'. Sprawdź inne odjazdy.');return}
  if(first.eta){const when=new Date(first.eta*1000),left=Math.max(0,Math.ceil((first.eta*1000-Date.now())/60000));say('Linia '+first.line+' w kierunku '+first.headsign+' ma przyjechać na '+a.from.name+' o '+new Intl.DateTimeFormat('pl-PL',{timeZone:'Europe/Warsaw',hour:'2-digit',minute:'2-digit'}).format(when)+'. Pozostało około '+left+' min.\nPotwierdzone dane rzeczywiste. '+first.provenance);}
  else say('Linia '+first.line+' → '+first.headsign+'\nPrzystanek: '+a.from.name+'\nOdjazd planowy: '+clock(first.dep)+'.\n'+(first.position?'Mam zgodną pozycję GPS tego kursu, pomiar sprzed '+first.position.age+' s. Źródło nie potwierdza prognozy przyjazdu na ten przystanek.':'Nie mam świeżej, zgodnej pozycji GPS ani prognozy przyjazdu tego kursu. Podaję godzinę z rozkładu.')+'\n'+first.provenance);
 }else say('Planowe odjazdy z '+a.from.name+' · '+a.when.slice(0,10)+'\n'+departures.slice(0,5).map(d=>(d.headway?'Co około '+Math.ceil(d.headway/60)+' min':clock(d.dep))+' · '+(d.operator||'')+' '+(d.train||d.line)+' → '+d.headsign).join('\n')+'\nOpóźnienie nie zostało sprawdzone.');
}
async function encyclopedia(q){const r=await native('search',{query:q});if(!r.results?.length){say('Nie znalazłem odpowiedzi w Wikipedii. Spróbuj krótszego hasła lub innej nazwy.');return}const b=bubble('Wyniki z Wikipedii · odczyt teraz. To wyszukiwanie encyklopedyczne, bez modelu AI.');r.results.forEach(x=>{const p=document.createElement('p'),title=document.createElement('strong');title.textContent=x.title;p.appendChild(title);p.appendChild(document.createTextNode('\n'+x.text));b.appendChild(p)});remember('assistant',r.results.map(x=>x.title+': '+x.text).join('\n'))}
async function ask(question,forceSearch=false){if(busy||updating)return;if(!assistant){toast('Zaczekaj na wczytanie bazy');return}const q=(question??$('chatInput').value).trim();if(!q)return;$('chatInput').value='';$('quickPrompts').classList.add('hidden');bubble(q,'user');const prior=history.slice();remember('user',q);setBusy(true);
 try{
 let model=null;
 const a=assistant.parse(q,model);
 if(forceSearch&&a.type==='general'){await encyclopedia(q);return}
 if(a.type==='route'){await routeAction(a);return}if(a.type==='departure'||a.type==='vehicle'){await departureAction(a);return}
 if(a.type==='clarify'){say(a.message,a.options);return}if(a.type==='answer'){say(a.message);return}
 if(a.type==='weather'){const w=await native('weather',a);say(weatherText(w)+'\nPrognoza sprawdzona teraz. '+w.source);return}
 if(a.type==='note'){await native('note',{text:a.text});say('Zapisałem w notatniku PA: „'+a.text+'”.');return}
 if(a.type==='reminder'){say('Ustawić przypomnienie „'+a.text+'” na '+a.when.replace('T',' ')+'?', [{label:'Zapisz przypomnienie',action:async()=>{try{const r=await native('reminder',a);say(r.message||'Przypomnienie zapisane.')}catch(e){say(e.message)}}}]);return}
 if(a.type==='general'){if(/wydarzen|dzieje sie|wiadomosc|aktualnosci/.test(norm(q))){say('Mogę pomóc z trasą, położeniem pojazdu, pogodą, notatką lub przypomnieniem. Aktualnych wiadomości i korków jeszcze tutaj nie sprawdzę.');return}await encyclopedia(q)}
 }catch(e){say(e.message||'Nie udało się pobrać informacji. Rozkłady zapisane w aplikacji pozostają dostępne.')}finally{setBusy(false);persistConversation()}}
$('ask').onclick=()=>ask();$('chatInput').onkeydown=e=>{if(e.key==='Enter'&&!e.shiftKey){e.preventDefault();ask()}};document.querySelectorAll('.prompt').forEach(b=>b.onclick=()=>ask(b.dataset.query));$('webSearch').onclick=()=>ask(undefined,true);
$('clearChat').onclick=()=>{generation++;assistant?.reset();history=[];$('messages').innerHTML='';try{sessionStorage.removeItem('pa2-session')}catch{}bubble('Zaczynamy nową rozmowę. W czym pomóc?')};
$('update').onclick=async()=>{if(updating||busy||$('search').disabled||busState.inFlight){toast('Poczekaj na zakończenie wyszukiwania.');return}updating=true;$('update').disabled=true;$('updateStatus').textContent='Pobieram i sprawdzam aktualne rozkłady. Obecna baza pozostaje dostępna.';try{const r=await native('update');$('updateStatus').textContent='Zapisano rozkłady z '+r.generated+'.';generation++;await init();setPoints(null,null);busStopList();$('results').innerHTML='';$('progress').textContent='Dane odświeżone. Wybierz trasę.';}catch(e){$('updateStatus').textContent=e.message}finally{updating=false;$('update').disabled=false}};

/* Dedicated verified vehicle lookup: stop -> route -> direction -> matched LIVE trip.
   No GPS invented from a published timetable, no street-distance-based ETA. */
const busState={stop:null,departures:[],seq:0,lastQuery:null,lineRoutes:new Map(),trainChoices:new Map(),snapshot:null,inFlight:false,refreshed:0};let appVisible=true;
const busModes=new Set([0,900,901,902,903,904,905,906,3,700,701,702,703,704,705,706,707,708,709,710,711,712,713,714,715,716]);
const busTram=t=>t===0||(t>=900&&t<=906);
const busRail=t=>PATransit.category(Number(t))==='rail';
const busDisplay=(mode,line,operator)=>busRail(mode)?'🚆 Pociąg '+line+(operator?' · '+operator:''):(busTram(mode)?'🚋 Tramwaj ':'🚌 Autobus ')+line;
function resetTrainPicker(){busState.trainChoices.clear();$('busTrainField').hidden=true;$('busTrain').replaceChildren(busOpt('','Wybierz linię i kierunek'));$('busTrain').disabled=true}
function busSelectedIds(){return busState.lineRoutes.get($('busLine').value)||[]}
function busRoutesAtStop(g){const pairs=new Map();
 for(const id of g.routes){
  const r=engine.idx.routes[id];if(!r||!busModes.has(Number(r[3]))&&!busRail(r[3]))continue;
  const key=[r[5],Number(r[3]),String(r[1]),r[6]||r[4]].join('|');
  if(!pairs.has(key))pairs.set(key,{line:String(r[1]),mode:Number(r[3]),source:r[5],operator:r[6]||r[4],ids:[]});
  pairs.get(key).ids.push(id);
 }
 return [...pairs.entries()].sort((a,b)=>a[1].line.localeCompare(b[1].line,'pl',{numeric:true})||a[1].mode-b[1].mode);
}
async function busDirectionsAtStop(group,routeIds){
 const selected=new Set(group.stops),headsigns=new Set();
 const loaded=await Promise.all(routeIds.map(id=>engine.route(id)));
 for(const data of loaded){const stoppingPatterns=new Set();
  (data.patterns||[]).forEach((pattern,i)=>{if(pattern.some(p=>selected.has(p[0])&&p[3]!==1))stoppingPatterns.add(i)});
  for(const run of data.runs||[])if(run[0]&&stoppingPatterns.has(run[2])&&run[1])headsigns.add(String(run[1]));
 }
 return [...headsigns].sort((a,b)=>a.localeCompare(b,'pl'));
}
if(typeof Element!=='undefined'&&!Element.prototype.replaceChildren){Element.prototype.replaceChildren=function(...items){while(this.firstChild)this.removeChild(this.firstChild);for(const item of items)this.appendChild(item)}}
const busOpt=(value,label)=>{const o=document.createElement('option');o.value=value;o.textContent=label;return o};
function clearBusMap(){const map=$('osmLiveMap');if(map&&map.__mapCleanup)map.__mapCleanup()}
function busResetResults(message=''){clearBusMap();busState.snapshot=null;busState.seq++;$('busResult').innerHTML='';$('busLoading').textContent=message;$('busRefresh').disabled=true}
function busStopList(){
 const q=$('busStop').value,box=$('busStops');busState.stop=null;busState.departures=[];busState.lineRoutes.clear();busResetResults();resetTrainPicker();
 $('busStopConfirmed').textContent='';$('busLine').replaceChildren(busOpt('','Najpierw wybierz przystanek'));$('busLine').disabled=true;
 $('busDirection').replaceChildren(busOpt('','Wybierz linię'));$('busDirection').disabled=true;$('checkBus').disabled=true;box.replaceChildren();
 $('busStop').setAttribute('aria-expanded','false');
 if(!engine||norm(q).length<3){box.classList.remove('open');return}
 const groups=engine.match(q,12);if(!groups.length){box.innerHTML='<p class="small" style="padding:12px">Nie znaleziono przystanku. Wpisz dokładniejszą nazwę.</p>';box.classList.add('open');$('busStop').setAttribute('aria-expanded','true');return}
 box.classList.add('open');$('busStop').setAttribute('aria-expanded','true');
 for(const g of groups){const b=document.createElement('button');b.type='button';b.setAttribute('role','option');b.className='list-option';b.innerHTML=esc(g.name)+'<small>'+esc(groupSummary(g))+'</small>';b.onclick=()=>busChooseStop(g);box.appendChild(b)}
}
function busChooseStop(g){
 busState.stop=g;busState.departures=[];busResetResults();resetTrainPicker();$('busStop').value=g.name;$('busStop').setAttribute('aria-expanded','false');$('busStops').classList.remove('open');
 $('busStopConfirmed').textContent='✓ Wybrano: '+g.name;
 const choices=busRoutesAtStop(g);busState.lineRoutes.clear();
 for(const [key,entry] of choices)busState.lineRoutes.set(key,entry.ids);
 $('busLine').replaceChildren(busOpt('','Wybierz linię'),...choices.map(([key,entry])=>busOpt(key,busDisplay(entry.mode,entry.line,entry.operator))));
 $('busLine').disabled=choices.length===0;
 $('busDirection').replaceChildren(busOpt('','Wybierz linię'));$('busDirection').disabled=true;$('checkBus').disabled=true;
 $('busProvider').textContent=choices.length?choices.length+' linii dostępnych dla przystanku · autobusowych, tramwajowych i kolejowych.':'Brak linii dla tego przystanku w aktualnej bazie.';
 if(!choices.length)$('busLoading').textContent='Ten przystanek nie ma kursów w dostępnej bazie. Sprawdź nazwę i rejon przystanku.';
}
async function busChangeLine(){
 const key=$('busLine').value,group=busState.stop,routeIds=busSelectedIds(),seq=++busState.seq;busState.departures=[];clearBusMap();busState.snapshot=null;resetTrainPicker();
 $('busDirection').replaceChildren(busOpt('','Wczytuję kierunki…'));$('busDirection').disabled=true;$('checkBus').disabled=true;$('busResult').innerHTML='';$('busRefresh').disabled=true;
 if(!key||!group||!routeIds.length){$('busDirection').replaceChildren(busOpt('','Wybierz linię'));$('busLoading').textContent='';return}
 const selected=engine.idx.routes[routeIds[0]],line=String(selected[1]);
 $('busLoading').textContent='Sprawdzam wszystkie kierunki linii '+line+' na tym przystanku…';
 try{
   const trainMode=busRail(selected[3]);
    const allDirections=await busDirectionsAtStop(group,routeIds);if(seq!==busState.seq)return;
    let directions=allDirections;
    if(trainMode){const upcoming=await engine.departures(group,wallNow(),line,null,72,routeIds);if(seq!==busState.seq)return;
      const fresh=new Set(upcoming.map(x=>x.headsign));directions=[...new Set(upcoming.map(x=>x.headsign).filter(Boolean)),...allDirections.filter(x=>!fresh.has(x))];}
   $('busDirection').replaceChildren(busOpt('','Wybierz kierunek'),...directions.map(x=>busOpt(x,x)));
   $('busDirection').disabled=!directions.length;
   $('busLoading').textContent=directions.length?'Wybierz kierunek, a Alfa sprawdzi najbliższy kurs.':'Brak kierunku odjazdu dla wybranego stanowiska. Spróbuj innego przystanku.';
   $('busProvider').textContent=trainMode?'Pociągi: godziny planowe. Pozycja na mapie tylko przy dostępnym i aktualnym GPS; nie każdy przewoźnik go udostępnia.':['warsaw','krakow_bus','krakow_mobilis','krakow_tram'].includes(selected[5])?'Dane rozkładowe; GPS i przyjazdy LIVE tylko po potwierdzeniu kursu.':'Dla tej linii może być dostępny wyłącznie rozkład planowy.';
 }catch(e){if(seq!==busState.seq)return;$('busDirection').replaceChildren(busOpt('','Nie udało się wczytać kierunków'));$('busLoading').textContent=e.message||'Nie mogę odczytać kierunków.'}
}
async function busChangeDirection(){
 busResetResults();resetTrainPicker();
 const ids=busSelectedIds(),group=busState.stop,direction=$('busDirection').value,seq=busState.seq;
 $('checkBus').disabled=!(group&&ids.length&&direction);if($('checkBus').disabled)return;
 const selected=engine.idx.routes[ids[0]];
 if(!busRail(selected[3])){$('busLoading').textContent='Gotowe. Sprawdź najbliższy kurs.';return}
 $('busTrainField').hidden=false;
 $('busTrain').replaceChildren(busOpt('','Wczytuję pociągi…'));$('busTrain').disabled=true;
 $('busLoading').textContent='Wyszukuję konkretne pociągi na tym przystanku…';
 try{
  const now=wallNow(),all=await engine.departures(group,now,String(selected[1]),direction,72,ids,1800);
  if(seq!==busState.seq)return;
  const seconds=Number(now.slice(11,13))*3600+Number(now.slice(14,16))*60;
  const trains=all.filter(d=>d.headsign===direction&&d.dep>=seconds-1800&&!d.headway).slice(0,48);
  busState.trainChoices.clear();
  const choices=trains.map((d,i)=>{const id='t'+i;busState.trainChoices.set(id,d);
   const h=Math.floor(((d.dep%86400)+86400)%86400/3600).toString().padStart(2,'0'),m=Math.floor(((d.dep%3600)+3600)%3600/60).toString().padStart(2,'0');
   return busOpt(id,h+':'+m+' · '+(d.train?'pociąg '+d.train+' · ':'')+d.operator+' → '+d.headsign)});
  $('busTrain').replaceChildren(busOpt('','Wszystkie najbliższe pociągi'),...choices);
  $('busTrain').disabled=!choices.length;
  $('busLoading').textContent=choices.length?'Możesz wybrać konkretny pociąg albo sprawdzić wszystkie najbliższe.':'Brak kursów w kolejnych 24 godzinach dla tego kierunku. Sprawdź datę rozkładu.';
 }catch(e){if(seq!==busState.seq)return;$('busTrain').replaceChildren(busOpt('','Nie mogę odczytać pociągów'));$('busLoading').textContent=e.message||'Nie udało się odczytać pociągów.'}
}
function busDistanceMetres(lat1,lon1,lat2,lon2){const rad=Math.PI/180,phi=(lat2-lat1)*rad,dl=(lon2-lon1)*rad,a=Math.sin(phi/2)**2+Math.cos(lat1*rad)*Math.cos(lat2*rad)*Math.sin(dl/2)**2;return Math.round(12742000*Math.asin(Math.min(1,Math.sqrt(a))))}
function busArrivalView(actual,d,checkedAt){
 const status=PALiveState.assess(d,actual,checkedAt);
 if(status.cancelled)return '<div class="bus-eta unknown"><strong>Kurs odwołany</strong><small>Wybierz inny kurs.</small></div>';
 if(status.skipped)return '<div class="bus-eta unknown"><strong>Przystanek pominięty</strong><small>Ten kurs się tutaj nie zatrzyma.</small></div>';
 if(d.headway)return '<div class="bus-eta schedule"><strong>Co około '+Math.ceil(d.headway/60)+' min</strong><small>'+clock(d.dep)+'–'+clock(d.frequencyEnd)+'. Rozkład częstotliwościowy; bez dokładnych godzin.</small></div>';
 if(status.etaFresh)return '<div class="bus-eta live"><strong>'+icon('clock')+(status.etaMinutes===0?'Przyjedzie teraz · LIVE':'Przyjedzie za '+status.etaMinutes+' min · LIVE')+'</strong><small>Prognoza z aktualizacji rozkładu · sprawdzona '+status.age+' s temu</small></div>';
 return '<div class="bus-eta schedule"><strong>'+icon('clock')+(d.arrival?'Planowy przyjazd ':'Planowy odjazd ')+clock(d.dep)+'</strong><small>Rozkład: '+clock(d.dep)+'. '+(actual.liveTimestamp||status.hadPosition?'Pokazuję godziny z rozkładu.':status.plannedMinutes<0?'Nie mam potwierdzenia odjazdu.':'Nie mam teraz informacji na żywo.')+'</small></div>';
}
function busStopCoords(d){
 const group=busState.stop;const candidates=group.stops.map(id=>engine.idx.stops[id]);const exact=candidates.find(s=>String(s[0])===String(d.stopId)||String(s[0]).split(':').at(-1)===String(d.stopId).split(':').at(-1));
 return exact||candidates[0];
}
function renderBusSnapshot(){
 const snap=busState.snapshot;if(!snap||!busState.stop)return;
 const rows=snap.departures.map(d=>{const actual=snap.live?.results?.find(v=>v.source===d.source&&v.stopId===d.stopId&&v.trip===d.trip&&v.serviceDay===d.serviceDay)||{...d,live:false};return{d,actual,status:PALiveState.assess(d,actual,snap.checked)}})
  .filter(x=>x.status.cancelled||x.status.skipped||x.status.plannedMinutes>=0||x.status.gpsFresh||x.status.etaFresh);
 rows.sort((a,b)=>Number(b.status.etaFresh||b.status.gpsFresh)-Number(a.status.etaFresh||a.status.gpsFresh)||a.d.dep-b.d.dep);
 if(!rows.length){clearBusMap();$('busResult').replaceChildren();$('busLoading').textContent='Wcześniejsze kursy już minęły. Odśwież dane, aby sprawdzić następne.';return}
 const first=rows[0],point=busStopCoords(first.d),position=first.status.gpsFresh?first.actual.position:null;
 const mapKey=[first.d.trip,position?.timestamp||'stop'].join('|'),old=$('osmLiveMap'),keep=old&&old.dataset.mapKey===mapKey;
 if(keep)old.remove();else clearBusMap();
 $('busResult').innerHTML=rows.slice(0,6).map(({d,actual,status},i)=>{
  const type=busRail(d.mode)?'train':busTram(Number(d.mode))?'tram':'bus',label=type==='train'?'Pociąg':type==='tram'?'Tramwaj':'Autobus';
  const state=status.cancelled?'Odwołany kurs':status.skipped?'Przystanek pominięty':status.gpsFresh?'Świeża pozycja GPS':status.etaFresh?'Prognoza przyjazdu LIVE':status.hadPosition?'Pozycja GPS jest nieaktualna':'Rozkład planowy';
  return '<article class="card trip"><div class="bus-card-header"><span class="bus-vehicle-line">'+icon(type)+esc(label+' '+(d.train||d.line)+(type==='train'?' · '+(d.operator||'Kolej'):''))+'</span><span class="pill">'+clock(d.dep)+'</span></div><p class="bus-destination">'+icon('arrow-right')+esc(d.headsign)+'</p><div class="bus-status '+(status.gpsFresh||status.etaFresh?'ok':'warn')+'">'+icon(status.gpsFresh?'pin':'clock')+esc(state)+'</div>'+busArrivalView(actual,d,snap.checked)+'<p class="small">Przystanek: <strong>'+esc(busState.stop.name)+'</strong>'+(d.platform?' · stanowisko '+esc(d.platform):'')+'</p>'+(d.pickupType===3?'<p class="small">Przystanek na żądanie: zasygnalizuj zamiar wejścia.</p>':d.pickupType===2?'<p class="small">Wsiadanie wymaga uzgodnienia z przewoźnikiem.</p>':'')+(status.gpsFresh?'<p class="small">Pomiar GPS sprzed '+status.positionAge+' s. Położenie nie jest prognozą przyjazdu.</p>':status.hadPosition?'<p class="small">Ostatni pomiar jest starszy niż 2 minuty. Znacznik pojazdu został ukryty.</p>':'')+(i===0?'<div id="osmLiveMap" role="region" aria-label="Mapa pojazdu i przystanku"></div>':'')+'<details class="bus-source"><summary>Źródło i status</summary>'+esc(actual.provenance||'Dane rozkładowe: '+d.source)+'</details></article>';
 }).join('');
 const node=$('osmLiveMap');if(keep)node.replaceWith(old);else if(point&&window.PAMap){node.dataset.mapKey=mapKey;PAMap.mount(node,{lat:point[3],lon:point[4]},position?{lat:position.latitude,lon:position.longitude}:null,{mode:first.d.mode,line:first.d.line})}
 $('busProvider').textContent='Sprawdzono '+new Intl.DateTimeFormat('pl-PL',{timeZone:'Europe/Warsaw',hour:'2-digit',minute:'2-digit',second:'2-digit'}).format(new Date(snap.checked*1000))+' · odświeżanie co 30 s';
}
async function checkLiveBus(){
 if(updating||busState.inFlight||!engine||!busState.stop||!busSelectedIds().length||!$('busDirection').value)return;
 const seq=++busState.seq,now=wallNow(),routeIds=busSelectedIds(),line=String(engine.idx.routes[routeIds[0]][1]),direction=$('busDirection').value,rail=busRail(engine.idx.routes[routeIds[0]][3]),specific=busState.trainChoices.get($('busTrain').value);
 busState.inFlight=true;$('checkBus').disabled=true;$('busRefresh').disabled=true;$('busLoading').textContent='Sprawdzam rozkład i dostępne dane pojazdu…';
 try{
  const all=await engine.departures(busState.stop,now,line,direction,72,routeIds,1800);if(seq!==busState.seq)return;
  const seconds=Number(now.slice(11,13))*3600+Number(now.slice(14,16))*60;
  const matching=all.filter(d=>d.headsign===direction&&d.dep>=seconds-1800&&(specific?d.trip===specific.trip&&d.serviceDay===specific.serviceDay&&d.stopId===specific.stopId&&d.dep===specific.dep:d.dep<=seconds+(rail?21600:7200)));
  const relevant=specific?matching.slice(0,1):[...matching.filter(d=>d.dep<seconds).slice(-4),...matching.filter(d=>d.dep>=seconds).slice(0,8)];
  if(!relevant.length){busResetResults(specific?'Wybrany pociąg nie jest już dostępny. Wybierz inny kurs.':rail?'Brak pociągów w kolejnych 6 godzinach. Spróbuj innego kierunku.':'Brak kursów w najbliższych 2 godzinach. Spróbuj innego kierunku.');return}
  let live=null,message='';try{live=await native('live',{departures:relevant})}catch(e){message='Nie mogę pobrać LIVE. Pokazuję rozkład planowy.'}
  if(seq!==busState.seq)return;
  const checked=Number(live?.checkedAt||Date.now()/1000);busState.snapshot={departures:relevant,live,checked};busState.refreshed=Date.now();
  renderBusSnapshot();$('busLoading').textContent=message||(live?.results?.some(x=>x.live)?'Dane pojazdu sprawdzone.':'Brak potwierdzonych danych LIVE. Dostępny jest rozkład planowy.');
 }catch(e){if(seq===busState.seq)$('busLoading').textContent=e.message||'Nie udało się sprawdzić kursu.'}
 finally{busState.inFlight=false;if(seq===busState.seq||!busState.snapshot){$('checkBus').disabled=!(busState.stop&&busSelectedIds().length&&$('busDirection').value);$('busRefresh').disabled=$('checkBus').disabled}}
}
function tickBus(){if(!appVisible||document.hidden||currentTab!=='liveBus'||!busState.snapshot)return;renderBusSnapshot();if(!busState.inFlight&&Date.now()-busState.refreshed>=30000)checkLiveBus()}
const liveTimer=setInterval(tickBus,5000);
window.PAVisibility=visible=>{appVisible=!!visible;if(appVisible){tickBus();if(currentTab==='stopBoard')refreshHomeStop()}};
document.addEventListener('visibilitychange',()=>{if(!document.hidden)tickBus()});
window.addEventListener('pagehide',()=>{clearInterval(liveTimer);clearBusMap()});
$('busStop').oninput=busStopList;$('busStop').onfocus=()=>{if(!busState.stop)busStopList()};
$('busLine').onchange=busChangeLine;$('busDirection').onchange=busChangeDirection;$('busTrain').onchange=()=>busResetResults();
$('checkBus').onclick=checkLiveBus;$('busRefresh').onclick=checkLiveBus;$('busQuick').onclick=()=>tab('liveBus');

/* Full stop view opened from the native home board, within the existing regional module. */
const homeBoard={stop:null,snapshot:null,inFlight:false,refreshed:0};
async function openHomeStop(id){
 if(!engine||!id)return;const sid=engine.idx.stops.findIndex(s=>s[0]===id);if(sid<0)return;const s=engine.idx.stops[sid];
 homeBoard.stop={name:s[1],stops:[sid],routes:new Set(s[5])};$('homeStopTitle').textContent=s[1]+(s[2]?' · '+s[2]:'');tab('stopBoard');
 PAMap.mount($('homeStopMap'),{lat:s[3],lon:s[4]},null);await refreshHomeStop();
}
async function refreshHomeStop(){
 if(!homeBoard.stop||homeBoard.inFlight||!appVisible||currentTab!=='stopBoard')return;homeBoard.inFlight=true;const requestedStop=homeBoard.stop,requestedKind=$('boardKind').value;
 try{const departures=$('boardKind').value==='arrivals'?await engine.arrivals(homeBoard.stop,wallNow(),72):await engine.departures(homeBoard.stop,wallNow(),null,null,72,null,1800);if(homeBoard.stop!==requestedStop||$('boardKind').value!==requestedKind)return;homeBoard.snapshot={departures,live:null,checked:Date.now()/1000};renderHomeStop();
  let live=null;try{if($('boardKind').value!=='arrivals')live=await native('live',{departures:departures.filter(d=>!d.headway).slice(0,36)})}catch{}if(homeBoard.stop!==requestedStop||$('boardKind').value!==requestedKind)return;homeBoard.snapshot={departures,live,checked:live?.checkedAt||Date.now()/1000};homeBoard.refreshed=Date.now();renderHomeStop();
 }catch(e){$('homeStopStatus').textContent=e.message}finally{homeBoard.inFlight=false;if(homeBoard.stop!==requestedStop||$('boardKind').value!==requestedKind)refreshHomeStop()}
}
function renderHomeStop(){
 const snap=homeBoard.snapshot;if(!snap)return;
 const wanted=$('boardMode').value,operator=$('boardOperator').value,kind=$('boardKind').value;
 const operators=[...new Set(snap.departures.map(d=>d.operator).filter(Boolean))].sort();
 $('boardOperator').innerHTML='<option value="">Wszyscy przewoźnicy</option>'+operators.map(o=>'<option value="'+esc(o)+'">'+esc(o)+'</option>').join('');const selectedOperator=operators.includes(operator)?operator:'';$('boardOperator').value=selectedOperator;
 const rows=snap.departures.filter(d=>(!wanted||PATransit.category(d.mode)===wanted)&&(!selectedOperator||d.operator===selectedOperator)).map(d=>{const actual=snap.live?.results?.find(a=>a.source===d.source&&a.trip===d.trip&&a.stopId===d.stopId&&a.serviceDay===d.serviceDay)||{};return{d,actual,status:PALiveState.assess(d,actual,snap.checked)}}).filter(x=>x.d.headway||x.status.cancelled||x.status.skipped||x.status.etaFresh||x.status.plannedMinutes>=0).slice(0,36);
 $('homeStopStatus').textContent=rows.length?'Godziny planowe · aktualizacje na żywo, gdy są dostępne':'Brak kolejnych '+(kind==='arrivals'?'przyjazdów':'odjazdów')+' w wybranym czasie i filtrze.';
 $('homeStopRows').innerHTML=rows.map(({d,actual,status})=>'<article class="card"><div class="row between wrap"><span class="pill">'+esc(mode(d.mode)+' · '+(d.operator||'')+' · '+(d.train?'Pociąg '+d.train:d.line))+'</span><strong class="board-time">'+(d.headway?'co ~'+Math.ceil(d.headway/60)+' min':clock(d.dep))+'</strong></div><p class="station">'+(kind==='arrivals'?'Z: ':'→ ')+esc(d.headsign)+'</p>'+busArrivalView(actual,d,snap.checked)+(status.gpsFresh?'<p class="small">Pozycja pojazdu dostępna · odjazd według rozkładu</p>':'')+(d.intermediate?.length?'<details><summary>Stacje pośrednie</summary>'+d.intermediate.map(p=>'<p class="small">'+clock(p.arrival)+' · '+esc(p.name)+'</p>').join('')+'</details>':'')+'<details><summary>Źródło informacji</summary><p class="small">'+esc(engine.idx.sources.find(s=>s.id===d.source)?.credit||d.source)+'</p></details></article>').join('');
}
for(const id of ['boardMode','boardOperator'])$(id).onchange=renderHomeStop;
$('boardKind').onchange=refreshHomeStop;
$('homeStopRefresh').onclick=refreshHomeStop;$('homeStopBack').onclick=()=>native('back').catch(()=>{});
const homeTimer=setInterval(()=>{if(!appVisible||document.hidden||currentTab!=='stopBoard')return;renderHomeStop();if(Date.now()-homeBoard.refreshed>=30000)refreshHomeStop()},5000);
window.addEventListener('pagehide',()=>{clearInterval(homeTimer);const m=$('homeStopMap');if(m.__mapCleanup)m.__mapCleanup()});

$('official').onclick=()=>native('official',{url:'https://portalpasazera.pl/'}).catch(e=>toast(e.message));$('goDashboard').onclick=()=>native('back').catch(e=>toast(e.message));
$('chatInput').addEventListener('focus',()=>{document.body.classList.add('keyboard-open');setTimeout(scrollChat,100)});
$('chatInput').addEventListener('blur',()=>{document.body.classList.remove('keyboard-open');setTimeout(layoutChat,100)});
window.addEventListener('resize',layoutChat);if(window.visualViewport)window.visualViewport.addEventListener('resize',layoutChat);
window.addEventListener('offline',()=>toast('Brak internetu. Rozkłady zapisane w aplikacji działają offline.'));init();
})();
