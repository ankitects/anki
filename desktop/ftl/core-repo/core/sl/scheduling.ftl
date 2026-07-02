## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount }s
scheduling-answer-button-time-minutes = { $amount }m
scheduling-answer-button-time-hours = { $amount }h
scheduling-answer-button-time-days = { $amount }dni
scheduling-answer-button-time-months = { $amount }mes.
scheduling-answer-button-time-years = { $amount }let

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] { $amount } sekunda
        [two] { $amount } sekunda
        [few] { $amount } sekundi
       *[other] { $amount } sekunde
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] { $amount } minut
        [two] { $amount } minuta
        [few] { $amount } minuti
       *[other] { $amount } minute
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount }  ur
        [two] { $amount } ura
        [few] { $amount } uri
       *[other] { $amount } ure
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } dan
        [two] { $amount } dneva
        [few] { $amount } dni
       *[other] { $amount } dni
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } mesec
        [two] { $amount } meseca
        [few] { $amount } mesece
       *[other] { $amount } mesecev
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } let
        [two] { $amount } leto
        [few] { $amount } leti
       *[other] { $amount } leta
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    Naslednja kartica za učenje bo pripravljena v { $unit ->
        [seconds]
            { $amount ->
                [one] { $amount } sekundi
               *[other] { $amount } sekundah
            }
        [minutes]
            { $amount ->
                [one] { $amount } minuti
               *[other] { $amount } minutah
            }
       *[hours]
            { $amount ->
                [one] { $amount } uri
               *[other] { $amount } urah
            }
    }-
scheduling-learn-remaining =
    { $remaining ->
        [one] Za kasneje danes ostaja še ena kartica za učenje.
        [two] Za kasneje danes ostajata še { $remaining } kartici za učenje.
        [few] Za kasneje danes ostajajo še { $remaining } kartice za učenje.
       *[other] Za kasneje danes ostaja še { $remaining } kartic za učenje.
    }
scheduling-congratulations-finished = Čestitam! S tem paketom ste za sedaj zaključili.
scheduling-today-review-limit-reached =
    Današnja meja pregledov je bila dosežena, vendar še vedno ostajajo
    kartice, ki čakajo na pregled. Za boljši spomin premislite o tem, da bi
    povečali dnevno mejo.
scheduling-today-new-limit-reached =
    Na voljo je še več novih kartic, vendar ste že dosegli dnevno
    mejo. Lahko povišate mejo, toda upoštevajte, da s tem ko povečate
    število kartic, bolj obremenite kratkoročni pregled.
scheduling-buried-cards-found = Ena ali več kartic ste zakopali in bodo prikazane jutri. Lahko jih { $unburyThem }, če jih želite videti takoj.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = odkopljete
scheduling-how-to-custom-study = Če želite nadaljevati z učenjem izven rednega urnika, lahko uporabite funkcijo { $customStudy }.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = učenje po meri

## Scheduler upgrade

scheduling-update-soon = Anki 2.1 ima nov razporejevalnik, ki popravlja številne težave, ki so jih imele predhodne različice. Posodobitev na to verzijo je priporočljiva.
scheduling-update-done = Razporejevalnik je bil uspešno posodobljen.
scheduling-update-button = Posodobi
scheduling-update-later-button = Kasneje
scheduling-update-more-info-button = Izvedite več
scheduling-update-required =
    Vašo kolekcijo moramo posodobiti na razporejevalnik V2.
    Prosimo, izberite { scheduling-update-more-info-button } pred nadaljevanjem.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = Pri predvajanju zvoka vedno vključite tudi stran z vprašanjem.
