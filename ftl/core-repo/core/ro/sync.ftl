### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = Adăugat(e): { $up }↑ { $down }↓
sync-media-removed-count = Eliminat(e): { $up }↑ { $down }↓
sync-media-checked-count = Verificat(e): { $count }
sync-media-starting = Începe sincronizarea media...
sync-media-complete = Sincronizarea media s-a încheiat.
sync-media-failed = Sincronizarea media nu a reușit.
sync-media-aborting = Se anulează sincronizarea media...
sync-media-aborted = Sincronizarea media a fost întreruptă.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = Sincronizarea media este dezactivată.
# Title of the screen that shows syncing progress history
sync-media-log-title = Jurnal de sincronizare media

## Error messages / dialogs

sync-conflict = Doar o singură copie a Anki se poate sincroniza cu contul tău în același timp. Te rog să aștepți câteva minute, apoi să încerci din nou.
sync-server-error = AnkiWeb a întâmpinat o problemă. Te rog să încerci din nou în câteva minute.
sync-client-too-old = Versiunea ta Anki este prea veche. Actualizează la cea mai recentă versiune pentru a continua sincronizarea.
sync-wrong-pass = ID-ul AnkiWeb sau parola au fost incorecte, te rog să încerci din nou.
sync-resync-required = Te rog să sincronizezi din nou. Dacă acest mesaj continuă să apară, te rog să apelezi la site-ul de asistență.
sync-must-wait-for-end = Anki se sincronizează în prezent. Așteaptă finalizarea sincronizării, apoi încearcă din nou.
sync-confirm-empty-download = Colecția locală nu are carduri. Descarci de pe AnkiWeb?
sync-conflict-explanation =
    Pachetele tale de aici și de pe AnkiWeb diferă în așa fel încât nu pot fi îmbinate împreună, așa că este necesar să suprascrii pachetele de pe o parte cu pachetele de pe cealaltă.
    
    Dacă alegi descărcare, Anki va prelua colecția de la AnkiWeb și orice modificări pe care le-ai făcut pe acest dispozitiv de la ultima sincronizare se vor pierde.
    
    Dacă alegeți să încarci, Anki va trimite datele acestui dispozitiv către AnkiWeb și orice modificări care sunt în așteptare pe AnkiWeb se vor pierde.
    
    După ce toate dispozitivele sunt sincronizate, recenziile viitoare și cardurile adăugate pot fi îmbinate automat.
sync-ankiweb-id-label = ID-ul AnkiWeb
sync-password-label = Parolă:
sync-account-required =
    <h1>Cont obligatoriu</h1>
    Un cont gratuit este necesar pentru a păstra sincronizată colecția. Te rog, <a href="{ $link }">înscrie-te</a> pentru un cont, apoi introdu detaliile tale.
sync-sanity-check-failed = Te rog să utilizezi funcția ”Verifică baza de date”, apoi sincronizează din nou. Dacă problemele persistă, te rog să forțezi o sincronizare completă în ecranul de preferințe.
sync-clock-off = Nu se poate sincroniza - ceasul nu este setat la ora corectă.
sync-upload-too-large =
    Fișierul tău de colecție este prea mare pentru a fi trimis către AnkiWeb. Îi poți reduce
    dimensiunea prin eliminarea oricăror pachete nedorite (opțional, exportându-le mai întâi) și
    apoi folosește ”Verifică baza de date” pentru a micșora dimensiunea fișierului. ({ $detalii })

## Buttons

sync-media-log-button = Jurnal Media
sync-abort-button = Renunță
sync-download-from-ankiweb = Descarcă de la AnkiWeb
sync-upload-to-ankiweb = Încarcă pe AnkiWeb
sync-cancel-button = Anulare

## Normal sync progress

sync-downloading-from-ankiweb = Descărcare de la AnkiWeb...
sync-uploading-to-ankiweb = Se încarcă pe AnkiWeb...
sync-syncing = Sincronizare...
sync-checking = Se verifică…
sync-connecting = Se conectează...
sync-added-updated-count = Adăugate/modificate: { $up }↑ { $down }↓
sync-log-out-button = Log Out
