#!/usr/bin/env python3
"""Wire the native station catalogue into the existing Pomocnik Alfa app."""
import json
import sys
from pathlib import Path

root = Path(sys.argv[1])
main_files = list(root.glob("smali*/com/ispina/lokalnie/MainActivity.smali"))
assert len(main_files) == 1, f"Expected one MainActivity.smali, found {len(main_files)}"
main = main_files[0]
text = main.read_text(encoding="utf-8")
old = "const-class v0, Lcom/ispina/lokalnie/RadioActivity;"
new = "const-class v0, Lcom/ispina/lokalnie/PARadioCatalogActivity;"
if new not in text:
    assert text.count(old) == 1, "Unexpected Radio menu Activity reference"
    text = text.replace(old, new, 1)
main.write_text(text, encoding="utf-8")

manifest = root / "AndroidManifest.xml"
xml = manifest.read_text(encoding="utf-8")
activity = '<activity android:exported="false" android:name="com.ispina.lokalnie.PARadioCatalogActivity" android:windowSoftInputMode="adjustResize"/>'
if 'android:name="com.ispina.lokalnie.PARadioCatalogActivity"' not in xml:
    anchor = '<activity android:exported="false" android:name="com.ispina.lokalnie.RadioActivity"/>'
    assert xml.count(anchor) == 1, "Unexpected RadioActivity manifest entry"
    xml = xml.replace(anchor, anchor + "\n        " + activity, 1)
manifest.write_text(xml, encoding="utf-8")

catalog = root / "assets" / "pa_regions" / "radio_catalog.json"
assert catalog.is_file(), f"Missing bundled station repository: {catalog}"
data = json.loads(catalog.read_text(encoding="utf-8"))
stations = data.get("stations", [])
assert len(stations) == 216, "Radio catalogue must contain 200 Polish plus 16 German and Russian stations"
assert sum(row.get("countryCode") == "PL" for row in stations) == 200, "Radio catalogue must preserve all 200 Polish stations"
assert all(row.get("url", "").startswith("https://") for row in stations), "Radio catalogue must use HTTPS streams"
print("PASS: Radio menu opens the editable catalogue with 200 Polish and 16 German/Russian stations")
