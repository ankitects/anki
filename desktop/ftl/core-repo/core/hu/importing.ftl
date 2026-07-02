importing-failed-debug-info = Sikertelen importálás. Információ a hiba elhárításához:
importing-aborted = Megszakítva: { $val }
importing-added-duplicate-with-first-field = Első mezőben egyező változat hozzáadva: { $val }
importing-all-supported-formats = Minden támogatott formátum { $val }
importing-allow-html-in-fields = HTML-formázás engedélyezése a mezőkben
importing-anki-files-are-from-a-very = Az .anki kiterjesztésű fájlok az Anki nagyon régi változatából származnak. Importálásuk az Anki honlapjáról letölthető Anki 2.0 vagy 175027074 bővítmény segítségével lehetséges.
importing-anki2-files-are-not-directly-importable = Az .anki2 kiterjesztésű fájlokat nem lehet közvetlenül importálni. Importáld az .apkg vagy .zip kiterjesztésű fájlokat helyette!
importing-appeared-twice-in-file = Kétszer szerepelt ebben a fájlban: { $val }
importing-by-default-anki-will-detect-the = Az Anki alapértelmezés szerint felismeri a mezők közti karaktert, például a tabulátort, a vesszőt stb. Ha rosszul ismerné fel, akkor itt megadhatod a helyes karaktert. A tabulátort a \t jelöli.
importing-cannot-merge-notetypes-of-different-kinds =
    A lyukasszöveg jegyzettípusokat nem lehet sima típusokkal egyesíteni.
    A fájl az "{ importing-merge-notetypes }" beállítás kikapcsolásával importálható.
importing-change = Módosítás
importing-colon = Kettőspont
importing-comma = Vessző
importing-empty-first-field = Üres az első mezője: { $val }
importing-field-separator = Határolójel
importing-field-separator-guessed = Határolójel (automatikus)
importing-field-mapping = Mezőleképezés
importing-field-of-file-is = A fájl <b>{ $val }</b>. mezője:
importing-fields-separated-by = Mezők határolójele: { $val }
importing-file-must-contain-field-column = A fáljnak tartalmaznia kell legalább egy oszlopot, ami leképezhető egy mezőre.
importing-file-version-unknown-trying-import-anyway = Ismeretlen fájlverzió, importálás ettől függetlenül.
importing-first-field-matched = Az első mező megegyezik: { $val }
importing-identical = Megegyező
importing-ignore-field = Mező figyelmen kívül hagyása
importing-ignore-lines-where-first-field-matches = Hagyja ki azokat a sorokat, ahol az első mező egyezik egy meglévő jegyzettel
importing-ignored = <figyelmen kívül hagyva>
importing-import-even-if-existing-note-has = Akkor is importálja, ha egy meglévő jegyzetnek azonos az első mezője
importing-import-options = Importálás beállításai
importing-importing-complete = Az importálás befejeződött.
importing-invalid-file-please-restore-from-backup = Érvénytelen fájl. Állítsd vissza egy biztonsági mentésből!
importing-map-to = Hozzárendelés ehhez: { $val }
importing-map-to-tags = Hozzárendelés címkékhez
importing-mapped-to = hozzárendelve ehhez: <b>{ $val }</b>
importing-mapped-to-tags = hozzárendelve ehhez: <b>Címkék</b>
# the action of combining two existing note types to create a new one
importing-merge-notetypes = Jegyzettípusok egyesítése
importing-merge-notetypes-help =
    Ha be van jelölve, és te vagy a másik szerző megosztotta a jegyzettípus sémáját,
    az Anki egyesíti a két jegyzettípust ahelyett, hogy mindkettőt megtartaná.
    
    Egy jegyzettípus sémája megváltozik mezők vagy sablonok hozzáadásával, eltávolításával,
    a sorrendjük módosításával  vagy a rendezési mező megváltoztatásával.
    Ellenben egy létező sablon előlepjának módosítása *nem* jelent sémaváltoztatást.
    
    FIgyelmeztetés: Ezután egyirányú szinkronizálás szükséges, ami után egyes jegyzetek
    módosítottnak lesznek jelölve.
importing-mnemosyne-20-deck-db = Mnemosyne 2.0-ban készült pakli (*.db)
importing-multicharacter-separators-are-not-supported-please = A többkarakteres elválasztójel nem támogatott. Csak egy karaktert adj meg!
importing-new-deck-will-be-created = { $name } nevű új pakli lesz létrehozva.
importing-notes-added-from-file = Hozzáadott jegyzetek a fájlból: { $val }
importing-notes-found-in-file = Jegyzetek a fájlban: { $val }
importing-notes-skipped-as-theyre-already-in = Kihagyott jegyzetek, mivel már szerepelnek a gyűjteményedben: { $val }
importing-notes-skipped-update-due-to-notetype = Nem frissített jegyzetek, mivel az első importálás óta a jegyzettípus megváltozott: { $val }
importing-notes-updated-as-file-had-newer = A jegyzetek frissítve, mivel a fájl újabb verziójú volt: { $val }
importing-include-reviews = Ismétlésekkel együtt
importing-also-import-progress = Tanulási állapottal együtt
importing-with-deck-configs = Pakli-előbeállításokkal együtt
importing-updates = Frissítések
importing-include-reviews-help =
    Ha engedélyezve van, minden korábbi ismétlést importál, amit a készítő megosztott.
    Különben minden kártya új kártyaként lesz importálva.
