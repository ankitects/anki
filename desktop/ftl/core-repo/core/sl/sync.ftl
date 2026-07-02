### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = Dodano: { $up }↑ { $down }↓
sync-media-removed-count = Odstranjeno: { $up }↑ { $down }↓
sync-media-checked-count = Preverjeno: { $count }
sync-media-starting = Začetek sinhronizacije medijskih datotek...
sync-media-complete = Dokončano sinhroniziranje medijskih datotek.
sync-media-failed = Sinhroniziranje medijskih datotek je spodletelo.
sync-media-aborting = Preklic sinhronizacije medijskih datotek...
sync-media-aborted = Sinhronizacija medijskih datotek je bila preklicana.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = Sinhronizacija medijskih datotek je bila onemogočena.
# Title of the screen that shows syncing progress history
sync-media-log-title = Zapisnik sinhronizacije medijskih datotek

## Error messages / dialogs

sync-conflict = Samo ena izvedba Ankija se lahko sinhronizira z vašim računom naenkrat. Prosimo, počakajte nekaj minut, nato poskusite znova.
sync-server-error = AnkiWeb je naletel na težavo. Prosimo, poskusite ponovno čez nekaj minut.
sync-client-too-old = Vaša različica Ankija je prestara. Za nadaljevanje sinhroniziranja prosimo, da posodobite program.
sync-wrong-pass = AnkiWeb uporabniško ime ali geslo je nepravilno; prosimo, poskusite znova.
sync-resync-required = Prosimo, ponovno sinhronizirajte. Če se to sporočilo ponavljajoče pojavlja, nas prosimo obvestite preko strani za podporo.
sync-must-wait-for-end = Anki trenutno sinhronizira. Prosimo, počakajte na zaključek sinhronizacije, nato poskusite ponovno.
sync-confirm-empty-download = Lokalna kolekcija ne vsebuje zbirk oz. kartic. Naložim z AnkiWeb-a?
sync-conflict-explanation =
    Vaše lokalne zbirke in zbirke na AnkiWebu se tako razlikujejo, da jih ni mogoče združiti. Zato je potrebno eno kolekcijo prepisati z drugo.
    
    Če izberete 'prenesi', bo Anki prenesel kolekcijo z AnkiWeba, vse spremembe, ki ste jih na tej napravi naredili od zadnje sinhronizacije, pa bodo izgubljene.
    
    Če izberete 'naloži', bo Anki poslal podatke s te naprave na AnkiWeb, vse čakajoče spremembe na AnkiWebu pa bodo izgubljene.
    
    Ko bodo vse naprave sinhronizirane, bodo prihodnji pregledi in dodane kartice samodejno združene.
sync-ankiweb-id-label = AnkiWeb uporabniško ime:
sync-password-label = Geslo:
sync-account-required =
    <h1>Zahtevan je račun</h1>
    Če želite vašo zbirko sinhronizirati, potrebujete brezplačen račun. <a href="{ $link }">Prijavite se</a> za brezplačen račun, nato pa spodaj vnesite podatke.
sync-sanity-check-failed = Prosimo, uporabite funkcijo 'Preglej podatkovno bazo', nato pa ponovno sinhronizirajte. Če težava ni odpravljena, vsilite popolno sinhronizacijo v pogovornem oknu za nastavitve.
sync-clock-off = Ni možno sinhronizirati - vaša ura ni nastavljena na pravi čas.
sync-upload-too-large =
    Vaša kolekcija presega dovoljeno velikost za pošiljanje na AnkiWeb.
    Velikost lahko zmanjšate z odstranitvijo neuporabljenih zbirk (pred tem jih izvozite),
    nato pa uporabite možnost 'Preveri podatkovno zbirko', da zmanjšate njeno velikost.
    ({ $details })

## Buttons

sync-media-log-button = Dnevnik medijskih datotek
sync-abort-button = Prekliči
sync-download-from-ankiweb = Naloži z AnkiWeb-a
sync-upload-to-ankiweb = Prenos na AnkiWeb
sync-cancel-button = Prekliči

## Normal sync progress

sync-downloading-from-ankiweb = Nalaganje z AnkiWeb-a
sync-uploading-to-ankiweb = Prenos v AnkiWeb...
sync-syncing = Sinhroniziranje...
sync-checking = Preverjanje...
sync-connecting = Povezovanje ...
sync-added-updated-count = Dodano/spremenjeno: { $up }↑ { $down }↓
sync-log-out-button = Izpiši se
