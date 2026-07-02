# The date a card will be ready to review
statistics-due-date = Per repassar
# The count of cards waiting to be reviewed
statistics-due-count = Pendents
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card =
    { $number ->
        [one] Nova #{ $number }
       *[other] Noves #{ $number }
    }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } targetes per minut
statistics-average-answer-time = { $average-seconds }s ({ statistics-cards-per-min })

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds =
    { $amount ->
        [one] en { $amount } segon
       *[other] en { $amount } segons
    }
statistics-in-time-span-minutes =
    { $amount ->
        [one] en { $amount } minut
       *[other] en { $amount } minuts
    }
statistics-in-time-span-hours =
    { $amount ->
        [one] en { $amount } hora
       *[other] en { $amount } hores
    }
statistics-in-time-span-days =
    { $amount ->
        [one] en { $amount } dia
       *[other] en { $amount } dies
    }
statistics-in-time-span-months =
    { $amount ->
        [one] en un mes
       *[other] en { $amount } mesos
    }
statistics-in-time-span-years =
    { $amount ->
        [one] en { $amount } any
       *[other] en { $amount } anys
    }
# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { statistics-in-time-span-seconds } part should be pasted in from the English
# version unmodified.
statistics-studied-today =
    Avui heu estudiat { statistics-cards } { $unit ->
        [seconds] { statistics-in-time-span-seconds }
        [minutes] { statistics-in-time-span-minutes }
        [hours] { statistics-in-time-span-hours }
        [days] { statistics-in-time-span-days }
        [months] { statistics-in-time-span-months }
       *[years] { statistics-in-time-span-years }
    } ({ $secs-per-card } segons per targeta).

##

statistics-cards =
    { $cards ->
        [one] { $cards } targeta
       *[other] { $cards } targetes
    }
statistics-notes =
    { $notes ->
        [one] Una nota
       *[other] { $notes } notes
    }
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews =
    { $reviews ->
        [one] { $reviews } repàs
       *[other] { $reviews } repassos
    }
# This fragment of the tooltip in the FSRS simulation
# diagram (Deck options -> FSRS) shows the total number of
# cards that can be recalled or retrieved on a specific date.
statistics-memorized = { $memorized } memoritzades
statistics-today-title = Avui
statistics-today-again-count = Oblidades:
statistics-today-type-counts = Apreses: { $learnCount }, Repassades: { $reviewCount }, Tornades a aprendre: { $relearnCount }, Filtrades: { $filteredCount }
statistics-today-no-cards = Avui no heu estudiat cap targeta.
statistics-today-no-mature-cards = Avui no heu estudiat cap targeta madura.
statistics-today-correct-mature = Respostes correctes a targetes madures: { $correct }/{ $total } ({ $percent } %)
statistics-counts-total-cards = Nombre total de targetes
statistics-counts-new-cards = Noves
statistics-counts-young-cards = Joves
statistics-counts-mature-cards = Madures
statistics-counts-suspended-cards = Suspeses
statistics-counts-buried-cards = Enterrades
statistics-counts-filtered-cards = Filtrades
statistics-counts-learning-cards = Aprenent
statistics-counts-relearning-cards = Reaprenent
statistics-counts-title = Totes les targetes
statistics-counts-separate-suspended-buried-cards = Separa les targetes suspeses o enterrades

## Retention represents your actual retention from past reviews, in
## comparison to the "desired retention" setting of FSRS, which forecasts
## future retention. Retention is the percentage of all reviewed cards
## that were marked as "Hard," "Good," or "Easy" within a specific time period.
##
## Most of these strings are used as column / row headings in a table.
## (Excluding -title and -subtitle)
## It is important to keep these translations short so that they do not make
## the table too large to display on a single stats card.
##
## N.B. Stats cards may be very small on mobile devices and when the Stats
##      window is certain sizes.

