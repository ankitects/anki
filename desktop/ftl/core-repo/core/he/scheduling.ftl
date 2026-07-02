## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount } שניות
scheduling-answer-button-time-minutes = { $amount } דקות
scheduling-answer-button-time-hours = { $amount } שעות
scheduling-answer-button-time-days = { $amount }ימים
scheduling-answer-button-time-months = { $amount }חודשים
scheduling-answer-button-time-years = { $amount } שנים

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] { $amount } שניה
        [two] { $amount } שניות
        [many] { $amount } שניות
       *[other] { $amount } שניות
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] { $amount } דקה
        [two] { $amount } דקות
        [many] { $amount } דקות
       *[other] { $amount } דקות
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } שעה
        [two] { $amount } שעות
        [many] { $amount } שעות
       *[other] { $amount } שעות
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } יום
        [two] { $amount } ימים
        [many] { $amount } ימים
       *[other] { $amount } ימים
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } חודש
        [two] { $amount } חודשים
        [many] { $amount } חודשים
       *[other] { $amount } חודשים
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } שנה
        [two] { $amount } שנים
        [many] { $amount } שנים
       *[other] { $amount } שנים
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    כרטיס הלימוד הבא יהיה מובן בעוד { $unit ->
        [seconds]
            { $amount ->
                [one] { $amount } שניה
               *[other] { $amount } שניות
            }
        [minutes]
            { $amount ->
                [one] { $amount } דקה
               *[other] { $amount } דקות
            }
       *[hours]
            { $amount ->
                [one] { $amount } שעה
               *[other] { $amount } שעות
            }
    }.
scheduling-learn-remaining =
    { $remaining ->
        [one] נותר כרטיס לימוד אחד מתוזמן ליותר מאוחר היום.
       *[other] נותרו { $remaining } כרטיסי לימוד מתוזמנים ליותר מאוחר היום.
    }
scheduling-congratulations-finished = אשריך! סיימת את החפיסה לעת עתה.
scheduling-today-review-limit-reached =
    הגעת למכסת החזרות היומית, אך עדיין יש
    כרטיסים שממתינים לחזרה. לזכרון אופטימלי
    אנא שקול להגדיל את המכסה היומית בהגדרות.
scheduling-today-new-limit-reached =
    יש עוד כרטיסים חדשים, אך הגעת למכסה היומית.
    תוכל להגדיל את המכסה בהגדרות, אך שים לב כי
    ככל שתציג יותר כרטיסים חדשים, כך יגדל העומס 
    של החזרות שלך בטווח הקצר.
scheduling-buried-cards-found = כרטיס אחד או יותר "הוטמנו" ויוצגו מחר. תוכל { $unburyThem } אם תרצה לראות אותם כעת.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = להוציא אותם מ"הטמנה"
scheduling-how-to-custom-study = אם אתה רוצה ללמוד מחוץ לתוכנית הרגילה, תוכל להשתמש באפשרות { $customStudy }.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = לימוד מותאם אישית

## Scheduler upgrade

scheduling-update-soon = אנקי בגרסה 2.1 מגיע עם מתזמן חדש, שתיקן מספר בעיות שהיו קיימות בגרסאות הישנות. מומלץ לעדכן.
scheduling-update-done = המתזמן עודכן בהצלחה.
scheduling-update-button = עדכון
scheduling-update-later-button = מאוחר יותר
scheduling-update-more-info-button = למד עוד
scheduling-update-required =
    המאגר שלך זקוק לעדכון למתזמן V2.
    בחר { scheduling-update-more-info-button } בטרם תמשיך.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = כלול תמיד את צד השאלה כאשר אודיו מופעל שנית
