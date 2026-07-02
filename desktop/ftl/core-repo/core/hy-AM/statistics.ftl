# The date a card will be ready to review
statistics-due-date = Կրկնելիք
# The count of cards waiting to be reviewed
statistics-due-count = Կրկնելիք
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = Նոր #{ $number }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } քարտ/րոպեում

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds =
    { $amount ->
        [one] { $amount } վայրկյանում
       *[other] { $amount } վայրկյանում
    }
statistics-in-time-span-minutes =
    { $amount ->
        [one] { $amount } րոպեում
       *[other] { $amount } րոպեում
    }
statistics-in-time-span-hours =
    { $amount ->
        [one] { $amount } ժամում
       *[other] { $amount } ժամում
    }
statistics-in-time-span-days =
    { $amount ->
        [one] { $amount } օրում
       *[other] { $amount } օրում
    }
statistics-in-time-span-months =
    { $amount ->
        [one] { $amount } ամսում
       *[other] { $amount } ամսում
    }
statistics-in-time-span-years =
    { $amount ->
        [one] { $amount } տարում
       *[other] { $amount } տարում
    }
statistics-cards =
    { $cards ->
        [one] { $cards } քարտ
       *[other] { $cards } քարտ
    }
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews =
    { $reviews ->
        [one] { $reviews } վերադիտում
       *[other] { $reviews } վերադիտում
    }
# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { statistics-in-time-span-seconds } part should be pasted in from the English
# version unmodified.
statistics-studied-today =
    Սովորեցիք { statistics-cards } { $unit ->
        [seconds] { statistics-in-time-span-seconds }
        [minutes] { statistics-in-time-span-minutes }
        [hours] { statistics-in-time-span-hours }
        [days] { statistics-in-time-span-days }
        [months] { statistics-in-time-span-months }
       *[years] { statistics-in-time-span-years }
    } այսօր ({ $secs-per-card }վ./քարտ)
# eg, "Time taken to review card: 5s"
statistics-seconds-taken = { $seconds }վ
statistics-today-title = Այսօր
statistics-today-again-count = Չհիշվողների քանակը՝
statistics-today-type-counts = Սովորվող՝ { $learnCount }, Կրկնվող՝ { $reviewCount }, Վերասովորվող՝ { $relearnCount }, Զտված՝ { $filteredCount }
statistics-today-no-cards = Այսօր ոչ մի քարտ չեք սովորել:
statistics-today-no-mature-cards = Այսօր ոչ մի հասուն քարտ չեք սովորել:
statistics-today-correct-mature = Հասուն քարտերի ճիշտ պատասխաններ՝ { $correct }/{ $total } ({ $percent }%)
statistics-counts-total-cards = Քարտերի քանակը
statistics-counts-new-cards = Նոր
statistics-counts-young-cards = Թարմերը
statistics-counts-mature-cards = Հասունները
statistics-counts-suspended-cards = Հեռացվածները
statistics-counts-buried-cards = Առանձնացված
statistics-counts-learning-cards = Սովորվող
statistics-range-all-time = ամբողջ ընթացքում
statistics-range-all-history = ամբողջ պատմությունը
statistics-range-deck = կապուկ
statistics-range-collection = հավաքածու
statistics-range-search = Որոնում
statistics-future-due-title = Կանխատեսում
statistics-future-due-subtitle = Կրկնելիք քարտերի քանակը:
statistics-added-title = Ավելացվել է
statistics-added-subtitle = Ձեր կողմից ավելացված նոր քարտերի քանակը:
statistics-reviews-count-subtitle = Ձեր կողմից պատասխանված քարտերի քանակը:
statistics-reviews-time-subtitle = Պատասխանների վրա ծախսված ժամանակ:
statistics-answer-buttons-title = Պատասխանի կոճակներ
# eg Button: 4
statistics-answer-buttons-button-number = Կոճակ
statistics-answer-buttons-subtitle = Յուրաքանչյուր կոճակը սեղմելու քանակը:
statistics-reviews-title = Կրկնություններ
statistics-reviews-time-checkbox = Ժամ
statistics-intervals-title = Ժամանակամիջոցներ
statistics-intervals-subtitle = Հետաձգումները հաջորդ կրկնությունից առաջ:
statistics-hours-title = Ըստ օրվա ժամանակի
statistics-hours-subtitle = Հաջողակ կրկնությունների բաժինը օրվա յուրաքանչյուր ժամի համար:
statistics-calendar-title = Օրացույց

## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.

statistics-elapsed-time-seconds = { $amount }վ.
statistics-elapsed-time-minutes = { $amount }ր.
statistics-elapsed-time-hours = { $amount }ժ.
statistics-elapsed-time-days = { $amount }օր
statistics-elapsed-time-months = { $amount }ամ.
statistics-elapsed-time-years = { $amount }տ.

##

statistics-average-for-days-studied = Միջինը սովորած օրերի համար
statistics-total = Ընդհանուր
statistics-days-studied = Սովորած օրերի մասնաբաժինը
statistics-average-answer-time-label = Պատասխանելու միջին ժամանակը
statistics-average = Միջինը
statistics-average-interval = Միջին ժամանակամիջոցը
statistics-longest-interval = Ամենաերկար ժամանակամիջոցը
statistics-due-tomorrow = Վաղը կրկնելիք
statistics-average-over-period = Եթե ամեն օր սովորեիք
statistics-average-ease = Միջին հեշտություն
statistics-save-pdf = Պահել PDF-ում
statistics-saved = Պահված է:
statistics-stats = վիճակագրություն
statistics-true-retention-total = Քարտերի քանակը
statistics-true-retention-young = Թարմերը
statistics-true-retention-mature = Հասունները
