### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    { $decks ->
        [one] koristi se u { $decks } špilu
        [few] koristi se u { $decks } špila
       *[other] koristi se u { $decks } špilova
    }
deck-config-default-name = Zadano
deck-config-title = Opcije špila

## Daily limits section

deck-config-daily-limits = Dnevni limiti
deck-config-new-limit-tooltip =
    Maksimalni broj novih kartica koje se mogu uvesti u jednom danu, ako su nove kartice dostupne.
    Budući da će novi materijal povećati vaše kratkoročno radno opterećenje ponavljanja, to bi obično trebalo biti barem 10 puta manje od vašeg limita ponavljanja.
deck-config-review-limit-tooltip = Maksimalni broj kartica ponavljanja koji će se prikazati u jednom danu, ako ima kartica koje su spremne za ponavljanje.
deck-config-limit-deck-v3 = Prilikom učenja špila koji ima podšpilove, limiti postavljeni na svakom podšpilu kontroliraju maksimalni broj karata sabranih iz tog određenog špila. Limiti odabranog špila kontroliraju ukupan broj kartica koje će biti prikazane.
deck-config-limit-new-bound-by-reviews = Limit ponavljanja utječe na limit novih kartica. Na primjer, ako je vaš limit ponavljanja postavljen na 200, a čeka vas 190 ponavljanja, bit će uvedeno maksimalno 10 novih kartica. Ako je vaše ograničenje pregleda dosegnuto, neće se prikazivati ​​nove kartice.
deck-config-limit-interday-bound-by-reviews = Limit ponavljanja također utječe na kartice koje prekoračuju u sljedeći dan. Prilikom primjene limita, kartice koje prekoračuju u sljedeći dan se sabiru prve, a onda kartice ponavljanja.
deck-config-tab-description =
    - `Predložak`: Ovaj se limit primjenjuje na sve špilove koji koriste ovaj predložak.
    - `Ovaj špil`: Limit se primjenjuje samo na ovaj špil.
    - `Samo danas`: Napravi privremenu promjenu limita samo za ovaj špil.
deck-config-new-cards-ignore-review-limit = Nove kartice ignoriraju limit ponavljanja
deck-config-new-cards-ignore-review-limit-tooltip = Prema zadanim postavkama, limit ponavljanja primjenjuje se i na nove kartice pa se stoga nove kartice neće se prikazivati ​​kada se dosegne limit ponavljanja. Ako je ova opcija omogućena, nove kartice će se prikazivati ​​bez obzira na limit ponavljanja.
deck-config-apply-all-parent-limits = Limiti počinju od vrha
deck-config-apply-all-parent-limits-tooltip =
    Prema zadanim postavkama, dnevni limiti višeg špila ne primjenjuju se ako učite iz njegovog podšpila.
    Ako je ova opcija omogućena, limiti će početi od špila najviše razine, što može biti korisno ako želite učiti pojedinačne podšpilove, a istovremeno nametnuti ukupni limit karata za stablo špila.
deck-config-affects-entire-collection = Utječe na cijelu kolekciju.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Predložak
deck-config-deck-only = Ovaj špil
deck-config-today-only = Samo danas

## New Cards section

deck-config-learning-steps = Koraci učenja
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Odgode su obično minute `1min` ili dani (npr. `2d`), no podržani su i sati (npr. `1h`) i sekunde (npr. `30s`).
deck-config-learning-steps-tooltip = Jedna ili više odgoda odvojenih zarezom. Prva odgoda bit će korištena kad pritisnete gumb `Ponovno` na novoj kartici i standardno je postavljena na 1 minutu. Gumb `Dobro` napreduje na sljedeći korak koji je standardno postavljen na 10 minuta. Nakon što se prođe kroz sve korake, kartica postaje kartica ponavljanja i pojavit će se neki drugi dan. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip = Koliko dana da se čeka prije ponovnog prikazivanja kartice nakon što se pritisne gumb `Dobro` u posljednjem koraku učenja.
deck-config-easy-interval-tooltip = Koliko dana da se čeka prije ponovnog prikazivanja kartice nakon što se klikom na gumb `Lako` odmah ukloni karta iz učenja.
deck-config-new-insertion-order = Poredak umetanja
deck-config-new-insertion-order-sequential = Sekvencijalno (prvo najstarije kartice)
deck-config-new-insertion-order-random = Nasumično
deck-config-new-insertion-order-random-with-v3 = S v3 raspoređivačem, bolje je postaviti ovu postavku na "sekvencijalno" i umjesto toga prilagoditi redoslijed sabiranja novih kartica.

