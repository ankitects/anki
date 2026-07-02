database-check-corrupt = Datoteka kolekcije je oštećena. Obnovite je iz automatske sigurnosne kopije.
database-check-rebuilt = Baza podataka ponovno izgrađena i optimizirana.
database-check-card-properties =
    { $count ->
        [one] Popravljeno { $count } neispravno svojstvo kartice.
        [few] Popravljena { $count } neispravna svojstva kartice.
       *[other] Popravljeno { $count } neispravnih svojstva kartice.
    }
database-check-card-last-review-time-empty =
    { $count ->
        [one] Vrijeme posljednjeg ponavljanja je dodano na { $count } karticu.
        [few] Vrijeme posljednjeg ponavljanja je dodano na { $count } kartice.
       *[other] Vrijeme posljednjeg ponavljanja je dodano na { $count } kartica.
    }
database-check-missing-templates =
    { $count ->
        [one] Izbrisana { $count } kartica s nedostajućim predlošcima.
        [few] Izbrisane { $count } kartice s nedostajućim predlošcima.
       *[other] Izbrisano { $count } kartica s nedostajućim predlošcima.
    }
database-check-field-count =
    { $count ->
        [one] Popravljena { $count } bilješka s netočnim brojem polja.
        [few] Popravljene { $count } bilješke s netočnim brojem polja.
       *[other] Popravljeno { $count } bilješki s netočnim brojem polja.
    }
database-check-card-missing-note =
    { $count ->
        [one] Izbrisana { $count } kartica s nedostajućom bilješkom.
        [few] Izbrisane { $count } kartice s nedostajućom bilješkom.
       *[other] Izbrisano { $count } kartica s nedostajućom bilješkom.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] Obrisana { $count } kartica s podvojenim predloškom.
        [few] Obrisane { $count } kartice s podvojenim predloškom.
       *[other] Obrisano { $count } kartica s podvojenim predloškom.
    }
database-check-missing-decks =
    { $count ->
        [one] Popravljen { $count } nedostajući špil.
        [few] Popravljena { $count } nedostajuća špila.
       *[other] Popravljeno { $count } nedostajućih špilova.
    }
database-check-revlog-properties =
    { $count ->
        [one] Popravljen { $count } unos o ponavljanju s neispravnim svojstvima.
        [few] Popravljena { $count } unosa o ponavljanju s neispravnim svojstvima.
       *[other] Popravljeno { $count } unosa o ponavljanju s neispravnim svojstvima.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] Popravljena { $count } bilješka s neispravnim utf8 znakovima.
        [few] Popravljene { $count } bilješke s neispravnim utf8 znakovima.
       *[other] Popravljeno { $count } bilješki s neispravnim utf8 znakovima.
    }
database-check-fixed-invalid-ids =
    { $count ->
        [one] Popravljen { $count } objekt s vremenskim oznakama u budućnosti.
        [few] Popravljena { $count } objekta s vremenskim oznakama u budućnosti.
       *[other] Popravljeno { $count } objekata s vremenskim oznakama u budućnosti.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Nedostajale su neke vrste bilješki. Bilješkama koje su koristile te vrste dane su nove vrste koje počinju s "db-check", no imena polja i dizajn kartica su se izgubili. Predlažemo da ih obnovite iz automatske sigurnosne kopije.

## Progress info

database-check-checking-integrity = Provjeravanje kolekcije...
database-check-rebuilding = Ponovna izgradnja...
database-check-checking-cards = Provjeravanje kartica...
database-check-checking-notes = Provjeravanje bilješki...
database-check-checking-history = Provjeravanje povijesti...
database-check-title = Provjeri bazu podataka
