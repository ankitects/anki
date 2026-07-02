# The date a card will be ready to review
statistics-due-date = Мерізімі
# The count of cards waiting to be reviewed
statistics-due-count = Бүгінге
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = #{ $number } жаңа

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } карта/минут
statistics-average-answer-time = { $average-seconds }с ({ statistics-cards-per-min })

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds = { $amount } секундта
statistics-in-time-span-minutes = { $amount } минутте
statistics-in-time-span-hours = { $amount } сағатта
statistics-in-time-span-days = { $amount } күнде
statistics-in-time-span-months = { $amount } айда
statistics-in-time-span-years = { $amount } жылда
# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { statistics-in-time-span-seconds } part should be pasted in from the English
# version unmodified.
statistics-studied-today =
    { $unit ->
        [seconds]
            Бүгін { statistics-cards }
            { statistics-in-time-span-seconds } оқылған
            ({ $secs-per-card }с/карта)
        [minutes]
            Бүгін { statistics-cards }
            { statistics-in-time-span-minutes } оқылған
            ({ $secs-per-card }с/карта)
        [hours]
            Бүгін { statistics-cards }
            { statistics-in-time-span-hours } оқылған
            ({ $secs-per-card }с/карта)
        [days]
            Бүгін { statistics-cards }
            { statistics-in-time-span-days } оқылған
            ({ $secs-per-card }с/карта)
        [months]
            Бүгін { statistics-cards }
            { statistics-in-time-span-months } оқылған
            ({ $secs-per-card }с/карта)
       *[years]
            Бүгін { statistics-cards }
            { statistics-in-time-span-years } оқылған
            ({ $secs-per-card }с/карта)
    }

##

statistics-cards = { $cards } карта
statistics-notes = { $notes } жазба
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews = { $reviews } қайталау
# This fragment of the tooltip in the FSRS simulation
# diagram (Deck options -> FSRS) shows the total number of
# cards that can be recalled or retrieved on a specific date.
statistics-memorized = { $memorized } есте сақталған
statistics-today-title = Бүгін
statistics-today-again-count = Қайталаулар саны:
statistics-today-type-counts = Үйрену: { $learnCount }, Қайталау: { $reviewCount }, Қайта үйрену: { $relearnCount }, Сүзілген: { $filteredCount }
statistics-today-no-cards = Бүгін карта оқылған жоқ.
statistics-today-no-mature-cards = Бүгін жетілген карта оқылған жоқ.
statistics-today-correct-mature = Жетілген картадағы дұрыс жауаптар: { $correct }/{ $total } ({ $percent }%)
statistics-counts-total-cards = Жалпы
statistics-counts-new-cards = Жаңа
statistics-counts-young-cards = Жас
statistics-counts-mature-cards = Жетілген
statistics-counts-suspended-cards = Кідіртілген
statistics-counts-buried-cards = Тығылған
statistics-counts-filtered-cards = Сүзілген
statistics-counts-learning-cards = Үйренуде
statistics-counts-relearning-cards = Қайта үйрену
statistics-counts-title = Карта Саны
statistics-counts-separate-suspended-buried-cards = Жекеленген кідіртілген/тығылған карталар

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

statistics-true-retention-title = Нақты Есте Сақтау
statistics-true-retention-subtitle = Аралығы  ≥ 1 күн карталар өтімі.
statistics-true-retention-tooltip = Егер сіз FSRS қолдансаңыз, есте сақталым көрсеткіші күткен мәніңізге жақын болады деп болжанады. Бір күндік деректер дәл болмауы мүмкін, сондықтан айлық деректерге қараған жөн.
statistics-true-retention-range = Аралық
statistics-true-retention-pass = Өту
statistics-true-retention-fail = Қате
# This will usually be the same as statistics-counts-total-cards
statistics-true-retention-total = Жалпы
statistics-true-retention-count = Санау
statistics-true-retention-retention = Сақталым
# This will usually be the same as statistics-counts-young-cards
statistics-true-retention-young = Жас
# This will usually be the same as statistics-counts-mature-cards
statistics-true-retention-mature = Жетілген
statistics-true-retention-all = Бәрі
statistics-true-retention-today = Бүгін
statistics-true-retention-yesterday = Кеше
statistics-true-retention-week = Өткен апта
statistics-true-retention-month = Өткен ай
statistics-true-retention-year = Өткен жыл
statistics-true-retention-all-time = Бүкіл уақыт
# If there are no reviews within a specific time period, the retention
# percentage cannot be calculated and is displayed as "N/A."
statistics-true-retention-not-applicable = N/A

##

