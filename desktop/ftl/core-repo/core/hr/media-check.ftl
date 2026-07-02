## Shown at the top of the media check screen

media-check-window-title = Provjeri medije
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    { $count ->
        [one] Koš za smeće: { $count } datoteka, { $megs } MB
        [few] Koš za smeće: { $count } datoteke, { $megs } MB
       *[other] Koš za smeće: { $count } datoteka, { $megs } MB
    }
media-check-missing-count = Nedostajućih datoteka: { $count }
media-check-unused-count = Nekorištenih datoteka: { $count }
media-check-renamed-count = Preimenovanih datoteka: { $count }
media-check-oversize-count = Preko 100MB: { $count }
media-check-subfolder-count = Podmapa: { $count }
media-check-extracted-count = Izvučenih slika: { $count }

## Shown at the top of each section

media-check-renamed-header = Neke datoteke su preimenovane radi kompatibilnosti:
media-check-oversize-header = Datoteke veće od 100MB se ne mogu sinkronizirati s AnkiWebom.
media-check-subfolder-header = Mape unutar mape za medije nisu podržane.
media-check-missing-header = Sljedeće datoteke su korištene na karticama, ali nedostaju u mapi za medije:
media-check-unused-header = Sljedeće datoteke su nađene u mapi za medije, ali se ne pojavljuju ni na jednoj kartici:
media-check-template-references-field-header =
    Anki ne može detektirati korištene datoteke ako koristite{ "{{Field}}" } reference u medijima/LaTeX oznakama. Mediji/LaTeX oznake trebaju biti postavljene na pojedinačnim bilješkama.
    
    Predlošci s referencama:

## Shown once for each file

media-check-renamed-file = Preimenovano: { $old } -> { $new }
media-check-oversize-file = Preko 100MB: { $filename }
media-check-subfolder-file = Mapa: { $filename }
media-check-missing-file = Nedostaje: { $filename }
media-check-unused-file = Nekorišteno: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = Provjereno { $count }...

## Deleting unused media

media-check-delete-unused-confirm = Izbrisati nekorištene medijske datoteke?
media-check-files-remaining =
    { $count ->
        [one] { $count } datoteka preostala.
        [few] { $count } datoteke preostale.
       *[other] { $count } datoteka preostalo.
    }
media-check-delete-unused-complete =
    { $count ->
        [one] { $count } datoteka premještena u koš za smeće.
        [few] { $count } datoteke premještene u koš za smeće.
       *[other] { $count } datoteka premješteno u koš za smeće.
    }
media-check-trash-emptied = Koš za smeće je sad prazan.
media-check-trash-restored = Izbrisane datoteke su obnovljene u mapu za medije.

## Rendering LaTeX

media-check-all-latex-rendered = Sav LaTeX iscrtan.

## Buttons

media-check-delete-unused = Izbriši nekorišteno
media-check-render-latex = Iscrtaj LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = Isprazni koš za smeće
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Obnovi izbrisano
media-check-check-media-action = Provjeri medije
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = nedostaju-mediji
# add a tag to notes with missing media
media-check-add-tag = Označi nedostajuće
