# The date a card will be ready to review
statistics-due-date = Lernenda
# The count of cards waiting to be reviewed
statistics-due-count = Lernendaj
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = Nova #{ $number }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } kartoj/minuto
statistics-average-answer-time = { $average-seconds } s ({ statistics-cards-per-min })

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds =
    { $amount ->
        [one] en { $amount } sekundo
       *[other] en { $amount } sekundoj
    }
statistics-in-time-span-minutes =
    { $amount ->
        [one] en { $amount } minuto
       *[other] en { $amount } minutoj
    }
statistics-in-time-span-hours =
    { $amount ->
        [one] en { $amount } horo
       *[other] en { $amount } horoj
    }
statistics-in-time-span-days =
    { $amount ->
        [one] en { $amount } tago
       *[other] en { $amount } tagoj
    }
statistics-in-time-span-months =
    { $amount ->
        [one] en { $amount } monato
       *[other] en { $amount } monatoj
    }
statistics-in-time-span-years =
    { $amount ->
        [one] en { $amount } jaro
       *[other] en { $amount } jaroj
    }
# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { statistics-in-time-span-seconds } part should be pasted in from the English
# version unmodified.
statistics-studied-today =
    { $unit ->
        [seconds] Vi hodiaŭ lernis { statistics-cards }n { statistics-in-time-span-seconds } ({ $secs-per-card }s/karto)
        [minutes] Vi hodiaŭ lernis { statistics-cards }n { statistics-in-time-span-minutes } ({ $secs-per-card }s/karto)
        [hours] Vi hodiaŭ lernis { statistics-cards }n { statistics-in-time-span-hours } ({ $secs-per-card }s/karto)
        [days] Vi hodiaŭ lernis { statistics-cards }n { statistics-in-time-span-days } ({ $secs-per-card }s/karto)
        [months] Vi hodiaŭ lernis { statistics-cards }n { statistics-in-time-span-months } ({ $secs-per-card }s/karto)
       *[years] Vi hodiaŭ lernis { statistics-cards }n { statistics-in-time-span-years } ({ $secs-per-card }s/karto)
    }

##

statistics-cards =
    { $cards ->
        [one] { $cards } karto
       *[other] { $cards } kartoj
    }
statistics-notes =
    { $notes ->
        [one] { $notes } noto
       *[other] { $notes } notoj
    }
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews =
    { $reviews ->
        [one] { $reviews } ripeto
       *[other] { $reviews } ripetoj
    }
# This fragment of the tooltip in the FSRS simulation
# diagram (Deck options -> FSRS) shows the total number of
# cards that can be recalled or retrieved on a specific date.
statistics-memorized = { $memorized } memoritaj
statistics-today-title = Hodiaŭ
statistics-today-again-count = Nombro da misrespondoj:
statistics-today-type-counts = Lernataj: { $learnCount }, ripetataj: { $reviewCount }, relernataj: { $relearnCount }, filtrataj: { $filteredCount }
statistics-today-no-cards = Hodiaŭ vi lernis neniun karton.
statistics-today-no-mature-cards = Hodiaŭ vi lernis neniun maljunan karton.
statistics-today-correct-mature = Ĝustaj respondoj al maljunaj kartoj: { $correct }/{ $total } ({ $percent }%)
statistics-counts-total-cards = Kartoj entute
statistics-counts-new-cards = Novaj
statistics-counts-young-cards = Junaj
statistics-counts-mature-cards = Maljunaj
statistics-counts-suspended-cards = Daŭre kaŝitaj
statistics-counts-buried-cards = Kaŝitaj por tago
statistics-counts-filtered-cards = Filtritaj
statistics-counts-learning-cards = Lernataj
statistics-counts-relearning-cards = Relernataj
statistics-counts-title = Nombro da kartoj
statistics-counts-separate-suspended-buried-cards = Apartigi kaŝitajn kartojn

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

