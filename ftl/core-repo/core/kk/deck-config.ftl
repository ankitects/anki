### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks = { $decks } колодамен қолданылады
deck-config-default-name = Әдепкі
deck-config-title = Колода Баптаулары

## Daily limits section

deck-config-daily-limits = Күндік Шектеулер
deck-config-new-limit-tooltip =
    Күніне берілетін жаңа карта санының шегі. (Жаңа карта қолжетімді болғанда)
    Жаңа материал қысқа мерізімді қайталау жүктемесін арттыратындықтан, бұл әдетте
    қайталау шектеуінен ең дегенде 10x кіші болуы керек.
deck-config-review-limit-tooltip =
    Күніне көрсетілетін қайталау карталарының шектеу саны. 
    (егер карталар дайын болса).
deck-config-limit-deck-v3 =
    Егер ішкі колодалары бар колодамен жұмыс істеп жатсаңыз, әр ішкі колодаға қойылған шектеулер сол колодадан алынатын ең көп карта санын анықтайды.
    Ал таңдалған негізгі колоданың шектеулері көрсетілетін жалпы карта санын реттейді.
deck-config-limit-new-bound-by-reviews =
    Қайталау шектеуі жаңа карталар шектеуіне әсер етеді. Мысалы, егер қайталау
    шектеуі 200 болып орнатылса және 190 карта қайталауға дайын тұрса, ең көбі
    10 жаңа картаға ғана көрсетіледі. Егер қайталау шектеуі толып қалса, жаңа
    карталар мүлде көрсетілмейді.
deck-config-limit-interday-bound-by-reviews =
    Қайталау шектеуі күнаралық үйрену карталарына да әсер етеді. Шектеу
    қолданылғанда, алдымен күнаралық оқу карталары алынады, содан кейін
    қайталау карталары қосылады.
deck-config-tab-description =
    - `Баптама`: Шектеу осы баптаманы қолданатын колодаларға қойылады.
    - `Осы колода`: Шектеу тек осы колодаға қойылады.
     - `Бүгін ғана`: Осы колоданың шектеуін уақытша өзгерту.
deck-config-new-cards-ignore-review-limit = Қайталау шектеуі жаңа карталарға әсер етпейді
deck-config-new-cards-ignore-review-limit-tooltip =
    Әддетте қайталау шектеуі тек жаңа карталарға қойылады және шектеу жетілгенде
    жаңа карта көрсетілмейді. Осы баптау қосылғанда жаңа карталар шектеуге
    қарамастан көрсетіледі.
deck-config-apply-all-parent-limits = Шектеулер жоғарыдан басталады
deck-config-apply-all-parent-limits-tooltip =
    Әдетте ішкі колодадан оқығанда жоғарғы  колоданың күндік шектеуі қолданылмайды.
    Осы баптау қосылғанда шектеулер ең жоғарғы деңгейдегі колодадан басталады.
    Бұл бүкіл колодалар ағашына шектеу қоя тұра, жеке ішкі колодаларды оқығыңыз
    келсе пайдалы бола алады.
deck-config-affects-entire-collection = Бүкіл жинаққа әсер есер етеді.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Баптама
deck-config-deck-only = Мына колода
deck-config-today-only = Бүгін ғана

## New Cards section

deck-config-learning-steps = Үйрену қадамдары
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Шегерулер әдетте минутпен (мыс. `1m`) немесе күнмен (мыс. `2d`) беріледі, бірақ сағат (мыс. `1h`) және секунд (мыс. `30s`) та қолдануға болады.
deck-config-learning-steps-tooltip =
    Бос орынмен бөлінген бір немесе бірнеше кідіріс. Жаңа картада `Қайтадан`
    түймесін басқанда алғашқы кідіріс қолданылады (әдетте 1 минут).
    `Жақсы`түймесі келесі қадамға өткізеді (әдетте 10 минут).
    Барлық қадамдардан өткен соң, карта қайталау картасына айналып,
    басқа күні көрсетіледі. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip =
    Үйренудің соңғы қадамында `Жақсы` түймесі басылғанда картаны қайтадан
    көрсеткеніне дейін өтетін күн саны.
deck-config-easy-interval-tooltip =
    Үйренуден алып тастау үшін `Оңай` түймесі басылғанда картаны қайтадан
    көрсеткеніне дейін өтетін күн саны.
