### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    { $decks ->
        [one] используется { $decks } колодой
        [few] используется { $decks } колодами
        [many] используется { $decks } колодами
       *[other] используется { $decks } колодами
    }
deck-config-default-name = По умолчанию
deck-config-title = Параметры колоды

## Daily limits section

deck-config-daily-limits = Ежедневный лимит
deck-config-new-limit-tooltip =
    Максимум новых карточек в день, если они доступны.
    Поскольку новый материал увеличивает краткосрочную учебную нагрузку, это число обычно должно быть в 10 раз меньше лимита повторений.
deck-config-review-limit-tooltip = Максимум повторяемых карточек в день, если подошёл их срок.
deck-config-limit-deck-v3 =
    Когда вы учите колоду с подколодами, лимиты каждой подколоды устанавливают максимум карточек, которые будут выбраны из этой подколоды.
    Лимиты самой колоды влияют на общее количество показываемых карточек.
deck-config-limit-new-bound-by-reviews = Лимит просмотров влияет на лимит новых. Например, если лимит равен 200 и в очереди 190 повторяемых, то в очередь будет добавлено не более 10 новых. Если лимит повторяемых достигнут, то новые не будут показаны.
deck-config-limit-interday-bound-by-reviews = Лимит повторений влияет на карточки, перенесённые на другой день. Когда применяется лимит, перенесённые отбираются первыми, затем повторяемые и, наконец, новые.
deck-config-tab-description =
    - `Конфигурация`: Лимит действует на все колоды с этой конфигурацией.
    - `Эта колода`: Лимит только для этой колоды.
    - `Только сегодня`: Временный лимит для этой колоды.
deck-config-new-cards-ignore-review-limit = Лимит повторений не влияет на новые
deck-config-new-cards-ignore-review-limit-tooltip = По умолчанию  лимит повторений применяется к новым карточкам, и ни одна из новых не будет показана по достижении этого лимита. Если опция включена, то новые карточки будут показаны вне зависимости от лимита.
deck-config-apply-all-parent-limits = Лимиты начинаются сверху
deck-config-apply-all-parent-limits-tooltip =
    По умолчанию ежедневные лимиты колоды более высокого уровня не применяются, если вы изучаете ее подколоду.
    Если эта опция включена, лимиты будут начинаться с колоды верхнего уровня, что может быть полезно, если вы хотите изучать отдельные подколоды, одновременно применяя общий лимит на карты для дерева колоды.
deck-config-affects-entire-collection = Действует для всей коллекции

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Конфигурация
deck-config-deck-only = Эта колода
deck-config-today-only = Только сегодня

## New Cards section

deck-config-learning-steps = Шаги изучения
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Интервалы обычно задаются в минутах (например, `1m`) или днях (`2d`), но их можно также задавать в часах (`1h`) и секундах (`30s`).
deck-config-learning-steps-tooltip = Один или более интервалов, разделённых пробелами. Первый будет назначен, когда вы нажмёте `Снова` на новой карточке (по умолчанию 1 минута). Нажатие `Хорошо` назначит карточке следующий интервал (по умолчанию 10 минут). Когда карточка пройдёт все шаги, она станет повторяемой и появится в другой день. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip = Количество дней до показа карточки, если на последнем шаге изучения была нажата кнопка `Хорошо`.
deck-config-easy-interval-tooltip = Количество дней до показа карточки, если она была сразу же убрана из изучаемых кнопкой `Легко`.
deck-config-new-insertion-order = Порядок добавления
deck-config-new-insertion-order-tooltip =
    Управляет положением (номером и сроком) новых карточек в очереди.
    Карточки с меньшим номером будут показаны раньше. Изменение этой настройки автоматически обновит положение новых карточек.
deck-config-new-insertion-order-sequential = Последовательный (сначала старые)
deck-config-new-insertion-order-random = Случайный
deck-config-new-insertion-order-random-with-v3 =
    С планировщиком V3 лучше оставить последовательный и
    вместо этого настроить порядок отбора новых карточек.

## Lapses section

deck-config-relearning-steps = Шаги переучивания
deck-config-relearning-steps-tooltip = Ноль или более интервалов, разделённых пробелами. По умолчанию, когда вы нажмёте `Снова` на повторяемой карточке, она будет показана снова через 10 минут. Если интервалы не заданы, её срок изменится без перевода в переучиваемые. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip = Количество нажатий `Снова`, после которого повторяемая карточка помечается как приставучая. Такие карточки тратят ваше время, и их стоит переделать, удалить или подкрепить мнемоническим правилом.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `Только пометить`: пометить как приставучую (`leech`) и показать сообщение.
    `Исключить карточку`: пометить и исключить карточку до тех пор, пока вы её не вернёте.

