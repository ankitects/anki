## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount } с
scheduling-answer-button-time-minutes = { $amount } мин.
scheduling-answer-button-time-hours = { $amount } ч.
scheduling-answer-button-time-days = { $amount } дн.
scheduling-answer-button-time-months = { $amount } мес.
scheduling-answer-button-time-years = { $amount } г.

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] { $amount } секунда
        [few] { $amount } секунды
        [many] { $amount } секунд
       *[other] { $amount } секунд
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] { $amount } минута
        [few] { $amount } минуты
        [many] { $amount } минут
       *[other] { $amount } минут
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } час
        [few] { $amount } часа
        [many] { $amount } часов
       *[other] { $amount } часов
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } день
        [few] { $amount } дня
        [many] { $amount } дней
       *[other] { $amount } дней
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } месяц
        [few] { $amount } месяца
        [many] { $amount } месяцев
       *[other] { $amount } месяцев
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } год
        [few] { $amount } года
        [many] { $amount } лет
       *[other] { $amount } лет
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    Следующая карточка будет доступна через { $unit ->
        [seconds]
            { $amount ->
                [one] { $amount } секунду.
                [few] { $amount } секунды.
                [many] { $amount } секунд.
               *[other] { $amount } секунд.
            }
        [minutes]
            { $amount ->
                [one] { $amount } минуту.
                [few] { $amount } минуты.
                [many] { $amount } минут.
               *[other] { $amount } минут.
            }
       *[hours]
            { $amount ->
                [one] { $amount } час.
                [few] { $amount } часа.
                [many] { $amount } часов.
               *[other] { $amount } часов.
            }
    }
scheduling-learn-remaining =
    { $remaining ->
        [one] На сегодня осталась одна карточка.
        [few] На сегодня остались { $remaining } карточки.
        [many] На сегодня остались { $remaining } карточек.
       *[other] На сегодня осталось { $remaining } карточек.
    }
scheduling-congratulations-finished = Ура! На сегодня всё.
scheduling-today-review-limit-reached =
    Сегодняшний лимит повторений был достигнут, но некоторые
    карточки ещё не были просмотрены.  Для оптимального запоминания
    подумайте об увеличении дневного лимита просмотров в опциях.
scheduling-today-new-limit-reached =
    Есть ещё новые карточки, но дневной лимит исчерпан.
    Вы можете увеличить лимит в настройках, но, пожалуйста,
    имейте в виду, что чем больше новых карточек вы просмотрите,
    тем больше вам надо будет повторять в ближайшее время.
scheduling-buried-cards-found = Одна или несколько карточек отложены и будут показаны завтра. Вы можете { $unburyThem }, если хотите увидеть их сейчас.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = вернуть их
scheduling-how-to-custom-study = Если вы хотите заниматься вне расписания, начните { $customStudy }.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = допзанятие

## Scheduler upgrade

scheduling-update-soon = Anki 2.1 поставляется с новым планировщиком, который исправляет ряд проблем, которые были в предыдущих версиях Anki. Рекомендуется обновление до этой версии.
scheduling-update-done = Планировщик успешно обновлён.
scheduling-update-button = Обновление
scheduling-update-later-button = Позже
scheduling-update-more-info-button = Узнать больше
scheduling-update-required =
    Вашу коллекцию нужно обновить до планировщика V2.
    Выберите { scheduling-update-more-info-button }, чтобы продолжить.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = Всегда повторять вопрос при повторении аудио
