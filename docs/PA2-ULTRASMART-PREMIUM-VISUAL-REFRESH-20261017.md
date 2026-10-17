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