## Burying section

deck-config-bury-title = Откладывание
deck-config-bury-new-siblings = Откладывать новые связанные до завтра
deck-config-bury-review-siblings = Откладывать повторяемые связанные до завтра
deck-config-bury-interday-learning-siblings = Откладывать связанные изучаемые, которые переносятся
deck-config-bury-new-tooltip = Будут ли другие `новые` карточки той же записи (например, обратные или смежные с пропусками) отложены до следующего дня.
deck-config-bury-review-tooltip = Будут ли другие `повторяемые` карточки той же записи отложены до следующего дня.
deck-config-bury-interday-learning-tooltip = Будут ли другие `изучаемые` карточки той же записи с интервалом больше 1 дня отложены до следующего дня.
deck-config-bury-priority-tooltip =
    Когда Anki отбирает карточки, сначала выбираются карточки на день, затем перенесённые, затем повторяемые и, наконец, новые карточки. Это влияет на как работает откладывание:
    - Если у вас включены все опции откладывания, то будет показана первая по списку связанная карточка. Например, повторяемая будет показана раньше новой.
    - Связанные карточки, которые ниже по списку, не влияют на те, что выше. Например, если отключить откладывание новых и изучить новую карточку, она не отложит перенесённые и повторяемые, и вы увидите как повторяемую, так и новую.

## Gather order and sort order of cards

deck-config-ordering-title = Порядок показа
deck-config-new-gather-priority = Порядок отбора новых
deck-config-new-gather-priority-tooltip-2 =
    `По колоде`: отбирает карточки из каждой колоды по их порядку, начиная сверху. Карточки из каждой колоды отбираются по возрастанию.
    Если достигнут дневной лимит выбранной колоды, отбор может остановиться до того, как все колоды будут пройдены. Этот вариант самый быстрый при работе с большими коллекциями и позволяет вам придавать больший вес верхним подколодам.
    
    `По возрастанию номеров`: отбирает карточки по возрастанию номера в очереди, и обычно старые добавляются первыми.
    
    `По убыванию номеров`: отбирает карты по убыванию номера в очереди, и обычно новые добавляются первыми.
    
    `Случайные записи`: отбирает карточки случайных записей. Если связанные откладываются, то это позволяет повторить все карточки записи во время учебного сеанса.
    
    `Случайные карточки`: отбирает карточки случайным образом.
deck-config-new-card-sort-order = Порядок новых
deck-config-new-card-sort-order-tooltip-2 =
    `По типу карточки`: Показывает карточки по порядку номера типа карточки. Если у вас отключено откладывание связанных, то это гарантирует, что все карточки вида Лицо→Оборот будут показаны раньше Оборот→Лицо.
    Это полезно для того, чтобы все карточки, созданные из одной записи, отображались в течение одного занятия, но не слишком близко друг к другу.
    
    `По порядку отбора`: Показывает карточки именно в том порядке, в котором они были отобраны. Если отключено откладывание связанных, то обычно все карточки одной записи будут отображаться друг за другом.
    
    `По типу карточки, затем случайный`: Аналогично `По типу карточки`, но перемешивает карточки каждого номера типа. Если вы используете `По возрастанию номеров` для отбора самых старых карточек, вы можете использовать эту настройку, чтобы увидеть эти карточки в случайном порядке, но при этом гарантировать, что карточки, созданные из одной записи, не окажутся слишком близко друг к другу.
    
    `Случайная запись, затем тип карточки`: Отбирает записи в случайном порядке, затем показывает все связанные карточки по порядку.
    
    `Случайный`: Полностью перетасовывает отобранные карточки.
deck-config-new-review-priority = Порядок новых (к повторяемым)
deck-config-new-review-priority-tooltip = Когда показывать новые карточки по отношению к повторяемым.
deck-config-interday-step-priority = Порядок перенесённых
deck-config-interday-step-priority-tooltip =
    Когда показывать изучаемые и переучиваемые, которые переносятся на следующий день.
    Лимит повторяемых сначала применяется к перенесённым изучаемым, после — к повторяемым. Этот параметр управляет порядком показа, но отбираются перенесённые изучаемые всегда первыми.