statistics-true-retention-title = Retenció vertadera
statistics-true-retention-subtitle = Percentatge d’encerts de les targetes amb un interval ≥ 1 dia.
statistics-true-retention-tooltip = Si feu servir l’FSRS, la vostra retenció ha d’acostar-se a la retenció desitjada. Tingueu en compte que les dades d’un sol dia són poc fiables, així que és millor observar les dades mensuals.
statistics-true-retention-range = Interval
statistics-true-retention-pass = Aprovades
statistics-true-retention-fail = Suspeses
# This will usually be the same as statistics-counts-total-cards
statistics-true-retention-total = Nombre total de targetes
statistics-true-retention-count = Total
statistics-true-retention-retention = Retenció
# This will usually be the same as statistics-counts-young-cards
statistics-true-retention-young = Joves
# This will usually be the same as statistics-counts-mature-cards
statistics-true-retention-mature = Madures
statistics-true-retention-all = Totes
statistics-true-retention-today = Avui
statistics-true-retention-yesterday = Ahir
statistics-true-retention-week = La setmana passada
statistics-true-retention-month = El mes passat
statistics-true-retention-year = L’any passat
statistics-true-retention-all-time = Sempre
# If there are no reviews within a specific time period, the retention
# percentage cannot be calculated and is displayed as "N/A."
statistics-true-retention-not-applicable = n/a

##

statistics-range-all-time = Vida de la baralla
statistics-range-1-year-history = últims 12 mesos
statistics-range-all-history = tot l’historial
statistics-range-deck = baralla
statistics-range-collection = col·lecció
statistics-range-search = Cercar
statistics-card-ease-title = Facilitat de la targeta
statistics-card-difficulty-title = Dificultat de les targetes
statistics-card-stability-title = Estabilitat de les targetes
statistics-card-stability-subtitle = Retard dins del qual és probable que en recordeu el 90 %.
statistics-median-stability = Estabilitat mediana
statistics-card-retrievability-title = Recuperabilitat de les targetes
statistics-card-ease-subtitle = Com més baixa sigui la facilitat, més freqüentment apareixerà la targeta.
statistics-card-difficulty-subtitle2 = Com més gran sigui la dificultat, més lentament augmentarà l’estabilitat.
statistics-retrievability-subtitle = Probabilitat que recordeu una targeta avui.
# eg "3 cards with 150-170% ease"
statistics-card-ease-tooltip =
    { $cards ->
        [one] 1 targeta amb { $percent } facilitat
       *[other] { $cards } targetes amb { $percent } facilitat
    }
statistics-card-difficulty-tooltip =
    { $cards ->
        [one] Una targeta amb una dificultat del { $percent }
       *[other] { $cards } targetes amb una dificultat del { $percent }
    }
statistics-retrievability-tooltip =
    { $cards ->
        [one] Una targeta amb una recuperabilitat del { $percent }
       *[other] { $cards } targetes amb una recuperabilitat del { $percent }
    }
statistics-future-due-title = Previsió
statistics-future-due-subtitle = Nombre de repassos futurs planificats.
statistics-added-title = Targetes afegides
statistics-added-subtitle = Nombre de targetes noves que heu afegit.
statistics-reviews-count-subtitle = Nombre de preguntes que heu respost.
statistics-reviews-time-subtitle = Temps que heu trigat a respondre les preguntes.
statistics-answer-buttons-title = Botons de resposta
# eg Button: 4
statistics-answer-buttons-button-number = Botó
# eg Times pressed: 123
statistics-answer-buttons-button-pressed = Vegades que l'heu premut
statistics-answer-buttons-subtitle = Nombre de vegades que heu premut cada botó.
statistics-reviews-title = Repassos
statistics-reviews-time-checkbox = Durada
statistics-in-days-single =
    { $days ->
        [0] Avui
        [1] Demà
       *[other] En { $days } dies
    }
statistics-in-days-range = En { $daysStart }-{ $daysEnd } dies
statistics-days-ago-single =
    { $days ->
        [1] Ahir
       *[other] { $days }  enrere
    }
statistics-days-ago-range = Fa { $daysStart }-{ $daysEnd } dies
statistics-running-total = Total acumulat
statistics-cards-due =
    { $cards ->
        [one] Una targeta pendent
       *[other] { $cards } targetes pendents
    }
