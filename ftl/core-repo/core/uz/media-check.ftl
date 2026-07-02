## Shown at the top of the media check screen

media-check-window-title = Media fayllarni tekshirish
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    { $count ->
        [one] Chiqitdon; { $count } ta fayl, { $megs } MB
       *[other] Chiqitdon; { $count } ta fayl, { $megs } MB
    }
media-check-missing-count = Yoʻqolgan fayllar: { $count }
media-check-unused-count = Ishlatilmagan fayllar: { $count }
media-check-renamed-count = Nomi oʻzgartirilgan fayllar: { $count }
media-check-oversize-count = 100MBʼdan katta: { $count }
media-check-subfolder-count = Ostjildlar: { $count }
media-check-extracted-count = Ajratilgan rasmlar: { $count }

## Shown at the top of each section

media-check-renamed-header = Moslik uchun baʼzi fayllar nomi oʻzgartirildi:
media-check-oversize-header = 100MBʼdan katta fayllar AnkiWeb bilan sinxronlab boʻlmaydi.
media-check-subfolder-header = Media jildidagi jildlar qoʻllab-quvvatlanmaydi.
media-check-missing-header = Quyidagi fayllarga kartalar orqali murojaat qilingan, biroq media jildida topilmadi:
media-check-unused-header = Quyidagi fayllar media jildida topildi, lekin hech qaysi kartada ishlatilmagan:
media-check-template-references-field-header =
    Media/LaTeX teglarida { "{{Field}}" } havolalaridan foydalanganingizda Anki foydalanilgan fayllarni aniqlay olmaydi. Media/LaTeX teglari alohida qaydlarga joylashtirilishi kerak.
    
    Havola qilayotgan shablonlar:

## Shown once for each file

media-check-renamed-file = Nomi oʻzgardi: { $old } -> { $new }
media-check-oversize-file = 100MBʼdan katta: { $filename }
media-check-subfolder-file = Jild: { $filename }
media-check-missing-file = Yoʻqolgan: { $filename }
media-check-unused-file = Ishlatilmagan: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = { $count } tekshirildi...

## Deleting unused media

media-check-delete-unused-confirm = Ishlatilmagan media fayllar oʻchirilsinmi?
media-check-files-remaining =
    { $count ->
        [one] { $count } ta fayl qoldi.
       *[other] { $count } ta fayl qoldi.
    }
media-check-delete-unused-complete =
    { $count ->
        [one] { $count } ta fayl chiqitdonga koʻchirildi.
       *[other] { $count } ta fayl chiqitdonga koʻchirildi.
    }
media-check-trash-emptied = Chiqitdon jildi boʻsh.
media-check-trash-restored = Oʻchirilgan fayllar media jildiga qaytarildi.

## Rendering LaTeX

media-check-all-latex-rendered = Barcha LaTeX hosil qilindi.

## Buttons

media-check-delete-unused = Ishlatilmaganlarni oʻchirish
media-check-render-latex = LaTeXʼni hosil qilish
# button to permanently delete media files from the trash folder
media-check-empty-trash = Chiqitdonni boʻshatish
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Oʻchirilganlarni qaytarish
media-check-check-media-action = Media fayllarni tekshirish
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = missing-media
# add a tag to notes with missing media
media-check-add-tag = Teg yoʻq
