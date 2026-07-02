importing-failed-debug-info = Не удалось импортировать. Отладочная информация:
importing-aborted = Прервано: { $val }
importing-added-duplicate-with-first-field = Добавлен повтор с первым полем: { $val }
importing-all-supported-formats = Все поддерживаемые форматы { $val }
importing-allow-html-in-fields = Разрешить HTML в полях
importing-anki-files-are-from-a-very = Эти файлы .anki для старой версии Anki. Их можно импортировать через Anki 2.0, доступной на сайте.
importing-anki2-files-are-not-directly-importable = Эти файлы .anki2 нельзя импортировать напрямую. Импортируйте .apkg или .zip, которые вы получили.
importing-appeared-twice-in-file = Дважды встречается в файле: { $val }
importing-by-default-anki-will-detect-the =
    По умолчанию Anki будет обнаруживать разделители полей: табуляцию, запятую и т. д.
    Если Anki определит разделитель неверно, введите его здесь.
    Табуляция обозначается \t.
importing-cannot-merge-notetypes-of-different-kinds =
    Записи с типом Cloze не могут быть объединены с обычными типами записей.
    Вы по-прежнему можете импортировать файл с отключенным '{ importing-merge-notetypes }'.
importing-change = Изменить
importing-colon = Двоеточие
importing-comma = Запятая
importing-empty-first-field = Пустое первое поле: { $val }
importing-field-separator = Разделитель полей
importing-field-separator-guessed = Разделитель полей (определяется автоматически)
importing-field-mapping = Сопоставление полей
importing-field-of-file-is = Поле <b>{ $val }</b> файла:
importing-fields-separated-by = Поля разделены: { $val }
importing-file-must-contain-field-column = В файле должен быть хотя бы один столбец, чтобы сопоставить его полю записи.
importing-file-version-unknown-trying-import-anyway = Версия файла неизвестна. Пытается импортировать.
importing-first-field-matched = Совпадающее первое поле: { $val }
importing-identical = Одинаковые
importing-ignore-field = Пропустить поле
importing-ignore-lines-where-first-field-matches = Пропустить строки, для которых есть запись с таким же первым полем
importing-ignored = <пропускается>
importing-import-even-if-existing-note-has = Импортировать, даже если существует запись с таким же первым полем
importing-import-options = Настройки импорта
importing-importing-complete = Импорт завершён.
importing-invalid-file-please-restore-from-backup = Недопустимый файл. Восстановите из резервной копии.
importing-map-to = Сопоставить { $val }
importing-map-to-tags = Сопоставить меткам
importing-mapped-to = сопоставить <b>{ $val }</b>
importing-mapped-to-tags = сопоставить <b>меткам</b>
# the action of combining two existing note types to create a new one
importing-merge-notetypes = Объединить типы записи
importing-merge-notetypes-help =
    Если этот флажок установлен, и вы или автор колоды изменили схему типа записи, Anki объединит две версии вместо того, чтобы оставить обе.
    
    Изменение схемы типа заметки означает добавление, удаление или переупорядочивание полей или шаблонов или изменение поля сортировки.
    С другой стороны, изменение лицевой стороны существующего шаблона *не* представляет собой изменение схемы.
    
    Предупреждение: потребуется односторонняя синхронизация и может пометить существующие заметки как изменённые.