## Lapses section

deck-config-relearning-steps = Koraci za ponovno učenje
deck-config-relearning-steps-tooltip = Nula ili više odgoda odvojenih zarezom. Standardno, ako se na kartici ponavljanja pritisne na gumb `Ponovno`, ona će se ponovno pojaviti za 10 minuta. Ako nije zadana nijedna odgoda, promijenit će se kartičin interval bez da uđe u ponovno učenje. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip = Broj puta koliko se `Ponovno` mora pritisnuti na kartici ponavljanja prije nego što se označi kao pijavica (pijavice su kartice koje vam oduzimaju puno vremena). Kada je kartica označena kao pijavica, dobra je ideja preformulirati je, izbrisati je ili smisliti mnemotehniku ​​koja će vam pomoći da je zapamtite.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `Samo označi`: Dodaj oznaku 'pijavica' na bilješku i prikaži skočni prozorčić.
    
    `Suspendiraj karticu`: Uz označavanje bilješke također sakrij karticu dok ne bude ručno maknuta iz suspenzije.

## Burying section

deck-config-bury-title = Zakapanje
deck-config-bury-new-siblings = Zakopaj nove srodne kartice
deck-config-bury-review-siblings = Zakopaj srodne kartice ponavljanja
deck-config-bury-interday-learning-siblings = Zakopaj srodne kartice učenja koje prekoračuju u idući dan
deck-config-bury-new-tooltip = Da li će druge `nove` kartice od iste bilješke (npr. obratne kartice, susjedne kartice na nadopunjavanje) biti odgođene na sljedeći dan.
deck-config-bury-review-tooltip = Da li će druge kartice `za ponavljanje` od iste bilješke biti odgođene na sljedeći dan.
deck-config-bury-interday-learning-tooltip = Da li će druge kartice `za učenje` od iste bilješke s intervalima većim od 1 dana biti odgođene na sljedeći dan.
deck-config-bury-priority-tooltip =
    Kad Anki sabire kartice, prvo sabire kartice koje ne prekoračuju u idući dan, zatim one koje prekoračuju, zatim kartice ponavljanja te naposljetku nove kartice. Ovo utječe na to kako funkcionira zakapanje:
    
    - Ako su sve opcije zakapanja uključene, bit će prikazana srodna kartica koja se pojavljuje prva u tom popisu. Na primjer, prikazat će se kartica ponavljanja radije nego nova kartica.
    - Srodne kartice na popisu ne mogu zakopati ranije tipove kartica. Na primjer, ako isključite zakapanje novih kartica i učite novu karticu, ona neće zakopati nijednu karticu koja prekoračuje u idući dan niti kartice ponavljanja. Može se dogoditi da vidite i srodnu karticu ponavljanja i novu srodnu karticu u istoj sesiji.

## Gather order and sort order of cards

deck-config-ordering-title = Redoslijed prikaza
deck-config-new-gather-priority = Redoslijed sabiranja novih kartica
deck-config-new-card-sort-order = Redoslijed sortiranja novih kartica
deck-config-new-card-sort-order-tooltip-2 =
    `Tip kartice, zatim redoslijed sabiranja`: prikazuje kartice poredane po broju njihovog tipa kartice.
    Kartice svakog tipa kartice prikazane su redoslijedom kojim su sabrane. 
    Ako je zakapanje srodnih kartica isključeno, ovo će osigurati da su sve kartice vrste "prednja strana→poleđina" viđene prije ijedne kartice vrste "poleđina→prednja strana".
    Ovo je korisno kako bi se imalo sve kartice iste bilješke u istoj sesiji, ali da ne budu preblizu jedna drugoj.
    
    `Redoslijed sabiranja`: Prikazuje kartice točno onako kako su sabrane. Ako je zakapanje srodnih kartica isključeno, ovo obično rezultira time da se sve kartice iste bilješke prikažu jedna za drugom.
    
    `Vrsta kartice, zatim nasumično`: Prikazuje kartice poredane po broju tipa kartice. Kartice istog broja tipa kartice prikazane su nasumičnim redoslijedom. Ovaj redoslijed je koristan ako ne želite da se srodne kartice pojave jedna preblizu drugoj, ali da i dalje želite da se kartice pojavljuju nasumičnim redoslijedom.
    
    `Nasumična bilješka, zatim tip kartice`: Nasumično odabire bilješke, zatim prikazuje sve njihove kartice po redu.
    
    `Nasumično`: Prikazuje kartice nasumičnim redoslijedom.
