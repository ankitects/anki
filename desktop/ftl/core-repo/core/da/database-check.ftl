database-check-corrupt = Samlingen er defekt. Se manualen.
database-check-rebuilt = Databasen er genopbygget og optimeret.
database-check-card-properties =
    { $count ->
        [one] Rettede { $count } kort med ugyldige egenskaber.
       *[other] Rettede { $count } kort med ugyldige egenskaber.
    }
database-check-missing-templates =
    { $count ->
        [one] Slettede { $count } kort med manglende skabelon.
       *[other] Slettede { $count } kort med manglende skabelon.
    }
database-check-field-count =
    { $count ->
        [one] Ordnede { $count } note med forkert felt-tal
       *[other] Ordnede { $count } noter med forkert felt-tal
    }
database-check-new-card-high-due =
    { $count ->
        [one] Fandt { $count } nyt kort med et forfaldstal >= 1,000,000 - overvej at omplacér dette i Gennemse-vinduet
       *[other] Fandt { $count } nye kort med forfaldstal >= 1,000,000 - overvej at omplacér disse i Gennemse-vinduet
    }
database-check-card-missing-note =
    { $count ->
        [one] Slettede { $count } kort med manglende note.
       *[other] Slettede { $count } kort med manglende note.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] Slettede { $count } kort med dupliceret skabelon.
       *[other] Slettede { $count } kort med duplicerede skabeloner.
    }
database-check-missing-decks =
    { $count ->
        [one] Ordnede { $count } manglende kortbunke.
       *[other] Ordnede { $count } manglende kortbunker.
    }
database-check-revlog-properties =
    { $count ->
        [one] Rettede { $count } gennemsyns-indlæg med ugyldige egenskaber.
       *[other] Rettede { $count } gennemsyns-indlæg med ugyldige egenskaber.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] Rettede { $count } note med ugyldige utf8 tegn.
       *[other] Rettede { $count } noter med ugyldige utf8 tegn.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Én eller flere notestyper mangler. Noterne som brugte dem, har fået nye notestyper som starter med "db-check", men feltnavnene og de brugte kortdesigns er forsvundet. Du kan evt. gendanne dem fra en automatisk backup.

## Progress info

database-check-checking-integrity = Gennemgår samling...
database-check-rebuilding = Opbygger...
database-check-checking-cards = Gennemgår kort...
database-check-checking-notes = Gennemgår noter...
database-check-checking-history = Gennemgår historik...
database-check-title = Gennemgår database
