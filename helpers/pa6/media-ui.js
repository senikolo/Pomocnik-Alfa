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
 }
 if(document.readyState==="loading")document.addEventListener("DOMContentLoaded",init,false);else init();
}());