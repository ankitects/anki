### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = Añadido: { $up }↑ { $down }↓
sync-media-removed-count = Eliminados: { $up }↑ { $down }↓
sync-media-checked-count = Comprobados: { $count }
sync-media-starting = Empezando la sincronización de multimedia....
sync-media-complete = Sincronización multimedia completada.
sync-media-failed = Sincronización multimedia ha fallado.
sync-media-aborting = Abortando la sincronización multimedia...
sync-media-aborted = Sincronización multimedia abortada.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = Sincronización multimedia deshabilitada.
# Title of the screen that shows syncing progress history
sync-media-log-title = Registro de Sincronización de Archivos Multimedia

## Error messages / dialogs

sync-conflict = Sólo una copia de Anki puede sincronizar a tu cuenta a la vez. Por favor, espera unos minutos y vuelve a intentarlo de nuevo.
sync-server-error = AnkiWeb ha encontrado un problema. Por favor, intentalo de nuevo en unos minutos.
sync-client-too-old = Tu versión de Anki es demasiado antigua. Por favor, actualiza a la última versión antes de sincronizar.
sync-wrong-pass = La ID de AnkiWeb o la contraseña son incorrectas; por favor, inténtalo de nuevo.
sync-resync-required = Por favor, sincroniza otra vez. Si el error sigue apareciendo, por favor, publica en el sitio de soporte.
sync-must-wait-for-end = Anki se está sincronizando actualmente. Espere a que se complete la sincronización e intente nuevamente.
sync-confirm-empty-download = La colección local no contiene ninguna tarjeta. ¿Desea descargarlas de AnkiWeb?
sync-confirm-empty-upload = No hay tarjetas en la colección de AnkiWeb. ¿Reemplazarla con la colección local?
sync-conflict-explanation =
    Tus mazos aquí y en AnkiWeb difieren de tal manera que no pueden ser combinados, por lo que es necesario sobrescribir los mazos de un lado con los del otro.
    
    Si eliges descargar, Anki descargará la colección desde AnkiWeb, y se perderá cualquier cambio que hayas hecho en tu ordenador desde la última sincronización.
    
    Si eliges subir, Anki subirá tu colección a AnkiWeb, y se perderá cualquier cambio que hayas hecho en AnkiWeb o en tus otros dispositivos desde la última sincronización.
    
    Después de que todos los dispositivos se hayan sincronizado, los futuros repasos y las tarjetas añadidas podrán ser combinados automáticamente.
sync-conflict-explanation2 =
    Hay un conflicto entre mazos en este dispositivo y AnkiWeb. Debes elegir que versión guardar:
    -Selecciona **{ sync-download-from-ankiweb }** para reemplazar los mazos en este dispositivo con la versión de AnkiWeb. Perderás cualquier cambio que hayas realizado desde la última vez que sincronizaste este dispositivo.
    -Selecciona **{ sync-upload-to-ankiweb }** para sobrescribir la versión de AnkiWeb con los mazos que se encuentran en este dispositivo, y eliminar cualquier cambio en AnkiWeb.
    
    Una vez que se resuelva el conflicto, la sincronización funcionará como normal.
sync-ankiweb-id-label = ID de AnkiWeb:
sync-password-label = Contraseña:
sync-account-required =
    <h1>Se requiere una cuenta</h1>
    Se requiere una cuenta gratuita para mantener tu colección sincronizada. Por favor, <a href="{ $link }">regístrate</a> e introduce tus detalles aquí debajo.
sync-sanity-check-failed = Por favor, use la función de Comprobar Base de Datos, después sincronize otra vez. Si los problemas persisten, por favor fuerce una sincronización completa en la ventana de preferencias.
sync-clock-off = Imposible sincronizar - compruebe que su reloj tenga la hora correcta.
sync-upload-too-large =
    Tu archivo de colección es demasiado grande para enviarlo a AnkiWeb. Puede reducir su tamaño 
    eliminando los mazos no deseados (opcionalmente exportándolas primero, por seguridad)
    y luego hacer clic en Comprobar base de datos (en el menú Herramientas) para reducir
    el tamaño del archivo. ({ $details })
sync-sign-in = Iniciar sesión
sync-ankihub-dialog-heading = Iniciar sesión en AnkiHub
sync-ankihub-username-label = Usuario o correo electrónico:
sync-ankihub-login-failed = Incapaz de iniciar sesión en Ankihub con las credenciales que se han proporcionado.
sync-ankihub-addon-installation = Instalación de complementos de AnkiHub.

## Buttons

sync-media-log-button = Registro de Archivos Multimedia
sync-abort-button = Abortar
sync-download-from-ankiweb = Descargar desde AnkiWeb
sync-upload-to-ankiweb = Subir a AnkiWeb
sync-cancel-button = Cancelar

## Normal sync progress

sync-downloading-from-ankiweb = Descargando desde AnkiWeb...
sync-uploading-to-ankiweb = Subiendo a AnkiWeb...
sync-syncing = Sincronizando...
sync-checking = Comprobando…
sync-connecting = Conectando...
sync-added-updated-count = Añadido/modificado: { $up }↑ { $down }↓
sync-log-in-button = Iniciar sesión
sync-log-out-button = Cerrar sesión
sync-collection-complete = Se ha sincronizado la colección
