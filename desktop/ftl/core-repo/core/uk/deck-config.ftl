### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    { $decks ->
        [one] Використовується{ $decks } колодою
        [few] Використовується{ $decks } колодами
        [many] Використовується{ $decks } колодами
       *[other] Використовується{ $decks } колодами
    }
deck-config-default-name = Типовий
deck-config-title = Налаштування колоди

## Daily limits section

deck-config-daily-limits = Щоденні обмеження
deck-config-new-limit-tooltip =
    Максимальна щоденна кількість нових карток, якщо нові картки існують.
    Оскільки новий матеріал збільшує короткострокове навантаження на пригадування,
    цей параметр зазвичай повинен бути хоча б вдесятеро меншим за межу пригадування.
deck-config-review-limit-tooltip =
    Максимальна щоденна кількість пригадувальних карток,
    якщо пригадувальні картки існують.
deck-config-limit-deck-v3 =
    Вивчаючи колоду, що містить вкладені колоди, обмеження вкладених колод
    визначають максимальну кількість обраних з них карток.
    Обмеження колоди вивчення визначають загальну кількість карток обраних для показу.
deck-config-limit-new-bound-by-reviews =
    Межа пригадування застосовується для нових меж. До прикладу, якщо Ваша межа пригадування
    складає 200, і доступно 190 пригадувальних карток, тоді буде показано лише 10 нових карток.
    Нові картки не будуть показуватися при досягненні межі пригадування.
deck-config-limit-interday-bound-by-reviews =
    Межа пригадування також впливає на картки для вивчення наступного дня.
    При застосуванні межі, картки для вивчення наступного дня показуються першими,
    перед картками для пригадування.
deck-config-tab-description =
    - `Конфігурація`: Застосувати обмеження для всіх колод з цією конфігурацією.
    - `Ця колода`: Застосувати обмеження до цієї колоди.
    - `Лише сьогодні`: Застосувати тимчасові обмеження для цієї колоди.
deck-config-new-cards-ignore-review-limit = Межа пригадування не застосовується для нових карток
deck-config-new-cards-ignore-review-limit-tooltip =
    Типово, межа пригадування застосовується і для нових карток: нові картки
    не будуть показуватися, коли досягнуто межі. Якщо цей параметр ввімкнено,
    нові картки будуть показуватися незалежно від досягнення межі.
deck-config-apply-all-parent-limits = Накладати обмеження верхнього рівня
deck-config-apply-all-parent-limits-tooltip =
    Типово, обмеження накладаються на обрану колоду. Ввімкнення цієї опції застосує накладання
    обмежень починаючи з колоди верхнього рівня, що може буде корисним, якщо Ви вивчаєте
    окремі вкладені колоди, використовуючи загальне обмеження кількості карток/день.
deck-config-affects-entire-collection = Впливає на всю колекцію.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Конфігурація
deck-config-deck-only = Ця колода
deck-config-today-only = Лише сьогодні

## New Cards section

deck-config-learning-steps = Кроки вивчення
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Затримки типово вказуються в хвилинах (напр. `1m`) чи днях (напр. `2d`), однак години (напр. `1h`) та секунди (напр. `30s`) також підтримуються.
deck-config-learning-steps-tooltip =
    Одна або декілька затримок, які відокремлюються пробілами. 
    Першу затримку буде застосовано коли ви натиснете на кнопку 
    `Знову` нової картки, і її типове значення 1 хвилина.
    Натиснення на кнопку `Добре` дозволить перейти до наступного
    кроку, на якому типова затримка складає 10 хвилин.
    Щойно буде пройдено всі кроки, картка стане пригадувальною,
    і з'явиться іншого дня. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip =
    Кількість днів очікування на появу картки після натиснення кнопки `Добре`
    на останньому кроці навчання.
deck-config-easy-interval-tooltip =
    Кількість днів очікування на появу картки. Застосовується негайно для видалення
    картки з навчання після натиснення кнопки `Легко`.
deck-config-new-insertion-order = Порядок вставки
deck-config-new-insertion-order-tooltip =
    Контролює позицію (№ пригадування), яка встановлюється для нових карток при їх додавання.
    Картки, які мають менший номер пригадування показуються швидше під час навчання. Зміна
    цього параметру автоматично оновити поточну позицію нових карток.
deck-config-new-insertion-order-sequential = Послідовний (спочатку найстаріші)
deck-config-new-insertion-order-random = Випадковий
deck-config-new-insertion-order-random-with-v3 =
    Для планувальника версії 3, краще залишити цей набір послідовним,
    а натомість налаштувати порядок збирання для нових карток.

## Lapses section

