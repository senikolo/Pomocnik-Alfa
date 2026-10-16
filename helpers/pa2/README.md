# Pomocnik Alfa 2.0 alpha.6

This extends the existing full PA APK. It does not replace native media, notes,
reminders, weather, reader, senior features or settings. Full original late-version
Java source is missing; the preserved base is decoded to smali/resources and rebuilt.

## Build

Run `.github/workflows/pa2-alpha.yml` on branch `work/pa2-alpha6-fixes-ux-20261015`.
It preserves the binary base in a persistent technical GitHub release so future builds
do not depend solely on expiring Actions artifacts. Java sources here compile to the
isolated fifth DEX. `patch_project.py` changes only integration of the regional module.
The preview uses `com.senikolo.pomocniknowy.pa2preview`, minSdk 29, targetSdk 34.
Original PA remains installed; stable signing secrets were confirmed empty.
The preview is signed once outside CI with its own retained preview key. Public
detached signature proofs allow CI to verify the exact APK without accessing any
private key. This key never signs the canonical production package.

## Scope

Eight GTFS feeds: WTP Warsaw (all published modes), MLD, three Krakow feeds,
Koleje Małopolskie rail, WKD, national rail (including KM and Intercity).
Each trip keeps original ID, arrival/departure seconds, boarding/alighting restrictions,
service masks and source attribution. Timetables work locally without network.
Foot transfers use proximity estimates, not a verified pedestrian road graph.
Maximum three transfers, rolling 14 future days plus previous service day.

## Conversation and internet

The local conversation is a transparent task assistant, not an LLM. Structured state
retains stops/time/line/direction across messages. Native bridges integrate existing
notepad and reminder scheduler. Weather uses Open-Meteo's noncommercial API; general
retrieval uses Polish Wikipedia. Neither is described as unrestricted web search.
The inactive online-AI toggle has been removed from the shipped UI. The local assistant and Wikipedia tools remain available. Users do not supply paid API keys. No secrets
are stored in APK. Puter is not an always-free alternative: users pay beyond allowances.

## Updates and LIVE

`pa2-data.zip` is checked with a per-file SHA-256 manifest and installed atomically.
A failed/expired update preserves current offline data. Update workflow publishes a
new dataset independently of APK. Vehicle positions and trip updates are matched by
original trip/date/stop IDs and expire after 120 seconds. GPS never generates an ETA.

## Verification

`test.cjs` covers real GTFS searches and conversation state; fixtures are isolated.
`PAProbe.java.test` installs and exercises the actual WebView and eight preserved
native screens on Android 10/11 in CI. Network playback on a physical device is not
claimed by these checks. Android 8/9 is not enabled without rebuilding and testing
older native dependencies; minSdk is not lowered by editing the manifest alone.

## Alpha.6 repairs

Journey buttons retain immutable trips; Polish dates, dot-times and follow-ups are supported. Live candidates include a bounded past window while normal scheduled departures exclude past trips. GPS and ETA expire by timestamp; visible vehicle screens refresh every 30 seconds and pause in background. Tram/map/dashboard icons match their function. Native modules and the original cat launcher are preserved. The package uses the retained PA2 preview key, signed locally; CI receives only exact-build public signing deltas.

## Continued UX stage: dashboard icon and tile audit

The native dashboard uses Polish, diacritic-insensitive semantic tile icons.
`PAIconsTest.java.test` has 24 icon cases; the build compiles and runs them.
The previous nearby-stop shortcut remains functional as `Przystanki w pobliżu`.
The full regional planner is `Planer i Alfa`; the dashboard smoke test asserts
there is exactly one of each. The Favorites page no longer copies the native
Radio/TV/Weather/Notes/Reminders cards and instead returns to the full dashboard.

The normal installed Pomocnik Alfa package is not changed. The preview uses
`com.senikolo.pomocniknowy.pa2preview` and needs Android 10+.
The alpha workflow now additionally produces
`PA2-installable-debug-alpha6` with an installable, **temporary-key signed**
APK and an Android 10/11 emulator smoke check. The temporary key changes for
each CI run, so this APK **cannot update an older separately signed preview**
in place: uninstall only the older `Pomocnik Alfa 2.0 TEST` first if Android
reports a signing conflict. Uninstalling that preview may clear its saved
test-only settings. Never uninstall the normal PA application to install this
preview. The normal long-term signing key is not in CI.

Do not call a build tested or usable until both emulator checks and APK
signature verification complete in GitHub Actions; real carrier GPS and
playback on physical devices still require field testing.


## Alpha.8 – Moje przystanki

The existing native nearby card now includes persistent exact-platform shortcuts.
Use the star to save/remove, tap a saved stop to open its board without GPS, or
use the manual chooser with its save checkbox. The editor removes selected saves.
Only stop IDs and labels are persisted; shared GTFS/RT and other modules are unchanged.
Rapid selection during an in-flight load reloads the latest chosen platform.

Branch `work/pa2-my-stops-20261016` builds alpha.8 (20008), package `.pa2preview`.
The retained PA2 preview key signs locally; public exact-build deltas trigger
`pa2-signed-test.yml`, which installs that exact APK on Android 11, tests favorite
UI, process restart with denied GPS and large fonts, then publishes a prerelease.
The alpha.7 `.pa2smarttest` temporary-key install cannot be updated in place;
alpha.8 installs beside it without removing its data. Future preview releases
must keep the retained key. Never use the production package for a test-key build.
