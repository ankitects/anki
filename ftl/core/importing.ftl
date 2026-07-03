importing-failed-debug-info = Import failed. Debugging info:
importing-aborted = Aborted: { $val }
importing-added-duplicate-with-first-field = Added duplicate with first field: { $val }
importing-all-supported-formats = All supported formats { $val }
importing-allow-html-in-fields = Allow HTML in fields
importing-anki-files-are-from-a-very = .anki files are from a very old version of Anki. You can import them with add-on 175027074 or with Anki 2.0, available on the Anki website.
importing-anki2-files-are-not-directly-importable = .anki2 files are not directly importable - please import the .apkg or .zip file you have received instead.
importing-appeared-twice-in-file = Appeared twice in file: { $val }
importing-by-default-anki-will-detect-the = By default, Anki will detect the character between fields, such as a tab, comma, and so on. If Anki is detecting the character incorrectly, you can enter it here. Use \t to represent tab.
importing-cannot-merge-notetypes-of-different-kinds =
    Cloze note types cannot be merged with regular note types.
    You may still import the file with '{ importing-merge-notetypes }' disabled.
importing-change = Change
importing-colon = Colon
importing-comma = Comma
importing-empty-first-field = Empty first field: { $val }
importing-field-separator = Field separator
importing-field-separator-guessed =  Field separator (guessed)
importing-field-mapping = Field mapping
importing-field-of-file-is = Field <b>{ $val }</b> of file is:
importing-fields-separated-by = Fields separated by: { $val }
importing-file-must-contain-field-column = File must contain at least one column that can be mapped to a note field.
importing-file-version-unknown-trying-import-anyway = File version unknown, trying import anyway.
importing-first-field-matched = First field matched: { $val }
importing-identical = Identical
importing-ignore-field = Ignore field
importing-ignore-lines-where-first-field-matches = Ignore lines where first field matches existing note
importing-ignored = <ignored>
importing-import-even-if-existing-note-has = Import even if existing note has same first field
importing-import-options = Import options
importing-importing-complete = Importing complete.
importing-invalid-file-please-restore-from-backup = Invalid file. Please restore from backup.
importing-map-to = Map to { $val }
importing-map-to-tags = Map to Tags
importing-mapped-to = mapped to <b>{ $val }</b>
importing-mapped-to-tags = mapped to <b>Tags</b>
# the action of combining two existing note types to create a new one
importing-merge-notetypes = Merge note types
importing-merge-notetypes-help =
    If checked, and you or the deck author altered the schema of a note type, Anki will
    merge the two versions instead of keeping both.
    
    Altering a note type's schema means adding, removing, or reordering fields or templates,
    or changing the sort field.
    As a counterexample, changing the front side of an existing template does *not* constitute
    a schema change.
    
    Warning: This will require a one-way sync, and may mark existing notes as modified.
importing-mnemosyne-20-deck-db = Mnemosyne 2.0 Deck (*.db)
importing-multicharacter-separators-are-not-supported-please = Multi-character separators are not supported. Please enter one character only.
importing-new-deck-will-be-created = A new deck will be created: { $name }
importing-notes-added-from-file = Notes added from file: { $val }
importing-notes-found-in-file = Notes found in file: { $val }
importing-notes-skipped-as-theyre-already-in = Notes skipped, as up-to-date copies are already in your collection: { $val }
importing-notes-skipped-update-due-to-notetype = Notes not updated, as note type has been modified since you first imported the notes: { $val }
importing-notes-updated-as-file-had-newer = Notes updated, as file had newer version: { $val }
importing-include-reviews = Include reviews
importing-also-import-progress = Import any learning progress
importing-with-deck-configs = Import any deck presets
importing-updates = Updates
importing-include-reviews-help =
    If enabled, any previous reviews that the deck sharer included will also be imported.
    Otherwise, all cards will be imported as new cards, and any "leech" or "marked"
    tags will be removed.
importing-with-deck-configs-help =
    If enabled, any deck options that the deck sharer included will also be imported.
    Otherwise, all decks will be assigned the default preset.
