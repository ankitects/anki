# The date a card will be ready to review
statistics-due-date = תאריך יעד
# The count of cards waiting to be reviewed
statistics-due-count = מתוזמנים
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = חדש #{ $number }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } כרטיסים/דקה
statistics-average-answer-time = { $average-seconds }שניות ({ statistics-cards-per-min })

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds =
    { $amount ->
        [one] תוך { $amount } שניה
        [two] תוך { $amount } שניות
        [many] תוך { $amount } שניות
       *[other] תוך { $amount } שניות
    }
statistics-in-time-span-minutes =
    { $amount ->
        [one] תוך { $amount } דקה
        [two] תוך { $amount } דקות
        [many] תוך { $amount } דקות
       *[other] תוך { $amount } דקות
    }
statistics-in-time-span-hours =
    { $amount ->
        [one] תוך { $amount } שעה
        [two] תוך { $amount } שעות
        [many] תוך { $amount } שעות
       *[other] תוך { $amount } שעות
    }
statistics-in-time-span-days =
    { $amount ->
        [one] תוך { $amount } יום
        [two] תוך { $amount } ימים
        [many] תוך { $amount } ימים
       *[other] תוך { $amount } ימים
    }
statistics-in-time-span-months =
    { $amount ->
        [one] תוך { $amount } חודש
        [two] תוך { $amount } חודשים
        [many] תוך { $amount } חודשים
       *[other] תוך { $amount } חודשים
    }
statistics-in-time-span-years =
    { $amount ->
        [one] תוך { $amount } שנה
        [two] תוך { $amount } שנים
        [many] תוך { $amount } שנים
       *[other] תוך { $amount } שנים
    }
# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { statistics-in-time-span-seconds } part should be pasted in from the English
# version unmodified.
statistics-studied-today =
    נלמדו { statistics-cards }
    { $unit ->
        [seconds] { statistics-in-time-span-seconds }
        [minutes] { statistics-in-time-span-minutes }
        [hours] { statistics-in-time-span-hours }
        [days] { statistics-in-time-span-days }
        [months] { statistics-in-time-span-months }
       *[years] { statistics-in-time-span-years }
    } היום

##

statistics-cards =
    { $cards ->
        [one] { $cards } כרטיס
        [two] { $cards } כרטיסים
        [many] { $cards } כרטיסים
       *[other] { $cards } כרטיסים
    }
statistics-notes =
    { $notes ->
        [one] רשומה אחת
        [two] שתי רשומות
       *[other] { $notes } רשומות
    }
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews =
    { $reviews ->
        [one] { $reviews } חזרה
        [two] { $reviews } חזרות
        [many] { $reviews } חזרות
       *[other] { $reviews } חזרות
    }
# This fragment of the tooltip in the FSRS simulation
# diagram (Deck options -> FSRS) shows the total number of
# cards that can be recalled or retrieved on a specific date.
statistics-memorized = { $memorized } ניתנים לשינון
statistics-today-title = היום
statistics-today-again-count = מנין השגיאות:
statistics-today-type-counts = לימוד:{ $learnCount }, חזרות: { $reviewCount }, לימוד מחדש: { $relearnCount }, מסוננים: { $filteredCount }
statistics-today-no-cards = לא נלמדו כרטיסים היום.
statistics-today-no-mature-cards = לא נלמדו כרטיסים בוגרים היום.
statistics-today-correct-mature = תשובות נכונות בכרטיסים בוגרים: { $correct }/{ $total } ({ $percent }%)
statistics-counts-total-cards = סך-הכל כרטיסים
statistics-counts-new-cards = חדשים
statistics-counts-young-cards = צעירים
statistics-counts-mature-cards = בוגרים
statistics-counts-suspended-cards = מושהים
statistics-counts-buried-cards = מוטמנים
statistics-counts-filtered-cards = מסונן
statistics-counts-learning-cards = נלמדים
statistics-counts-relearning-cards = נלמדים מחדש
statistics-counts-title = מניין הכרטיסים
statistics-counts-separate-suspended-buried-cards = הצג בנפרד כרטיסים מושהים ומוטמנים.

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