deck-config-new-review-priority = Redoslijed novih/ponavljanja
deck-config-new-review-priority-tooltip = Kad da se prikažu nove karticu u odnosu na kartice ponavljanja.
deck-config-interday-step-priority = Redoslijed učenja/ponavljanja između dana
deck-config-interday-step-priority-tooltip =
    Kad pokazati kartice (ponovnog) učenja koje prekoračuju u drugi dan.
    
    Limit ponavljanja uvijek se prvo primjenjuje na kartice koje prekoračuju u drugi dan, a zatim na kartice ponavljanja. Ova postavka određuje kojim će redoslijedom sabrane kartice biti prikazane, no kartice koje prekoračuju u drugi dan će uvijek biti sabrane prve.
deck-config-review-sort-order = Redoslijed sortiranja kartica ponavljanja
deck-config-review-sort-order-tooltip = Zadani redoslijed daje prednost karticama koje su najdulje čekale tako da će se, ako vam se nakupilo puno ponavljanja, one pojaviti prve. Ako imate velik zaostatak za koji je potrebno više od par dana da se raščisti, ili ako želite vidjeti kartice u redoslijedu podšpila, drugi redoslijedi sortiranja će vam možda više odgovarati.
deck-config-display-order-will-use-current-deck = Anki će koristiti redoslijed prikaza od špila koji ste odabrali učiti, a ne od njegovih podšpilova.

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = Špil
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = Špil, zatim nasumične bilješke
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = Pozicija (uzlazno)
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = Pozicija (silazno)
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = Nasumične bilješke
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = Nasumične kartice
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = Vrsta kartice, zatim nasumično
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = Nasumična bilješka, zatim tip kartice
# Sort the cards randomly.
deck-config-sort-order-random = Nasumično
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = Tip kartice, zatim redoslijed sabiranja
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = Redoslijed sabiranja
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = Pomiješano s ponavljanjima
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = Prikaži nakon ponavljanja
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = Prikaži prije ponavljanja
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = Interval (uzlazno)
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = Interval (silazno)
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = Lakoća (uzlazno)
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = Lakoća (silazno)
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = Prvo lagane kartice
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = Prvo teške kartice
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = Vjerojatnost prisjećanja (uzlazno)
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = Vjerojatnost prisjećanja (silazno)

## Timer section

deck-config-timer-title = Brojači
deck-config-maximum-answer-secs = Maksimalni broj sekundi za odgovor
deck-config-maximum-answer-secs-tooltip = Maksimalni broj sekundi koji će se zapisati za jedno ponavljanje. Ako odgovor premaši ovo vrijeme (jer ste se, primjerice, udaljili od zaslona), vrijeme će se zapisati kao ograničenje koje ste postavili.
deck-config-show-answer-timer-tooltip = Na ekranu za učenje prikaži brojač koji mjeri koliko dugo traje da naučite pojedinu karticu.
deck-config-stop-timer-on-answer = Zaustavi brojač na ekranu pri odgovoru
deck-config-stop-timer-on-answer-tooltip = Da li da se zaustavi brojač na ekranu kad se pokaže odgovor. Ovo ne utječe na statistiku.

## Auto Advance section

deck-config-seconds-to-show-question = Trajanje prikaza pitanja (sekunde)
deck-config-seconds-to-show-question-tooltip-3 = Kada je automatski napredak uključen, broj sekundi koje treba pričekati prije nego se primijeni radnja nakon pitanja. Postavite na 0 da biste onemogućili.
deck-config-seconds-to-show-answer = Trajanje prikaza odgovora (sekunde)
deck-config-seconds-to-show-answer-tooltip-2 = Kada je automatski napredak uključen, broj sekundi koje treba pričekati prije nego se primijeni radnja nakon odgovora. Postavite na 0 da biste onemogućili.
deck-config-question-action-show-answer = Prikaži odgovor
deck-config-question-action-show-reminder = Prikaži podsjetnik
deck-config-question-action = Radnja nakon pitanja
deck-config-question-action-tool-tip = Radnja koja se provede nakon što je prikazano pitanje i vrijeme je isteklo.
deck-config-answer-action = Radnja nakon odgovora
deck-config-answer-action-tooltip-2 = Radnja koja se provede nakon što je prikazan odgovor i vrijeme je isteklo.
deck-config-wait-for-audio-tooltip-2 = Čekaj da zvuk završi prije automatskog izvršavanja radnje nakon pitanja ili odgovora.

