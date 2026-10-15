(function(){'use strict';
const $=id=>document.getElementById(id),{norm,canonical}=PATransit,{wallNow,parseTime,localDate,format}=PAConversation;
let activeSearch=0;let engine,assistant,start=null,end=null,results=[],generation=0,currentTab='planner',selected=null,noteText='',noteWeather='',busy=false,requestNo=0,manualTime=false;const callbacks=new Map();
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const clock=sec=>{const d=Math.floor(sec/86400),x=((sec%86400)+86400)%86400;return String(Math.floor(x/3600)).padStart(2,'0')+':'+String(Math.floor(x%3600/60)).padStart(2,'0')+(d>0?' (+'+d+' dzień)':'')};
const minutes=s=>Math.ceil(s/60);const duration=sec=>{const m=minutes(sec);return m>=60?Math.floor(m/60)+' godz. '+(m%60?m%60+' min':''):m+' min'};
const mode=t=>t===1?'Metro':t===0||t===900?'Tramwaj':t===2||t===100||t===102?'Pociąg':'Autobus';
const load=(key,fallback)=>{try{return JSON.parse(localStorage.getItem(key))||fallback}catch{return fallback}};
function save(key,value){try{localStorage.setItem(key,JSON.stringify(value))}catch{toast('Nie udało się zapisać danych. Sprawdź wolne miejsce.')}}
let toastTimer;function toast(text){clearTimeout(toastTimer);$('toast').textContent=text;$('toast').classList.remove('hidden');toastTimer=setTimeout(()=>$('toast').classList.add('hidden'),3200)}
window.PANativeReply=(id,r)=>{const c=callbacks.get(id);if(!c)return;callbacks.delete(id);clearTimeout(c.timer);r.ok?c.resolve(r.data):c.reject(Error(r.message||'Czynność nie powiodła się.'))};
function native(type,payload={}){return new Promise((resolve,reject)=>{if(!window.PADevice){if(type==='copy'&&navigator.clipboard){navigator.clipboard.writeText(payload.text).then(()=>resolve({saved:true}),reject);return}reject(Error('Ta funkcja działa w zainstalowanej aplikacji PA.'));return}const id=String(++requestNo),timer=setTimeout(()=>{callbacks.delete(id);reject(Error('Upłynął czas oczekiwania. Spróbuj ponownie.'))},type==='update'?180000:50000);callbacks.set(id,{resolve,reject,timer});PADevice.request(JSON.stringify({id,type,payload}))})}
function tab(name){currentTab=name;document.body.classList.remove('keyboard-open');if(document.activeElement?.tagName==='TEXTAREA')document.activeElement.blur();document.querySelectorAll('.panel').forEach(x=>x.classList.toggle('active',x.id===name));document.querySelectorAll('nav button').forEach(x=>{x.classList.toggle('active',x.dataset.tab===name);x.setAttribute('aria-current',x.dataset.tab===name?'page':'false')});if(name==='favorites')renderFavorites();window.scrollTo({top:0,behavior:'auto'});if(name==='assistant')scrollChat()}
window.PABack=()=>{if(!$('settings').classList.contains('hidden')){$('settings').classList.add('hidden');layoutChat();return}if(currentTab!=='planner'){tab('planner');return}native('back').catch(()=>{})};
$('back').onclick=window.PABack;document.querySelectorAll('nav button').forEach(b=>b.onclick=()=>tab(b.dataset.tab));$('settingsToggle').onclick=()=>{$('settings').classList.toggle('hidden');layoutChat()};
function appearance(){const theme=$('theme').value,dark=theme==='dark'||theme==='auto'&&window.matchMedia('(prefers-color-scheme:dark)').matches;document.documentElement.dataset.theme=dark?'dark':'light';document.body.classList.toggle('senior',$('senior').value==='large');save('pa2-appearance',{theme,senior:$('senior').value});requestAnimationFrame(layoutChat)}
const style=load('pa2-appearance',{theme:'auto',senior:'normal'});$('theme').value=style.theme;$('senior').value=style.senior;$('theme').onchange=$('senior').onchange=appearance;appearance();const media=window.matchMedia('(prefers-color-scheme:dark)');if(media.addEventListener)media.addEventListener('change',appearance);else if(media.addListener)media.addListener(appearance);
function updateDateLabel(){const raw=$('departAt').value;if(!raw||!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(raw)){$('dateDisplay').textContent='Wybierz datę i godzinę';return}const [d,t]=raw.split('T'),[yy,mm,dd]=d.split('-');const today=wallNow().slice(0,10);$('dateDisplay').textContent=(d===today?'Dzisiaj · ':'')+dd+'.'+mm+'.'+yy+' · '+t.slice(0,5)}
$('departAt').value=wallNow();updateDateLabel();$('departAt').onchange=()=>{manualTime=true;updateDateLabel();if(start&&end)search()};$('now').onclick=()=>{manualTime=false;$('departAt').value=wallNow();updateDateLabel();if(start&&end)search()};
function sourceNotice(idx){const dates=idx.days.filter((d,i)=>idx.covered[i]);$('coverage').textContent=idx.routes.length+' linii i relacji · rozkład '+dates[0]+' – '+dates.at(-1)+'. Dostępny offline.';$('sources').innerHTML=idx.sources.map(s=>'<p><b>'+esc(s.credit)+'</b><br>'+esc(s.url)+'<br>Pobrano '+esc(s.downloaded.slice(0,16).replace('T',' '))+'. Przetworzono do Planera PA.'+(s.attributions||[]).map(a=>'<br>'+esc(a.organization_name)+' '+esc(a.attribution_url||'')).join('')+'</p>').join('')}
async function init(){try{const r=await fetch('/index_all.json',{cache:'no-store'});if(!r.ok)throw Error('Nie można odczytać bazy.');const idx=await r.json();if(idx.schema!==2)throw Error('Nieobsługiwana wersja rozkładu.');engine=new PATransit.Engine(idx,async id=>{const r=await fetch('/route_all_'+id+'.json',{cache:'no-store'});if(!r.ok)throw Error('Brakuje pliku rozkładu linii. Odśwież dane.');return r.json()});assistant=new PAConversation.Assistant(engine);sourceNotice(idx);restoreConversation();renderFavorites();}catch(e){$('coverage').textContent=e.message;$('progress').textContent='Nie udało się uruchomić Planera. Spróbuj ponownie otworzyć ten moduł.'}}
function groupSummary(g){const sources=[...new Set(g.stops.map(i=>engine.idx.stops[i][6]))],names={warsaw:'WTP Warszawa',mld:'MLD',krakow_bus:'Kraków',krakow_tram:'Kraków',krakow_mobilis:'Kraków',kmlrail:'Koleje Małopolskie',wkd:'WKD',polishrail:'kolej'};return [...new Set(sources.map(s=>names[s]||s))].join(' · ')}
function suggest(which){const text=$(which).value,box=$('suggest'+(which==='from'?'From':'To'));box.innerHTML='';$(which).setAttribute('aria-expanded','false');if(!engine||norm(text).length<3){box.classList.remove('open');return}const groups=engine.match(text);box.classList.add('open');$(which).setAttribute('aria-expanded','true');if(!groups.length){box.innerHTML='<p class="small" style="padding:12px">Nie znaleziono przystanku. Spróbuj innej pisowni.</p>';return}groups.slice(0,12).forEach(g=>{const b=document.createElement('button');b.type='button';b.setAttribute('role','option');b.innerHTML=esc(g.name)+'<small>'+esc(groupSummary(g))+'</small>';b.onclick=()=>{$(which).value=g.name;box.classList.remove('open');$(which).setAttribute('aria-expanded','false');$('confirm'+(which==='from'?'From':'To')).focus()};box.appendChild(b)});if(groups.length>12){const more=document.createElement('p');more.className='small';more.style.padding='8px 12px';more.textContent='Wpisz dokładniejszą nazwę, aby zawęzić listę.';box.appendChild(more)}}
function edit(which){if(which==='from')start=null;else end=null;$(which).setAttribute('aria-expanded','false');const suffix=which==='from'?'From':'To';$('status'+suffix).textContent='';$('confirm'+suffix).textContent='Akceptuj';$(which).readOnly=false;generation++;$('results').innerHTML=''}
function accept(which){if(!engine)return;const suffix=which==='from'?'From':'To';if($(which).readOnly){edit(which);$(which).focus();return}const r=engine.resolve($(which).value);if(!r.group){$('status'+suffix).textContent='Wybierz pełną nazwę z podpowiedzi.';suggest(which);return}if(which==='from')start=r.group;else end=r.group;$(which).value=r.group.name;$(which).readOnly=true;$(which).setAttribute('aria-expanded','false');$('status'+suffix).textContent='✓ '+r.group.name+' · wybrano';$('status'+suffix).classList.remove('pop');void $('status'+suffix).offsetWidth;$('status'+suffix).classList.add('pop');$('confirm'+suffix).textContent='Zmień';$('suggest'+suffix).classList.remove('open');if(start&&end){if(!manualTime)$('departAt').value=wallNow();updateDateLabel();search()}}
for(const which of ['from','to']){$(which).oninput=()=>{edit(which);suggest(which)};$(which).onfocus=()=>{if(!$(which).readOnly)suggest(which)};$('confirm'+(which==='from'?'From':'To')).onclick=()=>accept(which)}
function setPoints(a,b,when){start=a;end=b;for(const [id,g]of [['from',a],['to',b]]){const suffix=id==='from'?'From':'To';$(id).value=g?g.name:'';$(id).readOnly=!!g;$('confirm'+suffix).textContent=g?'Zmień':'Akceptuj';$('status'+suffix).textContent=g?'✓ '+g.name+' · wybrano':''}if(when){$('departAt').value=when;manualTime=true}updateDateLabel()}
$('swap').onclick=()=>{setPoints(end,start);if(start&&end)search()};
function options(){const modes={bus:[3,700,701,702,704,714],tram:[0,900],metro:[1],rail:[2,100,101,102,103,106,109]};return{minTransfer:Number($('transfer').value)||5,maxTransfers:Number($('changes').value),walk:$('walk').checked,sort:$('sort').value,modes:modes[$('modes').value]}}
for(const id of ['sort','transfer','changes','modes','walk'])$(id).onchange=()=>{if(start&&end)search()};
function renderTrip(r,i){const rides=r.legs.filter(l=>!l.walk);let steps='';r.legs.forEach((l,j)=>{const previous=r.legs[j-1];if(previous&&!l.walk){const wait=l.dep-previous.arr;if(wait>0)steps+='<div class="risk '+(wait<300?'tight':'')+'">Przesiadka · '+minutes(wait)+' min oczekiwania</div>'}if(l.walk){steps+='<div class="leg"><p>Pieszo · ok. '+l.metres+' m · '+duration(l.arr-l.dep)+'</p><p class="small">'+esc(engine.idx.stops[l.fromId][1])+' → '+esc(engine.idx.stops[l.toId][1])+'</p><p class="small">Dojście orientacyjne; sprawdź przejście.</p></div>';return}steps+='<div class="leg"><div class="row wrap"><span class="pill">'+esc(mode(l.mode)+' '+(l.train||l.line))+'</span><span class="small">'+clock(l.dep)+' → '+clock(l.arr)+'</span></div><p class="station">'+esc(l.from)+' → '+esc(l.to)+'</p><p class="small">Kierunek '+esc(l.headsign)+(l.platformFrom?' · stanowisko/peron '+esc(l.platformFrom):'')+'</p></div>'});
return '<article class="card trip"><div class="trip-head"><div><div class="trip-time">'+clock(r.dep)+' <small>→</small> '+clock(r.arr)+'</div><span class="small">'+(r.transfers?r.transfers+' '+(r.transfers===1?'przesiadka':'przesiadki'):'Bez przesiadek')+'</span></div><div class="duration"><strong>'+duration(r.duration)+'</strong><br>Rozkład planowy</div></div><div class="route-strip">'+rides.map(l=>'<span class="pill">'+esc(l.train||l.line)+'</span>').join('<span class="small">→</span>')+'</div><details><summary>Przebieg podróży</summary><div class="timeline">'+steps+'</div></details><button class="btn primary full chooseTrip" data-index="'+i+'">Wybieram · pokaż notatkę</button><p class="footnote">'+esc([...new Set(rides.map(l=>l.agency))].join(' · '))+'</p></article>'}
function showResults(rows){results=rows;$('results').innerHTML=rows.map(renderTrip).join('');document.querySelectorAll('.chooseTrip').forEach(b=>b.onclick=()=>selectTrip(Number(b.dataset.index)))}
function revealResults(){const target=$('progress');if(!target)return;const reduce=window.matchMedia?.('(prefers-reduced-motion:reduce)').matches;const top=target.getBoundingClientRect().top+window.scrollY-114;window.scrollTo({top:Math.max(0,top),behavior:reduce?'auto':'smooth'})}
async function search(){if(!engine)return;const seq=++generation;activeSearch=seq;if(!start||!end){$('progress').textContent='Najpierw zaakceptuj początek i cel.';return}$('progress').textContent='Sprawdzam kursy i przesiadki…';$('results').innerHTML='';$('search').disabled=true;const when=$('departAt').value;updateDateLabel();assistant.sync(start,end,when);
 try{const rows=await engine.find(start,end,when,{...options(),cancelled:()=>seq!==generation,progress:(round,percent)=>{if(seq===generation)$('progress').textContent='Sprawdzam połączenia · etap '+round+' · '+percent+'%'}});if(seq!==generation)return;showResults(rows);$('progress').textContent=rows.length?'Znalazłem '+rows.length+' połączeń na '+when.slice(0,10)+'. Wybierz najwygodniejsze.':'Brak połączeń spełniających te ustawienia w kolejnych 24 godzinach. Sprawdź inny przystanek lub zwiększ liczbę przesiadek.';persistConversation();if(rows.length)setTimeout(()=>{if(seq===generation&&currentTab==='planner')revealResults()},90);}
 catch(e){if(seq===generation){$('progress').textContent=e.message;$('results').innerHTML=''}}finally{if(activeSearch===seq)$('search').disabled=false}}
$('search').onclick=search;
function noteFor(r){let lines=['🧭 Twoja podróż z Alfą',r.date,'','Start: '+start.name,'Cel: '+end.name,'Wyjazd: '+clock(r.dep)+' · przyjazd: '+clock(r.arr),'Łącznie: '+duration(r.duration)+' · '+(r.transfers?r.transfers+' przesiadki':'bez przesiadek'),''];r.legs.forEach((l,i)=>{if(l.walk){lines.push('🚶 Dojście orientacyjne: ok. '+l.metres+' m, '+duration(l.arr-l.dep)+'.');return}if(i&&l.dep>r.legs[i-1].arr)lines.push('Przesiadka: '+minutes(l.dep-r.legs[i-1].arr)+' min oczekiwania.');lines.push(mode(l.mode)+' '+(l.train||l.line)+' → '+l.headsign,clock(l.dep)+' '+l.from+(l.platformFrom?' (stanowisko/peron '+l.platformFrom+')':''),clock(l.arr)+' '+l.to,'')});if(noteWeather)lines.push('🌤 '+noteWeather,'');lines.push('Godziny planowe z GTFS. Opóźnienia i zmiany nie są w tej notatce potwierdzone.','Wskazówka: przyjdź kilka minut wcześniej i sprawdź kierunek oraz stanowisko.');if(r.legs.some(l=>l.source==='polishrail'))lines.push('Przed podróżą kolejową potwierdź rozkład i bilet w Portalu Pasażera.');return lines.join('\n')}
function selectTrip(i){selected=results[i];noteWeather='';noteText=noteFor(selected);$('note').textContent=noteText;$('noteStatus').textContent='';tab('notePanel')}
$('returnResults').onclick=()=>tab('planner');$('copyNote').onclick=async()=>{try{await native('copy',{text:noteText});toast('Notatka skopiowana')}catch(e){$('noteStatus').textContent=e.message}};$('saveNote').onclick=async()=>{try{await native('note',{text:noteText});$('noteStatus').textContent='Zapisano w notatniku PA.'}catch(e){$('noteStatus').textContent=e.message}};
function weatherText(w){return w.city+' · '+w.date+': '+w.min+'–'+w.max+'°C, szansa opadów '+w.rain+'%.'+(w.rain>=50?' Warto zabrać parasol.':'')}
$('noteWeather').onclick=async()=>{if(!selected)return;$('noteStatus').textContent='Sprawdzam prognozę…';try{const city=end.name.replace(/\s+(?:Dworzec|Most|PKP|Dom Ludowy|Główny|Centralna).*$/i,'');const diff=Math.round((localDate(selected.date+'T12:00')-localDate(wallNow().slice(0,10)+'T12:00'))/86400000);if(diff<0||diff>6)throw Error('Prognoza jest dostępna na 7 dni od dzisiaj.');const stop=engine.idx.stops[selected.legs.at(-1).toId];const w=await native('weather',{city:end.name,latitude:stop[3],longitude:stop[4],offset:diff});noteWeather=weatherText(w)+' Źródło: '+w.source;noteText=noteFor(selected);$('note').textContent=noteText;$('noteStatus').textContent='Dodano prognozę.'}catch(e){$('noteStatus').textContent=e.message}};
let favorites=load('pa2-favorites',[]);
function addFavorite(value){if(favorites.some(x=>x.from===value.from&&x.to===value.to)){toast('Już jest w ulubionych');return}favorites.push(value);save('pa2-favorites',favorites);toast('Zapisano w ulubionych')}
$('saveRoute').onclick=()=>{if(!start){toast('Najpierw zaakceptuj przystanek początkowy');return}addFavorite({from:start.name,to:end?end.name:null})};
function renderFavorites(){const box=$('favoriteList');box.innerHTML='';if(!favorites.length){box.innerHTML='<div class="card"><h3>Tu będą Twoje stałe trasy</h3><p class="small">Zaakceptuj przystanek lub oba punkty podróży i wybierz „Zapisz trasę”.</p></div>';return}favorites.forEach((f,i)=>{const div=document.createElement('div');div.className='card';div.innerHTML='<h3>'+esc(f.from)+(f.to?' → '+esc(f.to):'')+'</h3><div class="row"><button class="btn primary use">'+(f.to?'Sprawdź teraz':'Wybierz przystanek')+'</button><button class="btn quiet remove">Usuń</button></div>';div.querySelector('.use').onclick=()=>{if(!engine)return;const a=engine.resolve(f.from).group,b=f.to?engine.resolve(f.to).group:null;if(!a||f.to&&!b){toast('Zapisanej nazwy nie ma w nowej bazie. Wybierz przystanek ponownie.');return}manualTime=false;setPoints(a,b,wallNow());manualTime=false;tab('planner');if(a&&b)search()};div.querySelector('.remove').onclick=()=>{favorites.splice(i,1);save('pa2-favorites',favorites);renderFavorites()};box.appendChild(div)})}
function layoutChat(){if(currentTab!=='assistant')return;const viewport=window.visualViewport?window.visualViewport.height:window.innerHeight;document.body.classList.toggle('compact-chat',viewport<540);const panel=$('assistant'),nav=document.querySelector('nav'),visible=getComputedStyle(nav).display!=='none',bottom=visible?Math.min(viewport,nav.getBoundingClientRect().top):viewport;panel.style.height=Math.max(180,bottom-panel.getBoundingClientRect().top-9)+'px'}
function scrollChat(){layoutChat();requestAnimationFrame(()=>{$('messages').scrollTop=$('messages').scrollHeight;requestAnimationFrame(()=>{$('messages').scrollTop=$('messages').scrollHeight})})}
let history=[];
function bubble(text,who='assistant',opts=[]){const b=document.createElement('div');b.className='bubble '+who;const body=document.createElement('div');body.textContent=text;b.appendChild(body);if(opts.length){const list=document.createElement('div');list.className='options';function addOption(option,parent){const x=document.createElement('button');x.className='btn';x.textContent=option.label||option;x.onclick=()=>option.action?option.action():ask(String(option));parent.appendChild(x)}
opts.slice(0,3).forEach(x=>addOption(x,list));if(opts.length>3){const more=document.createElement('div');more.className='options more-options hidden';opts.slice(3,12).forEach(x=>addOption(x,more));const toggle=document.createElement('button');toggle.className='btn quiet';toggle.textContent='Więcej propozycji ('+(Math.min(12,opts.length)-3)+')';toggle.setAttribute('aria-expanded','false');toggle.onclick=()=>{const open=more.classList.toggle('hidden');toggle.textContent=open?'Więcej propozycji ('+(Math.min(12,opts.length)-3)+')':'Zwiń propozycje';toggle.setAttribute('aria-expanded',String(!open));scrollChat()};list.appendChild(toggle);list.appendChild(more)}b.appendChild(list)}$('messages').appendChild(b);scrollChat();return b}
function remember(role,content){history.push({role,content:String(content).slice(0,3500)});history=history.slice(-20);persistConversation()}
function say(text,opts=[]){bubble(text,'assistant',opts);remember('assistant',text)}
function persistConversation(){if(!assistant)return;const s=assistant.state;try{sessionStorage.setItem('pa2-session',JSON.stringify({history,state:{...s,from:s.from?.name,to:s.to?.name,candidates:[],fromCandidates:[],toCandidates:[]}}))}catch{}}
function restoreConversation(){let last;try{last=JSON.parse(sessionStorage.getItem('pa2-session'))}catch{}if(last?.history?.length){history=last.history.slice(-20);history.forEach(m=>bubble(m.content,m.role));const s=last.state||{};assistant.state={...assistant.state,...s,from:s.from?engine.resolve(s.from).group:null,to:s.to?engine.resolve(s.to).group:null};if(assistant.state.from&&assistant.state.to)setPoints(assistant.state.from,assistant.state.to,assistant.state.when)}else{bubble((Number(wallNow().slice(11,13))>=18?'Dobry wieczór!':'Dzień dobry!')+' Jestem Alfa. Pomogę Ci zaplanować podróż, sprawdzić pogodę albo zapisać coś na później.\n\nDokąd się wybierasz?')}}
function setBusy(on,label='Sprawdzam…'){busy=on;$('ask').disabled=on;$('clearChat').disabled=on;$('typing').textContent=on?label:'';$('typing').classList.toggle('busy',on)}
function summarize(rows){return rows.slice(0,3).map((r,i)=>{const rides=r.legs.filter(l=>!l.walk);return(i+1)+'. '+clock(r.dep)+' → '+clock(r.arr)+' · '+rides.map(l=>l.train||l.line).join(' → ')+' · '+duration(r.duration)+(r.transfers?' · przesiadki: '+r.transfers:' · bez przesiadek')}).join('\n')}
async function routeAction(a){setPoints(a.from,a.to,a.when);$('typing').textContent='Sprawdzam rozkłady i przesiadki…';const rows=await engine.find(a.from,a.to,a.when,{...options(),mustLine:a.line});showResults(rows);if(rows.length){say((a.correction?a.correction+'\n':'')+(!rows.some(r=>r.transfers===0)?'Nie znalazłem bezpośredniego kursu z tego przystanku w przeszukanym oknie. Poniżej połączenia z przesiadką.\n':'')+'Z przystanku '+a.from.name+' do '+a.to.name+' · '+a.when.slice(0,10)+'\n'+summarize(rows)+'\n\nGodziny planowe. Wybierz połączenie, aby dostać notatkę.',rows.slice(0,3).map((r,i)=>({label:'Wybieram '+(i+1),action:()=>selectTrip(i)})))}else{
 let alternatives=[];const sourceName=canonical(a.from.name).split(' ')[0],endRoutes=new Set(a.to.routes);
 for(const g of new Set(engine.groups.values()))if(g!==a.from&&canonical(g.name).startsWith(sourceName+' ')&&[...g.routes].some(r=>endRoutes.has(r))){const nearest=Math.min(...a.from.stops.flatMap(i=>g.stops.map(j=>PATransit.distance(engine.idx.stops[i],engine.idx.stops[j]))));if(nearest<=2500)alternatives.push({g,nearest})}
 alternatives.sort((x,y)=>x.nearest-y.nearest);const checked=[];for(const item of alternatives.slice(0,4)){const r=await engine.find(item.g,a.to,a.when,{...options(),maxTransfers:0});if(r.length)checked.push({...item,result:r[0]})}
 const specific=!a.from.routes.size?'Ten przystanek nie ma czynnych kursów w wybranej bazie. ':'';
 say(specific+'Nie znalazłem połączenia z '+a.from.name+' do '+a.to.name+' spełniającego te ustawienia.'+(checked.length?'\nSprawdziłem rozkład dla innych przystanków:\n'+checked.map(x=>x.g.name+': '+clock(x.result.dep)+' → '+clock(x.result.arr)+' · '+x.result.legs.filter(l=>!l.walk).map(l=>l.line).join(', ')).join('\n')+'\nDojście trzeba sprawdzić osobno; nie potwierdzam dostępności drogi pieszej.':''),checked.map(x=>({label:x.g.name,action:()=>ask('Z '+x.g.name+' do '+a.to.name+' '+a.when.slice(0,10)+' o '+a.when.slice(11))})))
}}
async function departureAction(a){
 if(a.line&&![...a.from.routes].some(r=>norm(engine.idx.routes[r][1])===norm(a.line))){say('Linia '+a.line+' nie obsługuje przystanku '+a.from.name+' według tej bazy rozkładów. Wybierz inny przystanek lub linię.');assistant.state.asked='from';return}
 const departures=await engine.departures(a.from,a.when,a.line,a.direction);
 if(!departures.length&&a.direction){const other=await engine.departures(a.from,a.when,a.line,null);if(other.length){assistant.state.direction=null;assistant.state.asked='direction';say('Wcześniejszy cel nie wskazuje jednoznacznie kierunku tej linii. W którym kierunku jedziesz?',[...new Set(other.map(d=>d.headsign))]);return}}
 if(!departures.length){say((a.line&&!a.from.routes.size?'Linia nie ma tu potwierdzonych kursów. ':'')+'Brak pasujących odjazdów z '+a.from.name+' w kolejnych 24 godzinach. Spróbuj innej godziny albo kierunku.');return}
 const directions=[...new Set(departures.map(d=>d.headsign))];if(!a.direction&&directions.length>1){assistant.state.asked='direction';say('Z przystanku '+a.from.name+' mam kilka kierunków. Który wybierasz?',directions);return}
 if(a.type==='vehicle'){
  $('typing').textContent='Sprawdzam dostępne dane rzeczywiste…';let live;try{live=await native('live',{departures:departures.slice(0,6)})}catch(e){say('Nie mogę teraz pobrać danych rzeczywistych. '+e.message+'\n\nRozkład planowy:\n'+departures.slice(0,3).map(d=>clock(d.dep)+' · '+d.line+' → '+d.headsign).join('\n'));return}
  const first=live.results.find(x=>x.live&&!x.cancelled&&!x.skipped)||live.results[0];if(first.cancelled){say('Potwierdzono odwołanie kursu '+first.line+' → '+first.headsign+'. Sprawdź inne odjazdy.');return}
  if(first.eta){const when=new Date(first.eta*1000),left=Math.max(0,Math.ceil((first.eta*1000-Date.now())/60000));say('Linia '+first.line+' w kierunku '+first.headsign+' ma przyjechać na '+a.from.name+' o '+new Intl.DateTimeFormat('pl-PL',{timeZone:'Europe/Warsaw',hour:'2-digit',minute:'2-digit'}).format(when)+'. Pozostało około '+left+' min.\nPotwierdzone dane rzeczywiste. '+first.provenance);}
  else say('Linia '+first.line+' → '+first.headsign+'\nPrzystanek: '+a.from.name+'\nOdjazd planowy: '+clock(first.dep)+'.\n'+(first.position?'Mam zgodną pozycję GPS tego kursu, pomiar sprzed '+first.position.age+' s. Źródło nie potwierdza prognozy przyjazdu na ten przystanek.':'Nie mam świeżej, zgodnej pozycji GPS ani prognozy przyjazdu tego kursu. Podaję godzinę z rozkładu.')+'\n'+first.provenance);
 }else say('Planowe odjazdy z '+a.from.name+' · '+a.when.slice(0,10)+'\n'+departures.slice(0,5).map(d=>clock(d.dep)+' · '+d.line+' → '+d.headsign).join('\n')+'\nOpóźnienie nie zostało sprawdzone.');
}
async function encyclopedia(q){const r=await native('search',{query:q});if(!r.results?.length){say('Nie znalazłem odpowiedzi w Wikipedii. Wyszukiwanie aktualnych wydarzeń w całym internecie wymaga uruchomionej usługi AI online.');return}const b=bubble('Wyniki z Wikipedii · odczyt teraz. To wyszukiwanie encyklopedyczne, bez modelu AI.');r.results.forEach(x=>{const p=document.createElement('p'),title=document.createElement('strong');title.textContent=x.title;p.appendChild(title);p.appendChild(document.createTextNode('\n'+x.text));b.appendChild(p)});remember('assistant',r.results.map(x=>x.title+': '+x.text).join('\n'))}
async function ask(question,forceSearch=false){if(busy)return;if(!assistant){toast('Zaczekaj na wczytanie bazy');return}const q=(question??$('chatInput').value).trim();if(!q)return;$('chatInput').value='';$('quickPrompts').classList.add('hidden');bubble(q,'user');const prior=history.slice();remember('user',q);setBusy(true);
 try{
 let model=null;
 if($('aiOnline').checked&&!/pogod|temperatur|deszcz|przypomnij|zapisz|zanotuj/.test(norm(q))){try{const online=await native('ai',{question:q,messages:prior,when:wallNow(),search:forceSearch,context:{from:assistant.state.from?.name||'',to:assistant.state.to?.name||'',when:assistant.state.when||'',line:assistant.state.line||''}});if(online.type==='answer'){say(online.answer+(online.sources?.length?'\n\nŹródła: '+online.sources.map(x=>x.title+' · '+x.url).join('\n'):''));return}model=online.plan||null;$('aiStatus').textContent='AI online + dane Planera';}catch(e){$('aiStatus').textContent='AI niedostępna · działają narzędzia lokalne';say(e.message)}}
 const a=assistant.parse(q,model);
 if(forceSearch&&a.type==='general'){await encyclopedia(q);return}
 if(a.type==='route'){await routeAction(a);return}if(a.type==='departure'||a.type==='vehicle'){await departureAction(a);return}
 if(a.type==='clarify'){say(a.message,a.options);return}if(a.type==='answer'){say(a.message);return}
 if(a.type==='weather'){const w=await native('weather',a);say(weatherText(w)+'\nPrognoza sprawdzona teraz. '+w.source);return}
 if(a.type==='note'){await native('note',{text:a.text});say('Zapisałem w notatniku PA: „'+a.text+'”.');return}
 if(a.type==='reminder'){say('Ustawić przypomnienie „'+a.text+'” na '+a.when.replace('T',' ')+'?', [{label:'Zapisz przypomnienie',action:async()=>{try{const r=await native('reminder',a);say(r.message||'Przypomnienie zapisane.')}catch(e){say(e.message)}}}]);return}
 if(a.type==='general'){if(/wydarzen|dzieje sie|wiadomosc|aktualnosci/.test(norm(q))){say('Nie mam obecnie działającego wyszukiwania aktualnych wydarzeń w całym internecie. Mogę sprawdzić rozkład, pogodę, zapisać notatkę lub wyszukać wiedzę w Wikipedii.');return}await encyclopedia(q)}
 }catch(e){say(e.message||'Nie udało się pobrać informacji. Rozkłady zapisane w aplikacji pozostają dostępne.')}finally{setBusy(false);persistConversation()}}
$('ask').onclick=()=>ask();$('chatInput').onkeydown=e=>{if(e.key==='Enter'&&!e.shiftKey){e.preventDefault();ask()}};document.querySelectorAll('.prompt').forEach(b=>b.onclick=()=>ask(b.dataset.query));$('webSearch').onclick=()=>ask(undefined,true);
$('aiOnline').onchange=()=>{$('aiStatus').textContent=$('aiOnline').checked?'AI online: sprawdzę dostępność przy pytaniu':'Planer i narzędzia lokalne · bez modelu AI'};
$('clearChat').onclick=()=>{generation++;assistant?.reset();history=[];$('messages').innerHTML='';try{sessionStorage.removeItem('pa2-session')}catch{}bubble('Zaczynamy nową rozmowę. W czym pomóc?')};
$('update').onclick=async()=>{$('update').disabled=true;$('updateStatus').textContent='Pobieram i sprawdzam aktualne rozkłady. Obecna baza pozostaje dostępna.';try{const r=await native('update');$('updateStatus').textContent='Zapisano rozkłady z '+r.generated+'.';generation++;await init();setPoints(null,null);$('results').innerHTML='';$('progress').textContent='Dane odświeżone. Wybierz trasę.';}catch(e){$('updateStatus').textContent=e.message}finally{$('update').disabled=false}};

/* Dedicated verified vehicle lookup: stop -> route -> direction -> matched LIVE trip.
   No GPS invented from a published timetable, no street-distance-based ETA. */
const busState={stop:null,departures:[],seq:0,lastQuery:null};
const busModes=new Set([3,700,701,702,704,714]);
if(typeof Element!=='undefined'&&!Element.prototype.replaceChildren){Element.prototype.replaceChildren=function(...items){while(this.firstChild)this.removeChild(this.firstChild);for(const item of items)this.appendChild(item)}}
const busOpt=(value,label)=>{const o=document.createElement('option');o.value=value;o.textContent=label;return o};
function busResetResults(message=''){busState.seq++;$('busResult').innerHTML='';$('busLoading').textContent=message;$('busRefresh').disabled=true}
function busStopList(){
 const q=$('busStop').value,box=$('busStops');busState.stop=null;busState.departures=[];busResetResults();
 $('busStopConfirmed').textContent='';$('busLine').replaceChildren(busOpt('','Najpierw wybierz przystanek'));$('busLine').disabled=true;
 $('busDirection').replaceChildren(busOpt('','Wybierz linię'));$('busDirection').disabled=true;$('checkBus').disabled=true;box.replaceChildren();
 $('busStop').setAttribute('aria-expanded','false');
 if(!engine||norm(q).length<3){box.classList.remove('open');return}
 const groups=engine.match(q,12);if(!groups.length){box.innerHTML='<p class="small" style="padding:12px">Nie znaleziono przystanku. Wpisz dokładniejszą nazwę.</p>';box.classList.add('open');$('busStop').setAttribute('aria-expanded','true');return}
 box.classList.add('open');$('busStop').setAttribute('aria-expanded','true');
 for(const g of groups){const b=document.createElement('button');b.type='button';b.setAttribute('role','option');b.className='list-option';b.innerHTML=esc(g.name)+'<small>'+esc(groupSummary(g))+'</small>';b.onclick=()=>busChooseStop(g);box.appendChild(b)}
}
function busChooseStop(g){
 busState.stop=g;busState.departures=[];busResetResults();$('busStop').value=g.name;$('busStop').setAttribute('aria-expanded','false');$('busStops').classList.remove('open');
 $('busStopConfirmed').textContent='✓ Wybrano: '+g.name;
 const lines=[...new Set([...g.routes].filter(id=>busModes.has(engine.idx.routes[id][3])).map(id=>String(engine.idx.routes[id][1])))].sort((a,b)=>a.localeCompare(b,'pl',{numeric:true}));
 $('busLine').replaceChildren(busOpt('','Wybierz linię'),...lines.map(x=>busOpt(x,x)));$('busLine').disabled=lines.length===0;
 $('busDirection').replaceChildren(busOpt('','Wybierz linię'));$('busDirection').disabled=true;$('checkBus').disabled=true;
 if(!lines.length)$('busLoading').textContent='Ten przystanek nie ma autobusów w aktualnej bazie. Wybierz inny przystanek.';
}
async function busChangeLine(){
 const line=$('busLine').value,group=busState.stop,seq=++busState.seq;busState.departures=[];
 $('busDirection').replaceChildren(busOpt('','Wczytuję kierunki…'));$('busDirection').disabled=true;$('checkBus').disabled=true;$('busResult').innerHTML='';$('busRefresh').disabled=true;
 if(!line||!group){$('busDirection').replaceChildren(busOpt('','Wybierz linię'));$('busLoading').textContent='';return}
 $('busLoading').textContent='Sprawdzam dostępne kierunki linii '+line+'…';
 try{
   const dep=await engine.departures(group,wallNow(),line,null,48);if(seq!==busState.seq)return;
   busState.departures=dep;
   const dir=[...new Set(dep.map(x=>x.headsign).filter(Boolean))].sort((a,b)=>a.localeCompare(b,'pl'));
   $('busDirection').replaceChildren(busOpt('','Wybierz kierunek'),...dir.map(x=>busOpt(x,x)));$('busDirection').disabled=!dir.length;
   $('busLoading').textContent=dir.length?'Wybierz kierunek i sprawdź autobus.':'Nie ma najbliższych odjazdów tej linii w dostępnej bazie na ten dzień.';
   $('busProvider').textContent=dep.some(d=>['warsaw','krakow_bus','krakow_mobilis','krakow_tram'].includes(d.source))?'Dla części kursów dostępny jest świeży GPS i LIVE.':'Dla tej linii może być dostępny tylko rozkład planowy.';
 }catch(e){if(seq!==busState.seq)return;$('busDirection').replaceChildren(busOpt('','Brak danych'));$('busLoading').textContent=e.message||'Nie mogę odczytać kierunków.'}
}
function busChangeDirection(){busResetResults();$('checkBus').disabled=!(busState.stop&&$('busLine').value&&$('busDirection').value);if(!$('checkBus').disabled)$('busLoading').textContent='Gotowe. Sprawdź najbliższy autobus.'}
function busDistanceMetres(lat1,lon1,lat2,lon2){const rad=Math.PI/180,phi=(lat2-lat1)*rad,dl=(lon2-lon1)*rad,a=Math.sin(phi/2)**2+Math.cos(lat1*rad)*Math.cos(lat2*rad)*Math.sin(dl/2)**2;return Math.round(12742000*Math.asin(Math.min(1,Math.sqrt(a))))}
function busArrivalView(actual,plannedSeconds,checkedAt){
 const nowSec=Date.now()/1000,valid=Number(actual.eta)>nowSec-90&&Number(actual.eta)<nowSec+86400&&actual.live&&!actual.cancelled&&!actual.skipped;
 if(actual.cancelled)return '<div class="bus-eta unknown"><strong>Kurs odwołany</strong><small>Nie oczekuj tego autobusu.</small></div>';
 if(actual.skipped)return '<div class="bus-eta unknown"><strong>Przystanek pominięty</strong><small>Ten kurs nie zatrzyma się tutaj.</small></div>';
 if(valid){
  const left=Math.max(0,Math.ceil((actual.eta-nowSec)/60));
  const stamp=new Intl.DateTimeFormat('pl-PL',{timeZone:'Europe/Warsaw',hour:'2-digit',minute:'2-digit',hour12:false}).format(new Date(actual.eta*1000));
  const ago=Math.max(0,Math.round(nowSec-Number(checkedAt||nowSec)));
  return '<div class="bus-eta live"><strong>'+ (left===0?'Przyjazd teraz':'Przyjazd za '+left+' min')+' · '+esc(stamp)+'</strong><small>Prognoza LIVE przewoźnika · sprawdzona '+ago+' s temu. Nie jest to wyliczenie z GPS.</small></div>';
 }
 const today=wallNow(),seconds=Number(today.slice(11,13))*3600+Number(today.slice(14,16))*60;
 const left=Math.ceil((plannedSeconds-seconds)/60);
 return '<div class="bus-eta schedule"><strong>'+(left<0?'Planowy odjazd już minął':left===0?'Planowy odjazd: teraz':'Planowo za '+left+' min')+'</strong>'+
 '<small>Godzina według rozkładu: '+clock(plannedSeconds)+'. Brak potwierdzonej prognozy LIVE'+(actual.position?' mimo dostępnego GPS.':'.')+'</small></div>';
}
function busStopCoords(d){
 const group=busState.stop;const candidates=group.stops.map(id=>engine.idx.stops[id]);const exact=candidates.find(s=>String(s[0])===String(d.stopId)||String(s[0]).split(':').at(-1)===String(d.stopId).split(':').at(-1));
 return exact||candidates[0];
}
async function checkLiveBus(){
 if(!engine||!busState.stop||!$('busLine').value||!$('busDirection').value)return;
 const seq=++busState.seq,now=wallNow(),line=$('busLine').value,direction=$('busDirection').value;
 $('busResult').innerHTML='';$('checkBus').disabled=true;$('busRefresh').disabled=true;
 $('busLoading').textContent='Sprawdzam pozycję kursu w danych przewoźnika…';
 try{
  const all=await engine.departures(busState.stop,now,line,null,48);if(seq!==busState.seq)return;
  const nowMin=Number(now.slice(11,13))*60+Number(now.slice(14,16));
  const relevant=all.filter(d=>d.headsign===direction&&d.dep>=nowMin*60-240&&d.dep<=nowMin*60+120*60).slice(0,6);
  if(!relevant.length){$('busLoading').textContent='Brak pasującego kursu w pobliżu bieżącej godziny. Wybierz inny kierunek, przystanek lub spróbuj później.';return}
  let live;
  try{live=await native('live',{departures:relevant})}
  catch(e){if(seq!==busState.seq)return;$('busLoading').textContent='Nie udało się pobrać LIVE: '+(e.message||'brak połączenia')+'. Poniżej wyłącznie rozkład planowy.'}
  if(seq!==busState.seq)return;
  const checked=Number(live?.checkedAt||Date.now()/1000);
  const output=relevant.map((d,i)=>{
    const actual=live?.results?.find(v=>v.trip===d.trip&&v.serviceDay===d.serviceDay)||{...d,live:false};
    const cancelled=!!actual.cancelled,skipped=!!actual.skipped;
    const haveGps=!!(actual.position&&Number.isFinite(actual.position.latitude)&&Number.isFinite(actual.position.longitude)&&Number(actual.position.age)<=120&&Number(actual.position.age)>=0);
    const coords=busStopCoords(d);const location=haveGps&&coords&&Number.isFinite(coords[3])&&Number.isFinite(coords[4]);
    const dist=location?busDistanceMetres(actual.position.latitude,actual.position.longitude,coords[3],coords[4]):null;
    const status=cancelled?'Odwołany kurs':skipped?'Przystanek pominięty':haveGps?'Pozycja GPS potwierdzona':actual.eta?'Godzina LIVE potwierdzona':'Brak świeżej pozycji GPS · kurs planowy';
    const eta=actual.eta?new Intl.DateTimeFormat('pl-PL',{timeZone:'Europe/Warsaw',hour:'2-digit',minute:'2-digit',hour12:false}).format(new Date(actual.eta*1000)):null;
    const updateTime=haveGps?new Intl.DateTimeFormat('pl-PL',{timeZone:'Europe/Warsaw',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(new Date(actual.position.timestamp*1000)):'';
    return '<article class="card trip"><div class="bus-card-header"><span class="bus-vehicle-line">'+esc(d.line)+' → '+esc(d.headsign)+'</span><span class="pill">'+clock(d.dep)+'</span></div>'+
     '<div class="bus-status '+(haveGps||eta?'ok':'warn')+'">'+esc(status)+'</div>'+
     busArrivalView(actual,d.dep,checked)+
     '<p>Przystanek: <strong>'+esc(busState.stop.name)+'</strong><br>Odjazd planowy: <strong>'+clock(d.dep)+'</strong>'+
       (eta?'<br>Godzina prognozowana LIVE: <strong>'+esc(eta)+'</strong>':'')+'</p>'+
     (haveGps?'<p><strong>Gdzie jest autobus?</strong> GPS z '+esc(updateTime)+' (sprzed '+Math.round(actual.position.age)+' s).'+(dist===null?'':' Od przystanku dzieli go około <strong>'+ (dist>=1000?(dist/1000).toFixed(1)+' km':dist+' m')+'</strong> w linii prostej.')+'</p>':'<p class="small">Brak świeżej, potwierdzonej pozycji tego kursu. Nie tworzę fikcyjnego znacznika autobusu.</p>')+
     (i===0?'<div id="osmLiveMap" role="region" aria-label="Interaktywna mapa OpenStreetMap"></div>':'')+
     '<p class="bus-source">'+esc(actual.provenance||('Dane GTFS: '+d.source))+' · '+(actual.live?'Potwierdzono LIVE':'Bez potwierdzenia LIVE')+'</p></article>';
  }).join('');
  $('busResult').innerHTML=output;
  const shown=relevant[0],seen=live?.results?.find(v=>v.trip===shown.trip&&v.serviceDay===shown.serviceDay)||{},point=busStopCoords(shown);
  if(point&&Number.isFinite(point[3])&&Number.isFinite(point[4])&&window.PAMap){
   const position=seen.position&&Number(seen.position.age)>=0&&Number(seen.position.age)<=120?seen.position:null;
   PAMap.mount($('osmLiveMap'),{lat:point[3],lon:point[4]},position?{lat:position.latitude,lon:position.longitude}:null);
  }
  if(live?.results?.some(x=>x.live))$('busLoading').textContent='Sprawdzono aktualne dane LIVE. Wyświetlam tylko kursy pasujące do kierunku i przystanku.';
  else if(live)$('busLoading').textContent='Źródło nie podało świeżej pozycji wybranego kursu. Pokazuję wyłącznie rozkład planowy.';
  $('busProvider').textContent='Dane sprawdzone: '+new Intl.DateTimeFormat('pl-PL',{timeZone:'Europe/Warsaw',hour:'2-digit',minute:'2-digit',second:'2-digit'}).format(new Date(checked*1000));
 }catch(e){if(seq===busState.seq)$('busLoading').textContent=e.message||'Nie mogę teraz sprawdzić kursu.'}
 finally{if(seq===busState.seq){$('checkBus').disabled=false;$('busRefresh').disabled=false}}
}
$('busStop').oninput=busStopList;$('busStop').onfocus=()=>{if(!busState.stop)busStopList()};
$('busLine').onchange=busChangeLine;$('busDirection').onchange=busChangeDirection;
$('checkBus').onclick=checkLiveBus;$('busRefresh').onclick=checkLiveBus;$('busQuick').onclick=()=>tab('liveBus');

$('official').onclick=()=>native('official',{url:'https://portalpasazera.pl/'}).catch(e=>toast(e.message));document.querySelectorAll('.module').forEach(b=>b.onclick=()=>native('module',{name:b.dataset.module}).catch(e=>toast(e.message)));
$('chatInput').addEventListener('focus',()=>{document.body.classList.add('keyboard-open');setTimeout(scrollChat,100)});
$('chatInput').addEventListener('blur',()=>{document.body.classList.remove('keyboard-open');setTimeout(layoutChat,100)});
window.addEventListener('resize',layoutChat);if(window.visualViewport)window.visualViewport.addEventListener('resize',layoutChat);
window.addEventListener('offline',()=>toast('Brak internetu. Rozkłady zapisane w aplikacji działają offline.'));init();
})();
