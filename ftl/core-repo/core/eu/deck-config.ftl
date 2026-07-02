### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    { $decks ->
        [one] sorta batek erabilia
       *[other] { $decks } sortak erabilia
    }
deck-config-default-name = Lehenetsia
deck-config-title = Sortaren aukerak

## Daily limits section

deck-config-daily-limits = Eguneko mugak
deck-config-new-limit-tooltip =
    Egunean gehienez aurkeztuko diren txartel berrien kopurua, txartel berriak eskuragarri badaude.
    Material berriak epe motzeko berrikuspenen lan-karga handituko duenez, kopuru honek
    berrikuspen-muga baino 10 aldiz txikiagoa izan beharko luke, gehienez.
deck-config-review-limit-tooltip =
    Egunean gehienez erakutsiko diren berrikusteko txartelen kopurua,
    txartelak berrikusteko prest badaude.
deck-config-limit-deck-v3 =
    Azpisortak dituen sorta bat ikastean, azpisorta bakoitzean ezarritako mugek
    sorta jakin horretatik hartutako gehieneko txartel-kopurua kontrolatzen dute.
    Hautatutako sortaren mugek guztira erakutsiko diren txartelen kopurua kontrolatzen dute.
deck-config-limit-new-bound-by-reviews =
    Berrikuspen-mugak berrien mugari eragiten dio. Adibidez, 200eko berrikuspen-muga
    baduzu ezarrita, eta 190 berrikuspen badituzu zain, gehienez 10 txartel berri aurkeztuko dira.
    Berrikuspen-mugara iristean, ez da txartel berririk erakutsiko.
deck-config-limit-interday-bound-by-reviews =
    Lehendik "Ikasten" egoeran zeuden txartelei ere eragiten die berrikuspen-mugak. Muga aplikatzean,
    txartel horiek eskuratzen dira lehenik, berrikustekoak ondoren, eta berriak azkenik.
deck-config-tab-description =
    - `Aurrezarpena`: Aurrezarpen hau erabiltzen duten sorta guztietarako izango da muga.
    - `Sorta hau`: Sorta honetarako bakarrik izango da muga.
    - `Gaur bakarrik`: Aldatu sorta honen muga aldi baterako.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Aurrezarpena
deck-config-deck-only = Sorta hau
deck-config-today-only = Gaur bakarrik

## New Cards section

deck-config-learning-steps = Ikasketa-urratsak
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Tarteak minutuak (adib. `1m`) edo egunak (`2d`) izan ohi dira, baina orduak (`1h`) eta segundoak (`30s`) ere erabil daitezke.
deck-config-learning-steps-tooltip =
    Tarte bat edo gehiago, zuriunez banatuta. Lehen tartea txartel berri batean `Berriro` sakatzean
    erabiliko da, eta minutu batekoa da lehenespenez.
    `Ondo` botoiak hurrengo urratsera eramaten du, 10 minutukoa lehenespenez.
    Behin urrats guztiak gaindituta, berrikusteko txartel bihurtuko da txartela, eta
    beste egun batean agertuko da. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip =
    Txartel bat berriro erakutsi arte itxaron beharreko egun kopurua, azken ikasketa-pausoan
    `Ondo` botoia sakatu eta gero.
deck-config-easy-interval-tooltip =
    Txartel bat berriro erakutsi arte itxaron beharreko egun kopurua, `Erraza` botoia
    sakatu eta gero txartelaren ikasketa berehala gainditzeko.
deck-config-new-insertion-order = Txertatze-ordena
deck-config-new-insertion-order-tooltip =
    Txartel berriak eranstean, horiei esleitzen zaien posizioa (lehentasun-zenbakia) kontrolatzen du.
    Lehentasun-zenbaki txikiagoa duten txartelak lehenago erakutsiko dira ikastean. Aukera hau
    aldatzeak automatikoki eguneratuko du lehendik dauden txartel berrien posizioa.
deck-config-new-insertion-order-sequential = Sekuentziala (txartel zaharrenak lehenik)
deck-config-new-insertion-order-random = Ausaz
deck-config-new-insertion-order-random-with-v3 =
    V3 antolatzailearekin hobe da "Sekuentziala" aukera uztea, eta
    karta berriak biltzeko ordena doitzea.

## Lapses section

