# The date a card will be ready to review
statistics-due-date = ដល់កំណត់
# The count of cards waiting to be reviewed
statistics-due-count = ដល់កំណត់
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = ថ្មី #{ $number }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } កាត/នាទី
statistics-average-answer-time = { $average-seconds }វិនាទី ({ statistics-cards-per-min })

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds =
    { $amount ->
        [one] ក្នុង { $amount } វិនាទី
       *[other] ក្នុង { $amount } វិនាទី
    }
statistics-in-time-span-minutes =
    { $amount ->
        [one] ក្នុង { $amount } នាទី
       *[other] ក្នុង { $amount } នាទី
    }

##


## True Retention represents your actual retention rate from past reviews, in
## comparison to the "desired retention" parameter of FSRS, which forecasts
## future retention. True Retention is the percentage of all reviewed cards
## that were marked as "Hard," "Good," or "Easy" within a specific time period.
##
## Most of these strings are used as column / row headings in a table.
## (Excluding -title and -subtitle)
## It is important to keep these translations short so that they do not make
## the table too large to display on a single stats card.
##
## N.B. Stats cards may be very small on mobile devices and when the Stats
##      window is certain sizes.


##


## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.


##

