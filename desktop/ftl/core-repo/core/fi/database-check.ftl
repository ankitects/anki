database-check-corrupt = Kokoelmatiedosto on vioittunut. Palauta tiedosto automaattisesta varmuuskopiosta.
database-check-rebuilt = Tietokanta on rakennettu uudelleen ja optimoitu.
database-check-card-properties =
    { $count ->
        [one] Korjattiin { $count } kortti, jossa oli virheellisiä ominaisuuksia.
       *[other] Korjattiin { $count } korttia, joissa oli virheellisiä ominaisuuksia.
    }
database-check-card-last-review-time-empty =
    { $count ->
        [one] Lisättiin viimeisen kertauksen ajankohta { $count } korttiin.
       *[other] Lisättiin viimeisen kertauksen ajankohta { $count } korttiin.
    }
database-check-missing-templates =
    { $count ->
        [one] Poistettiin { $count } kortti, josta puuttui malline.
       *[other] Poistettiin { $count } korttia, joista puuttui malline.
    }
database-check-field-count =
    { $count ->
        [one] Korjattiin { $count } muistiinpano, jossa oli väärä kenttien määrä.
       *[other] Korjattiin { $count } muistiinpanoa, joissa oli väärä kenttien määrä.
    }
database-check-new-card-high-due =
    { $count ->
        [one] Löydettiin { $count } uusi korti, jossa oli erääntymisnumero >= 1 000 000 – harkitse sen sijoittamista uudelleen Selaa-näkymässä.
       *[other] Löydettiin { $count } uutta korttia, joissa oli erääntymisnumero >= 1 000 000 – harkitse niiden sijoittamista uudelleen Selaa-näkymässä.
    }
database-check-card-missing-note =
    { $count ->
        [one] Poistettiin { $count } kortti, johon ei liittynyt muistiinpanoa.
       *[other] Poistettiin { $count } korttia, joihin ei liittynyt muistiinpanoa..
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] Poistettiin { $count } kortti, josta oli kaksoismalli.
       *[other] Poistettiin { $count } korttia, joista oli kaksoismallit.
    }
database-check-missing-decks =
    { $count ->
        [one] Korjattiin { $count } puuttuva pakka.
       *[other] Korjattiin { $count } puuttuvaa pakkaa.
    }
database-check-revlog-properties =
    { $count ->
        [one] Korjattiin { $count } kertausmerkintä, jossa oli virheellisiä ominaisuuksia.
       *[other] Korjattiin { $count } kertausmerkintää, joissa oli virheellisiä ominaisuuksia.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] Korjattiin { $count } muistiinpano, jossa oli virheellisiä UTF-8-merkkejä.
       *[other] Korjattiin { $count } muistiinpanoa, joissa oli virheellisiä UTF-8-merkkejä.
    }
database-check-fixed-invalid-ids =
    { $count ->
        [one] Korjattiin { $count } kohde, jonka aikaleima oli tulevaisuudessa.
       *[other] Korjattiin { $count } kohdetta, joiden aikaleimat olivat tulevaisuudessa.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Yksi tai useampi muistiinpanotyyppi puuttui. Niitä käyttäneille muistiinpanoille on annettu uudet muistiinpanotyypit, joiden nimet alkavat tunnuksella "db-check", mutta kenttien nimet ja kortin ulkoasu ovat kadonneet, joten saattaa olla parempi palauttaa muistiinpanot automaattisesta varmuuskopiosta.

## Progress info

database-check-checking-integrity = Tarkistetaan kokoelmaa...
database-check-rebuilding = Rakennetaan uudelleen...
database-check-checking-cards = Tarkistetaan kortteja...
database-check-checking-notes = Tarkistetaan muistiinpanoja...
database-check-checking-history = Tarkistetaan historiaa...
database-check-title = Tarkista tietokanta
