# The date a card will be ready to review
statistics-due-date = Programate
# The count of cards waiting to be reviewed
statistics-due-count = Programate
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = Noi { $number }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } karda fe'i mentu
statistics-average-answer-time = snidu li { $average-seconds } to { statistics-cards-per-min } toi

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds =
    { $amount ->
        [one] în { $amount } secundă
        [few] în { $amount } secunde
       *[other] în { $amount } de secunde
    }
statistics-in-time-span-minutes =
    { $amount ->
        [one] ca lo mentu be li { $amount }
        [few] ca lo mentu be li { $amount }
       *[other] ca lo mentu be li { $amount }
    }
statistics-in-time-span-hours =
    { $amount ->
        [one] ca lo cacra be li { $amount }
        [few] ca lo cacra be li { $amount }
       *[other] ca lo cacra be li { $amount }
    }
statistics-in-time-span-days =
    { $amount ->
        [one] ca lo djedi be li { $amount }
        [few] ca lo djedi be li { $amount }
       *[other] ca lo djedi be li { $amount }
    }
statistics-in-time-span-months =
    { $amount ->
        [one] în { $amount } lună
        [few] în { $amount } luni
       *[other] în { $amount } de luni
    }
statistics-in-time-span-years =
    { $amount ->
        [one] în { $amount } an
        [few] în { $amount } ani
       *[other] în { $amount } de ani
    }
statistics-cards =
    { $cards ->
        [one] { $cards } karda
        [few] { $cards } carduri
       *[other] { $cards } carduri
    }
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews =
    { $reviews ->
        [one] { $reviews } repetiție
        [few] { $reviews } repetiții
       *[other] { $reviews } repetiții
    }
# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { statistics-in-time-span-seconds } part should be pasted in from the English
# version unmodified.
statistics-studied-today =
    { "." }i tadni { statistics-cards } { $unit ->
        [seconds] { statistics-in-time-span-seconds }
        [minutes] { statistics-in-time-span-minutes }
        [hours] { statistics-in-time-span-hours }
        [days] { statistics-in-time-span-days }
        [months] { statistics-in-time-span-months }
       *[years] { statistics-in-time-span-years }
    } ca lo cabdei to karda snidu li { $secs-per-card } toi
statistics-today-title = Astăzi
statistics-today-again-count = Numărate din nou:
statistics-today-type-counts = Învățate: { $learnCount }, Repetate: { $reviewCount }, Reînvățate: { $relearnCount }, Filtrate: { $filteredCount }
statistics-today-no-cards = Nu s-au studiat carduri astăzi.
statistics-today-no-mature-cards = Niciun card matur nu a fost studiat astăzi.
statistics-today-correct-mature = Răspunsuri corecte pentru cardurile mature: { $correct }/{ $total } ({ $percent }%)
statistics-counts-total-cards = Total carduri
statistics-counts-new-cards = Noi
statistics-counts-young-cards = Tinere
statistics-counts-mature-cards = Matur(e)
statistics-counts-suspended-cards = Suspendate
statistics-counts-buried-cards = Îngropate
statistics-counts-filtered-cards = Filtrate
statistics-counts-learning-cards = În învățare
statistics-counts-relearning-cards = În reînvățare
statistics-counts-title = Numără cardurile
statistics-counts-separate-suspended-buried-cards = Cardurile separate suspendate/îngropate
statistics-range-all-time = viață pachet
statistics-range-1-year-history = ultimele 12 luni
statistics-range-all-history = toată perioada
statistics-range-deck = pachet
statistics-range-collection = colecție
statistics-range-search = Caută
statistics-card-ease-title = Ușurința cardului
statistics-card-ease-subtitle = Cu cât ușurința este mai mică, cu atât va apărea mai des un card.
# eg "3 cards with 150-170% ease"
statistics-card-ease-tooltip =
    { $cards ->
        [one] { $cards } card cu { $percent } procent de ușurință
        [few] { $cards } carduri cu { $percent } procent de ușurință
       *[other] { $cards } carduri cu { $percent } procent de ușurință
    }
