# This word is used by TTS voices instead of the elided part of a cloze.
card-templates-blank = пропущено
card-templates-changes-will-affect-notes =
    { $count ->
        [one] Наступні зміни вплинуть на { $count } нотатку, який використовує цей тип картки.
        [few] Наступні зміни вплинуть на { $count } нотатки, які використовують цей тип картки.
       *[other] Наступні зміни вплинуть на { $count } нотаток, які використовують цей тип картки.
    }
card-templates-card-type = Тип картки:
card-templates-front-template = Шаблон передньої сторони
card-templates-back-template = Шаблон зворотної сторони
card-templates-template-styling = Стиль
card-templates-front-preview = Попередній перегляд передньої сторони
card-templates-back-preview = Попередній перегляд зворотної сторони
card-templates-preview-box = Попередній перегляд
card-templates-template-box = Шаблон
card-templates-sample-cloze = Ось { "{{c1::" } зразок { "}}" } закритого тексту.
card-templates-fill-empty = Заповніть пусті поля
card-templates-night-mode = Нічний режим
# Add "mobile" class to card preview, so the card appears like it would
# on a mobile device.
card-templates-add-mobile-class = Додати мобільний тип
card-templates-preview-settings = Налаштування
card-templates-invalid-template-number = Проблема з шаблоном картки { $number }  у типі нотаток '{ $notetype }'.
card-templates-identical-front = Передня сторона ідентична до шаблону картки { $number }.
card-templates-no-front-field = На передній стороні шаблону картки повинне бути поле для заміни.
card-templates-missing-cloze = На обох сторонах шаблону картки повинен бути '{ "{{" }cloze:Text{ "}}" }' або подібне поле.
card-templates-extraneous-cloze = 'cloze:' може використовуватися лише для нотаток з закритим текстом.
card-templates-see-preview = Щоб отримати більше інформації подивіться на попередній перегляд.
card-templates-field-not-found = Не знайдено поля '{ $field }'.
card-templates-changes-saved = Зміни збережено.
card-templates-discard-changes = Скасувати зміни?
card-templates-add-card-type = Додати тип картки…
card-templates-anki-couldnt-find-the-line-between = Anki не вдалося знайти межу між питанням і відповіддю. Налаштуйте шаблон вручну, щоб розділити питання та відповідь.
card-templates-at-least-one-card-type-is = Необхідно вказати принаймні один тип картки.
card-templates-browser-appearance = Вигляд переглядача…
card-templates-card = Картка { $val }
card-templates-card-types-for = Типи картки для { $val }
card-templates-cloze = Закрито { $val }
card-templates-deck-override = Заміна колоди…
card-templates-copy-info = Копіювати до буферу обміну
card-templates-delete-the-as-card-type-and = Видалити тип картки '{ $template }', та її { $cards }?
card-templates-enter-deck-to-place-new = Вкажіть колоду для { $val } нових карток або залиште порожнім:
card-templates-enter-new-card-position-1 = Введіть нову позицію картки (1...{ $val }):
card-templates-flip = Перевернути
card-templates-form = Форма
card-templates-off = (вимк)
card-templates-on = (увімкн)
card-templates-remove-card-type = Видалення типу картки…
card-templates-rename-card-type = Перейменування типу картки…
card-templates-reposition-card-type = Переміщення типу картки…
card-templates-card-count =
    { $count ->
        [one] { $count } картка
        [few] { $count } картки
       *[other] { $count } карток
    }
card-templates-this-will-create-card-proceed =
    { $count ->
        [one] Буде створено { $count } картку. Продовжити?
        [few] Буде створено { $count } картки. Продовжити?
       *[other] Буде створено { $count } карток. Продовжити?
    }
card-templates-type-boxes-warning = Шаблон картки підтримує лише одне поле вводу.
card-templates-restore-to-default = Відновити типовий шаблон
card-templates-restore-to-default-confirmation =
    Ця дія поверне всі поля та шаблони у даному типі нотатки до типових
    значень, видаливши додаткові поля/шаблони, їх вміст та вигляд. Бажаєте продовжити?
card-templates-restored-to-default = Тип нотатки відновлено до початкового стану.
