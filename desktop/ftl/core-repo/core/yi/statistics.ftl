# The date a card will be ready to review
statistics-due-date = טערמין
# The count of cards waiting to be reviewed
statistics-due-count = טערמיניקע
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = נײַ #{ $number }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } קאַרטלעך/מינוט
statistics-average-answer-time = { $average-seconds }סעק. ({ statistics-cards-per-min })

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds =
    { $amount ->
        [one] אין { $amount } סעקונד
       *[other] אין { $amount } סעקונדעס
    }
statistics-in-time-span-minutes =
    { $amount ->
        [one] אין { $amount } מינוט
       *[other] אין { $amount } מינוטן
    }
statistics-in-time-span-hours =
    { $amount ->
        [one] אין { $amount } שעה
       *[other] אין { $amount } שעה
    }
statistics-in-time-span-days =
    { $amount ->
        [one] אין { $amount } טאָג
       *[other] אין { $amount } טעג
    }
statistics-in-time-span-months =
    { $amount ->
        [one] אין { $amount } חודש
       *[other] אין { $amount } חדשים
    }
statistics-in-time-span-years =
    { $amount ->
        [one] אין { $amount } יאָר
       *[other] אין { $amount } יאָרן
    }
# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { statistics-in-time-span-seconds } part should be pasted in from the English
# version unmodified.
statistics-studied-today =
    { $unit ->
        [seconds] אײַנגע׳חזר׳ט { statistics-cards } { statistics-in-time-span-seconds } הײַנט ({ $secs-per-card } סעק/קאַרטל)
        [minutes] אײַנגע׳חזר׳ט { statistics-cards } { statistics-in-time-span-minutes } הײַנט ({ $secs-per-card } סעק/קאַרטל)
        [hours]
            אײַנגע׳חזר׳ט { statistics-cards }
            { statistics-in-time-span-hours }  הײַנט ({ $secs-per-card } סעק/קאַרטל)
        [days] אײַנגע׳חזר׳ט { statistics-cards } { statistics-in-time-span-days }  הײַנט ({ $secs-per-card } ס/קאַרטל)
        [months] אײַנגע׳חזר׳ט { statistics-cards } { statistics-in-time-span-months } הײַנט ({ $secs-per-card } סעק/קאַרטל)
       *[years] אײַנגע׳חזר׳ט { statistics-cards } { statistics-in-time-span-years } הײַנט ({ $secs-per-card } סעק/קאַרטל)
    }

##

statistics-cards =
    { $cards ->
        [one] { $cards } קאַרטל
       *[other] { $cards } קאַרטלעך
    }
statistics-notes =
    { $notes ->
        [one] { $notes } נאָטיץ
       *[other] { $notes } נאָטיצן
    }
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews =
    { $reviews ->
        [one] { $reviews } אײַנ׳חזר׳ונג
       *[other] { $reviews } אײַנ׳חזר׳ונגען
    }
# This fragment of the tooltip in the FSRS simulation
# diagram (Deck options -> FSRS) shows the total number of
# cards that can be recalled or retrieved on a specific date.
statistics-memorized = { $memorized } קאַרטלעך אויסגעלערנט
statistics-today-title = הײַנט
statistics-today-again-count = ווידער-חשבון:
statistics-today-type-counts = געלערנט: { $learnCount }, אײַנגע׳חזר׳ט: { $reviewCount }, קריקגעלערנט: { $relearnCount }, פֿילטרירט: { $filteredCount }
statistics-today-no-cards = קיין קאַרטל איז הײַנט נישט אײַנגע׳חזר׳ט געוואָרן.
statistics-today-no-mature-cards = קיין דערוואַקסן קאַרטל איז הײַנט נישט אײַנגע׳חזר׳ט געוואָרן.
statistics-today-correct-mature = ריכטיקע ענטפֿערס אויף דערוואַקסענע קאַרטלעך: { $correct }/{ $total } ({ $percent }%)
statistics-counts-total-cards = סך-הכּל
statistics-counts-new-cards = נײַע
statistics-counts-young-cards = יונגע
statistics-counts-mature-cards = דערוואַקסענע
statistics-counts-suspended-cards = אָפּגעשטעלטע
statistics-counts-buried-cards = אָפּגעהאַלטנע
statistics-counts-filtered-cards = פֿילטרירטע
statistics-counts-learning-cards = לערנענדיקע
statistics-counts-relearning-cards = קריקלערנענדיקע
statistics-counts-title = קאַרטל חשבונות
statistics-counts-separate-suspended-buried-cards = באַזונדערע אָפּגעשטעלטע/אָפּגעהאַלטענע קאַרטלעך

