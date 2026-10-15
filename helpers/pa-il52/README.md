# Pomocnik Alfa — wyszukiwanie autobusów jak w IL 52

The UI source is an exact, SHA-256-verified copy of the `assets/main_ui.js`
asset in the user's IL 2.0 Premium Motion 52 APK from 2026-10-14.

Target PA: 1.7.66 Android 10+; versionCode to be bumped for a separate TEST
install. Keep all existing PA activities, radio, reader, reminders, and data.
New PA home card opens an isolated Android WebView screen bundled in the APK;
it does **not** redirect to an external browser.

The native IL app's proprietary JavaScript interfaces and realtime vehicle GPS
are not copied automatically. Timetables can be embedded/stale; never label a
scheduled time or animation as live vehicle position. Compare with the
physically installed IL before claiming exact end-to-end equivalence.
