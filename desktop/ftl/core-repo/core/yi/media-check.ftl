## Shown at the top of the media check screen

media-check-window-title = באַטראַכטן מעדיע
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    { $count ->
        [one] מיסט פּאַפּקע: { $count } טעקע, { $megs } מעגאַבײַט
       *[other] מיסט פּאַפּקע: { $count } טעקעס, { $megs } מעגאַבײַט
    }
media-check-missing-count = פֿעלנדיקע טעקעס: { $count }
media-check-unused-count = נישט-געניצטע טעקעס: { $count }
media-check-renamed-count = איבערגעביטן נאָמען פֿון טעקעס: { $count }
media-check-oversize-count = איבער 100 מעגאַבײַט: { $count }
media-check-subfolder-count = אונטערפּאַפּקעס: { $count }
media-check-extracted-count = אַרויסגעצויגנע בילדער: { $count }

## Shown at the top of each section

media-check-renamed-header = טייל טעקעס האָט מען זיי געטוישט דעם נאָמען, זיי זאָלן זײַן גוט צוגעפּאַסט:
media-check-oversize-header = טעקעס וואָס איבער 100 מעגאַבײַט קען נישט סינכראָניזירט ווערן מיט „אַנקי-וועב״.
media-check-subfolder-header = פּאַפּקעס וואָס אינעווייניק די מעדיע-פּאַפּקע ווערן נישט דערקענט.
media-check-missing-header = אויף די ווײַטערדיקע טעקעס האָט מע געטײַטלט בײַ קאַטלעך, כאָטש זיי זענען נישט געפֿונען געוואָרן אין דער מעדיע-פּאַפּקע:
media-check-unused-header = די ווײַטערדיקע טעקעס געפֿונען זיך אין דער מעדיע-פּאַפּקע, כאָטש זיי ווערן אַפּנים נישט געניצט פון קיין קאַרטל:
media-check-template-references-field-header =
    ANKI דערקענט נישט געניצטע טעקעס מיט { "{{Field}} אָפּשיקן אין מעדיע אָדער LaTeX צעטלען. אַנשטאָט זאָל מען שטעלן די צעטלען " }אויף אייציקע נאָטיצן. 
    
    מוסטער-פֿאַררופֿן:

## Shown once for each file

media-check-renamed-file = איבערגערופֿענע: { $old }->{ $new }
media-check-oversize-file = איבער 100 מעגאַבײַט: { $filename }
media-check-subfolder-file = פּאַפּקע: { $filename }
media-check-missing-file = פֿעלנדיק: { $filename }
media-check-unused-file = נישט געניצט: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = איבערגעקוקט { $count }…

## Deleting unused media

media-check-delete-unused-confirm = אָפּמעקן נישט-געניצט מעדיע?
media-check-files-remaining =
    { $count ->
        [one] ס׳בלײַבט { $count } טעקע.
       *[other] ס׳בלײַבן { $count } טעקעס.
    }
media-check-delete-unused-complete =
    { $count ->
        [one] { $count } טעקע איז אַרײַן אין מיסטקאַסטן.
       *[other] { $count } טעקעס זענען אַרײַן אין מיסטקאַסטן.
    }
media-check-trash-emptied = דער מיסטקאַסטן איז יעצט ליידיק געוואָרן.
media-check-trash-restored = אָפּגעמעקטע טעקעס זענען צוריקגעשטעלט אין דער מעידע-פּאַפּקע.

## Rendering LaTeX

media-check-all-latex-rendered = אַלע LaTeX איז פֿאַר געמאַכט.

## Buttons

media-check-delete-unused = אָפּמעקן נישט-געניצטע
media-check-render-latex = מאַכן פֿאַר LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = אויסליידיקן מיסטקאַסטן
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = צוריקשטעלן אָפּגעמעקטע
media-check-check-media-action = באַטראַכטן מעדיע
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = missing-media
# add a tag to notes with missing media
media-check-add-tag = באַצעטלען פֿעלדניקע