deck-config-relearning-steps = Кроки перенавчання
deck-config-relearning-steps-tooltip =
    Нуль або більше інтервалів, відокремлених пробілами. Типово, при натисканні
    кнопки `Знову` при пригадуванні картки, вона з'явиться знову через 10 хвилин.
    Якщо інтервали не вказано, тоді зміниться інтервал показу картки, минаючи
    процес перенавчання. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip =
    Кількість необхідних натискань кнопки `Знову` для пригадувальної картки
    перед тим, як вона стане приставучою. Приставучі картки займають багато
    вашого часу, і коли картка стає приставучою, краще її переписати, видалити
    або ж подумати про асоціації, щоб запам'ятати її.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `Лише позначити`: додати до картки мітку "Приставуча" та показати
    спливне вікно.
    
    `Призупинити картку`: На додачу до мітки, сховати картку, допоки її не
    буде вручну поновлено.

## Burying section

deck-config-bury-title = Відкладення
deck-config-bury-new-siblings = Відкладати нові сестринські картки
deck-config-bury-review-siblings = Відкладати пригадувальні сестринські картки
deck-config-bury-interday-learning-siblings = Відкладати сестринські картки, які вивчаються в різні дні
deck-config-bury-new-tooltip =
    Чи `нові` картки тієї ж самої нотатки (напр. обернені чи картки з закритим текстом)
    буде затримано до наступного дня.
deck-config-bury-review-tooltip = Чи `пригадувальні` картки тієї ж самої нотатки буде затримано до наступного дня.
deck-config-bury-interday-learning-tooltip =
    Чи `навчальні` картки тієї ж самої нотатки з інтервалом понад 1 день
    буде затримано до наступного дня.
deck-config-bury-priority-tooltip =
    Коли Anki збирає картки, вона спершу збирає навчальні картки поточного дня, потім
    навчальні картки наступного дня, далі пригадувальні, і насамкінець - нові. Це впливає
    на те, як працює відкладання:
    
    - якщо включено всі налаштування приховування, тоді першими буде показано
    ті сестринські картки, які з'являються якнайшвидше у цьому списку. Наприклад,
    пригадувальна картка з'явиться перед новою карткою;
    - сестринські картки, які знаходяться далі у списку не можуть відкладати ті
    типи карток, які з'являються швидше. Наприклад, якщо Ви заборонили відкладання
    нових карток, і вивчаєте нову картку, вона не відкладатиме жодної навчальної картки
    того ж дня чи пригадувальної картки, а отже ви можете бачити як пригадувальну
    так і нову сестринські картки в межах однієї сесії.

## Gather order and sort order of cards

deck-config-ordering-title = Порядок показу
deck-config-new-gather-priority = Порядок збирання нових карток
deck-config-new-gather-priority-tooltip-2 =
    `Колода`: збирає картки з кожної колоди по порядку, починаючи з верхньої.
    Картки кожної колоди збираються у висхідному порядку. Якщо досягнуто
    щоденної межі для обраної колоди, процес збирання може закінчитися 
    до того, як буде перевірено усі колоди. Такий порядок є швидшим для великих
    колекцій та дозволяє встановити пріоритет для колод, які є ближчими до верху.
    
    `За зростанням`: збирає картки у порядку зростання (за № пригадування), 
    зазвичай «найстаріша буде першою».
    
    `За спаданням`: збирає картки у порядку спадання (за № пригадування), 
    зазвичай «найновіша буде першою».
    
    `За випадковими нотатки`: збирає картки з випадково обраних нотаток. Якщо
    відкладання сестринських карток відключено, тоді в межах сесії буде видно всі 
    картки (напр. як зворотну -> передню картку так і передню -> зворотну картку).
    
    `За випадковими картками`: збирає картки абсолютно випадковим чином.
deck-config-new-card-sort-order = Порядок сортування нових карток
deck-config-new-card-sort-order-tooltip-2 =
    `За типом картки`: Показувати картки за номером типу картки. Якщо приховування
    сестринських карток відключено, це налаштування показуватиме всі картки 
    "передня->зворотна" до того як буде показано картки "зворотна->передня". Це
    налаштування є корисним, якщо Ви хочете показувати всі картки однієї нотатки в
    межах однієї сесії, однак не надто близько одна до одної.
    
    `За порядком збору`: Показувати картки саме так, як вони були зібрані. Якщо 
    відключити приховування сестринських карток, тоді всі картки однієї нотатки
    показуватимуться одна за одною.
    
    `За типом картки, далі випадково`: Схоже до `За типом картки`, однак перемішує
    картки кожного номеру типу картки. Якщо Ви збираєте картки `За зростанням`, то
    можете використовувати даний параметр, щоб бачити ці картки випадково, однак
    залишатися впевненими що картки однієї нотатки не з'являтимуться надто близько.
    
    `За випадковою нотаткою, далі за типом картки`: Обирає нотатки випадково, а
    тоді показує всі сестринські картки за порядком.
    
    `Випадково`: Повністю перемішує зібрані картки.
