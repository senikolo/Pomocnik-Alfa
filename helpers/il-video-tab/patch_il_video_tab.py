#!/usr/bin/env python3
"""Patch the stable Ispina Lokalnie APK with an in-app Wideo menu and player."""

from __future__ import annotations

import json
import re
import struct
import sys
import zipfile
from pathlib import Path


OLD_NAME = "1.83.20"
NEW_NAME = "1.83.21"
OLD_CODE = 18320
NEW_CODE = 18321
MARKER = "__IL_WIDEO_TAB_18321__"

JS_PATCH = r"""
;(function(){
 'use strict';
 try{
  var d=document,nav=d.getElementById('nav'),panel=d.getElementById('iaP');
  if(!nav||!panel||d.getElementById('ilWideoView18321'))return;
  var style=d.createElement('style');style.id='ilWideoStyle18321';style.textContent='\
#ilWideoView18321{box-sizing:border-box;padding:14px 14px 30px;color:#173b32}\
#ilWideoView18321 .ilv-head{margin:2px 0 15px;padding:16px;background:#edf6f2;border:1px solid #d7e9e1;border-radius:16px}\
#ilWideoView18321 .ilv-head h2{margin:2px 0 7px;color:#174875;font-size:25px;line-height:1.25}\
#ilWideoView18321 .ilv-head p{margin:0;color:#526477;font-size:14px;line-height:1.55}\
#ilWideoView18321 .ilv-card{box-sizing:border-box;margin:12px 0;padding:14px;background:#fff;border:1px solid #dbe5ef;border-radius:16px;box-shadow:0 3px 12px rgba(23,72,117,.06)}\
#ilWideoView18321 .ilv-card h3{margin:0 0 11px;color:#174875;font-size:17px;line-height:1.38}\
#ilWideoView18321 .ilv-frame{position:relative;width:100%;padding-top:56.25%;overflow:hidden;border-radius:12px;background:#102c3c}\
#ilWideoView18321 .ilv-frame iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0}\
#ilWideoView18321 .ilv-link{display:inline-block;margin-top:9px;color:#176f77;font-size:14px;font-weight:750;text-decoration:none}\
@media(min-width:760px){#ilWideoView18321{max-width:900px;margin:0 auto}#ilWideoView18321 .ilv-card{padding:18px}}';
  d.head.appendChild(style);
  var view=d.createElement('div');view.className='view';view.id='ilWideoView18321';
  view.setAttribute('role','main');view.setAttribute('aria-label','Wideo z Ispiny');
  view.innerHTML='<div class="ilv-head"><h2>Wideo</h2><p>Trzy nagrania związane z Ispiną. Odtwarzaj je bezpośrednio w aplikacji.</p></div>'+
   '<article class="ilv-card"><h3>Archiwalny materiał TVP Kraków o pacyfikacji Ispiny</h3><div class="ilv-frame"><iframe title="Archiwalny materiał TVP Kraków o pacyfikacji Ispiny" src="about:blank" data-src="https://www.youtube-nocookie.com/embed/ZG7I26z3nZs?playsinline=1&amp;rel=0" loading="lazy" allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe></div><a class="ilv-link" href="https://www.youtube.com/watch?v=ZG7I26z3nZs">Otwórz na YouTube</a></article>'+
   '<article class="ilv-card"><h3>Ispina w programie „Jedź Bezpiecznie”</h3><div class="ilv-frame"><iframe title="Ispina w programie Jedź Bezpiecznie" src="about:blank" data-src="https://www.youtube-nocookie.com/embed/uH6bZTGhFdE?playsinline=1&amp;rel=0" loading="lazy" allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe></div><a class="ilv-link" href="https://www.youtube.com/watch?v=uH6bZTGhFdE">Otwórz na YouTube</a></article>'+
   '<article class="ilv-card"><h3>Ispina w TVN Turbo. Stare nagranie, ale problem aktualny</h3><div class="ilv-frame"><iframe title="Ispina w TVN Turbo" src="about:blank" data-src="https://www.youtube-nocookie.com/embed/uY9qCLdLzuY?playsinline=1&amp;rel=0" loading="lazy" allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe></div><a class="ilv-link" href="https://www.youtube.com/watch?v=uY9qCLdLzuY">Otwórz na YouTube</a></article>';
  panel.appendChild(view);
  var button=d.createElement('button');button.type='button';button.className='nb place';
  button.setAttribute('data-u',':video');button.id='ilWideoTab18321';
  button.innerHTML='<span class="ni">▶</span>Wideo';
  var camera=nav.querySelector('.nb[data-u=":camera"]');
  if(camera&&camera.parentNode)camera.parentNode.insertBefore(button,camera.nextSibling);
  else{
   var group=Array.prototype.find.call(nav.querySelectorAll('.ng'),function(n){return n.textContent.trim()==='O ISPINIE'});
   var next=group&&group.nextElementSibling;
   while(next&&!next.classList.contains('ng'))next=next.nextElementSibling;
   if(group)nav.insertBefore(button,next);else nav.appendChild(button);
  }
  function visible(){return view.classList.contains('on')&&panel.classList.contains('on')&&!d.hidden}
  function startPlayers(){if(!visible())return;view.querySelectorAll('iframe[data-src]').forEach(function(f){var u=f.getAttribute('data-src');if(f.getAttribute('src')!==u)f.setAttribute('src',u)})}
  function stopPlayers(){view.querySelectorAll('iframe[data-src]').forEach(function(f){if(f.getAttribute('src')!=='about:blank')f.setAttribute('src','about:blank')})}
  function open(){try{history.pushState({ia:1,video:1},'',location.href)}catch(e){}var drawer=d.getElementById('iaD'),shade=d.getElementById('sh');if(drawer)drawer.classList.remove('on');if(shade)shade.classList.remove('on');panel.querySelectorAll('.view').forEach(function(x){x.classList.remove('on')});view.classList.add('on');panel.classList.add('on');var sec=d.getElementById('sec');if(sec)sec.textContent='Wideo';panel.scrollTop=0;startPlayers()}
  button.addEventListener('click',open);
  var observer=new MutationObserver(function(){if(visible())startPlayers();else stopPlayers()});
  observer.observe(view,{attributes:true,attributeFilter:['class']});observer.observe(panel,{attributes:true,attributeFilter:['class']});
  d.addEventListener('visibilitychange',function(){if(visible())startPlayers();else stopPlayers()});
 }catch(e){try{console.error('IL Wideo tab 1.83.21:',e)}catch(_){}}
})();
"""


