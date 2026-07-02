database-check-rebuilt = Base de datos reconstruída e optimizada.
database-check-card-properties =
    { $count ->
        [one] Arranxouse { $count } tarxeta con propiedades non válidas.
       *[other] Arranxáronse { $count } tarxetas con propiedades non válidas.
    }
database-check-missing-templates =
    { $count ->
        [one] Eliminouse { $count } tarxeta sen modelo.
       *[other] Elimináronse { $count } tarxetas sen modelo.
    }
database-check-card-missing-note =
    { $count ->
        [one] Eliminouse { $count } tarxeta sen nota.
       *[other] Elimináronse { $count } tarxetas sen nota.
    }

## Progress info

database-check-checking-integrity = Comprobando colección...
database-check-rebuilding = Reconstruíndo...
database-check-checking-cards = Comprobando tarxetas...
database-check-checking-notes = Comprobando notas...
database-check-checking-history = Comprobando historial...
database-check-title = Comprobar base de datos