deck-config-new-review-priority = Порядок нових/пригадувальних
deck-config-new-review-priority-tooltip = Коли показувати нові картки, відносно пригадувальних.
deck-config-interday-step-priority = Порядок навчальних/пригадувальних для наступного дня
deck-config-interday-step-priority-tooltip =
    Коли показувати навчальні/пригадувальні картки які переносяться на наступний день.
    
    Межа пригадування завжди застосовується спершу до навчальних карток, а тоді
    до пригадувальних. Цей параметр керує порядком показу зібраних карток, однак картки
    які переносяться на наступний день завжди збираються першими.
deck-config-review-sort-order = Порядок сортування пригадувальних карток
deck-config-review-sort-order-tooltip =
    Типовий порядок сортування надає перевагу карткам, які найдовше 
    перебували в черзі очікування, тому, з переліку пригадувальних карток.
    першими з'являтимуться ті, які перебувають в черзі найдовше. Якщо Ваш
    перелік пригадування є настільки великим, що його очищення може 
    зайняти декілька днів, або Ви хочете бачити картки з вкладених колод,
    скористайтеся іншим порядком сортування.
deck-config-display-order-will-use-current-deck =
    Anki використовує порядок показу, зазначений для колоди,
    яку Ви вивчаєте, опускаючи налаштування вкладених колод

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = За колодою
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = За колодою, потім за випадковими нотатками
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = За зростанням
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = За спаданням
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = За випадковими нотатками
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = За випадковими картками
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = За типом картки, далі випадково
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = За випадковою нотаткою, далі за типом картки
# Sort the cards randomly.
deck-config-sort-order-random = Випадково
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = За типом картки
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = За порядком збору
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = Перемішати з пригадувальними
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = Показувати після пригадувальних
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = Показувати перед пригадувальними
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = За датою пригадування, далі - випадково
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = За датою пригадування, далі - за колодою
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = За колодою. далі - за датою пригадування
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = За зростанням інтервалів
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = За спаданням інтервалів
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = За зростанням легкості
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = За спаданням легкості
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = За зростанням складності
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = За спаданням складності
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = За легкістю пригадування
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = За складністю пригадування

## Timer section

deck-config-timer-title = Зворотній відлік
deck-config-maximum-answer-secs = Максимум секунд для відповіді
deck-config-maximum-answer-secs-tooltip =
    Максимальна кількість секунд, яку буде записано як час пригадування.
    Якщо Ваша відповідь займає понад цей час (напр. Ви відволіклись від
    екрану), то час на відповідь буде записано як вказане граничне значення.
deck-config-show-answer-timer-tooltip =
    На екрані пригадування, показувати таймер, який рахує кількість секунд
    затрачених на пригадування кожної картки.
deck-config-stop-timer-on-answer = Зупиняти таймер при показі відповіді
deck-config-stop-timer-on-answer-tooltip =
    Чи зупиняти таймер при показі відповіді.
    Не впливає на статистику.

## Auto Advance section

deck-config-seconds-to-show-question = Кількість секунд на показ запитання
deck-config-seconds-to-show-question-tooltip-3 = Кількість секунд, перед застосуванням дії при запитанні, коли включено автоперехід. 0 вимикає цю функціональність.
deck-config-seconds-to-show-answer = Кількість секунд на показ відповіді
deck-config-seconds-to-show-answer-tooltip-2 = Кількість секунд, перед застосуванням дії при відповіді, коли включено автоперехід. 0 вимикає цю функціональність.
deck-config-question-action-show-answer = Показати відповідь
deck-config-question-action-show-reminder = Показати нагадування
deck-config-question-action = При запитанні
deck-config-question-action-tool-tip = Дія, яка виконується після показу запитання та завершенні таймера.
deck-config-answer-action = При відповіді
deck-config-answer-action-tooltip-2 = Дія, яка виконується після показу відповіді та завершенні таймера.
deck-config-wait-for-audio-tooltip-2 = Очікувати на завершення аудіо перед автоматичним показом відповіді або запитання.

## Audio section

deck-config-audio-title = Аудіо
deck-config-disable-autoplay = Не відтворювати аудіо автоматично
deck-config-disable-autoplay-tooltip =
    Коли ввімкнено, Anki не буде відтворювати аудіо автоматично.
    Його можна відтворити натиснувши на іконку аудіо або використавши дію повторного відтворення.
