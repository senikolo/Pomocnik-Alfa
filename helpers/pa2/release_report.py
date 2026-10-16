"""Publish evidence only after exact signed APK installation and tab tests pass."""
import sys,json,pathlib,hashlib
src,emu,out=map(pathlib.Path,sys.argv[1:4]);sha,run=sys.argv[4:6]
logs={n:(emu/n).read_text() for n in ['upgrade.txt','instrumentation.txt','large-font.txt','gps-restart.txt']}
for name,marker in [('upgrade.txt','byte-identical'),('instrumentation.txt','TOTAL PASS system installation'),('large-font.txt','TOTAL PASS system large-font'),('gps-restart.txt','TOTAL PASS GPS tab process restart')]:
    assert marker in logs[name] and 'FAIL' not in logs[name], name
assert 'FATAL EXCEPTION' not in (emu/'final-crash-buffer.txt').read_text()
apk=out/'Pomocnik-Alfa-2.0.0-alpha.9-Zakladki-Przystankow-TEST.apk'
proof=json.loads(pathlib.Path('helpers/pa2/signatures.json').read_text())
sources=json.loads((src/'live-source-access.json').read_text())
text=f'''# Pomocnik Alfa SMART alpha.9 — zakładki GPS i Moje przystanki

APK: `{apk.name}` — {apk.stat().st_size:,} bajtów.
SHA-256: `{hashlib.sha256(apk.read_bytes()).hexdigest()}`.
Źródła: `{proof['source_sha']}`. Commit wydania: `{sha}`.
[Kompilacja i testy logiki](https://github.com/senikolo/Pomocnik-Alfa/actions/runs/{proof['run']}) · [Instalacja i test dokładnie tego podpisanego APK](https://github.com/senikolo/Pomocnik-Alfa/actions/runs/{run}).

## Zmiana

Na właściwym pulpicie są dwie osobne zakładki:

- **W pobliżu · GPS** — automatyczny wybór 1–3 najbliższych tablic. Zapisanie gwiazdką nie przełącza widoku GPS.
- **Moje przystanki** — zapisana lista, przyciski Dodaj i Edytuj oraz tablica wybranego stanowiska. Działa bez GPS i bez ponownego wpisywania nazwy.

Powrót do GPS zachowuje listę i ostatni ręczny wybór. Powrót do Moich przystanków przywraca ten wybór. Ostatnia zakładka zostaje po ponownym uruchomieniu. Na pierwszym uruchomieniu po alpha.8 domyślny jest GPS, a dotychczasowe zapisy są nadal dostępne w drugiej zakładce. Pusta lista nie przełącza samoczynnie na GPS. Późna odpowiedź poprzedniego widoku nie zastępuje aktualnie wybranej zakładki.

Ten sam GTFS/LIVE, oryginalna grafika kota, Planer, Alfa, pogoda i pozostałe moduły. Bez drugiej bazy rozkładów i bez śledzenia GPS w tle. Rozkład działa lokalnie w zakresie potwierdzonych kalendarzy; aktualizacje bazy nadal obsługuje Planer. GPS pojazdu nie tworzy potwierdzonego ETA ani opóźnienia. LIVE wygasa według timestampów.

## Instalacja i aktualizacja

Pakiet `com.senikolo.pomocniknowy.pa2preview`, wersja `2.0.0-alpha.9-smart`, versionCode 20009, minSdk 29 i targetSdk 34. Sprawdzono Android 11 API 30.

**Aktualizuje alpha.8 bez odinstalowania i z zachowaniem zapisów.** Certyfikat preview jest ten sam: SHA-256 `2b8b84ea2f39891532f6a76b4ebe5bf847acc534b50f69c4302801b291228603`. Klucz prywatny pozostaje poza GitHub i CI. Podpis i zipalign są poprawne. Oryginalny PA i alpha.7 z innym pakietem pozostają osobnymi aplikacjami.

## Wykonane testy

Testy jednostkowe i DOM uruchamia etap kompilacji, oddzielnie od testów instalacji. Obejmują Planer, kontekst rozmowy, GTFS/kalendarze, GTFS-RT i wygasanie LIVE, ikony oraz reguły wyboru 1/2/3 stanowisk.

Na emulatorze zainstalowano rzeczywisty alpha.8 i jego instrumentację. Interfejs alpha.8 zapisał testowe, prawdziwe stanowiska. Następnie `adb install -r` zaktualizował go dokładnie tym alpha.9, bez odinstalowania. Plik preferencji tablic był identyczny bajtowo przed i po aktualizacji; nowa aplikacja odczytała wcześniejszą listę i ręczny wybór.

Instrumentacja alpha.9 sprawdziła:

- obie widoczne zakładki oraz zachowanie GPS po zapisaniu gwiazdką;
- osobne stanowiska, dodawanie, brak duplikatów, edycję i pustą listę;
- wybór zapisanej tablicy bez GPS, niezależny wybór GPS, powrót do ostatniego przystanku;
- szybkie przełączanie zakładek i odrzucenie spóźnionej odpowiedzi poprzedniego widoku;
- brak domyślnego miasta bez lokalizacji, 1/2/3 tablice z rzeczywistych GTFS;
- odtworzenie pulpitu i restart procesu z zapamiętaną zakładką Moje przystanki oraz osobno GPS;
- pełny widok właściwego stanowiska z odjazdami i OSM;
- Planer, asystenta, kontekst rozmowy, notatki i uruchomienie dotychczasowych modułów;
- ekran 360 dp i font_scale 1.3. Brak FATAL EXCEPTION.

Zrzuty pochodzą z emulatora, z rzeczywistych rozkładów i jawnie wstrzykniętej lokalizacji testowej. Fixture protobuf sprawdza parser, a nie bieżący stan rzeczywistego pojazdu. Testy nie potwierdzają każdego strumienia radia/TV, przyszłych alarmów ani fizycznego GPS telefonu. Brak internetu symulowano dla pobierania LIVE; nie jest to pełny test trybu samolotowego.

## Dostępność źródeł podczas kompilacji

'''
for s in sources:
    status=f"HTTP {s['status']}, wiek {s.get('age_seconds','?')} s, świeże: {s.get('fresh',False)}" if 'status' in s else s.get('error','brak danych')
    text+=f"- {s['url']}: {status}\n"
for name,log in logs.items():text+='\n## '+name+'\n\n```text\n'+log+'\n```\n'
(out/'PA-SMART-RAPORT.md').write_text(text)
