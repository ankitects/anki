## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount }s
scheduling-answer-button-time-minutes = { $amount }m
scheduling-answer-button-time-hours = { $amount }h
scheduling-answer-button-time-days = { $amount }d
scheduling-answer-button-time-months = { $amount }mo
scheduling-answer-button-time-years = { $amount }y

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] { $amount } second
       *[other] { $amount } seconds
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] { $amount } minute
       *[other] { $amount } minutes
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } hour
       *[other] { $amount } hours
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } day
       *[other] { $amount } days
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } month
       *[other] { $amount } months
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } year
       *[other] { $amount } years
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    The next learning card will be ready in { $unit ->
        [seconds]
            { $amount ->
                [one] { $amount } second
               *[other] { $amount } seconds
            }
        [minutes]
            { $amount ->
                [one] { $amount } minute
               *[other] { $amount } minutes
            }
       *[hours]
            { $amount ->
                [one] { $amount } hour
               *[other] { $amount } hours
            }
    }.
scheduling-learn-remaining =
    { $remaining ->
        [one] There is one remaining learning card due later today.
       *[other] There are { $remaining } learning cards due later today.
    }
scheduling-congratulations-finished = Congratulations! You have finished this deck for now.
scheduling-today-review-limit-reached =
    Today's review limit has been reached, but there are still cards
    waiting to be reviewed. For optimum memory, consider increasing
    the daily limit in the options.
scheduling-today-new-limit-reached =
    There are more new cards available, but the daily limit has been
    reached. You can increase the limit in the options, but please
    bear in mind that the more new cards you introduce, the higher
    your short-term review workload will become.
scheduling-buried-cards-found = One or more cards were buried, and will be shown tomorrow. You can { $unburyThem } if you wish to see them immediately.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = unbury them
scheduling-how-to-custom-study = If you wish to study outside of the regular schedule, you can use the { $customStudy } feature.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = custom study

## Scheduler upgrade

scheduling-update-soon = Anki 2.1 comes with a new scheduler, which fixes a number of issues that previous Anki versions had. Updating to it is recommended.
scheduling-update-done = Scheduler updated successfully.
scheduling-update-button = Update
scheduling-update-later-button = Later
scheduling-update-more-info-button = Learn More
scheduling-update-required =
    Your collection needs to be upgraded to the V2 scheduler.
    Please select { scheduling-update-more-info-button } before proceeding.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = Always include question side when replaying audio
scheduling-at-least-one-step-is-required = At least one step is required.
scheduling-automatically-play-audio = Automatically play audio
scheduling-bury-related-new-cards-until-the = Bury related new cards until the next day
scheduling-bury-related-reviews-until-the-next = Bury related reviews until the next day
scheduling-days = days
scheduling-description = Description
scheduling-easy-bonus = Easy bonus
scheduling-easy-interval = Easy interval
scheduling-end = (end)
scheduling-general = General
scheduling-graduating-interval = Graduating interval
scheduling-hard-interval = Hard interval
scheduling-ignore-answer-times-longer-than = Ignore answer times longer than
scheduling-interval-modifier = Interval modifier
scheduling-lapses = Lapses
scheduling-lapses2 = lapses
scheduling-learning = Learning
scheduling-leech-action = Leech action
scheduling-leech-threshold = Leech threshold
scheduling-maximum-interval = Maximum interval
scheduling-maximum-reviewsday = Maximum reviews/day
scheduling-minimum-interval = Minimum interval
scheduling-mix-new-cards-and-reviews = Mix new cards and reviews
scheduling-new-cards = New Cards
scheduling-new-cardsday = New cards/day
scheduling-new-interval = New interval
scheduling-new-options-group-name = New options group name:
scheduling-options-group = Options group:
scheduling-order = Order
scheduling-parent-limit = (parent limit: { $val })
scheduling-reset-counts = Reset repetition and lapse counts
scheduling-restore-position = Restore original position where possible
scheduling-review = Review
scheduling-reviews = Reviews
scheduling-seconds = seconds
scheduling-set-all-decks-below-to = Set all decks below { $val } to this option group?
scheduling-set-for-all-subdecks = Set for all subdecks
scheduling-show-answer-timer = Show on-screen timer
scheduling-show-new-cards-after-reviews = Show new cards after reviews
scheduling-show-new-cards-before-reviews = Show new cards before reviews
scheduling-show-new-cards-in-order-added = Show new cards in order added
scheduling-show-new-cards-in-random-order = Show new cards in random order
scheduling-starting-ease = Starting ease
scheduling-steps-in-minutes = Steps (in minutes)
scheduling-steps-must-be-numbers = Steps must be numbers.
scheduling-tag-only = Tag Only
scheduling-the-default-configuration-cant-be-removed = The default configuration can't be removed.
scheduling-your-changes-will-affect-multiple-decks = Your changes will affect multiple decks. If you wish to change only the current deck, please add a new options group first.
scheduling-deck-updated =
    { $count ->
        [one] { $count } deck updated.
       *[other] { $count } decks updated.
    }
scheduling-set-due-date-prompt =
    { $cards ->
        [one] Show card in how many days?
       *[other] Show cards in how many days?
    }
scheduling-set-due-date-prompt-hint =
    0 = today
    1! = tomorrow + change interval to 1
    3-7 = random choice of 3-7 days
scheduling-set-due-date-done =
    { $cards ->
        [one] Set due date of { $cards } card.
       *[other] Set due date of { $cards } cards.
    }
scheduling-graded-cards-done =
    { $cards ->
        [one] Graded { $cards } card.
       *[other] Graded { $cards } cards.
    }
scheduling-forgot-cards =
    { $cards ->
        [one] Reset { $cards } card.
       *[other] Reset { $cards } cards.
    }
