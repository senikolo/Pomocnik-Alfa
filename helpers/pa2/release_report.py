"""Build the public report only after successful APK and Android 11 jobs."""
import sys,json,pathlib,hashlib
src,emu,out=map(pathlib.Path,sys.argv[1:4]);sha,run=sys.argv[4:6]
log=(emu/'instrumentation.txt').read_text();large=(emu/'large-font.txt').read_text()
assert 'TOTAL PASS system installation' in log and 'TOTAL PASS system large-font' in large
apk=out/'Pomocnik-Alfa-2.0.0-alpha.7-SMART-TEST.apk'
sources=json.loads((src/'live-source-access.json').read_text())
text=f'''# Pomocnik Alfa SMART alpha.7 – raport wydania testowego

APK: `{apk.name}`. Rozmiar: {apk.stat().st_size:,} bajtów.
SHA-256: `{hashlib.sha256(apk.read_bytes()).hexdigest()}`.
Commit: `{sha}`. [Kompilacja i testy](https://github.com/senikolo/Pomocnik-Alfa/actions/runs/{run}).

## Instalacja

Oddzielny pakiet `com.senikolo.pomocniknowy.pa2smarttest`, Android 10+ (minSdk 29 zachowane), sprawdzony na Androidzie 11 API 30.
Nowy testowy podpis CI różni się od alpha.6 i podpisu głównego PA. APK instaluje się OBOK obu wcześniejszych aplikacji. Nie odinstalowuj ich. Dane wcześniejszych pakietów nie są kopiowane automatycznie.
Weryfikacja podpisu i zipalign zakończyła się pomyślnie; certyfikat opisano w signature.txt.

## Wdrożenie

Istniejący główny kafelek Przystanki w pobliżu zawiera od jednej do trzech tablic. Pierwsza dotyczy najbliższego stanowiska; każda para stanowisk w zestawie jest oddalona o najwyżej 100 m. Duplikaty fizycznego stanowiska są scalane; przeciwne stanowiska pozostają osobne. Przy większym zespole brana jest pod uwagę liczba linii i różnorodność przewoźników.
Pięć najbliższych odjazdów na tablicę; przewijane tablice i przyciski nazw przystanków. Godziny, odliczanie, kierunki, kolor i tekst statusu, pomiar LIVE. Wybór ręczny bez GPS jest zapamiętywany. Kliknięcie otwiera to stanowisko w regionalnym module z OSM.
Wspólny zestaw schema-2 i wspólna aktywna generacja danych z Planerem. Bez śledzenia GPS w tle; zapytania LIVE co 30 s tylko na widocznym pulpicie, odliczanie co sekundę. Brak nowego niezależnego magazynu rozkładów.
Zachowano pozostałe moduły i oryginalne grafiki kota. Dostępność ekranów potwierdzono testami; odtwarzania każdej stacji TV/radia ani alarmów w przyszłości nie potwierdza ten test.

## Testy

| Scenariusz | Wykonana weryfikacja |
|---|---|
| 1: oddalony drugi przystanek | Test reguł Java oraz prawdziwy pulpit z realnym GTFS |
| 2: dwa stanowiska, 60 m | Dokładny fixture Java; realny pulpit z dwoma stanowiskami |
| 3: trzy stanowiska w 100 m | Fixture Java; realny pulpit z trzema tablicami |
| 4: więcej stanowisk | Test reguł max 3 i braku łańcuchowego grupowania |
| 5: brak GPS | Kod kontrolera na rzeczywistym pulpicie; przyciski GPS i wyboru ręcznego |
| 6: brak internetu | Instrumentowany fixture odrzucający żądania LIVE; lokalne pliki GTFS nadal służą tablicom. Nie jest to pełny test telefonu w trybie samolotowym |
| 7: brak LIVE | Instrumentowany fallback bez fikcyjnych ETA/opóźnień |
| 8: świeże RT | Instrumentowany protobuf z dokładnym trip/date/stop, ETA i opóźnieniem |
| 9: stare RT | Stary nagłówek oraz timestamp odrzucony; JS wygasza status i ETA |
| 10: zmiana lokalizacji | Wstrzyknięte współrzędne realnych przystanków, pulpit zmienia 1/2/3 tablice |
| 11: północ, święta, DST | Fixture kalendarzy i kursu 25:00; test Java czasu GTFS i zmiany DST |
| 12: ponowne otwarcie | Zamknięcie i odtworzenie MainActivity; zapisany ręczny przystanek |
| 13: współistnienie | Ten sam zainstalowany APK: Planer, asystent, notatnik, ekran pogody i tablice; brak FATAL EXCEPTION. Nie potwierdza każdego wyniku sieciowego pogody |
| 14: Android 11 i większa czcionka | Emulator API 30; ponowny test tablic na ekranie 360 dp z font_scale 1.3, kontrola braku ucięcia tytułów i zrzuty |
| 15: pozostałe funkcje i kot | Uruchomienie modułów oraz kontrola oryginalnej ikony launchera; zakres odtwarzania multimediów ograniczony jak powyżej |

Testy jednostkowe/logiki i DOM są oddzielone od faktycznej instalacji emulatora. Fixture RT potwierdza parser, nie stan rzeczywistego autobusu.
Zrzuty boards-1/2/3 powstały w emulatorze na właściwym pulpicie, z rozkładów źródłowych i testowo ustawioną pozycją. To nie jest rzeczywista lokalizacja użytkownika. Jeżeli RT brak, ekrany uczciwie pozostają planowe.

## Źródła LIVE – stan z kompilacji

'''
for s in sources:text+=f"- {s['url']}: "+(f"HTTP {s['status']}, wiek nagłówka {s.get('age_seconds','?')} s, świeże: {s.get('fresh',False)}" if 'status' in s else s.get('error','brak danych'))+'\n'
text+='''
Warszawski endpoint pozycji nie daje w tym module potwierdzonej prognozy ETA. TripUpdates Krakowa są używane wyłącznie po ścisłym dopasowaniu kursu, dnia i stanowiska oraz kontroli wieku 120 s. MLD/Koleje Małopolskie nie otrzymują wymyślonego LIVE, gdy skonfigurowane źródło go nie dostarcza. Godziny planowe pozostają dostępne offline przez okres ważności paczki; po jego zakończeniu odśwież bazę w Planerze.

## Log instalacji i UI

```text
'''+log+'\n```\n\n## Duża czcionka i mały ekran\n\n```text\n'+large+'\n```\n'
(out/'PA-SMART-RAPORT.md').write_text(text)
