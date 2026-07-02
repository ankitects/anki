### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    i bhfeidhm ag { $decks ->
        [one] paca amháin
        [two] { $decks } phaca
        [few] { $decks } phaca
        [many] { $decks } bpaca
       *[other] { $decks } paca
    }
deck-config-default-name = Réamhshocrú
deck-config-title = Sainroghanna Paca

## Daily limits section

deck-config-daily-limits = Uasmhéid Laethúla
deck-config-new-limit-tooltip =
    Srian ar an líon cártaí nua a thionscnófar in aon lá amháin, má tá cártaí nua agat in
    aon chor. Cuireann cártaí nua leis an ualach oibre go ceann tamaill, agus mar sin moltar
    go raibh an srian seo ar a laghad deich n-uaire níos lú ná an srian athbhreithnithe.
deck-config-review-limit-tooltip =
    Srian ar an líon cártaí a bheadh le hathbhreithniú agat in aon
    lá amháin, má tá cártaí le hathbhreithniú agat in aon chor.
deck-config-limit-deck-v3 =
    Agus staidéar á dhéanamh ar phaca a bhfuil fophacaí mar chuid de,
    cuireann an t-uasmhéid a bhaineann leis na fophacaí sin teorainn leis an 
    méid cártaí a tharraingeofar ón bhfophaca sin. Ansin cuireann uasmhéid
    an mhórphaca féin teorainn leis an méid cártaí trí chéile a fheicfear.
deck-config-limit-new-bound-by-reviews =
    Téann an t-uasmhéid athbhreithnithe i bhfeidhm ar an uasmhéid cártaí nua.
    Cuir i gcás, más é 200 an t-uasmhéid athbhreithnithe agus 190 athbhreithniú
    le déanamh agat, ní chuirfear ar fáil ach amháin 10 gcárta nua. Má tá an
    t-uasmhéid baint amach, ní chuirfear aon chárta nua ar fáil.
deck-config-limit-interday-bound-by-reviews =
    Cuirtear cártaí atá á bhfoghlaim fós san áireamh san uasmhéid athbhreithnithe.
    Agus an uasmhéid á cur i bhfeidhm, comhairfear na cártaí atá á bhfoghlaim ar dtús,
    ansin athbhreithnithe, agus ar deireadh cártaí nua.
deck-config-tab-description =
    - `Grúpa`: Baineann an srian le gach paca sa ghrúpa.
    - `An paca seo`: Baineann an srian leis an bpaca seo amháin.
    - `Inniu amháin`: Athraítear srian an phaca seo go sealadach.
deck-config-new-cards-ignore-review-limit = Ní bhacann cártaí nua leis an uasmhéid athbhreithnithe cártaí
deck-config-new-cards-ignore-review-limit-tooltip =
    Go hiondúil bíonn cártaí nua faoi réir an uasmhéid athbhreithnithe cártaí.
    Is é sin le rá nach bhfeicfear cárta nua ar bith nuair a bhaintear an uasmhéid
    athbhreithnithe amach. Má roghnaítear é seo, feicfear cártaí nua ar aon chaoi.
deck-config-affects-entire-collection = I bhfeidhm ar an gcnuasach uile.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Grúpa
deck-config-deck-only = An paca seo
deck-config-today-only = Inniu amháin

## New Cards section

deck-config-learning-steps = Céimeanna foghlamtha
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Féadtar eatraimh a scríobh le nóiméid (.i. "5m") nó le laethanta (.i. "2d").
deck-config-learning-steps-tooltip =
    Eatramh nó eatraimh agus spás curtha eatarthu. Oibreofar an chéad eatramh
    nuair a roghnaítear an cnaipe 'Arís' agus cárta nua á fhoghlaim - 1 nóiméad amháin
    an t-eatramh sin de ghnáth. Cuirfeadh an cnaipe 'Go maith' ar aghaidh thú go dtí
    an chéad eatramh eile - 10 nóiméad de ghnáth. Tar éis duit na céimeanna uile a
    bhaint amach, athróidh an cárta nua ina chárta athbhreithnithe, agus feicfear
    arís é lá éigin eile. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip =
    Feicfear an cárta seo arís tar éis an t-eatramh seo (laethanta) tar éis duit
    an cnaipe 'Go maith' a roghnú ag an gcéim dheireanach foghlamtha.
