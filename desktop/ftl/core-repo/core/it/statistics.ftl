# The date a card will be ready to review
statistics-due-date = Scadenza
# The count of cards waiting to be reviewed
statistics-due-count = Ripasso
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = Nuova #{ $number }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } carte/minuto
statistics-average-answer-time = { $average-seconds }s ({ statistics-cards-per-min })

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds =
    { $amount ->
        [one] in { $amount } secondo
       *[other] in { $amount } secondi
    }
statistics-in-time-span-minutes =
    { $amount ->
        [one] in { $amount } minuto
       *[other] in { $amount } minuti
    }
statistics-in-time-span-hours =
    { $amount ->
        [one] in { $amount } ora
       *[other] in { $amount } ore
    }
statistics-in-time-span-days =
    { $amount ->
        [one] in { $amount } giorno
       *[other] in { $amount } giorni
    }
statistics-in-time-span-months =
    { $amount ->
        [one] in { $amount } mese
       *[other] in { $amount } mesi
    }
statistics-in-time-span-years =
    { $amount ->
        [one] in { $amount } anno
       *[other] in { $amount } anni
    }
# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { statistics-in-time-span-seconds } part should be pasted in from the English
# version unmodified.
statistics-studied-today =
    { $unit ->
        [seconds] Oggi hai studiato { statistics-cards } { statistics-in-time-span-seconds } ({ $secs-per-card }s/carta)
        [minutes] Oggi hai studiato { statistics-cards } { statistics-in-time-span-minutes } ({ $secs-per-card }s/carta)
        [hours] Oggi hai studiato { statistics-cards } { statistics-in-time-span-hours } ({ $secs-per-card }s/carta)
        [days] Oggi hai studiato { statistics-cards } { statistics-in-time-span-days } ({ $secs-per-card }s/carta)
        [months] Oggi hai studiato { statistics-cards } { statistics-in-time-span-months } ({ $secs-per-card }s/carta)
       *[years] Oggi hai studiato { statistics-cards } { statistics-in-time-span-years } ({ $secs-per-card }s/carta)
    }

##

statistics-cards =
    { $cards ->
        [one] { $cards } carta
       *[other] { $cards } carte
    }
statistics-notes =
    { $notes ->
        [one] { $notes } nota
       *[other] { $notes } note
    }
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews =
    { $reviews ->
        [one] { $reviews } ripetizione
       *[other] { $reviews } ripetizioni
    }
# This fragment of the tooltip in the FSRS simulation
# diagram (Deck options -> FSRS) shows the total number of
# cards that can be recalled or retrieved on a specific date.
statistics-memorized = { $memorized } carte memorizzate
statistics-today-title = Oggi
statistics-today-again-count = Carte sbagliate:
statistics-today-type-counts = Imparate: { $learnCount }, Ripassate: { $reviewCount }, Reimparate: { $relearnCount }, Filtrate: { $filteredCount }
statistics-today-no-cards = Oggi non è stata studiata alcuna carta.
statistics-today-no-mature-cards = Oggi non è stata studiata alcuna carta matura.
statistics-today-correct-mature = Carte mature corrette: { $correct }/{ $total } ({ $percent }%)
statistics-counts-total-cards = Carte totali
statistics-counts-new-cards = Nuove
statistics-counts-young-cards = Giovani
statistics-counts-mature-cards = Mature
statistics-counts-suspended-cards = Sospese
statistics-counts-buried-cards = Seppellite
statistics-counts-filtered-cards = Filtrate
statistics-counts-learning-cards = In apprendimento
statistics-counts-relearning-cards = In riapprendimento
statistics-counts-title = Conteggio delle carte
statistics-counts-separate-suspended-buried-cards = Separa le carte sospese/seppellite

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

