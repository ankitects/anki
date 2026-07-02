importing-failed-debug-info = Uvoz nije uspio. Informacije o grešci:
importing-aborted = Prekinuto: { $val }
importing-added-duplicate-with-first-field = Dodan duplikat s prvim poljem: { $val }
importing-all-supported-formats = Svi podržani formati { $val }
importing-allow-html-in-fields = Dozvoli HTML u poljima
importing-anki-files-are-from-a-very = . anki datoteke su iz vrlo stare verzije Ankija. Možete ih uvesti uz dodatak 175027074 ili s Ankijem 2.0 koji je dostupan na Anki web-stranici.
importing-anki2-files-are-not-directly-importable = .anki2 datoteke nije moguće izravno uvesti – umjesto toga uvezite .apkg ili .zip datoteku koju ste primili.
importing-appeared-twice-in-file = Pojavilo se dvaput u datoteci: { $val }
importing-by-default-anki-will-detect-the = Anki će implicitno prepoznati znak između polja, kao što su tabulator, zarez itd. Ako Anki krivo prepoznaje znak, možete ga unijeti ovdje. Upotrijebite \t za tabulator.
importing-cannot-merge-notetypes-of-different-kinds =
    Tip bilješki na nadopunjavanje ne može se sjediniti s tipom običnih bilješki.
    I dalje možete uvesti datoteku ako isključite '{ importing-merge-notetypes }'.
importing-change = Promijeni
importing-colon = Dvotočka
importing-comma = Zarez
importing-empty-first-field = Prazno prvo polje: { $val }
importing-field-separator = Separator polja
importing-field-separator-guessed = Separator polja (pretpostavljen)
importing-field-mapping = Preslikavanje polja
importing-field-of-file-is = Polje <b>{ $val }</b> datoteke je:
importing-fields-separated-by = Polja su odvojena sa: { $val }
importing-file-must-contain-field-column = Datoteka mora sadržavati bar jedan stupac koji se može preslikati u polje bilješke
importing-file-version-unknown-trying-import-anyway = Nepoznata verzija datoteke, uvoz će se svejedno pokušati.
importing-first-field-matched = Prvo polje se poklapa s: { $val }
importing-identical = Identično
importing-ignore-field = Ignoriraj polje
importing-ignore-lines-where-first-field-matches = Ignoriraj linije gdje se prvo polje podudara sa postojećom bilješkom
importing-ignored = <ignorirano>
importing-import-even-if-existing-note-has = Uvezi čak i ako postojeća bilješka ima isto prvo polje
importing-import-options = Opcije uvoza
importing-importing-complete = Uvoz završen.
importing-invalid-file-please-restore-from-backup = Datoteka je neispravna. Molimo obnovite iz sigurnosne kopije.
importing-map-to = Preslikaj u { $val }
importing-map-to-tags = Preslikaj u oznake
importing-mapped-to = preslikano u <b>{ $val }</b>
importing-mapped-to-tags = Preslikano u <b>Tags</b>
# the action of combining two existing note types to create a new one
importing-merge-notetypes = Sjedini vrste bilješki
importing-merge-notetypes-help =
    Ako je označeno i ako ste vi ili autor/ica špila izmijenili shemu tipa bilješke, Anki će sjediniti te dvije verzije umjesto da zadrži obje.
    
    Pod mijenjanjem sheme tipa bilješke se podrazumijeva dodavanje, uklanjanje ili preraspoređivanje polja ili predložaka, ili mijenjanje polja za sortiranje.
    Za razliku od toga, mijenjanje prednje strane postojećeg predloška se *ne* broji kao promjena sheme.
    
    Upozorenje: Ovo zahtijeva sinkronizaciju u jednom smjeru i može označiti postojeće bilješke kao izmijenjene.
