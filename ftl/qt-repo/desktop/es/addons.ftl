addons-possibly-involved = Complementos posiblemente implicados: { $addons }
addons-failed-to-load =
    Un complemento que has instalado ha fallado al cargarse. Si los problemas persisten, por favor ve a Herramientas> Menú de complementos o deshabilita este complemento.
    
    Mientras cargando '{ $name }':
    { $traceback }
addons-failed-to-load2 =
    No se han podido cargar los siguientes complementos:
    { $addons }
    
    Puede que necesiten ser actualizados para ser compatibles con esta versión de Anki. Haz clic en el botón { addons-check-for-updates } para ver si hay actualizaciones disponibles.
    
    Puedes obtener información e informar al creador del complemento usando el botón { about-copy-debug-info }.
    
    Puedes deshabilitar los complementos que no tengan actualizaciones disponibles para prevenir que este mensaje aparezca.
addons-startup-failed = No se ha podido iniciar el complemento.
# Shown in the add-on configuration screen (Tools>Add-ons>Config), in the title bar
addons-config-window-title = Configurar '{ $name }'
addons-config-validation-error = Hubo un problema con la configuración proporcionada: { $problem }, en la ruta { $path }, contra el esquema { $schema }.
addons-window-title = Complementos
addons-addon-has-no-configuration = El Complemento no tiene configuración.
addons-addon-installation-error = Error de la instalación del complemento
addons-browse-addons = Explorar complementos
addons-changes-will-take-effect-when-anki = Los cambios tendrán efecto cuando Anki se reinicie
addons-check-for-updates = Comprobar actualizaciones
addons-checking = Comprobando…
addons-code = Código:
addons-config = Configuración
addons-configuration = Configuración
addons-corrupt-addon-file = El archivo del complemento está dañado.
addons-disabled = (desactivado)
addons-disabled2 = (desactivado)
addons-download-complete-please-restart-anki-to = Descarga completa. Reinicia Anki para aplicar los cambios.
addons-downloaded-fnames = { $fname } se ha descargado
addons-downloading-adbd-kb02fkb = Descargando { $part }/{ $total } ({ $kilobytes }KB)...
addons-error-downloading-ids-errors = Error al descargar <i>{ $id }</i>: { $error }
addons-error-installing-bases-errors = Error al instalar <i>{ $base }</i>: { $error }
addons-get-addons = Descargar complementos...
addons-important-as-addons-are-programs-downloaded = <b>Importante</b>: Como los complementos son programas descargados de Internet, son potencialmente maliciosos.<b>Solo debes instalar complementos en los que confíes.</b><br><br>¿Estás seguro de que deseas continuar con la instalación de los siguientes complemento(s) Anki?<br><br>%(names)s
addons-install-addon = Instalar complemento
addons-install-addons = Instalar complemento(s)
addons-install-anki-addon = Instalar complemento de Anki
addons-install-from-file = Instalar desde archivo…
addons-installation-complete = Instalación completada
addons-installed-names = Se instaló { $name }
addons-installed-successfully = Instalado correctamente.
addons-invalid-addon-manifest = El manifiesto del complemento no es válido.
addons-invalid-code = Código inválido.
addons-invalid-code-or-addon-not-available = El código no es válido o el complemento no está disponible para su versión de Anki.
addons-invalid-configuration = Configuración no válida:
addons-invalid-configuration-top-level-object-must = Configuración inválida: El objeto del nivel superior debe ser un mapa
addons-no-updates-available = No hay actualizaciones disponibles.
addons-one-or-more-errors-occurred = Uno o más errores han ocurrido:
addons-packaged-anki-addon = Complemento empaquetado de Anki
addons-please-check-your-internet-connection = Por favor, compruebe su conexión a Internet.
addons-please-report-this-to-the-respective = Informe de esto a los autores del respectivo complemento.
addons-please-restart-anki-to-complete-the = <b>Por favor, reinicia Anki para completar la instalación.</b>
addons-please-select-a-single-addon-first = Primero seleccione solo un complemento.
addons-requires = (requiere { $val })
addons-restored-defaults = Se restauró la configuración predeterminada
addons-the-following-addons-are-incompatible-with = Los siguientes complementos son incompatibles con { $name } y se han desactivado: { $found }
addons-the-following-addons-have-updates-available = Los siguientes complementos tienen actualizaciones disponibles. ¿Instalar ahora?
addons-the-following-conflicting-addons-were-disabled = Se desactivaron los siguientes complementos incompatibles:
addons-this-addon-is-not-compatible-with = Este complemento no es compatible con tu versión de Anki.
addons-to-browse-addons-please-click-the = Para explorar los complementos, pulse en el botón siguiente.<br><br>Cuando encuentre un complemento que le interese, pegue su código debajo. Puede pegar varios códigos; sepárelos mediante espacios.
addons-toggle-enabled = Habilitar sí/no
addons-unable-to-update-or-delete-addon = No se puede actualizar ni eliminar el complemento. Inicie Anki mientras mantiene presionada la tecla Mayús para desactivar temporalmente los complementos y, a continuación, intente de nuevo la operación.  Información de depuración: { $val }
addons-unknown-error = Error desconocido: { $val }
addons-view-addon-page = Visitar página del complemento
addons-view-files = Ver archivos
addons-delete-the-numd-selected-addon =
    { $count ->
        [one] ¿Quiere eliminar { $count } complemento seleccionado?
       *[other] ¿Quiere eliminar los { $count } complementos seleccionados?
    }
addons-choose-update-window-title = Actualizar complementos
addons-choose-update-update-all = Actualizar todo