deck-config-review-sort-order = Порядок повторяемых
deck-config-review-sort-order-tooltip = По умолчанию первыми идут старые карточки. Если у вас накопилось много карточек, то первыми будут те, что в очереди дольше всего. Если просмотр накопившихся займёт несколько дней или вы хотите повторять карточки в порядке подколод, то другой порядок может подойти лучше.
deck-config-display-order-will-use-current-deck = Anki будет использовать порядок показа из колоды, которую вы выбрали для изучения, а не из её подколод.

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = По колоде
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = По колоде, потом случайный
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = По возрастанию № позиции
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = По убыванию № позиции
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = Случайные записи
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = Случайные карточки
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = По типу карточки, потом случайный
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = Случайная запись, затем тип карточки
# Sort the cards randomly.
deck-config-sort-order-random = Случайный
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = По типу карточки
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = По порядку отбора
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = Перемешать с повторяемыми
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = Показывать после повторяемых
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = Показывать до повторяемых
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = По сроку, потом случайный
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = По сроку, потом по колоде
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = По колоде, потом по сроку
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = По возрастанию интервалов
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = По убыванию интервалов
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = По возрастанию лёгкости
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = По убыванию лёгкости
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = По возрастанию сложности
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = По убыванию сложности
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = По возрастанию вспоминаемости
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = По убыванию вспоминаемости

## Timer section

deck-config-timer-title = Таймер
deck-config-maximum-answer-secs = Максимум секунд для ответа
deck-config-maximum-answer-secs-tooltip = Максимум секунд для одного ответа. Если время ответа больше этого значения (например, если вы отошли от компьютера), то записанным временем будет заданный максимум.
deck-config-show-answer-timer-tooltip = Показывать при изучении секундомер, который засекает, сколько времени уходит у вас на ответ.
deck-config-stop-timer-on-answer = Остановить таймер при ответе
deck-config-stop-timer-on-answer-tooltip =
    Останавливать ли экранный таймер при появлении ответа.
    Это не влияет на статистику.

## Auto Advance section

deck-config-seconds-to-show-question = Время отображения вопроса (секунды)
deck-config-seconds-to-show-question-tooltip-3 = Когда активирован автопросмотр, количество секунд ожидания перед применением действия вопроса. Установите 0, чтобы отключить.
deck-config-seconds-to-show-answer = Время отображения ответа (секунды)
deck-config-seconds-to-show-answer-tooltip-2 = Когда активирован автопросмотр, количество секунд ожидания перед применением действия ответа. Установите 0, чтобы отключить.
deck-config-question-action-show-answer = Показать ответ
deck-config-question-action-show-reminder = Показать напоминание
deck-config-question-action = Действие для вопроса
deck-config-question-action-tool-tip = Действие, которое необходимо выполнить после отображения вопроса и истечения установленного времени.
deck-config-answer-action = Действие для ответа
deck-config-answer-action-tooltip-2 = Действие, которое необходимо выполнить после того, как ответ показан и время истекло. Чтобы отключить, установите 0.
deck-config-wait-for-audio-tooltip-2 = Ожидать окончания звука, прежде чем автоматически применить действие вопроса или ответа.

## Audio section

deck-config-audio-title = Звук
deck-config-disable-autoplay = Не воспроизводить звук автоматически
deck-config-disable-autoplay-tooltip =
    Если включена, Anki не будет автоматически воспроизводить звук.
    Звук можно воспроизвести вручную нажав на иконку или на кнопку "Воспроизвести снова".
deck-config-skip-question-when-replaying = Пропускать вопрос при воспроизведении ответа
deck-config-always-include-question-audio-tooltip = Будет ли озвучиваться вопрос, если включено повторное произведение при просмотре ответа

## Advanced section

deck-config-advanced-title = Дополнительные
deck-config-maximum-interval-tooltip = Максимум дней до следующего показа повторяемых. По достижении этого лимита `Трудно`, `Хорошо` и `Легко` будут назначать одинаковые интервалы. Чем меньше число, тем больше будет ваша учебная нагрузка.
deck-config-starting-ease-tooltip = Множитель лёгкости для новых карточек. По умолчанию `Хорошо` увеличивает интервал повторения в 2,5 раза.
deck-config-easy-bonus-tooltip = Дополнительный множитель для интервала повторяемой карточки, когда вы нажимаете `Легко`.
deck-config-interval-modifier-tooltip =
    Этот множитель применяется ко всем повторяемым карточкам. С его помощью планировщик Anki можно сделать менее или более агрессивным.
    Ознакомьтесь с руководством до того, как изменять его.
