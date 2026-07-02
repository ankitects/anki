## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount }s
scheduling-answer-button-time-minutes = { $amount }min
scheduling-answer-button-time-hours = { $amount }h
scheduling-answer-button-time-days = { $amount }d
scheduling-answer-button-time-months = { $amount }mj.
scheduling-answer-button-time-years = { $amount }god.

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] { $amount } sekunda
        [few] { $amount } sekunde
       *[other] { $amount } sekundi
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] { $amount } minuta
        [few] { $amount } minute
       *[other] { $amount } minuta
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } sat
        [few] { $amount } sata
       *[other] { $amount } sati
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } dan
        [few] { $amount } dana
       *[other] { $amount } dana
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } mjesec
        [few] { $amount } mjeseca
       *[other] { $amount } mjeseci
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } godina
        [few] { $amount } godine
       *[other] { $amount } godina
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    { $unit ->
        [seconds]
            { $amount ->
                [one] Sljedeća kartica za učenje bit će dostupna za { $amount } sekundu.
                [few] Sljedeća kartica za učenje bit će dostupna za { $amount } sekunde.
               *[other] Sljedeća kartica za učenje bit će dostupna za { $amount } sekundi.
            }
        [minutes]
            { $amount ->
                [one] Sljedeća kartica za učenje bit će dostupna za { $amount } minutu.
                [few] Sljedeća kartica za učenje bit će dostupna za { $amount } minute.
               *[other] Sljedeća kartica za učenje bit će dostupna za { $amount } minuta.
            }
       *[hours]
            { $amount ->
                [one] Sljedeća kartica za učenje bit će dostupna za { $amount } sat.
                [few] Sljedeća kartica za učenje bit će dostupna za { $amount } sata.
               *[other] Sljedeća kartica za učenje bit će dostupna za { $amount } sati.
            }
    }
scheduling-learn-remaining =
    { $remaining ->
        [one] Ostala je jedna kartica koja je kasnije danas na redu.
        [few] Ostale su { $remaining } kartice koje su kasnije danas na redu.
       *[other] Ostalo je { $remaining } kartica koje su kasnije danas na redu.
    }
scheduling-congratulations-finished = Čestitke! Zasad ste završili ovaj špil.
scheduling-today-review-limit-reached = Današnji dnevni limit ponavljanja je dosegnut, no ostalo je još kartica za ponavljanje. Za optimalno pamćenje, povećajte dnevni limit u postavkama.
scheduling-today-new-limit-reached = Dostupno je još novih kartica, ali dosegnut je dnevni limit. Možete povećati limit u postavkama, ali imajte na umu da što više novih kartica uvedete, to će vaše kratkotrajno opterećenje ponavljanja postati veće.
scheduling-buried-cards-found = Neke kartice su zakopane te će sutra biti prikazane. Možete { $unburyThem } ako ih želite odmah vidjeti.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = ih otkopati
scheduling-how-to-custom-study = Ako želite učiti izvan uobičajenog rasporeda, možete koristiti mogućnost { $customStudy }.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = učenje po mjeri

## Scheduler upgrade

scheduling-update-soon = Anki 2.1 dolazi s novim raspoređivačem, koji rješava brojne probleme iz prethodnih verzija Ankija. Preporučuje se ažuriranje.
scheduling-update-done = Raspoređivač je uspješno ažuriran.
scheduling-update-button = Ažuriraj
scheduling-update-later-button = Kasnije
scheduling-update-more-info-button = Nauči više
scheduling-update-required =
    Vaša kolekcija mora biti ažurirana na V2 raspoređivač.
    Stisnite { scheduling-update-more-info-button } prije nego što nastavite.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = Uvijek uključi stranu s pitanjem pri ponovnoj reprodukciji zvuka
