### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    { $decks ->
        [one] använd av { $decks } kortlek
       *[other] använd av { $decks } kortlekar
    }
deck-config-default-name = Förval
deck-config-title = Kortleksalternativ

## Daily limits section

deck-config-daily-limits = Dagliga begränsningar
deck-config-new-limit-tooltip = Det maximala antalet nya kort att introducera per dag, om nya kort finns tillgängliga. Eftersom nya kort kortsiktigt kommer öka mängden kort att repetera, bör detta vara åtminstone 10x mindre än gränsen för antalet repetitioner.
deck-config-review-limit-tooltip =
    Det maximala antalet repetitionskort som visas på ett dygn,
    om korten är redo att repeteras.
deck-config-limit-deck-v3 =
    Vid studerande av en kortlek med underkortlekar styr gränserna för respektive
    underkortlek det maximala antalet kort som samlas den kortleken.
    Den valda kortlekens gränser styr det totala antalet kort som kommer visas.
deck-config-limit-new-bound-by-reviews =
    Gränsen för antalet repetitioner påverkar gränsen för antalet nya kort, Ifall gränsen för antalet repetitioner
    är satt till 200 och 190 repetitioner kvarstår kommer maximalt 10 nya kort att
    introduceras. Om gränsen för antalet repetitioner är uppnådd kommer inga nya kort
    att visas.
deck-config-limit-interday-bound-by-reviews =
    Gränsen för antal repetitioner påverkar också inlärningskort som löper över flera dagar. När gränsen tillämpas
    kommer flerdagslöpande inlärningskort att samlas före repetitionskort.
deck-config-tab-description =
    - `Förinställning`: Gränsen tillämpas till alla kortlekar som använder denna förinställning.
    - `Denna kortlek`: Gränsen är specifik till denna kortlek.
    - `Bara idag`: En tillfällig ändring av denna kortleks gräns.
deck-config-new-cards-ignore-review-limit = Nya kort ignorerar daglig begränsning
deck-config-new-cards-ignore-review-limit-tooltip =
    Som förval tillämpas repetitionsgränsen även för nya kort, och inga nya kort kommer
    visas när repetitionsgränsen har uppnåtts. Om denna inställning är aktiverad kommer nya kort
    visas oavsett repetitionsgränsen.
deck-config-apply-all-parent-limits = Tillämpa alltid gränser på toppnivå
deck-config-apply-all-parent-limits-tooltip =
    Som förval kommer de dagliga begränsningarna av en kortlek på högre nivå ej tillämpas när dess underkortlek studeras.
    Om denna inställning är aktiverad kommer begränsningarna
    i första hand tillämpas från toppnivåkortleken, vilket kan vara till användning då enskilda
    kortlekar studeras men en total kortgräns bör tillämpas för hela kortleksträdet.
deck-config-affects-entire-collection = Påverkar hela samlingen

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Förinställning
deck-config-deck-only = Denna kortlek
deck-config-today-only = Bara idag

## New Cards section

deck-config-learning-steps = Inlärningssteg
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Frist skrivs typiskt i minuter (e.g. `1m`) eller dagar (e.g. `2d`), men timmar (e.g. `1h`) och sekunder (e.g. `30s`) är också godtagbara.
deck-config-learning-steps-tooltip =
    En eller flera frister, separerade med blanksteg. Den första fristen kommer användas
    när knappen `Igen` nedtrycks, och är som förval 1 minut.
    Knappen `Bra` kommer kortet avancera till nästa steg, vilket som förval är 10 minuter.
    När alla inlärningssteg har passerats kommer kortet att befordras till ett repetitionskort och
    schemaläggas till en annan dag. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip =
    Antal dagar att vänta innan ett kort visas igen efter att knappen `Bra`
    har nedtryckts i det sista inlärningssteget.
deck-config-easy-interval-tooltip =
    Antal dagar att vänta innan ett kort visas igen efter att knappen `Lätt`
    använts för att direkt befordra ett kort från inlärning.
deck-config-new-insertion-order = Insättningsordning
deck-config-new-insertion-order-tooltip =
    Styr positionen (förfallonr) nya kort tilldelas när de läggs till.
    När du studerar kommer kort med ett lägre förfallonummer att visas först. När denna
    inställning ändras uppdateras positionen hos nya kort automatiskt.
deck-config-new-insertion-order-sequential = Sekventiell (äldsta kort först)
deck-config-new-insertion-order-random = Slumpmässigt
deck-config-new-insertion-order-random-with-v3 =
    Med v3-schemaläggaren är det bättre att lämna detta alternativ satt till sekventiell och
    istället ändra på samlingsordningen för nya kort.