## Retention represents your actual retention from past reviews, in
## comparison to the "desired retention" setting of FSRS, which forecasts
## future retention. Retention is the percentage of all reviewed cards
## that were marked as "Hard," "Good," or "Easy" within a specific time period.
##
## Most of these strings are used as column / row headings in a table.
## (Excluding -title and -subtitle)
## It is important to keep these translations short so that they do not make
## the table too large to display on a single stats card.
##
## N.B. Stats cards may be very small on mobile devices and when the Stats
##      window is certain sizes.

statistics-true-retention-title = זכּרון
statistics-true-retention-subtitle = צאָל איבערהיפּן (קאַרטלעך מיט צווישנצײַט ≥ 1 טאַָג)
statistics-true-retention-tooltip = אַז מע ניצט FSRS, ס׳איז ערוואַרטעט אַז דער זכּרון-צאָל זאָל זײַן כּמעט גלײַך מיט דעם געוווּנטשן זכּרון. האַלט אין אויג, אַז די דאַטן פֿון איין מעת-לעת איז אומפּינקטלעך, איז דאָך בעסער איבערצוקוקן חדושלעכע דאַטן.
statistics-true-retention-range = גאַמע
statistics-true-retention-pass = ריכטיק
statistics-true-retention-fail = פֿאַרפֿעלט
# This will usually be the same as statistics-counts-total-cards
statistics-true-retention-total = סך-הכּל
statistics-true-retention-count = צאָל
statistics-true-retention-retention = זכּרון
# This will usually be the same as statistics-counts-young-cards
statistics-true-retention-young = יונגע
# This will usually be the same as statistics-counts-mature-cards
statistics-true-retention-mature = דערוואַקסענע
statistics-true-retention-all = אַלע
statistics-true-retention-today = הײַנט
statistics-true-retention-yesterday = נעכטן
statistics-true-retention-week = פֿאָריקע וואָך
statistics-true-retention-month = פֿאָריקע חודש
statistics-true-retention-year = פֿאַר אַ יאָרן
statistics-true-retention-all-time = אַלע צײַטן
# If there are no reviews within a specific time period, the retention
# percentage cannot be calculated and is displayed as "N/A."
statistics-true-retention-not-applicable = נישט חל

##

statistics-range-all-time = אַלע
statistics-range-1-year-history = פֿאַרגאַנגענע 12 חדשים
statistics-range-all-history = גאַנצע געשיכטע
statistics-range-deck = טעשל
statistics-range-collection = זאַמלונג
statistics-range-search = זוכן
statistics-card-ease-title = קאַרטל-גרינגקייט
statistics-card-difficulty-title = קאַרטל-שוועריקייט
statistics-card-stability-title = קאַרטל-געזעצקייט
statistics-card-stability-subtitle = דער אָפּייג וואָס נאָך אים, פֿאַלט די דערמאָנענקייט ביז 90%.
statistics-median-stability = מיטלפּונקט סטאַבילקייט
statistics-card-retrievability-title = קאַרטל דערמאָנענקייט
statistics-card-ease-subtitle = וואָס נידעריקער די גרינגקייט, אַלץ אָפֿטער ווערט אַ קאַרטל אויסגעוויזן.
statistics-card-difficulty-subtitle2 = וואָס העכער די שוועריקייט, אַלץ פּאַמעלעכער וואָס די געזעצקייט ווערן פֿאַרגרעסערט.
statistics-retrievability-subtitle = דער משמעות פון זיך דערמאָנען אַ קאַרטל הײַנט.
# eg "3 cards with 150-170% ease"
statistics-card-ease-tooltip =
    { $cards ->
        [one] { $cards } קאַרטל מיט { $percent } גרינגקייט
       *[other] { $cards } קאַרטלעך מיט { $percent } גרינגקייט
    }
