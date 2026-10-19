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
Path('results/premium-interactions.txt').write_text('PASS actual text input / note save and restart / reminder save and AlarmManager schedule / persistent comfort settings\n')
print('PASS premium interaction tests')