## Lapses section

deck-config-relearning-steps = Ominlärningssteg
deck-config-relearning-steps-tooltip =
    Noll eller flera frister, separerade med blanksteg. Som förval kommer nedtryckning av knappen `Igen`
    göra att kortet visas 10 minuter senare. Om inga frister tillhandahålls kommer kortet få sitt intervall förändrat utan att träda in i
    ominlärning. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip =
    Antalet gånger knappen `Igen` måste nedtryckas på ett repetitionskort innan det
    markeras som en energislukare. Energislukare är kort som tar oskälig tid, och
    när ett kort markeras som en energislukare är det en bra idé att skriva om det, ta bort det, eller
    komma på en minnesregel som gör det lättare att komma ihåg kortet.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `Tagga endast`: Lägger till en 'leech'-etikett (energislukare-etikett) till noten och visar en notis.
    `Lås kort`: Utöver att tagga kortet, uteslut kortet tills det
    manuellt upplåses.

## Burying section

deck-config-bury-title = Döljande
deck-config-bury-new-siblings = Dölj nya syskon
deck-config-bury-review-siblings = Dölj repeterande syskon
deck-config-bury-interday-learning-siblings = Dölj flerdagslöpande inlärningskort som är syskon
deck-config-bury-new-tooltip =
    Huruvida andra nya kort tillhörande samma not (e.g. omvända kort, intilliggande lucktexter)
    bör uppskjutas tills dagen efter.
deck-config-bury-review-tooltip = Huruvida andra repetitionskort tillhörande samma not bör uppskjutas tills dagen efter.
deck-config-bury-interday-learning-tooltip =
    Huruvida andra inlärningskort tillhörande samma not med intervaller > 1 dag
    bör uppskjutas tills dagen efter.
deck-config-bury-priority-tooltip =
    Anki samlar kort i ordningen endagslöpande inlärningskort, flerdagslöpande inlärningskort, repetitionskort och slutligen nya kort. Detta påverkar hur döljande fungerar:
    
    - Om alla döljningsalternativ är aktiva visas syskonet tidigast i
    listan. Exempelvis kommer ett repetitionskort att visas före
    ett nytt kort.
    - Syskon senare i listan kan inte dölja tidigare korttyper. Exempelvis kommer inga repetitionskort eller flerdagslöpande inlärningskort att döljas om du inaktiverar döljande av nya kort och studerar ett nytt kort, alltså kan både ett syskon som är ett repetitionskort och ett syskon som är ett nytt kort visas under samma session.

## Gather order and sort order of cards

deck-config-ordering-title = Visningsordning
deck-config-new-gather-priority = Samlingsordning för nya kort
deck-config-new-gather-priority-tooltip-2 =
    `Kortlek`: Samlar kort från respektive underkortlek i ordning, börjandes från toppen. Kort från varje underkortlek
    samlas med ökande position. Om den dagliga begränsningen för den valda kortleken uppnås, kan samlande
    sluta innan alla underkortlekar har genomgåtts. Denna ordning är snabbast i stora samlingar, och
    tillåter prioritering av underkortlekar som är närmre toppen.
    
    `Ökande position`: Samlar kort med ökande position (förfallonr), vilket typiskt sett
    är de äldst tillagda först.
    
    `Minskande position`: Samlar kort med minskande position (förfallonr), vilket typiskt sett
    är de senast tillagda först.
    
    `Slumpade noter`: Väljer noter slumpmässigt, och samlar sedan alla deras respektive kort.
    
    `Slumpade kort`: Samlar kort i en slumpmässig ordning.
deck-config-new-card-sort-order = Sorteringsordning för nya kort
deck-config-new-card-sort-order-tooltip-2 =
    `Korttyp, sedan ordning samlad`: Visar kort i ordning av korttypsnummer.
    Korten för respektive korttypsnummer visas i ordningen de samlades.
    Om syskondöljande är inaktiverat kommer detta att försäkra att alla framsida→baksida-kort visas före några baksida→framsida-kort.
    Detta är användbart för att alla kort som tillhör en not visas i samma session, men inte
    för nära inpå varandra.
    
    `Ordning samlad`: Visar kort exakt som de samlades. Om syskondöljande är inaktiverat
    kommer detta typiskt sett resultera i att alla kort för en not visas en efter en.
    
    `Korttyp, sedan slumpad`: Visar kort i ordning av korttypsnummer. Korten för respektive korttypsnummer
    visas i en slumpad ordning. Detta är användbart för att syskonkort inte ska visas för tätt inpå varandra, men att korten fortfarande visas i slumpmässig ordning.
    
    `Slumpad not, sedan korttyp`: Väljer slumpmässigt not, och visar sedan alla dess kort i ordning.
    
    `Slumpmässig`: Visar kort i en slumpmässig ordning.
