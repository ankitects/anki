# The date a card will be ready to review
statistics-due-date = morji detri
# The count of cards waiting to be reviewed
statistics-due-count = morji
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = { $number } moi lo'i cnino

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } karda fe'i mentu
statistics-average-answer-time = snidu li { $average-seconds } to { statistics-cards-per-min } toi

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds = ca lo snidu be li { $amount }
statistics-in-time-span-minutes = ca lo mentu be li { $amount }
statistics-in-time-span-hours = ca lo cacra be li { $amount }
statistics-in-time-span-days = ca lo djedi be li { $amount }
statistics-in-time-span-months = ca lo masti be li { $amount }
statistics-in-time-span-years = ca lo nanca be li { $amount }
statistics-cards = { $cards } karda
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews = { $reviews } nu morji
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
statistics-today-title = cabdei
statistics-today-again-count = .i da zo'u fliba tu'a da
statistics-today-type-counts = .i cilre { $learnCount } da .i morji { $reviewCount } da .i cilre { $relearnCount } da za'u re'u .i julne { $filteredCount } da
statistics-today-no-cards = .i tadni no karda ca lo cabdei
statistics-today-no-mature-cards = .i tadni no karda poi makcu ku'o ca lo cabdei
statistics-counts-total-cards = se zilkancu lo'i karda
statistics-counts-new-cards = cnino
statistics-counts-young-cards = na'e makcu
statistics-counts-mature-cards = makcu
statistics-counts-suspended-cards = se mipri
statistics-counts-buried-cards = zasni se mipri
statistics-counts-learning-cards = cilre
statistics-range-deck = karda selcmi
statistics-range-collection = karda selcmi selcmi
statistics-range-search = sisku
statistics-future-due-title = balvi
statistics-future-due-subtitle = .i se zilkancu lo'i karda poi jai se bilga fai lo ka ce'u ba morji
statistics-added-title = se jmina
statistics-added-subtitle = .i se zilkancu lo'i karda poi cnino poi do pu jmina
statistics-reviews-count-subtitle = .i se zilkancu lo'i preti poi do pu spuda
statistics-reviews-time-subtitle = .i temci fi lo nu spuda lo preti
statistics-answer-buttons-title = te spuda batkyuidje
statistics-answer-buttons-subtitle = .i ro da poi batkyuidje zo'u se zilkancu lo'i nu do terca'a fi da
statistics-reviews-title = morji
statistics-reviews-time-checkbox = temci
statistics-in-days-single =
    { $days ->
        [0] cabdei
        [1] bavlamdei
       *[other] ba'o li { $days } djedi
    }
statistics-intervals-subtitle = .i temci fi lo nu za'u re'u bilga lo ka ce'u morji

## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.


##

statistics-average-for-days-studied = cnano fi lo'i tadni djedi
statistics-total = sumji
statistics-days-studied = tadni djedi
statistics-average-answer-time-label = cnano fi lo'i spuda temci
statistics-average = cnano
statistics-average-interval = cnano fi lo'i temci
statistics-longest-interval = traji lo ka clani kei lo'i temci
statistics-due-tomorrow = morji ca lo bavlamdei
statistics-average-over-period = se vanbi lo nu do tadni ca ro djedi da'i
statistics-average-ease = cnano fi lo'i ni frili
statistics-save-pdf = rejgau pa me la me py dy fy.
statistics-saved = .i mo'u rejgau
statistics-stats = datni
statistics-true-retention-total = se zilkancu lo'i karda
statistics-true-retention-young = na'e makcu
statistics-true-retention-mature = makcu
