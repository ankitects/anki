### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = Dodano: { $up }↑ { $down }↓
sync-media-removed-count = Uklonjeno: { $up }↑ { $down }↓
sync-media-checked-count = Provjereno: { $count }
sync-media-starting = Početak sinkronizacije medijskih datoteka...
sync-media-complete = Sinkronizacija medijskih datoteka završena.
sync-media-failed = Sinkronizacija medijskih datoteka nije uspjela.
sync-media-aborting = Prekidanje sinkronizacije medijskih datoteka...
sync-media-aborted = Sinkronizacija medijskih datoteka prekinuta.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = Sinkronizacija medijskih datoteka onemogućena.
# Title of the screen that shows syncing progress history
sync-media-log-title = Zapisnik sinkronizacije medijskih datoteka

## Error messages / dialogs

sync-conflict = Istodobno se samo jedna kopija Ankija može sinkronizirati s vašim računom. Pričekajte par minuta i pokušajte ponovno.
sync-server-error = AnkiWeb je naišao na problem. Pokušajte ponovno za par minuta.
sync-client-too-old = Vaša verzija Ankija je prestara. Ažurirajte program na najnoviju verziju kako biste nastavili sinkronizaciju.
sync-wrong-pass = AnkiWeb ID ili lozinka su bili pogrešni; pokušajte ponovno.
sync-resync-required = Sinkronizirajte ponovno. Ako se ova poruka nastavi pojavljivati, javite se na stranicu za podršku s vašim problemom.
sync-must-wait-for-end = Anki se trenutno sinkronizira. Pričekajte da sinkronizacija završi, zatim pokušajte ponovno.
sync-confirm-empty-download = Lokalna kolekcija nema kartica. Želite li je preuzeti s AnkiWeba?
sync-confirm-empty-upload = AnkiWeb kolekcija nema kartica. Želite li je zamijeniti s lokalnom kolekcijom?
sync-conflict-explanation =
    Špilovi na ovom uređaju i špilovi na AnkiWebu se razlikuju na način da ih se ne može sjediniti, što znači da je potrebno pregaziti špilove na jednoj strani onima s druge strane.
    
    Ako odaberete preuzimanje, Anki će dohvatiti kolekciju s AnkiWeba, što znači da će biti izgubljene bilo kakve promjene koje ste na ovom uređaju napravili nakon zadnje sinkronizacije.
    
    Ako odaberete prijenos, Anki će podatke s ovog uređaja prenijeti na AnkiWeb, što znači da će bilo kakve promjene koje čekaju na AnkiWebu biti izgubljene.
    
    Kad svi uređaji budu sinkronizirani, buduća ponavljanja i dodane kartice moći će se automatski sjediniti.
sync-conflict-explanation2 =
    Špilovi na ovom uređaju i na AnkiWebu se ne podudaraju. Odaberite koji ćete špil zadržati:
    
    - Odaberite **{ sync-download-from-ankiweb }** kako biste zamijenili lokalne špilove s verzijama s AnkiWeba. Izgubit ćete sve promjene koje su napravljene na ovom uređaju nakon zadnje sinkronizacije.
    - Odaberite **{ sync-upload-to-ankiweb }** kako biste zamijenili špilove na AnkiWebu s verzijama špilova s ovog uređaja i kako biste izbrisali bilo kakve promjene na AnkiWebu.
    
    Nakon što se nepodudaranje razriješi, sinkronizacija će funkcionirati na uobičajen način.
sync-ankiweb-id-label = E-mail:
sync-password-label = Lozinka:
sync-account-required =
    <h1>Potreban je račun</h1>
    Za sinkronizaciju vaše kolekcije treba vam besplatan račun.  <a href="{ $link }">Registrirajte</a> račun, a zatim dolje unesite svoje podatke.
sync-sanity-check-failed = Pokrenite funkciju "Provjeri bazu podataka" te zatim sinkronizirajte ponovno. Ako se problem nastavi, iz ekrana postavki prisilno sprovedite sinkronizaciju u jednom smjeru.
sync-clock-off = Nije moguće sinkronizirati - vaš sat nije podešen na točno vrijeme.
# “details” expands to a string such as “300.14 MB > 300.00 MB”
sync-upload-too-large =
    Vaša kolekcija je prevelika da bi se poslala na AnkiWeb. Možete je smanjiti tako što uklonite neželjene špilove (koje prije toga možete izvesti) i zatim upotrijebite funkciju "Provjeri bazu podataka" kako biste smanjili veličinu datoteke.
    
    { $details } (bez sažimanja)
sync-sign-in = Prijava
sync-ankihub-dialog-heading = AnkiHub prijava
sync-ankihub-username-label = Korisničko ime ili e-mail:
sync-ankihub-login-failed = Nije se moguće prijaviti u AnkiHub pomoću upisanih vjerodajnica.
sync-ankihub-addon-installation = Instalacija dodatka AnkiHub

## Buttons

sync-media-log-button = Zapisnik medijskih datoteka
sync-abort-button = Prekini
sync-download-from-ankiweb = Preuzmi sa AnkiWeb-a
sync-upload-to-ankiweb = Pošalji na AnkiWeb
sync-cancel-button = Otkaži

## Normal sync progress

sync-downloading-from-ankiweb = Preuzimanje s AnkiWeb-a...
sync-uploading-to-ankiweb = Slanje na AnkiWeb...
sync-syncing = Sinkronizacija...
sync-checking = Provjera u tijeku...
sync-connecting = Povezivanje…
sync-added-updated-count = Dodano/izmijenjeno: { $up }↑ { $down }↓
sync-log-in-button = Prijavi se
sync-log-out-button = Odjavi se
sync-collection-complete = Dovršena sinkronizacija kolekcije.
