importing-failed-debug-info = La importación falló. Información de depuración:
importing-aborted = Abortada: { $val }
importing-added-duplicate-with-first-field = Añadida duplicada con primer campo: { $val }
importing-all-supported-formats = Todos los formatos compatibles { $val }
importing-allow-html-in-fields = Permitir HTML en los campos
importing-anki-files-are-from-a-very = Los archivos .anki son de una versión muy vieja de Anki. Puedes importarlos utilizando el complemento 175027074 o con Anki 2.0, disponible en el sitio web de Anki.
importing-anki2-files-are-not-directly-importable = Los archivos .anki2 no se pueden importar directamente. Por favor, en su lugar importa los archivos .apkg o .zip que hayas recibido.
importing-appeared-twice-in-file = Apareció dos veces en el archivo: { $val }
importing-by-default-anki-will-detect-the = Por defecto, Anki detectará el carácter entre campos, como podría ser una marca de tabulación, una coma o similares. Si Anki está detectando el carácter incorrectamente puedes introducirlo aquí. Usa \t para representar una marca de tabulación.
importing-cannot-merge-notetypes-of-different-kinds =
    Los tipos de nota de respuesta anidada no pueden ser combinados con los tipos de nota regulares.
    De todas formas, puedes importar igualmente el archivo desactivando “{ importing-merge-notetypes }”.
importing-change = Modificar
importing-colon = Dos puntos
importing-comma = Coma
importing-empty-first-field = Primer campo vacio: { $val }
importing-field-separator = Separador de campos
importing-field-separator-guessed = Separador de campo (detectado)
importing-field-mapping = Asignar campos
importing-field-of-file-is = El campo <b>{ $val }</b> del archivo es:
importing-fields-separated-by = Campos separados por: { $val }
importing-file-must-contain-field-column = El archivo debe contener al menos una columna que pueda asignarse a un campo de la nota.
importing-file-version-unknown-trying-import-anyway = Versión del archivo desconocida, intentando importarlo de todas formas.
importing-first-field-matched = Primer campo coincidente: { $val }
importing-identical = Idéntico
importing-ignore-field = Ignorar campo
importing-ignore-lines-where-first-field-matches = Ignorar líneas en que el primer campo coincida con una nota existente
importing-ignored = <ignorado>
importing-import-even-if-existing-note-has = Importar aún cuando exista alguna nota con el mismo primer campo
importing-import-options = Opciones de importación
importing-importing-complete = Importación completa.
importing-invalid-file-please-restore-from-backup = Archivo no válido. Por favor, restaura desde una copia de seguridad.
importing-map-to = Asignar a { $val }
importing-map-to-tags = Asignar a etiquetas
importing-mapped-to = asignado a <b>{ $val }</b>
importing-mapped-to-tags = asignado a <b>etiquetas</b>
# the action of combining two existing note types to create a new one
importing-merge-notetypes = Combinar tipos de nota
importing-merge-notetypes-help =
    Si está seleccionado, y tú o el autor del mazo alteran el esquema de un tipo de nota, 
    Anki combinará las dos versiones en lugar de mantener ambas.
    
    Alterar el esquema de una nota quiere decir añadir, eliminar o reordenar campos o plantillas, o cambiar el campo según el cual se ordena.
    Como contraejemplo, cambiar el anverso de una plantilla existente *no* constituye
    un cambio de esquema.
    
    Advertencia: esto requerirá una sincronización en una sola dirección, y puede marcar notas existentes como modificadas.
importing-mnemosyne-20-deck-db = Mazo Mnemosyne 2.0 (*.db)
importing-multicharacter-separators-are-not-supported-please = Los separadores de más de un carácter no son válidos. Por favor, introduzca un solo carácter.
importing-new-deck-will-be-created = Un nuevo mazo se creará: { $name }
importing-notes-added-from-file = Notas añadidas desde el archivo: { $val }
importing-notes-found-in-file = Notas encontradas en el archivo: { $val }
importing-notes-skipped-as-theyre-already-in = Notas omitidas, pues ya se encuentran en tu colección copias actualizadas: { $val }
importing-notes-skipped-update-due-to-notetype = Notas no actualizadas, ya que el tipo de nota ha sido modificado desde que importaste las notas por primera vez: { $val }
importing-notes-updated-as-file-had-newer = Notas actualizadas, existía una nueva versión del archivo: { $val }
importing-include-reviews = Incluir revisiones
importing-also-import-progress = Importar cualquier progreso de aprendizaje
importing-with-deck-configs = Importar cualquier configuración del mazo
importing-updates = Actualizaciones
importing-include-reviews-help =
    Si está activado, cualquier revisión anterior que la persona que compartió el mazo haya incluido también será importada.
    En caso contrario, todas las tarjetas serán importadas como tarjetas nuevas, y cualquier etiqueta 
    de “olvidada” o “marcada” será removida.
