### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    בשימוש ל- { $decks ->
        [one] 1 חפיסה
       *[other] { $decks } חפיסות
    }
deck-config-default-name = ברירת-מחדל
deck-config-title = אפשרויות חפיסה

## Daily limits section

deck-config-daily-limits = מגבלת לימוד יומית
deck-config-new-limit-tooltip =
    הגדר את המספר המרבי של כרטיסים חדשים שניתן להציג ביום (במידה והוספת כרטיסים).
    מכיוון שכרטיסים חדשים יגדילו את עומס החזרות לטווח קצר, בדרך כלל מומלץ
    שהכמות תהיה קטנה פי 10 ממגבלת החזרות הכללית שלך.
deck-config-review-limit-tooltip =
    המספר המרבי של כרטיסים ללימוד וחזרה ביום,
    (אם הכרטיסים אמורים להופיע בלימוד וחזרה).
deck-config-limit-deck-v3 =
    כאשר לומדים חפיסה שיש בתוכה תתי -חפיסות, המגבלות על החפיסה הראשית 
    יגבילו את הלימוד הכולל של החפיסה הראשית יחד עם תתי החפיסות.
    אולם במידה ותבחר ללמוד תת חפיסה (ולא דרך החפיסה הראשית) המגבלה תהיה
    כאילו היא החפיסה היחידה שנלמדת.
    ביכולתך להגדיר בנפרד לכל תת -חפיסה מהו מספר הכרטיסים המרבי שנלמד מאותה חפיסה .
    מגבלות החפיסה שיוגדרו ישלטו על סך הכרטיסים שבחזרה.
deck-config-limit-new-bound-by-reviews =
    מגבלת החזרות משפיעה על מגבלת הכרטיסים החדשים. לדוגמה, אם מגבלת החזרות שלך
    מוגדרת ל -200, ויש כרגע 190 כרטיסים לחזרה, לא יוצגו יותר מ10 כרטיסים חדשים.
    אם הכרטיסים לחזרה עוברים את מגבלת החזרות שלך, לא יוצגו כרטיסים חדשים.
deck-config-limit-interday-bound-by-reviews =
    מגבלת החזרות משפיעה גם על כרטיסי למידה של יותר מיום אחד. בעת החלת ההגבלה, כרטיסי
    למידה ראשונה מימים אחרים נלמדים תחילה, אחר כך חזרות, ולבסוף כרטיסים חדשים.
deck-config-tab-description =
    - `כל קבוצת ההגדרות`: המגבלה משותפת עם כל החפיסות המשתמשות בקבוצת הגדרות זו.
    - `חפיסה זו`: המגבלה היא ספציפית לחפיסה זו.
    - `רק היום`: בצע שינוי זמני למגבלה של החפיסה הזו.
deck-config-new-cards-ignore-review-limit = התעלם ממגבלת החזרות עבור כרטיסים חדשים
deck-config-new-cards-ignore-review-limit-tooltip =
    כברירת מחדל, מגבלת החזרות חלה גם על כרטיסים חדשים, וכרטיסים חדשים לא יהיו
    מוצגים מעבר למגבלת החזרות. אם אפשרות זו מופעלת, כרטיסים חדשים יוצגו ללא קשר
    למגבלת החזרות.
deck-config-apply-all-parent-limits = חישוב מגבלות מחפיסת האב העליונה
deck-config-apply-all-parent-limits-tooltip =
    כברירת מחדל, המגבלות מתחילות מהחפיסה שבחרת. אם אפשרות זו מופעלת, המגבלות יתחילו
    מהחפיסה ברמה העליונה במקום זאת, מה שיכול להיות שימושי אם אתה רוצה ללמוד חפיסות 
    משנה, מבלי לחרוג מההגבלה הכוללת על כרטיסים ביום של החפיסת-אב העליונה ביותר.
deck-config-affects-entire-collection = משפיע על כל המאגר.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = כל קבוצת ההגדרות
deck-config-deck-only = חפיסה זו
deck-config-today-only = להיום בלבד

## New Cards section

deck-config-learning-steps = שלבי למידה
# Please don't translate `1m`, `2d`
-deck-config-delay-hint =
    מרווחים הם בדרך כלל דקות (למשל '1m') או ימים (למשל '2d'), אך ניתן להגדיר גם שעות (למשל '1h') ושניות (למשל '30s') 
    (כדי להגדיר דקות הוסף ליד המספר את האות m לימים d לשעות h ולשניות s).
