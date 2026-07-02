# The date a card will be ready to review
statistics-due-date = Forfaller
# The count of cards waiting to be reviewed
statistics-due-count = Forfaller
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = Ny #{ $number }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } kort/minutt
statistics-average-answer-time = { $average-seconds }s({ statistics-cards-per-min })

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds =
    { $amount ->
        [one] i { $amount } sekund
       *[other] i { $amount } sekunder
    }
statistics-in-time-span-minutes =
    { $amount ->
        [one] i { $amount } minutt
       *[other] i { $amount } minutter
    }
statistics-in-time-span-hours =
    { $amount ->
        [one] i { $amount } time
       *[other] i { $amount } timer
    }
statistics-in-time-span-days =
    { $amount ->
        [one] i { $amount } dag
       *[other] i { $amount } dager
    }
statistics-in-time-span-months =
    { $amount ->
        [one] i { $amount } måned
       *[other] i { $amount } måneder
    }
statistics-in-time-span-years =
    { $amount ->
        [one] i { $amount } år
       *[other] i { $amount } år
    }
statistics-cards =
    { $cards ->
        [one] { $cards } kort
       *[other] { $cards } kort
    }
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews =
    { $reviews ->
        [one] { $reviews } repetering
       *[other] { $reviews } repeteringer
    }
# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { statistics-in-time-span-seconds } part should be pasted in from the English
# version unmodified.
statistics-studied-today =
    Studert { statistics-cards }
    { $unit ->
        [seconds] { statistics-in-time-span-seconds }
        [minutes] { statistics-in-time-span-minutes }
        [hours] { statistics-in-time-span-hours }
        [days] { statistics-in-time-span-days }
        [months] { statistics-in-time-span-months }
       *[years] { statistics-in-time-span-years }
    } i dag
    ({ $secs-per-card }s/kort)
statistics-today-title = I dag
statistics-today-again-count = Gjentatt antall:
statistics-counts-new-cards = Ny
statistics-counts-young-cards = Ung
statistics-counts-mature-cards = Gamle
statistics-counts-suspended-cards = Deaktivert
statistics-counts-buried-cards = Begravde
statistics-range-deck = kortstokk
statistics-range-collection = samling
statistics-range-search = Søk
statistics-reviews-title = Gjennomganger
statistics-intervals-title = Intervaller
statistics-answer-buttons-title = Svarknapper
statistics-added-title = Lagt til
statistics-axis-label-answer-count = Svar
statistics-axis-label-card-count = Kort
statistics-reviews-time-checkbox = Tid
statistics-total = Totalt
statistics-days-studied = Dager studert
statistics-average-answer-time-label = Gjennomsnitlig svartid
statistics-average = Middels
statistics-due-tomorrow = Forfaller i morgen
statistics-average-over-period = Hvis du skulle studere hver dag
statistics-average-ease = Middels vanskelighetsgrad
statistics-save-pdf = Lagre PDF
statistics-saved = Lagret.
statistics-stats = statistikk
statistics-true-retention-young = Ung
statistics-true-retention-mature = Gamle
