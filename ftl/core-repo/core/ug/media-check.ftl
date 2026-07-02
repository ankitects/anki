## Shown at the top of the media check screen

media-check-window-title = ۋاسىتە تەكشۈر
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    { $count ->
        [one] ئەخلەتخانا قىسقۇچ: { $count } ھۆججەت، { $megs }MB
       *[other] { "" }
    }
media-check-missing-count = يوقالغان ھۆججەت: { $count }
media-check-unused-count = ئىشلەتمىگەن ھۆججەت: { $count }
media-check-renamed-count = ئاتى ئۆزگەرگەن ھۆججەت: { $count }
media-check-oversize-count = 100MB دىن چوڭ: { $count }
media-check-subfolder-count = تارماق قىسقۇچ: { $count }
media-check-extracted-count = ئاجراتقان سۈرەت: { $count }

## Shown at the top of each section

media-check-renamed-header = بەزى ھۆججەتلەر ماسلىشىشچانلىق تۈپەيلىدىن ئاتى ئۆزگەرتىلدى.
media-check-oversize-header = سىغىمى 100MB دىن ئاشقان ھۆججەتنى AnkiWeb ئارقىلىق قەدەمداشلىغىلى بولمايدۇ.
media-check-subfolder-header = ۋاسىتە قىسقۇچ تارماق قىسقۇچنى قوللىمايدۇ.
media-check-missing-header = تۆۋەندىكى ھۆججەتنى ھېچقانداق كارتا نەقىل ئالمىغان ئەمما ۋاسىتە قىسقۇچتىن تېپىلمىدى:
media-check-unused-header = تۆۋەندىكى ھۆججەت ۋاسىتە قىسقۇچتا بايقالدى، ئەمما ھېچقانداق كارتىدا ئىشلىتىلمىگەن:
media-check-template-references-field-header = سىز media/LaTeX بەلگىسىگە { "{{Field}}" } قوشۇپ نەقىل ئالسىڭىز، Anki نەقىل ئالغان ھۆججەتنى تەكشۈرەلمەيدۇ، media/LaTeX بەلگىسىنى ھەر بىر خاتىرىگە قوشۇڭ.

## Shown once for each file

media-check-renamed-file = ئاتى ئۆزگەردى: { $old } -› { $new }
media-check-oversize-file = 100MB دىن چوڭ: { $filename }
media-check-subfolder-file = قىسقۇچ: { $filename }
media-check-missing-file = يوقالغان: { $filename }
media-check-unused-file = ئىشلىتىلمىگەن: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = { $count } تەكشۈردى…

## Deleting unused media

media-check-delete-unused-confirm = ئىشلىتىلمىگەن ۋاسىتەنى ئۆچۈرەمدۇ؟
media-check-files-remaining =
    { $count ->
        [one] { $count } ھۆججەت قالدى
       *[other] { $count } ھۆججەت قالدى
    }
media-check-delete-unused-complete =
    { $count ->
        [one] { $count } ھۆججەت ئەخلەتخانىغا يۆتكەلدى.
       *[other] { $count } ھۆججەت ئەخلەتخانىغا يۆتكەلدى.
    }
media-check-trash-emptied = ئەخلەتخانا قىسقۇچ ھازىر بوش.
media-check-trash-restored = ئۆچۈرۈلگەن ھۆججەت ۋاسىتە قىسقۇچقا ئەسلىگە كەلتۈرۈلدى.

## Rendering LaTeX

media-check-all-latex-rendered = بارلىق LaTeX رەڭلەندى.

## Buttons

media-check-delete-unused = ئىشلىتىلمىگەننى ئۆچۈر
media-check-render-latex = LaTeX ھاسىللا
# button to permanently delete media files from the trash folder
media-check-empty-trash = ئەخلەتخانىنى بوشات
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = ئۆچۈرۈلگەننى ئەسلىگە قايتۇر
media-check-check-media-action = ۋاسىتە تەكشۈر
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = يوقالغان ۋاسىتە
# add a tag to notes with missing media
media-check-add-tag = يوقالغانغا بەلگە سال
