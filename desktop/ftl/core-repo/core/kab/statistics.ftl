statistics-due-date = Tagara
statistics-due-count = Tagara
statistics-cards-per-min = { $cards-per-minute } tikarḍiwin/tesdat
statistics-in-time-span-seconds =
    { $amount ->
        [one] deg { $amount } tasint
       *[other] deg { $amount } tasinin
    }
statistics-in-time-span-minutes =
    { $amount ->
        [one] deg { $amount } tesdat
       *[other] deg { $amount } tisdatin
    }
statistics-in-time-span-hours =
    { $amount ->
        [one] de { $amount } usrag
       *[other] de { $amount } isragen
    }
statistics-in-time-span-days =
    { $amount ->
        [one] deg { $amount } n wass
       *[other] deg { $amount } n wussan
    }
statistics-in-time-span-months =
    { $amount ->
        [one] deg { $amount } waggur
       *[other] deg { $amount } wagguren
    }
statistics-in-time-span-years =
    { $amount ->
        [one] deg { $amount } useggas
       *[other] deg { $amount } yeaiseggasenrs
    }
statistics-studied-today =
    Yeɣra { statistics-cards } { $unit ->
        [seconds] { statistics-in-time-span-seconds }
        [minutes] { statistics-in-time-span-minutes }
        [hours] { statistics-in-time-span-hours }
        [days] { statistics-in-time-span-days }
        [months] { statistics-in-time-span-months }
       *[years] { statistics-in-time-span-years }
    } ass-a ({ $secs-per-card }s/takarḍa)
statistics-cards =
    { $cards ->
        [one] { $cards } n tkarḍa
       *[other] { $cards } n tkarḍiwin
    }
statistics-reviews =
    { $reviews ->
        [one] { $reviews } aceggir
       *[other] { $reviews } iceggiren
    }
statistics-today-title = Ass-a
statistics-today-again-count = Siḍen tikkelt-nniḍen:
statistics-counts-new-cards = Rnu
statistics-counts-suspended-cards = Yeḥbes
statistics-range-all-time = tudert n ukemmus
statistics-range-deck = akemmus
statistics-range-collection = tagrumma
statistics-range-search = Nadi
statistics-intervals-title = Izilalen
statistics-answer-buttons-title = Tiqeffalin n tririt
statistics-added-title = yettwarna
statistics-axis-label-answer-count = Tiririyin
statistics-axis-label-card-count = Tikaṛḍiwin
statistics-reviews-time-checkbox = Akud
statistics-total = Amatu
statistics-days-studied = Ussan n tɣuri
statistics-average-answer-time-label = Akud n tririt alemmas
statistics-average = Talemmast
statistics-saved = Yekles.
statistics-stats = tiddadanin
