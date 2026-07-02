importing-failed-debug-info = Nalaganje ni uspelo. Podatki o napaki:
importing-aborted = Preklicano: { $val }
importing-added-duplicate-with-first-field = Dodan dvojnik s prvim poljem: { $val }
importing-all-supported-formats = Vsi podprti formati { $val }
importing-allow-html-in-fields = Dovoli HTML v poljih
importing-anki-files-are-from-a-very = Datoteke s končnico .anki  so iz zelo stare različice Anki-ja. Lahko jih uvozite z dodatkom 175027074 ali z različico Anki 2.0, ki je na voljo na spletnem mestu Anki.
importing-anki2-files-are-not-directly-importable = Datotek .anki 2 ni možno neposredno uvažati - namesto tega, prosimo, uvozite .apkg ali .zip datoteko, ki ste jo prejeli.
importing-appeared-twice-in-file = Se je v datoteki pojavila dvakrat: { $val }
importing-by-default-anki-will-detect-the = Anki privzeto zazna znak, ki loči polja med seboj (kot npr. tabulator, vejica, itn.). Če je Anki ta znak narobe razpoznal, ga lahko vnesete tukaj. Za tabulator uporabite /t.
importing-change = Spremeni
importing-colon = Dvopičje
importing-comma = Vejica
importing-empty-first-field = Prazno prvo polje: { $val }
importing-field-separator = Ločilnik vnosnih polj
importing-field-mapping = Usklajevanje polj
importing-field-of-file-is = Polje <b>{ $val }</b> datoteke je:
importing-fields-separated-by = Polja ločena z: { $val }
importing-file-must-contain-field-column = Datoteka mora vsebovati vsaj en stolpec, ki ga lahko povežemo z vnosnim poljem zapiska.
importing-file-version-unknown-trying-import-anyway = Različica datoteke ni poznana, vseeno poskušam uvažanje.
importing-first-field-matched = Prvo vnosno polje se ujema z: { $val }
importing-identical = Identično
importing-ignore-field = Ignoriraj vnosno polje
importing-ignore-lines-where-first-field-matches = Prezri vse vrstice, kjer se prvo polje ujema z obstoječim zapiskom
importing-ignored = <ignorirano>
importing-import-even-if-existing-note-has = Uvozi kljub temu, da ima obstoječi zapisek enako prvo polje
importing-import-options = Možnosti uvoza
importing-importing-complete = Uvoz zaključen.
importing-invalid-file-please-restore-from-backup = Neveljavna datoteka. Prosimo, obnovite iz rezervne kopije.
importing-map-to = Poveži z/s { $val }
importing-map-to-tags = Poveži z oznakami
importing-mapped-to = povezano z <b>{ $val }</b>
importing-mapped-to-tags = povezano z <b>Oznakami</b>
importing-mnemosyne-20-deck-db = Paket Mnemosyne 2.0 (*.db)
importing-multicharacter-separators-are-not-supported-please = Več-znakovni ločilniki niso podprti. Prosimo, vnesite en sam znak.
importing-notes-added-from-file = Zapiski dodani iz datoteke: { $val }
importing-notes-found-in-file = Zapiski najdeni v datoteki: { $val }
importing-notes-skipped-as-theyre-already-in = Zapiske smo preskočili, saj so že v vaši kolekciji: { $val }
importing-notes-that-could-not-be-imported = Zapiske, ki jih ni bilo mogoče uvoziti, saj se je tip zapiska spremenil: { $val }
importing-notes-updated-as-file-had-newer = Zapiski posodobljeni, ker je datoteka imela novo različico: { $val }
importing-packaged-anki-deckcollection-apkg-colpkg-zip = Zapakirana Anki zbirka/kolekcija (*.apkg *.colpkg *.zip)
importing-pauker-18-lesson-paugz = Pauker lekija 1.8 (*.pau.gz)
# the '|' character
importing-pipe = Pokončni znak '|'
importing-rows-had-num1d-fields-expected-num2d = '{ $row }' je vsebovalo { $found } polja, pričakovano pa { $expected }
importing-selected-file-was-not-in-utf8 = Izbrana datoteka ni bila v formatu UTF-8. Prosimo, oglejte si del priročnika, ki govori o uvažanju datotek.
importing-semicolon = Podpičje
importing-skipped = Preskočeno
importing-supermemo-xml-export-xml = Izvoz Supermemo XML(*.xml)
importing-tab = Zavihek
importing-tag-modified-notes = Označi spremenjene zapiske:
importing-text-separated-by-tabs-or-semicolons = Besedilo ločeno s tabulatorji ali podpičji (*)
importing-the-first-field-of-the-note = Prvo polje tipa zapiska mora biti preslikano.
importing-the-provided-file-is-not-a = Datoteka ni veljavna .apkg datoteka.
importing-this-file-does-not-appear-to = Ta datoteka ni veljavna .apkg datoteka. Če to napako opazite pri datotekah, ki ste jih prenesli s strani AnkiWeb, obstaja možnost, da je bila napaka pri prenosu. Prosimo, pokusite znova, ob ponovni napaki pa uporabite drug brskalnik.
importing-this-will-delete-your-existing-collection = S tem boste izbrisali vašo obstoječo zbirko in jo nadomestili s podatki iz datoteke, ki jo uvažate. Ali ste prepričani?
importing-unable-to-import-from-a-readonly = Uvoz iz datoteke, ki je označena samo za branje, ni mogoč.
importing-unknown-file-format = Neznana oblika datoteke.
importing-update-existing-notes-when-first-field = Posodobi obstoječe zapiske, ko se prvi polji ujemata
importing-updated = Posodobljeno
importing-note-added =
    { $count ->
        [one] Dodanih { $count } zapiskov
        [two] Dodan { $count } zapisek
        [few] Dodana { $count } zapiska
       *[other] Dodanih { $count } zapiskov
    }