deck-config-hard-interval-tooltip = Множитель для интервала повторяемой карточки при нажатии `Трудно`.
deck-config-new-interval-tooltip = Множитель для интервала повторяемой карточки при нажатии `Снова`.
deck-config-minimum-interval-tooltip = Минимальный интервал для повторяемой карточки при нажатии `Снова`.
deck-config-custom-scheduling = Особое планирование
deck-config-custom-scheduling-tooltip = Влияет на всю коллекцию. Используйте на свой страх и риск!

## Easy Days section.

deck-config-easy-days-title = Лёгкие Дни
deck-config-easy-days-monday = Понедельник
deck-config-easy-days-tuesday = Вторник
deck-config-easy-days-wednesday = Среда
deck-config-easy-days-thursday = Четверг
deck-config-easy-days-friday = Пятница
deck-config-easy-days-saturday = Суббота
deck-config-easy-days-sunday = Воскресенье
deck-config-easy-days-normal = Обычный
deck-config-easy-days-reduced = Сниженный
deck-config-easy-days-minimum = Минимальный
deck-config-easy-days-no-normal-days = Хотя бы один день должен быть '{ deck-config-easy-days-normal }'.
deck-config-easy-days-change = Существующие повторы не будут перенесены, если в настройках FSRS не включен параметр '{ deck-config-reschedule-cards-on-change }'.

## Adding/renaming

deck-config-add-group = Добавить конфигурацию
deck-config-name-prompt = Название
deck-config-rename-group = Переименовать конфигурацию
deck-config-clone-group = Клонировать конфигурацию

## Removing

deck-config-remove-group = Удалить конфигурацию
deck-config-will-require-full-sync = Запрошенное изменение потребует односторонней синхронизации. Если изменения вы вносили изменения  на другом устройстве и не синхронизировали их с этим, сделайте это перед тем, как продолжать.
deck-config-confirm-remove-name = Удалить { $name }?

## Other Buttons

deck-config-save-button = Сохранить
deck-config-save-to-all-subdecks = Сохранить во все подколоды
deck-config-save-and-optimize = Оптимизировать все конфигурации
deck-config-revert-button-tooltip = Сбросить параметр

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Обработка, как в Anki 2.1.41+
deck-config-description-new-handling-hint =
    Считает ввод разметкой Markdown и очищает HTML-ввод. Если включена, описание также будет показано на экране с поздравлением.
    Markdown будет показан как текст в Anki версий не выше 2.1.40.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    { $cards ->
        [one] Лимит материнской колоды — { $cards } карточка, и он заменит этот лимит.
        [few] Лимит материнской колоды — { $cards } карточки, и он заменит этот лимит.
        [many] Лимит материнской колоды — { $cards } карточек, и он заменит этот лимит.
       *[other] Лимит материнской колоды — { $cards } карточек, и он заменит этот лимит.
    }
deck-config-reviews-too-low =
    { $cards ->
        [one] При добавлении { $cards } новой карточки в день, лимит повторяемых должен быть не менее { $expected }.
        [few] При добавлении { $cards } новых карточек в день, лимит повторяемых должен быть не менее { $expected }.
        [many] При добавлении { $cards } новых карточек в день, лимит повторяемых должен быть не менее { $expected }.
       *[other] При добавлении { $cards } новых карточек в день, лимит повторяемых должен быть не менее { $expected }.
    }
deck-config-learning-step-above-graduating-interval = Интервал перевода не должен быть короче последнего шага изучаемых.
deck-config-good-above-easy = Интервал лёгких не должен быть короче интервала перевода.
deck-config-relearning-steps-above-minimum-interval = Минимальный интервал забытых не должен быть короче последнего шага переучиваемых.
deck-config-maximum-answer-secs-above-recommended = Anki планирует более эффективно, когда вопросы короткие.
deck-config-too-short-maximum-interval = Максимальный интервал не рекомендуется устанавливать менее 6 месяцев (180 дней).
deck-config-ignore-before-info =
    { $totalCards ->
        [one]
            (Примерно) { $included }/{ $amount } карточка
            будет использована для оптимизации параметра FSRS (Гибкого графика интервальных повторений).
        [few]
            (Примерно) { $included }/{ $amount } карточек
            будет использовано для оптимизации параметра FSRS (Гибкого графика интервальных повторений).
        [many]
            (Примерно) { $included }/{ $amount } карточек
            будет использовано для оптимизации параметра FSRS (Гибкого графика интервальных повторений).
       *[other]
            (Примерно) { $included }/{ $amount } карточки
            будет использовано для оптимизации параметра FSRS (Гибкого графика интервальных повторений).
    }