deck-config-skip-question-when-replaying = Пропускати запитання при повторі відповіді
deck-config-always-include-question-audio-tooltip =
    Чи слід включати аудіо із запитання коли використовується дії «Повторити»
    при показі сторони з відповіддю.

## Advanced section

deck-config-advanced-title = Розширені
deck-config-maximum-interval-tooltip =
    Гранична кількість днів очікування на пригадування. Коли пригадувальна
    картка досягне цього значення, `Тяжко`, `Добре` і `Легко` встановлюватимуть
    ту ж саму затримку. Зменшення значення збільшить Ваше навантаження.
deck-config-starting-ease-tooltip =
    Коефіцієнт легкості для нових карток. Типово, кнопка `Добре` щойно вивченої
    картки встановлює наступне пригадування через 2.5 інтервали поточної затримки.
deck-config-easy-bonus-tooltip =
    Додатковий коефіцієнт який застосовується до інтервалу пригадувальної
    картки, коли Ви оцінили її як `Легко`.
deck-config-interval-modifier-tooltip =
    Цей множник застосовується до всіх пригадувань, і невеликі зміни можуть зробити
    планування у Anki більш консервативними чи агресивними. Зверніться до
    довідника перед зміною цього параметру.
deck-config-hard-interval-tooltip = Коефіцієнт, який застосовується до пригадувальної картки при відповіді `Тяжко`.
deck-config-new-interval-tooltip = Коефіцієнт, який застосовується до пригадувальної картки, при відповіді `Знову`.
deck-config-minimum-interval-tooltip = Мінімальний початковий інтервал після відповіді «Знову».
deck-config-custom-scheduling = Індивідуальне планування
deck-config-custom-scheduling-tooltip = Впливає на всю колекцію. Використовуйте на свій страх і ризик!

## Easy Days section.

deck-config-easy-days-title = Легкі дні
deck-config-easy-days-monday = Понеділок
deck-config-easy-days-tuesday = Вівторок
deck-config-easy-days-wednesday = Середа
deck-config-easy-days-thursday = Четвер
deck-config-easy-days-friday = П'ятниця
deck-config-easy-days-saturday = Субота
deck-config-easy-days-sunday = Неділя
deck-config-easy-days-normal = Звичайний
deck-config-easy-days-reduced = Скорочений
deck-config-easy-days-minimum = Мінімум
deck-config-easy-days-no-normal-days = '{ deck-config-easy-days-normal }' повинен бути хоча б одним днем
deck-config-easy-days-change = Поточні пригадування не буде переплановано допоки у налаштуваннях ВПІП увімкнено '{ deck-config-reschedule-cards-on-change }'

## Adding/renaming

deck-config-add-group = Додати конфігурацію
deck-config-name-prompt = Назва
deck-config-rename-group = Перейменувати конфігурацію
deck-config-clone-group = Дублювати конфігурацію

## Removing

deck-config-remove-group = Видалити конфігурацію
deck-config-will-require-full-sync =
    Ця зміна вимагатиме односторонньої синхронізації. Якщо ви зробили якісь зміни
    на іншому пристрої і ще не синхронізували їх, будь ласка, зробіть це
    перед тим як продовжувати.
deck-config-confirm-remove-name = Прибрати { $name }?

## Other Buttons

deck-config-save-button = Зберегти
deck-config-save-to-all-subdecks = Зберегти до усіх підколод
deck-config-save-and-optimize = Оптимізувати усе
deck-config-revert-button-tooltip = Повернути цей параметр до стандартного значення.

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Робота з Anki 2.1.41 та вище
deck-config-description-new-handling-hint =
    Вважає введені дані за розмітку markdown та очищає HTML формат. При
    увімкненні, опис також з'являтиметься на екрані налаштувань.
    Markdown відображається як текст у Anki 2.1.40 та попередніх версіях.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    { $cards ->
        [one] Граничне значення материнської колода, яке складає { $cards } картку, перезапише дане значення.
        [few] Граничне значення материнської колода, яке складає { $cards } картки, перезапише дане значення.
       *[many] Граничне значення материнської колода, яке складає { $cards } карток, перезапише дане значення.
    }
deck-config-reviews-too-low =
    { $cards ->
        [one] Додаючи щодня { $card } нову картку, межа пригадування складатиме щонайменше { $expected }.
        [few] Додаючи щодня { $card } нову картки, межа пригадування складатиме щонайменше { $expected }.
       *[many] Додаючи щодня { $card } нову карток, межа пригадування складатиме щонайменше { $expected }.
    }
