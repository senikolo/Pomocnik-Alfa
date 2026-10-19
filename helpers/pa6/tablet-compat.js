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
  if(x&&typeof x==="object"){y=x.top;x=x.left;}
  this.scrollLeft=Number(x)||0;this.scrollTop=Number(y)||0;
 };
 // Android 6 Chromium implements the numeric signature, but throws
 // TypeError on modern window.scrollTo({top:0, behavior:"smooth"}).
 var oldWindowScroll=window.scrollTo;
 window.scrollTo=function(left,top){
  if(left&&typeof left==="object")return oldWindowScroll.call(window,Number(left.left)||0,Number(left.top)||0);
  return oldWindowScroll.call(window,Number(left)||0,Number(top)||0);
 };
 window.addEventListener("load",function(){
  try{document.body.setAttribute("data-pa-mode",window.PADevice.premiumMode());}catch(e){}
 });
 document.addEventListener("click",function(e){
  var node=e.target;
  while(node&&node!==document.body){
   if(node.tagName==="BUTTON"||node.tagName==="A"||node.getAttribute("role")==="button"){
    try{window.PADevice.tap();}catch(ignore){}return;
   }
   node=node.parentElement;
  }
 },true);
}());
