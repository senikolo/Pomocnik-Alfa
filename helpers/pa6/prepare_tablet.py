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
html=html.replace('<script src="/transit-core.js"></script>',
                  '<script src="/tablet-compat.js"></script><script src="/transit-core.js"></script>')
(assets/'app.html').write_text(html,encoding='utf-8')
shutil.copyfile(repo/'helpers'/'pa6'/'tablet-legacy.css',assets/'tablet-legacy.css')
shutil.copyfile(repo/'helpers'/'pa6'/'tablet-compat.js',assets/'tablet-compat.js')
for p in data.glob('*.json'):
 shutil.copyfile(p,assets/p.name)
for required in ['index_all.json','manifest.json','summary.json']:
 if not (assets/required).is_file():raise SystemExit('Missing timetable '+required)
print('PASS Acer PA transport assets: real planner, Alfa, live status, favorites, OSM and GTFS index',len(list(assets.iterdir())))