deck-config-learning-step-above-graduating-interval = Градуйований інтервал має бути щонайменше таким довгим, як і останній крок навчання.
deck-config-good-above-easy = Інтервал легкості має бути щонайменше таким же, як і градуйований інтервал.
deck-config-relearning-steps-above-minimum-interval = Інтервал найменшого кола має бути щонайменше таким же, як і кінцевий крок перенавчання.
deck-config-maximum-answer-secs-above-recommended = Anki може ефективніше планувати пригадування, якщо запитання є короткими.
deck-config-too-short-maximum-interval = Не радимо встановлювати значення максимального інтервалу менше 6 місяців.
deck-config-ignore-before-info = Буде використано приблизно { $included }/{ $totalCards } карток для оптимізації параметрів ВПІП.

## Selecting a deck

deck-config-which-deck = Для якої колоди показати налаштування?

## Messages related to the FSRS scheduler

deck-config-updating-cards = Оновлюю картки: { $current_cards_count } / { $total_cards_count }...
deck-config-invalid-parameters = Вказано неправильні параметри ВПІП. Для типових значень залиште поля порожніми.
deck-config-not-enough-history = Історія пригадувань недостатня для виконання операції.
deck-config-must-have-400-reviews =
    { $count ->
        [one] Знайдено лише { $count } пригадувальну картку. Для цієї операції потрібно принаймні 400 карток.
        [few] Знайдено лише { $count } пригадувальні картки. Для цієї операції потрібно принаймні 400 карток.
       *[many] Знайдено лише { $count } пригадувальних карток. Для цієї операції потрібно принаймні 400 карток.
    }
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = Параметри ВПІП
deck-config-compute-optimal-weights = Оптимізувати параметри ВПІП
deck-config-optimize-button = Оптимізувати поточну конфігурацію
# Indicates that a given function or label, provided via the "text" variable, operates slowly.
deck-config-slow-suffix = { $text } (повільно)
deck-config-compute-button = Обчислити
deck-config-ignore-before = Ігнорувати попередні пригадування
deck-config-time-to-optimize = Це було так давно - варто натиснути кнопку "Оптимізувати усе".
deck-config-evaluate-button = Оцінити
deck-config-desired-retention = Бажана затримка
deck-config-historical-retention = Історична затримка
deck-config-smaller-is-better = Менші числа вказують на кращий успіх в історії пригадувань.
deck-config-steps-too-large-for-fsrs = Не варто вказувати кроки для 1 та наступних днів, коли увімкнено вільного планувальника РП.
deck-config-get-params = Отримати параметри
deck-config-complete = { $num }% завершено.
deck-config-iterations = Ітерація: { $count }...
deck-config-reschedule-cards-on-change = Переплановувати картки при змінах
deck-config-fsrs-tooltip =
    Впливає на всю колекцію.
    
    Вільний планувальник інтервального  пригадування (ВПІП) є альтернативою SuperMemo 2 (SM2) - старому планувальнику Anki.
    Точніше визначаючи момент забування, він допомагає запам'ятати більше матеріалу
    у той самий проміжок часу. Цей параметр застосовується до всіх конфігурацій колод.
    
    Перед тим, як включати цю опцію, переконайтеся, що секція індивідуального планування є порожньою,
    якщо Ви раніше користувались версією 'індивідуального планування' ВПІП.
deck-config-desired-retention-tooltip =
    Типове значення 0.9 планує картки так, щоб ви з 90% ймовірністю запам'ятали їх, коли
    прийде час пригадування. При збільшенні значення, Anki показуватиме картки частіше
    щоб зросли шанси запам'ятати їх. При зменшенні значення, Anki показуватиме картки
    рідше, і Ви будете забувати більшість з них. Будьте консервативним, змінюючи цей параметр, -
    більші значення збільшуватимуть навантаження, а менші можуть знеохотити коли Ви забуваєте
    багато матеріалу.
deck-config-desired-retention-tooltip2 = Значення навантаження у підказці є грубим наближенням. Отримати точніші значення можна за допомогою симулятора.
deck-config-historical-retention-tooltip =
    За відсутності окремих записів у історії пригадувань, ВПІП повинен їх заповнити. Типово,
    він припускає, що у старих пригадуваннях запам'яталось 90% матеріалу. Якщо старе значення затримки
    були значно вищими чи нижчими за 90%, зі зміною цього параметру ВПІП краще апроксимує
    відсутні пригадування.
    
    Історія пригадувань може бути неповною з двох причин:
    1. Було увімкнено опцію 'ігнорувати попередні пригадування';
    2. Ви видалили записи про пригадування щоб звільнити місце або імпортували матеріал з іншої програми,
    яка використовує планувальник розподіленого пригадування.
    
    Останнє є малоймовірним, тому цей параметр не слід змінювати, якщо опція була вимкнутою.
