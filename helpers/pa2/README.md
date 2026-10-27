# Pomocnik Alfa 2.0 — alpha.14 economy search and German practice

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

## Oszczędzanie i plan posiłków

- DealsActivity remains the single product-offer search. The duplicate Smart
  Saver home card is removed; subscriptions keep their existing dedicated card.
- The new “Plan posiłków” activity stores the daily budget, serving count and
  editable preferred-store list locally. It suggests a three-meal plan and a
  consolidated ingredient list (without quantities). Its meal costs are
  indicative estimates with a 12% buffer; current offers do not automatically
  reprice the menu, and the estimates are not verified store prices.
- Ingredient searches call the existing DealsRepository.search and filter
  returned offers by the selected supported retailers. The filter reads its
  canonical list from DealsParser, including POLOmarket and SPAR. Common Polish
  grocery forms are sent as comma-separated alternatives in one repository
  call; DealsRepository searches them in parallel under one deadline. This
  avoids waiting for separate full searches for each form. The repository's
  result order is preserved so unlike units are not ranked as though they were
  comparable. Result cards show package price, unit price and known purchase
  conditions. Meal search buttons scroll directly to the results. When selected
  stores have no match,
  results from other supported stores remain visible and can be added to the
  preferred list. Offer timestamps, source notes, validity and links are shown
  when supplied by the repository. An empty result
  does not mean that a product is unavailable or that no other store has a deal.
- Existing saving goals and 30/90-day ticket tools remain reachable from the
  new screen. Category-specific saving advice covers groceries, home energy,
  subscriptions and transport.

## Optional German learning

“Trenuj swój niemiecki” reads the official tagesschau.de Wirtschaft RSS feed.
The user chooses B1/B2, an approximate daily notification time and whether the
daily lesson is enabled. The lesson includes a short German news excerpt,
Polish vocabulary notes, a comprehension prompt and a link to the full story.
The lesson can open in the existing Czytnik Alfa as a local HTML document,
with the user's B1/B2 level, vocabulary and exercise. Tapping the daily
notification opens that reader document directly; the separate source button
continues to open the publisher's full article.
The B1/B2 control is an approximate exercise mode, not a CEFR certification of
each Tagesschau article. RSS excerpts are shown with their publication date.
The daily notification can be disabled in the module settings; article and
preference data are cached locally. No paid news or AI API is required.

## Build and signing

Branch `work/pa14-economy-search-20261027`, versionCode 20014,
versionName `2.0.0-alpha.14-economy-search`, minSdk 29, targetSdk 34. The
`pa2-alpha10-preview.yml` workflow rebuilds the full base, applies the regional,
radio, economy and German-learning patches, then creates an isolated test app
ID from the CI run ID. Each run uses a temporary test signing key. The isolated
test branch uploads the signed APK as a workflow artifact and skips the release
job; it can be installed alongside the existing app.

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

## Build

The alpha.14 build-only workflow prepares the app data, compiles the Android
modules, signs the isolated APK and uploads it as a workflow artifact. It does
not publish a GitHub release or run the emulator test suite. The previously
shared alpha.13 APK does not contain the economy-search fix.
