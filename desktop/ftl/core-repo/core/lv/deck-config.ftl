### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    { $decks ->
        [zero] ir izmantota { $decks } kavās
        [one] ir izmantota { $decks } kavās
       *[other] ir izmantota { $decks } kavās
    }
deck-config-default-name = Noklusējums
deck-config-title = Kavu iespējas

## Daily limits section

deck-config-daily-limits = Dienas ierobežojumi
deck-config-new-limit-tooltip =
    Lielākais dienā priekšā stādāmo jauno kartīšu skaits, ja ir pieejamas jaunas kartītes.
    Tā kā jauna viela īstermiņā palielinās pārskatīšanas apjomu, tam parasti
    vajadzētu būt vismaz 10 reizes mazākam par pārskatīšanas ierobežojumu.
deck-config-review-limit-tooltip =
    Lielākais dienā parādāmo pārskatāmo kartīšu skaits,
    ja kartītes ir sagatavotas pārskatīšanai.
deck-config-limit-deck-v3 =
    Izpētot kāršu komplektu, kurā ir apakškavas, katrai apakškavai
    notiek limitkontrole uz maksimālo kāršu skaitu, kas tiek savākts no konkrētā kavā.
    Izvēlētās kavas limiti kontrolē kopējo parādīto kāršu skaitu.
deck-config-limit-new-bound-by-reviews =
    Pārskatīšanas ierobežojums ietekmē jauno ierobežojumu. Piemēram, ja
    pārskatīšanas ierobežojums ir iestatīts uz 200 pārskatīšanām un jums ir 190 neizskatītas 
    pārskatīšanas, tiks ieviestas ne vairāk kā 10 jaunas kartes. Ja pārskatu ierobežojums ir 
    sasniegts, jaunas kartes netiks rādītas.
deck-config-limit-interday-bound-by-reviews =
    Pārskatīšanas ierobežojums ietekmē arī starpdienu mācību kartes. Piemērojot ierobežojumu,
    vispirms tiek apkopotas starpdienu mācību kartes, pēc tam pārskata kartes.
deck-config-tab-description =
    - „Iepreikš iestatīts”: Ierobežojums attiecas uz visām kavām, kuras izmanto šo iestatījumu.
    - „Šī kava”: Šis ierobežojums attiecas tikai uz šo kavu.
    - „Tikai šodien”: Veiciet pagaidu izmaiņas šīs kavas limitā.
deck-config-new-cards-ignore-review-limit = Jaunās kārtis ignorē pārskatīšanas ierobežojumus
deck-config-new-cards-ignore-review-limit-tooltip =
    Pēc noklusējuma pārskatīšanas ierobežojumi attiecas arī uz jaunām
    kārtīm, un jaunas kārtis netiks atvērtas. tiks rādītas, ja būs sasniegts
    pārskatīšanas limits. Ja šī opcija ir iespējota, jaunas kārtis tiks rādītas
    neatkarīgi no pārskatīšanas limita.
deck-config-apply-all-parent-limits = Ierobežojumi sākas no augšas
deck-config-apply-all-parent-limits-tooltip =
    Pēc noklusējuma augstākā līmeņa kāršu komplekta ikdienas ierobežojumi netiek piemēroti,
    ja mācāties no tā apakškavas. Ja šī opcija ir aktivizēta, ierobežojumi tiks piemēroti, sākot no
    augstākā līmeņa kavas, kas var būt noderīgi, ja vēlaties mācīties atsevišķas apakškavas,
    vienlaikus nosakot kopējo ierobežojumu kāršu skaitam visā komplekta hierarhijā.
deck-config-affects-entire-collection = Ietekmē visu krājumu.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Iepriekš iestatītais
deck-config-deck-only = Šī kava
deck-config-today-only = Tikai šodien

## New Cards section

deck-config-learning-steps = Mācību soļi
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Aizkaves parasti tiek norādītas minūtēs (piem. „1 min”) vai dienās (piem. „2 d”), taču tiek atbalstītas arī stundas (piem. „1 st.”) un sekundes (piem. „30 s”).
deck-config-learning-steps-tooltip =
    Viena vai vairākas aizkaves, atdalītas ar atstarpēm. Pirmā aizkave tiks piemērota,
    kad jaunā kartīte tiek nospiesta poga „Vēlreiz”, un pēc noklusējuma tā ir 1 minūte.
    Poga „Labi” pāries uz nākamo soli, kas pēc noklusējuma ir 10 minūtes.
    Tiklīdz visi soļi būs izieti, kartīte kļūs par pārskatāmo kartīti, un tā parādīsies
    citā dienā. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip =
    Dienu skaits, kas jāgaida, pirms kartīte tiek atkal parādīta pēc tam,
    kad pēdējā mācību solī ir nospiesta poga „Labi”.