scheduling-at-least-one-step-is-required = Должен быть хотя бы один шаг.
scheduling-automatically-play-audio = Автоматически воспроизводить аудио
scheduling-bury-related-new-cards-until-the = Откладывать связанные новые карточки до следующего дня
scheduling-bury-related-reviews-until-the-next = Откладывать повторение связанных карточек до следующего дня
scheduling-days = дней
scheduling-description = Описание
scheduling-easy-bonus = Множитель для «Легко»
scheduling-easy-interval = Интервал лёгких
scheduling-end = (конец)
scheduling-general = Общие
scheduling-graduating-interval = Интервал перевода
scheduling-hard-interval = Интервал для «Трудно»
scheduling-ignore-answer-times-longer-than = Не учитывать время ответа больше
scheduling-interval-modifier = Модификатор интервала
scheduling-lapses = Забыто
scheduling-lapses2 = забытых
scheduling-learning = Изучение
scheduling-leech-action = Что делать с приставучими
scheduling-leech-threshold = Порог для приставучих
scheduling-maximum-interval = Максимальный интервал
scheduling-maximum-reviewsday = Максимум повторяемых в день
scheduling-minimum-interval = Минимальный интервал
scheduling-mix-new-cards-and-reviews = Перемешивать новые и повторяемые
scheduling-new-cards = Новые карточки
scheduling-new-cardsday = Новых карточек в день
scheduling-new-interval = Новый интервал
scheduling-new-options-group-name = Название нового набора:
scheduling-options-group = Набор настроек:
scheduling-order = Порядок
scheduling-parent-limit = (лимит материнской: { $val })
scheduling-reset-counts = Сбросить счетчик количества повторений и забытых
scheduling-restore-position = Восстановить исходную позицию, когда возможно
scheduling-review = Повторяемые
scheduling-reviews = Повторено
scheduling-seconds = секунд
scheduling-set-all-decks-below-to = Отметить все колоды под { $val } как эта опциональная группа?
scheduling-set-for-all-subdecks = Назначить всем подколодам
scheduling-show-answer-timer = Показывать время ответа
scheduling-show-new-cards-after-reviews = Показывать новые карточки после повторяемых
scheduling-show-new-cards-before-reviews = Показывать новые карточки до повторяемых
scheduling-show-new-cards-in-order-added = Показывать новые карточки в порядке добавления
scheduling-show-new-cards-in-random-order = Показывать новые карточки в случайном порядке
scheduling-starting-ease = Начальная лёгкость
scheduling-steps-in-minutes = Шаги (в минутах)
scheduling-steps-must-be-numbers = Шаги указываются в виде чисел.
scheduling-tag-only = Только пометить
scheduling-the-default-configuration-cant-be-removed = Стандартные настройки не могут быть удалены.
scheduling-your-changes-will-affect-multiple-decks = Изменения затронут несколько колод. Если вы хотите поменять только текущую колоду, пожалуйста, добавьте новый набор настроек.
scheduling-deck-updated =
    { $count ->
        [one] { $count } колода обновлена.
        [few] { $count } колоды обновлены.
        [many] { $count } колод обновлено.
       *[other] { $count } колод обновлено.
    }
scheduling-set-due-date-prompt =
    { $cards ->
        [one] Через сколько дней показать карточку?
        [few] Через сколько дней показать карточки?
        [many] Через сколько дней показать карточки?
       *[other] Через сколько дней показать карточки?
    }
scheduling-set-due-date-prompt-hint =
    0 = сегодня
    1! = завтра + изменить интервал на 1
    3-7 = случайный выбор 3-7 дней
scheduling-set-due-date-done =
    { $cards ->
        [one] Установлено расписание { $cards } карточки.
        [few] Установлено расписание { $cards } карточек.
        [many] Установлено расписание { $cards } карточек.
       *[other] Установлено расписание { $cards } карточек.
    }
scheduling-graded-cards-done =
    { $cards ->
        [one] Рейтинг { $cards } карты
        [few] Рейтинг { $cards } карт
       *[many] Рейтинг { $cards } карт
    }
scheduling-forgot-cards =
    { $cards ->
        [one] Сброшена { $cards } карточка.
        [few] Сброшены { $cards } карточки.
        [many] Сброшено { $cards } карточек.
       *[other] Сброшено { $cards } карточек.
    }
