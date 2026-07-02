# The date a card will be ready to review
statistics-due-date = Esedékes
# The count of cards waiting to be reviewed
statistics-due-count = Esedékes
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = Új #{ $number }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } kártya/perc
statistics-average-answer-time = { $average-seconds }mp ({ statistics-cards-per-min })

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds =
    { $amount ->
        [one] { $amount } másodperc alatt
       *[other] { $amount } másodperc alatt
    }
statistics-in-time-span-minutes =
    { $amount ->
        [one] { $amount } perc alatt
       *[other] { $amount } perc alatt
    }
statistics-in-time-span-hours =
    { $amount ->
        [one] { $amount } óra alatt
       *[other] { $amount } óra alatt
    }
statistics-in-time-span-days =
    { $amount ->
        [one] { $amount } nap alatt
       *[other] { $amount } nap alatt
    }
statistics-in-time-span-months =
    { $amount ->
        [one] { $amount } hónap alatt
       *[other] { $amount } hónap alatt
    }
statistics-in-time-span-years =
    { $amount ->
        [one] { $amount } év alatt
       *[other] { $amount } év alatt
    }
# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { statistics-in-time-span-seconds } part should be pasted in from the English
# version unmodified.
statistics-studied-today =
    { $unit ->
        [seconds]
            Ma { statistics-cards } kártyát tanultál
            { statistics-in-time-span-seconds } alatt
            ({ $secs-per-card }mp/kártya)
        [minutes]
            Ma { statistics-cards } kártyát tanultál
            { statistics-in-time-span-minutes } alatt
            ({ $secs-per-card }mp/kártya)
        [hours]
            Ma { statistics-cards } kártyát tanultál
            { statistics-in-time-span-hours } alatt
            ({ $secs-per-card }mp/kártya)
        [days]
            Ma { statistics-cards } kártyát tanultál
            { statistics-in-time-span-days } alatt
            ({ $secs-per-card }mp/kártya)
        [months]
            Ma { statistics-cards } kártyát tanultál
            { statistics-in-time-span-months }alatt
            ({ $secs-per-card }mp/kártya)
       *[years]
            Ma { statistics-cards } kártyát tanultál
            { statistics-in-time-span-years } alatt
            ({ $secs-per-card }mp/kártya)
    }

##

statistics-cards =
    { $cards ->
        [one] { $cards } kártya
       *[other] { $cards } kártya
    }
statistics-notes =
    { $notes ->
       *[other] { $notes } jegyzet
    }
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews =
    { $reviews ->
        [one] { $reviews } ismétlés
       *[other] { $reviews } ismétlés
    }
# This fragment of the tooltip in the FSRS simulation
# diagram (Deck options -> FSRS) shows the total number of
# cards that can be recalled or retrieved on a specific date.
statistics-memorized = { $memorized } megtanult kártya
statistics-today-title = Ma
statistics-today-again-count = „Újra” válaszok száma:
statistics-today-type-counts = Tanulás: { $learnCount }, Ismétlés: { $reviewCount }, Újratanulás: { $relearnCount }, Szűrt: { $filteredCount }
statistics-today-no-cards = Ma még nem tanultál.
statistics-today-no-mature-cards = Ma még egyetlen veterán kártyát sem tanultál.
statistics-today-correct-mature = Helyes válaszok a veterán kártyákra: { $correct }/{ $total } ({ $percent }%)
statistics-counts-total-cards = Kártyák összesen
statistics-counts-new-cards = Új
statistics-counts-young-cards = Friss
statistics-counts-mature-cards = Veterán
statistics-counts-suspended-cards = Felfüggesztve
statistics-counts-buried-cards = Félretéve
statistics-counts-filtered-cards = Szűrt
statistics-counts-learning-cards = Tanulás
statistics-counts-relearning-cards = Újratanulás
statistics-counts-title = Kártyák száma
statistics-counts-separate-suspended-buried-cards = felfüggesztett/félretett kártyák elkülönítése

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

