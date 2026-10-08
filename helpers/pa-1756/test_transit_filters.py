#!/usr/bin/env python3
"""Static UI regression: accessible category filters never request another GPS fix."""
from pathlib import Path
app=Path("project/app/src/main/java/com/ispina/lokalnie/NearbyDeparturesActivity.java")
src=app.read_text(encoding="utf-8")
checks={
    "transport modes":'new String[]{"all","bus","tram","skm","metro"}',
    "instant filter handler":'activeMode=mode;',
    "no extra GPS from filter":'chip.setOnClickListener(v->{\n                activeMode=mode;\n                applyModeFilter();',
    "44dp tap targets":'chip.setMinHeight(dp(44));',
    "accessible description":'chip.setContentDescription("Filtruj odjazdy: "+names[i]);',
    "foreground-only countdown":'if(screenVisible && !destroyed && !countdownLabels.isEmpty())',
    "separate LIVE status":'value.liveNote==null?',
}
for name,token in checks.items():
    assert token in src,name
assert ('item.getValue().setVisibility(activeMode.equals("all")' in src or
        'item.getValue().setVisibility(modeSelected &&' in src), 'visibility toggling'
part=src.split("    private void addModeFilters(boolean warsaw){",1)[1].split("    private void applyModeFilter(){",1)[0]
assert "requestFix(" not in part and "searchTimetable(" not in part
print("PASS: 5 category filters, accessible controls, instant display-only toggle")
