# Pomocnik Alfa 2.0 — intelligence implementation backlog

This file tracks **real implemented features versus planned work**. The existing PA app is preserved; the experimental PA2 preview lives in a distinct package. This branch is NOT a release and does not itself produce a tested APK.

## Delivered on this branch — conversational context, step 1

- Natural departure-stop wording: `na/przy przystanku Tarchomin`, without inferring GPS.
- Clear a prior line's direction on a change of line, while respecting a new direction explicitly named in the same sentence.
- A new destination after checking live vehicle position switches intent back to **route**, clearing incompatible line/direction constraints.
- Relative conversational time: `pół godziny później` (+30 min), `półtorej godziny wcześniej` (-90 min).
- Dedicated regression checks in `test-fixes.cjs` for stop wording, changing line, returning to route, and time adjustment.

## Seven complementary intelligence capabilities — staged acceptance criteria

1. **Conversational intelligence** — current local state machine knows source, destination, date, time, line and direction. Next: intent routing with explicit confidence, follow-up disambiguation, unit tests for contradictory input, and optional server NLP fallback. Never ask again for an already specified field.
2. **Journey planning** — use the existing offline GTFS engine for Warsaw and Małopolska and preserve rail, service calendars, transfer chronology, accessibility where verified, walking estimates labelled as estimates, and actual source credits. Add real-time overlay only for feed-matched trip IDs.
3. **Where is my bus/tram?** — ask stop, line and direction only when missing. Position map is OpenStreetMap; ETA must come from trusted GTFS-RT prediction, not computed from arbitrary GPS distance. Never label schedule-only information as LIVE.
4. **Weather advice** — extend existing Open-Meteo bridge with location-aware precipitation/temperature recommendation, indicating forecast time and never assuming the user is in Warsaw/Ispina.
5. **Web search** — today the shipped preview supports Wikipedia, NOT unrestricted current-web search. Optional `backend/worker.mjs` is an unconfigured candidate requiring an owner's hosting, controls, reliable search source and quota. Do not ship unusable switches or embed API keys in APK.
6. **Personal assistant** — reuse native notes and reminder bridges. An action affecting user state requires an affirmative user tap; favorites and preferences remain local with clear reset control.
7. **Cross-module orchestration** — one constrained intent router should select verified planner / live feed / weather / notes / reminders / web tools. Keep citations, timestamps and provenance; reject invented timetable results and expired vehicle positions. Expose understandable errors and offline fallbacks.

## Quality gates before a user-facing APK

- Run `TZ=Europe/Warsaw node helpers/pa2/test.cjs build/data` and `TZ=Europe/Warsaw node helpers/pa2/test-fixes.cjs build/data`, then existing UI and LIVE tests.
- Run `node --check` for modified JavaScript, native instrumentation tests, and Android 10/11 emulator smoke checks.
- Do not lower minSdk 29 in the reconstructed preview without recovering and testing original Android 8/9 compatible native dependencies.
- Check app package name, signing certificate and version. The temporary CI debug key changes and **cannot update** an already installed preview signed with another key. Preserve the original Pomocnik Alfa installation and cat icon.
- Real telephone/GPS, network availability, audio and notification permissions require physical-device verification.
- Only publish an installable APK after building, signing and verifying that **exact** artifact. A branch or PR is not an APK.

## Priorities for next increments

P0: conversation semantics, stable state and safety test coverage.
P1: unify action routing with planner/live weather, optional full-web backend behind feature availability checks.
P1: test transport coverage and on-device UI of every mode (bus, tram, metro, rail).
P2: adaptive travel advice and user preferences, with opt-in permissions and low resource use.
P2: Android 8/9 compatibility through reproducible native rebuild, not manifest edits.
