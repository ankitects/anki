# This word is used by TTS voices instead of the elided part of a cloze.
card-templates-blank = Пусто
card-templates-changes-will-affect-notes =
    { $count ->
        [one] Следующие изменения затронут { $count } запись, использующую этот тип карточек.
        [few] Следующие изменения затронут { $count } записи, использующие этот тип карточек.
        [many] Следующие изменения затронут { $count } записей, использующих этот тип карточек.
       *[other] Следующие изменения затронут { $count } записей, использующих этот тип карточек.
    }
card-templates-card-type = Тип карточки:
card-templates-front-template = Шаблон лица
card-templates-back-template = Шаблон оборота
card-templates-template-styling = Таблица стилей
card-templates-front-preview = Предпросмотр лица
card-templates-back-preview = Предпросмотр оборота
card-templates-preview-box = Предпросмотр
card-templates-template-box = Шаблон
card-templates-sample-cloze = Вот { "{{c1::" }пример{ "}}" } заполнения пропуска.
card-templates-fill-empty = Заполнить пустые поля
card-templates-night-mode = Темный режим
# Add "mobile" class to card preview, so the card appears like it would
# on a mobile device.
card-templates-add-mobile-class = Как на мобильном
card-templates-preview-settings = Параметры
card-templates-invalid-template-number = Проблема с шаблоном карточки { $number }
card-templates-identical-front = Лицевая сторона идентична шаблону карточки { $number }.
card-templates-no-front-field = Предполагается, что на лицевой стороне шаблона карточки будет отображена замена поля.
card-templates-missing-cloze = Предполагается, что на лицевой и обратной стороне шаблона карточки будет '{ "{{" }пробел:Текст{ "}}" }' или что-то подобное.
card-templates-extraneous-cloze = пропуски: только в типах записей с пропусками.
card-templates-see-preview = Дополнительная информация в предпросмотре.
card-templates-field-not-found = Поле '{ $field }' не найдено.
card-templates-changes-saved = Изменения сохранены.
card-templates-discard-changes = Отменить изменения?
card-templates-add-card-type = Добавить тип карточек...
card-templates-anki-couldnt-find-the-line-between = Anki не нашла строку между вопросом и ответом. Отредактируйте шаблон вручную, чтобы поменять их местами.
card-templates-at-least-one-card-type-is = Нужен хотя бы один тип карточек.
card-templates-browser-appearance = Вид в окне "Просмотр"...
card-templates-card = Карточка { $val }
card-templates-card-types-for = Типы карточек для { $val }
card-templates-cloze = Задание с пропусками { $val }
card-templates-deck-override = Подмена колоды...
card-templates-copy-info = Копировать в буфер обмена
card-templates-delete-the-as-card-type-and = Удалить тип карточек '{ $template }' и его { $cards }?
card-templates-enter-deck-to-place-new = Укажите колоду для новых { $val } карточек, или оставьте поле пустым:
card-templates-enter-new-card-position-1 = Введите позиция новых карточек (1...{ $val }):
card-templates-flip = Перевернуть
card-templates-form = Форма
card-templates-off = (выкл.)
card-templates-on = (вкл.)
card-templates-remove-card-type = Удалить тип карточки...
card-templates-rename-card-type = Переименовать тип карточки...
card-templates-reposition-card-type = Переместить тип карточки...
card-templates-card-count =
    { $count ->
        [one] { $count } карточка
        [few] { $count } карточки
        [many] { $count } карточек
       *[other] { $count } карточек
    }
card-templates-this-will-create-card-proceed =
    { $count ->
        [one] Будет создана { $count } карточка. Продолжить?
        [few] Будут созданы { $count } карточки. Продолжить?
        [many] Будет создано { $count } карточек. Продолжить?
       *[other] Будет создано { $count } карточек. Продолжить?
    }
card-templates-type-boxes-warning = Поддерживается только одно поле ввода в шаблоне карточки.
card-templates-restore-to-default = Сбросить
card-templates-restore-to-default-confirmation = Все поля и шаблоны этого типа записи будут сброшены до значений по умолчанию, а дополнительные поля, шаблоны и стили будут удалены. Продолжить?
card-templates-restored-to-default = Типы записей восстановлены.