deck-config-new-review-priority = Ordning för nya kort/repetitionskort
deck-config-new-review-priority-tooltip = När nya kort bör visas i förhållande till repetitionskort.
deck-config-interday-step-priority = Ordning för flerdagslöpande inlärningskort/repetitionskort
deck-config-interday-step-priority-tooltip =
    När (om)inlärningskort som löper över flera dagar bör visas.
    
    Repetitionsgränsen tillämpas alltid först på flerdagslöpande inlärningskort, och
    därefter repetitionskort. Denna inställning kommer styra ordningen de samlade korten visas i,
    men flerdagslöpande inlärningskort kommer alltid samlas först.
deck-config-review-sort-order = Sorteringsordning för repetitionskort
deck-config-review-sort-order-tooltip =
    Den förvalda ordningen prioriterar kort som har väntat längst, så att dessa vid en
    eftersläpning alltså kommer att visas först. De andra sorteringsordningarna kan vara
    att föredra vid en stor eftersläpning som kommer ta mer än ett antal dagar att beta av,
    eller för att se korten ordnade efter underkortlek.
deck-config-display-order-will-use-current-deck =
    Anki kommer använda visningsordningen från den kortleken
    som har valts, och inte dess eventuella underkortlekar.

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = Kortlek
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = Kortlek sedan slumpmässiga noter
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = Ökande position
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = Minskande position
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = Slumpmässiga noter
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = Slumpmässiga kort
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = Korttyp, sedan slumpmässigt
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = Slumpad not, sedan korttyp
# Sort the cards randomly.
deck-config-sort-order-random = Slumpmässigt
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = Korttyp
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = Ordning samlad
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = Sammanväv med repetitionskort
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = Visa efter repetitioner
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = Visa innan repetitioner
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = Förfallodatum, sedan slumpad
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = Förfallodatum, sedan kortlek
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = Kortlek, sedan förfallodatum
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = Ökande intervall
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = Minskande intervall
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = Ökande lätthet
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = Minskande lätthet
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = Ökande svårighet
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = Minskande svårighet
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = Ökande återkallbarhet
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = Minskande återkallbarhet

## Timer section

deck-config-timer-title = Timer
deck-config-maximum-answer-secs = Max antal sekunder för svar
deck-config-maximum-answer-secs-tooltip =
    Det maximala antalet sekunder som räknas för en repetition. Om ett svar
    överstiger denna tid (exempelvis för att skärmen lämnades en stund),
    kommer den åtgångna tiden att räknas som den satta gränsen.
deck-config-show-answer-timer-tooltip =
    Visa ett tidur på repetitionsskärmen som räknar antalet sekunder som
    åtgått för att repetera kortet.
deck-config-stop-timer-on-answer = Pausa timer medan svar skrivs
deck-config-stop-timer-on-answer-tooltip =
    Huruvida tiduret bör stannas när svaret avslöjas.
    Detta påverkar ej statistiken.

## Auto Advance section

deck-config-seconds-to-show-question = Antal sekunder fråga visas
deck-config-seconds-to-show-question-tooltip-3 = När automatisk frammatning är aktiverad, antalet sekunder att vänta innan frågoåtgärden tillämpas. Sätt till 0 för att inaktivera.
deck-config-seconds-to-show-answer = Antal sekunder svar visas
deck-config-seconds-to-show-answer-tooltip-2 = När automatisk frammatning är aktiverad, antalet sekunder att vänta innan svarsåtgärden tillämpas. Sätt till 0 för att inaktivera.
deck-config-question-action-show-answer = Visa svar
deck-config-question-action-show-reminder = Visa påminnelse
deck-config-question-action = Frågoåtgärd
deck-config-question-action-tool-tip = Åtgärd som utförs när frågan har visats och tiden är slut
deck-config-answer-action = Svarsåtgärd
deck-config-answer-action-tooltip-2 = Åtgärd som utförs när svaret har visats och tiden är slut.
deck-config-wait-for-audio-tooltip-2 = Vänta på att ljudet spelas innan frågo- eller svarsåtgärden tillämpas automatiskt.

## Audio section

deck-config-audio-title = Ljud
deck-config-disable-autoplay = Spela inte ljud automatiskt
deck-config-disable-autoplay-tooltip =
    När aktiverad kommer Anki inte spela ljud automatiskt.
    Det kan fortfarande spelas manuellt genom att trycka på ljudikonen eller använda åtgärden Spela upp ljud igen.
