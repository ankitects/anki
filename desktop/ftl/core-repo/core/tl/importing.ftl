importing-failed-debug-info = Pumalya ang pag-import. Info ng debugging:
importing-aborted = Na-abort: { $val }
importing-added-duplicate-with-first-field = Nadagdag na duplicat kasama ang unang field: { $val }
importing-all-supported-formats = Lahat ng supported na format { $val }
importing-allow-html-in-fields = Payagan ang HTML sa mga field
importing-anki-files-are-from-a-very = ang mga .anki na file ay mula sa pinakalumang version ng Anki. Puwede mong ma-import ang mga ito gamit ang add-on na 175027074 o gamit ang Anki2.0, na available sa website ng Anki.
importing-anki2-files-are-not-directly-importable = hindi puwedeng direktang ma-import ang mga .anki2 na file - sa halip, paki-import ang .apkg o .zip file na natanggap mo.
importing-appeared-twice-in-file = Dalawang beses na lumabas sa file: { $val }
importing-by-default-anki-will-detect-the = By default, made-detect ng Anki ang character sa pagitan ng mga field, tulad ng tab, comma, at iba pa. Kung mali ang pag-detect ng Anki sa character, puwede mong i-enter ito rito. Gamitin ang  \t para i-represent ang tab.
importing-change = Baguhin
importing-colon = Colon
importing-comma = Comma
importing-empty-first-field = Tanggalin ang laman ng unang field: { $val }
importing-field-separator = Naghihiwalay ng field
importing-field-mapping = Pagma-map ng field
importing-field-of-file-is = Ang field na <b>{ $val }</b> ng file ay:
importing-fields-separated-by = Ang field ay nahihiwalay ng: { $val }
importing-file-must-contain-field-column = Dapat atleast may isang column ang file na puwedeng ma-map sa isang note field.
importing-file-version-unknown-trying-import-anyway = Unknown ang version ng file, pero sinusubukan pa rin na i-import.
importing-first-field-matched = Unang field na na-match: { $val }
importing-identical = Magkapareho
importing-ignore-field = I-ignore ang field
importing-ignore-lines-where-first-field-matches = I-ignore ang mga line kung saan ang unang field ay nagma-match sa existing na note
importing-ignored = <ignored>
importing-import-even-if-existing-note-has = I-import kahit na ang existing na note ay may parehas na unang field
importing-import-options = Mga option sa pag-i-import
importing-importing-complete = Tapos na ang pag-import.
importing-invalid-file-please-restore-from-backup = Invalid ang file. Paki-restore mula sa backup.
importing-map-to = I-map patungong { $val }
importing-map-to-tags = I-map patungo sa mga Tag
importing-mapped-to = na-map sa <b>{ $val }</b>
importing-mapped-to-tags = na-map sa <b>Mga Tag</b>
importing-mnemosyne-20-deck-db = Mnemosyne 2.0 Deck (*.db)
importing-multicharacter-separators-are-not-supported-please = Ang mga multi-character na seperator ay hindi supported. Isang character lang ang i-enter.
importing-notes-added-from-file = Mga note na nadagdag mula sa file: { $val }
importing-notes-found-in-file = Mga note na nahanap mula sa file: { $val }
importing-notes-skipped-as-theyre-already-in = Ini-skip ang mga note, dahil ang mga up-to-date na copy ay nasa collection mo na: { $val }
importing-notes-skipped-update-due-to-notetype = Hindi na-update ang mga note, dahil ang notetype ay na-modify dahil una mong na-import ang mga note: { $val }
importing-notes-updated-as-file-had-newer = Na-update ang mga note, dahil ang file ay merong mas bagong version: { $val }
importing-packaged-anki-deckcollection-apkg-colpkg-zip = Naka-package na Anki Deck/Collection (*.apkg *.colpkg *.zip)
importing-pauker-18-lesson-paugz = Pauker 1.8 Lesson (*.pau.gz)
# the '|' character
importing-pipe = Pipe
importing-rows-had-num1d-fields-expected-num2d = Ang '{ $row }' ay merong { $found } (na) mga field, { $expected } ang inasahan.
importing-selected-file-was-not-in-utf8 = Ang napiling file ay hindi naka-UTF-8 na format. Pakitingnan ang importing section ng manwal.
importing-semicolon = Semicolon
importing-skipped = Nalaktawan
importing-supermemo-xml-export-xml = Supermemo XML export (*.xml)
importing-tab = Tab
importing-tag-modified-notes = I-tag ang mga na-modify na note:
importing-text-separated-by-tabs-or-semicolons = Text na naka-separate gamit ng mga tab o semicolon (*)
importing-the-first-field-of-the-note = Dapat naka-map ang unang field ng note type.
importing-the-provided-file-is-not-a = Ang binigay na file ay hindi valid na .apkg file.
importing-this-file-does-not-appear-to =
    Parang hindi valid na .apkg file ito. Kung nakukuha mo itong error mula sa isang file
    na dinownload mula AnkiWeb, baka pumalya ang download mo. Subukan muli, at kung 
    may problema pa rin, subukan sa ibang browser.