importing-packaged-anki-deckcollection-apkg-colpkg-zip = Packaged Anki Deck/Collection (*.apkg *.colpkg *.zip)
# the '|' character
importing-pipe = Pipe
# Warning displayed when the csv import preview table is clipped (some columns were hidden)
# $count is intended to be a large number (1000 and above)
importing-preview-truncated =
    { $count ->
        *[other] Only the first { $count } columns are shown. If this doesn't seem right, try changing the field separator.
    }
importing-rows-had-num1d-fields-expected-num2d = '{ $row }' had { $found } fields, expected { $expected }
importing-selected-file-was-not-in-utf8 = Selected file was not in UTF-8 format. Please see the importing section of the manual.
importing-semicolon = Semicolon
importing-skipped = Skipped
importing-tab = Tab
importing-tag-modified-notes = Tag modified notes:
importing-text-separated-by-tabs-or-semicolons = Text separated by tabs or semicolons (*)
importing-the-first-field-of-the-note = The first field of the note type must be mapped.
importing-the-provided-file-is-not-a = The provided file is not a valid .apkg file.
importing-this-file-does-not-appear-to = This file does not appear to be a valid .apkg file. If you're getting this error from a file downloaded from AnkiWeb, chances are that your download failed. Please try again, and if the problem persists, please try again with a different browser.
importing-this-will-delete-your-existing-collection = This will delete your existing collection and replace it with the data in the file you're importing. Are you sure?
importing-unable-to-import-from-a-readonly = Unable to import from a read-only file.
importing-unknown-file-format = Unknown file format.
importing-update-existing-notes-when-first-field = Update existing notes when first field matches
importing-updated = Updated
importing-update-if-newer = If newer
importing-update-always = Always
importing-update-never = Never
importing-update-notes = Update notes
importing-update-notes-help =
    When to update an existing note in your collection. By default, this is only done
    if the matching imported note was more recently modified.
importing-update-notetypes = Update note types
importing-update-notetypes-help =
    When to update an existing note type in your collection. By default, this is only done
    if the matching imported note type was more recently modified. Changes to template text
    and styling can always be imported, but for schema changes (e.g. the number or order of
    fields has changed), the '{ importing-merge-notetypes }' option will also need to be enabled.
importing-note-added =
    { $count ->
        [one] { $count } note added
       *[other] { $count } notes added
    }
importing-note-imported =
    { $count ->
        [one] { $count } note imported.
       *[other] { $count } notes imported.
    }
importing-note-unchanged =
    { $count ->
        [one] { $count } note unchanged
       *[other] { $count } notes unchanged
    }
importing-note-updated =
    { $count ->
        [one] { $count } note updated
       *[other] { $count } notes updated
    }
importing-processed-media-file =
    { $count ->
        [one] Imported { $count } media file
       *[other] Imported { $count } media files
    }
importing-importing-file = Importing file...
importing-extracting = Extracting data...
importing-gathering = Gathering data...
importing-failed-to-import-media-file = Failed to import media file: { $debugInfo }
importing-processed-notes =
    { $count ->
        [one] Processed { $count } note...
       *[other] Processed { $count } notes...
    }
importing-processed-cards =
    { $count ->
        [one] Processed { $count } card...
       *[other] Processed { $count } cards...
    }
importing-existing-notes = Existing notes
# "Existing notes: Duplicate" (verb)
importing-duplicate = Duplicate
# "Existing notes: Preserve" (verb)
importing-preserve = Preserve
# "Existing notes: Update" (verb)
importing-update = Update
importing-tag-all-notes = Tag all notes
importing-tag-updated-notes = Tag updated notes
importing-file = File
# "Match scope: notetype / notetype and deck". Controls how duplicates are matched.
importing-match-scope = Match scope
# Used with the 'match scope' option
importing-notetype-and-deck = Note type and deck
importing-cards-added =
    { $count ->
        [one] { $count } card added.
       *[other] { $count } cards added.
    }
