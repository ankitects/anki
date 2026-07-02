## Shown at the top of the media check screen

media-check-window-title = Verificar archivos multimedia
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    Carpeta de papelera: { $count ->
        [one] 1 archivo, { $megs }MB
       *[other] { $count } archivos, { $megs }MB
    }
media-check-missing-count = Archivos faltantes: { $count }
media-check-unused-count = Archivos no usados: { $count }
media-check-renamed-count = Archivos renombrados: { $count }
media-check-oversize-count = Mayor de 100MB: { $count }
media-check-subfolder-count = Subcarpetas: { $count }
media-check-extracted-count = Imágenes extraídas: { $count }

## Shown at the top of each section

media-check-renamed-header = Algunos archivos han sido renombrados por compatibilidad:
media-check-oversize-header = Archivos mayores de 100MB no se pueden sincronizar con AnkiWeb.
media-check-subfolder-header = Carpetas dentro de la carepta multimedia no son soportadas.
media-check-missing-header = Los siguientes archivos son referenciados por tarjetas, no obstante no se encuentran en la carpeta multimedia:
media-check-unused-header = Los siguientes archivos se encuentran en la carpeta multimedia, no obstante, no aparecen ser usados por ninguna tarjeta:
media-check-template-references-field-header =
    Anki no puede detectar archivos cuando usas { "{{Field}}" } referencias en etiquetas multimedia/LaTeX. En su lugar, etiquetas multimedia/LaTeX deben colocarse en notas individuales.
    
    Plantillas de referencia:

## Shown once for each file

media-check-renamed-file = Renombrado: { $old } -> { $new }
media-check-oversize-file = Mayor de 100MB: { $count }
media-check-subfolder-file = Carpeta: { $filename }
media-check-missing-file = Falta: { $filename }
media-check-unused-file = Sin usar: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = Comprobado { $count }...

## Deleting unused media

media-check-delete-unused-confirm = ¿Eliminar los archivos multimedia sin usar?
media-check-files-remaining =
    { $count ->
        [one] 1 archivo
       *[other] { $count } archivos
    } restantes.
media-check-delete-unused-complete =
    { $count ->
        [one] 1 archivo
       *[other] { $count } archivos
    } movidos a la papelera de reciclaje.
media-check-trash-emptied = La carpeta de la papelera ahora está vacía.
media-check-trash-restored = Restaurado archivos borrados a la carpeta de archivos multimedia.

## Rendering LaTeX

media-check-all-latex-rendered = Todo LaTeX renderizado.

## Buttons

media-check-delete-unused = Eliminar no utilizadas
media-check-render-latex = Renderizar LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = Vacíar Papelera
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Restaurar Eliminadas
media-check-check-media-action = Verificar archivos multimedia
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = falta-multimedia
# add a tag to notes with missing media
media-check-add-tag = Etiquetar Faltantes
