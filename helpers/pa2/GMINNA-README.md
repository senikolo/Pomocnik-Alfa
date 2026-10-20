# Linia gminna w Pomocniku Alfa 11

Źródło: zweryfikowany model \`planner/test-data/linia-gminna.json\` z projektu Ispina Lokalnie, wersja 2026-09-25-r3. Dane skopiowane bez ręcznego przepisywania godzin.

* Oba kierunki i rozkład od poniedziałku do soboty; w niedziele nie ma kursów w źródłowym zestawie.
* Przystanki Ispina Most, Ispina Dom Ludowy, Ispina Wiślisko.
* Rozkład stanowi część wspólnego indeksu PA 2.0 — Planer, tablice i asystent korzystają z tych samych kursów.
* Współrzędne pochodzą wyłącznie z GTFS MLD/Krakowa, bez wymyślania położeń. Przystanki bez identyfikowalnych współrzędnych nie są publikowane (lista w summary.json).
* Godziny \`officialAnchors\` są rozkładowe; inne są szacunkowe, nigdy jako GPS LIVE. Wypisuje się ostrzeżenie dla użytkownika.
* Godziny świąteczne: źródłowy zestaw nie podaje wyjątków; przed podróżą w dni świąteczne należy sprawdzić u przewoźnika. Brak dostępnych danych rzeczywistych (GTFS-RT).
* Import nie modyfikuje modułów radia, TV, notatek ani ustawień. Istniejące ulubione nie są kasowane.
* Stary plik IL jest datowany 2026-09-25, ale aktualność tych rozkładów w dniu instalacji nie jest gwarantowana. W źródłowym repo należy utrzymywać dane.

Testowanie: uruchomić \`python3 helpers/pa2/test-municipal.py\` i testy silnika na zbudowanym \`build/data\` oraz kontrolę instalacji API 30 i API 33.
