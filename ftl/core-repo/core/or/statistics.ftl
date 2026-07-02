# The date a card will be ready to review
statistics-due-date = ଧାର୍ଯ୍ୟ ତାରିଖ
# The count of cards waiting to be reviewed
statistics-due-count = ବାକି ଅଛି
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = ନୂତନ #{ $number }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } ପତ୍ର/ମିନିଟ୍
statistics-average-answer-time = { $average-seconds }ସେ ({ statistics-cards-per-min })

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds =
    { $amount ->
        [one] { $amount } ସେକେଣ୍ଡରେ
       *[other] { $amount } ସେକେଣ୍ଡରେ
    }
statistics-in-time-span-minutes =
    { $amount ->
        [one] { $amount } ମିନିଟରେ
       *[other] { $amount } ମିନିଟରେ
    }
statistics-in-time-span-hours =
    { $amount ->
        [one] { $amount } ଘଣ୍ଟାରେ
       *[other] { $amount } ଘଣ୍ଟାରେ
    }
statistics-in-time-span-days =
    { $amount ->
        [one] { $amount } ଦିନରେ
       *[other] { $amount } ଦିନରେ
    }
statistics-in-time-span-months =
    { $amount ->
        [one] { $amount } ମାସରେ
       *[other] { $amount } ମାସରେ
    }
statistics-in-time-span-years =
    { $amount ->
        [one] { $amount } ବର୍ଷରେ
       *[other] { $amount } ବର୍ଷରେ
    }
statistics-cards =
    { $cards ->
        [one] { $cards }ଟିଏ ପତ୍ର
       *[other] { $cards }ଟି ପତ୍ର
    }
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews =
    { $reviews ->
        [one] { $reviews }ଟିଏ ସମୀକ୍ଷା
       *[other] { $reviews }ଟି ସମୀକ୍ଷା
    }
# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { statistics-in-time-span-seconds } part should be pasted in from the English
# version unmodified.
statistics-studied-today =
    ଆଜି { $unit ->
        [seconds] { statistics-in-time-span-seconds }
        [minutes] { statistics-in-time-span-minutes }
        [hours] { statistics-in-time-span-hours }
        [days] { statistics-in-time-span-days }
        [months] { statistics-in-time-span-months }
       *[years] { statistics-in-time-span-years }
    }
    { statistics-cards } ପତ୍ର ଅଧ୍ୟୟନ କଲେ
    ({ $secs-per-card }ସେ/ପତ୍ର)
# eg, "Time taken to review card: 5s"
statistics-seconds-taken = { $seconds }ସେ
statistics-today-title = ଆଜି
statistics-today-again-count = ପୁନର୍ବାର ଗଣନା:
statistics-today-type-counts = ଶିଖୁଛନ୍ତି: { $learnCount }, ସମୀକ୍ଷା: { $reviewCount }, ପୁନଃ ଶିକ୍ଷାଧିନ: { $relearnCount }, ଶୋଧିତ: { $filteredCount }
statistics-today-no-cards = ଆଜି କୌଣସି ପତ୍ର ଅଧ୍ୟୟନ କରାଯାଇ ନାହିଁ।
statistics-today-no-mature-cards = ଆଜି କୌଣସି ପରିପକ୍ୱ ପତ୍ର ଅଧ୍ୟୟନ କରାଯାଇ ନାହିଁ।
statistics-today-correct-mature = ପରିପକ୍ୱ ପତ୍ରଗୁଡ଼ିକରେ ସଠିକ୍ ଉତ୍ତର ଶତକଡ଼ା: { $correct }/{ $total } ({ $percent }%)
statistics-counts-total-cards = ସମୁଦାୟ
statistics-counts-new-cards = ନୂତନ
statistics-counts-young-cards = ଯୁବ
statistics-counts-mature-cards = ପରିପକ୍ୱ
statistics-counts-suspended-cards = ନିଲମ୍ବିତ ଅଛି
statistics-counts-buried-cards = ସ୍ଥଗିତ ଅଛି
statistics-counts-filtered-cards = ଶୋଧିତ
statistics-counts-learning-cards = ଶିଖୁଛନ୍ତି
statistics-counts-relearning-cards = ପୁନଃ ଶିକ୍ଷାଧିନ
statistics-counts-title = ପତ୍ର ଗଣନା
statistics-counts-separate-suspended-buried-cards = ସ୍ଥଗିତ/ନିଲମ୍ବିତ ପତ୍ରଗୁଡ଼ିକୁ ଅଲଗା କରନ୍ତୁ
statistics-range-all-time = ସମସ୍ତ
statistics-range-1-year-history = ଗତ 12 ମାସ
statistics-range-all-history = ସମସ୍ତ ଇତିବୃତ୍ତି
statistics-range-deck = ଡେକ୍
statistics-range-collection = ସଂଗ୍ରହ
statistics-range-search = ସନ୍ଧାନ
statistics-card-ease-title = ପତ୍ର ସହଜତା
statistics-card-ease-subtitle = ସହଜତା ଯେତେ କମ୍ ହେବ, ଏକ ପତ୍ର ସେତେ ଅଧିକ ଥର ଦୃଶ୍ୟମାନ ହେବ।
# eg "3 cards with 150-170% ease"
statistics-card-ease-tooltip =
    { $cards ->
        [one] { $percent } ସହଜତା ସହିତ { $cards }ଟିଏ ପତ୍ର
       *[other] { $percent } ସହଜତା ସହିତ { $cards }ଟି ପତ୍ର
    }
