database-check-corrupt = Kolekto estas difektita. Bonvolu vidi la gvidlibron.
database-check-rebuilt = La datumbazo estas rekunmetita kaj optimumigita.
database-check-card-properties =
    { $count ->
        [one] Nevalidaj ecoj de { $count } karto estas korektita.
       *[other] Nevalidaj ecoj de { $count } kartoj estas korektitaj.
    }
database-check-missing-templates =
    { $count ->
        [one] Forigis { $count } karton kun mankanta ŝablono.
       *[other] Forigis { $count } kartojn kun mankanta ŝablono.
    }
database-check-field-count =
    { $count ->
        [one] { $count } noto kun malĝusta kamponombro estas korektita.
       *[other] { $count } notoj kun malĝusta kamponombro estas korektitaj.
    }
database-check-new-card-high-due =
    { $count ->
        [one] { $count } nova karto kun limdato >= 1,000,000 estis trovita - bonvolu konsideri repozicii ĝin en la foliumilo.
       *[other] { $count } novaj kartoj kun limdato >= 1,000,000 estis trovitaj -bonvolu konsideri repozicii ilin en la foliumilo.
    }
database-check-card-missing-note =
    { $count ->
        [one] Forigis { $count } karton kies noto mankis.
       *[other] Forigis { $count } kartojn kies notoj mankis.
    }
database-check-missing-decks =
    { $count ->
        [one] { $count } mankanta kartaro esits korektita.
       *[other] { $count } mankantaj kartaroj esits korektitaj.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] { $count } noto kun nevalidaj UTF-8-signoj estis korektita.
       *[other] { $count } notoj kun nevalidaj UTF-8-signoj estis korektitaj.
    }
database-check-fixed-invalid-ids =
    { $count ->
        [one] { $count } objekto kun tempindiko en la estonteco estis korektita.
       *[other] { $count } objektoj kun tempindiko en la estonteco estis korektitaj.
    }

## Progress info

database-check-checking-integrity = Kontrolado de kolekto...
database-check-rebuilding = Rekonstruado...
database-check-checking-cards = Kontrolado de kartoj...
database-check-checking-notes = Kontrolado de notoj...
database-check-checking-history = Kontrolado de historio...
database-check-title = Kontroli datumbazon
