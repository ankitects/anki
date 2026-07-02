# The date a card will be ready to review
statistics-due-date = К просмотру
# The count of cards waiting to be reviewed
statistics-due-count = К просмотру
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = Новая № { $number }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } карточек/мин.
statistics-average-answer-time = { $average-seconds } с ({ statistics-cards-per-min })

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds =
    { $amount ->
        [one] за { $amount } секунду
        [few] за { $amount } секунды
        [many] за { $amount } секунд
       *[other] за { $amount } секунд
    }
statistics-in-time-span-minutes =
    { $amount ->
        [one] за { $amount } минуту
        [few] за { $amount } минуты
        [many] за { $amount } минут
       *[other] за { $amount } минут
    }
statistics-in-time-span-hours =
    { $amount ->
        [one] за { $amount } час
        [few] за { $amount } часа
        [many] за { $amount } часов
       *[other] за { $amount } часов
    }
statistics-in-time-span-days =
    { $amount ->
        [one] за { $amount } день
        [few] за { $amount } дня
        [many] за { $amount } дней
       *[other] за { $amount } дней
    }
statistics-in-time-span-months =
    { $amount ->
        [one] за { $amount } месяц
        [few] за { $amount } месяца
        [many] за { $amount } месяцев
       *[other] за { $amount } месяцев
    }
statistics-in-time-span-years =
    { $amount ->
        [one] за { $amount } год
        [few] за { $amount } года
        [many] за { $amount } лет
       *[other] за { $amount } лет
    }
# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { statistics-in-time-span-seconds } part should be pasted in from the English
# version unmodified.
statistics-studied-today =
    { $unit ->
        [seconds]
            Сегодня { statistics-cards }
            { statistics-in-time-span-seconds }
            ({ $secs-per-card } с/карт.)
        [minutes]
            Сегодня { statistics-cards }
            { statistics-in-time-span-minutes }
            ({ $secs-per-card } с/карт.)
        [hours]
            Сегодня { statistics-cards }
            { statistics-in-time-span-hours }
            ({ $secs-per-card } с/к.)
        [days]
            Сегодня { statistics-cards }
            { statistics-in-time-span-days }
            ({ $secs-per-card } с/карт.)
        [months]
            Сегодня { statistics-cards }
            { statistics-in-time-span-months }
            ({ $secs-per-card } с/карт.)
       *[years]
            Сегодня { statistics-cards }
            { statistics-in-time-span-years }
            ({ $secs-per-card } с/карт.)
    }

##

statistics-cards =
    { $cards ->
        [one] { $cards } карточка
        [few] { $cards } карточки
        [many] { $cards } карточек
       *[other] { $cards } карточек
    }
statistics-notes =
    { $notes ->
        [one] { $notes } запись
        [few] { $notes } записи
       *[many] { $notes } записей
    }
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews =
    { $reviews ->
        [one] { $reviews } повторение
        [few] { $reviews } повторения
        [many] { $reviews } повторений
       *[other] { $reviews } повторений
    }
# This fragment of the tooltip in the FSRS simulation
# diagram (Deck options -> FSRS) shows the total number of
# cards that can be recalled or retrieved on a specific date.
statistics-memorized = { $memorized } выучено
statistics-today-title = Сегодня
statistics-today-again-count = Количество "Снова":
statistics-today-type-counts = Изучаемых: { $learnCount }, повторяемых: { $reviewCount }, переучиваемых: { $relearnCount }, фильтрованных: { $filteredCount }
statistics-today-no-cards = Ни одна карточка не была повторена сегодня.
statistics-today-no-mature-cards = Давно изученные карты сегодня не повторяли.
statistics-today-correct-mature = Верных ответов в давно изученных картах: { $correct }/{ $total } ({ $percent }%)
statistics-counts-total-cards = Всего карточек
statistics-counts-new-cards = Новая
statistics-counts-young-cards = Свежеизученная
statistics-counts-mature-cards = Давно изученная
statistics-counts-suspended-cards = Исключена
statistics-counts-buried-cards = Отложена
statistics-counts-filtered-cards = Отфильтрованные
statistics-counts-learning-cards = Изучение
statistics-counts-relearning-cards = Переучиваемая
statistics-counts-title = Количество карточек
statistics-counts-separate-suspended-buried-cards = Исключённые и отложенные отдельно

## Retention represents your actual retention from past reviews, in
## comparison to the "desired retention" setting of FSRS, which forecasts
## future retention. Retention is the percentage of all reviewed cards
## that were marked as "Hard," "Good," or "Easy" within a specific time period.
##
## Most of these strings are used as column / row headings in a table.
## (Excluding -title and -subtitle)
## It is important to keep these translations short so that they do not make
## the table too large to display on a single stats card.
##
## N.B. Stats cards may be very small on mobile devices and when the Stats
##      window is certain sizes.

