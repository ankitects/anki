## Shown at the top of the media check screen

media-check-window-title = Comprobar multimedia
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    { $count ->
        [one] Papeleira: { $count } ficheiro, { $megs }MB
       *[other] Papeleira: { $count } ficheiros, { $megs }MB
    }
media-check-missing-count = Ficheiros ausentes: { $count }
media-check-unused-count = Ficheiros sen usar: { $count }
media-check-renamed-count = Ficheiros renomeados: { $count }
media-check-subfolder-count = Subcartafoles: { $count }
media-check-extracted-count = Imaxes extraídas:  { $count }

## Shown at the top of each section

media-check-missing-header = Faltan no cartafol multimedia mais usanse en tarxetas:
media-check-unused-header = Os seguintes ficheiros están presentes no cartafol de multimedia, mais non se usan en ningunha tarxeta:

## Shown once for each file

media-check-renamed-file = Renomeouse: { $old } -> { $new }
media-check-subfolder-file = Cartafol: { $filename }
media-check-unused-file = Sen usar: { $filename }

##


## Progress


## Deleting unused media

media-check-delete-unused-confirm = Eliminar os ficheiros multimedia non usados?
media-check-files-remaining =
    { $count ->
        [one] { $count } ficheiro restante.
       *[other] { $count } ficheiros restantes.
    }
media-check-delete-unused-complete =
    { $count ->
        [one] Moveuse { $count } ficheiro á papeleira.
       *[other] Movéronse { $count } ficheiros á papeleira.
    }
media-check-trash-emptied = Baleirouse a papeleira.
media-check-trash-restored = Restauráronse os ficheiros eliminados ao cartafol multimedia.

## Rendering LaTeX


## Buttons

media-check-delete-unused = Eliminar non usados
# button to permanently delete media files from the trash folder
media-check-empty-trash = Baleirar papeleira
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Restaurar eliminados
media-check-check-media-action = Comprobar multimedia
