## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount } s
scheduling-answer-button-time-minutes = { $amount } min
scheduling-answer-button-time-hours = { $amount } h
scheduling-answer-button-time-days = { $amount } d.
scheduling-answer-button-time-months = { $amount } měs.
scheduling-answer-button-time-years = { $amount } r.

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] { $amount } sekunda
        [few] { $amount } sekundy
        [many] { $amount } sekundy
       *[other] { $amount } sekund
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] { $amount } minuta
        [few] { $amount } minuty
        [many] { $amount } minuty
       *[other] { $amount } minut
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } hodina
        [few] { $amount } hodiny
        [many] { $amount } hodiny
       *[other] { $amount } hodin
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } den
        [few] { $amount } dny
        [many] { $amount } dne
       *[other] { $amount } dní
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } měsíc
        [few] { $amount } měsíce
        [many] { $amount } měsíce
       *[other] { $amount } měsíců
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } rok
        [few] { $amount } roky
        [many] { $amount } roku
       *[other] { $amount } let
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    Další karta k učení bude připravena za { $unit ->
        [seconds]
            { $amount ->
                [one] { $amount } sekundu
                [few] { $amount } sekundy
                [many] { $amount } sekundy
               *[other] { $amount } sekund
            }
        [minutes]
            { $amount ->
                [one] { $amount } minutu
                [few] { $amount } minuty
                [many] { $amount } minuty
               *[other] { $amount } minut
            }
       *[hours]
            { $amount ->
                [one] { $amount } hodinu
                [few] { $amount } hodiny
                [many] { $amount } hodiny
               *[other] { $amount } hodin
            }
    }.
scheduling-learn-remaining =
    { $remaining ->
        [one] Ještě dnes bude dostupná jedna zbývající karta k učení.
        [few] Ještě dnes budou dostupné { $remaining } zbývající karty k učení.
       *[other] Ještě dnes bude dostupných { $remaining } zbývajících karet k učení.
    }
scheduling-congratulations-finished = Gratuluji! Tento balíček máte prozatím hotov.
scheduling-today-review-limit-reached =
    Byl dosažen denní limit, ale stále vám zbývají karty k opakování. Zvažte
    zvýšení denního limitu pro lepší zapamatování.
scheduling-today-new-limit-reached =
    Zbývají vám další nové karty, ale byl dosažen denní limit. Můžete ho
    zvýšit, ale mějte na paměti, že čím víc nových karet naráz, tím víc
    opakování.
scheduling-buried-cards-found = Jedna nebo více karet byly přeskočeny a budou zobrazeny zítra. Můžete { $unburyThem }, jestli chcete, abyste je viděli okamžitě.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = zrušit přeskočení
scheduling-how-to-custom-study = Jestliže chcete studovat mimo pravidelný plán, můžete použít funkci { $customStudy }.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = vlastní studium

## Scheduler upgrade

scheduling-update-soon = Anki 2.1 přináší nový plánovač, který opravuje několik problémů, které měly předchozí verze Anki. Aktualizace se doporučuje.
scheduling-update-done = Plánovač úspěšně aktualizován.
scheduling-update-button = Aktualizovat
scheduling-update-later-button = Později
scheduling-update-more-info-button = Dozvědět se více
scheduling-update-required =
    Vaše kolekce se musí povýšit na V2 plánovač.
    Než budete pokračovat, prosím vyberte { scheduling-update-more-info-button }.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = Vždy zahrnout stranu s otázkou při přehrávání zvuku