statistics-backlog-checkbox = Acumulació
statistics-intervals-title = Intervals
statistics-intervals-subtitle = Nombre de targetes en funció del seu interval de repàs.
statistics-intervals-day-range =
    { $cards ->
        [one] 1 targeta amb un interval de { $daysStart }~{ $daysEnd } dies
       *[other] { $cards } targetes amb un interval de { $daysStart }~{ $daysEnd } dies
    }
statistics-intervals-day-single =
    { $cards ->
        [one] 1 targeta amb un interval de  { $day } dies
       *[other] { $cards } targetes amb un interval de { $day } dies
    }
statistics-stability-day-range =
    { $cards ->
        [one] Una targeta amb una estabilitat diària de { $daysStart }~{ $daysEnd }
       *[other] { $cards } targetes amb una estabilitat diària de { $daysStart }~{ $daysEnd }
    }
statistics-stability-day-single =
    { $cards ->
        [one] Una targeta amb un interval de { $day } dies
       *[other] { $cards } targetes amb un interval de { $day } dies
    }
# hour range, eg "From 14:00-15:00"
statistics-hours-range = Des de { $hourStart }:00~{ $hourEnd }:00
statistics-hours-correct = { $correct }/{ $total } correctes ({ $percent } %)
statistics-hours-correct-info = → (excepte «De nou»)
# the emoji depicts the graph displaying this number
statistics-hours-reviews = 📊 { $reviews } repassos
# the emoji depicts the graph displaying this number
statistics-hours-correct-reviews = 📈 { $percent } % correctes ({ $reviews } repassos)
statistics-hours-title = Distribució horària
statistics-hours-subtitle = Percentatge de repassos correctes al llarg del dia.
# shown when graph is empty
statistics-no-data = SENSE DADES
statistics-calendar-title = Calendari

## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.

statistics-elapsed-time-seconds = { $amount }s
statistics-elapsed-time-minutes = { $amount }m
statistics-elapsed-time-hours = { $amount }h
statistics-elapsed-time-days = { $amount }d
statistics-elapsed-time-months = { $amount }me
statistics-elapsed-time-years = { $amount }a

##

statistics-average-for-days-studied = Mitjana per dia estudiat
# This term is used in a variety of contexts to refers to the total amount of
# items (e.g., cards, mature cards, etc) for a given period, rather than the
# total of all existing items.
statistics-total = Total
statistics-days-studied = Dies que heu estudiat
statistics-average-answer-time-label = Temps de resposta mitjà
statistics-average = Mitjana
statistics-median-interval = Interval medià
statistics-due-tomorrow = Planificades per a demà
# This string, ‘Daily load,’ appears in the ‘Future due’ table and represents a
# forecasted estimate of the number of cards expected to be reviewed daily in 
# the future. Unlike the other strings in the table that display actual data 
# derived from the current scheduling (e.g., ‘Average’, ‘Due tomorrow’),
# ‘Daily load’ is a projection based on the given data.
statistics-daily-load = Càrrega diària
# eg 5 of 15 (33.3%)
statistics-amount-of-total-with-percentage = { $amount } de { $total } ({ $percent } %)
statistics-average-over-period = Mitjana si haguéssiu estudiat tots els dies
statistics-reviews-per-day =
    { $count ->
        [one] { $count } repàs/dia
       *[other] { $count } repassos/dia
    }
statistics-minutes-per-day =
    { $count ->
        [one] { $count } minut/dia
       *[other] { $count } minuts/dia
    }
statistics-cards-per-day =
    { $count ->
        [one] { $count } targeta/dia
       *[other] { $count } targetes/dia
    }
statistics-median-ease = Facilitat mediana
statistics-median-difficulty = Dificultat mediana
statistics-average-retrievability = Recuperabilitat mitjana
statistics-estimated-total-knowledge = Coneixement total estimat
statistics-save-pdf = Guarda en PDF
statistics-saved = S’ha guardat.
statistics-stats = estadístiques
statistics-title = Estadístiques

## These strings are no longer used - you do not need to translate them if they
## are not already translated.

statistics-average-stability = Estabilitat mitjana
statistics-average-interval = Interval mitjà
statistics-average-ease = Facilitat mitjana
statistics-average-difficulty = Dificultat mitjana