deck-config-learning-steps-tooltip =
    הגדרת שלבי הלמידה הראשונים של כרטיס חדש.
    מוגדר על ידי מרווח אחד או יותר, מופרדים ברווחים. המרווח הראשון ישמש בלחיצה על הלחצן 'שוב' בכרטיס חדש, והוא כברירת מחדל בדקה 1.
    כפתור 'טוב' יעבור לשלב הבא, שהוא 10 דקות כברירת מחדל .
    לאחר שכל השלבים עברו, הכרטיס יהפוך לכרטיס חזרות,
    ויופיע ביום אחר.
    { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip =
    מרווח הימים להצגה חוזרת של כרטיס, לאחר לחיצה על לחצן 'טוב'
    בשלב הלמידה האחרון של כרטיס חדש.
deck-config-easy-interval-tooltip =
    מרווח הימים להצגה חוזרת של כרטיס, לאחר בחירה בלחצן 'קל'
    פעיל גם לפני שלב הלמידה האחרון (ומדלג על יתר שלבי החזרה).
deck-config-new-insertion-order = סדר הלימוד של כרטיסים חדשים
deck-config-new-insertion-order-tooltip =
    מגדיר את סדר הלימוד של כרטיסים חדשים.
    מגדיר מיקום לכרטיסים חדשים בעת הוספת כרטיסים חדשים נוספים.
    כרטיסים עם מספר חזרות נמוך יותר יוצגו תחילה בעת הלימוד. שינוי
    שינוי הגדרה זו יעדכן אוטומטית את המיקום הקיים של כרטיסים חדשים.
deck-config-new-insertion-order-sequential = לפי סדר הוספה (הכרטיסים הישנים ראשונים)
deck-config-new-insertion-order-random = אקראי
deck-config-new-insertion-order-random-with-v3 = עם מתזמן V3, עדיף להשאיר את ההגדרה הזו לרצף, ולהתאים את סדר איסוף הכרטיסים החדש במקום זאת.

## Lapses section

deck-config-relearning-steps = שלבי למידה מחדש
deck-config-relearning-steps-tooltip =
    הגדרת שלבי הלמידה מחדש לכרטיסים שבמהלך החזרה דורגו בכפתור "שוב".
    מוגדר על ידי אפס ללימוד מחדש באותו יום,  או הגדרת ימים, מופרדים ברווחים.
    כברירת מחדל, לחיצה על כפתור 'שוב' בחזרה על כרטיס תציג אותו שוב כעבור 
    10 דקות. אם אין מרווחים מוגדרים, ישתנה מרווח הזמן של הכרטיס מבלי להיכנס
    ללמידה מחדש.
    { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip =
    כרטיס עלוקה הוא כרטיס שהרבה פעמים לא ידעת את התשובה עליו ולחצת על `שוב`.
    כאשר כרטיס מוגדר ככרטיס עלוקה מומלץ לערוך אותו, לכתוב אותו מחדש, לחלק אותו לכמה כרטיסים קצרים יותר או למצוא דרך אחרת שתעזור לזכור אותו.
    הגדרה זו משפיעה על מספר הפעמים שלחיצה על לחצן `שוב` תגדיר אוטומטית כרטיס כ `כרטיס עלוקה`.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    מגדיר מה יקרה בעת שרשומה מוגדרת ככרטיס עלוקה.
    `סמן בלבד`- מוסיף לרשומה תג "עלוקה" ומציג חלון קופץ בעת החזרה עליה.
    `השהה כרטיס`- בנוסף לתיוג, מוציא את הכרטיס מסדר החזרות עד לביטול ההשהיה באופן ידני.

## Burying section

deck-config-bury-title = הטמנה
deck-config-bury-new-siblings = הטמן אחים חדשים
deck-config-bury-review-siblings = הטמן אחים במצב חזרה
deck-config-bury-interday-learning-siblings = הטמן אחים במצב לימוד בין יומיים
deck-config-bury-new-tooltip =
    האם כרטיסים  אחרים מאותה רשומה במצב `חדש`(למשל כרטיסים מתהפכים, השלם את החסר)
    ידחו ליום הבא.
deck-config-bury-review-tooltip = האם כרטיסים אחרים במצב `חזרה` מאותה רשומה ידחו ליום הבא.
deck-config-bury-interday-learning-tooltip =
    האם כרטיסים אחרים במצב `למידה` מאותה רשומה עם מרווחים של יותר מיום אחד
    ידחו ליום הבא.
deck-config-bury-priority-tooltip =
    כאשר הכרטיסים מסודרים לחזרה, הראשונים בסדר העדיפויות הם כרטיסי 
    למידה מהיום, ואז כרטיסי למידה מימים אחרים, אחר כך חזרות, ולבסוף 
    כרטיסים חדשים. זה משפיע על איך עובדת ההטמנה:
    
    - אם כל אפשרויות ההטמנה מופעלות, האח שמגיע הכי מוקדם מרשימה זו יוצג.
    לדוגמה, תהיה עדיפות בסדר ההטמנה לכרטיס חזרה על פני כרטיס אח חדש (שיוטמן).
    - אחים בהמשך הרשימה לא יכולים להטמין סוגי כרטיסים מוקדמים יותר. לדוגמה, 
    אם הושבתה הטמנת הכרטיסים החדשים וכעת נלמד כרטיס חדש, זה לא יטמין שום
    כרטיסי למידה או חזרה, וייתכן שתראה גם אח חזרה וגם אח חדש באותה חזרה.

## Gather order and sort order of cards

deck-config-ordering-title = סדר חזרה
deck-config-new-gather-priority = סדר איסוף כרטיסים חדשים
deck-config-new-gather-priority-tooltip-2 =
    `חפיסה`: לחזרה נאספים כרטיסים מכל חפיסה לפי הסדר, החל מלמעלה. כרטיסים מכל חפיסה נאספים בסדר עולה. אם הכמות עולה על ההגבלה היומית של החפיסה שנבחרה, ייתכן שהאיסוף ייפסק לפני שכל החפיסות נבדקו. הסדר הזה הוא המהיר ביותר באוספים גדולים, ומאפשר לך לתעדף תת-חפיסות הקרובות יותר לראש.
    
    `מיקום עולה`: אוסף כרטיסים לפי מיקום עולה (due #), שהוא בדרך כלל הוותיק ביותר שנוסף יהיה ראשון.
    
    `מיקום יורד`: אוסף כרטיסים לפי מיקום יורד (due #), שהוא בדרך כלל האחרון שהתווסף יהיה ראשון.
    
    `רשומות אקראיות`: אוסף כרטיסים של רשומות שנבחרו באקראי. כאשר 'הטמנת אחים' מושבתת, זה מאפשר לראות את כל הכרטיסים  של הרשומה בהפעלה (למשל, גם כרטיס קדמי->אחורי וגם אחורי->כרטיס קדמי)
    
    `כרטיסים אקראיים`: אוסף כרטיסים באופן אקראי לחלוטין.
deck-config-new-card-sort-order = סדר חזרה של כרטיסים חדשים
deck-config-new-card-sort-order-tooltip-2 =
    `תבנית כרטיס`: מציג כרטיסים בסדר תבנית כרטיס. אם 'הטמנת אחים' מושבתת, זה יבטיח שכל הכרטיסים הקדמיים->אחוריים ייראו לפני כל הכרטיסים  האחוריים->קדמיים.
    
    `סדר נאסף`: מציג כרטיסים בדיוק כפי שנאספו. אם 'הטמנת אחים' מושבתת, זה בדרך כלל יביא לכך שכל הכרטיסים  של הרשומה ייראו בזה אחר זה.
    
    `תבנית כרטיס, ואחר כך אקראי`: כמו 'תבנית כרטיס', אבל מערבב את הכרטיסים של כל תבנית. בשילוב עם סדר איסוף במיקום עולה, ניתן להשתמש בזה כדי להציג את הכרטיסים הוותיקים ביותר בסדר אקראי למשל.
    
    `רשומה אקראית, ואז תבנית כרטיס`: בוחר רשומות באקראי, ואז מציג את כל האחים שלהן לפי הסדר.
    
    `אקראי`: מערבב במלואו את הכרטיסים שנאספו.
deck-config-new-review-priority = סדר חדש/חזרה
deck-config-new-review-priority-tooltip = מתי להציג כרטיסים חדשים ביחס לכרטיסי חזרה.
deck-config-interday-step-priority = סדר למידה בין-יומית/חזרות
deck-config-interday-step-priority-tooltip =
    מתי להציג (מחדש) כרטיסי למידה שנותרו מאתמול.
    
    במידה וכמות הכרטיסים לחזרה וללמידה גדולים מהמגבלה היומית, המגבלה תחול על הכרטיסים לחזרה ולא על אלה שללמידה. הגדרה זו משפיעה על הסדר שבו יוצגו הכרטיסים לאחר שנקבע אילו כרטיסים יופיעו היום במסגרת המגבלה היומית, אבל לא תשפיע על אילו כרטיסים לא יופיעו כלל.
deck-config-review-sort-order = סדר מיון חזרות
deck-config-review-sort-order-tooltip =
    סדר ברירת המחדל נותן עדיפות לכרטיסים שחיכו הכי הרבה זמן, כך
    שאם יש לך כמות כרטיסים גדולה לחזרה, הכרטיסים הממתינים במשך
    הזמן הרב ביותר יופיעו בתחילה. אם יש לך כמות כרטיסים גדולה שיקח
    לך מספר ימים עד שתסיים אותה או שאתה מעוניין לראות
    כרטיסים לפי הסדר של תתי החפיסות, ייתכן שתמצא את פקודות המיון
    החליפיות עדיפות.
deck-config-display-order-will-use-current-deck =
    אנקי ישתמש בהגדרת סדר החזרות לפי החפיסה שאתה 
    בוחר ללמוד, ולא לפי ההגדרות של תתי -החפיסה שלה.

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = חפיסה
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = חפיסה ואח"כ רשומות אקראיות
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = סדר עולה
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = סדר יורד
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = רשומות אקראיות
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = כרטיסים אקראיים
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = תבנית כרטיס, ולאחר מכן סדר אקראי
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = רשומה אקראית. ואז, תבנית כרטיס
# Sort the cards randomly.
deck-config-sort-order-random = אקראי
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = תבנית כרטיס ולאחר מכן בסדר האסיפה
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = סדר האסיפה
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = ערבב עם חזרות
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = הצג לאחר חזרות
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = הצג לפני חזרות
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = תאריך יעד, ואז אקראי
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = תאריך יעד, ואז חפיסה
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = חפיסה, ואז תאריך יעד
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = מרווחים עולים
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = מרווחים יורדים
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = קלות עולה
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = קלות יורדת
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = קושי בסדר עולה
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = קושי בסדר יורד
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = יכולת אחזור עולה
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = יכולת אחזור יורדת

## Timer section

deck-config-timer-title = שעון עצר
deck-config-maximum-answer-secs = מקסימום שניות לתשובה
deck-config-maximum-answer-secs-tooltip =
    מספר השניות המרבי לחזרה בודדת. אם משך תשובה
    חורג מהזמן הזה (כי התרחקת מהמסך למשל), 
    הזמן בטיימר יעצר לפי המגבלה שהגדרת.
deck-config-show-answer-timer-tooltip = הצג במסך החזרה טיימר המציג את משך הזמן שעבר מתחילת החזרה על שאלה זו.
deck-config-stop-timer-on-answer = עצור טיימר בתשובה
deck-config-stop-timer-on-answer-tooltip =
    קובע האם לעצור את הטיימר בהצגת התשובה.
    זה לא משפיע על הסטטיסטיקות.

## Auto Advance section

deck-config-seconds-to-show-question = שניות המתנה בהצגת השאלה
deck-config-seconds-to-show-question-tooltip-3 = כשמופעלת התקדמות אוטומטית, זה מספר השניות להמתנה בטרם החלת פעולת השאלה. 0 להשבתה.
deck-config-seconds-to-show-answer = שניות המתנה בהצגת התשובה
deck-config-seconds-to-show-answer-tooltip-2 = מספר השניות שיש להמתין לפני החלת פעולת התשובה כאשר התקדמות אוטומטית מופעלת. הגדר ל0 כדי להשבית.
deck-config-question-action-show-answer = הצגת תשובה
deck-config-question-action-show-reminder = הצגת תזכורת
deck-config-question-action = פעולת שאלה
deck-config-question-action-tool-tip = הפעולה לביצוע לאחר הצגת השאלה והזמן למענה חלף.
deck-config-answer-action = פעולת תשובה
deck-config-answer-action-tooltip-2 = הפעולה לביצוע לאחר הצגת התשובה והזמן שחלף.
deck-config-wait-for-audio-tooltip-2 = המתן לסיום השמע בטרם החלת פעולת השאלה או פעולת התשובה.

## Audio section

deck-config-audio-title = שמע
deck-config-disable-autoplay = אל תפעיל שמע אוטומטית
deck-config-disable-autoplay-tooltip =
    כאשר מופעל, אנקי לא ינגן קבצי שמע באופן אוטומטי.
    ניתן להפעיל אותו באופן ידני על ידי לחיצה/הקשה על סמל שמע, או על ידי שימוש בפעולת השמע שוב.
deck-config-skip-question-when-replaying = דלג על השאלה בניגון חוזר של התשובה
deck-config-always-include-question-audio-tooltip =
    הגדרה זו קובעת האם יש לכלול את שמע השאלה כאשר אפשרות ההשמעה מחדש
    מופעלת בעת הסתכלות בצד התשובה של כרטיס.

## Advanced section

deck-config-advanced-title = מתקדם
deck-config-maximum-interval-tooltip =
    מרווח הזמן המרבי בין חזרה לחזרה על כרטיס.
    כשכרטיס מגיע למרווח הזמן הזה מהחזרה הקודמת, אין אפשרות למרווח גדול יותר מהמרווח הזה עד לחזרה הבאה בלי קשר ללחצן- בין אם דורג "טוב" או "קל" או קשה".
    ככל שמרווח הזמן הזה יהיה קצר יותר עומס החזרות יהיה גדול יותר.
deck-config-starting-ease-tooltip =
    הגדרת הקלות בכרטיסים חדשים. כברירת מחדל, הלחצן 'טוב' בכרטיס
    חדש שנלמד יגדיל את המרווח עד לחזרה הבאה פי 2.5 מהמרווח הקודם.
deck-config-easy-bonus-tooltip =
    מכפיל נוסף המיושם על מרווח של הכרטיס כאשר אתה מדרג
    אותו 'קל'.
deck-config-interval-modifier-tooltip =
    מכפיל זה מיושם על כלל החזרות וניתן להשתמש בהתאמות קלות
    להפוך את אנקי לחלש או חזק יותר בלוח הזמנים. בבקשה תראה
    את המדריך לפני שינוי אפשרות זו.
deck-config-hard-interval-tooltip = המכפיל יחול על מרווח החזרות בעת דירוג 'קשה'.
deck-config-new-interval-tooltip = המכפיל יחול על מרווח החזרות בעת דירוג 'שוב'.
deck-config-minimum-interval-tooltip = מרווח הזמן המינימלי המוגדר לכרטיס לאחר שדורג "שוב".
deck-config-custom-scheduling = תזמון בהתאמה אישית
deck-config-custom-scheduling-tooltip = משפיע על כל האוסף. השתמש באחריותך בלבד!

## Easy Days section.

deck-config-easy-days-title = ימים קלים
deck-config-easy-days-monday = יום שני
deck-config-easy-days-tuesday = יום שלישי
deck-config-easy-days-wednesday = יום רביעי
deck-config-easy-days-thursday = יום חמישי
deck-config-easy-days-friday = יום שישי
deck-config-easy-days-saturday = יום שבת
deck-config-easy-days-sunday = יום ראשון
deck-config-easy-days-normal = רגיל
deck-config-easy-days-reduced = מופחת
deck-config-easy-days-minimum = מינימום
deck-config-easy-days-no-normal-days = לפחות יום אחד צריך להיות מוגדר ל '{ deck-config-easy-days-normal }'.
deck-config-easy-days-change = חזרות קיימות לא יתוזמנו מחדש אלא אם '{ deck-config-reschedule-cards-on-change }' מופעל באפשרויות FSRS.

## Adding/renaming

deck-config-add-group = הוסף קבוצת הגדרות
deck-config-name-prompt = שם
deck-config-rename-group = שנה קבוצת הגדרות
deck-config-clone-group = שכפל קבוצת הגדרות

## Removing

deck-config-remove-group = הסר קבוצת הגדרות
deck-config-will-require-full-sync =
    השינוי המבוקש ידרוש סנכרון חד כיווני. אם ביצעת שינויים
    במכשיר אחר, וטרם סנכרנת אותם למכשיר זה, אנא עשה זאת לפני
    שאתה ממשיך.
deck-config-confirm-remove-name = למחוק { $name }?

## Other Buttons

deck-config-save-button = שמור
deck-config-save-to-all-subdecks = שמור לכל תתי החפיסות
deck-config-save-and-optimize = בצע אופטימיזציה של כל ההגדרות המוגדרות מראש
deck-config-revert-button-tooltip = שחזר הגדרה זו לברירת המחדל שלה.

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = תפעול של אנקי2.1.41+
deck-config-description-new-handling-hint =
    מתייחס לקלט כאל סימון, ומנקה קלט HTML. כשהוא מופעל ,
    התיאור יוצג גם במסך "סיימת חפיסה" .
    הסימון יופיע כטקסט ב- Anki 2.1.40 ומטה.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    לחפיסה המכילה חפיסה זאת יש מגבלה של { $cards ->
        [one] { $cards } כרטיס
       *[other] { $cards } כרטיסים
    }, לעבור את הגבול הזה.
deck-config-reviews-too-low =
    אם תוסיף { $cards ->
        [one] { $cards } כרטיס חדש בכל יום
       *[other] { $cards } כרטיסים חדשים בכל יום
    } מגבלת החזרות שלך צריכה להיות לפחות { $expected }
deck-config-learning-step-above-graduating-interval = מרווח הסיום צריך להיות ארוך לפחות כמו שלב הלמידה הסופי שלך.
deck-config-good-above-easy = המרווח הקל צריך להיות ארוך לפחות כמו מרווח הסיום.
deck-config-relearning-steps-above-minimum-interval = מרווח הזמן המזערי המינימלי צריך להיות לפחות כמו שלב הלימוד מחדש הסופי שלך.
deck-config-maximum-answer-secs-above-recommended = אנקי יכול לתזמן את החזרות שלך בצורה יעילה יותר כאשר אתה מקצר כל שאלה.
deck-config-too-short-maximum-interval = מרווח זמן מקסימלי של פחות מ- 6 חודשים אינו מומלץ.
deck-config-ignore-before-info = { $included }/{ $totalCards } כרטיסים (בערך) ישמשו לאופטימיזציה של פרמטרי FSRS.

## Selecting a deck

deck-config-which-deck = איזו חפיסה הינך רוצה?

## Messages related to the FSRS scheduler

deck-config-updating-cards = מעדכן כרטיסים: { $current_cards_count }/{ $total_cards_count }...
deck-config-invalid-parameters = פרמטרי ה-FSRS שסופקו אינם חוקיים. השאר אותם ריקים כדי להשתמש בפרמטרי ברירת המחדל.
deck-config-not-enough-history = אין מספיק היסטוריית חזרות לביצוע פעולה זו.
deck-config-must-have-400-reviews =
    { $count ->
        [one] נמצאה רק חזרה { $count }. עליך לבצע לפחות 400 חזרות עבור פעולה זו.
        [two] נמצאו רק { $count } חזרות. עליך לבצע לפחות 400 חזרות עבור פעולה זו.
       *[other] נמצאו רק { $count } חזרות. עליך לבצע לפחות 400 חזרות עבור פעולה זו.
    }
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = משקלי מודל
deck-config-compute-optimal-weights = בצע אופטימיזציה של משקלי FSRS
deck-config-optimize-button = בצע אופטימיזציה
# Indicates that a given function or label, provided via the "text" variable, operates slowly.
deck-config-slow-suffix = { $text } (איטי)
deck-config-compute-button = חשב
deck-config-ignore-before = התעלם מחזרות לפני
deck-config-time-to-optimize = עבר זמן מה - מומלץ להשתמש בכפתור אופטימיזציה של הכל.
deck-config-evaluate-button = הערכה
deck-config-desired-retention = שימור רצוי
deck-config-historical-retention = שימור היסטורי
deck-config-smaller-is-better = מספרים קטנים יותר מצביעים על התאמה טובה יותר להיסטוריית החזרות שלך.
deck-config-steps-too-large-for-fsrs = כאשר FSRS מופעל, שלבי למידה במשך יום 1 אינם מומלצים.
deck-config-get-params = קבל פרמטרים
deck-config-complete = { $num } % הושלם.
deck-config-iterations = ביצוע: { $count }...
deck-config-reschedule-cards-on-change = תזמון מחדש של כרטיסים בשינוי
deck-config-fsrs-tooltip =
    מתזמן החזרות החופשיות (FSRS) הוא חלופה למתזמן SuperMemo 2 (SM2) הוותיק של Anki.
    על ידי קביעה מדויקת יותר מתי החומר צפוי להישכח, זה יכול לעזור לך לזכור
    יותר חומר באותו פרק זמן. הגדרה זו משותפת לכל החפיסות שתחת קבוצת ההגדרות של חפיסה זו.
deck-config-desired-retention-tooltip =
    ערך ברירת המחדל של 0.9 יתזמן כרטיסים כך שיש לך סיכוי של 90% לזכור אותם כאשר
    הם עולים שוב לחזרה. אם תגדיל את הערך הזה, אנקי תציג כרטיסים בתדירות גבוהה יותר
    כדי להגדיל את הסיכוי שתזכור אותם. אם תוריד את הערך, אנקי תציג כרטיסים
    בתדירות נמוכה יותר, ואתה תשכח יותר מהם. היה זהיר בעת התאמה זו - ערכים גבוהים
    יותר יגדילו מאוד את עומס החזרות שלך, וערכים נמוכים יותר עלולים לגרום לשכוח
    הרבה חומר.
deck-config-desired-retention-tooltip2 = רמות העומס המפורטות בתיבת המידע הן בהערכה גסה. לרמת דיוק גבוהה יותר, השתמש בסימולטור.
deck-config-historical-retention-tooltip =
    כאשר חלק מהיסטוריית החזרות שלך חסר, FSRS צריך להשלים את החסר. כברירת מחדל, החישוב יוצא
    מנקודת הנחה שכשעשית את החזרות הישנות האלה, זכרת 90% מהחומר. אם השמירה הישנה שלך
    היתה גבוהה או נמוכה במידה ניכרת מ-90%, התאמת אפשרות זו תאפשר ל-FSRS להעריך טוב יותר
    את החזרות החסרות.
    ייתכן שהיסטוריית החזרות שלך לא שלמה משתי סיבות:
    1. כי השתמשת באפשרות 'התעלם מחזרות לפני'.
    2. כי מחקת בעבר יומני חזרות כדי לפנות מקום, או ייבאת חומר מתוכנית SRS אחרת.
    האפשרות האחרונה די נדירה, ולכן אם לא השתמשת באפשרות הראשונה, כנראה שלא תצטרך להתאים
    הגדרה זו.
deck-config-weights-tooltip2 = משתני FSRS משפיעים על איך כרטיסים מתוזמנים. Anki יופעל עם משתני ברירת המחדל, אפשר להשתמש באפשרות שלהלן כדי למטב את המשתנים לביצועים שלך בחפיסות שמשתמשות בערכה הזאת.
deck-config-reschedule-cards-on-change-tooltip =
    משפיע על כל האוסף, ואינו נשמר.
    
    אפשרות זו קובעת האם תאריכי היעד של כרטיסים ישתנו בעת הפעלת FSRS, או בעת אופטימיזציה של הפרמטרים. ברירת המחדל היא לא לתזמן מחדש כרטיסים: חזרות עתידיות ישתמשו בתזמון החדש, אך לא יהיה שינוי מיידי בעומס החזרות שלך. אם תזמון מחדש יופעל, תאריכי היעד של כרטיסים ישתנו.
deck-config-reschedule-cards-warning =
    בהתאם לשמירת זכרון הרצויה שלך, זה יכול לגרום למספר רב של כרטיסים להפוך 
    לממתינים לחזרה, ולכן זה לא מומלץ בעת המעבר הראשון מ-SM2.
deck-config-ignore-before-tooltip-2 =
    אם מוגדר, אופטימיזציה של פרמטרי FSRS לא תתחשב בכרטיסים שנלמדו לפני התאריך שסופק.
    זה יכול להיות שימושי אם ייבאת כרטיסים עם נתוני תזמון של מישהו אחר, או שינית את אופן השימוש בלחצני התשובה.
deck-config-compute-optimal-weights-tooltip2 =
    כאשר תלחץ על כפתור האופטימיזציה, FSRS ינתח את היסטוריית החזרות שלך ויפיק פרמטרים אופטימליים לזיכרון שלך ולתוכן שאתה לומד. אם החפיסות שלך משתנות מאוד בקושי הסובייקטיבי, מומלץ להקצות להם הגדרות מוגדרות מראש נפרדות, מכיוון שהפרמטרים לחפיסות קלות וחפיסות קשות יהיו שונים. 
    אתה לא צריך לבצע אופטימיזציה של הפרמטרים שלך לעתים קרובות - אחת לכמה חודשים מספיקה.
    כברירת מחדל, הפרמטרים יחושבו מהיסטוריית החזרה של כל החפיסות תוך שימוש בהגדרה הנוכחית. אתה יכול להתאים את החיפוש לפני חישוב הפרמטרים, אם ברצונך לשנות את ההגדרה אילו כרטיסים משמשים לאופטימיזציה של הפרמטרים.
deck-config-please-save-your-changes-first = אנא שמור את השינויים שלך תחילה.
deck-config-workload-factor-change =
    עומס משוער: { $factor }x
    (בהשוואה ל-{ $previousDR }% שימור רצוי)
deck-config-workload-factor-unchanged = ככל שערך זה גבוה יותר, כך יוצגו כרטיסים בתדירות גבוהה יותר.
deck-config-desired-retention-too-low = השמירה הרצויה נמוכה מאוד, מה שיכול להוביל למרווחי זמן ארוכים מאוד.
deck-config-desired-retention-too-high = השמירה הרצויה גבוהה מאוד, מה שיכול להוביל למרווחי זמן קצרים מאוד.
deck-config-percent-of-reviews =
    { $reviews ->
        [one] { $pct }% מתוך { $reviews } חזרה
       *[other] { $pct }% מתוך { $reviews } חזרות
    }
deck-config-percent-input = { $pct }%
# This message appears during FSRS parameter optimization.
deck-config-checking-for-improvement = בודק אם יש שיפור...
deck-config-optimizing-preset = שפר הגדרות מראש { $current_count }/{ $total_count }...
deck-config-fsrs-must-be-enabled = תחילה יש להפעיל את FSRS.
deck-config-fsrs-params-optimal = נראה כי פרמטרי FSRS כרגע הם אופטימליים.
deck-config-fsrs-params-no-reviews = לא נמצאו חזרות. אנא בדוק שהגדרה מראש זו מוקצית לכל החפיסות שברצונך לבצע אופטימיזציה (כולל חפיסות משנה) ונסה שוב.
deck-config-wait-for-audio = המתן לשמע
deck-config-show-reminder = הצג תזכורת
deck-config-answer-again = ענה שוב
deck-config-answer-hard = תשובה קשה
deck-config-answer-good = תשובה טובה
deck-config-days-to-simulate = ימים לסימולציה
deck-config-desired-retention-below-optimal = השמירה הרצויה שלך נמוכה מהאופטימלית. מומלץ להגדיל אותה.
# Description of the y axis in the FSRS simulation
# diagram (Deck options -> FSRS) showing the total number of
# cards that can be recalled or retrieved on a specific date.
deck-config-fsrs-simulator-experimental = סימולטור FSRS (ניסיוני)
deck-config-fsrs-simulate-desired-retention-experimental = סימולטור שימור רצוי של FSRS (ניסיוני)
deck-config-fsrs-simulate-save-preset = לאחר האופטימיזציה, אנא שמור את קבוצת ההגדרות של החפיסה שלך לפני הפעלת הסימולטור.
deck-config-fsrs-desired-retention-help-me-decide-experimental = עזור לי להחליט (ניסיוני)
deck-config-additional-new-cards-to-simulate = כרטיסים חדשים נוספים לסימולטור
deck-config-simulate = צור סימולציה
deck-config-clear-last-simulate = נקה סימולציה אחרונה
deck-config-fsrs-simulator-radio-count = חזרות
deck-config-advanced-settings = הגדרות מתקדמות
deck-config-smooth-graph = גרף חלק
deck-config-suspend-leeches = השהה כרטיסי עלוקה
deck-config-save-options-to-preset = שמור שינויים בקבוצת הגדרות
deck-config-save-options-to-preset-confirm = להחליף את האפשרויות בהגדרה הנוכחית שלך עם האפשרויות המוגדרות כעת בסימולטור?
# Radio button in the FSRS simulation diagram (Deck options -> FSRS) selecting
# to show the total number of cards that can be recalled or retrieved on a
# specific date.
deck-config-fsrs-simulator-radio-memorized = ניתן לשינון
deck-config-fsrs-simulator-radio-ratio = זמן / יחס שינון
# $time here is pre-formatted e.g. "10 Seconds" 
deck-config-fsrs-simulator-ratio-tooltip = { $time } לכל כרטיס ששונן

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.

# Checkbox
deck-config-health-check = בדוק תקינות בעת אופטימיזציה
# Message box showing the result of the health check
deck-config-fsrs-bad-fit-warning =
    קשה ל-FSRS לחזות את הזיכרון שלך. המלצות:
    
    - להשעות או לנסח מחדש כרטיסי עלוקה.
    - השתמשו בכפתורי התשובה באופן עקבי. זכרו ש"קשה" הוא ציון עובר, לא ציון נכשל.
    - להבין לפני שאתה משנן.
    
    מעקב אחר ההצעות האלה, ישפר בדרך כלל את הביצועים במהלך החודשים הקרובים.
# Message box showing the result of the health check
deck-config-fsrs-good-fit = FSRS מותאם היטב לזיכרון שלך.

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-unable-to-determine-desired-retention = לא ניתן לקבוע שימור אופטימלי.
deck-config-predicted-minimum-recommended-retention = שמירה מינימלית מומלצת: { $num }
deck-config-compute-minimum-recommended-retention = שמירה מינימלית מומלצת
deck-config-compute-optimal-retention-tooltip4 = כלי זה ינסה למצוא את ערך השמירה הרצוי שיוביל ללמידה היעילה ביותר, במינימום זמן. המספר המחושב יכול לשמש כשאתה מחליט למה להגדיר את השמירה הרצויה שלך. ייתכן שתרצה לבחור שמירה רצויה גבוהה יותר, שזה אומר להפסיד יותר זמן לימוד עבור שיעור חזרות גבוה יותר. הגדרת השמירה הרצויה נמוכה מהמינימום לא מומלצת, מכיוון שזה יוביל לעומס עבודה גבוה יותר, בגלל שיעור השכחה הגבוה.
deck-config-plotted-on-x-axis = (מוצג על ציר ה-X)
deck-config-a-100-day-interval =
    { $days ->
        [one] מרווח של 100 ימים יהפוך ל{ $days } יום.
       *[other] מרווח של 100 ימים יהפוך ל{ $days } ימים.
    }
deck-config-fsrs-simulator-y-axis-title-time = שעת/יום חזרה
deck-config-fsrs-simulator-y-axis-title-count = כמות/יום חזרה
deck-config-fsrs-simulator-y-axis-title-memorized = סך הכל שינון
deck-config-bury-siblings = הטמן אחים
deck-config-do-not-bury = אל תטמין אחים
deck-config-bury-if-new = הטמן אם במצב חדש
deck-config-bury-if-new-or-review = הטמן אם במצב חדש או במצב חזרה
deck-config-bury-if-new-review-or-interday = הטמן אם במצב חדש, חזרה, או במצב לימוד שהתפרס על פני יותר מיום אחד .
deck-config-bury-tooltip =
    אחים הם כרטיסים אחרים מאותה רשומה  (למשל כרטיסים מתהפכים, או
    השלם את החסר אחרים מאותו טקסט).
    
    כאשר אפשרות זו כבויה, ייתכן שיראו כרטיסים מרובים מאותה רשומה באותו
    יום. כאשר מופעל, אנקי יטמין אוטומטית את האחים, ותסתיר אותם עד היום
    הבא. אפשרות זו מאפשרת לך לבחור אילו סוגי רשומות עשויים להיות מוטמנים
    כאשר אתה עונה על אחד האחים שלהם.
    
    בעת שימוש במתזמן V3, ניתן גם לקבור כרטיסי למידה בין-יומיים. כרטיסי למידה
    בין יומיים הם כרטיסים עם שלב למידה נוכחי של יום אחד או יותר.
deck-config-seconds-to-show-question-tooltip = מספר השניות להמתנה לפני חשיפת התשובה כאשר התקדמות אוטומטית מופעלת. 0 להשבתה.
deck-config-answer-action-tooltip = הפעולה שיש לבצע בכרטיס הנוכחי לפני התקדמות אוטומטית לכרטיס הבא.
deck-config-wait-for-audio-tooltip = המתן עד שהשמע יסתיים לפני שתחשוף את התשובה או השאלה הבאה באופן אוטומטי
deck-config-ignore-before-tooltip =
    אם מוגדר, יתעלם מחזרות לפני התאריך שסופק בעת אופטימיזציה והערכת פרמטרי FSRS.
    זה יכול להיות שימושי אם ייבאת נתוני תזמון של מישהו אחר, או שינית את אופן השימוש בלחצני התשובה.
deck-config-compute-optimal-retention-tooltip =
    הכלי הזה מניח שאתה מתחיל עם 0 כרטיסים, והוא ינסה לחשב את כמות החומר שאתה
    מסוגל לשמור בזיכרון במסגרת הזמן הנתונה. השמירה המשוערת תהיה תלויה מאוד ביכולות שלך,
    ואם זה שונה משמעותית מ-0.9, זה סימן שהזמן שהקצבת בכל יום נמוך מדי
    או גבוה מדי עבור כמות הכרטיסים שאתה מנסה ללמוד. מספר זה יכול להיות שימושי כהפניה, אבל
    לא מומלץ להעתיק אותו לשדה השמירה הרצוי.
deck-config-health-check-tooltip1 = פעולה זו תציג אזהרה אם FSRS מתקשה להסתגל לזיכרון שלך.
deck-config-health-check-tooltip2 = בדיקת תקינות מתבצעת רק בעת שימוש באפשרות אופטימיזציה של הגדרה נוכחית.
deck-config-compute-optimal-retention = חישוב יכולת זכירה אופטימלית
deck-config-predicted-optimal-retention = תחלופה מיטבית חזויה: { $num }
deck-config-weights-tooltip =
    משקלי הדגם משפיעים על אופן תזמון הכרטיסים. לאחר שצברת 1000+ חזרות, תוכל לבצע אופטימיזציה
    של המשקלים למטה.
deck-config-compute-optimal-weights-tooltip =
    לאחר שעשית 1000+ חזרות ב-Anki, תוכל להשתמש בלחצן האופטימיזציה כדי לנתח את היסטוריית החזרות שלך,
    ולהפיק אוטומטית משקלים האופטימליים לזיכרון שלך ולתוכן שאתה לומד.
    אם יש לך חפיסות שדרגות הקושי בהן משתנות מאוד, מומלץ להקצות להן קבוצות הגדרות נפרדות, ואז
    המשקלים לחפיסות קלות ולחפיסות קשות יהיו שונים. אין צורך לייעל את המשקלים שלך
    לעתים קרובות - פעם אחת בכמה חודשים מספיקה.
    
    כברירת מחדל, משקלים יחושבו מהיסטוריית החזרה של כל החפיסות באמצעות הקביעה הנוכחית. באפשרותך
    להתאים את החיפוש לפני חישוב המשקלים, אם תרצה לשנות אילו כרטיסים משמשים
    לאופטימיזציה של המשקולות.
deck-config-compute-optimal-retention-tooltip2 =
    הכלי הזה מניח שאתה מתחיל עם 0 כרטיסים נלמדים, ותנסה למצוא את ערך השמירה 
    הרצויה שיוביל לחומר הנלמד ביותר, במינימום זמן. ניתן להשתמש במספר זה בתור א
    התייחסות בעת החלטה על מה להגדיר את השמירה הרצויה. ייתכן שתרצה לבחור שמירה רצויה גבוהה יותר,
    אם אתה מוכן להקדיש יותר זמן לימוד עבור שיעור זכירה גבוה יותר. הגדרת השמירה הרצויה שלך כנמוכה מהאופטימלית
    אינה מומלצת, מכיוון שהיא תוביל ליותר עבודה ללא תועלת.
deck-config-compute-optimal-retention-tooltip3 =
    הכלי הזה מניח שאתה מתחיל עם 0 כרטיסים שנלמדו, והוא ינסה למצוא את ערך השמירה הרצוי שיוביל ללמידה הטובה ביותר  במינימום זמן. 
    כדי לדמות במדויק את תהליך הלמידה שלך, תכונה זו דורשת מינימום של 400+ חזרות.
    המספר המחושב יכול לשמש כהמלצה לאיזה ערך להגדיר את השמירה הרצויה שלך.
    ייתכן שתרצה לבחור בשימור רצוי גבוה יותר, אם אתה מוכן להשקיע יותר זמן בחזרה במקום בלימוד חדש.
    הגדרת ערך שמירה הרצויה נמוך מהמינימום אינה מומלצת, ועלולה להוביל לעומס חזרות גבוה יותר, בגלל שיעור השכחה הגבוה.
deck-config-seconds-to-show-question-tooltip-2 = מספר השניות שיש להמתין לפני גילוי התשובה כאשר התקדמות אוטומטית מופעלת. הגדר ל0 כדי להשבית.
deck-config-invalid-weights = משקלים חייבים להיות ריקים כדי להשתמש בברירות המחדל, או חייבים להיות 17 מספרים מופרדים בפסיקים.
deck-config-fsrs-on-all-clients =
    אנא ודא שכל גרסאות Anki בכל המכשירים שלך הם Anki(Mobile) 23.10+ או AnkiDroid 2.17+. 
    FSRS לא יעבוד כראוי אם באחד מהמכשירים שלך אתה משתמש בAnki מתחת לגרסאות אלו.
deck-config-optimize-all-tip = אתה יכול לבצע אופטימיזציה של כל ההעדפות המוגדרות מראש בבת אחת על ידי שימוש בכפתור שבחלק העליון.
