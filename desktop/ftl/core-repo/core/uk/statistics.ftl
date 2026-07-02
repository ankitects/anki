# The date a card will be ready to review
statistics-due-date = Пригадати
# The count of cards waiting to be reviewed
statistics-due-count = Пригадати
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = Нових #{ $number }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } карток/хвилину
statistics-average-answer-time = { $average-seconds } с ({ statistics-cards-per-min })

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds =
    { $amount ->
        [one] за { $amount } секунду
        [few] за { $amount } секунди
        [many] за { $amount } секунд
       *[other] за { $amount } секунд
    }
statistics-in-time-span-minutes =
    { $amount ->
        [one] за { $amount } хвилину
        [few] за { $amount } хвилини
        [many] за { $amount } хвилин
       *[other] за { $amount } хвилин
    }
statistics-in-time-span-hours =
    { $amount ->
        [one] за { $amount } годину
        [few] за { $amount } години
        [many] за { $amount } годин
       *[other] за { $amount } годин
    }
statistics-in-time-span-days =
    { $amount ->
        [one] за { $amount } день
        [few] за { $amount } дні
        [many] за { $amount } днів
       *[other] за { $amount } днів
    }
statistics-in-time-span-months =
    { $amount ->
        [one] за { $amount } місяць
        [few] за { $amount } місяці
        [many] за { $amount } місяців
       *[other] за { $amount } місяців
    }
statistics-in-time-span-years =
    { $amount ->
        [one] за { $amount } рік
        [few] за { $amount } роки
        [many] за { $amount } років
       *[other] за { $amount } років
    }
# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { statistics-in-time-span-seconds } part should be pasted in from the English
# version unmodified.
statistics-studied-today =
    Сьогодні переглянуто { statistics-cards }, { $unit ->
        [seconds] { statistics-in-time-span-seconds }
        [minutes] { statistics-in-time-span-minutes }
        [hours] { statistics-in-time-span-hours }
        [days] { statistics-in-time-span-days }
        [months] { statistics-in-time-span-months }
       *[years] { statistics-in-time-span-years }
    } ({ $secs-per-card }s/картку)

##

statistics-cards =
    { $cards ->
        [one] { $cards } картка
        [few] { $cards } карток
        [many] { $cards } карток
       *[other] { $cards } карток
    }
statistics-notes =
    { $notes ->
        [one] { $notes } нотатка
        [few] { $notes } нотатки
       *[many] { $notes } нотаток
    }
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews =
    { $reviews ->
        [one] { $reviews } повторення
        [few] { $reviews } повторень
        [many] { $reviews } повторень
       *[other] { $reviews } повторень
    }
# This fragment of the tooltip in the FSRS simulation
# diagram (Deck options -> FSRS) shows the total number of
# cards that can be recalled or retrieved on a specific date.
statistics-memorized = Запам'ятовано { $memorized }
statistics-today-title = Сьогодні
statistics-today-again-count = Кількість карток з відповіддю "Знову":
statistics-today-type-counts = Вивченні: { $learnCount }, Повторюванні: { $reviewCount }, Забуті: { $relearnCount }, Відфільтровані: { $filteredCount }
statistics-today-no-cards = Не повторено жодної картки.
statistics-today-no-mature-cards = Сьогодні не переглянуто жодної зрілої картки.
statistics-today-correct-mature = Правильні відповіді по зрілим карткам: { $correct }/{ $total } ({ $percent }%)
statistics-counts-total-cards = Загальна кількість карток
statistics-counts-new-cards = Нові
statistics-counts-young-cards = Незрілі
statistics-counts-mature-cards = Зрілі
statistics-counts-suspended-cards = Призупинені
statistics-counts-buried-cards = Приховані
statistics-counts-filtered-cards = Відфільтровано
statistics-counts-learning-cards = Навчання
statistics-counts-relearning-cards = Перенавчання
statistics-counts-title = Кількість карток
statistics-counts-separate-suspended-buried-cards = Відокремити призупинені/приховані картки

## True Retention represents your actual retention rate from past reviews, in
## comparison to the "desired retention" parameter of FSRS, which forecasts
## future retention. True Retention is the percentage of all reviewed cards
## that were marked as "Hard," "Good," or "Easy" within a specific time period.
##
## Most of these strings are used as column / row headings in a table.
## (Excluding -title and -subtitle)
## It is important to keep these translations short so that they do not make
## the table too large to display on a single stats card.
##
## N.B. Stats cards may be very small on mobile devices and when the Stats
##      window is certain sizes.

