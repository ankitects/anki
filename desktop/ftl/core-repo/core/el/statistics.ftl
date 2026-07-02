# The date a card will be ready to review
statistics-due-date = Προθεσμία
# The count of cards waiting to be reviewed
statistics-due-count = Προγραμματισμένες
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = Νέες #{ $number }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } κάρτες/λεπτό
statistics-average-answer-time = { $average-seconds }s ({ statistics-cards-per-min })

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds =
    { $amount ->
        [one] σε ένα δευτερόλεπτο
       *[other] σε { $amount } δευτερόλεπτα
    }
statistics-in-time-span-minutes =
    { $amount ->
        [one] σε { $amount } λεπτό
       *[other] σε { $amount } λεπτά
    }
statistics-in-time-span-hours =
    { $amount ->
        [one] σε { $amount } ώρα
       *[other] σε { $amount } ώρες
    }
statistics-in-time-span-days =
    { $amount ->
        [one] σε { $amount } μέρα
       *[other] σε { $amount } μέρες
    }
statistics-in-time-span-months =
    { $amount ->
        [one] σε { $amount } μήνα
       *[other] σε { $amount } μήνες
    }
statistics-in-time-span-years =
    { $amount ->
        [one] σε { $amount } χρόνο
       *[other] σε { $amount } χρόνια
    }
# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { statistics-in-time-span-seconds } part should be pasted in from the English
# version unmodified.
statistics-studied-today =
    Διαβάστηκαν { statistics-cards }
    { $unit ->
        [seconds] { statistics-in-time-span-seconds }
        [minutes] { statistics-in-time-span-minutes }
        [hours] { statistics-in-time-span-hours }
        [days] { statistics-in-time-span-days }
        [months] { statistics-in-time-span-months }
       *[years] { statistics-in-time-span-years }
    } σήμερα
    ({ $secs-per-card }s/κάρτα)

##

statistics-cards =
    { $cards ->
        [one] { $cards } κάρτα
       *[other] { $cards } κάρτες
    }
statistics-notes =
    { $notes ->
        [one] { $notes } σημείωση
       *[other] { $notes } σημειώσεις
    }
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews =
    { $reviews ->
        [one] { $reviews } επανάληψη
       *[other] { $reviews } επαναλήψεις
    }
# This fragment of the tooltip in the FSRS simulation
# diagram (Deck options -> FSRS) shows the total number of
# cards that can be recalled or retrieved on a specific date.
statistics-memorized = { $memorized } απομνημονεύτηκαν
statistics-today-title = Σήμερα
statistics-today-again-count = Ξανά:
statistics-today-type-counts = Μελέτη: { $learnCount }, Επανάληψη: { $reviewCount }, Επανεκμάθηση: { $relearnCount }, Φιλτραρισμένες: { $filteredCount }
statistics-today-no-cards = Καμία κάρτα δεν διαβάστηκε σήμερα.
statistics-today-no-mature-cards = Καμία ώριμη κάρτα δεν διαβάστηκε σήμερα.
statistics-today-correct-mature = Σωστές απαντήσεις σε ώριμες κάρτες: { $correct }/{ $total } ({ $percent }%)
statistics-counts-total-cards = Σύνολο
statistics-counts-new-cards = Νέο
statistics-counts-young-cards = Πρόσφατες
statistics-counts-mature-cards = Ώριμες
statistics-counts-suspended-cards = Σε αναστολή
statistics-counts-buried-cards = Σε αναβολή
statistics-counts-filtered-cards = Φιλτραρισμένα
statistics-counts-learning-cards = Εκμάθηση
statistics-counts-relearning-cards = Επανεκμάθηση
statistics-counts-title = Αριθμός καρτών
statistics-counts-separate-suspended-buried-cards = Διαχωρισμός καρτών σε αναβολή/αναστολή

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

