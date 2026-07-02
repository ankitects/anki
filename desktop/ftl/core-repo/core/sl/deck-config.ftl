### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    Uporabljeno v { $decks ->
        [one] { $decks } zbirki
        [two] { $decks } zbirkah
        [few] { $decks } zbirkah
       *[other] { $decks } zbirkah
    }
deck-config-default-name = Privzeto
deck-config-title = Možnosti zbirke

## Daily limits section

deck-config-daily-limits = Dnevna omejitev
deck-config-new-limit-tooltip =
    Najvišje število novih kartic na en dan, če so nove kartice na voljo.
    Ker novo učno gradivo poveča obremenitev kratkotrajnega spomina, bi to število
    običajno moralo biti vsaj 10-krat manjše od vašega števila ponovitev.
deck-config-review-limit-tooltip =
    Najvišje število kartic za ponovitev, ki jih prikažemo v enem dnevu,
    če so kartice pripravljene za ponavljanje.
deck-config-limit-deck-v3 =
    Pri učenju zbirke, ki ima podrejene zbirke, nastavitve najvišjega števila na 
    vsaki podrejeni zbirki določa najvišje število kartic, vzetih iz te zbirke.
    Omejitve izbrane zbirke določajo skupno število kartic, ki bodo prikazane.
deck-config-limit-new-bound-by-reviews =
    Omejitev ponavljanj vpliva na novo omejitev. Če je, na primer, vaša omejitev ponavljanja
    nastavljena na 200 in imate 190 čakajočih ponovitev, bo prikazanih največ 10 novih kartic.
    Če pa je bila omejitev ponavljanj že dosežena, nove kartice ne bodo prikazane.
deck-config-limit-interday-bound-by-reviews =
    Omejitev ponavljanj vpliva tudi na učenje kartic vnaprej. Pri uveljavitvi omejitve
    so najprej vzete učne kartice z vnaprejšnjim stanjem, nato ponovitve, šele na koncu nove kartice.
deck-config-tab-description =
    - 'Prednastavitev': Omejitev je skupna vsem zbirkam s to prednastavitvijo.
    - 'Ta zbirka': Omejitev velja le za to zbirko.
    - 'Samo danes': Spremembe omejitve za to zbirko uveljavi le začasno.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Prednastavitev
deck-config-deck-only = Ta zbirka
deck-config-today-only = Samo danes

## New Cards section

deck-config-learning-steps = Učni koraki
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Zakasnitve so običajno minute (npr. '1m') ali dnevi (npr. '2d'), podprte pa so tudi ure (npr. '1h') in sekunde (npr. '30s').
deck-config-learning-steps-tooltip =
    En ali več zamikov, ločenih s presledki. Prvi zamik bo uporabljen ob pritisku
    gumba 'Ponovno' na novi kartici in je privzeto 1 minuta. Gumb 'Dobro'
    premakne kartico na naslednji korak, ki je privzeto 10 minut.
    Ko gre kartica skozi vse korake, postane kartica za ponovitev in se pojavi
    drug dan. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip =
    Število dni, po katerih se kartica ponovno prikaže, zatem ko
    je na končnem koraku učenja pritisnjen gumb 'Dobro'.
deck-config-easy-interval-tooltip =
    Število dni, po katerih se kartica ponovno prikaže, zatem ko
    je pritisnjen gumb 'Dobro', s katerim se kartica takoj izloči iz postopka učenja.