statistics-card-difficulty-tooltip =
    { $cards ->
        [one] { $cards } קאַרטל מיט { $percent } שוועריקייט
       *[other] { $cards } קאַרטלעך מיט { $percent } שוועריקייט
    }
statistics-retrievability-tooltip =
    { $cards ->
        [one] { $cards } קאַרטל מיט { $percent } דערמאָנענקייט
       *[other] { $cards } קאַרטלעך מיט { $percent } דערמאָנענקייט
    }
statistics-future-due-title = טערמיניקע אין צוקונפֿט
statistics-future-due-subtitle = וויפֿל איבער׳חזר׳ונגען וועט ווערן טערמיניק אין דער צוקונפֿט.
statistics-added-title = צוגעלייגט
statistics-added-subtitle = וויפֿל נײַע קאַרטלעך זענען געשאַפֿן געוואָרן.
statistics-reviews-count-subtitle = וויפֿל פֿראַגעס זענען געענטפֿערט געוואָרן.
statistics-reviews-time-subtitle = דער משך פון ענטפֿערן פֿראַגעס.
statistics-answer-buttons-title = ענטפֿער-קנעפּלעך
# eg Button: 4
statistics-answer-buttons-button-number = קנעפּל
# eg Times pressed: 123
statistics-answer-buttons-button-pressed = מאָל געקוועטשט
statistics-answer-buttons-subtitle = וויפֿל מאָל איז איטלעכע קנעפּל געקוועטשט געוואָרן.
statistics-reviews-title = אײַנ׳חזר׳ונגען
statistics-reviews-time-checkbox = משך
statistics-in-days-single =
    { $days ->
        [1] מאָרגן
        [0] הײַנט
        [one] נאָך { $days } טאָג
       *[other] נאָך { $days } טעג
    }
statistics-in-days-range = נאָך { $daysStart }-{ $daysEnd } טעג
statistics-days-ago-single =
    { $days ->
        [1] נעכטן
        [one] מיט { $days } טאָג צוריק
       *[other] מיט { $days } טעג צוריק
    }
statistics-days-ago-range = מיט { $daysStart }-{ $daysEnd } טעג צוריק
statistics-running-total = גיייִקער סך-הכּל
statistics-cards-due =
    { $cards ->
        [one] { $cards } קאַרטל טערמיניק
       *[other] { $cards } טערמיניקע קאַרטלעך
    }
statistics-backlog-checkbox = אָנגעזאַמלטע
statistics-intervals-title = איבער׳חזר-צווישנצײַטן
statistics-intervals-subtitle = אָפּלייגן ביז חזר-קאַרטלעך ווערן ווידער אויסגעוויזן
statistics-intervals-day-range =
    { $cards ->
        [one] { $cards } קאַרטל מיט אַ { $daysStart }~{ $daysEnd }-טאָג צווישנצײַט
       *[other] { $cards } קאַרטלעך מיט אַ { $daysStart }~{ $daysEnd }-טאָג צווישנצײַט
    }
statistics-intervals-day-single =
    { $cards ->
        [one] { $cards } קאַרטל מיט אַ { $day }-טאָג צווישנצײַט
       *[other] { $cards } קאַרטלעך מיט אַ { $day }-טאָג צווישנצײַט
    }
statistics-stability-day-range =
    { $cards ->
        [one] { $cards } קאַרטל מיט אַ { $daysStart }~{ $daysEnd }-טאָג געזעצקייט
       *[other] { $cards } קאַרטלעך מיט אַ { $daysStart }~{ $daysEnd }-טאָג געזעצקייט
    }