## Selecting a deck

deck-config-which-deck = Какую колоду вы выбираете?

## Messages related to the FSRS scheduler

deck-config-updating-cards = Обновление карт: { $current_cards_count }/{ $total_cards_count }...
deck-config-invalid-parameters = Указанные параметры FSRS недействительны. Оставьте поле для параметров пустым, чтобы использовать параметры по умолчанию.
deck-config-not-enough-history = Недостаточно повторений для выполнения данной операции.
deck-config-must-have-400-reviews =
    { $count ->
        [one] Найдено только { $count } повторение. Для выполнения этой операции у вас должно быть не менее 400 повторений.
        [few] Найдено только { $count } повторения. Для выполнения этой операции у вас должно быть не менее 400 повторений.
       *[many] Найдено только { $count } повторений. Для выполнения этой операции у вас должно быть не менее 400 повторений.
    }
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = Параметры FSRS
deck-config-compute-optimal-weights = Оптимизировать параметры FSRS
deck-config-optimize-button = Оптимизировать
# Indicates that a given function or label, provided via the "text" variable, operates slowly.
deck-config-slow-suffix = { $text } (медленно)
deck-config-compute-button = Вычислить
deck-config-ignore-before = Игнорировать карточки, повторенные до
deck-config-time-to-optimize = С момента последней оптимизации прошло некоторое время — рекомендуется воспользоваться кнопкой «Оптимизировать все конфигурации».
deck-config-evaluate-button = Оценить
deck-config-desired-retention = Желаемое усвоение
deck-config-historical-retention = Историческое усвоение
deck-config-smaller-is-better = Низкие значения указывают, что алгоритм хорошо адаптировался к вашей истории повторений.
deck-config-steps-too-large-for-fsrs = Когда включён FSRS, шаги длиннее 1 дня не рекомендуются.
deck-config-get-params = Получить параметры
deck-config-complete = { $num }% выполнено.
deck-config-iterations = Итерация { $count }...
deck-config-reschedule-cards-on-change = Перепланировать карточки при изменениях
deck-config-fsrs-tooltip =
    Затрагивает всю коллекцию.
    Free Spaced Repetition Scheduler (FSRS) - это альтернатива предыдущему алгоритму Anki, SuperMemo 2 (SM2).
    FSRS более точно определяет, когда вы забудете карточку и поможет вам запомнить больше материала за то же время. 
    Эта настройка затрагивает все конфигурации.
    Если вы ранее использовали версию FSRS для "особого планирования", пожалуйста, убедитесь, что вы удалили код из "особого планирования" перед включением этой настройки.
deck-config-desired-retention-tooltip =
    По умолчанию, повторения будут запланированы таким образом, чтобы вероятность вспомнить 
    карточку во время следующего повторения была 90%. Если вы увеличите это значение, Anki будет 
    показывать карточки чаще, чтобы увеличить вероятность того, что вы их вспомните. 
    Если вы уменьшите это значение, Anki будет показывать карточки реже и вероятность вспомнить их
    будет ниже. Будьте осторожны, когда меняете значение этого параметра - более высокие значения 
    значительно увеличат вашу нагрузку, а более низкие могут быть деморализующими т.к. вы будете 
    забывать много материала.
deck-config-desired-retention-tooltip2 = Значения рабочей нагрузки, указанные в подсказке, являются грубым приближением. Для большей точности используйте симулятор.
deck-config-historical-retention-tooltip =
    Когда часть вашей истории повторений отсутствует, FSRS необходимо заполнить пробелы. 
    По умолчанию FSRS предполагает, что когда вы делали эти старые повторения, вы помнили 90% материала.
    Если ваш старый уровень усвоения был значительно выше или ниже 90%, измените это значение, 
    чтобы FSRS мог более точно заполнить недостающие повторения.
    
    Ваша история повторений может быть неполной по двум причинам:
    1. Вы использовали опцию "Игнорировать карточки, повторенные до".
    2. Вы удалили информацию о повторениях, чтобы освободить место, или импортировали материал из 
    другой программы интервального повторения.
    
    Последнее случается довольно редко, поэтому, если вы не использовали первый вариант, вам, вероятно, 
    не нужно менять значение исторического усвоения.