scheduling-at-least-one-step-is-required = Potreban je bar jedan korak.
scheduling-automatically-play-audio = Automatska reprodukcija zvučnog zapisa
scheduling-bury-related-new-cards-until-the = Zakopaj srodne nove kartice do idućeg dana
scheduling-bury-related-reviews-until-the-next = Zakopaj srodne kartice ponavljanja do idućeg dana
scheduling-days = dana
scheduling-description = Opis
scheduling-easy-bonus = Bonus za "Lako"
scheduling-easy-interval = Interval za lagane
scheduling-end = (kraj)
scheduling-general = Općenito
scheduling-graduating-interval = Interval apsolviranja
scheduling-hard-interval = Interval za teške
scheduling-ignore-answer-times-longer-than = Ignoriraj vremena odgovora duža od
scheduling-interval-modifier = Modifikator intervala
scheduling-learning = Učenje
scheduling-leech-action = Postupak za pijavice
scheduling-leech-threshold = Prag za pijavice
scheduling-maximum-interval = Najveći razmak
scheduling-maximum-reviewsday = Maksimalan broj ponavljanja po danu
scheduling-minimum-interval = Najmanji razmak
scheduling-mix-new-cards-and-reviews = Miješaj nove kartice i ponavljanja
scheduling-new-cards = Nove kartice
scheduling-new-cardsday = Novih kartica po danu
scheduling-new-interval = Novi interval
scheduling-new-options-group-name = Novi naziv grupe postavki:
scheduling-options-group = Grupa postavki:
scheduling-order = Redoslijed
scheduling-parent-limit = (ograničenje za nadređeni komplet: { $val })
scheduling-restore-position = Obnovi originalnu poziciju ako je moguće
scheduling-review = Ponavljanje
scheduling-reviews = Ponavljanja
scheduling-seconds = sekundi
scheduling-set-all-decks-below-to = Sve špilove ispod { $val } postavi u ovu grupu opcija?
scheduling-set-for-all-subdecks = Postavi za sve pod-špilove
scheduling-show-answer-timer = Prikaži brojač vremena
scheduling-show-new-cards-after-reviews = Prikaži nove kartice nakon ponavljanja
scheduling-show-new-cards-before-reviews = Prikaži nove kartice prije ponavljanja
scheduling-show-new-cards-in-order-added = Prikaži nove kartice redoslijedom kojim su dodane
scheduling-show-new-cards-in-random-order = Prikaži nove kartice u nasumičnom redoslijedu
scheduling-starting-ease = Početna težina
scheduling-steps-in-minutes = Koraka (u minutama)
scheduling-steps-must-be-numbers = Koraci moraju biti brojevi.
scheduling-tag-only = Samo oznaka
scheduling-the-default-configuration-cant-be-removed = Standardna konfiguracija se ne može ukloniti.
scheduling-your-changes-will-affect-multiple-decks = Vaše će promjene utjecati na više špilova. Ako želite promijeniti samo trenutni špil, prvo dodajte novu grupu opcija.
scheduling-deck-updated =
    { $count ->
        [one] { $count } špil aktualiziran.
        [few] { $count } špila aktualizirana.
       *[other] { $count } špilova aktualizirano.
    }
scheduling-set-due-date-prompt =
    { $cards ->
        [one] Pokaži karticu za koliko dana?
        [few] Pokaži kartice za koliko dana?
       *[other] Pokaži kartice za koliko dana?
    }
scheduling-set-due-date-prompt-hint =
    0 = danas
    1! = sutra + promijeni interval na 1
    3-7 = nasumičan odabir između 3 i 7 dana.
scheduling-graded-cards-done =
    { $cards ->
        [one] { $cards } kartica ocijenjena.
        [few] { $cards } kartice ocijenjene.
       *[other] { $cards } kartica ocijenjeno.
    }
scheduling-forgot-cards =
    { $cards ->
        [one] { $cards } kartica resetirana.
        [few] { $cards } kartice resetirane.
       *[other] { $cards } kartica resetirano.
    }
