# Pomocnik Alfa — modernizacja transportu 17.10.2026

## Ustalona baza i podpis

Ostatnie opublikowane wydanie: pa2-smart-alpha9-20261016-11, źródła e9b9a96f4727c3a241421c8e5680805363a6229e, commit wydania 8f6471e3a8014d01d4b7e475211486d763f88651. Zachowany pakiet com.senikolo.pomocniknowy.pa2preview i istniejący pełny projekt (odtwarzany z pa2-source-base); kolejne wydanie 20010. Ustawienia, identyfikatory zapisanych stanowisk i ikony kota są zachowane. Zmienia się tylko aktywna generacja danych rozkładowych, bez kasowania ulubionych.

WAŻNE: alpha.9 używa certyfikatu preview 2b8b84ea2f39891532f6a76b4ebe5bf847acc534b50f69c4302801b291228603. SIGNING-STABLE.md wymaga innego certyfikatu 361442c898552236a7d4299eeda5c3dc275ba1f368c011292dc07d44b2e8ace1. Żaden APK podpisany tym drugim kluczem nie aktualizuje alpha.9. Nie odinstalowywać działającej aplikacji: grozi utratą lokalnych danych. Workflow używa wyłącznie istniejących Secrets PA_STABLE_KEYSTORE_B64 / PASS, sprawdza fingerprint przed podpisaniem i po nim. Nie generuje klucza. Jeśli sekretów brak, wydanie jest zablokowane. Test nowej instalacji nie dowodzi zgodności aktualizacji z alpha.9.

## Wykryte i poprawiane problemy

- Główna tablica zawierała „GPS LIVE, brak ETA”. Nowe komunikaty odróżniają aktualną prognozę, pozycję pojazdu, odjazd planowy, stare dane i potwierdzone odwołanie. GPS sam nie daje potwierdzonej prognozy.
- Przewoźnik nie był czytelny na tablicach mimo obecności danych kolejowych. Dodano oddzielny identyfikator operatora, numer pociągu, przyjazdy planowe, stacje pośrednie i filtry rodzaju transportu/operatora na pełnej tablicy.
- KM pochodzi z polish_trains GTFS; KMŁ ma własny kolejowy GTFS; MLD pozostaje osobnym operatorem autobusowym. SKM rozpoznaje się po agencji lub znanym źródle WTP i opublikowanej linii, nigdy po samym route_type=2.
- W podwójnych źródłach kolejowych preferowany jest dedykowany WTP/SKA dla dni, na które ma potwierdzone kursy. Dla pozostałych dni pozostaje krajowy GTFS. Nie dopasowuje się LIVE po samym numerze pociągu.
- Import pomijał frequencies.txt, traktując wzorce metra jak konkretne kursy. exact_times=1 jest rozwijane; exact_times=0 jest prezentowane jako częstotliwość. Takie metro nie wyznacza dokładnych połączeń przesiadkowych w obecnym planerze; interfejs podaje to ograniczenie. Nie są tworzone fikcyjne odjazdy.
- Rozbieżna obsługa zmiany czasu w Androidzie i JS: oba używają reguły GTFS lokalnego południa minus 12 godzin.
- Pobieranie GTFS odbywa się strumieniowo z limitem 256 MiB archiwum / 2 GiB po rozpakowaniu i kontrolą CRC. Brak jednego źródła nie przerywa pobrania pozostałych. Zachowane dane muszą przejść kontrolę kalendarzy.
- Własne nazwy i kolejność zapisanych przystanków: przytrzymanie skrótu otwiera edycję nazwy i przesuwanie wcześniej/później. Istniejący zapis i usuwanie zachowane, GPS nie jest wymagany.
- Pełna tablica ma duże godziny, rozwijane źródła/stacje pośrednie i obsługę ograniczenia animacji. Mapa OSM pozostaje interaktywna i używa wyłącznie rzeczywistych, świeżych pozycji.

## Źródła i rzeczywiste rozmiary