statistics-true-retention-title = Megtartás
statistics-true-retention-subtitle = Helyes válasz aránya a legalább egynapos időközű kártyák között.
statistics-true-retention-tooltip = Ha az FSRS-t használod, a valós megtartás várhatóan közel áll a kívánt megtartáshoz. Mivel egynapnyi adatban nagy kilengések lehetnek, érdemes a havi adatokat nézni.
statistics-true-retention-pass = Helyes válasz
statistics-true-retention-fail = Rossz válasz
# This will usually be the same as statistics-counts-total-cards
statistics-true-retention-total = Kártyák összesen
statistics-true-retention-count = Mennyiség
statistics-true-retention-retention = Megtartás
# This will usually be the same as statistics-counts-young-cards
statistics-true-retention-young = Friss
# This will usually be the same as statistics-counts-mature-cards
statistics-true-retention-mature = Veterán
statistics-true-retention-all = Összes
statistics-true-retention-today = Ma
statistics-true-retention-yesterday = Tegnap
statistics-true-retention-week = Az elmúlt héten
statistics-true-retention-month = Az elmúlt hónapban
statistics-true-retention-year = Az elmúlt évben
statistics-true-retention-all-time = Kezdetektől
# If there are no reviews within a specific time period, the retention
# percentage cannot be calculated and is displayed as "N/A."
statistics-true-retention-not-applicable = N/A

##

statistics-range-all-time = csomag élettartama
statistics-range-1-year-history = Az elmúlt 12 hónap
statistics-range-all-history = Teljes történet
statistics-range-deck = pakli
statistics-range-collection = gyűjtemény
statistics-range-search = Keresés
statistics-card-ease-title = Kártya könnyűsége
statistics-card-difficulty-title = Kártya nehézsége
statistics-card-stability-title = Kártyastabilitás
statistics-card-stability-subtitle = A késleltetés, ami mellett az előhívás 90% alá esik.
statistics-median-stability = Medián stabilitás
statistics-card-retrievability-title = Előhívás
statistics-card-ease-subtitle = Minél kisebb a könnyűség, annál gyakrabban jelenik meg a kártya.
statistics-card-difficulty-subtitle2 = Minél magasabb a nehézség, annál lassabban növekszik a stabilitás.
statistics-retrievability-subtitle = Egy kártya előhívásának valószínűsége ma.
# eg "3 cards with 150-170% ease"
statistics-card-ease-tooltip =
    { $cards ->
       *[other] { $cards } kártya { $percent } könnyűséggel
    }
statistics-card-difficulty-tooltip =
    { $cards ->
       *[other] { $cards } kártya { $percent } nehézséggel
    }
statistics-retrievability-tooltip =
    { $cards ->
       *[other] { $cards } kártya { $percent } előhívással
    }
statistics-future-due-title = Előrejelzés
statistics-future-due-subtitle = A jövőben esedékes ismétlések száma
statistics-added-title = Hozzáadott kártyák
statistics-added-subtitle = Az általad hozzáadott új kártyák száma.
statistics-reviews-count-subtitle = Megválaszolt kérdések száma
statistics-reviews-time-subtitle = A kérdések megválaszolásával eltöltött idő
statistics-answer-buttons-title = Válaszgombok
# eg Button: 4
statistics-answer-buttons-button-number = Gomb
# eg Times pressed: 123
statistics-answer-buttons-button-pressed = Válaszok száma
statistics-answer-buttons-subtitle = Az egyes válaszgombok lenyomásának száma
statistics-reviews-title = Ismétlések
statistics-reviews-time-checkbox = Idő
statistics-in-days-single =
    { $days ->
        [0] Ma
        [1] Holnap
        [2] Holnapután
       *[other] { $days } nap múlva
    }
statistics-in-days-range = { $daysStart }-{ $daysEnd } nap múlva
statistics-days-ago-single =
    { $days ->
        [1] Tegnap
        [2] Tegnapelőtt
       *[other] { $days } napja
    }
