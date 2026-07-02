# The date a card will be ready to review
statistics-due-date = Poteče
# The count of cards waiting to be reviewed
statistics-due-count = Preteklih
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = Novih #{ $number }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } kartic/minuto
statistics-average-answer-time = { $average-seconds } s ({ statistics-cards-per-min })

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds =
    { $amount ->
        [one] v { $amount } sekundi
        [two] v { $amount } sekundah
        [few] v { $amount } sekundah
       *[other] v { $amount } sekundah
    }
statistics-in-time-span-minutes =
    { $amount ->
        [one] v { $amount } minuti
        [two] v { $amount } minutah
        [few] v { $amount } minutah
       *[other] v { $amount } minutah
    }
statistics-in-time-span-hours =
    { $amount ->
        [one] v { $amount } uri
        [two] v { $amount } urah
        [few] v { $amount } urah
       *[other] v { $amount } urah
    }
statistics-in-time-span-days =
    { $amount ->
        [one] v { $amount } dnevu
        [two] v { $amount } dneh
        [few] v { $amount } dneh
       *[other] v { $amount } dneh
    }
statistics-in-time-span-months =
    { $amount ->
        [one] v { $amount } mesecu
        [two] v { $amount } mesecih
        [few] v { $amount } mesecih
       *[other] v { $amount } mesecih
    }
statistics-in-time-span-years =
    { $amount ->
        [one] v { $amount } letu
        [two] v { $amount } letih
        [few] v { $amount } letih
       *[other] v { $amount } letih
    }
statistics-cards =
    { $cards ->
        [one] { $cards } kartica
        [two] { $cards } kartici
        [few] { $cards } kartice
       *[other] { $cards } kartic
    }
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews =
    { $reviews ->
        [one] { $reviews } pregledov
        [two] { $reviews } pregled
        [few] { $reviews } pregleda
       *[other] { $reviews } pregledi
    }
# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { statistics-in-time-span-seconds } part should be pasted in from the English
# version unmodified.
statistics-studied-today =
    Pregledanih { statistics-cards }
    { $unit ->
        [seconds] { statistics-in-time-span-seconds }
        [minutes] { statistics-in-time-span-minutes }
        [hours] { statistics-in-time-span-hours }
        [days] { statistics-in-time-span-days }
        [months] { statistics-in-time-span-months }
       *[years] { statistics-in-time-span-years }
    } danes
    ({ $secs-per-card } sek/kartico)
statistics-today-title = Danes
statistics-today-again-count = Ponovno štetje:
statistics-today-type-counts = Učenje: { $learnCount }, Pregled: { $reviewCount }, Ponovno učenje: { $relearnCount }, Filtrirano: { $filteredCount }
statistics-today-no-cards = Danes niste pregledovali kartic.
statistics-today-no-mature-cards = Danes niste pregledovali "zrelih" kartic.
statistics-today-correct-mature = Pravilnih odgovorov na "zrelih" karticah: { $correct }/{ $total } ({ $percent }%)
statistics-counts-total-cards = Skupaj kartic
statistics-counts-new-cards = Novo
statistics-counts-young-cards = Sveže
statistics-counts-mature-cards = Zrel
statistics-counts-suspended-cards = Suspendirane
statistics-counts-buried-cards = Zakopane
statistics-counts-filtered-cards = Filtrirane
statistics-counts-learning-cards = Učenje
statistics-counts-relearning-cards = Ponovno učenje
statistics-counts-title = Števci kartic
statistics-counts-separate-suspended-buried-cards = Ločene suspendirane/zakopane kartice
statistics-range-all-time = vse
statistics-range-1-year-history = zadnjih 12 mesecev
statistics-range-all-history = vsa zgodovina
statistics-range-deck = paket
statistics-range-collection = zbirka
statistics-range-search = Iskanje
statistics-card-ease-title = Težavnost kartic
statistics-card-difficulty-title = Težavnost kartice
statistics-card-stability-title = Stabilnost kartice
statistics-card-stability-subtitle = Razmik, pri katerem bo pomnjenje najverjetneje 90-odstotno.
statistics-average-stability = Povprečna stabilnost
statistics-card-retrievability-title = Povratnost kartice
statistics-card-ease-subtitle = Večja kot je težavnost, bolj pogosto se bo kartica pojavila.
statistics-card-difficulty-subtitle2 = Višja kot je težavnost, počasneje bo naraščala stabilnost.
statistics-retrievability-subtitle = Verjetnost ponovnega priklica kartice danes.
# eg "3 cards with 150-170% ease"
statistics-card-ease-tooltip =
    { $cards ->
        [one] { $cards } kartica s težavnostjo { $percent }
        [two] { $cards } kartici s težavnostjo { $percent }
        [few] { $cards } kartice s težavnostjo { $percent }
       *[other] { $cards } kartic s težavnostjo { $percent }
    }
statistics-card-difficulty-tooltip =
    { $cards ->
        [one] { $cards } kartica z { $percent } težavnostjo
        [two] { $cards } kartici z { $percent } težavnostjo
        [few] { $cards } kartice z { $percent } težavnostjo
       *[other] { $cards } kartic z { $percent } težavnostjo
    }
statistics-retrievability-tooltip =
    { $cards ->
        [one] { $cards } kartica z { $percent } povratnostjo
        [two] { $cards } kartici z { $percent } povratnostjo
        [few] { $cards } kartice z { $percent } povratnostjo
       *[other] { $cards } kartic z { $percent } povratnostjo
    }