statistics-future-due-title = ଭବିଷ୍ୟତ ଦେୟ
statistics-future-due-subtitle = ଭବିଷ୍ୟତରେ ଧାର୍ଯ୍ୟ ସମୀକ୍ଷା ସଂଖ୍ୟା।
statistics-added-title = ଯୋଡ଼ାଯାଇଛି
statistics-added-subtitle = ଆପଣ ଯୋଡ଼ିଥିବା ନୂତନ ପତ୍ରର ସଂଖ୍ୟା।
statistics-reviews-count-subtitle = ଆପଣ ଉତ୍ତର ଦେଇଥିବା ପ୍ରଶ୍ନର ସଂଖ୍ୟା।
statistics-reviews-time-subtitle = ପ୍ରଶ୍ନଗୁଡ଼ିକର ଉତ୍ତର ଦେବାକୁ ନିଆଯାଇଥିବା ସମୟ।
statistics-answer-buttons-title = ଉତ୍ତର ବଟନ୍
# eg Button: 4
statistics-answer-buttons-button-number = ବଟନ୍
# eg Times pressed: 123
statistics-answer-buttons-button-pressed = କେତେଥର ଦବାଗଲା
statistics-answer-buttons-subtitle = ଆପଣ ପ୍ରତ୍ୟେକ ବଟନ୍ କୁ କେତେଥର ଦବାଇଛନ୍ତି।
statistics-reviews-title = ସମୀକ୍ଷା
statistics-reviews-time-checkbox = ସମୟ
statistics-in-days-single =
    { $days ->
        [0] ଆଜି
        [1] ଆସନ୍ତାକାଲି
       *[other] { $days } ଦିନ ମଧ୍ୟରେ
    }
statistics-in-days-range = { $daysStart }-{ $daysEnd } ଦିନରେ
statistics-days-ago-single =
    { $days ->
        [1] ଗତକାଲି
       *[other] { $days } ଦିନ ପୂର୍ବେ
    }
statistics-days-ago-range = { $daysStart }-{ $daysEnd } ଦିନ ପୂର୍ବେ
statistics-running-total = ଚାଲୁଥିବା ସମୁଦାୟ
statistics-cards-due =
    { $cards ->
        [one] { $cards }ଟିଏ ପତ୍ର ବାକି ଅଛି
       *[other] { $cards }ଟି ପତ୍ର ବାକି ଅଛି
    }