def uleb(data: bytes, pos: int) -> tuple[int, int]:
    value = 0
    shift = 0
    while True:
        byte = data[pos]
        pos += 1
        value |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return value, pos
        shift += 7


def utf16_len(data: bytes, pos: int) -> tuple[int, int]:
    value = struct.unpack_from("<H", data, pos)[0]
    pos += 2
    if value & 0x8000:
        value = ((value & 0x7FFF) << 16) | struct.unpack_from("<H", data, pos)[0]
        pos += 2
    return value, pos


def string_pool(data: bytes, off: int, size: int) -> tuple[list[str], list[tuple[int, int, str]]]:
    count, = struct.unpack_from("<I", data, off + 8)
    flags, = struct.unpack_from("<I", data, off + 16)
    strings_start, = struct.unpack_from("<I", data, off + 20)
    utf8 = bool(flags & 0x100)
    values: list[str] = []
    payloads: list[tuple[int, int, str]] = []
    for i in range(count):
        rel, = struct.unpack_from("<I", data, off + 28 + i * 4)
        pos = off + strings_start + rel
        if utf8:
            _, pos = uleb(data, pos)
            byte_len, pos = uleb(data, pos)
            raw = data[pos : pos + byte_len]
            value = raw.decode("utf-8")
            payloads.append((pos, byte_len, value))
        else:
            char_len, pos = utf16_len(data, pos)
            byte_len = char_len * 2
            raw = data[pos : pos + byte_len]
            value = raw.decode("utf-16le")
            payloads.append((pos, byte_len, value))
        values.append(value)
    return values, payloads


