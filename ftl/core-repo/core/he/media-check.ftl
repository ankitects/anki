## Shown at the top of the media check screen

media-check-window-title = בדוק מדיה
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    תיקיית אשפה: { $count ->
        [one] 1 קובץ, { $megs }MB
       *[other] { $count } קבצים, { $megs }MB
    }
media-check-missing-count = קבצים חסרים: { $count }
media-check-unused-count = קבצים שאינם בשימוש: { $count }
media-check-renamed-count = קבצים ששמם שונה: { $count }
media-check-oversize-count = יותר מ- 100MB: { $count }
media-check-subfolder-count = תיקיות משנה: { $count }
media-check-extracted-count = תמונות שחולצו: { $count }

## Shown at the top of each section

media-check-renamed-header = השתנה השם לכמה קבצים לצורך תאימות:
media-check-oversize-header = קבצים הגדולים מ- 100MB אינם יכולים להיות מסונכרנים עם AnkiWeb.
media-check-subfolder-header = תיקיות בתוך תיקיית המדיה אינן נתמכות.
media-check-missing-header = הקבצים הבאים בשימוש ע"י כרטיסים, אך חסרים בתיקיית המדיה:
media-check-unused-header = הקבצים הבאים נמצאו בתוך תיקיית המדיה, אך אינם בשימוש על ידי שום כרטיס:
media-check-template-references-field-header =
    אנקי לא יכול לזהות קבצים בשימוש כאשר אתה משתמש בהפניות של { "{{Field}}" } בתגיות media/LaTeX. במקום זאת יש למקם את תגי המדיה/LaTeX על הערות בודדות.
    
    תבניות הפניה:

## Shown once for each file

media-check-renamed-file = השתנה השם: { $old } -> { $new }
media-check-oversize-file = גדול מ- 100MB: { $filename }
media-check-subfolder-file = תיקייה: { $filename }
media-check-missing-file = חסר: { $filename }
media-check-unused-file = לא בשימוש: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }:{ $card_type }({ $side })

## Progress

media-check-checked = נבדקו { $count }...

## Deleting unused media

media-check-delete-unused-confirm = מחק מדיה שאינה בשימוש?
media-check-files-remaining =
    { $count ->
        [one] 1 קובץ
       *[other] { $count } קבצים
    } נותרו.
media-check-delete-unused-complete =
    { $count ->
        [one] 1 קובץ
       *[other] { $count } קבצים
    } הועברו לזבל.
media-check-trash-emptied = תיקיית הזבל ריקה כעת.
media-check-trash-restored = הקבצים שנמחקו שוחזרו לתיקיית המדיה.

## Rendering LaTeX

media-check-all-latex-rendered = כל ה - LaTeX עברו רינדור.

## Buttons

media-check-delete-unused = מחק קבצים שאינם בשימוש
media-check-render-latex = רנדר את ה-LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = רוקן זבל
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = שחזר מחוקים
media-check-check-media-action = בדוק מדיה
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = מדיה חסרה
# add a tag to notes with missing media
media-check-add-tag = תג חסר
