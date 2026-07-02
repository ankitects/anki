database-check-corrupt = Bildumaren fitxategia hondatuta dago. Leheneratu bilduma babeskopia automatiko batetik.
database-check-rebuilt = Datu-basea berreraiki eta optimizatuta.
database-check-card-properties =
    { $count ->
        [one] Txarteletako propietate baliogabe bat konponduta.
       *[other] Txarteletako { $count } propietate baliogabe konponduta.
    }
database-check-missing-templates =
    { $count ->
        [one] Txantiloirik gabeko txartel bat ezabatuta.
       *[other] Txantiloirik gabeko { $count } txartel ezabatuta.
    }
database-check-field-count =
    { $count ->
        [one] Eremu-kopuru okerra zuen ohar bat konponduta.
       *[other] Eremu-kopuru okerra zuten { $count } ohar konponduta.
    }
database-check-new-card-high-due =
    { $count ->
        [one] Txartel berri bat aurkitu da 1.000.000 baino lehentasun-zenbaki handiagoarekin. Agian posizioz aldatu behar zenuke arakatzailean.
       *[other] { $count } txartel berri aurkitu dira 1.000.000 baino lehentasun-zenbaki handiagoarekin. Agian posizioz aldatu behar zenituzke arakatzailean.
    }
database-check-card-missing-note =
    { $count ->
        [one] Oharrik gabeko txartel bat ezabatuta.
       *[other] Oharrik gabeko { $count } txartel ezabatuta.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] Txantiloia errepikatuta zuen txartel bat ezabatuta.
       *[other] Txantiloia errepikatuta zuten { $count } txartel ezabatuta.
    }
database-check-missing-decks =
    { $count ->
        [one] Falta zen sorta bat konponduta.
       *[other] Falta ziren { $count } sorta konponduta.
    }
database-check-revlog-properties =
    { $count ->
        [one] Propietate baliogabeak zituen berrikuspen bat konponduta.
       *[other] Propietate baliogabeak zituzten { $count } berrikuspen konponduta.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] UTF-8 karaktere baliogabeak zituen ohar bat konponduta.
       *[other] UTF-8 karaktere baliogabeak zituzten { $count } ohar konponduta.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Ohar-mota bat edo gehiago falta ziren. Erabiltzen zituzten oharrei "db-check" hasiera duten ohar-mota berriak eman zaizkie, baina eremuen izenak eta txartelen diseinuak galdu dira. Beraz, agian hobe duzu babeskopia automatiko batetik lehenaratu.

## Progress info

database-check-checking-integrity = Bilduma egiaztatzen...
database-check-rebuilding = Berreraikitzen...
database-check-checking-cards = Txartelak egiaztatzen...
database-check-checking-notes = Oharrak egiaztatzen...
database-check-checking-history = Historia egiaztatzen...
database-check-title = Egiaztatu datu-basea
