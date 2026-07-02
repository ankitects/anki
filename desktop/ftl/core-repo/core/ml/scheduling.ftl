## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount }s
scheduling-answer-button-time-minutes = { $amount }m
scheduling-answer-button-time-hours = { $amount }h
scheduling-answer-button-time-days = { $amount }d
scheduling-answer-button-time-months = { $amount }mo
scheduling-answer-button-time-years = { $amount }y

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] { $amount } സെക്കൻഡ്
       *[other] { $amount } സെക്കൻഡുകൾ
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] { $amount } മിനിറ്റ്
       *[other] { $amount } മിനിറ്റുകൾ
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } മണിക്കൂർ
       *[other] { $amount } മണിക്കൂറുകൾ
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } ദിവസം
       *[other] { $amount } ദിവസങ്ങൾ
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } മാസം
       *[other] { $amount } മാസങ്ങൾ
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } വർഷം
       *[other] { $amount } വർഷങ്ങൾ
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    അടുത്ത പഠന കാർഡ് ഇതിനകം തയ്യാറാകും { $unit ->
        [seconds]
            { $amount ->
                [one] { $amount } സെക്കൻഡ്
               *[other] { $amount } സെക്കൻഡുകൾ
            }
        [minutes]
            { $amount ->
                [one] { $amount } മിനിറ്റ്
               *[other] { $amount } മിനുട്ടുകൾ
            }
       *[hours]
            { $amount ->
                [one] { $amount } മണിക്കൂർ
               *[other] { $amount } മണിക്കൂറുകൾ
            }
    }.
scheduling-learn-remaining =
    { $remaining ->
        [one] അവശേഷിക്കുന്ന പഠന കാർഡ് ഇന്നത്തേക്ക് ഡ്യൂ ആണ്.
       *[other] അവശേഷിക്കുന്ന { $remaining } പഠന കാർഡുകൾ ഇന്നത്തേക്ക് ഡ്യൂ ആണ്
    }
scheduling-congratulations-finished = അഭിനന്ദനങ്ങൾ! നിങ്ങൾ ഇപ്പോഴത്തേക്ക് ഈ ഡെക്ക് പൂർത്തിയാക്കി.
scheduling-today-review-limit-reached = ഇന്നത്തെ അവലോകന പരിധിയിലെത്തി, പക്ഷേ അവലോകനം ചെയ്യാൻ കാത്തിരിക്കുന്ന കാർഡുകൾ ഇപ്പോഴും ഉണ്ട്. ഒപ്റ്റിമൽ മെമ്മറിയ്ക്കായി, ഓപ്ഷനുകളിൽ ദൈനംദിന പരിധി വർദ്ധിപ്പിക്കുന്നത് പരിഗണിക്കുക.
scheduling-today-new-limit-reached = കൂടുതൽ പുതിയ കാർഡുകൾ ലഭ്യമാണ്, പക്ഷേ പ്രതിദിന പരിധിയിലെത്തി. ഓപ്‌ഷനുകളിൽ‌ നിങ്ങൾ‌ക്ക് പരിധി വർദ്ധിപ്പിക്കാൻ‌ കഴിയും, പക്ഷേ നിങ്ങൾ‌ കൂടുതൽ‌ പുതിയ കാർ‌ഡുകൾ‌ അവതരിപ്പിക്കുമ്പോൾ‌ നിങ്ങളുടെ ഹ്രസ്വകാല അവലോകന വർ‌ക്ക്ലോഡ് ഉയരുമെന്ന് ദയവായി ഓർമ്മിക്കുക.
scheduling-buried-cards-found = ഒന്നോ അതിലധികമോ കാർഡുകൾ കുഴിച്ചിട്ടു, അവ നാളെ കാണിക്കും. നിങ്ങൾക്ക് ഉടനടി കാണണമെങ്കിൽ { $unburyThem }-നു സാധിക്കും.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = കുഴിച്ചിട്ടത് തിരിച്ചെടുക്കുക
scheduling-how-to-custom-study = പതിവ് ഷെഡ്യൂളിന് പുറത്ത് പഠിക്കാൻ നിങ്ങൾ ആഗ്രഹിക്കുന്നുവെങ്കിൽ, നിങ്ങൾക്ക് { $customStudy } സവിശേഷത ഉപയോഗിക്കാം.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = ഇഷ്‌ടാനുസൃത പഠനം