statistics-future-due-title = Napoved
statistics-future-due-subtitle = Število pregledov, ki bodo na vrsti v prihodnje.
statistics-added-title = Dodano
statistics-added-subtitle = Število dodanih novih kartic.
statistics-reviews-count-subtitle = Število vprašanj, ki ste jih odgovorili.
statistics-reviews-time-subtitle = Čas porabljen za odgovore na vprašanja.
statistics-answer-buttons-title = Gumbi z odgovori
# eg Button: 4
statistics-answer-buttons-button-number = Gumb
# eg Times pressed: 123
statistics-answer-buttons-button-pressed = Število pritiskov
statistics-answer-buttons-subtitle = Število klikov na vsak gumb.
statistics-reviews-title = Pregledi
statistics-reviews-time-checkbox = Čas
statistics-in-days-single =
    { $days ->
        [0] Danes
        [1] Jutri
        [one] V { $days } dnevu
        [two] V { $days } dneh
        [few] V { $days } dneh
       *[other] V { $days } dneh
    }
statistics-in-days-range = V { $daysStart }-{ $daysEnd } dnevih
statistics-days-ago-single =
    { $days ->
        [1] Včeraj
        [one] Pred { $days } dnevom
        [two] Pred { $days } dnevoma
        [few] Pred { $days } dnevi
       *[other] Pred { $days } dnevi
    }
statistics-days-ago-range = Pred { $daysStart }-{ $daysEnd } dnevi
statistics-running-total = Skupni čas
statistics-cards-due =
    { $cards ->
        [one] Poteklo kartic: { $cards }
        [two] Poteklo kartic: { $cards }
        [few] Poteklo kartic: { $cards }
       *[other] Poteklo kartic: { $cards }
    }
statistics-backlog-checkbox = Dnevnik zapisov
statistics-intervals-title = Intervali
statistics-intervals-subtitle = Odloži dokler se spet ne pokažejo pregledi.
statistics-intervals-day-range =
    { $cards ->
        [one] { $cards } kartica z intervalom { $daysStart }~{ $daysEnd } dni
        [two] { $cards } kartici z intervalom { $daysStart }~{ $daysEnd } dni
        [few] { $cards } kartice z intervalom { $daysStart }~{ $daysEnd } dni
       *[other] { $cards } kartic z intervalom { $daysStart }~{ $daysEnd } dni
    }
statistics-intervals-day-single =
    { $cards ->
        [one] { $cards } kartica z intervalom { $day } dni
        [two] { $cards } kartici z intervalom { $day } dni
        [few] { $cards } kartice z intervalom { $day } dni
       *[other] { $cards } kartic z intervalom { $day } dni
    }
statistics-stability-day-range =
    { $cards ->
        [one] { $cards } kartica s stabilnostjo { $daysStart }~{ $daysEnd } dni
        [two] { $cards } kartici s stabilnostjo { $daysStart }~{ $daysEnd } dni
        [few] { $cards } kartice s stabilnostjo { $daysStart }~{ $daysEnd } dni
       *[other] { $cards } kartic s stabilnostjo { $daysStart }~{ $daysEnd } dni
    }
statistics-stability-day-single =
    { $cards ->
        [one] { $cards } kartica s stabilnostjo { $day } dni
        [two] { $cards } kartici s stabilnostjo { $day } dni
        [few] { $cards } kartice s stabilnostjo { $day } dni
       *[other] { $cards } kartic s stabilnostjo { $day } dni
    }
# hour range, eg "From 14:00-15:00"
statistics-hours-range = Od { $hourStart }:00~{ $hourEnd }:00
statistics-hours-correct = { $correct }/{ $total } pravilnih ({ $percent }%)
# the emoji depicts the graph displaying this number
statistics-hours-reviews = { $reviews } pregledov
# the emoji depicts the graph displaying this number
statistics-hours-correct-reviews = { $percent }% pravilnih ({ $reviews })
statistics-hours-title = Razčlenitev po urah
statistics-hours-subtitle = Uspešnost pregleda za vse ure dneva.
# shown when graph is empty
statistics-no-data = NI PODATKOV
statistics-calendar-title = Koledar

## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.

statistics-elapsed-time-seconds = { $amount } sek
statistics-elapsed-time-minutes = { $amount } mes
statistics-elapsed-time-hours = { $amount } h
statistics-elapsed-time-days = { $amount } dni
statistics-elapsed-time-months = { $amount } mes.
statistics-elapsed-time-years = { $amount } let

##

statistics-average-for-days-studied = Povprečje za dneve študija
statistics-total = Skupaj
statistics-days-studied = Dnevi študija
statistics-average-answer-time-label = Povprečen čas za odgovor
statistics-average = Povprečje
statistics-average-interval = Povprečni interval
statistics-due-tomorrow = Zapadejo jutri
# eg 5 of 15 (33.3%)
statistics-amount-of-total-with-percentage = { $amount } od { $total } ({ $percent }%)
statistics-average-over-period = Če bi študirali vsak dan
statistics-reviews-per-day =
    { $count ->
        [one] { $count } pregled/dan
        [two] { $count } pregleda/dan
        [few] { $count } pregledi/dan
       *[other] { $count } pregledov/dan
    }
statistics-minutes-per-day =
    { $count ->
        [one] minut/dan: { $count }
        [two] minut/dan: { $count }
        [few] minut/dan: { $count }
       *[other] minut/dan: { $count }
    }
statistics-cards-per-day =
    { $count ->
        [one] kartic/dan: { $count }
        [two] kartic/dan: { $count }
        [few] kartic/dan: { $count }
       *[other] kartic/dan: { $count }
    }
statistics-average-ease = Povprečna enostavnost
statistics-average-difficulty = Povprečna težavnost
statistics-average-retrievability = Povprečna povratnost
statistics-save-pdf = Shrani PDF
statistics-saved = Shranjeno.
statistics-stats = statistika
statistics-title = Statistike
statistics-true-retention-total = Skupaj kartic
statistics-true-retention-young = Sveže
statistics-true-retention-mature = Zrel
