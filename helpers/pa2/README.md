# Pomocnik Alfa 2.0 SMART alpha.8 – Moje przystanki

Extends the existing full PA APK. Native media, notes, reminders, weather,
reader, senior features, settings, cat artwork and launcher remain in the base.
The complete late-version Java source is unavailable: the preserved base is
decoded to smali/resources and rebuilt, with the native regional module in DEX 5.

## Saved stops

The existing nearby card includes “Moje przystanki”. Save/remove an exact
platform using its star, tap a shortcut to open its board without typing or GPS,
or use the manual chooser with its default save checkbox. “Edytuj” removes
unchecked entries. Source + stop ID, name, platform and provider are persisted
in `pa2_boards/saved_stops`; the last chosen stop is retained separately.
Duplicate IDs are not inserted. Different platforms remain independent. Missing
IDs show an explanation while keeping shortcuts available. Rapid selections
respect the latest tap when an earlier load finishes.

Only IDs and labels are stored; departures still use the one active, validated
GTFS generation shared with the planner. No background GPS tracking is added.
LIVE refresh runs every 30 seconds on the visible dashboard and pauses offscreen.

## Build and signing

Branch `work/pa2-my-stops-20261016`, workflow `pa2-alpha.yml`, builds alpha.8
(versionCode 20008). It restores the complete `pa2-source-base` release, integrates
sources, runs logic/DOM/Java tests, rebuilds and zipaligns the unsigned APK.
For this UI change, `reuse_validated_data.py` reuses alpha.7's shared timetable
only after checking the pinned APK hash, all 814 manifest entries and current
coverage of all eight sources. Otherwise CI fetches a fresh generation.

The package is `com.senikolo.pomocniknowy.pa2preview`, minSdk 29, targetSdk 34.
A retained, separate PA2 preview key signs locally. Only exact-build public
signature deltas go into `signatures.json`; the private key never enters GitHub.
That commit triggers `pa2-signed-test.yml`, which reconstructs the signed APK,
verifies its certificate and alignment, installs that exact file on Android 11,
exercises saves, restarts the process with denied GPS and large fonts, and
publishes a prerelease only after the tests pass.

The alpha.7 `.pa2smarttest` APK used a one-time CI key and cannot be updated by
this key. Alpha.8 installs beside it and the main PA, without deleting either.
It can update an older `.pa2preview` signed with the retained preview key.
Other packages' private data are not copied automatically. Keep the retained
preview key for future upgrades; never use it on the production package.

## Transport and assistant

Eight GTFS feeds: WTP Warsaw (all published modes), MLD, three Krakow feeds,
Koleje Małopolskie rail, WKD and national rail. Trips preserve original IDs,
boarding/alighting restrictions, calendar masks and source attribution.
Data updates validate per-file SHA-256 and install atomically. Missing/expired
coverage is reported honestly. Foot transfers estimate geographic proximity.
Vehicle positions and TripUpdates match trip/date/stop IDs and expire after
120 seconds. Vehicle GPS never creates an ETA or confirmed delay.

The local assistant retains route/time/line/direction context and integrates
native notes and reminders. Weather uses Open-Meteo; general retrieval uses
Polish Wikipedia. It is a task assistant, not an unrestricted online LLM.

## Verification

Logic and DOM checks are separate from actual APK instrumentation. The latter
clicks save, manual chooser, quick selection and removal; checks unique IDs,
missing-stop recovery, restored shortcuts and exact boards after home recreation
and process restart; opens stop details and preserved native modules. The same
signed APK is tested on API 30 at standard size and 360 dp/font_scale 1.3.
Screenshots use real GTFS with explicitly injected test locations. Protobuf
fixtures test parser behavior and do not represent current real vehicles.
Physical-device GPS, every media stream and future reminder firing are not
asserted by the emulator smoke checks. Do not call a release tested until the
installation, instrumentation, signature and zipalign checks pass.