statistics-true-retention-title = Справжня затримка
statistics-true-retention-subtitle = Коефіцієнт вивчення карток з інтервалом ≥ 1 дня.
statistics-true-retention-tooltip = При використанні ВПІП, очікується, що Ваша правдива затримка буде близькою до бажаної. Пам'ятайте, що дані за один день містять шуми, тому краще оцінювати місячні дані.
statistics-true-retention-range = Діапазон
statistics-true-retention-pass = Вивчено
statistics-true-retention-fail = Забуто
# This will usually be the same as statistics-counts-total-cards
statistics-true-retention-total = Загальна кількість карток
statistics-true-retention-count = Кількість
statistics-true-retention-retention = Затримка
# This will usually be the same as statistics-counts-young-cards
statistics-true-retention-young = Незрілі
# This will usually be the same as statistics-counts-mature-cards
statistics-true-retention-mature = Зрілі
statistics-true-retention-all = Всі
statistics-true-retention-today = Сьогодні
statistics-true-retention-yesterday = Вчора
statistics-true-retention-week = Минулого тижня
statistics-true-retention-month = Минулого місяця
statistics-true-retention-year = Минулого року
statistics-true-retention-all-time = За весь час
# If there are no reviews within a specific time period, the retention
# percentage cannot be calculated and is displayed as "N/A."
statistics-true-retention-not-applicable = Н/д

##

statistics-range-all-time = тривалість життя колоди
statistics-range-1-year-history = За остані 12 місяців
statistics-range-all-history = За весь час
statistics-range-deck = колода
statistics-range-collection = колекція
statistics-range-search = Пошук
statistics-card-ease-title = Легкість картки
statistics-card-difficulty-title = Складність картки
statistics-card-stability-title = Стійкість картки
statistics-card-stability-subtitle = Затримка при якій легкість пригадування опускається до 90%
statistics-median-stability = Медіанна стабільність
statistics-card-retrievability-title = Легкість пригадування картки
statistics-card-ease-subtitle = Чим менша легкість, тим частіше з'являтиметься картка.
statistics-card-difficulty-subtitle2 = Чим більша складність, тим повільніше зростає стійкість.
statistics-retrievability-subtitle = Ймовірність повторного показу картки сьогодні.
# eg "3 cards with 150-170% ease"
statistics-card-ease-tooltip =
    { $cards ->
        [one] { $cards } картка з легкістю { $percent }
        [few] { $cards } картки з легкістю { $percent }
       *[other] { $cards } карток з легкістю { $percent }
    }
statistics-card-difficulty-tooltip =
    { $cards ->
        [one] { $cards } картка з складністю { $percent }
        [few] { $cards } картки з складністю { $percent }
       *[many] { $cards } карток з складністю { $percent }
    }
statistics-retrievability-tooltip =
    { $cards ->
        [one] { $cards } картка з { $percent } легкістю пригадування
        [few] { $cards } картки з { $percent } легкістю пригадування
       *[many] { $cards } карток з { $percent } легкістю пригадування
    }
statistics-future-due-title = Прогноз
statistics-future-due-subtitle = Кількість майбутніх пригадувань.
statistics-added-title = Додано
statistics-added-subtitle = Кількість доданих нових карток.
statistics-reviews-count-subtitle = Кількість питань, на які ви відповіли.
statistics-reviews-time-subtitle = Час, витрачений, щоб відповісти на питання.
statistics-answer-buttons-title = Кнопки відповіді
# eg Button: 4
statistics-answer-buttons-button-number = Кнопка
# eg Times pressed: 123
statistics-answer-buttons-button-pressed = Разів натиснуто
statistics-answer-buttons-subtitle = Кількість разів, що ви натисли кожну кнопку.
statistics-reviews-title = Повторення
statistics-reviews-time-checkbox = Час
statistics-in-days-single =
    { $days ->
        [0] Сьогодні
        [1] Завтра
        [one] За { $days } день
        [few] За { $days } дні
        [many] За { $days } днів
       *[other] За { $days } днів
    }
statistics-in-days-range = За { $daysStart }-{ $daysEnd } дні
statistics-days-ago-single =
    { $days ->
        [1] Вчора
        [one] { $days } день тому
        [few] { $days } дні тому
       *[other] { $days } днів тому
    }
statistics-days-ago-range = { $daysStart }-{ $daysEnd } днів тому
statistics-running-total = Загалом
statistics-cards-due =
    { $cards ->
        [one] { $cards } картка очікує
        [few] { $cards } картки очікує
        [many] { $cards } карток очікує
       *[other] { $cards } карток очікує
    }