statistics-future-due-title = Previziune
statistics-future-due-subtitle = Numărul de repetiții programate în viitor
statistics-added-title = Adăugat(e)
statistics-added-subtitle = Numărul de carduri noi pe care le-ai adăugat.
statistics-reviews-count-subtitle = Numărul de întrebări la care ai răspuns
statistics-reviews-time-subtitle = Timpul de care a fost nevoie pentru a răspunde întrebărilor
statistics-answer-buttons-title = Butoane de răspuns
# eg Button: 4
statistics-answer-buttons-button-number = Buton
# eg Times pressed: 123
statistics-answer-buttons-button-pressed = Număr de apăsări
statistics-answer-buttons-subtitle = De câte ori ați apăsat fiecare buton.
statistics-reviews-title = Repetiții
statistics-reviews-time-checkbox = Timp
statistics-in-days-single =
    { $days ->
        [0] Astăzi
        [1] Mâine
        [one] În { $days } zile
        [few] În { $days } zile
       *[other] În { $days } zile
    }
statistics-in-days-range = În { $daysStart }-{ $daysEnd } zile
statistics-days-ago-single =
    { $days ->
        [1] Ieri
        [one] { $days } zi în urmă
        [few] { $days } zile în urmă
       *[other] { $days } zile în urmă
    }
statistics-days-ago-range = { $daysStart }-{ $daysEnd } zile în urmă
statistics-running-total = Total curent
statistics-cards-due =
    { $cards ->
        [one] { $cards } carte de parcurs
        [few] { $cards } cărți de parcurs
       *[other] { $cards } cărți de parcurs
    }
statistics-backlog-checkbox = Restanțe
statistics-intervals-title = Intervale
statistics-intervals-subtitle = Amână până când repetițiile sunt afișate din nou.
statistics-intervals-day-range =
    { $cards ->
        [one] Un card cu un interval de  { $daysStart }~{ $daysEnd } zile
        [few] { $cards } carduri cu un interval de { $daysStart }~{ $daysEnd } zile
       *[other] { $cards } carduri cu un interval de { $daysStart }~{ $daysEnd } zile
    }
statistics-intervals-day-single =
    { $cards ->
        [one] Un card cu un interval de { $day } zile
        [few] { $cards } carduri cu un interval de { $day } zile
       *[other] { $cards } carduri cu un interval de { $day } zile
    }
# hour range, eg "From 14:00-15:00"
statistics-hours-range = De la { $hourStart }:00~{ $hourEnd }:00
statistics-hours-correct = { $correct }/{ $total } corect ({ $percent }%)
statistics-hours-title = Defalcare pe ore
statistics-hours-subtitle = Examineză rata de succes pentru fiecare oră din zi.
# shown when graph is empty
statistics-no-data = NU EXISTĂ DATE
statistics-calendar-title = Calendar

## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.

statistics-elapsed-time-seconds = { $amount }s
statistics-elapsed-time-minutes = { $amount }m
statistics-elapsed-time-hours = { $amount }h
statistics-elapsed-time-days = { $amount }z
statistics-elapsed-time-months = { $amount }luni
statistics-elapsed-time-years = { $amount }ani

##

statistics-average-for-days-studied = Media zilelor studiate
statistics-total = Total
statistics-days-studied = Zile studiate
statistics-average-answer-time-label = Timp mediu de răspuns
statistics-average = Medie
statistics-average-interval = Interval mediu
statistics-due-tomorrow = Programate pentru mâine
# eg 5 of 15 (33.3%)
statistics-amount-of-total-with-percentage = { $amount } din { $total } ({ $percent }%)
statistics-average-over-period = Medie pe perioadă
statistics-reviews-per-day =
    { $count ->
        [one] Un recapitulare/zi
        [few] { $count } recapitulări/zi
       *[other] { $count } recapitulări/zi
    }
statistics-minutes-per-day =
    { $count ->
        [one] Un minut/zi
        [few] { $count } minute/zi
       *[other] { $count } minute/zi
    }
statistics-cards-per-day =
    { $count ->
        [one] Un card/zi
        [few] { $count } carduri/zi
       *[other] { $count } carduri/zi
    }
statistics-average-ease = Ușurință medie
statistics-save-pdf = Salvează pdf
statistics-saved = Salvat.
statistics-stats = status
statistics-true-retention-total = Total carduri
statistics-true-retention-young = Tinere
statistics-true-retention-mature = Matur(e)
