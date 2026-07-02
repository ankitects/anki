### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    געניצט פון { $decks ->
        [one] { $decks } טעשל
       *[other] { $decks } טעשלעך
    }
deck-config-default-name = דורך פֿעליקייט
deck-config-title = טעשל ברירות

## Daily limits section

deck-config-daily-limits = טעגלעכע גרענעצן
deck-config-new-limit-tooltip =
    דער מאַקסימאַל ציפֿער נײַע קאַרטלעך אויף פֿאָרצושטעלן אין אַ מעת-לעת, ווען עס בלײַבן נײַע קאַרטלעך.
    וויבאַלד נײַע קאַרטלעך וועלן פֿאַרהעכערן אײַער אָנלאָדונג קורץ-משכדיק, זאָל דאָס ציפֿער געוויינטלעך זײַן כאָטש 10 מאָל קלענער ווי דײַן אײַנ׳חזר-גרענעץ.
deck-config-review-limit-tooltip = דער מאַקסימאַל נומער קאַרטלעך צו ווײַזן אויף איבערצו׳חזר׳ן אין איין מעת-לעת, ווען ס׳זענען שוין גרייט אַזעלכע קאַרטלעך.
deck-config-limit-deck-v3 =
    אַז מע חזר׳ט אײַן אַ טעשל וואָס האָט סוב-טעשלעך דערין, די אײַנגעשטעלטע גרענעצן אויף יעדער סוב-טעשל קערעווען דעם מאַקסימאַלן צאָל קאַרטלעך וואָס ווערן צוגעצויגן פֿונעם געוויסן טעשל.
    די גרענעצן פֿונעם אויסגעקליבנעם טעשל קערעווען דעם סך-הכּל קאַרטלעך וואָס ווערן באַוויזן.
deck-config-limit-new-bound-by-reviews = דער אײַנ׳חזר-גרענעץ ווירקט אויף דעם נײַעם גרענעץ. למשל, ווען אײַער נײַער גרענעץ ווערט אײַנגעשטעלט אויף 200, און ס׳בלײַבן 190 קאַרטלעך אײַנצו׳חזר׳ן, אַ מאַקסימום 10 נײַע קאַרטלעך וועלן אײַנגעפֿירט ווערן. ווען דו האָסט אים שוין דערגרייכט, מער נישט קאַרטלעך וועלן ווערן באַוויזן.
deck-config-limit-interday-bound-by-reviews = דער אײַנ׳חזר-גרענעץ ווירט אויך אויף צווישן-טעג קאַרטלעך אויף ערשט זיך צו לערנען. אַז דער גרענעץ ווערט אָנגעווענדט, צווישן-טעג קאַרטלעך וואָס מע לערנט זיך ווערן צוגעבראַכט קודם, דערנאָך חזר-קאַרטלעך.
deck-config-tab-description =
    - ׳מוסטער׳: דער גרענעץ ווערט מיטגעטיילט מיט אַלע טעשלעך וואָס ניצן אָט דעם מוסטער.
    - ׳דאָס טעשל׳: דער גרענעץ ווערט געניצט ספּעציעל פון אָט דאָס טעשל.
    - ׳נאָר הײַנט׳: בײַטן נאָר צײַטווײַליק דעם גרענעץ פון אָט דאָס טעשל.
deck-config-new-cards-ignore-review-limit = נײַע קאַרטלעך קוקן נישט אויפֿן אײַנ׳חזר-גרענעץ.
deck-config-new-cards-ignore-review-limit-tooltip = דורך פֿעליקייט, דער אײַנ׳חזר-גרענעץ ווענדט אָפּ אויך אויף נײַע קאַרטלעך, און קיין נײַ קאַרטל וועט נישט ווערן באַוויזן ווי נאָר מ׳וועט דערגרייכן דעם אײַנ׳חזר-גרענעץ. ווען מע אַקטיווירט די ברירה, נײַע קאַרטלעך וועלן ווערן באַוויזן נישט געקוקט אויף דער אײַנ׳חזר-גרענעץ.
deck-config-apply-all-parent-limits = גרענעצן הייבן אָן פון אַרויף
deck-config-apply-all-parent-limits-tooltip = דורך פֿעליקייט, גרענעצן הייבן אָן פֿונעם אויסגעקליבענעם טעשל. ווען אָט די ברירה ווערט אַקטיווירט, וועלן די גרענעצן אָנהייבן פֿונעם העכסטן טעשל, וואָס קען ווערן ניצלעך ווען דו ווילסט זיך לערנען פון געוויסע אונטער-טעשלעך, בעת׳ן אויפֿהאַלטן אַן איינציקע גענעץ פון אַלע קאַרטעך אין איין מעת-לעת.
deck-config-affects-entire-collection = ס׳איז משפּיע אויף דער גאַנצער זאַמלונג.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = מוסטער
deck-config-deck-only = דאָס טעשל
deck-config-today-only = נאָר הײַנט

## New Cards section

deck-config-learning-steps = לערן-שטאַפּלען
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = אָפּלייג-צײַטן זענען בדרך-כּלל מינוטן (צ.ב.ש. ׳1מ׳) אָדער טעג (צ.ב.ש. ׳2ט׳), אָבער שעהען (צ.ב.ש. ׳1ש׳) און סעקונדעס (צ.ב.ש. ׳30ס׳) ווערן אויך געשטיצט.
deck-config-learning-steps-tooltip =
    אײַנער אָדער נאָך אָפּלייגן, מיט בלויזן אין מיטן. דער ערשטער אָפּלייג ניצט זיך אַז מע קוועטשט ׳ווידער׳ אויף אַ נײַ קאַרטל, און איז 1 מינוט דורך פֿעליקייט. 
    דאָס ׳גוט׳ קנעפּל וועט גיין פֿאָרויס צום קומעדיקן שטאַפּל, וואָס איז 10 מינוטן דורך פֿעליקייט.
    ווי נאָר אַלע שטאַפּלען זענען פֿאַרבײַ, דאָס קאַרטל ווערט אַ חזר-קאַרטל, און וועט ווערן באַוויזן אַן אַנדערן טאָג. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip = וויפֿל טעג אויף צו וואַרטן ביז אַ קאַרטל באַווײַזט זיך ווידער, ווי נאָר דאָס ׳גוט׳ קנעפּל ווערט געקוועטשט אונעם לעצטן לערנשטאַפּל.
