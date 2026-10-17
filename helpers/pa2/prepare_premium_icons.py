#!/usr/bin/env python3
"""Compile Lucide ISC-licensed SVGs into Android VectorDrawables without extra dependencies."""
import json,sys
from pathlib import Path
from xml.etree import ElementTree as ET
from xml.sax.saxutils import escape
def number(node,k,default=0):return float(node.get(k,default))
def path(tag,n):
 v=lambda k,d=0:number(n,k,d)
 if tag=='path':return n.get('d','')
 if tag=='line':return f"M{v('x1')} {v('y1')} L{v('x2')} {v('y2')}"
 if tag in ('circle','ellipse'):
  cx,cy=v('cx'),v('cy');rx=v('r') if tag=='circle' else v('rx');ry=v('r') if tag=='circle' else v('ry')
  return f'M{cx-rx} {cy} a{rx} {ry} 0 1 0 {2*rx} 0 a{rx} {ry} 0 1 0 {-2*rx} 0'
 if tag=='rect':
  x,y,w,h=v('x'),v('y'),v('width'),v('height');r=min(v('rx',v('ry')),w/2,h/2)
  if not r:return f'M{x} {y} h{w} v{h} h{-w} Z'
  return (f'M{x+r} {y} H{x+w-r} A{r} {r} 0 0 1 {x+w} {y+r} '
   f'V{y+h-r} A{r} {r} 0 0 1 {x+w-r} {y+h} '
   f'H{x+r} A{r} {r} 0 0 1 {x} {y+h-r} V{y+r} A{r} {r} 0 0 1 {x+r} {y} Z')
 if tag in ('polyline','polygon'):
  pts=n.get('points','').replace(',',' ').split()
  if len(pts)<4 or len(pts)%2:return ''
  return 'M'+pts[0]+' '+pts[1]+' '+' '.join('L'+pts[i]+' '+pts[i+1] for i in range(2,len(pts),2))+(' Z' if tag=='polygon' else '')
 return ''
def compile_icons(root,manifest):
 icons=json.loads(manifest.read_text(encoding='utf-8'))['icons'];drawable=root/'res'/'drawable';drawable.mkdir(parents=True,exist_ok=True)
 for name,svg in icons.items():
  paths=[]
  for elem in ET.fromstring(svg).iter():
   tag=elem.tag.split('}')[-1];d=path(tag,elem)
   if d:paths.append(d)
   elif tag not in ('svg','g','title','desc'):raise ValueError('Unsupported SVG '+tag+' in '+name)
  if not paths:raise ValueError('Empty SVG '+name)
  parts=['<?xml version="1.0" encoding="utf-8"?>','<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">']
  for d in paths:
   parts.append(' <path android:pathData="'+escape(d,{'"':'&quot;'})+'" android:fillColor="#00000000" android:strokeColor="#FF000000" android:strokeWidth="2" android:strokeLineCap="round" android:strokeLineJoin="round"/>')
  parts.append('</vector>')
  (drawable/('pa_premium_'+name.replace('-','_')+'.xml')).write_text('\n'.join(parts)+'\n',encoding='utf-8')
 print('PASS: '+str(len(icons))+' Lucide Android vector icons compiled')
if __name__=='__main__':compile_icons(Path(sys.argv[1]),Path(sys.argv[2]))