scheduling-at-least-one-step-is-required = Zahtevan je vsaj en korak.
scheduling-automatically-play-audio = Samodejno predvajaj zvok
scheduling-bury-related-new-cards-until-the = Zakoplji sorodne nove kartice do naslednjega dne
scheduling-bury-related-reviews-until-the-next = Zakoplji sorodne ponovitvene kartice do naslednjega dne
scheduling-days = dni
scheduling-description = Opis
scheduling-easy-bonus = Dodatek za lahke
scheduling-easy-interval = Enostaven interval
scheduling-end = (konec)
scheduling-general = Splošno
scheduling-graduating-interval = Interval napredovanja
scheduling-hard-interval = Težak interval
scheduling-ignore-answer-times-longer-than = Spreglej odgovore, za katere je bilo porabljeno več kot
scheduling-interval-modifier = Modifikator intervala
scheduling-lapses = Spodrsljaji
scheduling-lapses2 = spodrsljaji
scheduling-learning = Učenje
scheduling-leech-action = Akcija za pijavke
scheduling-leech-threshold = Prag pijavk
scheduling-maximum-interval = Največji interval
scheduling-maximum-reviewsday = Maksimum ponovitev/dan
scheduling-minimum-interval = Najmanjši interval
scheduling-mix-new-cards-and-reviews = Pomešaj nove kartice in preglede
scheduling-new-cards = Nove kartice
scheduling-new-cardsday = Novih kartic/dan
scheduling-new-interval = Nov interval
scheduling-new-options-group-name = Novo ime zbirke opcij:
scheduling-options-group = Skupina možnosti:
scheduling-order = Vrstni red
scheduling-parent-limit = (omejitev nadrejenega: { $val })
scheduling-reset-counts = Ponastavi števce ponovitev in spodrsljajev
scheduling-restore-position = Kjer je možno, ponastavi originalne pozicije
scheduling-review = Pregled
scheduling-reviews = Pregledi
scheduling-seconds = sekund
scheduling-set-all-decks-below-to = Nastavim vse pakete pod { $val } za to skupino možnosti?
scheduling-set-for-all-subdecks = Nastavi za vse podrejene pakete
scheduling-show-answer-timer = Prikaži časomer za odgovor
scheduling-show-new-cards-after-reviews = Pokaži nove kartice po pregledu
scheduling-show-new-cards-before-reviews = Pokaži nove kartice pred pregledom
scheduling-show-new-cards-in-order-added = Pokaži nove kartice v vrstnem redu, kot so bile dodane
scheduling-show-new-cards-in-random-order = Pokaži nove kartice v naključnem vrstnem redu
scheduling-starting-ease = Začetna dostopnost
scheduling-steps-in-minutes = Koraki (v minutah)
scheduling-steps-must-be-numbers = Koraki morajo biti številke.
scheduling-tag-only = Samo oznaka
scheduling-the-default-configuration-cant-be-removed = Privzeta konfiguracija ne možno odstraniti.
scheduling-your-changes-will-affect-multiple-decks = Vaše spremembe bodo vplivale na več zbirk. Če želite spreminjati le obstoječo zbirko, najprej dodajte skupino novih možnosti.
scheduling-deck-updated =
    { $count ->
        [one] { $count } paketov posodobljenih.
        [two] { $count } paket posodobljen.
        [few] { $count } paketa posodobljena.
       *[other] { $count } paketov posodobljenih.
    }
scheduling-set-due-date-prompt =
    { $cards ->
        [one] V koliko dneh pokažem kartico?
        [two] V koliko dneh pokažem kartici?
        [few] V koliko dneh pokažem kartice?
       *[other] V koliko dneh pokažem kartice?
    }
scheduling-set-due-date-prompt-hint =
    0 = danes
    1! = jutri + spremeni interval v 1
    3-7 = naključna izbira 3-7 dni
scheduling-set-due-date-done =
    { $cards ->
        [one] Nastavi datum poteka za toliko kartic: { $cards }.
        [two] Nastavi datum poteka za toliko kartic: { $cards }.
        [few] Nastavi datum poteka za toliko kartic: { $cards }.
       *[other] Nastavi datum poteka za toliko kartic: { $cards }.
    }
scheduling-forgot-cards =
    { $cards ->
        [one] Pozabljenih kartic: { $cards }.
        [two] Pozabljenih kartic: { $cards }.
        [few] Pozabljenih kartic: { $cards }.
       *[other] Pozabljenih kartic: { $cards }.
    }
