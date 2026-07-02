## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount } s
scheduling-answer-button-time-minutes = { $amount } min
scheduling-answer-button-time-hours = { $amount } h
scheduling-answer-button-time-days = { $amount } d
scheduling-answer-button-time-months = { $amount } mon.
scheduling-answer-button-time-years = { $amount } a

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] { $amount } sekundo
       *[other] { $amount } sekundoj
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] { $amount } minuto
       *[other] { $amount } minutoj
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } horo
       *[other] { $amount } horoj
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } tago
       *[other] { $amount } tagoj
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } monato
       *[other] { $amount } monatoj
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } jaro
       *[other] { $amount } jaroj
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    { $unit ->
        [seconds]
            { $amount ->
                [one] La sekva karto por lerni estos disponebla post { $amount } sekundo.
               *[other] La sekva karto por lerni estos disponebla post { $amount } sekundoj.
            }
        [minutes]
            { $amount ->
                [one] La sekva karto por lerni estos disponebla post { $amount } minuto.
               *[other] La sekva karto por lerni estos disponebla post { $amount } minutoj.
            }
       *[hours]
            { $amount ->
                [one] La sekva karto por lerni estos disponebla post { $amount } horo.
               *[other] La sekva karto por lerni estos disponebla post { $amount } horoj.
            }
    }
scheduling-learn-remaining =
    { $remaining ->
        [one] Unu plia karto atendas hodiaŭan lernadon.
       *[other] { $remaining } pliaj kartoj atendas hodiaŭan lernadon.
    }
scheduling-congratulations-finished = Gratulon! Vi finis lernadon de tiu ĉi kartaro por hodiaŭ.
scheduling-today-review-limit-reached = La hodiaŭa limigo de ripetoj estas atingita, sed ankoraŭ ĉeestas ripetendaj kartoj. Por plej bone memorado pripensu altigi la tagan limigon en la agordoj.
scheduling-today-new-limit-reached = Ĉeestas ankoraŭ pli da kartoj, sed la taga limigo estis atingita. Vi povas pliigi la limigon en la agordoj, sed estu konscia, ke ju pli da kartoj vi enkondukos, des pli da kartoj vi devos ripeti dum la proksima tempo.
scheduling-buried-cards-found = Unu aŭ pliaj kartoj estas kaŝitaj por tago kaj estos montritaj morgaŭ. Vi povas { $unburyThem }, se vi volas ripeti ilin nun.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = malkaŝi ilin
scheduling-how-to-custom-study = Vi vi volas lerni ekster la norma lern-plano, vi povas aktivigi la agordon { $customStudy }.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = propra lernado

## Scheduler upgrade

scheduling-update-soon = La versio 2.1 de Anki liveras novan planilon, kiu riparas kelkajn problemojn ĉeestantajn en la antaŭaj versioj de Anki. Ĝisdatigo estas konsilinda.
scheduling-update-done = Sukcese ĝisdatigis la planilon.
scheduling-update-button = Ĝisdatigi
scheduling-update-later-button = Poste
scheduling-update-more-info-button = pliajn informojn
scheduling-update-required =
    Vi kolekto devas esti ĝisdatigita al la planilo de versio V2.
    Legu la { scheduling-update-more-info-button } antaŭ ol pluigi.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = Ĉiam inkluzivi la demandoflankon dum la reludo de sono
