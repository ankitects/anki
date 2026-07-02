## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-hours = { $amount }o
scheduling-answer-button-time-days = { $amount }e
scheduling-answer-button-time-months = { $amount }h
scheduling-answer-button-time-years = { $amount }u

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] Segundo { $amount }
       *[other] { $amount } segundo
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] Minutu { $amount }
       *[other] { $amount } minutu
    }
scheduling-time-span-hours =
    { $amount ->
        [one] Ordu { $amount }
       *[other] { $amount } ordu
    }
scheduling-time-span-days =
    { $amount ->
        [one] Egun { $amount }
       *[other] { $amount } egun
    }
scheduling-time-span-months =
    { $amount ->
        [one] Hilabete { $amount }
       *[other] { $amount } hilabete
    }
scheduling-time-span-years =
    { $amount ->
        [one] Urte { $amount }
       *[other] { $amount } urte
    }

## Shown in the "Congratulations!" message after study finishes.

scheduling-congratulations-finished = Zorionak! sorta hau amaitu duzu oraindik
scheduling-today-review-limit-reached = Gaurko berrikuspen muga helduta da, baina oraindik karta batzuk berrikustekozain daude. Memorizazioa hobetzeko, aukeretan eguneroko muga handitzerik ez ahaztu
scheduling-today-new-limit-reached =
    Karta berri gehiagoa erabilgarria da, baina eguneroko muga orain
    heldu da. Aukeretan muga handi dezakezu, baina mesedez
    hau gogoratu : zenbat eta karta berri gehiago sartzen duzu, 
    orduan eta epe motza duen berrikuspena gehiago kargatua izango da.

## Scheduler upgrade


## Other scheduling strings

scheduling-at-least-one-step-is-required = Gutxienez urrats bat behar da.
scheduling-automatically-play-audio = Automatikoki audioa jo
scheduling-bury-related-new-cards-until-the = hurrengo egun arte harremanak dituzten kartak lurperatu
scheduling-bury-related-reviews-until-the-next = hurrengo egun arte harremanak dituzten ikuskatzeak lurperatu
scheduling-days = egunak
scheduling-description = Deskribapena
scheduling-easy-bonus = Erraztasunako hobaria
scheduling-easy-interval = Erraztasunako tarte
scheduling-end = (bukaera)
scheduling-general = Orokorra
scheduling-graduating-interval = Graduatze-tartea
scheduling-ignore-answer-times-longer-than = erantzun denbora handiena bazter uzti baino lehen :
scheduling-interval-modifier = tarte aldatzaile
scheduling-lapses = Hutsegiteak
scheduling-lapses2 = denbora-tarteak
scheduling-learning = Ikasten
scheduling-leech-action = Neketsuko tratamendua
scheduling-leech-threshold = Neketsua izateko ataria
scheduling-maximum-interval = denbora-tarte handiena
scheduling-maximum-reviewsday = Genieneko berrikuspen/egun
scheduling-minimum-interval = Denbora-tarte txikiena
scheduling-mix-new-cards-and-reviews = Nahastu karta berriak eta berrikuspenak
scheduling-new-cards = Karta berriak
scheduling-new-cardsday = karta berri egungo
scheduling-new-interval = Bitarte berria
scheduling-new-options-group-name = aukeren taldeko izen berria :
scheduling-options-group = aukeretako profilea
scheduling-order = Ordena
scheduling-parent-limit = gurasoen muga : { $val }
scheduling-review = Berrikusi
scheduling-reviews = Berrikuspenak
scheduling-seconds = segundo
scheduling-set-all-decks-below-to = { $val } azpian dauden sorta guztiei aukera multzoa ?
scheduling-set-for-all-subdecks = Azpisorta guztietarako jarri
scheduling-show-answer-timer = kronometroa erakutsi
scheduling-show-new-cards-after-reviews = Erakutsi karta berriak berrikuspenen ondoren
scheduling-show-new-cards-before-reviews = Erakutsi karta berriak berrikuspenen aurretik
scheduling-show-new-cards-in-order-added = Erakutsi karta berriak gehitutako ordenean
scheduling-show-new-cards-in-random-order = Erakutsi karta berriak ausazko ordenean
scheduling-starting-ease = Hasteko erraztasuna
scheduling-steps-in-minutes = Urratsak (min)
scheduling-steps-must-be-numbers = Urratask zenbakiak izan behar dute
scheduling-tag-only = Etiketatu (*)
scheduling-the-default-configuration-cant-be-removed = Itxura lehentsia ezin da ezabatu
scheduling-your-changes-will-affect-multiple-decks = zure aldaketeek hainbat sorta eragingo dituzte.Uneko sorta aldatu nahi baduzu, lehenbizi aukerako talde berri bat gehitu ezazu mesedez
scheduling-deck-updated =
    { $count ->
        [one] Sorta { $count } eguneratuta.
       *[other] { $count } sorta eguneratuta.
    }
