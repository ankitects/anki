## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount }סעק.
scheduling-answer-button-time-minutes = { $amount }מינ.
scheduling-answer-button-time-hours = { $amount }שעה
scheduling-answer-button-time-days = { $amount }טעג
scheduling-answer-button-time-months = { $amount }חד.
scheduling-answer-button-time-years = { $amount }יאָר

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] { $amount } סעקונדע
       *[other] { $amount } סעקונדעס
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] { $amount } מינוטן
       *[other] { $amount } מינוטן
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } שעה
       *[other] { $amount } שעה
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } טאָג
       *[other] { $amount } טעג
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } חודש
       *[other] { $amount } חדושים
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } יאָר
       *[other] { $amount } יאָרן
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    { $unit ->
        [seconds]
            { $amount ->
                [one] די קומעדיקע לערנענדיקע-קאַרטל וועט זײַט גרייט נאָך { $amount } סעקונד.
               *[other] די קומעדיקע לערנענדיקע-קאַרטל וועט זײַט גרייט נאָך { $amount } סעקונדן.
            }
        [minutes]
            { $amount ->
                [one] די קומעדיקע לערנענדיקע-קאַרטל וועט זײַט גרייט נאָך { $amount }  מינוט.
               *[other] די קומעדיקע לערנענדיקע-קאַרטל וועט זײַט גרייט נאָך { $amount } מינוטן.
            }
       *[hours]
            { $amount ->
                [one] די קומעדיקע לערנענדיקע-קאַרטל וועט זײַט גרייט נאָך { $amount } שעה.
               *[other] די קומעדיקע לערנענדיקע-קאַרטל וועט זײַט גרייט נאָך { $amount } שעה.
            }
    }
scheduling-learn-remaining =
    { $remaining ->
        [one] ס׳בלײַבט איין לערנענדיקע-קאַרטל וואָס קומט טערמיניק שפּעטער הײַנט.
       *[other] ס׳בלײַבן { $remaining } לערנענדיקע-קאַרטלעך וואָס קומען טערמיניק שפּעטער הײַנט.
    }
scheduling-congratulations-finished = יישר-כּוח! האָסט פֿאַרענדיקט דעם טעשל לעת-עתּה.
scheduling-today-review-limit-reached = האָסט שוין דערגרייכט דעם הײַנטיקן אײַנ׳חזר-גרענעץ, כאָטש עס בלײַבן נאָך קאַרלעך אויף וואָס זיך אײַנצו׳חזר׳ן. כּדי צו אָפּטימיזירן דאָס אויסשטודירן, איז אפֿשר כּדאי צו פֿאַרגרעסערן דעם טעגליכן גרענעץ אין די „ברירות״.
scheduling-today-new-limit-reached = ס׳בלײַבן נאָך נײַע קאַרטלעך, כאָטש דו האָסט שוין דערגרייכט דעם טעגלעכן גרענעץ. קענסט פֿאַרגרעסערן דעם גרענעץ אין די ברירות, נאָר היט זיך אַז, וואָס מערער נײַע קאַרטלעך ווערן אײַנגעפֿירט, אַלץ גרעסער וועט ווערן די קורץ-משכדיק אָנלאָדונג.
scheduling-buried-cards-found = עס זענען דאָ קאַרטלעך וואָס זענען אָפּגעהאַלטן געוואָרן, און וועט מאָרגן ווערן אַרויסגעוויזן. קענסט { $unburyThem } ווען דו ווילסט זיי זען תּיכף-ומיד.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = זיי צוריקנעמען
scheduling-how-to-custom-study = ווען דו ווילסט זיך לערנען אויסן דעם געווייטלעכן פּלאַן, קענסט { $customStudy }.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = אײַנ׳חזר׳ן צוגעפּאַסט

## Scheduler upgrade

scheduling-update-soon = „אַנקי״ 2.1 האַלט אַ נײַ פּלאַנירער, וואָס פֿאַרריכט עטלעכע ענינים פון פּריערדיקע ווערסיעס. ס׳איז כּדאי אַזוי צו דערהײַנטיקן דערצו.
scheduling-update-done = פּלאַנירער איז דערהײַנטיקט געוואָרן.
scheduling-update-button = דערהײַנטיקן
scheduling-update-later-button = שפּעטער
scheduling-update-more-info-button = לערן זיך נאָך
scheduling-update-required =
    דײַן זאַמלונג דאַרף ווערן דערהײַנטיקט ביז דער V2 פּלאַנירער. 
    ביטע קלײַב אויס { scheduling-update-more-info-button } פֿאַרן ממשיך זײַן.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = תּמיד נעמען אַרײַן פֿראַגעזײַט ווען ס׳שפּילט אוידיאָ
