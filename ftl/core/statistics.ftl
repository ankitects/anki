# The date a card will be ready to review
statistics-due-date = Due
# The count of cards waiting to be reviewed
statistics-due-count = Due
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = New #{ $number }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } cards/minute
statistics-average-answer-time = { $average-seconds }s ({ statistics-cards-per-min })

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds =
    { $amount ->
        [one] in { $amount } second
       *[other] in { $amount } seconds
    }
statistics-in-time-span-minutes =
    { $amount ->
        [one] in { $amount } minute
       *[other] in { $amount } minutes
    }
statistics-in-time-span-hours =
    { $amount ->
        [one] in { $amount } hour
       *[other] in { $amount } hours
    }
statistics-in-time-span-days =
    { $amount ->
        [one] in { $amount } day
       *[other] in { $amount } days
    }
statistics-in-time-span-months =
    { $amount ->
        [one] in { $amount } month
       *[other] in { $amount } months
    }
statistics-in-time-span-years =
    { $amount ->
        [one] in { $amount } year
       *[other] in { $amount } years
    }
statistics-cards =
    { $cards ->
        [one] { $cards } card
       *[other] { $cards } cards
    }
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews =
    { $reviews ->
        [one] { $reviews } review
       *[other] { $reviews } reviews
    }
# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { statistics-in-time-span-seconds } part should be pasted in from the English
# version unmodified.
statistics-studied-today =
    Studied { statistics-cards }
    { $unit ->
        [seconds] { statistics-in-time-span-seconds }
        [minutes] { statistics-in-time-span-minutes }
        [hours] { statistics-in-time-span-hours }
        [days] { statistics-in-time-span-days }
        [months] { statistics-in-time-span-months }
       *[years] { statistics-in-time-span-years }
    } today
    ({ $secs-per-card }s/card)
# eg, "Time taken to review card: 5s"
statistics-seconds-taken = { $seconds }s
statistics-today-title = Today
statistics-today-again-count = Again count:
statistics-today-type-counts = Learn: { $learnCount }, Review: { $reviewCount }, Relearn: { $relearnCount }, Filtered: { $filteredCount }
statistics-today-no-cards = No cards have been studied today.
statistics-today-no-mature-cards = No mature cards were studied today.
statistics-today-correct-mature = Correct answers on mature cards: { $correct }/{ $total } ({ $percent }%)
statistics-counts-total-cards = Total
statistics-counts-new-cards = New
statistics-counts-young-cards = Young
statistics-counts-mature-cards = Mature
statistics-counts-suspended-cards = Suspended
statistics-counts-buried-cards = Buried
statistics-counts-early-cards = Early
statistics-counts-learning-cards = Learning
statistics-counts-relearning-cards = Relearning
statistics-counts-title = Card Counts
statistics-counts-separate-suspended-buried-cards = Separate suspended/buried cards
statistics-range-all-time = all
statistics-range-1-year-history = last 12 months
statistics-range-all-history = all history
statistics-range-deck = deck
statistics-range-collection = collection
statistics-range-search = Search
statistics-card-ease-title = Card Ease
statistics-card-ease-subtitle = The lower the ease, the more frequently a card will appear.
# eg "3 cards with 150-170% ease"
statistics-card-ease-tooltip =
    { $cards ->
        [one] { $cards } card with { $percent } ease
       *[other] { $cards } cards with { $percent } ease
    }
statistics-future-due-title = Future Due
statistics-future-due-subtitle = The number of reviews due in the future.
statistics-added-title = Added
statistics-added-subtitle = The number of new cards you have added.
statistics-reviews-count-subtitle = The number of questions you have answered.
statistics-reviews-time-subtitle = The time taken to answer the questions.
statistics-answer-buttons-title = Answer Buttons
# eg Button: 4
statistics-answer-buttons-button-number = Button
# eg Times pressed: 123
statistics-answer-buttons-button-pressed = Times pressed
statistics-answer-buttons-subtitle = The number of times you have pressed each button.
statistics-reviews-title = Reviews
statistics-reviews-time-checkbox = Time
statistics-in-days-single =
    { $days ->
        [0] Today
        [1] Tomorrow
       *[other] In { $days } days
    }
statistics-in-days-range = In { $daysStart }-{ $daysEnd } days
statistics-days-ago-single =
    { $days ->
        [1] Yesterday
       *[other] { $days } days ago
    }
statistics-days-ago-range = { $daysStart }-{ $daysEnd } days ago
statistics-running-total = Running total
statistics-cards-due =
    { $cards ->
        [one] { $cards } card due
       *[other] { $cards } cards due
    }
statistics-backlog-checkbox = Backlog
statistics-intervals-title = Review Intervals
statistics-intervals-subtitle = Delays until reviews are shown again.
statistics-intervals-day-range =
    { $cards ->
        [one] { $cards } card with a { $daysStart }~{ $daysEnd } day interval
       *[other] { $cards } cards with a { $daysStart }~{ $daysEnd } day interval
    }
statistics-intervals-day-single =
    { $cards ->
        [one] { $cards } card with a { $day } day interval
       *[other] { $cards } cards with a { $day } day interval
    }
# hour range, eg "From 14:00-15:00"
statistics-hours-range = From { $hourStart }:00~{ $hourEnd }:00
statistics-hours-correct = { $correct }/{ $total } correct ({ $percent }%)
statistics-hours-title = Hourly Breakdown
statistics-hours-subtitle = Review success rate for each hour of the day.
# shown when graph is empty
statistics-no-data = NO DATA
statistics-calendar-title = Calendar

## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.

statistics-elapsed-time-seconds = { $amount }s
statistics-elapsed-time-minutes = { $amount }m
statistics-elapsed-time-hours = { $amount }h
statistics-elapsed-time-days = { $amount }d
statistics-elapsed-time-months = { $amount }mo
statistics-elapsed-time-years = { $amount }y

##

statistics-average-for-days-studied = Average for days studied
statistics-total = Total
statistics-days-studied = Days studied
statistics-average-answer-time-label = Average answer time
statistics-average = Average
statistics-average-interval = Average interval
statistics-longest-interval = Longest interval
statistics-due-tomorrow = Due tomorrow
# eg 5 of 15 (33.3%)
statistics-amount-of-total-with-percentage = { $amount } of { $total } ({ $percent }%)
statistics-average-over-period = Average over period
statistics-reviews-per-day =
    { $count ->
        [one] { $count } review/day
       *[other] { $count } reviews/day
    }
statistics-minutes-per-day =
    { $count ->
        [one] { $count } minute/day
       *[other] { $count } minutes/day
    }
statistics-cards-per-day =
    { $count ->
        [one] { $count } card/day
       *[other] { $count } cards/day
    }
statistics-average-ease = Average ease
statistics-save-pdf = Save PDF
statistics-saved = Saved.
statistics-stats = stats
