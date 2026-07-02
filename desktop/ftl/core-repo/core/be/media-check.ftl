## Shown at the top of the media check screen

media-check-window-title = Праверыць медыя
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    Сметніца:{ $count ->
        [one] { $count } файл
        [few] { $count } файлы
        [many] { $count } файлаў
       *[other] { $count } файлаў
    }, { $megs } МБ
media-check-missing-count = Адсутныя файлы: { $count }
media-check-unused-count = Неўжываныя файлы: { $count }
media-check-renamed-count = Перайменаваныя файлы: { $count }
media-check-oversize-count = Больш за 100 МБ: { $count }
media-check-subfolder-count = Падпапак: { $count }

## Shown at the top of each section

media-check-renamed-header = Некаторыя файлы былі перайменаваны для сумяшчальнасці:
media-check-oversize-header = Файлы больш за 100 МБ не могуць быць сінхранізаваны з AnkiWeb.
media-check-subfolder-header = Папкі ў папцы для медыя не падтрымліваюцца

## Shown once for each file

media-check-renamed-file = Перайменавана: { $old } -> { $new }
media-check-oversize-file = Больш за 100 МБ: { $filename }
media-check-subfolder-file = Папка: { $filename }
media-check-missing-file = Адсутнічае: { $filename }
media-check-unused-file = Неўжываны: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = Праверана { $count }...

## Deleting unused media

media-check-delete-unused-confirm = Выдаліць невыкарыстаныя медыя?
media-check-files-remaining =
    Застаецца { $count ->
        [one] { $count } файл
        [few] { $count } файлы
        [many] { $count } файлаў
       *[other] { $count } файлаў
    }.
media-check-delete-unused-complete =
    { $count ->
        [one] { $count } файл перамешчаны
        [few] { $count } фалы перамешчаны
        [many] { $count } фалаў перамешчана
       *[other] { $count } фалаў перамешчана
    } ў сметніцу.
media-check-trash-emptied = Сметніца цяпер пустая.
media-check-trash-restored = Выдаленыя файлы адноўлены ў папку медыя.

## Rendering LaTeX

media-check-all-latex-rendered = Увесь LaTeX апрацаваны.

## Buttons

media-check-delete-unused = Выдаліць неўжываныя
media-check-render-latex = Апрацоўваць LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = Ачысціць сметніцу
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Аднавіць выдаленае
media-check-check-media-action = Праверыць медыя
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = без-медыя
# add a tag to notes with missing media
media-check-add-tag = Пазначыць пустыя
