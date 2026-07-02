### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    { $decks ->
        [one] uzata de { $decks } kartaro
       *[other] uzata de { $decks } kartaroj
    }
deck-config-default-name = Implicita
deck-config-title = Agordoj pri kartaro

## Daily limits section

deck-config-daily-limits = Tagaj limigoj
deck-config-new-limit-tooltip = La maksimuma nombro da novaj kartoj por montri por tago (se novaj kartoj estas disponeblaj). Ĉar la nova lern-materialo provizore pliigos vian necesan penon, tiu ĉi agordo estu almenaŭ 10-foje malpli granda ol via limigo de ripetoj.
deck-config-review-limit-tooltip = La maksimuma nombro da kartoj por ripeti dum tago (se ekzistas kartoj por ripeti).
deck-config-limit-deck-v3 = Ripetante kartaron, kiu ampleksas subkartarojn; la limigoj agorditaj al ĉiu subkartaro difinas la maksimumaj nombroj da kartoj prenataj el tiu kartaro. La limigoj de elektita kartaro difinas la tutan nombron da kartoj por montri.
deck-config-limit-new-bound-by-reviews = La limigo de ripetoj influas la novan limigon. Ekzemplo: se via limigo de ripetoj estas 200 kaj vi havas 190 atendantajn ripetojn, maksimume 10 novaj kartoj estos montrataj. Se vi atingos limigon de ripetoj, neniu nova karto montriĝos.
deck-config-limit-interday-bound-by-reviews = La limigo de ripetoj ankaŭ influas plurtage lernatajn kartojn. Post apliki la limigon, la plurtage lernataj kartoj estas montrataj antaŭ ol ripetataj kartoj.
deck-config-tab-description =
    - `Antaŭagordo`: la limigo aplikiĝas al ĉiuj kartaroj uzantaj tiun ĉi antaŭagordon.
    - `Tiu ĉi kartaro`: la limigo aplikiĝas nur al tiu ĉi kartaro.
    - `Nur hodiaŭ`: faras provizoran ŝanĝon nur al tiu ĉi kartaro.
deck-config-new-cards-ignore-review-limit = Novaj kartoj ignoras limigon de ripetoj
deck-config-new-cards-ignore-review-limit-tooltip = Implicite la limigo de ripetoj aplikiĝas ankaŭ al novaj kartoj, do neniu nova karto montriĝos antaŭ ol akiri la limigon de ripetoj. Se tiu ĉi agordo estas aktiva, novaj kartoj montriĝos malgraŭ la limigo de ripetoj.
deck-config-apply-all-parent-limits = Kalkuli limigojn ekde plej supera kartaro
deck-config-apply-all-parent-limits-tooltip = Implicite la tagaj limigoj de supera kartaro ne aplikiĝas dum ripeti kartojn el ĝia subkartaro. Se tiu ĉi agordo estas aktiva, la limigoj kalkuliĝos ekde la plej supera kartaro, kio povas esti utila, kiam vi volas ripeti apartajn subkartarojn devigante la tutan limigon de kartar-arbo.
deck-config-affects-entire-collection = Influas al la tuta kolekto.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Antaŭagordo
deck-config-deck-only = Tiu ĉi kartaro
deck-config-today-only = Nur hodiaŭ

## New Cards section

