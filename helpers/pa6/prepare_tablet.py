#!/usr/bin/env python3
"""Assemble PA's original WebView client for Acer B3-A32 (API23)."""
from pathlib import Path
import shutil,sys
repo=Path(__file__).resolve().parents[2]
src=repo/'helpers'/'pa2'
data=Path(sys.argv[1])
assets=Path(sys.argv[2])
assets.mkdir(parents=True,exist_ok=True)
html=(src/'app.html').read_text(encoding='utf-8')
assert '</head>' in html and '<script src="/app.js"></script>' in html
html=html.replace('</head>','<link rel="stylesheet" href="/tablet-legacy.css"></head>',1)
html=html.replace('<body>','<body><div id="pa6-indicator" role="status">PA UltraSmart Tablet · Android 6 · edycja transportowa · dane LIVE tylko gdy potwierdzone</div>',1)
html=html.replace('Radio, telewizja, pogoda, notatnik i przypomnienia są już na głównym pulpicie. Tutaj zostają tylko zapisane trasy i przystanki.',
                  'W wersji tabletowej dostępne są Planer, Pojazdy, Alfa i Ulubione. Pozostałe moduły PA wymagają oddzielnego dostosowania do Androida 6.')
html=html.replace('Wróć do pulpitu PA','Wróć do Planera')
polyfill="""<script>
(function(){'use strict';
if(!window.globalThis)window.globalThis=window;
if(!Element.prototype.replaceChildren)Element.prototype.replaceChildren=function(){
 while(this.firstChild)this.removeChild(this.firstChild);
 for(var i=0;i<arguments.length;i++)this.appendChild(typeof arguments[i]==='string'?document.createTextNode(arguments[i]):arguments[i]);
};
if(!Element.prototype.scrollTo)Element.prototype.scrollTo=function(x,y){this.scrollLeft=Number(x)||0;this.scrollTop=Number(y)||0;};
window.addEventListener('load',function(){var b=document.getElementById('goDashboard');if(b)b.addEventListener('click',function(e){e.preventDefault();e.stopImmediatePropagation();document.getElementById('tabPlanner').click();},true);});
})();
</script>
"""
html=html.replace('<script src="/transit-core.js"></script>',polyfill+'<script src="/transit-core.js"></script>')
(assets/'app.html').write_text(html,encoding='utf-8')
shutil.copyfile(repo/'helpers'/'pa6'/'tablet-legacy.css',assets/'tablet-legacy.css')
for p in data.glob('*.json'):
 shutil.copyfile(p,assets/p.name)
for required in ['index_all.json','manifest.json','summary.json']:
 if not (assets/required).is_file():raise SystemExit('Missing timetable '+required)
print('PASS Acer PA transport assets: real planner, Alfa, live status, favorites, OSM and GTFS index',len(list(assets.iterdir())))
