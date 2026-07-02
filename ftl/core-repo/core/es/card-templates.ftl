# This word is used by TTS voices instead of the elided part of a cloze.
card-templates-blank = vacío
card-templates-changes-will-affect-notes =
    { $count ->
        [one] Los cambios debajo afectarán { $count } nota que utiliza este tipo de tarjeta.
       *[other] Los cambios debajo afectarán { $count } notas que utilizan este tipo de tarjeta.
    }
card-templates-card-type = Tipo de tarjeta:
card-templates-front-template = Plantilla del anverso
card-templates-back-template = Plantilla del reverso
card-templates-template-styling = Estilo
card-templates-front-preview = Vista previa del anverso
card-templates-back-preview = Vista previa del reverso
card-templates-preview-box = Previsualizar
card-templates-template-box = Plantilla
card-templates-sample-cloze = Esto es un { "{{c1::" }ejemplo{ "}}" } de respuesta anidada.
card-templates-fill-empty = Rellenar campos vacíos
card-templates-night-mode = Modo nocturno
# Add "mobile" class to card preview, so the card appears like it would
# on a mobile device.
card-templates-add-mobile-class = Añadir clase de CSS para dispositivos móviles
card-templates-preview-settings = Opciones
card-templates-invalid-template-number = La plantilla de tarjeta { $number } en el tipo de nota “{ $notetype }” tiene un problema.
card-templates-identical-front = El anverso es idéntico a la plantilla de tarjeta { $number }.
card-templates-no-front-field = Se espera encontrar un reemplazo de campo en el anverso de la plantilla de tarjeta.
card-templates-missing-cloze = Se espera encontrar “{ "{{" }cloze:Texto{ "}}" }” o similar en el anverso y reverso de la plantilla de tarjeta.
card-templates-extraneous-cloze = “cloze:” solo se puede usar en notas de tipo respuesta anidada.
card-templates-see-preview = Vea la vista previa para obtener más información.
card-templates-field-not-found = Campo “{ $field }” no encontrado.
card-templates-changes-saved = Cambios guardados.
card-templates-discard-changes = ¿Quieres descartar los cambios?
card-templates-add-card-type = Añadir tipo de tarjeta…
card-templates-anki-couldnt-find-the-line-between = Anki no ha podido encontrar la línea de separación entre la pregunta y la respuesta. Por favor, ajusta manualmente la plantilla para intercambiar la pregunta y la respuesta.
card-templates-at-least-one-card-type-is = Se requiere como mínimo un tipo de tarjeta.
card-templates-browser-appearance = Apariencia en el navegador…
card-templates-card = Tarjeta { $val }
card-templates-card-types-for = Tipos de tarjeta para { $val }
card-templates-cloze = Respuesta anidada { $val }
card-templates-deck-override = Reemplazar mazo…
card-templates-copy-info = Copiar información al portapapeles
card-templates-delete-the-as-card-type-and = ¿Eliminar el tipo de tarjeta “{ $template }”, y sus { $cards }?
card-templates-enter-deck-to-place-new = Introduce el mazo en el que quieras colocar las { $val } tarjetas nuevas, o deja el campo vacío:
card-templates-enter-new-card-position-1 = Introduce la nueva posición de la tarjeta (1…{ $val }):
card-templates-flip = Invertir
card-templates-form = Formulario
card-templates-off = (desactivado)
card-templates-on = (activado)
card-templates-remove-card-type = Eliminar tipo de tarjeta…
card-templates-rename-card-type = Renombrar tipo de tarjeta…
card-templates-reposition-card-type = Reposicionar tipo de tarjeta…
card-templates-card-count =
    { $count ->
        [one] { $count } tarjeta
       *[other] { $count } tarjetas
    }
card-templates-this-will-create-card-proceed =
    { $count ->
        [one] Se creará { $count } tarjeta. ¿Seguir?
       *[other] Se crearán { $count } tarjetas. ¿Seguir?
    }
card-templates-type-boxes-warning = Solo se permite un cuadro de escritura por plantilla de tarjeta.
card-templates-restore-to-default = Restaurar valores predeterminados
card-templates-restore-to-default-confirmation =
    Esto restablecerá todos los campos y plantillas en este tipo de nota a sus valores predeterminados, 
    eliminando cualquier campo/plantilla adicional y su contenido, así como cualquier estilo personalizado. ¿Deseas continuar?
card-templates-restored-to-default = El tipo de nota ha sido restaurado a su estado original.