deck-config-learning-steps = Lernpaŝoj
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Intertempoj kutime estos en minutoj (ekzemple `1m`) aŭ tagoj (ekzemple `2d`), sed vi ankaŭ povas uzi horojn (ekzemple `1h`) kaj sekundojn (ekzemple `30s`).
deck-config-learning-steps-tooltip = Unu aŭ pli intertempoj, disigitaj per spacetoj. La unua intertempo estos uzita, kiam vi alklakos la butonon “Denove” ĉe nova karto, ĝi implicite estas 1 minuto. La butono “Bona” movos karton al la sekva paŝo, kiu implicite estas 10 minutoj.  Kiam ĉiuj paŝoj estos plenumitaj, la karto estiĝos ripeta karto kaj aperos je alia tago. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip = Nombro da tagoj por atendi antaŭ ol montri la karton denove, se estis premita la butono `Bona` dum la fina lernpaŝo.
deck-config-easy-interval-tooltip = La nombra da tagoj por atendi antaŭ ol montri la karton denove, se estis premita la butono `Facila` por senprokraste forigi la karton el lernado.
deck-config-new-insertion-order = Ordo de enmeto
deck-config-new-insertion-order-tooltip = Agordas pozicion (kampo Lernenda” #) asignitan al nova karto. Ju malpli altan pozicion “Lernenda” la karto havas, des pli frue la karto estos montrita por lerni. Ŝanĝi tiun ĉi agordon aŭtomate ŝanĝos ekzistajn poziciojn de novaj kartoj.
deck-config-new-insertion-order-sequential = laŭvice (malpli novaj kartoj unue)
deck-config-new-insertion-order-random = hazarde
deck-config-new-insertion-order-random-with-v3 = Uzante la planilon V3 estas pli bone lasi tion ĉi al “laŭvice” kaj alĝustigi la agordon “Ordo de preni novajn kartojn”.

## Lapses section

deck-config-relearning-steps = Relernpaŝoj
deck-config-relearning-steps-tooltip = Nul aŭ pli intertempoj, disigitaj per spacetoj. Implicite premi la butonon `Denove` remontros ripetatan karton post 10 minutoj. Se neniu intertempo estas agordita, intertempo de la karto ŝanĝiĝos, sen ŝanĝi staton de la karto al “relernata”. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip = Kiomfoje la butono `Denove` devas esti premita ĉe ripetata karto antaŭ ol ĝi estos markita kiel “forgesema”. Forgesemaj kartoj bezonas multan vian tempon por lerni. Konsideru reskribi tian karton, forigi ĝin aŭ elpensi memorarton, kiu helpos al vi memori ĝin.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `Aldoni nur etikedon`: etikedas karton kiel “forgeseman” kaj montras sciigon.
    
    `Paŭzigi karton`: krom etikedi la karton, kaŝas ĝin antaŭ ol vi permane ĝin malkaŝos.

## Burying section

deck-config-bury-title = Kaŝado (por tago)
deck-config-bury-new-siblings = Kaŝi novajn parencajn kartojn
deck-config-bury-review-siblings = Kaŝi ripetatajn parencajn kartojn
deck-config-bury-interday-learning-siblings = Kaŝi plurtage lernatajn parencajn kartojn
deck-config-bury-new-tooltip = Ĉu aliaj `novaj` kartoj de la sama noto (ekz. kartoj en mala direkto, apudaj truoj en teksto) estos prokrastitaj je unu tago.
deck-config-bury-review-tooltip = Ĉu aliaj `ripetataj` kartoj de la sama noto estos prokrastitaj je unu tago.
deck-config-bury-interday-learning-tooltip = Ĉu aliaj `lernataj` kartoj de la sama noto kun intertempoj > 1 tago estos prokrastitaj je unu tago.
deck-config-bury-priority-tooltip =
    Anki prenas kartojn laŭ la jena ordo: tiu-ĉi-tage lernataj kartoj, plurtage lernataj kartoj, ripetataj kartoj, novaj kartoj. Tio ĉi influas al maniero kiel kaŝado funkcias:
    
    - Se ĉiuj agordoj pri kaŝado estas aktivaj, parenca karto estanta pli unue en la listo estos montrata. Ekzemplo: ripetata karto montriĝos prefere al nova karto.
    - Parencaj kartoj estantaj poste en la listo ne povas kaŝi pli antaŭajn kartojn. Ekzemplo: se vi malaktivigos kaŝadon de novaj kartoj, kaj sekve lernos novan karton, ĝi ne povos kaŝi plurtagan aŭ ripetatan karton, do vi povas vidi kaj ripetatan parencan kaj novan kartojn dum unu lernada seanco.

## Gather order and sort order of cards

deck-config-ordering-title = Ordo de vidigo
deck-config-new-gather-priority = Ordo de preni novajn kartojn
deck-config-new-gather-priority-tooltip-2 =
    `Kartaro`: prenas kartojn de ĉiu subkartaro laŭ ordo, komence de plej supera. Kartoj el ĉiu subkartaro estas prenataj laŭ kreskanta ordo. Se la taga limigo por elektita kartaro estos akirita, prenata finos antaŭ ol kontroli ĉiujn subkartarojn. Tiu ĉi ordiga maniero estas plej rapida en grandaj kolektoj kaj ebligos al vi antaŭrangigi kartarojn estantajn pli supere.
    
    `Pozicio kreskante`: prenas kartojn laŭ kreskanta ordo (parametro “Lernenda #”), kutime unue prenas malnovajn kartojn.
    
    `Pozicio malkreskante`: prenas kartojn laŭ malkreskanta ordo (parametro “Lernenda #”), kutime unue prenas plej novajn karotjn.
    
    `Hazardaj notoj`: hazarde prenas notojn kaj sekve prenas ĉiujn iliajn kartojn.
    
    `Hazardaj kartoj`: prenas kartojn en hazarda ordo.
deck-config-new-card-sort-order = Ordo de novaj kartoj
deck-config-new-card-sort-order-tooltip-2 =
    `Kartotipo, sekve ordo de preno`: ordigas kartojn laŭ numero de kartotipo. Kartoj de ĉiu numero de kartotipo montriĝas laŭ ordo de preno. Se kaŝado de parencaj kartoj estas malaktiva, tio ĉi certigos, ke ĉiuj kartoj de speco “fronto→dorso” montriĝos antaŭ ĉiuj kartoj de speco “dorso→fronto”. Tio ĉi utilas por montri ĉiujn kartojn de la sama noto dum unu lernada seanco, sed ne tro proksime.
    
    `Ordo de preno`: ordigas kartojn ĝuste laŭ ordo de preno. Se kaŝado de parencaj kartoj estas malaktiva, tio ĉi probable montros ĉiujn kartojn de la sama noto unu post alia.
    
    `Kartotipo, sekve hazarde`: ordigas kartojn laŭ numero de kartotipo. Kartoj de ĉiu numero kartotipo montriĝos hazarde. Tio ĉi utilas, se vi ne volas por ke parencaj kartoj estu tro proksime, sed plue volas montri ilin laŭ hazarda ordo.
    
    `Hazarda noto, kaj kartotipo`: prenas notojn hazarde, kaj sekve montras ĉiujn iliajn kartoj laŭ la ordo.
    
    `Hazarde`: prenas kartojn laŭ hazarda ordo.
deck-config-new-review-priority = Ordo de novaj/ripetataj
deck-config-new-review-priority-tooltip = Kiam enmeti novajn kartojn rilate al ripetataj kartoj.
deck-config-interday-step-priority = Ordo de plurtage lernataj/ripetataj
deck-config-interday-step-priority-tooltip =
    Kie montri (re)lernatajn kartojn, kies intertempo estas pli longa ol tago.
    
    La limigo de ripetoj aplikiĝas unue al plurtage lernataj kartoj, kaj sekve al ripetataj kartoj. Tiu ĉi agordo ebligas alĝustigi ordon de prenataj kartoj, sed plurtage lernataj kartoj ĉiam montriĝos unue.
deck-config-review-sort-order = Ordo de ripetataj
deck-config-review-sort-order-tooltip = La implicita ordo antaŭrangigas plej longe atendantajn kartojn, do se vi havas malfruon kun ripetoj, la plej longe atendantaj ripetoj unue montriĝos. Se vi havas grandan malfruon, kiu necesigas kelkajn tagojn por prilabori aŭ volas vidi kartojn laŭ ordo de subkartaroj, alternativaj manieroj de ordigo povas helpi al vi fari tion.
deck-config-display-order-will-use-current-deck = Anki uzos ordon de la vidigo de kartaro elektita por lerni, ne de ĝiaj subkartaroj.

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = kartaro
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = kartaro, sekve hazardaj notoj
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = laŭ pozicio kreskante
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = laŭ pozicio malkreskante
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = hazardaj notoj
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = hazardaj kartoj
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = kartotipo, sekve hazarde
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = hazarda noto, sekve kartotipo
# Sort the cards randomly.
deck-config-sort-order-random = hazarde
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = kartotipo, kaj ordo de preno
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = ordo de preno
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = miksi kun ripetoj
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = montri post ripetoj
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = montri antaŭ ol ripetoj
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = planita dato, sekve hazarde
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = planita dato, sekve kartaro
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = kartaro, sekve planita dato
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = laŭ intertempoj kreskante
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = laŭ intertempoj malkreskante
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = laŭ facileco kreskante
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = laŭ facileco malkreskante
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = facilaj kartoj unue
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = malfacilaj kartoj unue
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = laŭ rememoriga probablo kreskante
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = laŭ rememoriga probablo malkreskante

## Timer section

deck-config-timer-title = Tempmezuriloj
deck-config-maximum-answer-secs = Maksimuma responda tempo (sekundoj)
deck-config-maximum-answer-secs-tooltip = La maksimuma nombro da sekundoj por registri respondon. Se respondado troigos tiun ĉi tempon – ekzemple, ĉar vi malproksimiĝos de la komputilo – la responda tempo registriĝos kiel la limigo agordita tie ĉi.
deck-config-show-answer-timer-tooltip = Montri tempmezurilon sur la lernada ekrano, kiu kalkulos tempon okupatan por lerni ĉiun karton.
deck-config-stop-timer-on-answer = Halti tempmezurilon post respondi
deck-config-stop-timer-on-answer-tooltip = Ĉu halti la tempmezurilon post montri respondon. Ne influas al statistikoj.

## Auto Advance section

deck-config-seconds-to-show-question = Montri demandon por (sekundoj)
deck-config-seconds-to-show-question-tooltip-3 = Kiam aŭtomata malkaŝo estas aktiva, nombro da sekundoj por atendi antaŭ ol plenumi agon de demando. Agordu al 0 por malaktivigi.
deck-config-seconds-to-show-answer = Montri respondon por (sekundoj)
deck-config-seconds-to-show-answer-tooltip-2 = Kiam aŭtomata malkaŝo estas aktiva, nombro da sekundoj antaŭ ol plenumi agon de respondo. Agordu al 0 por malaktivigi.
deck-config-question-action-show-answer = montri respondon
deck-config-question-action-show-reminder = montri sciigon
deck-config-question-action = Ago de demando
deck-config-question-action-tool-tip = La ago por plenumi post vidigi demandon kaj kiam pasis tempo por respondi.
deck-config-answer-action = Ago de respondo
deck-config-answer-action-tooltip-2 = La ago por plenumi post vidigi respondon kaj kiam pasis tempo por respondi.
deck-config-wait-for-audio-tooltip-2 = Atendi ĝis sono finiĝos antaŭ ol aŭtomate plenumi la agon de demando aŭ respondo.

## Audio section

deck-config-audio-title = Sono
deck-config-disable-autoplay = Ne ludi sonon aŭtomate
deck-config-disable-autoplay-tooltip = Kiam aktiva, Anki ne ludos sonon aŭtomate. La sono povos esti ludita permane per alklaki/frapeti bildsimbolon de sono aŭ per uzi la agon “Reludi”.
deck-config-skip-question-when-replaying = Preterpasi demandon dum reludi respondon
deck-config-always-include-question-audio-tooltip = Ĉu ludi sonon de demando je uzi la agon “Reludi” dum montri dorsan flankon de la karto.

## Advanced section

deck-config-advanced-title = Altnivelaj
deck-config-maximum-interval-tooltip = La maksimuma nombro da tagoj, kiom ripetata karto atendos ĝis la sekva ripeto. Kiam la limigo estos atingita, prokrastoj por `Malfacila`, `Bona` kaj `Facila` estos egalaj. Ju malpli alta tiu ĉi agordo estos, des pli granda estos via necesa peno.
deck-config-starting-ease-tooltip = La facilec-obligilo por novaj kartoj. Implicite presi la butonon `Bona` ĉe nove lernita karto prokrastos la sekvan ripeton je 2,5 × antaŭa intertempo.
deck-config-easy-bonus-tooltip = Kroma obligilo, kiu aplikiĝas al intertempo de ripetata karto, al kiu via respondo estas `Facila`.
deck-config-interval-modifier-tooltip = Tiu ĉi obligilo aplikiĝas al ĉiuj ripetataj kartoj. Delikataj modifoj al ĝi kaŭzos Anki plani ripetojn pli konserveme aŭ pli agrese. Legu la gvidlibron antaŭ ol modifi tiun ĉi agordon.
deck-config-hard-interval-tooltip = La obligilo, kiu aplikiĝas al intertempo de ripetata karto, al kiu via respondo estas `Malfacila`.
deck-config-new-interval-tooltip = La obligilo, kiu aplikiĝas al intertempo de ripetata karto, al kiu via respondo estas `Denove`.
deck-config-minimum-interval-tooltip = La minimuma intertempo, kiu aplikiĝos al ripetata karto, al kiu via respondo estas `Denove`.
deck-config-custom-scheduling = Propra planilo
deck-config-custom-scheduling-tooltip = Influas la tutan kolekton. Uzu nur je via risko!

## Easy Days section.

deck-config-easy-days-title = Facilaj tagoj
deck-config-easy-days-monday = Lu.
deck-config-easy-days-tuesday = Ma.
deck-config-easy-days-wednesday = Me.
deck-config-easy-days-thursday = Ĵa.
deck-config-easy-days-friday = Ve.
deck-config-easy-days-saturday = Sa.
deck-config-easy-days-sunday = Di.
deck-config-easy-days-normal = Norme
deck-config-easy-days-reduced = Malpli
deck-config-easy-days-minimum = Minimume
deck-config-easy-days-no-normal-days = Almenaŭ unu tago estu agordita al `{ deck-config-easy-days-normal }`.
deck-config-easy-days-change = Tiel longe, kiel la agordo “{ deck-config-reschedule-cards-on-change }” estos aktiva en la sekcio “FSRS”, ekzistaj ripetoj ne estos replanitaj.

## Adding/renaming

deck-config-add-group = Aldoni antaŭagordon
deck-config-name-prompt = Nomo
deck-config-rename-group = Alinomi antaŭagordon
deck-config-clone-group = Duobligi antaŭagordon

## Removing

deck-config-remove-group = Forigi antaŭagordon
deck-config-will-require-full-sync = La petita ŝanĝo postulos unudirektan samtempigon. Se vi faris ŝanĝojn ĉe alia aparato, kiuj ne estas samtempigitaj kun tiu ĉi aparato, faru la samtempigon antaŭ ol pluigi.
deck-config-confirm-remove-name = Ĉu forigi la antaŭagordon { $name }?

## Other Buttons

deck-config-save-button = Konservi
deck-config-save-to-all-subdecks = Konservi al ĉiuj subkartaroj
deck-config-save-and-optimize = Plejbonigi ĉiujn antaŭagordojn
deck-config-revert-button-tooltip = Restarigi tiun ĉi agordon al implicita valoro

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Subteni Anki 2.1.41+
deck-config-description-new-handling-hint = Trakti enigon kiel Markdown kaj forigi HTML-datumojn el enigo. Kiam aktiva, la priskribo ankaŭ montriĝos sur la ekrano de gratuloj. Markdown estos vidigita kiel teksto en Anki 2.1.40 kaj pli malnovaj.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    { $cards ->
        [one] Limigo de supera kartaro estas { $cards } karto, kiu superskribos tiun ĉi limigon.
       *[other] Limigo de supera kartaro estas { $cards } kartoj, kiu superskribos tiun ĉi limigon.
    }
deck-config-reviews-too-low =
    { $cards ->
        [one] Se vi aldonas { $cards } karton ĉiutage, via limigo de ripetoj estu almenaŭ { $expected }.
       *[other] Se vi aldonas { $cards } kartojn ĉiutage, via limigo de ripetoj estu almenaŭ { $expected }.
    }
deck-config-learning-step-above-graduating-interval = La intertempo por lernitaj kartoj estu almenaŭ tiel longa kiel via lasta lernpaŝo.
deck-config-good-above-easy = La intertempo de facila respondo estu almenaŭ tiel longa kiel la intertempo de lernitaj kartoj.
deck-config-relearning-steps-above-minimum-interval = La minimuma intertempo de misrespondo estu almenaŭ tiel longa kiel via fina relernpaŝo.
deck-config-maximum-answer-secs-above-recommended = Anki povas plani viajn ripetojn pli efike, kiam tempo por ĉiu via respondo estas mallonga.
deck-config-too-short-maximum-interval = Maksimuma intertempo malpli longa ol 6 monatoj ne estas konsilinda.
deck-config-ignore-before-info = Proksimume { $included }/{ $totalCards } kartoj estos uzataj por plejbonigi parametrojn de FSRS.

## Selecting a deck

deck-config-which-deck = Por kiu kartaro vi volas montri agordojn?

## Messages related to the FSRS scheduler

deck-config-updating-cards = Ĝisdatigado de notoj: { $current_cards_count }/{ $total_cards_count }…
deck-config-invalid-parameters = La liveritaj parametroj de FSRS estas eraraj. Lasu la kampon malplena por uzi implicitajn parametrojn.
deck-config-not-enough-history = Nesufiĉa historio de ripetoj por plenumi tiun ĉi agon.
deck-config-must-have-400-reviews =
    { $count ->
        [one] Trovis nur { $count } ripeton. Vi devas havi almenaŭ 400 ripetojn por plenumi tiun ĉi agon.
       *[other] Trovis nur { $count } ripetojn. Vi devas havi almenaŭ 400 ripetojn por plenumi tiun ĉi agon.
    }
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = Parametroj de FSRS
deck-config-compute-optimal-weights = Plejbonigi parametrojn de FSRS
deck-config-optimize-button = Plejbonigi nunan agordaron
# Indicates that a given function or label, provided via the "text" variable, operates slowly.
deck-config-slow-suffix = { $text } (malrapida)
deck-config-compute-button = Kalkuli
deck-config-ignore-before = Ignori kartojn ripetitajn antaŭ ol
deck-config-time-to-optimize = Pasis kelkan tempon – estas konsilinde uzi nun la butonon “Plejbonigi ĉiujn agordarojn”.
deck-config-evaluate-button = Taksi
deck-config-desired-retention = Dezirata memorigado
deck-config-historical-retention = Historia memorigado
deck-config-smaller-is-better = Malpli grandaj nombroj indikas pli bonan alĝustigon al via historio de ripetoj.
deck-config-steps-too-large-for-fsrs = Kiam FSRS estas aktiva, lernpaŝoj de 1 tago aŭ pli longaj estas malkonsilindaj.
deck-config-get-params = Akiri parametrojn
deck-config-complete = Farita en { $num }%.
deck-config-iterations = Iteracio: { $count }…
deck-config-reschedule-cards-on-change = Replani kartojn je ŝanĝo
deck-config-fsrs-tooltip =
    Influas la tutan kolekton
    
    La planilo FSRS («Free Spaced Repetition Scheduler») estas alternativo al la kaduka algoritmo SM-2 (SuperMemo 2). Per pli precize determini kiam vi probable forgesos karton, ĝi povas helpi al vi memori pli en la sama kvanto da tempo. Tio ĉi aplikiĝas al ĉiuj antaŭagordoj.
deck-config-desired-retention-tooltip = Implicite Anki planas montri kartojn tiel, ke vi havas 90% ŝancon por memori karton, kiam ĝi montriĝos ree. Se vi pliigos tiun ĉi valoron, Anki montros kartojn pli ofte por pliigi vian ŝancon por memori ilin. Se vi malpliigos tiun ĉi valoron, Anki montros kartojn malpli ofte kaj vi forgesos pli da ili. Estu singarda dum manipuli tiun ĉi agordon – pli altaj valoroj pliigos vian necesan penon kaj malpli altaj valoroj malfervorigos vin, kiam vi estos forgesanta multan lern-materialon.
deck-config-desired-retention-tooltip2 = La liverataj valoroj de necesa peno estas proksimumaj. Por precizigi ilin, uzu la simulilon.
deck-config-historical-retention-tooltip =
    Kiam parto da via historio de ripetoj mankas, FSRS devas plenigi la mankojn. Implicite ĝi supozos, ke dum tiuj estintaj ripetoj vi memoris 90% de lern-materialo. Se via historia memorigado estis signife pli aŭ malpli granda ol 90%, alĝustigi tiun ĉi agordon ebligos al FSRS pli bone proksimumigi la mankajn ripetojn.
    
    Via historio de ripetoj povas esti nekompleta pro du kialoj:
    1. Vi uzas la agordon “Ignori kartojn ripetitajn antaŭ ol…”.
    2. Vi forviŝis historion de ripetoj por liberigi spacon aŭ vi enportis materialon el alia period-ripeta programo.
    
    La dua kialo estas malofta, do escepte se vi uzis la agordon “Ignori…”, vi ne devas alĝustigi tiun ĉi agordon.
deck-config-weights-tooltip2 = Parametroj de FSRS influas kiel kartoj estas planataj. Anki komenciĝos kun implicitaj parametroj. Vi povas uzi la suban agordon por plejbonigi la parametrojn por ke ili plej bone kongruu kun via rapido lerni kartarojn, kiuj uzis tiun ĉi agordon.

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.


## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

