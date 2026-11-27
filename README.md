# Pomocnik Alfa 18 TEST: telewizja i postęp nagrywania

## Nagrywanie na żywo

1. Włącz stację w zakładce **Radio** lub kanał w zakładce **Telewizja**.
2. Naciśnij **Nagraj radio** lub **Nagraj telewizję**. Nagrywanie może trwać w tle.
3. Panel **Nagrywanie** i powiadomienie pokazują czas, rozmiar i aktualny format pliku. W HLS czas sumuje zapisane fragmenty. Dla strumieni radia zapisywanych bezpośrednio czas jest mierzony od rozpoczęcia zapisu.
4. Naciśnij **Zakończ nagranie** w aplikacji albo w powiadomieniu, jeśli powiadomienia są włączone.
5. Plik znajdziesz w folderze **Pobrane / Pomocnik Alfa**.

Obsługiwane są bezpośrednie strumienie radia (MP3, AAC, Ogg, FLAC) i nieszyfrowane
transmisje HLS, w których obraz i dźwięk są razem w segmentach MPEG-TS lub fMP4.
Osadzone strony nadawców oraz HLS z oddzielnym obrazem i dźwiękiem, szyfrowaniem
lub segmentami wskazanymi zakresami bajtów są poza obsługą. Aplikacja nagrywa
jedną transmisję naraz.

## Stacje radiowe

- **Polska**: pobieranie stacji zarejestrowanych w Polsce, także tych działających wyłącznie przez internet, z publicznego katalogu Radio Browser.
- **Polskie za granicą**: osobna zakładka z rozgłośniami spoza Polski, których katalogowy język zawiera „polish” lub „polski”. Radio Znad Wilii (Wilno, Litwa) nie jest obecnie oferowane jako działający strumień, ponieważ wykorzystywany adres `https://plays.zw.lt/zw128` zwrócił 403 użytkownikowi, a alternatywny player Radijas.eu nie uruchomił transmisji. Adres jest pomijany do czasu potwierdzenia działającego źródła.
- **Inne kraje**: zachowane 10 niemieckich i 6 rosyjskich stacji z wcześniejszego wydania.
- **Wszystkie**: wspólna wyszukiwarka po nazwach i tagach.
- **Moje stacje, Ulubione, Dodaj własną stację**: dotychczasowe opcje nadal działają. Aktualizacja katalogu nie usuwa własnych stacji ani ulubionych.

Katalog pobierany jest stronicami z dwóch zapasowych serwerów API Radio Browser. Aktualizuje się przy uruchomieniu aplikacji, jeśli minęła doba, albo po naciśnięciu przycisku **Odśwież katalog**. Zawartość trafia do prywatnej pamięci telefonu. Przy braku połączenia aplikacja korzysta z ostatnio pobranego katalogu i z fabrycznej listy 221 stacji (205 PL, 10 DE, 6 RU).

Dla odtwarzacza zachowano wyłącznie strumienie HTTPS; źródła HTTP bez dostępnego odpowiednika HTTPS nie pojawią się na liście automatycznej.

**Ważne ograniczenie:** Radio Browser nie jest urzędowym spisem wszystkich koncesji i strumieni radiowych. Stacje internetowe powstają i znikają, metadane o języku mogą być niepełne, a niektóre transmisje są ograniczane geograficznie. Katalog jest szeroki i aktualizowalny, lecz **nie można zagwarantować obecności i działania absolutnie wszystkich rozgłośni**. Brakującą rozgłośnię można dodać ręcznie po podaniu adresu HTTPS transmisji.

## Kompatybilność i bezpieczeństwo

- Baza: **Pomocnik Alfa 17 TEST**, z poprawką otwierania ekranu Telewizja.
- Kompilacja: Java 8 / DEX, minSdk 29 (**Android 10 lub nowszy**).
- Wydanie PA18 ma odrębny identyfikator pakietu i tymczasowy podpis testowy; instaluje się obok wcześniejszych wersji, nie nadpisuje wersji stabilnej.
- Dane katalogu są pobierane w tle; przy błędzie nie nadpisują istniejącego zapisu.
- Nie wymagamy konta, klucza API ani płatnego serwera.

## Budowanie i testy

Workflow: `.github/workflows/pa2-alpha10-preview.yml`. Źródła: `helpers/pa2/PARadioCatalogActivity.java`, `helpers/pa2/PARadioDirectory.java`. Test kontraktowy: `python3 helpers/pa2/test_radio_directory.py`.

Plik APK, po pomyślnym przebiegu GitHub Actions, pojawi się jako artefakt **PA18-recording-progress-signed-apk**. Odtwarzanie poszczególnych transmisji wymaga sprawdzenia na rzeczywistym telefonie.
