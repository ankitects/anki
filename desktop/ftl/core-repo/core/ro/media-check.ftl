## Shown at the top of the media check screen

media-check-window-title = Verificare Media
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    Dosarul de gunoi: { $count ->
        [one] { $count } fișier, { $megs }MB
        [few] { $count } fișiere, { $megs }MB
       *[other] { $count } fișiere, { $megs }MB
    }
media-check-missing-count = Fișiere lipsă: { $count }
media-check-unused-count = Fișiere nefolosite: { $count }
media-check-renamed-count = Fișiere redenumite: { $count }
media-check-oversize-count = Mai mari de 100MB: { $count }
media-check-subfolder-count = Sub-dosare: { $count }

## Shown at the top of each section

media-check-renamed-header = Unele fișiere au fost redenumite pentru compatibilitate:
media-check-oversize-header = Fișierele de peste 100 MB nu pot fi sincronizate cu  AnkiWeb.
media-check-subfolder-header = Nu sunt acceptate dosare in interiorul dosarului media
media-check-missing-header = Următoarele fișiere sunt citate de unele carduri, dar nu au fost găsite în folderul media:
media-check-unused-header = Următoarele fișiere au fost găsite în dosarulmedia, dar nu par să fie folosite pe niciun card:

## Shown once for each file

media-check-renamed-file = Redenumit: { $old } -> { $new }
media-check-oversize-file = Peste 100MB: { $filename }
media-check-subfolder-file = Dosar: { $filename }
media-check-missing-file = Lipsește: { $filename }
media-check-unused-file = Nefolosit: { $filename }

## Progress

media-check-checked = Verificate { $count }...

## Deleting unused media

media-check-delete-unused-confirm = Ștergi fișierele media nefolosite?
media-check-files-remaining =
    { $count ->
        [one] { $count } fișier
        [few] { $count } fișiere
       *[other] { $count } fișiere
    }rămas(e)
media-check-delete-unused-complete =
    { $count ->
        [one] { $count } fișier
        [few] { $count } fișiere
       *[other] { $count } fișiere
    } mutat(e) la gunoi.
media-check-trash-emptied = Dosarul de gunoi este acum gol.
media-check-trash-restored = Restaurează fișierele șterse în dosarul media.

## Rendering LaTeX

media-check-all-latex-rendered = Toate LaTeX au fost randate.

## Buttons

media-check-delete-unused = Șterge fișierele neutilizate
media-check-render-latex = Randează LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = Golește gunoiul
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Restabilește elementele șterse
media-check-check-media-action = Verifică Media