deck-config-skip-question-when-replaying = Hoppa över fråga när svar spelas upp igen
deck-config-always-include-question-audio-tooltip =
    Huruvida frågans ljud bör inkluderas när Spela upp ljud igen-åtgärden
    används medan svarssidan av kortet åskådas.

## Advanced section

deck-config-advanced-title = Avancerat
deck-config-maximum-interval-tooltip =
    Det maximala antalet dagar ett repetitionskort kan vänta. När repetitioner
    har uppnått gränsen kommer `Svår`, `Bra` och `Lätt` alla ge samma frist.
    Ju kortare detta är satt till, desto större kommer arbetsbelastningen att vara.
deck-config-starting-ease-tooltip =
    Lätthetsfaktorn nya kort börjar med. Som förval kommer `Bra`-knappen på ett
    nyligen inlärt kort att uppskjuta nästa repetition med 2,5x den tidigare fristen.
deck-config-easy-bonus-tooltip =
    En ytterligare faktor som multipliceras med ett repetitionskorts intervall när det
    graderas som `Lätt`.
deck-config-interval-modifier-tooltip =
    Denna faktor tillämpas för alla repetitioner, och mindre justeringar kan användas för
    att göra Anki mer konservativ eller aggressiv i sin schemaläggning. Var god
    se manualen före ändring av denna inställning.
deck-config-hard-interval-tooltip = Faktorn som multipliceras med ett repetitionsintervall efter att `Svår` svaras.
deck-config-new-interval-tooltip = Faktorn som multipliceras med ett repetitionsintervall efter att `Igen` svaras.
deck-config-minimum-interval-tooltip = Minimiintervallet som ges till ett repetitionskort efter att `Igen` svaras.
deck-config-custom-scheduling = Anpassad schemaläggning
deck-config-custom-scheduling-tooltip = Påverkar hela samlingen. Använd på egen risk!

## Easy Days section.

deck-config-easy-days-title = Lätta dagar
deck-config-easy-days-monday = Måndag
deck-config-easy-days-tuesday = Tisdag
deck-config-easy-days-wednesday = Onsdag
deck-config-easy-days-thursday = Torsdag
deck-config-easy-days-friday = Fredag
deck-config-easy-days-saturday = Lördag
deck-config-easy-days-sunday = Söndag
deck-config-easy-days-normal = Normal
deck-config-easy-days-reduced = Minskad
deck-config-easy-days-minimum = Minimal
deck-config-easy-days-no-normal-days = Åtminstone en dag bör vara satt till '{ deck-config-easy-days-normal }'.
deck-config-easy-days-change = Befintliga repetitioner kommer inte omplaneras såvida '{ deck-config-reschedule-cards-on-change }' inte är aktiverad i FSRS-alternativen.

## Adding/renaming

deck-config-add-group = Lägg till förinställning
deck-config-name-prompt = Namn
deck-config-rename-group = Döp om förinställnig
deck-config-clone-group = Klona förinställning

## Removing

deck-config-remove-group = Radera förinställning
deck-config-will-require-full-sync =
    För att utföra den önskade ändringen erfordras en envägssynkronisering. Vid ändringar på
    en annan enhet som inte än är synkroniserade till denna enhet, var god synkronisera dessa
    först.
deck-config-confirm-remove-name = Radera { $name }?

## Other Buttons

deck-config-save-button = Spara
deck-config-save-to-all-subdecks = Spara till alla underkortlekar
deck-config-save-and-optimize = Optimera alla förinställningar
deck-config-revert-button-tooltip = Återställ denna inställning till sitt förvalda värde?

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Anki 2.1.41+-hantering
deck-config-description-new-handling-hint =
    Behandlar inmatning som Markdown, och rensar HTML-inmatning. När aktiverad
    kommer beskrivningen även visas på gratulationsskärmen.
    Markdown kommer te sig som rå text på Ankiversion 2.1.40 och under.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    { $cards ->
        [one] En överordnad kortlek har en gräns på { $cards } kort, vilket kommer åsidosätta denna gräns.
       *[other] En överordnad kortlek har en gräns på { $cards } kort, vilket kommer åsidosätta denna gräns.
    }
deck-config-reviews-too-low =
    { $cards ->
        [one] Om { $cards } nytt kort läggs till varje dag, bör repetitionsgränsen vara åtminstone { $expected }.
       *[other] Om { $cards } nya kort läggs till varje dag, bör repetitionsgränsen vara åtminstone { $expected }.
    }
