## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount } سېكۇنت
scheduling-answer-button-time-minutes = { $amount } مىنۇت
scheduling-answer-button-time-hours = { $amount } سائەت
scheduling-answer-button-time-days = { $amount } كۈن
scheduling-answer-button-time-months = { $amount } ئاي
scheduling-answer-button-time-years = { $amount } يىل

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] { $amount } سېكۇنت
       *[other] { $amount } سېكۇنت
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] { $amount } مىنۇت
       *[other] { $amount } مىنۇت
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } سائەت
       *[other] { $amount } سائەت
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } كۈن
       *[other] { $amount } كۈن
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } ئاي
       *[other] { $amount } ئاي
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } يىل
       *[other] { $amount } يىل
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    { $unit ->
        [seconds]
            { $amount ->
                [one] كېيىنكى ئۆگىنىدىغان كارتا { $amount } سېكۇنتتا تەييار بولىدۇ.
               *[other] كېيىنكى ئۆگىنىدىغان كارتا { $amount } سېكۇنتتا تەييار بولىدۇ.
            }
        [minutes]
            { $amount ->
                [one] كېيىنكى ئۆگىنىدىغان كارتا { $amount } مىنۇتتا تەييار بولىدۇ.
               *[other] كېيىنكى ئۆگىنىدىغان كارتا { $amount } مىنۇتتا تەييار بولىدۇ.
            }
       *[hours]
            { $amount ->
                [one] كېيىنكى ئۆگىنىدىغان كارتا { $amount } سائەتتە تەييار بولىدۇ.
               *[other] كېيىنكى ئۆگىنىدىغان كارتا { $amount } سائەتتە تەييار بولىدۇ.
            }
    }
scheduling-learn-remaining =
    { $remaining ->
        [one] بۈگۈن ئۆگىنىۋاتقان كارتىدىن 1 نىڭ ۋاقتى توشىدۇ.
       *[other] بۈگۈن ئۆگىنىۋاتقان كارتىدىن { $remaining } نىڭ ۋاقتى توشىدۇ.
    }
scheduling-congratulations-finished = مۇبارەك بولسۇن! ھازىرچە دەستە ئۆگىنىشنى تاماملىدىڭىز.
scheduling-today-review-limit-reached = بۈگۈنكى ئۆگىنىش يۇقىرى چېكىگە يەتتى، ئەمما يەنىلا تەكرارلايدىغان كارتا بار. ئەڭ ياخشى ئەستە قالدۇرۇش ئۈنۈمىگە يېتىش ئۈچۈن، تەڭشەكتە كۈندىلىك ئۆگىنىش يۇقىرى چېكىنى ئۆرلىتىش تەۋسىيە قىلىنىدۇ.
scheduling-today-new-limit-reached = ئۆگىنىدىغان نۇرغۇن يېڭى كارتا بار، ئەمما بۈگۈنكى ئۆگىنىش چېكىگە يەتتى. تاللانمىدىن ئۆگىنىش يۇقىرى چېكىنى ئۆرلىتىڭ، ئەمما ئۆگىنىدىغان يېڭى كارتا قانچە كۆپ بولسا، قىسقا مۇددەتتە تەكرارلايدىغان كارتا سانى شۇنچە كۆپ بولىدۇ.
scheduling-buried-cards-found = يوشۇرۇلغان بىر ياكى بىر قانچە كارتا ئەتە كۆرسىتىلىدۇ. ئەگەر دەرھال كۆرمەكچى بولسىڭىز { $unburyThem }.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = يوشۇرما
scheduling-how-to-custom-study = ئەگەر ئادەتتىكى كۈنتەرتىپنىڭ سىرتىدا ئۆگەنمەكچى بولسىڭىز، { $customStudy } ئىقتىدارىنى ئىشلىتىڭ.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = ئىختىيارى ئۆگىنىش

## Scheduler upgrade

scheduling-update-soon = Anki 2.1 دە يېڭى كۈنتەرتىپلىگۈچكە يېڭىلاندى، كونا نەشرىدىكى بەزى مەسىلىلەر ئوڭشالدى، يېڭىلاش تەۋسىيە قىلىنىدۇ.
scheduling-update-done = كۈنتەرتىپلىگۈچ مۇۋەپپەقىيەتلىك يېڭىلاندى.
scheduling-update-button = يېڭىلا
scheduling-update-later-button = كېيىن
scheduling-update-more-info-button = تەپسىلاتى
scheduling-update-required =
    توپلىمىڭىزنى V2 كۈنتەرتىپلىگۈچكە يۈكسەلتىشىڭىز كېرەك.
    داۋاملاشتۇرۇشتىن ئىلگىرى { scheduling-update-more-info-button } نى تاللاڭ.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = ئۈننى قايتا قويغاندا ھەمىشە سوئال تەرەپنى ئۆز ئىچىگە ئالىدۇ
