# PA 2.0 UltraSmart Premium — Visual Refresh (17.10.2026)

Zachowano istniejące moduły, pakiet testowy, oryginalną ikonę kota, zakładki Planer/Pojazdy/Alfa/Ulubione, natywne tablice odjazdów, GPS i Moje przystanki. Prace wykonano na gałęzi `work/pa2-ultrasmart-transport-20261017` PR #243.

## Ulepszenia
- **Premium**: granatowo-fioletowy nagłówek, spójna lawendowa paleta, delikatne obramowania i cienie, kontrastowy ciemny motyw, minimum 48 dp na interaktywne elementy.
- **Pulpit natywny**: zwarta sekcja „Najbliższe odjazdy”, oszczędne przełączanie 1–3 tablic, najbliższe kursy przed przyciskiem zapisu, dostępne ręczne i GPS-owe wybieranie przystanków.
- **Planer**: prostszy układ Skąd/Dokąd, duży CTA „Znajdź połączenia”; sortowanie, przesiadki, dojście i rodzaj transportu przeniesione do rozwijanych „Opcji podróży”; wszystkie oryginalne ID zachowano.
- **Tablica pełna**: lista odjazdów (godziny, kierunki, przewoźnicy) **przed** mapą; zaawansowane filtry pod rozwijanym panelem. Przejrzyste planowe przyjazdy/odjazdy i szczegóły źródeł.
- **Pojazdy**: jednolita hierarchia kart, większy obszar mapy, wyraźnie odróżnione odjazdy planowe i potwierdzone LIVE, bez generowania fikcyjnego GPS.
- **Alfa**: wyniki tras jako dwuwierszowe przyciski kursów z godziną, numerami linii, czasem i przesiadkami zamiast długich raportów tekstowych; dotknięcie nadal otwiera notatkę.
- **Nawigacja**: obecne cztery zakładki; usunięto widoczny zdublowany skrót do Pojazdów w Planerze (logika i funkcja nadal dostępne przez dolne menu).
- **Animacje i dostępność**: krótkie przejścia 180–250 ms, `prefers-reduced-motion`, większe strefy dotyku, senior font, zawijanie treści przy ekranie do 390 px.

## Bezpieczeństwo zmian
Jedyną bazą prawdy pozostają aktualne źródła GTFS/GTFS-RT. Nie zmieniano zasad potwierdzania LIVE/ETA ani uprawnień dostępu. Zachowano ważną zasadę: status LIVE i pozycja GPS wyświetlane wyłącznie przy aktualnym źródle.
Dodatkowo poprawiono synchronizację filtra przewoźnika na tablicy, aby zmiana widocznych operatorów nie dawała fałszywie pustej listy.

## Kontrola
Uruchamiany workflow `.github/workflows/pa2-alpha10-preview.yml`: logika tras, testy GTFS, regresje, DOM (w tym kolej, tablica, krytyczny wybór 134 Ostródzka), pełny Android build, podpis testowym kluczem, instrumentacja Android API 30 i screenshoty. Status końcowy sprawdzać w aktualnej akcji GitHub.
Paczka testowa ma odrębną nazwę pakietu `com.senikolo.pomocniknowy.pa2railtest`. Każdy run podpisywany jest **osobnym tymczasowym kluczem**; wymiana poprzedniej instalacji alpha.10 test może wymagać odinstalowania tamtej aplikacji. Alpha.9 pozostaje nienaruszona. Wersji próbnej nie wolno opisywać jako bezstratnej aktualizacji alpha.9.

## Celowo pozostawione ograniczenia
- Nie zmieniano oryginalnego natywnego ekranu powitalnego w niezweryfikowanych fragmentach smali. Zmniejszono pionowe marginesy w kontrolowanej sekcji tablic.
- Nie przepisano na nowo map OpenStreetMap ani algorytmu tras przy okazji samej poprawy wyglądu.
- Rozległe zespoły przystankowe nadal potrzebują dalszej kontroli przejść pieszych (osobny audyt Planera).


## Etap 2: pełny natywny pulpit Androida (17 października 2026)

W przeciwieństwie do pierwszego etapu, który dotyczył głównie WebView Planera i tablic, ten etap modernizuje oryginalny ekran `MainActivity`.

- Nowy `PADashboardPremium.java`: wspólna paleta i gradient nagłówka, kafelki o spójnych obramowaniach, typografia i subtelny efekt dotyku, zmniejszone odstępy.
- Zastąpienie mieszanych emoji kafelków jednym zestawem wektorowych rysunków konturowych, tworzonych natywnie na Androidzie: Planer, transport, Radio, TV, dzieci, przypomnienia, subskrypcje, czytnik, zakupy, notatki, lotniska, ustawienia itd.
- Oryginalne ikony Alfa Tube i Alfa Tube Kids oraz **kot w ikonie startowej aplikacji** zachowane. Wszystkie istniejące moduły i słuchacze kliknięć pozostały niezmienione.
- `nearby_patch.py` dokłada wyłącznie bezpieczny hook po już istniejącej inicjalizacji tablic GPS / Moich przystanków.
- Logika edycji i kolejności zapisanych przystanków działa nadal, bez zmian schematów i danych użytkownika.
- Po inspekcji zrzutu ekranu naprawiono regresję kontrastu: natywny nagłówek jest identyfikowany własnym tagiem, nie może zostać nadpisany jako zwykła biała karta informacyjna. Również dynamiczne powitanie (tekst zmieniający się po uruchomieniu) ma biały kolor na ciemnym fiolecie.
- Dodano testy instrumentowane sprawdzające co najmniej 10 kart Premium, zachowanie Radia, Notatek i Ustawień, **gradient nagłówka i kontrast powitania**.

**Walidacja:** `PAProbe.java.test` w workflow Android API 30 kontroluje instalację, oryginalną ikonę kota, przejścia do modułów, start Planera, GPS i zapisane przystanki, Radio/Notatki/Ustawienia, tryb ciemny, powiększoną czcionkę i odtworzenie GPS po restarcie. Weryfikowane są też rzeczywiste zrzuty ekranu. Plik APK jest oddzielną aplikacją testową, podpisywaną na nowo; nie aktualizuje alpha.9 w miejscu.