deck-config-learning-step-above-graduating-interval = Befordringsintervallet bör vara åtminstone lika långt som det sista inlärningssteget.
deck-config-good-above-easy = Lättintervallet bör vara åtminstone lika långt som befordringsintervallet.
deck-config-relearning-steps-above-minimum-interval = Det minsta bortglömningsintervallet borde vara åtminstone lika långt som det sista ominlärningssteget.
deck-config-maximum-answer-secs-above-recommended = Anki kan schemalägga repetitioner mer effektivt om varje enskild fråga är kortfattad.
deck-config-too-short-maximum-interval = Ett största intervall på mindre än 6 månader rekommenderas ej.
deck-config-ignore-before-info = (Uppskattningsvis) { $included }/{ $totalCards } kort kommer användas för att optimera FSRS-parametrarna.

## Selecting a deck

deck-config-which-deck = Vilken kortlek vill du välja?

## Messages related to the FSRS scheduler

deck-config-updating-cards = Uppdaterar kort: { $current_cards_count } av { $total_cards_count } ...
deck-config-invalid-parameters = De tillhandahållna FSRS-parametrarna är ogiltiga. Lämna dem blanka för att använda de förvalda parametrarna.
deck-config-not-enough-history = Otillräcklig repetitionshistorik för att utföra denna åtgärd.
deck-config-must-have-400-reviews =
    { $count ->
        [one] Enbart { $count } repetition hittades. Åtminstone 400 repetitioner krävs för denna åtgärd.
       *[other] Enbart { $count } repetitioner hittades. Åtminstone 400 repetitioner krävs för denna åtgärd.
    }
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = FSRS-parametrar
deck-config-compute-optimal-weights = Optimera FSRS-parametrar
deck-config-optimize-button = Optimera
# Indicates that a given function or label, provided via the "text" variable, operates slowly.
deck-config-slow-suffix = { $text } (långsam)
deck-config-compute-button = Beräkna
deck-config-ignore-before = Ignorera repetitioner innan
deck-config-time-to-optimize = Det var ett tag sedan - att använda knappen Optimera alla förinställningar rekommenderas.
deck-config-evaluate-button = Utvärdera
deck-config-desired-retention = Önskad återkallningskvot
deck-config-historical-retention = Historisk återkallningskvot
deck-config-smaller-is-better = Mindre tal antyder en bättre passning till repetitionshistoriken.
deck-config-steps-too-large-for-fsrs = När FSRS är aktiverat avrådes steg större än 1 dag.
deck-config-get-params = Hämta parametrar
deck-config-complete = { $num } % klart
deck-config-iterations = Iteration: { $count } ...
deck-config-reschedule-cards-on-change = Omplanera kort vid ändring
deck-config-fsrs-tooltip =
    Påverkar hela samlingen.
    
    FSRS (Free Spaced Repetition Scheduler) är ett alternativ till den gamla schemaläggaren för Anki, SuperMemo 2 (SM-2). FSRS kan mer exakt bestämma sannolikheten att ett kort glöms, vilket i slutändan innebär att mer information kan memoreras på samma tid. Inställningen är gemensam för alla förinställningar.
deck-config-desired-retention-tooltip =
    Som förval schemaläggs kort så att sannolikheten att de återkallas är 90 % vid tillfället för repetition.
    Om detta värde ökas kommer Anki att visa kort oftare för att öka sannolikheten att de återkallas. Om
    detta värde minskas kommer Anki däremot att visa kort mer sällan, varför fler av korten kommer att
    glömmas. Rekommendationen är att vara återhållsam vid justering av denna inställning, då höga
    värden kommer öka arbetsbelastningen mycket, medan låga värden riskerar att orsaka missmod.
deck-config-desired-retention-tooltip2 = Arbetsbelastningen som står i inforutan är en grov uppskattning. För att öka precisionen, var god använd simulatorn.
deck-config-historical-retention-tooltip =
    När en del av repetitionshistoriken saknas måste FSRS fylla i luckorna. Som förval kommer
    FSRS anta att, när de gamla repetitionerna utfördes, återkallades 90 % av korten. Om den gamla
    återkallningskvoten var avsevärt högre eller lägre än 90 % låter denna inställning FSRS bättre
    uppskatta de saknade repetitionerna.
    
    Repetitionshistoriken kan vara ofullständig av två anledningar:
    1. Eftersom alternativet 'ignorera tidigare repeterade kort' är aktiverat.
    2. Eftersom repetitionsloggar tidigare har raderats för att frigöra lagringsutrymme, eller eftersom material importerats
    från ett annat SRS-program.
    
    Det senare är tämligen ovanligt, så om inte det tidigare alternativet inte är aktiverat behöver detta alternativ troligen
    inte justeras.
deck-config-weights-tooltip2 =
    FSRS-parametrar påverkar hur korten schemaläggs. Anki kommer utgå från de förvalda parametrarna. Alternativet
    nedan kan användas för att optimera parametrarna för att bäst överensstämma med de kortlekar som använder denna förinställning.
