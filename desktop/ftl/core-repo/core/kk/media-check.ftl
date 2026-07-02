## Shown at the top of the media check screen

media-check-window-title = Медиа Тексеру
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count = Себет: { $count } файл, { $megs } МБ
media-check-missing-count = Жоқ файлдар: { $count }
media-check-unused-count = Қолданбаған файлдар: { $count }
media-check-renamed-count = Қайта аталған файлдар: { $count }
media-check-oversize-count = 100МБ-тан астам: { $count }
media-check-subfolder-count = Туынды қалталар: { $count }
media-check-extracted-count = Шығарылған суреттер: { $count }

## Shown at the top of each section

media-check-renamed-header = Кейбір файлдар үйлесімдік үшін қайта аталды:
media-check-oversize-header = Көлемі 100МБ-тан астам файлдар AnkiWeb-пен үйлесе алмайды.
media-check-subfolder-header = Медиа қалтасындағы қалталар қолдалмайды.
media-check-missing-header = Келесі файлдарға карталарда сілтеме жасалған, бірақ олар медиа қалтасында табылмады:
media-check-unused-header = Келесі файлдар медиа қалтасында табылды, бірақ олар ешқандай карталарда пайдаланылмайтын сияқты:
media-check-template-references-field-header = Anki { "{{Field}}" } сілтемелерін медиа/LaTeX тамғаларында қолданғанда пайдаланылған файлдарды анықтай алмайды. Медиа/LaTeX тамғалары жеке жазбаларға қойылуы тиіс.

## Shown once for each file

media-check-renamed-file = Қайта аталды: { $old } -> { $new }
media-check-oversize-file = 100МБ-тан астам: { $filename }
media-check-subfolder-file = Қалта: { $filename }
media-check-missing-file = Жетіспейді: { $filename }
media-check-unused-file = Қолданылмаған: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = { $count } тексерілді...

## Deleting unused media

media-check-delete-unused-confirm = Қолданылмаған медианы жою?
media-check-files-remaining = { $count } файл қалды.
media-check-delete-unused-complete = { $count } файл себетке жылжытылды.
media-check-trash-emptied = Себет қалтасы бос.
media-check-trash-restored = Жойылған файлдар медиа қалтасына қайтарылды.

## Rendering LaTeX

media-check-all-latex-rendered = Барлық LaTeX өңделді.

## Buttons

media-check-delete-unused = Қолданылмағанды Жою
media-check-render-latex = LaTeX Өңдеу
# button to permanently delete media files from the trash folder
media-check-empty-trash = Себетті Босату
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Жойылғанды Қайтару
media-check-check-media-action = Медиа Тексеру
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = жоқ-медиа
# add a tag to notes with missing media
media-check-add-tag = Тамға Жоқ