## Audio section

deck-config-audio-title = Zvuk
deck-config-disable-autoplay = Isključi automatsku reprodukciju zvuka
deck-config-disable-autoplay-tooltip = Kad je uključeno, Anki neće automatski reproducirati zvuk. Zvuk se može ručno reproducirati pritiskom na ikonu zvuka ili korištenjem radnje "Ponovno reproduciraj"
deck-config-skip-question-when-replaying = Preskoči pitanje pri ponovnoj reprodukciji odgovora
deck-config-always-include-question-audio-tooltip = Da li da se uključi zvuk kad se provede radnja "Ponovno reproduciraj" dok se strana kartice s odgovorom.

## Advanced section

deck-config-advanced-title = Napredno
deck-config-maximum-interval-tooltip = Maksimalni broj dana koji će kartica ponavljanja čekati. Kad kartica dosegne limit, `Teško`, `Dobro` i `Lako` svi će dati istu odgodu. Što ste ovo postavili kraće, to će vaše radno opterećenje biti veće.
deck-config-starting-ease-tooltip = Faktor lakoće s kojim počinju nove kartice. Standardno, gumb `Dobro` će na novo naučenoj kartici odgoditi sljedeće ponavljanje za količinu vremena koja je 2,5 puta dulja od prijašnje odgode.
deck-config-easy-bonus-tooltip = Interval na kartici ponavljanja će se pomnožiti ovim brojem kad je ocijenite s `Lako`.
deck-config-interval-modifier-tooltip = Svi intervali ponavljanja se množe ovim brojem pa se uz male promjene nad njim Anki može podesiti da bude konzervativniji ili agresivniju u svom raspoređivanju. Pogledajte priručnik prije mijenjanja ove postavke.
deck-config-hard-interval-tooltip = Broj kojim se množi interval ponavljanja kad se odgovori `Teško`.
deck-config-new-interval-tooltip = Broj kojim se množi interval ponavljanja kad se odgovori `Ponovno`.
deck-config-minimum-interval-tooltip = Minimalni interval koji je dan kartici ponavljanja kad se odgovori `Ponovno`.
deck-config-custom-scheduling-tooltip = Utječe na cijelu kolekciju. Koristite na vlastitu odgovornost!

## Easy Days section.

deck-config-easy-days-title = Lakši dani
deck-config-easy-days-monday = Pon
deck-config-easy-days-tuesday = Uto
deck-config-easy-days-wednesday = Sri
deck-config-easy-days-thursday = Čet
deck-config-easy-days-friday = Pet
deck-config-easy-days-saturday = Sub
deck-config-easy-days-sunday = Ned
deck-config-easy-days-normal = Normalno
deck-config-easy-days-reduced = Smanjeno
deck-config-easy-days-minimum = Minimalno
deck-config-easy-days-no-normal-days = Bar jedan dan treba biti postavljen na '{ deck-config-easy-days-normal }'.
deck-config-easy-days-change = Postojeća ponavljanja neće biti preraspoređena osim ako '{ deck-config-reschedule-cards-on-change }' nije uključeno u FSRS postavkama.

## Adding/renaming

deck-config-add-group = Dodaj predložak
deck-config-name-prompt = Naziv
deck-config-rename-group = Preimenuj predložak
deck-config-clone-group = Kloniraj predložak

## Removing

deck-config-remove-group = Ukloni predložak
deck-config-will-require-full-sync = Tražena promjena zahtijeva sinkronizaciju u jednom smjeru. Ako ste napravili promjene na drugom uređaju koje još niste sinkronizirali na ovaj uređaj, učinite to prije nego što nastavite.
deck-config-confirm-remove-name = Ukloniti { $name }?

## Other Buttons

