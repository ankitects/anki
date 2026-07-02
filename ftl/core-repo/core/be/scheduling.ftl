## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount } с
scheduling-answer-button-time-minutes = { $amount } хв
scheduling-answer-button-time-hours = { $amount } гадз
scheduling-answer-button-time-days = { $amount } д.
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
        [one] { $amount } хвіліна
        [few] { $amount } хвіліны
        [many] { $amount } хвілін
       *[other] { $amount } хвілін
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } гадзіна
        [few] { $amount } гадзіны
        [many] { $amount } гадзін
       *[other] { $amount } гадзін
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } дзень
        [few] { $amount } дні
        [many] { $amount } дзён
       *[other] { $amount } дзён
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } месяц
        [few] { $amount } месяцы
        [many] { $amount } месяцаў
       *[other] { $amount } месяцаў
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } год
        [few] { $amount } гады
        [many] { $amount } гадоў
       *[other] { $amount } гадоў
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    Наступная вучэбная картка будзе гатова праз { $unit ->
        [seconds]
            { $amount ->
                [one] { $amount } секунду
                [few] { $amount } секунды
                [many] { $amount } секунд
               *[other] { $amount } секунд
            }
        [minutes]
            { $amount ->
                [one] { $amount } хвіліну
                [few] { $amount } хвіліны
                [many] { $amount } хвілін
               *[other] { $amount } хвілін
            }
       *[hours]
            { $amount ->
                [one] { $amount } гадзіну
                [few] { $amount } гадзіны
                [many] { $amount } гадзін
               *[other] { $amount } гадзін
            }
    }
scheduling-learn-remaining =
    Сёння { $remaining ->
        [one] засталася адна картка
        [few] засталося { $remaining } карткі
        [many] засталося { $remaining } картак
       *[other] засталося { $remaining } картак
    } на вывучэнне пазней.
scheduling-congratulations-finished = Віншуем! Вы завяршылі гэту калоду на дадзены момант.
scheduling-buried-cards-found = Адна або больш картак былі адкладзены і будуць паказаны заўтра. Вы можаце { $unburyThem }, калі вы хочаце іх убачыць неадкладна.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = вярнуць іх
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = дапасаванае навучанне

## Scheduler upgrade

scheduling-update-soon = Anki 2.1 ідзе разам з новым планіроўшчыкам, які выпраўляе пэўную колькасць праблем, якія былі ў папярэдніх версіях Anki. Рэкамендуецца зрабіць абнаўленне.
scheduling-update-done = Планіроўшчык абноўлены.
scheduling-update-button = Абнавіць
scheduling-update-later-button = Пазней
scheduling-update-more-info-button = Даведацца больш

## Other scheduling strings

scheduling-at-least-one-step-is-required = Неабходны прынамсі адзін крок
scheduling-automatically-play-audio = Аўтаматычна прайграваць аўдыя
scheduling-bury-related-new-cards-until-the = Адкладваць звязаныя новыя карткі да наступнага дня
scheduling-bury-related-reviews-until-the-next = Адкладваць звязаныя перагляды да наступнага дня
scheduling-days = дзён
scheduling-description = Апісанне
scheduling-easy-bonus = Бонус для лёгкіх
scheduling-easy-interval = Інтэрвал для лёгкіх
scheduling-end = (канец)
scheduling-general = Агульныя
scheduling-graduating-interval = Інтэрвал да выпуску
scheduling-hard-interval = Інтэрвал для цяжкіх
scheduling-interval-modifier = Мадыфікатар інтэрвалу
scheduling-lapses = Недагляды
scheduling-lapses2 = недаглядаў
scheduling-learning = Вывучаюцца
scheduling-leech-action = Дзеянне з прыліплымі
scheduling-leech-threshold = Парог для прыліплых
scheduling-maximum-interval = Максімальны інтэрвал
scheduling-maximum-reviewsday = Максімум пераглядаў у дзень
scheduling-minimum-interval = Мінімальны інтэрвал
scheduling-mix-new-cards-and-reviews = Перамешваць карткі да перагляду з новымі
scheduling-new-cards = Новыя карткі
scheduling-new-cardsday = Новых картак у дзень
scheduling-new-interval = Інтэрвал для новых
scheduling-new-options-group-name = Назва новай групы параметраў:
scheduling-options-group = Група параметраў:
scheduling-order = Парадак
scheduling-parent-limit = (бацькоўскі ліміт: { $val })
scheduling-review = На перагляд
scheduling-reviews = Перагляды
scheduling-seconds = секунд
scheduling-set-all-decks-below-to = Прызначыць усе калоды ніжэй за { $val } да гэтай групы параметраў?
scheduling-set-for-all-subdecks = Задаць для ўсіх падкалод
scheduling-show-answer-timer = Паказваць час адказу
scheduling-show-new-cards-after-reviews = Паказваць карткі да перагляду перад новымі
scheduling-show-new-cards-before-reviews = Паказваць карткі да перагляду пасля новых
scheduling-show-new-cards-in-order-added = Паказваць новыя карткі ў парадку дадавання
scheduling-show-new-cards-in-random-order = Паказваць новыя карткі ў выпадковым парадку
scheduling-starting-ease = Пачатковая лёгкасць
scheduling-steps-in-minutes = Крокі (у хвілінах)
scheduling-steps-must-be-numbers = Крокі павінны быць лікамі.
scheduling-tag-only = Толькі цэтлік
scheduling-the-default-configuration-cant-be-removed = Прадвызначаная канфігурацыя не можа быць выдалена.
scheduling-deck-updated =
    { $count ->
        [one] { $count } калода абноўлена
        [few] { $count } калоды абноўлены
        [many] { $count } калод абноўлена
       *[other] { $count } калод абноўлена
    }.
scheduling-set-due-date-prompt =
    Праз колькі дзён паказаць { $cards ->
        [one] картку
        [few] карткі
        [many] картак
       *[other] картак
    }?
scheduling-set-due-date-prompt-hint =
    0 = сёння
    1! = заўтра+скінуць інтэрвал пераглядаў
    3-7 = выпадковы выбар паміж 3-7 дзён
scheduling-set-due-date-done =
    Зададзены { $cards ->
        [one] тэрмін { $cards } карткі
        [few] тэрміны { $cards } картак
        [many] тэрміны { $cards } картак
       *[other] тэрмін { $cards } картак
    }.
scheduling-graded-cards-done =
    { $cards ->
        [one] Ацэнена { $cards } картка.
        [few] Ацэнена { $cards } карткі.
        [many] Ацэнена { $cards } картак.
       *[other] Ацэнена { $cards } картак.
    }
scheduling-forgot-cards =
    { $cards ->
        [one] Забыта { $cards } картка
        [few] Забыты { $cards } карткі
        [many] Забыта { $cards } картак
       *[other] Забыта { $cards } картак
    }.
