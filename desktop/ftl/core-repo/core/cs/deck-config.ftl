### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    { $decks ->
        [one] používá 1 balíček
        [few] používají { $decks } balíčky
       *[other] používá { $decks } balíčků
    }
deck-config-default-name = Výchozí
deck-config-title = Možnosti balíčku

## Daily limits section

deck-config-daily-limits = Denní limity
deck-config-new-limit-tooltip =
    Maximální počet nových karet za den, pokud jsou dostupné nové karty.
    Protože nový materiál zvýší Vaši krátkodobou zátěž při opakování, mělo 
    by to obvykle být nejméně 10× menší než limit opakování.
deck-config-review-limit-tooltip =
    Maximální počet opakovaných karet za den,
    jestliže jsou karty připraveny k zopakování.
deck-config-limit-deck-v3 =
    Při studování balíčku, který má podřízené balíčky, limity nastavené pro každý 
    podřízený balíček řídí maximální počet karet nabraných z daného balíčku. 
    Zvolené limity balíčků řídí celkový počet karet, které se zobrazí.
deck-config-limit-new-bound-by-reviews =
    Limit opakování ovlivní limit nových karet. Například, jestliže je limit opakování 
    nastaven na 200, a čeká vás 190 opakování, bude zobrazeno maximálně 10 nových 
    karet. Jestliže se dosáhne limitu opakování, žádné nové karty se nezobrazí.
deck-config-limit-interday-bound-by-reviews =
    Limit opakování také ovlivní učené karty, které překračují do dalšího dne. 
    Když se uplatní limit, karty, které překračují do dalšího dne, jsou nabrány 
    první, poté opakování a nakonec nové karty.
deck-config-tab-description =
    - `Předvolba`:  Limit je sdílen všemi balíčky, které používají tuto předvolbu.
    - `Tento balíček`: Limit je specifický pro tento balíček.
    - `Pouze dnes`: Provede dočasnou změnu limitu tohoto balíčku.
deck-config-new-cards-ignore-review-limit = Nové karty ignorují limit opakování
deck-config-new-cards-ignore-review-limit-tooltip = Ve výchozím nastavení limit opakování také platí pro nové karty. Žádné nové karty nebudou zobrazeny, když byl dosažen limit opakování. Jestliže je tato volba povolena, nové karty se budou zobrazovat bez ohledu na limit opakování.
deck-config-apply-all-parent-limits = Limity začínají odshora
deck-config-apply-all-parent-limits-tooltip =
    Ve výchozím nastavení limity začínají od balíčku, který jste vybrali. Jestliže je tato volba povolena, 
    limity budou místo toho začínat od rodičovského balíčku, což může být užitečné, pokud si přejete 
    studovat jednotlivé podřízené balíčky, ale vynutit celkový limit na karty/den.
deck-config-affects-entire-collection = Ovlivní celou kolekci.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Předvolba
deck-config-deck-only = Tento balíček
deck-config-today-only = Pouze dnes

## New Cards section

deck-config-learning-steps = Kroky učení
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Prodlevy jsou obvykle v minutách (např. `1m`) nebo dnech (např. `2d`), ale hodiny (např `1h`) a sekundy (např. `30s`) jsou také podporovány.
deck-config-learning-steps-tooltip =
    Jedna nebo více prodlev oddělených mezerami. První prodleva se použije,
    když na nové kartě stisknete tlačítko `Znovu`, a ve výchozím nastavení je 1 minuta.
    Tlačítko `Dobré` posune kartu do dalšího kroku, který je ve výchozím nastavení 10 minut.
    Jakmile se projdou všechny kroky, karta se stane opakovanou kartou a 
    objeví se v jiný den. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip =
    Počet dní, které se čeká, než se karta zobrazí znovu po stisknutí tlačítka `Dobré` 
    během posledního kroku učení.
deck-config-easy-interval-tooltip =
    Počet dní, které se čeká, než se karta zobrazí znovu po stisknutí tlačítka `Snadné`, 
    které okamžitě vyjme kartu z učení.