statistics-true-retention-title = Ritenzione reale
statistics-true-retention-subtitle = Tasso di carte corrette aventi un intervallo di 1 o più giorni.
statistics-true-retention-tooltip = Se usi FSRS, la tua ritenzione reale dovrebbe avvicinarsi alla ritenzione desiderata. Tieni presente che i dati di un solo giorno sono molto rumorosi (poco affidabili), per cui è preferibile fare riferimento ai dati mensili.
statistics-true-retention-range = Intervallo
statistics-true-retention-pass = Corrette
statistics-true-retention-fail = Sbagliate
# This will usually be the same as statistics-counts-total-cards
statistics-true-retention-total = Carte totali
statistics-true-retention-count = Conteggio
statistics-true-retention-retention = Ritenzione
# This will usually be the same as statistics-counts-young-cards
statistics-true-retention-young = Giovani
# This will usually be the same as statistics-counts-mature-cards
statistics-true-retention-mature = Mature
statistics-true-retention-all = Tutte
statistics-true-retention-today = Oggi
statistics-true-retention-yesterday = Ieri
statistics-true-retention-week = Ultima settimana
statistics-true-retention-month = Ultimo mese
statistics-true-retention-year = Ultimo anno
statistics-true-retention-all-time = Tutto il periodo
# If there are no reviews within a specific time period, the retention
# percentage cannot be calculated and is displayed as "N/A."
statistics-true-retention-not-applicable = N.D.

##

statistics-range-all-time = vita del mazzo
statistics-range-1-year-history = ultimi 12 mesi
statistics-range-all-history = tutto il periodo
statistics-range-deck = mazzo
statistics-range-collection = collezione
statistics-range-search = Cerca
statistics-card-ease-title = Facilità della carta
statistics-card-difficulty-title = Difficoltà delle carte
statistics-card-stability-title = Stabilità delle carte
statistics-card-stability-subtitle = Il lasso di tempo necessario affinché la rammentabilità scenda a 90%.
statistics-median-stability = Stabilità mediana
statistics-card-retrievability-title = Rammentabilità delle carte
statistics-card-ease-subtitle = Più bassa è la facilità, più frequentemente la carta apparirà.
statistics-card-difficulty-subtitle2 = Più alta è la difficoltà, tanto più lentamente crescerà la stabilità.
statistics-retrievability-subtitle = La probabilità di ricordare una carta (oggi).
# eg "3 cards with 150-170% ease"
statistics-card-ease-tooltip =
    { $cards ->
        [one] { $cards } carta con una facilità del { $percent }
       *[other] { $cards } carte con una facilità del { $percent }
    }
statistics-card-difficulty-tooltip =
    { $cards ->
        [one] { $cards } carta con una difficoltà del { $percent }
       *[other] { $cards } carte con una difficoltà del { $percent }
    }
statistics-retrievability-tooltip =
    { $cards ->
        [one] { $cards } carta con una rammentabilità del { $percent }
       *[other] { $cards } carte con una rammentabilità del { $percent }
    }
statistics-future-due-title = Previsioni
statistics-future-due-subtitle = Numero di ripetizioni che scadranno in futuro.
statistics-added-title = Carte aggiunte
statistics-added-subtitle = Numero di carte nuove aggiunte.
statistics-reviews-count-subtitle = Numero di domande alle quali si è risposto.
statistics-reviews-time-subtitle = Tempo impiegato per rispondere alle domande.
statistics-answer-buttons-title = Pulsanti di risposta
# eg Button: 4
statistics-answer-buttons-button-number = Pulsante
# eg Times pressed: 123
statistics-answer-buttons-button-pressed = Numero di volte premuto
statistics-answer-buttons-subtitle = Numero di volte che è stato premuto ciascun pulsante.
statistics-reviews-title = Ripetizioni
statistics-reviews-time-checkbox = Durata
statistics-in-days-single =
    { $days ->
        [0] Oggi
        [1] Domani
        [one] In { $days } giorno
       *[other] In { $days } giorni
    }
statistics-in-days-range = In { $daysStart }-{ $daysEnd } giorni
statistics-days-ago-single =
    { $days ->
        [1] Ieri
       *[other] { $days } giorni fa
    }
statistics-days-ago-range = { $daysStart }-{ $daysEnd } giorni fa
statistics-running-total = Totale accumulato
statistics-cards-due =
    { $cards ->
        [one] { $cards } carta in programma
       *[other] { $cards } carte in programma
    }
