from pathlib import Path
import re, zipfile, sys

src = Path(sys.argv[1] if len(sys.argv) > 1 else "base.apk")
out = Path(sys.argv[2] if len(sys.argv) > 2 else "Ispina-Lokalnie-Live-1.82.95-A7-WEATHER-PERFORMANCE-FIX.apk")
sig = re.compile(r"^META-INF/(?:MANIFEST\\.MF|[^/]+\\.(?:SF|RSA|DSA|EC))$", re.I)

repls = {
"setInterval(function(){if(!d0.hidden)ilSoonRefresh(d0)},1000)":"setInterval(function(){if(!d0.hidden)ilSoonRefresh(d0)},3000)",
"setInterval(function(){if(!document.hidden){loadNativeSchedules();ilPoll()}},10000)":"setInterval(function(){if(!document.hidden){loadNativeSchedules();ilPoll()}},30000)",
"setInterval(ilPoll,2000)":"setInterval(ilPoll,5000)",
"setInterval(function(){if(!d0.hidden)latestCard()},10000)":"setInterval(function(){if(!d0.hidden)latestCard()},30000)",
"setInterval(function(){if(!document.hidden)refresh()},5000)":"setInterval(function(){if(!document.hidden)refresh()},12000)",
"setInterval(updateAll,6000)":"setInterval(updateAll,15000)",
"setInterval(updateAll,7000)":"setInterval(updateAll,15000)",
"setInterval(function(){if(!d.hidden){updateBack();updateBusy();updateDiagError();installOfflineQueue();improveNearEmpty()}},6000)":"setInterval(function(){if(!d.hidden){updateBack();updateBusy();updateDiagError();installOfflineQueue();improveNearEmpty()}},15000)",
"setInterval(function(){if(!d.hidden)syncNow()},12000)":"setInterval(function(){if(!d.hidden)syncNow()},30000)",
"setInterval(function(){if(!d.hidden)friendly()},12000)":"setInterval(function(){if(!d.hidden)friendly()},30000)",
"setInterval(function(){if(!d.hidden)all()},10000)":"setInterval(function(){if(!d.hidden)all()},20000)",
"setInterval(function(){if(!d.hidden)all()},12000)":"setInterval(function(){if(!d.hidden)all()},30000)",
"setInterval(function(){if(!d.hidden){updateHero();tidyNews();updateMigration();version()}},12000)":"setInterval(function(){if(!d.hidden){updateHero();tidyNews();updateMigration();version()}},30000)",
"setInterval(function(){if(!d.hidden){updateFresh();robustDashRefresh();robustNewsRefresh();improveNearEmpty();updateDiag()}},12000)":"setInterval(function(){if(!d.hidden){updateFresh();robustDashRefresh();robustNewsRefresh();improveNearEmpty();updateDiag()}},30000)",
"setInterval(function(){updateCard();updateDiag()},12000)":"setInterval(function(){updateCard();updateDiag()},30000)",
}

shim = r""";/* Android 7 performance guard; weather/network code untouched. */
(function(){try{
 var ua=String((navigator&&navigator.userAgent)||'');
 if(!/Android\\s+7(?:[._;\\s]|$)/i.test(ua))return;
 window.__IL_ANDROID7_PERFORMANCE_FIX=true;
 function lite(){try{
  var d=document;if(!d||!d.head||d.getElementById('ilA7PerfFix'))return;
  var st=d.createElement('style');st.id='ilA7PerfFix';
  st.textContent='html{scroll-behavior:auto!important}.ken1863,.ken1864,.ken1865,.il-soon-dot{animation:none!important}';
  d.head.appendChild(st);
 }catch(_){}}
 if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',lite,{once:true});else lite();
}catch(_){}})();
"""

with zipfile.ZipFile(src, "r") as zin:
    js = zin.read("assets/main_ui.js").decode("utf-8")
    if "api.open-meteo.com/v1/forecast" not in js:
        raise SystemExit("weather endpoint missing")
    changed = 0
    for a,b in repls.items():
        n = js.count(a)
        if n:
            js = js.replace(a,b)
            changed += n
    if changed < 12:
        raise SystemExit(f"expected >=12 changes, got {changed}")
    js = (shim + js).encode("utf-8")
    with zipfile.ZipFile(out, "w", allowZip64=True) as zout:
        for info in zin.infolist():
            if sig.match(info.filename):
                continue
            data = js if info.filename == "assets/main_ui.js" else zin.read(info.filename)
            zi = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            zi.compress_type = info.compress_type
            zi.comment = info.comment
            zi.extra = info.extra
            zi.create_system = info.create_system
            zi.create_version = info.create_version
            zi.extract_version = info.extract_version
            zi.flag_bits = info.flag_bits & ~1
            zi.volume = info.volume
            zi.internal_attr = info.internal_attr
            zi.external_attr = info.external_attr
            zout.writestr(zi, data, compress_type=info.compress_type, compresslevel=9)
print(f"patched_intervals={changed}")
