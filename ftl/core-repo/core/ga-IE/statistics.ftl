# The date a card will be ready to review
statistics-due-date = Le staidéar
# The count of cards waiting to be reviewed
statistics-due-count = Le staidéar
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = Nua #{ $number }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } cárta/nóiméad
statistics-average-answer-time = { $average-seconds }s ({ statistics-cards-per-min })

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds =
    taobh istigh de { $amount ->
        [one] { $amount } shoicind amháin
        [two] { $amount } shoicind
        [few] { $amount } shoicind
        [many] { $amount } soicind
       *[other] { $amount } soicind
    }
statistics-in-time-span-minutes =
    taobh istigh de { $amount ->
        [one] { $amount } nóiméad amháin
        [two] { $amount } nóiméad
        [few] { $amount } nóiméad
        [many] { $amount } nóiméad
       *[other] { $amount } nóiméad
    }
statistics-in-time-span-hours =
    taobh istigh de { $amount ->
        [one] { $amount } uair an chloig
        [two] { $amount } uair an chloig
        [few] { $amount } huaire an chloig
        [many] { $amount } n-uaire an chloig
       *[other] { $amount } uair an chloig
    }
statistics-in-time-span-days =
    taobh istigh de { $amount ->
        [one] { $amount } lá amháin
        [two] { $amount } lá
        [few] { $amount } lá
        [many] { $amount } lá
       *[other] { $amount } lá
    }
statistics-in-time-span-months =
    taobh istigh de { $amount ->
        [one] { $amount } mhí amháin
        [two] { $amount } mhí
        [few] { $amount } mhí
        [many] { $amount } mí
       *[other] { $amount } mí
    }
statistics-in-time-span-years =
    taobh istigh de { $amount ->
        [one] { $amount } bhliain amháin
        [two] { $amount } bhliain
        [few] { $amount } bliana
        [many] { $amount } mbliana
       *[other] { $amount } bliain
    }
statistics-cards =
    { $cards ->
        [one] { $cards } chárta amháin
        [two] { $cards } chárta
        [few] { $cards } chárta
        [many] { $cards } gcárta
       *[other] { $cards } cárta
    }
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews =
    { $reviews ->
        [one] { $reviews } athbhreithniú amháin
        [two] { $reviews } athbhreithniú
        [few] { $reviews } athbhreithniú
        [many] { $reviews } n-athbhreithniú
       *[other] { $reviews } athbhreithniú
    }
# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { statistics-in-time-span-seconds } part should be pasted in from the English
# version unmodified.
statistics-studied-today =
    Staidéar déanta ar { statistics-cards } { $unit ->
        [seconds] { statistics-in-time-span-seconds }
        [minutes] { statistics-in-time-span-minutes }
        [hours] { statistics-in-time-span-hours }
        [days] { statistics-in-time-span-days }
        [months] { statistics-in-time-span-months }
       *[years] { statistics-in-time-span-years }
    } inniu
    ({ $secs-per-card } s/cárta)
statistics-today-title = Inniu
statistics-today-again-count = Arís:
statistics-today-type-counts = Foghlaim: { $learnCount }, Athbhreithnigh: { $reviewCount }, Athfhoghlaim: { $relearnCount }, Scagtha: { $filteredCount }
statistics-today-no-cards = Níl staidéar déanta ar aon chárta inniu
statistics-today-no-mature-cards = Níl staidéar déanta ar aon chárta aibí inniu.
statistics-today-correct-mature = Freagraí cearta ar chártaí aibí: { $correct }/{ $total } ({ $percent }%)
statistics-counts-total-cards = Iomlán
statistics-counts-new-cards = Nua
statistics-counts-young-cards = Óg
statistics-counts-mature-cards = Aibí
statistics-counts-suspended-cards = Ar fionraí
statistics-counts-buried-cards = I bhfolach
statistics-counts-filtered-cards = Scagtha
statistics-counts-learning-cards = Á bhfoghlaim
statistics-counts-relearning-cards = Á n-athfhoghlaim
statistics-counts-title = Líonta Cártaí
statistics-counts-separate-suspended-buried-cards = Cuir na cártaí atá ar fionraí/i bhfolach i leataobh
statistics-range-all-time = uile
statistics-range-1-year-history = le 12 mhí anuas
statistics-range-all-history = riamh
statistics-range-deck = paca
statistics-range-collection = cnuasach
statistics-range-search = Cuardaigh
statistics-card-ease-title = Éascaíocht Chártaí
statistics-card-ease-subtitle = Dá ísle éascaíocht cárta áirithe, is ea is minice a fheictear é.
# eg "3 cards with 150-170% ease"
statistics-card-ease-tooltip =
    { $cards ->
        [one] { $cards } chárta amháin agus éascaíocht { $percent } aige
        [two] { $cards } chárta agus éascaíocht { $percent } acu
        [few] { $cards } chárta agus éascaíocht { $percent } acu
        [many] { $cards } gcárta agus éascaíocht { $percent } acu
       *[other] { $cards } cárta agus éascaíocht { $percent } acu
    }
