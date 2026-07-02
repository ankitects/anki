statistics-due-date = Escasença
statistics-due-count = Escasença
statistics-cards-per-min = { $cards-per-minute } cartas/minuta
statistics-studied-today =
    Estudiat { statistics-cards } { $unit ->
        [seconds] { statistics-in-time-span-seconds }
        [minutes] { statistics-in-time-span-minutes }
        [hours] { statistics-in-time-span-hours }
        [days] { statistics-in-time-span-days }
        [months] { statistics-in-time-span-months }
       *[years] { statistics-in-time-span-years }
    } uèi ({ $secs-per-card }s/card)
statistics-cards =
    { $cards ->
        [one] { $cards } carta
       *[other] { $cards } cartas
    }
statistics-reviews =
    { $reviews ->
        [one] { $reviews } revision
       *[other] { $reviews } revisions
    }
statistics-today-again-count = Doblits :
statistics-today-no-cards = Cap carta es estada estudiada uèi
statistics-counts-new-cards = Novèl
statistics-future-due-title = Previsions
statistics-reviews-title = Revisions
statistics-answer-buttons-title = Botons de responsa
statistics-added-title = Apondut
statistics-axis-label-answer-count = Responsas
statistics-axis-label-card-count = Cartas
statistics-days-studied = Jorns obrants
statistics-average-answer-time-label = Durada de réponse moyenne
statistics-average = Mejana
statistics-average-interval = Interval mejan
statistics-average-ease = Facilitat mejana
statistics-stats = estatisticas
