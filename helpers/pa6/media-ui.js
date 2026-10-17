/* External same-origin script: Android 6 WebView CSP compliant. */
(function(){
 "use strict";
 function launch(kind){
  try{
   if(!window.PADevice){window.alert("Otwórz tę funkcję w zainstalowanej aplikacji.");return;}
   window.PADevice.request(JSON.stringify({id:"890"+(kind==="radio"?"123":"124"),type:"module",payload:{name:kind}}));
  }catch(e){window.alert("Nie udało się otworzyć modułu. Spróbuj ponownie.");}
 }
 function init(){
  var r=document.getElementById("pa6radio"),t=document.getElementById("pa6tv");
  if(r)r.addEventListener("click",function(){launch("radio");},false);
  if(t)t.addEventListener("click",function(){launch("tv");},false);
  var match=/(?:^|[?&])tab=(planner|bus|assistant|favorites)(?:&|$)/.exec(window.location.search);
  if(match){
   var tabId=match[1]==="bus"?"tabBus":match[1]==="assistant"?"tabAssistant":match[1]==="favorites"?"tabFavorites":"tabPlanner";
   var selected=document.getElementById(tabId);
   if(selected)selected.click();
  }
 }
 if(document.readyState==="loading")document.addEventListener("DOMContentLoaded",init,false);else init();
}());