importing-file-empty = The file you selected is empty.
importing-notes-added =
    { $count ->
        [one] { $count } new note imported.
       *[other] { $count } new notes imported.
    }
importing-notes-updated =
    { $count ->
        [one] { $count } note was used to update existing ones.
       *[other] { $count } notes were used to update existing ones.
    }
importing-existing-notes-skipped =
    { $count ->
        [one] { $count } note already present in your collection.
       *[other] { $count } notes already present in your collection.
    }
importing-notes-failed =
    { $count ->
        [one] { $count } note could not be imported.
        *[other] { $count } notes could not be imported.
    }
importing-conflicting-notes-skipped =
    { $count ->
        [one] { $count } note was not imported, because its note type has changed.
       *[other] { $count } notes were not imported, because their note type has changed.
    }
importing-conflicting-notes-skipped2 =
    { $count ->
        [one] { $count } note was not imported, because its note type has changed, and '{ importing-merge-notetypes }' was not enabled.
        *[other] { $count } notes were not imported, because their note type has changed, and '{ importing-merge-notetypes }' was not enabled.
    }
importing-import-log = Import Log
importing-no-notes-in-file = No notes found in file.
importing-notes-found-in-file2 =
    { $notes ->
        [one] { $notes } note
       *[other] { $notes } notes
    } found in file. Of those:
importing-show = Show
importing-details = Details
importing-status = Status
importing-duplicate-note-added = Duplicate note added
importing-added-new-note = New note added
importing-existing-note-skipped = Note skipped, as an up-to-date copy is already in your collection
importing-note-skipped-update-due-to-notetype = Note not updated, as note type has been modified since you first imported the note
importing-note-skipped-update-due-to-notetype2 = Note not updated, as note type has been modified since you first imported the note, and '{ importing-merge-notetypes }' was not enabled
importing-note-updated-as-file-had-newer = Note updated, as file had newer version
importing-note-skipped-due-to-missing-notetype = Note skipped, as its notetype was missing
importing-note-skipped-due-to-missing-deck = Note skipped, as its deck was missing
importing-note-skipped-due-to-empty-first-field = Note skipped, as its first field is empty
importing-field-separator-help =
    The character separating fields in the text file. You can use the preview to check
    if the fields are separated correctly.
    
    Please note that if this character appears in any field itself, the field has to be
    quoted accordingly to the CSV standard. Spreadsheet programs like LibreOffice will
    do this automatically.

    It cannot be changed if the text file forces use of a specific separator via a file header.
    If a file header is not present, Anki will try to guess what the separator is.
importing-allow-html-in-fields-help =
    Enable this if the file contains HTML formatting. E.g. if the file contains the string
    '&lt;br&gt;', it will appear as a line break on your card. On the other hand, with this
    option disabled, the literal characters '&lt;br&gt;' will be rendered.
importing-notetype-help =
    Newly-imported notes will have this note type, and only existing notes with this
    note type will be updated.
    
    You can choose which fields in the file correspond to which note type fields with the
    mapping tool.
importing-deck-help = Imported cards will be placed in this deck.
importing-existing-notes-help =
    What to do if an imported note matches an existing one.
    
    - `{ importing-update }`: Update the existing note.
    - `{ importing-preserve }`: Do nothing.
    - `{ importing-duplicate }`: Create a new note.
importing-match-scope-help =
    Only existing notes with the same note type will be checked for duplicates. This can
    additionally be restricted to notes with cards in the same deck.
importing-tag-all-notes-help =
    These tags will be added to both newly-imported and updated notes.
importing-tag-updated-notes-help = These tags will be added to any updated notes.
importing-overview = Overview

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

importing-importing-collection = Importing collection...
importing-unable-to-import-filename = Unable to import { $filename }: file type not supported
importing-notes-that-could-not-be-imported = Notes that could not be imported as note type has changed: { $val }
importing-added = Added
importing-pauker-18-lesson-paugz = Pauker 1.8 Lesson (*.pau.gz)
importing-supermemo-xml-export-xml = Supermemo XML export (*.xml)
