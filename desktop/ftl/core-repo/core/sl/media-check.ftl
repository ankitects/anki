## Shown at the top of the media check screen

media-check-window-title = Preglej medijske datoteke
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    Koš: { $count ->
        [one] { $count } datoteka, { $megs }MB
        [two] { $count } datoteki, { $megs }MB
        [few] { $count } datoteke, { $megs }MB
       *[other] { $count } datotek, { $megs }MB
    }
media-check-missing-count = Manjkajoče datoteke: { $count }
media-check-unused-count = Neuporabljene datoteke: { $count }
media-check-renamed-count = Preimenovane datoteke: { $count }
media-check-oversize-count = Preko 100MB: { $count }
media-check-subfolder-count = Podrejene mape: { $count }

## Shown at the top of each section

media-check-renamed-header = Nekatere datoteke so bile preimenovane za združljivost:
media-check-oversize-header = Datoteke z velikostjo nad 100MB ni možno sinhronizirati s storitvijo AnkiWeb.
media-check-subfolder-header = Mape znotraj medijske mape niso podprte.
media-check-missing-header = Uporabljeno na karticah, a manjka v mapi medijskih datotek:
media-check-unused-header = Naslednje datoteke smo našli v medijski mapi, vendar se ne pojavljajo na nobeni kartici:
media-check-template-references-field-header =
    Anki ne more najti uporabljenih datotek, kadar so reference { "{{Field}}" } uporabljene v oznakah media/LaTeX. Tovrstne oznake bi morale biti postavljene na posameznih zapiskih.
    
    Predloge z referencami:

## Shown once for each file

media-check-renamed-file = Preimenovano: { $old } -> { $new }
media-check-oversize-file = Preko 100MB: { $filename }
media-check-subfolder-file = Mapa: { $filename }
media-check-missing-file = Manjka: { $filename }
media-check-unused-file = Neuporabljeno: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = Pregledano { $count }...

## Deleting unused media

media-check-delete-unused-confirm = Izbrišem neuporabljene medijske datoteke?
media-check-files-remaining =
    { $count ->
        [one] { $count } datoteka
        [two] { $count } datoteki
        [few] { $count } datoteke
       *[other] { $count } datotek
    } do zaključka.
media-check-delete-unused-complete =
    { $count ->
        [one] { $count } datoteka
        [two] { $count } datoteki
        [few] { $count } datoteke
       *[other] { $count } datotek
    }premaknjenih v koš.
media-check-trash-emptied = Koš je izpraznjen.
media-check-trash-restored = V mapo z medijskimi datotekami smo obnovili izbrisane datoteke.

## Rendering LaTeX

media-check-all-latex-rendered = Ves LaTeX zapis pripravljen.

## Buttons

media-check-delete-unused = Izbriši neuporabljene
media-check-render-latex = Pripravi LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = Izprazni koš
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Obnovi izbrisane
media-check-check-media-action = Preglej medijske datoteke
