## Shown at the top of the media check screen

media-check-window-title = Проверить медиафайлы
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    Корзина: { $count ->
        [one] 1 файл, { $megs } МБ
        [few] { $count } файла, { $megs } МБ
        [many] { $count } файлов, { $megs } МБ
       *[other] { $count } файлов, { $megs } МБ
    }
media-check-missing-count = Отсутствует файлов: { $count }
media-check-unused-count = Не используется файлов: { $count }
media-check-renamed-count = Переименовано файлов: { $count }
media-check-oversize-count = Больше, чем 100 МБ: { $count }
media-check-subfolder-count = Подпапок: { $count }
media-check-extracted-count = Извлечено изображени: { $count }

## Shown at the top of each section

media-check-renamed-header = Переименованные для совместимости:
media-check-oversize-header = Файлы больше 100 МБ не могут быть синхронизированы с AnkiWeb.
media-check-subfolder-header = Медиапапка не должна содержать другие папки.
media-check-missing-header = Эти файлы прикреплены к карточкам, но отсутствуют в медиапапке:
media-check-unused-header = Эти файлы в медиапапке, но не прикреплены ни к одной карточке:
media-check-template-references-field-header =
    Anki не может обнаружить файлы, когда используются ссылки { "{{Field}}" } в медиа- или LaTeX-тегах. Медиа- и LaTeX-теги должны быть в отдельных записях.
    
    Ссылающиеся шаблоны:

## Shown once for each file

media-check-renamed-file = Переименован: { $old } в { $new }
media-check-oversize-file = Больше, чем 100 МБ: { $filename }
media-check-subfolder-file = Папка: { $filename }
media-check-missing-file = Отсутствует: { $filename }
media-check-unused-file = Не используется: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = Проверено { $count }...

## Deleting unused media

media-check-delete-unused-confirm = Удалить неиспользуемые медиафайлы?
media-check-files-remaining =
    { $count ->
        [one] 1 файл
        [few] { $count } файла
        [many] { $count } файлов
       *[other] { $count } файлов
    } осталось.
media-check-delete-unused-complete =
    { $count ->
        [one] 1 файл
        [few] { $count } файла
        [many] { $count } файлов
       *[other] { $count } файлов
    } удалено
media-check-trash-emptied = Корзина очищена.
media-check-trash-restored = Удалённые файлы восстановлены в медиапапку.

## Rendering LaTeX

media-check-all-latex-rendered = Весь LaTeX отрисован.

## Buttons

media-check-delete-unused = Удалить неиспользуемые
media-check-render-latex = Отрисовать LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = Очистить корзину
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Восстановить удалённые
media-check-check-media-action = Проверить медиафайлы
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = missing-media
# add a tag to notes with missing media
media-check-add-tag = Добавить метку
