## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount }s
scheduling-answer-button-time-minutes = { $amount }min
scheduling-answer-button-time-hours = { $amount }g
scheduling-answer-button-time-days = { $amount }d
scheduling-answer-button-time-months = { $amount }mc
scheduling-answer-button-time-years = { $amount }r

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] { $amount } sekunda
        [few] { $amount } sekundy
        [many] { $amount } sekund
       *[other] { $amount } sekundy
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] { $amount } minuta
        [few] { $amount } minuty
        [many] { $amount } minut
       *[other] { $amount } minuty
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } godzina
        [few] { $amount } godziny
        [many] { $amount } godzin
       *[other] { $amount } godziny
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } dzień
        [few] { $amount } dni
        [many] { $amount } dni
       *[other] { $amount } dni
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } miesiąc
        [few] { $amount } miesiące
        [many] { $amount } miesięcy
       *[other] { $amount } miesiąca
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } rok
        [few] { $amount } lata
        [many] { $amount } lat
       *[other] { $amount } roku
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    { $unit ->
        [seconds]
            { $amount ->
                [one] Następna karta będzie dostępna za { $amount } sekundę.
                [few] Następna karta będzie dostępna za { $amount } sekundy.
                [many] Następna karta będzie dostępna za { $amount } sekund.
               *[other] Następna karta będzie dostępna za { $amount } sekund.
            }
        [minutes]
            { $amount ->
                [one] Następna karta będzie dostępna za { $amount } minutę.
                [few] Następna karta będzie dostępna za { $amount } minuty.
                [many] Następna karta będzie dostępna za { $amount } minut.
               *[other] Następna karta będzie dostępna za { $amount } minut.
            }
       *[hours]
            { $amount ->
                [one] Następna karta będzie dostępna za { $amount } godzinę.
                [few] Następna karta będzie dostępna za { $amount } godziny.
                [many] Następna karta będzie dostępna za { $amount } godzin.
               *[other] Następna karta będzie dostępna za { $amount } godzin.
            }
    }
scheduling-learn-remaining =
    { $remaining ->
        [one] Została { $remaining } karta do przejrzenia dziś w późniejszym czasie.
        [few] Zostały { $remaining } karty do przejrzenia dziś w późniejszym czasie.
        [many] Zostało { $remaining } kart do przejrzenia dziś w późniejszym czasie.
       *[other] Zostało { $remaining } kart do przejrzenia dziś w późniejszym czasie.
    }
scheduling-congratulations-finished = Gratulacje! Zakończono powtórki na dziś.
scheduling-today-review-limit-reached =
    Dzisiejszy limit powtórki został osiągnięty, ale są jeszcze karty
    czekające na powtórkę. Dla najlepszego zapamiętywania,
    rozważ zwiększenie dziennego limitu w opcjach.
scheduling-today-new-limit-reached =
    Jest dostępnych więcej nowych kart, lecz dzienny limit został osiągnięty. Możesz zwiększyć go w opcjach, ale miej na uwadze, że im więcej nowych kart poznajesz, tym większa będzie liczba kart
    do przejrzenia w najbliższym czasie.
scheduling-buried-cards-found = Jedna lub więcej kart zostało zakopanych, przez co zostaną pokazane jutro. Możesz { $unburyThem }, jeśli chcesz zobaczyć te karty teraz.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = odkopać je
scheduling-how-to-custom-study = Aby uczyć się poza normalnym rozkładem możesz użyć opcji { $customStudy }.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = Nauka własna

## Scheduler upgrade

scheduling-update-soon = W Anki 2.1 zaimplementowano nowego planistę, który naprawia liczne błędy poprzednich wersji Anki. Zalecane jest uaktualnienie planisty do nowszej wersji.
scheduling-update-done = Pomyślnie uaktualniono planistę.
scheduling-update-button = Uaktualnij
scheduling-update-later-button = Później
scheduling-update-more-info-button = Dowiedz się więcej
scheduling-update-required =
    Twoja kolekcja musi być zaktualizowana do algorytmu planowania V2.
    Wybierz { scheduling-update-more-info-button } przed kontynuowaniem.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = Zawsze dołączaj stronę pytania przy odtwarzaniu nagrania
