const fs=require('fs');
// Output ES2017 syntax; .at is polyfilled for older Android WebViews.
const cp=require('child_process');const dir=process.argv[2];
for(const name of ['transit-core.js','conversation.js','osm-map.js','live-state.js','app.js']){
 const p=dir+'/'+name;cp.execFileSync('npx',['--yes','esbuild@0.25.10',p,'--target=es2017','--outfile='+p+'.tmp'],{stdio:'inherit'});let text=fs.readFileSync(p+'.tmp','utf8');
 if(name==='transit-core.js')text="if(!Array.prototype.at){Object.defineProperty(Array.prototype,'at',{value:function(i){i=Math.trunc(i)||0;if(i<0)i+=this.length;return this[i]},configurable:true,writable:true})}\n"+text;
 fs.writeFileSync(p,text);fs.unlinkSync(p+'.tmp');
}
