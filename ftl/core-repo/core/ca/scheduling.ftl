## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount }s
scheduling-answer-button-time-minutes = { $amount }m
scheduling-answer-button-time-hours = { $amount }h
scheduling-answer-button-time-days = { $amount }d
scheduling-answer-button-time-months = { $amount }me
scheduling-answer-button-time-years = { $amount }a

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] Un segon
       *[other] { $amount } segons
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] Un minut
       *[other] { $amount } minuts
    }
scheduling-time-span-hours =
    { $amount ->
        [one] Una hora
       *[other] { $amount } hores
    }
scheduling-time-span-days =
    { $amount ->
        [one] Un dia
       *[other] { $amount } dies
    }
scheduling-time-span-months =
    { $amount ->
        [one] Un mes
       *[other] { $amount } mesos
    }
scheduling-time-span-years =
    { $amount ->
        [one] Un any
       *[other] { $amount } anys
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    La pròxima targeta estarà disponible en { $unit ->
        [seconds]
            { $amount ->
                [one] un segon
               *[other] { $amount } segons
            }
        [minutes]
            { $amount ->
                [one] un minut
               *[other] { $amount } minuts
            }
       *[hours]
            { $amount ->
                [one] una hora
               *[other] { $amount } hores
            }
    }.
scheduling-learn-remaining =
    { $remaining ->
        [one] Queda una targeta en la cua d'aprenentatge más tard per a avui.
       *[other] Queden { $remaining } targetes en la cua d'aprenentatge más tard per a avui.
    }
scheduling-congratulations-finished = Enhorabona! De moment heu acabat amb aquesta baralla.
scheduling-today-review-limit-reached =
    Heu arribat al límit diari de repassos, però encara hi ha targetes
    per repassar. Per a una memorització òptima, considereu
    augmentar el límit diari en la configuració.
scheduling-today-new-limit-reached = Hi ha més targetes noves, però heu arribat al límit diari. Podeu augmentar-lo en la configuració, però compte: com més targetes afegiu, més augmentarà la càrrega d’estudi a curt termini.
scheduling-buried-cards-found = Una o més targetes enterrades es mostraran demà. Podeu { $unburyThem } si voleu repassar-les ara.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = desenterrar-les
scheduling-how-to-custom-study = Si voleu estudiar fora de l’horari habitual, feu servir l’{ $customStudy }.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = estudi personalitzat

## Scheduler upgrade

scheduling-update-soon = Anki 2.1 conté un nou planificador que soluciona alguns problemes presents en versions anteriors d'Anki. Us recomanem que l'actualitzeu.
scheduling-update-done = S’ha actualitzat el planificador amb èxit.
scheduling-update-button = Actualitza
scheduling-update-later-button = Més tard
scheduling-update-more-info-button = Saber-ne més
scheduling-update-required =
    Heu d’actualitzar la vostra col·lecció a la versió 2 del planificador.
    Seleccioneu { scheduling-update-more-info-button } abans de continuar.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = Inclou sempre la cara de la pregunta quan se'n torni a reproduir el so
scheduling-at-least-one-step-is-required = Es requereix un pas com a mínim.
scheduling-automatically-play-audio = Reprodueix el so automàticament
scheduling-bury-related-new-cards-until-the = Enterra les targetes noves relacionades fins a l'endemà
scheduling-bury-related-reviews-until-the-next = Enterrar els repassos relacionats fins a l'endemà
scheduling-days = dies
scheduling-description = Descripció
scheduling-easy-bonus = Bonus per a Fàcil
scheduling-easy-interval = Interval per a Fàcil
scheduling-end = (fi)
scheduling-general = Opcions generals
scheduling-graduating-interval = Interval de graduació
scheduling-hard-interval = Interval dificil
scheduling-ignore-answer-times-longer-than = Ignoreu temps de resposta més llargs de
scheduling-interval-modifier = Modificació de l'interval
scheduling-lapses = Targetes oblidades
scheduling-lapses2 = oblidades
scheduling-learning = Aprenent
scheduling-leech-action = Acció per a les sangoneres
scheduling-leech-threshold = Llindar de sangoneres
scheduling-maximum-interval = Interval màxim
scheduling-maximum-reviewsday = Màxim de repassos per dia
scheduling-minimum-interval = Interval mínim
scheduling-mix-new-cards-and-reviews = Barreja targetes noves amb repassos
scheduling-new-cards = Targetes noves
scheduling-new-cardsday = Targetes noves per dia
scheduling-new-interval = Nou interval
scheduling-new-options-group-name = Nou nom del grup d'opcions:
scheduling-options-group = Opcions del grup:
scheduling-order = Ordre
scheduling-parent-limit = (límit superior: { $val })
scheduling-reset-counts = Reestableix els recomptes de repeticions i oblidades
scheduling-restore-position = Reestableix la posició original quan sigui possible
scheduling-review = Per repassar
scheduling-reviews = Repassos
scheduling-seconds = segons
scheduling-set-all-decks-below-to = Voleu assignar aquest grup d’opcions a totes les baralles per sota de { $val }?
scheduling-set-for-all-subdecks = Assigna a totes les baralles secundàries
scheduling-show-answer-timer = Mostra el temporitzador de resposta
scheduling-show-new-cards-after-reviews = Mostra les targetes noves després dels repassos
scheduling-show-new-cards-before-reviews = Mostra les targetes noves abans dels repassos
scheduling-show-new-cards-in-order-added = Mostra les targetes noves per ordre d’addició
scheduling-show-new-cards-in-random-order = Mostra les targetes noves de manera aleatòria
scheduling-starting-ease = Facilitat inicial
scheduling-steps-in-minutes = Passos (en minuts)
scheduling-steps-must-be-numbers = Els passos han de ser números.
scheduling-tag-only = Etiqueta la targeta
scheduling-the-default-configuration-cant-be-removed = No podeu eliminar la configuració per defecte.
scheduling-your-changes-will-affect-multiple-decks = Els canvis afectaran a més d’una baralla. Si únicament voleu modificar la baralla actual, afegiu primer un nou grup d’opcions.
scheduling-deck-updated =
    { $count ->
        [one] S’ha actualitzat una baralla.
       *[other] S’han actualitzat { $count } baralles.
    }
scheduling-set-due-date-prompt =
    { $cards ->
        [one] D'ací a quants dies voleu mostrar aquesta targeta?
       *[other] D'ací a quants dies voleu mostrar aquestes targetes?
    }
scheduling-set-due-date-prompt-hint =
    0 = avui
    1! = demà + canviar l'interval a 1
    3-7 = elecció aleatòria de 3 a 7 dies
scheduling-set-due-date-done =
    { $cards ->
        [one] Estableix la data de repàs d'una targeta.
       *[other] Estableix la data de repàs de { $cards } targetes.
    }
scheduling-graded-cards-done =
    { $cards ->
        [one] S’ha avaluat una targeta.
       *[other] S’han avaluat { $cards } targetes.
    }
scheduling-forgot-cards =
    { $cards ->
        [one] Heu oblidat una targeta.
       *[other] Heu oblidat { $cards } targetes.
    }