deck-config-relearning-steps = Berrikasketa-urratsak
deck-config-relearning-steps-tooltip =
    Zero tarte edo gehiago, zuriunez banatuta. Lehenespenez, `Berriro`botoia sakatu
    eta 10 minutu geroago erakutsiko da berriro txartela. Ez baduzu tarterik zehazten,
    txartelaren tartea aldatuko da, baina berrikasi beharrik gabe. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip =
    Txartel batean `Berriro` zenbat aldiz sakatu behar den, neketsu gisa markatu dadin.
    Txartel neketsuek denbora eskatzen dizute; txartel batek marka hori jasotzen badu,
    hobe duzu berridatzi, ezabatu edo gogoratzen lagunduko dizun mnemotekniko bat asmatu.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `Etiketatu bakarrik`: Gehitu "neketsua" etiketa oharrari, eta erakutsi pop-up bat.
    
    `Eten txartela`: Oharra etiketatzeaz gainera, ezkutatu oharra eskuz berrekin arte.

## Burying section

deck-config-bury-title = Lurperatzea
deck-config-bury-new-siblings = Lurperatu haurride berriak
deck-config-bury-review-siblings = Lurperatu berrikusteko haurrideak
deck-config-bury-tooltip =
    Ea ohar bereko beste txartelak (adib. alderantzizko txartela, beste hutsuneak)
    hurrengo egunera arte atzeratuko diren.

## Ordering section

deck-config-ordering-title = Erakuste-ordena
deck-config-new-gather-priority = Txartel berriak biltzeko ordena
deck-config-new-gather-priority-tooltip-2 =
    `Sorta`: sortaz sorta biltzen ditu txartelak, goikotik hasita. Sorta bakoitzeko txartelak
    goranzko ordenan jasotzen dira. Hautatutako sortaren eguneko mugara iritsiz gero, bilketa
    gelditu egingo da sorta guztiak arakatu baino lehen. Ordena hau da azkarrena bilduma handietan,
    eta gorago dauden sortei lehentasuna emateko aukera ematen du.
    
    `Posizioa gorantz`: goranzko lehentasun-zenbakiaren arabera biltzen ditu txartelak. Beraz, normalean, erantsitako txartel zaharrenak aurretik bilduko dira.
    
    `Posizioa beherantz`: beheranzko lehentasun-zenbakiaren arabera biltzen ditu txartelak. Beraz, normalean, erantsitako txartel berrienak aurretik bilduko dira.
    
    `Ausazko oharrak`: ausaz aukeratutako oharren txartelak biltzen ditu. Haurrideen lurperatzea
    desgaituta dagoenean, honek aukera ematen du saio berean ikusteko ohar baten txartel guztiak
    (adibidez, bai aurrealdea→atzealdea eta bai atzealdea→aurrealdea txartelak). 
    
    `Ausazko txartelak`: erabat ausaz biltzen ditu txartelak.
deck-config-new-gather-priority-deck = Sorta
deck-config-new-gather-priority-position-lowest-first = Posizioa gorantz
deck-config-new-gather-priority-position-highest-first = Posizioa beherantz
deck-config-new-gather-priority-random-notes = Ausazko oharrak
deck-config-new-gather-priority-random-cards = Ausazko txartelak
deck-config-new-card-sort-order = Txartel berrien ordena
deck-config-new-card-sort-order-tooltip-2 =
    `Txartel-mota`: txartel-motaren zenbakiaren araberako ordenan erakusten ditu txartelak. Haurrideen
    lurperatzea desgaituta badago, honek bermatuko du aurrealdea→atzealdea txartel guztiak ikusiko direla
    atzealdea→aurrealdea txartelik ikusi baino lehen. Baliagarria da ohar bereko txartel guztiak saio berean
    ikusteko, baina ez elkarrengandik hurbilegi.
    
    `Bildutako ordena`: bildu bezalaxe erakusten ditu txartelak. Haurrideen lurperatzea desaktibatuta
    badago, normalean, ohar bereko txartel guztiak elkarren jarraian ikustea eragingo du.
    
    `Txartel-mota, ondoren ausaz`: `Txartel-mota` bezala, baina txartel-mota bakoitzeko txartelak nahasten
    ditu. `Posizioa gorantz` erabiltzen baduzu txartel zaharrenak biltzeko, aukera hau erabil dezakezu
    txartel horiek ausaz ordenatuta ikusteko, baina bermatuz ohar bereko txartelak ez direla elkarrengandik
    hurbilegi agertuko.
    
    `Ausazko oharra, ondoren txartel-mota`: oharrak ausaz hartzen ditu, eta bakoitzaren txartel guztiak
    ordenan erakusten ditu.
    
    `Ausaz`: bildutako txartelak guztiz nahasten ditu.
