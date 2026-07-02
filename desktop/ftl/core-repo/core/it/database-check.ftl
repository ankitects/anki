database-check-corrupt = Il file della collezione è corrotto e necessita di essere ripristinato da un backup automatico.
database-check-rebuilt = Database ricostruito e ottimizzato.
database-check-card-properties =
    { $count ->
        [one] Corretta { $count } carta con proprietà non valide.
       *[other] Corrette { $count } carte con proprietà non valide.
    }
database-check-card-last-review-time-empty =
    { $count ->
        [one] Aggiunta data dell'ultima ripetizione a { $count } carta.
       *[other] Aggiunta data dell'ultima ripetizione a { $count } carte.
    }
database-check-missing-templates =
    { $count ->
        [one] Cancellata { $count } carta con modello mancante.
       *[other] Cancellate { $count } carte con modello mancante.
    }
database-check-field-count =
    { $count ->
        [one] Corretta { $count } nota con conteggio dei campi errato.
       *[other] Corrette { $count } note con conteggio dei campi errato.
    }
database-check-new-card-high-due =
    { $count ->
        [one] Trovata { $count } nuova carta con un numero di scadenza >= 1.000.000: valuta di riposizionarla nella schermata "Sfoglia".
       *[other] Trovate { $count } nuove carte con un numero di scadenza >= 1.000.000: valuta di riposizionarle nella schermata "Sfoglia".
    }
database-check-card-missing-note =
    { $count ->
        [one] Eliminata { $count } carta con nota mancante.
       *[other] Eliminate { $count } carte con nota mancante.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] Eliminata { $count } carta con modello duplicato.
       *[other] Eliminate { $count } carte con modello duplicato.
    }
database-check-missing-decks =
    { $count ->
        [one] Risolto { $count } mazzo mancante.
       *[other] Risolti { $count } mazzi mancanti.
    }
database-check-revlog-properties =
    { $count ->
        [one] Risolta { $count } voce di revisione con proprietà non valide.
       *[other] Risolte { $count } voci di revisione con proprietà non valide.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] Risolta { $count } nota con caratteri utf8 non validi.
       *[other] Risolte { $count } note con caratteri utf8 non validi.
    }
database-check-fixed-invalid-ids =
    { $count ->
        [one] Corretto { $count } oggetto con timestamp nel futuro.
       *[other] Corretti { $count } oggetti con timestamp nel futuro.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Mancano uno o più tipi di nota. Alle note di quel tipo sono stati assegnati nuovi tipi di nota che iniziano con "db-check", ma i nomi dei campi e il design delle carte sono andati persi, quindi è consigliabile ripristinarle da un backup automatico.

## Progress info

database-check-checking-integrity = Controllo della collezione in corso...
database-check-rebuilding = Ricostruzione in corso...
database-check-checking-cards = Controllo delle carte in corso...
database-check-checking-notes = Controllo delle note in corso...
database-check-checking-history = Controllo della cronologia in corso...
database-check-title = Controlla database