statistics-true-retention-title = Vera memorado
statistics-true-retention-subtitle = Elcento da memorigitaj kartoj por intertempo ≥ 1 tago.
statistics-true-retention-tooltip = Se vi uzas FSRS, via vera memorado probable estos proksimuma al via dezirata memorado. Memoru, ke datumoj por unuopa tago estas bruplenaj, do estas pli bone rigardi al monataj datumoj.
statistics-true-retention-range = Variejo
statistics-true-retention-pass = Ĝustaj
statistics-true-retention-fail = Malĝustaj
# This will usually be the same as statistics-counts-total-cards
statistics-true-retention-total = Kartoj entute
statistics-true-retention-count = Nombro
statistics-true-retention-retention = Memorado
# This will usually be the same as statistics-counts-young-cards
statistics-true-retention-young = Junaj
# This will usually be the same as statistics-counts-mature-cards
statistics-true-retention-mature = Maljunaj
statistics-true-retention-all = Ĉiuj
statistics-true-retention-today = Hodiaŭ
statistics-true-retention-yesterday = Hieraŭ
statistics-true-retention-week = Antaŭa semajno
statistics-true-retention-month = Antaŭa monato
statistics-true-retention-year = Antaŭa jaro
statistics-true-retention-all-time = Tuta tempo
# If there are no reviews within a specific time period, the retention
# percentage cannot be calculated and is displayed as "N/A."
statistics-true-retention-not-applicable = –

##

statistics-range-all-time = ekde kreo
statistics-range-1-year-history = antaŭaj 12 monatoj
statistics-range-all-history = tuta historio
statistics-range-deck = kartaro
statistics-range-collection = kolekto
statistics-range-search = Serĉi
statistics-card-ease-title = Facileco de kartoj
statistics-card-difficulty-title = Malfacileco de kartoj
statistics-card-stability-title = Stabileco de kartoj
statistics-card-stability-subtitle = Tempo post kiu rememoriga probablo akiras 90%.
statistics-median-stability = Mediana stabileco
statistics-card-retrievability-title = Rememoriga probablo de kartoj
statistics-card-ease-subtitle = Ju malpli alta facileco, des pli ofte karto aperos.
statistics-card-difficulty-subtitle2 = Ju pli alta malfacileco, des malpli rapide stabileco pliiĝos.
statistics-retrievability-subtitle = Probablo por rememorigi karton hodiaŭ.
# eg "3 cards with 150-170% ease"
statistics-card-ease-tooltip =
    { $cards ->
        [one] { $cards } karto kun facileco { $percent }
       *[other] { $cards } kartoj kun facileco { $percent }
    }
statistics-card-difficulty-tooltip =
    { $cards ->
        [one] { $cards } karto kun malfacileco { $percent }
       *[other] { $cards } kartoj kun malfacileco { $percent }
    }
statistics-retrievability-tooltip =
    { $cards ->
        [one] { $cards } karto kun rememoriga probablo { $percent }
       *[other] { $cards } kartoj kun rememoriga probablo { $percent }
    }
statistics-future-due-title = Prognozo
statistics-future-due-subtitle = Nombro de ripetoj, kiuj estas lernendaj en la estonto.
statistics-added-title = Aldonitaj
statistics-added-subtitle = Nombro de novaj kartoj, kiujn vi aldonis.
statistics-reviews-count-subtitle = Nombro de demandoj, al kiuj vi respondis.
statistics-reviews-time-subtitle = La tempo, kiu pasis por respondi la demandojn.
statistics-answer-buttons-title = Respondobutonoj
# eg Button: 4
statistics-answer-buttons-button-number = Butono
# eg Times pressed: 123
statistics-answer-buttons-button-pressed = Nombro de premoj
statistics-answer-buttons-subtitle = Kiomfoje vi premis specifajn butonojn.
statistics-reviews-title = Ripetoj
statistics-reviews-time-checkbox = Tempo
statistics-in-days-single =
    { $days ->
        [0] hodiaŭ
        [1] morgaŭ
        [one] post { $days } tago
       *[other] post { $days } tagoj
    }
statistics-in-days-range = post { $daysStart }–{ $daysEnd } tagoj
statistics-days-ago-single =
    { $days ->
        [1] hieraŭ
        [one] antaŭ { $days } tago
       *[other] antaŭ { $days } tagoj
    }
statistics-days-ago-range = antaŭ { $daysStart }–{ $daysEnd } tagoj
statistics-running-total = Tute
statistics-cards-due =
    { $cards ->
        [one] { $cards } lernenda karto
       *[other] { $cards } lernendaj kartoj
    }
