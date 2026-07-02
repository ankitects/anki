database-check-corrupt = מאגר פגום. אנא ראה את המדריך.
database-check-rebuilt = מסד הנתונים התייעל ונבנה מחדש.
database-check-card-properties =
    { $count ->
        [one] תוקן { $count } כרטיס עם מאפיינים שגויים.
        [two] תוקנו { $count } כרטיסים עם מאפיינים שגויים.
        [many] תוקנו { $count } כרטיסים עם מאפיינים שגויים.
       *[other] תוקנו { $count } כרטיסים עם מאפיינים שגויים.
    }
database-check-card-last-review-time-empty =
    { $count ->
        [one] נוסף זמן לימוד אחרון לכרטיס אחד.
        [two] נוסף זמן לימוד אחרון ל- { $count } כרטיסים.
       *[other] נוסף זמן לימוד אחרון ל- { $count } כרטיסים.
    }
database-check-missing-templates =
    { $count ->
        [one] נמחק { $count } כרטיס ללא תבנית.
        [two] נמחקו { $count } כרטיסים ללא תבנית.
        [many] נמחקו { $count } כרטיסים ללא תבנית.
       *[other] נמחקו { $count } כרטיסים ללא תבנית.
    }
database-check-field-count =
    { $count ->
        [one] תוקנה { $count } רשומה עם מנין שדות שגוי.
       *[other] תוקנו { $count } רשומות עם מנין שדות שגוי.
    }
database-check-new-card-high-due =
    { $count ->
        [one] נמצא { $count } כרטיס חדש עם תאריך יעד גדול מ1,000,000 - שקול למקם אותו מחדש בחלון העיון.
       *[other] נמצאו { $count } כרטיסים חדשים עם תאריך יעד גדול מ1,000,000 - שקול למקם אותם מחדש בחלון העיון.
    }
database-check-card-missing-note =
    { $count ->
        [one] נמחקה { $count } כרטיס ללא רשומות.
        [two] נמחקו { $count } כרטיסים ללא רשומות.
        [many] נמחקו { $count } כרטיסים ללא רשומות.
       *[other] נמחקו { $count } כרטיסים ללא רשומות.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] נמחק כרטיס { $count } עם תבנית כפולה.
       *[other] נמחקו { $count } כרטיסים עם תבנית כפולה.
    }
database-check-missing-decks =
    { $count ->
        [one] תוקנה { $count } חפיסה חסרה.
       *[other] תוקנו { $count } חפיסות חסרות.
    }
database-check-revlog-properties =
    { $count ->
        [one] תוקן { $count } רישום חזרה עם מאפיינים שגויים.
       *[other] תוקנו { $count } רישומי חזרה עם מאפיינים שגויים.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] תוקנה רשומה { $count } עם תווי utf8 שאינם חוקיים.
       *[other] תוקנו { $count } רשומות עם תווי utf8 שאינם חוקיים.
    }
database-check-fixed-invalid-ids =
    { $count ->
        [one] נפתר אובייקט { $count } עם חותמות זמן בעתיד.
       *[other] נפתרו { $count } אובייקטים עם חותמות זמן בעתיד.
    }
# "db-check" is always in English
database-check-notetypes-recovered = אחד או יותר מסוגי הרשומות היו חסרים. הרשומות שהשתמשו בהם קיבלו סוג רשומה חדשה מתחיל במילים "db-check", אך שמות השדות ועיצוב הכרטיס נאבדו. מומלץ לשחזר מהגיבוי האוטומטי.

## Progress info

database-check-checking-integrity = בודק אוסף...
database-check-rebuilding = בונה מחדש...
database-check-checking-cards = בודק כרטיסים...
database-check-checking-notes = בודק רשומות...
database-check-checking-history = בודק היסטוריה...
database-check-title = בדוק מסד נתונים