deck-config-reschedule-cards-on-change-tooltip =
    Påverkar hela samlingen, och sparas ej.
    
    Detta alternativ styr om kortens förfallodatum ändras när du aktiverar FSRS eller optimerar parametrarna. Förvalet är att inte omplanera kort: framtida repetitioner kommer använda den nya schemaläggningen, men
    det kommer inte vara någon omedelbar förändring i arbetsbelastningen. Om omplanering är aktiverat kommer kortens förfallodatum däremot att ändras.
deck-config-reschedule-cards-warning =
    Rekommenderas ej vid det inledande bytet från SM-2 eftersom detta, beroende på den önskade
    återkallningskvoten, kan resultera i att ett stort antal kort förfaller.
    
    Använd detta alternativ förbehållsamt, då det kommer lägga till en repetitionspost för varje kort
    och därmed öka storleken på samlingen.
deck-config-ignore-before-tooltip-2 =
    Om aktiverat kommer kort repeterade före det tillhandahållna datumet att ignoreras när FSRS-parametrarna optimeras.
    Detta kan vara användbart om någon annans schemaläggningsdata har importerats, eller sättet svarsknapparna brukas är annorlunda.
deck-config-compute-optimal-weights-tooltip2 =
    När Optimera-knappen nedtrycks kommer FSRS att analysera repetitionshistoriken och generera parametrar som
    är optimala för användarens minnesförmåga och det studerade innehållet. Om kortlekarna skiljer sig avsevärt i svårighetsgrad
    rekommenderas att de tilldelas olika förinställningar, eftersom parametrarna för lätta och svåra kortlekar kommer skilja sig.
    Parametrarna behöver ej justeras ofta - några månaders mellanrum är tillräckligt ofta.
    
    Som förval kommer parametrar att beräknas utifrån repetitionshistoriken för alla kortlekar som använder den valda förinställningen.
    Sökningen kan valfritt justeras innan parametrarna beräknas om de kort som ingår i beräkningen önskas ändras.
deck-config-please-save-your-changes-first = Var god spara dina ändringar först.
deck-config-workload-factor-change =
    Uppskattad arbetsbelastning: { $factor }x
    (i jämförelse med den önskade återkallningskvoten på { $previousDR }%)
deck-config-workload-factor-unchanged = Ju högre värde, desto oftare kommer kort att visas.
deck-config-desired-retention-too-low = Den önskade återkallningskvoten är mycket låg, vilket kan leda till väldigt långa intervaller.
deck-config-desired-retention-too-high = Den önskade återkallningskvoten är mycket hög, vilket kan leda till väldigt korta intervaller.
deck-config-percent-of-reviews =
    { $reviews ->
        [one] { $pct }% av { $reviews } repetition
       *[other] { $pct }% av { $reviews } repetitioner
    }
deck-config-percent-input = { $pct } %
# This message appears during FSRS parameter optimization.
deck-config-checking-for-improvement = Granskar för förbätting ...
deck-config-optimizing-preset = Optimerar förinställning { $current_count } av { $total_count } ...
deck-config-fsrs-must-be-enabled = FSRS måste vara aktiverat först.
deck-config-fsrs-params-optimal = FSRS-parametrarna förefaller redan vara optimala.
deck-config-fsrs-params-no-reviews = Inga repetitioner kunde hittas. Var god kolla att denna förinställning är tilldelad alla kortlekar som önskas optimeras (inklusive underkortlekar) och försök igen.
deck-config-wait-for-audio = Vänta på ljud
deck-config-show-reminder = Visa påminnelse
deck-config-answer-again = Svara "igen"
deck-config-answer-hard = Svara "svårt"
deck-config-answer-good = Svara "bra"
deck-config-days-to-simulate = Dagar att simulera
deck-config-desired-retention-below-optimal = Den önskade återkallningskvoten är under det optimala värdet. Att öka den rekommenderas.
# Description of the y axis in the FSRS simulation
# diagram (Deck options -> FSRS) showing the total number of
# cards that can be recalled or retrieved on a specific date.
deck-config-fsrs-simulator-experimental = FSRS-simulator (experimentell)
deck-config-fsrs-simulate-desired-retention-experimental = FSRS simulator för önskad återkallningskvot (experimentell)
deck-config-fsrs-simulate-save-preset = Efter att du har optimerat, var god spara kortleken innan du kör simulatorn.
deck-config-fsrs-desired-retention-help-me-decide-experimental = Hjälpverktyg (experimentell)
deck-config-additional-new-cards-to-simulate = Ytterligare nya kort att simulera
deck-config-simulate = Simulera
deck-config-clear-last-simulate = Rensa senaste simulering
deck-config-fsrs-simulator-radio-count = Repetitioner
deck-config-advanced-settings = Avancerade inställningar
deck-config-smooth-graph = Glatt kurva
deck-config-suspend-leeches = Lås energislukare
deck-config-save-options-to-preset = Spara ändringar till förinställning
deck-config-save-options-to-preset-confirm = Skriva över alternativen i den nuvarande förinställningen med alternativen som är satta i simulatorn?
# Radio button in the FSRS simulation diagram (Deck options -> FSRS) selecting
# to show the total number of cards that can be recalled or retrieved on a
# specific date.
deck-config-fsrs-simulator-radio-memorized = Memorerade
deck-config-fsrs-simulator-radio-ratio = Tid per memorerad
# $time here is pre-formatted e.g. "10 Seconds" 
deck-config-fsrs-simulator-ratio-tooltip = { $time } per memorerat kort

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.

