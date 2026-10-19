#!/usr/bin/env python3
"""Two bounded source probes; playlist AND a media fragment, no permanent-failure inference."""
import concurrent.futures,datetime,json,re,ssl,time,urllib.request,urllib.error
from pathlib import Path
from urllib.parse import urljoin
UA='Mozilla/5.0 (Pomocnik Alfa source verification)'
def get(url,limit=160000):
 req=urllib.request.Request(url,headers={'User-Agent':UA,'Referer':'https://vod.tvp.pl/','Range':'bytes=0-159999'})
 with urllib.request.urlopen(req,timeout=9) as res:return res.status,res.read(limit)
def check(source):
 try:
  if source.startswith('tvp:'):
   code,body=get('https://vod.tvp.pl/api/products/'+source[4:]+'/videos/playlist?platform=BROWSER&videoType=LIVE')
   data=json.loads(body)
   if data.get('drm'):return {'result':'protected','media_accessible':False}
   source=data['sources']['HLS'][0]['src']
  for depth in range(4):
   code,body=get(source)
   txt=body.decode('utf8','replace')
   if not txt.lstrip().startswith('#EXTM3U'):return {'result':'not_hls','media_accessible':False}
   # A protected playlist is not evidence that this app can decode the channel.
   if '#EXT-X-KEY:' in txt and 'METHOD=NONE' not in txt:return {'result':'encrypted_playlist','media_accessible':False}
   entries=[l.strip() for l in txt.splitlines() if l.strip() and not l.startswith('#')]
   if not entries:return {'result':'empty_playlist','media_accessible':False}
   child=urljoin(source,entries[0])
   if '#EXT-X-STREAM-INF' in txt:source=child;continue
   status,fragment=get(child)
   return {'result':'media_fragment' if len(fragment)>1000 else 'short_fragment','media_accessible':len(fragment)>1000,'http':status,'bytes_sampled':len(fragment)}
  return {'result':'playlist_depth','media_accessible':False}
 except urllib.error.HTTPError as e:return {'result':'http_'+str(e.code),'media_accessible':False}
 except Exception as e:return {'result':type(e).__name__,'media_accessible':False}
def main():
 code=Path('helpers/pa6/native/PA6TvActivity.java').read_text()
 channels=re.findall(r'new Channel\("([^"\n]+)","[^"\n]*","[^"\n]*","([^"\n]+)"',code)
 assert len(channels)==36,len(channels)
 results={}
 def probe(entry):
  name,url=entry;attempts=[check(url),check(url)]
  return name,{'attempts':attempts,'media_accessible':any(r['media_accessible'] for r in attempts)}
 with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
  for name,result in pool.map(probe,channels):results[name]=result
 report={'date':datetime.datetime.now(datetime.timezone.utc).strftime('%d.%m.%Y'),'checked_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'method':'2x HLS playlist and bounded media fragment GET; CI IP, not physical Acer or Poland; not a playback test','removed':[],'channels':results}
 out=Path('output');out.mkdir(exist_ok=True)
 (out/'tv-source-audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
 assets=Path('build/pa6-assets');assets.mkdir(parents=True,exist_ok=True);(assets/'pa6-tv-audit.json').write_text(json.dumps(report,ensure_ascii=False))
 lines=['# Weryfikacja źródeł TV — '+report['date'],'','Dwie próby: lista HLS i fragment medium. Sprawdzono z adresu CI. To nie test pełnego odtwarzania na Acerze ani dostępu z Polski.','', '| Kanał | Próba 1 | Próba 2 |', '|---|---|---|']
 for n,r in results.items():lines.append('| '+n+' | '+r['attempts'][0]['result']+' | '+r['attempts'][1]['result']+' |')
 lines+=['','Usunięte kanały: brak. Nie potwierdzono trwałego wyłączenia źródeł. Niedostępne i niezweryfikowane zachowano z odpowiednim oznaczeniem.']
 (out/'tv-source-audit.md').write_text('\n'.join(lines)+'\n')
 print(json.dumps({n:r['attempts'] for n,r in results.items()},ensure_ascii=False))
if __name__=='__main__':main()
