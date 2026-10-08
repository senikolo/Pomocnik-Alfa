#!/usr/bin/env python3
"""Stop/departure semantic color mapping and WCAG AA foreground/background audit."""
from pathlib import Path
import re
src=Path("project/app/src/main/java/com/ispina/lokalnie/NearbyDeparturesActivity.java").read_text(encoding="utf-8")
mapping={
    "lineChip":("FF5836A5","FFFFFFFF","FF714DC0","FFFFFFFF"),
    "destinationChip":("FFE6F0FF","FF174783","FF1C3A61","FFF0F6FF"),
    "stopChip":("FFE0F4E9","FF075B46","FF173F34","FFE3FFF0"),
    "platformChip":("FFFFEDD0","FF794200","FF52391D","FFFFEAC0"),
    "timeChip":("FF125E7B","FFFFFFFF","FF17678B","FFFFFFFF"),
}
def luminosity(s):
    rgb=[int(s[n:n+2],16)/255 for n in (2,4,6)]
    lin=[x/12.92 if x<=0.04045 else ((x+0.055)/1.055)**2.4 for x in rgb]
    return .2126*lin[0]+.7152*lin[1]+.0722*lin[2]
def ratio(a,b):
    x,y=sorted((luminosity(a),luminosity(b)),reverse=True)
    return (x+.05)/(y+.05)
for name,colors in mapping.items():
    assert src.count("private TextView "+name+"(")==1,name
    body=src.split("private TextView "+name+"(",1)[1].split("\n    }",1)[0]
    for c in colors:assert "0x"+c in body,(name,c)
    for label,fg,bg in (("light",colors[1],colors[0]),("dark",colors[3],colors[2])):
        score=ratio(fg,bg)
        assert score>=4.5,(name,label,score)
        print(name,label,round(score,2))
assert src.count("view.setSingleLine(true)")>=1
assert "destinationChip(d.headsign)" in src
assert "TextView platform=platformChip(" in src
assert 'scroller.setHorizontalScrollBarEnabled(false)' in src
print("PASS: 5 consistent semantic colors, WCAG AA in light and dark, single-line direction")