statistics-range-all-time = бүкіл уақыт
statistics-range-1-year-history = соңғы 12 ай
statistics-range-all-history = бүкіл тарих
statistics-range-deck = колода
statistics-range-collection = жинақ
statistics-range-search = Іздеу
statistics-card-ease-title = Карта Жеңілдігі
statistics-card-difficulty-title = Карта Қиындығы
statistics-card-stability-title = Карта Тұрақтылығы
statistics-card-stability-subtitle = Түсірілімі 90%-ға түсетін іркіліс.
statistics-median-stability = Орташа тұрақтылық
statistics-card-retrievability-title = Карта Түсірілімі
statistics-card-ease-subtitle = Жеңілдік неғұрлым төмен, карта соғұрлым жиі кездеседі.
statistics-card-difficulty-subtitle2 = Қиындық неғұрлым жоғары, тұрақтылық соғұрлым баяу өседі.
statistics-retrievability-subtitle = Бүгін картаны еске түсіру ықтималдығы.
# eg "3 cards with 150-170% ease"
statistics-card-ease-tooltip = Жеңілдігі { $percent } { $cards } карта
statistics-card-difficulty-tooltip = Қиындығы { $percent } { $cards } карта
statistics-retrievability-tooltip = Түсірілімі { $percent } { $cards } карта
statistics-future-due-title = Болжам
statistics-future-due-subtitle = Келешектегі қайталау саны.
statistics-added-title = Қосылған
statistics-added-subtitle = Сіз қосқан жаңа карта саны.
statistics-reviews-count-subtitle = Сіз жауап берген сұрақ саны.
statistics-reviews-time-subtitle = Сұраққа жауап беруге кеткет уақыт.
statistics-answer-buttons-title = Жауап Түймелері
# eg Button: 4
statistics-answer-buttons-button-number = Түйме
# eg Times pressed: 123
statistics-answer-buttons-button-pressed = Басылған саны
statistics-answer-buttons-subtitle = Әр түймені басқан саны.
statistics-reviews-title = Қайталаулар
statistics-reviews-time-checkbox = Уақыт
statistics-in-days-single =
    { $days ->
        [2] Бүрсігүні
        [1] Ертең
        [0] Бүгін
       *[other] { $days } күнде
    }
statistics-in-days-range = { $daysStart }-{ $daysEnd } күнде
statistics-days-ago-single =
    { $days ->
        [2] Алдыңгүні
        [1] Кеше
       *[other] { $days } күн бұрын
    }
statistics-days-ago-range = { $daysStart }-{ $daysEnd } күн бұрын
statistics-running-total = Өспелі нәтиже
statistics-cards-due = мерізімі келген { $cards } карта
statistics-backlog-checkbox = Қорлану
statistics-intervals-title = Қайталар Аралықтары
statistics-intervals-subtitle = Карталарды қайта көрсету алдындағы іркілісі.
statistics-intervals-day-range = Аралығы { $daysStart }~{ $daysEnd } күн { $cards } карта
statistics-intervals-day-single = Аралығы { $day } күн { $cards } карта
statistics-stability-day-range = Тұрақтылығы { $daysStart }~{ $daysEnd } күн { $cards } карта
statistics-stability-day-single = Тұрақтылығы { $day } күн { $cards } карта
# hour range, eg "From 14:00-15:00"
statistics-hours-range = { $hourStart }:00~{ $hourEnd }:00 бастап
statistics-hours-correct = { $correct }/{ $total } дұрыс ({ $percent }%)
statistics-hours-correct-info = → ('Қайтадан' емес)
# the emoji depicts the graph displaying this number
statistics-hours-reviews = 📊 { $reviews } қайталау
# the emoji depicts the graph displaying this number
statistics-hours-correct-reviews = 📈 { $percent }% дұрыс ({ $reviews })
statistics-hours-title = Сағатқа Шаққанда
statistics-hours-subtitle = Әр сағаттың табыс жиілігі.
# shown when graph is empty
statistics-no-data = ДЕРЕК ЖОҚ
statistics-calendar-title = Күнтізбе

## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.

statistics-elapsed-time-seconds = { $amount }сек
statistics-elapsed-time-minutes = { $amount }м
statistics-elapsed-time-hours = { $amount }сағ
statistics-elapsed-time-days = { $amount }к
statistics-elapsed-time-months = { $amount }ай
statistics-elapsed-time-years = { $amount }ж

##

statistics-average-for-days-studied = Оқыған күндердегі орташа
# This term is used in a variety of contexts to refers to the total amount of
# items (e.g., cards, mature cards, etc) for a given period, rather than the
# total of all existing items.
statistics-total = Жалпы
statistics-days-studied = Оқыған күндер
statistics-average-answer-time-label = Орташа жауап уақыты
statistics-average = Орташа
statistics-median-interval = Орташа аралық
statistics-due-tomorrow = Ертеңге
# This string, ‘Daily load,’ appears in the ‘Future due’ table and represents a
# forecasted estimate of the number of cards expected to be reviewed daily in 
# the future. Unlike the other strings in the table that display actual data 
# derived from the current scheduling (e.g., ‘Average’, ‘Due tomorrow’),
# ‘Daily load’ is a projection based on the given data.
statistics-daily-load = Күнделікті жүктеме
# eg 5 of 15 (33.3%)
statistics-amount-of-total-with-percentage = { $amount }/{ $total } ({ $percent }%)
statistics-average-over-period = Мерізім орташасы
statistics-reviews-per-day = { $count } қайталау/күн
statistics-minutes-per-day = { $count } қайталау/күн
statistics-cards-per-day = { $count } карта/күн
statistics-median-ease = Орташа жеңілдік
statistics-median-difficulty = Орташа қиындық
statistics-average-retrievability = Орташа түсірілім
statistics-estimated-total-knowledge = Жалпы білім шамасы
statistics-save-pdf = PDF сақтау
statistics-saved = Сақталды.
statistics-stats = санақ
statistics-title = Статистика

## These strings are no longer used - you do not need to translate them if they
## are not already translated.

statistics-average-stability = Орташа тұрақтылық
statistics-average-interval = Орташа аралық
statistics-average-ease = Орташа жеңілдік
statistics-average-difficulty = Орташа қиындық
