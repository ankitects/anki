importing-failed-debug-info = Import failed. Debugging info:
importing-aborted = Aborted: { $val }
importing-added-duplicate-with-first-field = Added duplicate with first field: { $val }
importing-all-supported-formats = All supported formats { $val }
importing-allow-html-in-fields = Allow HTML in fields
importing-anki-files-are-from-a-very = .anki files are from a very old version of Anki. You can import them with add-on 175027074 or with Anki 2.0, available on the Anki website.
importing-anki2-files-are-not-directly-importable = .anki2 files are not directly importable - please import the .apkg or .zip file you have received instead.
importing-appeared-twice-in-file = Appeared twice in file: { $val }
importing-by-default-anki-will-detect-the = By default, Anki will detect the character between fields, such as a tab, comma, and so on. If Anki is detecting the character incorrectly, you can enter it here. Use \t to represent tab.
importing-change = Change
importing-colon = Colon
importing-comma = Comma
importing-empty-first-field = Empty first field: { $val }
importing-field-separator = Field separator
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
importing-mnemosyne-20-deck-db = Mnemosyne 2.0 Deck (*.db)
importing-multicharacter-separators-are-not-supported-please = Multi-character separators are not supported. Please enter one character only.
importing-notes-added-from-file = Notes added from file: { $val }
importing-notes-found-in-file = Notes found in file: { $val }
importing-notes-skipped-as-theyre-already-in = Notes skipped, as up-to-date copies are already in your collection: { $val }
importing-notes-skipped-update-due-to-notetype = Notes not updated, as notetype has been modified since you first imported the notes: { $val }
importing-notes-updated-as-file-had-newer = Notes updated, as file had newer version: { $val }
importing-packaged-anki-deckcollection-apkg-colpkg-zip = Packaged Anki Deck/Collection (*.apkg *.colpkg *.zip)
importing-pauker-18-lesson-paugz = Pauker 1.8 Lesson (*.pau.gz)
# the '|' character
importing-pipe = Pipe
importing-rows-had-num1d-fields-expected-num2d = '{ $row }' had { $found } fields, expected { $expected }
importing-selected-file-was-not-in-utf8 = Selected file was not in UTF-8 format. Please see the importing section of the manual.
importing-semicolon = Semicolon
importing-skipped = Skipped
importing-supermemo-xml-export-xml = Supermemo XML export (*.xml)
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
importing-notetype-and-deck = Notetype and deck
importing-cards-added =
    { $count ->
        [one] { $count } card added.
       *[other] { $count } cards added.
    }

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

importing-importing-collection = Importing collection...
importing-unable-to-import-filename = Unable to import { $filename }: file type not supported
importing-notes-that-could-not-be-imported = Notes that could not be imported as note type has changed: { $val }