importing-note-imported =
    { $count ->
        [one] Uvoženih { $count } zapiskov.
        [two] Uvožen { $count } zapisek.
        [few] Uvožena { $count } zapiska.
       *[other] Uvoženi { $count } zapiski.
    }
importing-note-unchanged =
    { $count ->
        [one] Nespremenjenih zapiskov: { $count }
        [two] Nespremenjenih zapiskov: { $count }
        [few] Nespremenjenih zapiskov: { $count }
       *[other] Nespremenjenih zapiskov: { $count }
    }
importing-note-updated =
    { $count ->
        [one] Posodobljenih { $count } zapiskov
        [two] Posodobljen { $count } zapisek
        [few] Posodobljena { $count } zapiska
       *[other] Posodobljeni { $count } zapiski
    }
importing-processed-media-file =
    { $count ->
        [one] Uvoženih medijskih datotek: { $count }
        [two] Uvoženih medijskih datotek: { $count }
        [few] Uvoženih medijskih datotek: { $count }
       *[other] Uvoženih medijskih datotek: { $count }
    }
importing-importing-collection = Uvažanje kolekcije...
importing-importing-file = Uvažanje datoteke...
importing-extracting = Razširjanje podatkov...
importing-gathering = Zbiranje podatkov...
importing-failed-to-import-media-file = Uvažanje ni uspelo: { $debugInfo }
importing-processed-notes =
    { $count ->
        [one] Procesiranih zapiskov: { $count }
        [two] Procesiranih zapiskov: { $count }
        [few] Procesiranih zapiskov: { $count }
       *[other] Procesiranih zapiskov: { $count }
    }
importing-processed-cards =
    { $count ->
        [one] Procesiranih kartic: { $count }
        [two] Procesiranih kartic: { $count }
        [few] Procesiranih kartic: { $count }
       *[other] Procesiranih kartic: { $count }
    }
importing-unable-to-import-filename = Ni možno uvoziti { $filename }; vrsta datoteke ni podprta.
importing-existing-notes = Obstoječi zapiski
# "Existing notes: Duplicate" (verb)
importing-duplicate = Podvoji
# "Existing notes: Preserve" (verb)
importing-preserve = Ohrani
# "Existing notes: Update" (verb)
importing-update = Posodobi
importing-tag-all-notes = Označi vse zapiske
importing-tag-updated-notes = Označi posodobljene zapiske
importing-file = Datoteka
importing-added = Dodano
