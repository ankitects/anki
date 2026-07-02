addons-possibly-involved = Mogući uključeni dodaci: { $addons }
addons-failed-to-load =
    Dodatak koji ste instalirali nije se uspio učitati. Ako problemi potraju, idite na izbornik Alati>Dodaci i onemogućite ili izbrišite dodatak.
    
    Prilikom učitavanja '{ $name }':
    { $traceback }
addons-failed-to-load2 =
    Sljedeći dodaci nisu se uspjeli učitati:
    { $addons }
    
    Možda ih je potrebno ažurirati kako bi podržavali ovu verziju Ankija. Kliknite gumb { addons-check-for-updates }
    kako biste vidjeli jesu li dostupna ažuriranja.
    
    Možete koristiti gumb { about-copy-debug-info } kako biste dobili informacije koje možete zalijepiti u izvješće autoru dodatka.
    
    Za dodatke koji nemaju dostupno ažuriranje, možete onemogućiti ili izbrisati dodatak kako biste spriječili pojavljivanje ove poruke.
addons-startup-failed = Pokretanje dodatka nije uspjelo
# Shown in the add-on configuration screen (Tools>Add-ons>Config), in the title bar
addons-config-window-title = Konfiguriraj '{ $name }'
addons-config-validation-error = Došlo je do problema s navedenom konfiguracijom: { $problem }, na putu { $path }, u odnosu na shemu { $schema }.
addons-window-title = Dodaci
addons-addon-has-no-configuration = Dodatak nema konfiguraciju.
addons-addon-installation-error = Greška pri instalaciji dodatka
addons-browse-addons = Pregledaj dodatke
addons-changes-will-take-effect-when-anki = Promjene će stupiti na snagu kada se Anki ponovno pokrene.
addons-check-for-updates = Provjeri ima li ažuriranja
addons-checking = Provjera u tijeku...
addons-code = Kôd:
addons-config = Konfiguracija
addons-configuration = Konfiguracija
addons-corrupt-addon-file = Oštećena datoteka dodatka.
addons-disabled = (onemogućeno)
addons-disabled2 = (onemogućeno)
addons-download-complete-please-restart-anki-to = Preuzimanje je dovršeno. Ponovno pokrenite Anki kako biste primijenili promjene.
addons-downloaded-fnames = Preuzeto { $fname }
addons-downloading-adbd-kb02fkb = Preuzimanje { $part }/{ $total } ({ $kilobytes }KB)...
addons-error-downloading-ids-errors = Greška pri preuzimanju <i>{ $id } </i>: { $error }
addons-error-installing-bases-errors = Greška pri instaliranju <i>{ $base }</i>: { $error }
addons-get-addons = Nabavite dodatke...
addons-important-as-addons-are-programs-downloaded = <b>Važno</b>: Budući da su dodaci programi preuzeti s interneta, potencijalno su zlonamjerni.<b>Trebali biste instalirati samo dodatke kojima vjerujete.</b><br><br>Jeste li sigurni da želite nastaviti s instalacijom sljedećih Anki dodataka?<br><br>%(names)s
addons-install-addon = Instaliraj dodatak
addons-install-addons = Instaliraj dodatke
addons-install-anki-addon = Instaliraj Anki dodatak
addons-install-from-file = Instaliraj iz datoteke...
addons-installation-complete = Instalacija završena
addons-installed-names = { $name } je instalirano
addons-installed-successfully = Uspješno instalirano.
addons-invalid-addon-manifest = Nevažeći manifest dodatka.
addons-invalid-code = Neispravan kôd.
addons-invalid-code-or-addon-not-available = Nevažeći kôd ili dodatak nije dostupan za Vašu verziju Ankija.
addons-invalid-configuration = Nevažeća konfiguracija:
addons-invalid-configuration-top-level-object-must = Nevažeća konfiguracija: objekt najviše razine mora biti mapa
addons-no-updates-available = Nema dostupnih ažuriranja.
addons-one-or-more-errors-occurred = Došlo je do jedne ili više grešaka:
addons-packaged-anki-addon = Pakirani Anki dodatak
addons-please-check-your-internet-connection = Molimo provjerite svoju internetsku vezu.
addons-please-report-this-to-the-respective = Molimo Vas da ovo prijavite autoru(ima) odgovarajućeg dodatka.
addons-please-restart-anki-to-complete-the = <b>Ponovno pokrenite Anki kako biste dovršili instalaciju.</b>
addons-please-select-a-single-addon-first = Prvo odaberite jedan dodatak.
addons-requires = (zahtijeva { $val })
addons-restored-defaults = Vraćene zadane postavke
addons-the-following-addons-are-incompatible-with = Sljedeći dodaci nisu kompatibilni s { $name } i onemogućeni su: { $found }
addons-the-following-addons-have-updates-available = Sljedeći dodaci imaju dostupna ažuriranja. Želite li ih sada instalirati?
addons-the-following-conflicting-addons-were-disabled = Sljedeći konfliktni dodaci su onemogućeni:
addons-this-addon-is-not-compatible-with = Ovaj dodatak nije kompatibilan s vašom verzijom Ankija.
addons-to-browse-addons-please-click-the = Da biste pregledavali dodatke, kliknite donji gumb Pregledaj.<br> <br>Kada pronađete dodatak koji Vam se sviđa, zalijepite njegov kod niže u nastavku. Možete zalijepiti više kodova odvojenih razmacima.
addons-toggle-enabled = Uključi/isključi
addons-unable-to-update-or-delete-addon = Nije moguće ažurirati ili izbrisati dodatak. Pokrenite Anki dok držite pritisnutu tipku Shift kako biste privremeno onemogućili dodatke, a zatim pokušajte ponovno. Informacije o otklanjanju grešaka: { $val }
addons-unknown-error = Nepoznata greška: { $val }
addons-view-addon-page = Prikaži stranicu dodatka
addons-view-files = Pogledaj datoteke
addons-delete-the-numd-selected-addon =
    { $count ->
        [one] Izbrisati { $count } odabrani dodatak?
        [few] Izbrisati { $count } odabrana dodatka?
       *[other] Izbrisati { $count } odabranih dodataka?
    }
addons-choose-update-window-title = Ažuriraj dodatke
addons-choose-update-update-all = Ažuriraj sve
