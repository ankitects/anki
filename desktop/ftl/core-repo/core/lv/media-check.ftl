## Shown at the top of the media check screen

media-check-window-title = Pārbaudīt informācijas nesējus
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    { $count ->
        [zero] Atkritnes mape: { $count } datņu, { $megs } MB
        [one] Atkritnes mape: { $count } datne, { $megs } MB
       *[other] Atkritnes mape: { $count } datnes, { $megs } MB
    }
media-check-missing-count = Trūkst datņu: { $count }
media-check-unused-count = Neizmantotas datnes: { $count }
media-check-renamed-count = Pārdēvētas datnes: { $count }
media-check-oversize-count = Virs 100 MB: { $count }
media-check-subfolder-count = Apakšmapes: { $count }
media-check-extracted-count = Iegūtie attēli: { $count }

## Shown at the top of each section

media-check-renamed-header = Dažas datnes tika pārdēvētas saderībai:
media-check-oversize-header = Datnes, kas ir lielākas par 100 MB, nevar sinhronizēt ar AnkiWeb.
media-check-subfolder-header = Informācijas nesēju mapē netiek atbalstītas mapes.
media-check-missing-header = Uz šīm datnēm ir atsauces no kartītēm, bet netika atrastas informācijas nesēju mapē:
media-check-unused-header = Šīs datnes tika atrastas informācijas nesēju mapē, bet izskatās, ka tās netiek izmantotas nevienā kartītē:
media-check-template-references-field-header =
    Anki nevar noteikt izmantotās datnes, ja tiek izmantotas atsauces { "{{Field}}" } informācijas nesēju / LaTeX birkās. Tā vietā Informācijas nesēju / LaTeX birkas jāizmanto atsevišķām piezīmēm.
    
    Veidnes ar atsaucēm:

## Shown once for each file

media-check-renamed-file = Pārdēvēta: { $old } -> { $new }
media-check-oversize-file = Virs 100 MB: { $filename }
media-check-subfolder-file = Mape: { $filename }
media-check-missing-file = Trūkst: { $filename }
media-check-unused-file = Neizmantota: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = Pārbaudīti { $count }...

## Deleting unused media

media-check-delete-unused-confirm = Izdzēst neizmantotos informācijas nesējus?
media-check-files-remaining =
    { $count ->
        [zero] Atlikušas { $count } datņu.
        [one] Atlikusi { $count } datne.
       *[other] Atlikušas { $count } datnes.
    }
media-check-delete-unused-complete =
    { $count ->
        [zero] { $count } datņu pārvietotas uz atkritni.
        [one] { $count } datne pārvietota uz atkritni.
       *[other] { $count } datnes pārvietotas uz atkritni.
    }
media-check-trash-emptied = Atkritnes mape tagad ir tukša.
media-check-trash-restored = Atjaunot izdzēstās datnes informācijas nesēju mapē.

## Rendering LaTeX

media-check-all-latex-rendered = Atveidots viss LaTeX.

## Buttons

media-check-delete-unused = Izdzēst neizmantotos
media-check-render-latex = Atveidot LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = Iztukšot atkritni
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Atjaunot izdzēstās
media-check-check-media-action = Pārbaudīt informācijas nesējus
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = trūkst-informācijas-nesēji
# add a tag to notes with missing media
media-check-add-tag = Trūkst birkas