deck-config-save-button = Spremi
deck-config-save-to-all-subdecks = Spremi u sve podšpilove
deck-config-save-and-optimize = Optimiziraj sve predloške
deck-config-revert-button-tooltip = Vrati ovu postavku na zadanu vrijednost?

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Postupanje kao u Anki 2.1.41+
deck-config-description-new-handling-hint = Tretira unos kao markdown i čisti HTML unos. Kad je uključeno, opis će se također prikazivati na ekranu s čestitkama. Markdown se prikazuje kao tekst u Anki verziji 2.1.40 i niže.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    { $cards ->
        [one] Nadređeni špil ima limit od { $cards } kartice, što će nadjačati ovaj limit.
        [few] Nadređeni špil ima limit od { $cards } kartice, što će nadjačati ovaj limit.
       *[other] Nadređeni špil ima limit od { $cards } kartica, što će nadjačati ovaj limit.
    }
deck-config-reviews-too-low =
    { $cards ->
        [one] Ako dodajete { $cards } novu karticu svaki dan, vaš limit ponavljanja bi trebao biti barem { $expected }.
        [few] Ako dodajete { $cards } nove kartice svaki dan, vaš limit ponavljanja bi trebao biti barem { $expected }.
       *[other] Ako dodajete { $cards } novih kartica svaki dan, vaš limit ponavljanja bi trebao biti barem { $expected }.
    }
deck-config-learning-step-above-graduating-interval = Interval apsolviranja treba biti jednako dug ili dulji od vašeg zadnjeg koraka učenja.
deck-config-good-above-easy = Interval za lagane treba biti jednako dug ili dulji od intervala apsolviranja.
deck-config-maximum-answer-secs-above-recommended = Anki može učinkovitije rasporediti vaša ponavljanja ako svako pitanje zadržite kratkim.
deck-config-too-short-maximum-interval = Ne preporučuje se maksimalni interval kraći od 6 mjeseci.
deck-config-ignore-before-info = (Otprilike) { $included }/{ $totalCards } kartica će se koristiti za optimizaciju FSRS parametara.

## Selecting a deck

deck-config-which-deck = Za koji špil želite prikazati postavke?

## Messages related to the FSRS scheduler

deck-config-updating-cards = Ažuriranje kartica: { $current_cards_count }/{ $total_cards_count }...
deck-config-invalid-parameters = Navedeni FSRS parametri nisu valjani. Ostavite ih praznima da biste koristili zadane parametre.
deck-config-not-enough-history = Nema dovoljno povijesti ponavljanja da bi se izvela ova operacija
deck-config-must-have-400-reviews =
    { $count ->
        [one] Samo { $count } ponavljanje je pronađeno. Morate imati bar 400 ponavljanja za ovu operaciju.
        [few] Samo { $count } ponavljanja su pronađena. Morate imati bar 400 ponavljanja za ovu operaciju.
       *[other] Samo { $count } ponavljanja je pronađeno. Morate imati bar 400 ponavljanja za ovu operaciju.
    }
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = FSRS parametri
deck-config-compute-optimal-weights = Optimiziraj FSRS parametre
deck-config-optimize-button = Optimiziraj trenutni predložak
# Indicates that a given function or label, provided via the "text" variable, operates slowly.
deck-config-slow-suffix = { $text } (sporo)
deck-config-compute-button = Izračunaj
deck-config-ignore-before = Ignoriraj kartice ponovljene prije
deck-config-time-to-optimize = Prošlo je dosta vremena - preporuča se korištenje gumba ''{ deck-config-save-and-optimize }".
deck-config-evaluate-button = Evaluiraj
deck-config-desired-retention = Željena retencija
deck-config-historical-retention = Povijesna retencija
deck-config-smaller-is-better = Manji brojevi ukazuju na bolje poklapanje s vašom povijesti ponavljanja.
deck-config-steps-too-large-for-fsrs = Kad je FSRS uključen, ne preporučuju se koraci od jednog dana ili dulje.
deck-config-get-params = Dobij parametre
deck-config-complete = { $num }% dovršeno.
deck-config-iterations = Iteracija: { $count }...
deck-config-reschedule-cards-on-change = Prerasporedi kartice pri promjeni
deck-config-fsrs-tooltip =
    Utječe na cijelu kolekciju.
    
    FSRS (Free Spaced Repetition Scheduler) je alternativa Ankijevom starom SuperMemo 2 (SM-2) algoritmu.
    Preciznijim određivanjem toga kad ćete zaboraviti karticu, može vam pomoći zapamtiti više materijala u istom vremenu. Svi predlošci dijele ovu postavku.