importing-mnemosyne-20-deck-db = Mnemosyne 2.0 špil (*.db)
importing-multicharacter-separators-are-not-supported-please = Separatori od više znakova nisu podržani. Unesite samo jedan znak.
importing-new-deck-will-be-created = Novi špil će biti stvoren: { $name }
importing-notes-added-from-file = Bilješke dodane iz datoteke: { $val }
importing-notes-found-in-file = Bilješke nađene u datoteci: { $val }
importing-notes-skipped-as-theyre-already-in = Bilješke koje su preskočene jer se ažurne kopije već nalaze u vašoj kolekciji: { $val }
importing-notes-skipped-update-due-to-notetype = Bilješke nisu ažurirane jer je tip bilješke bio mijenjan od prošlog uvoza: { $val }
importing-notes-updated-as-file-had-newer = Bilješke ažurirane jer je datoteka imala noviju verziju: { $val }
importing-include-reviews = Uključi ponavljanja
importing-also-import-progress = Uvezi napredak učenja
importing-with-deck-configs = Uvezi predloške špila
importing-updates = Ažuriranje
importing-include-reviews-help =
    Ako je omogućeno, uvozit će se i sva prethodna ponavljanja koja je uključio/la autor/ica dijeljenog špila.
    U suprotnom će se sve kartice uvesti kao nove kartice, a sve će se oznake „pijavice” ili „markirano” ukloniti.
importing-with-deck-configs-help =
    Ako je omogućeno, uvozit će se i sve opcije špila koje je autor/ica dijeljenog špila uključio/la.
    U suprotnom će svim špilovima biti dodijeljen zadani predložak.
importing-packaged-anki-deckcollection-apkg-colpkg-zip = Pakiran Anki špil/kolekcija (*.apkg *.colpkg *.zip)
# the '|' character
importing-pipe = Pipe
# Warning displayed when the csv import preview table is clipped (some columns were hidden)
# $count is intended to be a large number (1000 and above)
importing-preview-truncated =
    { $count ->
        [one] Samo je prvih { $count } stupaca prikazano. Ako ovo ne valja, pokušajte zamijeniti separator polja.
        [few] Samo su prva { $count } stupca prikazana. Ako ovo ne valja, pokušajte zamijeniti separator polja.
       *[other] Samo je prvih { $count } stupaca prikazano. Ako ovo ne valja, pokušajte zamijeniti separator polja.
    }
importing-rows-had-num1d-fields-expected-num2d = '{ $row }' je ima { $found } polja, očekivano je { $expected }
importing-selected-file-was-not-in-utf8 = Odabrana datoteka nije bila u UTF-8 formatu. Pogledajte dio priručnika o uvoženju.
importing-semicolon = Točka-zarez
importing-skipped = Preskočeno
importing-tab = Tabulator
importing-tag-modified-notes = Označi izmijenjene bilješke:
importing-text-separated-by-tabs-or-semicolons = Tekst odvojen tabulatorom ili točkom-zarez (*)
importing-the-first-field-of-the-note = Prvo polje tipa bilješke mora biti preslikano.
importing-the-provided-file-is-not-a = Navedena datoteka nije valjana .apkg datoteka.
importing-this-file-does-not-appear-to = Čini se da ova datoteka nije valjana .apkg datoteka. Ako ovu pogrešku dobivate iz datoteke preuzete s Ankiweba, postoji vjerojatnost da preuzimanje nije uspjelo. Pokušajte ponovno, a ako se problem nastavi, pokušajte ponovno s drugim preglednikom.
importing-this-will-delete-your-existing-collection = Ovo će obrisati vašu postojeću kolekciju i zamijeniti je sa podacima u datoteci koju uvozite. Jeste li sigurni?
importing-unable-to-import-from-a-readonly = Nije moguć uvoz iz datoteke koja je samo za čitanje.
importing-unknown-file-format = Nepoznat format datoteke.
importing-update-existing-notes-when-first-field = Ažuriraj postojeće bilješke kada se prvo polje podudara
importing-updated = Ažurirano
importing-update-if-newer = Ako je novije
importing-update-always = Uvijek
importing-update-never = Nikad
importing-update-notes = Ažuriraj bilješke
importing-update-notes-help = Kada ažurirati postojeću bilješku u zbirci. Po zadanom, to se radi samo ako je odgovarajuća uvezena bilješka novija od postojeće.
importing-update-notetypes = Ažuriraj vrste bilješki
importing-update-notetypes-help = Kada ažurirati postojeću bilješku u zbirci. Po zadanom, to se radi samo ako je odgovarajuća uvezena bilješka novija od postojeće. Promjene teksta i oblikovanja predloška uvijek je moguće uvesti, ali za promjene sheme (npr. broj ili redoslijed polja se promijenio) morat će biti omogućena i mogućnost '{ importing-merge-notetypes }'.
importing-note-added =
    { $count ->
        [one] { $count } bilješka dodana
        [few] { $count } bilješke dodane
       *[other] { $count } bilješki dodano
    }
