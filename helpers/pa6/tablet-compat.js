/* PA Tablet Edition Android 6: ES5-only DOM fallbacks.
 * Separate file is required by the original PA Content Security Policy.
 */
(function(){
 "use strict";
 if(!window.globalThis)window.globalThis=window;
 if(!Element.prototype.replaceChildren)Element.prototype.replaceChildren=function(){
  while(this.firstChild)this.removeChild(this.firstChild);
  for(var i=0;i<arguments.length;i++)
   this.appendChild(typeof arguments[i]==="string"?document.createTextNode(arguments[i]):arguments[i]);
 };
 if(!Element.prototype.scrollTo)Element.prototype.scrollTo=function(x,y){
  this.scrollLeft=Number(x)||0;this.scrollTop=Number(y)||0;
 };
 window.addEventListener("load",function(){
  var b=document.getElementById("goDashboard");
  if(b)b.addEventListener("click",function(e){
   e.preventDefault();e.stopImmediatePropagation();
   document.getElementById("tabPlanner").click();
  },true);
 });
}());
