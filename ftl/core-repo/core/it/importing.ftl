importing-failed-debug-info = Importazione non riuscita. Informazioni per il debug:
importing-aborted = Interrotto: { $val }
importing-added-duplicate-with-first-field = Aggiunto duplicato con primo campo: { $val }
importing-all-supported-formats = Tutti i formati supportati { $val }
importing-allow-html-in-fields = Consenti l'HTML nei campi
importing-anki-files-are-from-a-very = I file .anki sono di una versione molto vecchia di Anki. È possibile importarli utilizzando Anki 2.0, disponibile sul sito di Anki.
importing-anki2-files-are-not-directly-importable = I file .anki2 non sono importabili direttamente; importa invece i file .apkg o .zip che hai ricevuto.
importing-appeared-twice-in-file = Apparso due volte nel file: { $val }
importing-by-default-anki-will-detect-the = Di default, vengono rilevati i caratteri tra i campi, come tabulazioni, virgole, ecc. Se i caratteri non vengono rilevati correttamente, puoi inserirli qui. Usa \t per rappresentare le tabulazioni.
importing-cannot-merge-notetypes-of-different-kinds = I tipi di nota "Cloze" non possono essere uniti con i tipi di nota regolari. Puoi comunque importare il file disabilitando '{ importing-merge-notetypes }'.
importing-change = Modifica
importing-colon = Due punti
importing-comma = Virgola
importing-empty-first-field = Primo campo vuoto: { $val }
importing-field-separator = Separatore di campo
importing-field-separator-guessed = Separatore di campo (rilevato)
importing-field-mapping = Mappatura dei campi
importing-field-of-file-is = Il campo <b>{ $val }</b> del file è:
importing-fields-separated-by = Campi separati da: { $val }
importing-file-must-contain-field-column = Il file deve contenere almeno una colonna che possa essere mappata al campo di una nota.
importing-file-version-unknown-trying-import-anyway = Versione del file sconosciuta, tento ugualmente l'importazione.
importing-first-field-matched = Primo campo corrispondente: { $val }
importing-identical = Identico
importing-ignore-field = Ignora campo
importing-ignore-lines-where-first-field-matches = Ignora le righe il cui primo campo corrisponde a una nota esistente
importing-ignored = <ignorato>
importing-import-even-if-existing-note-has = Importa anche se una nota esistente ha lo stesso primo campo
importing-import-options = Opzioni di importazione
importing-importing-complete = Importazione completata.
importing-invalid-file-please-restore-from-backup = File non valido. Ripristina dal backup.
importing-map-to = Mappa su { $val }
importing-map-to-tags = Mappa verso le etichette
importing-mapped-to = mappato su <b>{ $val }</b>
importing-mapped-to-tags = mappato verso le <b>etichette</b>
# the action of combining two existing note types to create a new one
importing-merge-notetypes = Unisci tipi di nota
importing-merge-notetypes-help =
    Se selezionato ed è stato modificato lo schema di un tipo di nota, verranno unite le due versioni
    anziché mantenerle entrambe.
    
    Modificare lo schema di un tipo di nota significa aggiungere, rimuovere o riordinare i campi o i modelli,
    o cambiare il campo di ordinamento.
    
    Come controesempio, cambiare il lato fronte di un modello già esistente *non* costituisce una modifica dello schema.
    
    Avviso: questa modifica richiederà una sincronizzazione unidirezionale e alcune note già esistenti potrebbero essere contrassegnate come 'modificate'.
importing-mnemosyne-20-deck-db = Mazzo di Mnemosyne 2.0 (*.db)
importing-multicharacter-separators-are-not-supported-please = Separatori multi-carattere non sono supportati. Inserisci un solo carattere.
importing-new-deck-will-be-created = Verrà creato un nuovo mazzo: { $name }
importing-notes-added-from-file = Note aggiunte dal file: { $val }
importing-notes-found-in-file = Note trovate nel file: { $val }
importing-notes-skipped-as-theyre-already-in = Note ignorate, poiché una copia aggiornata è già presente nella propria collezione: { $val }
importing-notes-skipped-update-due-to-notetype = Note non aggiornate, poiché il tipo di nota è stato modificato dopo l'importazione originale: { $val }
importing-notes-updated-as-file-had-newer = Note aggiornate, in quanto il file contiene una nuova versione: { $val }
importing-include-reviews = Includi le ripetizioni
importing-also-import-progress = Importa anche eventuali progressi di apprendimento
importing-with-deck-configs = Importa eventuali preimpostazioni dei mazzi
importing-updates = Aggiornamenti
importing-include-reviews-help =
    Se abilitato, saranno importate anche tutte le ripetizioni precedenti incluse dall'autore del mazzo. 
    In caso contrario, tutte le carte saranno importate come nuove carte.
importing-with-deck-configs-help =
    Se abilitato, verranno importate anche eventuali impostazioni (preimpostazione) incluse dall'autore del mazzo. 
    In caso contrario, tutti i mazzi verranno assegnati alla preimpostazione predefinita.