scheduling-at-least-one-step-is-required = Je vyžadován alespoň jeden krok.
scheduling-automatically-play-audio = Automaticky přehrát zvuk
scheduling-bury-related-new-cards-until-the = Přeskočit příbuzné nové karty do příštího dne
scheduling-bury-related-reviews-until-the-next = Přeskočit příbuzná opakování do příštího dne
scheduling-days = dny/dní
scheduling-description = Popisek
scheduling-easy-bonus = Bonus pro snadné
scheduling-easy-interval = Interval pro snadné
scheduling-end = (konec)
scheduling-general = Základní
scheduling-graduating-interval = Interval absolvování
scheduling-hard-interval = Interval pro těžké
scheduling-ignore-answer-times-longer-than = Ignorovat odpovědi trvající déle než
scheduling-interval-modifier = Modifikátor intervalu
scheduling-lapses = Chyby
scheduling-lapses2 = chyb
scheduling-learning = Učení
scheduling-leech-action = Akce pro pijavice
scheduling-leech-threshold = Práh pro pijavice
scheduling-maximum-interval = Maximální interval
scheduling-maximum-reviewsday = Maximum opakování za den
scheduling-minimum-interval = Minimální interval
scheduling-mix-new-cards-and-reviews = Smíchat nové karty a opakování
scheduling-new-cards = Nové karty
scheduling-new-cardsday = Nové karty za den
scheduling-new-interval = Nový interval
scheduling-new-options-group-name = Nový název skupiny nastavení:
scheduling-options-group = Skupina voleb:
scheduling-order = Pořadí
scheduling-parent-limit = (rodičovský limit: { $val })
scheduling-reset-counts = Vynulovat počty opakování a chyb
scheduling-restore-position = Obnovit původní pořadí, je-li to možné
scheduling-review = Opakování
scheduling-reviews = Počet opakování
scheduling-seconds = sekund
scheduling-set-all-decks-below-to = Přesunout všechny balíčky pod { $val } do téhle skupiny?
scheduling-set-for-all-subdecks = Nastavit pro všechny podřízené balíčky
scheduling-show-answer-timer = Zobrazovat čas odpovědi
scheduling-show-new-cards-after-reviews = Zobrazit nové karty až po opakování
scheduling-show-new-cards-before-reviews = Zobraz nové karty před opakováním
scheduling-show-new-cards-in-order-added = Zobrazit nové karty v pořadí, v jakém byly přidány
scheduling-show-new-cards-in-random-order = Zobrazit nové karty v náhodném pořadí
scheduling-starting-ease = Počáteční 'snadnost'
scheduling-steps-in-minutes = Kroky (v minutách)
scheduling-steps-must-be-numbers = Kroky musí být v číslech.
scheduling-tag-only = Pouze označit štítkem
scheduling-the-default-configuration-cant-be-removed = Výchozí konfiguraci nelze odstranit.
scheduling-your-changes-will-affect-multiple-decks = Vaše změny ovlivní vícero balíčků. Pokud chcete změnit pouze současný balíček, přidejte nejdřív novou skupinu nastavení.
scheduling-deck-updated =
    { $count ->
        [one] { $count } balíček aktualizován.
        [few] { $count } balíčky aktualizovány.
       *[other] { $count } balíčků aktualizováno.
    }
scheduling-set-due-date-prompt =
    { $cards ->
        [one] Zobrazit kartu za kolik dní?
        [few] Zobrazit karty za kolik dní?
       *[other] Zobrazit karty za kolik dní?
    }
scheduling-set-due-date-prompt-hint =
    0 = dnes
    1! = zítra+obnovit interval opakování
    3-7 = náhodný výběr 3-7 dní
scheduling-set-due-date-done =
    { $cards ->
        [one] Nastavit datum zkoušení { $cards } karty.
        [few] Nastavit datum zkoušení { $cards } karet.
       *[other] Nastavit datum zkoušení { $cards } karet.
    }
scheduling-graded-cards-done =
    { $cards ->
        [one] Zodpovězena { $cards } karta.
        [few] Zodpovězeny { $cards } karty.
        [many] Zodpovězeno { $cards } karty.
       *[other] Zodpovězeno { $cards } karet.
    }
scheduling-forgot-cards =
    { $cards ->
        [one] Zapomenuta { $cards } karta.
        [few] Zapomenuty { $cards } karty.
       *[other] Zapomenuto { $cards } karet.
    }
