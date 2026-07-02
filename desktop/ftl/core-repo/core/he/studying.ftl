studying-again = שוב
studying-all-buried-cards = כל הכרטיסים ה"מוטמנים"
studying-audio-5s = אודיו -5 שניות
studying-audio-and5s = אודיו +5 שניות
studying-buried-siblings = כרטיסים תואמים ש"הוטמנו"
studying-bury = הטמנה
studying-bury-card = הטמן כרטיס
studying-bury-note = הטמן רשומה
studying-card-suspended = כרטיס הושהה.
studying-card-was-a-leech = הכרטיס היה "עלוקה".
studying-cards-buried =
    { $count ->
        [one] כרטיס אחד הוטמן.
       *[other] { $count } כרטיסים הוטמנו.
    }
studying-cards-will-be-automatically-returned-to = כרטיסים יוחזרו אוטומטית לחפיסות המקוריות שלהם לאחר שתסקור אותם.
studying-continue = המשך
studying-counts-differ = הספירה שונה מרשימת החפיסות, מכיוון שהטמנה מופעלת. חלק מהכרטיסים לא נכללו, ואחרים אולי תפסו את מקומם.
studying-delete-note = מחק רשומה
studying-deleting-this-deck-from-the-deck = מחיקת חפיסה זו מרשימת החפיסות תחזיר את כל הכרטיסים הנותרים לחפיסות המקוריות שלהן.
studying-easy = קל
studying-edit = עריכה
studying-empty = רוקן
studying-finish = סיים
studying-flag-card = סמן כרטיס בדגל
studying-good = טוב
studying-hard = קשה
studying-it-has-been-suspended = זה הושהה.
studying-manually-buried-cards = כרטיסים ש"הוטמנו" ידנית
studying-mark-note = סמן רשומה
studying-more = עוד
studying-no-cards-are-due-yet = עדיין אין כרטיסים מתוזמנים.
studying-note-suspended = רשומה הושהתה.
studying-pause-audio = הפסק אודיו
studying-please-run-toolsempty-cards = הפעל כלים>כרטיסים ריקים
studying-record-own-voice = הקלט את עצמך
studying-replay-own-voice = השמע את עצמך
studying-show-answer = הצג תשובה
studying-space = רווח
studying-study-now = למד כעת
studying-suspend = השהה
studying-suspend-note = השהה רשומה
studying-this-is-a-special-deck-for = זוהי חפיסה מיוחדת ללימוד מחוץ ללימוד הרגיל.
studying-to-review = לחזרה
studying-type-answer-unknown-field = סוג תשובה: שדה בלתי ידוע { $val }
studying-unbury = הוצא מהטמנה
studying-what-would-you-like-to-unbury = האם תרצה להוציא מהטמנה?
studying-you-havent-recorded-your-voice-yet = לא הקלטת את קולך עדיין.
studying-card-studied-in-minute =
    { $cards ->
        [one]
            { $minutes ->
                [one] כרטיס אחד נלמד בדקה.
                [two] כרטיס אחד נלמד בשתי דקות.
               *[other] כרטיס אחד נלמד ב{ $minutes } דקות.
            }
        [two]
            { $minutes ->
                [one] שני כרטיסים נלמדו בדקה.
                [two] שני כרטיסים נלמדו בשתי דקות.
               *[other] שני כרטיסים נלמדו ב{ $minutes } דקות.
            }
       *[other]
            { $minutes ->
                [one] { $cards } כרטיסים נלמדו בדקה.
                [two] { $cards } כרטיסים נלמדו בשתי דקות.
               *[other] { $cards } כרטיסים נלמדו ב{ $minutes } דקות.
            }
    }
studying-question-time-elapsed = זמן השאלה חלף
studying-answer-time-elapsed = זמן התשובה חלף

## OBSOLETE; you do not need to translate this

studying-card-studied-in =
    { $count ->
        [one] { $count } כרטיס נלמד ב
       *[other] { $count } כרטיסים נלמדו ב
    }
studying-minute =
    { $count ->
        [one] { $count } דקה.
       *[other] { $count } דקות.
    }