importing-packaged-anki-deckcollection-apkg-colpkg-zip = Pacchetto mazzo/collezione Anki (*.apkg *.colpkg *.zip)
# the '|' character
importing-pipe = Barra verticale (Pipe)
# Warning displayed when the csv import preview table is clipped (some columns were hidden)
# $count is intended to be a large number (1000 and above)
importing-preview-truncated =
    { $count ->
        [one] Viene mostrata solo la prima colonna. Se non ti sembra corretto, prova a cambiare il separatore di campo.
       *[other] Vengono mostrate solo le prime { $count } colonne. Se non ti sembra corretto, prova a cambiare il separatore di campo.
    }
importing-rows-had-num1d-fields-expected-num2d = '{ $row }' contiene { $found } campi, ma ne erano previsti { $expected }
importing-selected-file-was-not-in-utf8 = Il file selezionato non è nel formato UTF-8. Consulta la sezione Importazione nel manuale.
importing-semicolon = Punto e virgola
importing-skipped = Ignorato
importing-tab = Tabulazione
importing-tag-modified-notes = Etichetta le note modificate:
importing-text-separated-by-tabs-or-semicolons = Testo separato da tabulazioni o punti e virgola (*)
importing-the-first-field-of-the-note = Il primo campo della nota dev'essere mappato.
importing-the-provided-file-is-not-a = Il file non è un file .apkg valido.
importing-this-file-does-not-appear-to = Questo file sembra non essere un file .apkg valido. Se ricevi questo errore con un file scaricato da AnkiWeb, è probabile che il download non sia riuscito. Riprova e, se il problema rimane, prova di nuovo con un altro browser.
importing-this-will-delete-your-existing-collection = La collezione esistente verrà eliminata e sostituita con i dati del file che si sta importando. Confermare?
importing-unable-to-import-from-a-readonly = Impossibile importare da un file di sola lettura.
importing-unknown-file-format = Formato del file sconosciuto.
importing-update-existing-notes-when-first-field = Aggiorna le note esistenti se il primo campo corrisponde
importing-updated = Aggiornato
importing-update-if-newer = Se più nuova
importing-update-always = Sempre
importing-update-never = Mai
importing-update-notes = Aggiorna le note
importing-update-notes-help =
    Determina quando aggiornare una nota già esistente nella propria collezione. Di default, 
    ciò avviene solo se la nota corrispondente che sta venendo importata è stata modificata più di recente.