deck-config-weights-tooltip2 =
    Параметри ВПІП впливають на планування для карток. Anki запускається з типовими параметрами. Ви можете використовувати
    опцію нижче, для оптимізації параметрів вашої продуктивності в колодах, використовуючи цю конфігурацію.
deck-config-reschedule-cards-on-change-tooltip =
    Впливає на всю колекцію і не зберігається.
    
    Ця опція контролює чи дати очікуваного пригадування карток зміняться при увімкненні ВПІП або
    оптимізації параметрів. Типово, розклад не змінюється; майбутні пригадування будуть використовувати
    новий графік, однак не буде жодних миттєвих змін у поточному навантаженні. Дати очікуваного
    пригадування зміняться, якщо опція увімкнена.
deck-config-reschedule-cards-warning =
    Залежно від бажаної затримки, результатом може бути поява великої кількості карток
    для пригадування, тому не слід використовувати при першому переході з SM2.
    
    Вживайте цю опцію обережно, адже вона додасть запис про пригадування 
    до кожної картки, та збільшить розмір колекції.
deck-config-ignore-before-tooltip-2 =
    Картки, пригадані до вказаної дати проігноруються при оптимізації параметрів ВПІП, якщо опцію включено.
    Доцільно використати, при імпорті чужих даних планування або зміні методу використання кнопок відповідей.
deck-config-compute-optimal-weights-tooltip2 =
    Після натиснення кнопки «Оптимізувати», ВПІП проаналізує історію пригадувань та згенерує оптимальні параметри для
    запам'ятовування та матеріалу, який вивчаєте. Якщо ваші колоди суттєво відрізняються своєю складністю,
    радимо створити для них різні конфігурації, адже параметри для легких та тяжких колод відрізнятимуться.
    Немає необхідності в частій оптимізації параметрів - одного разу на декілька місяців є достатньо.
    
    Типово, параметри обчисляться на основі історії пригадувань усіх колод, які використовують поточну конфігурацію. 
    Перед обчисленням параметрів можна налаштувати вибірку, щоб змінити набір карток, які використовуватимуться
    для оптимізації параметрів.
deck-config-please-save-your-changes-first = Будь ласка, збережіть зміни.
deck-config-workload-factor-change =
    Приблизне навантаження:  { $factor }x
    (порівняно з { $previousDR }% бажаної затримки)
deck-config-workload-factor-unchanged = Зі збільшенням значення картки показуватимуться частіше.
deck-config-desired-retention-too-low = Бажана затримка є дуже малою і може призвести до дуже довгих інтервалів.
deck-config-desired-retention-too-high = Бажана затримка є дуже великою і може призвести до дуже коротких інтервалів.
deck-config-percent-of-reviews =
    { $reviews ->
        [one] { $pct }% з { $reviews } пригадування
        [few] { $pct }% з { $reviews } пригадувань
       *[many] { $pct }% з { $reviews } пригадувань
    }
deck-config-percent-input = { $pct }%
# This message appears during FSRS parameter optimization.
deck-config-checking-for-improvement = Перевірка наявності вдосконалень…
deck-config-optimizing-preset = Оптимізація { $current_count } з { $total_count } конфігурацій...
deck-config-fsrs-must-be-enabled = Спершу слід увімкнути ВПІП.
deck-config-fsrs-params-optimal = Схоже, що параметри ВПІП є оптимальними.
deck-config-fsrs-params-no-reviews = Пригадування не знайдено. Перевірте, що конфігурацію вказано для всіх колод які слід оптимізувати (з підколодами) і спробуйте ще раз.
deck-config-wait-for-audio = Чекати кінця аудіо
deck-config-show-reminder = Показати нагадування
deck-config-answer-again = Відповісти знову
deck-config-answer-hard = Вважати відповідь тяжкою
deck-config-answer-good = Вважати відповідь доброю
deck-config-days-to-simulate = Кількість днів для симуляції
deck-config-desired-retention-below-optimal = Обране значення затримка є меншим за оптимальне. Варто її збільшити.
# Description of the y axis in the FSRS simulation
# diagram (Deck options -> FSRS) showing the total number of
# cards that can be recalled or retrieved on a specific date.
deck-config-fsrs-simulator-experimental = Симулятор ВПІП (експериментальний)
deck-config-fsrs-simulate-desired-retention-experimental = Симулятор бажаної затримки ВПІП (Експериментально)
deck-config-fsrs-simulate-save-preset = Перед запуском симулятора збережіть налаштування після оптимізації.
deck-config-fsrs-desired-retention-help-me-decide-experimental = Допоможіть мені вирішити (Експериментально)
deck-config-additional-new-cards-to-simulate = Додаткові нові картки для симуляції
deck-config-simulate = Симулювати
deck-config-clear-last-simulate = Очистити останню симуляцію
deck-config-fsrs-simulator-radio-count = Пригадування
deck-config-advanced-settings = Додаткові налаштування
deck-config-smooth-graph = Графік однорідності
deck-config-suspend-leeches = Призупиняти приставучі
deck-config-save-options-to-preset = Зберегти зміни до конфігурації
deck-config-save-options-to-preset-confirm = Замістити налаштування поточної конфігурації поточними налаштуваннями симулятора?
# Radio button in the FSRS simulation diagram (Deck options -> FSRS) selecting
# to show the total number of cards that can be recalled or retrieved on a
# specific date.
deck-config-fsrs-simulator-radio-memorized = Запам'ятовано
deck-config-fsrs-simulator-radio-ratio = Співвідношення часу та запам'ятовування
# $time here is pre-formatted e.g. "10 Seconds" 
deck-config-fsrs-simulator-ratio-tooltip = { $time } на запам'ятовану картку

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.

