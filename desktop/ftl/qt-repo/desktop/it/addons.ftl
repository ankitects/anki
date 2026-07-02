addons-possibly-involved = Add-on che potrebbero essere coinvolti: { $addons }
addons-failed-to-load =
    Un add-on attualmente installato non ha potuto essere caricato. Se il problema permane, accedere al menu Strumenti>Add-on, e disabilitare o eliminare l'add-on in questione.
    
    Caricando '{ $name }':
    { $traceback }
addons-failed-to-load2 =
    Non è stato possibile caricare i seguenti add-on:
    { $addons }
    
    Potrebbe essere necessario aggiornarli per supportare questa versione di Anki.
    Per verificare la disponibilità di aggiornamenti cliccare
    sul pulsante { addons-check-for-updates }.
    
    Per ottenere informazioni da includere in una segnalazione all'autore dell'add-on,
    utilizzare il pulsante { about-copy-debug-info }.
    
    Per gli add-on che non hanno aggiornamento disponibili, è possibile disattivarli o eliminarli per evitare 
    che questo messaggio ricompaia.
addons-startup-failed = Avvio dell'add-on non riuscito
# Shown in the add-on configuration screen (Tools>Add-ons>Config), in the title bar
addons-config-window-title = Configura '{ $name }'
addons-config-validation-error = C'è stato un problema con la configurazione fornita: { $problem }, nel percorso { $path }, relativo allo schema { $schema }.
addons-window-title = Add-on
addons-addon-has-no-configuration = L'add-on non ha configurazioni.
addons-addon-installation-error = Errore di installazione dell'add-on
addons-browse-addons = Sfoglia add-on
addons-changes-will-take-effect-when-anki = I cambiamenti avranno effetto dopo il riavvio di Anki.
addons-check-for-updates = Controlla aggiornamenti
addons-checking = Controllo in corso...
addons-code = Codice:
addons-config = Configura
addons-configuration = Configurazione
addons-corrupt-addon-file = File add-on difettoso.
addons-disabled = (disabilitato)
addons-disabled2 = (disabilitato)
addons-download-complete-please-restart-anki-to = Download completo. RIavviare Anki per rendere effettive le modifiche.
addons-downloaded-fnames = Scaricato { $fname }
addons-downloading-adbd-kb02fkb = Download { $part }/{ $total } ({ $kilobytes }KB)...
addons-error-downloading-ids-errors = Errore durante il download <i>{ $id }</i>: { $error }
addons-error-installing-bases-errors = Errore nell'installazione di <i>{ $base }</i>: { $error }
addons-get-addons = Scarica add-on...
addons-important-as-addons-are-programs-downloaded = <b>Importante</b>: Gli add-on sono programmi scaricati da internet, e per questo potenzialmente dannosi.<b>È consigliato installare soltanto add-on di cui ci si fida.</b><br><br>Procedere all'installazione del seguente add-on per Anki?<br><br>%(names)s
addons-install-addon = Installa un Add-on
addons-install-addons = Installa Add-on
addons-install-anki-addon = Installa un add-on di Anki
addons-install-from-file = Installa da file...
addons-installation-complete = Installazione completata
addons-installed-names = { $name } installato
addons-installed-successfully = Installato con successo.
addons-invalid-addon-manifest = Il file manifest dell'add-on non è valido.
addons-invalid-code = Codice non valido.
addons-invalid-code-or-addon-not-available = Codice non valido, o add-on non disponibile per questa versione di Anki.
addons-invalid-configuration = Configurazione non valida:
addons-invalid-configuration-top-level-object-must = Configurazione non valida: l'oggetto di primo livello deve essere una mappa
addons-no-updates-available = Nessun aggiornamento disponibile.
addons-one-or-more-errors-occurred = Si sono verificati uno o più errori:
addons-packaged-anki-addon = Add-on di Anki impachettato
addons-please-check-your-internet-connection = Verificare la propria connessione ad internet.
addons-please-report-this-to-the-respective = Segnalalo al rispettivo autore dell'add-on.
addons-please-restart-anki-to-complete-the = <b>Riavvia Anki per completare l'installazione.</b>
addons-please-select-a-single-addon-first = Seleziona dapprima un singolo add-on.
addons-requires = (richiede { $val })
addons-restored-defaults = Ripristinate le impostazioni predefinite
addons-the-following-addons-are-incompatible-with = I seguenti add-on sono incompatibili con { $name } e sono stati disattivati: { $found }
addons-the-following-addons-have-updates-available = Sono disponibili aggiornamenti per i seguenti add-on. Vuoi installarli ora?
addons-the-following-conflicting-addons-were-disabled = Gli add-on seguenti in conflitto tra di loro sono stati disattivati:
addons-this-addon-is-not-compatible-with = Questo add-on non è compatibile con la versione di Anki in uso.
addons-to-browse-addons-please-click-the = Per sfogliare gli add-on, fare clic sul pulsante "Sfoglia add-on" qui sotto.<br><br>Una volta trovato un add-on di proprio interesse, incollare il suo codice qui sotto. È possibile inserire anche più codici separati da uno spazio.
addons-toggle-enabled = Attiva/Disattiva
addons-unable-to-update-or-delete-addon = Non è stato possibile aggiornare o eliminare l'add-on. Avviare Anki tenendo premuto il tasto Maiusc per disattivare temporaneamente gli add-on, e riprovare. Informazioni per il debug: { $val }
addons-unknown-error = Errore sconosciuto: { $val }
addons-view-addon-page = Visualizza pagina degli add-on
addons-view-files = Mostra i file
addons-delete-the-numd-selected-addon =
    { $count ->
        [one] Eliminare { $count } add-on selezionato?
       *[other] Eliminare i { $count } add-on selezionati?
    }
addons-choose-update-window-title = Aggiorna gli add-on
addons-choose-update-update-all = Aggiorna tutto