importing-update-notetypes = Aggiorna i tipi di nota
importing-update-notetypes-help =
    Determina quando aggiornare un tipo di nota già esistente nella collezione.
    Di default, ciò avviene solo se il tipo di nota che sta venendo importato è stato modificato più di recente. 
    Le modifiche al testo e allo stile dei modelli possono sempre essere importate, ma per le modifiche dello schema (ad esempio, modifiche al numero o all'ordine dei campi) è necessario abilitare l'opzione '{ importing-merge-notetypes }'.
importing-note-added =
    { $count ->
        [one] { $count } nota aggiunta
       *[other] { $count } note aggiunte
    }
importing-note-imported =
    { $count ->
        [one] { $count } nota importata.
       *[other] { $count } note importate.
    }
importing-note-unchanged =
    { $count ->
        [one] { $count } nota invariata
       *[other] { $count } note invariate
    }
importing-note-updated =
    { $count ->
        [one] { $count } nota aggiornata
       *[other] { $count } note aggiornate
    }
importing-processed-media-file =
    { $count ->
        [one] Elaborato { $count } file multimediale
       *[other] Elaborati { $count } file multimediali
    }
importing-importing-file = Importazione del file in corso...
importing-extracting = Estrazione dei dati in corso...
importing-gathering = Raccolta dei dati in corso...
importing-failed-to-import-media-file = Impossibile importare il file multimediale: { $debugInfo }
importing-processed-notes =
    { $count ->
        [one] Elaborata { $count } nota...
       *[other] Elaborate { $count } note...
    }
importing-processed-cards =
    { $count ->
        [one] Elaborata { $count } carta...
       *[other] Elaborate { $count } carte...
    }
importing-existing-notes = Note esistenti
# "Existing notes: Duplicate" (verb)
importing-duplicate = Duplica
# "Existing notes: Preserve" (verb)
importing-preserve = Mantieni
# "Existing notes: Update" (verb)
importing-update = Aggiorna
importing-tag-all-notes = Etichetta tutte le note
importing-tag-updated-notes = Etichetta le note aggiornate
importing-file = File
# "Match scope: notetype / notetype and deck". Controls how duplicates are matched.
importing-match-scope = Ambito di confronto
# Used with the 'match scope' option
importing-notetype-and-deck = Tipo di nota e mazzo
importing-cards-added =
    { $count ->
        [one] Aggiunta { $count } carta.
       *[other] Aggiunte { $count } carte.
    }
importing-file-empty = Il file selezionato è vuoto.
importing-notes-added =
    { $count ->
        [one] Importata { $count } nuova nota.
       *[other] Importate { $count } nuove note.
    }
importing-notes-updated =
    { $count ->
        [one] { $count } nota è stata usata per aggiornarne altre già presenti nella collezione.
       *[other] { $count } note sono state usate per aggiornarne altre già presenti nella collezione.
    }
importing-existing-notes-skipped =
    { $count ->
        [one] { $count } nota già presente nella collezione.
       *[other] { $count } note già presenti nella collezione.
    }
importing-notes-failed =
    { $count ->
        [one] Impossibile importare { $count } nota
       *[other] Impossibile importare { $count } note
    }
importing-conflicting-notes-skipped =
    { $count ->
        [one] { $count } nota non è stata importata poiché il suo tipo di nota è cambiato.
       *[other] { $count } note non sono state importate, poiché il loro tipo di nota è cambiato.
    }
importing-conflicting-notes-skipped2 =
    { $count ->
        [one] { $count } nota non è stata importata perché il suo tipo di nota è cambiato e '{ importing-merge-notetypes }' non era abilitato.
       *[other] { $count } note non sono state importate perché il loro tipo di nota è cambiato e '{ importing-merge-notetypes }' non era abilitato.
    }
importing-import-log = Registro delle Importazioni
importing-no-notes-in-file = Nessuna nota trovata nel file.
importing-notes-found-in-file2 =
    { $notes ->
        [one] { $notes } nota trovata nel file. In particolare:
       *[other] { $notes } note trovate nel file. In particolare:
    }
importing-show = Mostra
importing-details = Dettagli
importing-status = Stato
importing-duplicate-note-added = Aggiunta nota duplicata.
importing-added-new-note = Aggiunta nuova nota.
importing-existing-note-skipped = Nota ignorata, poiché una copia aggiornata è già presente nella collezione.
importing-note-skipped-update-due-to-notetype = Nota non aggiornata, poiché il tipo di nota è stato modificato dopo l'importazione originale: { $val }
importing-note-skipped-update-due-to-notetype2 = Nota non aggiornata, poiché il tipo di nota è stato modificato dopo l'importazione originale e "{ importing-merge-notetypes }" non era abilitato
importing-note-updated-as-file-had-newer = Note aggiornate, in quanto il file contiene una nuova versione: { $val }
importing-note-skipped-due-to-missing-notetype = Nota ignorata, poiché il suo tipo di nota era mancante
importing-note-skipped-due-to-missing-deck = Nota ignorata, poiché il suo mazzo era mancante
importing-note-skipped-due-to-empty-first-field = Nota ignorata, poiché il suo primo campo è vuoto
importing-field-separator-help =
    Il carattere che separa i campi nel file di testo. Puoi utilizzare l'anteprima per verificare se i campi sono separati correttamente.
    
    Tieni conto che se questo carattere compare esso stesso all'interno di un qualsiasi campo,
    tale campo dovrà essere delimitato/racchiuso tra virgolette secondo lo standard CSV. 
    I programmi per fogli di calcolo come LibreOffice lo faranno automaticamente.
importing-allow-html-in-fields-help =
    Abilitare questa opzione se il file contiene formattazione HTML. Ad esempio, se il file contiene la stringa
    "&lt;br&gt;", essa verrà visualizzata nella carta come un'interruzione di riga. D'altra parte, se non si abilita
    questa opzione, verranno mostrati invece i caratteri letterali "&lt;br&gt;".
importing-notetype-help =
    Le note appena importate avranno questo tipo di nota; inoltre, verranno aggiornate solo le note già esistenti che appartengono a questo tipo di nota.
    
    Puoi scegliere quali campi nel file corrispondono ai campi del tipo di nota utilizzando lo strumento di mappatura.
importing-deck-help = Le carte importate saranno collocate in questo mazzo.
importing-existing-notes-help =
    Cosa fare se una nota che sta venendo importata corrisponde ad una nota già esistente.
    
    - `{ importing-update }`: Aggiorna la nota già esistente.
    - `{ importing-preserve }`: Non fare nulla.
    - `{ importing-duplicate }`: Crea una nuova nota.
importing-match-scope-help =
    Nella ricerca dei duplicati, verranno controllate solo le note già esistenti che appartengono allo stesso tipo di nota.
    Il campo di ricerca può essere ulteriormente ristretto alle note le cui carte si trovano nello stesso mazzo.
importing-tag-all-notes-help = Queste etichette verranno aggiunte sia alle note appena importate che a quelle aggiornate.
importing-tag-updated-notes-help = Queste etichette saranno aggiunte ad ogni nota aggiornata.
importing-overview = Panoramica

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

importing-importing-collection = Importazione della collezione...
importing-unable-to-import-filename = Impossibile importare { $filename }: tipo di file non supportato
importing-notes-that-could-not-be-imported = Note che non è stato possibile importare poiché è cambiato il tipo di nota: { $val }
importing-added = Aggiunto
importing-pauker-18-lesson-paugz = Lezione di Pauker 1.8 (*.pau.gz)
importing-supermemo-xml-export-xml = Supermemo esportato in XML (*.xml)
