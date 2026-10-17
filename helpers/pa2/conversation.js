/* Conversation state, independently tested. Rules are explicitly an offline fallback. */
(function(root){'use strict';const {norm,canonical}=root.PATransit;
const pad=n=>String(n).padStart(2,'0');
function wallNow(){const p=new Intl.DateTimeFormat('sv-SE',{timeZone:'Europe/Warsaw',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(new Date()),v={};p.forEach(x=>v[x.type]=x.value);return v.year+'-'+v.month+'-'+v.day+'T'+v.hour+':'+v.minute}
function localDate(s){const [d,t]=s.split('T'),[y,m,day]=d.split('-').map(Number),[h,min]=t.split(':').map(Number);return new Date(y,m-1,day,h,min)}
const format=d=>d.getFullYear()+'-'+pad(d.getMonth()+1)+'-'+pad(d.getDate())+'T'+pad(d.getHours())+':'+pad(d.getMinutes());
function parseTime(q,previous,now=wallNow()){
 const n=norm(q);let d=localDate(previous||now),explicit=false,relative=false;
 if(/\bteraz\b/.test(n)){d=localDate(now);explicit=true}
 const offset=/\bpojutrze\b/.test(n)?2:/\bjutro\b/.test(n)?1:/\bdzis(?:iaj)?\b/.test(n)?0:null;
 if(offset!==null){const keep=[d.getHours(),d.getMinutes()];d=localDate(now);d.setDate(d.getDate()+offset);if(previous)d.setHours(...keep);explicit=true}
 const timeFree=q.replace(/\b(?:po|o|od|na|około|okolo|godzinie|godzina|godz\.?)\s+\d{1,2}[.:]\d{2}\b/gi,'');
 const iso=q.match(/\b(20\d{2})-(\d{2})-(\d{2})\b/),pol=timeFree.match(/\b(\d{1,2})[./](\d{1,2})(?:[./](20\d{2}))?\b/);
 if(iso||pol){const y=Number(iso?iso[1]:pol[3]||d.getFullYear()),m=Number(iso?iso[2]:pol[2]),day=Number(iso?iso[3]:pol[1]);const nd=new Date(y,m-1,day,d.getHours(),d.getMinutes());if(nd.getMonth()!==m-1||nd.getDate()!==day)return{error:'Ta data nie istnieje. Podaj dzień, miesiąc i godzinę.'};d=nd;explicit=true}
 const weekdays={poniedzialek:1,wtorek:2,srode:3,sroda:3,czwartek:4,piatek:5,sobote:6,sobota:6,niedziele:0,niedziela:0};const w=n.match(/\b(?:w|we)\s+(poniedzialek|wtorek|srode|sroda|czwartek|piatek|sobote|sobota|niedziele|niedziela)\b/);if(w){d=localDate(now);let shift=(weekdays[w[1]]-d.getDay()+7)%7;if(/przyszl/.test(n)&&shift===0)shift=7;d.setDate(d.getDate()+shift);explicit=true}
 const hour=q.match(/\b(?:po|o|od|na|około|okolo|godzinie|godzina|godz\.?)\s+(\d{1,2})(?:[:.](\d{2}))?(?=\s|[!?.,]|$)/i)||q.match(/\b(\d{1,2}):(\d{2})\b/);
 if(hour){const h=Number(hour[1]),m=Number(hour[2]||0);if(h>23||m>59)return{error:'Podaj godzinę od 00:00 do 23:59.'};d.setHours(h,m,0,0);explicit=true}
 if(!hour&&/\bwieczorem\b/.test(n)){d.setHours(18,0,0,0);explicit=true}if(!hour&&/\brano\b/.test(n)){d.setHours(7,0,0,0);explicit=true}
 const shift=n.match(/\b(?:(\d+)\s*(godzin\w*|minut\w*|min)|(?:godzine|godzina))\s+(pozniej|wczesniej)\b/);
 if(shift){const amount=(shift[1]?Number(shift[1]):1)*(shift[2]&&shift[2].startsWith('min')?1:60);d.setMinutes(d.getMinutes()+(shift[3]==='wczesniej'?-amount:amount));explicit=relative=true}
 const half=n.match(/\b(poltorej|pol)\s+godziny\s+(pozniej|wczesniej)\b/);if(half){d.setMinutes(d.getMinutes()+(half[1]==='poltorej'?90:30)*(half[2]==='wczesniej'?-1:1));explicit=relative=true}
 const za=n.match(/\bza\s+(\d+)\s*(minut\w*|min|godzin\w*)\b/);if(za){d=localDate(now);d.setMinutes(d.getMinutes()+Number(za[1])*(za[2].startsWith('godzin')?60:1));explicit=relative=true}
 return{when:format(d),explicit,relative}
}
function locations(q){
 const clean=q.replace(/\b(?:w kierunku|kierunek|w stronę|w strone)\s+.+?(?=\s+(?:jutro|pojutrze|dzisiaj|dziś|po\s+\d|o\s+\d)|[?!]|$)/gi,' § ')
  .replace(/\b20\d{2}-\d{2}-\d{2}\b|\b\d{1,2}[./]\d{1,2}(?:[./]20\d{2})?\b/g,' § ')
  .replace(/\b(?:po|o|od|około|okolo|godz\.?)\s+\d{1,2}(?:[.:]\d{2})?\b/gi,' § ');
 const end='(?=\\s+(?:do|na|z|ze|§|jutro|pojutrze|dziś|dzis|dzisiaj|wieczorem|rano|w\\s+(?:poniedziałek|wtorek|środę|czwartek|piątek|sobotę|niedzielę))\\b|\\s+§|[!?]|$)';
 const f=clean.match(new RegExp('(?:^|\\s)(?:z|ze)\\s+(.+?)'+end,'i'));
 const t=clean.match(new RegExp('(?:^|\\s)(?:do|na)\\s+(.+?)'+end,'i'));
 return{from:f?f[1].trim().replace(/[.,]$/,''):null,to:t?t[1].trim().replace(/[.,]$/,''):null};
}
class Assistant{
 constructor(engine){this.engine=engine;this.reset();this.history=[]}
 reset(){this.state={from:null,to:null,fromHint:'',toHint:'',when:null,asked:'',line:null,direction:null,intent:'route',candidates:[]}}
 choose(name){const r=this.engine.resolve(name);if(!r.group)return null;const which=this.state.asked;if(which==='from'||which==='to'){this.state[which]=r.group;this.state[which+'Hint']='';this.state.asked=''}else if(which==='direction'){this.state.direction=name;this.state.asked=''}return r.group}
 sync(from,to,when){Object.assign(this.state,{from,to,when,intent:'route',asked:'',line:null,direction:null})}
 pending(which,options=[]){this.state.asked=which;this.state.candidates=options;const s=this.state;
  const message=which==='from'?'Z którego przystanku chcesz wyruszyć?':which==='to'?'Na którym przystanku chcesz zakończyć podróż?':which==='line'?'O którą linię chodzi?':'W którym kierunku jedziesz?';
  return{type:'clarify',message:message+(s.when&&['from','to'].includes(which)?' Datę i godzinę mam: '+s.when.replace('T',' ')+'.':''),options:options.map(x=>typeof x==='string'?x:x.name)}
 }
 parse(q,model=null){
  q=String(q||'').trim().slice(0,2000);const n=norm(q),s=this.state;
  if(!q)return{type:'answer',message:'Napisz, w czym mogę pomóc.'};
  if(/\b(wyczysc rozmowe|od nowa|nowa trasa)\b/.test(n)){this.reset();return{type:'answer',message:'Zaczynamy nową trasę. Skąd i dokąd chcesz jechać?'}}
  if(/^(hej|czesc|dzien dobry|dobry wieczor|witaj)$/.test(n))return{type:'answer',message:'Cześć! Dokąd się wybierasz? Mogę też sprawdzić pogodę, wyszukać informacje albo zapisać notatkę.'};
  if(/^(dzieki|dziekuje|super|ok|okej)$/.test(n))return{type:'answer',message:'Nie ma za co. Możemy też sprawdzić późniejszy kurs.'};
  const note=q.match(/(?:zapisz|dodaj|zanotuj)\s+(?:notatkę|notatke|notatka)?\s*:?\s*(.+)$/i);if(note)return{type:'note',text:note[1].trim()};
  if(/\bprzypomnij\b/.test(n)){
   const time=parseTime(q,null),hasDate=/\b(jutro|pojutrze|dzis|dzisiaj|za \d+|20\d{2}|w (poniedzialek|wtorek|srode|czwartek|piatek|sobote|niedziele))\b/.test(n),hasTime=/\b(?:o|po|za)\s+\d+|\d+:\d+/.test(n);
   if(time.error)return{type:'answer',message:time.error};if(!hasDate||!hasTime)return{type:'answer',message:'Podaj dzień, godzinę i treść, np. „Przypomnij mi jutro o 15 o lekarzu”.'};
   const text=q.replace(/^.*?przypomnij\s*(?:mi)?/i,'').replace(/(?:jutro|pojutrze|dzisiaj|dziś)/gi,'').replace(/\bo\s+\d{1,2}(?:[:.]\d{2})?/,'').replace(/^\s*o\s+/,'').trim();return{type:'reminder',when:time.when,text:text||'Przypomnienie'};
  }
  if(/pogod|temperatur|deszcz|padac|padalo|padac|paras(o|ó)l|sloneczn/.test(n)){
   const place=q.match(/\b(?:w|we|dla)\s+(.+?)(?=\s+(?:jutro|pojutrze|dziś|dzisiaj|będzie|bedzie)|[?!]|$)/i);let city=place?canonical(place[1]):null;
   if(!city&&s.to)city=s.to.name;return city?{type:'weather',city,offset:/pojutrze/.test(n)?2:/jutro/.test(n)?1:0}:{type:'answer',message:'Dla jakiej miejscowości sprawdzić pogodę? Napisz np. „Pogoda jutro w Warszawie”.'}
  }
  const loc=locations(q);
  // Stop-name phrases carry the departure point even without the word 'z'.
  // Never assume the user's actual GPS position from a conversational phrase.
  const atStop=q.match(/\b(?:na|przy|z|ze)\s+przystanku\s+(.+?)(?=\s+(?:w\s+kierunku|w\s+stron[ęe]|jutro|pojutrze|dzisiaj|dziś|teraz|o\s+\d|po\s+\d)|[?!]|$)/i);
  if(atStop&&!loc.from)loc.from=atStop[1].trim();
  const travel=!!(loc.from||loc.to)||/\b(dojade|dojechac|jechac|jade|podroz|trasa|polaczeni|nastepn\w*|odjazd\w*|kiedy.*a\d|gdzie.*autobus|autobus|tramwaj|metro|pociag)\b/.test(n),follow=!!s.from&&/\b(?:jutro|pojutrze|dzis|dzisiaj|teraz|w poniedzialek|we wtorek|w srode|w czwartek|w piatek|w sobote|w niedziele)\b/.test(n)||/pozniej|wczesniej|^(?:a )?(?:o|po|od) \d|w druga strone|powrot|zamien/.test(n),isAnswer=!!s.asked;
  if(model&&['route','departure','vehicle'].includes(model.intent)){
   if(model.from&&!loc.from)loc.from=model.from;if(model.to&&!loc.to)loc.to=model.to;
  }
  if(!travel&&!follow&&!isAnswer&&!(model&&['route','departure','vehicle'].includes(model.intent)))return{type:'general',query:q};
  const t=parseTime(q,s.when);if(t.error)return{type:'answer',message:t.error};
  const newTrip=!!(loc.from&&loc.to);if(newTrip){s.line=null;s.direction=null;s.intent='route';s.asked='';if(!t.explicit)s.when=wallNow()}
  if(t.explicit||!s.when)s.when=t.when;
  if(model&&/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(model.when)&&!t.explicit)s.when=model.when;
  const oldFrom=s.from;
  const lm=n.match(/\b(?:linia|linie|linii|autobus|autobusu|tramwaj|tramwaju|numer|nr)\s*(a\d{1,3}|n\d{1,3}|m[12]|s\d{1,2}|\d{1,3})\b/)||n.match(/\b(a\d{1,3}|n\d{1,3}|m[12]|s\d{1,2})\b/);
  if(lm){const line=lm[1].toUpperCase();if(s.line&&s.line!==line)s.direction=null;s.line=line}
  else if(model&&model.line){const line=String(model.line).slice(0,16);if(s.line&&s.line!==line)s.direction=null;s.line=line}
  const op=/\bkm[lł]\b|kolej\w* malopolsk/.test(n)?'KMŁ':/\bkm\b|kolej\w* mazowieck/.test(n)?'KM':/\bskm\b/.test(n)?'SKM Warszawa':/\bmld\b/.test(n)?'MLD':null;if(op)s.operator=op;
  const vehicle=/\bgdzie\b.*\b(autobus|bus|tramwaj|pociag)\b|\bgps\b/.test(n)||model?.intent==='vehicle';
  const departure=/\b(kiedy|nastepn\w*|odjazd\w*|najblizsz\w*)\b/.test(n)&&!loc.to||model?.intent==='departure';
  if(vehicle||departure){const previous=s.to;s.intent=vehicle?'vehicle':'departure';s.to=null;if(!s.direction&&previous)s.direction=previous.name.split(' ')[0];}
  const direction=q.match(/(?:w kierunku|kierunek|w stronę|w strone)\s+(.+?)(?=\s+(?:jutro|dzisiaj|po \d|o \d)|[?!]|$)/i);if(direction)s.direction=direction[1].trim();
  // 'A do Nowodworów?' after checking a vehicle is a new route, not another LIVE lookup.
  // A route's old line and direction are not constraints on a newly selected destination.
  if(loc.to&&!vehicle&&!departure){if(s.intent!=='route'||(s.to&&canonical(s.to.name)!==canonical(loc.to))){if(!lm&&!model?.line)s.line=null;s.direction=null}s.intent='route'}
  for(const which of ['from','to'])if(loc[which]){const r=this.engine.resolve(loc[which]);if(['bochnia','warszawa','krakow'].includes(canonical(loc[which]))&&this.engine.match(loc[which],20).length>1){r.group=null;r.candidates=this.engine.match(loc[which],8)}s[which]=r.group;s[which+'Hint']=loc[which];s[which+'Candidates']=r.candidates;if(r.correction)s.correction='Sprawdź pisownię nazwy. Wybierz właściwy przystanek z podpowiedzi.'}
  if(s.from&&oldFrom&&s.from.key!==oldFrom.key){if(!direction)s.direction=null;if(!lm&&s.line&&![...s.from.routes].some(id=>norm(this.engine.idx.routes[id][1])===norm(s.line)))s.line=null;}
  if(!loc.from&&!loc.to&&isAnswer){
   if(s.asked==='line'){const line=n.match(/\b(a\d{1,3}|n\d{1,3}|m[12]|s\d{1,2}|\d{1,3})\b/);if(line)s.line=line[1].toUpperCase()}
   else if(s.asked==='direction')s.direction=q;
   else{const r=this.engine.resolve(q);if(r.group){s[s.asked]=r.group;s.asked=''}else{s[s.asked+'Candidates']=r.candidates;return this.pending(s.asked,r.candidates)}}
  }
  if(/w druga strone|powrot|zamien/.test(n)&&s.from&&s.to){[s.from,s.to]=[s.to,s.from];s.line=null}
  if(!s.from)return this.pending('from',s.fromCandidates||[]);
  if(s.intent==='vehicle'&&!s.line)return this.pending('line');
  if(s.intent==='route'&&!s.to)return this.pending('to',s.toCandidates||[]);
  s.asked='';return{type:s.intent,from:s.from,to:s.to,when:s.when,line:s.line,direction:s.direction,operator:s.operator,correction:/\bz ispin(i)\b/.test(n)?'Drobna poprawka: „z Ispiny”.':s.correction||''}
 }
}
root.PAConversation={Assistant,parseTime,locations,wallNow,format,localDate};if(typeof module!=='undefined')module.exports=root.PAConversation;
})(typeof globalThis!=='undefined'?globalThis:this);
