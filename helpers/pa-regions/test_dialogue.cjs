#!/usr/bin/env node
"use strict";
// Lightweight in-app dialogue regression suite, run with the real app.js in a fake DOM.
const assert = require("node:assert/strict");
const vm = require("node:vm");
const fs = require("node:fs");
const messages = [];
const nodes = new Map();
function element(id) {
  if (nodes.has(id)) return nodes.get(id);
  const e = {
    id, value: id === "network" ? "warsaw" : "", textContent: "", innerHTML: "",
    style: {}, dataset: {}, readOnly: false, scrollTop: 0, scrollHeight: 10,
    classList: { add(){}, remove(){}, toggle(){} },
    setAttribute(){}, focus(){}, querySelectorAll(){return []},
    append(n){ if(id === "messages") messages.push({who:n.className, text:n.textContent}); }
  };
  nodes.set(id,e); return e;
}
const d = new Date();
const day = (i) => {
  const dt = new Date(d.getFullYear(), d.getMonth(), d.getDate()+i);
  return dt.getFullYear()+"-"+String(dt.getMonth()+1).padStart(2,"0")+"-"+String(dt.getDate()).padStart(2,"0");
};
const days = Array.from({length:14},(_,i)=>day(i));
const common = {days, covered:days.map(()=>true)};
const mld = {
  ...common,source:"mld",
  stops:[
    ["A","Ispina","",50,20,[]],
    ["B","Ispina Most","",50,20,[0]],
    ["C","Bochnia Dworzec PKP","",50,20,[0]],
    ["D","Bochnia Szpital","",50,20,[0]],
    ["E","Ispina Leśniczówka","",50,20,[]]
  ], routes:[["mld-a4","A4",""]]
};
const warsaw = {...common,source:"warsaw",
  stops:[["W1","Metro Młociny","",52,21,[0]],["W2","Dw. Centralny","",52,21,[0]]],
  routes:[["w-102","102",""]]};
const routeMld = {trips:[[16383,"Bochnia",[[1,550],[2,610],[3,620]]]]};
const routeWarsaw = {trips:[[16383,"Dw. Centralny",[[0,550],[1,585]]]]};
const fakeFetch = async url => {
  const name = String(url).split("/").at(-1);
  const data = name === "index_mld.json" ? mld
    : name === "index_warsaw.json" ? warsaw
    : name === "route_mld_0.json" ? routeMld
    : name === "route_warsaw_0.json" ? routeWarsaw : null;
  return data ? {ok:true,json:async()=>JSON.parse(JSON.stringify(data))} : {ok:false};
};
const document = {
  getElementById: element,
  createElement: () => ({className:"",textContent:""}),
  querySelectorAll: () => []
};
vm.runInNewContext(fs.readFileSync("helpers/pa-regions/app.js","utf8"), {
  document, fetch:fakeFetch, navigator:{clipboard:{writeText:async()=>{}}},
  console, Date, Set, Map, Promise, Error
}, {timeout:5000});
const ask = async q => {
  element("chatInput").value=q;
  await element("ask").onclick();
  return messages.at(-1).text;
};
(async () => {
  let answer=await ask("Chcę jechać do Bochni z Ispiny jutro po 9");
  assert.match(answer,/Na którym dokładnie przystanku/);
  assert.doesNotMatch(answer,/Z którego dokładnie przystanku/);
  assert.match(answer,/09:00/);
  answer=await ask("Bochnia Dworzec PKP");
  assert.match(answer,/linia A4/);
  assert.match(answer,/Ispina Most/);
  assert.match(answer,/09:10/);
  answer=await ask("Z Ispiny Most do Bochni Dworzec PKP jutro po 9");
  assert.match(answer,/linia A4/);
  answer=await ask("Z Ispiny do Bochni jutro po 9");
  assert.match(answer,/Na którym dokładnie przystanku/);
  assert.doesNotMatch(answer,/linia A4/);
  answer=await ask("Dzięki");
  assert.match(answer,/Nie ma za co/);
  answer=await ask("od nowa");
  assert.match(answer,/nową trasę/);
  answer=await ask("Kiedy następna A4 jutro po 9?");
  assert.match(answer,/Z którego przystanku/);
  answer=await ask("Ispina Most");
  assert.match(answer,/Planowe odjazdy/);
  assert.match(answer,/09:10/); // If clock is > 09:10, the trip must be in tomorrow's date!
  answer=await ask("Z Ispiny Leśniczówka do Bochni Dworzec PKP jutro po 9");
  assert.match(answer,/Nie znalazłem potwierdzonego połączenia/);
  assert.match(answer,/Ispina Most/);
  answer=await ask("Z Metro Młociny do Dw. Centralny jutro po 9");
  assert.match(answer,/linia 102/);
  answer=await ask("Z Ispiny do Bochni Dworzec PKP jutro po 9");
  assert.match(answer,/linia A4/);
  answer=await ask("o 10");
  assert.doesNotMatch(answer,/linia A4 09:10/);
  assert.doesNotMatch(answer,/Podaj konkretny przystanek/);
  console.log("PASS: natural dialogue, pending departures, no invented routes, Warsaw switch, time change");
  console.log("PASS: reverse phrase, canonical Ispina Most, future time retention, ambiguous destination, no stale route, chat greeting");
})().catch(e=>{console.error(e);process.exitCode=1});
