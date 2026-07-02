### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    { $decks ->
        [one] { $decks } دەستە ئىشلىتىلدى
       *[other] { $decks } دەستە ئىشلىتىلدى
    }
deck-config-default-name = كۆڭۈلدىكى
deck-config-title = دەستە تاللانما

## Daily limits section

deck-config-daily-limits = كۈندىلىك چېكى
deck-config-new-limit-tooltip = ئۆگىنىدىغان يېڭى كارتا بولغاندا، بىر كۈندە ئۆگىنىدىغان ئەڭ كۆپ كارتا سانى. يېڭى كارتا ئۆگەنگەندە قىسقا مۇددەتلىك تەكرارلاش مىقدارى كۆپەيگەنلىكتىن، كۈندە ئۆگىنىدىغان يېڭى كارتىنىڭ يۇقىرى چېكى كۈندىلىك تەكرارلايدىغان كارتىنىڭ يۇقىرى چېكىنىڭ 10x دىن كىچىك قىلىپ تەڭشىلىدۇ.
deck-config-review-limit-tooltip = تەكرارلايدىغان كارتا تەييار بولغاندا، بىر كۈندە تەكرارلايدىغان ئەڭ كۆپ كارتا سانى.
deck-config-limit-deck-v3 = ئۆگىنىدىغان دەستىنىڭ تارماق دەستىسى بولسا، ھەر قايسى تارماق دەستىدىن كۆرۈنىدىغان كارتىنىڭ  يۇقىرى چېكى ھەر قايسى تارماق دەستىدە تەڭشەلگەن يۇقىرى چېكى بولىدۇ. كۆرسىتىدىغان كارتا ئومۇمىي سانى ئانا دەستەدە تەڭشەلگەن يۇقىرى چېكى بولىدۇ.
deck-config-limit-new-bound-by-reviews = تەكرارلايدىغان كارتىنىڭ يۇقىرى چېكى يېڭى كارتىنىڭ يۇقىرى چېكىگە تەسىر كۆرسىتىدۇ، ئەگەر تەكرارلايدىغان كارتىنىڭ يۇقىرى چېكى 200 گە تەڭشەلسە، يەنە 190 تەكرارلايدىغان كارتا بولسا ئۇنداقتا ئەڭ كۆپ بولغاندا 10 يېڭى كارتىنى كۆرسىتىدۇ. ئەگەر تەكرارلاش چېكىگە يەتكەن بولسا ئۇنداقتا يېڭى كارتىنى كۆرسەتمەيدۇ.
deck-config-limit-interday-bound-by-reviews = تەكرارلاش چېكى كۈن ئاتلاپ ئۆگىنىدىغان كارتىغىمۇ تەسىر كۆرسىتىدۇ. تەكرارلاش چېكى قوللىنىلغاندا، كۈن ئاتلاپ ئۆگىنىدىغان كارتا-›تەكرارلايدىغان كارتا-›يېڭى كارتا تەرتىپىدە كۆرۈنىدۇ.
deck-config-tab-description =
    - «ئالدىن تەڭشەك»: بۇ چەك مەزكۇر ئالدىن تەڭشەكنى ئىشلىتىدىغان بارلىق دەستىگە ماس كېلىدۇ.
    - «نۆۋەتتىكى دەستە»: بۇ چەك پەقەت نۆۋەتتىكى دەستىگىلا ماس كېلىدۇ.
    - «بۈگۈنكىلا»: پەقەت نۆۋەتتىكى دەستىنىڭ چېكىنى ۋاقىتلىق ئۆزگەرتىشكە ماس كېلىدۇ.
deck-config-new-cards-ignore-review-limit = يېڭى كارتا تەكرارلاش چېكىنىڭ تەسىرىگە ئۇچرىمايدۇ
deck-config-new-cards-ignore-review-limit-tooltip = كۆڭۈلدىكى ئەھۋالدا، تەكرارلاش كارتىسىنىڭ يۇقىرى چېكى، يېڭى كارتىغا ماس كېلىدۇ، تەكرارلايدىغان كارتا سانى يۇقىرى چېكىگە يەتكەندە يېڭى كارتا كۆرۈنمەيدۇ. ئەگەر بۇ تاللانما قوزغىتىلسا، تەكرارلايدىغان كارتىنىڭ يۇقىرى چېكىنىڭ قانداق بولۇشىدىن قەتئىينەزەر يېڭى كارتا كۆرۈنۈۋېرىدۇ.
deck-config-apply-all-parent-limits = چوققىسىدىن باشلاش چېكى
deck-config-apply-all-parent-limits-tooltip = كۆڭۈلدىكى ئەھۋالدا، يۇقىرى چېكى سىز تاللىغان دەستىدىكى تەڭشەكتىن باشلىنىدۇ. ئەگەر بۇ تاللانما قوزغىتىلسا، يۇقىرى چېكى يۇقىرى دەرىجىلىك دەستىنىڭ تەڭشىكىدىن باشلىنىدۇ، شۇنىڭ بىلەن بىللە كۈندىلىك ئومۇمىي كارتا سانى يۇقىرى چېكىدىن ئاشماسلىققا كاپالەتلىك قىلىدۇ.
deck-config-affects-entire-collection = پۈتكۈل توپلامغا تەسىر كۆرسىتىدۇ.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = ئالدىن سەپلەش
deck-config-deck-only = بۇ دەستە
deck-config-today-only = بۈگۈنلا

## New Cards section

