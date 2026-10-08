#!/usr/bin/env python3
"""Remote stream availability smoke test; not proof of on-device playback."""
import json, re, ssl, time, urllib.request, urllib.error, concurrent.futures
from pathlib import Path
base=Path("project/app/src/main/java/com/ispina/lokalnie")
radio=(base/"RadioActivity.java").read_text(encoding="utf-8")
television=(base/"TvActivity.java").read_text(encoding="utf-8")
pairs=[]
# Radio STATIONS contains name + url tuples.
area=radio.split("private static final String[][] STATIONS={",1)[1].split("};",1)[0]
for name,url in re.findall(r'\{"([^"]+)","(https?://[^"]+)"\}',area):
    pairs.append(("radio",name,url))
for name,url in re.findall(r'addStation\([^;]+\bstartDirectFallback\("([^"]+)",\s*new String\[\]\{"(https?://[^"]+)"',television):
    pairs.append(("tv",name,url))
# Dedicated fallback URL is added separately so its liveness is visible.
for m in re.findall(r'MOSCOW_SECONDARY="(https?://[^"]+)"',radio):
    pairs.append(("radio_fallback","Москва FM zapasowy",m))
def probe(item):
    kind,name,url=item
    started=time.time()
    result=dict(kind=kind,station=name,url=url)
    try:
        req=urllib.request.Request(url,headers={
            "User-Agent":"Mozilla/5.0 (compatible; PomocnikAlfaStreamAudit/1.7.47)",
            "Range":"bytes=0-4095",
            "Accept":"*/*"
        })
        with urllib.request.urlopen(req,timeout=9,context=ssl.create_default_context()) as r:
            raw=r.read(1536)
            ct=r.headers.get("Content-Type","")
            # HTML 200 is generally not a playable audio/video stream.
            mime_ok=any(x in ct.lower() for x in ("audio","video","mpegurl","octet-stream","application/vnd.apple"))
            magic=raw[:8].hex()
            is_playlist=(b'#EXTM3U' in raw[:300] or b'<MPD' in raw[:300])
            is_html=b'<html' in raw[:300].lower() or b'<!doctype html' in raw[:300].lower()
            result.update(status=r.status,content_type=ct,bytes_read=len(raw),playlist=is_playlist,html=is_html,likely_media=bool((mime_ok or is_playlist) and not is_html),final_url=r.geturl(),duration=round(time.time()-started,2))
    except urllib.error.HTTPError as e:
        result.update(status=e.code,error=f"HTTP {e.code}",duration=round(time.time()-started,2))
    except Exception as e:
        result.update(error=type(e).__name__+": "+str(e)[:120],duration=round(time.time()-started,2))
    return result
with concurrent.futures.ThreadPoolExecutor(max_workers=7) as pool:
    results=list(pool.map(probe,pairs))
out={"timestamp_utc":time.strftime("%Y-%m-%dT%H:%M:%SZ",time.gmtime()),
     "note":"Remote GitHub runner probe only. HTTP success is not a device playback guarantee. Geo-blocking can cause 403 or timeout.",
     "tested":len(results),
     "potential_media":sum(bool(r.get("likely_media")) for r in results),
     "results":results}
Path("pa1747-stream-report.json").write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding="utf-8")
print("TOTAL",out["tested"],"POTENTIAL_MEDIA",out["potential_media"])
for x in results:
    print(x["kind"],x["station"],str(x.get("status","ERR")),x.get("content_type",""),"PLAYABLE_CANDIDATE" if x.get("likely_media") else "UNCERTAIN",x.get("error",""))
