database-check-corrupt = El fitxer de la col·lecció està malmès. Restaureu la col·lecció des d'una còpia de seguretat automàtica.
database-check-rebuilt = S’ha reconstruït i optimitzat la base de dades.
database-check-card-properties =
    { $count ->
        [one] S’ha corregit una propietat invàlida.
       *[other] S’han corregit { $count } propietats invàlides.
    }
database-check-card-last-review-time-empty =
    { $count ->
        [one] S’ha afegit l’última data de repàs a una targeta.
       *[other] S’ha afegit l’última data de repàs a { $count } targetes.
    }
database-check-missing-templates =
    { $count ->
        [one] S'ha suprimit una targeta sense plantilla.
       *[other] S'han suprimit { $count } targetes sense plantilla.
    }
database-check-field-count =
    { $count ->
        [one] S'ha corregit una targeta que tenia un nombre erroni de camps.
       *[other] S'han corregit { $count } targetes que tenien un nombre erroni de camps.
    }
database-check-new-card-high-due =
    { $count ->
        [one] S'ha trobat una targeta el número de venciment de la qual és d'almenys un milió. Considereu reposicionar-la en la finestra «Navega».
       *[other] S'han trobat { $count } targetes el número de venciment de les quals és d'almenys un milió. Considereu reposicionar-les en la finestra «Navega».
    }
database-check-card-missing-note =
    { $count ->
        [one] S'ha suprimit una targeta sense nota.
       *[other] S'han suprimit { $count } targetes sense nota.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] S'ha suprimit una targeta amb plantilla duplicada.
       *[other] S'han suprimit { $count } targetes amb plantilla duplicada.
    }
database-check-missing-decks =
    { $count ->
        [one] S’ha corregit una baralla que faltava.
       *[other] S’han corregit { $count } baralles que faltaven.
    }
database-check-revlog-properties =
    { $count ->
        [one] S'ha corregit un entrada de repàs amb propietats invàlides.
       *[other] S'han corregit { $count } entrades de repàs amb propietats invàlides.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] S'ha corregit una nota amb caràcters UTF-8 invàlids.
       *[other] S'han corregit { $count } notes amb caràcters UTF-8 invàlids.
    }
database-check-fixed-invalid-ids =
    { $count ->
        [one] S’ha corregit un objecte datat en el futur.
       *[other] S’han corregit { $count } objectes datats en el futur.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Falten un o més tipus de notes. A les notes que les feien servir, s'hi han assignat nous tipus el nom dels quals comença per «db-check». Tanmateix, els noms dels camps i el disseny de la targeta s'han perdut, de manera que us recomanem que reestabliu una còpia de seguretat automàtica.

## Progress info

database-check-checking-integrity = S'està comprovant la col·lecció…
database-check-rebuilding = S'està reconstruint…
database-check-checking-cards = S'estan comprovant les targetes…
database-check-checking-notes = S'estan comprovant les notes…
database-check-checking-history = S’està comprovant l'historial…
database-check-title = Comprova la base de dades
