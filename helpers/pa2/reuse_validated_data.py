"""Reuse the unchanged, hash-checked shared timetable for a UI-only release.
Expired/missing coverage exits unsuccessfully so CI fetches a fresh generation.
"""
import datetime, hashlib, json, re, sys, zipfile
from pathlib import Path
from zoneinfo import ZoneInfo
apk, out = Path(sys.argv[1]), Path(sys.argv[2])
assert hashlib.sha256(apk.read_bytes()).hexdigest() == 'adc5cfe9015c5180edf35de4f2570ead3a9b98b17a8a3310b84c301d18ca8452'
with zipfile.ZipFile(apk) as z:
    prefix = 'assets/pa_regions/'
    manifest = json.loads(z.read(prefix + 'manifest.json'))
    idx = json.loads(z.read(prefix + 'index_all.json'))
    today = datetime.datetime.now(ZoneInfo('Europe/Warsaw')).date().isoformat()
    assert idx['schema'] == manifest['schema'] == 2
    day = idx['days'].index(today)
    assert idx['covered'][day] and all(s['covered'][day] for s in idx['sources']), 'Refresh uncovered sources'
    for item in manifest['files']:
        assert re.fullmatch(r'[A-Za-z0-9_.-]+\.json', item['name'])
        data = z.read(prefix + item['name'])
        assert len(data) == item['size'] and hashlib.sha256(data).hexdigest() == item['sha256']
    out.mkdir(parents=True, exist_ok=True)
    for item in manifest['files']:
        (out / item['name']).write_bytes(z.read(prefix + item['name']))
    (out / 'manifest.json').write_bytes(z.read(prefix + 'manifest.json'))
    print(f"PASS shared generation {idx['generated']}; valid {idx['days'][0]}..{idx['days'][-1]}; {len(manifest['files'])} hashes verified; all sources covered {today}")
