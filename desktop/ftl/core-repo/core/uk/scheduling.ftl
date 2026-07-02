## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount } с.
scheduling-answer-button-time-minutes = { $amount } хв.
scheduling-answer-button-time-hours = { $amount } год.
scheduling-answer-button-time-days = { $amount } д.
scheduling-answer-button-time-months = { $amount }мі
scheduling-answer-button-time-years = { $amount } р.

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] { $amount } секунда
        [few] { $amount } секунди
        [many] { $amount } секунд
       *[other] { $amount } секунд
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] { $amount } хвилина
        [few] { $amount } хвилини
        [many] { $amount } хвилин
       *[other] { $amount } хвилин
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } година
        [few] { $amount } години
        [many] { $amount } годин
       *[other] { $amount } годин
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } день
        [few] { $amount } дні
        [many] { $amount } днів
       *[other] { $amount } днів
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } місяць
        [few] { $amount } місяці
        [many] { $amount } місяців
       *[other] { $amount } місяців
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } рік
        [few] { $amount } роки
        [many] { $amount } років
       *[other] { $amount } років
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    Наступна картка доступна через { $unit ->
        [seconds]
            { $amount ->
                [one] { $amount } секунду
                [few] { $amount } секунди
                [many] { $amount } секунд
               *[other] { $amount } секунд
            }
        [minutes]
            { $amount ->
                [one] { $amount } хвилину
                [few] { $amount } хвилини
                [many] { $amount } хвилин
               *[other] { $amount } хвилин
            }
       *[hours]
            { $amount ->
                [one] { $amount } годину
                [few] { $amount } години
                [many] { $amount } годин
               *[other] { $amount } годин
            }
    }.
scheduling-learn-remaining =
    { $remaining ->
        [one] На сьогодні залишилась { $remaining } картка.
        [few] На сьогодні залишилось { $remaining } картки.
       *[other] На сьогодні залишилось { $remaining } карток.
    }
scheduling-congratulations-finished = Вітаємо! В даний момент ви закінчили роботу з цією колодою.
scheduling-today-review-limit-reached =
    Щоденної межі пригадувань досягнуто, однак ще є пригадувальні картки.
    Для оптимального запам'ятовування можна збільшити добові 
    обмеження у налаштуваннях.
scheduling-today-new-limit-reached =
    У колоді ще є нові картки, але досягнуто добового обмеження.
    Ви можете збільшити обмеження у налаштуваннях,
    але, будь ласка, не забувайте: чим більше нових карток
    Ви запровадите у навчальний цикл, тим більше карток вам
    доведеться повторювати за короткий період.
scheduling-buried-cards-found = Одну або декілька карток відкладено до завтра. Ви можете { $unburyThem }, якщо бажаєте побачити зараз.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = повернути їх
scheduling-how-to-custom-study = Якщо Ви бажаєте вивчати не лише за розкладом, то можете скористатись { $customStudy }.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = додатковим навчанням

## Scheduler upgrade

scheduling-update-soon = Anki 2.1 містить новий планувальник, який виправляє ряд помилок у попередніх версіях Anki. Радимо оновитися до нього.
scheduling-update-done = Планувальник успішно оновлено.
scheduling-update-button = Оновлення
scheduling-update-later-button = Пізніше
scheduling-update-more-info-button = Вчити більше
scheduling-update-required =
    Вашу колекцію потрібно оновити до планувальника V2.
    Перш ніж продовжити, виберіть { scheduling-update-more-info-button }.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = Завжди показувати сторону картки з питанням під час повтору аудіо