statistics-true-retention-title = שימור אמיתי
statistics-true-retention-subtitle = אחוזי הידע של כרטיסים עם מרווח של ≥ 1 יום.
statistics-true-retention-tooltip = אם אתם משתמשים ב-FSRS, צפויה רמת השמירה האמיתית שלכם להיות קרובה לרמת השמירה הרצויה. שימו לב שנתונים ליום בודד הם לא מדויקים, לכן עדיף להסתכל על נתונים חודשיים.
statistics-true-retention-range = טווח
statistics-true-retention-pass = עבר
statistics-true-retention-fail = נכשל
# This will usually be the same as statistics-counts-total-cards
statistics-true-retention-total = סך-הכל כרטיסים
statistics-true-retention-count = כמות
statistics-true-retention-retention = שימור
# This will usually be the same as statistics-counts-young-cards
statistics-true-retention-young = צעירים
# This will usually be the same as statistics-counts-mature-cards
statistics-true-retention-mature = בוגרים
statistics-true-retention-all = הכל
statistics-true-retention-today = היום
statistics-true-retention-yesterday = אתמול
statistics-true-retention-week = שבוע אחרון
statistics-true-retention-month = חודש אחרון
statistics-true-retention-year = שנה אחרונה
statistics-true-retention-all-time = כל הזמן
# If there are no reviews within a specific time period, the retention
# percentage cannot be calculated and is displayed as "N/A."
statistics-true-retention-not-applicable = אין

##

statistics-range-all-time = משך חיי חפיסה
statistics-range-1-year-history = בשנה האחרונה
statistics-range-all-history = מאז ומתמיד
statistics-range-deck = חפיסה
statistics-range-collection = אוסף
statistics-range-search = חיפוש
statistics-card-ease-title = קלות הכרטיס
statistics-card-difficulty-title = קושי כרטיס
statistics-card-stability-title = יציבות כרטיס
statistics-card-stability-subtitle = כרטיסים במרווח שבו סיכויי הזכירה של השאלה במבחן ירדו ל90%.
statistics-median-stability = יציבות חציונית
statistics-card-retrievability-title = אחזור כרטיס
statistics-card-ease-subtitle = ככל שהקלות נמוכה יותר, כך גוברת התדירות שהכרטיס יופיע.
statistics-card-difficulty-subtitle2 = ככל שרמת הקושי גבוהה יותר, כך היציבות תגדל לאט יותר.
statistics-retrievability-subtitle = כמה סביר שתזכור את התשובה במבחן.
# eg "3 cards with 150-170% ease"
statistics-card-ease-tooltip =
    { $cards ->
        [one] כרטיס 1 עם קלות { $percent }
       *[other] { $cards } כרטיסים עם קלות { $percent }
    }
statistics-card-difficulty-tooltip =
    { $cards ->
        [one] { $cards } כרטיס עם { $percent } קושי
       *[other] { $cards } כרטיסים עם { $percent } קושי
    }
statistics-retrievability-tooltip =
    { $cards ->
        [one] { $cards } כרטיס עם { $percent } קלות
       *[other] { $cards } כרטיסים עם { $percent } קלות
    }
statistics-future-due-title = תחזית
statistics-future-due-subtitle = מספר החזרות המתוכננות בעתיד.
statistics-added-title = נוספו
statistics-added-subtitle = מספר הכרטיסים החדשים שהוספת.
statistics-reviews-count-subtitle = מספר השאלות שענית עליהן.
statistics-reviews-time-subtitle = הזמן שלקח לך לענות על השאלות.
statistics-answer-buttons-title = כפתורי תשובה
# eg Button: 4
statistics-answer-buttons-button-number = לחצן
# eg Times pressed: 123
statistics-answer-buttons-button-pressed = מספר לחיצות
statistics-answer-buttons-subtitle = מספר הפעמים שלחצת על כל לחצן.
statistics-reviews-title = חזרות
statistics-reviews-time-checkbox = זמן
statistics-in-days-single =
    { $days ->
        [0] היום
        [1] מחר
        [one] בעוד { $days } יום
       *[other] בעוד { $days } ימים
    }
statistics-in-days-range = בעוד { $daysStart }-{ $daysEnd } ימים
statistics-days-ago-single =
    { $days ->
        [1] אתמול
        [one] לפני { $days } יום
       *[other] לפני { $days } ימים
    }
statistics-days-ago-range = לפני { $daysStart }-{ $daysEnd } ימים
statistics-running-total = סה"כ רץ
statistics-cards-due =
    { $cards ->
        [one] 1 כרטיס מתוזמן
       *[other] { $cards } כרטיסים מתוזמנים
    }