scheduling-at-least-one-step-is-required = Wymagany jest przynajmniej jeden krok.
scheduling-automatically-play-audio = Automatycznie odtwarzaj dźwięk
scheduling-bury-related-new-cards-until-the = Zakop powiązane nowe karty do następnego dnia
scheduling-bury-related-reviews-until-the-next = Zakop powiązane powtórki do następnego dnia
scheduling-days = dni
scheduling-description = Opis
scheduling-easy-bonus = Premia odpowiedzi Łatwa
scheduling-easy-interval = Przerwa dla łatwych
scheduling-end = (koniec)
scheduling-general = Ogólne
scheduling-graduating-interval = Przerwa dla kart po nauce
scheduling-hard-interval = Przerwa dla trudnych
scheduling-ignore-answer-times-longer-than = Ignoruj odpowiedzi z czasem dłuższym niż
scheduling-interval-modifier = Modyfikator przerw
scheduling-lapses = Pomyłki
scheduling-lapses2 = pomyłek
scheduling-learning = Do nauki
scheduling-leech-action = Działanie dla pijawek
scheduling-leech-threshold = Próg pijawek
scheduling-maximum-interval = Maksymalna przerwa
scheduling-maximum-reviewsday = Maksymalnie powtórek/dzień
scheduling-minimum-interval = Minimalna przerwa
scheduling-mix-new-cards-and-reviews = Mieszaj nowe karty i powtórki
scheduling-new-cards = Nowe karty
scheduling-new-cardsday = Nowe karty/dzień
scheduling-new-interval = Przerwa nowej karty
scheduling-new-options-group-name = Nazwa nowej grupy opcji:
scheduling-options-group = Grupa opcji:
scheduling-order = Kolejność
scheduling-parent-limit = (limit talii nadrzędnej: { $val })
scheduling-reset-counts = Wyzeruj liczbę powtórek i pomyłek
scheduling-restore-position = Przywróć gdzie to możliwe oryginalną pozycję
scheduling-review = Powtarzane
scheduling-reviews = Powtórki
scheduling-seconds = sekund
scheduling-set-all-decks-below-to = Ustawić tę opcję grup dla wszystkich poniższych { $val } talii?
scheduling-set-for-all-subdecks = Ustaw dla wszystkich podtalii
scheduling-show-answer-timer = Pokaż czas odpowiedzi
scheduling-show-new-cards-after-reviews = Pokaż nowe karty po powtórkach
scheduling-show-new-cards-before-reviews = Pokaż nowe karty przed powtórkami
scheduling-show-new-cards-in-order-added = Pokaż nowe karty w kolejności dodania
scheduling-show-new-cards-in-random-order = Pokaż nowe karty w losowej kolejności
scheduling-starting-ease = Początkowa łatwość
scheduling-steps-in-minutes = Kroki (w minutach)
scheduling-steps-must-be-numbers = Kroki muszą być liczbami.
scheduling-tag-only = Tylko tag
scheduling-the-default-configuration-cant-be-removed = Usunięcie domyślnej konfiguracji nie jest możliwe.
scheduling-your-changes-will-affect-multiple-decks = Twoje zmiany dotkną wiele talii. Jeśli chcesz zmienić tylko aktualną talię, dodaj najpierw nową grupę opcji.
scheduling-deck-updated =
    { $count ->
        [one] Zaktualizowano { $count } talię.
        [few] Zaktualizowano { $count } talie.
        [many] Zaktualizowano { $count } talii.
       *[other] Zaktualizowano { $count } talii.
    }
scheduling-set-due-date-prompt =
    { $cards ->
        [one] Za ile dni pokazać kartę?
        [few] Za ile dni pokazać karty?
        [many] Za ile dni pokazać karty?
       *[other] Za ile dni pokazać karty?
    }
scheduling-set-due-date-prompt-hint =
    0 = dziś
    1! = jutro+ ustaw przerwę na 1
    3-7 = losowy wybór w zakresie 3-7 dni
scheduling-set-due-date-done =
    { $cards ->
        [one] Ustaw termin przejrzenia { $cards } karty.
        [few] Ustaw termin przejrzenia { $cards } kart.
        [many] Ustaw termin przejrzenia { $cards } kart.
       *[other] Ustaw termin przejrzenia { $cards } kart.
    }
scheduling-graded-cards-done =
    { $cards ->
        [one] Oceniono { $cards } kartę.
        [few] Oceniono { $cards } karty.
        [other] Nie oceniono żadnej karty.
       *[many] Oceniono { $cards } kart.
    }
scheduling-forgot-cards =
    { $cards ->
        [one] Zresetuj { $cards } kartę.
        [few] Zresetuj { $cards } karty.
        [many] Zresetuj { $cards } kart.
       *[other] Nie resetuj żadnej karty.
    }