statistics-true-retention-title = Настоящее усвоение
statistics-true-retention-subtitle = Процент правильных карточек с интервалом больше 1 дня.
statistics-true-retention-tooltip = При использовании FSRS фактический показатель усвоения, скорее всего, будет близок к желаемому показателю усвоения. Данные за один день могут колебаться, поэтому полезнее рассматривать период не менее одного месяца.
statistics-true-retention-range = Диапазон
statistics-true-retention-pass = Вспомнено
statistics-true-retention-fail = Забыто
# This will usually be the same as statistics-counts-total-cards
statistics-true-retention-total = Всего карточек
statistics-true-retention-count = Кол-во
statistics-true-retention-retention = Усвоение
# This will usually be the same as statistics-counts-young-cards
statistics-true-retention-young = Свежеизученная
# This will usually be the same as statistics-counts-mature-cards
statistics-true-retention-mature = Давно изученная
statistics-true-retention-all = Все
statistics-true-retention-today = Сегодня
statistics-true-retention-yesterday = Вчера
statistics-true-retention-week = На прошлой неделе
statistics-true-retention-month = В прошлом месяце
statistics-true-retention-year = В прошлом году
statistics-true-retention-all-time = За всё время
# If there are no reviews within a specific time period, the retention
# percentage cannot be calculated and is displayed as "N/A."
statistics-true-retention-not-applicable = N/A

##

statistics-range-all-time = всё время
statistics-range-1-year-history = за 12 месяцев
statistics-range-all-history = вся история
statistics-range-deck = колода
statistics-range-collection = коллекция
statistics-range-search = Поиск
statistics-card-ease-title = Лёгкость карточки
statistics-card-difficulty-title = Сложность карточки
statistics-card-stability-title = Стабильность карточки
statistics-card-stability-subtitle = Время, за которое вспоминаемость падает до 90%.
statistics-median-stability = Медиана стабильности
statistics-card-retrievability-title = Вспоминаемость карточки
statistics-card-ease-subtitle = Чем ниже лёгкость, тем чаще будет появляться карточка.
statistics-card-difficulty-subtitle2 = Чем выше сложность, тем медленнее растёт стабильность.
statistics-retrievability-subtitle = Вероятность вспомнить карточку сегодня.
# eg "3 cards with 150-170% ease"
statistics-card-ease-tooltip =
    { $cards ->
        [one] { $cards } карточка с { $percent } лёгкости
        [few] { $cards } карточки с { $percent } лёгкости
        [many] { $cards } карточек с { $percent } лёгкости
       *[other] { $cards } карточек с { $percent } лёгкости
    }
statistics-card-difficulty-tooltip =
    { $cards ->
        [one] { $cards } карточка со сложностью { $percent }
        [few] { $cards } карточки со сложностью { $percent }
       *[many] { $cards } карточек со сложностью { $percent }
    }
statistics-retrievability-tooltip =
    { $cards ->
        [one] { $cards } карточка с { $percent } вспоминаемости
        [few] { $cards } карточек с { $percent } вспоминаемости
        [many] { $cards } карточек с { $percent } вспоминаемости
       *[other] { $cards } карточек с { $percent } вспоминаемости
    }
statistics-future-due-title = Прогноз повторений
statistics-future-due-subtitle = Число повторяемых в последующие дни.
statistics-added-title = Добавленные
statistics-added-subtitle = Количество новых карточек.
statistics-reviews-count-subtitle = Количество вопросов, на которые вы ответили.
statistics-reviews-time-subtitle = Время, затраченное на ответы
statistics-answer-buttons-title = Кнопки ответа
# eg Button: 4
statistics-answer-buttons-button-number = Кнопка
# eg Times pressed: 123
statistics-answer-buttons-button-pressed = Раз нажато
statistics-answer-buttons-subtitle = Сколько раз вы нажали каждую кнопку.
statistics-reviews-title = Повторения
statistics-reviews-time-checkbox = Время
statistics-in-days-single =
    { $days ->
        [0] Сегодня
        [1] Завтра
        [one] за { $days } день
        [few] за { $days } дня
        [many] за { $days } дней
       *[other] за { $days } дней
    }
statistics-in-days-range = за { $daysStart }-{ $daysEnd } дней
statistics-days-ago-single =
    { $days ->
        [1] Вчера
        [one] { $days } день назад
        [few] { $days } дня назад
        [many] { $days } дней назад
       *[other] { $days } дней назад
    }
statistics-days-ago-range = { $daysStart }-{ $daysEnd } дней назад
statistics-running-total = Промежуточная сумма
statistics-cards-due =
    { $cards ->
        [one] { $cards } к просмотру
        [few] { $cards } к просмотру
        [many] { $cards } к просмотру
       *[other] { $cards } к просмотру
    }