statistics-days-ago-range = { $daysStart }-{ $daysEnd } napja
statistics-running-total = Összesen
statistics-cards-due =
    { $cards ->
       *[other] { $cards } kártya esedékes
    }
statistics-backlog-checkbox = Hátralék
statistics-intervals-title = Időközök
statistics-intervals-subtitle = A következő ismétlésig hátralevő idő
statistics-intervals-day-range =
    { $cards ->
       *[other] { $cards } kártya { $daysStart }-{ $daysEnd } napos időközzel
    }
statistics-intervals-day-single =
    { $cards ->
       *[other] { $cards } kártya { $day } napos időközzel
    }
statistics-stability-day-range =
    { $cards ->
       *[other] { $cards } kártya { $daysStart }-{ $daysEnd } napos stabilitással
    }
statistics-stability-day-single =
    { $cards ->
       *[other] { $cards } kártya { $day } napos stabilitással
    }
# hour range, eg "From 14:00-15:00"
statistics-hours-range = { $hourStart }:00-{ $hourEnd }:00
statistics-hours-correct = { $correct }/{ $total } helyes ({ $percent }%)
statistics-hours-correct-info = → (nem "Újra")
# the emoji depicts the graph displaying this number
statistics-hours-reviews = 📊 { $reviews } ismétlés
# the emoji depicts the graph displaying this number
statistics-hours-correct-reviews = 📈 { $percent }% helyes ({ $reviews })
statistics-hours-title = Óránkénti lebontás
statistics-hours-subtitle = Ismétlés sikerességének aránya a nap egyes óráiban
# shown when graph is empty
statistics-no-data = NINCS ADAT
statistics-calendar-title = Naptár

## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.

statistics-elapsed-time-seconds = { $amount }mp
statistics-elapsed-time-minutes = { $amount }p
statistics-elapsed-time-hours = { $amount }ó
statistics-elapsed-time-days = { $amount }n
statistics-elapsed-time-months = { $amount } hó
statistics-elapsed-time-years = { $amount } év

##

statistics-average-for-days-studied = Tanulással töltött napok átlaga
# This term is used in a variety of contexts to refers to the total amount of
# items (e.g., cards, mature cards, etc) for a given period, rather than the
# total of all existing items.
statistics-total = Összesen
statistics-days-studied = Tanulással töltött napok
statistics-average-answer-time-label = Átlagos válaszadási idő
statistics-average = Átlag
statistics-median-interval = Medián időköz
statistics-due-tomorrow = Holnap esedékes
# This string, ‘Daily load,’ appears in the ‘Future due’ table and represents a
# forecasted estimate of the number of cards expected to be reviewed daily in 
# the future. Unlike the other strings in the table that display actual data 
# derived from the current scheduling (e.g., ‘Average’, ‘Due tomorrow’),
# ‘Daily load’ is a projection based on the given data.
statistics-daily-load = Várható terhelés
# eg 5 of 15 (33.3%)
statistics-amount-of-total-with-percentage = { $amount }/{ $total } ({ $percent }%)
statistics-average-over-period = Teljes időtartam átlaga
statistics-reviews-per-day =
    { $count ->
       *[other] { $count } ismétlés/nap
    }
statistics-minutes-per-day =
    { $count ->
       *[other] { $count } perc/nap
    }
statistics-cards-per-day =
    { $count ->
       *[other] { $count } kártya/nap
    }
statistics-median-ease = Medián könnyűség
statistics-median-difficulty = Medián nehézség
statistics-average-retrievability = Átlagos előhívás
statistics-estimated-total-knowledge = Becsült teljes tudás
statistics-save-pdf = Mentés PDF-ként
statistics-saved = Mentve.
statistics-stats = statisztika
statistics-title = Statisztika

## These strings are no longer used - you do not need to translate them if they
## are not already translated.

statistics-average-stability = Átlagos stabilitás
statistics-average-interval = Átlagos időköz
statistics-average-ease = Átlagos könnyűség
statistics-average-difficulty = Átlagos nehézség
