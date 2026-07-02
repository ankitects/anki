### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    { $decks ->
        [one] używana przez { $decks } talię
        [few] używana przez { $decks } talie
        [many] używana przez { $decks } talii
       *[other] używana przez { $decks } talii
    }
deck-config-default-name = Domyślna
deck-config-title = Opcje talii

## Daily limits section

deck-config-daily-limits = Dzienne limity
deck-config-new-limit-tooltip =
    Maksymalna liczba nowych kart do pokazania na dzień.
    Jako, że nowy materiał zwiększy twój krótkoterminowy nakład pracy,
    powinien on być co najmniej 10 razy mniejszy niż ustawiony limit powtórek.
deck-config-review-limit-tooltip =
    Maksymalna dzienna liczba powtórek,
    jeśli istnieją karty do powtórki.
deck-config-limit-deck-v3 =
    Podczas nauki talii, która ma w sobie talie podrzędne, limit ustawiony na każdej
    talii podrzędnej kontroluje maksymalną liczbę kart pobieranych z tej konkretnej talii.
    Limity wybranej talii kontrolują całkowitą liczbę kart, które bedą pokazywane.
deck-config-limit-new-bound-by-reviews =
    Limit powtórek ma wpływ na limit nowych kart. Na przykład, jeśli twój limit powtórek 
    wynosi 200 i masz 190 oczekujących powtórek, zostanie pokazane maksymalnie 10 nowych kart.
    Jeśli twój limit powtórek został osiągnięty, nowe karty nie będą pokazywane.
deck-config-limit-interday-bound-by-reviews =
    Limit powtórek wpływa też na wielodniowe karty w nauce. Przy aplikowaniu limitu,
    wielodniowe karty w nauce są pobierane najpierw, przed kartami powtarzanymi.
deck-config-tab-description =
    - `Opcje`: Ten limit jest wspólny dla wszystkich talii używających tych opcji.
    - `Ta talia`: Ten limit dotyczy jedynie tej talii.
    - `Tylko dziś`: Tymczasowa zmiana limitu talii.
deck-config-new-cards-ignore-review-limit = Nowe karty nie są liczone do limitu powtórek
deck-config-new-cards-ignore-review-limit-tooltip = Domyślnie limit powtórek ma również wpływ na nowe karty i nie zostaną one pokazane, gdy limit powtórek został osiągnięty. Po włączeniu tej opcji nowe karty będą pokazywane bez względu na limit powtórek.
deck-config-apply-all-parent-limits = Limity liczone od najwyższego poziomu
deck-config-apply-all-parent-limits-tooltip =
    Domyślnie limity są liczone w wybranej talii. Włączając tę opcję, limity będą liczone
    od talii na najwyższym poziomie – co może być przydatne, jeśli chcesz uczyć się
    pojedynczych podtalii, ale z zachowaniem sumarycznego limitu kart na dzień.
deck-config-affects-entire-collection = Ma wpływ na całą kolekcję

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Opcje
deck-config-deck-only = Ta talia
deck-config-today-only = Tylko dziś

## New Cards section

deck-config-learning-steps = Kroki nauki
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Przerwy to zazwyczaj minuty (np. `1m`) lub dni (np. `2d`), ale można też używać godzin (np. `1h`) i sekund (np. `30s`).
deck-config-learning-steps-tooltip =
    Jedna lub więcej przerw , oddzielonych spacjami. Pierwsza przerwa będzie wykorzystana, gdy użyjesz odpowiedzi `Powtórz` na nowej karcie i domyślne wynosi 1 minutę.
    Odpowiedź `Dobra` przejdzie do następnego kroku, który domyślnie wynosi 10 minut.
    Gdy już wszystkie kroki zostana zaliczone karta stanie się kartą powtórkową i pojawi się innego dnia. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip =
    Liczba dni przed ponownym pokazaniem karty, gdy został naciśnięty przycisk `Dobra`
    w ostatnim kroku nauki.