scheduling-at-least-one-step-is-required = ס׳מוז דאָ זײַן כאָטש אין שטאַפּל.
scheduling-automatically-play-audio = שפּילן אוידיאָ אויטאָמאַטיש
scheduling-bury-related-new-cards-until-the = אָפּהאַלטן שײַכותדיקע קאַרטלעך ביזן אַנדערן טאָג.
scheduling-bury-related-reviews-until-the-next = אָפּהאַלטן שײַכותדיקע חזר-קאַרטלעך ביזן אַנדערן טאָג.
scheduling-days = טעג
scheduling-description = באַשרײַבונג
scheduling-easy-bonus = גרינג-צוגאָב
scheduling-easy-interval = גרינג-צווישנצײַט
scheduling-end = (סוף)
scheduling-general = אַלגעמיין
scheduling-graduating-interval = גראַדויִרן-צווישנצײַט
scheduling-hard-interval = שווער-צווישנצײַט
scheduling-ignore-answer-times-longer-than = פֿאַרקוקן ענטפֿער-צײַטן לענגער פון
scheduling-interval-modifier = צווישנצײַט מאָדיפֿיצירער
scheduling-lapses = לאַפּסוסן
scheduling-lapses2 = לאַפּסוסן
scheduling-learning = לערנענדיקע
scheduling-leech-action = שנאָרער-טוּונג
scheduling-leech-threshold = שנאָרער-שוועל
scheduling-maximum-interval = מאַקסימאַל צווישנצײַט
scheduling-maximum-reviewsday = מאַקסימאַלער צאָל אײַנצו׳חזר׳ן אין איין מעת-לעת
scheduling-minimum-interval = מינימאַל צווישנצײַט
scheduling-mix-new-cards-and-reviews = צעמישן נײַע קאַרטלעך און חזר-קאַרטלעך
scheduling-new-cards = נײַע קאַרטלעך
scheduling-new-cardsday = נײַע קאַרטלעך לויט טאָג
scheduling-new-interval = נײַע צווישנצײַט
scheduling-new-options-group-name = נײַע ברירות-גרופּע נאָמען:
scheduling-options-group = ברירות-גרופּע:
scheduling-order = סדר
scheduling-parent-limit = (אָפּשטאם גרענעץ: { $val })
scheduling-reset-counts = אײַנשטעלן חשבונות פֿון איבער׳חזר׳ן און פֿאַרטונקלען אויף ס׳נײַ
scheduling-restore-position = אויפֿריכטן ערשטיקע אָרט וואו ס׳איז מעגלעך
scheduling-review = אײַנ׳חזר׳ן
scheduling-reviews = אײַנ׳חזר׳ונגען
scheduling-seconds = סעקונדעס
scheduling-set-all-decks-below-to = באַשטימען אַלע טעשלעך קלענער ווי { $val } אין אָט דער ברירה-גרופּע?
scheduling-set-for-all-subdecks = באַשטימען אויף אַלע סובטעשלעך
scheduling-show-answer-timer = ווײַזן ענטפֿער-זייגער
scheduling-show-new-cards-after-reviews = אַרויסווײַזן נײַע קאַרטלעך נאָך חזר-קאַרטלעך
scheduling-show-new-cards-before-reviews = אַרויסווײַזן נײַע קאַרטלעך פֿאַר חזר-קאַרטלעך
scheduling-show-new-cards-in-order-added = אַרויסווײַזן נײַע קאַרטלעך אינעם מוסיף-סדר
scheduling-show-new-cards-in-random-order = אַרויסווײַזן נײַע קאַרטלעך אויף טראַף
scheduling-starting-ease = גרונט-גרינגקייט
scheduling-steps-in-minutes = שטאַפּלען (אין מינוטן)
scheduling-steps-must-be-numbers = שטאַפּלען מוזן זײַן ציפֿער.
scheduling-tag-only = נאָר באַצעטלען
scheduling-the-default-configuration-cant-be-removed = מע קען נישט אויסמעקן דעם עצם-אויסשטעל
scheduling-your-changes-will-affect-multiple-decks = דײַנע איבערבײַטן גילטן אויף עטלעכע טעשלעך. ווען דו ווילסט טוישן נאָר דאָס גיייִקע טעשל, קודם שאַף אַ נײַע ברירות-גרופּע.
scheduling-deck-updated =
    { $count ->
        [one] דערהײַנטיקט { $count } טעשל.
       *[other] דערהײַנטיקט { $count } טעשלעך.
    }
scheduling-set-due-date-prompt =
    { $cards ->
        [one] אויסווײַזן קאַרטל נאָך וויפֿל טעג?
       *[other] אויסווײַזן קאַרטלעך נאָך וויפֿל טעג?
    }
scheduling-set-due-date-prompt-hint =
    0 = הײַנט
    1! = מאָרגן + טוישן צווישנצײַט אין 1
    3-7 = פון 3 ביז -7 טעג, אויסגעקליבן אויף טראַף
scheduling-set-due-date-done =
    { $cards ->
        [one] באַשטימען טערמין פון { $cards } קאַרטל.
       *[other] באַשטימען טערמין פון { $cards } קאַרטלעך.
    }
scheduling-graded-cards-done =
    { $cards ->
        [one] צייכנס געשטעלט אויף { $cards } קאַרטל.
       *[other] צייכנס געשטעלט אויף { $cards } קאַרטלעך.
    }
scheduling-forgot-cards =
    { $cards ->
        [one] אײַנשטעלן { $cards } קאַרטל אויף ס׳נײַ.
       *[other] אײַנשטעלן { $cards } קאַרטלעך אויף ס׳נײַ.
    }
