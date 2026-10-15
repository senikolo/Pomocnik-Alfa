# Pomocnik Alfa 2.0 alpha.1

This extends the existing full PA APK. It does not replace native media, notes,
reminders, weather, reader, senior features or settings. Full original late-version
Java source is missing; the preserved base is decoded to smali/resources and rebuilt.

## Build

Run `.github/workflows/pa2-alpha.yml` on branch `work/pa2-20261015`.
It preserves the binary base in a persistent technical GitHub release so future builds
do not depend solely on expiring Actions artifacts. Java sources here compile to the
isolated fifth DEX. `patch_project.py` changes only integration of the regional module.
APK application ID is canonical `com.senikolo.pomocniknowy`, minSdk 29, targetSdk 34.
The existing stable signing secret is required and its certificate is checked.
No key is generated as a substitute.

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
Optional Cloudflare Workers AI backend is prepared separately; it is not active until
an owner connects/provisions the service. Users do not supply paid API keys. No secrets
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
