### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = Hozzáadva: { $up }↑ { $down }↓
sync-media-removed-count = Törölve: { $up }↑ { $down }↓
sync-media-checked-count = Ellenőrizve: { $count }
sync-media-starting = Médiaszinkronizálás indítása...
sync-media-complete = Médiaszinkronizálás kész.
sync-media-failed = Médiaszinkronizálás sikertelen.
sync-media-aborting = Médiaszinkronizálás megszakítása ...
sync-media-aborted = Médiaszinkronizálás megszakítva.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = Médiaszinkronizálás letiltva.
# Title of the screen that shows syncing progress history
sync-media-log-title = Médiaszinkronizálási napló

## Error messages / dialogs

sync-conflict = Az Anki csak egy példánya szinkronizálható egyszerre egy fiókkal. Próbálkozz újra néhány perc múlva!
sync-server-error = Az AnkiWeb problémába ütközött. Próbálkozz újra néhány perc múlva!
sync-client-too-old = Az Anki verziója túl régi. Frissíts a legújabb verzióra a szinkronizálás folytatásához!
sync-wrong-pass = Az emailcím vagy jelszó nem megfelelő. Próbálkozz újra!
sync-resync-required = Szinkronizálj újra! Ha ez az üzenet továbbra is megjelenik, kérj segítséget az anki webes felületén.
sync-must-wait-for-end = Szinkronizálás folyamatban. Várd meg, amíg befejeződik, majd próbálkozz újra!
sync-confirm-empty-download = A helyi gyűjteményben nincs kártya. Letöltés az AnkiWebről?
sync-confirm-empty-upload = Az AnkiWeb gyűjteményében nincs kártya. Helyettesítsük helyi gyűjteménnyel?
sync-conflict-explanation =
    Az itteni és az AnkiWeben tárolt kártyacsomagjaid oly mértékben eltérnek, hogy nem lehet egyesíteni őket, így az egyik helyen lévő csomagokat felül kell írni a másikon lévőkkel.
    
    Ha a letöltést választod, az Anki letölti a gyűjteményt az AnkiWebről, és minden olyan változtatás el fog veszni, amit a számítógépeden végeztél a legutóbbi szinkronizálás óta.
    
    Ha a feltöltést választod, az Anki feltölti a gyűjteményedet az AnkiWebre, és minden olyan változtatás el fog veszni, amit az AnkiWeben vagy más eszközödön végeztél az ezzel a géppel való legutóbbi szinkronizálás óta.
    
    Miután minden eszköz szinkronba került, a későbbi ismétlések és hozzáadott kártyák automatikusan be lesznek illesztve.
sync-conflict-explanation2 =
    Az eszközön lévő paklik és az AnkiWeb között konfliktus van. Választanod kell, hogy melyik verziót szeretnéd megtartani:
    
    - A **{ sync-download-from-ankiweb }** lehetőség az itteni paklikat az AnkiWeb verziójára cseréli. Az utolsó szinkronizálás óta ezen az eszközön végzett módosítások elvesznek.
    
    - A **{ sync-upload-to-ankiweb }** lehetőség az AnkiWeb verzióit felülírja az itteni paklikkal, és törli az AnkiWeben végzett módosításokat.
    
    Ha a konfliktus feloldódott, a szinkronizálás a szokásos módon fog működni.
sync-ankiweb-id-label = Email:
sync-password-label = Jelszó:
sync-account-required =
    <h1>Felhasználói fiók szükséges</h1>
    A gyűjteményed szinkronizálásához ingyenes felhasználói fiókra van szükség. <a href="{ $link }">Hozz létre</a> magadnak egyet, majd add meg az adatait.
sync-sanity-check-failed = Használd az Adatbázis ellenőrzése funkciót, majd szinkronizálj újra! Ha a problémák továbbra is fennállnak, jelöld be az egyirányú szinkronizálást a Beállítások képernyőn!
sync-clock-off = Nem lehetséges a szinkronizálás — az órád nem megfelelő időre van állítva.
# “details” expands to a string such as “300.14 MB > 300.00 MB”
sync-upload-too-large =
    A gyűjteményed túl nagy ahhoz, hogy feltöltsd az AnkiWebre. Csökkentheted a méretét a nem kívánt paklik eltávolításával (opcionálisan előbb exportálhatod őket), 
    majd az Adatbázis ellenőrzése használatával csökkentheted a fájl méretét.
    
    { $details } (tömörítés előtt)
sync-sign-in = Bejelentkezés
sync-ankihub-dialog-heading = AnkiHub bejelentkezés
sync-ankihub-username-label = Felhasználónév vagy emailcím:
sync-ankihub-login-failed = Nem sikerült bejelentkezni az AnkuHubra a megadott adatokkal.
sync-ankihub-addon-installation = AnkiHub bővítmény telepítése

## Buttons

sync-media-log-button = Médianapló
sync-abort-button = Megszakítás
sync-download-from-ankiweb = Letöltés az AnkiWebről
sync-upload-to-ankiweb = Feltöltés az AnkiWebre
sync-cancel-button = Mégsem

## Normal sync progress

sync-downloading-from-ankiweb = Letöltés az AnkiWebről...
sync-uploading-to-ankiweb = Feltöltés az AnkiWebre...
sync-syncing = Szinkronizálás...
sync-checking = Ellenőrzés...
sync-connecting = Kapcsolódás...
sync-added-updated-count = Hozzáadva/módosítva: { $up }↑ { $down }↓
sync-log-in-button = Bejelentkezés
sync-log-out-button = Kijelentkezés
sync-collection-complete = Gyűjtemény szinkronizálása befejeződött.