statistics-backlog-checkbox = Відставання
statistics-intervals-title = Інтервали
statistics-intervals-subtitle = Час, через який будуть знову показуватися картки для повторювання.
statistics-intervals-day-range =
    { $cards ->
        [one] { $cards } картка з проміжком { $daysStart }~{ $daysEnd } дні
        [few] { $cards } картки з проміжком { $daysStart }~{ $daysEnd } дні
       *[other] { $cards } карток з проміжком { $daysStart }~{ $daysEnd } дні
    }
statistics-intervals-day-single =
    { $cards ->
        [one] { $cards } картка з проміжком { $day } день
        [few] { $cards } картки з проміжком { $day } день
       *[other] { $cards } карток з проміжком { $day } день
    }
statistics-stability-day-range =
    { $cards ->
        [one] { $cards } картка з { $daysStart }~{ $daysEnd } денною стабільністю
        [few] { $cards } картки з { $daysStart }~{ $daysEnd } денною стабільністю
       *[many] { $cards } карток з { $daysStart }~{ $daysEnd } денною стабільністю
    }
statistics-stability-day-single =
    { $cards ->
        [one] { $cards } картка з { $day } денною стабільністю
        [few] { $cards } картки з { $day } денною стабільністю
       *[many] { $cards } карток з { $day } денною стабільністю
    }
# hour range, eg "From 14:00-15:00"
statistics-hours-range = З { $hourStart }:00~{ $hourEnd }:00
statistics-hours-correct = { $correct }/{ $total } правильно ({ $percent }%)
statistics-hours-correct-info = → (не 'Знову')
# the emoji depicts the graph displaying this number
statistics-hours-reviews = 📊 { $reviews } пригадувань
# the emoji depicts the graph displaying this number
statistics-hours-correct-reviews = 📈 { $percent }% правильних ({ $reviews })
statistics-hours-title = Погодинна розбивка
statistics-hours-subtitle = Продивитися процент успішності на кожну годину дня.
# shown when graph is empty
statistics-no-data = НЕМАЄ ДАНИХ
statistics-calendar-title = Календар

## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.

statistics-elapsed-time-seconds = { $amount }с
statistics-elapsed-time-minutes = { $amount }хв
statistics-elapsed-time-hours = { $amount }год
statistics-elapsed-time-days = { $amount }д
statistics-elapsed-time-months = { $amount }міс
statistics-elapsed-time-years = { $amount }р

##

statistics-average-for-days-studied = Середній показник за дні роботи з програмою
# This term is used in a variety of contexts to refers to the total amount of
# items (e.g., cards, mature cards, etc) for a given period, rather than the
# total of all existing items.
statistics-total = Разом
statistics-days-studied = Днів роботи з програмою
statistics-average-answer-time-label = Середній час відповіді
statistics-average = Середнє
statistics-median-interval = Медіанний інтервал
statistics-due-tomorrow = Пригадати завтра
# This string, ‘Daily load,’ appears in the ‘Future due’ table and represents a
# forecasted estimate of the number of cards expected to be reviewed daily in 
# the future. Unlike the other strings in the table that display actual data 
# derived from the current scheduling (e.g., ‘Average’, ‘Due tomorrow’),
# ‘Daily load’ is a projection based on the given data.
statistics-daily-load = Щоденне навантаження
# eg 5 of 15 (33.3%)
statistics-amount-of-total-with-percentage = { $amount } з { $total } ({ $percent }%)
statistics-average-over-period = Якби ви вчились щодня
statistics-reviews-per-day =
    { $count ->
        [one] { $count } перегляд/добу
        [few] { $count } перегляди/добу
       *[other] { $count } переглядів/добу
    }
statistics-minutes-per-day =
    { $count ->
        [one] { $count } хвилину/добу
        [few] { $count } хвилини/добу
       *[other] { $count } хвилин/добу
    }
statistics-cards-per-day =
    { $count ->
        [one] { $count } картка/добу
        [few] { $count } картки/добу
       *[other] { $count } карток/добу
    }
statistics-median-ease = Медіанне "легко"
statistics-median-difficulty = Медіанна складність
statistics-average-retrievability = Середня легкість пригадування
statistics-estimated-total-knowledge = Загальна кількість знань
statistics-save-pdf = Зберегти в форматі PDF
statistics-saved = Збережено.
statistics-stats = статистика
statistics-title = Статистика

## These strings are no longer used - you do not need to translate them if they
## are not already translated.

statistics-average-stability = Середня стійкість
statistics-average-interval = Середній інтервал
statistics-average-ease = Середня легкість
statistics-average-difficulty = Усереднена складність
