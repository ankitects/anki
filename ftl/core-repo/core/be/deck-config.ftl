### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    выкарыстоўваецца { $decks ->
        [one] { $decks } калодай
        [few] { $decks } калодамі
        [many] { $decks } калодамі
       *[other] { $decks } калод
    }
deck-config-default-name = Прадвызначаная
deck-config-title = Параметры калоды

## Daily limits section

deck-config-daily-limits = Дзённыя ліміты
deck-config-review-limit-tooltip =
    Максімальная колькасць картак на перагляд ў дзень,
    калі карткі гатовыя да перагляду.
deck-config-limit-new-bound-by-reviews =
    Ліміт пераглядаў уплывае на ліміт новых картак. Напрыклад, калі
    ваш ліміт пераглядаў зададзены на 200 і вас чакае 190 пераглядаў,
    будзе ўведзена максімум 10 новых картак. Калі ваш ліміт пераглядаў
    будзе дасягнуты, аніякія новыя карткі не будуць паказаны.
deck-config-tab-description =
    - `Набор налад`: Абмежаванне распаўсюджваецца на ўсе калоды, якія выкарыстоўваюць гэты набор налад.
    - `Гэта калода`: Абмежаванне, спецыфічнае для гэтай калоды.
    - `Толькі сёння`: Зрабіць часовую змену у абмежаванні гэтай калоды.
deck-config-new-cards-ignore-review-limit = Ліміт пераглядаў не ўплывае на новыя карткі
deck-config-affects-entire-collection = Уплывае на ўсю калекцыю.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Набор налад
deck-config-deck-only = Гэта калода
deck-config-today-only = Толькі сёння

## New Cards section

deck-config-learning-steps = Крокі вывучэння
deck-config-graduating-interval-tooltip =
    Колькасць дзён чакання да таго, каб паказваць картку зноў пасля націскання на
    кнопку «Добра» на апошнім навучальным кроку.
deck-config-new-insertion-order = Парадак устаўкі
deck-config-new-insertion-order-sequential = Паслядоўны (спачатку найстарэйшыя карткі)
deck-config-new-insertion-order-random = Выпадковы
deck-config-new-insertion-order-random-with-v3 =
    З планіроўшчыкам V3 лепей пакідаць гэты набор паслядоўным і
    замест гэтага дапасаваць парадак збірання новых картак.

## Lapses section

deck-config-relearning-steps = Крокі паўторнага вывучэння
deck-config-leech-threshold-tooltip =
    Колькасць націсканняў на «Зноў», патрэбная для таго, каб картка да перагляду
    была пазначана як прыліплая. Прыліплыя карткі — гэта тыя, што забіраюць
    шмат вашага часу. Такая пазнака сведчыць аб тым, што такія карткі патрабуюць
    перапісвання, выдалення або прыдумляння мнеманічнага правіла для іх запамінання.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `Толькі цэтлік`: Дадае цэтлік «прыліплай» да нататкі, і адлюстроўвае ўсплывальнае акно.
    
    `Прыпыніць картку`: У дадатак да надання нататцы цэтліка, хавае картку, пакуль
    яна не будзе ўзноўлена ўручную.

## Burying section

deck-config-bury-title = Адкладванне
deck-config-bury-new-siblings = Адкладваць новыя сястрынскія
deck-config-bury-review-siblings = Адкладваць сястрынскія да перагляду
deck-config-bury-interday-learning-siblings = Адкладваць сястрынскія карткі на вывучэнні, на іншыя дні

## Gather order and sort order of cards

deck-config-ordering-title = Парадак паказвання
deck-config-new-gather-priority = Парадак збірання новых картак
deck-config-new-card-sort-order = Парадак сартавання новых картак
deck-config-new-review-priority = Парадак новых/пераглядаў
deck-config-new-review-priority-tooltip = Калі паказваць новыя карткі адносна картак да перагляду.
deck-config-interday-step-priority = Парадак вывучэння/пераглядаў між дзён
deck-config-review-sort-order = Парадак сартавання пераглядаў

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = Калода
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = Калода, а потым выпадковыя нататкі
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = Па ўзрастанні пазіцыі
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = Па ўбыванні пазіцыі
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = Выпадковыя нататкі
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = Выпадковыя карткі
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = Па шаблоне карткі, потым выпадковы
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = Выпадковая нататка, потым тып карткі
# Sort the cards randomly.
deck-config-sort-order-random = Выпадковы
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = Па шаблоне каркі, потым па парадку збірання
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = У парадку збірання
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = Змешваць з пераглядамі
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = Паказваць пасля пераглядаў
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = Паказваць перад пераглядамі
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = Па тэрміне, потым выпадковы
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = Па тэрміне, потым па калодзе
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = Па калодзе, потым па тэрміне
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = Па ўзрастанні інтэрвалаў
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = Па ўбыванні інтэрвалаў
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = Па ўзрастанні лёгкасці
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = Па ўбыванні лёгкасці
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = Спачатку простыя карткі
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = Спачатку складаныя карткі

## Timer section

deck-config-timer-title = Таймер
deck-config-maximum-answer-secs = Максімум секунд для адказу

## Auto Advance section

