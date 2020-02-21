# The date a card will be ready to review
due-date = Due
# The count of cards waiting to be reviewed
due-count = Due
# Shown in the Due column of the Browse screen when the card is a new card
due-for-new-card = New #{$number}


## eg 16.8s (3.6 cards/minute)

cards-per-min = {$cards-per-minute} cards/minute
average-answer-time = {$average-seconds}s ({cards-per-min})

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

in-time-span-seconds = { $amount ->
   [one]   in {$amount} second
  *[other] in {$amount} seconds
  }

in-time-span-minutes = { $amount ->
   [one]   in {$amount} minute
  *[other] in {$amount} minutes
  }

in-time-span-hours = { $amount ->
   [one]   in {$amount} hour
  *[other] in {$amount} hours
  }

in-time-span-days = { $amount ->
   [one]   in {$amount} day
  *[other] in {$amount} days
  }

in-time-span-months = { $amount ->
   [one]   in {$amount} month
  *[other] in {$amount} months
  }

in-time-span-years = { $amount ->
   [one]   in {$amount} year
  *[other] in {$amount} years
  }

##

cards = { $cards ->
   [one] {$cards} card
  *[other] {$cards} cards
  }

# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { in-time-span-seconds } part should be pasted in from the English
# version unmodified.
studied-today =
  Studied { cards }
  { $unit ->
     [seconds] { in-time-span-seconds }
     [minutes] { in-time-span-minutes }
     [hours]   { in-time-span-hours }
     [days]    { in-time-span-days }
     [months]  { in-time-span-months }
    *[years]   { in-time-span-years }
  } today
  ({$secs-per-card}s/card)
