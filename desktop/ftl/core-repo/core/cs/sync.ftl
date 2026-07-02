### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = Přidáno: { $up }↑ { $down }↓
sync-media-removed-count = Odstraněno: { $up }↑ { $down }↓
sync-media-checked-count = Zkontrolováno: { $count }
sync-media-starting = Spouští se synchronizace multimédií...
sync-media-complete = Synchronizace multimédií dokončena.
sync-media-failed = Synchronizace multimédií selhala.
sync-media-aborting = Synchronizace multimédií se přerušuje...
sync-media-aborted = Synchronizace multimédií přerušena.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = Synchronizace multimédií je zakázána.
# Title of the screen that shows syncing progress history
sync-media-log-title = Záznam synchronizace multimédií

## Error messages / dialogs

sync-conflict = V jeden okamžik se může synchronizovat s vaším účtem pouze jedna kopie Anki. Prosím počkejte několik minut, poté to zkuste znovu.
sync-server-error = AnkiWeb narazil na problém. Prosím zkuste to znovu za několik minut.
sync-client-too-old = Vaše verze Anki je příliš stará. Prosím aktualizujte na nejnovější verzi, abyste mohli pokračovat v synchronizaci.
sync-wrong-pass = Přihlašovací jméno nebo heslo byly nesprávné, zkuste to prosím znova.
sync-resync-required = Prosím synchronizujte znova. Pokud se tato zpráva stále objevuje, prosím napište na stránku podpory.
sync-must-wait-for-end = Anki se nyní synchronizuje. Prosím počkejte, dokud se synchronizace nedokončí, poté to zkuste znovu.
sync-confirm-empty-download = Místní kolekce nemá žádné karty. Stáhnout z AnkiWebu?
sync-confirm-empty-upload = AnkiWeb kolekce nemá žádné karty. Nahradit ji místní kolekcí?
sync-conflict-explanation =
    Vaše balíčky zde a na AnkiWeb jsou rozdílné natolik, že nemohou být sloučeny, takže je nutné balíčky jedné strany přepsat balíčky druhé strany.
    
    Jestliže zvolíte stáhnout, Anki stáhne kolekci z AnkiWeb a všechny změny, které jste provedli na tomto zařízení od poslední synchronizace, budou ztraceny.
    
    Pokud zvolíte nahrát, Anki odešle vaši kolekci na AnkiWeb a všechny změny, které jste provedli na AnkiWeb nebo jiných zařízeních od poslední synchronizace daného zařízení, budou ztraceny.
    
    Poté, co všechna zařízení budou synchronizována, mohou být budoucí opakování a přidané karty sloučeny automaticky.
sync-conflict-explanation2 =
    Mezi balíčky na tomto zařízení a na AnkiWebu nastal konflikt. Musíte si vybrat, kterou verzi si ponecháte:
    
    - Zvolte **{ sync-download-from-ankiweb }**, abyste nahradili balíčky na tomto zařízení verzí z AnkiWebu. Ztratíte všechny změny, které jste na tomto zařízení provedli od poslední synchronizace.
    - Zvolte **{ sync-upload-to-ankiweb }**, abyste přepsali verze na AnkiWebu balíčky z tohoto zařízení, a odstranili všechny změny na AnkiWebu.
    
    Po vyřešení konfliktu bude synchronizace fungovat jako obvykle.
sync-ankiweb-id-label = Přihlašovací jméno:
sync-password-label = Heslo:
sync-account-required =
    <h1>Je vyžadován účet</h1>
    Pro synchronizaci vaši kolekce je vyžadován účet (dostupný zdarma). <a href="{ $link }">Zaregistrujte si</a> účet a pak zadejte své údaje níže.
sync-sanity-check-failed = Prosím použijte funkci Zkontrolovat databázi, poté synchronizujte znovu. Jestliže problém přetrvává, prosím vynuťte úplnou synchronizaci na obrazovce předvolby.
sync-clock-off = Nelze synchronizovat - vaše hodiny nemají nastaveny správný čas.
sync-upload-too-large =
    Soubor s vaší kolekcí je příliš velký, než aby se dal poslat na AnkiWeb. Můžete ho 
    zmenšit tak, že odstraníte nechtěné balíčky (nejdříve si je můžete exportovat) a poté 
    použijete Zkontrolovat databázi, což zmenší velikost souboru. ({ $details })
sync-sign-in = Přihlásit se
sync-ankihub-dialog-heading = Přihlášení AnkiHub
sync-ankihub-username-label = Uživatelské jméno nebo email:

## Buttons

sync-media-log-button = Záznam multimédií
sync-abort-button = Přerušit
sync-download-from-ankiweb = Stáhnout z AnkiWebu
sync-upload-to-ankiweb = Nahrát na AnkiWeb
sync-cancel-button = Zrušit

## Normal sync progress

sync-downloading-from-ankiweb = Stahuje se z AnkiWebu...
sync-uploading-to-ankiweb = Nahrává se na AnkiWeb...
sync-syncing = Synchronizuje se...
sync-checking = Kontroluje se...
sync-connecting = Připojování...
sync-added-updated-count = Přidáno/upraveno: { $up }↑ { $down }↓
sync-log-in-button = Přihlásit se
sync-log-out-button = Odhlásit se
sync-collection-complete = Synchronizace kolekce dokončena.