deck-config-weights-tooltip2 = Параметры FSRS влияют на то, как планируются карты. Anki начнет работу с параметрами по умолчанию. Вы можете использовать опцию ниже, чтобы оптимизировать параметры для наилучшего соответствия вашим показателям в колодах с использованием этой конфигурации.
deck-config-reschedule-cards-on-change-tooltip =
    Затрагивает всю коллекцию и не сохраняется.
    
    Эта настройка определяет, будут ли изменены сроки карточек, когда вы включите FSRS или оптимизируете параметры. По умолчанию карточки не будут изменены: будущие повторения будут использовать новое планирование, но это не приведет к немедленному изменению вашей нагрузки. Если перепланирование включено, сроки карточек будут изменены.
deck-config-reschedule-cards-warning =
    В зависимости от значения желаемого усвоения, это может привести к тому, 
    что большое количество карточек станет подлежащими просмотру, поэтому 
    не рекомендуется использовать эту опцию при переходе с SM2. Используйте эту опцию 
    осторожно, так как она добавит информацию о повторении к каждой из ваших карточек
    и увеличит размер вашей коллекции.
deck-config-ignore-before-tooltip-2 =
    Если дата выбрана, то карточки, повторенные до указанной даты, не будут использованы для оптимизации параметров FSRS.
    Это может быть полезно, если вы импортировали чужие данные или изменили ваш метод выбора кнопок ответа.
deck-config-compute-optimal-weights-tooltip2 =
    Когда вы нажмете кнопку «Оптимизировать», FSRS проанализирует вашу историю повторений и подберёт параметры, оптимальные для вашей памяти и изучаемого материала. Если ваши колоды сильно различаются по сложности, то рекомендуется назначить им отдельные конфигурации, так как параметры для легких и трудных колод будут отличаться. 
    Не нужно часто оптимизировать параметры – достаточно одного раза в несколько месяцев.
    
    По умолчанию параметры будут оптимизированы на основе истории просмотров всех колод, использующих текущую конфигурацию. Вы можете дополнительно настроить поиск перед оптимизацией параметров, если вы хотите изменить, какие карты будут использоваться для оптимизации.
deck-config-please-save-your-changes-first = Пожалуйста, сначала сохраните изменения.
deck-config-workload-factor-change =
    Примерная рабочая нагрузка: { $factor }x
    (по сравнению с { $previousDR }% желаемого усвоения)
deck-config-workload-factor-unchanged = Чем выше это значение, тем чаще вам будут показываться карточки.
deck-config-desired-retention-too-low = Желаемый вами уровень усвоения очень низкий, что может привести к очень длительным интервалам.
deck-config-desired-retention-too-high = Желаемый вами уровень усвоения очень высок, что может привести к очень коротким интервалам.
deck-config-percent-of-reviews =
    { $reviews ->
        [one] { $pct }% от { $reviews } повторения
        [few] { $pct }% от { $reviews } повторений
       *[many] { $pct }% от { $reviews } повторений
    }
deck-config-percent-input = { $pct }%
# This message appears during FSRS parameter optimization.
deck-config-checking-for-improvement = Проверка на предмет улучшения...
deck-config-optimizing-preset = Оптимизация конфигурации { $current_count }/{ $total_count }...
deck-config-fsrs-must-be-enabled = Сначала необходимо включить FSRS.
deck-config-fsrs-params-optimal = В настоящее время параметры FSRS, вероятно, оптимальны.
deck-config-fsrs-params-no-reviews = Повторений не обнаружено. Проверьте, что этот пакетный профиль назначен всем колодам и подколодам, которые вы хотите оптимизировать, затем попробуйте еще раз.
deck-config-wait-for-audio = Ждать аудио
deck-config-show-reminder = Показать напоминание
deck-config-answer-again = Ответить «снова»
deck-config-answer-hard = Ответить «трудно»
deck-config-answer-good = Ответить «хорошо»
deck-config-days-to-simulate = Число дней для симуляции
deck-config-desired-retention-below-optimal = Ваше значение желаемого усвоения ниже оптимального. Рекомендуется увеличить его.
# Description of the y axis in the FSRS simulation
# diagram (Deck options -> FSRS) showing the total number of
# cards that can be recalled or retrieved on a specific date.
deck-config-fsrs-simulator-experimental = Симулятор FSRS (Гибкого графика интервальных повторений) (пробный)
deck-config-fsrs-simulate-desired-retention-experimental = FSRS симулятор желаемого удержания (экспериментальный)
deck-config-fsrs-simulate-save-preset = После оптимизации сохраните конфигурацию перед запуском симулятора.
deck-config-fsrs-desired-retention-help-me-decide-experimental = Помощь в принятии решения (экспериментальный)
deck-config-additional-new-cards-to-simulate = Дополнительные новые карточки для тестирования
deck-config-simulate = Протестировать
deck-config-clear-last-simulate = Удалить последнюю симуляцию
deck-config-fsrs-simulator-radio-count = Повторения
deck-config-advanced-settings = Расширенные настройки
deck-config-smooth-graph = Плавный график
deck-config-suspend-leeches = Исключить приставучии
deck-config-save-options-to-preset = Сохранить изменения в конфигурации
deck-config-save-options-to-preset-confirm = Перезаписать параметры в текущей конфигурации параметрами, которые в данный момент установлены в симуляторе?
# Radio button in the FSRS simulation diagram (Deck options -> FSRS) selecting
# to show the total number of cards that can be recalled or retrieved on a
# specific date.
deck-config-fsrs-simulator-radio-memorized = Выученo
deck-config-fsrs-simulator-radio-ratio = Соотношение время / запоминание
# $time here is pre-formatted e.g. "10 Seconds" 
deck-config-fsrs-simulator-ratio-tooltip = { $time } на запоминание карты

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.