importing-note-imported =
    { $count ->
        [one] { $count } bilješka uvezena.
        [few] { $count } bilješke uvezene
       *[other] { $count } bilješki uvezeno.
    }
importing-note-unchanged =
    { $count ->
        [one] { $count } bilješka neizmijenjena.
        [few] { $count } bilješke neizmijenjene.
       *[other] { $count } bilješki neizmijenjeno.
    }
importing-note-updated =
    { $count ->
        [one] { $count } bilješka ažurirana
        [few] { $count } bilješke ažurirane
       *[other] { $count } bilješki ažurirano
    }
importing-processed-media-file =
    { $count ->
        [one] Uvezena je { $count } medijska datoteka
        [few] Uvezene su { $count } medijske datoteke
       *[other] Uvezeno je { $count } medijskih datoteka
    }
importing-importing-file = Datoteka se uvozi...
importing-extracting = Ekstrakcija podataka...
importing-gathering = Prikupljanje podataka...
importing-failed-to-import-media-file = Nije se mogla uvesti medijska datoteka: { $debugInfo }
importing-processed-notes =
    { $count ->
        [one] Obrađena { $count } bilješka...
        [few] Obrađene { $count } bilješke...
       *[other] Obrađeno { $count } bilješki...
    }
importing-processed-cards =
    { $count ->
        [one] Obrađena { $count } kartica...
        [few] Obrađene { $count } kartice...
       *[other] Obrađeno { $count } kartica...
    }
importing-existing-notes = Postojeće bilješke
# "Existing notes: Duplicate" (verb)
importing-duplicate = Dupliciraj
# "Existing notes: Preserve" (verb)
importing-preserve = Zadrži
# "Existing notes: Update" (verb)
importing-update = Ažuriraj
importing-tag-all-notes = Označi sve bilješke
importing-tag-updated-notes = Označi ažurirane bilješke
importing-file = Datoteka
# "Match scope: notetype / notetype and deck". Controls how duplicates are matched.
importing-match-scope = Područje poklapanja
# Used with the 'match scope' option
importing-notetype-and-deck = Vrsta bilješke i špil
importing-cards-added =
    { $count ->
        [one] { $count } karta dodana.
        [few] { $count } karte dodane.
       *[other] { $count } karata dodano.
    }
importing-file-empty = Odabrana datoteka je prazna.
importing-notes-added =
    { $count ->
        [one] { $count } nova bilješka uvezena.
        [few] { $count } nove bilješke uvezene.
       *[other] { $count } novih bilješki uvezeno.
    }
importing-notes-updated =
    { $count ->
        [one] { $count } bilješka je korištena da bi se ažurirale postojeće.
        [few] { $count } bilješke su korištene da bi se ažurirale postojeće.
       *[other] { $count } bilješku je korišteno da bi se ažurirale postojeće.
    }
importing-existing-notes-skipped =
    { $count ->
        [one] { $count } bilješka već postoji u vašoj kolekciji.
        [few] { $count } bilješke već postoje u vašoj kolekciji.
       *[other] { $count } bilješki već postoji u vašoj kolekciji.
    }
importing-notes-failed =
    { $count ->
        [one] Nije se moglo uvesti { $count } bilješku.
        [few] Nije se moglo uvesti { $count } bilješke.
       *[other] Nije se moglo uvesti { $count } bilješki.
    }
importing-conflicting-notes-skipped =
    { $count ->
        [one] { $count } bilješka nije uvezena jer se promijenio njen tip bilješke.
        [few] { $count } bilješke nisu uvezene jer se promijenio njihov tip bilješke.
       *[other] { $count } bilješki nije uvezeno jer se promijenio njihov tip bilješke.
    }
importing-conflicting-notes-skipped2 =
    { $count ->
        [one] { $count } bilješka nije uvezena jer se promijenio njen tip bilješke i jer '{ importing-merge-notetypes }' nije bilo uključeno.
        [few] { $count } bilješke nisu uvezene jer se promijenio njihov tip bilješke i jer '{ importing-merge-notetypes }' nije bilo uključeno.
       *[other] { $count } bilješki nije uvezeno jer se promijenio njihov tip bilješke i jer '{ importing-merge-notetypes }' nije bilo uključeno.
    }