deck-config-new-insertion-order = Vrstni red vstavitve
deck-config-new-insertion-order-tooltip =
    Nastavi pozicijo (rok #) novih kartic, ko jih dodajate.
    Kartice z bližjim rokom bodo pri učenju prikazane najprej.
    Sprememba te nastavitve bo samodejno posodobila obstoječo pozicijo novih kartic.
deck-config-new-insertion-order-sequential = Sekvencijsko (najstarejše kartice naprej)
deck-config-new-insertion-order-random = Naključno
deck-config-new-insertion-order-random-with-v3 =
    Z razporejevalnikom V3 je to nastavitev bolje pustiti na sekvencijsko,
    namesto tega pa nastaviti vrstni red zbiranja novih kartic.

## Lapses section

deck-config-relearning-steps = Koraki ponovnega učenja
deck-config-relearning-steps-tooltip =
    Nič ali več zamikov, ločenih s presledki. Privzeto bo pritisk na gumb 'Ponovno'
    na ponovitveni kartici kartico prikazal čez 10 minut. Če ni določenih nobenih 
    zamikov, bo kartici spremenjen interval, brez stanja ponovnega učenja. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip =
    Število potrebnih pritiskov na gumb 'Ponovno', preden je ponovitvena kartica
    označena kot pijavka. Pijavke so kartice, ki vam jemljejo veliko časa pri ponavljanju.
    Ko je kartica označena kot pijavka, bi jo bilo smiselno ponovno sestaviti, brisati
    ali pa se domisliti mnemonične tehnike, ki vam bo pomagala pri zapomnitvi podatka.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    'Samo označene': Dodaj oznako "pijavka" zapisku in prikaži pojavno okno.
    
    'Suspendiraj kartico': Poleg dodajanja oznake skrij kartico dokler ni ročno
    odstranjena iz suspendiranih.

## Burying section

deck-config-bury-title = Zakopavanje
deck-config-bury-new-siblings = Zakoplji nove sorodne kartice
deck-config-bury-review-siblings = Zakoplji ponovitvene sorodne kartice
deck-config-bury-interday-learning-siblings = Zakoplji sorodne kartice za učenje vnaprej
deck-config-bury-new-tooltip =
    Določi, ali bodo 'nove' kartice istega zapiska (npr. obratne kartice, kartice z zaporo besedila)
    prestavljene do naslednjega dne.
deck-config-bury-review-tooltip = Določa ali bodo kartice 'za ponovitev' istega zapiska prestavljene za en dan.
deck-config-bury-interday-learning-tooltip =
    Določa, ali bodo ostale kartice 'za učenje' istega zapiska z intarvali > 1 dan
    prestavljene do naslednjega dne.

## Ordering section

deck-config-ordering-title = Vrstni red prikaza
deck-config-new-gather-priority = Vrstni red zbiranja novih kartic
deck-config-new-gather-priority-tooltip-2 =
    'Zbirka': združuje kartice iz vsake zbirke v vrstnem redu, ki se začne pri vrhu. Kartice iz vsake zbirke so
    zbrane v naraščajočem vrstnem redu (poziciji). Če je dosežen dnevni limit izbrane zbirke, se združevanje lahko
    zaključi, še preden so bile pregledane vse zbirke. Tak vrstni red je najhitrejši v velikih kolekcijah in vam 
    omogoča prioritiziranje podrejenih zbirk, ki so bolj pri vrhu.
    
    'Naraščajoča pozicija': združuje kartice po naraščajoči poziciji (# poteka), kar v praksi pomeni
    najprej najstarejše dodane.
    
    'Padajoča pozicija': združuje kartice po padajoči poziciji (# poteka), kar v praksi pomeni
    najprej zadnje dodane.
    
    'Naključni zapiski': združi kartice iz naklučno izbranih zapiskov. Kadar je zakopavanje sorodnih
    izklopljeno, vam to omogoča prikaz vseh kartic enega zapiska znotraj enega pregleda (npr. obe,
    spredaj->zadaj in zadaj->spredaj kartici).
    
    'Naključne kartice': združi popolnoma naključne kartice.
deck-config-new-gather-priority-deck = Zbirka
deck-config-new-gather-priority-position-lowest-first = Naraščajoče
deck-config-new-gather-priority-position-highest-first = Pojemajoče
deck-config-new-gather-priority-random-notes = Naključni zapiski
deck-config-new-gather-priority-random-cards = Naključne kartice
deck-config-new-card-sort-order = Vrstni red novih kartic
deck-config-new-card-sort-order-tooltip-2 =
    'Tip kartice': Prikaže kartice po vrstnem redu tipa kartice. Če je zakopavanje sorodnih kartic
    izklopljeno, bo to zagotovilo, da bodo vse spredaj-zadaj kartice vidne pred zadaj-spredaj 
    karticami. To je uporabno, kadar želite vse kartice enega zapiska videti v istem pregledu,
    toda ne preveč blizu ene drugi.
    
    'Uredi kot zbrano': Pokaže kartice kot so bile zbrane. Če je zakopavanje sorodnih kartic
    izklopljeno, bo to običajno povzročilo, da bodo vse kartice istega zapiska prikazane ena
    za drugo.
    
    'Po tipu kartice, nato naključno': Podobno kot 'Tip kartice', ampak pomeša vse kartice
    posameznega tipa kartice. Če uporabite 'Naraščajoča pozicija' za združevanje najstarejših
    kartic, lahko to nastavitev uporabite da te kartice prikažete v naključnem vrstnem redu, toda
    še vedno zagotovite, da se kartice istega zapiska ne pojavijo preveč skupaj.
    
    'Naključni zapisek, nato po tipu kartice': Izbere naključne zapiske, nato pa pokaže vse sorodne
    v vrstnem redu tipa kartice.
    
    'Naključno': vse zbrane kartice pomeša naključno.
deck-config-sort-order-card-template-then-random = Najprej tip kartice, nato naključno
deck-config-sort-order-random-note-then-template = Naključen zapisek, nato tip kartice
deck-config-sort-order-random = Naključno
deck-config-sort-order-template-then-gather = Tip kartice
deck-config-sort-order-gather = Uredi zbrano
deck-config-new-review-priority = Vrstni red nove/ponovitve
deck-config-new-review-priority-tooltip = Kdaj naj se prikažejo nove kartice glede na ponovitvene kartice.
deck-config-interday-step-priority = Vrstni red učenja/ponavljanja v istem dnevu
deck-config-interday-step-priority-tooltip =
    Čas, ko prikažemo kartice za (ponovno) učenje, ki presegajo dnevno mejo.
    
    Omejitev za preglede vedno najprej velja za kartice, ki so na vrsti za učenje določen dan,
    nato sledijo ponavljalne kartice. Ta možnost določa vrstni red prikaza zbranih kartic,
    toda kartice za določen dan bodo vedno zbrane najprej.
deck-config-review-mix-mix-with-reviews = Pomešaj s ponovitvenimi
deck-config-review-mix-show-after-reviews = Pokaži po ponovitvenih
deck-config-review-mix-show-before-reviews = Pokaži pred ponovitvenimi
deck-config-review-sort-order = Vrstni red ponovitvenih
deck-config-review-sort-order-tooltip =
    Privzet vrstni red da prednost kartica, ki čakajo najdlje; če imate vklopljen dnevnik
    pregledov, bodo najdlje čakajoče vidne najprej. Če imate zelo velik dnevnik, za katerega
    bi potrebovali več dni, da ga pregledate, ali pa želite videti kartice v vrstnem redu
    podrejenih zbirk, vam bo morda v pomoč druga možnost razporejanja.
deck-config-sort-order-due-date-then-random = Po roku zapadlosti, nato naključno
deck-config-sort-order-due-date-then-deck = Po roku zapadlosti, nato po zbirki
deck-config-sort-order-deck-then-due-date = Po zbirki, nato po roku zapadlosti
deck-config-sort-order-ascending-intervals = Naraščajoči intervali
deck-config-sort-order-descending-intervals = Padajoči intervali
deck-config-sort-order-ascending-ease = Naraščajoča težavnost
deck-config-sort-order-descending-ease = Padajoča težavnost
deck-config-sort-order-relative-overdueness = Relativna zapadlost roka
deck-config-display-order-will-use-current-deck =
    Anki bo uporabil vrstni red prikaza iz zbirke, ki 
    jo izberete za učenje in ne po morebitnih podrejenih zbirkah.

## Timer section

deck-config-timer-title = Časovnik
deck-config-maximum-answer-secs = Omejitev odgovora v sekundah
deck-config-maximum-answer-secs-tooltip =
    Največje število sekund za posamezen pregled kartice. Če odgovor presega
    to število (ker npr. niste bili pred ekranom v tem času), bo potreben čas
    nastavljen kot omejitev.
deck-config-show-answer-timer-tooltip = V oknu za pregled prikaži časovnik, ki šteje, koliko sekund pregledujete vsako kartico.

## Audio section

deck-config-audio-title = Zvok
deck-config-disable-autoplay = Ne predvajaj zvoka samodejno
deck-config-disable-autoplay-tooltip =
    Ko je omogočeno, Anki ne bo predvajal samodejno zvoka.
    Zvok lahko predvajate ročno s klikom/dotikom na ikono zvoka ali z uporabo akcije predvajanja zvoka.
deck-config-skip-question-when-replaying = Pri ponovnem prikazu odgovora preskoči vprašanje
deck-config-always-include-question-audio-tooltip =
    Ali naj se zvok vprašanja tudi vključi pri uporabi funkcije Predvajaj
    pri ogledu strani z odgovorom na kartici.

## Advanced section

deck-config-advanced-title = Napredno
deck-config-maximum-interval-tooltip =
    Največje število dni, ko bo čakala kartica za ponavljanje. Ko ponavljanja
    dosežejo mejo, odgovori 'Težko', 'Dobro' in 'Enostavno' pomenijo enak zamik.
    Krajša kot je ta nastavitev, večji bo napor pri učenju.
deck-config-starting-ease-tooltip =
    Množilnik težavnosti, s katerim začnejo nove kartice. Privzeto gumb 'Dobro'
    na novo naučeni kartici zamakne ponovitev za 2,5-kratnik prejšnjega zamika.
deck-config-easy-bonus-tooltip =
    Dodatni množilnik, ki je dodan k ponovljeni kartici, ko jo ocenite
    z gumbom 'Enostavno'.
deck-config-interval-modifier-tooltip =
    Ta množilnik je dodan vsem ponovitvenim karticam, manjše prilagoditve pa
    lahko uporabite, da naredite Anki bolj konzervativen ali agresiven pri razporejanju.
    Pred spremembo te možnosti prosimo preberite navodila za uporabo programa.
deck-config-hard-interval-tooltip = Ta množilnik se doda intervalu za ponovitev, kadar označite odgovor 'Težko'.
deck-config-new-interval-tooltip = Ta množilnik se doda intervalu za ponovitev, kadar označite odgovor 'Ponovno'.
deck-config-minimum-interval-tooltip = Najmanjši interval, ki se doda ponovitveni kartici pri odgovoru 'Ponovno'.
deck-config-custom-scheduling = Razporejanje po meri
deck-config-custom-scheduling-tooltip = Vpliva na celotno kolekcijo. Uporabite na svojo odgovornost!

## Adding/renaming

deck-config-add-group = Dodaj prednastavitev
deck-config-name-prompt = Ime
deck-config-rename-group = Preimenuj pednastavitev
deck-config-clone-group = Kloniraj prednastavitev

## Removing

deck-config-remove-group = Odstrani prednastavitev
deck-config-will-require-full-sync =
    Zahtevana sprememba mora biti izvedena z enosmerno sinhronizacijo. Če ste
    spremembe naredili na drugi napravi, in jih s to napravo še niste sinhronizirali,
    vas prosimo, da to storite, preden nadaljujete.
deck-config-confirm-remove-name = Odstranim { $name }?

## Other Buttons

deck-config-save-button = Shrani
deck-config-save-to-all-subdecks = Shrani v vse podrejene zbirke
deck-config-revert-button-tooltip = To nastavitev povrni na privzeto vrednost.

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Obdelava Anki 2.1.41+
deck-config-description-new-handling-hint =
    Vnos obravnava kot 'markdown' in očisti vnos HTML značk. Kadar je ta možnost
    omogočena, bo opis viden na zaslonu za čestitke.
    Markdown je kot besedilo vidno v Ankiju 2.1.40 in nižje.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    Nadrejena zbirka ima omejitev { $cards ->
        [one] { $cards } kartice
        [two] { $cards } kartic
        [few] { $cards } kartic
       *[other] { $cards } kartic
    }, kar bo prepisalo to omejitev.
deck-config-reviews-too-low =
    Pri dodajanju { $cards ->
        [one] { $cards } nove kartice vsak dan
        [two] { $cards } novih kartic vsak dan
        [few] { $cards } novih kartic vsak dan
       *[other] { $cards } novih kartic vsak dan
    } bi vaša omejitev ponovitev morala biti vsaj { $expected }.
deck-config-learning-step-above-graduating-interval = Interval za dozoritev kartice bi moral biti vsaj tako dolg kot vaš zadnji učni korak.
deck-config-good-above-easy = Interval za 'lahko' bi moral biti vsaj tako dolg kot interval za dozoritev.
deck-config-relearning-steps-above-minimum-interval = Najmanjši interval za preskok bi moral biti vsaj tako dolg kot končni korak za ponovno učenje.
deck-config-maximum-answer-secs-above-recommended = Anki vaše ponovitve lahko bolj učinkovito razporeja, kadar so vprašanja kratka.

## Selecting a deck

deck-config-which-deck = Katero zbirko želite?

## NO NEED TO TRANSLATE. These strings have been replaced with new versions, and will be removed in the future.