# Checkbox
deck-config-health-check = Проверка работоспособности при оптимизации
# Message box showing the result of the health check
deck-config-fsrs-bad-fit-warning =
    FSRS сложно предсказать вашу память. Рекомендации:
    
    - Приостановите или переформулируйте приставучие.
    - Используйте кнопки ответов последовательно. Помните, что «Сложно» — это проходной балл, а не провальный.
    - Поймите, прежде чем запоминать.
    
    Если вы будете следовать этим рекомендациям, производительность, как правило, улучшится в течение следующих нескольких месяцев.
# Message box showing the result of the health check
deck-config-fsrs-good-fit = FSRS хорошо адаптирован к вашей памяти.

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-unable-to-determine-desired-retention = Не удалось вычислить минимальное рекомендуемое усвоение
deck-config-predicted-minimum-recommended-retention = Минимальное рекомендуемое усвоение: { $num }
deck-config-compute-minimum-recommended-retention = Минимальное рекомендуемое усвоение
deck-config-compute-optimal-retention-tooltip4 =
    Этот инструмент попытается найти желаемую величину усвоения, которая приведет к наибольшему усвоению материала за наименьшее время. Рассчитанное число может служить ориентиром при принятии решения о том, каким должно быть желаемое усвоение. Вы можете выбрать более высокое значение, если вы готовы потратить больше учебного времени на его достижение. Устанавливать желаемый уровень удержания ниже минимального не рекомендуется, так как это приведет к увеличению нагрузки 
    из-за большого количества забытых карточек.
deck-config-plotted-on-x-axis = (Отложено по оси X)
deck-config-a-100-day-interval =
    { $days ->
        [one] Интервал в 100 дней превратится в { $days } день.
        [few] Интервал в 100 дней превратится в { $days } дня.
       *[many] Интервал в 100 дней превратится в { $days } дней.
    }
deck-config-fsrs-simulator-y-axis-title-time = Повторяли в день
deck-config-fsrs-simulator-y-axis-title-count = Повторений в день
deck-config-fsrs-simulator-y-axis-title-memorized = Всего выучено
deck-config-bury-siblings = Откладывать связанные
deck-config-do-not-bury = Не откладывать связанные
deck-config-bury-if-new = Откладывать новые
deck-config-bury-if-new-or-review = Откладывать новые и повторяемые
deck-config-bury-if-new-review-or-interday = Откладывать новые, повторяемые и перенесённые
deck-config-bury-tooltip =
    Связанные — это карточки одной записи (прямая и обратная, карточки с пропусками в одном и тот же тесте).
    
    Когда опция отключена, несколько карточек одно записи могут быть показаны в один день. Если она включена, Anki будет *откладывать* связанные до следующего дня. Опция позволяет выбрать, что делать с остальными связанными после ответа на одну из них.
    
    При использовании планировщика V3 перенесённые на другой день тоже откладываются. Перенесённые — те, у которых шаг изучаемой больше одного дня.
deck-config-seconds-to-show-question-tooltip = Когда активирован автопросмотр, количество секунд ожидания перед раскрытием ответа. Установите 0, чтобы отключить.
deck-config-answer-action-tooltip = Действие, которое необходимо выполнить на текущей карточке перед автоматическим переходом к следующей.
deck-config-wait-for-audio-tooltip = Ожидать окончания звука, прежде чем автоматически показывать ответ или следующий вопрос.
deck-config-ignore-before-tooltip =
    Когда выбрана дата, повторения до указанной даты будут исключены из оптимизации и оценки параметров FSRS.
    Это может быть полезно, если вы импортировали чужие данные о повторениях или изменили ваши привычки использования кнопок ответа.