statistics-backlog-checkbox = malfruaĵoj
statistics-intervals-title = Intertempoj
statistics-intervals-subtitle = Intertempoj ĝis ripetoj denove montriĝas.
statistics-intervals-day-range =
    { $cards ->
        [one] { $cards } karto kun intertempo { $daysStart }–{ $daysEnd } tagoj
       *[other] { $cards } kartoj kun intertempo { $daysStart }–{ $daysEnd } tagoj
    }
statistics-intervals-day-single =
    { $cards ->
        [one] { $cards } karto kun { $day }-taga intertempo
       *[other] { $cards } kartoj kun { $day }-taga intertempo
    }
statistics-stability-day-range =
    { $cards ->
        [one] { $cards } karto kun stabileco de { $daysStart }–{ $daysEnd } tagoj
       *[other] { $cards } kartoj kun stabileco de { $daysStart }–{ $daysEnd } tagoj
    }
statistics-stability-day-single =
    { $cards ->
        [one] { $cards } karto kun { $day }-taga stabileco
       *[other] { $cards } kartoj kun { $day }-taga stabileco
    }
# hour range, eg "From 14:00-15:00"
statistics-hours-range = ekde { $hourStart }:00 ĝis { $hourEnd }:00
statistics-hours-correct = { $correct }/{ $total } ĝustaj ({ $percent }%)
statistics-hours-correct-info = → (alia ol “Denove”)
# the emoji depicts the graph displaying this number
statistics-hours-reviews = 📊 { $reviews } ripetoj
# the emoji depicts the graph displaying this number
statistics-hours-correct-reviews = 📈 { $percent }% ĝustaj ({ $reviews })
statistics-hours-title = Hora disigo
statistics-hours-subtitle = Elcento da ĝustaj respondoj laŭ horoj.
# shown when graph is empty
statistics-no-data = NIENIUJ DATUMOJ
statistics-calendar-title = Kalendaro

## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.

statistics-elapsed-time-seconds = { $amount } s
statistics-elapsed-time-minutes = { $amount } min
statistics-elapsed-time-hours = { $amount } h
statistics-elapsed-time-days = { $amount } d
statistics-elapsed-time-months = { $amount } mon.
statistics-elapsed-time-years = { $amount } a

##

statistics-average-for-days-studied = Meznombro por lerntagoj
# This term is used in a variety of contexts to refers to the total amount of
# items (e.g., cards, mature cards, etc) for a given period, rather than the
# total of all existing items.
statistics-total = Sumo
statistics-days-studied = Tagoj de lernado
statistics-average-answer-time-label = Mezuma respondotempo
statistics-average = Meznombro
statistics-median-interval = Mediana intertempo
statistics-due-tomorrow = Lernendaj morgaŭ
# This string, ‘Daily load,’ appears in the ‘Future due’ table and represents a
# forecasted estimate of the number of cards expected to be reviewed daily in 
# the future. Unlike the other strings in the table that display actual data 
# derived from the current scheduling (e.g., ‘Average’, ‘Due tomorrow’),
# ‘Daily load’ is a projection based on the given data.
statistics-daily-load = Taga prognozo
# eg 5 of 15 (33.3%)
statistics-amount-of-total-with-percentage = { $amount } el { $total } ({ $percent }%)
statistics-average-over-period = Se vi lernus ĉiutage
statistics-reviews-per-day =
    { $count ->
        [one] { $count } ripeto/tago
       *[other] { $count } ripetoj/tago
    }
statistics-minutes-per-day =
    { $count ->
        [one] { $count } minuto/tago
       *[other] { $count } minutoj/tago
    }
statistics-cards-per-day =
    { $count ->
        [one] { $count } karto/tago
       *[other] { $count } kartoj/tago
    }
statistics-median-ease = Mediana facileco
statistics-median-difficulty = Mediana malfacileco
statistics-average-retrievability = Mezuma rememoriga probablo
statistics-estimated-total-knowledge = Antaŭkalkulita tuta memorigo
statistics-save-pdf = Konservi kiel PDF
statistics-saved = Konservita.
statistics-stats = statistikoj
statistics-title = Statistikoj

## These strings are no longer used - you do not need to translate them if they
## are not already translated.

statistics-average-stability = Mezuma stabileco
statistics-average-interval = Mezuma intertempo
statistics-average-ease = Mezuma facileco
statistics-average-difficulty = Mezuma malfacileco
