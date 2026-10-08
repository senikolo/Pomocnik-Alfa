#!/usr/bin/env python3
"""Static smoke checks for PA's attributed embedded LIVE website."""
from pathlib import Path
base=Path("project/app/src/main/java/com/ispina/lokalnie")
activity=(base/"LiveMapActivity.java").read_text(encoding="utf-8")
line=(base/"LineDeparturesActivity.java").read_text(encoding="utf-8")
near=(base/"NearbyDeparturesActivity.java").read_text(encoding="utf-8")
manifest=Path("project/app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
links=(base/"transit/CzynaczasLinks.java").read_text(encoding="utf-8")
assert manifest.count('android:name=".LiveMapActivity"')==1
assert "android:exported=\"false\"" in manifest
assert "setJavaScriptEnabled(true)" in activity
assert "setDomStorageEnabled(true)" in activity
assert "setAllowFileAccess(false)" in activity
assert "setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW)" in activity
assert "addJavascriptInterface" not in activity
assert 'EXTRA_STOP_ID' in activity and 'EXTRA_STOP_NAME' in activity
assert 'render(data,line,stopId,stopName)' in line
assert 'EXTRA_STOP_NAME,stopName' in line
assert 'EXTRA_STOP_ID,selectedStopId' in line
assert 'result.stops.isEmpty()?null:result.stops.get(0).id' not in line
assert 'closest.id.matches("[0-9]{6}")' in near
assert "LiveMapActivity.class" in near
assert 'CzynaczasLinks.forStop("warsaw",id)' in activity
assert '?przystanek=' in links
assert 'PA nie wylicza opóźnień' in line
print("PASS: embedded external LIVE, explicit platform context, fallback, accessibility and security guards")
