# Audyt logiczności Planera PA 2.0 – 17 października 2026

## Zakres
Audyt uruchomiono na **rzeczywistym APK alpha.10**, pobranym z własnego artefaktu GitHub Actions, z bazą GTFS wygenerowaną 2026-10-17 (24 158 przystanków, 819 tras/relacji, 8 źródeł). Odtworzono silnik `assets/pa_regions/transit-core.js` na Node.js i przeszukano wskazane trasy na 2026-10-19, godz. 09:00. Nie zakładano, że czasy planowe są opóźnieniami LIVE.

## Potwierdzone błędy przed naprawą
1. **Duplikaty jednego pociągu**: przejścia 0–100 m do innych reprezentacji tego samego przystanku powodowały kilka identycznych pozycji. Warszawa Centralna→Kraków Główny: ten sam IC 5350/1 w kilku wynikach.
2. **Brak odrzucania tras zdominowanych**: A7 Ispina→Ispina Przedszkole, spacer z powrotem, A65 Ispina→Bochnia 10:15, pomimo bezpośredniego A65 09:29→10:15. Występowały też przejazdy poza cel i powrót pieszo.
3. **Przesiadki po kilkunastu godzinach**: Tarchomin→Nowodwory proponował przejazd 09:24→00:02 zamiast 4-minutowych tramwajów, gdy brakowało ograniczenia długości oczekiwania między dwoma przejazdami.
4. **Ucinanie listy wartościowych odjazdów**: najwyżej 5 etykiet na przystanek sortowanych według najwcześniejszego przyjazdu, więc późniejszy bezpośredni tramwaj mógł być wyparty przez egzotyczną trasę przez Warszawę.
5. **Brak czytelnego ostrzeżenia taryfowego** przy trasach przechodzących z transportu miejskiego na PKP Intercity/POLREGIO.

## Wprowadzone zmiany
- Ograniczenie oczekiwania na kolejny środek transportu do 120 minut (nie dotyczy oczekiwania na pierwszy kurs z przystanku).
- Budżet etykiet podniesiony z 5 do 12, dzięki czemu silnik uwzględnia więcej różniących się czasem odjazdów.
- Tożsamość wyniku definiują odcinki rzeczywistego przejazdu: źródło, ID kursu, dzień służbowy, godzina i przystanki wsiadania/wysiadania. Alternatywne ścieżki dojścia nie generują sztucznych kopii.
- Filtr dominacji Pareto: późniejszy start, nie późniejszy przyjazd, nie więcej przesiadek i nie więcej metrów pieszo mają pierwszeństwo.
- Filtr rażąco wydłużonych podróży oparty na najkrótszym znalezionym wariancie, z marginesem dla dłuższych podróży kolejowych.
- Gdy znaleziono częste kursy bezpośrednie, pomijane są znacznie dłuższe warianty z przesiadkami.
- Walidacja chronologii odcinków oraz odrzucanie wyników, których rozpoczęcie nastąpiłoby przed godziną żądaną przez użytkownika.
- Ostrzeżenie: bilet miejski nie musi obowiązywać w pociągach PKP IC/POLREGIO.

## Porównanie (początek podróży 2026-10-19, godz. 09:00)
| Relacja | Przed | Po |
|---|---|---|
| Ispina Most → Bochnia Dworzec PKP | 24 pozycje z wieloma objazdami | Bezpośrednie A65 09:29→10:15, 10:24→11:10, A4 11:14→12:10; alternatywy MLD/kolej tylko tam, gdzie nie są zdominowane |
| Warszawa Centralna → Kraków Główny | 24 pozycje, wielokrotne IC 5350/1 | 1 pozycja na faktyczny pociąg, późniejsze IC/Leo Express zachowane |
| Warszawa Wschodnia → Warszawa Centralna | 24, warianty dojścia po 13–70 m | Odrębne odjazdy 09:01, 09:05, 09:09, 09:13, 09:17 itd. |
| Tarchomin → Nowodwory | 24, w tym nocny objazd >14 h | 14 bezpośrednich połączeń na początku wyszukiwania (2, 17, 509); bez objazdów nocnych |
| Kraków Główny → Tarnów | Powielone ścieżki wokół stacji | Odrębne pociągi KMŁ/IC/POLREGIO, bez powielania dojścia w obrębie stacji |
| Białołęka-Ratusz → Dw. Centralny | Powielane dojścia i objazdy | Różne sensowne warianty m.in. 518→17, 509→160, 152→KM |

## Pozostałe ryzyka (nie zatajać)
- **Łączenie przystanków pod jedną nazwą**: w bazie wytypowano **45 zespołów** mających co najmniej dwa stanowiska oddalone od siebie o >400 m (np. Dw. Centralny, Metro Stadion Narodowy). Aktualna funkcja tworzenia zespołu akceptuje punkty do 800 m od pierwszego, przez co może abstrahować od czasu dojścia w rozległym zespole. Wymagana osobna poprawka z geometrią peronów / czytelnym wyborem stanowisk; nie należy obiecywać fizycznie możliwej przesiadki wyłącznie na tej podstawie.
- **Trasy piesze** są oszacowaniem z odległości w linii prostej; nie sprawdzają tuneli, mostów, torowisk, ogrodzeń ani dostępności dla osób z ograniczoną mobilnością.
- **Bilety i rezerwacje** nie są automatycznie weryfikowane, nawet jeśli GTFS zawiera kurs.
- **Połączenia LIVE** są niezależne od GTFS i nie powinny udawać opóźnienia rzeczywistego, gdy jest tylko plan.
- Filtr jakości jest heurystyczny: wyjątkowo długa, ale realnie jedyna przesiadka może nie zostać pokazana; należy rozważyć osobny tryb „pokaż także trudne połączenia” lub rozszerzone opcje, a nie traktować wyników jako pełnej wyszukiwarki krajowej.
- Wersja alpha.9 i osobna testowa alpha.10 mają inne podpisy/identyfikatory. Kolejne jednorazowe podpisy testowe nie zastępują poprawnie trwałego klucza aktualizacji.

## Miejsca zmian
- `helpers/pa2/transit-core.js`
- `helpers/pa2/test.cjs`
- `helpers/pa2/app.js`

Nowe poprawki trafiają na gałąź `work/pa2-ultrasmart-transport-20261017`, do PR #243.
