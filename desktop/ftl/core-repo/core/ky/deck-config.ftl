### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    { $decks ->
        [one] { $decks } топтомдо колдонулат
       *[other] { $decks } топтомдордо колдонулат
    }
deck-config-default-name = Демейки
deck-config-title = Топтомдун жөндөөлөрү

## Daily limits section

deck-config-daily-limits = Күнүмдүк лимиттер
deck-config-new-limit-tooltip =
    Эгер жаңы карталар бар болсо, бир күндө киргизиле турган жаңы карталардын эң көп саны.
    Жаңы материал сиздин кыска мөөнөттүү кайталоо жүгүңүздү көбөйткөндүктөн,
    бул сан адатта кайталоо чегиңизден кеминде 10 эсе аз болушу керек.
deck-config-review-limit-tooltip =
    Эгер карталар кайталоого даяр болсо,
    бир күндө көрсөтүлө турган кайталоо карталарынын эң көп саны.
deck-config-limit-deck-v3 =
    Ичинде ички топтомдору бар топтомду окуп жатканда, ар бир ички топтомго коюлган чектер ошол топтомдон алына турган карталардын эң көп санын көзөмөлдөйт.
    Тандалган топтомдун чектер жалпы көрсөтүлө турган карталардын санын көзөмөлдөйт.
deck-config-limit-new-bound-by-reviews = Кайталоо чеги жаңы чекке таасир этет. Мисалы, эгер кайталоо чегиңиз 200гө коюлса жана сизде 190 кайталоо күтүп турса, эң көп дегенде 10 жаңы карта киргизилет. Эгер кайталоо чегиңизге жеткен болсо, жаңы карталар көрсөтүлбөйт.
deck-config-limit-interday-bound-by-reviews = Кайталоо чеги ошондой эле күндөр аралык үйрөнүү карталарына да таасир этет. Чек колдонулганда, адегенде күндөр аралык үйрөнүү карталары, андан кийин кайталоо карталары чогултулат.
deck-config-tab-description =
    - 'Алдын ала коюлган': Чек ушул алдын ала коюлган жөндөөнү колдонгон бардык топтомдорго тиешелүү.
    - 'Бул топтом': Чек ушул топтомго гана тиешелүү.
    - 'Бүгүн гана': Бул топтомдун чегине убактылуу өзгөртүү киргизүү.
deck-config-new-cards-ignore-review-limit = Жаңы карталар кайталоо лимитин эске албайт
deck-config-new-cards-ignore-review-limit-tooltip = Адатта, кайталоо чеги жаңы карталарга да тиешелүү, жана кайталоо чегине жеткенде жаңы карталар көрсөтүлбөйт. Эгер бул жөндөө иштетилсе, жаңы карталар кайталоо чегине карабастан көрсөтүлөт.
deck-config-apply-all-parent-limits = Лимиттер жогорудан башталат
deck-config-apply-all-parent-limits-tooltip = Адатта, жогорку деңгээлдеги топтомдун күнүмдүк лимиттер анын ички топтомунан окуп жатсаңыз, колдонулбайт. Эгер бул жөндөө иштетилсе, чектер(лимит) жогорку деңгээлдеги топтомдон башталат, бул жеке ички топтомдорду окуп, бирок топтом дарагындагы карталарга жалпы чекти сактагыңыз келсе, пайдалуу болушу мүмкүн.
deck-config-affects-entire-collection = Бүткүл коллекцияга таасир этет.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Алдын ала коюлган
deck-config-deck-only = Бул топтом
deck-config-today-only = Бүгүн гана

## New Cards section

deck-config-learning-steps = Үйрөнүү кадамдары
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Кечиктирүүлөр адатта мүнөт (мис., '1м') же күн (мис., '2к') менен көрсөтүлөт, бирок саат (мис., '1с') жана секунд (мис., '30с') да колдоого алынат.
deck-config-learning-steps-tooltip =
    Бир же бир нече кечиктирүү, боштуктар менен бөлүнгөн.
    Биринчи кечиктирүү жаңы картада 'Кайра' баскычын басканда колдонулат жана ал баштапкы абалда 1 мүнөт.
    'Жакшы' баскычы кийинки кадамга өткөрөт, ал баштапкы абалда 10 мүнөт. Бардык кадамдар өтүлгөндөн кийин, карта кайталоо картасы болуп, башка күнү пайда болот. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip =
    Акыркы үйрөнүү кадамында 'Жакшы' баскычы басылгандан кийин,
    картаны кайра көрсөтүүгө чейин күтө турган күндөрдүн саны.
