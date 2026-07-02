### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = נוסף: ↓{ $up }↑ { $down }
sync-media-removed-count = נמחק: ↓{ $up }↑ { $down }
sync-media-checked-count = נבדקו: { $count }
sync-media-starting = מתחיל סינכרון מדיה...
sync-media-complete = סינכרון מדיה הסתיים.
sync-media-failed = סינכרון מדיה נכשל.
sync-media-aborting = מפסיק סינכרון מדיה...
sync-media-aborted = סינכרון מדיה הופסק.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = סינכרון מדיה מבוטל.
# Title of the screen that shows syncing progress history
sync-media-log-title = יומן רישום סינכרון מדיה

## Error messages / dialogs

sync-conflict = רק עותק אחד של Anki יכול לסנכרן לחשבונך באותו הזמן. המתן מספר דקות ונסה שנית.
sync-server-error = AnkiWeb נקלע לבעיה. נסה שנית בעוד מספר דקות.
sync-client-too-old = גירסת התוכנה שלך ישנה מדי. עדכן לגירסה האחרונה כדי להמשיך לסנכרן.
sync-wrong-pass = ID או סיסמה של AnkiWeb שגויים; אנא נסה/י שוב.
sync-resync-required = נא סנכרן שנית. אם הודעה זו מופיעה שוב, פנה לאתר התמיכה.
sync-must-wait-for-end = Anki מסנכרן עכשיו. המתן לסיום הסינכרון, ואז נסה שנית.
sync-confirm-empty-download = באוסף המקומי אין כרטיסים. להוריד מ-AnkiWeb?
sync-confirm-empty-upload = לאוסף AnkiWeb אין כרטיסים. להחליף אותו באוסף מקומי?
sync-conflict-explanation =
    החפיסות כאן ובאתר AnkiWeb שונות באופן כזה שאינן יכולות להתמזג יחד, הכרחי לדרוס את החפיסות בצד אחד עם החפיסות שבצד האחר.
    
    אם תבחר להוריד, Anki יוריד את האוסף מAnkiWeb, וכל השינויים במחשבך מאז הסינכרון האחרון יאבדו.
    
    אם תבחר להעלות, Anki יעלה את האוסף שלך לAnkiWeb, וכל השינויים שעשית באתר או בשאר המכשירים מאז הסינכרון האחרון יאבדו.
    
    לאחר שכל המכשירים מסונכרנים, חזרות עתידיות וכרטיסים שנוספו יתמזגו אוטומטית.
sync-conflict-explanation2 =
    יש התנגשות בין חפיסות במכשיר הזה לבין AnkiWeb. עליך לבחור איזו גרסה לשמור:
    - בחר **{ sync-download-from-ankiweb }** כדי להחליף את החפיסות שבמכשיר זה בגרסה של AnkiWeb. תאבד את כל השינויים שביצעת במכשיר הזה מאז הסנכרון האחרון שלך.
    - בחר **{ sync-upload-to-ankiweb }** כדי להחליף את הגרסאות של AnkiWeb עם חפיסות מהמכשיר הזה, ולמחוק כל שינוי ב-AnkiWeb.
    לאחר פתרון ההתנגשות, הסנכרון יפעל כרגיל.
sync-ankiweb-id-label = מזהה AnkiWeb:
sync-password-label = ססמה:
sync-account-required =
    <h1>נדרש חשבון</h1>
    נדרש חשבון חינמי כדי לשמור על האוסף שלך מסונכרן. אנא <a href="{ $link }">הירשם</a> לקבלת חשבון, ולאחר מכן הכנס את פרטייך למטה.
sync-sanity-check-failed = נא הפעל את הפקודה 'בדוק מסד נתונים', וסנכרן שוב. אם הבעיה נמשכת, הפעל דרך מסך ההעדפות - סינכרון מלא.
sync-clock-off = לא ניתן לסנכרן - השעון שלך לא מכוון.
sync-upload-too-large =
    קובץ האוסף שלך גדול מכדי לשלוח אותו ל- AnkiWeb. אתה יכול להפחית את זה
    גודל על ידי הסרת חפיסות לא רצויות (אופציונלי לייצא אותם קודם),
    ולאחר מכן השתמש בבדיקת מסד הנתונים כדי לצמצם את גודל הקובץ.  ({ $details })
sync-sign-in = היכנס
sync-ankihub-dialog-heading = כניסה ל-AnkiHub
sync-ankihub-username-label = שם משתמש או דואר אלקטרוני:
sync-ankihub-login-failed = לא ניתן להיכנס ל-AnkiHub עם האישורים שסופקו.
sync-ankihub-addon-installation = התקנת תוסף AnkiHub

## Buttons

sync-media-log-button = יומן רישום מדיה
sync-abort-button = עצור
sync-download-from-ankiweb = הורד מ-AnkiWeb
sync-upload-to-ankiweb = העלאה ל-AnkiWeb
sync-cancel-button = ביטול

## Normal sync progress

sync-downloading-from-ankiweb = מוריד מ-AnkiWeb...
sync-uploading-to-ankiweb = מעלה ל-AnkiWeb...
sync-syncing = מסנכרן ...
sync-checking = בודק...
sync-connecting = מתחבר...
sync-added-updated-count = נוסף/השתנה: ↓{ $up }↑ { $down }
sync-log-in-button = התחבר
sync-log-out-button = צא מהחשבון
sync-collection-complete = סנכרון המאגר הושלם.
