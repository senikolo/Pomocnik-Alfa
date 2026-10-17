/* Downlevel the EXISTING PA planner and Alfa scripts for Android 6 System WebView.
   Babel handles async/await, optional chaining and core-js usage. Esbuild bundles
   polyfills into ES5 browser IIFEs with no runtime require/import on the device.\n   ES5 is deliberate: Android 6 bundled WebView rejects shorthand object keys\n   even when older Chrome compatibility targets accept them. */
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process');
const babel=require('@babel/core');
const src=path.resolve(process.argv[2]),dest=path.resolve(process.argv[3]);
if(!src||!dest)throw Error('Usage: transpile.cjs <pa2-sources> <assets-dir>');
fs.mkdirSync(dest,{recursive:true});
const names=['transit-core.js','conversation.js','osm-map.js','live-state.js','app.js'];
for(const name of names){
 let source=fs.readFileSync(path.join(src,name),'utf8');
 if(name==='conversation.js'){
  // Chromium 44 lacks Intl.DateTimeFormat.formatToParts (added much later).
  // sv-SE format returns yyyy-MM-dd HH:mm for Europe/Warsaw, without
  // throwing, and uses local-clock fallback if this locale is unavailable.
  const from="function wallNow(){const p=new Intl.DateTimeFormat('sv-SE',{timeZone:'Europe/Warsaw',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(new Date()),v={};p.forEach(x=>v[x.type]=x.value);return v.year+'-'+v.month+'-'+v.day+'T'+v.hour+':'+v.minute}";
  const to="function wallNow(){try{var v=new Intl.DateTimeFormat('sv-SE',{timeZone:'Europe/Warsaw',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}).format(new Date());var x=v.match(/(\\d{4})-(\\d{2})-(\\d{2})\\s+(\\d{2}):(\\d{2})/);if(x)return x[1]+'-'+x[2]+'-'+x[3]+'T'+x[4]+':'+x[5]}catch(e){}return format(new Date())}";
  if(!source.includes(from))throw Error('Original PA Warsaw clock changed; inspect legacy fallback');
  source=source.replace(from,to);
 }
 const compiled=babel.transformSync(source,{
  filename:name,sourceType:'script',comments:false,compact:false,
  presets:[['@babel/preset-env',{targets:{chrome:'44'},useBuiltIns:'usage',corejs:'3.47',modules:'commonjs'}]]
 });
 if(!compiled?.code)throw Error('Babel did not transpile '+name);
 const entry=path.join(dest,name+'.legacy-entry.cjs');
 fs.writeFileSync(entry,compiled.code);
 cp.execFileSync(path.join(__dirname,'../../build/pa6-node/node_modules/.bin/esbuild'),
  [entry,'--bundle','--platform=browser','--format=iife','--target=es5','--outfile='+path.join(dest,name)],
  {stdio:'inherit'});
 fs.unlinkSync(entry);
 const built=fs.readFileSync(path.join(dest,name),'utf8');
 if(!built.includes('function'))throw Error('Empty browser bundle '+name);
 if(/(^|\n)\s*(?:import\s|export\s)/.test(built))throw Error('Unbundled import '+name);
 console.log('PASS Android 6 compatibility script',name,built.length);
}
