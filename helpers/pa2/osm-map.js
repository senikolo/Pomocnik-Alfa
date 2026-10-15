(function(root){'use strict';
/* Small offline-code / online-tile map. Only visible OSM tiles are requested.
   Native Android bridge identifies the app and caches each tile for seven days. */
const clamp=(n,a,b)=>Math.max(a,Math.min(b,n));
const TILE=256,MINZOOM=8,MAXZOOM=18;
const merc=(lat,lon,z)=>{const n=Math.pow(2,z)*TILE,r=clamp(lat,-85.0511,85.0511)*Math.PI/180;
 return{x:(lon+180)/360*n,y:(1-Math.log(Math.tan(r)+1/Math.cos(r))/Math.PI)/2*n}};
const fromMerc=(x,y,z)=>{const n=TILE*Math.pow(2,z);return{lon:x/n*360-180,lat:Math.atan(Math.sinh(Math.PI*(1-2*y/n)))*180/Math.PI}};
const deg=(a,b,c,d)=>{const rad=Math.PI/180,dy=(c-a)*rad,dx=(d-b)*rad;
 return Math.round(12742000*Math.asin(Math.min(1,Math.sqrt(Math.sin(dy/2)**2+Math.cos(a*rad)*Math.cos(c*rad)*Math.sin(dx/2)**2))))};
function initialZoom(dist){return dist<350?16:dist<1000?15:dist<2500?14:dist<7000?13:dist<15000?12:11}
function mount(node,stop,vehicle,options={}){
 if(!node||!stop||!Number.isFinite(stop.lat)||!Number.isFinite(stop.lon))return;
 if(node.__mapCleanup)node.__mapCleanup();
 const tram=Number(options.mode)===0||(Number(options.mode)>=900&&Number(options.mode)<=906),kind=tram?'Tramwaj':'Autobus',kindLower=tram?'tramwaju':'autobusu';
 const dist=vehicle?deg(stop.lat,stop.lon,vehicle.lat,vehicle.lon):0;
 let z=initialZoom(dist),center={lat:vehicle?(stop.lat+vehicle.lat)/2:stop.lat,lon:vehicle?(stop.lon+vehicle.lon)/2:stop.lon};
 const panel=document.createElement('div');panel.className='osm-map';
 const header=document.createElement('div');header.className='osm-toolbar';
 const title=document.createElement('strong');title.textContent=vehicle?kind+(options.line?' '+options.line:'')+' i przystanek':'Twój przystanek · brak świeżego GPS';
 const buttons=document.createElement('div');buttons.className='osm-buttons';
 const plus=document.createElement('button'),minus=document.createElement('button'),reset=document.createElement('button');
 for(const [b,label,desc] of [[plus,'+','Powiększ mapę'],[minus,'−','Pomniejsz mapę'],[reset,'⌖','Wyśrodkuj mapę']]){
  b.type='button';b.textContent=label;b.setAttribute('aria-label',desc);buttons.appendChild(b)}
 header.appendChild(title);header.appendChild(buttons);
 const view=document.createElement('div');view.className='osm-viewport';view.setAttribute('role','img');
 view.setAttribute('aria-label',vehicle?'Mapa OpenStreetMap: '+kindLower+' i przystanku':'Mapa OpenStreetMap: lokalizacja przystanku');
 const layer=document.createElement('div');layer.className='osm-layer';view.appendChild(layer);
 const markStop=document.createElement('div');markStop.className='osm-marker osm-stop';markStop.innerHTML='<svg class="vicon" viewBox="0 0 24 24" aria-hidden="true"><use href="#pa-pin"></use></svg>';markStop.title='Twój przystanek';view.appendChild(markStop);
 let markVehicle=null;
 if(vehicle){markVehicle=document.createElement('div');markVehicle.className='osm-marker osm-bus';markVehicle.innerHTML='<svg class="vicon" viewBox="0 0 24 24" aria-hidden="true"><use href="#pa-'+(tram?'tram':'bus')+'"></use></svg>';markVehicle.title='Potwierdzony GPS '+kindLower;view.appendChild(markVehicle)}
 const attribution=document.createElement('div');attribution.className='osm-attribution';attribution.textContent='© OpenStreetMap contributors';view.appendChild(attribution);
 const hint=document.createElement('p');hint.className='osm-hint';hint.textContent='Przesuwaj mapę palcem, rozsuń dwa palce albo użyj + i −. Znaczniki pokazują położenie, nie trasę przejazdu.';
 panel.appendChild(header);panel.appendChild(view);panel.appendChild(hint);node.replaceChildren(panel);
 const tiles=new Map();let loaded=0;const unavailable=document.createElement('div');unavailable.className='osm-unavailable hidden';unavailable.textContent='Mapa chwilowo niedostępna. Rozkład i opis kursu są nadal widoczne.';unavailable.setAttribute('role','status');view.appendChild(unavailable);
 function update(){
  const w=Math.max(260,view.clientWidth||320),h=Math.max(200,view.clientHeight||260),p=merc(center.lat,center.lon,z),n=1<<z;
  const x0=Math.floor((p.x-w/2)/TILE),x1=Math.floor((p.x+w/2)/TILE);
  const y0=Math.max(0,Math.floor((p.y-h/2)/TILE)),y1=Math.min(n-1,Math.floor((p.y+h/2)/TILE));
  const wanted=new Set();let count=0;
  for(let iy=y0;iy<=y1;iy++)for(let ix=x0;ix<=x1;ix++){
   if(++count>20)break;
   const rx=(ix%n+n)%n,key=z+'/'+rx+'/'+iy;wanted.add(key);
   let tile=tiles.get(key);if(!tile){tile=document.createElement('img');tile.alt='';tile.decoding='async';tile.draggable=false;
     tile.onload=()=>{loaded++;unavailable.classList.add('hidden')};tile.onerror=()=>{if(!loaded)unavailable.classList.remove('hidden')};tile.width=TILE;tile.height=TILE;tile.style.position='absolute';tile.src='/osm/'+key+'.png';layer.appendChild(tile);tiles.set(key,tile)}
   tile.style.left=Math.round(ix*TILE-p.x+w/2)+'px';tile.style.top=Math.round(iy*TILE-p.y+h/2)+'px';
  }
  for(const [key,img] of tiles)if(!wanted.has(key)){img.remove();tiles.delete(key)}
  function moveMarker(el,item){const v=merc(item.lat,item.lon,z);el.style.left=(w/2+v.x-p.x)+'px';el.style.top=(h/2+v.y-p.y)+'px'}
  moveMarker(markStop,stop);if(markVehicle)moveMarker(markVehicle,vehicle);
  plus.disabled=z>=MAXZOOM;minus.disabled=z<=MINZOOM;
 }
 const changeZoom=diff=>{if((diff>0&&z>=MAXZOOM)||(diff<0&&z<=MINZOOM))return;z=clamp(z+diff,MINZOOM,MAXZOOM);update()};
 plus.onclick=()=>changeZoom(1);minus.onclick=()=>changeZoom(-1);
 reset.onclick=()=>{z=initialZoom(dist);center={lat:vehicle?(stop.lat+vehicle.lat)/2:stop.lat,lon:vehicle?(stop.lon+vehicle.lon)/2:stop.lon};update()};
 let gesture=null;
 const pt=e=>({x:e.clientX,y:e.clientY});
 function pan(dx,dy){const p=merc(center.lat,center.lon,z),n=TILE*(1<<z);center=fromMerc(p.x-dx,clamp(p.y-dy,0,n),z);update()}
 view.addEventListener('pointerdown',e=>{if(e.target.closest('.osm-attribution'))return;if(!gesture){gesture={id:e.pointerId,p:pt(e)};view.setPointerCapture?.(e.pointerId)}});
 view.addEventListener('pointermove',e=>{if(gesture&&gesture.id===e.pointerId){const p=pt(e);pan(p.x-gesture.p.x,p.y-gesture.p.y);gesture.p=p}});
 const end=e=>{if(gesture?.id===e.pointerId)gesture=null};
 view.addEventListener('pointerup',end);view.addEventListener('pointercancel',end);
 let pinch=null;view.addEventListener('touchstart',e=>{if(e.touches.length===2){const [a,b]=e.touches;pinch=Math.hypot(a.clientX-b.clientX,a.clientY-b.clientY)}},{passive:true});
 view.addEventListener('touchmove',e=>{if(e.touches.length===2&&pinch){e.preventDefault();const [a,b]=e.touches;const next=Math.hypot(a.clientX-b.clientX,a.clientY-b.clientY);if(next>pinch*1.55){changeZoom(1);pinch=next}else if(next<pinch/1.55){changeZoom(-1);pinch=next}}},{passive:false});
 view.addEventListener('touchend',e=>{if(e.touches.length<2)pinch=null});
 view.addEventListener('wheel',e=>{e.preventDefault();changeZoom(e.deltaY<0?1:-1)},{passive:false});
 const onSize=()=>{if(node.isConnected)update()};
 root.addEventListener('resize',onSize);
 node.__mapCleanup=()=>{root.removeEventListener('resize',onSize);tiles.clear();gesture=null};
 update();
}
root.PAMap=Object.freeze({mount,merc,deg});
})(typeof window==='undefined'?globalThis:window);