deck-config-learning-steps = ئۆگىنىش باسقۇچى
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = كېچىكتۈرۈلۈش ۋاقتى ئادەتتە مىنۇت (مەسىلەن «1m») ياكى كۈن (مەسىلەن «2d») قىلىپ تەڭشىلىدۇ ئەمما سائەت (مەسىلەن «1h») ۋە سېكۇنت (مەسىلەن «30s») نىمۇ قوللايدۇ.
deck-config-learning-steps-tooltip = بىر ياكى بىر قانچە كېچىكتۈرۈش بوشلۇق بىلەن ئايرىلىدۇ. بىرىنچى كېچىكتۈرۈش يېڭى كارتا ئۆگەنگەندە، «تەكرار» نى تاللىغاندىن كېيىن كارتا قايتا كۆرۈنىدىغان ۋاقىت ئارىلىقى (كۆڭۈلدىكى قىممىتى 1 مىنۇت). ئىككىنچى كېچىكتۈرۈش يېڭى كارتا ئۆگەنگەندە، «ياخشى» نى تاللىغاندىن كېيىن كېيىنكى باسقۇچتىكى ۋاقىت ئارىلىقى (كۆڭۈلدىكى قىممىتى 10 مىنۇت). ھەممە باسقۇچتىن كېيىن، كارتا تەكرارلايدىغان كارتىغا ئۆزگىرىپ ئوخشاش بولمىغان كۈنلەردە كۆرۈنىدۇ. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip = ئەڭ ئاخىرقى باسقۇچتا «ياخشى» توپچە تاللانغاندىن كېيىن، كارتىنى قايتا كۆرسىتىدىغان كۈن سانى.
deck-config-easy-interval-tooltip = «ئاسان» تاللىنىپ ئۆگىنىش جەريانىدىن ئاتلاپ كەتكەندىن كېيىن، كارتىنى قايتا كۆرسىتىدىغان كۈن سانى.
deck-config-new-insertion-order = قىستۇرۇش تەرتىپى
deck-config-new-insertion-order-tooltip = يېڭى كارتىنىڭ ئورنى (مۆھلىتى #) نى قوشۇشنى تىزگىنلەيدۇ. ئۆگەنگەندە مۆھلەت ۋاقتىنىڭ سانى كىچىكرەك بولغان كارتىنى ئالدى بىلەن كۆرسىتىدۇ. بۇ تاللانما ئۆزگەرتىلسە مەۋجۇت يېڭى كارتىنىڭ ئورنى ئۆزلۈكىدىن يېڭىلىنىدۇ.
deck-config-new-insertion-order-sequential = تەرتىپلىك (كونا كارتا ئالدىدا)
deck-config-new-insertion-order-random = خالىغانچە
deck-config-new-insertion-order-random-with-v3 = V3 كۈنتەرتىپ ھېسابلاش ئۇسۇلىنى ئىشلەتكەندە، تەرتىپ بويىچە قىستۇرۇشنىڭ ئورنىغا يېڭى كارتا توپلاش تەرتىپى تاللانمىسى تەڭشەلگىنى ياخشى.

## Lapses section

deck-config-relearning-steps = قايتا ئۆگىنىش باسقۇچى
deck-config-relearning-steps-tooltip = نۆل ياكى كۆپ كېچىكىش بوشلۇق بىلەن ئايرىلىدۇ. كۆڭۈلدىكى ئەھۋالدا، كارتا تەكرارلاۋاتقاندا «تەكرار» تاللانسا، كارتا 10 مىنۇتتىن كېيىن قايتا كۆرۈنىدۇ. ئەگەر كېچىكتۈرۈش تەمىنلەنمىگەن بولسا، كارتا ئارىلىقى تەڭشىلىپ، قايتا ئۆگىنىش باسقۇچىغا كىرمەيدۇ. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip = تەكرارلايدىغان كارتىغا ئەستە تۇتۇش قىيىن «ئۇنتۇلغاق كارتا» بەلگىسى سېلىشتىن ئىلگىرى، «تەكرار» نى تاللاش قېتىم سانى. ئۇنتۇلغاق كارتا ئەستە ساقلاشقا كۆپ ۋاقىت سەرپ قىلىدىغان كارتا بولۇپ، كارتىغا ئۇنتۇلغاق كارتا بەلگىسى سېلىنغاندا، ئەڭ ياخشى ئەستە قالدۇرۇش ئۇسۇلى كارتىنى قايتا يېزىش، ئۆچۈرۈش ياكى قىسقارتىپ يېزىش، يادلاش قاتارلىق ياردەمچى ئەستە ساقلاش ئۇسۇلىنى قوللىنىش كېرەك.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    «بەلگىلا سېلىنىدۇ»: خاتىرىگە «ئۇنتۇلغاق كارتا» بەلگىسى سېلىنىپ، ئەسكەرتىش كۆرۈنىدۇ.
    
    «كارتىنى كېچىكتۈر»: خاتىرىگە بەلگە سېلىپ، قولدا كېچىكتۈرۈشنى توختاتمىغۇچە كارتا يوشۇرۇلىدۇ.

## Burying section

deck-config-bury-title = يوشۇرۇش
deck-config-bury-new-siblings = يېڭى ئالاقىدار كارتىنى يوشۇر
deck-config-bury-review-siblings = تەكرارلايدىغان ئالاقىدار كارتىنى يوشۇر
deck-config-bury-interday-learning-siblings = كۈن ئاتلاپ ئۆگىنىدىغان ئالاقىدار كارتىنى يوشۇر
deck-config-bury-new-tooltip = ئوخشاش خاتىرەدىكى باشقا يېڭى كارتا (مەسىلەن ئۆرۈلىدىغان كارتا، قوشنا بوش ئورۇننى تولدۇرۇش كارتىسى) ئەتىسىگىچە كېچىكتۈرۈلەمدۇ يوق.
deck-config-bury-review-tooltip = ئوخشاش بىر خاتىرەدىكى باشقا «تەكرارلايدىغان» كارتا ئەتىسىگىچە كېچىكتۈرۈلەمدۇ يوق.
deck-config-bury-interday-learning-tooltip = ئوخشاش بىر خاتىرەدىكى باشقا ئۆگىنىش ۋاقتى 1 كۈندىن يۇقىرى بولغان «تەكرارلايدىغان» كارتا ئەتىسىگىچە كېچىكتۈرۈلەمدۇ يوق.
deck-config-bury-priority-tooltip =
    Anki نىڭ كارتا توپلاش تەرتىپى شۇ كۈنى ئۆگىنىدىغان كارتا ← كۈن ئاتلاپ ئۆگىنىدىغان كارتا ← تەكرارلايدىغان كارتا ← يېڭى كارتا.
    بۇ تەرتىپ كارتا يوشۇرۇشنى بىر تەرەپ قىلىش ئۇسۇلىغا تەسىر كۆرسىتىدۇ:
    
    - ئەگەر بارلىق يوشۇرۇش تاللانمىلىرى قوزغىتىلغان بولسا، تىزىمنىڭ ئەڭ ئالدىدىكى ئالاقىدار كارتىنى كۆرسىتىدۇ. مەسىلەن، تەكرارلايدىغان كارتىنى يېڭى كارتىدىن بۇرۇن كۆرسىتىدۇ.
    - تىزىمنىڭ ئارقىسىدىكى ئالاقىدار كارتا تىزىمنىڭ ئالدىدىكى كارتىنىڭ تۈرىنى يوشۇرالمايدۇ. مەسىلەن، يېڭى كارتا يوشۇر چەكلەنگەندە، يېڭى كارتا ئۆگەنگەندە ئالاقىدار كۈن ئاتلاپ ئۆگىنىدىغان كارتا ۋە تەكرارلايدىغان كارتىنى يوشۇرمايدۇ. شۇڭلاشقا ئالاقىدار يېڭى كارتا ۋە تەكرارلايدىغان كارتا بىر قېتىملىق تەكرارلاشتا بىرلا ۋاقىتتا كۆرۈنۈشى مۇمكىن.

## Gather order and sort order of cards

deck-config-ordering-title = كۆرسىتىش تەرتىپى
deck-config-new-gather-priority = يېڭى كارتىنى تارتىش تەرتىپى
deck-config-new-gather-priority-tooltip-2 =
    «دەستە»: چوققىسىدىن باشلاپ، ھەر بىر دەستىدىن ئۆسكۈچى تەرتىپ بويىچە كارتىلارنى توپلايدۇ. ئەگەر تاللىغان دەستىنىڭ كۈندىلىك يۇقىرى چېكىگە يەتسە، بارلىق دەستىنى تەكشۈرمەيلا توپلاشنى توختىتىشى مۇمكىن. چوڭ توپلامغا نىسبەتەن بۇ ئۇسۇل ئەڭ تېز بولۇپ، چوققىدىكى دەستىنى ئالدىن بىر تەرەپ قىلىدۇ.
    
    «ئورنى بويىچە ئۆسكۈچى تەرتىپتە»: ئورنى بويىچە ئۆسكۈچى تەرتىپتە (due #) كارتىنى تارتىدۇ، كونا كارتا ئالدىن تارتىلىدۇ.
    
    «ئورنى بويىچە كېمەيگۈچى تەرتىپتە»: ئورنى بويىچە كېمەيگۈچى تەرتىپتە  (due #) كارتىنى تارتىدۇ، يېڭى كارتا ئالدىن تارتىلىدۇ.
    
    «خاتىرە ئىختىيارى تەرتىپتە»: ئالدى بىلەن تاللىغان خاتىرىنى ئىختىيارى تەرتىپتە تىزىدۇ، ئاندىن ئارىسىدىن كارتىنى تارتىدۇ. ئالاقىدار كارتىنى يوشۇرۇش ئىقتىدارى چەكلەنگەندە، بىر خاتىرىنىڭ بارلىق كارتىسى بىر قېتىملىق تەكرارلاشتا كۆرۈنىدۇ. (مەسىلەن، ئالدى يۈزى -› كەينى يۈزى ۋە كەينى يۈزى -› ئالدى يۈزى بىر قېتىملىق تەكرارلاشتا كۆرۈنىدۇ)
    
    «كارتا ئىختىيارى تەرتىپتە»: كارتىنى پۈتۈنلەي ئىختىيارىي تەرتىپتە تارتىپ توپلايدۇ.
deck-config-new-card-sort-order = يېڭى كارتا تەرتىپلەش تەرتىپى
deck-config-new-card-sort-order-tooltip-2 =
    «كارتا تۈرى»: كارتىنى كارتا تۈرى نومۇرى بويىچە كۆرسىتىدۇ. ئەگەر ئالاقىدار كارتىنى يوشۇرۇش ئىقتىدارى قوزغىتىلغان بولسا، بارلىق ئالدى يۈزى ← كەينى يۈزى كارتىنىڭ ئالدىنلىقى كەينى يۈزى ← ئالدى يۈزى كارتىنىڭ كۆرسىتىلىشىدىن ئىلگىرى بولىدۇ. بۇ تاللانما ئوخشاش بىر خاتىرىنىڭ كارتىسى بىر قېتىملىق تەكرارلاشقا كۆرسىتىلىدۇ ھەمدە ئارىلىقى بەك يېقىن بولۇپ كېتىشنىڭ ئالدىنى ئالىدۇ.
    
    «توپلام تەرتىپى»: كارتا توپلانغان تەرتىپتە كۆرسىتىلىدۇ. ئەگەر ئالاقىدار كارتىنى يوشۇرۇش ئىقتىدارى چەكلەنگەن بولسا، بىر خاتىرىدىكى ھەر بىر كارتا تەرتىپ بويىچە كۆرسىتىلىدۇ.
    
    «كارتا تۈرى ← ئىختىيارى تەرتىپ»:  كارتا تۈرى تەرتىپى بىلەن ئوخشاش، ئەمما ھەر بىر كارتا تۈرى نومۇرىنى ئارىلاشتۇرۇۋېتىدۇ. ئەگەر «ئورۇن ئۆسكۈچى» تەرتىپتە كونىراق كارتىنى توپلايدۇ، بۇ تاللانما ئىشلىتىلسە كارتا ئىختىيارى تەرتىپتە كۆرسىتىلىدۇ، شۇنىڭ بىلەن بىللە ئوخشاش بىر خاتىرىنىڭ كارتىسى بەك يېقىن بولۇپ قالماسلىققا كاپالەتلىك قىلىدۇ.
    
    «ئىختىيارى خاتىرە ← كارتا تۈرى»: خاتىرە ئىختىيارى تەرتىپتە تارتىلىدۇ، ئاندىن ئۇنىڭغا ئالاقىدار بارلىق كارتىلارنى  تەرتىپ بويىچە كۆرسىتىدۇ.
    
    «ئىختىيارى»: توپلىغان كارتىنىڭ ھەممىسىنى تولۇق ئارىلاشتۇرۇۋېتىدۇ.
deck-config-new-review-priority = يېڭى/تەكرارلاش تەرتىپى
deck-config-new-review-priority-tooltip = تەكرارلايدىغان كارتىغا مۇناسىۋەتلىك يېڭى كارتىنى قاچان كۆرسىتىدۇ.
deck-config-interday-step-priority = كۈن ئاتلاپ ئۆگىنىش/تەكرارلايدىغان كارتا تەرتىپى
deck-config-interday-step-priority-tooltip =
    كۈن ئاتلايدىغان قايتا ئۆگىنىۋاتقان كارتىنى قانداق كۆرسىتىدۇ.
    
    تەكرارلايدىغان كارتا چېكى ئالدى بىلەن كۈن ئاتلايدىغان كارتىغا قوللىنىلىدۇ، ئاندىن تەكرارلايدىغان كارتىغا قوللىنىلىدۇ.
    گەرچە بۇ تاللانما تارتىدىغان كارتىنىڭ كۆرسىتىش تەرتىپىنى تەڭشىيەلىسىمۇ ئەمما ھەمىشە كۈن ھالقىغان كارتىنى ئالدىن تارتىپ ئالىدۇ.
deck-config-review-sort-order = تەكرارلايدىغان كارتا تەرتىپى
deck-config-review-sort-order-tooltip = كۆڭۈلدىكى ئەھۋالدا كارتا كۈتىدىغان ۋاقىتنىڭ ئۇزۇن قىسقىلىق تەرتىپى بويىچە كارتىنى كۆرسىتىدۇ، يىغىلىپ قالغان كارتا بولسا، كۈتكەن ۋاقتى ئەڭ ئۇزۇن بولغان كارتا ئالدى بىلەن كۆرۈنىدۇ. ئەگەر بىر قانچە كۈن سەرپ قىلىپ يىغىلىپ قالغان كارتىنى بىر تەرەپ قىلىشنى خالىمىغاندا ياكى تارماق تەستە تەرتىپى بويىچە تىزىشنى ئۈمىد قىلغاندا، باشقا تەرتىپلەش ئۇسۇلىغا ئۆزگەرتىش تەۋسىيە قىلىنىدۇ.
deck-config-display-order-will-use-current-deck = تاللانغان دەستەدىكى ئۆگىنىش تەرتىپى بويىچە كۆرسىتىدۇ، Anki تارماق دەستە تەرتىپىگە پەرۋا قىلمايدۇ.

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = دەستە
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = دەستە ئاندىن خالىغان خاتىرە
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = ئۆسكۈچى تەرتىپ
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = كېمەيگۈچى تەرتىپ
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = خالىغان خاتىرە
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = خالىغان كارتا
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = كارتا تۈرى، ئاندىن خالىغانچە
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = خالىغانچە خاتىرە، ئاندىن كارتا تۈرى
# Sort the cards randomly.
deck-config-sort-order-random = خالىغانچە
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = كارتا تۈرى
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = توپلاش تەرتىپى
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = يېڭىسى بىلەن تەكرارلايدىغىنى ئارىلاش
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = ئاۋۋال تەكرارلاپ ئاندىن يېڭىنى ئۆگىنىدۇ
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = ئاۋۋال يېڭىنى ئۆگىنىپ ئاندىن تەكرارلايدۇ
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = ئاۋۋال مۆھلىتى توشقاننى ئاندىن خالىغانچە
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = ئاۋۋال مۆھلىتى توشقاننى ئاندىن دەستە
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = ئاۋۋال دەستە ئاندىن مۆھلىتى توشقاننى
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = مەزگىل ئۆسكۈچى
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = مەزگىل كېمەيگۈچى
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = ئاسانلىق ئۆسكۈچى
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = ئاسانلىق كېمەيگۈچى
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = قىيىنلىق ئۆسكۈچى
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = قىيىنلىق كېمەيگۈچى
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = ئۆسكۈچى ئەستە تۇتۇشچانلىق
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = كېڭەيگۈچى ئەستە تۇتۇشچانلىق

## Timer section

deck-config-timer-title = ۋاقىت خاتىرىلىگۈچ
deck-config-maximum-answer-secs = ئەڭ ئۇزۇن جاۋاب ۋاقتى سېكۇنت
deck-config-maximum-answer-secs-tooltip = بىر قېتىم تەكرارلىغاندا خاتىرىلىگىلى بولىدىغان ئەڭ ئۇزۇن سېكۇنت سانى. ئەگەر جاۋاب بېرىش ۋاقتى بۇ ۋاقىتتىن ئېشىپ كەتسە (چۈنكى ئېكراندىن ئايرىلىشتىن ئىلگىرى)، كارتىنىڭ جاۋاب بېرىش ۋاقتى مەزكۇر تەڭشەكتە بەلگىلەنگەن ۋاقىت بويىچە خاتىرىلىنىدۇ.
deck-config-show-answer-timer-tooltip = تەكرارلاش ئېكرانىدا ۋاقىت خاتىرىلىگۈچتىن بىرنى كۆرسىتىپ، ھەر بىر كارتىنى تەكرارلىغاندا سەرپ قىلغان ۋاقىتنى خاتىرىلەيدۇ.
deck-config-stop-timer-on-answer = جاۋابنى كۆرسەتكەندە ۋاقىت خاتىرىلىگۈچ توختايدۇ
deck-config-stop-timer-on-answer-tooltip = جاۋاب كۆرۈنگەندە ۋاقىت خاتىرىلىگۈچ توختامدۇ يوق. ئىستاتىستىكا سانلىق مەلۇماتىغا تەسىر كۆرسەتمەيدۇ.

## Auto Advance section

deck-config-seconds-to-show-question = جاۋابنى ئۆزلۈكىدىن كۆرسىتىشتىن ئىلگىرى كۈتىدىغان سېكۇنت سانى
deck-config-seconds-to-show-question-tooltip-3 = ئۆزلۈكىدىن كۆرسىتىش ئاكتىپلاشقاندا، سوئالنى كۆرسەتكەندىن كېيىن ئاپتوماتىك ئىجرا قىلىدىغان مەشغۇلاتتىن ئىلگىرى كۈتىدىغان ۋاقىت بىرلىكى سېكۇنت. 0 گە تەڭشەلسە چەكلەيدۇ.
deck-config-seconds-to-show-answer = جاۋابنى كۆرسەتكەندىن كېيىن ئاپتوماتىك مەشغۇلات قىلىشتىن ئىلگىرىكى كۈتۈش ۋاقتى
deck-config-seconds-to-show-answer-tooltip-2 = ئۆزلۈكىدىن كۆرسىتىش ئاكتىپلاشقاندا، جاۋابنى كۆرسەتكەندىن كېيىن ئاپتوماتىك ئىجرا قىلىدىغان مەشغۇلاتتىن ئىلگىرى كۈتىدىغان ۋاقىت بىرلىكى سېكۇنت. 0 گە تەڭشەلسە چەكلەيدۇ.
deck-config-question-action-show-answer = جاۋابنى كۆرسەت
deck-config-question-action-show-reminder = ئەسكەرتىشنى كۆرسەت
deck-config-question-action = سوئالدىن كېيىن مەشغۇلات
deck-config-question-action-tool-tip = سوئالنى كۆرسىتىش ۋاقتى توشقاندىن كېيىن ئۆزلۈكىدىن مەشغۇلاتنى ئىجرا قىلىدۇ.
deck-config-answer-action = جاۋابتىن كېيىن مەشغۇلات
deck-config-answer-action-tooltip-2 = جاۋابنى كۆرسىتىش ۋاقتى توشقاندىن كېيىن ئۆزلۈكىدىن مەشغۇلاتنى ئىجرا قىلىدۇ.
deck-config-wait-for-audio-tooltip-2 = سوئال ياكى جاۋاب مەشغۇلاتىنى ئۆزلۈكىدىن قوللىنىشتىن ئىلگىرى ئۈننىڭ تۈگىشىنى كۈتىدۇ.

## Audio section

deck-config-audio-title = ئۈن
deck-config-disable-autoplay = ئۈننى ئۆزلۈكىدىن قويما
deck-config-disable-autoplay-tooltip = قوزغىتىلسا Anki ئۈننى ئۆزلۈكىدىن قويمايدۇ. ئۈن قويۇش توپچىنى چېكىپ/نوقۇپ ياكى قايتا قويۇش مەشغۇلاتى ئارقىلىق ئۈننى قويغىلى بولىدۇ.
deck-config-skip-question-when-replaying = جاۋابنى تەكرارلىغاندا سوئالدىن ئاتلا
deck-config-always-include-question-audio-tooltip = ئەگەر جاۋابنى كۆرگەندە قايتا قويۇش مەشغۇلاتىن ئېلىپ بېرىلغان بولسا، سوئالنىڭ ئاۋازىنى ئۆز ئىچىگە ئالامدۇ يوق.

## Advanced section

deck-config-advanced-title = ئالىي
deck-config-maximum-interval-tooltip = تەكرارلايدىغان كارتىنىڭ ئەڭ ئۇزۇن كۈتۈش ۋاقتى كۈن. تەكرارلايدىغان كارتىنىڭ ئارىلىقى مۇشۇ كۈنگە يەتكەندە، «تەس»، «ياخشى» ۋە «ئاسان» نىڭ ئارىلىقى ئوخشاش بولىدۇ. بۇ ئارىلىق قانچە قىسقا بولسا ئىش مىقدارى شۇنچە كۆپ بولىدۇ.
deck-config-starting-ease-tooltip = يېڭى كارتىنىڭ دەسلەپكى ئاسانلىق كۆپەيگۈچىسى. كۆڭۈلدىكى تەڭشەكتە، يېڭى ئۆگەنگەن كارتىغا «ياخشى» تاللانغاندىن كېيىن، كېيىنكى قېتىم تەكرارلاش ئارىلىقى ئالدىنقى قېتىمنىڭ 2.5x ھەسسىسى بولىدۇ.
deck-config-easy-bonus-tooltip = قوشۇمچە كۆپەيگۈچى، كارتىنى تەكرارلىغاندا «ئاسان» تاللانغاندىن كېيىنكى ئارىلىقنى تەڭشەشكە قوللىنىلىدۇ.
deck-config-interval-modifier-tooltip = بۇ كۆپەيگۈچى تەكرارلايدىغان ھەممە كارتىغا قوللىنىلىدۇ، ئازراقلا تەڭشەلسە Anki كۈنتەرتىپى تېخىمۇ مۇتەئەسسىپ ياكى ئاشقۇن بولىدۇ. بۇ تاللانمىنى ئۆزگەرتىشتىن ئىلگىرى قوللانمىنى كۆرۈڭ.
deck-config-hard-interval-tooltip = «تەس» تاللانغاندىن كېيىن تەكرارلاشنىڭ مەزگىلى.
deck-config-new-interval-tooltip = «قايتا» تاللانغاندىن كېيىن تەكرارلاشنىڭ مەزگىلى.
deck-config-minimum-interval-tooltip = تەكرارلايدىغان كارتىغا «قايتا» تاللانغاندىن كېيىنكى ئەڭ قىسقا تەكرارلاش مەزگىلى.
deck-config-custom-scheduling = ئىختىيارى كۈنتەرتىپ
deck-config-custom-scheduling-tooltip = پۈتكۈل توپلامغا تەسىر كۆرسىتىدۇ، ئىشلىتىشتە ئېھتىيات قىلىڭ!

## Easy Days section.

deck-config-easy-days-title = ھەر كۈنى
deck-config-easy-days-monday = دۈشەنبە
deck-config-easy-days-tuesday = سەيشەنبە
deck-config-easy-days-wednesday = چارشەنبە
deck-config-easy-days-thursday = پەيشەنبە
deck-config-easy-days-friday = جۈمە
deck-config-easy-days-saturday = شەنبە
deck-config-easy-days-sunday = يەكشەنبە
deck-config-easy-days-normal = ئادەتتىكى
deck-config-easy-days-reduced = ئازايتىلدى
deck-config-easy-days-minimum = ئەڭ ئاز
deck-config-easy-days-no-normal-days = كەم دېگەندە بىر كۈن «{ deck-config-easy-days-normal }» دەپ تەڭشىلىشى كېرەك.
deck-config-easy-days-change = مەۋجۇت تەكرارلايدىغانلار FSRS تاللانمىسىدىكى «{ deck-config-reschedule-cards-on-change }» قوزغىتىلمىغۇچە قايتا كۈنتەرتىپلەنمەيدۇ.

## Adding/renaming

deck-config-add-group = ئالدىن تەڭشەك قوش
deck-config-name-prompt = ئاتى
deck-config-rename-group = ئالدىن تەڭشەك ئاتىنى ئۆزگەرت
deck-config-clone-group = ئالدىن تەڭشەكنى كۆچۈر

## Removing

deck-config-remove-group = ئالدىن تەڭشەكنى چىقىرىۋەت
deck-config-will-require-full-sync = بۇ مەشغۇلات يەككە يۆنىلىشتە قەدەمداشلايدۇ. ئەگەر باشقا ئۈسكۈنىدىكى ئۆزگىرىش مەزكۇر ئۈسكۈنىدە قەدەمداشلانمىغان بولسا، قەدەمداشلىغاندىن كېيىن ئاندىن ئۆزگەرتىڭ.
deck-config-confirm-remove-name = { $name } چىقىرىۋېتەمدۇ؟

## Other Buttons

deck-config-save-button = ساقلا
deck-config-save-to-all-subdecks = بارلىق تارماق دەستىگە ساقلا
deck-config-save-and-optimize = بارلىق ئالدىن تەڭشەكنى ئەلالاشتۇر
deck-config-revert-button-tooltip = بۇ تەڭشەكنى كۆڭۈلدىكى قىممەتكە ئەسلىگە قايتۇرىدۇ.

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Anki 2.1.41+ بىر تەرەپ قىلىش ئۇسۇلى
deck-config-description-new-handling-hint = markdown سۈپىتىدە كىرگۈزۈلىدۇ، كىرگۈزۈلگەن HTML تازىلىنىدۇ. قوزغىتىلغاندىن كېيىن، چۈشەندۈرۈشى مۇبارەكلەش ئېكرانىدا كۆرۈنىدۇ. Anki 2.1.40 ۋە ئۇنىڭدىن تۆۋەن نەشرىدە Markdown ساپ تېكىست شەكلىدە كۆرۈنىدۇ.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    { $cards ->
        [one] ئانا دەستىنىڭ يۇقىرى كارتا چېكى { $cards }، بۇ يۇقىرى چەك قاپلىۋېتىلىدۇ.
       *[other] ئانا دەستىنىڭ يۇقىرى كارتا چېكى { $cards }، بۇ يۇقىرى چەك قاپلىۋېتىلىدۇ.
    }
deck-config-reviews-too-low =
    { $cards ->
        [one] ئەگەر ھەر كۈنى { $cards } يېڭى كارتا قوشۇلسا، تەكرارلاش چېكىڭىز كەم دېگەندە { $expected } بولىدۇ.
       *[other] ئەگەر ھەر كۈنى { $cards } يېڭى كارتا قوشۇلسا، تەكرارلاش چېكىڭىز كەم دېگەندە { $expected } بولىدۇ.
    }
deck-config-learning-step-above-graduating-interval = ئوقۇش پۈتتۈرۈش كارتىسىنىڭ قايتا كۆرۈنۈش مەزگىلى ئەڭ ئاخىرقى قېتىملىق ئۆگىنىش باسقۇچىنىڭ ئۇزۇنلۇقىدىن كەم بولماسلىقى كېرەك.
deck-config-good-above-easy = ئاسان كارتىنىڭ قايتا كۆرۈنۈش مەزگىلى ئوقۇش پۈتتۈرۈش كارتىسىنىڭ قايتا كۆرۈنۈش ئارىلىقىدىن كەم بولماسلىقى كېرەك.
deck-config-relearning-steps-above-minimum-interval = ئەڭ قىسقا ئۇنتۇش مەزگىلى ئاخىرقى قېتىملىق قايتا ئۆگىنىش باسقۇچىدىن كەم بولماسلىقى كېرەك.
deck-config-maximum-answer-secs-above-recommended = ھەر بىر سوئالغا جاۋاب بېرىش ۋاقتى قىسقا بولغاندا، Anki تەكرارلاش كۈنتەرتىپىنى تېخىمۇ ئۈنۈملۈك ئورۇنلاشتۇرالايدۇ.
deck-config-too-short-maximum-interval = ئەڭ ئۇزۇن مەزگىلنى 6 ئايدىن قىسقا قىلىپ تەڭشەش تەۋسىيە قىلىنىدۇ.
deck-config-ignore-before-info = (تەخمىنەن) { $included }/{ $totalCards } كارتا FSRS نى ئەلالاشتۇرۇشقا ئىشلىتىلىدۇ.

## Selecting a deck

deck-config-which-deck = قايسى دەستەنىڭ تاللانمىسىنى كۆرسەتمەكچى؟

## Messages related to the FSRS scheduler

deck-config-updating-cards = كارتىنى يېڭىلاۋاتىدۇ: { $current_cards_count }/{ $total_cards_count }…
deck-config-invalid-parameters = تەمىنلەنگەن FSRS پارامېتىرى ئىناۋەتسىز. كۆڭۈلدىكى پارامېتىرنى ئىشلىتىش ئۈچۈن ئۇلار بوش قالدۇرۇلسا بولىدۇ.
deck-config-not-enough-history = تەكرارلاش تارىخ خاتىرىسى بەك ئاز، بۇ مەشغۇلاتنى ئىجرا قىلالمايدۇ.
deck-config-must-have-400-reviews =
    { $count ->
        [one] پەقەت { $count } تەكرارلاش خاتىرىسىنى تاپتى. بۇ مەشغۇلاتنى ئىجرا قىلىش ئۈچۈن كەم دېگەندە 400 تەكرارلاش خاتىرىڭىز بولۇشى كېرەك.
       *[other] پەقەت { $count } تەكرارلاش خاتىرىسىنى تاپتى. بۇ مەشغۇلاتنى ئىجرا قىلىش ئۈچۈن كەم دېگەندە 400 تەكرارلاش خاتىرىڭىز بولۇشى كېرەك.
    }
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = FSRS پارامېتىر
deck-config-compute-optimal-weights = FSRS پارامېتىرنى ئەلالاشتۇرۇش
deck-config-optimize-button = ئەلالاشتۇر
# Indicates that a given function or label, provided via the "text" variable, operates slowly.
deck-config-slow-suffix = { $text } (ئاستا)
deck-config-compute-button = ھېسابلا
deck-config-ignore-before = ئىلگىرىكى تەكرارلاش خاتىرىسىگە پەرۋا قىلما
deck-config-time-to-optimize = ئەلالاشتۇرۇلمىغىنى بىر مەزگىل بولدى - ھەممىنى ئەلالاشتۇر توپچىنى ئىشلىتىش تەۋسىيە قىلىنىدۇ.
deck-config-evaluate-button = باھالاش
deck-config-desired-retention = ئارزۇدىكى ئەستە ساقلاش نىسبىتى
deck-config-historical-retention = تارىختىكى ئەستە ساقلاش نىسبىتى
deck-config-smaller-is-better = قىممىتى قانچە كىچىك بولسا سىزنىڭ تەكرارلاش تارىخ خاتىرىڭىزگە شۇنچە ماس كېلىدىغانلىقىنى ئىپادىلەيدۇ.
deck-config-steps-too-large-for-fsrs = FSRS قوزغىتىلغاندا، 1 كۈندىن ئارتۇق ئۆگىنىش باسقۇچى ئارىلىقىنى تەڭشەش تەۋسىيە قىلىنمايدۇ.
deck-config-get-params = پارامېتىرغا ئېرىشىش
deck-config-complete = { $num }% تامام.
deck-config-iterations = تەكرارلىنىشى: { $count }…
deck-config-reschedule-cards-on-change = ئۆزگەرگەندە كارتىنى قايتا كۈنتەرتىپكە تىزىدۇ
deck-config-fsrs-tooltip =
    پۈتكۈل توپلامغا تەسىر كۆرسىتىدۇ.
    
    ئەركىن ئارىلىق تەكرار كۈنتەرتىپلىگۈچ (FSRS) ئىلگىرىكى Anki نىڭ SuperMemo 2 (SM2) كۈنتەرتىپلىگۈچنىڭ ئورنىنى ئالدى. قاچان ئۇنتۇيدىغانلىقىڭىزنى تېخىمۇ دەل جەزملەش ئارقىلىق، ئوخشاش ۋاقىت ئىچىدە تېخىمۇ كۆپ مەزمۇننى ئەستە ساقلىشىڭىزغا ياردەم بېرىدۇ. بۇ تەڭشەك بارلىق دەستىنىڭ ئالدىن تەڭشىكىگە تەسىر كۆرسىتىدۇ.
    
    ئەگەر ئىلگىرى FSRS نىڭ «ئىختىيارى كۈنتەرتىپلىگۈچ» ئىشلىتىلگەن بولسا، مەزكۇر تەڭشەكنى قوزغىتىشتىن ئىلگىرى ئىختىيارى كۈنتەرتىپ مەزمۇنىنى تازىلىۋېتىڭ.
deck-config-desired-retention-tooltip = كۆڭۈلدىكى قىممىتى 0.9 بولغان كارتىنى كۈنتەرتىپكە تىزىپ، كېيىنكى قېتىم تەكرارلىغاندا، %90 ئەستە قالدۇرۇش ئېھتىماللىقى بار. ئەگەر قىممىتىنى ئۆرلەتسىڭىز، Anki كارتىنى كۆرسىتىش چاستوتىسىنى ئۆرلىتىپ، ئەستە قالدۇرۇش ئېھتىماللىقىنى ئۆرلىتىدۇ. ئەگەر قىممىتىنى تۆۋەنلەتسىڭىز، Anki كارتىنى كۆرسىتىش چاستوتىسىنى تۆۋەنلىتىپ، ئەستە قالدۇرۇش ئېھتىماللىقى تۆۋەنلەيدۇ. ئۆسكۈچى سانلىق قىممەتكە كاپالەتلىك قىلىڭ، چۈنكى بۇنداق بولغان خىزمەت مىقدارىڭىز ئاشىدۇ؛ تۆۋەنرەك قىممەتتە كۆپرەك مەزمۇننى ئۇنۇتقىنىڭىزدا كەيپىياتىڭىز تۆۋەن بولۇشى مۇمكىن.
deck-config-desired-retention-tooltip2 = قورال ئەسكەرتىشى تەمىنلىگەن خىزمەت يۈكى قىممىتى تەخمىنىي قىممەت. تېخىمۇ يۇقىرى ئېنىقلىققا ئېرىشىشتە، تەقلىدلىگۈچنى ئىشلىتىڭ.
deck-config-historical-retention-tooltip =
    بەزى تەكرارلاش خاتىرىڭىز يوقالغاندا، FSRS بوشلۇقنى تولدۇرىدۇ. كۆڭۈلدىكى ئەھۋالدا ئىلگىرىكى كونا تەكرارىڭىزدا، %90 مەزمۇننى ئەستە تۇتتىڭىز. ئەگەر سىزنىڭ ئەستە تۇتۇش نىسبىتىڭىز %90 يۇقىرى ياكى تۆۋەن بولسا، تەڭشەكنى ئۆزگەرتىش ئارقىلىق كەم قالغان تەكرارلاش خاتىرىسىنى FSRS بىلەن مۆلچەرلىگىلى بولىدۇ.
    
    تەكرارلاش خاتىرىڭىز ئىككى سەۋەبتىن تولۇق بولماسلىقى مۇمكىن:
    1-«ئىلگىرىكى تەكرارلاش خاتىرىسىگە پەرۋا قىلما» تاللانمىسىنى ئىشلەتكەن.
    2-ئىلگىرى دىسكا بوشلۇقى تازىلىغاندا تەكرارلاش خاتىرىسىنى ئۆچۈرۈۋەتكەن ياكى ئوخشاش بولمىغان ۋاقىت ئارىلىقىدا تەكرار يۆتكەش SRS پىروگراممىسىدىن ماتېرىيال ئەكىرگەن.
    
    كېيىنكى ئىنتايىن ئاز ئۇچرايدۇ، شۇڭلاشقا ئالدىنقى تاللانمىنى ئىشلەتمىگەن بولسىڭىزلا، مەزكۇر تاللانمىنى تەڭشىشىڭىز ھاجەتسىز.
deck-config-weights-tooltip2 = FSRS پارامېتىرى كارتىنى كۈنتەرتىپكە قانداق تىزىشقا تەسىر كۆرسىتىدۇ. Anki كۆڭۈلدىكى پارامېتىرنى ئىشلىتىدۇ. تۆەندىكى تاللانما ئارقىلىق ئەلالاشتۇرۇرۇپ، بۇ ئالدىن تەڭشەكنى ئىشلەتكەن دەستەدە سىزنىڭ تەكرارلاش ئىپادىڭىز بىلەن ماسلاشتۇرىدۇ.
deck-config-reschedule-cards-on-change-tooltip =
    پۈتكۈل توپلامغا تەسىر كۆرسىتىدۇ. ساقلانمايدۇ.
    
    بۇ تاللانما سىز FSRS نى قوزغاتقان ياكى پارامېتىرنى ئەلالاشتۇرغاندا، كارتىنىڭ قەرەلى توشۇش ۋاقتىنى ئۆزگەرتىش ياكى ئۆزگەرتمەسلىكنى تىزگىنلەيدۇ. كۆڭۈلدىكى ئەھۋالدا كارتىنى قايتىدىن كۈنتەرتىپكە تىزمايدۇ: كەلگۈسىدىكى تەكرارلاش يېڭى كۈنتەرتىپلەشنى ئىشلىتىدۇ، ئەمما ئىش مىقدارىڭىزدا شۇئان ئۆزگىرىش بولمايدۇ. ئەگەر قايتىدىن كۈنتەرتىپكە تىزىش قوزغىتىلسا، كارتىنىڭ قەرەلى توشۇش ۋاقتى ئۆزگەرتىلىدۇ.
deck-config-reschedule-cards-warning =
    سىز تەڭشىگەن ئەستە ساقلاش نىسبىتىگە ئاساسەن، كۆپ مىقداردىكى كارتىنىڭ مۆھلىتى توشۇشى مۇمكىن. شۇڭلاشقا تۇنجى قېتىم SM2 دىن ئالماشتۇرغاندا بۇ تاللانمىنى قوزغىتىش تەۋسىيە قىلىنمايدۇ.
    
    بۇ تاللانما تاللانسا ھەر بىر خاتىرىگە تەكرارلاش خاتىرىسى قوشۇلىدۇ ھەمدە توپلامنىڭ سىغىمىنى ئاشۇرىدۇ، شۇڭا ئېھتىيات بىلەن ئىشلىتىڭ.
deck-config-ignore-before-tooltip-2 = ئەگەر تەڭشەلسە، FSRS پارامېتىرىنى ئەلالاشتۇرۇش بېرىلگەن چېسلادىن ئىلگىرىكى كارتا تەكرارلاش خاتىرىسىگە پەرۋا قىلمايدۇ. بۇ تاللانما باشقىلارنىڭ كۈنتەرتىپىدىكى سانلىق مەلۇماتنى ئەكىرگەندە ئىشلىتىشكە بولىدۇ ياكى ھەر قايسى جاۋاب توپچە ئۇسۇلىنى ئۆزگەرتكەندە ماس كېلىدۇ.
deck-config-compute-optimal-weights-tooltip2 =
    «ئەلالاشتۇر» توپچە چېكىلگەندە، FSRS تەكرارلاش خاتىرىڭىزنى تەھلىل قىلىپ، سىزگە ئەڭ ماس كېلىدىغان ئەستە تۇتۇش ۋە ئۆگىنىۋاتقان مەزمۇننىڭ پارامېتىرىنى ھاسىل قىلىدۇ. ئەگەر ئوبيېكتىپ جەھەتتە قىيىنلىق دەرىجىسى زور دەرىجىدە پەرقلىنىدىغان دەستىڭىز بولسا، ئۇلارغا ئوخشاش بولمىغان ئالدىن تەڭشەك ئىشلىتىشىڭىزنى تەۋسىيە قىلىمىز. چۈنكى ئاددى دەستە بىلەن مۇرەككەپ دەستەنىڭ پارامېتىرى پەرقلىق. دائىم پارامېتىرنى ئەلالاشتۇرۇشىڭىز ھاجەتسىز، بىر قانچە ئايدا بىر قېتىم ئەلالاشتۇرسىڭىزلا يېتەرلىك.
    
    كۆڭۈلدىكى ئەھۋالدا، مەزكۇر ئالدىن تەڭشەكنى ئىشلىتىدىغان دەستەنىڭ تەكرارلاش خاتىرىسىگە ئاساسەن پارامېتىر ھېسابلىنىدۇ. ئەگەر قايسى پارامېتىرنىڭ ئەلالاشتۇرۇلغان كارتىغا ئىشلىتىدىغانلىقىنى ئۆزگەرتمەكچى بولسىڭىز، پارامېتىرنى ھېسابلاشتىن ئىلگىرى ئىزدەش مەزمۇنىنى تەڭشەشنى تاللىسىڭىز بولىدۇ.
deck-config-please-save-your-changes-first = ئاۋۋال ئۆزگەرتىشىڭىزنى ساقلاڭ.
deck-config-workload-factor-change =
    مۆلچەردىكى خىزمەت مىقدارى: { $factor }x
    (%{ $previousDR } مۆلچەردىكى ئەستە تۇرۇش نىسبىتىنى سېلىشتۇرىدۇ)
deck-config-workload-factor-unchanged = قىممىتى قانچە يۇقىرى بولسا، كارتىنىڭ كۆرۈنۈش نىسبىتى شۇنچە يۇقىرى بولىدۇ.
deck-config-desired-retention-too-low = سىزنىڭ مۆلچەردىكى ئەستە قالدۇرۇش نىسبىتىڭىز بەك تۆۋەن، تەكرارلاش ئارىلىقى بەك ئۇزۇن بولۇپ كېتىشى مۇمكىن.
deck-config-desired-retention-too-high = سىزنىڭ مۆلچەردىكى ئەستە قالدۇرۇش نىسبىتىڭىز بەك يۇقىرى، تەكرارلاش ئارىلىقى بەك قىسقا بولۇپ قېلىشى مۇمكىن.
deck-config-percent-of-reviews =
    { $reviews ->
        [one] { $reviews } نىڭ تەكرارالىنىشى { $pct }%
       *[other] { $reviews } نىڭ تەكرارالىنىشى { $pct }%
    }
deck-config-percent-input = { $pct }%
# This message appears during FSRS parameter optimization.
deck-config-checking-for-improvement = ياخشىلىنىشنى تەكشۈرۈۋاتىدۇ…
deck-config-optimizing-preset = ئالدىن تەڭشەكنى ئەلالاشتۇرۇۋاتىدۇ { $current_count }/{ $total_count }…
deck-config-fsrs-must-be-enabled = ئالدى بىلەن FSRS نى قوزغىتىش كېرەك.
deck-config-fsrs-params-optimal = نۆۋەتتە FSRS نىڭ پارامېتىرلىرى ئەلالاشتۇرۇلغان.
deck-config-fsrs-params-no-reviews = تەكرارلاش خاتىرىسىنى تاپالمىدى. بارلىق ئەلالاشتۇرۇشقا تېگىشلىك دەستە (تارماق دەستىمۇ ئىچىدە) نىڭ ھەممىسى نۆۋەتتىكى ئالدىن تەڭشەكنى ئىشلىتىۋاتقانلىقىنى تەكشۈرۈپ ئاندىن قايتا سىناڭ.
deck-config-wait-for-audio = ئۈن قويۇلۇشىنى كۈتىدۇ
deck-config-show-reminder = ئەسكەرتىشنى كۆرسەت
deck-config-answer-again = جاۋابى قايتا
deck-config-answer-hard = جاۋابى تەس
deck-config-answer-good = جاۋابى ياخشى
deck-config-days-to-simulate = تەقلىد كۈن
deck-config-desired-retention-below-optimal = ئارزۇيىڭىزدىكى ئەستە ساقلاش نىسبىتى ئەڭ ياخشى ئەستە ساقلاش نىسبىتىدىن تۆۋەن، كۆپەيتىش تەۋسىيە قىلىنىدۇ.
# Description of the y axis in the FSRS simulation
# diagram (Deck options -> FSRS) showing the total number of
# cards that can be recalled or retrieved on a specific date.
deck-config-fsrs-simulator-experimental = FSRS تەقلىدلىگۈچ (تەجرىبە)
deck-config-fsrs-simulate-desired-retention-experimental = FSRS ئۈمىد قىلغان ئەستە ساقلاش نىسبىتىنى تەقلىدلىگۈچ (تەجرىبە)
deck-config-fsrs-simulate-save-preset = ئەلالاشتۇرۇلغاندىن كېيىن، تەقلىدلىگۈچنى ئىجرا قىلىشتىن ئىلگىرى دەستىڭىزنىڭ ئالدىن تەڭشىكىنى ساقلاڭ.
deck-config-fsrs-desired-retention-help-me-decide-experimental = ماڭا ياردەملىشىپ قارار قىلىدۇ (تەجرىبە)
deck-config-additional-new-cards-to-simulate = تەقلىدلەيدىغان قوشۇمچە يېڭى كارتا سانى
deck-config-simulate = تەقلىد
deck-config-clear-last-simulate = ئاخىرقى تەقلىدلەشنى تازىلا
deck-config-fsrs-simulator-radio-count = تەكرارلىقى
deck-config-advanced-settings = ئالىي تەڭشەك
deck-config-smooth-graph = سىلىق گىرافىك
deck-config-suspend-leeches = ئۈنۈمسىز كارتىنى ۋاقىتلىق توختىتىدۇ
deck-config-save-options-to-preset = ئۆزگىرىشنى ئالدىن تەڭشەككە ساقلايدۇ
deck-config-save-options-to-preset-confirm = تەقلىدلىگۈچتىكى نۆۋەتتىكى تەڭشەكنىڭ تاللانمىسىنى ھازىرقى ئالدىن تەڭشەكتىكى تاللانما بىلەن قاپلىۋېتەمدۇ يوق؟
# Radio button in the FSRS simulation diagram (Deck options -> FSRS) selecting
# to show the total number of cards that can be recalled or retrieved on a
# specific date.
deck-config-fsrs-simulator-radio-memorized = ئەستە تۇتقان
deck-config-fsrs-simulator-radio-ratio = ۋاقىت/ئەستە ساقلاش نىسبىتى
# $time here is pre-formatted e.g. "10 Seconds" 
deck-config-fsrs-simulator-ratio-tooltip = { $time } ھەر كارتىنى ئەستە ساقلاش

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.

# Checkbox
deck-config-health-check = ئەلالاشتۇرغاندا ساغلاملىق ئەھۋالىنى تەكشۈرىدۇ
# Message box showing the result of the health check
deck-config-fsrs-bad-fit-warning =
    FSRS ئەستە ساقلاش قانۇنىيىتىڭىزنى مۆلچەرلىيەلمىدى. تەكلىپ:
    
    - ئەستە ساقلاش تەس كارتىنى ۋاقىتلىق توختىتىڭ ياكى ئەسلىگە قايتۇرۇڭ.
    - جاۋاب توپچىنى ئىشلىتىشتە بىردەكلىكنى ساقلاڭ. «تەس» ئۆتكەنلىكىنى ئەمما مەغلۇپ بولغانلىقىنى بىلدۈرمەيدۇ.
    - ئاۋۋال چۈشىنىپ ئاندىن ئەستە تۇتۇڭ.
    
    ئەگەر بۇ تەكلىپكە ئەگەشسىڭىز، بىر قانچە ئايدا ئۈنۈمىنى كۆرىسىز.
# Message box showing the result of the health check
deck-config-fsrs-good-fit = FSRS ئەستە ساقلاش ھالىتىڭىزگە ياخشى ماسلاشتى.

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-unable-to-determine-desired-retention = تەۋسىيە قىلىنغان ئەڭ ئاز ئەستە ساقلاش نىسبىتىنى ھېسابلىيالمايدۇ
deck-config-predicted-minimum-recommended-retention = تەۋسىيە قىلىنغان ئەڭ تۆۋەن ئەستە ساقلاش نىسبىتى: { $num }
deck-config-compute-minimum-recommended-retention = تەۋسىيە قىلىنغان ئەڭ تۆۋەن ئەستە ساقلاش نىسبىتى
deck-config-compute-optimal-retention-tooltip4 = بۇ قورال ئەڭ قىسقا ۋاقىتتا ئەڭ كۆپ ماتېرىيال ئۆگىنەلەيدىغانلىقىنى ھېسابلاپ ئارزۇدىكى ئەستە ساقلاش نىسبىتىنى تېپىپ چىقىدۇ، ئەستە ساقلاش نىسبىتىنى قانچە قىلىپ تەڭشەشنى قارار قىلغاندا، بۇ ھېسابلانغان سانلىق قىممەتتىن پايلانغىلى بولىدۇ. ئەگەر تېخىمۇ كۆپ ۋاقىتتا تېخىمۇ يۇقىرى ئەستە ساقلاش نىسبىتىگە ئېرىشمەكچى بولسىڭىز، تېخىمۇ يۇقىرى ئەستە ساقلاش نىسبىتىنى تاللاڭ. ئەستە ساقلاش نىسبىتىنى ئەڭ تۆۋەن قىممەتتىنمۇ تۆۋەن تەڭشەش تەشەببۇس قىلىنمايدۇ، چۈنكى يۇقىرى ئۇنتۇش نىسبىتى خىزمەت ۋاقتىنى ئاشۇرۇۋېتىدۇ.
deck-config-plotted-on-x-axis = (X ئوقىدا سىزىدۇ)
deck-config-a-100-day-interval =
    { $days ->
        [one] ئەسلىدىكى 100 كۈنلۈك مەزگىل { $days } كۈنگە ئۆزگىرىدۇ.
       *[other] ئەسلىدىكى 100 كۈنلۈك مەزگىل { $days } كۈنگە ئۆزگىرىدۇ.
    }
deck-config-fsrs-simulator-y-axis-title-time = تەكرارلىغان ۋاقىت/كۈن
deck-config-fsrs-simulator-y-axis-title-count = تەكرارلىغان سانى/كۈن
deck-config-fsrs-simulator-y-axis-title-memorized = جەمئىي ئەستە ساقلىغىنى
deck-config-bury-siblings = مۇناسىۋەتلىك كارتىلارنى يوشۇر
deck-config-do-not-bury = مۇناسىۋەتلىك كارتىلارنى يوشۇرما
deck-config-bury-if-new = يېڭى كارتىلارنى يوشۇر
deck-config-bury-if-new-or-review = يېڭى كارتا ياكى تەكرارلىغان كارتىنى يوشۇر
deck-config-bury-if-new-review-or-interday = مۇناسىۋەتلىك يېڭى كارتا، تەكرارلىغان كارتا ۋە كۈن ئاتلىغان ئۆگىنىش كارتىسىنى يوشۇرىدۇ
deck-config-bury-tooltip =
    ئالاقىدا ر كارتا ئوخشاش بىر خاتىرىنىڭ باشقا كارتىسى (مەسىلەن، ئالدى يۈز ياكى ئارقا يۈز كارتا، ئوخشاش بىر بوش ئورۇننى تولدۇرۇش خاتىرىسىدىكى باشقا بوش ئورۇن تولدۇرۇش كارتىسى).
    
    بۇ تاللانما تاقالغاندىن كېيىن، بىر خاتىرىنىڭ كۆپ كارتىسى ئوخشاش بىر كۈندە كۆرۈنۈشى مۇمكىن. بۇ تاللانما قوزغىتىلغاندىن كېيىن، ئوخشاش بىر كۈندىكى ئالاقىدار كارتىلار ئۆزلۈكىدىن يوشۇرۇلىدۇ. بۇ تاللانما يەنە جاۋاب بەرگەندىن كېيىن ئالاقىدار كارتىنىڭ تۈرىنى يوشۇرۇشقا يول قويىدۇ.
    V3 كۈنتەرتىپلىگۈچ ئىشلەتكەندە، كۈن ئاتلىغان ئۆگىنىش كارتىسى يوشۇرۇلىدۇ. كۈن ئاتلىغان ئۆگىنىش كارتىسى ئۆگىنىش باسقۇچى بىرىنچى كۈنى ياكى بىر قانچە كۈن بولغان كارتىنى كۆرسىتىدۇ.
deck-config-seconds-to-show-question-tooltip = ئۆزلۈكىدىن كۆرسىتىش ئاكتىپلاشقاندا، جاۋابنى ئۆزلۈكىدىن كۆرسىتىشتىن ئىلگىرى كۈتىدىغان ۋاقىت بىرلىكى سېكۇنت. 0 گە تەڭشەلسە چەكلەيدۇ.
deck-config-answer-action-tooltip = كېيىنكى كارتىنى كۆرسىتىشتىن ئىلگىرى نۆۋەتتىكى كارتىغا ئۆزلۈكىدىن مەشغۇلات ئېلىپ بارىدۇ.
deck-config-wait-for-audio-tooltip = جاۋاب كۆرسىتىش ياكى كېيىنكى كارتىنى كۆرسىتىشتىن ئىلگىرى ئۈننىڭ قويۇلۇپ تاماملىنىشىنى كۈتىدۇ.
deck-config-ignore-before-tooltip = ئەگەر تەڭشەلسە، FSRS پارامېتىرىنى ئەلالاشتۇرۇش ۋە باھالاش بېرىلگەن چېسلادىن ئىلگىرىكى تەكرارلاش خاتىرىسىگە پەرۋا قىلمايدۇ. بۇ تاللانما باشقىلارنىڭ كۈنتەرتىپىدىكى سانلىق مەلۇماتنى ئەكىرگەندە ئىشلىتىشكە بولىدۇ ياكى ھەر قايسى جاۋاب توپچە ئۇسۇلىنى ئۆزگەرتكەندە ماس كېلىدۇ.
deck-config-compute-optimal-retention-tooltip = بۇ قورال سىزنى 0 كارتىدىن باشلىدى دەپ پەرەز قىلىدۇ ھەمدە سىز بەلگىلىگەن ۋاقىت دائىرىسىدە ئەستە ساقلىيالايدىغان ماتېرىيالنىڭ مىقدارىنى ھېسابلاشنى سىنايدۇ. مۆلچەرلىگەن ئەستە ساقلاش نىسبىتى كۆپ ھاللاردا كىرگۈزگىنىڭىزگە باغلىق بولىدۇ: ئەگەر ئۇ 0.9 بىلەن زور دەرىجىدە پەرقلەنسە، ئۇنداقتا ھەر كۈنى تەقسىملەنگەن ۋاقىت ئۆگىنىدىغان كارتىنىڭ مىقدارىغا نىسبەتەن بەك يۇقىرى ياكى بەك تۆۋەن ئىكەنلىكىنى بىلدۈرىدۇ. بۇ سانلىق مەلۇماتنى پايدىنىش سۈپىتىدە قوللانسا بولىدۇ، ئەمما ئۇنى مۆھلىتى توشىدىغان خاتىرىنىڭ ئەستە ساقلاش نىسبىتى بۆلىكىگە كۆچۈرۈش تەۋسىيە قىلىنمايدۇ.
deck-config-health-check-tooltip1 = ئەگەر FSRS ئەستە ساقلاش ھالىتىڭىزگە پەقەت ماس كەلمىگەندە ئاگاھلاندۇرۇشنى كۆرسىتىدۇ.
deck-config-health-check-tooltip2 = ساغلاملىق ئەھۋالىنى تەكشۈرۈش پەقەت نۆۋەتتىكى ئالدىن تەڭشەكنى ئەلالاشتۇرغاندىلا ئىجرا قىلىنىدۇ.
deck-config-compute-optimal-retention = كومپيۇتېر تەۋسىيە قىلغان ئەڭ تۆۋەن ئەستە ساقلاش نىسبىتى
deck-config-predicted-optimal-retention = تەۋسىيە قىلىنغان ئەڭ تۆۋەن ئەستە ساقلاش نىسبىتى: { $num }
deck-config-weights-tooltip = FSRS پارامېتىرى كارتىنى كۈنتەرتىپكە قانداق تىزىشقا تەسىر كۆرسىتىدۇ. Anki كۆڭۈلدىكى پارامېتىر قىممىتىنى ئىشلىتىدۇ. +1000 قېتىملىق تەكرارلاش توپلانغاندىن كېيىن، تۆۋەندىكى تاللانمىنى ئىشلىتىپ پارامېتىرلارنى ئەلالاشتۇرۇپ، بۇ ئالدىن تەڭشەلگەن قىممەتنى ئىشلىتىدىغان دەستەدىكى تەكرارلاش ئىپادىڭىز بىلەن ماسلاشتۇرىدۇ.
deck-config-compute-optimal-weights-tooltip =
    سىز Anki دە +1000 قېتىملىق تەكرارلاشنى تاماملىسىڭىزلا، «ئەلالاشتۇر» توپچىنى چېكىپ، تەكرارلاش خاتىرىڭىزنى تەھلىل قىلىپ، سىزگە ئەڭ ماس كېلىدىغان ئەستە تۇتۇش ۋە ئۆگىنىۋاتقان مەزمۇننىڭ پارامېتىرىنى ھاسىل قىلىدۇ. ئەگەر ئوبيېكتىپ جەھەتتە قىيىنلىق دەرىجىسى زور دەرىجىدە پەرقلىنىدىغان دەستىڭىز بولسا، ئۇلارغا ئوخشاش بولمىغان ئالدىن تەڭشەك ئىشلىتىشىڭىزنى تەۋسىيە قىلىمىز. چۈنكى ئاددى دەستە بىلەن مۇرەككەپ دەستەنىڭ پارامېتىرى پەرقلىق. دائىم پارامېتىرىنى ئەلالاشتۇرۇشىڭىز ھاجەتسىز، بىر قانچە ئايدا بىر قېتىم ئەلالاشتۇرسىڭىزلا يېتەرلىك.
    
    كۆڭۈلدىكى ئەھۋالدا، مەزكۇر ئالدىن تەڭشەكنى ئىشلىتىدىغان دەستەنىڭ تەكرارلاش خاتىرىسىگە ئاساسەن پارامېتىر ھېسابلىنىدۇ. ئەگەر قايسى پارامېتىرنىڭ ئەلالاشتۇرۇلغان كارتىغا ئىشلىتىدىغانلىقىنى ئۆزگەرتمەكچى بولسىڭىز، پارامېتىرنى ھېسابلاشتىن ئىلگىرى ئىزدەش مەزمۇنىنى تەڭشەشنى تاللىسىڭىز بولىدۇ.
deck-config-compute-optimal-retention-tooltip2 = بۇ قورال سىزنى 0 دانە ئۆگىنىپ بولغان كارتىدىن باشلىدى دەپ پەرەز قىلىپ، ئەڭ قىسقا ۋاقىتتا ئەڭ كۆپ ماتېرىيال ئۆگىنەلەيدىغانلىقىنى ھېسابلاپ ئارزۇدىكى ئەستە ساقلاش نىسبىتىنى تېپىپ چىقىدۇ، ئەستە ساقلاش نىسبىتىنى قانچە قىلىپ تەڭشەشنى قارار قىلغاندا، بۇ ھېسابلانغان سانلىق قىممەتتىن پايلانغىلى بولىدۇ. ئەگەر تېخىمۇ كۆپ ۋاقىتتا تېخىمۇ يۇقىرى ئەستە ساقلاش نىسبىتىگە ئېرىشمەكچى بولسىڭىز، تېخىمۇ يۇقىرى ئەستە ساقلاش نىسبىتىنى تاللاڭ. ئەستە ساقلاش نىسبىتىنى ئەڭ تۆۋەن قىممەتتىنمۇ تۆۋەن تەڭشەش تەشەببۇس قىلىنمايدۇ، چۈنكى يۇقىرى ئۇنتۇش نىسبىتى خىزمەت ۋاقتىنى ئاشۇرۇۋېتىدۇ پايدىسى يوق.
deck-config-compute-optimal-retention-tooltip3 = بۇ قورال سىزنى 0 دانە ئۆگىنىپ بولغان كارتىدىن باشلىدى دەپ پەرەز قىلىپ، ئەڭ قىسقا ۋاقىتتا ئەڭ كۆپ ماتېرىيال ئۆگىنەلەيدىغانلىقىنى ھېسابلاپ ئارزۇدىكى ئەستە ساقلاش نىسبىتىنى تېپىپ چىقىدۇ، ئۆگىنىش جەريانىڭىزنى ئىنچىكە تەقلىد قىلىش ئۈچۈن، بۇ ئىقتىدار +400 قېتىملىق ئۆگىنىش خاتىرىسىگە ئېھتىياجلىق. ئەستە ساقلاش نىسبىتىنى قانچە قىلىپ تەڭشەشنى قارار قىلغاندا، بۇ ھېسابلانغان سانلىق قىممەتتىن پايلانغىلى بولىدۇ. ئەگەر تېخىمۇ كۆپ ۋاقىتتا تېخىمۇ يۇقىرى ئەستە ساقلاش نىسبىتىگە ئېرىشمەكچى بولسىڭىز، تېخىمۇ يۇقىرى ئەستە ساقلاش نىسبىتىنى تاللاڭ. ئەستە ساقلاش نىسبىتىنى ئەڭ تۆۋەن قىممەتتىنمۇ تۆۋەن تەڭشەش تەشەببۇس قىلىنمايدۇ، چۈنكى يۇقىرى ئۇنتۇش نىسبىتى خىزمەت ۋاقتىنى ئاشۇرۇۋېتىدۇ پايدىسى يوق.
deck-config-seconds-to-show-question-tooltip-2 = ئۆزلۈكىدىن كۆرسىتىش ئاكتىپلاشقاندا، جاۋابنى ئۆزلۈكىدىن كۆرسىتىشتىن ئىلگىرى كۈتىدىغان ۋاقىت بىرلىكى سېكۇنت. 0 گە تەڭشەلسە چەكلەيدۇ.
deck-config-invalid-weights = پارامېتىر چوقۇم ئىنگلىزچە 17 پەش «,» بىلەن ئايرىلغان سان بولۇشى ياكى كۆڭۈلدىكى قىممەتنى ئىشلىتىش ئۈچۈن بوش قالدۇرۇلۇشى كېرەك.
deck-config-fsrs-on-all-clients = Anki خېرىدار پىروگراممىڭىزنىڭ Anki(Mobile) 23.10+ ياكى 2.17+ بولۇشىغا كاپالەتلىك قىلىڭ. ئەگەر خېرىدار پىروگراممىسى كونىراق بولسا، FSRS توغرا ئىشلىمەسلىكى مۇمكىن.
deck-config-optimize-all-tip = سىز «ساقلا» توپچىنىڭ ئوڭ تەرىپىدىكى تارتما تىزىملىكتىكى توپچەنى ئىشلىتىپ بارلىق ئالدىن تەڭشەكلەرنى ئەلالاشتۇرالايسىز.
