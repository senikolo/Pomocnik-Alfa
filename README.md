# Pomocnik Alfa 16 TEST: radio Polska i Polonia

Wersja testowa rozszerza sekcję **Radio**, zachowując telewizję i pozostałe funkcje PA15.

## Stacje radiowe

- **Polska**: pobieranie stacji zarejestrowanych w Polsce, także tych działających wyłącznie przez internet, z publicznego katalogu Radio Browser.
- **Polskie za granicą**: osobna zakładka z rozgłośniami spoza Polski, których katalogowy język zawiera „polish” lub „polski”.
- **Inne kraje**: zachowane 10 niemieckich i 6 rosyjskich stacji z wcześniejszego wydania.
- **Wszystkie**: wspólna wyszukiwarka po nazwach i tagach.
- **Moje stacje, Ulubione, Dodaj własną stację**: dotychczasowe opcje nadal działają. Aktualizacja katalogu nie usuwa własnych stacji ani ulubionych.

Katalog pobierany jest stronicami z dwóch zapasowych serwerów API Radio Browser. Aktualizuje się przy uruchomieniu aplikacji, jeśli minęła doba, albo po naciśnięciu przycisku **Odśwież katalog**. Zawartość trafia do prywatnej pamięci telefonu. Przy braku połączenia aplikacja korzysta z ostatnio pobranego katalogu i z fabrycznej listy 221 stacji (205 PL, 10 DE, 6 RU).

Dla odtwarzacza zachowano wyłącznie strumienie HTTPS; źródła HTTP bez dostępnego odpowiednika HTTPS nie pojawią się na liście automatycznej.

**Ważne ograniczenie:** Radio Browser nie jest urzędowym spisem wszystkich koncesji i strumieni radiowych. Stacje internetowe powstają i znikają, metadane o języku mogą być niepełne, a niektóre transmisje są ograniczane geograficznie. Katalog jest szeroki i aktualizowalny, lecz **nie można zagwarantować obecności i działania absolutnie wszystkich rozgłośni**. Brakującą rozgłośnię można dodać ręcznie po podaniu adresu HTTPS transmisji.

## Kompatybilność i bezpieczeństwo

- Baza: **Pomocnik Alfa 15**, wraz z telewizją TV Republika i wPolsce24.
- Kompilacja: Java 8 / DEX, minSdk 29 (**Android 10 lub nowszy**).
- Wydanie PA16 ma odrębny identyfikator pakietu i tymczasowy podpis testowy; instaluje się obok wcześniejszych wersji, nie nadpisuje wersji stabilnej.
- Dane katalogu są pobierane w tle; przy błędzie nie nadpisują istniejącego zapisu.
- Nie wymagamy konta, klucza API ani płatnego serwera.

## Budowanie i testy

Workflow: `.github/workflows/pa2-alpha10-preview.yml`. Źródła: `helpers/pa2/PARadioCatalogActivity.java`, `helpers/pa2/PARadioDirectory.java`. Test kontraktowy: `python3 helpers/pa2/test_radio_directory.py`.

Plik APK, po pomyślnym przebiegu GitHub Actions, pojawi się jako artefakt **PA16-radio-pl-polonia-isolated-signed-apk**. Odtwarzanie poszczególnych transmisji wymaga sprawdzenia na rzeczywistym telefonie.