scheduling-at-least-one-step-is-required = Необхідно принаймні один крок.
scheduling-automatically-play-audio = Автоматично програвати звук
scheduling-bury-related-new-cards-until-the = Відкладати пов'язані нові картки до наступного дня
scheduling-bury-related-reviews-until-the-next = Відкладати пов'язані картки на повторення до наступного дня
scheduling-days = днів
scheduling-description = Опис
scheduling-easy-bonus = Бонус легкості
scheduling-easy-interval = Інтервал легкості
scheduling-end = (кінець)
scheduling-general = Загальне
scheduling-graduating-interval = Градуйований інтервал
scheduling-hard-interval = Інтервал для «Тяжко»
scheduling-ignore-answer-times-longer-than = Не враховувати час відповіді довше ніж
scheduling-interval-modifier = Модифікатор інтервалу
scheduling-lapses = Невдачі
scheduling-lapses2 = невдачі
scheduling-learning = Навчання
scheduling-leech-action = Дія для приставучої картки
scheduling-leech-threshold = Поріг для приставучих карток
scheduling-maximum-interval = Максимальний інтервал
scheduling-maximum-reviewsday = Максимальна кількість повторених карток в день.
scheduling-minimum-interval = Мінімальний інтервал
scheduling-mix-new-cards-and-reviews = Змішати нові картки з переглянутими
scheduling-new-cards = Нові картки
scheduling-new-cardsday = Нових карток/день
scheduling-new-interval = Новий інтервал
scheduling-new-options-group-name = Нова назва групи налаштувань:
scheduling-options-group = Група налаштувань:
scheduling-order = Порядок
scheduling-parent-limit = (межа батьківської колоди: { $val })
scheduling-reset-counts = Скинути лічильники повторювань та провалів
scheduling-restore-position = Відновити за можливості початкову позицію
scheduling-review = Повторювані
scheduling-reviews = Повторення
scheduling-seconds = секунд(и)
scheduling-set-all-decks-below-to = Віднести усі колоди нижче  { $val } до цієї групи налаштувань?
scheduling-set-for-all-subdecks = Встановити для усіх підколод
scheduling-show-answer-timer = Показати таймер відповіді
scheduling-show-new-cards-after-reviews = Показувати нові картки після карток для повторення
scheduling-show-new-cards-before-reviews = Показувати нові картки перед переглядом
scheduling-show-new-cards-in-order-added = Показувати нові картки в порядку їх додавання
scheduling-show-new-cards-in-random-order = Показувати нові картки у випадковому порядку
scheduling-starting-ease = Початкова легкість
scheduling-steps-in-minutes = Кроки (у хвилинах)
scheduling-steps-must-be-numbers = Кроки мають бути числами.
scheduling-tag-only = Встановити мітку
scheduling-the-default-configuration-cant-be-removed = Конфігурацію за замовчуванням не можна видаляти.
scheduling-your-changes-will-affect-multiple-decks = Ваші зміни вплинуть на кілька колод. Якщо ви хочете змінити лише поточну колоду, спочатку треба додати нову групу налаштувань.
scheduling-deck-updated =
    { $count ->
        [one] { $count } колоду оновлено.
        [few] { $count } колод оновлено.
       *[other] { $count } колод оновлено.
    }
scheduling-set-due-date-prompt =
    { $cards ->
        [one] Через скільки днів показати картку?
        [few] Через скільки днів показати картки?
       *[many] Через скільки днів показати карток?
    }
scheduling-set-due-date-prompt-hint =
    0 = сьогодні¶
    1! = завтра + змінити інтервал на 1¶
    3-7 = випадково від 3 до 7 днів
scheduling-set-due-date-done =
    { $cards ->
        [one] Встановлено дату пригадування для { $cards } картки.
        [few] Встановлено дату пригадування для { $cards } карток.
       *[many] Встановлено дату пригадування для { $cards } карток.
    }
scheduling-graded-cards-done =
    { $cards ->
        [one] Оцінено { $cards } картку.
        [few] Оцінено { $cards } картки.
       *[many] Оцінено { $cards } карток.
    }
scheduling-forgot-cards =
    { $cards ->
        [one] { $cards } картку було забуто.
        [few] { $cards } картки було забуто.
       *[many] { $cards }  карток було забуто.
    }