deck-config-easy-interval-tooltip =
    Feicfear an cárta seo arís tar éis an t-eatramh seo (laethanta) tar éis duit
    an cnaipe 'Éasca' a roghnú agus tú ag foghlaim cárta nua.
deck-config-new-insertion-order = Ord ionsáite
deck-config-new-insertion-order-tooltip =
    Socraítear suíomh (# staidéir) aon chárta nua a chuireann tú leis. An cárta
    a bhfuil an uimhir staidéir is ísle aige, feicfear i dtosach é. Má athraítear
    an socrú seo athrófar suíomh do chuid cárta nuaí go huathoibríoch.
deck-config-new-insertion-order-sequential = In ord (is sine i dtosach)
deck-config-new-insertion-order-random = Gan ord (ord randamach)
deck-config-new-insertion-order-random-with-v3 =
    Anois agus Sceidealú LGN3 i bhfeidhm, is fearr é seo a fhágáil mar atá
    (is sine i dtosach), agus ord tagtha na gcártaí nua a athrú ina ionad.

## Lapses section

deck-config-relearning-steps = Céimeanna athfhoghlamtha
deck-config-relearning-steps-tooltip =
    Bíodh eatraimh anseo agus spás eatarthu, nó bíodh an spás seo folamh.
    Nuair a roghnaítear an cnaipe 'Arís' agus cárta á athbhreithniú, 
    taispeáintear arís é tar éis 10 nóiméad de ghnáth. Má fhágtar an spás
    folamh, athrófar eatramh an chárta ach ní dhéanfar aon athfhoghlaim
    air. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip =
    Má roghnaíonn tú Arís faoin méid seo agus tú ag athbhreithniú an chárta seo
    aithneofar mar shúmaire é. Súmaire is ea é an cárta a mbíonn tú ag caitheamh
    go leor ama air. Dhéanfadh sé maith duit an cárta súmaire a athscríobh nó a
    scriosadh, nó cuimhneamh ar sheift éigin chun cuimhne níos fearr a fháil air.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    <b>Ach an Chlib</b>: Cuirtear an chlib "súmaire" leis an nóta, agus taispeáintear fógra.<br>
    <b>Cuir ar Fionraí</b>: Cuir an chlib leis agus cuir an nóta ar fionraí sa chaoi nach bhfeicfear
    arís é gan thú é a tharraingt amach as fionraí thú féin.

## Burying section

deck-config-bury-title = Cur i bhFolach
deck-config-bury-new-siblings = Cuir deirfiúrchártaí nua i bhfolach go dtí an lá arna mhárach
deck-config-bury-review-siblings = Cuir deirfiúrchártaí athbhreithnithe i bhfolach go dtí an lá arna mhárach
deck-config-bury-interday-learning-siblings = Cuir deirfiúrchártaí foghlamtha ile-lae i bhfolach go dtí an lá arna mhárach

## Ordering section

deck-config-ordering-title = Ord Taispeána
deck-config-new-gather-priority = Tosaíocht tagtha na gcártaí nua
deck-config-new-gather-priority-tooltip-2 =
    `Paca`: tagann cártaí ó gach uile phaca de réir a chéile ón mbarr anuas. Tagann cártaí
    ó gach uile phaca agus uimhir shuíomh an chárta ag dul i méid. A luaithe is a bhaintear
    an t-uasmhéid laethúil amach, beidh deireadh le theacht na gcártaí, fiú amháin más rud
    é nár tháinig cártaí ó na pacaí uile. Is é seo an t-ord is sciobtha nuair atá cnuasach mór
    i gceist agus tugtar tosach áite do na fophacaí is gaire don bharr.
    
    `Is ísle i dtosach`: tagann cártaí agus uimhir shuíomh an chárta ag dul i méid. Is iad na
    cártaí is túisce a cruthaíodh a mbíonn an uimhir is lú acu de ghnáth.
    
    `Is uaisle i dtosach`: tagann cártaí agus uimhir shuíomh an chárta ag dul i laghad. Is iad na
    cártaí is deireanaí a cruthaíodh a mbíonn an uimhir is mó acu de ghnáth.
    
    `Nótaí randamacha`: tagann cártaí ó nótaí a roghnaítear go randamach. Nuair nach 
    bhfuil deirfiúrchártaí á gcur i bhfolach, feictear gach uile chárta a bhaineann leis an nóta
    le chéile (mar shampla, tosach->cúl agus cúl->tosach)
    
    `Cártaí randamacha`: tagann cártaí go randamach uile.
deck-config-new-gather-priority-deck = Paca
deck-config-new-gather-priority-position-lowest-first = Suíomh (is ísle i dtosach)
deck-config-new-gather-priority-position-highest-first = Suíomh (is uaisle i dtosach)
deck-config-new-gather-priority-random-notes = Nótaí randamacha
deck-config-new-gather-priority-random-cards = Cártaí randamacha
deck-config-new-card-sort-order = Ord scagtha cártaí nua
deck-config-new-card-sort-order-tooltip-2 =
    `Teimpléad cárta`: Feictear cártaí de réir ord theimpléad na gcártaí féin. Nuair nach
    bhfuil deirfiúrchártaí á gcur i bhfolach, feictear gach uile chárta tosach->cúl sula
    bhfeictear aon chárta cúl->tosach.
    
    `Ord tagtha`: Feictear cártaí san ord céanna inar tháinig siad. Nuair nach bhfuil
    deirfiúrchártaí á gcur i bhfolach, feictear gach uile chárta a bhaineann le nóta amháin
    de réir a chéile de ghnáth.
    
    `Teimpléad cárta, ansin ord randamach`: Cosúil le `Teimpléad cárta`, ach measctar
    na cártaí a bhaineann le gach uile theimpléad. Agus cártaí ag teacht `is ísle i dtosach`,
    d'fheicfí na cártaí is túisce a cruthaíodh in ord randamach, cuirtear i gcás.
    
    `Nóta randamach, ansin de réir theimpléad an chárta`: Roghnaítear nótaí go randamach
    agus ansin feictear a gcuid deirfiúrchártaí in ord an teimpléid.
    
    `Ord randamach`: Feictear na cártaí uile a tháinig in ord randamach.
deck-config-sort-order-card-template-then-random = Teimpléad cárta, ansin ord randamach
deck-config-sort-order-random-note-then-template = Nóta randamach, ansin de réir theimpléad an chárta
deck-config-sort-order-random = Ord randamach
deck-config-sort-order-template-then-gather = Teimpléad cárta, ansin ord bailithe
deck-config-sort-order-gather = Ord tagtha
deck-config-new-review-priority = Tosaíocht do nua/athbhreithniú
deck-config-new-review-priority-tooltip = Cén áit a bhfeicfear cártaí nua (i gcoibhneas na gcártaí athbhreithnithe)
deck-config-interday-step-priority = Tosaíocht an fhoghlamtha/athbhreithnithe ile-lae
deck-config-interday-step-priority-tooltip = Cén áit a bhfeicfear cártaí (ath)foghlamtha ón lá roimhe.
deck-config-review-mix-mix-with-reviews = Measctha tríd na hathbhreithnithe
deck-config-review-mix-show-after-reviews = Chun cúil ar na hathbhreithnithe
deck-config-review-mix-show-before-reviews = Chun tosaigh ar na hathbhreithnithe
deck-config-review-sort-order = Ord scagtha na n-athbhreithnithe
deck-config-review-sort-order-tooltip =
    Gan an rogha seo a bheith athraithe, tugtar tús áite do na cártaí is faide
    atá ag fanacht. Má tá staidéar curtha siar agat le laethanta anuas, mar sin,
    feicfidh tú na cártaí is faide gan staidéar i dtosach. Má tá a oiread sin 
    cártaí curtha siar agat, nó más mian leat cártaí a fheiceáil in ord an fhophaca,
    b'fhéidir go mbeifeá ag iarraidh ord scagtha eile a roghnú.
deck-config-sort-order-due-date-then-random = De réir dáta staidéir, ansin in ord randamach
deck-config-sort-order-due-date-then-deck = De réir dáta staidéir, ansin de réir (fo)paca
deck-config-sort-order-deck-then-due-date = De réir (fo)paca, ansin de réir dáta staidéir
deck-config-sort-order-ascending-intervals = De réir eatraimh (is lú ar dtús)
deck-config-sort-order-descending-intervals = De réir eatraimh (is mó ar dtús)
deck-config-sort-order-ascending-ease = Ag dul i ndeacracht
deck-config-sort-order-descending-ease = Ag dul in éascaíocht
deck-config-sort-order-relative-overdueness = De réir deireanaí
deck-config-display-order-will-use-current-deck =
    Cloífear leis an ord taispeána a bhaineann leis an
    bpaca a roghnaigh tú seachas ord taispeána a
    bhfophacaí (más ann dóibh).

## Timer section

deck-config-timer-title = Clog
deck-config-maximum-answer-secs = Srian ar shoicind fhreagartha
deck-config-maximum-answer-secs-tooltip =
    Srian ar an méid soicind is féidir a chaitheamh ar aon athbhreithniú amháin. Má
    thógann sé níos mó ama ná sin ort (de bharr thú a bheith ag déanamh gnó eile,
    cuir i gcás), stopfar ag comhaireamh tar éis an méid seo ama.
deck-config-show-answer-timer-tooltip =
    Agus tú i mbun athbhreithnithe, bíodh clog ag taispeáint duit cé
    mhéad soicind atá caite agat ar an gcárta seo.

## Auto Advance section


## Audio section

deck-config-audio-title = Fuaim
deck-config-disable-autoplay = Ná seinntear fuaim go huathoibríoch
deck-config-skip-question-when-replaying = Ná bac leis an gceist nuair atá an freagra á athsheinm
deck-config-always-include-question-audio-tooltip =
    Cé acu an seinnfear fuaim na ceiste arís nó nach seinnfear nuair a
    chastar arís fuaim an fhreagra.

## Advanced section

deck-config-advanced-title = Ardsocruithe
deck-config-maximum-interval-tooltip =
    Srian ar a eatramh is faide is féidir gan cárta áirithe a fheiceáil. Nuair atá an
    t-eatramh seo bainte amach ag cárta, is cuma cé acu Deacair, Go maith, nó
    Éasca a roghnaíonn tú cloífear fós leis an eatramh seo. Dá ghiorra an t-eatramh
    seo is amhlaidh is mó a bheidh an t-ualach oibre.
deck-config-starting-ease-tooltip =
    An t-iolrú éascaíochta a bhaineann le cártaí nua. De ghnáth, cuirtear fad 2.5x
    an tseaneatraimh mar eatramh nua tar éis duit an cnaipe Go maith a roghnú
    agus tú tar éis cárta nua a fhoghlaim.
deck-config-easy-bonus-tooltip =
    Iolrú breise a chuirtear le heatramh athbhreithnithe an chárta tar éis duit
    an cnaipe Éasca a oibriú leis.
deck-config-interval-modifier-tooltip = Is é seo an t-iolrú a oibrítear i gcás gach uile athbhreithnithe. Féadtar athraithe beaga a dhéanamh chun go mbeidh uaillmhian Anki níos mó nó níos lú agus eatraimh á socrú. Ná déantar aon athrú air seo gan an lámhleabhar a bhreathnú.
deck-config-hard-interval-tooltip = An t-iolrú ar eatraimh athbhreithnithe tar éis duit Deacair a roghnú.
deck-config-new-interval-tooltip = An t-iolrú ar eatraimh athbhreithnithe tar éis duit Arís a roghnú.
deck-config-minimum-interval-tooltip = An t-eatramh is lú is féidir a shocrú do chárta athbhreithnithe tar éis duit Arís a roghnú.
deck-config-custom-scheduling = Sceidealú saincheaptha
deck-config-custom-scheduling-tooltip = Téann sé seo i bhfeidhm ar an gcnuasach uile. Ar do phriacal féin a dhéanann!

## Adding/renaming

deck-config-add-group = Cuir Grúpa leis
deck-config-name-prompt = Ainm:
deck-config-rename-group = Athainmnigh Grúpa
deck-config-clone-group = Déan Cóip de Ghrúpa

## Removing

deck-config-remove-group = Bain Grúpa
deck-config-will-require-full-sync =
    Má dhéantar an t-athrú seo, beidh ort sioncronú a dhéanamh in aon treo amháin.
    Má tá aon athrú déanta agat ar ghléas eile nach bhfuil sioncronaithe leis an ngléas
    seo go fóill, déan é sin sula ndéana tú an t-athrú seo.
deck-config-confirm-remove-name = Bain { $name }?

## Other Buttons

deck-config-save-button = Sábháil
deck-config-save-to-all-subdecks = Sábháil ar Gach Fophaca
deck-config-revert-button-tooltip = Fill ar réamhshocrú sa chás seo.

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Anki 2.1.41 + córas
deck-config-description-new-handling-hint =
    Glacfar ionchur mar Markdown agus glanfar ionchur HTML. Agus é
    roghnaithe, taispeánfar cur síos an phaca ar an scáileán 'Maith thú!'.
    Feicfear Markdown mar théacs i leagan 2.1.40 nó níos lú d'Anki.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    Tá srian { $cards ->
        [one] { $cards } chárta amháin
        [two] { $cards } chárta
        [few] { $cards } chárta
        [many] { $cards } gcárta
       *[other] { $cards } cárta
    } i bhfeidhm ag mórphaca a bhfuil an paca seo mar chuid de. Tabharfar tús áite do shrian an mhórphaca.