statistics-backlog-checkbox = Arretrato
statistics-intervals-title = Intervalli
statistics-intervals-subtitle = Lasso di tempo prima che le carte da ripetere vengano ripresentate.
statistics-intervals-day-range =
    { $cards ->
        [one] { $cards } carta con un intervallo di { $daysStart }-{ $daysEnd } giorni
       *[other] { $cards } carte con un intervallo di { $daysStart }-{ $daysEnd } giorni
    }
statistics-intervals-day-single =
    { $cards ->
        [one] { $cards } carta con un intervallo di { $day } giorni
       *[other] { $cards } carte con un intervallo di { $day } giorni
    }
statistics-stability-day-range =
    { $cards ->
        [one] { $cards } carta con stabilità di { $daysStart }~{ $daysEnd } gg.
       *[other] { $cards } carte con stabilità di { $daysStart }~{ $daysEnd } gg.
    }
statistics-stability-day-single =
    { $cards ->
        [one] { $cards } carta con stabilità di { $day } gg.
       *[other] { $cards } carte con stabilità di { $day } gg.
    }
# hour range, eg "From 14:00-15:00"
statistics-hours-range = Da { $hourStart }:00~{ $hourEnd }:00
statistics-hours-correct = { $correct }/{ $total } corrette ({ $percent }%)
statistics-hours-correct-info = → (non valutate "Difficile")
# the emoji depicts the graph displaying this number
statistics-hours-reviews = 📊 { $reviews } ripetizioni
# the emoji depicts the graph displaying this number
statistics-hours-correct-reviews = 📈 { $percent }% corrette ({ $reviews })
statistics-hours-title = Suddivisione oraria
statistics-hours-subtitle = Successo delle ripetizioni per ora del giorno.
# shown when graph is empty
statistics-no-data = NESSUN DATO
statistics-calendar-title = Calendario

## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.

statistics-elapsed-time-seconds = { $amount }s
statistics-elapsed-time-minutes = { $amount }m
statistics-elapsed-time-hours = { $amount }h
statistics-elapsed-time-days = { $amount }g
statistics-elapsed-time-months = { $amount }me
statistics-elapsed-time-years = { $amount }a

##

statistics-average-for-days-studied = Media per i giorni di studio
# This term is used in a variety of contexts to refers to the total amount of
# items (e.g., cards, mature cards, etc) for a given period, rather than the
# total of all existing items.
statistics-total = Totale
statistics-days-studied = Giorni di studio
statistics-average-answer-time-label = Tempo medio di risposta
statistics-average = Media
statistics-median-interval = Intervallo mediano
statistics-due-tomorrow = Da ripetere domani
# This string, ‘Daily load,’ appears in the ‘Future due’ table and represents a
# forecasted estimate of the number of cards expected to be reviewed daily in 
# the future. Unlike the other strings in the table that display actual data 
# derived from the current scheduling (e.g., ‘Average’, ‘Due tomorrow’),
# ‘Daily load’ is a projection based on the given data.
statistics-daily-load = Carico giornaliero
# eg 5 of 15 (33.3%)
statistics-amount-of-total-with-percentage = { $amount } di { $total } ({ $percent }%)
statistics-average-over-period = Media per il periodo
statistics-reviews-per-day =
    { $count ->
        [one] { $count } ripetizione/giorno
       *[other] { $count } ripetizioni/giorno
    }
statistics-minutes-per-day =
    { $count ->
        [one] { $count } minuto/giorno
       *[other] { $count } minuti/giorno
    }
statistics-cards-per-day =
    { $count ->
        [one] { $count } carta/giorno
       *[other] { $count } carte/giorno
    }
statistics-median-ease = Facilità mediana
statistics-median-difficulty = Difficoltà mediana
statistics-average-retrievability = Rammentabilità media
statistics-estimated-total-knowledge = Stima conoscenza complessiva
statistics-save-pdf = Salva PDF
statistics-saved = Salvato.
statistics-stats = statistiche
statistics-title = Statistiche

## These strings are no longer used - you do not need to translate them if they
## are not already translated.

statistics-average-stability = Stabilità media
statistics-average-interval = Intervallo medio
statistics-average-ease = Facilità media
statistics-average-difficulty = Difficoltà media