deck-config-desired-retention-tooltip = Anki po zadanome raspoređuje kartice tako da imate 90% vjerojatnost da ih se sjetite kad se ponovno pojave za ponavljanje. Ako povećate ovu vrijednost, Anki će učestalije prikazivati kartice kako bi povećao vjerojatnost da ih se sjetite. Ako smanjite vrijednost, Anki će rjeđe prikazivati kartice pa ćete ih više zaboraviti. Budite konzervativni kad mijenjate ovu vrijednost - veće vrijednosti će mnogo povećati vaše radno opterećenje, a manje vrijednosti mogu biti obeshrabrujuće kad zaboravite mnogo štiva.
deck-config-desired-retention-tooltip2 = Vrijednosti radnog opterećenja koje pruža informacijski okvir gruba su aproksimacija. Za veću razinu točnosti koristite simulator.
deck-config-historical-retention-tooltip =
    Kad dio vaše povijesti ponavljanja nedostaje, FSRS mora popuniti praznine. Po zadanim postavkama, pretpostavit će da ste se sjetili 90% štiva kad ste obavljali ta stara ponavljanja. Ako je vaša stara retencija bila znatno viša ili niža od 90%, podešavanje ove postavke omogućit će da FSRS bolje aproksimira nedostajuća ponavljanja.
    
    Vaša povijest ponavljanja može biti nepotpuna iz dva razloga:
    1. Jer ste koristili opciju 'ignoriraj kartice ponovljene prije'.
    2. Jer ste prije izbrisali zapisnike ponavljanja kako biste oslobodili mjesta ili jer ste uvezli štivo iz drugog SRS programa.
    
    Drugi razlog je prilično rijedak pa ako niste koristili prethodno spomenutu opciju, vjerojatno ne morate podešavati ovu postavku.
deck-config-compute-optimal-weights-tooltip2 =
    Kad pritisnete gumb Optimiziraj, FSRS će analizirati vašu povijest ponavljanja i generirati parametre koji su optimalni za vaše sjećanje i za sadržaj koji učite. Ako vaši špilovi imaju vrlo različite subjektivne težine, preporučuje se dodijeliti im različite predloške jer će parametri za lake i teške špilove biti drugačiji.
    Nije potrebno često optimizirati parametre - jednom svakih par mjeseci je dovoljno.
    
    Po zadanim postavkama, parametri će biti izračunati iz povijesti ponavljanja svih špilova koji koriste trenutni predložak. Ako želite, možete podesiti pretraživanje prije izračuna parametara ako želite promijeniti koje će kartice biti korištene za optimizaciju parametara.
deck-config-please-save-your-changes-first = Prvo spremite svoje promjene.
deck-config-workload-factor-change =
    Približno radno opterećenje: { $factor }x
    (u usporedbi s { $previousDR }% željenom retencijom)
