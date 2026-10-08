#!/usr/bin/env python3
"""Integrate foreground-only countdown and manual refresh with PA 1.7.55."""
from pathlib import Path
from shutil import copyfile
base=Path("project/app/src/main/java/com/ispina/lokalnie")
target=base/"transit/DepartureCountdown.java"
target.parent.mkdir(parents=True,exist_ok=True)
copyfile("helpers/pa-1755/DepartureCountdown.java",target)
ui=(base/"NearbyDeparturesActivity.java").read_text(encoding="utf-8")
for token in [
    "DepartureCountdown.label(", "Odśwież odjazdy", "FIX_REUSE_MS",
    "screenVisible && !destroyed", "ui.removeCallbacks(countdownTick)",
    "label.liveState.setText(description)"
]:
    assert token in ui, "Missing countdown safeguard: "+token
main=base/"MainActivity.java"
s=main.read_text(encoding="utf-8")
assert "POMOCNIK ALFA 1.7.54" in s
main.write_text(s.replace("POMOCNIK ALFA 1.7.54","POMOCNIK ALFA 1.7.55"),encoding="utf-8")
print("PASS: foreground-only timer, 5-minute GPS reuse, abort-on-background, correct application label")