Pobrano 17.10.2026: warsaw.zip 99 778 979 bajtów skompresowanych wpisów (587 288 477 po rozpakowaniu); polish_trains.zip 31 132 184 / 117 900 296; kmlrail 435 982 / 2 965 098; mld 826 801 / 5 846 895. Faktyczny ZIP ma dodatkowo nagłówki. Stary GtfsNearby.java ma limit 29 MiB i klasyfikuje kolej jako „skm”; nie jest silnikiem nowego pulpitu/planera PA2. Archiwalne helpery nie mogą być traktowane jako aktualne źródła tego APK. Podczas rekonstrukcji starego modułu zmieniono klucz kategorii skm na rail i ogólne etykiety SKM na Kolej oraz limit kompaktowego GTFS z 29 do 80 MiB. Teksty ze starych ekranów są czyszczone, bez deklarowania nowej integracji w tych ekranach.

- https://mkuran.pl/gtfs/ — opis źródeł, licencji i ograniczeń, krajowy GTFS obejmuje KM, SKM Warszawa, KMŁ; krajowy LIVE bywa nieaktualny przez ponad godzinę.
- https://kolejemalopolskie.com.pl/pl/rozklady-jazdy/gtfs — odrębne oficjalne pliki kolejowy i autobusowy.
- https://www.kolejemalopolskie.com.pl/rozklady_jazdy/kml-ska-gtfs.zip
- https://mkuran.pl/gtfs/warsaw.zip — pobrany plik zawiera S1, S2, S3, S4, S40.
- https://mkuran.pl/gtfs/polish_trains.zip — agency_id KM, KML, SKM są różnymi operatorami.
- https://gtfs.ztp.krakow.pl/ — istniejące źródła Krakowa zachowane.

LIVE zawsze wymaga zgodności źródła, trip_id, dnia i przystanku oraz wieku do 120 s. Przyjazdy na osobnej tablicy są obecnie planowe, nie używają prognozy odjazdu jako przyjazdu. W dedykowanym GTFS KMŁ nie potwierdzono zgodnego źródła LIVE; aplikacja pokazuje rozkład. Pełne licencje i atrybucje pochodzą z indeksu/attributions.txt w ustawieniach aplikacji.

## Kontrola jakości

Wyniki dopisywane po faktycznym uruchomieniu: testy generatora i kalendarzy, logika planera na pobranych danych, testy DOM, kompilacja Java/DEX i pełnego APK, instrumentacja Android API 30. Sam ten dokument nie potwierdza przejścia bramek ani gotowości APK. Pipeline publikuje logi osobno; podpis i test urządzenia muszą być zakończone sukcesem przed deklaracją instalowalnego wydania.


## Wyniki i blokada wydania

Lokalnie: PASS 20 scenariuszy planera, PASS 16 poprawek/regresji, PASS testy DOM bez błędów wykonania, PASS rzeczywiste trasy KMŁ Kraków–Wieliczka/Lotnisko/Tarnów i KM Warszawa Wschodnia–Legionowo, numery pociągów, przyjazdy, stacje pośrednie, rozróżnienie operatorów i zmiana czasu. Dane testów to pobrane GTFS, a scenariusze LIVE korzystają także z jawnych fixture; nie potwierdzają każdego rzeczywistego pojazdu.

GitHub Actions run 39007700465 skompilował Javę, DEX, pełne APK i instrumentację. Podpisywanie zatrzymało się z komunikatem `Missing PA_STABLE_KEYSTORE_B64`. Test Androida został pominięty z powodu braku podpisanego APK. Nie ma potwierdzonego instalowalnego wydania ani wyników uruchomienia Androida z tego zadania. Pierwszy przebieg zawierał również niepoprawnie dobraną stację testu metra (przystanek autobusowy „Metro Młociny” zamiast stacji „Młociny”); poprawiono test i włączono obowiązkowy pipefail, aby żaden błąd testów nie był ukryty przez tee. Kolejne przebiegi weryfikują poprawione źródła.

Nie wygenerowano nowego klucza i nie odczytywano wartości sekretów. Do dokończenia konieczne jest przywrócenie właściwego istniejącego podpisu. Samo przywrócenie produkcyjnego certyfikatu z SIGNING-STABLE.md nie zapewnia aktualizacji alpha.9, która używa podpisu preview. Nie należy odinstalowywać działającej alpha.9 w celu obejścia tej niezgodności.
