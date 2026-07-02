## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount } s
scheduling-answer-button-time-minutes = { $amount } min
scheduling-answer-button-time-hours = { $amount } tim
scheduling-answer-button-time-days = { $amount } d
scheduling-answer-button-time-months = { $amount } mån
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
       *[other] { $amount } minuter
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } timme
       *[other] { $amount } timmar
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } dag
       *[other] { $amount } dagar
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } månad
       *[other] { $amount } månader
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } år
       *[other] { $amount } år
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    { $unit ->
        [seconds]
            { $amount ->
                [one] Nästa inlärningskort kommer vara redo om { $amount } sekund.
               *[other] Nästa inlärningskort kommer vara redo om { $amount } sekunder.
            }
        [minutes]
            { $amount ->
                [one] Nästa inlärningskort kommer vara redo om { $amount } minut.
               *[other] Nästa inlärningskort kommer vara redo om { $amount } minuter.
            }
       *[hours]
            { $amount ->
                [one] Nästa inlärningskort kommer vara redo om { $amount } timme.
               *[other] Nästa inlärningskort kommer vara redo om { $amount } timmar.
            }
    }
scheduling-learn-remaining =
    { $remaining ->
        [one] Det finns ett återstående inlärningskort som förfaller senare idag.
       *[other] Det finns { $remaining } återstående inlärningskort som förfaller senare idag.
    }
scheduling-congratulations-finished = Grattis! Du är klar med den här kortleken för idag.
scheduling-today-review-limit-reached =
    Gränsen för hur många kort du får repetera per dag är nådd,
    men det finns fortfarande kort att repetera. För optimal inlärning
    överväg att öka gränsen i inställningarna för den här gruppen.
scheduling-today-new-limit-reached =
    Det finns flera nya kort tillgängliga, men gränsen för antalet nya kort
    per dag är nådd. Du kan ändra detta i inställningarna, men
    kom ihåg att ju fler nya kort du för in, desto tyngre
    blir arbetsbördan med fler repetitioner under den närmsta tiden.
scheduling-buried-cards-found = Ett eller flera kort doldes och kommer visas imorgon. Det går att { $unburyThem } om de önskas framställas omedelbart.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = visa dem
scheduling-how-to-custom-study = För att studera utöver de schemalagda korten kan funktionen { $customStudy } användas.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = anpassade studier

## Scheduler upgrade

scheduling-update-soon = Anki 2.1 kommer med en ny schemaläggare som fixar ett antal fel som tidigare Ankiversioner hade. Uppdatering till den rekommenderas.
scheduling-update-done = Schemaläggare uppdaterades framgångsrikt.
scheduling-update-button = Uppdatera
scheduling-update-later-button = Senare
scheduling-update-more-info-button = Läs mer
scheduling-update-required =
    Samlingen måste uppgraderas till V2-schemaläggaren.
    Var god välj { scheduling-update-more-info-button } innan vidare åtgärd.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = Inkludera alltid frågosidan när ljud spelas igen
scheduling-at-least-one-step-is-required = Åtminstone ett steg krävs.
scheduling-automatically-play-audio = Spela upp ljud automatiskt
scheduling-bury-related-new-cards-until-the = Dölj relaterade nya kort tills nästa dag
scheduling-bury-related-reviews-until-the-next = Dölj relaterade repetitioner till nästa dag
scheduling-days = dagar
scheduling-description = Beskrivning
scheduling-easy-bonus = Enkel bonus
scheduling-easy-interval = Lätt intervall
scheduling-end = (slut)
scheduling-general = Allmänt
scheduling-graduating-interval = Befordringsintervall
scheduling-hard-interval = Hårt intervall
scheduling-ignore-answer-times-longer-than = Ignorera svarstider längre än
scheduling-interval-modifier = Intervallfaktor
scheduling-lapses = Bortglömningar
scheduling-lapses2 = bortglömningar
scheduling-learning = Inlärning
scheduling-leech-action = Åtgärd för energislukare
scheduling-leech-threshold = Tröskelvärde för energislukare
scheduling-maximum-interval = Största intervall
scheduling-maximum-reviewsday = Maximalt antal repetitioner/dag
scheduling-minimum-interval = Minsta intervall
scheduling-mix-new-cards-and-reviews = Blanda nya kort och repetitioner
scheduling-new-cards = Nya kort
scheduling-new-cardsday = Nya kort/dag
scheduling-new-interval = Nytt intervall
scheduling-new-options-group-name = Namn på ny alternativgrupp:
scheduling-options-group = Alternativgrupp:
scheduling-order = Ordning
scheduling-parent-limit = (gräns för överordnad: { $val })
scheduling-reset-counts = Återställ repetitions- och bortglömningsantal
scheduling-restore-position = Återställ ursprunglig position där möjligt
scheduling-review = Repetera
scheduling-reviews = Repetitioner
scheduling-seconds = sekunder
scheduling-set-all-decks-below-to = Låt alla kortlekar under { $val } använda denna alternativgrupp?
scheduling-set-for-all-subdecks = Ställ in för alla underkortlekar
scheduling-show-answer-timer = Visa svarstimer
scheduling-show-new-cards-after-reviews = Visa nya kort efter repetitioner
scheduling-show-new-cards-before-reviews = Visa nya kort innan repetitioner
scheduling-show-new-cards-in-order-added = Visa nya kort i den ordning de lades till
scheduling-show-new-cards-in-random-order = Visa nya kort i slumpmässig ordning
scheduling-starting-ease = Ursprunglig lätthet
scheduling-steps-in-minutes = Steg (i minuter)
scheduling-steps-must-be-numbers = Steg måste vara siffror
scheduling-tag-only = Etikettera endast
scheduling-the-default-configuration-cant-be-removed = Den förvalda konfigurationen kan inte tas bort.
scheduling-your-changes-will-affect-multiple-decks = Dina ändringar kommer påverka flera kortlekar. Om du endast vill ändra nuvarande kortlek, var god lägg till en ny alternativgrupp först.
scheduling-deck-updated =
    { $count ->
        [one] { $count } kortlek uppdaterad.
       *[other] { $count } kortlekar uppdaterade.
    }
scheduling-set-due-date-prompt =
    { $cards ->
        [one] Visa kort om hur många dagar?
       *[other] Visa kort om hur många dagar?
    }
scheduling-set-due-date-prompt-hint =
    0 = idag
    1! = imorgon + ändra intervall till 1
    3-7 = slumpat val av 3-7 dagar
scheduling-set-due-date-done =
    { $cards ->
        [one] Satte förfallodatum för { $cards } kort.
       *[other] Satte förfallodatum för { $cards } kort.
    }
scheduling-graded-cards-done =
    { $cards ->
        [one] Graderade { $cards } kort.
       *[other] Graderade { $cards } kort.
    }
scheduling-forgot-cards =
    { $cards ->
        [one] Återställ { $cards } kort.
       *[other] Återställ { $cards } kort.
    }