statistics-future-due-title = Le Staidéar Amach Anseo
statistics-future-due-subtitle = Líon na n-athbhreithnithe a bheidh le déanamh amach anseo
statistics-added-title = Curtha leis
statistics-added-subtitle = Líon na gcártaí nua a chuir tú leis an bpaca.
statistics-reviews-count-subtitle = Líon na gceisteanna ar thug tú freagra orthu.
statistics-reviews-time-subtitle = An méid ama caite ag freagairt na gceisteanna.
statistics-answer-buttons-title = Cnaipí Freagartha
# eg Button: 4
statistics-answer-buttons-button-number = Cnaipe
# eg Times pressed: 123
statistics-answer-buttons-button-pressed = Brúite
statistics-answer-buttons-subtitle = Cé mhéad uair a bhrúigh tú gach cnaipe.
statistics-reviews-title = Athbhreithnithe
statistics-reviews-time-checkbox = Am
statistics-in-days-single =
    { $days ->
        [0] Inniu
        [1] Amárach
        [one] i gceann { $days } lá amháin
        [two] i gceann { $days } lá
        [few] i gceann { $days } lá
        [many] i gceann { $days } lá
       *[other] i gceann { $days } lá
    }
statistics-in-days-range = I gceann { $daysStart }-{ $daysEnd } lá
statistics-days-ago-single =
    { $days ->
        [1] Inné
        [one] Lá ó shin
        [two] { $days } lá ó shin
        [few] { $days } lá ó shin
        [many] { $days } lá ó shin
       *[other] { $days } lá ó shin
    }
statistics-days-ago-range = { $daysStart }-{ $daysEnd } lá ó shin
statistics-running-total = Iomlán reatha
statistics-cards-due =
    { $cards ->
        [one] { $cards } chárta amháin le staidéar
        [two] { $cards } chárta le staidéar
        [few] { $cards } chárta le staidéar
        [many] { $cards } gcárta le staidéar
       *[other] { $cards } cárta le staidéar
    }
statistics-backlog-checkbox = Riaráiste
statistics-intervals-title = Eatraimh Athbhreithnithe
statistics-intervals-subtitle = Cé chomh fada le fanacht idir athbhreithnithe.
statistics-intervals-day-range =
    { $cards ->
        [one] { $cards } chárta amháin agus eatramh { $daysStart }~{ $daysEnd } aige
        [two] { $cards } chárta agus eatramh { $daysStart }~{ $daysEnd } acu
        [few] { $cards } chárta agus eatramh { $daysStart }~{ $daysEnd } acu
        [many] { $cards } gcárta agus eatramh { $daysStart }~{ $daysEnd } acu
       *[other] { $cards } cárta agus eatramh { $daysStart }~{ $daysEnd } acu
    }
statistics-intervals-day-single =
    { $cards ->
        [one] { $cards } chárta amháin agus eatramh { $day } lá aige
        [two] { $cards } chárta agus eatramh { $day } lá acu
        [few] { $cards } chárta agus eatramh { $day } lá acu
        [many] { $cards } gcárta agus eatramh { $day } lá acu
       *[other] { $cards } cárta agus eatramh { $day } lá acu
    }
# hour range, eg "From 14:00-15:00"
statistics-hours-range = Ó { $hourStart }.00~{ $hourEnd }.00
statistics-hours-correct = { $correct }/{ $total } ceart ({ $percent }%)
# the emoji depicts the graph displaying this number
statistics-hours-reviews = 📊 { $reviews } athbhreithniú
# the emoji depicts the graph displaying this number
statistics-hours-correct-reviews = 📈 { $percent }% ceart ({ $reviews })
statistics-hours-title = Miondealú de réir uaire
statistics-hours-subtitle = Staitisticí de réir gach uair an chloig den lá.
# shown when graph is empty
statistics-no-data = GAN SONRAÍ
statistics-calendar-title = Féilire

## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.

statistics-elapsed-time-seconds = { $amount }s
statistics-elapsed-time-minutes = { $amount }n
statistics-elapsed-time-hours = { $amount }u
statistics-elapsed-time-days = { $amount }l
statistics-elapsed-time-months = { $amount }m
statistics-elapsed-time-years = { $amount }b

##

statistics-average-for-days-studied = Meán (laethanta gníomhacha)
statistics-total = Iomlán
statistics-days-studied = Laethanta gníomhacha
statistics-average-answer-time-label = Am meánach chun freagra a thabhairt
statistics-average = Meán
statistics-average-interval = Meáneatramh
statistics-due-tomorrow = Le staidéar amárach
# eg 5 of 15 (33.3%)
statistics-amount-of-total-with-percentage = { $amount } as { $total } ({ $percent }%)
statistics-average-over-period = Meán (an tréimhse uile)
statistics-reviews-per-day =
    { $count ->
        [one] { $count } athbhreithniú/lá
        [two] { $count } athbhreithniú/lá
        [few] { $count } athbhreithniú/lá
        [many] { $count } n-athbhreithniú/lá
       *[other] { $count } athbhreithniú/lá
    }
statistics-minutes-per-day =
    { $count ->
        [one] { $count } nóiméad/lá
        [two] { $count } nóiméad/lá
        [few] { $count } nóiméad/lá
        [many] { $count } nóiméad/lá
       *[other] { $count } nóiméad/lá
    }
statistics-cards-per-day =
    { $count ->
        [one] { $count } chárta/lá
        [two] { $count } chárta/lá
        [few] { $count } chárta/lá
        [many] { $count } gcárta/lá
       *[other] { $count } cárta/lá
    }
statistics-average-ease = Méanéascaíocht
statistics-save-pdf = Sábháil PDF
statistics-saved = Sábháilte.
statistics-stats = staitisticí
statistics-title = Staitisticí
statistics-true-retention-total = Iomlán
statistics-true-retention-young = Óg
statistics-true-retention-mature = Aibí