deck-config-new-insertion-order = Pořadí vkládání
deck-config-new-insertion-order-tooltip =
    Řídí pořadí (ke zkoušení #), v jakém jsou nové karty umístěny, když přidáte nové karty.
    Karty s nižším číslem zkoušení budou zobrazeny první během studia. Změna
    tohoto nastavení automaticky aktualizuje stávající pořadí nových karet.
deck-config-new-insertion-order-sequential = Postupně (nejstarší karty první)
deck-config-new-insertion-order-random = Náhodně
deck-config-new-insertion-order-random-with-v3 =
    S V3 plánovačem je lepší nechat toto nastavení na postupně 
    a upravit místo toho pořadí nabíraní nových karet.

## Lapses section

deck-config-relearning-steps = Kroky znovu učených
deck-config-relearning-steps-tooltip = Žádná nebo více prodlev oddělených mezerami. Ve výchozím nastavení stisknutí tlačítka `Znovu` na opakované kartě zobrazí tuto kartu znovu za 10 minut. Nejsou-li uvedeny žádné prodlevy, kartě se změní interval, aniž by se stala znovu učenou. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip =
    Počet stisknutí `Znovu` na opakované kartě předtím, než je označena jako 
    pijavice. Pijavice jsou karty, které spotřebovávají hodně času, a když je karta 
    označena jako pijavice, je dobrý nápad ji přepsat, odstranit, nebo se zamyslet 
    nad mnemotechnickou pomůckou, která pomůže si ji zapamatovat.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `Pouze označit štítkem`: Přidá k poznámce štítech „leech“ a zobrazí vyskakovací okno.
    
    `Vyřadit kartu`: Navíc k označení poznámky schová kartu, dokud nebude vyřazení
    ručně zrušeno.

## Burying section

deck-config-bury-title = Přeskakování
deck-config-bury-new-siblings = Odložit nové příbuzné karty na další den
deck-config-bury-review-siblings = Odložit opakované příbuzné karty na další den
deck-config-bury-interday-learning-siblings = Odložit učené příbuzné karty na další den
deck-config-bury-new-tooltip =
    Jestli ostatní `nové` karty stejné poznámky (např. obrácené karty, sousední doplňovačky
    budou odloženy na další den.
deck-config-bury-review-tooltip = Jestli ostatní `opakované` karty stejné poznámky budou odloženy na další den.
deck-config-bury-interday-learning-tooltip =
    Jestli ostatní `učené` karty stejné poznámky s intervalem >1 den 
    budou odloženy na další den.
deck-config-bury-priority-tooltip =
    Když Anki nabírá karty, nejdříve nabere karty učené v jednom dni, poté učené karty, které překračují do dalšího dne,  poté opakování a nakonec nové karty. To ovlivní, jak přeskakování funguje:
    
    - Jsou-li povoleny všechny možnosti přeskakování, zobrazí se příbuzné karty, které jsou v tomto seznamu na řadě nejdříve. Například, karta k zopakování se zobrazí přednostně před novou kartou.
    - Příbuzné karty níže na seznamu nemohou přeskočit typy karet, které jsou výše. Například, jestliže zakážete přeskakování nových karet a učíte se novou kartu, nepřeskočí žádnou učenou kartu, která překračuje do dalšího dne nebo kartu k zopakování a můžete tak vidět obě příbuzné karty během jednoho procvičování.

## Gather order and sort order of cards

deck-config-ordering-title = Pořadí zobrazování
deck-config-new-gather-priority = Pořadí nabírání nových karet
deck-config-new-gather-priority-tooltip-2 =
    `Balíček`: nabírá karty z každého balíčku v pořadí, začíná z vrchu. Karty z každého balíčku jsou
    nabírány ve vzestupném pořadí. Jestliže se dosáhne denního limitu vybraného balíčku, nabírání 
    karet se může zastavit dříve, než byly prověřeny všechny balíčky. Toto řazení je nejrychlejší pro
    velké kolekce a umožňuje upřednostňovat podřízené balíčky, které jsou výše.
    
    `Umístění vzestupně`: nabírá karty podle pozice (ke zkoušení) vzestupně, což je obvykle
    nejstarší přidané jako první.
    
    `Umístění sestupně`: nabírá karty podle pozice (ke zkoušení) sestupně, což je obvykle
    nejnovější přidané jako první.
    
    `Poznámky náhodně`: vybírá poznámky náhodně, poté nabere všechny jejich karty.
    
    `Karty náhodně`: nabírá karty zcela náhodně.
deck-config-new-card-sort-order = Pořadí řazení nových karet
deck-config-new-card-sort-order-tooltip-2 =
    `Typ karty, poté pořadí nabrání`: Zobrazuje karty v pořadí podle čísla typu karty. 
    Karty každého typu karty se zobrazují v pořadí, v jakém byly nabrány. Jestliže je zakázáno 
    odkládání příbuzných karet, toto zajistí, že se všechny karty líc→rub zobrazí dříve než karty rub→líc.
    Toto je užitečné, když se mají všechny karty stejné poznámky zobrazovat ve stejném sezení, 
    ale ne příliš blízko u sebe.
    
    `V pořadí nabrání`: Zobrazuje karty přesně tak, jak byly nabrány. Jestliže je zakázáno odkládání příbuzných 
    karet, toto obvykle vede k tomu, že se všechny karty jedné poznámky zobrazí jedna po druhé.
    
    `Typ karty, poté náhodně`: Zobrazuje karty v pořadí podle čísla typu karty. Karty každého čísla typu karty 
    se zobrazí v náhodném pořadí. Toto řazení je užitečné, pokud nechcete, aby se příbuzné karty zobrazovaly 
    příliš blízko sebe, ale přesto chcete, aby se karty zobrazovaly v náhodném pořadí.
    
    `Poznámky náhodně, poté typ karty`: Vybírá poznámky náhodně, poté zobrazí všechny jejich karty v pořadí.
    
    `Náhodně`: Zobrazí karty v náhodném pořadí.
deck-config-new-review-priority = Pořadí nové/opakování
deck-config-new-review-priority-tooltip = Kdy zobrazit nové karty ve vztahu k opakovaným kartám.
deck-config-interday-step-priority = Pořadí učení/opakování mezi dny
deck-config-interday-step-priority-tooltip =
    Kdy zobrazit (znovu) učené karty, které překračují do dalšího dne.
    
    Limit opakování se vždy použije jako první na učené karty, které překračují do dalšího 
    dne, a poté na opakování. Tato volba řídí pořadí, v jakém jsou nabrané karty zobrazeny, 
    ale učené karty překračující do dalšího dne budou vždy nabrány jako první.
deck-config-review-sort-order = Pořadí řazení opakování
deck-config-review-sort-order-tooltip =
    Výchozí řazení upřednostňuje karty, které čekaly nejdéle, takže jestliže máte 
    nevyřízená opakování, nejdéle čekající karty se zobrazí první. Jestliže máte 
    velké množství nevyřízených opakování, která zaberou více, než několik málo 
    dní, nebo si přejte vidět karty v pořadí podřízených balíčků, můžete shledat 
    alternativní pořadí řazení vhodnější.
deck-config-display-order-will-use-current-deck =
    Anki použije pořadí zobrazování balíčku, který jste vybrali 
    ke studiu, a ne podřízených balíčků, které může mít.

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = Balíček
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = Balíček, poté poznámky náhodně
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = Umístění vzestupně
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = Umístění sestupně
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = Poznámky náhodně
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = Karty náhodně
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = Typ karty, poté náhodně
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = Poznámky náhodně, poté typ karty
# Sort the cards randomly.
deck-config-sort-order-random = Náhodně
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = Typ karty
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = V pořadí nabrání
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = Smíchat s opakováním
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = Zobrazit po opakování
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = Zobrazit před opakováním
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = Datum zkoušení, poté náhodně
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = Datum zkoušení, poté balíček
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = Balíček, poté datum zkoušení
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = Intervaly vzestupně
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = Intervaly sestupně
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = Snadnost vzestupně
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = Snadnost sestupně
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = Snadné karty nejdříve
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = Obtížné karty nejdříve
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = Zapamatování vzestupně
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = Zapamatování sestupně

## Timer section

deck-config-timer-title = Časovač
deck-config-maximum-answer-secs = Maximální čas odpovědi v sekundách
deck-config-maximum-answer-secs-tooltip =
    Maximální počet sekund, které se zaznamenají u jednoho opakování. 
    Jestliže odpověď přesahuje tento čas (protože například odejdete od 
    obrazovky), bude zaznamenán nastavený limit.
deck-config-show-answer-timer-tooltip =
    Na obrazovce opakování zobrazit časovač, který počítá sekundy 
    strávené opakováním každé karty.
deck-config-stop-timer-on-answer = Zastavit časovač po odpovědi
deck-config-stop-timer-on-answer-tooltip =
    Jestli se má časovač zastavit, když se zobrazí odpověď.
    Neovlivní to statistiky.

## Auto Advance section

deck-config-seconds-to-show-question = Počet sekund, po které je zobrazena otázka
deck-config-seconds-to-show-question-tooltip-3 = Když je automatický posun aktivovaný, počet sekund, po které se čeká, než se aplikuje akce po otázce. Nastavením na 0 je zakázán.
deck-config-seconds-to-show-answer = Počet sekund, po které je zobrazena odpověď
deck-config-seconds-to-show-answer-tooltip-2 = Když je automatický posun aktivovaný, počet sekund, po které se čeká, než se aplikuje akce po odpovědi. Nastavením na 0 je zakázán.
deck-config-question-action-show-answer = Zobrazit odpověď
deck-config-question-action-show-reminder = Zobrazit upomínku
deck-config-question-action = Akce po otázce
deck-config-question-action-tool-tip = Akce, která se provede po zobrazení otázky a vypršení času.
deck-config-answer-action = Akce po odpovědi
deck-config-answer-action-tooltip-2 = Akce, která se provede po zobrazení odpovědi a vypršení času.
deck-config-wait-for-audio-tooltip-2 = Čeká se, než dohraje zvuková stopa, až poté se automaticky aplikuje akce po otázce nebo akce po odpovědi.

## Audio section

deck-config-audio-title = Zvuk
deck-config-disable-autoplay = Nepřehrávat zvuk automaticky
deck-config-disable-autoplay-tooltip =
    Je-li povoleno, Anki nepřehraje zvuk automaticky.
    Je možné ho přehrát ručně kliknutím/stisknutím ikony zvuku nebo použitím akce přehrát zvuk.
deck-config-skip-question-when-replaying = Přeskočit otázku, když se přehrává odpověď
deck-config-always-include-question-audio-tooltip =
    Zda má být zvuk v otázce přehrán, když se zvuk přehrává znovu během 
    prohlížení strany karty s odpovědí.

## Advanced section

deck-config-advanced-title = Pokročilé
deck-config-maximum-interval-tooltip =
    Maximální počet dní, po které bude opakovaná karta čekat. Když opakování 
    dosáhne limitu, `Těžké`, `Dobré` a `Snadné` udělí stejnou prodlevu. 
    Čím kratší tento limit bude, tím větší bude pracovní zátěž.
deck-config-starting-ease-tooltip =
    Snadnost coby násobitel, se kterým nové karty začínají. Ve výchozím nastavení, 
    tlačítko `Dobré` na nově naučené kartě přesune následující opakování 
    o 2,5 × předchozí prodleva.
deck-config-easy-bonus-tooltip =
    Extra násobitel, který se aplikuje na interval opakovaných karet, když je 
    hodnotíte jako `Snadné`.
deck-config-interval-modifier-tooltip =
    Tento násobitel se aplikuje na všechna opakování a menší přenastavení lze 
    použít k tomu, aby byl Anki více konzervativní nebo agresivní ve svém 
    plánování. Prosím prostudujte si manuál před změnou tohoto nastavení.
deck-config-hard-interval-tooltip = Násobitel, který se aplikuje na interval opakování, když se odpoví `Těžké`.
deck-config-new-interval-tooltip = Násobitel, který se aplikuje na interval opakování, když se odpoví `Znovu`.
deck-config-minimum-interval-tooltip = Minimální interval daný opakované kartě po odpovědi `Znovu`.
deck-config-custom-scheduling = Vlastní plánování
deck-config-custom-scheduling-tooltip = Ovlivní celou kolekci. Používejte na vlastní nebezpečí!

## Easy Days section.

deck-config-easy-days-title = Snadné dny
deck-config-easy-days-monday = Pondělí
deck-config-easy-days-tuesday = Úterý
deck-config-easy-days-wednesday = Středa
deck-config-easy-days-thursday = Čtvrtek
deck-config-easy-days-friday = Pátek
deck-config-easy-days-saturday = Sobota
deck-config-easy-days-sunday = Neděle
deck-config-easy-days-normal = Normální
deck-config-easy-days-reduced = Snížené
deck-config-easy-days-minimum = Minimální
deck-config-easy-days-no-normal-days = Alespoň jeden den by měl být nastaven na '{ deck-config-easy-days-normal }'.
deck-config-easy-days-change = Stávající opakování nebudou přeplánována, pokud '{ deck-config-reschedule-cards-on-change }' není povoleno v nastavení FSRS.

## Adding/renaming

deck-config-add-group = Přidat předvolbu
deck-config-name-prompt = Název
deck-config-rename-group = Přejmenovat předvolbu
deck-config-clone-group = Klonovat předvolbu

## Removing

deck-config-remove-group = Odstranit předvolbu
deck-config-will-require-full-sync = Požadovaná změna způsobí kompletní nahrání databáze na server při příští synchronizaci Vaší kolekce. Máte-li opakování nebo jiné změny na jiném zařízení, které ještě nebyly synchronizovány, budou ztraceny.
deck-config-confirm-remove-name = Odstranit { $name }?

## Other Buttons

deck-config-save-button = Uložit
deck-config-save-to-all-subdecks = Uložit pro všechny podřízené balíčky
deck-config-save-and-optimize = Optimalizovat všechny předvolby
deck-config-revert-button-tooltip = Obnovit toto nastavení na výchozí hodnotu.

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Anki 2.1.41+ zpracování
deck-config-description-new-handling-hint =
    Považuje vstup za markdown a čistí HTML vstup. Když je 
    povoleno, popis se také zobrazí na obrazovce gratulace.
    Markdown se objeví jako text v Anki 2.1.40 a méně.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    Rodičovský balíček má limit { $cards ->
        [one] { $cards } karta
        [few] { $cards } karty
       *[other] { $cards } karet
    }, což přepíše tento limit.
deck-config-reviews-too-low =
    Když se { $cards ->
        [one] přidává { $cards } nová karta každý den
        [few] přidávají { $cards } nové karty každý den
       *[other] přidává { $cards } nových karet každý den
    }, limit opakování by měl být nejméně { $expected }.
deck-config-learning-step-above-graduating-interval = Interval absolvování by měl být nejméně stejně dlouhý jako poslední krok učení.
deck-config-good-above-easy = Interval pro snadné by měl být nejméně stejně dlouhý jako interval absolvování.
deck-config-relearning-steps-above-minimum-interval = Minimální interval chyby by měl být nejméně stejně dlouhý jako poslední krok znovu učených.
deck-config-maximum-answer-secs-above-recommended = Anki může plánovat Vaše opakování účinněji, když máte krátké otázky.
deck-config-too-short-maximum-interval = Maximální interval kratší než 6 měsíců se nedoporučuje.
deck-config-ignore-before-info = (Přibližně) { $included }/{ $totalCards } karet se použije k optimalizování FSRS parametrů.

## Selecting a deck

deck-config-which-deck = Který balíček požadujete?

## Messages related to the FSRS scheduler

deck-config-updating-cards = Aktualizují se karty: { $current_cards_count }/{ $total_cards_count }...
deck-config-invalid-parameters = Zadané parametry FSRS jsou neplatné. Ponechte je prázdné, použijí se tak výchozí parametry.
deck-config-not-enough-history = K provedení této operace není dostatečná historie opakování.
deck-config-must-have-400-reviews =
    { $count ->
        [one] Bylo nalezeno pouze { $count } opakování. Tato činnost vyžaduje alespoň 400 opakování.
        [few] Byly nalezeny pouze { $count } opakování. Tato činnost vyžaduje alespoň 400 opakování.
        [many] Bylo nalezeno pouze { $count } opakování. Tato činnost vyžaduje alespoň 400 opakování.
       *[other] Bylo nalezeno pouze { $count } opakování. Tato činnost vyžaduje alespoň 400 opakování.
    }
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = Váhy modelu
deck-config-compute-optimal-weights = Optimalizovat FSRS váhy
deck-config-optimize-button = Optimalizovat
# Indicates that a given function or label, provided via the "text" variable, operates slowly.
deck-config-slow-suffix = { $text } (pomalé)
deck-config-compute-button = Vypočítat
deck-config-ignore-before = Ignorovat opakování před
deck-config-time-to-optimize = Už je to nějakou dobu - doporučuje se použít tlačítko Optimalizovat vše.
deck-config-evaluate-button = Vyhodnotit
deck-config-desired-retention = Požadovaná retence
deck-config-historical-retention = Historická retence
deck-config-smaller-is-better = Menší čísla naznačují lepší způsobilost pro vaši historii opakování.
deck-config-steps-too-large-for-fsrs = Je-li FSRS povoleno, kroky učení delší než 1 den nejsou doporučeny.
deck-config-get-params = Získat parametry
deck-config-complete = { $num }% hotovo.
deck-config-iterations = Iterace: { $count }...
deck-config-reschedule-cards-on-change = Přeplánovat karty po změně
deck-config-fsrs-tooltip =
    Free Spaced Repetition Scheduler (FSRS) je alternativou k původnímu plánovači Anki SuperMemo 2 (SM2).
    Pomocí přesnějšího určování, kdy pravděpodobně zapomenete, vám může pomoci zapamatovat si 
    více materiálu za stejný čas. Toto nastavení je sdíleno všemi předvolbami balíčků.
deck-config-desired-retention-tooltip =
    Výchozí hodnota 0,9 bude plánovat karty tak, že máte 90% šanci zapamatovat si je, když 
    jsou připraveny k opakování. Jestliže zvýšíte tuto hodnotu, Anki bude zobrazovat karty častěji, 
    aby se zvýšila šance na jejich zapamatování. Jestliže snížíte tuto hodnotu, Anki bude zobrazovat 
    karty méně často, a vy jich více zapomenete. Při nastavování buďte konzervativní - vyšší 
    hodnoty významně zvýší vaše vytížení a nižší hodnoty mohou být demotivující, když zapomenete 
    hodně materiálu.
deck-config-desired-retention-tooltip2 = Hodnoty pracovní zátěže uvedené v informačním rámečku jsou přibližné. Pro větší přesnost použijte simulátor.
deck-config-historical-retention-tooltip =
    Jestliže chybí část vaší historie opakování, FSRS musí vyplnit mezery. Ve výchozím nastavení bude předpokládat, že když jste dělali tato stará opakování, pamatovali jste si 90 % materiálu. Pokud vaše stará retence byla znatelně vyšší nebo nižší než 90 %, změna tohoto nastavení umožní FSRS lépe aproximovat chybějící opakování.
    
    Vaše historie opakování může být neúplná ze dvou důvodů:
    1. Protože jste již dříve použili volbu 'ignorovat opakování před'.
    2. Protože jste dříve smazali záznamy opakování, abyste uvolnili místo, nebo jste importovali materiál z jiného SRS programu.
    
    To druhé je poměrně vzácné, takže pokud jste nepoužili první možnost, pravděpodobně nebudete muset toto nastavení upravovat.
deck-config-weights-tooltip2 = FSRS parametry ovlivňují způsob plánování karet. Anki začíná s výchozími parametry. Můžete použít možnost níže, abyste optimalizovali parametry tak, aby co nejlépe odpovídaly vašemu výkonu v balíčcích, které používají tuto předvolbu.
deck-config-reschedule-cards-on-change-tooltip =
    Tato možnost řídí, jestli se změní data zkoušení karet, když povolíte FSRS nebo změníte 
    váhy. Výchozí hodnota je nepřeplánovat karty: budoucí opakování použijí nové plánování, ale 
    nenastane žádná okamžitá změna vašeho zatížení. Jestliže je přeplánování povoleno, data zkoušení 
    karet se změní. V závislosti na vaší požadované retenci může toto vyústit ve velký počet karet, 
    které budou ke zkoušení, proto není tato možnost doporučena, když poprvé přepínáte ze SM2.
deck-config-reschedule-cards-warning = V závislosti na vaší požadované retenci může toto vyústit ve velké množství karet, které budou ke zkoušení, takže toto není doporučeno, když poprvé přepínáte ze SM2.
deck-config-ignore-before-tooltip-2 =
    Je-li nastaveno, karty opakované před uvedeným datem budou ignorovány při optimalizaci FSRS parametrů.
    To může být užitečné, pokud jste importovali data plánování někoho jiného nebo jste změnili způsob, jakým používáte tlačítka odpovědí.
deck-config-compute-optimal-weights-tooltip2 =
    Po kliknutí na tlačítko Optimalizovat FSRS analyzuje vaši historii opakování a vygeneruje parametry, které jsou optimální pro vaši paměť a obsah, který studujete. Pokud se obtížnost vašich balíčků velmi liší, doporučujeme jim přiřadit samostatné předvolby, protože parametry pro lehké balíčky a těžké balíčky se budou lišit. 
    Parametry nemusíte optimalizovat často - stačí jednou za několik měsíců.
    
    Ve výchozím nastavení se parametry vypočítají z historie opakování všech balíčků, které používají aktuální předvolbu. Pokud chcete změnit, které karty se použijí pro optimalizaci parametrů, můžete navíc před výpočtem parametrů upravit vyhledávání.
deck-config-please-save-your-changes-first = Prosím nejdříve uložte změny.
deck-config-workload-factor-change =
    Přibližná pracovní zátěž: { $factor }x
    (v porovnání s { $previousDR }% požadované retence)
deck-config-workload-factor-unchanged = Čím vyšší je tato hodnota, tím častěji se vám budou karty zobrazovat.
deck-config-desired-retention-too-low = Vaše požadovaná retence je velmi nízká, což může vést k velmi dlouhým intervalům.
deck-config-desired-retention-too-high = Vaše požadovaná retence je velmi vysoká, což může vést k velmi krátkým intervalům.
deck-config-percent-of-reviews =
    { $reviews ->
        [one] { $pct }% z { $reviews } opakování
        [few] { $pct }% ze { $reviews } opakování
       *[other] { $pct }% z { $reviews } opakování
    }
deck-config-percent-input = { $pct }%
# This message appears during FSRS parameter optimization.
deck-config-checking-for-improvement = Hledají se vylepšení...
deck-config-optimizing-preset = Optimalizuje se předvolba { $current_count }/{ $total_count }...
deck-config-fsrs-must-be-enabled = Nejdříve musí být povoleno FSRS.
deck-config-fsrs-params-optimal = FSRS parametry se nyní zdají být optimální.
deck-config-fsrs-params-no-reviews = Nebyla nalezena žádná opakování. Ujistěte se, že je toto předvolba přiřazena ke všem balíčkům (včetně podřízených balíčků), které chcete optimalizovat, a zkuste to znovu.
deck-config-wait-for-audio = Čekat na zvukovou stopu
deck-config-show-reminder = Zobrazit upomínku
deck-config-answer-again = Odpovědět znovu
deck-config-answer-hard = Odpovědět těžké
deck-config-answer-good = Odpovědět dobré
deck-config-days-to-simulate = Simulovat dní
deck-config-desired-retention-below-optimal = Vaše požadovaná retence není optimální. Je doporučeno ji zvýšit.
# Description of the y axis in the FSRS simulation
# diagram (Deck options -> FSRS) showing the total number of
# cards that can be recalled or retrieved on a specific date.
deck-config-fsrs-simulator-experimental = FSRS simulátor (experimentální)
deck-config-fsrs-simulate-desired-retention-experimental = FSRS simulátor požadované retence (experimentální)
deck-config-fsrs-simulate-save-preset = Po optimalizaci prosím uložte předvolbu balíčku, než spustíte simulátor.
deck-config-fsrs-desired-retention-help-me-decide-experimental = Pomoz mi rozhodnout (experimentální)
deck-config-additional-new-cards-to-simulate = Další nové karty pro simulaci
deck-config-simulate = Simulovat
deck-config-clear-last-simulate = Vymazat poslední simulaci
deck-config-fsrs-simulator-radio-count = Opakování
deck-config-advanced-settings = Pokročilé nastavení
deck-config-suspend-leeches = Vyřadit pijavice
deck-config-save-options-to-preset = Uložit změny do předvolby
deck-config-save-options-to-preset-confirm = Přepsat nastavení v aktuální předvolbě nastavením, které je aktuálně zadáno v simulátoru?
# Radio button in the FSRS simulation diagram (Deck options -> FSRS) selecting
# to show the total number of cards that can be recalled or retrieved on a
# specific date.
deck-config-fsrs-simulator-radio-memorized = Zapamatováno
deck-config-fsrs-simulator-radio-ratio = Poměr čas / zapamatováno
# $time here is pre-formatted e.g. "10 Seconds" 
deck-config-fsrs-simulator-ratio-tooltip = { $time } na zapamatovanou kartu

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.

# Checkbox
deck-config-health-check = Při optimalizaci zkontrolovat zdraví
# Message box showing the result of the health check
deck-config-fsrs-bad-fit-warning =
    Kontrola zdraví:
    Pro FSRS je obtížné předvídat vaši paměť. Doporučení:
    
    - Vyřaďte pijavice nebo změňte jejich formulace.
    - Tlačítka odpovědí používejte důsledně. Mějte na paměti, že „těžké“ je známka správné odpovědi, nikoliv špatné odpovědi.
    - Než se budete učit nazpaměť, porozumějte.
    
    Pokud se budete řídit těmito doporučeními, výkon se v průběhu několika následujících měsíců obvykle zlepší.
# Message box showing the result of the health check
deck-config-fsrs-good-fit =
    Kontrola zdraví:
    FSRS se může dobře přizpůsobit vaší paměti.

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-unable-to-determine-desired-retention = Nelze určit optimální retenci.
deck-config-predicted-minimum-recommended-retention = Minimální doporučená retence: { $num }
deck-config-compute-minimum-recommended-retention = Minimální doporučená retence
deck-config-compute-optimal-retention-tooltip4 = Tento nástroj se pokusí najít požadovanou hodnotu retence, která povede k naučení se co největšího množství materiálu za co nejkratší dobu. Vypočítané číslo může sloužit jako referenční hodnota při rozhodování, jakou hodnotu požadované retence nastavit. Pokud jste ochotni vyměnit více studijního času za vyšší míru zapamatování, můžete zvolit vyšší hodnotu požadované retence. Nastavení požadované retence na nižší než minimální hodnotu se nedoporučuje, protože to povede k vyšší pracovní zátěži kvůli vysoké míře zapomínání.
deck-config-a-100-day-interval =
    { $days ->
        [one] 100denní interval se změní na { $days } den.
        [few] 100denní interval se změní na { $days } dny.
       *[other] 100denní interval se změní na { $days } dní.
    }
deck-config-fsrs-simulator-y-axis-title-memorized = Zapamatováno celkem
deck-config-seconds-to-show-question-tooltip = Když je automatický posun aktivovaný, počet sekund, po které se čeká, než se zobrazí odpověď. Nastavením na 0 je zakázán.
deck-config-answer-action-tooltip = Akce, která se provede na současnou kartu předtím, než se automaticky posunete na další.
deck-config-wait-for-audio-tooltip = Čeká se, než dohraje zvuková stopa, až poté se automaticky zobrazí odpověď nebo další otázka
deck-config-ignore-before-tooltip =
    Je-li nastaveno, opakování před uvedeným datem budou ignorovány při optimalizaci a vyhodnocení FSRS parametrů.
    To může být užitečné, pokud jste importovali data plánování někoho jiného nebo jste změnili způsob, jakým používáte tlačítka odpovědí.
deck-config-compute-optimal-retention-tooltip =
    Tento nástroj předpokládá, že začínáte s 0 kartami, a pokusí se vypočítat množství materiálu, který budete schopen udržovat v daném časovém rámci. Odhadovaná retence bude do značné míry záviset na vašich vstupech, 
    a pokud se výrazně liší od 0,9, je to znamení, že čas, který jste přidělili na každý den, je buď příliš nízký,
    nebo příliš vysoký na množství karet, které se pokoušíte naučit. Toto číslo může být užitečné jako reference, ale
    nedoporučuje se jej kopírovat do pole požadovaná retence.
deck-config-health-check-tooltip1 = Zobrazí se varování, jestliže má FSRS potíže přizpůsobit se vaší paměti.
deck-config-compute-optimal-retention = Vypočítat optimální retenci
deck-config-predicted-optimal-retention = Předpovězená optimální retence: { $num }
deck-config-weights-tooltip =
    Váhy modelu ovlivňují, jak jsou karty plánovány. Jakmile máte 1000+ opakování, můžete optimalizovat 
    váhy níže.
deck-config-compute-optimal-weights-tooltip =
    Jakmile provedete 1000+ opakování v Anki, můžete použít tlačítko Optimalizovat, aby se analyzovala vaše 
    historie opakování a automaticky se vygenerovaly váhy, které jsou optimální pro vaší paměť a obsah, který 
    studujete. Jestliže máte balíčky, u kterých se velmi liší obtížnost, je doporučeno přiřadit jim samostatné
    předvolby, protože váhy pro jednoduché balíčky a těžké balíčky budou různé. Není potřeba optimalizovat vaše váhy často - jednou za několik měsíců je dostatečné.
    
    Ve výchozím nastavení se váhy budou počítat z historie opakování všech balíčků za použití současné 
    předvolby. Před výpočtem vah můžete volitelně nastavit hledání, pokud chcete změnit, které karty 
    se použijí pro optimalizování vah.
deck-config-seconds-to-show-question-tooltip-2 = Když je automatický posun aktivovaný, počet sekund, po které se čeká, než se zobrazí odpověď. Nastavením na 0 je zakázán.
deck-config-invalid-weights = Váhy musí být buď ponechány prázdné, kdy se použijí výchozí hodnoty, nebo musí být 17 čísel oddělených čárkou.
deck-config-fsrs-on-all-clients =
    Prosím ujistěte se, že všechny vaše Anki klienty jsou Anki(Mobile) 23.10+ nebo AnkiDroid 2.17+. FSRS nebude 
    fungovat správně, jestliže je některý z vašich klientů starší.
deck-config-optimize-all-tip = Můžete optimalizovat všechny předvolby najednou použitím tlačítka výše.
