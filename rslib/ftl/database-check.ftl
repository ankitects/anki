database-check-corrupt = Collection file is corrupt. Please restore from an automatic backup.
database-check-rebuilt = Database rebuilt and optimized.
database-check-card-properties =
    { $count ->
        [one] Fixed { $count } invalid card property.
       *[other] Fixed { $count } invalid card properties.
    }
database-check-missing-templates =
    { $count ->
        [one] Deleted { $count } card with missing template.
       *[other] Deleted { $count } cards with missing template.
    }
database-check-field-count =
    { $count ->
        [one] Fixed { $count } note with wrong field count.
       *[other] Fixed { $count } notes with wrong field count.
    }
database-check-new-card-high-due =
    { $count ->
        [one] Found { $count } new card with a due number >= 1,000,000 - consider repositioning it in the Browse screen.
       *[other] Found { $count } new cards with a due number >= 1,000,000 - consider repositioning them in the Browse screen.
    }
database-check-card-missing-note =
    { $count ->
        [one] Deleted { $count } card with missing note.
       *[other] Deleted { $count } cards with missing note.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] Deleted { $count } card with duplicate template.
       *[other] Deleted { $count } cards with duplicate template.
    }
database-check-missing-decks =
    { $count ->
        [one] Fixed { $count } missing deck.
       *[other] Fixed { $count } missing decks.
    }
database-check-revlog-properties =
    { $count ->
        [one] Fixed { $count } review entry with invalid properties.
       *[other] Fixed { $count } review entries with invalid properties.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] Fixed { $count } note with invalid utf8 characters.
       *[other] Fixed { $count } notes with invalid utf8 characters.
    }
# "db-check" is always in English
database-check-notetypes-recovered = One or more notetypes were missing. The notes that used them have been given new notetypes starting with "db-check", but field names and card design have been lost, so you may be better off restoring from an automatic backup.

## Progress info

database-check-checking-integrity = Checking collection...
database-check-rebuilding = Rebuilding...
database-check-checking-cards = Checking cards...
database-check-checking-notes = Checking notes...
database-check-checking-history = Checking history...
database-check-title = Check Database