deck-config-workload-factor-unchanged = Što je ova vrijednost viša, to će se češće prikazivati kartice.
deck-config-desired-retention-too-low = Vaša željena retencija je vrlo niska, što može dovesti do vrlo dugih intervala.
deck-config-desired-retention-too-high = Vaša željena retencija je vrlo visoka, što može dovesti do vrlo kratkih intervala.
deck-config-percent-of-reviews = { $pct }% od { $reviews } ponavljanja
deck-config-percent-input = { $pct }%
# This message appears during FSRS parameter optimization.
deck-config-checking-for-improvement = Provjera mogućih poboljšanja...
deck-config-optimizing-preset = Optimizacija predloška { $current_count }/{ $total_count }...
deck-config-fsrs-must-be-enabled = Prvo mora biti uključen FSRS.
deck-config-fsrs-params-optimal = Trenutno se čini da su FSRS parametri optimalni.
deck-config-fsrs-params-no-reviews = Nijedno ponavljanje nije pronađeno. Osigurajte da je ovaj predložak dodijeljen svim špilovima (uključujući podšpilove) koje želite optimizirati, a zatim pokušajte ponovno.
deck-config-wait-for-audio = Čekaj zvuk
deck-config-show-reminder = Prikaži podsjetnik
deck-config-answer-again = Odgovori "Ponovno"
deck-config-answer-hard = Odgovori "Teško"
deck-config-answer-good = Odgovori "Dobro"
deck-config-days-to-simulate = Broj dana za simulirati
deck-config-desired-retention-below-optimal = Vaša željena retencija je ispod optimalne razine. Preporučuje se povisiti je.
# Description of the y axis in the FSRS simulation
# diagram (Deck options -> FSRS) showing the total number of
# cards that can be recalled or retrieved on a specific date.
deck-config-fsrs-simulator-experimental = FSRS simulator (eksperimentalno)
deck-config-fsrs-simulate-desired-retention-experimental = FSRS simulator željene retencije (eksperimentalno)
deck-config-fsrs-simulate-save-preset = Nakon optimizacije, spremite predložak špila prije pokretanja simulatora.Nakon optimizacije, spremite predložak špila prije pokretanja simulatora.
deck-config-fsrs-desired-retention-help-me-decide-experimental = Pomozi mi odlučiti (eksperimentalno)
deck-config-additional-new-cards-to-simulate = Dodatne nove kartice za simulaciju
deck-config-simulate = Simuliraj
deck-config-clear-last-simulate = Očisti zadnju simulaciju
deck-config-fsrs-simulator-radio-count = Ponavljanja
deck-config-advanced-settings = Napredne postavke
deck-config-smooth-graph = Glatki graf
deck-config-suspend-leeches = Suspendiraj pijavice
deck-config-save-options-to-preset = Spremi promjene u predložak
# $time here is pre-formatted e.g. "10 Seconds" 
deck-config-fsrs-simulator-ratio-tooltip = { $time } po zapamćenoj kartici

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.

# Checkbox
deck-config-health-check = Provjeri zdravlje pri optimizaciji
# Message box showing the result of the health check
deck-config-fsrs-bad-fit-warning =
    Provjera zdravlja:
    FSRS teško predviđa vaše pamćenje. Preporuke:
    
    - Suspendirajte ili preformulirajte kartice koje stalno zaboravljate.
    - Koristite gumbe za odgovor na konzistentan način. Imajte na umu da je "Teško" prolazna ocjena, a ne pad.
    - Razumijte prije nego pokušate zapamtiti.
    
    Ako slijedite ove prijedloge, rezultati obično porastu kroz sljedećih par mjeseci.

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-unable-to-determine-desired-retention = placeholder
deck-config-predicted-minimum-recommended-retention = placeholder
deck-config-compute-minimum-recommended-retention = placeholder
deck-config-compute-optimal-retention-tooltip4 = placeholder
deck-config-plotted-on-x-axis = (Prikazano na X-osi)
deck-config-a-100-day-interval = placeholder
deck-config-fsrs-simulator-y-axis-title-time = placeholder
deck-config-fsrs-simulator-y-axis-title-count = placeholder
deck-config-fsrs-simulator-y-axis-title-memorized = placeholder
deck-config-bury-siblings = placeholder
deck-config-do-not-bury = placeholder
deck-config-bury-if-new = placeholder
deck-config-bury-if-new-or-review = placeholder
deck-config-bury-if-new-review-or-interday = placeholder
deck-config-bury-tooltip = placeholder
deck-config-seconds-to-show-question-tooltip = placeholder
deck-config-answer-action-tooltip = placeholder
deck-config-wait-for-audio-tooltip = placeholder
deck-config-ignore-before-tooltip = placeholder
deck-config-compute-optimal-retention-tooltip = placeholder
deck-config-health-check-tooltip1 = placeholder
deck-config-health-check-tooltip2 = placeholder
deck-config-compute-optimal-retention = placeholder
deck-config-predicted-optimal-retention = placeholder
deck-config-weights-tooltip = placeholder
deck-config-compute-optimal-weights-tooltip = placeholder
deck-config-compute-optimal-retention-tooltip2 = placeholder
deck-config-compute-optimal-retention-tooltip3 = placeholder
deck-config-seconds-to-show-question-tooltip-2 = placeholder
deck-config-invalid-weights = placeholder
deck-config-fsrs-on-all-clients = placeholder
deck-config-optimize-all-tip = placeholder