deck-config-easy-interval-tooltip = 'Оңой' баскычы картаны дароо үйрөнүүдөн алып салуу үчүн колдонулгандан кийин, картаны кайра көрсөтүүгө чейин күтө турган күндөрдүн саны.
deck-config-new-insertion-order = Киргизүү ирети
deck-config-new-insertion-order-tooltip = Жаңы карталарды кошкондо аларга дайындалуучу орунду (мөөнөттү #) көзөмөлдөйт. Мөөнөт номери төмөн болгон карталар окуу учурунда биринчи көрсөтүлөт. Бул жөндөөнү өзгөртүү жаңы карталардын учурдагы ордун автоматтык түрдө жаңыртылат.
deck-config-new-insertion-order-sequential = Ирети менен (эң эски карталар биринчи)
deck-config-new-insertion-order-random = Туш келди
deck-config-new-insertion-order-random-with-v3 = v3 пландоочу менен, муну ирети менен калтырып, анын ордуна жаңы карталарды чогултуу тартибин тууралаган жакшы.

## Lapses section

deck-config-relearning-steps = Кайра үйрөнүү кадамдары
deck-config-relearning-steps-tooltip = Нөл же андан көп кечиктирүүлөр, боштуктар менен бөлүнгөн. Адатта, кайталоо картасында 'Кайра' баскычын басуу аны 10 мүнөттөн кийин кайра көрсөтөт. Эгер эч кандай кечиктирүү көрсөтүлбөсө, карта кайра үйрөнүүгө кирбестен, интервалы өзгөрөт. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip = Кайталоо картасы "татаал" деп белгиленгенге чейин 'Кайра' баскычын канча жолу басуу керектиги. Татаал карталар – бул көп убактыңызды алган карталар. Карта "татаал" деп белгиленгенде, аны кайра жазып, өчүрүп же эстеп калууга жардам берүүчү мнемоника ойлоп табуу жакшы идея.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    'Тег гана': Эскертмеге 'leech' тегин кошот жана калкыма терезени көрсөтөт.
    'Картаны токтотуу': Эскертмени белгилөөдөн тышкары, картаны кол менен токтотуудан чыгарылганга чейин жашырат.

## Burying section

deck-config-bury-title = Көрсөтпөй туруу
deck-config-bury-new-siblings = Жаңы карталардын жуптарын эртеңкиге жылдыр
deck-config-bury-review-siblings = Кайталоодогу карталардын жуптарын эртеңкиге жылдыр
deck-config-bury-interday-learning-siblings = Бир нече күнгө созулган үйрөнүү карталарынын жуптарын эртеңкиге жылдыр
deck-config-bury-new-tooltip = Бир эле эскертменин башка 'жаңы' карталары (мис., тескери карталар, жанаша жашыруун сөздөр) кийинки күнгө чейин кечиктирилеби.
deck-config-bury-review-tooltip = Бир эле эскертменин башка 'кайталоо' карталары кийинки күнгө чейин кечиктирилеби.
deck-config-bury-interday-learning-tooltip = Бир эле эскертменин интервалы 1 күндөн ашык болгон башка 'үйрөнүү' карталары кийинки күнгө чейин кечиктирилеби.
deck-config-bury-priority-tooltip =
    Anki карталарды тандаганда, аларды төмөнкү иретте чыгарат:
    Бир эле күндүн ичинде кайра-кайра көрүлүп жаткан үйрөнүүчү карталар, бир күндөн ашык убакытка созулуп жаттап жүргөн карталар, мурун жатталган, бирок кайра эске түшүрүү үчүн кайталанып жаткан карталар, жаңы биринчи ирет ачылган карталар. Бул тартип “кийинкиге жылдырып туруу” (жашырып коюу) функциясына да таасир этет:
    
    - Эгер жылдыруу өзгөчөлүктөрүнүн баары күйүп турган болсо, ушул тизмеде кайсысы эртерээк келсе — ошол эле темага тиешелүү карта биринчи көрсөтүлөт. Мисалы: мурда жатталып, кайра кайталанып жаткан карта жаңы картага караганда биринчи көрсөтүлөт.
    - Тизмеде кийин турган карта түрлөрү өзүнөн мурун турган түрдөгү карталарды жашыра албайт. Мисалы: эгер жаңы карталарды жашыруу өчүрүлгөн болсо, сен жаңы картаны көрсөң, ал мурун окула баштаган же кайра кайталанып жаткан карталарды жашырбайт. Ошондуктан бир эле сөздүн мурун окуган версиясы менен жаңы версиясын бир сессия ичинде көрүп калышың мүмкүн.

## Gather order and sort order of cards

deck-config-ordering-title = Көрсөтүү тартиби
deck-config-new-gather-priority = Жаңы карталарды чогултуу тартиби
deck-config-new-gather-priority-tooltip-2 =
    'Топтом': Ар бир ички топтомдон карталарды ирети менен чогултат, жогорудан баштап. Ар бир ички топтомдон карталар өсүү тартибинде чогултулат. Тандалган топтомдун күнүмдүк чегине жеткенде, бардык ички топтомдор текшерилгенге чейин чогултуу токтошу мүмкүн. Бул тартип чоң коллекцияларда эң тез жана жогору жактагы ички топтомдорго артыкчылык берүүгө мүмкүндүк берет.
    
    'Өсүү тартибинде': Карталарды өсүү тартибиндеги орду боюнча чогултат, бул адатта эң эски кошулгандар биринчи. 
    
    'Кемүү тартибинде': Карталарды кемүү тартибиндеги орду боюнча чогултат (мөөнөтү #), бул адатта эң акыркы кошулгандар биринчи. 
    
    'Кокус эскертмелер': Эскертмелерди кокус тандап, анан анын бардык карталарын алат. 
    
    'Кокус карталар': Карталарды кокус тартипте чогултат.
deck-config-new-card-sort-order = Жаңы карталарды иреттөө тартиби
deck-config-new-card-sort-order-tooltip-2 =
    `Карта түрү боюнча, андан кийин жыйналган тартипте`: Карталар алгач алардын түрүнүн номерине жараша көрсөтүлөт.
    Ар бир карта түрүнүн ичиндеги карталар — жыйналган тартипте көрсөтүлөт.
    Эгер бир темага тиешелүү башка формадагы карталарды (мисалы: алды → арты) жашыруу өчүрүлгөн болсо, анда алды-арты формалары толук бүтүп чыккандан кийин гана арты-алды формалары көрсөтүлөт.
    Бул — бир эле маалыматка тиешелүү карталарды бир сессияда көрүүнү каалаган, бирок алардын бири-бирине өтө жакын чыгып кетишин каалабаган учурларга ылайыктуу.
    
    `Жыйналган тартипте`: Карталар дал жыйналган тартипте көрсөтүлөт`: Эгер бир теманын башка формаларын жашыруу өчүрүлгөн болсо, бир эле темага тиешелүү бардык карталар бири-биринин артынан дароо көрсөтүлөт.
    
    `Карта түрү боюнча, андан кийин туш келди`: Карталар алгач түрү боюнча бөлүнөт, бирок ар бир түрдүн ичиндеги карталар туш келди (аралаш) иретте көрсөтүлөт.
    Бул — бир эле темага тиешелүү карталар бир сессия ичинде чыкса да, биринин артынан бири дароо чыкпасын деген учурларда ыңгайлуу.
    
    `Туш келди тема, андан кийин карта түрү`: Алгач туш келди бир тема (note) тандалат, андан кийин ошол темага тиешелүү бардык карталар өз тизмеси боюнча кезеги менен көрсөтүлөт.
    
    `Туш келди`: Карталар толугу менен туш келди (аралаш) тартипте көрсөтүлөт.
deck-config-new-review-priority = Жаңы/кайталоо тартиби
deck-config-new-review-priority-tooltip = Жаңы карталарды кайталоо карталарына карата качан көрсөтүү керек.
deck-config-interday-step-priority = Күндөр аралык үйрөнүү/кайталоо тартиби
deck-config-interday-step-priority-tooltip = Күн чегинен өткөн (кайра) үйрөнүү карталарын качан көрсөтүү керек. Кайталоо чеги дайыма алгач күндөр аралык үйрөнүү карталарына, андан кийин кайталоо карталарына колдонулат. Бул жөндөө чогултулган карталардын көрсөтүлүү тартибин көзөмөлдөйт, бирок күндөр аралык үйрөнүү карталары дайыма биринчи чогултулат.
deck-config-review-sort-order = Кайталоону иреттөө тартиби
deck-config-review-sort-order-tooltip =
    Баштапкы тартип эң көп күткөн карталарга артыкчылык берет, ошондуктан эң көп күткөндөр биринчи пайда болот. 
    Эгер сизде тазалоого бир нече күндөн ашык убакыт кете турган чоң карыз болсо, же ички топтом тартибинде карталарды көргүңүз келсе, альтернативдүү иреттөө тартиптерин артык көрүшүңүз мүмкүн.
deck-config-display-order-will-use-current-deck = Anki сиз окуу үчүн тандаган топтомдун көрсөтүү тартибин колдонот, жана анын ичиндеги ички топтомдорду эмес.

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = Топтом
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = Топтом, анан кокус эскертмелер
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = Өсүү тартибиндеги орду
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = Кемүү тартибиндеги орду
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = Кокус эскертмелер
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = Кокус карталар
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = Карта түрү, анан рандом(туш келди)
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = Рандомдук эскертме, анан карта түрү
# Sort the cards randomly.
deck-config-sort-order-random = Кокус / Туш келди
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = Карта түрү, анан чогултулган тартип
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = Чогултулган тартип
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = Кайталоолор менен аралаштыруу
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = Кайталоолордон кийин көрсөтүү
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = Кайталоолорго чейин көрсөтүү
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = Аткаруу мөөнөтү, анан кокус
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = Аткаруу мөөнөтү, анан топтом
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = Топтом, анан аткаруу мөөнөтү
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = Өсүүчү аралыктар
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = Кемүүчү аралыктар
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = Өсүүчү оңойлук
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = Оңойлукту азайтуу
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = Оңой карталар биринчи
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = Татаал карталар биринчи
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = Эстеп чыгуу жеңилдигине жараша өсүү тартибинде
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = Эстеп чыгуу жеңилдигине жараша төмөндөө тартибинде

## Timer section

deck-config-timer-title = Таймерлер
deck-config-maximum-answer-secs = Жооп үчүн максималдуу секунд
deck-config-maximum-answer-secs-tooltip = Бир кайталоону жазуу үчүн максималдуу секунд саны. Эгер жооп бул убакыттан ашып кетсе (мисалы, экрандан алыстап кеткендиктен), алынган убакыт сиз койгон чек катары жазылат.
deck-config-show-answer-timer-tooltip = "Окуу" экранында, ар бир картаны окууга кетирген убактыңызды эсептеген таймерди көрсөтүү.
deck-config-stop-timer-on-answer = Жооп бергенде экрандагы таймерди токтотуу
deck-config-stop-timer-on-answer-tooltip = Жооп ачылганда экрандагы таймерди токтотуу керекпи. Бул статистикага таасир этпейт.

## Auto Advance section

deck-config-seconds-to-show-question = Суроону көрсөтүү үчүн секунд
deck-config-seconds-to-show-question-tooltip-3 = Авто-өтүү иштетилгенде, суроо аракетин колдонуудан мурун күтө турган секунддардын саны. Өчүрүү үчүн 0 деп коюңуз.
deck-config-seconds-to-show-answer = Жоопту көрсөтүү үчүн секунд
deck-config-seconds-to-show-answer-tooltip-2 = Авто-өтүү иштетилгенде, жооп аракетин колдонуудан мурун күтө турган секунддардын саны. Өчүрүү үчүн 0 деп коюңуз.
deck-config-question-action-show-answer = Жообун көрсөтүү
deck-config-question-action-show-reminder = Эстеткичти көрсөтүү
deck-config-question-action = Суроо аракети
deck-config-question-action-tool-tip = Суроо көрсөтүлүп, убакыт өткөндөн кийин аткарыла турган аракет.
deck-config-answer-action = Жооп аракети
deck-config-answer-action-tooltip-2 = Жооп көрсөтүлүп, убакыт өткөндөн кийин аткарыла турган аракет.
deck-config-wait-for-audio-tooltip-2 = Суроо же жооп аракетин автоматтык түрдө колдонуудан мурун аудионун бүтүшүн күтүү.

## Audio section

deck-config-audio-title = Аудио
deck-config-disable-autoplay = Аудиону автоматтык түрдө ойнотпоо
deck-config-disable-autoplay-tooltip = Иштетилгенде, Anki аудиону автоматтык түрдө ойнотпойт. Аны аудио сүрөтчөсүн басуу аркылуу же "Кайра ойнотуу" аракетин колдонуу менен кол менен ойнотсо болот.
deck-config-skip-question-when-replaying = Жоопту кайра ойнотуп жатканда суроону өткөрүп жиберүү
deck-config-always-include-question-audio-tooltip = "Кайра ойнотуу" аракети картанын жооп жагын карап жатканда колдонулганда, суроонун аудиосу кошулушу керекпи.

## Advanced section

deck-config-advanced-title = Өркүндөтүлгөн
deck-config-maximum-interval-tooltip = Кайталоо картасы күтө турган максималдуу күндөрдүн саны. Кайталоолор чекке жеткенде, 'Оор', 'Жакшы' жана 'Оңой' баары бирдей кечиктирүүнү берет. Муну канчалык кыска койсоңуз, жумуш жүгүңүз ошончолук чоң болот.
deck-config-starting-ease-tooltip = Жаңы карталар баштаган оңойлук көбөйткүчү. Адатта, жаңы үйрөнүлгөн картадагы 'Жакшы' баскычы кийинки кайталоону мурунку кечиктирүүдөн 2.5 эсе кечиктирет.
deck-config-easy-bonus-tooltip = Кайталоо картасын 'Оңой' деп баалаганда, анын интервалына колдонулуучу кошумча көбөйткүч.
deck-config-interval-modifier-tooltip = Бул көбөйткүч бардык кайталоолорго колдонулат, жана кичине тууралоолор Anki'ни пландоодо консервативдүү же агрессивдүү кылуу үчүн колдонулушу мүмкүн. Бул жөндөөнү өзгөртүүдөн мурун колдонмону караңыз.
deck-config-hard-interval-tooltip = 'Оор' деп жооп бергенде кайталоо интервалына колдонулуучу көбөйткүч.
deck-config-new-interval-tooltip = 'Кайра' деп жооп бергенде кайталоо интервалына колдонулуучу көбөйткүч.
deck-config-minimum-interval-tooltip = 'Кайра' деп жооп бергенден кийин кайталоо картасына берилген минималдуу интервал.
deck-config-custom-scheduling = Жекече пландаштыруу
deck-config-custom-scheduling-tooltip = Бүткүл коллекцияга таасир этет. Өз тобокелчилигиңиз менен колдонуңуз!

## Easy Days section.

deck-config-easy-days-title = Оңой күндөр
deck-config-easy-days-monday = Дш
deck-config-easy-days-tuesday = Шш
deck-config-easy-days-wednesday = Шр
deck-config-easy-days-thursday = Бш
deck-config-easy-days-friday = Жм
deck-config-easy-days-saturday = Иш
deck-config-easy-days-sunday = Жш
deck-config-easy-days-normal = Кадимки
deck-config-easy-days-reduced = Азайтылган
deck-config-easy-days-minimum = Минималдуу
deck-config-easy-days-no-normal-days = Жок дегенде бир күн '{ deck-config-easy-days-normal }' деп коюлушу керек.
deck-config-easy-days-change = FSRS жөндөөлөрүндө '{ deck-config-reschedule-cards-on-change }' иштетилбесе, учурдагы кайталоолор кайра пландаштырылбайт.

## Adding/renaming

deck-config-add-group = Алдын ала жөндөө кошуу
deck-config-name-prompt = Аты
deck-config-rename-group = Алдын ала жөндөөнүн атын өзгөртүү
deck-config-clone-group = Алдын ала жөндөөнү клондоо

## Removing

deck-config-remove-group = Алдын ала жөндөөнү өчүрүү
deck-config-will-require-full-sync = Суралган өзгөртүү бир тараптуу шайкештештирүүнү талап кылат. Эгер башка түзмөктө өзгөртүүлөрдү киргизип, аларды бул түзмөккө шайкештештире элек болсоңуз, улантуудан мурун муну жасаңыз.
deck-config-confirm-remove-name = { $name } өчүрүлсүнбү?

## Other Buttons

deck-config-save-button = Сактоо
deck-config-save-to-all-subdecks = Бардык ички топтомдорго сактоо
deck-config-save-and-optimize = Бардык алдын ала жөндөөлөрдү оптималдаштыруу
deck-config-revert-button-tooltip = Бул жөндөөнү баштапкы маанисине кайтарасызбы?

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Anki 2.1.41+ иштетүүсү
deck-config-description-new-handling-hint = Киргизилгенди markdown катары кабыл алып, HTML киргизүүнү тазалайт. Иштетилгенде, сүрөттөмө куттуктоо экранында да көрсөтүлөт. Markdown Anki 2.1.40 жана андан төмөнкү версияларда текст катары көрүнөт.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    { $cards ->
        [one] Түпкү топтомдун { $cards } карта чеги бар, ал бул чекти алмаштырат.
       *[other] Түпкү топтомдун { $cards } карталар чеги бар, ал бул чекти алмаштырат.
    }
deck-config-reviews-too-low =
    { $cards ->
        [one] Күн сайын { $cards } жаңы карта кошуп жатсаңыз, кайталоо чегиңиз жок дегенде { $expected } болушу керек.
       *[other] Күн сайын { $cards } жаңы карталар кошуп жатсаңыз, кайталоо чегиңиз жок дегенде { $expected } болушу керек.
    }
deck-config-learning-step-above-graduating-interval = "Аяктаган" интервал жок дегенде акыркы үйрөнүү кадамыңыздай узун болушу керек.
deck-config-good-above-easy = "Оңой" интервал жок дегенде "аяктаган" интервалдай узун болушу керек.
deck-config-relearning-steps-above-minimum-interval = Минималдуу "унутуу" интервалы жок дегенде акыркы кайра үйрөнүү кадамыңыздай узун болушу керек.
deck-config-maximum-answer-secs-above-recommended = Ар бир суроону кыска кармасаңыз, Anki кайталоолоруңузду натыйжалуураак пландаштыра алат.
deck-config-too-short-maximum-interval = 6 айдан аз максималдуу интервал сунушталбайт.
deck-config-ignore-before-info = FSRS параметрлерин оптималдаштыруу үчүн (болжол менен) { $included }/{ $totalCards } карта колдонулат.

## Selecting a deck

deck-config-which-deck = Кайсы топтомдун жөндөөлөрүн көрсөткүңүз келет?

## Messages related to the FSRS scheduler

deck-config-updating-cards = Карталар жаңыртылууда: { $current_cards_count }/{ $total_cards_count }...
deck-config-invalid-parameters = Берилген FSRS параметрлери жараксыз. Баштапкы параметрлерди колдонуу үчүн аларды бош калтырыңыз.
deck-config-not-enough-history = Бул операцияны аткаруу үчүн кайталоо тарыхы жетишсиз.
deck-config-must-have-400-reviews =
    { $count ->
        [one] Болгону { $count } кайталоо табылды. Бул операция үчүн сизде жок дегенде 400 кайталоо болушу керек.
       *[other] Болгону { $count } кайталоолор табылды. Бул операция үчүн сизде жок дегенде 400 кайталоо болушу керек.
    }
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = FSRS параметрлери
deck-config-compute-optimal-weights = FSRS параметрлерин оптималдаштыруу
deck-config-optimize-button = Учурдагы алдын ала жөндөөнү оптималдаштыруу
# Indicates that a given function or label, provided via the "text" variable, operates slowly.
deck-config-slow-suffix = { $text } (жай)
deck-config-compute-button = Эсептөө
deck-config-ignore-before = Мурун кайталанган карталарды эске албоо
deck-config-time-to-optimize = Бир топ убакыт өттү - "Бардык алдын ала жөндөөлөрдү оптималдаштыруу" баскычын колдонуу сунушталат.
deck-config-evaluate-button = Баалоо
deck-config-desired-retention = Каалаган эске сактоо
deck-config-historical-retention = Тарыхый эске сактоо
deck-config-smaller-is-better = Кичирээк сандар сиздин кайталоо тарыхыңызга жакшыраак дал келерин көрсөтөт.
deck-config-steps-too-large-for-fsrs = FSRS иштетилгенде, 1 күн же андан көп кадамдар сунушталбайт.
deck-config-get-params = Параметрлерди алуу
deck-config-complete = { $num }% аяктады.
deck-config-iterations = Итерация: { $count }...
deck-config-reschedule-cards-on-change = Өзгөртүүдө карталарды кайра пландаштыруу
deck-config-fsrs-tooltip =
    Бүткүл коллекцияга таасир этет.
    
    Эркин Аралыктуу Кайталоо Пландоочусу (FSRS) – бул Anki'нин эски SuperMemo 2 (SM-2) алгоритмине альтернатива.
    Картаны унутуп калуу ыктымалдыгын так аныктоо менен, ал сизге ошол эле убакыттын ичинде көбүрөөк материалды эстеп калууга жардам берет. Бул жөндөө бардык алдын ала коюлган жөндөөлөр үчүн жалпы болуп саналат.
deck-config-desired-retention-tooltip = Адатта, Anki карталарды кайра кайталоого келгенде аларды эстеп калуу ыктымалдыгыңыз 90% болгондой пландаштырат. Эгер бул маанини жогорулатсаңыз, Anki эстеп калуу мүмкүнчүлүгүңүздү жогорулатуу үчүн карталарды тез-тез көрсөтөт. Эгер маанини төмөндөтсөңүз, Anki карталарды азыраак көрсөтөт, жана сиз алардын көбүн унутуп каласыз. Муну тууралоодо этият болуңуз - жогорку маанилер жумуш жүгүңүздү бир топ жогорулатат, ал эми төмөнкү маанилер көп материалды унутуп калганда көңүлдү чөктүрүшү мүмкүн.
deck-config-desired-retention-tooltip2 = Маалымат кутучасында берилген жумуш жүгүнүн маанилери болжолдуу эсеп. Тагыраак деңгээл үчүн симуляторду колдонуңуз.
deck-config-historical-retention-tooltip =
    Кайталоо тарыхыңыздын бир бөлүгү жок болгондо, FSRS боштуктарды толтурушу керек. Адатта, ал ошол эски кайталоолорду жасаганда, сиз материалдын 90% эстеп калдыңыз деп болжолдойт. Эгер эски эске сактоо деңгээлиңиз 90%дан кыйла жогору же төмөн болсо, бул жөндөөнү тууралоо FSRS'ке жок кайталоолорду жакшыраак болжолдоого мүмкүндүк берет. 
    
    Кайталоо тарыхыңыз эки себептен улам толук эмес болушу мүмкүн:
    1. "Мурун кайталанган карталарды эске албоо" жөндөөсүн колдонуп жатканыңыз үчүн.
    2. Мурда орун бошотуу үчүн кайталоо журналдарын өчүргөнүңүз үчүн же башка SRS программасынан материал импорттогонуңуз үчүн. 
    
    Экинчиси өтө сейрек кездешет, андыктан биринчи жөндөөнү колдонбосоңуз, бул жөндөөнү тууралоонун кажети жок болушу мүмкүн.
deck-config-weights-tooltip2 = FSRS параметрлери карталардын кантип пландаштырыларына таасир этет. Anki баштапкы параметрлер менен баштайт. Төмөнкү жөндөөнү колдонуп, бул алдын ала коюлган жөндөөнү колдонгон топтомдордогу аткарууңузга эң жакшы дал келген параметрлерди оптималдаштыра аласыз.
deck-config-reschedule-cards-on-change-tooltip = Бүткүл коллекцияга таасир этет жана сакталбайт. Бул жөндөө FSRS'ти иштеткенде же параметрлерди оптималдаштырганда карталардын аткаруу мөөнөттөрү өзгөртүлөбү же жокпу, ошону көзөмөлдөйт. Баштапкы жөндөө - карталарды кайра пландаштырбоо: келечектеги кайталоолор жаңы пландаштырууну колдонот, бирок жумуш жүгүңүздө дароо өзгөрүү болбойт. Эгер кайра пландаштыруу иштетилсе, карталардын аткаруу мөөнөттөрү өзгөртүлөт.
deck-config-reschedule-cards-warning =
    Сиз каалаган эске сактоо деңгээлине жараша, бул көп сандагы карталардын мөөнөтү келип калышына алып келиши мүмкүн, андыктан SM-2ден биринчи жолу өтүп жатканда сунушталбайт.
    
    Бул жөндөөнү этияттык менен колдонуңуз, анткени ал ар бир картаңызга кайталоо жазуусун кошуп, коллекцияңыздын өлчөмүн көбөйтөт.
deck-config-ignore-before-tooltip-2 =
    Эгер коюлса, берилген датага чейин кайталанган карталар FSRS параметрлерин оптималдаштырууда эске алынбайт. 
    Бул башка бирөөнүн пландаштыруу маалыматтарын импорттогон болсоңуз же жооп баскычтарын колдонуу ыкмаңызды өзгөрткөн болсоңуз, пайдалуу болушу мүмкүн.
deck-config-compute-optimal-weights-tooltip2 =
    Оптималдаштыруу баскычын басканда, FSRS кайталоо тарыхыңызды талдап, эс тутумуңузга жана сиз окуп жаткан мазмунга оптималдуу болгон параметрлерди түзөт. Эгер топтомдоруңуздун субъективдүү татаалдыгы ар кандай болсо, аларга өзүнчө алдын ала жөндөөлөрдү дайындоо сунушталат, анткени оңой жана татаал топтомдор үчүн параметрлер ар башка болот. 
    
    Параметрлериңизди тез-тез оптималдаштыруунун кажети жок - бир нече айда бир жолу жетиштүү. 
    
    Адатта, параметрлер учурдагы алдын ала жөндөөнү колдонгон бардык топтомдордун кайталоо тарыхынан эсептелет. Кааласаңыз, параметрлерди эсептөөдөн мурун, параметрлерди оптималдаштыруу үчүн кайсы карталар колдонуларын өзгөрткүңүз келсе, издөөнү тууралай аласыз.
deck-config-please-save-your-changes-first = Сураныч, адегенде өзгөртүүлөрүңүздү сактаңыз.
deck-config-workload-factor-change = Болжолдуу жумуш жүгү: { $factor }x ({ $previousDR }% каалаган эске сактоого салыштырмалуу)
deck-config-workload-factor-unchanged = Бул маани канчалык жогору болсо, карталар сизге ошончолук тез-тез көрсөтүлөт.
deck-config-desired-retention-too-low = Сиз каалаган эске сактоо деңгээли өтө төмөн, бул өтө узун аралыктарга алып келиши мүмкүн.
deck-config-desired-retention-too-high = Сиз каалаган эске сактоо деңгээли өтө жогору, бул өтө кыска аралыктарга алып келиши мүмкүн.
deck-config-percent-of-reviews =
    { $reviews ->
        [one] { $reviews } кайталоонун { $pct }%
       *[other] { $reviews } кайталоолорунун { $pct }%
    }
deck-config-percent-input = { $pct }%
# This message appears during FSRS parameter optimization.
deck-config-checking-for-improvement = Жакшыруу текшерилүүдө...
deck-config-optimizing-preset = Алдын ала жөндөө оптималдаштырылууда { $current_count }/{ $total_count }...
deck-config-fsrs-must-be-enabled = Адегенде FSRS иштетилиши керек.
deck-config-fsrs-params-optimal = FSRS параметрлери учурда оптималдуу көрүнөт.
deck-config-fsrs-params-no-reviews = Кайталоолор табылган жок. Бул алдын ала жөндөө сиз оптималдаштыргыңыз келген бардык топтомдорго (ички топтомдорду кошкондо) дайындалганын текшерип, кайра аракет кылыңыз.
deck-config-wait-for-audio = Аудиону күтүү
deck-config-show-reminder = Эстеткичти көрсөтүү
deck-config-answer-again = Кайра деп жооп берүү
deck-config-answer-hard = Оор деп жооп берүү
deck-config-answer-good = Жакшы деп жооп берүү
deck-config-days-to-simulate = Симуляциялоо үчүн күндөр
deck-config-desired-retention-below-optimal = Сиз каалаган эске сактоо деңгээли оптималдуудан төмөн. Аны жогорулатуу сунушталат.
# Description of the y axis in the FSRS simulation
# diagram (Deck options -> FSRS) showing the total number of
# cards that can be recalled or retrieved on a specific date.
deck-config-fsrs-simulator-experimental = FSRS Симулятору (Эксперименталдык)
deck-config-fsrs-simulate-desired-retention-experimental = FSRS Каалаган Эске Сактоо Симулятору (Эксперименталдык)
deck-config-fsrs-simulate-save-preset = Оптималдаштыргандан кийин, симуляторду иштетүүдөн мурун топтомдун алдын ала жөндөөсүн сактаңыз.
deck-config-fsrs-desired-retention-help-me-decide-experimental = Чечим чыгарууга жардам бер (Эксперименталдык)
deck-config-additional-new-cards-to-simulate = Симуляциялоо үчүн кошумча жаңы карталар
deck-config-simulate = Симуляциялоо
deck-config-clear-last-simulate = Акыркы симуляцияны тазалоо
deck-config-fsrs-simulator-radio-count = Кайталоолор
deck-config-advanced-settings = Өркүндөтүлгөн жөндөөлөр
deck-config-smooth-graph = Жылмакай график
deck-config-suspend-leeches = Татаал карталарды токтотуу
deck-config-save-options-to-preset = Өзгөртүүлөрдү алдын ала жөндөөгө сактоо
deck-config-save-options-to-preset-confirm = Учурдагы алдын ала жөндөөдөгү параметрлерди симулятордо учурда коюлган параметрлер менен алмаштырасызбы?
# Radio button in the FSRS simulation diagram (Deck options -> FSRS) selecting
# to show the total number of cards that can be recalled or retrieved on a
# specific date.
deck-config-fsrs-simulator-radio-memorized = Жатталган
deck-config-fsrs-simulator-radio-ratio = Убакыт / Жатталган катышы
# $time here is pre-formatted e.g. "10 Seconds" 
deck-config-fsrs-simulator-ratio-tooltip = Ар бир жатталган картага { $time }

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.

# Checkbox
deck-config-health-check = Оптималдаштырууда абалын текшерүү
# Message box showing the result of the health check
deck-config-fsrs-bad-fit-warning =
    Абалын текшерүү: 
    Сиздин эс тутумуңузду FSRS үчүн болжолдоо кыйын. Сунуштар: 
    - Дайыма унутуп жаткан карталарды токтотуңуз же кайра түзүңүз. 
    - Жооп баскычтарын ырааттуу колдонуңуз. "Оор" бул өтүү баасы, өтпөө баасы эмес экенин эске алыңыз. 
    - Жаттоодон мурун түшүнүңүз. 
    
    Эгер ушул сунуштарды аткарсаңыз, аткаруу адатта кийинки бир нече айдын ичинде жакшырат.
# Message box showing the result of the health check
deck-config-fsrs-good-fit =
    Абалын текшерүү: 
    FSRS сиздин эс тутумуңузга жакшы ыңгайлаша алат.

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-unable-to-determine-desired-retention = Минималдуу сунушталган эске сактоо деңгээлин аныктоо мүмкүн болбоду.
deck-config-predicted-minimum-recommended-retention = Минималдуу сунушталган эске сактоо деңгээли: { $num }
deck-config-compute-minimum-recommended-retention = Минималдуу сунушталган эске сактоо деңгээли
deck-config-compute-optimal-retention-tooltip4 =
    Бул курал эң аз убакыттын ичинде эң көп материалды үйрөнүүгө алып келе турган каалаган эске сактоо маанисин табууга аракет кылат. Эсептелген сан каалаган эске сактоо деңгээлиңизди эмнеге коюуну чечүүдө ориентир катары кызмат кыла алат. Эгер ага жетүү үчүн көбүрөөк окуу убактысын жумшоого даяр болсоңуз, анда жогорураак каалаган эске сактоо деңгээлин тандасаңыз болот. 
    Каалаган эске сактоо деңгээлиңизди минималдуудан төмөн коюу сунушталбайт, анткени жогорку унутуу деңгээлинен улам бул жогорку жумуш жүгүнө алып келет.
deck-config-plotted-on-x-axis = (X-огунда белгиленген)
deck-config-a-100-day-interval =
    { $days ->
        [one] 100 күндүк аралык { $days } күн болот.
       *[other] 100 күндүк аралык { $days } күн болот.
    }
deck-config-fsrs-simulator-y-axis-title-time = Кайталоо убактысы/Күн
deck-config-fsrs-simulator-y-axis-title-count = Кайталоо саны/Күн
deck-config-fsrs-simulator-y-axis-title-memorized = Жалпы жатталгандар
deck-config-bury-siblings = Карталардын жуптарын кийинкиге калтыруу)
deck-config-do-not-bury = Туугаш карталарды жашырбоо
deck-config-bury-if-new = Эгер жаңы болсо – жашыруу
deck-config-bury-if-new-or-review = Эгер жаңы же кайталоо картасы болсо – жашыруу
deck-config-bury-if-new-review-or-interday = Эгер жаңы, кайталоо же күндөр аралык үйрөнүү картасы болсо – жашыруу
deck-config-bury-tooltip =
    Туугаш карталар — бул бир эле маалыматка байланышкан башка карта формалары.
    Мисалы, алды → арты болуп алмашкан карталар же бир эле тексттен чыгарылган башка жашыруулар (cloze) сыяктуу.
    
    Эгер бул жөндөө өчүрүлгөн болсо, бир эле маалыматка тиешелүү бир нече картаны ошол эле күнү көрүп калуу мүмкүн.
    Ал эми күйгүзүлгөн болсо, Anki туугаш карталарды автоматтык түрдө кийинки күнгө чейин жашырып турат.
    Төмөндө сен кайсы учурда туугаш карталарды жашырууну кааларыңды тандай аласың — башкача айтканда, кайсы картага жооп бергенден кийин анын туугандарын жашыруу керектигин өзүң белгилейсиң.
    
    Эгер V3 пландагычы колдонулса, бир күндөн ашык убакытка созулган үйрөнүү карталары да жашырылышы мүмкүн.
    Көп күндүк үйрөнүү карталары — бул учурдагы үйрөнүү этабы бир күн же андан көпкө белгиленген карталар.
deck-config-seconds-to-show-question-tooltip = Авто-өтүү иштетилгенде, жоопту ачуудан мурун күтө турган секунддардын саны. Өчүрүү үчүн 0 деп коюңуз.
deck-config-answer-action-tooltip = Кийинкисине автоматтык түрдө өтүүдөн мурун учурдагы картада аткарыла турган аракет.
deck-config-wait-for-audio-tooltip = Жоопту же кийинки суроону автоматтык түрдө ачуудан мурун аудионун бүтүшүн күтүү.
deck-config-ignore-before-tooltip = Эгер коюлса, берилген датага чейинки кайталоолор FSRS параметрлерин оптималдаштырууда жана баалоодо эске алынбайт. Бул башка бирөөнүн пландаштыруу маалыматтарын импорттогон болсоңуз же жооп баскычтарын колдонуу ыкмаңызды өзгөрткөн болсоңуз, пайдалуу болушу мүмкүн.
deck-config-compute-optimal-retention-tooltip = Бул курал сиз 0 карта менен баштап жатасыз деп болжолдойт жана берилген убакыт аралыгында канча материалды эсиңизде сактай аларыңызды эсептөөгө аракет кылат. Болжолдонгон эске сактоо деңгээли сиздин киргизген маалыматтарыңызга абдан көз каранды болот, жана эгер ал 0.9дан кыйла айырмаланса, бул сиз күн сайын бөлгөн убакыт сиз үйрөнүүгө аракет кылып жаткан карталардын саны үчүн өтө аз же өтө көп экенинин белгиси. Бул сан ориентир катары пайдалуу болушу мүмкүн, бирок аны каалаган эске сактоо талаасына көчүрүү сунушталбайт.
deck-config-health-check-tooltip1 = Бул FSRS эс тутумуңузга ыңгайлашууда кыйналса, эскертүү көрсөтөт.
deck-config-health-check-tooltip2 = Абалды текшерүү "Учурдагы алдын ала жөндөөнү оптималдаштыруу" колдонулганда гана аткарылат.
deck-config-compute-optimal-retention = Минималдуу сунушталган эске сактоо деңгээлин эсептөө
deck-config-predicted-optimal-retention = Минималдуу сунушталган эске сактоо деңгээли: { $num }
deck-config-weights-tooltip = FSRS параметрлери карталардын кантип пландаштырыларына таасир этет. Anki баштапкы параметрлер менен баштайт. 1000+ кайталоо топтогондон кийин, бул алдын ала коюлган жөндөөнү колдонгон топтомдордогу аткарууңузга эң жакшы дал келген параметрлерди оптималдаштыруу үчүн төмөнкү жөндөөнү колдонсоңуз болот.
deck-config-compute-optimal-weights-tooltip = Anki'де 1000+ кайталоо жасагандан кийин, кайталоо тарыхыңызды талдоо үчүн Оптималдаштыруу баскычын колдонуп, эс тутумуңузга жана сиз окуп жаткан мазмунга оптималдуу болгон параметрлерди автоматтык түрдө түзө аласыз... (толук текст үчүн 3-сүрөттү караңыз).
deck-config-compute-optimal-retention-tooltip2 = Бул курал сиз 0 үйрөнүлгөн карта менен баштап жатасыз деп болжолдойт жана эң аз убакыттын ичинде эң көп материалды үйрөнүүгө алып келе турган каалаган эске сактоо маанисин табууга аракет кылат... (толук текст үчүн 8-сүрөттү караңыз).
deck-config-compute-optimal-retention-tooltip3 = Бул курал сиз 0 үйрөнүлгөн карта менен баштап жатасыз деп болжолдойт жана эң аз убакыттын ичинде эң көп материалды үйрөнүүгө алып келе турган каалаган эске сактоо маанисин табууга аракет кылат. Окуу процессиңизди так симуляциялоо үчүн, бул функцияга кеминде 400+ кайталоо талап кылынат. Эсептелген сан каалаган эске сактоо деңгээлиңизди эмнеге коюуну чечүүдө ориентир катары кызмат кыла алат. Эгер көбүрөөк эске тутуу деңгээли үчүн көбүрөөк окуу убактысын алмаштырууга даяр болсоңуз, анда жогорураак каалаган эске сактоо деңгээлин тандасаңыз болот. Каалаган эске сактоо деңгээлиңизди минималдуудан төмөн коюу сунушталбайт, анткени жогорку унутуу деңгээлинен улам бул жогорку жумуш жүгүнө алып келет.
deck-config-seconds-to-show-question-tooltip-2 = Авто-өтүү иштетилгенде, жоопту ачуудан мурун күтө турган секунддардын саны. Өчүрүү үчүн 0 деп коюңуз.
deck-config-invalid-weights = Параметрлерди же баштапкы жөндөөлөрдү колдонуу үчүн бош калтыруу керек, же 17 үтүр менен ажыратылган сан болушу керек.
deck-config-fsrs-on-all-clients = Бардык Anki кардарларыңыз Anki(Mobile) 23.10+ же AnkiDroid 2.17+ экенин текшериңиз. Эгер кардарларыңыздын бири эскирээк болсо, FSRS туура иштебейт.
deck-config-optimize-all-tip = "Сактоо" баскычынын жанындагы ачылуучу баскычты колдонуп, бардык алдын ала жөндөөлөрдү бир убакта оптималдаштырсаңыз болот.
