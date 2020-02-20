## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

answer-button-time-seconds = {$amount}s
answer-button-time-minutes = {$amount}m
answer-button-time-hours = {$amount}h
answer-button-time-days = {$amount}d
answer-button-time-months = {$amount}mo
answer-button-time-years = {$amount}y

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

time-span-seconds = { $amount ->
   [one]   {$amount} second
  *[other] {$amount} seconds
  }

time-span-minutes = { $amount ->
   [one]   {$amount} minute
  *[other] {$amount} minutes
  }

time-span-hours = { $amount ->
   [one]   {$amount} hour
  *[other] {$amount} hours
  }

time-span-days = { $amount ->
   [one]   {$amount} day
  *[other] {$amount} days
  }

time-span-months = { $amount ->
   [one]   {$amount} month
  *[other] {$amount} months
  }

time-span-years = { $amount ->
   [one]   {$amount} year
  *[other] {$amount} years
  }

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes". In English the text
## just adds "in" to the start of time-span-*, but other
## languages may need to use different words here instead
## of reusing the time-span-* text.
## See the 'studied-today' context for where this is used,
## and the Polish translation for an example of different
## wordings used here.

in-time-span-seconds = in { time-span-seconds }
in-time-span-minutes = in { time-span-minutes }
in-time-span-hours = in { time-span-hours }
in-time-span-days = in { time-span-days }
in-time-span-months = in { time-span-months }
in-time-span-years = in { time-span-years }

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
  ({$secs-per-card}s/card).
