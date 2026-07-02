## Shown at the top of the media check screen

media-check-window-title = Tjek medie
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    Skraldmappe: { $count ->
        [one] { $count } fil, { $megs } MB
       *[other] { $count } filer, { $megs } MB
    }
media-check-missing-count = Manglende filer: { $count }
media-check-unused-count = Ubrugte filer: { $count }
media-check-renamed-count = Omdøbte filer: { $count }
media-check-oversize-count = Over 100MB: { $count }
media-check-subfolder-count = Undermapper: { $count }

## Shown at the top of each section

media-check-renamed-header = Nogle filer er blevet omdøbt for kompatibilitet:
media-check-oversize-header = Filer over 100MB kan ikke blive synkroniseret med AnkiWeb.
media-check-subfolder-header = Mapper inde i media-mapper er ikke understøttet.
media-check-missing-header = Anvendt i kort, men findes ikke i mediemappe:
media-check-unused-header = De følgende filer er blevet fundet inde i media-mapper, men ser ikke ud til at blive brugt i nogle kort:
media-check-template-references-field-header =
    Anki kan ikke finde brugte filer, når du bruger { "{{Field}}" }-referencer i media/LaTeX tags. Media/LaTeX tags skal være placerede på individuelle noter i stedet.
    
    Referenceskabeloner:

## Shown once for each file

media-check-renamed-file = Omdøbte: { $old } -> { $new }
media-check-oversize-file = Over 100MB: { $filename }
media-check-subfolder-file = Mappe: { $filename }
media-check-missing-file = Manglende: { $filename }
media-check-unused-file = Ubrugt: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = Gennemgik { $count }...

## Deleting unused media

media-check-delete-unused-confirm = Slet medier som ikke anvendes?
media-check-files-remaining =
    { $count ->
        [one] { $count } fil
       *[other] { $count } filer
    } tilbage.
media-check-delete-unused-complete =
    { $count ->
        [one] { $count } fil
       *[other] { $count } filer
    } blev flyttet til skraldspanden.
media-check-trash-emptied = Skraldmappen er nu tom.
media-check-trash-restored = Gendannede, slettede filer til media-mappen.

## Rendering LaTeX

media-check-all-latex-rendered = All LaTeX afgivet

## Buttons

media-check-delete-unused = Slet ubrugt
media-check-render-latex = Afgiv LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = Tøm skraldespand
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Gendan slettet
media-check-check-media-action = Tjek medie