importing-with-deck-configs-help =
    Ha engedélyezve van, minden paklibeállítást importál, amt a készítő megosztott.
    Különben minden pakli az alapbeállítást kapja.
importing-packaged-anki-deckcollection-apkg-colpkg-zip = Tömörített Anki-pakli/-gyűjtemény (*.apkg *.colpkg *.zip)
# the '|' character
importing-pipe = Függőleges vonal
# Warning displayed when the csv import preview table is clipped (some columns were hidden)
# $count is intended to be a large number (1000 and above)
importing-preview-truncated =
    { $count ->
       *[other] Csak az első { $count } oszlop van megjelenítve. Ha hibásnak tűnik, módosítsd a határolójelet.
    }
importing-rows-had-num1d-fields-expected-num2d = "{ $row }" csak { $found } mezőt tartalmaz a várt { $expected } helyett.
importing-selected-file-was-not-in-utf8 = A kiválasztott fájl nem UTF-8 formátumú. Nézd meg a kézikönyv importálásra vonatkozó részét.
importing-semicolon = Pontosvessző
importing-skipped = Kihagyva
importing-tab = Tabulátor
importing-tag-modified-notes = Módosított jegyzetek felcímkézése:
importing-text-separated-by-tabs-or-semicolons = Tabulátorral vagy pontosvesszővel határolt szöveg (*)
importing-the-first-field-of-the-note = A jegyzettípus első mezőjének kapcsolódnia kell a kártyák tartalmához.
importing-the-provided-file-is-not-a = A megadott fájl nem érvényes .apkg állomány.
importing-this-file-does-not-appear-to = Úgy tűnik, ez nem érvényes .apkg fájl. Ha ezt a hibaüzenetet egy AnkiWebről letöltött fájl okozta, a letöltés valószínűleg sikertelen volt. Próbáld újra, és ha a probléma továbbra is fennáll, próbálkozz egy másik böngészővel.
importing-this-will-delete-your-existing-collection = Ez a művelet törli a meglévő gyűjteményedet, és a most importált fájlból származó adatokkal írja felül. Biztosan folytatod?
importing-unable-to-import-from-a-readonly = Csak olvasható fájlból nem lehetséges az importálás.
importing-unknown-file-format = Ismeretlen fájlformátum.
importing-update-existing-notes-when-first-field = Meglévő jegyzet frissítése, ha első mezője egyezik
importing-updated = Frissítve
importing-update-if-newer = Ha újabb
importing-update-always = Mindig
importing-update-never = Soha
importing-update-notes = Jegyzetek frissítése
importing-update-notes-help =
    Mikor frissítse a már a gyűjteményedben lévő jegyzeteket. Alapból
    csak akkor, ha az importált jegyzet később lett módosítva.
importing-update-notetypes = Jegyzettípusok frissítése
importing-update-notetypes-help =
    Mikor frissítse a már a gyűjteményedben lévő jegyzettípusokat. Alapból
    csak akkor, ha az importált jegyzettípus később lett módosítva. A sablonszöveg
    és stílus importálása minden esteben lehetséges, de sémamódosításhoz (pl. ha a mezők
    száma vagy sorrendje változott) az "{ importing-merge-notetypes }" beállítás engedélyezése szükséges.
importing-note-added =
    { $count ->
        [one] { $count } jegyzet hozzáadva
       *[other] { $count } jegyzet hozzáadva
    }
importing-note-imported =
    { $count ->
        [one] { $count } jegyzet importálva.
       *[other] { $count } jegyzet importálva.
    }
importing-note-unchanged =
    { $count ->
        [one] { $count } jegyzet változatlan
       *[other] { $count } jegyzet változatlan
    }
importing-note-updated =
    { $count ->
        [one] { $count } jegyzet frissítve
       *[other] { $count } jegyzet frissítve
    }
importing-processed-media-file =
    { $count ->
       *[other] { $count } médiafájl importálva
    }
importing-importing-file = Importálás...
importing-extracting = Kibontás...
importing-gathering = Adatok összegyűjtése...
importing-failed-to-import-media-file = Nem sikerült a médiafájl importálása: { $debugInfo }
importing-processed-notes =
    { $count ->
       *[other] { $count } jegyzet feldolgozva...
    }
importing-processed-cards =
    { $count ->
       *[other] { $count } kártya feldolgozva...
    }
importing-existing-notes = Meglévő jegyzetek
# "Existing notes: Duplicate" (verb)
importing-duplicate = Megkettőzés
# "Existing notes: Preserve" (verb)
importing-preserve = Megtartás
# "Existing notes: Update" (verb)
importing-update = Frissítés
importing-tag-all-notes = Minden jegyzet felcímkézése
importing-tag-updated-notes = Frissített jegyzetek felcímkézése
importing-file = Fájl
# "Match scope: notetype / notetype and deck". Controls how duplicates are matched.
importing-match-scope = Egyezés típusa
# Used with the 'match scope' option
importing-notetype-and-deck = Jegyzettípus és pakli
importing-cards-added =
    { $count ->
       *[other] { $count } kártya hozzáadva.
    }
