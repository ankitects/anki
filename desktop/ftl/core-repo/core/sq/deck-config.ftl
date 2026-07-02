### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    { $decks ->
        [one] përdoret nga { $decks } pako
       *[other] përdoret nga { $decks } pako
    }
deck-config-default-name = I paracaktuar
deck-config-title = Cilësimet e pakos

## Daily limits section

deck-config-daily-limits = Limiti ditor
deck-config-new-limit-tooltip =
    Numri maksimal i kartave të reja për të prezantuar në një ditë, nëse kartat e reja janë në dispozicion.
    Për shkak që materiali i ri rrit sasinë e punës për kartat e juaja me përsëritje afatshkurt, kjo duhet zakonisht
    të jetë 10x më i vogël se limiti juaj i përsëritjes.
deck-config-review-limit-tooltip =
    Numri maksimal i kartave për përsëritje që shfaqen në një ditë,
    nëse kartat janë gati për përsëritje.
deck-config-limit-deck-v3 =
    Kur mësoni një pako që ka nënpako në të, limiti i vendosur në seciën
    nënpako përcakton numrin maksimal të kartave të grumbulluara nga ajo pako përkatëse.
    Limitet e pakos së zgjedhur përcaktojnë numrin total të kartave që do të shfaqen.
deck-config-limit-new-bound-by-reviews =
    Limiti i përsëritjeve ndikon në limitin e ri. Për shembull, nëse limiti juaj i përsëritjeve
    është vendosur në 200, dhe ju keni 190 përsëritje në pritje, maksimum 10 karta të reja
    do të prezantohen. Nëse limiti juaj i përsëritjeve është mbërrit, asnjë kartë
    e re nuk do të shfaqet.
deck-config-limit-interday-bound-by-reviews =
    Limiti i përsëritjeve poashtu ndikon në kartat e mësimit të ditës. Kur të vendoset limiti,
    kartat e mësimit të ditës grumbullohen së pari, pastaj kartat e përsëritjes.
deck-config-tab-description =
    - 'Paravendosur': Limiti vlen për të gjitha pakot që përdorin këtë paravendosje.
    - 'Kjo pako': Limiti vlen vetëm për këtë pako.
    - 'Vetëm sot': Bëj një ndryshim të përkohshëm të limitit të kësaj pakos.
deck-config-new-cards-ignore-review-limit = Kartat e reja injorojnë limitin e përsëritjeve
deck-config-new-cards-ignore-review-limit-tooltip =
    Me paracaktim, limiti i përsëritjeve poashtu vlen për kartat e reja, dhe asnjë kartë e re nuk
    shfaqet kur të arrihet limiti i përsëritjeve. Nëse ky cilësim është lejuar, kartat e reja
    do të shfaqen pavarsisht limitit të përsëritjeve.
deck-config-apply-all-parent-limits = Limitet fillojnë nga lartë
deck-config-apply-all-parent-limits-tooltip =
    Me paracaktim, limiti ditor i një pakoje nuk vlen nëse mësoni nga nënpako.
    Nëse ky cilësim është i lejuar, në këtë rast limitet do të
    fillojnë nga pakoja e nivelit më të lartë, e cila mund të jetë e dobishme nëse dëshironi të mësoni nënpakot e vequara,
    përderisa përforconi një limit total në karta për pemën e pakos.
deck-config-affects-entire-collection = Ndikon në tërë koleksionin.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = E parapregaditur
deck-config-deck-only = Kjo pako
deck-config-today-only = Vetëm sot

## New Cards section

