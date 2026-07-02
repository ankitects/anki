### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    brugt af { $decks ->
        [one] { $decks } kortbunke
       *[other] { $decks } kortbunker
    }
deck-config-default-name = Standard
deck-config-title = Kortbunke-indstillinger

## Daily limits section

deck-config-daily-limits = Daglige begrænsninger
deck-config-new-limit-tooltip =
    Det maksimale antal af nye kort at introducere hver dag, såfremt nye kort er tilgængelige.
    Da nyt materiale kommer at øge din korttids-gennemsyns workload, bør disse typisk 
    være mindst 10x mindre end din gennemsyns-begrænsning.
deck-config-review-limit-tooltip =
    Det maksimale antal af gennemsyns-kort at vise på en dag,
    hvis kort et tilgængelige for gennemsyn.
deck-config-limit-deck-v3 =
    Når man gennemgår en kortbunke, som har en underbunke i sig, begrænsningen på hver 
    underbunke kontrollerer det maksimale antal af kort som kan blive trukket fra den pågældende bunke.
    Begrænsningen på den valgte kortbunke kontrollerer det totale antal af viste kort.
deck-config-limit-new-bound-by-reviews =
    Repetitionsgrænsen påvirker den nye grænse. For eksempel, hvis din repetitionsgrænse er
    sat til 200 og du har 190 repetitioner der venter, vil maksimalt 10 nye kort
    blive introduceret. Hvis din repetitionsgrænse er nået vil ingen nye kort blive vist.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Forudindstillet
deck-config-deck-only = Den her kortbunke
deck-config-today-only = Kun i dag

## New Cards section

deck-config-learning-steps = Indlæringsskridt
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Forsinkelser er typisk minuter (fx. `1m`) eller dage (fx. `2d`), men timer (fx. `1h`) og sekunder (fx. `30s`) er også understøttet.
deck-config-new-insertion-order = Indsættelsesrækkefølge
deck-config-new-insertion-order-sequential = Sekventiel (ældste kort først)
deck-config-new-insertion-order-random = Tilfældig

## Lapses section

deck-config-relearning-steps = Genindlærdingstrin
deck-config-relearning-steps-tooltip =
    Nul eller flere forsinkelser, separerer af mellemrum. Som standard, når du trykker på `Igen`
    knappen på et gennemsynskort, viser kortet igen 10 minutter senere. Hvis ingen forsinkelser 
    er defineret, kommer kortet af have ændret sig interval, uden at gennemgå
    genindlæring. { -deck-config-delay-hint }

## Burying section

deck-config-bury-title = Begrav
deck-config-bury-new-siblings = Begrav nye søskene
deck-config-bury-review-siblings = Begrav gennemgangs-søskene
deck-config-bury-interday-learning-siblings = Gegrav mellemdags-søskene
deck-config-bury-new-tooltip =
    Såfremt andre `nye` kort af den samme notestype (fx. omvendte kort, nærbeslægtede cloze-deletioner)
    bliver udskudt til næste dag.
deck-config-bury-review-tooltip = Såfremt andre `gennmegangs`kort af samme notestype vil blive udskudt til næste dag.
deck-config-bury-interday-learning-tooltip =
    Såfremt andre `indlærings` kort af samme notestype med intervallet >1 dag
    vil blive udskudt til næste dag.

## Ordering section

deck-config-ordering-title = Visningsrækkefølge
deck-config-new-gather-priority = Samlingsrækkefølge af nye kort
deck-config-new-gather-priority-deck = Bunke
deck-config-new-gather-priority-position-lowest-first = Stigende position
deck-config-new-gather-priority-position-highest-first = Faldende position
deck-config-new-gather-priority-random-notes = Tilfældige noter
deck-config-new-gather-priority-random-cards = Tilfældige kort
deck-config-new-card-sort-order = Sorteringsrækkefølge af nye kort
deck-config-sort-order-card-template-then-random = Korttype, efterfølgende slumpmæssig
deck-config-sort-order-random-note-then-template = Slumpmæssig notestype, og så korttype
deck-config-sort-order-random = Tilfældig
deck-config-sort-order-template-then-gather = Korttype
deck-config-sort-order-gather = Samlet rækkefølge
deck-config-new-review-priority = Ny/gennemgangsrækkefølge
deck-config-new-review-priority-tooltip = Hvornår man skal vise nye kort i relation til gennemsynskort.
deck-config-interday-step-priority = Rækkefølge af mellemdagsindlæring/gennemgang.
deck-config-review-mix-mix-with-reviews = Blandet med gennemgangskort
deck-config-review-mix-show-after-reviews = Vis efter gennemgangkort
deck-config-review-mix-show-before-reviews = Vis før gennemgangskort
deck-config-review-sort-order = Rækkefølge af gennemgangskort
deck-config-sort-order-due-date-then-random = Forfaldsdato, efterfølgende slumpmæssig
deck-config-sort-order-due-date-then-deck = Forfaldsdato, efterfølgende kortbunke
deck-config-sort-order-deck-then-due-date = Kortbunke, efterfølgende forfaldsdato
deck-config-sort-order-ascending-intervals = Tiltagende intervaller
deck-config-sort-order-descending-intervals = Aftagende intervaller
deck-config-sort-order-ascending-ease = Tiltagende lethed
deck-config-sort-order-descending-ease = Aftagende lethed
deck-config-sort-order-relative-overdueness = Relativ overskridelse
deck-config-display-order-will-use-current-deck =
    Anki kommer vise rækkefølgen fra bunken som du 
    valgte at studere, men ingen eventuelle underbunker som den har.

## Timer section

deck-config-timer-title = Timer
deck-config-maximum-answer-secs = Maksimalt antal svars-sekunder

## Auto Advance section


## Audio section

deck-config-audio-title = Lyd
deck-config-disable-autoplay = Afspil ikke lyd automatisk
deck-config-skip-question-when-replaying = Spring spørgsmålet over, når svaret gives

## Advanced section

deck-config-advanced-title = Avanceret
deck-config-custom-scheduling = Brugertilpasset tidsplanlægning
deck-config-custom-scheduling-tooltip = Påvirker hele kollektionen. Brug på din egen risiko!

## Adding/renaming

deck-config-add-group = Tilføj forudindstilling
deck-config-name-prompt = Navn
deck-config-rename-group = Omdøb forudindstilling
deck-config-clone-group = Klon forudindstilling

## Removing

deck-config-remove-group = Fjern forudindstilling
deck-config-confirm-remove-name = Slet { $name }?

## Other Buttons

deck-config-save-button = Gem
deck-config-save-to-all-subdecks = Gem til alle underkortbunker
deck-config-revert-button-tooltip = Gendan den her indstilling til dennes oprindelige værdi.

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Anki 2.1.41+ håndtering
deck-config-description-new-handling-hint =
    Behandler inputs som markdown, og renser HTML-input. Når aktiveret, 
    kommer forklaringen også at ses på tillykke-skærmen.
    Markdown kommer at fremgå som tekst på Anki 2.1.40 og under.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    En voksenbunke har en begrænsning på { $cards ->
        [one] { $cards } kort
       *[other] { $cards } kort
    }, hvilket kommer til at overstyre den her begrænsning.
deck-config-reviews-too-low =
    Hvis du tilføjer { $cards ->
        [one] { $cards } nye kort hver dag
       *[other] { $cards } nye kort hver dag
    }, bør din gennemgangsbegrænsning at være mindst { $expected }.

## Selecting a deck

deck-config-which-deck = Hvilken bunke til du bruge?

## Messages related to the FSRS scheduler


## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