# Checkbox
deck-config-health-check = Перевіряти стан при оптимізації
# Message box showing the result of the health check
deck-config-fsrs-bad-fit-warning =
    ВПІП складно передбачити запам'ятовування. Поради:
    
    - Призупиніть або переформулюйте приставучі картки.
    - Використовуйте кнопки відповідей відповідно. Пам'ятайте, що "Тяжко" це мірило успіху, а не невдачі.
    - Зрозумійте перед тим, як запам'ятовувати.
    
    Використовуючи ці поради, Ви покращите продуктивність впродовж кількох наступних місяців.
# Message box showing the result of the health check
deck-config-fsrs-good-fit = ВПІП добре налаштовано до Вашого запам'ятовування.

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-unable-to-determine-desired-retention = Неможливо визначити мінімальну рекомендовану затримку.
deck-config-predicted-minimum-recommended-retention = Найменша рекомендована затримка: { $num }
deck-config-compute-minimum-recommended-retention = Найменша рекомендована затримка
deck-config-compute-optimal-retention-tooltip4 =
    Цей інструмент спробує обчислити значення бажаної затримки що сприятиме кращому вивченню матеріалу за найменший час.
    Обчислене значення може слугувати еталонним, коли Ви будете вирішувати якою має бути бажана затримка.
    Ви можете вказати більше значення затримки, якщо хочете зменшити час навчання за рахунок збільшення коефіцієнту
    пригадування. Не радимо встановлювати значення бажаної затримки меншим за мінімальне, адже наслідком буде
    більше навантаження у зв'язку з вищим коефіцієнтом забування.
deck-config-plotted-on-x-axis = (Зображено по осі абсцис)
deck-config-a-100-day-interval =
    { $days ->
        [one] 100-денний інтервал стане { $days } денним.
        [few] 100-денний інтервал стане { $days } денним.
       *[many] 100-денний інтервал стане { $days } дненним.
    }
deck-config-fsrs-simulator-y-axis-title-time = Пригадування Час/День
deck-config-fsrs-simulator-y-axis-title-count = Пригадування Кількість/День
deck-config-fsrs-simulator-y-axis-title-memorized = Всього запам'ятовано
deck-config-bury-siblings = Відкласти сестринські картки
deck-config-do-not-bury = Не відкладати сестринські картки
deck-config-bury-if-new = Відкласти, якщо нова картка
deck-config-bury-if-new-or-review = Відкладати нові та пригадувальні картки
deck-config-bury-if-new-review-or-interday = Відкладати нові та пригадувальні картки а також карти для міжденного навчання.
deck-config-bury-tooltip =
    Сестринськими є інші картки однієї нотатки (напр. звичайні чи обернені картки,
    або картки з закритим текстом).
    
    Коли це налаштування вимкнено, декілька карток однієї нотатки можуть показуватися протягом одного
    дня. Якщо ввімкнено, Anki автоматично *сховає* сестринські картки до наступного
    дня. Це налаштування дозволяє вибрати, які типи карток можна приховувати при відповіді
    на сестринську картку.
    
    Картки для вивчення наступного дня можуть приховуватися у планувальнику V3. Такими картками
    є ті, у яких поточний крок навчання є один або кілька днів.