importing-mnemosyne-20-deck-db = Колода Mnemosyne 2.0 (*.db)
importing-multicharacter-separators-are-not-supported-please = Разделители из нескольких символов не поддерживаются. Введите только один символ.
importing-new-deck-will-be-created = Будет создана новая колода: { $name }
importing-notes-added-from-file = Записи, добавленные из файла: { $val }
importing-notes-found-in-file = Записи, найденные в файле: { $val }
importing-notes-skipped-as-theyre-already-in = Пропущенные записи, которые уже есть в коллекции: { $val }
importing-notes-skipped-update-due-to-notetype = Записи не обновлены, потому что тип записи был изменён со времени первого импорта записей: { $val }
importing-notes-updated-as-file-had-newer = Записи, которые обновлены: { $val }
importing-include-reviews = Включать повторения
importing-also-import-progress = Импортировать прогресс обучения
importing-with-deck-configs = Импортировать конфигурации колод
importing-updates = Процедура обновления
importing-include-reviews-help = Если включено, все предыдущие обзоры, которые включил пользователь, разделяющий колоду, также будут импортированы. В противном случае все карты будут импортированы как новые карты, а любые метки «leech» или «marked» будут удалены.
importing-with-deck-configs-help =
    Если эта функция включена, то все параметры колоды, указанные пользователем, также будут импортированы.
    В противном случае всем колодам будет назначена конфигурация по умолчанию.
importing-packaged-anki-deckcollection-apkg-colpkg-zip = Упакованная колода/коллекция Anki (*.apkg *.colpkg *.zip)
# the '|' character
importing-pipe = Черта
# Warning displayed when the csv import preview table is clipped (some columns were hidden)
# $count is intended to be a large number (1000 and above)
importing-preview-truncated =
    { $count ->
        [one] Отображаются только { $count } столбец. Если это кажется неправильным, попробуйте изменить разделитель полей.
        [few] Отображаются только { $count } столбца. Если это кажется неправильным, попробуйте изменить разделитель полей.
       *[many] Отображаются только { $count } столбца. Если это кажется неправильным, попробуйте изменить разделитель полей.
    }
importing-rows-had-num1d-fields-expected-num2d = В '{ $row }' { $found } полей, но должно быть { $expected }
importing-selected-file-was-not-in-utf8 = Выбранный файл не в кодировке UTF-8. Пожалуйста, прочтите раздел об импорте в руководстве.
importing-semicolon = Точка с запятой
importing-skipped = Пропущено
importing-tab = Табуляция
importing-tag-modified-notes = Метки для изменённых:
importing-text-separated-by-tabs-or-semicolons = Текст, разделённый табуляцией или точками с запятой
importing-the-first-field-of-the-note = Первое поле записи должно быть прикрепленно.
importing-the-provided-file-is-not-a = Указанный файл должен быть в формате .apkg.
importing-this-file-does-not-appear-to = Это неисправный файл .apkg. Если вы скачали файл с AnkiWeb, может быть, загрузка произошла с ошибкой. Попробуйте скачать файл в другом браузере.
importing-this-will-delete-your-existing-collection = Это действие удалит существующую коллекцию, заменив её данными из импортируемого файла. Вы уверены?
importing-unable-to-import-from-a-readonly = Не удалось импортировать из доступного только для чтения файла.
importing-unknown-file-format = Неизвестный формат файла.
importing-update-existing-notes-when-first-field = Обновить существующие записи, если первое поле совпадает
importing-updated = Обновлено
importing-update-if-newer = Если новее
importing-update-always = Всегда
importing-update-never = Никогда
importing-update-notes = Обновить записи
importing-update-notes-help = Когда обновлять существующую запись в вашей коллекции. По умолчанию это делается только в том случае, если соответствующая импортированная запись была недавно изменена.
importing-update-notetypes = Обновить типы записей
importing-update-notetypes-help =
    Когда обновлять существующий тип записи в вашей коллекции. По умолчанию это делается только в том случае, если соответствующий импортированный тип записи был недавно изменен.
    Изменения текста и стиля шаблона всегда можно импортировать, но для изменений схемы (например, если изменилось количество или порядок полей) также необходимо включить опцию '{ importing-merge-notetypes }'.
importing-note-added =
    { $count ->
        [one] { $count } запись добавлена
        [few] { $count } записи добавлены
        [many] { $count } записей добавлено
       *[other] { $count } записей добавлено
    }
