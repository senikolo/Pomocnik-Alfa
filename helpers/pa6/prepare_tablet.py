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
# Chromium 44 supports SVG <use> via xlink:href, not the SVG2 href spelling.
html=html.replace('<svg ', '<svg xmlns:xlink="http://www.w3.org/1999/xlink" ')
html=html.replace('<use href=', '<use xlink:href=')
html=html.replace('vertical-align:middle">2.0</span>', 'vertical-align:middle">Premium</span>')
assert '</head>' in html and '<script src="/app.js"></script>' in html
html=html.replace('</head>','<link rel="stylesheet" href="/tablet-legacy.css"></head>',1)
html=html.replace('<body>','<body><div id="pa6-indicator" role="status">ALFA PREMIUM · Twoja podróż zaczyna się tutaj</div>',1)
html=html.replace('<header>','<div class="pa6-media" aria-label="Multimedia Pomocnika Alfa"><button id="pa6radio" type="button" class="pa6-tile">📻 Radio</button><button id="pa6tv" type="button" class="pa6-tile">📺 Telewizja</button></div><header>',1)
assert 'id="pa6radio"' in html and 'id="pa6tv"' in html
html=html.replace('Radio, telewizja, pogoda, notatnik i przypomnienia są już na głównym pulpicie. Tutaj zostają tylko zapisane trasy i przystanki.',
                  'Planer, pojazdy i zapisane przystanki masz pod ręką. Radio, telewizję i pozostałe funkcje znajdziesz także na ekranie głównym.')
html=html.replace('Wróć do pulpitu PA','Wróć do ekranu głównego')
html=html.replace('</body>','<script src="/media-ui.js"></script></body>',1)
html=html.replace('<script src="/transit-core.js"></script>',
                  '<script src="/tablet-compat.js"></script><script src="/transit-core.js"></script>')
(assets/'app.html').write_text(html,encoding='utf-8')
shutil.copyfile(repo/'helpers'/'pa6'/'tablet-legacy.css',assets/'tablet-legacy.css')
shutil.copyfile(repo/'helpers'/'pa6'/'tablet-compat.js',assets/'tablet-compat.js')
shutil.copyfile(repo/'helpers'/'pa6'/'media-ui.js',assets/'media-ui.js')
for p in data.glob('*.json'):
 shutil.copyfile(p,assets/p.name)
for required in ['index_all.json','manifest.json','summary.json']:
 if not (assets/required).is_file():raise SystemExit('Missing timetable '+required)
print('PASS Acer PA transport assets: real planner, Alfa, live status, favorites, OSM and GTFS index',len(list(assets.iterdir())))