statistics-backlog-checkbox = מצטבר
statistics-intervals-title = מרווחי זמן
statistics-intervals-subtitle = הזמן שעובר עד שחזרות מופיעות שנית.
statistics-intervals-day-range =
    { $cards ->
        [one] 1 כרטיס עם מרווח זמן של { $daysStart }~{ $daysEnd } ימים
       *[other] { $cards } כרטיסים עם מרווח זמן של { $daysStart }~{ $daysEnd } ימים
    }
statistics-intervals-day-single =
    { $cards ->
        [one] 1 כרטיס עם { $day } יום מרווח זמן
       *[other] { $cards } כרטיסים עם { $day } יום מרווח זמן
    }
statistics-stability-day-range =
    { $cards ->
        [one] { $cards } כרטיס עם { $daysStart }~{ $daysEnd } ימי יציבות
       *[other] { $cards } כרטיסים עם { $daysStart }~{ $daysEnd } ימי יציבות
    }
statistics-stability-day-single =
    { $cards ->
        [one] { $cards } כרטיס עם { $day } ימי יציבות
       *[other] { $cards } כרטיס עם { $day } ימי יציבות
    }
# hour range, eg "From 14:00-15:00"
statistics-hours-range = מ- { $hourStart }:00~{ $hourEnd }:00
statistics-hours-correct = { $correct }/{ $total } נכונים ({ $percent }%)
statistics-hours-correct-info = → (לא 'שוב')
# the emoji depicts the graph displaying this number
statistics-hours-reviews = 📊 { $reviews } חזרות
# the emoji depicts the graph displaying this number
statistics-hours-correct-reviews = 📈 { $percent }% נכון ({ $reviews })
statistics-hours-title = פילוח שעתי
statistics-hours-subtitle = ניקוד הצלחה בחזרות לכל שעה ביום.
# shown when graph is empty
statistics-no-data = אין נתונים
statistics-calendar-title = לוח שנה

## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.

statistics-elapsed-time-seconds = { $amount } שניות
statistics-elapsed-time-minutes = { $amount } ד'
statistics-elapsed-time-hours = { $amount }שע'
statistics-elapsed-time-days = { $amount } ימים
statistics-elapsed-time-months = { $amount }ח'
statistics-elapsed-time-years = { $amount } שנ'

##

statistics-average-for-days-studied = ממוצע עבור ימים שנלמדו
# This term is used in a variety of contexts to refers to the total amount of
# items (e.g., cards, mature cards, etc) for a given period, rather than the
# total of all existing items.
statistics-total = סך-הכל
statistics-days-studied = ימי לימוד
statistics-average-answer-time-label = זמן תשובה ממוצע
statistics-average = ממוצע
statistics-median-interval = טווח חציוני
statistics-due-tomorrow = מתוזמן למחר
# This string, ‘Daily load,’ appears in the ‘Future due’ table and represents a
# forecasted estimate of the number of cards expected to be reviewed daily in 
# the future. Unlike the other strings in the table that display actual data 
# derived from the current scheduling (e.g., ‘Average’, ‘Due tomorrow’),
# ‘Daily load’ is a projection based on the given data.
statistics-daily-load = עומס יומי
# eg 5 of 15 (33.3%)
statistics-amount-of-total-with-percentage = { $amount } מתוך { $total } ({ $percent }%)
statistics-average-over-period = אם למדת כל יום
statistics-reviews-per-day =
    { $count ->
        [one] { $count } חזרה ליום
       *[other] { $count } חזרות ליום
    }
statistics-minutes-per-day =
    { $count ->
        [one] { $count } דקה ליום
       *[other] { $count } דקות ליום
    }
statistics-cards-per-day =
    { $count ->
        [one] { $count } כרטיס ליום
       *[other] { $count } כרטיסים ליום
    }
statistics-median-ease = קלות חציונית
statistics-median-difficulty = קושי חציוני
statistics-average-retrievability = יכולת אחזור ממוצעת
statistics-estimated-total-knowledge = ידע כולל משוער
statistics-save-pdf = שמור כ-PDF
statistics-saved = נשמר.
statistics-stats = סטטיסטיקה
statistics-title = סטטיסטיקה

## These strings are no longer used - you do not need to translate them if they
## are not already translated.

statistics-average-stability = יציבות ממוצעת
statistics-average-interval = מרווח ממוצע
statistics-average-ease = קלות ממוצעת
statistics-average-difficulty = קושי ממוצע