statistics-true-retention-title = Πραγματική Ανάκληση
statistics-true-retention-subtitle = Ποσοστό επιτυχίας των καρτών με διάστημα ≥ 1 ημέρα.
statistics-true-retention-tooltip = Εάν χρησιμοποιείτε το FSRS, η πραγματική σας ανάκληση αναμένεται να είναι κοντά στην επιθυμητή σας ανάκληση. Λάβετε υπόψη σας ότι τα δεδομένα για μία μόνο ημέρα είναι ανακριβή, οπότε είναι προτιμότερο να εξετάζετε μηνιαία δεδομένα.
statistics-true-retention-range = Εύρος
statistics-true-retention-pass = Pass
statistics-true-retention-fail = Fail
# This will usually be the same as statistics-counts-total-cards
statistics-true-retention-total = Σύνολο
statistics-true-retention-count = Αριθμός
statistics-true-retention-retention = Ανάκληση
# This will usually be the same as statistics-counts-young-cards
statistics-true-retention-young = Πρόσφατες
# This will usually be the same as statistics-counts-mature-cards
statistics-true-retention-mature = Ώριμες
statistics-true-retention-all = Σύνολο
statistics-true-retention-today = Σήμερα
statistics-true-retention-yesterday = Χθες
statistics-true-retention-week = Τελευταία εβδομάδα
statistics-true-retention-month = Τελευταίος μήνας
statistics-true-retention-year = Τελευταίο έτος
statistics-true-retention-all-time = Συνολικός χρόνος
# If there are no reviews within a specific time period, the retention
# percentage cannot be calculated and is displayed as "N/A."
statistics-true-retention-not-applicable = N/A

##

statistics-range-all-time = ζωή της τράπουλας
statistics-range-1-year-history = τελευταίοι 12 μήνες
statistics-range-all-history = όλο το ιστορικό
statistics-range-deck = τράπουλα
statistics-range-collection = συλλογή
statistics-range-search = Αναζήτηση
statistics-card-ease-title = Ευκολία κάρτας
statistics-card-difficulty-title = Δυσκολία κάρτας
statistics-card-stability-title = Σταθερότητα κάρτας
statistics-card-stability-subtitle = Η καθυστέρηση στην οποία η πιθανότητα ανάκλησης είναι 90%.
statistics-median-stability = Μέση σταθερότητα
statistics-card-retrievability-title = Ανακτησιμότητα κάρτας
statistics-card-ease-subtitle = Όσο χαμηλότερη η ευκολία, τόσο πιο συχνά θα εμφανίζεται η κάρτα.
statistics-card-difficulty-subtitle2 = Όσο μεγαλύτερη η δυσκολία, τόσο πιο αργά θα αυξηθεί η σταθερότητα.
statistics-retrievability-subtitle = Η πιθανότητα ανάκλησης της κάρτας σήμερα.
# eg "3 cards with 150-170% ease"
statistics-card-ease-tooltip =
    { $cards ->
        [one] { $cards } κάρτα με { $percent } ευκολία
       *[other] { $cards } κάρτες με { $percent } ευκολία
    }
statistics-card-difficulty-tooltip =
    { $cards ->
        [one] { $cards } κάρτα με { $percent } δυσκολία
       *[other] { $cards } κάρτες με { $percent } δυσκολία
    }
statistics-retrievability-tooltip =
    { $cards ->
        [one] { $cards } κάρτα με { $percent } ανακτησιμότητα
       *[other] { $cards } κάρτες με { $percent } ανακτησιμότητα
    }
statistics-future-due-title = Πρόγνωση
statistics-future-due-subtitle = Ο αριθμός επαναλήψεων στο μέλλον.
statistics-added-title = Προστέθηκε
statistics-added-subtitle = Ο αριθμός των νέων καρτών που προσθέσατε.
statistics-reviews-count-subtitle = Ο αριθμός των ερωτήσεων που έχετε απαντήσει.
statistics-reviews-time-subtitle = Ο χρόνος που πήρατε για να απαντήσετε στις ερωτήσεις.
statistics-answer-buttons-title = Κουμπιά απάντησης
# eg Button: 4
statistics-answer-buttons-button-number = Κουμπί
# eg Times pressed: 123
statistics-answer-buttons-button-pressed = Φορές που πατήθηκε
statistics-answer-buttons-subtitle = Ο αριθμός που έχετε πατήσει το κάθε κουμπί.
statistics-reviews-title = Επαναλήψεις
statistics-reviews-time-checkbox = Χρόνος
statistics-in-days-single =
    { $days ->
        [0] Σήμερα
        [1] Αύριο
        [one] Σε { $days } μέρα
       *[other] Σε { $days } μέρες
    }
statistics-in-days-range = Σε { $daysStart }-{ $daysEnd } μέρες
statistics-days-ago-single =
    { $days ->
        [1] Χθες
        [one] Χθες
       *[other] πριν { $days } μέρες
    }
statistics-days-ago-range = πριν { $daysStart }-{ $daysEnd } μέρες
statistics-running-total = Συνολικό άθροισμα
statistics-cards-due =
    { $cards ->
        [one] { $cards } προγραμματισμένη κάρτα
       *[other] { $cards } προγραμματισμένες κάρτες
    }
