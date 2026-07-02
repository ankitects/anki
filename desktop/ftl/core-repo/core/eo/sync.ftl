### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = Aldonis: { $up }↑ { $down }↓
sync-media-removed-count = Forigis: { $up }↑ { $down }↓
sync-media-checked-count = Kontrolis: { $count }
sync-media-starting = Ekigado de samtempigado de aŭdvidaĵoj…
sync-media-complete = Sukcesis samtempigi aŭdvidaĵojn.
sync-media-failed = Malsukcesis samtempigi aŭdvidaĵojn.
sync-media-aborting = Ĉesigado de samtempigado de aŭdvidaĵoj…
sync-media-aborted = Ĉesigis samtempigi aŭdvidaĵojn.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = Samtempigo de aŭdvidaĵoj malaktiva.
# Title of the screen that shows syncing progress history
sync-media-log-title = Protokolo pri samtempigataj dosieroj

## Error messages / dialogs

sync-conflict = Nur unu kopio de Anki povas samtempe samtempigi dosierojn kun via konto. Atendu kelkajn minutojn kaj reprovu.
sync-server-error = Okazis problemo kun AnkiWeb. Reprovu post kelkaj minutoj.
sync-client-too-old = Vi uzas tro malnovan version de Anki. Ĝisdatigu la programon al la plej nova versio por pluigi samtempigon.
sync-wrong-pass = Identigilo aŭ pasvorto al AnkiWeb estas malĝusta; reprovu.
sync-resync-required = Reprovu ekigi samtempigon. Se tiu ĉi sciigo pluos montriĝi, raportu eraron sur nia subtena retpaĝo.
sync-must-wait-for-end = Daŭras samtempigo. Atendu ĝis la samtempigo finiĝos kaj reprovu.
sync-confirm-empty-download = La loka kolekto havas neniujn kartojn. Ĉu vi volas elŝuti kartojn de AnkiWeb?
sync-confirm-empty-upload = Kolekto AnkiWeb enhavas neniun karton. Ĉu anstataŭigi ĝin per loka kolekto?
sync-conflict-explanation =
    La kartaroj ĉi tie kaj sur AnkiWeb diferencas tiel, ke ili estas nekunfandeblaj. Tial necesas superskribi la kartarojn sur unu flanko per la kartaroj de la alia.
    
    Se vi elektos elŝuton, Anki elŝutos la kolekton de AnkiWeb kaj vi perdos ĉiujn ŝanĝojn sur ĉi tiu komputilo, kiun vi faris ekde la lasta samtempigo.
    
    Se vi elektos alŝuton, Anki alŝutos vian kolekton al AnkiWeb kaj vi perdos ĉiujn ŝanĝojn sur AnkiWeb aŭ aliaj aranĝaĵoj ekde ties lasta samtempigo.
    
    Post kiam ĉiuj aranĝaĵoj estos samtempigitaj, estontaj ripetoj kaj aldonataj kartoj povos esti aŭtomate kunfanditaj.
sync-conflict-explanation2 =
    Okazis konflikto inter kartaroj en tiu ĉi aparato kaj en AnkiWeb. Elektu kiun version vi volas teni:
    
    – Elektu “**{ sync-download-from-ankiweb }**” por anstataŭigi lokajn kartarojn per kartaroj el AnkiWeb. Vi perdos ĉiujn ŝanĝojn faritajn en tiu ĉi aparato ekde via antaŭa samtempigo.
    – Elektu “**{ sync-upload-to-ankiweb }**” por anstataŭigi kartarojn en AnkiWeb per kartaroj de tiu ĉi aparato kaj perdi ĉiujn ŝanĝojn en AnkiWeb.
    
    Kiam konflikto estas solvita, samtempigo funkcios laŭkutime.
sync-ankiweb-id-label = AnkiWeb-identigilo:
sync-password-label = Pasvorto:
sync-account-required =
    <h1>Konto necesas</h1>
    Senkosta konto necesas por teni vian kolekton samtempigita. <a href="{ $link }">Registriĝu</a> por akiri konton kaj sekve entajpu viajn ensalutilojn malsupre.
sync-sanity-check-failed = Uzu la eblon “Kontroli datumbazon” kaj reprovu samtempigon. Se la problemo plue okazos, devigu unudirektan samtempigon en agordoj.
sync-clock-off = Ne eblas samtempigi – horloĝo de via operaciumo estas misagordita.
sync-upload-too-large = Via kolekto estas tro granda por sendi al AnkiWeb. Vi povas etigi ĝian grandon per forigi nedeziratajn kartarojn (malnepre antaŭe elporti ilin) kaj uzi la eblon “Kontroli datumbazon” por malpliigi grandon de la dosiero. ({ $details })
sync-sign-in = Ensaluti
sync-ankihub-dialog-heading = Ensaluti al AnkiHub
sync-ankihub-username-label = Uzantnomo aŭ retpoŝto:
sync-ankihub-login-failed = Ne eblas ensaluti al AnkiHub uzante la liveritajn ensalutilojn.
sync-ankihub-addon-installation = Instali aldonaĵojn AnkiHub

## Buttons

sync-media-log-button = Protokolo pri dosieroj
sync-abort-button = Ĉesigi
sync-download-from-ankiweb = Elŝuti el AnkiWeb
sync-upload-to-ankiweb = Alŝuti al AnkiWeb
sync-cancel-button = Nuligi

## Normal sync progress

sync-downloading-from-ankiweb = Elŝutado el AnkiWeb…
sync-uploading-to-ankiweb = Alŝutado al AnkiWeb…
sync-syncing = Samtempigado…
sync-checking = Kontrolado…
sync-connecting = Konektado…
sync-added-updated-count = Aldonis/modifis: { $up }↑ { $down }↓
sync-log-in-button = Ensaluti
sync-log-out-button = Elsaluti
sync-collection-complete = Samtempigo de kolekto finita.
