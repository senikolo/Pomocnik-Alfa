#!/usr/bin/env python3
"""Actual user input + durable persistence on the isolated API23 test installation."""
import subprocess,sys,time,re,xml.etree.ElementTree as ET
from pathlib import Path
pkg=sys.argv[1]
def adb(*args):return subprocess.check_output(['adb',*args],text=True)
def start(name):
 adb('shell','am','force-stop',pkg);adb('shell','am','start','-n',pkg+'/com.ispina.lokalnie.'+name);time.sleep(2)
def nodes():
 adb('shell','uiautomator','dump','/sdcard/interaction.xml')
 return list(ET.fromstring(adb('shell','cat','/sdcard/interaction.xml')).iter('node'))
def tap(n):
 x1,y1,x2,y2=map(int,re.findall(r'\d+',n.attrib['bounds']));adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
def field(value):
 for n in nodes():
  if n.get('class')=='android.widget.EditText':tap(n);adb('shell','input','text',value);adb('shell','input','keyevent','4');return
 raise AssertionError('Field missing')
def click(value):
 for attempt in range(4):
  for n in nodes():
   if value in n.get('text','') and n.get('clickable')=='true':tap(n);time.sleep(1);return
  adb('shell','input','swipe','500','650','500','250','350')
 raise AssertionError(value+' missing')
start('PA6NotesActivity');field('PA6_Premium_persistence');click('Zapisz notatkę')
start('PA6NotesActivity');assert 'PA6_Premium_persistence' in adb('shell','cat','/data/data/'+pkg+'/shared_prefs/native_notes.xml')
start('PA6RemindersActivity');field('PA6_Premium_reminder');click('Zapisz przypomnienie')
xml=adb('shell','cat','/data/data/'+pkg+'/shared_prefs/pa6_reminders.xml')
assert 'PA6_Premium_reminder' in xml
assert pkg in adb('shell','dumpsys','alarm')
start('PA6SettingsActivity')
assert 'vibration' in adb('shell','cat','/data/data/'+pkg+'/shared_prefs/pa6_comfort.xml')
# Network-dependent weather is reported honestly; local persistence remains a required check.
start('PA6WeatherActivity');field('Warszawa');click('Pokaż prognozę')
weather=False
for attempt in range(6):
 time.sleep(4)
 weather=any('Teraz:' in n.get('text','') for n in nodes())
 if weather:break
Path('results/premium-weather-status.txt').write_text('CONFIRMED actual Warsaw forecast in app\n' if weather else 'UNCONFIRMED live forecast on CI network\n')
# The actual PDF renderer and TXT reader, not just their launcher screens.
Path('results/pa6-reader.txt').write_text('Alfa Premium offline reader')
objects=[b'<< /Type /Catalog /Pages 2 0 R >>',b'<< /Type /Pages /Kids [3 0 R] /Count 1 >>',b'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',b'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>']
stream=b'BT /F1 18 Tf 20 100 Td (Alfa Premium PDF) Tj ET'
objects.append(b'<< /Length '+str(len(stream)).encode()+b' >>\nstream\n'+stream+b'\nendstream')
data=b'%PDF-1.4\n';offsets=[0]
for i,obj in enumerate(objects,1):offsets.append(len(data));data+=str(i).encode()+b' 0 obj\n'+obj+b'\nendobj\n'
xref=len(data);data+=b'xref\n0 6\n0000000000 65535 f \n'
for offset in offsets[1:]:data+=('%010d 00000 n \n'%offset).encode()
data+=b'trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n'+str(xref).encode()+b'\n%%EOF\n'
Path('results/pa6-reader.pdf').write_bytes(data)
for ext,expected in [('txt','Alfa Premium offline reader'),('pdf','strona 1 z 1')]:
 subprocess.run(['adb','push','results/pa6-reader.'+ext,'/sdcard/Download/pa6-reader.'+ext],check=True)
 adb('shell','am','force-stop',pkg);adb('shell','am','start','-n',pkg+'/com.ispina.lokalnie.PA6ReaderActivity','--es','verify_file','/sdcard/Download/pa6-reader.'+ext);time.sleep(2)
 assert any(expected in n.get('text','') for n in nodes()),'Reader '+ext+' failed'
Path('results/premium-reader-status.txt').write_text('PASS actual offline TXT and PDF render\n')
Path('results/premium-interactions.txt').write_text('PASS actual text input / note save and restart / reminder save and AlarmManager schedule / persistent comfort settings\n')
print('PASS premium interaction tests')