## Scheduler upgrade

scheduling-update-soon = മുമ്പത്തെ Anki പതിപ്പുകളിലുണ്ടായിരുന്ന നിരവധി പ്രശ്‌നങ്ങൾ പരിഹരിക്കുന്ന ഒരു പുതിയ ഷെഡ്യൂളറുമായി Anki 2.1 വരുന്നു. ഇതിലേക്ക് അപ്‌ഡേറ്റ് ചെയ്യുന്നത് ശുപാർശ ചെയ്യുന്നു.
scheduling-update-done = ഷെഡ്യൂളർ വിജയകരമായി അപ്‌ഡേറ്റുചെയ്‌തു.
scheduling-update-button = പുതുക്കുക
scheduling-update-later-button = പിന്നീട്
scheduling-update-more-info-button = കൂടുതലറിവ് നേടുക

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = ഓഡിയോ വീണ്ടും പ്ലേ ചെയ്യുമ്പോൾ എല്ലായ്പ്പോഴും ചോദ്യ വശം ഉൾപ്പെടുത്തുക
scheduling-at-least-one-step-is-required = കുറഞ്ഞത് ഒരു ഘട്ടമെങ്കിലും ആവശ്യമാണ്.
scheduling-automatically-play-audio = ഓഡിയോ യാന്ത്രികമായി പ്ലേ ചെയ്യുക
scheduling-bury-related-new-cards-until-the = ബന്ധപ്പെട്ട പുതിയ കാർഡുകൾ അടുത്ത ദിവസം വരെ കുഴിച്ചിടുക
scheduling-bury-related-reviews-until-the-next = അനുബന്ധ അവലോകനങ്ങൾ അടുത്ത ദിവസം വരെ കുഴിച്ചിടുക
scheduling-days = ദിവസങ്ങള്‍
scheduling-description = വിവരണം
scheduling-description-to-show-on-overview-screen = നിലവിലെ ഡെക്കിനായി അവലോകന സ്ക്രീനിൽ കാണിക്കുന്നതിനുള്ള വിവരണം:
scheduling-easy-bonus = എളുപ്പമുള്ള ബോണസ്
scheduling-easy-interval = എളുപ്പമുള്ള ഇടവേള
scheduling-end = (അവസാനം)
scheduling-general = പൊതുവായത്
scheduling-graduating-interval = ബിരുദ ഇടവേള
scheduling-hard-interval = കഠിന ഇടവേള
scheduling-ignore-answer-times-longer-than = ഉത്തര സമയത്തേക്കാൾ കൂടുതൽ സമയം അവഗണിക്കുക
scheduling-interval-modifier = ഇടവേള പരിഷ്കരണി
scheduling-lapses = വീഴ്ചകൾ
scheduling-lapses2 = വീഴ്ചകൾ
scheduling-learning = പഠിച്ചുകൊണ്ടിരിക്കുന്നു
scheduling-leech-action = ലീച്ച് പ്രവർത്തനം
scheduling-leech-threshold = ലീച്ച് പരിധി
scheduling-maximum-interval = പരമാവധി ഇടവേള
scheduling-maximum-reviewsday = പരമാവധി അവലോകനങ്ങൾ / ദിവസം
scheduling-minimum-interval = കുറഞ്ഞ ഇടവേള
scheduling-mix-new-cards-and-reviews = പുതിയ കാർഡുകളും അവലോകനങ്ങളും മിക്സ് ചെയ്യുക
scheduling-new-cards = പുതിയ കാർഡുകൾ
scheduling-new-cardsday = പുതിയ കാർഡുകൾ / ദിവസം
scheduling-new-interval = പുതിയ ഇടവേള
scheduling-new-options-group-name = പുതിയ ഓപ്ഷൻ ഗ്രൂപ്പിന്റെ പേര്:
scheduling-options-group = ഓപ്ഷൻസ് ഗ്രൂപ്പ്:
scheduling-order = ക്രമം
scheduling-parent-limit = (പാരന്റ് പരിധി: { $val })
scheduling-review = അവലോകനം
scheduling-reviews = അവലോകനങ്ങൾ
scheduling-seconds = സെക്കന്റുകള്‍
scheduling-set-all-decks-below-to = { $val }-ന് താഴെയുള്ള എല്ലാ ഡെക്കുകളും ഈ ഓപ്ഷൻ ഗ്രൂപ്പിലേക്ക് സജ്ജമാക്കണോ?
scheduling-set-for-all-subdecks = എല്ലാ ഉപഡെക്കുകൾക്കും സജ്ജമാക്കുക
scheduling-show-answer-timer = ഉത്തര ടൈമർ പ്രദർശിപ്പിക്കുക
scheduling-show-new-cards-after-reviews = അവലോകനങ്ങൾക്ക് ശേഷം പുതിയ കാർഡുകൾ പ്രദർശിപ്പിക്കുക
scheduling-show-new-cards-before-reviews = അവലോകനങ്ങൾക്ക് മുമ്പ് പുതിയ കാർഡുകൾ പ്രദർശിപ്പിക്കുക
scheduling-show-new-cards-in-order-added = ചേർത്ത ക്രമത്തിൽ പുതിയ കാർഡുകൾ പ്രദർശിപ്പിക്കുക
scheduling-show-new-cards-in-random-order = ക്രമരഹിതമായി പുതിയ കാർഡുകൾ പ്രദർശിപ്പിക്കുക
scheduling-starting-ease = ആരംഭത്തിലെ എളുപ്പം
scheduling-steps-in-minutes = ഘട്ടങ്ങൾ (മിനിറ്റിനുള്ളിൽ)
scheduling-steps-must-be-numbers = ഘട്ടങ്ങൾ അക്കങ്ങളായിരിക്കണം.
scheduling-tag-only = ടാഗ് മാത്രം
scheduling-the-default-configuration-cant-be-removed = സ്ഥിരസ്ഥിതി കോൺഫിഗറേഷൻ നീക്കംചെയ്യാൻ കഴിയില്ല.
scheduling-your-changes-will-affect-multiple-decks = നിങ്ങളുടെ മാറ്റങ്ങൾ ഒന്നിലധികം ഡെക്കുകളെ ബാധിക്കും. നിലവിലെ ഡെക്ക് മാത്രം മാറ്റാൻ നിങ്ങൾ ആഗ്രഹിക്കുന്നുവെങ്കിൽ, ആദ്യം ഒരു പുതിയ ഓപ്ഷൻ ഗ്രൂപ്പ് ചേർക്കുക.
scheduling-deck-updated =
    { $count ->
        [one] { $count } ഡെക്ക് അപ്‌ഡേറ്റുചെയ്‌തു
       *[other] { $count } ഡെക്കുകൾ അപ്‌ഡേറ്റുചെയ്‌തു.
    }
scheduling-set-due-date-prompt =
    { $cards ->
        [one] എത്ര ദിവസത്തിനുള്ളിൽ കാർഡ് കാണിക്കുക?
       *[other] എത്ര ദിവസത്തിനുള്ളിൽ കാർഡ് കാണിക്കുക?
    }
scheduling-set-due-date-prompt-hint =
    0 = ഇന്ന്
    1! = നാളെ + അവലോകന ഇടവേള പുനസജ്ജമാക്കുക
    3-7 = 3-7 ദിവസത്തെ ക്രമരഹിതമായ തിരഞ്ഞെടുപ്പ്
scheduling-set-due-date-done =
    { $cards ->
        [one] { $cards } കാർഡിന്റെ നിശ്ചിത തീയതി സജ്ജമാക്കുക
       *[other] { $cards } കാർഡുകളുടെ നിശ്ചിത തീയതി സജ്ജമാക്കുക
    }
scheduling-forgot-cards =
    { $cards ->
        [one] { $cards } കാർഡ് മറന്നു
       *[other] { $cards } കാർഡുകൾ മറന്നു
    }
