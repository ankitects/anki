studying-again = Powtórz
studying-all-buried-cards = Wszystkie zakopane karty
studying-audio-5s = Dźwięk -5s
studying-audio-and5s = Dźwięk +5s
studying-buried-siblings = Zakop podobne
studying-bury = Zakop
studying-bury-card = Zakop kartę
studying-bury-note = Zakop notatkę
studying-card-suspended = Karta zawieszona.
studying-card-was-a-leech = Karta okazała się pijawką.
studying-cards-buried =
    { $count ->
        [one] Zakopano { $count } kartę.
        [few] Zakopano { $count } karty.
        [many] Zakopano { $count } kart.
       *[other] Nie zakopano żadnej karty.
    }
studying-cards-will-be-automatically-returned-to = Po zakończeniu powtórek, karty automatycznie powrócą do talii źródłowej.
studying-continue = Kontynuuj
studying-counts-differ = Liczby są różne od tych na liście talii, ponieważ jest włączone zakopywanie. Niektóre karty są pominięte, a inne mogą zająć ich miejsce.
studying-delete-note = Usuń notatkę
studying-deleting-this-deck-from-the-deck = Usunięcie tej talii z listy talii zwróci wszystkie pozostałe karty do ich oryginalnej talii.
studying-easy = Łatwa
studying-edit = Edytuj
studying-empty = Opróżnij
studying-finish = Zakończ
studying-flag-card = Oflaguj kartę
studying-good = Dobra
studying-hard = Trudna
studying-it-has-been-suspended = Została zawieszona.
studying-manually-buried-cards = Ręcznie zakopane karty
studying-mark-note = Wyróżnij notatkę
studying-more = Więcej
studying-no-cards-are-due-yet = Nie oczekują jeszcze żadne karty.
studying-note-suspended = Notatka zawieszona.
studying-pause-audio = Pauzuj dźwięk
studying-please-run-toolsempty-cards = Uruchom Narzędzia>Puste karty
studying-record-own-voice = Nagraj swój głos
studying-replay-own-voice = Odtwórz swój głos
studying-show-answer = Pokaż odpowiedź
studying-space = Spacja
studying-study-now = Ucz się teraz
studying-suspend = Zawieś
studying-suspend-note = Zawieś notatkę
studying-this-is-a-special-deck-for = Specjalna talia do nauki poza normalnym rozkładem.
studying-to-review = Do powtórki
studying-type-answer-unknown-field = Wpisz odpowiedź: nieznane pole { $val }
studying-unbury = Odkop
studying-what-would-you-like-to-unbury = Co chcesz odkopać?
studying-you-havent-recorded-your-voice-yet = Nie nagrałeś jeszcze swojego głosu.
studying-card-studied-in-minute =
    { $cards ->
        [one]
            { $minutes ->
                [one]
                    Przejrzano { $cards } kartę
                    w { $minutes } minutę.
                [few]
                    Przejrzano { $cards } kartę
                    w { $minutes } minuty.
               *[many]
                    Przejrzano { $cards } kartę
                    w { $minutes } minut.
            }
        [few]
            { $minutes ->
                [one]
                    Przejrzano { $cards } karty
                    w { $minutes } minutę.
                [few]
                    Przejrzano { $cards } karty
                    w { $minutes } minuty.
               *[many]
                    Przejrzano { $cards } karty
                    w { $minutes } minut.
            }
       *[many]
            { $minutes ->
                [one]
                    Przejrzano { $cards } kart
                    w { $minutes } minutę.
                [few]
                    Przejrzano { $cards } kartę
                    w { $minutes } minuty.
               *[many]
                    Przejrzano { $cards } kart
                    w { $minutes } minut.
            }
    }
studying-question-time-elapsed = Minął czas na odpowiedź
studying-answer-time-elapsed = Minął czas na odpowiedź

## OBSOLETE; you do not need to translate this

studying-card-studied-in =
    { $count ->
        [one] Przejrzano { $count } kartę w
        [few] Przejrzano { $count } karty w
        [many] Przejrzano { $count } kart w
       *[other] Przejrzano { $count } kart w
    }
studying-minute =
    { $count ->
        [one] { $count } minuta.
        [few] { $count } minuty.
        [many] { $count } minut.
       *[other] { $count } minut.
    }
