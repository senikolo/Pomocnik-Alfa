# Pomocnik Alfa 2.1 Legacy – audyt Androida 6
Data: 18.10.2026
Urządzenie docelowe: Acer Iconia One 10, Android 6.0 (API 23), RAM 2 GB, LTE.
Repozytorium: senikolo/Pomocnik-Alfa
Gałąź: work/pa6-full-audit-ui-20261018

## Metoda i granice audytu
Przegląd źródeł helpers/pa6 (19+ modułów Java), porównanie z wcześniejszą gałęzią PA6 oraz historycznymi raportami testów API23, analiza logów emulatora, zrzutów ekranu i konfiguracji CI. Zautomatyzowane testy uruchamiają moduły PA6 w emulatorze, ale nie zastąpią testów na fizycznym Acerze. Nie wykonywano dwugodzinnego testu manualnego na fizycznym tablecie.

**Ważne:** nie należy utożsamiać poprawnej kompilacji oraz odtwarzania kontrolnego filmu H.264 z potwierdzeniem odtwarzania filmów z serwisu YouTube.

## Macierz problemów
| ID | Priorytet | Moduł | Stan przed audytem | Zmiana w PA6 Audyt | Walidacja |
|---|---|---|---|---|---|
| A01 | P0 | Alfa Tube | NewPipe zwracał SignInConfirmNotBotException, Piped 500/502, Invidious 401/403; YouTube wymaga potwierdzenia z chmurowego IP | Czytelniejsza diagnostyka, możliwość skopiowania wyniku, usunięcie niedziałającego WebView, timeout w silniku, historia dopiero po realnym odtworzeniu | **Nierozwiązane w pełni:** autoryzacja YouTube i dostępność wideo zależą od usługi i sieci |
| A02 | P1 | LibVLC | Łączenie mogło trwać bez końca | Watchdog 25 sekund, pojedyncze zgłoszenie błędu, obsługa starych prób, status prawdziwego odtwarzania | Wymaga potwierdzenia w regresji |
| A03 | P1 | Tube Kids | Korzysta z tego samego problematycznego silnika YouTube | Wspólna diagnostyka i watchdog; filtr zapytań kieruje do dziecięcych treści | To nie jest pełny filtr bezpieczeństwa dzieci |
| A04 | P2 | Pulpit | Telewizja, Alfa Tube, Radio występowały dwukrotnie | Jedne szybkie kafle + niepowielane multimedia; mniejsza zajętość ekranu; responsywny układ | UI do oceny na emulatorze |
| A05 | P2 | Radio | Dwie pozycje tej samej stacji Radio ZET 2000 | Usunięto duplikat HTTP, poprawiono wyszukiwanie z klawiatury | UI do testu |
| A06 | P1 | Notatnik | Użytkownik musiał pamiętać o zapisie przed niektórymi przejściami | Automatyczny cichy zapis podczas onPause, osobny komunikat po ręcznym zapisie | Wymaga smoke API23 |
| A07 | P1 | Pogoda | Domyślne zaufanie TLS starszego systemu mogło odrzucać nowe certyfikaty, wymuszone wyszukiwanie tylko w Polsce | Użycie istniejącego CA adaptera PA6, geokodowanie miast bez ograniczenia do PL, ograniczone czasy oczekiwania | Sieć oraz geokodowanie wymagają testu |
| A08 | P1 | Przypomnienia | Możliwy zapis alarmu jako sukces mimo błędu Harmonogramu; niespójność kalendarza i prezentowanej strefy | Planowanie przed zapisem, odrzucenie pozornej rejestracji, usuwanie przeterminowanych rekordów, strefa urządzenia | Zachowanie po restarcie i Doze wymaga testów fizycznych |
| A09 | P2 | Czytnik, Szumy, Narzędzia | Niespójne fioletowe style, różne przyciski i czcionki | Ujednolicenie PA6Ui: granat, jasne tło, obramowania i duże cele dotykowe | Wymaga smoke API23 |
| A10 | P1 | Testy | Poprzednio kontrolowano tylko część ekranów | Smoke uruchamia również Weather, Notes, Reminders, Reader; zapisuje diagnostykę ekstraktora | Weryfikacja workflow |
| A11 | P1 | Telewizja RU | Wcześniejsza wersja odbiera rosyjskojęzyczne kanały (potwierdzenie użytkownika) | Nienaruszony katalog oraz odtwarzacz, poza wspólną obsługą watchdog LibVLC | **Regresja musi być wykluczona na Acerze** |
| A12 | P2 | Podpisywanie | Eksperymentalne buildy podpisywane kluczami generowanymi na nowo i nie aktualizują się nawzajem | Oddzielna instalacja pod nową nazwą pakietu | Docelowo wymaga stabilnego klucza podpisywania w CI |
| A13 | P1 | Całość | Wersja Legacy nie ma automatycznie pełnej funkcjonalności wydania Android 11 | Zachowano moduły GAłęzi PA6; nie tworzymy pozornej kopii całego PA11 | Porównanie funkcja po funkcji i uzupełnianie braków to odrębny etap |
| A14 | P1 | Strumienie TV/radio | Listy URL mogą się zmieniać, część źródeł wymaga regionu | Zachowano zapasowe źródła TV, nie deklarujemy dostępności wszystkich łączeń | Sprawdzać per-stacja na fizycznym urządzeniu |

## Alfa Tube – protokół diagnozy
1. Uruchom PA6 Audyt i Alfa Tube.
2. Wyszukaj film i dotknij miniatury. Zwróć uwagę na prawdziwy stan odtwarzania, nie tylko miniaturę.
3. Jeśli po próbach źródeł film nie uruchomi się, wybierz „Kopiuj informacje o błędzie”. Wklej tekst do rozmowy.
4. Dla odróżnienia awarii LibVLC od awarii dostawcy, uruchom film kontrolny H.264. Działanie materiału kontrolnego oznacza tylko sprawny lokalny dekoder.

YouTube może wymagać logowania / PoToken / CAPTCHA i odmówić anonimowego pobierania. Nie obejdziemy gwarantowanie każdego ograniczenia i nie należy podszywać tego pod działające obejście. Legacy Android6 System WebView nie obsługuje współczesnych skryptów osadzania YouTube.

## Zasady wydania
- Sukces CI = kompilacja, instalacja, brak crashy w objętych testem ekranach, testy GTFS, odtwarzanie materiału kontrolnego.
- Sukces realnego Alfa Tube = film YouTube na fizycznym Acerze ma obraz oraz dźwięk, a UI poprawnie zgłasza brak dostępności.
- Każde zdanie typu „wszystko naprawione” byłoby niezgodne z dostępnymi dowodami.

## Lista dalszych prac
P0: realny odtwarzacz YouTube na konkretnej sieci Acera lub z legalnie działającym źródłem.
P1: manualny test wszystkich TV RU, radia w tle, budzenia przypomnienia po restarcie i dłuższym Doze, części transportowej z GTFS-RT.
P1: porównanie funkcjonalności Legacy 6 kontra referencyjny PA Android 11 (m.in. moduły niedostępne jeszcze na Legacy).
P2: stabilny certyfikat wydawcy, monitoring źródeł i testy wydajności CPU / temperatury / 2 GB RAM na Acerze.
