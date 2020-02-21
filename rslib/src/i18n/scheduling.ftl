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

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
next-learn-due =
  The next learning card will be ready in { $unit ->
     [seconds] { $amount ->
        [one]   {$amount} second
       *[other] {$amount} seconds
       }
     [minutes] { $amount ->
        [one]   {$amount} minute
       *[other] {$amount} minutes
       }
    *[hours] { $amount ->
        [one]   {$amount} hour
       *[other] {$amount} hours
       }
    }.

learn-remaining = { $remaining ->
    [one]   There is one remaining learning card due later today.
   *[other] There are {$remaining} learning cards due later today.
  }