deck-config-easy-interval-tooltip = וויפֿל טעג אויף צו וואַרטן ביז אַ קאַרטל באַווײַזט זיך ווידער, ווי נאָר דאָס ׳גרינג׳ קנעפּל ווערט געניצט כּדי תּיכּף צוצונעמען אַ קאַרטל פֿונעם לערן-סדר.
deck-config-new-insertion-order = אַרײַנלייג-סדר
deck-config-new-insertion-order-tooltip =
    ס׳באַשטימט דאָס אָרט (טערמין #) וואו נײַע קאַרטלעך ווערן צוגעלייגט.
    קאַרטעך מיט אַ קלענערן ציפֿער ווערן ערשט באַוויזן בשעת אײַנ׳חזר׳ן. דאָס בײַטן אָט די ברירה וועט דערהײַנטיקן אויטאָמאַטיש דאָס יעצטיקע אָרט פון נײַע קאַרטלעך.
deck-config-new-insertion-order-sequential = נאָכאַנאַנדיק (עלטסטע קאַרטלעך קודם)
deck-config-new-insertion-order-random = אויף טראַף
deck-config-new-insertion-order-random-with-v3 = מיטן V3 פּלאַנירער, ס׳איז בעסער, מע זאָל דאָס לאָזן ״נאָכאַנאַנדיק״ און צופּאַסן די זאַמל-סדר פון נײַע קאַרטלעך.

## Lapses section

deck-config-relearning-steps = קריקלערן-שטאַפּלען
deck-config-relearning-steps-tooltip = נול אָדער מער אָפּלייגן, אָפּגעטיילט פֿון בלויזן. דורך פֿעליקייט, דאָס דריקן דעם `ווידער` קנעפּל אויף אַ חזר-קאַרטל וועט עס ווײַזן אויף ס׳נײַ אין 10 מינוט אַרום. אויב קיין אָפּלייג ווערט נישט צוגעשטעלט, די צווישנצײַט פֿונעם קאַרטל וועט ווערן איבערגעטוישט, אָן דעם אָנפֿאַנגען מיטן ׳קריקלערנען זיך׳ { -deck-config-delay-hint }.
deck-config-leech-threshold-tooltip = די צאָל מאָל ׳ווידער׳ מוז ווערן געדריקט אויף אַ חזר-קאַרטל פֿאַר ס׳ווערט פֿאַרציינט אַ ״שנאָרער״. שנאָרערס זענען קאַרטלעך וואָס פֿאַרניצן אַ סך צײַט, און אַז אַ קאַרטל ווערט פֿאַרצייכנט אַ שנאָרער, ס׳איז כּדאי עס איבערצושרײַבן, אויסצומעקן אָדער אויסטראַכטן אַ געדענק-מיטל (מנעמאָניק) כּדי עס זיך צו קענען דערמאָנען.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    ׳נאָר באַצעטלען׳: צושטעלן אַ 'שנאָרער' צעטל אינעם נאָטיץ, און באַווײַזן אַ פּאָפּ-אָפּ.
    
    ׳אָפּשטעלן קאַרטל׳: נאָך דערצו דאָס באַצעטלען די נאָטיץ, דאָס קאַרטל וועט ווערן באַהאַלטן ביז מע נעמט אים צוריק בידים.

## Burying section

deck-config-bury-title = אָפּהאַלטן
deck-config-bury-new-siblings = אָפּהאַלטן נײַע געשוויסטער
deck-config-bury-review-siblings = אָפּהאַלטן חזר-געשוויסטער
deck-config-bury-interday-learning-siblings = אָפּהאַלטן צווישטן-טעג לערן-געשוויסטער
deck-config-bury-new-tooltip = צי אַנדערע ׳נײַע קאַרטלעך׳ פֿון דער זעלבער נאָטיץ (וו.צ.ב. פֿאַרקערטע קאַרטלעך, שכנותדיקע שפּאַלט-אויסמעקונגען) זאָלן ווערן אָפּגעלייגט ביז׳ן קומעדיקן טאָג.
deck-config-bury-review-tooltip = צי אַנדערע ׳חזר-קאַרטלעך׳ פֿון דער זעלבער נאָטיץ זאָלן ווערן אָפּגעלייגט ביז׳ן קומעדיקן טאָג.
deck-config-bury-interday-learning-tooltip = צי אַנדערע ׳חזר-קאַרטלעך׳ פֿון דער זעלבער נאָטיץ מיט צווישנצײַטן > 1 טאָג זאָלן ווערן אָפּגעלייגט ביז׳ן קומעדיקן טאָג.
deck-config-bury-priority-tooltip =
    ווען אַנקי זאַמלט קאַטלעך צום אָנהייב, ס׳זאַמלט ערשט אינטראַ-טאָג לערן-קאַרטלעך, דערנאָך צווישן-טעג לענרן-קאַרטלעך, דערנאָך חזר-קאַרטלעך, לסוף נײַע קאַרטלעך. דאָס ווירקט אויף וויאַזוי דאָס אָפּהאַלטן זאָל אַרבעטן:
    
    - מיט אַלע אָפּהאַלט-ברירות אַקטיווירט, דער געשוויסטער וואָס קומט ערשנטס אין דער רשימה וועט זיך באַווײַזן פֿאַר אַ נײַ קאַרטל
    - געשוויסטער וואָס שפּעטער אין דער רשימה קענען נישט אָפּהאַלטן פֿריערדיקע קאַרטלגרופּעס. למשל, אויב מע מאַכט בטל דאָס אָפּהאַלטן נײַע קאַרטלעך, אַז מע חזר׳ט זיך אײַן אַ נײַע קאַרטלן, ס׳וועט נישט אָפּהאַלטן קיין צווישן-טעג אָדער חזר-קאַרטלעך, און מע קען זען סײַ אַ חזר-געשוויסטער סײַ אַ נײַ געשוויסטער קאַרטל אין דער זעלביקער זיצונג.

## Gather order and sort order of cards

deck-config-ordering-title = ווײַז-סדר
deck-config-new-gather-priority = נײַ-קאַרטל זאַמלונג-סדר
deck-config-new-gather-priority-tooltip-2 =
    ׳טעשל׳: עס זאַמלען זיך קאַרטלעך פֿון יעדן טעשל לויטן סדר, אָנהייבנדיק פֿון אַרויף. קאַרטלעך פֿון יעדן טעשל זאַמלען זיך באַרג-אַרויף. אַז מע דערגרייכט נישט דעם טעגליכן גרענעץ פֿונעם אויסגעקליבנעם טעשל דאָס זאַמלען קענען זיך אויפֿהערן פֿאַר יעדער טעשל האָט זיך איבערגעקוקט. אָט דער סדר איז דער שנעלסטער אין גרויסע זאַמלונגען, און ס׳לערלויבט, אַז סוב-טעשלעך וואָס נעענסטער צו אויבן זאָלן ווערן פּריאָריטיזירט.
    
    ׳באַרג-אַרויף׳: עס זאַמלען זיך קאַרטלעך באַרג-אַרויף (# טערמיניק), וואָס הייסט, געוויינטלעך, אַז די וואָס זענען ערשט צוגעלייגט געוואָרן קומען ערשטנס.
    
    ׳באַרג-אַראָפּ׳: עס זאַמלען זיך קאַרטלעך באַרג-אַראָפּ (# טערמיניק), וואָס הייסט, געוויינטלעך, אַז די וואָס זענען צום שפּעטסטן צוגעלייגט געוואָרן קומען ערשטנס.
    
    ׳נאָטיצן אויף טראַף׳: עס זאַמלען זיך נאָטיצן געקליבן סתּם אַזוי. ווען ׳אפּהאַלטן געשוויסטער׳ גילט נישט, עס דערלויבט, אַז אַלע קאַרטלעך פֿון אַ נאָטיץ זאָלן זיך זען אין אַ סעסיע (וו.צ.ב סײַ פֿאָרנט←הינטן און אַ הינטן←פֿאָרנט קאַרטלעך).
    
    ׳קאַרטלעך אויף טאַרף׳: ס׳ זאַמלען זיך קאַרטלעך אין גאַנצן אויף טראַף.
deck-config-new-card-sort-order = נײַ-קאַרטל סאָרטיר-סדר
deck-config-new-card-sort-order-tooltip-2 =
    ׳קאַרטלגרופּע׳: ס׳ווײַזן זיך קאַרטלעך לויטן סדר פֿון קאַרטלגרופּע. ווען ׳אָפּהאַלטן געשוויסטער׳ גילט נישט, אָט דאָס וועט פֿאַרזיכערן אַז אַלע פֿאָרנט←הינטן קאַרטלעך זאָלן זיך זען פֿאַר יעדער הינטן←פֿאָרנט קאַרטל. דאָס איז ניצלעך כּדי זיך צו באַווײַזן אַלע קאַרטלעך פֿון איין נאָטיץ אין דער זעלבער סעסיע, נאָר נישט צו נאָענט איינער פֿונעם צווייטן.
    
    ׳סדר פֿאַרזאַמלט׳: ס׳ווײַזן זיך קאַרטלעך פּונקט ווי זיי זענען געזאַמלט געוואָרן. ווען ׳אָפּהאַלטן געשוויסטער׳ גילט נישט, אָט דאָס דערפֿירט געוויינטלעך, אַז אַלע קאַרטלעך פֿון איין נאָטיץ זאָלן זיך לאָזן זען איינער נאָכ׳ן צוייטן.
    
    ׳קאַרטלנאָטיץ, דערנאָך אויף טראַף׳: אַזוי ווי ׳קאַרטלנאָטיץ׳, אָבער ס׳מישן זיך די קאַרטלעך פֿון יעדער נומער קאַרטלגרופּע. אויב מע ניצט ׳באַרג-אַרויף׳ כּדי צו זאַמלען די עלטסטע קאַרטלעך, וואָלטסט געקענט ניצן די ברירה כּדי צו זען יענע קאַרטלעך אויף טראָף, נאָר נאָך אַלץ מיט׳ן פֿאַרזיכערן אַז קאַרטלעך פֿון דער זעלבער נאָטיץ זאָלן זיך נישט געפֿונען צו נאָענט איינער פֿונעם צווייטן.
    
    ׳נאָטיץ אויף טראַף, דערנאָך קאַרטלגרופּע׳: ס׳קלײַבן אויס נאָטיצן אויף טראַף, דערנאָך, ס׳ווײַזן זיך אַלע געשוויסטער דערפֿון לויט׳ן סדר.
    
    ׳אויף טראַף׳: ס׳צעמישן זיך אין גאַנצן די פֿאַרזאַמלטע קאַרטלעך.
deck-config-new-review-priority = נײַ/חזר-סדר
deck-config-new-review-priority-tooltip = ווען נײַע קאַרטלעך זאָלן זיך ווײַזן לגבי חזר-קאַרטלעך
deck-config-interday-step-priority = צווישטן-טעג לערן-/איבערחזר-סדר
deck-config-interday-step-priority-tooltip =
    ווען ס׳זאָלן זיך ווײַזן (קריק)לערן-קאַרטלעך וואָס גיין איבער אַ גרענעץ פֿון אַ מעת-לעת.
    
    דער חזר-גרענעץ גילט שטענדיק ערשט אויף צווישן-טעג לערן-קאַרטלעך, דערנאָך חזר-קאַרטלעך. די ברירה וועט באַשטימען דעם סדר וואָס פֿון אים ווערן די פֿאַרזאַמלטע קאַרטלעך באַוויזן, אָבער צווישן-טעג לערן-קאַרטלעך וועלן זיך תּמיד פֿאַרזאַמלען קודם.
deck-config-review-sort-order = סאָרטיר-סדר פֿון אײַנ׳חזר׳ן
deck-config-review-sort-order-tooltip = דער סדר דורך פֿעליקייט פּריאָריטיזירט קאַרטלעך וואָס וואַרטן שוין צום לאַנגסטן, אַזוי ווי, ווען ס׳איז דאָ אַ זאַפּאַס אָנגעזאַמלטע חזר-קאַרטלעך, די וואָס זענען שוין גרייט צום לאַנגסטן זאָלן זיך באַווײַזן קודם. אויב ס׳איז דאָ אַ גרעסער צאָל אָנגעזאַלטע וואָס ס׳וועט דויערן מער ווי אַ פּאָר טעג די צו פֿאַרענדיקן, אויב דו ווילסט זען קאַרטלעך אין אַ סדר פֿון סוב-טעשל, קען זײַן, אַ דו וועסט גיכער ניצן אַלטערנאַטיווע סאָרטיר-סדרים.
deck-config-display-order-will-use-current-deck = „אַנקי״ וועט ניצן דעם סאָרטיר-סדר פֿונעם אויסגעקליבענעם טעשל, אַנשטאָט פֿון אַ סוב-טעשל, ווען ס׳איז דאָ איינס.

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = טעשל
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = טעשל, דערנאָך נאָטיצן אויף טראַף
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = באַרג-אַרויף
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = באַרג-אַראָפּ
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = נאָטיצן אויף טראַף
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = קאַרטלעך אויף טראַף
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = קאַרטלגרופּע, דערנאָך אויף טראַף
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = נאָטיץ אויף טראַף, דערנאָך קאַרטלגרופּע
# Sort the cards randomly.
deck-config-sort-order-random = אויף טראַף
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = קאַרטלגרופּע
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = סדר פֿאַרזאַמלט
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = צעמישן מיט חזר-קאַרטלעך
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = ווײַזן נאָך חזר-קאַרטלעך
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = ווײַזן פֿאַר חזר-קאַרטלעך
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = טערמין, דערנאָך אויף טראַף
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = טערמין, דערנאָך טעשל
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = טעשל, דערנאָך טערמין
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = צווישנצײַט באַרג-אַרויף
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = צווישנצײַט באַרג-אַראָפּ
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = גרינגקייט באַרג-אַרויף
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = גרינגקייט באַרג-אַראָפּ
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = שוועריקייט באַרג-אַרויף
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = שוועריקייט באַרג-אַראָפּ
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = דערמאָנענקייט באַרג-אַרויף
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = דערמאָנענקייט באַרג-אַראָפּ

## Timer section

deck-config-timer-title = זייגער
deck-config-maximum-answer-secs = מאַקסימאַלע ענטפֿער-סעקונדעס
deck-config-maximum-answer-secs-tooltip = דער גרעסטער צאָל סעקונדעס וואָס קענען פֿאַרשריבן ווערן פֿון אַן איינציקער אײַנ׳חזר׳ונג. ווען אַן ענטפֿער ווערט גרעסער פֿון דעם משך (למשל, אַז מע גייט אַוועק פֿון קאָמפּיוטער), דער משך וועט זיך פֿאַרשרײַבן אַלס דער באַשטימטער גרענעץ.
deck-config-show-answer-timer-tooltip = אינעם חזר-עקראַן, ווײַזן אַ זייגער וואָס ציילט די צאָל סעקונדעס אויף אײַנצו׳חזר׳ן יעדער קאַרטל.
deck-config-stop-timer-on-answer = אָפּשטעלן זייגער בײַם ענטפֿערן
deck-config-stop-timer-on-answer-tooltip =
    צי דער זייגער זאָל זיך אָפּשטעלן ווען די ענטפֿער באַווײַזט זיך.
    ס׳האָט נישט קיין השפּעה אויף דער סטאַטיסטיק.

## Auto Advance section

deck-config-seconds-to-show-question = סעקונדעס אויף צו ווײַזן פֿראַגע
deck-config-seconds-to-show-question-tooltip-3 = ווען אויטאָ-פֿאָרויסגיין איז חל, די ציפֿער סעקונדעס אויף צו וואַרטן ביז׳ן ניצן די פֿראַגע טוּונג. כּדי בטל צו מאַכן, שטעלן ׳0׳.
deck-config-seconds-to-show-answer = סעקונדעס אויף צו ווײַזן ענטפֿער
deck-config-seconds-to-show-answer-tooltip-2 = ווען אויטאָ-פֿאָרויסגיין איז חל, די ציפֿער סעקונדעס אויף צו וואַרטן ביז׳ן ניצן די ענטפֿער טוּונג. כּדי בטל צו מאַכן, שטעלן ׳0׳.
deck-config-question-action-show-answer = באַווײַזן ענטפֿער
deck-config-question-action-show-reminder = באַווײַזן דערמאָנונג
deck-config-question-action = פֿראַגע טוּונג
deck-config-question-action-tool-tip = דו טוּונג וואָס פֿירט זיך אויס נאָכן באַווײַזן אַ פֿראַגע, און זמן איז שוין פֿאַרבײַגעגאַנגען.
deck-config-answer-action = ענטפֿער טוּונג
deck-config-answer-action-tooltip-2 = דו טוּונג וואָס פֿירט זיך אויס נאָכן באַווײַזן אַן ענטפֿער, און זמן איז שוין פֿאַרבײַגעגאַנגען.
deck-config-wait-for-audio-tooltip-2 = וואַרט ביז דער אוידיאָ זאָל זיך פֿאַרענדיקן פֿאַר׳ן דורכפֿירן די פֿראַגע-טוּונג אָדער ענטפֿער-טוּונג.

## Audio section

deck-config-audio-title = אוידיאָ
deck-config-disable-autoplay = שפּילן נישט דעם אוידיאָ אויטאָמאַטיש
deck-config-disable-autoplay-tooltip =
    וועל ס׳איז חל, אנקי וועט נישט שפּילן קיין אוידיאָ אויטאָמאַטיש.
    מע קען אים שפּילן בידים מיט׳ן קליקן/דריקן אַן אוידיאָ בילדל, אָדער מיטן ניצן די ״איבערשפּילן״ טוּונג.
deck-config-skip-question-when-replaying = איבערהיפּן פֿראַגע בעת׳ן איבערשפּילן ענטפֿער
deck-config-always-include-question-audio-tooltip = צי דער פֿראַגע-אוידיאָ זאָל זיך אַרײַננעמען ווען די איבערשפּילן טוּונג ווערט געניצט בעת׳ן קוקן די ענטפֿער-זײַט פֿון אַ קאַרטל.

## Advanced section

deck-config-advanced-title = ווײַטער
deck-config-maximum-interval-tooltip = דער מאַקסומאַלער צאָל טעג וואָס מע זאָל וואַרטן אויף איבער׳חזר׳ן אַ קאַרטל. אַז מ׳האָט שוין דערגרייכט דעם חזר-גרענעץ, וועלן ׳שווער׳, ׳גוט׳, און ׳גרינג׳ אַלע דערפֿירן צו די זעלבע אָפּלייג-צײַט. וואָס קירצער דער מאַקסימום, אַלץ גרעסער וועט זײַן די אָנלאָדונג.
deck-config-starting-ease-tooltip = דער גרינגהייט-כּפֿלער (פֿאַקטאָר) וואָס מיט אים הייבן זיך אָן נײַע קאַרטלעך. דורך פֿעליקייט, דער ׳גוט׳ קנעפּל אויף אַ נײַ-געלערנט קאַרטל וועט אָפּלייגן די קומעדיקע איבער׳חזר׳ונג לויט 2.5 מאָל פֿון דער פֿריערדיקער אָפּלייגונג.
deck-config-easy-bonus-tooltip = אַן איבעריקער כּפֿלער (פֿאַקטאָר) וואָס ווערט כּפֿלעט אויף דער צווישנצײַט פֿון אַ חזר-קאַרטל אַז מע שאַצט עס אָפּ ווי ׳גרינג׳.
deck-config-interval-modifier-tooltip = אָט דער כּפֿלער ווערט כּפֿלעט אויף אַלע איבער׳חזר׳ונגען, און מע קען ניצן קליינע צופּאַסונגען כּדי צו מאַכן „אַנקי״ מער קאָנסערוואַטיוו אָדער אַגרעסיוו מיטן פּלאַנירן. ביטע גיב אַ קוק איבער דאָס האַנטביכל פֿאַרן בײַטן די ברירה.
deck-config-hard-interval-tooltip = דער כּפֿלער וואָס ווערט כּפֿלעט אויף אַ חזר-צווישנצײַט, אַז מע ענטפֿערט ׳שווער׳.
deck-config-new-interval-tooltip = דער כּפֿלער וואָס ווערט כּפֿלעט אויף אַ חזר-צווישנצײַט, אַז מע ענטפֿערט ׳ווידער׳.
deck-config-minimum-interval-tooltip = די מינימאַלע צווישנצײַט וואָס ווערט געגעבן אַ חזר-קאַרטל נאָכ׳ן ענטפֿערן ׳ווידער׳.
deck-config-custom-scheduling = צופּאַסן פּלאַנירונג
deck-config-custom-scheduling-tooltip = ס׳גילט אויף דער גאַנצער זאַמלונג. היט זיך, אויפֿן אייגענעם אחריות!

## Easy Days section.

deck-config-easy-days-title = גרינגע טעג
deck-config-easy-days-monday = מאָנטיק
deck-config-easy-days-tuesday = דינסטיק
deck-config-easy-days-wednesday = מיטוואָך
deck-config-easy-days-thursday = דאָנערשטיק
deck-config-easy-days-friday = פֿרײַטיק
deck-config-easy-days-saturday = שבת
deck-config-easy-days-sunday = זונטיק
deck-config-easy-days-normal = נאָרמאַל
deck-config-easy-days-reduced = רעדוצירט
deck-config-easy-days-minimum = מינימום
deck-config-easy-days-no-normal-days = כאָטש איין טאָג זאָל מען אײַנשטעלן אויף '{ deck-config-easy-days-normal }'
deck-config-easy-days-change = טעשלעך וואָס זענען שוין פֿאַראַן וועלן נישט ווערט אָפּגעלייגט סײַדן מ׳האָט אַקטיווירט '{ deck-config-reschedule-cards-on-change }' אין די FSRS ברירות.

## Adding/renaming

deck-config-add-group = צולייגן מוסטער
deck-config-name-prompt = נאָמען
deck-config-rename-group = טוישן מוסטער-נאָמען
deck-config-clone-group = דופּליקירן מוסטער

## Removing

deck-config-remove-group = אויסמעקן מוסטער
deck-config-will-require-full-sync = די געבעטנע בײַט וועט דאַרפֿן אַן אַהינ-סינך. ווען איר האָט שוין געמאַכט בײַטן אויף אַן אַנדער מכשיר, און זיי נאָך נישט סינכראָניזירט אין אָט דעם מכשיר, ביטע סינכראָניזירט זיי פֿאַרן ממשיך זײַן.
deck-config-confirm-remove-name = אויסמעקן { $name }?

## Other Buttons

deck-config-save-button = אויפֿהיטן
deck-config-save-to-all-subdecks = אויפֿהיטן אין אַלע סוב-טעשלעך
deck-config-save-and-optimize = אָפּטימיזירן אַלע מוסטערס
deck-config-revert-button-tooltip = צוריקציִען די פֿיקסירונג אין ווי ס׳איז געווען דורך פֿעליקייט.

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = אַנקי 2.1.41+ האַלטונג
deck-config-description-new-handling-hint =
    ס׳באַהאַנדלט אַרײַנוואַרג ווי „מאַרקדאַון״, און רייניקט עס אָפּ HTML אַרײַנוואַרג.
    ווען ס׳איז אַקטיוו, די באַשרײַבונג ווערט אויך באַוויזן אויף די „מזל טוב״ עקראַן.
    מאַרקדאָון ווײַזט זיך ווי טעקסט אין „אַנקי״ 2.1.40 און עלטער.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    { $cards ->
        [one] אַ מוטער-טעשל האָט אַ גרענעץ פֿון { $cards } קאַרטלעך, וואָס וועט בטל מאַכן אָט דעם גרענעץ.
       *[other] אַ מוטער-טעשל האָט אַ גרענעץ פֿון { $cards } קאַרטל, וואָס וועט בטל מאַכן אָט דעם גרענעץ.
    }
deck-config-reviews-too-low =
    { $cards ->
        [one] אַז מע שטעלט צו { $cards } נײַ קאַרטל יעדן טאָג, זאָל דער חזר-גרענעץ זײַן לכל־⁠הפּחות { $expected }.
       *[other] אַז מע שטעלט צו { $cards } נײַע קאַרטלעך יעדן טאָג, זאָל דער חזר-גרענעץ זײַן לכל־⁠הפּחות { $expected }.
    }
deck-config-learning-step-above-graduating-interval = די גראַדויִרן-צווישנצײַט זאָל זײַן לכל-הפּחוט אַזוי לאַנג ווי דעם לעצטן לערן-שטאַפּל.
deck-config-good-above-easy = די גרינג-צווישנצײַט זאָל נײַן לכל-הפּחוט אַזוי לאַנג ווי די גראַדויִרן-צווישנצײַט.
deck-config-relearning-steps-above-minimum-interval = די מינימאַלע פֿאַרטונקל-צווישנצײַט זאָל זײַן לכל-הפּחוט אַזוי לאַנג ווי דעם לעצטן קריקלער-שטאַפּל.
deck-config-maximum-answer-secs-above-recommended = אַנקי קען פּלאַנירן עפֿעקטיווער איבער׳חזר׳ונגען, ווען יעדע פֿראַגע איז אַ קורצע.
deck-config-too-short-maximum-interval = ס׳איז כּדאי, די מאַקסימאַל צווישנצײַט זאָל נישט זײַן ווייניק פון 6 מאָנאַטן.
deck-config-ignore-before-info = (בערך) { $included }/{ $totalCards } קאַרטלעך וועלן ווערן געניצט כּדי צו אָפּטימיזירן די FSRS פּאַראַמעטערס.

## Selecting a deck

deck-config-which-deck = פֿון וועלכען טעשל ווילסטו ווײַזן די ברירות?

## Messages related to the FSRS scheduler

deck-config-updating-cards = דערהײַנטיקנדיק קאַרטלעך: { $current_cards_count }/{ $total_cards_count }…
deck-config-invalid-parameters = זי צוגעשטעלטע FSRS פּאַראַמעטערס זענען פּסול. לאָז זיי ליידיק כּדי צו ניצן די פּאַראַמעטערס דורך פֿעליקייט.
deck-config-not-enough-history = דער אײַנ׳חזר-זכּרון קלעקט נישט אויסצופֿירן די פֿונקציע.
deck-config-must-have-400-reviews =
    { $count ->
        [one] נאָר { $count } איבער׳חזר׳ונג האָט מען געפֿונען. מע דאַרף כאָטש 400 איבער׳חזר׳ונגען אויף אויסצופֿירן די פֿונקציע.
       *[other] נאָר { $count } איבער׳חזר׳ונגען האָט מען געפֿונען. מע דאַרף כאָטש 400 איבער׳חזר׳ונגען אויף אויסצופֿירן די פֿונקציע.
    }
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = FSRS פּאַראַמעטערס
deck-config-compute-optimal-weights = אָפּטימיזירן FSRS פּאַראַמעטערס
deck-config-optimize-button = אָפּטימיזירן
# Indicates that a given function or label, provided via the "text" variable, operates slowly.
deck-config-slow-suffix = { $text } (פּאַמעלעך)
deck-config-compute-button = אויסרעכענען
deck-config-ignore-before = איגנאָרירן קאַרטלעך וואָס פֿריער איבערגע׳חזר׳ט
deck-config-time-to-optimize = שוין לאַנג זיך נישט געזען - ס׳איז כּדאי צו „אָפּטימיזירן אַלץ״.
deck-config-evaluate-button = אָפּשאַצן
deck-config-desired-retention = געוווּנטשענער זכּרון
deck-config-historical-retention = היסטאָריש זכּרון
deck-config-smaller-is-better = קלענערע צאָלן ווײַזן אָן די וואָס זענען בעסער צוגעפּאַסט אויף דער אײַנ׳חזר-געשיכטע.
deck-config-steps-too-large-for-fsrs = ווען FSRS גילט, שטאַפּלען 1 טאָג אָדער מער זענען נישט כּדאי.
deck-config-get-params = אָפּטראָגן פּאַראַמס
deck-config-complete = { $num }% פֿאַרענדיקט
deck-config-iterations = גילגול: { $count }…
deck-config-reschedule-cards-on-change = אָפּלייגן קאַרטלעך אויפֿן טוישן
deck-config-fsrs-tooltip =
    ס׳איז חל איבער דער גאַנצער זאַמלונג.
    
    דער „פֿרײַ גערוקטער איבער׳חזר-פּלאַנירער״ (FSRS) איז אַן אַלטערנאַטיוו פֿאר אַנקי׳ס אַמאָליקער "SuperMemo 2" (SM-2) אַלגאָריטעם.
    מיטן גלײַכער באַשטימען דאָס מעגלעכקייט פֿונעם פֿאַרגעסן אַ קאַרטל, ס׳קען דיך העלפֿן אויסלערנען מער לערנוואַרג אינעם זעלבן משך. די אָ פֿיקסירונג ווערט געניצט בשותּפֿותדיק איבער אַלע מוסטערן.
deck-config-desired-retention-tooltip = דורך פֿעליקייט ווערן קאַרטלעך פּלאַנירט כּדי ס׳איז דאָ אַ 90% שאַנס אַ דו וועסט זיך דערמאָנען אַ קאַרטל ווען עס ווערט ווידער איבערגע׳חזר׳ט. וואָס גערסער דער צאָל, אַלץ אָפֿטער ווערן קאַרטלעך אָנגעוויזן, כּדי מע זאָל זיי זיך אַלץ מעגלעכער דערמאָנען. וואָס קלענער דער צאָל, אַלץ זעלטענער ווערן קאַרטלעך אָנגעוויזן, הייסט עס אַ מער פֿון זיי ווערן אָפֿטער פֿאַרגעסן. גיב אַכטונג מיטן דאָס צופּאַסן: העכערע צאָלן וועלן ממש פֿאַרגרעסערן דײַן אָנלאָדונג, קענען דאָך פֿאָרט קלענערע צאָלן ווערן אַן אַנטוישונג אַז מע פֿאַרגעסט מער ידיועת.
deck-config-desired-retention-tooltip2 = די אָנלאָדונג צאָלן אינעם אינפֿאָ-קעסטל זענען בערכדיק. אויף גרעסער פּינקטלעכקייט, באַניץ דעם סימולירער.
deck-config-historical-retention-tooltip =
    אויב טייל פֿון דײַן חזר-געשיכטע פֿעלט, FSRS מוז אינטערפּאָלירן. דורך פֿעליקייט וועט עס אָננעמען, אַז דו האָסט זיך דערמאָנט 90% ווען דו האָסט זיי פֿריִער אײַנגע׳חזר׳ט. אין פֿאַל דײַן אַלטער זכּרון איז דעמאָלץ געווען ממש העכער אָדער קלענער פֿון 90%, דאָס צופּאַסט די אָ ברירה קען העלפֿן בעסער אָפּשאַצן די איבער׳חזר׳ונגען וואָס פֿעלן.
    
    ס׳קען עפּעס פֿעלן פֿון דער חזר-געשיכטע צוליב צוויי סיבות:
    1. ווײַל דו ניצטסט „איגנאָרירן קאַרטלעך וואָס פֿריער איבערגע׳חזר׳ט״.
    2. ווײַל דו האָסט פֿריִער אָפּגעמעקט חזר-בלעטלעך כּדי צו פֿאַרעפֿענען נאָך דיסק-אָרט אָדער דו האָסט אַרײַנגעפֿירט קאַרטלעך פֿון אַן אַנדער SRS פּראָגראַם. 
    
    די צווייטע הייסט גאָר זעלטן, איז, סײַדן דו ניצטסט די ערשטע ברירה, קערסט מסתּמא נישט צופּאַסן די אָ ברירה.
deck-config-weights-tooltip2 = FSRS אייגנקייטן גילטן אויף ווי אַזוי קאַרטלעך ווערן אויסגע׳סדר׳ט. אַנקי וועט אָנהייבן מיט אייגנקייטן דורך פֿעליקייט. קענסט ניצן די ווײַטערדיקע ברירה כּדי צו אָפּטימיזירן די אָ אייגנקייטן, זיי זאָלן זיך צום בעסטנס צופּאַסן דײַן פּעולה אין טעשלעך וואָס ניצן אָט דעם מוסטער.
deck-config-reschedule-cards-on-change-tooltip =
    סע גילט אויף דער גאַנצער זאַמלונג, און ווערט נישט אויפֿגעהיטן.
    
    די ברירה קאָנטראָלירט צי די טערמינען פֿון קאַרטלעך וועלן ווערן אויסגעביטן ווען FSRS ווערט אַקטיווירט, אָדער ווען אייגנקייטן ווערן אָפּטימיזירט. דורך פֿעליקייט ווערן די סדרים נישט אויסגעביטן: הייסט עס, צוקונפֿטיקע איבער׳חזר׳ונגען וועלן ניצן דעם נײַעם סדר, אָבער ס׳וועט נישט זײַן קיין באַלדיקע טויש אין דײַן אָנלאָדונג. אויב „אָפּלייגן טערמין״ איז יאָ חל, וועלן זיך יאָ אויסבײַטן די טערמינען פֿון קאַרטלעך.
deck-config-reschedule-cards-warning =
    לויט דײַן געוווּנטשענער אָנלאָדונג, קען דאָס דערפֿירן צו אַ גרויסן צאָל קאַרטלעך וואָס ווערן טערמיניק. איז עס נישט כּדאי אַז מע בײַט ערשטנס פֿון SM-2.
    
    זײַ מצמצם בײַם באַניצן די אָ ברירה, וועט עס צולייגן אַן איבערחזר׳ונג אין יעדן איינעם פֿון דײַנע קאַרטלעך, און וועט פֿאַרגרעסערן די דײַן זאַמלונג-גרייס.
deck-config-ignore-before-tooltip-2 =
    ווען ס׳איז חל, קאַרטלעך וואָס ווערן איבערגע׳חזר׳ט פֿאַר דער אָ דאַטע וועלן ווערן איגנאָרירט בשעת׳ן אָפּטימיזירן FSRS אייגנקייטן.
    ס׳קען זײַן ניצלעך אויב דו האָסט אַרײַנגעפֿירט אַן אַנדערס פּלאַניר-דאַטן, אָדער אויב דו האָסט געביטן ווי אַזוי דו ניצטסט די ענטפֿער-קנעפּלעך.
deck-config-compute-optimal-weights-tooltip2 =
    בײַם קוועטשען דאָס „אָפּטימיזירן״ קנעפּל, וועט דער FSRS אַנאַליזירן דײַן חזר-געשיכטע און אַזוי פּראָדיצירן אָפּטימאַלע אייגנקייטן לויט דײַן זכּרון און זעם געוויסן אינהאַלט וואָס דו לערנסט זיך. אויב דײַנע טעשלעך זענען שטאַרק מחולק לויט שוועריקייט, ס׳איז כּדאי צו באַשטימען פֿאַרשידענע מוסטערן, היות די אייגנקייטן אויף גרינגע און שווערע טעשלעך זאָלן זײַן אַנדערש. מע מוז נישט אָפֿט אָפּטימיזירן די אייגנקייטן - קלעקט עס נאָר איין מאָל יעדע פּאָר חדשים.
    
    דורך פֿעליקייט, וועלן אייגנקייטן ווערן אויסגערעכנט פֿון דער געשיכטע פֿון אַלע טעשלעך וואָס ניצן דעם זעלבן מוסטער. פֿאַר דעם מעג מען אָבער צופּאַסן ווי אַזוי סע זוכט אַדורך, ווען מע וויל פֿאַרבײַטן וועלכע קאַטרלעך זאָלן ווערן געניצט דערבײַ.
deck-config-please-save-your-changes-first = קודם, ביטע היט אויף טוישונגען.
deck-config-workload-factor-change = בערכדיקע אָנלאָדונג: { $factor }x (אַקעגן { $previousDR }% געוווּנטשענע זכּרון)
deck-config-workload-factor-unchanged = וואָס העכער אָט דער ציפֿער, אַלץ אָפֿטער וועט קאַרטלעך ווערן דיר געוויזן.
deck-config-desired-retention-too-low = דער געוווּנטשענער זכּרון איז גאָר נידעריק, וואָס קען גורם זײַן גאַנץ לאַנגע צווישנצײַטן.
deck-config-desired-retention-too-high = דער געוווּנטשענער זכּרון איז גאָר הויך, וואָס קען גורם זײַן גאַנץ קורצע צווישנצײַטן.
deck-config-percent-of-reviews =
    { $reviews ->
        [one] { $pct }% פֿון { $reviews } איבער׳חזר׳ונג
       *[other] { $pct }% פֿון { $reviews } איבער׳חזר׳ונגען
    }
deck-config-percent-input = { $pct }%
# This message appears during FSRS parameter optimization.
deck-config-checking-for-improvement = באַקוקנדיק אויף פֿאַרבעסערונגען…
deck-config-optimizing-preset = ס׳אָפּטימיזירט זיך מוסטער { $current_count }/{ $total_count }…
deck-config-fsrs-must-be-enabled = FSRS מוז קודם אַקטיווירט ווערן.
deck-config-fsrs-params-optimal = ס׳זעט אויס, ווי די פּאַראַמעטערס פֿון FSRS זענען שוין אָפּטימיזירט.
deck-config-fsrs-params-no-reviews = קיין אײַנ׳חזר׳ונג נישט געפֿונען. מאַך זיכער באַשטמיען אָט דער מוסטער אויף אַלע טעשלעך (אַרײַנגערעכנט סוב-טעשלעך) וואָס דו ווילסט אָפּטימיזירן, און פּרוביר ווידער אַ מאָל.
deck-config-wait-for-audio = וואַרטן אויף אוידיאָ
deck-config-show-reminder = באַווײַזן דערמאָנונג
deck-config-answer-again = ענטפֿערן „ווידער״
deck-config-answer-hard = ענטפֿערן „שווער״
deck-config-answer-good = ענטפֿערן „גוט״
deck-config-days-to-simulate = טעג אויף צו סימולירן
deck-config-desired-retention-below-optimal = דער געוווּנטשענער זכּרון איז נידעריקער ווי אָפּטימאַל, איז כּדאי אים צו פֿאַרגרעסערן.
# Description of the y axis in the FSRS simulation
# diagram (Deck options -> FSRS) showing the total number of
# cards that can be recalled or retrieved on a specific date.
deck-config-fsrs-simulator-experimental = FSRS סימולירער (עקספּערימענטאַל)
deck-config-fsrs-simulate-desired-retention-experimental = FSRS געוווּנטשענער זכּרון סימולירער (עקספּערימענטאַל)
deck-config-fsrs-simulate-save-preset = נאָכ׳ן אָפּטימיזירן, היט אויף דײַן טעשל-מוסטער פֿאַר׳ן אויספֿירן דעם סימולירער.
deck-config-fsrs-desired-retention-help-me-decide-experimental = העלף מיך באַשליסן (עקספּערימענטאַל)
deck-config-additional-new-cards-to-simulate = איבעריקע נײַע קאַרטלעך אויף צו סומילירן
deck-config-simulate = סימולירן
deck-config-clear-last-simulate = אָפּרייניקן לעצטיקע סימולאַציע
deck-config-fsrs-simulator-radio-count = אײַנ׳חזר׳ונגען
deck-config-advanced-settings = אַוואַנסירטע ברירות
deck-config-smooth-graph = פֿאַרגלעטן גראַפֿיק
deck-config-suspend-leeches = אָפּשטעלן שנאָרערס
deck-config-save-options-to-preset = אויפֿהיטן בײַטן אינעם מוסטער
deck-config-save-options-to-preset-confirm = איבערשרײַבן די ברירות אין דײַן יעצטיקע מוסטער מיט די ברירות וואָס מ׳האָט זיי פֿיקסירט אינעם סימולירער?
# Radio button in the FSRS simulation diagram (Deck options -> FSRS) selecting
# to show the total number of cards that can be recalled or retrieved on a
# specific date.
deck-config-fsrs-simulator-radio-memorized = אויסגעלערנט
deck-config-fsrs-simulator-radio-ratio = צײַט / אויסגעלערנט פּראָפּאָרציע
# $time here is pre-formatted e.g. "10 Seconds" 
deck-config-fsrs-simulator-ratio-tooltip = { $time } לויט אויסגעלערנט קאַרטל

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.

# Checkbox
deck-config-health-check = איבערקוקן ס׳געזונט בשעת׳ן אָפּטימיזירן
# Message box showing the result of the health check
deck-config-fsrs-bad-fit-warning =
    געזונט-קאָנטראָל:
    איז שווער פֿאָרויסצוזען דײַן זכּרון. עצות: 
    - אָפּשטעלן אָדער פֿאָרמוליר אויף ס׳נײַ אָפֿט-פֿאַרגעסענע קאַרטלעך
    - ניץ אױסהאַלטעוודיק די ענטפֿער קנעפּלעך. היט זיך אַז „שווער״ הייסט ריכטיק, נישט טעותדיק.
    - פֿאַרשטיי איידער דו פֿאַרגעדענקסט.
    
    ווען דו היטסט זיך די עצות, קער די פּעולה זיך פֿאַרבעסערן אין די קומעדיקע חדשים.
# Message box showing the result of the health check
deck-config-fsrs-good-fit =
    געזונט-קאָנטראָל:
    FSRS קען זיך גוט צופּאַסן אויף דײַן זכּרון.

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-unable-to-determine-desired-retention = מ׳האָט נישט געקענט באַשטימען אַ מינימאַל-רעקאָמענדירט אָנהאַלטן.
deck-config-predicted-minimum-recommended-retention = מינימאַל רעקאָמענדירטע זכּרון: { $num }
deck-config-compute-minimum-recommended-retention = מינימאַל רעקאָמענדירטע זכּרון
deck-config-compute-optimal-retention-tooltip4 = [NO NEED TO TRANSLATE]
deck-config-plotted-on-x-axis = <MARKED AS NOT NEEDED>
deck-config-a-100-day-interval =
    { $days ->
        [one] אַ צווישנצײַט פֿון 100 טעג וועט ווערן { $days } טאָג.
       *[other] אַ צווישנצײַט פֿון 100 טעג וועט ווערן { $days } טעג.
    }
deck-config-fsrs-simulator-y-axis-title-time = <MARKED AS NOT NEEDED>
deck-config-fsrs-simulator-y-axis-title-count = אײַנחזר-צאָל / טאָג
deck-config-fsrs-simulator-y-axis-title-memorized = סך-הכּל אויסגעלערנט
deck-config-bury-siblings = אָפּהאַלטן געשוויסטער
deck-config-do-not-bury = אָפּהאַלטן נישט קיין געשוויסטער
deck-config-bury-if-new = אָפּהאַלטן ווען נײַ
deck-config-bury-if-new-or-review = אָפּהאַַלטן ווען נײַ אָדער אויף איבערצו׳חזר׳ן
deck-config-bury-if-new-review-or-interday = אָפּהאַַלטן ווען נײַ, אויף איבערצו׳חזר׳ן, אָדער צווישן-טעג אײַנצו׳חזר׳ן
deck-config-bury-tooltip = <MARKED AS NOT NEEDED>
deck-config-seconds-to-show-question-tooltip = <MARKED AS NOT NEEDED>
deck-config-answer-action-tooltip = <MARKED AS NOT NEEDED>
deck-config-wait-for-audio-tooltip = <MARKED AS NOT NEEDED>
deck-config-ignore-before-tooltip = <MARKED AS NOT NEEDED>
deck-config-compute-optimal-retention-tooltip = <MARKED AS NOT NEEDED>
deck-config-health-check-tooltip1 = <MARKED AS NOT NEEDED>
deck-config-health-check-tooltip2 = <MARKED AS NOT NEEDED>
deck-config-compute-optimal-retention = <MARKED AS NOT NEEDED>
deck-config-predicted-optimal-retention = מינימאַל רעקאָמענדירטע זכּרון: { $num }
deck-config-weights-tooltip = <MARKED AS NOT NEEDED>
deck-config-compute-optimal-weights-tooltip = <MARKED AS NOT NEEDED>
deck-config-compute-optimal-retention-tooltip2 = <MARKED AS NOT NEEDED>
deck-config-compute-optimal-retention-tooltip3 = <MARKED AS NOT NEEDED>
deck-config-seconds-to-show-question-tooltip-2 = ווען אויטאָ-פֿאָרויסגיין איז חל, די ציפֿער סעקונדעס אויף צו וואַרטן ביז׳ן באַווײַזן די ענטפֿער. כּדי בטל צו מאַכן, שטעלן ׳0׳.
deck-config-invalid-weights = <MARKED AS NOT NEEDED>
deck-config-fsrs-on-all-clients = <MARKED AS NOT NEEDED>
deck-config-optimize-all-tip = מע קען אָפּטימיזירן אַלע מוסטערס מיט אַ מאָל בײַם ניצן דאָס אַראָפּצי-קנעפּל פּונקט לעבן "אויפֿהיטן".