importing-this-will-delete-your-existing-collection = Idi-delete nito ang existing collection mo at papalitan ng data sa file na ini-import mo. Sure ka ba?
importing-unable-to-import-from-a-readonly = Hindi kayang ma-import mula sa isang read-only file.
importing-unknown-file-format = Unknown na file format.
importing-update-existing-notes-when-first-field = I-update ang exisitng notes pagka-match ng unang field.
importing-updated = Na-update
importing-note-added =
    { $count ->
        [one] { $count } note ang nadagdag.
       *[other] { $count } (na) note ang nadagdag.
    }
importing-note-imported =
    { $count ->
        [one] { $count } note ang na-import.
       *[other] { $count } (na) note ang na-import.
    }
importing-note-unchanged =
    { $count ->
        [one] { $count } note ang hindi nabago.
       *[other] { $count } (na) note ang hindi nabago.
    }
importing-note-updated =
    { $count ->
        [one] { $count } note ang na-update.
       *[other] { $count } (na) note ang na-update.
    }
importing-processed-media-file =
    { $count ->
        [one] Na-import ang { $count } media file
       *[other] Na-import ang { $count } (na) media file
    }
importing-importing-file = Ini-import ang file...
importing-extracting = Ine-extract ang data...
importing-gathering = Tinitipon ang data...
importing-failed-to-import-media-file = Pumalya ang pag-import ng media file: { $debugInfo }
importing-processed-notes =
    { $count ->
        [one] Naproseso ang { $count } note...
       *[other] Naproseso ang { $count } (na) note...
    }
importing-processed-cards =
    { $count ->
        [one] Naproseso ang { $count } card...
       *[other] Naproseso ang { $count } (na) card...
    }
importing-existing-notes = Mga existing na note
# "Existing notes: Duplicate" (verb)
importing-duplicate = Duplicate
# "Existing notes: Preserve" (verb)
importing-preserve = I-preserve
# "Existing notes: Update" (verb)
importing-update = I-update
importing-tag-all-notes = I-tag ang lahat ng note
importing-tag-updated-notes = I-tag ang mga updated note
importing-file = File
# "Match scope: notetype / notetype and deck". Controls how duplicates are matched.
importing-match-scope = Match scope
# Used with the 'match scope' option
importing-notetype-and-deck = Notetype at deck
importing-cards-added =
    { $count ->
        [one] { $count } card ang nadagdag.
       *[other] { $count } (na) card ang nadagdag.
    }
importing-file-empty = Walang laman ang file na napili mo.

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

importing-importing-collection = Ini-import ang collection...
importing-unable-to-import-filename = Hindi kayang ma-import ang { $filename }: hindi supported ang file
importing-notes-that-could-not-be-imported = Mga note na hindi ma-import dahil nagbago ang note type: { $val }
importing-added = Mga nadagdag na