deck-config-compute-optimal-retention-tooltip =
    Этот инструмент предполагает, что вы начинаете с 0 карточек, и попытается рассчитать количество материала, которое вы сможете усвоить за данный промежуток времени.
    Расчетное усвоение будет во многом зависеть от ваших данных, и если оно значительно отличается от 0.9, это признак того, что время, которое вы выделяете каждый день, либо слишком мало, либо слишком большое для количества карточек, которые вы пытаетесь выучить. Это число может быть полезным в качестве справочного материала, но не рекомендуется копировать его в желаемое поле усвоения.
deck-config-health-check-tooltip1 = Если FSRS с трудом адаптируется к вашей памяти, появится предупреждение.
deck-config-health-check-tooltip2 = Проверка работоспособности выполняется только при использовании функции «Оптимизировать» (текущую конфигурацию).
deck-config-compute-optimal-retention = Вычислить минимальное рекомендуемое усвоение
deck-config-predicted-optimal-retention = Минимальное рекомендуемое усвоение: { $num }
deck-config-weights-tooltip =
    Параметры FSRS влияют на то, как планируются карточки. Изначально используются параметры
    по умолчанию. После того, как вы наберете более 1000 повторений, вы можете использовать 
    опцию ниже, чтобы найти параметры, которые наилучшим образом соответствуют 
    вашей истории повторений в колодах, использующих эту конфигурацию.
deck-config-compute-optimal-weights-tooltip =
    После того как вы сделали более 1000 повторений в Anki, вы можете воспользоваться кнопкой "Оптимизировать",
    чтобы проанализировать историю повторений и автоматически подобрать параметры, оптимальные для вашей
    памяти и изучаемого материала.
    Если у вас есть колоды, сильно различающиеся по сложности, рекомендуется сделать для них отдельные
    конфигурации, так как параметры для легких и трудных колод будут отличаться. Нет необходимости часто
    оптимизировать параметры - достаточно одного раза в несколько месяцев.
    
    По умолчанию параметры будут рассчитываться на основе истории повторений всех колод, использующих
    текущую конфигурацию. Вы можете дополнительно настроить поиск перед оптимизацией параметров, 
    если хотите изменить, какие карточки будут использоваться для оптимизации.
deck-config-compute-optimal-retention-tooltip2 =
    Данный инструмент предполагает, что вы начинаете с 0 выученных карточек и попытается найти значение 
    усвоения, которое поможет вам выучить как можно больше материала за наименьшее количество 
    времени. Полученное число можно использовать в качестве ориентира при принятии решения о том, 
    какое значение усвоения вы хотите установить. Вы можете выбрать более высокое значение, если вы 
    готовы посвятить больше времени обучению, чтобы запомнить больше.
    Устанавливать значение желаемого усвоения ниже минимального не рекомендуется, так как это приведет 
    к увеличению нагрузки без пользы.
deck-config-compute-optimal-retention-tooltip3 =
    Этот инструмент предполагает, что вы начинаете с 0 выученных карточек, и попытается найти желаемое значение усвоения, которое приведет к максимальному усвоению материала за наименьшее количество времени.
    Для точной имитации вашего процесса обучения эта функция требует не менее 400+ обзоров. Вычисленное число может служить ориентиром при принятии решения о том, какое значение установить для желаемого усвоения.
    Вы можете выбрать более высокое желаемое усвоение, если вы готовы пожертвовать большим временем на изучение ради большей скорости запоминания. Установка желаемого усвоения ниже минимального не рекомендуется, так как это приведет к более высокой рабочей нагрузке из-за высокой скорости забывания.
deck-config-seconds-to-show-question-tooltip-2 = Когда активирован автопросмотр, количество секунд ожидания перед раскрытием ответа. Установите 0, чтобы отключить.
deck-config-invalid-weights = Поле для параметров должно быть либо оставлено пустым, чтобы использовать значения по умолчанию, либо должно содержать 17 чисел, разделенных запятыми.
deck-config-fsrs-on-all-clients =
    Пожалуйста, убедитесь, что вы используете Anki(Mobile) 23.10+ или AnkiDroid 2.17+. 
    FSRS будет работать некорректно, если вы используете более старые версии Anki.
deck-config-optimize-all-tip = Вы можете оптимизировать все конфигурации сразу, воспользовавшись кнопкой из выпадающего списка рядом с "Сохранить".
