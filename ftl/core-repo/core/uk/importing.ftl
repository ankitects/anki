importing-failed-debug-info = Імпортування не вдалося. Інформація для зневаджування:
importing-aborted = Відхилено: { $val }
importing-added-duplicate-with-first-field = Додано дублікат з однаковим першим полем: { $val }
importing-all-supported-formats = Усі формати з підтримкою { $val }
importing-allow-html-in-fields = Допускається HTML у полях
importing-anki-files-are-from-a-very = Файли .anki з дуже старої версії Anki. Ви можете імпортувати їх у версії Anki 2.0, яка доступна на сайті.
importing-anki2-files-are-not-directly-importable = Файли .anki2 напряму не імпортуються — будь ласка, імпортуйте .apkg чи .zip файл який Ви отримали.
importing-appeared-twice-in-file = Повторилося двічі у файлі: { $val }
importing-by-default-anki-will-detect-the = За замовчуванням, Anki буде знаходити знаки між полями, такі як символ табуляції, кома, и т.д. Якщо Anki визначить символ невірно, ви можете ввести його тут. Використовуйте \t для відображення TAB.
importing-cannot-merge-notetypes-of-different-kinds =
    Типи нотаток Cloze не об’єднуються зі звичайними типами нотаток.¶
    Ви можете імпортувати файл, вимкнувши '{ importing-merge-notetypes }'.
importing-change = Змінити
importing-colon = Двокрапка
importing-comma = Кома
importing-empty-first-field = Порожнє перше поле: { $val }
importing-field-separator = Поля розділює
importing-field-separator-guessed = Роздільник полів (здогадка)
importing-field-mapping = Відповідність полів
importing-field-of-file-is = Поле <b>{ $val }</b> з файлу:
importing-fields-separated-by = Поля, розділені: { $val }
importing-file-must-contain-field-column = Файл повинен мати стовпець, який можна прив'язати до поля примітки.
importing-file-version-unknown-trying-import-anyway = Невідома версія файлу, все одно спробувати імпортувати.
importing-first-field-matched = Перше поле співпало: { $val }
importing-identical = Тотожні
importing-ignore-field = Ігнорувати поле
importing-ignore-lines-where-first-field-matches = Ігнорувати рядки, в яких перше поле має відповідник в існуючій нотатці
importing-ignored = <ignored>
importing-import-even-if-existing-note-has = Імпортувати, навіть якщо існуюча нотатка містить однакове перше поле
importing-import-options = Параметри імпорту
importing-importing-complete = Імпорт завершено.
importing-invalid-file-please-restore-from-backup = Недійсний файл. Відновіть з резервної копії.
importing-map-to = Підмапитись до { $val }
importing-map-to-tags = Співставити з мітками
importing-mapped-to = відображувати на <b>{ $val }</b>
importing-mapped-to-tags = співставлено з <b>мітками</b>
# the action of combining two existing note types to create a new one
importing-merge-notetypes = Об’єднати типи нотаток
importing-merge-notetypes-help =
    Якщо обрано, і ви чи автор колоди змінили схему для типу нотаток, Anki буде
    об’єднувати дві версії замість зберігати по-різно.
    
    Зміна схеми для типу нотаток включає додавання, видалення чи перевпорядкування 
    полів або шаблонів, а також зміну поля сортування.
    Контрприклад: зміна передньої сторони у наявному шаблоні *не* є зміною схеми.
    
    Увага: Ця дія вимагає односторонньої синхронізації і може позначити наявні нотатки як змінені.