deck-config-sort-order-card-template-then-random = Txartel-mota, ondoren ausaz
deck-config-sort-order-random-note-then-template = Ausazko oharra, ondoren txartel-mota
deck-config-sort-order-random = Ausaz
deck-config-sort-order-template-then-gather = Txartel-mota
deck-config-sort-order-gather = Bildutako ordena
deck-config-new-review-priority = Berrien eta berrikustekoen arteko ordena
deck-config-new-review-priority-tooltip = Noiz erakutsi txartel berriak berrikustekoekin alderatuta.
deck-config-interday-step-priority = Lehendik "Ikasten" egoeran zeudenen eta berrikustekoen arteko ordena
deck-config-interday-step-priority-tooltip =
    Noiz erakutsi lehendik "Ikasten" egoeran zeuden txartelak.
    
    Berrikuspen-mugak beti eragiten die lehenik txartel horiei, eta ondoren berrikuspenei. Aukera honek
    kontrolatuko den zein ordenatan erakutsi bildutako txartelak, baina lehendik "Ikasten" egoeran zeuden
    txartelak beti bilduko dira lehenik.
deck-config-review-mix-mix-with-reviews = Nahasi berrikustekoekin
deck-config-review-mix-show-after-reviews = Erakutsi berrikustekoen ondoren
deck-config-review-mix-show-before-reviews = Erakutsi berrikustekoen aurretik
deck-config-review-sort-order = Berrikustekoen ordena
deck-config-review-sort-order-tooltip =
    Ordena lehenetsiak lehentasuna ematen die denbora gehien zain daramaten txartelei. Horrela, berrikuspen
    asko pilatu bazaizkizu, zain gehien daramatenak agertuko dira lehenik. Egun gutxi batzuetan berrikusi
    ditzakezunak baino txartel gehiago pilatzen bazaizkizu, edo txartelak azpisorten arabera ordenatuta
    ikusi nahi badituzu, beste aukerak probatu ditzakezu.
deck-config-sort-order-due-date-then-random = Berrikuste-data, ondoren ausaz
deck-config-sort-order-due-date-then-deck = Berrikuste-data, ondoren sorta
deck-config-sort-order-deck-then-due-date = Sorta, ondoren berrikuste-data
deck-config-sort-order-ascending-intervals = Tarteak gorantz
deck-config-sort-order-descending-intervals = Tarteak beherantz
deck-config-sort-order-ascending-ease = Erraztasuna gorantz
deck-config-sort-order-descending-ease = Erraztasuna beherantz
deck-config-sort-order-relative-overdueness = Atzerapen erlatiboa
deck-config-display-order-will-use-current-deck =
    Ankik ikasteko hautatzen duzun sortaren erakuste-ordena
    erabiliko du, eta ez horrek izan litzakeen azpisortenak.

## Timer section

deck-config-timer-title = Kronometroa
deck-config-maximum-answer-secs = Gehieneko erantzun-denbora, segundotan
deck-config-maximum-answer-secs-tooltip =
    Berrikuspen bakar baterako gehienez erregistratuko den segundo kopurua. Erantzun batek
    denbora hori gainditzen badu (adibidez, pantailatik aldendu zarelako), ezarritako muga
    erabiliko da erantzun-denbora erregistratzeko.
deck-config-show-answer-timer-tooltip =
    Erakutsi kronometroa berrikuspen-pantailan, txartel bakoitza berrikusteko
    behar dituzun segundoak zenbatzen dituena.

## Audio section

deck-config-audio-title = Audioa
deck-config-disable-autoplay = Ez erreproduzitu audioa automatikoki
deck-config-skip-question-when-replaying = Saltatu galdera erantzuna berriro erreproduzitzean
deck-config-always-include-question-audio-tooltip =
    Ea galderaren audioa ere erreproduzituko den "Berriro erreproduzitu" sakatzean,
    txartelaren erantzunari begira zaudela.