statistics-stability-day-single =
    { $cards ->
        [one] { $cards } קאַרטל מיט אַ { $day }-טאָג געזעצקייט
       *[other] { $cards } קאַרטלעך מיט אַ { $day }-טאָג געזעצקייט
    }
# hour range, eg "From 14:00-15:00"
statistics-hours-range = פון { $hourStart }:00~{ $hourEnd }:00
statistics-hours-correct = { $correct }/{ $total } ריכטיק ({ $percent }%)
statistics-hours-correct-info = ← (נישט 'ווידער')
# the emoji depicts the graph displaying this number
statistics-hours-reviews = 📊 { $reviews } איבער׳חזר׳ונגען
# the emoji depicts the graph displaying this number
statistics-hours-correct-reviews = 📈 { $percent }% ריכטיק ({ $reviews })
statistics-hours-title = צע׳פּרט׳לונג לויט שעהען
statistics-hours-subtitle = הצלחה לויט שעה פֿונעם טאָג.
# shown when graph is empty
statistics-no-data = קיין דאַטן
statistics-calendar-title = קאַלענדאַר

## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.

statistics-elapsed-time-seconds = { $amount }ס
statistics-elapsed-time-minutes = { $amount }מ
statistics-elapsed-time-hours = { $amount }ש
statistics-elapsed-time-days = { $amount }ט
statistics-elapsed-time-months = { $amount }ח
statistics-elapsed-time-years = { $amount }י

##

statistics-average-for-days-studied = דורכשניט פון שטודיר-טעג
# This term is used in a variety of contexts to refers to the total amount of
# items (e.g., cards, mature cards, etc) for a given period, rather than the
# total of all existing items.
statistics-total = סך-הכּל
statistics-days-studied = טעג אײַנגע׳חזר׳ט
statistics-average-answer-time-label = ענטפֿער-משך אין דורכשניט
statistics-average = דורכשניט
statistics-median-interval = מיטלפּונקט צווישנצײַט
statistics-due-tomorrow = טערמיניקע מאָרגן
# This string, ‘Daily load,’ appears in the ‘Future due’ table and represents a
# forecasted estimate of the number of cards expected to be reviewed daily in 
# the future. Unlike the other strings in the table that display actual data 
# derived from the current scheduling (e.g., ‘Average’, ‘Due tomorrow’),
# ‘Daily load’ is a projection based on the given data.
statistics-daily-load = טעגלעכע משׂא
# eg 5 of 15 (33.3%)
statistics-amount-of-total-with-percentage = { $amount } פון { $total } ({ $percent }%)
statistics-average-over-period = דורכשניט לויט זמן
statistics-reviews-per-day =
    { $count ->
        [one] { $count } איבער׳חזר׳ונג / טאָג
       *[other] { $count } איבער׳חזר׳ונגען / טאָג
    }
statistics-minutes-per-day =
    { $count ->
        [one] { $count }מיניט/טאָג
       *[other] { $count }מיניט/טאָג
    }
statistics-cards-per-day =
    { $count ->
        [one] { $count } קאַרטל/טאָג
       *[other] { $count } קאַרטלעך/טאָג
    }
statistics-median-ease = מיטלפּונקט גרינגקייט
statistics-median-difficulty = מיטלפּונקט שוועריקייט
statistics-average-retrievability = דורכשניטלעכע דערמאָנענקייט
statistics-estimated-total-knowledge = סך-הכּל וויסן לויט אָפּשאַץ
statistics-save-pdf = אויפֿהיטן PDF
statistics-saved = אויפֿגעהיט.
statistics-stats = סטאַט.
statistics-title = סטאַטיסטיק

## These strings are no longer used - you do not need to translate them if they
## are not already translated.

statistics-average-stability = דורכשניטלעכע געזעצקייט
statistics-average-interval = דורכשניטלעכער צווישנצײַט
statistics-average-ease = דורכשניטלעכע גרינגקייט
statistics-average-difficulty = דורכשניטלעכע שוועריקייט