importing-mnemosyne-20-deck-db = Колода Mnemosyne 2.0 (*.db)
importing-multicharacter-separators-are-not-supported-please = Багатосимвольні розділювачі не підтримуються. Будь ласка, введіть тільки один символ.
importing-new-deck-will-be-created = Буде створено колоду: { $name }
importing-notes-added-from-file = Нотатки додані з файлу: { $val }
importing-notes-found-in-file = Нотатки знайдені в файлі: { $val }
importing-notes-skipped-as-theyre-already-in = Нотатки пропущено, оскільки вже є в колекції: { $val }
importing-notes-skipped-update-due-to-notetype = Нотатки не оновлено, оскільки тип нотаток змінено від часу першого імпорту нотаток: { $val }
importing-notes-updated-as-file-had-newer = Нотатки оновлено, як файл новішої версії: { $val }
importing-include-reviews = Включити пригадування
importing-also-import-progress = Імпортувати прогрес навчання
importing-with-deck-configs = Імпортувати конфігурації колоди
importing-updates = Оновлення
importing-include-reviews-help =
    Якщо ввімкнено, імпортуються попередні пригадування, якими поділився співвласник колоди.
    Якщо вимкнено, картки імпортуються як нові, а мітки "приставуча" та "позначена" видаляться.
importing-with-deck-configs-help =
    Якщо ввімкнено, імпортуються налаштування колоди, які включив співвласник колоди.
    Якщо вимкнено, усі колоди матимуть типову конфігурацію.
importing-packaged-anki-deckcollection-apkg-colpkg-zip = Пакунок Anki колода/колекція (*.apkg *.colpkg *.zip)
# the '|' character
importing-pipe = Вертикальна риска (|)
# Warning displayed when the csv import preview table is clipped (some columns were hidden)
# $count is intended to be a large number (1000 and above)
importing-preview-truncated =
    { $count ->
        [one] Показано лише один стовпець. Змініть роздільник поля, якщо такий поділ неправильний.
        [few] Показано лише перші { $count } стовпці. Змініть роздільник поля, якщо такий поділ неправильний.
       *[many] Показано лише перші { $count } стовпців. Змініть роздільник поля, якщо такий поділ неправильний.
    }
importing-rows-had-num1d-fields-expected-num2d = '{ $row }' вміщує { $found } полів, очікуючих { $expected }
importing-selected-file-was-not-in-utf8 = Обнаний файл не був у форматі UTF-8. Перегляньте розділ "Імпортування" в інструкції користувача.
importing-semicolon = Крапка з комою
importing-skipped = Пропущені
importing-tab = Табуляція
importing-tag-modified-notes = Додати мітку зміненим записам:
importing-text-separated-by-tabs-or-semicolons = Текстовий файл, розділений TAB або крапкою з комою (*)
importing-the-first-field-of-the-note = Для першого поля типу нотатки має бути відповідник.
importing-the-provided-file-is-not-a = Вибраний файл не є справжнім файлом .apkg.
importing-this-file-does-not-appear-to = Файл, здається, не є справжнім файлом .apkg. Якщо ви отримуєте помилку з файлу, завантаженого з AnkiWeb, існує вірогідність, що скачування файлу не пройшло успішно. Будь ласка, повторіть спробу, і якщо проблема не зникне, спробуйте скачати файл в іншому браузері.
importing-this-will-delete-your-existing-collection = Це видалить вашу існуючу колекцію колод та замінить дані у файлі, що ви імпортуєте. Ви впевнені?
importing-unable-to-import-from-a-readonly = Неможливо імпортувати файл, призначений лише для зчитування.
importing-unknown-file-format = Невідомий формат файлу.
importing-update-existing-notes-when-first-field = Оновити існуючі нотатки, коли співпадають перше поле
importing-updated = Оновлено
importing-update-if-newer = Якщо новіше
importing-update-always = Завжди
importing-update-never = Ніколи
importing-update-notes = Оновити нотатки
importing-update-notes-help =
    Коли оновлювати наявну нотатку у колекції. Типово це відбувається
    якщо відповідну імпортовану нотатку  нещодавно було змінено.
importing-update-notetypes = Оновити типи нотаток
importing-update-notetypes-help =
    Коли оновлювати наявний тип нотаток у колекції. Типово це відбувається
    якщо відповідний імпортований тип нотаток  нещодавно було змінено. Зміни тексту шаблону
    і стилю завжди імпортуються, але для зміни схеми (наприклад, якщо кількість або порядок
    полів змінено), слід увімкнути налаштування '{ importing-merge-notetypes }'.