def patch_manifest(data: bytes) -> bytes:
    buf = bytearray(data)
    pos = 8
    strings: list[str] | None = None
    payloads: list[tuple[int, int, str]] = []
    while pos + 8 <= len(buf):
        chunk_type, header_size, chunk_size = struct.unpack_from("<HHI", buf, pos)
        if chunk_size < header_size or pos + chunk_size > len(buf):
            raise ValueError("Nieprawidłowy rozmiar fragmentu AndroidManifest.xml")
        if chunk_type == 0x0001:
            strings, payloads = string_pool(buf, pos, chunk_size)
            break
        pos += chunk_size
    if strings is None:
        raise ValueError("Nie znaleziono puli tekstów manifestu")

    code_data_offset = None
    name_idx = None
    pos = 8
    while pos + 8 <= len(buf):
        chunk_type, header_size, chunk_size = struct.unpack_from("<HHI", buf, pos)
        if chunk_size < header_size or pos + chunk_size > len(buf):
            raise ValueError("Nieprawidłowy rozmiar fragmentu AndroidManifest.xml")
        if chunk_type == 0x0102:
            ext = pos + header_size
            tag_name_idx, = struct.unpack_from("<I", buf, ext + 4)
            if tag_name_idx < len(strings) and strings[tag_name_idx] == "manifest":
                attr_start, attr_size, attr_count = struct.unpack_from("<HHH", buf, ext + 8)
                attrs = ext + attr_start
                for i in range(attr_count):
                    at = attrs + i * attr_size
                    ns_idx, attr_name_idx = struct.unpack_from("<II", buf, at)
                    if attr_name_idx >= len(strings):
                        continue
                    attr_name = strings[attr_name_idx]
                    ns = strings[ns_idx] if ns_idx < len(strings) else ""
                    if ns != "http://schemas.android.com/apk/res/android":
                        continue
                    value_type = buf[at + 15]
                    value_data, = struct.unpack_from("<I", buf, at + 16)
                    if attr_name == "versionCode":
                        if value_type not in (0x10, 0x11):
                            raise ValueError("Nieznany typ android:versionCode")
                        code_data_offset = at + 16
                        if value_data != OLD_CODE:
                            raise ValueError(f"Oczekiwano versionCode {OLD_CODE}, jest {value_data}")
                    elif attr_name == "versionName":
                        if value_type != 0x03 or value_data >= len(strings):
                            raise ValueError("Nieznany typ android:versionName")
                        name_idx = value_data
        pos += chunk_size
    if code_data_offset is None or name_idx is None:
        raise ValueError("Brakuje android:versionCode lub android:versionName")
    if strings[name_idx] != OLD_NAME:
        raise ValueError(f"Oczekiwano versionName {OLD_NAME}, jest {strings[name_idx]!r}")
    start, length, value = payloads[name_idx]
    if value != OLD_NAME:
        raise ValueError("Nie udało się wskazać tekstu android:versionName")
    if len(NEW_NAME.encode("utf-8")) != length and len(NEW_NAME.encode("utf-16le")) != length:
        raise ValueError("Nowa nazwa wersji ma inną długość niż dotychczasowa")
    if length == len(OLD_NAME.encode("utf-8")):
        encoded = NEW_NAME.encode("utf-8")
    else:
        encoded = NEW_NAME.encode("utf-16le")
    buf[start : start + length] = encoded
    struct.pack_into("<I", buf, code_data_offset, NEW_CODE)
    return bytes(buf)


def signature_entry(name: str) -> bool:
    upper = name.upper()
    if upper == "META-INF/MANIFEST.MF":
        return True
    if not upper.startswith("META-INF/"):
        return False
    leaf = upper.rsplit("/", 1)[-1]
    return leaf.endswith((".RSA", ".DSA", ".EC", ".SF", ".MF")) or leaf.startswith("SIG-")


def patch_apk(source: Path, output: Path) -> None:
    with zipfile.ZipFile(source, "r") as zin:
        names = set(zin.namelist())
        required = {"AndroidManifest.xml", "assets/main_ui.js", "assets/il-build.json"}
        missing = required - names
        if missing:
            raise ValueError(f"Brak wymaganych plików APK: {sorted(missing)}")
        manifest = patch_manifest(zin.read("AndroidManifest.xml"))
        js = zin.read("assets/main_ui.js").decode("utf-8")
        if MARKER in js:
            raise ValueError("Poprawka Wideo jest już w tym APK")
        metadata = json.loads(zin.read("assets/il-build.json"))
        if metadata.get("versionName") != OLD_NAME or metadata.get("versionCode") != OLD_CODE:
            raise ValueError("Metadane APK nie odpowiadają stabilnej wersji 1.83.20")
        metadata["versionName"] = NEW_NAME
        metadata["versionCode"] = NEW_CODE
        metadata["variant"] = "R16-WIDEO-TAB"
        replacements = {
            "AndroidManifest.xml": manifest,
            "assets/main_ui.js": (js + "\n" + MARKER + JS_PATCH).encode("utf-8"),
            "assets/il-build.json": json.dumps(metadata, ensure_ascii=False, indent=2).encode("utf-8"),
        }
        output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(output, "w", allowZip64=True) as zout:
            for info in zin.infolist():
                if signature_entry(info.filename):
                    continue
                data = replacements.get(info.filename, zin.read(info.filename))
                zout.writestr(info, data)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Użycie: patch_il_video_tab.py input.apk output.apk")
    patch_apk(Path(sys.argv[1]), Path(sys.argv[2]))
    print(f"Utworzono wersję {NEW_NAME} z zakładką Wideo.")