importing-with-deck-configs-help =
    Si está activado, cualquier opción del mazo que la persona que compartió el mazo haya incluido también será importada.
    En caso contrario, se le asignará la configuración por defecto a todos los mazos.
importing-packaged-anki-deckcollection-apkg-colpkg-zip = Mazo de Anki/Colección comprimido (*.apkg *.colpkg *.zip)
# the '|' character
importing-pipe = Barra vertical
# Warning displayed when the csv import preview table is clipped (some columns were hidden)
# $count is intended to be a large number (1000 and above)
importing-preview-truncated =
    { $count ->
        [one] Solo se muestra la primera columna. Si esto no parece correcto, trata cambiando el separador de campos.
       *[other] Solo se muestran las primeras { $count } columnas. Si esto no parece correcto, trata cambiando el separador de campos.
    }
importing-rows-had-num1d-fields-expected-num2d = “{ $row }” tenía { $found } campos, se esperaban { $expected }
importing-selected-file-was-not-in-utf8 = El archivo seleccionado no estaba en formato UTF-8. Por favor, lee la sección sobre importación en el manual.
importing-semicolon = Punto y coma
importing-skipped = Saltado
importing-tab = Tabulación
importing-tag-modified-notes = Etiqueta las notas modificadas:
importing-text-separated-by-tabs-or-semicolons = Texto separado por tabulaciones o punto y coma (*)
importing-the-first-field-of-the-note = El primer campo del tipo de nota debe ser asignado a algo.
importing-the-provided-file-is-not-a = El archivo proporcionado no es un archivo .apkg valido.
importing-this-file-does-not-appear-to = Este archivo no parece ser un archivo .apkg válido. Si estás obteniendo este error con un archivo descargado desde AnkiWeb, es posible que tu descarga haya fallado. Por favor, vuelve a intentarlo, y si el problema continua, vuelve a intentarlo con otro navegador.
importing-this-will-delete-your-existing-collection = Esto eliminará tu colección actual y la reemplazará con los datos del archivo que estás importando. ¿Estás seguro?
importing-unable-to-import-from-a-readonly = No es posible importar desde un archivo de sólo lectura.
importing-unknown-file-format = Formato de archivo desconocido.
importing-update-existing-notes-when-first-field = Actualizar las tarjetas existentes cuando el primer campo coincida
importing-updated = Actualizado
importing-update-if-newer = Si es más nueva
importing-update-always = Siempre
importing-update-never = Nunca
importing-update-notes = Actualizar notas
importing-update-notes-help =
    Cuándo actualizar una nota existente en tu colección. Por defecto, esto solo se hace
    si la nota importada que coincide fue modificada más recientemente.
importing-update-notetypes = Actualizar tipos de nota
importing-update-notetypes-help =
    Cuándo actualizar un tipo de nota existente en tu colección. Por defecto, esto solo se hace
    si el tipo de nota importado coincidente fue modificado más recientemente. Cambios al texto de la plantilla
    y al formato siempre pueden ser importados, pero para cambios al esquema (por ejemplo, si el número
    o el orden de los campos cambió) la opción “{ importing-merge-notetypes }” también debe estar activada.
importing-note-added =
    { $count ->
        [one] { $count } nota añadida
       *[other] { $count } notas añadidas
    }
importing-note-imported =
    { $count ->
        [one] { $count } nota importada.
       *[other] { $count } notas importadas.
    }
importing-note-unchanged =
    { $count ->
        [one] { $count } nota sin cambios
       *[other] { $count } notas sin cambios
    }
importing-note-updated =
    { $count ->
        [one] { $count } nota actualizada
       *[other] { $count } notas actualizadas
    }
importing-processed-media-file =
    { $count ->
        [one] Se importó { $count } archivo multimedia
       *[other] Se importaron { $count } archivos multimedia
    }
importing-importing-file = Importando archivo…
importing-extracting = Extrayendo datos…
importing-gathering = Recopilando datos…
importing-failed-to-import-media-file = No se pudo importar el archivo multimedia: { $debugInfo }
importing-processed-notes =
    { $count ->
        [one] { $count } nota procesada…
       *[other] { $count } notas procesadas…
    }
importing-processed-cards =
    { $count ->
        [one] Se procesó { $count } tarjeta…
       *[other] Se procesaron { $count } tarjetas…
    }
importing-existing-notes = Notas existentes
# "Existing notes: Duplicate" (verb)
importing-duplicate = Duplicar
# "Existing notes: Preserve" (verb)
importing-preserve = Conservar
# "Existing notes: Update" (verb)
importing-update = Actualizar
importing-tag-all-notes = Etiquetar todas las notas
importing-tag-updated-notes = Etiquetar las notas actualizadas
importing-file = Archivo
# "Match scope: notetype / notetype and deck". Controls how duplicates are matched.
importing-match-scope = Alcance de coincidencia
# Used with the 'match scope' option
importing-notetype-and-deck = Tipo de nota y mazo
importing-cards-added =
    { $count ->
        [one] { $count } tarjeta añadida.
       *[other] { $count } tarjetas añadidas.
    }
