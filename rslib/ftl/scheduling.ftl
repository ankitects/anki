## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = {$amount}s
scheduling-answer-button-time-minutes = {$amount}m
scheduling-answer-button-time-hours = {$amount}h
scheduling-answer-button-time-days = {$amount}d
scheduling-answer-button-time-months = {$amount}mo
scheduling-answer-button-time-years = {$amount}y

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds = { $amount ->
   [one]   {$amount} second
  *[other] {$amount} seconds
  }

scheduling-time-span-minutes = { $amount ->
   [one]   {$amount} minute
  *[other] {$amount} minutes
  }

scheduling-time-span-hours = { $amount ->
   [one]   {$amount} hour
  *[other] {$amount} hours
  }

scheduling-time-span-days = { $amount ->
   [one]   {$amount} day
  *[other] {$amount} days
  }

scheduling-time-span-months = { $amount ->
   [one]   {$amount} month
  *[other] {$amount} months
  }

scheduling-time-span-years = { $amount ->
   [one]   {$amount} year
  *[other] {$amount} years
  }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
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

scheduling-learn-remaining = { $remaining ->
    [one]   There is one remaining learning card due later today.
   *[other] There are {$remaining} learning cards due later today.
  }