scheduling-at-least-one-step-is-required = Almenaŭ unu paŝo necesas.
scheduling-automatically-play-audio = Aŭtomate ludi sonon
scheduling-bury-related-new-cards-until-the = Kaŝi rilatajn novajn kartojn ĝis la venonta tago
scheduling-bury-related-reviews-until-the-next = Kaŝi rilatajn ripetojn ĝis la venonta tago
scheduling-days = tagoj
scheduling-description = Priskribo
scheduling-easy-bonus = Facileca premio
scheduling-easy-interval = Intertempo de facila respondo
scheduling-end = (fino)
scheduling-general = Ĝeneralaj
scheduling-graduating-interval = Intertempo por lernitaj kartoj
scheduling-hard-interval = Intertempo de malfacila respondo
scheduling-ignore-answer-times-longer-than = Ignori respondojn pli longajn ol
scheduling-interval-modifier = Modifilo de intertempo
scheduling-lapses = Misrespondoj
scheduling-lapses2 = misrespondoj
scheduling-learning = Lernado
scheduling-leech-action = Ago por forgesemaj kartoj
scheduling-leech-threshold = Sojlo de forgesemaj kartoj
scheduling-maximum-interval = Maksimuma intertempo
scheduling-maximum-reviewsday = Maksimumaj ripetoj/tago
scheduling-minimum-interval = Minimuma intertempo
scheduling-mix-new-cards-and-reviews = Miksi novajn kartojn kaj ripetojn
scheduling-new-cards = Novaj kartoj
scheduling-new-cardsday = Novaj kartoj/tago
scheduling-new-interval = Intertempo por nova karto
scheduling-new-options-group-name = Nomo de nova grupo de agordoj:
scheduling-options-group = Grupo de agordoj:
scheduling-order = Ordigo
scheduling-parent-limit = (limigo de supera kartaro: { $val })
scheduling-reset-counts = Nuligi nombrojn de ripetoj kaj misrespondoj
scheduling-restore-position = Restarigi originalan pozicion (se eblas)
scheduling-review = Ripetado
scheduling-reviews = Ripetoj
scheduling-seconds = sekundoj
scheduling-set-all-decks-below-to = Ĉu agordi ĉiujn kartarojn sub { $val } al tiu ĉi grupo de agordoj?
scheduling-set-for-all-subdecks = Agordi al ĉiuj subkartaroj
scheduling-show-answer-timer = Montri tempmezurilon
scheduling-show-new-cards-after-reviews = Montri novajn kartojn post ripetoj
scheduling-show-new-cards-before-reviews = Montri novajn kartojn antaŭ ripetoj
scheduling-show-new-cards-in-order-added = Montri novajn kartojn laŭ ordo de aldono
scheduling-show-new-cards-in-random-order = Montri novajn kartojn en hazarda ordo
scheduling-starting-ease = Komenca facileco
scheduling-steps-in-minutes = Paŝoj (en minutoj)
scheduling-steps-must-be-numbers = Paŝoj devas esti nombroj.
scheduling-tag-only = Aldoni nur etikedon
scheduling-the-default-configuration-cant-be-removed = La implicita agordaro ne povas esti forigita.
scheduling-your-changes-will-affect-multiple-decks = Viaj ŝanĝoj aplikiĝos al multaj kartaroj. Se vi nur volas ŝanĝi la aktualan kartaron, unue aldonu novan grupon de agordoj.
scheduling-deck-updated =
    { $count ->
        [one] { $count } kartaro estas ĝisdatigita.
       *[other] { $count } kartaroj estas ĝisdatigitaj.
    }
scheduling-set-due-date-prompt =
    { $cards ->
        [one] Post kiom da tagoj montri karton?
       *[other] Post kiom da tagoj montri kartojn?
    }
scheduling-set-due-date-prompt-hint =
    0 = hodiaŭ
    1! = morgaŭ + ŝanĝi intertempon al 1
    3–7 = elekti hazarde inter 3–7 tagoj
scheduling-set-due-date-done =
    { $cards ->
        [one] Agordis limdaton por { $cards } karto.
       *[other] Agordis limdatojn por { $cards } kartoj.
    }
scheduling-graded-cards-done =
    { $cards ->
        [one] Atribuis noton por { $cards } karto.
       *[other] Atribuis notojn por { $cards } kartoj.
    }
scheduling-forgot-cards =
    { $cards ->
        [one] Reagordis { $cards } karton.
       *[other] Reagordis { $cards } karotjn.
    }