statistics-backlog-checkbox = ବ୍ୟାକ୍‍‌ଲଗ୍
statistics-intervals-title = ସମୀକ୍ଷା ଅନ୍ତରାଳ
statistics-intervals-subtitle = ସମୀକ୍ଷାଗୁଡ଼ିକ ପୁନର୍ବାର ଦେଖାଯିବା ପର୍ଯ୍ୟନ୍ତ ବିଳମ୍ବ।
statistics-intervals-day-range =
    { $cards ->
        [one] { $daysStart }~{ $daysEnd } ଦିନ ବ୍ୟବଧାନ ସହିତ { $cards }ଟିଏ ପତ୍ର
       *[other] { $daysStart }~{ $daysEnd } ଦିନ ବ୍ୟବଧାନ ସହିତ { $cards }ଟି ପତ୍ର
    }
statistics-intervals-day-single =
    { $cards ->
        [one] { $day } ଦିନ ବ୍ୟବଧାନ ସହିତ { $cards }ଟିଏ ପତ୍ର
       *[other] { $day } ଦିନ ବ୍ୟବଧାନ ସହିତ { $cards }ଟି ପତ୍ର
    }
# hour range, eg "From 14:00-15:00"
statistics-hours-range = { $hourStart }:00~{ $hourEnd }:00 ରୁ
statistics-hours-correct = { $correct }/{ $total } ଠିକ୍‌ ({ $percent }%)
statistics-hours-title = ଘଣ୍ଟାକ୍ରମେ ଦେଖିବା
statistics-hours-subtitle = ଦିନର ପ୍ରତ୍ୟେକ ଘଣ୍ଟା ପାଇଁ ସମୀକ୍ଷା ସଫଳତା ହାର।
# shown when graph is empty
statistics-no-data = କୌଣସି ଡାଟା ନାହିଁ
statistics-calendar-title = କ୍ୟାଲେଣ୍ଡର୍

## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.

statistics-elapsed-time-seconds = { $amount }ସେ
statistics-elapsed-time-minutes = { $amount }ମି
statistics-elapsed-time-hours = { $amount }ଘ
statistics-elapsed-time-days = { $amount }ଦି
statistics-elapsed-time-months = { $amount }ମା
statistics-elapsed-time-years = { $amount }ବ

##

statistics-average-for-days-studied = ଅଧ୍ୟୟନ କରାଯାଇଥିବା ଦିନଗୁଡ଼ିକ ପାଇଁ ହାରାହାରି
statistics-total = ମୋଟ
statistics-days-studied = ଅଧ୍ୟୟନ ଦିନସଂଖ୍ୟା
statistics-average-answer-time-label = ହାରାହାରି ଉତ୍ତର ସମୟ
statistics-average = ହାରାହାରି
statistics-average-interval = ହାରାହାରି ଅନ୍ତରାଳ
statistics-longest-interval = ଦୀର୍ଘତମ ଅନ୍ତରାଳ
statistics-due-tomorrow = ଆସନ୍ତାକାଲି ପାଇଁ ବାକି
# eg 5 of 15 (33.3%)
statistics-amount-of-total-with-percentage = { $total } ରୁ { $amount } ({ $percent }%)
statistics-average-over-period = ଅବଧି ମଧ୍ୟରେ ହାରାହାରି
statistics-reviews-per-day =
    { $count ->
        [one] { $count } ସମୀକ୍ଷା/ଦିନ
       *[other] { $count }ଟି ସମୀକ୍ଷା/ଦିନ
    }
statistics-minutes-per-day =
    { $count ->
        [one] { $count } ମିନିଟ୍/ଦିନ
       *[other] { $count } ମିନିଟ୍/ଦିନ
    }
statistics-cards-per-day =
    { $count ->
        [one] { $count }ଟିଏ ପତ୍ର/ଦିନ
       *[other] { $count }ଟି ପତ୍ର/ଦିନ
    }
statistics-average-ease = ହାରାହାରି ସହଜତା
statistics-save-pdf = PDF ସଂରକ୍ଷଣ କରନ୍ତୁ
statistics-saved = ସଞ୍ଚୟ ହୋଇଛି।
statistics-stats = ପରିସଂଖ୍ୟାନ
statistics-true-retention-total = ସମୁଦାୟ
statistics-true-retention-young = ଯୁବ
statistics-true-retention-mature = ପରିପକ୍ୱ
