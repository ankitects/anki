## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount } sek
scheduling-answer-button-time-minutes = { $amount } min
scheduling-answer-button-time-hours = { $amount } time
scheduling-answer-button-time-days = { $amount } dag
scheduling-answer-button-time-months = { $amount }mdr
scheduling-answer-button-time-years = { $amount } år

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] { $amount } sekund
       *[other] { $amount } sekunder
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] { $amount } minut
       *[other] { $amount } minutter
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } time
       *[other] { $amount } timer
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } dag
       *[other] { $amount } dage
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } måned
       *[other] { $amount } måneder
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } år
       *[other] { $amount } år
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    Næste kort bliver klart om { $unit ->
        [sekunder]
            { $amount ->
                [en] { $amount } sekund
               *[andet] { $amount } sekunder
            }
        [minutter]
            { $amount ->
                [en] { $amount } minut
               *[andet] { $amount } minutes
            }
       *[timer]
            { $amount ->
                [en] { $amount } time
               *[andet] { $amount } timer
            }
    }.
scheduling-learn-remaining =
    { $remaining ->
        [one] Der er et kort tilbage at lære i dag.
       *[other] Der er { $remaining } kort tilbage at lære i dag.
    }
scheduling-congratulations-finished = Tillykke! Du er færdig med dette kortsæt for nu.
scheduling-today-review-limit-reached =
    Dagens genopfrisknings-grænse er nået, men der er stadig kort
    der venter på at blive anmeldt. For at optimere hukommelsen
    bør du overveje at øge den daglige grænse.
scheduling-today-new-limit-reached =
    Der er flere nye kort tilgængelige, men den daglige 
    grænse er opbrugt. Du kan øge grænsen, men husk at
    jo flere kort du introducerer, jo flere genopfriskninger
    skal du foretage.
scheduling-buried-cards-found = Et eller flere kort er blevet begravet, og vises igen i morgen. Du kan { $unburyThem } hvis du ønsker at se dem med det samme.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = grav ned
scheduling-how-to-custom-study = Hvis du ønsker at studere uden for det almindelige skema, kan du bruge { $customStudy }.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = Brugerdefineret studium

## Scheduler upgrade

scheduling-update-soon = Anki 2.1 kommer med et nyt planlægningssystem, som ordner nogle kendt problemer med tidligere Anki-versioner. Det anbefales at opdatere.
scheduling-update-done = Skemaet opdateret.
scheduling-update-button = Opdatér
scheduling-update-later-button = Senere
scheduling-update-more-info-button = Lær mere
scheduling-update-required =
    Din kollektion behøver opgraderes til V2-skemaet.
    Vær venlig og vælg { scheduling-update-more-info-button } før du fortsætter.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = Inkluder altid spørgsmålssiden når lyden genspilles.
scheduling-at-least-one-step-is-required = Mindst et skridt er nødvendigt.
scheduling-automatically-play-audio = Afspil lyden automatisk
scheduling-bury-related-new-cards-until-the = Læg relaterede nye kort til side indtil næste dag
scheduling-bury-related-reviews-until-the-next = Læg relaterede genopfriskninger til side til næste dag
scheduling-days = dage
scheduling-description = Beskrivelse
scheduling-easy-bonus = Nem bonus
scheduling-easy-interval = Nemt interval
scheduling-end = (slut)
scheduling-general = Generelt
scheduling-graduating-interval = Gradueringsinterval
scheduling-hard-interval = Svært interval
scheduling-ignore-answer-times-longer-than = Ignorer svartider længere end
scheduling-interval-modifier = Interval-modifikator
scheduling-lapses = Udfald
scheduling-lapses2 = omgange
scheduling-learning = Indlæring
scheduling-leech-action = Igle-handling
scheduling-leech-threshold = Igle-grænseværdi
scheduling-maximum-interval = Maksimum-interval
scheduling-maximum-reviewsday = Maksimum for genopfriskninger/dag
scheduling-minimum-interval = Minimalt interval
scheduling-mix-new-cards-and-reviews = Bland nye kort med kort til genopfriskning
scheduling-new-cards = Nye kort
scheduling-new-cardsday = Nye kort / dag
scheduling-new-interval = Nyt interval
scheduling-new-options-group-name = Ny indstillings gruppenavn:
scheduling-options-group = Indstillingsgruppe
scheduling-order = Rækkefølge
scheduling-parent-limit = (overordnet grænse: { $val })
scheduling-reset-counts = Nulstil repetition og omgange.
scheduling-restore-position = Nulstil originalpositionen hvis muligt.
scheduling-review = Gennemgang
scheduling-reviews = Genopfriskninger
scheduling-seconds = sekunder
scheduling-set-all-decks-below-to = Indstil alle kortsæt nedenfor { $val } til denne tilvalgsgruppe?
scheduling-set-for-all-subdecks = Angiv for alle under-kortsæt
scheduling-show-answer-timer = Vis svartider
scheduling-show-new-cards-after-reviews = Vis nye kort efter genopfriskning
scheduling-show-new-cards-before-reviews = Vis nye kort før genopfriskninger
scheduling-show-new-cards-in-order-added = Vis nye kort i den rækkefølge de er tilføjet
scheduling-show-new-cards-in-random-order = Vis nye kort i tilfældig rækkefølge
scheduling-starting-ease = Sværhedsgrad ved start
scheduling-steps-in-minutes = Trin (i minutter)
scheduling-steps-must-be-numbers = Trin skal være tal.
scheduling-tag-only = Mærkat kun
scheduling-the-default-configuration-cant-be-removed = Standardkonfigurationen kan ikke fjernes.
scheduling-your-changes-will-affect-multiple-decks = Dine ændringer vil påvirke flere kortsæt. Hvis du blot ønsker at ændre den nuværende kortsæt, så skal du først tilføje en ny tilvalgsgruppe.
scheduling-deck-updated =
    { $count ->
        [one] { $count } kortsæt blev opdateret
       *[other] { $count } kortsæt blev opdateret
    }
scheduling-set-due-date-prompt =
    { $cards ->
        [one] Vis kort i hvor mange dage?
       *[other] Vis kort i hvor mange dage?
    }
scheduling-set-due-date-prompt-hint =
    0 = idag
    1! = i morgen + skift interval til 1
    3-7 = slumpmæssigt valg mellem 3-7 dage
scheduling-set-due-date-done =
    { $cards ->
        [one] Sæt sidste dato til { $cards } kort.
       *[other] Sæt sidste dato til { $cards } kort.
    }
scheduling-forgot-cards =
    { $cards ->
        [one] Glem { $cards } kort.
       *[other] Glem { $cards } kort.
    }