importing-import-log = Zapisnik uvoza
importing-no-notes-in-file = Nijedna bilješka nije nađena u datoteci.
importing-notes-found-in-file2 =
    { $notes ->
        [one] { $notes } bilješka nađena u datoteci. Od njih:
        [few] { $notes } bilješke nađene u datoteci. Od njih:
       *[other] { $notes } bilješki nađeno u datoteci. Od njih:
    }
importing-show = Pokaži
importing-details = Detalji
importing-status = Status
importing-duplicate-note-added = Dodana duplikatna bilješka
importing-added-new-note = Dodana nova bilješka
importing-existing-note-skipped = Bilješka preskočena jer u vašoj kolekciji već postoji ažurna kopija
importing-note-skipped-update-due-to-notetype = Bilješka nije ažurirana jer se vrsta bilješke promijenila otkad je prvo bila uvezena
importing-note-skipped-update-due-to-notetype2 = Bilješka nije ažurirana jer se vrsta bilješke promijenila otkad je prvo bila uvezena i '{ importing-merge-notetypes }' nije bilo uključeno
importing-note-updated-as-file-had-newer = Bilješka ažurirana jer je datoteka imala noviju verziju
importing-note-skipped-due-to-missing-notetype = Bilješka preskočena jer je nedostajala njena vrsta bilješke
importing-note-skipped-due-to-missing-deck = Bilješka preskočena jer nedostaje njen špil
importing-note-skipped-due-to-empty-first-field = Bilješka preskočena jer je njeno prvo polje prazno
importing-field-separator-help =
    Separator polja u tekstnoj datoteci. Možete koristiti pregled da biste provjerili jesu li polja ispravno odvojena.
    
    Napominjemo da ako se taj znak pojavljuje u bilo kojem polju, polje se mora citirati u skladu sa standardom CSV. Programi proračunske tablice kao što je LibreOffice to će učiniti automatski.
    
    Ne može se promijeniti ako tekstualna datoteka prisiljava korištenje određenog separatora putem zaglavlja. Ako zaglavlje datoteke nije prisutno, Anki će pokušati pogoditi što je separator.
importing-allow-html-in-fields-help = Omogućite ovo ako datoteka sadrži HTML formatiranje. Npr. ako datoteka sadrži niz '&lt;br&gt;', prikazat će se kao prijelom retka na vašoj kartici. S druge strane, ako je ta mogućnost onemogućena, prikazat će se doslovni znakovi  '&lt;br&gt;'.
importing-notetype-help =
    Novouvezene bilješke imat će ovu vrstu bilješki, a ažurirat će se samo postojeće bilješke s tom vrstom bilješki.
    
    Možete odabrati koja polja u datoteci odgovaraju kojim poljima vrste bilješke s alatom za mapiranje.
importing-deck-help = Uvezene kartice bit će stavljene u ovaj špil.
importing-existing-notes-help =
    Što učiniti ako se uvezena bilješka podudara s postojećom.
    
    - `{ importing-update }`: Ažuriraj postojeću bilješku.
    - `{ importing-preserve }`: Ne čini ništa.
    - `{ importing-duplicate }`: Stvori novu bilješku.
importing-match-scope-help = Bit će provjereni duplikati samo postojećih bilješki iste vrste. To se može dodatno ograničiti na bilješke s karticama u istom špilu.
importing-tag-all-notes-help = Ove oznake će biti dodane i ažuriranim i novo dodanim bilješkama.
importing-tag-updated-notes-help = Ove oznake bit će dodane svi ažuriranim bilješkama.
importing-overview = Pregled

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

importing-importing-collection = Uvoz kolekcije...
importing-unable-to-import-filename = Ne može se uvesti { $filename }: vrsta datoteke nije podržana
importing-notes-that-could-not-be-imported = Bilješke koje nisu uvezene jer se tip bilješke promijenio: { $val }
importing-added = Dodano
importing-pauker-18-lesson-paugz = Pauker 1.8 Lesson (*.pau.gz)
importing-supermemo-xml-export-xml = Supermemo XML export (*.xml)