# Checkbox
deck-config-health-check = Kontrollera FSRS anpassningsgrad vid optimering
# Message box showing the result of the health check
deck-config-fsrs-bad-fit-warning =
    FSRS-kontroll:
    Användarens minne är svårförutsägbart. Förslag:
    
    - Lås eller omformulera energislukare
    - Använd svarsknapparna konsekvent. Kom ihåg att "svår" innebär att kortet graderas som avklarat, inte misslyckat.
    - Förstå innehåller före memorering.
    
    Vid efterlevnad av dessa förslag brukar prestandan förbättras över de kommande månaderna.
# Message box showing the result of the health check
deck-config-fsrs-good-fit =
    FSRS-kontroll:
    FSRS kan anpassa sig väl till användarens minnesförmåga.

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-unable-to-determine-desired-retention = Misslyckades att bestämma en minsta rekommenderad återkallningskvot.
deck-config-predicted-minimum-recommended-retention = Minsta rekommenderad återkallningskvot: { $num }
deck-config-compute-minimum-recommended-retention = Minsta rekommenderad återkallningskvot
deck-config-compute-optimal-retention-tooltip4 =
    Detta verktyg kommer försöka hitta den önskade återkallningskvot
    som kommer leda till att mest material lärs på minst tid. Det beräknade talet kan tjäna som ett referensvärde
    vid ändring av den önskade återkallningskvoten. Det kan vara värt att välja en högre önskad återkallningskvot om
    tiden läggs ned för att uppnå den. Att sätta den önskade återkallningskvoten under minimivärdet
    rekommenderas ej, då det kommer leda till en högre arbetsbelastning på grund av det höga andelen kort som bortglöms.
deck-config-plotted-on-x-axis = (ritad på X-axeln)
deck-config-a-100-day-interval =
    { $days ->
        [one] Ett 100-dagarsintervall kommer bli { $days } dag.
       *[other] Ett 100-dagarsintervall kommer bli { $days } dagar.
    }
deck-config-fsrs-simulator-y-axis-title-time = Repetitionstid/dag
deck-config-fsrs-simulator-y-axis-title-count = Repetitionsantal/dag
deck-config-fsrs-simulator-y-axis-title-memorized = Totalt memorerat
deck-config-bury-siblings = Dölj syskon
deck-config-do-not-bury = Dölj inte syskon
deck-config-bury-if-new = Dölj om ny
deck-config-bury-if-new-or-review = Dölj om ny eller repeterande
deck-config-bury-if-new-review-or-interday = Dölj om ny, repeterande eller flerdagslöpande inlärning
deck-config-bury-tooltip =
    Syskon är andra kort från samma not (e.g. fram/omvända kort, eller andra
    lucktexter från samma text).
    
    När detta alternativ är inaktiverat kan många kort från samma not visas på samma
    dag. När detta alternativ är aktiverat kommer Anki att *dölja* syskon, vilket gömmer dem tills nästa
    dag. Detta alternativ tillåter att välja vilken sorts kort som döljs när en av deras
    syskon besvaras.
    
    När V3-schemaläggaren används kommer flerdagslöpande inlärningskort också att begravas. Flerdagslöpande
    inlärningskort är kort som har ett nuvarande inlärningssteg på mer än en dag.
deck-config-seconds-to-show-question-tooltip = När automatisk frammatning är aktiverad, antalet sekunder att vänta innan svaret avslöjas. Sätt till 0 för att inaktivera.
deck-config-answer-action-tooltip = Åtgärden att utföra på det nuvarande kortet innan automatisk fortsättning till nästa kort.
deck-config-wait-for-audio-tooltip = Vänta på att ljudet spelas klart innan automatiskt avslöjande av svaret eller nästa fråga.
deck-config-ignore-before-tooltip =
    Om aktiverat kommer repeterade före det tillhandahållna datumet att ignoreras när FSRS-parametrarna optimeras & utvärderas.
    Detta kan vara användbart om någon annans schemaläggningsdata har importerats, eller sättet svarsknapparna brukas är annorlunda.