statistics-backlog-checkbox = Отставание
statistics-intervals-title = Интервалы повторений
statistics-intervals-subtitle = Время до следующего показа повторяемых.
statistics-intervals-day-range =
    { $cards ->
        [one] { $cards } карточка с перерывом в { $daysStart }~{ $daysEnd } дней
        [few] { $cards } карточки с перерывом в { $daysStart }~{ $daysEnd } дней
        [many] { $cards } карточек с перерывом в { $daysStart }~{ $daysEnd } дней
       *[other] { $cards } карточек с перерывом в { $daysStart }~{ $daysEnd } дней
    }
statistics-intervals-day-single =
    { $cards ->
        [one] { $cards } карточка с перерывом в { $day } дней
        [few] { $cards } карточки с перерывом в { $day } дней
        [many] { $cards } карточек с перерывом в { $day } дней
       *[other] { $cards } карточек с перерывом в { $day } дней
    }
statistics-stability-day-range =
    { $cards ->
        [one] { $cards } карточка со стабильностью { $daysStart }~{ $daysEnd } дней
        [few] { $cards } карточки со стабильностью { $daysStart }~{ $daysEnd } дней
       *[many] { $cards } карточек со стабильностью { $daysStart }~{ $daysEnd } дней
    }
statistics-stability-day-single =
    { $cards ->
        [one] { $cards } карточка со стабильностью { $day } дней
        [few] { $cards } карточки со стабильностью { $day } дней
       *[many] { $cards } карточек со стабильностью { $day } дней
    }
# hour range, eg "From 14:00-15:00"
statistics-hours-range = С { $hourStart }:00 до { $hourEnd }:00
statistics-hours-correct = { $correct }/{ $total } верных ({ $percent }%)
statistics-hours-correct-info = → (не 'Снова')
# the emoji depicts the graph displaying this number
statistics-hours-reviews = { $reviews } повторений
# the emoji depicts the graph displaying this number
statistics-hours-correct-reviews = { $percent }% правильных ({ $reviews })
statistics-hours-title = По часам
statistics-hours-subtitle = Процент правильных ответов в зависимости от времени.
# shown when graph is empty
statistics-no-data = Нет данных
statistics-calendar-title = Календарь

## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.

statistics-elapsed-time-seconds = { $amount } с
statistics-elapsed-time-minutes = { $amount } мин.
statistics-elapsed-time-hours = { $amount } ч.
statistics-elapsed-time-days = { $amount } дн.
statistics-elapsed-time-months = { $amount } мес.
statistics-elapsed-time-years = { $amount } г.

##

statistics-average-for-days-studied = В среднем в день учёбы
# This term is used in a variety of contexts to refers to the total amount of
# items (e.g., cards, mature cards, etc) for a given period, rather than the
# total of all existing items.
statistics-total = Всего
statistics-days-studied = Дней учёбы
statistics-average-answer-time-label = Среднее время ответа
statistics-average = В среднем
statistics-median-interval = Средний интервал
statistics-due-tomorrow = На завтра
# This string, ‘Daily load,’ appears in the ‘Future due’ table and represents a
# forecasted estimate of the number of cards expected to be reviewed daily in 
# the future. Unlike the other strings in the table that display actual data 
# derived from the current scheduling (e.g., ‘Average’, ‘Due tomorrow’),
# ‘Daily load’ is a projection based on the given data.
statistics-daily-load = Ежедневная нагрузка
# eg 5 of 15 (33.3%)
statistics-amount-of-total-with-percentage = { $amount } из { $total } ({ $percent }%)
statistics-average-over-period = Если бы вы учились каждый день
statistics-reviews-per-day =
    { $count ->
        [one] { $count } повторение в день
        [few] { $count } повторения в день
        [many] { $count } повторений в день
       *[other] { $count } повторений в день
    }
statistics-minutes-per-day =
    { $count ->
        [one] { $count } минута за день
        [few] { $count } минуты за день
        [many] { $count } минут за день
       *[other] { $count } минут за день
    }
statistics-cards-per-day =
    { $count ->
        [one] { $count } карточка за день
        [few] { $count } карточки за день
        [many] { $count } карточек за день
       *[other] { $count } карточек за день
    }
statistics-median-ease = Медиана легкости
statistics-median-difficulty = Средний уровень сложности
statistics-average-retrievability = Средняя вспоминаемость
statistics-estimated-total-knowledge = Оценочный общий объём знаний
statistics-save-pdf = Сохранить в формате PDF
statistics-saved = Сохранено.
statistics-stats = статистика
statistics-title = Статистика

## These strings are no longer used - you do not need to translate them if they
## are not already translated.

statistics-average-stability = Средняя стабильность
statistics-average-interval = Средний интервал
statistics-average-ease = Средняя лёгкость
statistics-average-difficulty = Средняя сложность