deck-config-reviews-too-low =
    Más mian leat go bhfeicfeá{ $cards ->
        [one] { $cards } chárta nua gach uile lá
        [two] { $cards } chárta nua gach uile lá
        [few] { $cards } chárta nua gach uile lá
        [many] { $cards } gcárta nua gach uile lá
       *[other] { $cards } cárta nua gach uile lá
    }, ba cheart go mbeadh an srian athbhreithnithe níos mó ná { $expected }.
deck-config-learning-step-above-graduating-interval = Bíodh an t-eatramh comhlíonta ar a laghad ar comhfhad leis an eatramh deiridh foghlamtha.
deck-config-good-above-easy = Bíodh an t-eatramh 'Éasca' ar a laghad ar comhfhad leis an eatramh comhlíonta.
deck-config-relearning-steps-above-minimum-interval = Bíodh an t-eatramh is giorra is féidir i gcás an chliste ar a laghad ar comhfhad leis an eatramh deiridh athfhoghlamtha.
deck-config-maximum-answer-secs-above-recommended = Dá ghiorra do chuid ceisteanna is amhlaidh is éifeachtaí an aiste athbhreithnithe a shocróidh Anki.

## Selecting a deck

deck-config-which-deck = Cé acu paca atá i gceist agat?

## Messages related to the FSRS scheduler

deck-config-wait-for-audio = Fantar leis an bhFuaim
deck-config-show-reminder = Taispeáintear Meabhrúchán
deck-config-answer-again = Freagair 'Arís'
deck-config-answer-hard = Freagair 'Deacair'
deck-config-answer-good = Freagair 'Go Maith'

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-bury-siblings = Cuir deirfiúrchártaí i bhfolach
deck-config-do-not-bury = Ná cuir deirfiúrchártaí i bhfolach
deck-config-bury-if-new = NO NEED TO TRANSLATE
deck-config-bury-if-new-or-review = NO NEED TO TRANSLATE
deck-config-bury-if-new-review-or-interday = NO NEED TO TRANSLATE
deck-config-bury-tooltip =
    Roghnaigh cé acu an bhfágfar aon chárta atá bainteach leis an nóta
    céanna (.i. cártaí malartacha, sleachta eile iomlánaithe) go dtí lá eile.