deck-config-easy-interval-tooltip =
    Dienu skaits, kas jāgaida, pirms kartīte tiek atkal rādīta pēc pogas „Viegli”
    izmantošanas, kas uzreiz noņem kartīti no mācīšanās.
deck-config-new-insertion-order = Ievietošanas secība
deck-config-new-insertion-order-tooltip =
    Pārvalda pozīciju (līdz #), kuru jaunām kārtīm piešķir, tās pievienojot.
    Mācoties, kārtis ar zemāku „līdz” skaitli tiks rādītas pirmās.
    Mainot šo iespēju, automātiski tiks atjaunināta esošā jaunās kārts pozīcija.
deck-config-new-insertion-order-sequential = Pēc kārtas (vecākais vispirms)
deck-config-new-insertion-order-random = Pēc nejaušības
deck-config-new-insertion-order-random-with-v3 =
    Ar v3 grafika veidotāju labāk ir atstāt šo iestatījumu uz „Pēc kārtas”
    un pielāgot jaunās kārts apkopojuma secību.

## Lapses section

deck-config-relearning-steps = Pārapguves soļi
deck-config-relearning-steps-tooltip =
    Nulle vai vairāk aizkavju, atdalītas ar atstarpēm. Pēc noklusējuma pogas „Vēlreiz”
    nospiešana pārskatāmajā kartītē tā tiks rādīta atkārtoti pēc 10 minūtēm. Ja nav
    norādītas aizkaves, tiks mainīts kartītes starplaiks, nepārejot uz pārapguvi.
    { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip =
    Cik reižu pārskatāmajā kartītē jāspiež "Vēlreiz", pirms tā tiek atzīmēta kā
    izsūcoša. Izsūcošās kartītes ir kartītes, kas patērē daudz laika, un, kad
    kartīte ir atzīmēta kā izsūcoša, laba doma ir to pārrakstīt, izdzēst vai
    izdomāt mnemoniku, kas palīdzētu to atcerēties.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    „Tikai birka”: pievieno piezīmei birku "leech" un parāda uznirstošo logu.
    
    „Atlikt kartīti”: papildus piezīmes atzīmēšanai ar birku paslēpj kartīti,
    līdz tā tiek pašrocīgi atjaunota.

## Burying section

deck-config-bury-title = Paslēpšana
deck-config-bury-new-siblings = Paslēpt jaunās saistītās kārtis
deck-config-bury-review-siblings = Paslēpt saistītās pārskatīšanas kārtis
deck-config-bury-interday-learning-siblings = Paslēpt starpdienu mācīšanās kārtis
deck-config-bury-new-tooltip =
    Vai citas tās pašas piezīmes „jaunās” kartītes (piem., apvērstās kartītes, blakus esošās
    aizpildes izdzēšanas) tiks atliktas līdz nākamajai dienai.
deck-config-bury-review-tooltip = Vai citas „pārskatīšanas” kārtis no tās pašas piezīmes tiks atliktas līdz nākamajai dienai.
deck-config-bury-interday-learning-tooltip = Vai citas „mācīšanās” kārtis no tās pašas piezīmes ar intervāliem > 1 dienu tiks atliktas līdz nākamajai dienai.
deck-config-bury-priority-tooltip =
    Kad Anki vāc kārtis, tas vispirms savāc iekšdienu mācīšanās kārtis, pēc tam
    starpdienu mācīšanās kārtis, pēc tam pārskatīšanas kārtis un visbeidzot jaunās kārtis.
    Tas ietekmē, kā kāršu paslēpšana darbojas:
    
    - Ja visi paslēpšanas iestatījumi ir ieslēgti, vispirms tiks rādīta tā saistītā kārts, kas ir 
    saraksta sākumā. Piemēram, pārskatīšanas kārts tiks rādīta pirms jaunās kārts.
    - Saistītās kārtis, kas atrodas tālāk sarakstā, nevar paslēpt vecākus kāršu veidus.
    Piemēram, ja izslēdz paslēpšanu jaunajām kārtīm un to mācies, tā neslēps nekādas
    starpdienu mācīšanās vai pārskatīšanas kārtis, un tu vari redzēt gan pārskatīšanas,
    gan jauno saistīto kārti tajā pašā sesijā.

## Gather order and sort order of cards

deck-config-ordering-title = Attēlošanas secība
deck-config-new-gather-priority = Jauno kāršu vākšanas secība
deck-config-new-gather-priority-tooltip-2 =
    „Kava”: secīgi iegūst kartītes no katras apakškavas, sākot no augšas. Kartītes no katras apakškavas tiek
    iegūtas augošā secībā. Ja tiek sasniegts izvēlētās kavas dienas ierobežojums, iegūšana var tikt
    pārtraukta, pirms ir pārbaudītas visas apakškavas. Šī secība ir ātrāka lielos krājumos, un
    ļauj noteikt lielāku svarīgumu apakškavām, kas atrodas tuvāk augšai.
    
    „Augošs novietojums”: iegūst kartītes augošā secībā (līdz #), kas parasti nozīmē,
    ka pirmās ir senāk pievienotās.
    
    „Dilstošs novietojums”: iegūst kartītes dilstošā secībā (līdz #), kas parasti nozīmē,
    ka pirmās ir nesenāk pievienotās.
    
    „Nejaušās piezīmes”: atlasa piezīmes nejauši, pēc tam iegūst tās visas kārtis.
    
    „Nejaušas kartītes”: iegūst kartītes nejaušā secībā.
deck-config-new-card-sort-order = Jauna kāršu šķirošanas secība
deck-config-new-card-sort-order-tooltip-2 =
    `Kartes tips, tad savākšanas secība`: parāda kartes pēc kartes tipa numura.
    Kartes ar katru kartes tipa numuru tiek parādītas tādā secībā, kādā tās tika savāktas. ¶
    Ja jums ir atspējota brāļu apglabāšana, tas nodrošinās, ka visas priekšējās→aizmugurējās kartes tiek parādītas pirms jebkādām aizmugurējām→priekšējām kartēm.¶
    Tas ir noderīgi, lai visas vienas piezīmes kartes tiktu parādītas vienā sesijā, bet ne¶
    pārāk tuvu viena otrai.¶
    ¶
    `Sakārtotas pēc savākšanas kārtības`: parāda kartes tieši tādā secībā, kādā tās tika savāktas. Ja brāļu apglabāšana ir atspējota,¶
    tas parasti nozīmē, ka visas vienas piezīmes kartes tiek parādītas viena pēc otras.¶
    ¶
    `Kartes tips, tad nejauši`: parāda kartes pēc kartes tipa numura. Kārtis ar katru kartes¶
    tipa numuru tiek parādītas nejaušā secībā. Šī secība ir noderīga, ja nevēlaties, lai līdzīgas kartes¶
    parādītos pārāk tuvu viena otrai, bet tomēr vēlaties, lai kartes parādītos nejaušā secībā.¶
    ¶
    `Nejauša piezīme, tad kartes tips`: Izvēlas piezīmes nejauši, tad parāda visas tās kartes¶
    secībā.¶
    ¶
    `Nejauši`: Parāda kartes nejaušā secībā.

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = Kava
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = Kava, pēc tam nejaušas piezīmes
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = Augoša pozīcija
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = Dilstoša pozīcija
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = Nejaušas piezīmes
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = Nejaušas kārtis

## Timer section


## Auto Advance section

deck-config-question-action-show-answer = Parādīt atbildi

## Audio section


## Advanced section

deck-config-new-interval-tooltip = Reizinātājs, kas tiek pielietots pārskatīšanas starplaikam, kad tiek atbildēts ar "Vēlreiz".
deck-config-minimum-interval-tooltip = Mazākais pieļaujamais pārskatāmajai kartītei piešķiramais starplaiks pēc atbildēšanas ar "Vēlreiz".
deck-config-custom-scheduling-tooltip = Ietekmē visu krājumu. Jāizmanto uz savu atbildību.

## Easy Days section.

deck-config-easy-days-monday = Pirmdiena
deck-config-easy-days-tuesday = Otrdiena
deck-config-easy-days-wednesday = Trešdiena
deck-config-easy-days-thursday = Ceturtdiena
deck-config-easy-days-friday = Piektdiena
deck-config-easy-days-saturday = Sestdiena
deck-config-easy-days-sunday = Svētdiena

## Adding/renaming


## Removing


## Other Buttons

deck-config-save-button = Saglabāt

## These strings are shown via the Description button at the bottom of the
## overview screen.


## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    { $cards ->
        [zero] Vecākkavai ir { $cards } kartīšu ierobežojums, kas aizvietos šo ierobežojumu.
        [one] Vecākkavai ir { $cards } kartītes ierobežojums, kas aizvietos šo ierobežojumu.
       *[other] Vecākkavai ir { $cards } kartīšu ierobežojums, kas aizvietos šo ierobežojumu.
    }
deck-config-relearning-steps-above-minimum-interval = Mazākajam pieļaujamajam misēkļu starplaikam ir jābūt vismaz tikpat ilgam kā pēdējam pārapguves solim.

## Selecting a deck


## Messages related to the FSRS scheduler

deck-config-fsrs-tooltip =
    Ietekmē visu krājumu.
    
    Free Spaced Repetition Scheduler (FSRS) ir aizvietotājs Anki novecojušajam Super Memo 2 (SM-2) algoritmam.
    Ar precīzāku kartītes aizmiršanas iespējas noteikšanu, tas var palīdzēt atcerēties
    vairāk vielas tajā pašā laika daudzumā. Šis iestatījums ir kopīgs visiem priekšiestatījumiem.
deck-config-desired-retention-tooltip =
    Pēc noklusējuma Anki ieplāno kartītes, lai tām būtu 90% atcerēšanās iespējamība, kad
    atkal pienāk to pārskatīšana. Ja šo vērtību palielina, Anki biežāk rādīs kartītes,
    lai tām palielinātu atcerēšanās iespēju. Ja vērtība tiek samazināta, Aki retāk rādīs
    kartītes, tādējādi vairāk tiks aizmirsts. Vēlams ievērot piesardzību, kad šo pielāgo, -
    lielākas vērtības ievērojami palielinās noslogojumu, un mazākas vērtības var būt
    nomācošas, kad tiek aizmirsts daudz vielas.
deck-config-historical-retention-tooltip =
    Kad trūkst daļa no pārskatīšanas vēstures, FSRS ir nepieciešams aizpildīt robus. Pēc noklusējuma tas
    pieņems, ka, kad tika veiktas vecās pārskatīšanas, Tu atcerējies 90% no vielas. Ja prātā tika paturēts
    vērā ņemami vairāk vai mazāk nekā 90%, šīs iespējas pielāgošana ļaus FSRS labāk novērtēt
    trūkstošās pārskatīšanas.
    
    Pārskatīšanu vēsture var būt nepilnīga divu iemeslu dēļ:
    1. jo Tu izmanto iespēju "Neņemt vērāk kartītes, kas pārskatītas prims";
    2. jo Tu iepriekš izdzēsi pārskatīšanas žurnālus, lai atbrīvotu vietu, vai ievietoji vielu no citas
    SRS programmas.
    
    Pēdējais iemesls ir diezgan rets, tā ka, ja vien neizmanto pirmo, visdrīzāk nav nepieciešams pielāgot
    šo iespēju.
deck-config-reschedule-cards-on-change-tooltip =
    Ietekmē visu krājumu, un netiek saglabāts.
    
    Šī iespēja nosaka, vai kartīšu pienākšanas laika datumi tiks mainīti, kad tiks iespējots FSRS vai optimizētas
    raksturvērtības. Noklusējums ir nepārplānot kartītes: nākotnes izskatīšanas izmantos jauno plānošanu, bet
    esošajam darba apjomam nebūs tūlītēju izmaiņu. Ja ir iespējota pārplānošana, kartīšu pienākšanas laika datumi
    tiks mainīti.
deck-config-reschedule-cards-warning =
    Atkarībā no vēlamā saglabāšanas laika tas var beigties ar liela daudzuma kartīšu laika pienākšanu, tādēļ nav ieteicams, kad pirmoreiz notiek pārslēgšanāš no SM-2.
    
    Šī iespēja ir jāizmanto piesardzīgi, jo tā pievienos jaunu pārskatīšanas ierakstu katrai kartītei un palielinās krājuma izmēru.
deck-config-fsrs-params-no-reviews = Nav atrasta neviena pārskatīšana. Lūgums pārbaudīt, ka šis priekšiestatījums ir piešķirts visām optimizējamajām kavām (tajā skaitā apakškavām), un mēģināt vēlreiz.
deck-config-answer-again = Atbildēt vēlreiz
deck-config-suspend-leeches = Atlikt izsūcošās kartītes

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.


## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-bury-tooltip =
    Līdzkartītes ir citas vienas piezīmes kartītes (piem., priekšējās/apvērstās kartītes
    vai citas aizpildes izdzēšanas tajā pašā tekstā).
    
    Kad šī iespēja ir izslēgta, vairākas vienas piezīmes kartītes var tikt parādītas tajā pašā
    dienā. Kad iespējota, Anki automātiski *noraks* līdzkartītes, paslēpjot tās līdz nākamajai
    dienai. Šī iespēja ļauj izvēlēties, kura veida kartītes var tikt paslēptas, kad tiek atbildēts
    uz vienu no to līdzkartītēm.
    
    Kad tiek izmantots V3 plānotājs, var paslēpt arī starpdienu mācīšanās kartītes. Starpdienu
    mācīšanās kartītes ir kartītes ar pašreizējo mācīšanās soli, kas ir viena vai vairākas dienas.
