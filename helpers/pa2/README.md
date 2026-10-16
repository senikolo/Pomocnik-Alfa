# Pomocnik Alfa 2.0 SMART alpha.9 — GPS and My Stops tabs

Extends the existing full PA APK, preserving native media, notes, reminders,
weather, reader, settings, the original cat and launcher. The preserved base is
decoded to smali/resources and rebuilt; the native regional module uses DEX 5.

## Dashboard

The existing native card is now titled “Tablice odjazdów”. It contains two real
view selectors, “W pobliżu · GPS” and “Moje przystanki”, with a visible selected
state and accessible text. GPS continues automatic 1–3 nearby boards. Saving a
star does not change the active GPS tab. My Stops contains saved shortcuts,
Add/Edit and the exact selected platform board, without requiring GPS.

`pa2_boards/saved_stops` retains source + stop IDs and display labels, while
`manual_stop` retains the last manually chosen platform. `boards_tab` remembers
the selected view; a legacy alpha.8 upgrade initially uses GPS without erasing
its existing saves. Switching to GPS never clears the manual choice. A revision
token rejects late results from a previously selected tab. Deleting the current
save selects another save or displays an empty My Stops view, with no GPS fallback.

The card reattaches by its stable view tag after the main dashboard is rebuilt.
Stop details still open the regional Activity with the exact platform ID and OSM.

## Build and signing

Branch `work/pa2-stops-tabs-20261016`, versionCode 20009, package
`com.senikolo.pomocniknowy.pa2preview`, minSdk 29, targetSdk 34.
`pa2-alpha.yml` restores the complete base, validates the shared GTFS generation,
runs logic/DOM/Java tests, rebuilds and zipaligns the unsigned APK. An unexpired
alpha.7 timetable is reused only after the pinned APK hash, all 814 manifest
entries and current coverage are verified; otherwise a new generation is fetched.

The retained PA2 preview key signs locally. Only exact-build public signature
deltas enter `signatures.json`; the private key never enters GitHub or CI.
`pa2-signed-test.yml` reconstructs and verifies the exact signed APK, tests it on
Android 11 and publishes a prerelease only after all checks pass.
Alpha.9 updates alpha.8 in place using the same key and package. Never uninstall
alpha.8 to update. The original production PA and alpha.7 `.pa2smarttest` remain
separate applications; the preview key never signs the production package.

## Data and assistant

One active GTFS generation is shared by dashboard and planner. Eight feeds cover
WTP Warsaw, MLD, three Krakow feeds, Koleje Małopolskie, WKD and national rail.
Trips retain original IDs, service masks and source attribution. Updates validate
SHA-256 and install atomically. Expired or missing coverage is reported honestly.
Vehicle GPS does not create ETA or confirmed delays; real TripUpdates require
exact trip/date/stop matching and expire after 120 seconds. Foreground refresh is
30 seconds; no background GPS tracking is added.

The local task assistant retains route/time/line/direction context and integrates
native notes/reminders. Weather uses Open-Meteo; general retrieval uses Wikipedia.

## Verification

Logic and DOM tests are separate from actual installation and UI instrumentation.
CI first installs the released alpha.8 and saves real platforms through its UI.
It upgrades with `adb install -r`, compares byte-identical preferences, then tests
alpha.9 tabs, independent selections, add/remove, rapid switching, unavailable
stops, main recreation and both tab choices across process restarts without GPS.
The same APK is tested at 360 dp/font_scale 1.3 and exercises the existing planner,
assistant, notes and preserved native screens. Screenshots use real GTFS with
explicitly injected test positions. Parser fixtures do not represent real vehicles.
Physical-device GPS, every media stream and future reminder firing are not asserted.