deck-config-new-insertion-order = Енгізу реті
deck-config-new-insertion-order-tooltip =
    Жаңа карталар қосылғанда оларға берілетін рет нөмірін (мерзім #) анықтайды.
    Мерзім нөмірі төмен карталар үйрену кезінде алдымен көрсетіледі.
    Бұл параметрді өзгерту бар жаңа карталардың қолданыстағыорындарын
    автоматты түрде қайта есептейді.
deck-config-new-insertion-order-sequential = Реттік (алдымен ескі карталар)
deck-config-new-insertion-order-random = Кездейсоқ
deck-config-new-insertion-order-random-with-v3 =
    v3 жоспарлаушысына сай, бұны реттік деп қалдырып, жаңа карта
    жинау ретін түзеткен жөн.

## Lapses section

deck-config-relearning-steps = Қайта үйрену қадамдары
deck-config-relearning-steps-tooltip =
    Нөл және одан да көп ірклістер, бостықтармен бөлінген. Әдетте,
    қайталау картасында `Қайтадан` түймесін басқанда карта 10 минуттен
    кейін қайта көрсетіледі. Еш іркіліс қойылмаса, карта қайта үйренуге
    өтпей, оның аралығы өзгереді. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip =
    Картаны жабысқақ деп белгілегенше `Қайтадан` түймесін басу саны.
    Жабысқақтар көп уақытыңызды алады. Карта жабысқақ деп танылса,
    оны қайта жазу, жою немесе оны есте сақтауға көмектесетін мнемоника
    құрыстару жөн болар.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `Тек Тамғалау`: картаға `жабысқақ` тамғасын беріп, қалқыма көрсету.
    
    `Картаны Кідірту`: Картаны тамғалаудың үстінде, қолмен
    тірілтілмейінше жасыру.

## Burying section

deck-config-bury-title = Тығу
deck-config-bury-new-siblings = Жаңа туыстарын тығу
deck-config-bury-review-siblings = Қайталау тектестерін тығу
deck-config-bury-interday-learning-siblings = Күнаралық үйрену туыстарын тығу
deck-config-bury-new-tooltip =
    Сол жазбаға тиесілі, аралығы 1 күннен асатын басқа `жаңа` карталарының (мыс. теріс
    карта, іргелес саңылау) келесі күнге дейін шегерілуі-шегерілмеуі.
deck-config-bury-review-tooltip = Сол жазбаға тиесілі басқа `қайталау` карталарының келесі күнге дейін шегерілу/шегерілмеуі.
deck-config-bury-interday-learning-tooltip =
    Сол жазбаға тиесілі, аралығы 1 күннен асатын басқа `үйрену`
    карталарының келесі күнге дейін шегерілу/шегерілмеуі.
deck-config-bury-priority-tooltip =
    Anki карталарды жинағанда, алдымен бір күн ішіндегі үйрену карталарын, кейін
    күнаралық үйрену карталарын, одан соң қайталау карталарын, соңында жаңа
    карталарды жинайды. Бұл тығу тәртібіне әсер етеді:
    
    - Егер тығудың барлық баптаулары қосулы болса, осы тізімде ертерек тұрған тектес
    карта көрсетіледі. Мысалы, қайталау картасы жаңа картадан бұрын көрсетіледі.
    - Тізімде кейін тұрған тектес карталар ертерек түрдегі карталарды тыға алмайды.
    Мысалы, егер жаңа карталарды тығу өшірілген болса және сіз жаңа картаны оқысаңыз,
    ол күн аралық үйрену немесе қайталау карталарын тыға алмайды, сондықтан бір
    сессияда қайталау тектесі де, жаңа тектесі де қатар көрінуі мүмкін.

## Gather order and sort order of cards

deck-config-ordering-title = Көрсету Реті
deck-config-new-gather-priority = Карталардың жиналған реті
deck-config-new-gather-priority-tooltip-2 =
    `Колода`: Карталарды жоғарғыдан бастап әр ішкі колодадан жинайды. Әр ішкі колодадан карталар
    орын ретінің артуы бойынша жиналады. Егер таңдалған колоданың күндік шегі толса, барлық ішкі
    колодалар тексерілмей тұрып жинау тоқтауы мүмкін. Бұл рет үлкен жинақтарда ең жылдам, әрі
    жоғарыда тұрған ішкі колодаларға басымдық беруге мүмкіндік береді.
    
    `Арту бойынша`: Карталарды орын нөмірінің арту ретімен (мерзім #) жинайды — әдетте ең ерте қосылғандар алдымен.
    
    `Кему бойынша`: Карталарды орын нөмірінің кему ретімен (мерзім #) жинайды — әдетте ең соңғы қосылғандар алдымен.
    
    `Кездейсоқ жазбалар`: Жазбаларды кездейсоқ таңдап, сол жазбаға тиесілі барлық карталарды жинайды.
    
    `Кездейсоқ карталар`: Карталарды кездейсоқ ретпен жинайды.
deck-config-new-card-sort-order = Жаңа карта жинау реті
deck-config-new-card-sort-order-tooltip-2 =
    `Карта түрі, сосын жиналу реті`: Карталарды карта түрінің нөмірі бойынша көрсетеді.
    Әр карта түрі ішіндегі жиналған ретімен беріледі.Тектес карталарды тығу өшірулі болса, бұл
    алдымен алды→арты, содан кейін арты→алды карталары көрсетіледі.
    Бұл бір жазбаға тиесілі барлық карталардың бір сессияда, бірақ бір-біріне тым жақын емес
    көрінуі үшін қолайлы.
    
    `Жиналу реті`: Карталарды жиналған күйінде дәл сол ретпен көрсетеді.
    Тектес карталарды тығу өшірулі болса, әдетте бір жазбаның барлық карталары бірінен
    кейін бірі көрсетіледі.
    
    `Карта түрі, сосын кездейсоқ`: Карталарды карта түрінің нөмірі бойынша көрсетеді, ал әр
    карта түрінің ішіндегі карталар кездейсоқ ретпен беріледі.
    Бұл рет тектес карталардың бір-біріне тым жақын шықпауын қаласаңыз, бірақ жалпы
    реттің кездейсоқ болғанын сақтағыңыз келсе пайдалы.
    
    `Кездейсоқ жазба, кейін карта түрі`: Жазбаларды кездейсоқ таңдап, сол жазбаға тиесілі
    барлық карталарды ретімен көрсетеді.
    
    `Кездейсоқ`: Карталарды кездейсоқ ретпен көрсетеді.
deck-config-new-review-priority = Жаңа/қайталау реті
deck-config-new-review-priority-tooltip = Қайталау карталарына қарағанда жаңа карталар қашан көрсетіледі.
deck-config-interday-step-priority = Күнаралық үйрену/қайталау реті
deck-config-interday-step-priority-tooltip =
    Күн ауысу шегінен өткен (қайта)үйрену карталарын көрсету уақыты.
    
    Қайталау шегі алдымен күнаралық үйрену карталарына, содан кейін қайталау
    карталарына қолданылады. Бұл баптау жиналған карталардың көрсетілу
    ретін басқарады, бірақ күн аралық үйрену карталары әрқашан бірінші жиналады.
deck-config-review-sort-order = Қайталауларды сұрыптау реті
deck-config-review-sort-order-tooltip =
    Әдепкі рет ең ұзақ күткен карталарды басымдықпен көрсетеді. Яғни,
    егер қайталау қоры жиналып қалса, алдымен ең көп күткендері шығады.
    Егер қайталау қоры бірнеше күннен артық уақыт алатындай үлкен болса
    немесе ішкі колодалар ретімен көргіңіз келсе, балама сұрыптау реттері
    ыңғайлырақ болуы мүмкін.
deck-config-display-order-will-use-current-deck =
    Anki көрсету ретін сіз оқуға таңдаған колодадан
    таңдайды, болуы мүмкін туындыларға қарамай.

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = Колода
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = Колода, сосын кездейсоқ жазбалар
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = Өсу реті
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = Кему реті
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = Кездейсоқ жазбалар
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = Кездейсоқ карталар
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = Карта түрі, сосын кездейсоқ
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = Кездейсоқ жазба, сосынн карта түрі
# Sort the cards randomly.
deck-config-sort-order-random = Кездейсоқ
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = Карта түрі, сосын жинау реті
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = Жинау реті
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = Қайталаулармен араластыру
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = Қайталаулардан кейін көрсету
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = Қайталауларға дейін көрсету
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = Мерзімі, сосын кездейсоқ
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = Мерізімі, сосын колода
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = Колода, сосын мерізімі
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = Аралықтардың өсуі
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = Аралыұтардың кемуі
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = Жеңілдіктің өсуі
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = Жеңілдіктің кемуі
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = Алдымен оңай карталар
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = Алдымен қиын карталар
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = Түсірілімнің өсуі
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = Түсірілімнің кемуі

## Timer section

deck-config-timer-title = Таймер
deck-config-maximum-answer-secs = Жауап беру уақытының шегі (секунд)
deck-config-maximum-answer-secs-tooltip =
    Бір қайталау үшін тіркелетін уақыттың ең жоғарғы шегі (секунд). Егер жауап
    беру уақыты осы шектен асып кетсе (мысалы, экраннан алыстап кеткен
    жағдайда), жазылатын уақыт орнатылған шекпен шектеледі.
deck-config-show-answer-timer-tooltip =
    Оқу экранында картаны үйрену жұмсалған уақыт өлшейтін
    сағатты көрсету.
deck-config-stop-timer-on-answer = Жауап бергенде бейнебет таймерін тоқтату
deck-config-stop-timer-on-answer-tooltip =
    Жауап көрсетіленде экрандағы таймерді тоқтату-тоқтатпау.
    Статистикаға әсер етпейді.

## Auto Advance section

deck-config-seconds-to-show-question = Сұрақты көрсету уақыты (секунд)
deck-config-seconds-to-show-question-tooltip-3 = Аутоілгеру қосылған кезінде сұрақ әрекетін атқарғанша өтетін уақыт. Өшіру үшін 0 деп қойыңыз.
deck-config-seconds-to-show-answer = Жауапты көрсету уақыты (секунд)
deck-config-seconds-to-show-answer-tooltip-2 = Аутоілгеру қосылған кезінде жауап әрекетін атқарғанша өтетін уақыт. Өшіру үшін 0 деп қойыңыз.
deck-config-question-action-show-answer = Жауабын Көрсету
deck-config-question-action-show-reminder = Ескерткішті Көрсету
deck-config-question-action = Сұрақ әрекеті
deck-config-question-action-tool-tip = Сұрақ көрсетіліп, уақыт өткеннен кейін атқарылатын әрекет.
deck-config-answer-action = Жауап әрекеті
deck-config-answer-action-tooltip-2 = Жауап көрсетіліп, уақыт өткеннен кейін атқарылатын әрекет.
deck-config-wait-for-audio-tooltip-2 = Аудио аяқтағаннан соң сұрақ әрекетін не жауап әрекетін автоматты түрде атқару.

## Audio section

deck-config-audio-title = Аудио
deck-config-disable-autoplay = Аудионы автоматты түрде ойнамау
deck-config-disable-autoplay-tooltip =
    Қосылғанда Anki аудионы автоматты түрде ойнамайды.
    Аудио сүретін басу немесе Қайталау әрекетін қолдану арқылы қолмен ойнатуға болады.
deck-config-skip-question-when-replaying = Жауапты қайта қарағанда сұрақты өткізу
deck-config-always-include-question-audio-tooltip =
    Қайталау әрекеті қосылып, картаның жауап жағын қарағанда сұрақ
    аудиосын қосу-қоспау.

## Advanced section

deck-config-advanced-title = Қосымша
deck-config-maximum-interval-tooltip =
    Қайталау картсы күте алатын шекті күн саны. Қайталау шектеуге жеткенде
    `Қиын`, `Жақсы`, `Оңай` түгел бірдей аралық береді.
    Бұл аралық неғұрлым қысқа, жүктемеңіз соғұрлым жоғары.
deck-config-starting-ease-tooltip = Жаңа карталардың жеңілдік көбейткіші. Әдетте `Жақсы` түймесі жаңадан оқылған картаға бұрынғы шегерілімнен 2.5 есе үлкен шегерілім береді.
deck-config-easy-bonus-tooltip =
    `Оңай` дегенде қайталау картасының аралығына қолданылатын қосымша
    көбейткіш.
deck-config-interval-modifier-tooltip =
    Бұл көбейткіш барлық қайталауларға қолданылады, ал шағын түзетулер
    арқылы Anki-дің жоспарлауын сақтырақ немесе белсендірек етуге болады.
    Осы баптауды өзгертпес бұрын нұсқаулықты көріңіз.
deck-config-hard-interval-tooltip = `Қиын` дегенде қайталау аралығына қолданылатын көбейткіш.
deck-config-new-interval-tooltip = `Қайтадан` дегенде қайталау аралығына қолданылатын көбейткіш.
deck-config-minimum-interval-tooltip = `Қайтадан` дегенде қайталау картасына берілетін ең қысқа аралық.
deck-config-custom-scheduling = Өзгертпелі жоспарлау
deck-config-custom-scheduling-tooltip = Бүкіл жинаққа әсер етеді. Жауапкершілік өзіңізде!

## Easy Days section.

deck-config-easy-days-title = Жеңіл Күндер
deck-config-easy-days-monday = Дүйсенбі
deck-config-easy-days-tuesday = Сейсенбі
deck-config-easy-days-wednesday = Сәрсенбі
deck-config-easy-days-thursday = Бейсенбі
deck-config-easy-days-friday = Жұма
deck-config-easy-days-saturday = Сенбі
deck-config-easy-days-sunday = Жексенбі
deck-config-easy-days-normal = Қалыпты
deck-config-easy-days-reduced = Ықшамдалған
deck-config-easy-days-minimum = Минимум
deck-config-easy-days-no-normal-days = Кемінде бір күн '{ deck-config-easy-days-normal }' болуы керек.
deck-config-easy-days-change = FSRS баптаулырындағы `{ deck-config-reschedule-cards-on-change }` қосылмаса, бар қайталаулар жаңартылмайды.

## Adding/renaming

deck-config-add-group = Баптама Қосу
deck-config-name-prompt = Атау
deck-config-rename-group = Баптаманы Қайта Атау
deck-config-clone-group = Баптаманы Көшіру

## Removing

deck-config-remove-group = Баптаманы Жою
deck-config-will-require-full-sync =
    Сұралған өзгеріс біржақты үйлесімді қажет етеді. Басқа құрылғыда өзгерістер
    жасап, оларды осы құрыда үйлестірмесеңіз, жалғастырмас бұрын оны істеңіз.
deck-config-confirm-remove-name = { $name } жою?

## Other Buttons

deck-config-save-button = Сақтау
deck-config-save-to-all-subdecks = Барлық Туынды Колодасына Сақтау
deck-config-save-and-optimize = Барлық Баптаманы Оңтайландыру
deck-config-revert-button-tooltip = Осы баптауды қалпына келтіру?

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Anki 2.1.41+ қолдауы
deck-config-description-new-handling-hint =
    Енгізілген мәтінді Markdown ретінде өңдейді және HTML кодын
    тазартады. Қосылғанда, сипаттама құттықтау экранында да көрсетіледі.
    Anki 2.1.40 және одан төмен нұсқаларда Markdown қарапайым мәтін
    ретінде көрінеді.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped = Аталық колодасының осы шектеуді жауып тастайтын { $cards } карта шектеуі бар.
deck-config-reviews-too-low = Күніне { $cards } жаңа карта қосаты болсаңыз, қайталау шектеуіңіз кемінде { $expected } болуға тиіс.
deck-config-learning-step-above-graduating-interval = Бітіру аралығы кем дегенде соңғы үйрену аралығымен бірдей болуы керек.
deck-config-good-above-easy = Оңай аралығы кем дегенде бітіру аралығымен бірдей болуы керек.
deck-config-relearning-steps-above-minimum-interval = Ең кіші босау аралығы кем дегенде соңғы үйрену аралығымен бірдей болуы керек.
deck-config-maximum-answer-secs-above-recommended = Әр сұрақ қысқа болғанда, Anki қайталауларыңызды тиімдірек жоспарлай алады.
deck-config-too-short-maximum-interval = 6 айдан қысқа шекті аралық қоймаған жөн.
deck-config-ignore-before-info = FSRS баптауларын оңтайландыруға (шамамен) { $included }/{ $totalCards } карта қолданылады.

## Selecting a deck

deck-config-which-deck = Қай колоданың баптауларын көрсетесіз?

## Messages related to the FSRS scheduler

deck-config-updating-cards = Карталарды жаңарту: { $current_cards_count }/{ $total_cards_count }...
deck-config-invalid-parameters = Ұсынылған FSRS баптаулары жарамсыз. Босы қалдырыңыз не қалыпты баптауларды қолданыңыз.
deck-config-not-enough-history = Бұл әрекетті орындау үшін қайталау тарихы жеткіліксіз.
deck-config-must-have-400-reviews = { $count } қайталау ғана табылды. Бұл әрекет үшін кемінде 400 қайталау қажет.
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = FSRS параметрлері
deck-config-compute-optimal-weights = FSRS параметрлерін оңтайландыру
deck-config-optimize-button = Осы Баптаманы Оңтайландыру
# Indicates that a given function or label, provided via the "text" variable, operates slowly.
deck-config-slow-suffix = ( $text ) (баяу)
deck-config-compute-button = Есептеу
deck-config-ignore-before = Осыдан бұрын қайталанған карталарды елемеу:
deck-config-time-to-optimize = Көп уақыт өтті. Барлық Қалыпты Оңтайландыру түймесін басқан жөн.
deck-config-evaluate-button = Бағалау
deck-config-desired-retention = Күткен меңгерім
deck-config-historical-retention = Тарихи меңгерім
deck-config-smaller-is-better = Сандар неғұрлым кіші болса, қайталау тарихыңызға соғұрлым жақсы сәйкес келеді.
deck-config-steps-too-large-for-fsrs = FSRS қосылғанда 1 күн не одан да үлкен қадам қоймаған жөн.
deck-config-get-params = Баптауларды Алу
deck-config-complete = { $num }% аяқталған.
deck-config-iterations = Итерация: { $count }...
deck-config-reschedule-cards-on-change = Өзгерісте карталарды қайта жоспарлау
deck-config-fsrs-tooltip =
    Бүкіл жинаққа әсер етеді.
    
    Free Spaced Repetition Scheduler (FSRS) — Anki-дің бұрынғы SuperMemo 2 (SM-2) алгоритміне балама жоспарлау жүйесі.
    Картаны ұмыту ықтималдығын дәлірек есептеу арқылы, сол бір уақыт ішінде көбірек материал есте сақтауға көмектеседі.
    
    Бұл параметр барлық баптамаға ортақ.
deck-config-desired-retention-tooltip =
    Әдетте Anki карталарды қайта көрсеткенде оларды 90% ықтималдықпен есте сақтайтындай
    етіп жоспарлайды. Бұл мәнді арттырсаңыз, есте сақтау ықтималдығын көтеру үшін карталар
    жиірек көрсетіледі. Мәнді төмендетсеңіз, карталар сирек көрсетіледі де, көбірек ұмытасыз.
    Бұл параметрді өзгерткенде сақ болыңыз: жоғары мәндер жүктемені айтарлықтай арттырады,
    ал төмен мәндер көп материалды ұмытудан көңіл түсіруі мүмкін.
deck-config-desired-retention-tooltip2 = Мәлімет терезесіндегі жүктеме мөлшері болжамды есеп. Жоғарырық дәлдік үшін симуляторды қолданыңыз.
deck-config-historical-retention-tooltip =
    Қайталау тарихының бір бөлігі жоқ болған жағдайда, FSRS олқылықтарды толықтыруға мәжбүр
    болады. Әдетте, ол бұрынғы қайталауларда материалдың 90% меңгерілген деп есептейді.
    Егер бұрынғы меңгерім деңгейіңіз 90%-дан айтарлықтай жоғары немесе төмен болса, осы баптауды реттеу FSRS-ке жетіспейтін қайталауларды дәлірек шамалауға мүмкіндік береді.
    
    Қайталау тарихыңыздың толық болмауының екі себебі болуы мүмкін:
    1. 'Осыдан бұрын қайталанған карталарды елемеу:' баптауын қолдандыңыз .
    2. Орын босату мақсатында қайталау журналдарын бұрын өшіргеніңіз немесе материалды басқа SRS
    бағдарламасынан импорттадыңыз.
    
    Екіншісі өте сирек кездеседі, сондықтан алғашқы опцияны қолданбасаңыз, бұл баптауды
    өзгерту әдетте қажет емес.
deck-config-weights-tooltip2 =
    FSRS баптаулары карталар кестесіне әсер етеді. Anki әдепкі баптаулармен бастайды. Төмендегі баптауды  пайдаланып, осы баптаманы қолданатын колодалардың параметрлерін үлгеріміңізге барынша сәйкес
    келетіндей оңтайландыра аласыз.
deck-config-reschedule-cards-on-change-tooltip =
    Бүкіл жинаққа әсер етеді және сақталмайды.
    
    Бұл опция FSRS-ті қосқанда немесе баптауларын оңтайландырғанда карталардың мерзім күндері
    өзгерер-өзгермесін анықтайды. Әдетте қайта жоспарлау өшірулі: болашақ қайталаулар жаңа
    0жоспарлауды қолданады, бірақ жүктемеде бірден өзгеріс болмайды. Қайта жоспарлау қосылса,
    карталардың мерзім күндері жаңартылады.
deck-config-reschedule-cards-warning =
    Меңгерім деңгейіне байланысты бұл баптау көп картаның бірден мерзімді болуына
    әкелуі мүмкін, сондықтан SM-2 жүйесінен алғаш ауысқанда қолдану ұсынылмайды.
    
    Бұл опцияны сақтықпен пайдаланыңыз: ол әр картаға қайталау жазбасын қосады
    және жинақ көлемін ұлғайтады.
deck-config-ignore-before-tooltip-2 =
    Егер орнатылса, берілген күнге дейін қайталанған карталар FSRS параметрлерін оңтайландыру кезінде есепке алынбайды.
    Бұл басқа біреудің жоспарлау деректерін импорттаған болсаңыз немесе жауап түймелерін пайдалану тәсілін өзгерткен жағдайда пайдалы.
deck-config-compute-optimal-weights-tooltip2 =
    «Оңтайландыру» түймесін басқанда, FSRS қайталу тарихыңызды талдап, жадыңызға және оқып жатқан
    мазмұныңызға ең қолайлы баптаулар жасайды. Егер колодалардың субъектив қиындығы аса әркелкі
    болса, оларды бөлек баптамаларға бөлген жөн, өйткені жеңіл және қиын колодаларға арналған
    баптаулар әртүрлі болады.
    Баптауларды жиі оңтайландырудың қажеті жоқ, бірнеше айда бір рет жеткілікті.
    
    Әдетте, баптаулар ағымдағы баптаманы қолданатын барлық колодалардың қайталу тарихы негізінде
    есептеледі. Қаласаңыз, есептеуге дейін іздеу шарттарын өзгертіп, параметрлерді оңтайландыруға қай
    карталар қолданылатынын реттей аласыз.
deck-config-please-save-your-changes-first = Алдымен өзгерістеріңізді сақтаңыз.
deck-config-workload-factor-change =
    Болжам жүктеме: { $factor }x
    (қалаулы жүктеменің { $previousDR }%-ымен салыстырғанда)
deck-config-workload-factor-unchanged = Бұл мән неғұрлым жоғары, карталар соғұрлым жиі көрсетіледі.
deck-config-desired-retention-too-low = Қалаулы меңгерім өте төмен, бұл аралықтарды өте ұзақ етуі мүмкін.
deck-config-desired-retention-too-high = Қалаулы меңгерім өте жоғары, бұл аралықтарды өте қысқа етуі мүмкін.
deck-config-percent-of-reviews = { $reviews } қайталаулардың { $pct }%-ы
deck-config-percent-input = { $pct }%
# This message appears during FSRS parameter optimization.
deck-config-checking-for-improvement = Жетілдірулерді тексеру...
deck-config-optimizing-preset = Баптаманы оңтайландыру { $current_count }/{ $total_count }...
deck-config-fsrs-must-be-enabled = Алдымен FSRS қосылуы керек.
deck-config-fsrs-params-optimal = Қазір FSRS баптаулары оңтайлы болып көріненді.
deck-config-fsrs-params-no-reviews = Қайталау табылмады. Осы баптаманы оңтайландырғыңыз келетін барлық колодаға (ішкілеріне де) орнатылғанына көз жеткізіп, қайтадан байқап көріңіз.
deck-config-wait-for-audio = Аудионы күту
deck-config-show-reminder = Ескерткішті Көрсету
deck-config-answer-again = Қайтадан Деу
deck-config-answer-hard = Қиын Деу
deck-config-answer-good = Жақсы Деу
deck-config-days-to-simulate = Есептелетін күндер саны
deck-config-desired-retention-below-optimal = Қалаулы меңгеріміңіз тиімді мөлшерден кем. Оны көтерген жөн.
# Description of the y axis in the FSRS simulation
# diagram (Deck options -> FSRS) showing the total number of
# cards that can be recalled or retrieved on a specific date.
deck-config-fsrs-simulator-experimental = FSRS Симуляторы (Эксперименттік)
deck-config-fsrs-simulate-desired-retention-experimental = Қалаулы Меңгерім FSRS Симуляторы (эксперименттік)
deck-config-fsrs-simulate-save-preset = Оңтайландырудан кейін, симуляторды жеккенге дейін колодаңызды сақтаңыз.
deck-config-fsrs-desired-retention-help-me-decide-experimental = Шешуге көмектес (Эксперименттік)
deck-config-additional-new-cards-to-simulate = Есептелетін қосымша жаңа карталар
deck-config-simulate = Симуляциялау
deck-config-clear-last-simulate = Соңғы Симуляцияны Тазалау
deck-config-fsrs-simulator-radio-count = Қайталаулар
deck-config-advanced-settings = Қосымша Баптаулар
deck-config-smooth-graph = Тегіс график
deck-config-suspend-leeches = Жабысқақтарды кідірту
deck-config-save-options-to-preset = Өзгерістерді Баптамаға Сақтау
deck-config-save-options-to-preset-confirm = Ағымдағы қалыптағы баптауларды симулятордағы баптауларға ауыстырасыз ба?
# Radio button in the FSRS simulation diagram (Deck options -> FSRS) selecting
# to show the total number of cards that can be recalled or retrieved on a
# specific date.
deck-config-fsrs-simulator-radio-memorized = Жатталған
deck-config-fsrs-simulator-radio-ratio = Уақыт / Жатталған арасалмағы
# $time here is pre-formatted e.g. "10 Seconds" 
deck-config-fsrs-simulator-ratio-tooltip = Жатталған картаға { $time }

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.

# Checkbox
deck-config-health-check = Оңтайландыру кезінде саулықты тексеру
# Message box showing the result of the health check
deck-config-fsrs-bad-fit-warning =
    Жүйелік тексеріс:
    FSRS үшін жадыңызды болжау қиын. Ұсыныстар:
    
    - Үнемі ұмытылатын карталарды тоқтатыңыз немесе қайта тұжырымдаңыз.
    - Жауап түймелерін дәйекті қолданыңыз. "Қиын" дегені өтетін баға, сәтсіздік емес екенін ескеріңіз.
    - Түсініп алып, содан кейін ғана жаттаңыз.
    
    Осы кеңестерді ұстансаңыз, алдағы бірнеше айда нәтижелер әдетте жақсарады.
# Message box showing the result of the health check
deck-config-fsrs-good-fit =
    Саулық Тексерісі:
    FSRS жадыңызға жақсы бейімделе алады.

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-unable-to-determine-desired-retention = Unable to determine a minimum recommended retention.
deck-config-predicted-minimum-recommended-retention = Minimum recommended retention: { $num }
deck-config-compute-minimum-recommended-retention = Minimum recommended retention
deck-config-compute-optimal-retention-tooltip4 =
    This tool will attempt to find the desired retention value 
    that will lead to the most material learnt, in the least amount of time. The calculated number can serve as a reference
    when deciding what to set your desired retention to. You may wish to choose a higher desired retention if you’re 
    willing to invest more study time to achieve it. Setting your desired retention lower than the minimum
    is not recommended, as it will lead to a higher workload, because of the high forgetting rate.
deck-config-plotted-on-x-axis = (X білігіне түсірілген)
deck-config-a-100-day-interval = 100 күн аралық { $days } күн болады.
deck-config-fsrs-simulator-y-axis-title-time = Review Time/Day
deck-config-fsrs-simulator-y-axis-title-count = Review Count/Day
deck-config-fsrs-simulator-y-axis-title-memorized = Memorized Total
deck-config-bury-siblings = Bury siblings
deck-config-do-not-bury = Do not bury siblings
deck-config-bury-if-new = Bury if new
deck-config-bury-if-new-or-review = Bury if new or review
deck-config-bury-if-new-review-or-interday = Bury if new, review, or interday learning
deck-config-bury-tooltip =
    Siblings are other cards from the same note (eg forward/reverse cards, or
    other cloze deletions from the same text).
    
    When this option is off, multiple cards from the same note may be seen on the same
    day. When enabled, Anki will automatically *bury* siblings, hiding them until the next
    day. This option allows you to choose which kinds of cards may be buried when you answer
    one of their siblings.
    
    When using the V3 scheduler, interday learning cards can also be buried. Interday
    learning cards are cards with a current learning step of one or more days.
deck-config-seconds-to-show-question-tooltip = When auto advance is activated, the number of seconds to wait before revealing the answer. Set to 0 to disable.
deck-config-answer-action-tooltip = The action to perform on the current card before automatically advancing to the next one.
deck-config-wait-for-audio-tooltip = Wait for audio to finish before automatically revealing answer or next question.
deck-config-ignore-before-tooltip =
    If set, reviews before the provided date will be ignored when optimizing & evaluating FSRS parameters.
    This can be useful if you imported someone else's scheduling data, or have changed the way you use the answer buttons.
deck-config-compute-optimal-retention-tooltip =
    This tool assumes you're starting with 0 cards, and will attempt to calculate the amount of material you'll
    be able to retain in the given time frame. The estimated retention will greatly depend on your inputs, and
    if it significantly differs from 0.9, it's a sign that the time you've allocated each day is either too low
    or too high for the amount of cards you're trying to learn. This number can be useful as a reference, but it
    is not recommended to copy it into the desired retention field.
deck-config-health-check-tooltip1 = This will show a warning if FSRS struggles to adapt to your memory.
deck-config-health-check-tooltip2 = Health check is performed only when using Optimize Current Preset.
deck-config-compute-optimal-retention = Compute minimum recommended retention
deck-config-predicted-optimal-retention = Minimum recommended retention: { $num }
deck-config-weights-tooltip =
    FSRS parameters affect how cards are scheduled. Anki will start with default parameters. Once
    you've accumulated 1000+ reviews, you can use the option below to optimize the parameters to best
    match your performance in decks using this preset.
deck-config-compute-optimal-weights-tooltip =
    Once you've done 1000+ reviews in Anki, you can use the Optimize button to analyze your review history,
    and automatically generate parameters that are optimal for your memory and the content you're studying.
    If you have decks that vary wildly in difficulty, it is recommended to assign them separate presets, as
    the parameters for easy decks and hard decks will be different. There is no need to optimize your parameters
    frequently - once every few months is sufficient.
    
    By default, parameters will be calculated from the review history of all decks using the current preset. You can
    optionally adjust the search before calculating the parameters, if you'd like to alter which cards are used for
    optimizing the parameters.
deck-config-compute-optimal-retention-tooltip2 =
    This tool assumes that you’re starting with 0 learned cards, and will attempt to find the desired retention
    value that will lead to the most material learnt, in the least amount of time. This number can be used as a
    reference when deciding what to set your desired retention to. You may wish to choose a higher desired retention,
    if you’re willing to trade more study time for a greater recall rate. Setting your desired retention lower than
    the minimum is not recommended, as it will lead to more work without benefit.
deck-config-compute-optimal-retention-tooltip3 =
    This tool assumes that you’re starting with 0 learned cards, and will attempt to find the desired retention value 
    that will lead to the most material learnt, in the least amount of time. To accurately simulate your learning process, 
    this feature requires a minimum of 400+ reviews. The calculated number can serve as a reference when deciding what to 
    set your desired retention to. You may wish to choose a higher desired retention, if you’re willing to trade more study 
    time for a greater recall rate. Setting your desired retention lower than the minimum is not recommended, as it will 
    lead to a higher workload, because of the high forgetting rate.
deck-config-seconds-to-show-question-tooltip-2 = When auto advance is activated, the number of seconds to wait before revealing the answer. Set to 0 to disable.
deck-config-invalid-weights = Parameters must be either left blank to use the defaults, or must be 17 comma-separated numbers.
deck-config-fsrs-on-all-clients =
    Please ensure all of your Anki clients are Anki(Mobile) 23.10+ or AnkiDroid 2.17+. FSRS will
    not work correctly if one of your clients is older.
deck-config-optimize-all-tip = You can optimize all presets at once by using the dropdown button next to "Save".