importing-file-empty = El archivo que seleccionaste está vacío.
importing-notes-added =
    { $count ->
        [one] { $count } nueva nota importada.
       *[other] { $count } nuevas notas importadas.
    }
importing-notes-updated =
    { $count ->
        [one] { $count } nota fue utilizada para actualizar notas existentes.
       *[other] { $count } notas fueron utilizadas para actualizar notas existentes.
    }
importing-existing-notes-skipped =
    { $count ->
        [one] { $count } nota ya presente en tu colección.
       *[other] { $count } notas ya presentes en tu colección.
    }
importing-notes-failed =
    { $count ->
        [one] { $count } nota no pudo ser importada.
       *[other] { $count } notas no pudieron ser importadas.
    }
importing-conflicting-notes-skipped =
    { $count ->
        [one] { $count } nota no fue importada, pues su tipo de nota cambió.
       *[other] { $count } notas no fueron importadas, pues sus tipos de nota cambiaron.
    }
importing-conflicting-notes-skipped2 =
    { $count ->
        [one] { $count } nota no fue importada, porque su tipo de nota cambió, y “{ importing-merge-notetypes }” no estaba activado.
       *[other] { $count } notas no fueron importadas, porque su tipo de nota cambió, y “{ importing-merge-notetypes }” no estaba activado.
    }
importing-import-log = Bitácora de importación
importing-no-notes-in-file = No se encontraron notas en el archivo.
importing-notes-found-in-file2 =
    { $notes ->
        [one] { $notes } nota encontrada en el archivo. De estas:
       *[other] { $notes } notas encontradas en el archivo. De estas:
    }
importing-show = Mostrar
importing-details = Detalles
importing-status = Estado
importing-duplicate-note-added = Nota duplicada añadida
importing-added-new-note = Nueva nota añadida
importing-existing-note-skipped = Nota omitida, porque ya hay una copia actualizada en tu colección
importing-note-skipped-update-due-to-notetype = Nota no actualizada, porque el tipo de nota ha sido modificado desde que importaste la nota por primera vez
importing-note-skipped-update-due-to-notetype2 = Nota no actualizada, porque el tipo de nota ha sido modificado desde que importaste la nota por primera vez, y “{ importing-merge-notetypes }” no estaba activado
importing-note-updated-as-file-had-newer = Nota actualizada, pues el archivo tenía una versión más reciente
importing-note-skipped-due-to-missing-notetype = Nota omitida, pues falta su tipo de nota
importing-note-skipped-due-to-missing-deck = Nota omitida, pues falta su mazo
importing-note-skipped-due-to-empty-first-field = Nota omitida, pues su primer campo está vacío
importing-field-separator-help =
    El carácter separando los campos en el archivo de texto. Puedes usar la vista previa
    para verificar que los campos estén separados correctamente.
    
    Por favor, ten en cuenta que si este carácter aparece en cualquier campo, dicho campo
    debe encontrarse entre comillas, de acuerdo con el estándar CSV. Los programas de hoja de cálculo
    como LibreOffice harán esto automáticamente.
importing-allow-html-in-fields-help = Habilita esto si el archivo contiene formato  HTML. P.ej. si el archivo cotiene la cadena '&lt;br&gt;', aparecerá como un salto de línealidad en tu tarjeta. Sin embargo, con esta opción deshabilitada, los caracteres '&lt;br&gt;' se mostrarán como texto plano.
importing-notetype-help =
    Las notas que se importen ahora tendrán este tipo de nota, y sólo las notas existentes con este¶
    tipo de nota se actualizarán¶
    ¶
    Puedes elegir qué campos corresponden a cada tipo de nota mediante la herramienta de mapeo¶.
importing-deck-help = Las tarjetas importadas serán puestas en este mazo.
importing-existing-notes-help =
    Qué hacer cuando una nota importada coincida con una existente.
    
    - `{ importing-update }`: Actualizar la nota existente.¶
    - `{ importing-preserve }`: Hacer nada.¶
    - `{ importing-duplicate }`: Crear una nota nueva.
importing-match-scope-help =
    Solo las notas existentes, del mismo tipo de nota, serán comprobadas para ver si están duplicadas.
    Adicionalmente, se puede restringir a notas en el mismo mazo.
importing-tag-all-notes-help = Se añadirán las etiquetas tanto a las notas que importes y a las que actualices.
importing-tag-updated-notes-help = Estas etiquetas serán añadidas a las notas que actualizes.
importing-overview = Resumen

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

importing-importing-collection = Importando colección...
importing-unable-to-import-filename = No fue posible importar { $filename }: tipo de archivo no compatible
importing-notes-that-could-not-be-imported = Notas que no pudieron importarse debido a un cambio de tipo: { $val }
importing-added = Añadidas
importing-pauker-18-lesson-paugz = Lección Pauker 1.8 (*.pau.gz)
importing-supermemo-xml-export-xml = XML exportado de Supermemo (*.xml)
