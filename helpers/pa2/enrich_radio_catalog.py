#!/usr/bin/env python3
"""Add station icon URLs to the bundled catalogue before the Android build."""
import json
import sys
from pathlib import Path
from urllib.error import URLError
from urllib.parse import urlencode, urlparse, urlunparse
from urllib.request import Request, urlopen


API = "https://de1.api.radio-browser.info/json/stations/byuuid?"
USER_AGENT = "PomocnikAlfa/2.0 (Polish radio catalogue)"
BATCH_SIZE = 40


def https_url(value: str) -> str:
    value = (value or "").strip()
    if value.startswith("http://"):
        value = "https://" + value[7:]
    parsed = urlparse(value)
    if parsed.scheme != "https" or not parsed.netloc:
        return ""
    return urlunparse(parsed)


def homepage_icon(homepage: str) -> str:
    homepage = https_url(homepage)
    if not homepage:
        return ""
    parsed = urlparse(homepage)
    return f"https://{parsed.netloc}/favicon.ico"


def main() -> None:
    path = Path(sys.argv[1])
    root = json.loads(path.read_text(encoding="utf-8"))
    stations = root.get("stations", [])
    by_id = {row.get("id", row.get("stationuuid", "")): row for row in stations}
    ids = [station_id for station_id in by_id if station_id]
    api_count = 0
    errors = 0

    for start in range(0, len(ids), BATCH_SIZE):
        batch = ids[start : start + BATCH_SIZE]
        request_url = API + urlencode({"uuids": ",".join(batch)})
        request = Request(request_url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"})
        try:
            with urlopen(request, timeout=10) as response:
                result = json.loads(response.read().decode("utf-8"))
            if not isinstance(result, list):
                raise ValueError("Unexpected Radio Browser response")
        except (OSError, URLError, ValueError, json.JSONDecodeError) as error:
            errors += 1
            print(f"Radio Browser icon lookup skipped batch {start // BATCH_SIZE + 1}: {type(error).__name__}")
            continue

        for info in result:
            station_id = info.get("stationuuid", "")
            row = by_id.get(station_id)
            if row is None:
                continue
            icon = https_url(info.get("favicon", ""))
            if icon:
                row["favicon"] = icon
                api_count += 1

    fallback_count = 0
    for row in stations:
        if not https_url(row.get("favicon", "")):
            icon = homepage_icon(row.get("homepage", ""))
            if icon:
                row["favicon"] = icon
                fallback_count += 1

    path.write_text(json.dumps(root, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    with_icon = sum(1 for row in stations if https_url(row.get("favicon", "")))
    print(f"Radio station marks: {with_icon}/{len(stations)} URLs; {api_count} Radio Browser icons, {fallback_count} homepage fallbacks; {errors} lookup errors")


if __name__ == "__main__":
    main()