scheduling-at-least-one-step-is-required = נדרש לפחות צעד אחד.
scheduling-automatically-play-audio = נגן אודיו אוטומטית
scheduling-bury-related-new-cards-until-the = הטמן כרטיסים חדשים קשורים עד יום למחרת
scheduling-bury-related-reviews-until-the-next = "הטמנת" חזרות קשורות עד יום למחרת
scheduling-days = ימים
scheduling-description = תיאור
scheduling-easy-bonus = בונוס עבור "קל"
scheduling-easy-interval = מרווח-זמן של "קל"
scheduling-end = (סוף)
scheduling-general = כללי
scheduling-graduating-interval = מרווח-זמן של "טוב"
scheduling-hard-interval = מרווח זמן קשה
scheduling-ignore-answer-times-longer-than = התעלם מתשובה שלוקחת יותר מ-
scheduling-interval-modifier = שינוי מרווח-זמן
scheduling-lapses = שגיאות
scheduling-lapses2 = שגיאות
scheduling-learning = לימוד
scheduling-leech-action = התנהגות כרטיס עלוקה
scheduling-leech-threshold = סף כרטיס עלוקה
scheduling-maximum-interval = מרווח מרבי
scheduling-maximum-reviewsday = מקסימום חזרות/ליום
scheduling-minimum-interval = מרווח מינימלי
scheduling-mix-new-cards-and-reviews = ערבוב כרטיסים חדשים וכרטיסים לחזרה
scheduling-new-cards = כרטיסים חדשים
scheduling-new-cardsday = כרטיסים חדשים/יום
scheduling-new-interval = מרווח זמן חדש
scheduling-new-options-group-name = שם קבוצת אפשרויות חדש:
scheduling-options-group = קבוצת אפשרויות:
scheduling-order = סדר
scheduling-parent-limit = (מגבלת על: { $val })
scheduling-reset-counts = אפס את ספירת החזרות וההפסקות
scheduling-restore-position = שחזר מיקום מקורי אם אפשרי
scheduling-review = חזרה
scheduling-reviews = חזרות
scheduling-seconds = שניות
scheduling-set-all-decks-below-to = להגדיר את כל החפיסות מתחת { $val } לקבוצת האפשרויות הזו?
scheduling-set-for-all-subdecks = החל עבור כל תתי-החפיסות
scheduling-show-answer-timer = הצג טיימר על המסך
scheduling-show-new-cards-after-reviews = הצג כרטיסים חדשים לאחר החזרות
scheduling-show-new-cards-before-reviews = הצג כרטיסים חדשים לפני כרטיסים לחזרה
scheduling-show-new-cards-in-order-added = הצג כרטיסים חדשים לפי סדר הוספתם
scheduling-show-new-cards-in-random-order = הצג כרטיסים חדשים בסדר אקראי
scheduling-starting-ease = קלות התחלתית
scheduling-steps-in-minutes = צעדים (דקות)
scheduling-steps-must-be-numbers = צעדים מוכרחים להיות מספרים.
scheduling-tag-only = סמן בלבד
scheduling-the-default-configuration-cant-be-removed = תצורת ברירת המחדל אינה ניתנת למחיקה.
scheduling-your-changes-will-affect-multiple-decks = השינויים שתבצע ישפיעו על מספר חפיסות. אם אתה מעוניין לשנות רק את החפיסה הנוכחית, אנא הוסף קבוצת אפשרויות חדשה קודם.
scheduling-deck-updated =
    { $count ->
        [one] { $count } חפיסה עודכנה.
       *[other] { $count } חפיסות עודכנו.
    }
scheduling-set-due-date-prompt =
    { $cards ->
        [one] בעוד כמה ימים להציג את הכרטיס?
       *[other] בעוד כמה ימים להציג את הכרטיסים?
    }
scheduling-set-due-date-prompt-hint =
    0=היום
    1!=מחר+איפוס המרווח בין חזרות
    3-7=בחירה אקראית בין 3 ל-7 ימים.
scheduling-set-due-date-done =
    { $cards ->
        [one] הוגדר תאריך יעד לכרטיס אחד.
       *[other] הוגדר תאריך יעד ל{ $cards } כרטיסים.
    }
scheduling-graded-cards-done =
    { $cards ->
        [one] כרטיס אחד דורג
        [two] { $cards } כרטיסים דורגו
       *[other] { $cards } כרטיסים דורגו
    }
scheduling-forgot-cards =
    { $cards ->
        [one] כרטיס { $cards } נשכח.
       *[other] שכח { $cards } כרטיסים.
    }
