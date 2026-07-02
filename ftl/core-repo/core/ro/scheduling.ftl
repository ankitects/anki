## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount }s
scheduling-answer-button-time-minutes = { $amount }m
scheduling-answer-button-time-hours = { $amount }h
scheduling-answer-button-time-days = { $amount }z
scheduling-answer-button-time-months = { $amount }l
scheduling-answer-button-time-years = { $amount }an

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] { $amount } secundă
        [few] { $amount } secunde
       *[other] { $amount } de secunde
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] { $amount } minut
        [few] { $amount } minute
       *[other] { $amount } de minute
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } oră
        [few] { $amount } ore
       *[other] { $amount } de ore
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } zi
        [few] { $amount } zile
       *[other] { $amount } de zile
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } lună
        [few] { $amount } luni
       *[other] { $amount } de luni
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } an
        [few] { $amount } ani
       *[other] { $amount } de ani
    }

## Shown in the "Congratulations!" message after study finishes.

scheduling-congratulations-finished = Felicitări! Ai terminat acest pachet pentru moment.
scheduling-today-review-limit-reached =
    A fost atinsă limita repetițiilor pentru astăzi, dar încă există carduri 
    care așteaptă să fie repetate. Pentru o memorare optimă, ia în considerare
    creșterea limitei zilnice în opțiuni.
scheduling-today-new-limit-reached =
    Mai există carduri noi valabile, dar a fost atinsă limita 
    zilnică. În Opțiuni, poți mări limita, dar te rog să ții cont 
    de faptul că, introducând mai multe carduri noi, volumul 
    de muncă al repetițiilor pe termen scurt va deveni mai mare.
scheduling-buried-cards-found = Una sau mai multe carduri au fost îngropate și vor fi afișate mâine. Puteți să { $unburyThem } dacă doriți să le vedeți imediat.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = le dezgropați
scheduling-how-to-custom-study = Dacă doriți să studiați în afara programului obișnuit, puteți utiliza funcția { $customStudy }.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = studiu personalizat

## Scheduler upgrade

scheduling-update-soon = Anki 2.1 vine cu un nou programator, care rezolvă o serie de probleme pe care le-au avut versiunile anterioare Anki. Se recomandă actualizarea acestuia.
scheduling-update-done = Programatorul a fost actualizat cu succes.
scheduling-update-button = Actualizează
scheduling-update-later-button = Mai târziu
scheduling-update-more-info-button = Află mai multe
scheduling-update-required =
    Colecția dvs. trebuie să fie actualizată la programatorul V2.
    Vă rugăm să selectați { scheduling-update-more-info-button } înainte de a continua.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = Includeți întotdeauna partea de întrebare când redați din nou conținutul audio
scheduling-at-least-one-step-is-required = Cel puțin o etapă este necesară.
scheduling-automatically-play-audio = Redare automată audio
scheduling-bury-related-new-cards-until-the = Ascunde cardurile noi până în ziua următoare
scheduling-bury-related-reviews-until-the-next = Ascunde recapitulările asociate până în ziua următoare
scheduling-days = zile
scheduling-description = Descriere
scheduling-easy-bonus = Bonus ușor
scheduling-easy-interval = Interval ușor
scheduling-end = (sfârșit)
scheduling-general = Generalități
scheduling-graduating-interval = Intervalul gradual
scheduling-ignore-answer-times-longer-than = Ignoră timpul de răspuns mai mare de
scheduling-interval-modifier = Modificator interval
scheduling-lapses = Rateuri
scheduling-lapses2 = rateuri
scheduling-learning = Învățate
scheduling-leech-action = Acțiune pentru lipitoare
scheduling-leech-threshold = Prag pentru lipitoare
scheduling-maximum-interval = Interval maxim
scheduling-maximum-reviewsday = Repetiții maxime/zi
scheduling-minimum-interval = Interval minim
scheduling-mix-new-cards-and-reviews = Fă un amestec între cardurile noi și cele repetate
scheduling-new-cards = Carduri noi
scheduling-new-cardsday = Carduri noi/zi
scheduling-new-interval = Interval nou
scheduling-new-options-group-name = Nume nou pentru grupul de opțiuni
scheduling-options-group = Grup de opțiuni:
scheduling-order = Ordine
scheduling-parent-limit = (limita părintelui: { $val })
scheduling-reset-counts = Resetați numărul de repetare și intervale
scheduling-restore-position = Restabiliți poziția inițială acolo unde este posibil
scheduling-review = Revedere
scheduling-reviews = Repetiții
scheduling-seconds = secunde
scheduling-set-all-decks-below-to = Setați toate pachetele de sub { $val } la acest grup de opțiuni?
scheduling-set-for-all-subdecks = Setare pentru toate subpachetele
scheduling-show-answer-timer = Afișează temporizatorul de răspuns
scheduling-show-new-cards-after-reviews = Arată cardurile noi după repetiții
scheduling-show-new-cards-before-reviews = Arată cărțile noi înaintea recapitulării
scheduling-show-new-cards-in-order-added = Arată cărțile noi în ordinea adăugării
scheduling-show-new-cards-in-random-order = Arată cărțile noi în ordine aleatoare
scheduling-starting-ease = Start ușurință
scheduling-steps-in-minutes = Pași (în minute)
scheduling-steps-must-be-numbers = Pașii trebuie să fie numere.
scheduling-tag-only = Doar marcaj
scheduling-the-default-configuration-cant-be-removed = Configurația implicită nu poate fi eliminată.
scheduling-your-changes-will-affect-multiple-decks = Modificările dvs. vor afecta mai multe pachete. Dacă doriți să schimbați doar pachetul actual, adăugați mai întâi un nou grup de opțiuni.
scheduling-deck-updated =
    { $count ->
        [one] { $count } pachet actualizat.
        [few] { $count } pachete actualizate.
       *[other] { $count } pachete actualizate.
    }
scheduling-set-due-date-prompt =
    { $cards ->
        [one] Afișați cardul în câte zile?
        [few] Afișați cardurile în câte zile?
       *[other] Afișați cardurile în câte zile?
    }
scheduling-set-due-date-prompt-hint =
    0 = astăzi
    1! = mâine+resetare interval de revedere
    3-7 = alegere aleatorie intre 3-7 zile
scheduling-set-due-date-done =
    { $cards ->
        [one] Setați data scadenței cardului.
        [few] Setați data scadenței pentru { $cards } carduri.
       *[other] Setați data scadenței pentru { $cards } carduri.
    }
scheduling-forgot-cards =
    { $cards ->
        [one] Uită un card
        [few] Uită { $cards } carduri.
       *[other] Uită { $cards } carduri.
    }