## Advanced section

deck-config-advanced-title = Aurreratuak
deck-config-maximum-interval-tooltip =
    Berrikusteko txartel bat gehienez zain egongo den egun kopurua. Berrikuspenek
    muga gainditzean, `Zaila`, `Ondo` eta `Erraza` botoiek berdin atzeratuko dute
    txartela. Zenbat eta motzagoa tarte hau, orduan eta handiagoa zure lan-karga.
deck-config-starting-ease-tooltip =
    Txartel berrien hasierako erraztasun-biderkatzailea. Lehenespenez, `Ondo` botoia
    sakatzean, 2,5 bider aurreko tartea izango da ikasi berri duzun txartel baten hurrengo tartea.
deck-config-easy-bonus-tooltip = `Erraza` sakatzean berrikusteko txartel baten tarteari aplikatzen zaion biderkatzaile gehigarria.
deck-config-interval-modifier-tooltip =
    Biderkatzaile hau berrikuspen guztiei aplikatzen zaie. Doikuntza txikiak egin daitezke
    Ankik antolaketa kontserbadoreagoa edo agresiboagoa egin dezan. Kontsultatu
    eskuliburua aukera hau aldatu baino lehen.
deck-config-hard-interval-tooltip = `Zaila` sakatzean berrikuspen-tarteari aplikatzen zaion biderkatzailea.
deck-config-new-interval-tooltip = `Berriro` sakatzean berrikuspen-tarteari aplikatzen zaion biderkatzailea.
deck-config-minimum-interval-tooltip = `Berriro` sakatzean berrikusteko txartel bati eman dakiokeen tarte txikiena.
deck-config-custom-scheduling = Antolaketa pertsonalizatua
deck-config-custom-scheduling-tooltip = Bilduma osoari eragiten dio. Kontuz erabili!

## Adding/renaming

deck-config-add-group = Gehitu aurrezarpena
deck-config-name-prompt = Izena
deck-config-rename-group = Berrizendatu aurrezarpena
deck-config-clone-group = Klonatu aurrezarpena

## Removing

deck-config-remove-group = Kendu aurrezarpena
deck-config-will-require-full-sync =
    Eskatutako aldaketak noranzko bakarreko sinkronizazio bat beharko du. Beste gailu batean
    aldaketak egin badituzu, eta oraindik ez badituzu gailu honekin sinkronizatu, egin ezazu
    jarraitu baino lehen.
deck-config-confirm-remove-name = Kendu { $name }?

## Other Buttons

deck-config-save-button = Gorde
deck-config-save-to-all-subdecks = Gorde azpisorta guztietarako
deck-config-revert-button-tooltip = Berrezarri ezarpenaren balio lehenetsia.

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Anki 2.1.41+ tratamendua
deck-config-description-new-handling-hint =
    Sarrera Markdown gisa tratatzen du eta HTML sarrera garbitzen du. Gaituta dagoenean,
    deskribapena "Zorionak!" pantailan ere agertuko da. Markdown-a testu gisa agertuko da
    Anki 2.1.40 eta aurrekoetan.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    Sorta guraso batek { $cards ->
        [one] txartel bakarreko
       *[other] { $cards } txarteleko
    } muga dauka. Horrek muga hau gainidatziko du.
deck-config-reviews-too-low =
    Egunero { $cards ->
        [one] txartel berri bat gehitzen baduzu
       *[other] { $cards } txartel berri gehitzen badituzu
    }, berrikuspen-mugak { $expected }(e)koa izan behar luke gutxienez.
deck-config-learning-step-above-graduating-interval = Graduatze-tarteak gutxienez azken ikasketa-urratsak bezain luzea izan behar luke.
deck-config-good-above-easy = Errazetarako tarteak gutxienez graduatze-tarteak bezain luzea izan behar luke.
deck-config-relearning-steps-above-minimum-interval = Gutxieneko tarteak azken berrikasketa-urratsak bezain luzea izan behar luke gutxienez.
deck-config-maximum-answer-secs-above-recommended = Ankik era eraginkorragoan antolatu ditzake berrikuspenak galdera motzak idazten badituzu.

## Selecting a deck

deck-config-which-deck = Zein sorta nahi duzu?

## NO NEED TO TRANSLATE. These strings have been replaced with new versions, and will be removed in the future.

