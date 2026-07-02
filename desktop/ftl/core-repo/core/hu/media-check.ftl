## Shown at the top of the media check screen

media-check-window-title = Média ellenőrzése
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    { $count ->
       *[other] Lomtár: { $count } fájl, { $megs } MB
    }
media-check-missing-count = Hiányzó fájlok: { $count }
media-check-unused-count = Nem használt fájlok: { $count }
media-check-renamed-count = Átnevezett fájlok: { $count }
media-check-oversize-count = 100 MB fölött: { $count }
media-check-subfolder-count = Almappák: { $count }
media-check-extracted-count = Kicsomagolt képek: { $count }

## Shown at the top of each section

media-check-renamed-header = Néhány fájl kompatibilitási okokból át lett nevezve:
media-check-oversize-header = A 100 MB feletti fájlokat nem lehet szinkronizálni az AnkiWebbel.
media-check-subfolder-header = A médiamappában lévő mappák nem támogatottak.
media-check-missing-header = Az alábbi fájlokra kártyák hivatkoznak, de nem találhatók a médiamappában:
media-check-unused-header = Az alábbi fájlokra egy kártya sem hivatkozik:
media-check-template-references-field-header =
    Az Anki nem tudja felismerni a használt fájlokat, mikor  a { "{{Field}}" } hivatkozásokat a média/LaTeX címkékben használod. Ezek helyett a média/LaTeX címkéket az egyes jegyzetekben kell elhelyezni.
    
    Vonatkozó sablonok:

## Shown once for each file

media-check-renamed-file = Átnevezve: { $old } -> { $new }
media-check-oversize-file = 100 MB fölött: { $filename }
media-check-subfolder-file = Mappa: { $filename }
media-check-missing-file = Hiányzik: { $filename }
media-check-unused-file = Nem használt: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = { $count } ellenőrizve...

## Deleting unused media

media-check-delete-unused-confirm = Törlöd a nem használt médiaállományokat?
media-check-files-remaining =
    { $count ->
       *[other] { $count } fájl van hátra.
    }
media-check-delete-unused-complete =
    { $count ->
       *[other] { $count } fájl áthelyezve a lomtárba.
    }
media-check-trash-emptied = A lomtár üres.
media-check-trash-restored = A törölt fájlokat visszaállította a médiamappába.

## Rendering LaTeX

media-check-all-latex-rendered = Az összes LaTeX megjelenítve.

## Buttons

media-check-delete-unused = Törölje a nem használtat
media-check-render-latex = LaTeX megjelenítése
# button to permanently delete media files from the trash folder
media-check-empty-trash = Lomtár ürítése
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Törölt fájlok visszaállítása
media-check-check-media-action = Média ellenőrzése
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = hiányzó-média
# add a tag to notes with missing media
media-check-add-tag = Hiányos jegyzetek felcímkézése