deck-config-easy-interval-tooltip = Liczba dni zanim karta zostanie pokazana ponownie po naciśnięciu natychmiastowym przycisku `Łatwa`, aby usunąć kartę z "Uczonych".
deck-config-new-insertion-order = Kolejność wstawiania
deck-config-new-insertion-order-tooltip =
    Kontroluje pozycję (due #) przypisaną nowym kartom gdy je dodajesz.
    Karty z niższym numerem będą pokazywane w pierwszej kolejności podczas nauki. Zmiana
    tej opcji automatycznie zaktualizuje pozycję istniejących już nowych kart.
deck-config-new-insertion-order-sequential = Po kolei (najpierw najstarsze karty)
deck-config-new-insertion-order-random = Losowa
deck-config-new-insertion-order-random-with-v3 =
    Przy algorytmie planowania V3, lepiej wybrać "Po kolei"
    i dostosować zbieranie nowych kart.

## Lapses section

deck-config-relearning-steps = Kroki ponownej nauki
deck-config-relearning-steps-tooltip = Zero lub więcej przerw, oddzielonych spacjami. Domyślnie naciskając przycisk `Powtórz` na karcie powtórkowej, zostanie ona pokazana 10 minut później. Jeśli nie zostaną ustalone żadne przerwy, przerwa karty zostanie zmieniona, bez przechodzenia do fazy ponownej nauki. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip =
    Ile razy musisz użyć odpowiedzi `Powtórz` zanim powtarzana karta zostanie oznaczona jako pijawka. 
    Pijawki to karty, których  nauka zabiera bardzo dużo twojego czasu. Kiedy karta zostaje oznaczona jako pijawka dobrym pomysłem jest zmienić jej treść, usunąć ją lub pomyśleć nad mnemotechniką, która pomoże ci ją zapamiętać.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `Tylko tag`: Dodaj tag „leech” — oznaczający kartę często zapominaną
    i problematyczną — do notatki i wyświetl okienko z powiadomieniem.
    
    `Zawieś kartę`: Oprócz oznaczenia notatki tagiem, ukryj kartę do czasu jej ręcznego odblokowania.

## Burying section

deck-config-bury-title = Zakopywanie
deck-config-bury-new-siblings = Zakop nowe podobne do następnego dnia
deck-config-bury-review-siblings = Zakop przeglądane podobne do następnego dnia
deck-config-bury-interday-learning-siblings = Zakop podobne z wielodniowych w nauce
deck-config-bury-new-tooltip =
    Czy inne `nowe` karty tej samej notatki (np. odwrotne karty, sąsiadujące karty typu Luka)
    będą przekładane na następny dzień.
deck-config-bury-review-tooltip = Czy inne `powtórkowe` karty tej samej notatki będą przekładane na następny dzień.
deck-config-bury-interday-learning-tooltip =
    Czy inne `uczone` karty tej samej notatki o przerwie > 1 dzień
    będą przełożone na następny dzień.
deck-config-bury-priority-tooltip =
    Gdy Anki zbiera karty, zbierane są najpierw karty uczone w ten sam dzień, następnie wielodniowe karty w nauce, potem karty powtarzane, a na końcu nowe karty. Ma to wpływ na to, jak działa zakopywanie.
    
    - Jeśli wszystkie opcje zakopywania są włączone, zostanie pokazana karta podobna, która znajduje się najwcześniej na liście. Na przykład: Karta powtarzana będzie pokazana przed nową kartą.
    
    - Karty podobne znajdujące się dalej na liście nie mogą zakopać wcześniejszych typów kart. Na przykład: Jeśli wyłączysz zakopywanie nowych kart, a następnie będziesz uczył się nowej karty, nie zakopie ona nowych lub powtarzanych kart nauki wielodniowej i możesz zobaczyć zarówno kartę podobną powtarzaną jak i nową w tej samej sesji

## Gather order and sort order of cards

deck-config-ordering-title = Kolejność wyświetlania
deck-config-new-gather-priority = Kolejność zbierania nowych kart
deck-config-new-gather-priority-tooltip-2 =
    `Talia`: Karty są zbierane z każdej talii podrzędnej w kolejności, zaczynając od góry. Karty z każdej talii podrzędnej są zbierane w rosnącej pozycji. Jeśli dzienny limit wybranej talii został osiągnięty, zbieranie kart może zakończyć się przed sprawdzeniem wszystkich talii podrzędnych. Ta kolejność jest najszybsza w przypadku dużych kolekcji i pozwala prioretyzować talie podrzędne, które znajdują się na górze.
    
    `Pozycja rosnąco`: Karty są zbierane w pozycji rosnącej (due #), co  oznacza zazwyczaj, że najpierw zbierane są karty dodane najdawniej.
    
    `Pozycja malejąco`: Karty są zbierane w pozycji malejącej (due #), co oznacza zazwyczaj, że najpierw zbierane są karty dodane najpóźniej.
    
    `Losowe notatki`: Notatki są wybierane losowo, a następnie zbierane są wszystkie ich karty.
    
    `Losowe karty`: Karty są zbierane w losowej kolejności.
deck-config-new-card-sort-order = Kolejność nowych kart
deck-config-new-card-sort-order-tooltip-2 =
    `Typ karty`: Karty są pokazywane według numeru typu karty.
    Karty każdego numeru typu karty są pokazywane w kolejności, w jakiej zostały zebrane.
    Jeśli opcja zakopywanie podobnych jest wyłączona, zapewni to, że wszystkie karty przód→tył będą pokazane przed kartami tył→przód.
    Jest to przydatne, aby pokazane zostały wszystkie karty tej samej notatki w tej samej sesji, ale nie zbyt blisko siebie.
    
    `Kolejność zebrania`: Karty są pokazywane dokładnie w kolejności, w jakiej zostały zebrane. Jeśli opcja zakopywana podobnych jest wyłączona, skutkuje to zazwyczaj pokazaniem wszystkich kart jednej notatki po sobie.
    
    `Typ karty, następnie losowo`: Karty są pokazywane według numeru typu karty. Karty każdego numeru typu karty są wyświetlane w losowej kolejności. Ta kolejność jest przydatna aby uniknąć pokazywania kart podobnych zbyt blisko siebie. Będą one jednak dalej pokazywane w losowej kolejności.
    
    `Losowa notatka, następnie typ karty`: Notatki są wybierane losowo, a następnie wszystkie ich karty wyświetlane są w kolejności.
    
    `Losowo`: Karty są pokazywane w losowej kolejności.
deck-config-new-review-priority = Kolejność nowych/powtórek
deck-config-new-review-priority-tooltip = Kiedy pokazywać nowe karty względem kart powtarzanych.
deck-config-interday-step-priority = Kolejność nauki/powtórek wielodniowych
deck-config-interday-step-priority-tooltip =
    Kiedy pokazywać (ponownie) uczone karty, których przerwa przekroczyła granicę dnia.
    
    Limit powtórek ma zawsze zastosowanie najpierw do wielodniowych kart w nauce, a następnie powtórek. Ta opcja kontroluje kolejność pokazywania zebranych kart, jednak wielodniowe karty w nauce zawsze bedą miały pierwszeństwo.
deck-config-review-sort-order = Kolejność przeglądania
deck-config-review-sort-order-tooltip =
    Domyślna kolejność prioretyzuje karty, które oczekiwały najdłużej, więc jeśli
    masz zaległe powtórki, najpierw pojawią się te, które oczekiwały najdłużej.
    Jeśli masz duże zaległości, których nadrobienie zajmie więcej niż kilka dni lub chcesz widzieć karty w kolejności talii podrzędnej, alternatywne tryby sortowania mogą być dla ciebie odpowiednie.
deck-config-display-order-will-use-current-deck = Anki wykorzysta kolejność wyświetlania  z talii, której będziesz się uczył, zamiast z którejś z jej podtalii.

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = Talia
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = Talia, potem losowe notatki
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = Pozycja rosnąco
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = Pozycja malejąco
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = Losowe notatki
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = Losowe karty
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = Typ karty, następnie losowo
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = Losowa notatka, następnie typ karty
# Sort the cards randomly.
deck-config-sort-order-random = Losowo
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = Typ karty
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = Kolejność zebrania
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = Mieszaj z powtórkami
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = Pokaż po powtórkach
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = Pokaż przed powtórkami
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = Zaplanowana data, potem losowa
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = Zaplanowana data, potem talia
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = Talia, potem zaplanowana data
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = Po rosnącej przerwie
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = Po malejącej przerwie
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = Po rosnącej łatwości
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = Po malejącej łatwości
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = Trudność rosnąco
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = Trudność malejąco
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = Rosnąca przywoływalność
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = Malejąca przywoływalność

## Timer section

deck-config-timer-title = Czasomierz
deck-config-maximum-answer-secs = Maksymalnie sekund na odpowiedź
deck-config-maximum-answer-secs-tooltip = Maksymalny czas, który będzie ujmowany w statystykach dla pojedynczej powtórki. Jeśli odpowiedź przekroczy ten czas (ponieważ na przykład oddaliłeś się od ekranu), czas poświęcony na odpowiedź będzie czasem z ustawionego przez ciebie limitu.
deck-config-show-answer-timer-tooltip = Pokaż w trybie przeglądania stoper, który pokazuje, ile sekund zajmuje ci przejrzenie danej karty.
deck-config-stop-timer-on-answer = Zatrzymaj czasomierz po odpowiedzi
deck-config-stop-timer-on-answer-tooltip =
    Czy zatrzymać czasomierz po ujawnieniu odpowiedzi.
    Opcja nie ma wpływu na statystyki.

## Auto Advance section

deck-config-seconds-to-show-question = Czas pokazywania pytania
deck-config-seconds-to-show-question-tooltip-3 = Przy włączonym auto-postępie, liczba sekund zanim zostanie wykonane działanie pytania. Ustaw 0 by wyłączyć.
deck-config-seconds-to-show-answer = Czas pokazywania odpowiedzi
deck-config-seconds-to-show-answer-tooltip-2 = Przy włączonym auto-postępie, liczba sekund zanim zostanie wykonana reakcja odpowiedzi. Ustaw 0 by wyłączyć.
deck-config-question-action-show-answer = Pokaż odpowiedź
deck-config-question-action-show-reminder = Pokaż przypomnienie
deck-config-question-action = Działanie pytania
deck-config-question-action-tool-tip = Działanie wykonywane, gdy pytanie jest pokazane i minął czas na odpowiedź
deck-config-answer-action = Reakcja odpowiedzi
deck-config-answer-action-tooltip-2 = Reakcja, która zostanie podjęta po pokazaniu odpowiedzi i upłynięciu limitu czasowego.
deck-config-wait-for-audio-tooltip-2 = Czekaj, aż skończy się dźwięk przed automatycznym zastosowaniem działania odpowiedzi lub reakcji odpowiedzi.

## Audio section

deck-config-audio-title = Dźwięk
deck-config-disable-autoplay = Nie odtwarzaj automatycznie dźwięku
deck-config-disable-autoplay-tooltip =
    Powoduje, że Anki nie będzie odtwarzać dźwięku automatycznie.
    Dźwięk będzie można odtworzyć przez kliknięcie na ikonę dźwięku albo wybranie opcji "Odtworzenie dźwięku".
deck-config-skip-question-when-replaying = Pomiń pytanie przy ponownym odtwarzaniu odpowiedzi
deck-config-always-include-question-audio-tooltip =
    Czy dźwięk pytania będzie dołączony, gdy działanie Odtworzenie dźwięku jest
    użyte przy oglądaniu strony odpowiedzi w karcie.

## Advanced section

deck-config-advanced-title = Zaawansowane
deck-config-maximum-interval-tooltip = Maksymalna liczba dni, ile powtarzana karta będzie czekać na kolejną powtórkę. Gdy zostanie osiągnięty  limit powtórek, `Trudna`, `Dobra` i `Łatwa` będą miały taką samą przerwę. Im mniejsza wartość, tym większe obciążenie powtórkami.
deck-config-starting-ease-tooltip = Współczynnik łatwości nowych kart. Domyślnie wciśnięcie `Dobra` na świeżo nauczonej karcie odłoży w czasie następną powtórkę o 2,5 raza dłużej, niż wynosiła wcześniejsza przerwa.
deck-config-easy-bonus-tooltip = Dodatkowy mnożnik, który modyfikuje przerwę karty powtórkowej, gdy ocenisz ją jako `Łatwa`.
deck-config-interval-modifier-tooltip = Ten mnożnik jest stosowany przy wszystkich powtórkach. Delikatne zmiany mogą być wykorzystane, aby sprawić, żeby Anki planowało powtórki bardziej agresywnie lub zachowawczo. Przeczytaj poradnik przed zmianą tego ustawienia.
deck-config-hard-interval-tooltip = Mnożnik stosowany do przerwy karty przy odpowiedzi `Trudna`.
deck-config-new-interval-tooltip = Mnożnik stosowany do przerwy karty przy odpowiedzi `Powtórz`.
deck-config-minimum-interval-tooltip = Minimalna przerwa przypisywana karcie powtórkowej przy odpowiedzi `Powtórz`.
deck-config-custom-scheduling = Własne planowanie
deck-config-custom-scheduling-tooltip = Ma wpływ na całą kolekcję. Używasz na własne ryzyko!

## Easy Days section.

deck-config-easy-days-title = Lżejsze dni
deck-config-easy-days-monday = Pon.
deck-config-easy-days-tuesday = Wt.
deck-config-easy-days-wednesday = Śr.
deck-config-easy-days-thursday = Czw.
deck-config-easy-days-friday = Pt.
deck-config-easy-days-saturday = Sob.
deck-config-easy-days-sunday = Nd.
deck-config-easy-days-normal = Normalnie
deck-config-easy-days-reduced = Mniej
deck-config-easy-days-minimum = Minimalnie
deck-config-easy-days-no-normal-days = Przynajmniej jeden dzień powinień być ustawiony na '{ deck-config-easy-days-normal }'.
deck-config-easy-days-change = Dopóki '{ deck-config-reschedule-cards-on-change }' jest włączona w opcjach FSRS, istniejące powtórki nie będą ponownie planowane.

## Adding/renaming

deck-config-add-group = Dodaj opcje
deck-config-name-prompt = Nazwa
deck-config-rename-group = Zmień nazwę opcji
deck-config-clone-group = Klonuj opcje

## Removing

deck-config-remove-group = Usuń opcje
deck-config-will-require-full-sync =
    Ta zmiana wymaga synchronizacji w jedną stronę. Jeśli masz zmiany
    na innym urządzeniu, które nie były synchronizowane z obecnym urządzeniem,
    uruchom teraz tam synchronizację.
deck-config-confirm-remove-name = Usunąć { $name }?

## Other Buttons

deck-config-save-button = Zapisz
deck-config-save-to-all-subdecks = Zapisz do wszystkich podtalii
deck-config-save-and-optimize = Optymalizuj wszystkie opcje FSRS
deck-config-revert-button-tooltip = Przywróć to ustawienie do domyślnej wartości

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Obsługa Anki 2.1.41+
deck-config-description-new-handling-hint =
    Traktuje dane wejściowe jako markdown i usuwa dane wejściowe HTML. Jeśli opcja jest włączona, opis będzie także wyświetlany na ekranie z gratulacjami.
    Markdown pojawia się jako tekst w wersjach Anki 2.1.40 i starszych.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    { $cards ->
        [one] Talia nadrzędna ma limit { $cards } karty, który nadpisze ten limit.
        [few] Talia nadrzędna ma limit { $cards } kart, który nadpisze ten limit.
       *[many] Talia nadrzędna ma limit { $cards } kart, który nadpisze ten limit.
    }
deck-config-reviews-too-low =
    { $cards ->
        [one] Jeśli dodajesz { $cards } kartę dziennie, twój limit powinien wynosić przynajmniej { $expected }.
        [few] Jeśli dodajesz { $cards } karty dziennie, twój limit powinien wynosić przynajmniej { $expected }.
        [many] Jeśli dodajesz { $cards } kart dziennie, twój limit powinien wynosić przynajmniej { $expected }.
       *[other] Jeśli dodajesz { $cards } kart dziennie, twój limit powinien wynosić przynajmniej { $expected }.
    }
deck-config-learning-step-above-graduating-interval = Przerwa dla kart po nauce powinna być przynajmniej tak długa jak ostatni z kroków nauki.
deck-config-good-above-easy = Przerwa dla łatwych powinna być przynajmniej tak długa jak przerwa dla kart po nauce.
deck-config-relearning-steps-above-minimum-interval = Minimalna przerwa pomyłki powinna być przynajmniej długa jak ostatni krok ponownej nauki.
deck-config-maximum-answer-secs-above-recommended = Anki będzie skutecznie planować powtórki tylko pod warunkiem, że będziesz ustawiać krótkie pytania.
deck-config-too-short-maximum-interval = Nie zaleca się stosowania maksymalnego okresu krótszego niż 6 miesięcy.
deck-config-ignore-before-info =
    { $included ->
        [one] (Około) { $included }/{ $totalCards } karta zostanie użyta do optymalizacji ustawień FSRS.
        [few] (Około) { $included }/{ $totalCards } karty zostaną użyte do optymalizacji ustawień FSRS.
        [many] (Około) { $included }/{ $totalCards } kart zostanie użytych do optymalizacji ustawień FSRS.
       *[other] (Około) { $included }/{ $totalCards } kart zostanie użytych do optymalizacji ustawień FSRS.
    }

## Selecting a deck

deck-config-which-deck = Dla której talii chcesz wyświetlić opcje?

## Messages related to the FSRS scheduler

deck-config-updating-cards = Aktualizowanie kart: { $current_cards_count }/{ $total_cards_count }...
deck-config-invalid-parameters = Podane parametry FSRS są nieprawidłowe. Pozostaw je puste, aby użyć domyślnych parametrów.
deck-config-not-enough-history = Historia powtórek jest niewystarczająca do przeprowadzenia tej operacji.
deck-config-must-have-400-reviews =
    { $count ->
        [one] Znaleziono tylko { $count } powtórkę. Musisz mieć co najmniej 400 powtórek aby przeprowadzić tę operację.
        [few] Znaleziono tylko { $count } powtórki. Musisz mieć co najmniej 400 powtórek aby przeprowadzić tę operację.
       *[many] Znaleziono tylko { $count } powtórek. Musisz mieć co najmniej 400 powtórek aby przeprowadzić tę operację.
    }
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = Parametry FSRS
deck-config-compute-optimal-weights = Optymalizuj parametry FSRS
deck-config-optimize-button = Optymalizuj
# Indicates that a given function or label, provided via the "text" variable, operates slowly.
deck-config-slow-suffix = { $text } (działa wolno)
deck-config-compute-button = Wylicz
deck-config-ignore-before = Ignoruj powtórki przed
deck-config-time-to-optimize = Minęło już trochę czasu - zaleca się użycie przycisku "Optymalizuj wszystkie opcje FSRS"
deck-config-evaluate-button = Oceń
deck-config-desired-retention = Pożądane zapamiętywanie
deck-config-historical-retention = Historyczne zapamiętywanie
deck-config-smaller-is-better = Mniejsze liczby oznaczają lepsze dopasowanie do twojej historii powtórek.
deck-config-steps-too-large-for-fsrs = Gdy FSRS jest włączony nie jest rekomendowanie kroków w ilości 1 dnia lub więcej.
deck-config-get-params = Zdobądź parametry
deck-config-complete = Ukończono { $num }%.
deck-config-iterations = Iteracja: { $count }...
deck-config-reschedule-cards-on-change = Ponownie zaplanuj przy zmianie
deck-config-fsrs-tooltip =
    Wpływa na całą kolekcję
    
    FSRS (Free Spaced Repetition Scheduler) to alternatywa dla starszego algorytmu używanego przez Anki - SuperMemo 2 (SM-2) .
    
    Może pomóc zapamiętywać więcej materiału w tym samym czasie poprzez dokładniejszą ocenę jaka jest szansa zapomnienia karty. To ustawienie jest wspólne dla wszystkich opcji.
deck-config-desired-retention-tooltip = Domyślna wartość 0.9 planuje karty w taki sposób, że masz 90% szans na pamiętanie ich, gdy pojawią się ponownie. Jeśli zwiększysz tę wartość, Anki będzie pokazywać karty częściej, aby zwiększyć szansę pamiętania ich. Jeśli ją zmniejszysz, Anki będzie pokazywać karty rzadziej, a ty będziesz zapominał więcej z nich. Bądź ostrożny przy ustawianiu tej wartości - większe wartości poważnie zwiększą liczbę powtórek, a mniejsze wartości mogą demotywować przez zapominanie dużej ilości materiału.
deck-config-desired-retention-tooltip2 = Podane wartości obciążenia są przybliżone. Aby uzyskać większą dokładność, skorzystaj z symulatora.
deck-config-historical-retention-tooltip =
    Gdy brakuje części twojej historii powtórek, FSRS musi wypełnić luki. Domyślnie algorytm założy, że gdy przeglądałeś te stare powtórki, pamiętałeś 90% materiału. Jeśli twoje stare zapamiętywanie było znacznie wyższe lub niższe niż 90%, dostosowanie tej opcji pozwoli FSRS lepiej oszacować brakujące powtórki.
    
    Twoja historia powtórek może być niekompletna z dwóch powodów:
    1. Ponieważ używasz opcji "ignoruj powtórki przed...".
    2. Ponieważ usunąłeś wcześniej dzienniki powtórek, aby uwolnić miejsce lub zaimportowałeś materiał z innego programu SRS.
    
    To drugie jest dość rzadkie, więc o ile nie ma do Ciebie zastosowania punkt pierwszy, nie musisz ustawiać tej opcji
deck-config-weights-tooltip2 = Parametry FSRS wpływają na to, jak planowane są karty. Anki zaczyna ze standardowymi parametrami. Możesz użyć opcji poniżej, aby zoptymalizować parametry w stosunku do tego, jak dobrze radzisz sobie w taliach używających tej opcji.
deck-config-reschedule-cards-on-change-tooltip =
    Dotyczy całej kolekcji i nie jest zapisywane.
    
    Ta opcja kontroluje czy data, kiedy karty mają zostać pokazane zostanie zmieniona po włączeniu FSRS lub 
    czy nastąpi tylko optymalizacja parametrów. Domyślnie ustawiona opcja to brak zmian: przyszłe powtórki beda używały planowania FSRS, ale nie będzie natychmiastowej zmiany w dziennym obciążeniu powtórkami. Jeśli zostanie włączona opcja ponownego planowania, data kiedy karty zostaną pokazane zostanie zmieniona.
deck-config-reschedule-cards-warning =
    W zależności od twojego oczekiwanego zapamiętywania, może skutkować większą ilością kart do powtórki, więc nie jest zalecane podczas pierwszej zmiany z algorytmu SM-2.
    
    Używaj tej opcji oszczędnie, ponieważ doda ona nowy wpis powtórki do każdej z twoich kart i powiększy rozmiar twojej kolekcji
deck-config-ignore-before-tooltip-2 =
    Jeśli zostanie ustawione, karty powtórzone przed podaną datą bedą ignorowane podczas optymalizacji parametrów FSRS.
    Może być przydatne, jeśli zaimportowałeś dane planowania innej osoby lub zmieniłeś sposób korzystania z przycisków odpowiedzi.
deck-config-compute-optimal-weights-tooltip2 =
    Kiedy klikniesz przycisk Optymalizuj, FSRS przeanalizuje twoją historię powtórek i wygeneruje parametry, które będą optymalne dla twojej pamięci i materiału, którego się uczysz. Jeśli twoim zdaniem, talie, których się uczysz wyraźnie różnią się  poziomem trudności,  zalecane jest nadanie im osobnych opcji, jako że parametry dla łatwych i trudnych talii będą inne. 
    Nie musisz często optymalizować parametrów - wystarczy raz na kilka miesięcy.
    
    Domyślnie  parametry beda obliczane z historii powtórek wszystkich talii używających obecnych opcji. Możesz opcjonalnie dostosować wyszukiwanie przed obliczeniem parametrów, jeśli chciałbyś zmienić, które karty są używane do optymalizowania parametrów.
deck-config-please-save-your-changes-first = Najpierw zapisz dokonane zmiany.
deck-config-workload-factor-change =
    Przybliżone obciążenie: { $factor }x
    (w porównaniu z { $previousDR }% docelowego poziomu zapamiętania)
deck-config-workload-factor-unchanged = Im wyższa wartość, tym częściej karta będzie się pojawiać.
deck-config-desired-retention-too-low =
    Docelowy poziom zapamiętania jest bardzo niski. Odstępy w wyświetlaniu
    mogą być wydłużone.
deck-config-desired-retention-too-high =
    Docelowy poziom zapamiętania jest bardzo wysoki. Odstępy w wyświetlaniu
    mogą być bardzo krótkie.
deck-config-percent-of-reviews =
    { $reviews ->
        [one] { $pct }% z { $reviews } powtórki
        [few] { $pct }% z { $reviews } powtórek
        [many] { $pct }% z { $reviews } powtórek
       *[other] { $pct }% z { $reviews } powtórek
    }
deck-config-percent-input = { $pct }%
# This message appears during FSRS parameter optimization.
deck-config-checking-for-improvement = Sprawdzanie postępów...
deck-config-optimizing-preset = Optymalizowanie opcji { $current_count }/{ $total_count }...
deck-config-fsrs-must-be-enabled = Musisz najpierw włączyć FSRS.
deck-config-fsrs-params-optimal = Parametry FSRS wyglądają obecnie na optymalne.
deck-config-fsrs-params-no-reviews = Nie znaleziono powtórek. Sprawdź, czy ta opcja FSRS jest przypisana do wszystkich talii, które chcesz zoptymalizować (wliczając w to podtalie) i spróbuj ponownie.
deck-config-wait-for-audio = Czekaj na dźwięk
deck-config-show-reminder = Pokaż przypomnienie
deck-config-answer-again = Odpowiedz Powtórz
deck-config-answer-hard = Odpowiedz Trudna
deck-config-answer-good = Odpowiedz Dobra
deck-config-days-to-simulate = Dni do zasymulowania
deck-config-desired-retention-below-optimal = Twoje pożądane zapamiętywanie jest poniżej optymalnego. Zaleca się jego zwiększenie.
# Description of the y axis in the FSRS simulation
# diagram (Deck options -> FSRS) showing the total number of
# cards that can be recalled or retrieved on a specific date.
deck-config-fsrs-simulator-experimental = Symulator FSRS (eksperymentalny)
deck-config-fsrs-simulate-desired-retention-experimental = Symulator pożądanego zapamiętywania FSRS (eksperymentalne)
deck-config-fsrs-simulate-save-preset = Po optymalizacji zapisz talię zanim uruchomisz symulator.
deck-config-fsrs-desired-retention-help-me-decide-experimental = Pomóż mi zdecydować (eksperymentalne)
deck-config-additional-new-cards-to-simulate = Dodatkowe nowe karty do symulacji
deck-config-simulate = Symulacja
deck-config-clear-last-simulate = Wyczyść ostatnią symulację
deck-config-fsrs-simulator-radio-count = Powtórki
deck-config-advanced-settings = Ustawienia zaawansowane
deck-config-smooth-graph = Wygładzony wykres
deck-config-suspend-leeches = Zawieś fiszki trudne do zapamiętania
deck-config-save-options-to-preset = Zapisz zmiany w opcjach
deck-config-save-options-to-preset-confirm = Nadpisać ustawienia w obecnych opcjach zmianami ustawionymi obecnie w symulatorze?
# Radio button in the FSRS simulation diagram (Deck options -> FSRS) selecting
# to show the total number of cards that can be recalled or retrieved on a
# specific date.
deck-config-fsrs-simulator-radio-memorized = Zapamiętane
deck-config-fsrs-simulator-radio-ratio = Stosunek czasu do zapamiętania
# $time here is pre-formatted e.g. "10 Seconds" 
deck-config-fsrs-simulator-ratio-tooltip = { $time } na jedną zapamiętaną kartę

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.

# Checkbox
deck-config-health-check = Sprawdź kondycję podczas optymalizacji
# Message box showing the result of the health check
deck-config-fsrs-bad-fit-warning =
    Sprawdzenie kondycji:
    Twoja pamięć jest trudna do przewidzenia przez FSRS. Zalecenia:
    
    - Zawieś lub zmodyfikuj fiszki trudne do zapamiętania.
    - Używaj spójnie przycisków odpowiedzi. Pamiętaj, że „Trudna” to ocena dopuszczająca, nie negatywna.
    - Staraj się rozumieć materiał, zanim go zapamiętasz.
    
    Jeśli zastosujesz się do tych wskazówek, Twoje wyniki zwykle poprawią się w ciągu kilku miesięcy.
# Message box showing the result of the health check
deck-config-fsrs-good-fit =
    Sprawdzenie kondycji:
    FSRS dobrze dostosowuje się do Twojej pamięci.

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-unable-to-determine-desired-retention = Nie można ustalić minimalnego zalecanego zapamiętywania.
deck-config-predicted-minimum-recommended-retention = Minimalne rekomendowane zapamiętywanie: { $num }
deck-config-compute-minimum-recommended-retention = Minimalne rekomendowane zapamiętywanie
deck-config-compute-optimal-retention-tooltip4 =
    To narzędzie spróbuje znaleźć optymalną wartość zapamiętywania,
    który doprowadzi do nauki największej ilości materiału w jak najkrótszym czasie. Obliczona wartość może służyć jako odniesienie podczas decyzji przy ustalaniu oczekiwanej wartości zapamiętywania. Możesz ustawić wyższe oczekiwane zapamiętywanie, jeśli jesteś chętny poświęcić więcej czasu, aby je osiągnąć. Ustawienie oczekiwanego zapamiętywania niżej niż wartość minimalna nie jest zalecane, ponieważ doprowadzi to do wyższego obciążenia z powodu dużej wartości zapominania.
deck-config-plotted-on-x-axis = (Narysowane na osi X)
deck-config-a-100-day-interval =
    { $days ->
        [one] Stu-dniowa przerwa zmieni się w { $days } dzień.
        [few] Stu-dniowa przerwa zmieni się w { $days } dni.
        [other] Stu-dniowa przerwa zmieni się w { $days } dni.
       *[many] Stu-dniowa przerwa zmieni się w { $days } dni.
    }
deck-config-fsrs-simulator-y-axis-title-time = .
deck-config-fsrs-simulator-y-axis-title-count = .
deck-config-fsrs-simulator-y-axis-title-memorized = .
deck-config-bury-siblings = Zakop podobne
deck-config-do-not-bury = Nie zakopuj podobnych
deck-config-bury-if-new = Zakop nowe
deck-config-bury-if-new-or-review = Zakop nowe i powtarzane
deck-config-bury-if-new-review-or-interday = Zakop nowe, powtarzane i powtarzane między dniami
deck-config-bury-tooltip =
    Siblings are other cards from the same note (eg forward/reverse cards, or
    other cloze deletions from the same text).
    
    When this option is off, multiple cards from the same note may be seen on the same
    day. When enabled, Anki will automatically *bury* siblings, hiding them until the next
    day. This option allows you to choose which kinds of cards may be buried when you answer
    one of their siblings.
    
    When using the V3 scheduler, interday learning cards can also be buried. Interday
    learning cards are cards with a current learning step of one or more days.
deck-config-seconds-to-show-question-tooltip = Przy włączonym auto-postępie, liczba sekund zanim zostanie pokazana odpowiedź. Ustaw 0, by wyłączyć.
deck-config-answer-action-tooltip = Działanie wykonane na aktualnej karcie przed automatycznym przejściem do następnej.
deck-config-wait-for-audio-tooltip = Czeka, aż dźwięk się skończy przed automatycznym pokazaniem odpowiedzi lub następnego pytania.
deck-config-ignore-before-tooltip =
    If set, reviews before the provided date will be ignored when optimizing & evaluating FSRS parameters.
    This can be useful if you imported someone else's scheduling data, or have changed the way you use the answer buttons.
deck-config-compute-optimal-retention-tooltip =
    This tool assumes you're starting with 0 cards, and will attempt to calculate the amount of material you'll
    be able to retain in the given time frame. The estimated retention will greatly depend on your inputs, and
    if it significantly differs from 0.9, it's a sign that the time you've allocated each day is either too low
    or too high for the amount of cards you're trying to learn. This number can be useful as a reference, but it
    is not recommended to copy it into the desired retention field.
deck-config-health-check-tooltip1 = Jeśli FSRS będzie miało trudności aby dostosować się do Twojej pamięci, pojawi się ostrzeżenie
deck-config-health-check-tooltip2 = Sprawdzenie kondycji ma miejsce tylko, gdy włączona jest opcja "Optymalizuj wszystkie opcje FSRS"
deck-config-compute-optimal-retention = Compute minimum recommended retention
deck-config-predicted-optimal-retention = Minimalny rekomendowany wskaźnik zapamiętywania: { $num }
deck-config-weights-tooltip = Parametry FSRS wpływają na to, jak planowane są karty. Anki zaczyna ze standardowymi parametrami. Gdy osiągniesz 1000+ powtórek, możesz użyć opcji poniżej, aby zoptymalizować parametry w stosunku do tego, jak dobrze radzisz sobie w taliach używających tej opcji.
deck-config-compute-optimal-weights-tooltip =
    Once you've done 1000+ reviews in Anki, you can use the Optimize button to analyze your review history,
    and automatically generate parameters that are optimal for your memory and the content you're studying.
    If you have decks that vary wildly in difficulty, it is recommended to assign them separate presets, as
    the parameters for easy decks and hard decks will be different. There is no need to optimize your parameters
    frequently - once every few months is sufficient.
    
    By default, parameters will be calculated from the review history of all decks using the current preset. You can
    optionally adjust the search before calculating the parameters, if you'd like to alter which cards are used for
    optimizing the parameters.
deck-config-compute-optimal-retention-tooltip2 =
    This tool assumes that you’re starting with 0 learned cards, and will attempt to find the desired retention
    value that will lead to the most material learnt, in the least amount of time. This number can be used as a
    reference when deciding what to set your desired retention to. You may wish to choose a higher desired retention,
    if you’re willing to trade more study time for a greater recall rate. Setting your desired retention lower than
    the minimum is not recommended, as it will lead to more work without benefit.
deck-config-compute-optimal-retention-tooltip3 =
    This tool assumes that you’re starting with 0 learned cards, and will attempt to find the desired retention value 
    that will lead to the most material learnt, in the least amount of time. To accurately simulate your learning process, 
    this feature requires a minimum of 400+ reviews. The calculated number can serve as a reference when deciding what to 
    set your desired retention to. You may wish to choose a higher desired retention, if you’re willing to trade more study 
    time for a greater recall rate. Setting your desired retention lower than the minimum is not recommended, as it will 
    lead to a higher workload, because of the high forgetting rate.
deck-config-seconds-to-show-question-tooltip-2 = Przy włączonym auto-postępie, liczba sekund zanim zostanie pokazana odpowiedź. Ustaw 0 by wyłączyć.
deck-config-invalid-weights = Parametry muszą albo być zostawione puste, by użyć wartości domyślnych, lub być 17 liczbami oddzielonymi przecinkami.
deck-config-fsrs-on-all-clients = Upewnij się, że wszystkie programy Anki, z których korzystasz mają odpowiednią wersję: Anki(Mobile) 23.10 lub wyższa, AnkiDroid 2.17 lub wyższa . FSRS nie będzie działać poprawnie jeśli któryś z tych programów posiada starszą wersję.
deck-config-optimize-all-tip = Możesz zoptymalizować wszystkie opcje na raz używając przycisku rozwijanego obok opcji "Zapisz".
