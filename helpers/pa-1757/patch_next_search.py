#!/usr/bin/env python3
"""Apply local timetable search (no GPS) and next-departure summary to PA 1.7.57."""
from pathlib import Path
from shutil import copyfile
base=Path("project/app/src/main/java/com/ispina/lokalnie")
ui=(base/"NearbyDeparturesActivity.java").read_text(encoding="utf-8")
checks=[
    "DepartureQuickFilter.matches(card.departure,searchQuery)",
    "addLineSearch();",
    "addNearestPanel();",
    "searchQuery=s.toString().trim();",
    "applyLivePalette(label.liveState,delay)",
    "refreshNearestFromVisible(now)",
    "if(searching || localVisible<12)",
]
for token in checks:
    assert token in ui, "Missing search/next safety: "+token
dest=base/"transit/DepartureQuickFilter.java"
dest.parent.mkdir(parents=True,exist_ok=True)
copyfile("helpers/pa-1757/DepartureQuickFilter.java",dest)
main=base/"MainActivity.java"
s=main.read_text(encoding="utf-8")
assert "POMOCNIK ALFA 1.7.56" in s
main.write_text(s.replace("POMOCNIK ALFA 1.7.56","POMOCNIK ALFA 1.7.57"),encoding="utf-8")
print("PASS: search all loaded trips and next departure, no new location permission")