deck-config-question-action-show-answer = Паказаць адказ
deck-config-question-action-show-reminder = Паказаць напамін
deck-config-answer-action = Дзеянне пасля адказу

## Audio section

deck-config-audio-title = Аўдыя
deck-config-disable-autoplay = Не прайграваць аўдыя аўтаматычна
deck-config-skip-question-when-replaying = Прапускаць пытанне пры паўторным прайграванні адказу

## Advanced section

deck-config-advanced-title = Пашыраныя налады
deck-config-easy-bonus-tooltip =
    Дадатковы множнік, які ўжываецца да інтэрвалу перагляду картак, калі вы ім
    ставіце ацэнку «Лёгка».
deck-config-hard-interval-tooltip = Множнік, які ўжываецца да інтэрвалу перагляду картак пры адказе «Цяжка».
deck-config-new-interval-tooltip = Множнік, які ўжываецца да інтэрвалу перагляду картак пры адказе «Зноў».
deck-config-minimum-interval-tooltip = Мінімальны інтэрвал, які надаецца картцы да перагляду пасля адказу «Зноў».
deck-config-custom-scheduling = Уласны расклад
deck-config-custom-scheduling-tooltip = Уплывае на ўсю калекцыю. Выкарыстоўвайце на ўласную рызыку!

## Easy Days section.

deck-config-easy-days-title = Лёгкія дні
deck-config-easy-days-monday = пн
deck-config-easy-days-tuesday = аў
deck-config-easy-days-wednesday = ср
deck-config-easy-days-thursday = чц
deck-config-easy-days-friday = пт
deck-config-easy-days-saturday = сб
deck-config-easy-days-sunday = нд
deck-config-easy-days-normal = Звычайна
deck-config-easy-days-reduced = Менш
deck-config-easy-days-minimum = Мінімальна

## Adding/renaming

deck-config-add-group = Дадаць набор налад
deck-config-name-prompt = Назва
deck-config-rename-group = Перайменаваць набор налад
deck-config-clone-group = Кланіраваць набор налад

## Removing

deck-config-remove-group = Выдаліць набор налад
deck-config-confirm-remove-name = Выдаліць { $name }?

## Other Buttons

deck-config-save-button = Захаваць
deck-config-save-to-all-subdecks = Захаваць ва ўсе падкалоды
deck-config-revert-button-tooltip = Аднавіць гэту наладу да прадвызначанага значэння

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Апрацоўка, як у Anki 2.1.41+

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    Бацькоўская калода мае абмежаванне ў { $cards ->
        [one] { $cards } картку
        [few] { $cards } карткі
        [many] { $cards } картак
       *[other] { $cards } картак
    }, што перазапіша гэта абмежаванне.
deck-config-learning-step-above-graduating-interval = Інтэрвал да выпуску павінен быць прынамсі такім жа доўгім, як і ваш фінальны навучальны крок.
deck-config-good-above-easy = Інтэрвал для лёгкіх павінен быць прынамсі такім жа доўгім, як і інтэрвал да выпуску.

## Selecting a deck

deck-config-which-deck = Якую калоду вы хочаце?

## Messages related to the FSRS scheduler

deck-config-unable-to-determine-desired-retention = Немагчыма вызначыць мінімальнае рэкамендаванае запамінанне.
deck-config-compute-minimum-recommended-retention = Мінімальнае рэкамендаванае запамінанне
# Indicates that a given function or label, provided via the "text" variable, operates slowly.
deck-config-slow-suffix = { $text } (павольна)
deck-config-desired-retention = Жаданае запамінанне
deck-config-historical-retention = Гістарычнае запамінанне
deck-config-predicted-minimum-recommended-retention = Мінімальнае рэкамендаванае запамінанне: { $num }
deck-config-complete = { $num }% выканана.
deck-config-iterations = Ітэрацыя: { $count }...
deck-config-please-save-your-changes-first = Спачатку захавайце свае змены.
deck-config-percent-input = { $pct }%
deck-config-wait-for-audio = Чакаць аўдыя
deck-config-show-reminder = Паказваць напамін
deck-config-answer-again = Адказаць зноў
deck-config-answer-hard = Адказаць «цяжка»
deck-config-answer-good = Адказаць «добра»
deck-config-simulate = Сімуляваць
deck-config-clear-last-simulate = Ачысціць апошнюю сімуляцыю
deck-config-fsrs-simulator-radio-count = На перагляд
deck-config-advanced-settings = Пашыраныя налады
# Radio button in the FSRS simulation diagram (Deck options -> FSRS) selecting
# to show the total number of cards that can be recalled or retrieved on a
# specific date.
deck-config-fsrs-simulator-radio-memorized = Завучана

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.


## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-bury-siblings = Адкладваць сястрынскія
deck-config-do-not-bury = Не адкладваць сястрынскія
deck-config-bury-if-new = Адкладваць новае
deck-config-bury-if-new-or-review = Адкладваць новыя або на пераглядзе
deck-config-compute-optimal-retention = Падлічыць мінімальнае рэкамендаванае запамінанне
deck-config-predicted-optimal-retention = Мінімальнае рэкамендаванае запамінанне: { $num }
