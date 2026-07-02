database-check-corrupt = Krājuma datne ir bojāta. Lūgums atjaunot no automātiskās rezerves kopijas.
database-check-rebuilt = Datubāze ir pārbūvēta un optimizēta.
database-check-card-properties =
    { $count ->
        [zero] Salabotas { $count } nederīgas kartīšu īpašības.
        [one] Salabota { $count } nederīga kartīšu īpašība.
       *[other] Salabotas { $count } nederīgas kartīšu īpašības.
    }
database-check-card-last-review-time-empty =
    { $count ->
        [zero] Pievienots pēdējais pārskata laiks { $count } kartītēs.
        [one] Pievienots pēdējais pārskata laiks { $count } kartītē.
       *[other] Pievienots pēdējais pārskata laiks { $count } kartītē(s).
    }
database-check-missing-templates =
    { $count ->
        [zero] Izdzēstas { $count } kartīšu bez veidnes.
        [one] Izdzēsta { $count } kartīte bez veidnes.
       *[other] Izdzēstas { $count } kartītes bez veidnes.
    }
database-check-field-count =
    { $count ->
        [zero] Salabotas { $count } piezīmes ar nepareizu lauku skaitu.
        [one] Salabota { $count } piezīme ar nepareizu lauku skaitu.
       *[other] Salabotas { $count } piezīmes ar nepareizu lauku skaitu.
    }
database-check-new-card-high-due =
    { $count ->
        [zero] Atrastas { $count } jaunas kartītes ar kārtas numuru ≥ 1 000 000 000 - jāapsver pārkārtot to pārlūkošanas ekrānā.
        [one] Atrasta { $count } jauna kartīte ar kārtas numuru ≥ 1 000 000 000 - jāapsver pārkārto to pārlūkošanas ekrānā.
       *[other] Atrastas { $count } jaunas kartītes ar kārtas numuru ≥ 1 000 000 000 - jāapsver pārkārtot to pārlūkošanas ekrānā.
    }
database-check-card-missing-note =
    { $count ->
        [zero] Izdzēstas { $count } kartītes bez piezīmes.
        [one] Izdzēsta { $count } kartīte bez piezīmes.
       *[other] Izdzēstas { $count } kartītes bez piezīmes.
    }
database-check-duplicate-card-ords =
    { $count ->
        [zero] Izdzēstas { $count } kartītes ar divkāršu veidni.
        [one] Izdzēsta { $count } kartīte ar divkāršu veidni.
       *[other] Izdzēstas { $count } kartītes ar divkāršu veidni.
    }
database-check-missing-decks =
    { $count ->
        [zero] Salabotas { $count } trūkstošās kavas.
        [one] Salabota { $count } trūkstoša kava.
       *[other] Salabotas { $count } trūkstošas kavas.
    }
database-check-revlog-properties =
    { $count ->
        [zero] Salaboti { $count } pārskatīšanas ierakstu ar nederīgām īpašībām.
        [one] Salabots { $count } pārskatīšanas ieraksts ar nederīgām īpašībām.
       *[other] Salaboti { $count } pārskatīšanas ieraksti ar nederīgām īpašībām.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [zero] Salabotas { $count } piezīmes ar nederīgām UTF8 rakstzīmēm.
        [one] Salabota { $count } piezīme ar nederīgām UTF8 rakstzīmēm.
       *[other] Salabotas { $count } piezīmes ar nederīgām UTF8 rakstzīmēm.
    }
database-check-fixed-invalid-ids =
    { $count ->
        [zero] Salaboti { $count } objektu ar laikspiedoliem nākotnē.
        [one] Salabots { $count } objekts ar laikspiedoliem nākotnē.
       *[other] Salaboti { $count } objekti ar laikspiedoliem nākotnē.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Trūkst viens vai vairāki piezīmju veidi. Piezīmēm, kurās tie tika izmantoti, ir piešķirti jauni piezīmju veidi, kas sākas ar «db-check», bet lauku nosaukumi un kartīšu izskats ir zaudēts, tāpēc būtu labāk atjaunot no automātiskas rezerves kopijas.

## Progress info

database-check-checking-integrity = Pārbauda krājumu...
database-check-rebuilding = Pārbūvē...
database-check-checking-cards = Pārbauda kartītes...
database-check-checking-notes = Pārbauda piezīmes...
database-check-checking-history = Pārbauda vēsturi...
database-check-title = Pārbaudīt datubāzi