deck-config-learning-steps = Hapat mësimor
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Shtyerjet janë zakonisht minuta (p.sh. `1m`) ose ditë (p.sh. `2d`), por orët (p.sh. `1h`) dhe sekondat (p.sh. `30s`) mbështeten.
deck-config-learning-steps-tooltip =
    Një apo më shumë vonesa, të ndara me hapsirë. Vonesa e parë do të përdoret
    kur ju shtypni butonin `Përsëri` në një kartë të re, dhe është e paracaktuar të jetë 1 minut.
    Butoni `Mirë` avancon në hapin e radhës, që është paracaktuar të jetë 10 minuta.
    Pasi të gjithë hapat janë kaluar, karta bëhet kartë për përsëritje, dhe
    do shfaqet në një ditë tjeter. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip =
    Numri i ditëve për të pritur para se një kartë shfaqet përsëri, pasi butoni `Mirë`
    është shtypur në hapin e fundit të mësuarit.
deck-config-easy-interval-tooltip =
    Numri i ditëve për të pritur para se një kartë shfaqet përsëri, pasi butoni `Lehtë`
    është përdorur për ta larguar kartën menjëherë nga mësimi.
deck-config-new-insertion-order = Renditja e të shtuarit
deck-config-new-insertion-order-tooltip =
    Kontrollon pozicionin (për të mësuar #) që i përcaktohet kartave të reja kur shtoni karta të reja.
    Kartat me numër më të ulët do të shtohen së pari gjatë mësimit. Kur ndërroni
    këtë opsion pozicioni i kartave të reja do ndërrohet automatikisht.
deck-config-new-insertion-order-sequential = Me radhë (kartat më të vjetra të parat)
deck-config-new-insertion-order-random = Rastësor
deck-config-new-insertion-order-random-with-v3 =
    Me ndërtuesin e orarit v3, është më mirë ta leni këtë të vendosur "Me radhë", dhe të
    ndreqni renditjen e bashkësisë së kartës së re.

## Lapses section

deck-config-relearning-steps = Hapat e rimësimit
deck-config-relearning-steps-tooltip = Zero ose më shumë vonesa, të ndara nga hapsira. Me paracaktim, duke shtypur butonin `Përsëri` në një kartë për përsëritje do ta shfaqë atë prapë 10 minuta më vonë. Nëse asnjë vonesë nuk është dhënë, kartës do ti ndryshohet intervali, pa u futur në rimësim. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip =
    Hera e satë `Përsëri` është shtypur duhet të jetë prezent në një kartë për përsëritje para se të shënjohet si kartë shushunjë. Shushunjat janë karta që konsumojnë shumë kohë, dhe 
    që kur të shënjohen si shushunjë, është ide e mirë ta rishkruani atë, ta fshini atë, ose
    të mendoni një mnemonikë, që ju ndihmon ta mbani në mend.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `Vetëm etiketë`: Shto një etiketë `shushunjë` në shënim, dhe shfaq një dritare.
    
    `Pezullo kartën`: Përveq etiketimit të shënimit, fshih kartën përderisa nuk është hequr pezullimi manualisht.

## Burying section

deck-config-bury-title = Duke groposur
deck-config-bury-new-siblings = Gropos binjakun e ri
deck-config-bury-review-siblings = Gropos binjakun për përsëritje
deck-config-bury-interday-learning-siblings = Gropos hapat e mësimit brendaditor
deck-config-bury-new-tooltip =
    Nëse karta të tjera `të reja` të shënimit të njejtë (p.sh kartat e kthyera mbrapsht, fshirjet cloze të afërta)
    do të shtyhen deri në ditën e ardhshme.
deck-config-bury-review-tooltip = Nëse karta të tjera `përsëritje` të shënimit të njejtë do të shtyhen deri në ditën e ardhshme.
deck-config-bury-interday-learning-tooltip =
    Nëse kartat e tjera `të mësimit` të shënimit të njejtë me intervalet > 1 ditë
    do të shtyhen deri në diten e radhës.

## Gather order and sort order of cards

deck-config-ordering-title = Radha e shfaqjes

## Gather order and sort order of cards – Combobox entries


## Timer section


## Auto Advance section


## Audio section


## Advanced section


## Easy Days section.


## Adding/renaming


## Removing


## Other Buttons


## These strings are shown via the Description button at the bottom of the
## overview screen.


## Warnings shown to the user


## Selecting a deck


## Messages related to the FSRS scheduler


## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.


## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

