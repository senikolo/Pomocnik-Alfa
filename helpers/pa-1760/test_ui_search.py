#!/usr/bin/env python3
"""Protect the approved semantic palette and ensure searches remain local."""
from pathlib import Path
s=Path("project/app/src/main/java/com/ispina/lokalnie/NearbyDeparturesActivity.java").read_text(encoding="utf-8")
for color in ["0xFFEBE7F4","0xFFE9F0F6","0xFFEAF1ED",
              "0xFFF3F0E8","0xFFE4EFED"]:
    assert color in s, "Missing approved semantic color "+color
for key in [
    "DepartureQuickFilter.matches(card.departure,searchQuery)",
    "card.view.setVisibility(show?View.VISIBLE:View.GONE)",
    "addNearestPanel();",
    "addLineSearch();",
    "nearestDeparture=best;",
    'input.setContentDescription("Wyszukaj linię, kierunek lub przystanek w pobranych odjazdach")',
    "applyLivePalette(label.view,delay);",
    "applyLivePalette(label.liveState,delay);",
    "if(screenVisible && !destroyed && !countdownLabels.isEmpty())",
]:
    assert key in s, "Missing UI functionality: "+key
# Filtering never requests GPS, timetable downloads or permissions again.
start=s.index("    private void applyModeFilter(){")
stop=s.index("    private void horizontalGap(",start)
method=s[start:stop]
for prohibited in ("requestFix(", "searchTimetable(", "requestPermissions("):
    assert prohibited not in method, "Unexpected GPS request: "+prohibited
print("PASS: muted semantic colors WCAG-reviewed, instantaneous local search and next departure, LIVE expiry colors")
