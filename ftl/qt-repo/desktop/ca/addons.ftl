addons-possibly-involved = Complements que potser hi estan involucrats: { $addons }
addons-failed-to-load =
    No s’ha pogut carregar un dels complements que heu instal·lat. Si el problema persisteix, deshabiliteu-lo o suprimiu-lo en el menú Eines > Complements.
    
    Mentre es carregava «{ $name }»
    { $traceback }
addons-failed-to-load2 =
    No s’ha pogut carregar els següents complements:
    { $addons }
    
    És possible que hàgiu d’actualitzar-los perquè siguin compatibles amb aquesta versió d’Anki. Feu clic en { addons-check-for-updates } per a comprovar si n’hi ha actualitzacions disponibles.
    
    Podeu fer servir el botó { about-copy-debug-info } per a obtenir informació que pugueu enviar a l’autor del complement.
    
    Quant als complements que no tenen cap actualització disponible, podeu desactivar o suprimir el complement per a evitar que aparegui aquest missatge.
addons-startup-failed = L’activació del complement ha fallat
# Shown in the add-on configuration screen (Tools>Add-ons>Config), in the title bar
addons-config-window-title = Configura '{ $name }'
addons-config-validation-error = Hi ha hagut un problema amb la configuració que heu proporcionat: { $problem }, en la ruta { $path }, contra l'esquema { $schema }.
addons-window-title = Complements
addons-addon-has-no-configuration = Aquest complement no té cap configuració.
addons-addon-installation-error = No s’ha pogut instal·lar el complement
addons-browse-addons = Explora els complements
addons-changes-will-take-effect-when-anki = Els canvis s’aplicaran quan reinicieu Anki.
addons-check-for-updates = Comprova si hi ha actualitzacions
addons-checking = S'està comprovant…
addons-code = Codi:
addons-config = Configuració
addons-configuration = Configuració
addons-corrupt-addon-file = El fitxer del complement està malmès.
addons-disabled = (desactivat)
addons-disabled2 = (desactivat)
addons-download-complete-please-restart-anki-to = S'ha completat la descàrrega. Reinicieu Anki per a aplicar els canvis.
addons-downloaded-fnames = S'ha descarregat { $fname }
addons-downloading-adbd-kb02fkb = S’està descarregant { $part }/{ $total } ({ $kilobytes } KB)…
addons-error-downloading-ids-errors = No s’ha pogut descarregar <i>{ $id }</i>: { $error }
addons-error-installing-bases-errors = No s’ha pogut instal·lar <i>{ $base }</i>: { $error }
addons-get-addons = Descarrega complements…
addons-important-as-addons-are-programs-downloaded = <b>Important:</b> Els complements que descarregueu d’internet poden contenir programari maliciós.<b>Instal·leu només els complements en què confieu.</b><br><br>Segur que voleu continuar amb la instal·lació del(s) següent(s) complement(s) d’Anki?<br><br>%(names)s
addons-install-addon = Instal·la un complement
addons-install-addons = Instal·la el(s) complement(s)
addons-install-anki-addon = Instal·la un complement d'Anki
addons-install-from-file = Instal·la des d’un fitxer…
addons-installation-complete = S'ha completat la instal·lació
addons-installed-names = S'ha instal·lat { $name }
addons-installed-successfully = S'ha instal·lat correctament.
addons-invalid-addon-manifest = El manifiest del complement no es vàlid.
addons-invalid-code = El codi no és vàlid.
addons-invalid-code-or-addon-not-available = El codi no és vàlid o el complement no està disponible per a la vostra versió d’Anki.
addons-invalid-configuration = Configuració no vàlida:
addons-invalid-configuration-top-level-object-must = Configuració no vàlida: l'objecte del nivell superior ha de ser un mapa
addons-no-updates-available = No hi ha actualitzacions disponibles.
addons-one-or-more-errors-occurred = S’han produït un o més errors:
addons-packaged-anki-addon = Paquet de complement d’Anki
addons-please-check-your-internet-connection = Comproveu la vostra connexió a internet.
addons-please-report-this-to-the-respective = Informeu-ne els autors del complement.
addons-please-restart-anki-to-complete-the = <b>Reinicieu Anki per a completar la instal·lació.</b>
addons-please-select-a-single-addon-first = Primer heu de seleccionar un sol complement.
addons-requires = (requereix { $val })
addons-restored-defaults = S'ha restaurat la configuració per defecte
addons-the-following-addons-are-incompatible-with = Els següents complements no són compatibles amb { $name } i s'han desactivat: { $found }
addons-the-following-addons-have-updates-available = Els següents complements tenen actualitzacions disponibles. Voleu instal·lar-les ara?
addons-the-following-conflicting-addons-were-disabled = Els següents complements no són compatibles i s'han desactivat:
addons-this-addon-is-not-compatible-with = Aquest complement no és compatible amb la vostra versió d'Anki.
addons-to-browse-addons-please-click-the = Per a explorar els complements, feu clic en el botó de davall.<br><br>Quan en trobeu un que us interessi, introduïu-ne el codi a continuació. Podeu introduir-hi més d’un codi separant-los amb espais.
addons-toggle-enabled = Activa/desactiva
addons-unable-to-update-or-delete-addon = No s'ha pogut actualitzar ni eliminar el complement. Inicieu Anki mentre manteniu premuda la tecla de majúscules per a desactivar temporalment els complements i torneu-ho a intentar.   Informació de depuració: { $val }
addons-unknown-error = S’ha produït un error desconegut: { $val }
addons-view-addon-page = Obre la pàgina del complement
addons-view-files = Mostra els fitxers
addons-delete-the-numd-selected-addon =
    { $count ->
        [one] Voleu suprimir aquest complement?
       *[other] Voleu suprimir els { $count } complements seleccionats?
    }
addons-choose-update-window-title = Actualitza els complements
addons-choose-update-update-all = Actualitza-ho tot
