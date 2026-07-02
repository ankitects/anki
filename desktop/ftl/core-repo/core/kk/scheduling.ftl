## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount }сек
scheduling-answer-button-time-minutes = { $amount }м
scheduling-answer-button-time-hours = { $amount }сағ
scheduling-answer-button-time-days = { $amount }к
scheduling-answer-button-time-months = { $amount }ай
scheduling-answer-button-time-years = { $amount }ж

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds = { $amount } сек
scheduling-time-span-minutes = { $count } минут
scheduling-time-span-hours = { $amount } сағат
scheduling-time-span-days = { $amount } күн
scheduling-time-span-months = { $amount } ай
scheduling-time-span-years = { $amount } жыл

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    { $unit ->
        [seconds]
            { $amount ->
               *[other] Келесі үйрену картасы { $amount } секундтан кейін дайын болады.
            }
        [minutes]
            { $amount ->
               *[other] Келесі үйрену картасы { $amount } минуттан кейін дайын болады.
            }
       *[hours]
            { $amount ->
               *[other] Келесі үйрену картасы { $amount } сағаттан кейін дайын болады.
            }
    }
scheduling-learn-remaining = Бүгінге { $remaining } үйрену қалды.
scheduling-congratulations-finished = Құттықтаймыз! Әзірше колоданы бітірдіңіз.
scheduling-today-review-limit-reached =
    Бүгінгі қайталау шегіне жеттіңіз, бірақ әлі де қаралуы керек
    карталар бар. Есте сақтауды жақсарту үшін күнделікті лимитті
    баптаулардан көбейтуді қарастырыңыз
scheduling-today-new-limit-reached =
    Жаңа карталар әлі де бар, бірақ күнделікті шектеуге жеттіңіз.
    Шектеуді баптаулардан арттыра аласыз, бірақ есте сақтаңыз:
    жаңа карталарды көбірек енгізсеңіз, қысқа мерзімді қайталау
    жүктемесі де артады.
scheduling-buried-cards-found = Бір не одан да көп карта тығылды және ертең көрсетіледі. Қазір көргіңіз келсе, { $unburyThem } болады.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = оларды ақтару
scheduling-how-to-custom-study = Әдепкі жоспардан тыс оқығыңыз келсе, { $customStudy } мүмкіндігін пайдалана аласыз.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = жеке оқу

## Scheduler upgrade

scheduling-update-soon = Anki 2.1 жаңа жоспарлаушымен бірге келеді, ол алдыңғы Anki нұсқаларындағы бірнеше мәселені түзетеді. Жаңартуды ұсынамыз.
scheduling-update-done = Жоспарлаушы сәтті жаңартылды.
scheduling-update-button = Жаңарту
scheduling-update-later-button = Сосын
scheduling-update-more-info-button = Көбірек
scheduling-update-required = Жинақ V2 жоспарлаушысына жаңартылуы керек. Жалғастыру алдында { scheduling-update-more-info-button } таңдаңыз.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = Аудионы қайта ойнатқанда әрқашан сұрақ жағын қосу
scheduling-at-least-one-step-is-required = Кем дегенде бір қадам керек.
scheduling-automatically-play-audio = Автоматты түрде аудионы ойнату
scheduling-bury-related-new-cards-until-the = Түйіндес карталарды келесі күнге дейін тығу.
scheduling-bury-related-reviews-until-the-next = Түйіндес қайталауларды келесі күнге дейін тығу.
scheduling-days = күн
scheduling-description = Сипаттама
scheduling-easy-bonus = Оңай бонус
scheduling-easy-interval = Оңай аралық
scheduling-end = (ақыр)
scheduling-general = Жалпы
scheduling-graduating-interval = Бітіру аралығы
scheduling-hard-interval = Қиын аралық
scheduling-ignore-answer-times-longer-than = Осы уақыттан ұзақ жауаптарды елемеу:
scheduling-interval-modifier = Аралық өзгерткіші
scheduling-lapses = Босаулар
scheduling-lapses2 = босаулар
scheduling-learning = Үйрену
scheduling-leech-action = Жабысқақ әрекеті
scheduling-leech-threshold = Жабысқақ шегі
scheduling-maximum-interval = Ең үлкен аралық
scheduling-maximum-reviewsday = Күніне ең көп қайталау
scheduling-minimum-interval = Ең қысқа аралық
scheduling-mix-new-cards-and-reviews = Жаңа карталар мен қайталауларды араластыру
scheduling-new-cards = Жаңа Карталар
scheduling-new-cardsday = Жаңа карталар/күн
scheduling-new-interval = Жаңа аралық
scheduling-new-options-group-name = Жаңа баптаулар тобының атауы:
scheduling-options-group = Баптаулар тобы:
scheduling-order = Рет
scheduling-parent-limit = (аталық шектеуі: { $val })
scheduling-reset-counts = Қайталау мен босалу санын қалпына келтіру
scheduling-restore-position = Мүмкін жерде бастапқы орнын қалпына келтіру
scheduling-review = Қайталау
scheduling-reviews = Қайталаулар
scheduling-seconds = секунд
scheduling-set-all-decks-below-to = { $val } мәнінен төмен колодаларды осы топқа қою?
scheduling-set-for-all-subdecks = Барлық туынды колодасына орнату
scheduling-show-answer-timer = Жауап таймерін көрсету
scheduling-show-new-cards-after-reviews = Жаңа карталарды қайталаулардан кейін көрсету
scheduling-show-new-cards-before-reviews = Жаңа карталарды қайталауларға дейін көрсету
scheduling-show-new-cards-in-order-added = Жаңа карталарды қосу ретінде көрсету
scheduling-show-new-cards-in-random-order = Жаңа карталарды кездейсоқ ретте көрсету
scheduling-starting-ease = Бастау оңайлығы
scheduling-steps-in-minutes = Қадам (минут)
scheduling-steps-must-be-numbers = Қадамдар сан болуы керек.
scheduling-tag-only = Тек Тамға
scheduling-the-default-configuration-cant-be-removed = Әдепкі пішімдемені жою мүмкін емес.
scheduling-your-changes-will-affect-multiple-decks = Өзгерістеріңіз бірнеше колодаға әсер етеді. Тек ағымдағы колоданы өзгертуіңіз келсе, алдымен жаңа баптаулар тобын қосыңыз.
scheduling-deck-updated = { $count } колода жаңартылды.
scheduling-set-due-date-prompt =
    { $cards ->
        [one] Картаны неше күннен кейін көрсету?
       *[other] Карталарды неше күннен кейін көрсету?
    }
scheduling-set-due-date-prompt-hint =
    0 = бүгін
    1! = ертең + аралықты 1 ету
    3-7 = 3-7 күннің кездейсоқ таңдауы
scheduling-set-due-date-done =
    { $cards ->
        [one] Картаның мерізімін орнату.
       *[other] Карталардың мерізімін орнату.
    }
scheduling-graded-cards-done = { $cards } карта бағаланды.
scheduling-forgot-cards = { $cards } картаны қалпына келтіру.