deck-config-seconds-to-show-question-tooltip = Кількість секунд перед показом запитання, коли включено автоперехід. 0 вимикає цю функціональність.
deck-config-answer-action-tooltip = Дія, яка виконується з поточною карткою перед автоматичним переходом до наступної.
deck-config-wait-for-audio-tooltip = Чекати на завершення аудіо перед автоматичним показом відповіді чи наступного запитання
deck-config-ignore-before-tooltip =
    Ігнорувати пригадування до вказаної дати при оптимізації та оцінці параметрів ВПІП.
    Опція є корисною якщо ви імпортуєте чужі графіки або змінили спосіб  використання кнопок відповідей.
deck-config-compute-optimal-retention-tooltip =
    Цей інструмент припускає, що ви починаєте з 0 карток, і спробує обчислити кількість матеріалу, який ви зможете
    запамʼятати за вказаний термін. Отримане значення затримки значно залежатиме від вхідних даних і,
    його суттєва відмінність від 0,9, означатиме, що виділений Вами щоденний час на навчання, є або занадто малим
    або занадто великим для тієї кількості карток, які ви намагаєтеся вивчити. Цей параметр можна використовувати як 
    еталон, але не радимо встановлювати його як значення затримки.
deck-config-health-check-tooltip1 = Показувати попередження якщо ВПІП має проблеми з налаштуванням пам'яті.
deck-config-health-check-tooltip2 = Перевірка стану відбувається при використанні "Оптимізувати поточну конфігурацію"
deck-config-compute-optimal-retention = Обчислити оптимальне запам'ятовування
deck-config-predicted-optimal-retention = Прогнозоване оптимальне пригадування: { $num }
deck-config-weights-tooltip =
    Параметри ВПІП впливають на планування карток. Anki спершу використовує типові значення. Після
    1000 пригадувань параметри можна оптимізувати, щоб вони краще відповідали Вашій продуктивності
    у колодах цієї конфігурації, за допомогою цієї опції.
deck-config-compute-optimal-weights-tooltip =
    Після 1000+ пригадувань у Anki, Ви можете скористатися кнопкою «Оптимізувати», щоб проаналізувати історію пригадувань,
    та автоматично згенерувати оптимальні параметри для вашої пам’яті та матеріалу, який ви вивчаєте.
    Якщо Ви маєте колоди, які дуже відрізняються за складністю, слід призначити їм окремі конфігурації, адже
    параметри легких і складних колод будуть різними. Недоцільно оптимізувати параметри часто – раз на декілька місяців достатньо.
    
    Типово параметри обчислюватимуться виходячи з історії пригадувань усіх колод які використовують поточну конфігурацію.
    Ви можете, за бажанням, сформувати вибірку до обчислення параметрів, якщо забажаєте вказати для яких карток 
    використовується оптимізація параметрів.
deck-config-compute-optimal-retention-tooltip2 =
    Цей інструмент припускає, що у Вас є 0 вивчених карток, і спробує обчислити значення бажаної затримки щоб
    наблизити Вас до вивчення якнайбільшої кількості матеріалу за найменший час. Обчислене значення може
    слугувати еталоном, коли Ви будете встановлювати своє значення. Встановіть значення бажаної затримки більшим,
    якщо бажаєте витрачати більше часу щоб отримати кращий коефіцієнт пригадування. Не слід встановлювати
    значення бажаної затримки меншим ніж мінімальне, адже наслідком стане збільшення даремного навчання.
deck-config-compute-optimal-retention-tooltip3 =
    Цей інструмент припускає, що ви починаєте з 0 вивчених карток, і намагатиметься знайти бажане значення затримки,
    яке дозволить засвоїти якнайбільше матеріалу за найменший час. Для точної симуляції процесу навчання,
    ця властивість потребує понад 400 пригадувань. Обчислене значення може слугувати еталоном, при прийнятті рішення
    про бажану затримку. Якщо Ви бажаєте присвятити більше часу, щоб отримати кращий рівень пригадування, то можете
    встановити вище значення бажаної затримки. Не радимо встановлювати значення бажаної затримки меншим за 
    мінімальне значення, адже наслідком стане збільшення навантаження у зв'язку з вищим рівнем забування.
deck-config-seconds-to-show-question-tooltip-2 = При активованому авто переході, кількість секунд очікування до появи відповіді. 0 вимикає налаштування.
deck-config-invalid-weights = Параметри повинні бути порожніми, щоб вжити типові значення, або 17-ма числами, розділеними комами.
deck-config-fsrs-on-all-clients =
    Будь ласка, переконайтеся, що всі Ваші Anki клієнти мають версію Anki(Mobile) 23.10+ або AnkiDroid 2.17+.
    Вільний планувальник РП не працюватиме правильно, якщо у Вас є старіші версії.
deck-config-optimize-all-tip = Ви можете оптимізувати всі конфігурації одночасно за допомогою спадної кнопки біля "Зберегти".