importing-note-imported =
    { $count ->
        [one] { $count } запись импортирована.
        [few] { $count } записи импортированы.
        [many] { $count } записей импортировано.
       *[other] { $count } записей импортировано.
    }
importing-note-unchanged =
    { $count ->
        [one] { $count } запись не изменена
        [few] { $count } записи не изменены
        [many] { $count } записей не изменено
       *[other] { $count } записей не изменено
    }
importing-note-updated =
    { $count ->
        [one] { $count } запись обновлена
        [few] { $count } записи обновлены
        [many] { $count } записей обновлено
       *[other] { $count } записей обновлено
    }
importing-processed-media-file =
    { $count ->
        [one] Обработан { $count } медиафайл
        [few] Обработано { $count } медиафайла
        [many] Обработано { $count } медиафайлов
       *[other] Обработано { $count } медиафайлов
    }
importing-importing-file = Файл импортируется...
importing-extracting = Данные извлекаются...
importing-gathering = Данные собираются...
importing-failed-to-import-media-file = Не удалось импортировать медиафайл: { $debugInfo }
importing-processed-notes =
    { $count ->
        [one] Обработана { $count } запись...
        [few] Обработаны { $count } записи...
        [many] Обработано { $count } записей...
       *[other] Обработано { $count } записей...
    }
importing-processed-cards =
    { $count ->
        [one] Обработана { $count } карточка...
        [few] Обработаны { $count } карточек...
        [many] Обработано { $count } карточек...
       *[other] Обработано { $count } карточек...
    }
importing-existing-notes = Существующие записи
# "Existing notes: Duplicate" (verb)
importing-duplicate = Дублировать
# "Existing notes: Preserve" (verb)
importing-preserve = Сохранить
# "Existing notes: Update" (verb)
importing-update = Обновить
importing-tag-all-notes = Пометить все
importing-tag-updated-notes = Пометить обновлённые
importing-file = Файл
# "Match scope: notetype / notetype and deck". Controls how duplicates are matched.
importing-match-scope = Где проверять
# Used with the 'match scope' option
importing-notetype-and-deck = Тип записи и колода
importing-cards-added =
    { $count ->
        [one] { $count } карточка добавлена.
        [few] { $count } карточки добавлены.
        [many] { $count } карточек добавлено.
       *[other] { $count } карточек добавлено.
    }
importing-file-empty = Выбранный файл пуст.
importing-notes-added =
    { $count ->
        [one] { $count } новая запись импортирована.
        [few] { $count } новые записи импортированы.
        [many] { $count } новых записей импортировано.
       *[other] { $count } новых записей импортировано.
    }
importing-notes-updated =
    { $count ->
        [one] { $count } запись обновила существующие.
        [few] { $count } записи обновили существующие.
        [many] { $count } записей обновили существующие.
       *[other] { $count } записей обновили существующие.
    }
importing-existing-notes-skipped =
    { $count ->
        [one] { $count } запись уже в коллекции.
        [few] { $count } записи уже в коллекции.
        [many] { $count } записей уже в коллекции.
       *[other] { $count } записей уже в коллекции.
    }
importing-notes-failed =
    { $count ->
        [one] { $count } запись не может быть импортирована.
        [few] { $count } записи не могут быть импортированы.
       *[many] { $count } записей не могут быть импортированы.
    }
importing-conflicting-notes-skipped =
    { $count ->
        [one] { $count } запись не импортирована, потому что изменился её тип.
        [few] { $count } записи не импортированы, потому что изменился их тип.
        [many] { $count } записей не импортировано, потому что изменился их тип.
       *[other] { $count } записей не импортировано, потому что изменился их тип.
    }
importing-conflicting-notes-skipped2 =
    { $count ->
        [one] { $count } запись не была импортирована, поскольку ее тип изменился, а '{ importing-merge-notetypes }' не был включен.
        [few] { $count } записи не были импортированы, поскольку тип записи их изменился, а '{ importing-merge-notetypes }' не был включен.
       *[many] { $count } записей не были импортированы, поскольку тип записи их изменился, а '{ importing-merge-notetypes }' не был включен.
    }
