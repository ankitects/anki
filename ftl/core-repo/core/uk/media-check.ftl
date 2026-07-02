## Shown at the top of the media check screen

media-check-window-title = Перевірити медіа-файли
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    Смітник:{ $count ->
        [one] { $count } файл, { $megs } МБ
        [few] { $count } файли, { $megs } МБ
       *[other] { $count } файлів, { $megs } МБ
    }
media-check-missing-count = Пропущено файлів: { $count }
media-check-unused-count = Невикористано файлів: { $count }
media-check-renamed-count = Перейменовано файлів: { $count }
media-check-oversize-count = Більше 100МБ: { $count }
media-check-subfolder-count = Підтек: { $count }
media-check-extracted-count = Здобуті зображення: { $count }

## Shown at the top of each section

media-check-renamed-header = Деякі файли перейменовано для сумісності:
media-check-oversize-header = Файли більші за 100МБ не можуть синхронізуватися з AnkiWeb.
media-check-subfolder-header = Теки в медіа-теках не підтримуються.
media-check-missing-header = Використовується в картках, але відсутнє в медіа теці:
media-check-unused-header = Наступні файли знайдені в медіа-теці, але не використовуються в жодній з карток:
media-check-template-references-field-header =
    Anki не виявляє задіяні файли, при використанні посилань { "{{Field}}" } у мітках media/LaTeX. Натомість мітки media/LaTeX слід розмістити у окремих нотатках.
    
    Еталонні шаблони:

## Shown once for each file

media-check-renamed-file = Перейменовано: { $old } → { $new }
media-check-oversize-file = Більше ніж 100МБ: { $filename }
media-check-subfolder-file = Тека: { $filename }
media-check-missing-file = Пропущено: { $filename }
media-check-unused-file = Не використовується: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = Перевірено { $count }…

## Deleting unused media

media-check-delete-unused-confirm = Видалити невикористані медіа-файли?
media-check-files-remaining =
    { $count ->
        [one] Залишився { $count } файл
        [few] Залишилось { $count } файли
       *[other] Залишилось { $count } файлів
    }.
media-check-delete-unused-complete =
    { $count ->
        [one] { $count } файл
        [few] { $count } файли
       *[other] { $count } файлів
    } переміщено в смітник.
media-check-trash-emptied = Смітник порожній.
media-check-trash-restored = Видалені файли відновлено в медіа-теку.

## Rendering LaTeX

media-check-all-latex-rendered = Всі LaTeX відрисовані.

## Buttons

media-check-delete-unused = Видалити невикористане
media-check-render-latex = Відрисувати LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = Спорожнити кошик
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Відновити видалене
media-check-check-media-action = Перевірити медіа-файли
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = без медіа
# add a tag to notes with missing media
media-check-add-tag = Без мітки