importing-file-empty = A kiválasztott fájl üres.
importing-notes-added =
    { $count ->
       *[other] { $count } új jegyzet importálva.
    }
importing-notes-updated =
    { $count ->
       *[other] { $count } meglévő jegyzet frissítve.
    }
importing-existing-notes-skipped =
    { $count ->
       *[other] { $count } jegyzet már szerepel a gyűjteményedben.
    }
importing-notes-failed =
    { $count ->
       *[other] { $count } jegyzet importálása sikertelen.
    }
importing-conflicting-notes-skipped =
    { $count ->
       *[other] { $count } jegyzet nem lett importálva, mivel a típusa megváltozott.
    }
importing-conflicting-notes-skipped2 =
    { $count ->
       *[other] { $count } jegyzet nem lett importálva, mivel a típusa megváltozott és a "{ importing-merge-notetypes }" beállítás nem volt engedélyezve.
    }
importing-import-log = Importálási napló
importing-no-notes-in-file = A fájlban nincsenek jegyzetek.
importing-notes-found-in-file2 =
    { $notes ->
        [one] A fájlban { $notes } jegyzet szerepel:
       *[other] A fájlban { $notes } jegyzet szerepel. Ebből:
    }
importing-show = Megjelenítés
importing-details = Részletek
importing-status = Állapot
importing-duplicate-note-added = Másodpéldány hozzáadva
importing-added-new-note = Új jegyzet hozzáadva
importing-existing-note-skipped = Jegyzet kihagyva, mivel naprakész másolata már szerepel a gyűjteményedben
importing-note-skipped-update-due-to-notetype = Jegyzet nem lett frissítve, mivel az első importálás óta a típusa megváltozott
importing-note-skipped-update-due-to-notetype2 = Jegyzet nem lett frissítve, mivel az első importálás óta a típusa megváltozott és a "{ importing-merge-notetypes }" beállítás nem volt engedélyezve
importing-note-updated-as-file-had-newer = Jegyzet frissítve újabb verzióra
importing-note-skipped-due-to-missing-notetype = Jegyzet kihagva, mivel a típusa hiányzott
importing-note-skipped-due-to-missing-deck = Jegyzet kihagyva, mivel a paklija hiányzott
importing-note-skipped-due-to-empty-first-field = Jegyzet kihagyva, mivel az első mezője üres
importing-field-separator-help =
    A szövegfájlban a mezőket elválasztó karakter. Az előnézetben ellenőrizheted,
    hogy a mzők megfelelően vannak-e elválasztva.
    
    Amennyiben ez a karakter bármelyik mezőben szerepel, azt a CSV szabványnak
    megfelelően idézőjelek közé kell tenni. Táblázatkezelő programok ezt
    automatikusan megteszik.
    
    Ha a fájl fejlécében megadja a határolójelet, az nem módosítható.
    Amennyiben nincs fejléc, az Anki megpróbálja felsimerni a használt karaktert.
importing-allow-html-in-fields-help =
    Engedélyezd, ha a fájlban HTML formázás található. Például ha "&lt;br&gt;" szerepel,
    az a kártyán sortörésként fog megjelenni. Ha ez nincs engedélyezve, a
    "&lt;br&gt;" lesznek megjelenítve.
importing-notetype-help =
    Az újonnan importált jegyzetek típusa. A meglévő jegyzetek közül csak az ilyen típusúak
    lesznek frissítve.
    
    A Mezőleképezés eszközzel kiválaszthatod, hogy a fájl melyik mezője melyik jegyzettípus-mezőhöz
    tartozzon.
importing-deck-help = Az importált kártyák ebbe a pakliba kerülnek.
importing-existing-notes-help =
    Amennyiben egy importált jegyzet egy meglévővel egyezik:
    
    - `{ importing-update }`: Meglévő jegyzet frissítése
    - `{ importing-preserve }`: Jegyzet kihagyása
    - `{ importing-duplicate }`: Új jegyzet létrehozása
importing-match-scope-help =
    Csak az azonos típusú jegyzetek között lehessen egyezés. Ez tovább
    szűkíthető csak azonos pakliban lévő jegyzetekre.
importing-tag-all-notes-help = Az újonnan importált és frissített jegyzetekre akalmazott címkék.
importing-tag-updated-notes-help = Az újonnan importált jegyzetekre alkalmazott címkék.
importing-overview = Áttekintés

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

importing-importing-collection = Gyűjtemény importálása...
importing-unable-to-import-filename = { $filename } importálása sikertelen: nem támogatott formátum
importing-notes-that-could-not-be-imported = Jegyzetek, amelyeket nem lehetett importálni, mivel a típusuk megváltozott: { $val }
importing-added = Hozzáadva
importing-pauker-18-lesson-paugz = Pauker 1.8 lecke (*.pau.gz)
importing-supermemo-xml-export-xml = XML-be exportált Supermemo-fájl (*.xml)