importing-import-log = Журнал импорта
importing-no-notes-in-file = В файле не найдены записи.
importing-notes-found-in-file2 =
    { $notes ->
        [one] { $notes } запись  найдено в файле, из которых:
        [few] { $notes } записи  найдено в файле, из которых:
        [many] { $notes } записей  найдено в файле, из которых:
       *[other] { $notes } записей  найдено в файле, из которых:
    }
importing-show = Показать
importing-details = Подробности
importing-status = Статус
importing-duplicate-note-added = Добавлен повтор
importing-added-new-note = Добавлена новая запись
importing-existing-note-skipped = Запись пропущена, потому что актуальная версия уже есть в коллекции
importing-note-skipped-update-due-to-notetype = Запись не обновлена, потому что тип записи был изменён с момента добавления
importing-note-skipped-update-due-to-notetype2 = Запись не обновлена, так как тип записи был изменен с момента первого импорта этой записи, а '{ importing-merge-notetypes }' не был включен
importing-note-updated-as-file-had-newer = Запись обновлена, потому что в файле более поздняя версия
importing-note-skipped-due-to-missing-notetype = Запись пропущена, потому что отсутствует тип записи
importing-note-skipped-due-to-missing-deck = Запись пропущена, потому что отсутствует тип колода
importing-note-skipped-due-to-empty-first-field = Запись пропущена, потому что первое поле пустое
importing-field-separator-help =
    Символ, разделяющий поля в текстовом файле. Вы можете использовать предварительный просмотр, чтобы проверить, правильно ли разделены поля.
    
    Обратите внимание, что если этот символ появляется в каком-либо поле, поле должно быть заключено в кавычки в соответствии со стандартом CSV. Программы для работы с электронными таблицами, такие как LibreOffice, сделают это автоматически.
    
    Его нельзя изменить, если текстовый файл принудительно использует определенный разделитель через заголовок файла.
    Если заголовок файла отсутствует, Anki попытается угадать, какой разделитель используется.
importing-allow-html-in-fields-help =
    Включите этот параметр, если файл содержит форматирование HTML. Например, если файл содержит строку '&lt;br&gt;', она будет отображаться как перенос строки на вашей карточке.
    С другой стороны, если этот параметр отключен, будут отображаться литеральные символы '&lt;br&gt;'.
importing-notetype-help =
    Новые импортированные записи будут иметь этот тип, и только существующие записи с этим типом будут обновлены.
    
    Вы можете выбрать, какие поля в файле соответствуют полям с определенным типом записи, с помощью инструмента сопоставления.
importing-deck-help = Импортированные карточки будут помещены в эту колоду.
importing-existing-notes-help =
    Что делать, если импортированная запись совпадает с существующей.
    
    - `{ importing-update }`: Обновить существующую запись.
    - `{ importing-preserve }`: Ничего не делать.
    - `{ importing-duplicate }`: Создать новую запись.
importing-match-scope-help =
    Только существующие записи с одинаковым типом будут проверяться на наличие дубликатов.
    Это может быть дополнительно ограничено записями с картами в той же колоде.
importing-tag-all-notes-help = Эти метки будут добавлены к импортированным и обновлённым записям.
importing-tag-updated-notes-help = Эти метки будут добавлены к обновлённым записям.
importing-overview = Обзор

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

importing-importing-collection = Импортирование коллекции...
importing-unable-to-import-filename = Не удалось импортировать { $filename }: тип файлов не поддерживается
importing-notes-that-could-not-be-imported = Записи, которые не импортированы, потому что изменился их тип: { $val }
importing-added = Успешно добавлено
importing-pauker-18-lesson-paugz = Урок Pauker 1.8 (*.pau.gz)
importing-supermemo-xml-export-xml = Экспорт в Supermemo XML (*.xml)