importing-note-added =
    { $count ->
        [one] Додано { $count } нотатку
        [few] Додано { $count } нотатки
        [many] Додано { $count } нотаток
       *[other] Додано { $count } нотаток
    }
importing-note-imported =
    { $count ->
        [one] Імпортовано { $count } нотатку.
        [few] Імпортовано { $count } нотатки.
        [many] Імпортовано { $count } нотаток.
       *[other] Імпортовано { $count } нотаток.
    }
importing-note-unchanged =
    { $count ->
        [one] { $count } нотатка не змінилась
        [few] { $count } нотатки не змінились
        [many] { $count } нотаток не змінились
       *[other] { $count } нотаток не змінились
    }
importing-note-updated =
    { $count ->
        [one] { $count } нотатку оновлено
        [few] { $count } нотаток оновлено
       *[other] { $count } нотаток оновлено
    }
importing-processed-media-file =
    { $count ->
        [one] Оброблено { $count } медіафайл
        [few] Оброблено { $count } медіафайл
       *[other] Оброблено { $count } медіафайл
    }
importing-importing-file = Імпортування файлу
importing-extracting = Витягування даних…
importing-gathering = Збір даних...
importing-failed-to-import-media-file = Медіа-файл не імпортувався: { $debugInfo }
importing-processed-notes =
    { $count ->
        [one] Оброблено { $count } нотатку…
        [few] Оброблено { $count } нотатки…
       *[other] Оброблено { $count } нотаток…
    }
importing-processed-cards =
    { $count ->
        [one] Оброблено { $count } картку…
        [few] Оброблено { $count } картки…
       *[other] Оброблено { $count } карток…
    }
importing-existing-notes = Існуючі нотатки
# "Existing notes: Duplicate" (verb)
importing-duplicate = Дублювати
# "Existing notes: Preserve" (verb)
importing-preserve = Запобігти
# "Existing notes: Update" (verb)
importing-update = Оновити
importing-tag-all-notes = Мітки для всіх нотаток
importing-tag-updated-notes = Мітки для оновлених нотаток
importing-file = Файл
# "Match scope: notetype / notetype and deck". Controls how duplicates are matched.
importing-match-scope = Область збігів
# Used with the 'match scope' option
importing-notetype-and-deck = Тип нотаток та колоди
importing-cards-added =
    { $count ->
        [one] Додано { $count } картку.
        [few] Додано { $count } картки.
       *[many] Додано { $count } карток.
    }
importing-file-empty = Вибраний файл є порожнім.
importing-notes-added =
    { $count ->
        [one] Імпортовано { $count } нову нотатку.
        [few] Імпортовано { $count } нові нотатки.
        [many] Імпортовано { $count } нових нотаток.
       *[other] Імпортовано { $count } нових нотаток.
    }
importing-notes-updated =
    { $count ->
        [one] Використано { $count } примітку щоб оновити наявні.
        [few] Використано { $count } примітки щоб оновити наявні.
       *[many] Використано { $count } приміток щоб оновити наявні.
    }
importing-existing-notes-skipped =
    { $count ->
        [one] { $count } нотатка уже є у колекції.
        [few] { $count } нотатки уже є у колекції.
       *[many] { $count } нотаток уже є у колекції.
    }
importing-notes-failed =
    { $count ->
        [one] Не вдалось імпортувати { $count } нотатку.
        [few] Не вдалось імпортувати { $count } нотатки.
       *[many] Не вдалось імпортувати { $count } нотаток.
    }
importing-conflicting-notes-skipped =
    { $count ->
        [one] { $count } нотатку не імпортовано, оскільки її тип нотатки змінився.
        [few] { $count } нотатки не імпортовано, оскільки їх тип нотатки змінився.
       *[many] { $count } нотаток не імпортовано, оскільки їх тип нотатки змінився.
    }
