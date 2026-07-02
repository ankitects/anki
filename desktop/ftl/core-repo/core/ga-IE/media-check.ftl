## Shown at the top of the media check screen

media-check-window-title = Seiceáil Meáin
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    Fillteán bruscair:{ $count ->
        [one] { $count } chomhad amháin, { $megs }MB
        [two] { $count } chomhad, { $megs }MB
        [few] { $count } chomhad, { $megs }MB
        [many] { $count } gcomhad, { $megs }MB
       *[other] { $count } comhad, { $megs }MB
    }
media-check-missing-count =
    Comhaid ar iarraidh: { $count ->
        [one] { $count } cheann amháin
        [two] { $count } cheann
        [few] { $count } cinn
        [many] { $count } gcinn
       *[other] { $count } ceann
    }
media-check-unused-count =
    Comhaid gan úsáid: { $count ->
        [one] { $count } cheann amháin
        [two] { $count } cheann
        [few] { $count } cinn
        [many] { $count } gcinn
       *[other] { $count } ceann
    }
media-check-renamed-count =
    Comhaid athainmnithe: { $count ->
        [one] { $count } cheann amháin
        [two] { $count } cheann
        [few] { $count } cinn
        [many] { $count } gcinn
       *[other] { $count } ceann
    }
media-check-oversize-count =
    Os cionn 100MB: { $count ->
        [one] { $count } cheann amháin
        [two] { $count } cheann
        [few] { $count } cinn
        [many] { $count } gcinn
       *[other] { $count } ceann
    }
media-check-subfolder-count =
    Fofhillteáin: { $count ->
        [one] { $count } cheann amháin
        [two] { $count } cheann
        [few] { $count } cinn
        [many] { $count } gcinn
       *[other] { $count } ceann
    }

## Shown at the top of each section

media-check-renamed-header = Cuireadh ainmneacha nua ar chomhaid áirithe mar mhaithe leis an gcomhoiriúnacht:
media-check-oversize-header = Ní féidir comhaid os cionn 100MB a shioncronú le AnkiWeb.
media-check-subfolder-header = Ní féidir fillteáin a bheith san fhillteán meán.
media-check-missing-header = Tagraíonn cártaí do na comhaid seo a leanas, nach bhfuil san fhillteán meán:
media-check-unused-header = Tá na comhaid seo san fhillteán meán, ach ní thagraíonn cárta ar bith dóibh:
media-check-template-references-field-header =
    Ní aithneoidh Anki na comhaid atá i gceist má dhéantar tagairt do { "{{Field}}" } i gclibeanna meán/LaTeX. B'fhearr na clibeanna meán/LaTeX a chur ar nótaí dá gcuid féin.
    
    Teimpléid atá i gceist:

## Shown once for each file

media-check-renamed-file = Athainmnithe: { $old } -> { $new }
media-check-oversize-file = Os cionn 100MB: { $filename }
media-check-subfolder-file = Fillteán: { $filename }
media-check-missing-file = Ar iarraidh: { $filename }
media-check-unused-file = Gan úsáid: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked =
    Seicáilte: { $count ->
        [one] { $count } cheann amháin
        [two] { $count } cheann
        [few] { $count } cinn
        [many] { $count } gcinn
       *[other] { $count } ceann
    }...

## Deleting unused media

media-check-delete-unused-confirm = Scrios meáin gan úsáid?
media-check-files-remaining =
    { $count ->
        [one] Tá { $count } chomhad amháin
        [two] Tá { $count } chomhad
        [few] Tá { $count } chomhad
        [many] Tá { $count } gcomhad
       *[other] Tá { $count } comhad
    } fágtha
media-check-delete-unused-complete =
    { $count ->
        [one] Cuireadh { $count } chomhad amháin
        [two] Cuireadh { $count } chomhad
        [few] Cuireadh { $count } chomhad
        [many] Cuireadh { $count } gcomhad
       *[other] Cuireadh { $count } comhad
    } sa bhruscar.
media-check-trash-emptied = Tá an fillteán bruscair folmhaithe.
media-check-trash-restored = Aischuireadh comhaid scriosta san fhillteán meán.

## Rendering LaTeX

media-check-all-latex-rendered = Gach LaTeX rindreáilte.

## Buttons

media-check-delete-unused = Scrios ábhar gan úsáid
media-check-render-latex = Rindreáil LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = Folmhaigh bruscar
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Aischuir ábhar a scriosadh
media-check-check-media-action = Seiceáil Meáin
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = meáin-ar-iarraidh
# add a tag to notes with missing media
media-check-add-tag = Cuir Clib le Meáin ar Iarraidh
