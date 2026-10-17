#!/usr/bin/env python3
"""Reuse the original PA cat launcher artwork from the user's existing base APK."""
import re,sys,zipfile
from pathlib import Path
apk=Path(sys.argv[1]);out=Path(sys.argv[2])
with zipfile.ZipFile(apk) as z:
 names=z.namelist()
 options=[
  n for n in names
  if n.endswith(".png") and ("ic_launcher_cat_foreground" in n or re.search(r"/ic_launcher\.png$",n))
 ]
 if not options:raise SystemExit('STOP: cat art not found in original PA; do not ship another icon')
 options.sort(key=lambda n:(0 if "cat_foreground" in n else 1,-z.getinfo(n).file_size))
 chosen=options[0]
 data=z.read(chosen)
 if data[:8]!=b'\x89PNG\r\n\x1a\n':raise SystemExit('Launcher is not PNG')
 out.parent.mkdir(parents=True,exist_ok=True)
 out.write_bytes(data)
 print('PASS original PA cat icon',chosen,'bytes',len(data))