statistics-intervals-title = Διαστήματα
statistics-intervals-subtitle = Το χρονικό διάστημα μέχρι την επανεμφάνιση των καρτών.
statistics-intervals-day-range =
    { $cards ->
        [one] { $cards } κάρτα με διάστημα { $daysStart }~{ $daysEnd } ημερών
       *[other] { $cards } κάρτες με διάστημα { $daysStart }~{ $daysEnd } ημερών
    }
statistics-intervals-day-single =
    { $cards ->
        [one] { $cards } κάρτα με διάστημα { $day } ημερών
       *[other] { $cards } κάρτες με διάστημα { $day } ημερών
    }
statistics-stability-day-range =
    { $cards ->
        [one] { $cards } κάρτα με σταθερότητα { $daysStart }~{ $daysEnd } ημερών
       *[other] { $cards } κάρτες με σταθερότητα { $daysStart }~{ $daysEnd } ημερών
    }
statistics-stability-day-single =
    { $cards ->
        [one] { $cards } κάρτα με σταθερότητα { $day } ημερών
       *[other] { $cards } κάρτες με σταθερότητα { $day } ημερών
    }
# hour range, eg "From 14:00-15:00"
statistics-hours-range = Από { $hourStart }:00~{ $hourEnd }:00
statistics-hours-correct = { $correct }/{ $total } σωστά ({ $percent }%)
statistics-hours-correct-info = → (όχι 'Ξανά')
# the emoji depicts the graph displaying this number
statistics-hours-reviews = 📊 { $reviews } επαναλήψεις
# the emoji depicts the graph displaying this number
statistics-hours-correct-reviews = 📈 { $percent }% σωστά ({ $reviews })
statistics-hours-title = Ωριαία ανάλυση
statistics-hours-subtitle = Ρυθμός επιτυχών επαναλήψεων για κάθε ώρα της ημέρας.
# shown when graph is empty
statistics-no-data = ΚΑΝΕΝΑ ΔΕΔΟΜΕΝΟ
statistics-calendar-title = Ημερολόγιο

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

statistics-average-for-days-studied = Μέσος όρος για ημέρες μελέτης
# This term is used in a variety of contexts to refers to the total amount of
# items (e.g., cards, mature cards, etc) for a given period, rather than the
# total of all existing items.
statistics-total = Σύνολο
statistics-days-studied = Ημέρες μελέτης
statistics-average-answer-time-label = Μέσος χρόνος απάντησης
statistics-average = Μέσος
statistics-median-interval = Μέσο διάστημα
statistics-due-tomorrow = Προθεσμία αύριο
# This string, ‘Daily load,’ appears in the ‘Future due’ table and represents a
# forecasted estimate of the number of cards expected to be reviewed daily in 
# the future. Unlike the other strings in the table that display actual data 
# derived from the current scheduling (e.g., ‘Average’, ‘Due tomorrow’),
# ‘Daily load’ is a projection based on the given data.
statistics-daily-load = Καθημερινός φόρτος
# eg 5 of 15 (33.3%)
statistics-amount-of-total-with-percentage = { $amount } από { $total } ({ $percent }%)
statistics-average-over-period = Αν μελετούσες κάθε μέρα
statistics-reviews-per-day =
    { $count ->
        [one] { $count } επανάληψη/μέρα
       *[other] { $count } επαναλήψεις/μέρα
    }
statistics-minutes-per-day =
    { $count ->
        [one] { $count } λεπτό/μέρα
       *[other] { $count } λεπτά/μέρα
    }
statistics-cards-per-day =
    { $count ->
        [one] { $count } κάρτα/μέρα
       *[other] { $count } κάρτες/μέρα
    }
statistics-median-ease = Μέση ευκολία
statistics-median-difficulty = Μέση δυσκολία
statistics-average-retrievability = Μέση ανακτησιμότητα
statistics-estimated-total-knowledge = Εκτιμώμενη συνολική γνώση
statistics-save-pdf = Αποθήκευση PDF
statistics-saved = Αποθηκεύτηκε.
statistics-stats = στατιστικά
statistics-title = Στατιστικά

## These strings are no longer used - you do not need to translate them if they
## are not already translated.

statistics-average-stability = Μέση σταθερότητα
statistics-average-interval = Μέσο ενδιάμεσο διάστημα
statistics-average-ease = Μέσος όρος ευκολίας
statistics-average-difficulty = Μέση δυσκολία