scheduling-at-least-one-step-is-required = كەم دېگەندە بىر ئۆگىنىش باسقۇچىنى تاللاش كېرەك.
scheduling-automatically-play-audio = ئۈننى ئۆزلۈكىدىن قويىدۇ.
scheduling-bury-related-new-cards-until-the = ئەتىگىچە مۇناسىۋەتلىك يېڭى كارتىنى يوشۇرىدۇ
scheduling-bury-related-reviews-until-the-next = ئەتىگىچە مۇناسىۋەتلىك تەكرارلاشنى يوشۇرىدۇ
scheduling-days = كۈن
scheduling-description = چۈشەندۈرۈش
scheduling-easy-bonus = ئاسانلىق ھەسسىلىكى
scheduling-easy-interval = ئاسانلىق مەزگىلى
scheduling-end = (ئاخىرى)
scheduling-general = ئادەتتىكى
scheduling-graduating-interval = ئوقۇش پۈتتۈرۈش مەزگىلى
scheduling-hard-interval = قىيىن مەزگىلى
scheduling-ignore-answer-times-longer-than = جاۋاب ۋاقتى ئۇزۇن بولسا پەرۋا قىلما
scheduling-interval-modifier = مەزگىل ئۆزگەرتكۈچ
scheduling-lapses = ئۇنتۇلۇشى
scheduling-lapses2 = ئۇنتۇلۇشى
scheduling-learning = ئۆگىنىۋاتىدۇ
scheduling-leech-action = ئۇنتۇلغاق كارتا مەشغۇلاتى
scheduling-leech-threshold = ئۇنتۇلغاق كارتا بوسۇغا قىممىتى
scheduling-maximum-interval = ئەڭ ئۇزۇن مەزگىل
scheduling-maximum-reviewsday = كۈندىلىك تەكرارلاش يۇقىرى چېكى(كارتا/كۈن)
scheduling-minimum-interval = ئەڭ قىسقا مەزگىل
scheduling-mix-new-cards-and-reviews = يېڭى كارتا بىلەن تەكرارلايدىغان كارتىنى ئارىلاشتۇر
scheduling-new-cards = يېڭى كارتا
scheduling-new-cardsday = يېڭى كارتا/كۈن
scheduling-new-interval = يېڭى مەزگىل
scheduling-new-options-group-name = يېڭى تاللانما گۇرۇپپا ئىسمى:
scheduling-options-group = تاللانما گۇرۇپپا:
scheduling-order = تەرتىپ
scheduling-parent-limit = (ئانا دەستە چېكى: { $val })
scheduling-reset-counts = تەكرارلاش ۋە ئۇنتۇش سانىنى ئەسلىگە قايتۇرىدۇ
scheduling-restore-position = مۇمكىن بولسا دەسلەپكى ئورنىغا ئەسلىگە كەلتۈرىدۇ
scheduling-review = تەكرار
scheduling-reviews = تەكرارلىقى
scheduling-seconds = سېكۇنت
scheduling-set-all-decks-below-to = بۇ تاللانما گۇرۇپپىنى { $val } نىڭ ئاستىدىكى ھەممە دەستىگە تەڭشەمدۇ؟
scheduling-set-for-all-subdecks = بارلىق تارماق دەستىگە تەڭشە
scheduling-show-answer-timer = جاۋاب ۋاقىت خاتىرىلىگۈچنى كۆرسەت
scheduling-show-new-cards-after-reviews = ئاۋۋال تەكرارلايدىغان كارتىنى ئاندىن يېڭى كارتىنى كۆرسەت
scheduling-show-new-cards-before-reviews = ئاۋۋال يېڭى كارتىنى ئاندىن تەكرارلايدىغان كارتىنى كۆرسەت
scheduling-show-new-cards-in-order-added = يېڭى كارتىنى قوشۇلغان تەرتىپتە كۆرسەت
scheduling-show-new-cards-in-random-order = يېڭى كارتىنى خالىغان تەرتىپتە كۆرسەت
scheduling-starting-ease = باشلاش ئاسانلىقى
scheduling-steps-in-minutes = ئۆگىنىش باسقۇچى (مىنۇت)
scheduling-steps-must-be-numbers = ئۆگىنىش باسقۇچىنىڭ قىممىتى چوقۇم سان بولۇشى كېرەك.
scheduling-tag-only = بەلگىلا قوش
scheduling-the-default-configuration-cant-be-removed = كۆڭۈلدىكى سەپلىمىنى چىقىرىۋېتەلمەيدۇ.
scheduling-your-changes-will-affect-multiple-decks = ئۆزگەرتىشىڭىز كۆپ دەستىگە تەسىر كۆرسىتىدۇ. ئەگەر نۆۋەتتىكى دەستىنىلا ئۆزگەرتمەكچى بولسىڭىز، ئاۋۋال يېڭى تاللانما گۇرۇپپىدىن بىرنى قوشۇڭ.
scheduling-deck-updated =
    { $count ->
        [one] { $count } دەستە يېڭىلاندى.
       *[other] { $count } دەستە يېڭىلاندى.
    }
scheduling-set-due-date-prompt =
    { $cards ->
        [one] كارتىنى قانچە كۈندىن كېيىن كۆرسىتىدۇ؟
       *[other] كارتىنى قانچە كۈندىن كېيىن كۆرسىتىدۇ؟
    }
scheduling-set-due-date-prompt-hint =
    0 = بۈگۈن
    1! = ئەتە + مەزگىل ئۆزگىرىشى 1
    3-7 = خالىغانچە تاللاش 3-7 كۈن
scheduling-set-due-date-done =
    { $cards ->
        [one] { $cards } كارتىغا مۆھلەت تەڭشەلدى.
       *[other] { $cards } كارتىغا مۆھلەت تەڭشەلدى.
    }
scheduling-graded-cards-done =
    { $cards ->
        [one] { $cards } كارتا باھالاندى.
       *[other] { $cards } كارتا باھالاندى.
    }
scheduling-forgot-cards =
    { $cards ->
        [one] { $cards } كارتىنى ئەسلىگە قايتۇردى.
       *[other] { $cards } كارتىنى ئەسلىگە قايتۇردى.
    }
