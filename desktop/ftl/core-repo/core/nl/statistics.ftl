statistics-due-date = Verwacht
statistics-due-count = Verwacht
statistics-cards-per-min = { $cards-per-minute } kaarten/minuut
statistics-in-time-span-seconds =
    { $amount ->
        [one] in { $amount } seconde
       *[other] in { $amount } seconden
    }
statistics-in-time-span-minutes =
    { $amount ->
        [one] in { $amount } minuut
       *[other] in { $amount } minuten
    }
statistics-in-time-span-hours =
    { $amount ->
        [one] in { $amount } uur
       *[other] in { $amount } uren
    }
statistics-in-time-span-days =
    { $amount ->
        [one] in { $amount } dag
       *[other] in { $amount } dagen
    }
statistics-in-time-span-months =
    { $amount ->
        [one] in { $amount } maand
       *[other] in { $amount } maanden
    }
statistics-in-time-span-years =
    { $amount ->
        [one] in { $amount } jaar
       *[other] in { $amount } jaren
    }
statistics-studied-today =
    { statistics-cards } { $unit ->
        [seconds] { statistics-in-time-span-seconds }
        [minutes] { statistics-in-time-span-minutes }
        [hours] { statistics-in-time-span-hours }
        [days] { statistics-in-time-span-days }
        [months] { statistics-in-time-span-months }
       *[years] { statistics-in-time-span-years }
    } vandaag geleerd ({ $secs-per-card }s/card)
statistics-cards =
    { $cards ->
        [one] { $cards } kaart
       *[other] { $cards } kaarten
    }
statistics-reviews =
    { $reviews ->
        [one] { $reviews } herhaling
       *[other] { $reviews } herhalingen
    }
statistics-today-title = Vandaag
statistics-today-again-count = Aantal te herdoen:
statistics-today-type-counts = Leren: { $learnCount }, Herhalen: { $reviewCount }, Opnieuw leren: { $relearnCount }, Gefilterd: { $filteredCount }
statistics-today-no-cards = Er zijn vandaag geen kaarten geleerd.
statistics-today-no-mature-cards = Er zijn vandaag geen volwassen kaarten geleerd.
statistics-today-correct-mature = Juiste antwoorden voor volwassen kaarten: { $correct }/{ $total } ({ $percent }%)
statistics-counts-total-cards = Totaal aantal kaarten
statistics-counts-new-cards = Nieuw
statistics-counts-young-cards = Jong
statistics-counts-mature-cards = Volwassen
statistics-counts-suspended-cards = Opgeschort
statistics-counts-buried-cards = Begraven
statistics-range-all-time = levensduur set
statistics-range-deck = set
statistics-range-collection = collectie
statistics-range-search = Zoeken
statistics-future-due-title = Voorspelling
statistics-reviews-title = Herhalingen
statistics-intervals-title = Intervallen
statistics-answer-buttons-title = Antwoordknoppen
statistics-hours-title = Verdeling per uur
statistics-added-title = Toegevoegd
statistics-axis-label-answer-count = Antwoorden
statistics-axis-label-card-count = Kaarten
statistics-axis-label-review-time = Herhalingstijd
statistics-future-due-subtitle = Het aantal op komst zijnde herhalingen.
statistics-added-subtitle = Het aantal nieuwe kaarten dat u heeft toegevoegd.
statistics-reviews-count-subtitle = Het aantal vragen dat u beantwoord hebt.
statistics-reviews-time-subtitle = De tijd die u nam om vragen te beantwoorden.
statistics-intervals-subtitle = Vertragingen totdat herhalingen weer getoond worden.
statistics-answer-buttons-subtitle = Het aantal keer dat u de verschillende knoppen heeft ingedrukt.
statistics-hours-subtitle = Succespercentage voor elk uur van de dag bekijken.
statistics-counts-learning-cards = Aan het leren
statistics-reviews-time-checkbox = Tijd
statistics-average-for-days-studied = Gemiddelde voor dagen waarop je geleerd hebt
statistics-total = Totaal
statistics-days-studied = Dagen geleerd
statistics-average-answer-time-label = Gemiddelde antwoordtijd
statistics-average = Gemiddelde
statistics-average-interval = Gemiddeld interval
statistics-longest-interval = Langste interval
statistics-due-tomorrow = Morgen verwacht
statistics-average-over-period = Als je elke dag had geleerd
statistics-average-ease = Gemiddelde gemak
statistics-save-pdf = Opslaan als PDF
statistics-saved = Opgeslagen.
statistics-stats = statistieken
statistics-true-retention-total = Totaal aantal kaarten
statistics-true-retention-young = Jong
statistics-true-retention-mature = Volwassen
