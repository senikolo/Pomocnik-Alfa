#!/usr/bin/env python3
"""Offline contract checks for the PA16 radio catalogue and the native directory."""
import json
import re
from pathlib import Path

folder = Path(__file__).resolve().parent
root = json.loads((folder / "radio_catalog.json").read_text(encoding="utf-8"))
stations = root["stations"]
assert len(stations) == 221, "The bundled offline fallback changed unexpectedly"
assert sum(x["countryCode"] == "PL" for x in stations) == 205
assert sum(x["countryCode"] == "DE" for x in stations) == 10
assert sum(x["countryCode"] == "RU" for x in stations) == 6
assert len({x["id"] for x in stations}) == len(stations)
assert all(x["name"].strip() and x["url"].startswith("https://") for x in stations)

activity = (folder / "PARadioCatalogActivity.java").read_text(encoding="utf-8")
directory = (folder / "PARadioDirectory.java").read_text(encoding="utf-8")
workflow = Path(".github/workflows/pa2-alpha10-preview.yml").read_text(encoding="utf-8")

checks = {
    "existing list preserved": "loadMyStations();\n        loadFavorites();\n        mergeDirectory(" in activity,
    "searchable country and Polish abroad sections": 'addCountryTab(poloniaTab, "POLONIA")' in activity,
    "manual refresh": "refreshOnlineDirectory(true)" in activity,
    "daily cached refresh": "REFRESH_INTERVAL_MS" in activity and directory,
    "pre-existing stations as fallback": "bundledCatalog.addAll(catalog)" in activity,
    "persisted directory": "writeCached" in directory and "readCached" in directory,
    "country search": "/json/stations/bycountrycodeexact/PL" in directory,
    "multilingual Polish search": "/json/stations/bylanguage/polish" in directory,
    "only HTTPS radio streams": '"https".equalsIgnoreCase(url.getProtocol())' in directory,
    "paginated directory": "MAX_ROWS_PER_QUERY" in directory and "offset=" in directory,
    "preserved TV changes": "python3 helpers/pa2/patch_tv_channels.py decoded" in workflow,
    "native compilation": "javac -source 8" in workflow,
    "separate test package": "pa16radiotest" in workflow,
}
for name, result in checks.items():
    assert result, name
    print("PASS:", name)

print(f"PASS: {len(stations)} bundled stations preserved, online radio directory configured")
