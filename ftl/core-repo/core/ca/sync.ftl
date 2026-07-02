### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = Afegits: { $up }↑ { $down }↓
sync-media-removed-count = Eliminats: { $up }↑ { $down }↓
sync-media-checked-count = Comprovats: { $count }
sync-media-starting = Iniciant la sincronizació dels fitxers multimèdia…
sync-media-complete = Sincronitació dels fitxers multimèdia completada.
sync-media-failed = La sincronització dels fitxers multimèdia ha fallat.
sync-media-aborting = Avortant la sincronització dels fitxers multimèdia…
sync-media-aborted = Sincronització dels fitxers multimèdia avortada.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = La sincronització dels fitxers multimèdia està desactivada.
# Title of the screen that shows syncing progress history
sync-media-log-title = Registre de sincronització dels fitxers multimèdia

## Error messages / dialogs

sync-conflict = No podeu sincronitzar més d'una instància d'Anki alhora al vostre compte. Espereu uns minuts i torneu-ho a intentar.
sync-server-error = AnkiWeb ha trobat un problema. Espereu uns minuts i torneu-ho a intentar.
sync-client-too-old = La vostra versió d'Anki és massa antiga. Actualitzeu a l'última versió per a continuar amb la sincronització.
sync-wrong-pass = El vostre identificador d'AnkiWeb o la contrasenya són incorrectes; torneu-ho a intentar.
sync-resync-required = Torneu a sincronitzar les dades. Si l'error persisteix, publiqueu un missatge al lloc de suport.
sync-must-wait-for-end = S’està sincronitzant Anki. Espereu que acabi la sincronització i torneu-ho a intentar.
sync-confirm-empty-download = La col·lecció local no conté cap targeta. Voleu descarregar-ne des d’AnkiWeb?
sync-confirm-empty-upload = La col·lecció d’AnkiWeb està buida. Voleu substituir-la per la col·lecció local?
sync-conflict-explanation =
    Les baralles d’aquest dispositiu i les d’AnkiWeb són diferents i no es poden combinar. Cal substituir les baralles d’una ubicació per les de l’altra.
    
    Si premeu «Baixa d’AnkiWeb», Anki baixarà la col·lecció d’AnkiWeb i perdreu tots els canvis fets en aquest dispositiu des de l’última sincronització.
    
    Si premeu «Puja a AnkiWeb», Anki pujarà la col·lecció a AnkiWeb i perdreu tots els canvis fets en AnkiWeb o en altres dispositiu des de l’última sincronització.
    
    Quan tots els dispositius estiguin sincronitzats, els repassos futurs i les targetes noves es combinaran automàticament.
sync-conflict-explanation2 =
    S’ha produït un conflicte entre les baralles d’aquest dispositiu i les d’AnkiWeb. Trieu quines voleu conservar:
    
    - **{ sync-download-from-ankiweb }:** substitueix les baralles d’aquest dispositiu amb les d’AnkiWeb (perdreu els canvis fets en aquest dispositiu des de l’última sincronització).
    - **{ sync-upload-to-ankiweb }:** substitueix les baralles d’AnkiWeb amb les baralles d’aquest dispositiu i elimina qualsevol canvi en AnkiWeb.
    
    Quan s’hagi resolt el conflicte, la sincronització funcionarà amb normalitat.
sync-ankiweb-id-label = Identificador d'AnkiWeb:
sync-password-label = Contrasenya:
sync-account-required =
    <h1>Cal tenir un compte</h1>
    Cal que tingueu un compte gratuït per a mantenir la vostra col·lecció actualitzada. <a href="{ $link }">Registreu-vos</a> i inseriu les vostres credencials a sota.
sync-sanity-check-failed = Premeu «Comprova la base de dades» i torneu a sincronitzar la col·lecció. Si els problemes persisteixen, forceu la sincronització des del menú de preferències.
sync-clock-off = No s'ha pogut sincronitzar la base de dades. Comproveu que el rellotge del vostre dispositiu mostra l'hora correcta.
# “details” expands to a string such as “300.14 MB > 300.00 MB”
sync-upload-too-large = El fitxer de col·lecció és massa gran per pujar-lo a AnkiWeb. Podeu reduir-ne la mida eliminant les baralles que no utilitzeu (si voleu, podeu exportar-les abans) i, a continuació, fent servir la funció «Comprova la base de dades» per a reduir la mida del fitxer. ({ $details })
sync-sign-in = Inicia la sessió
sync-ankihub-dialog-heading = Inicia la sessió a AnkiHub
sync-ankihub-username-label = Nom d’usuari o adreça electrònica:
sync-ankihub-login-failed = No s’ha pogut iniciar la sessió a AnkiHub amb les credencials proporcionades.
sync-ankihub-addon-installation = Instal·lació del complement d’AnkiHub

## Buttons

sync-media-log-button = Registre dels fitxers multimèdia
sync-abort-button = Interromp
sync-download-from-ankiweb = Baixa d’AnkiWeb
sync-upload-to-ankiweb = Puja a AnkiWeb
sync-cancel-button = Anul·la

## Normal sync progress

sync-downloading-from-ankiweb = S'està baixant des d'AnkiWeb…
sync-uploading-to-ankiweb = S’està pujant a AnkiWeb…
sync-syncing = S’està sincronitzant…
sync-checking = S’està comprovant…
sync-connecting = S'està connectant…
sync-added-updated-count = Afegides o modificades: { $up }↑ { $down }↓
sync-log-in-button = Inicia la sessió
sync-log-out-button = Tanca la sessió
sync-collection-complete = S’ha sincronitzat la col·lecció.
