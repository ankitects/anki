database-check-corrupt = La colección esta corrompida. Por favor, reestablécela desde un respaldo.
database-check-rebuilt = Base de datos reconstruida y optimizada.
database-check-card-properties =
    { $count ->
        [one] { $count } tarjeta con propiedades erróneas corregida.
       *[other] { $count } tarjetas con propiedades erróneas corregidas.
    }
database-check-missing-templates =
    { $count ->
        [one] { $count } tarjeta sin plantilla eliminada.
       *[other] { $count } tarjetas sin plantilla eliminadas.
    }
database-check-field-count =
    { $count ->
        [one] { $count } tarjeta con número erróneo de campos corregida.
       *[other] { $count } tarjetas con número erróneo de campos corregidas.
    }
database-check-new-card-high-due =
    { $count ->
        [one] Se encontró { $count } tarjeta con el número de revisión >= 1,000,000. Considere reposicionarla en la vista de explorador.
       *[other] Se encontraron { $count } tarjetas con el número de revisión >= 1,000,000. Considere reposicionarlas en la vista de explorador.
    }
database-check-card-missing-note =
    { $count ->
        [one] Se eliminó { $count } tarjeta sin nota.
       *[other] Se eliminaron { $count } tarjetas sin nota.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] Se eliminó { $count } tarjeta con plantilla duplicada.
       *[other] Se eliminaron { $count } tarjetas con plantilla duplicada.
    }
database-check-missing-decks =
    { $count ->
        [one] Se corrigió { $count } mazo faltante.
       *[other] Se corrigieron { $count } mazos faltantes.
    }
database-check-revlog-properties =
    { $count ->
        [one] Se corrigió { $count } entrada de revisión con propiedades no válidas.
       *[other] Se corrigieron { $count } entradas de revisión con propiedades no válidas.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] Se corrigió { $count } nota con caracteres utf8 inválidos.
       *[other] Se corrigieron { $count } notas con caracteres utf8 inválidos.
    }
database-check-fixed-invalid-ids =
    { $count ->
        [one] Se corrigió { $count } objeto con marcas de tiempo en el futuro.
       *[other] Se corrigieron { $count } objetos con marcas de tiempo en el futuro.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Faltan uno o más tipos de notas. A las notas que los usaban se les han asignado nuevos tipos de notas cuyo  nombre empieza con “db-check”, pero los nombres de los campos y el diseño de la tarjeta se han perdido, por lo que es mejor que restaures desde una copia de seguridad automática.

## Progress info

database-check-checking-integrity = Comprobando colección…
database-check-rebuilding = Reconstruyendo…
database-check-checking-cards = Comprobando tarjetas…
database-check-checking-notes = Comprobando notas…
database-check-checking-history = Comprobando historial…
database-check-title = Verificar base de datos
