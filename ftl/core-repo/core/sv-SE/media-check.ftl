## Shown at the top of the media check screen

media-check-window-title = Kontrollera media
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    { $count ->
        [one] Papperskorg: { $count } fil, { $megs }MB
       *[other] Papperskorg: { $count } filer, { $megs }MB
    }
media-check-missing-count = Saknade filer: { $count }
media-check-unused-count = Oanvända filer: { $count }
media-check-renamed-count = Omdöpta filer: { $count }
media-check-oversize-count = Över 100MB: { $count }
media-check-subfolder-count = Undermappar: { $count }
media-check-extracted-count = Extraherade bilder: { $count }

## Shown at the top of each section

media-check-renamed-header = Några filer har döpts om av kompabilitetsskäl:
media-check-oversize-header = Filer större än 100 MB kan inte synkas med AnkiWeb.
media-check-subfolder-header = Underkataloger i mediakatalogen stöds ej.
media-check-missing-header = Används på kort men saknas i mediamappen:
media-check-unused-header = Följande filer hittades i media-mappen, men verkar inte användas i något kort:
media-check-template-references-field-header =
    Anki kan inte upptäcka använda filer när { "{{Field}}" }-referenser används i media/LaTeX-taggar. Media/LaTeX-taggar bör istället placeras på individuella noter.
    
    Referensmallar:

## Shown once for each file

media-check-renamed-file = Bytte namn: { $old } ->{ $new }
media-check-oversize-file = Över 100MB: { $filename }
media-check-subfolder-file = Mapp: { $filename }
media-check-missing-file = Saknas: { $filename }
media-check-unused-file = Oanvänd: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = Kontrollerade { $count } ...

## Deleting unused media

media-check-delete-unused-confirm = Ta bort oanvända media?
media-check-files-remaining =
    { $count ->
        [one] { $count } fil återstår.
       *[other] { $count } filer återstår.
    }
media-check-delete-unused-complete =
    { $count ->
        [one] { $count } fil flyttad till papperskorgen.
       *[other] { $count } filer flyttade till papperskorgen.
    }
media-check-trash-emptied = Papperskorgen är nu tom.
media-check-trash-restored = Återställde raderade filer till media-mappen.

## Rendering LaTeX

media-check-all-latex-rendered = All LaTeX är renderad

## Buttons

media-check-delete-unused = Ta bort oanvända filer
media-check-render-latex = Rendera LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = Töm papperskorgen
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Återställ raderade filer
media-check-check-media-action = Kontrollera media
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = media-saknas
# add a tag to notes with missing media
media-check-add-tag = Etikettera saknad
