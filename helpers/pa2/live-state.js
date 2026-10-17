(function(root){'use strict';
 const parts=new Intl.DateTimeFormat('sv-SE',{timeZone:'Europe/Warsaw',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'});
 function epoch(day,seconds){
  const [y,m,d]=day.split('-').map(Number),target=Date.UTC(y,m-1,d,12);let guess=target;
  for(let i=0;i<3;i++){const p={};parts.formatToParts(new Date(guess)).forEach(x=>p[x.type]=x.value);const wall=Date.UTC(+p.year,+p.month-1,+p.day,+p.hour,+p.minute,+p.second);const delta=target-wall;if(!delta)break;guess+=delta;}
  return guess/1000-43200+seconds;
 }
 function assess(d,actual,checked,now=Date.now()/1000){
  const stamp=Number(actual.position?.timestamp||0),positionAge=stamp?Math.max(0,Math.floor(now-stamp)):Infinity;
  const age=Math.max(0,Math.floor(now-Number(actual.liveTimestamp||checked||0)));
  const liveStamp=Number(actual.liveTimestamp||0),confirmed=liveStamp>0&&liveStamp<=now+20&&now-liveStamp<=120;
  const cancelled=confirmed&&!!actual.cancelled,skipped=confirmed&&!!actual.skipped,valid=!cancelled&&!skipped;
  const gpsFresh=valid&&stamp>0&&stamp<=now+20&&now-stamp<=120&&Number.isFinite(actual.position.latitude)&&Number.isFinite(actual.position.longitude);
  const etaFresh=valid&&confirmed&&actual.live&&age<=120&&Number(actual.eta)>=now-30&&Number(actual.eta)<now+86400;
  return{cancelled,skipped,gpsFresh,hadPosition:!!actual.position,positionAge,etaFresh,age,etaMinutes:Math.max(0,Math.ceil((Number(actual.eta)-now)/60)),plannedMinutes:Math.ceil((epoch(d.queryDay||d.serviceDay,d.dep)-now)/60)};
 }
 root.PALiveState=Object.freeze({epoch,assess});if(typeof module!=='undefined')module.exports=root.PALiveState;
})(typeof globalThis!=='undefined'?globalThis:this);