deck-config-compute-optimal-retention-tooltip =
    Detta verktyg antar att 0 kort börjas med och kommer försöka beräkna mängden innehåll som kommer
    ihågkommas i den tillhandahållna tidsramen. Den uppskattade återkallningskvoten kommer skilja sig vitt beroende på inmatningarna,
    och om den skiljer sig avsevärt från 0,9 är det ett tecken att tiden allokerad varje dag är för lite eller för mycket
    för den mängd kort som studeras. Detta tal kan tjäna som en referens, men rekommenderas inte att kopieras till
    fältet för önskad återkallningskvot.
deck-config-health-check-tooltip1 = Detta kommer visa en varning om FSRS misslyckas att anpassa sig till användarens minne.
deck-config-health-check-tooltip2 = FSRS-kontroll utförs endast vid användning av Optimera denna förinställning.
deck-config-compute-optimal-retention = Beräkna minsta rekommenderad återkallningskvot
deck-config-predicted-optimal-retention = Minsta rekommenderad återkallningskvot: { $num }
deck-config-weights-tooltip =
    FSRS-parametrar påverkar hur korten schemaläggs. Anki kommer utgå från de förvalda parametrarna. När 1000+ repetitioner har ansamlats kan alternativet nedan kan användas för att optimera parametrarna för att bäst
    överensstämma med de kortlekar som använder denna förinställning.
deck-config-compute-optimal-weights-tooltip =
    När 1000+ repetitioner har utförts i Anki kan Optimera-knappen användas för att FSRS ska analysera repetitionshistoriken och automatiskt generera parametrar som
    är optimala för användarens minnesförmåga och det studerade innehållet. Om kortlekar finns som skiljer sig avsevärt i svårighetsgrad
    rekommenderas att de tilldelas olika förinställningar, eftersom parametrarna för lätta och svåra kortlekar kommer skilja sig.
    Parametrarna behöver ej justeras ofta - några månaders mellanrum är tillräckligt ofta.
    
    Som förval kommer parametrar att beräknas utifrån repetitionshistoriken för alla kortlekar som använder den valda förinställningen.
    Sökningen kan valfritt justeras innan parametrarna beräknas om de kort som ingår i beräkningen önskas ändras.
deck-config-compute-optimal-retention-tooltip2 =
    Detta verktyg antar att 0 inlärda kort finns och kommer försöka hitta den önskade återkallningskvot
    som kommer leda till att mest material lärs på minst tid. Det beräknade talet kan tjäna som ett referensvärde
    vid ändring av den önskade återkallningskvoten. Det kan vara värt att välja en högre önskad återkallningskvot om
    du lägger ned tiden för en högre återkallningskvot. Att sätta den önskade återkallningskvoten under minimivärdet
    rekommenderas ej, då det kommer leda till mer arbete utan någon vinst.
deck-config-compute-optimal-retention-tooltip3 =
    Detta verktyg antar att 0 inlärda kort finns och kommer försöka hitta den önskade återkallningskvot
    som kommer leda till att mest material lärs på minst tid. För att precist simulera inlärningsframsteg
    kräver detta verktyg åtminstone 400+ repetitioner. Det beräknade talet kan tjäna som ett referensvärde
    vid ändring av den önskade återkallningskvoten. Det kan vara värt att välja en högre önskad återkallningskvot om
    du lägger ned tiden för en högre återkallningskvot. Att sätta den önskade återkallningskvoten under minimivärdet
    rekommenderas ej, då det kommer leda till en högre arbetsbelastning, på grund av den höga andelen kort som bortglöms.
deck-config-seconds-to-show-question-tooltip-2 = När automatisk frammatning är aktiverad, antalet sekunder att vänta innan svaret avslöjas. Sätt till 0 för att inaktivera.
deck-config-invalid-weights = Parametrarna måste antingen lämnas blanka för att använda de förvalda parametrarna, eller vara 17 kommaseparerade tal.
deck-config-fsrs-on-all-clients = Var god försäkra att alla Anki-klienter är Anki(Mobile) 23.10+ eller AnkiDroid 2.17+. FSRS kommer inte fungera korrekt om en av klienterna är äldre.
deck-config-optimize-all-tip = Alla förinställningar kan optimeras samtidigt genom att använda rullgardinsmenyn intill "Spara".