importing-conflicting-notes-skipped2 =
    { $count ->
        [one] { $count } нотатку не імпортовано, оскільки її тип нотатки змінився, а '{ importing-merge-notetypes }'. не увімкнено.
        [few] { $count } нотатки не імпортовано, оскільки їх тип нотатки змінився, а '{ importing-merge-notetypes }'. не увімкнено.
       *[many] { $count } нотаток не імпортовано, оскільки їх тип нотатки змінився, а '{ importing-merge-notetypes }'. не увімкнено.
    }
importing-import-log = Журнал імпорту
importing-no-notes-in-file = У файлі не знайдено жодної нотатки.
importing-notes-found-in-file2 =
    { $notes ->
        [one] У файлі знайдено { $notes } нотатку. Зокрема:
        [few] У файлі знайдено { $notes } нотатки. Зокрема:
       *[many] У файлі знайдено { $notes } нотаток. Зокрема:
    }
importing-show = Показати
importing-details = Детальніше
importing-status = Статус
importing-duplicate-note-added = Додано дубльовану нотатку
importing-added-new-note = Додано нову нотатку
importing-existing-note-skipped = Нотатку пропущено, оскільки її останній примірник вже в колекції
importing-note-skipped-update-due-to-notetype = Нотатку не оновлено, оскільки тип нотатки змінився відколи її імпортували вперше
importing-note-skipped-update-due-to-notetype2 = Нотатку не оновлено, оскільки тип нотатки змінився відколи її імпортували вперше і { importing-merge-notetypes } не увімкнено
importing-note-updated-as-file-had-newer = Нотатку оновлено, адже у файлі була новіша версія
importing-note-skipped-due-to-missing-notetype = Нотатку пропущено, адже відсутній тип нотатки
importing-note-skipped-due-to-missing-deck = Нотатку пропущено, оскільки нема її колоди
importing-note-skipped-due-to-empty-first-field = Нотатку пропущено, адже її перше поле є порожнім
importing-field-separator-help =
    Символ-роздільник полів у текстовому файлі. Скористайтеся попереднім переглядом, щоб
    переконатися, що поля правильно розділяються.
    
    Зверніть увагу, якщо цей символ з’являється всередині поля, таке поле слід
    взяти в лапки, згідно з стандартом CSV. Табличні процесори, такі як LibreOffice
    роблять це автоматично.
importing-allow-html-in-fields-help =
    Увімкніть, якщо файл містить HTML форматування. Наприклад якщо у файлі є рядок
    '&lt;br&gt;', на картці він з'явиться як розрив рядка. Проте, якщо це налаштування
    вимкнено, то на картці буде показано тільки символи '&lt;br&gt;' .
importing-notetype-help =
    Цей тип нотаток матимуть лише нові імпортовані нотатки, а наявні нотатки цього типу
    буде оновлено.
    
    Ви можете вибрати, відповідність полів файлу полям типу нотатки за допомогою
    інструменту відповідності.
importing-deck-help = Імпортовані карти з'являться у цій колоді.
importing-existing-notes-help =
    Що робити, якщо імпортована нотатка збігається з наявною.
    
    -`{ importing-update }`: Оновити існуючу нотатку.
    -`{ importing-preserve }`: Нічого не робити.
    -`{ importing-duplicate }`: Створити нову нотатку.
importing-match-scope-help =
    Перевірка на дублікати відбудеться лише для наявних нотаток однакового типу. Додатково
    можна обмежити перевірку до карток в межах однієї колоди.
importing-tag-all-notes-help = Ці мітки додадуться до нових імпортованих та оновлених нотаток.
importing-tag-updated-notes-help = Ці мітки додадуться до усіх оновлених нотаток.
importing-overview = Огляд

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

importing-importing-collection = Імпортування колекції…
importing-unable-to-import-filename = Неможливо імпортувати { $filename }: тип файлу не підтримується
importing-notes-that-could-not-be-imported = Нотатки не імпортовано через зміну їх типу: { $val }
importing-added = Додано
importing-pauker-18-lesson-paugz = Урок Pauker 1.8 (*.pau.gz)
importing-supermemo-xml-export-xml = Файл Supermemo у форматі XML (*.xml)
