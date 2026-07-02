addons-possibly-involved = Esetlegesen közrejátszó bővítmények: { $addons }
addons-failed-to-load =
    Nem sikerült betölteni egy telepített kiegészítőt. Ha a probléma továbbra is fennáll, lépjen az Eszközök - Kiegészítők menübe, és tiltsa le vagy törölje a kiegészítőt.
    
    '{ $name }' betöltésekor:
    { $traceback }
addons-failed-to-load2 =
    A következő kiegészítők betöltése nem sikerült:
    { $addons }
    
    Lehet, hogy frissíteni kell őket, hogy támogassák az Anki ezen verzióját. Kattintson a { addons-check-for-updates } gombra.
    hogy megnézze, elérhetőek-e frissítések.
    
    Az { about-copy-debug-info } gomb segítségével olyan információkat kaphat, amelyeket beilleszthet a kiegészítő 
    szerzőjének küldött jelentésbe.
    
    Az olyan bővítmények esetében, amelyekhez nem áll rendelkezésre frissítés, letilthatja vagy törölheti a 
    bővítményt, hogy ez az üzenet ne jelenjen meg.
addons-startup-failed = A bővítmény indítása sikertelen
# Shown in the add-on configuration screen (Tools>Add-ons>Config), in the title bar
addons-config-window-title = '{ $name }' konfigurálása
addons-config-validation-error = Probléma merült fel a megadott konfigurációval: { $problem }, { $path } helyen, a { $schema } sémával szemben.
addons-window-title = Bővítmények
addons-addon-has-no-configuration = A bővítmény nem rendelkezik konfigurációval.
addons-addon-installation-error = Kiegészítő-telepítési hiba
addons-browse-addons = Bővítmények keresése
addons-changes-will-take-effect-when-anki = A módosítások akkor lépnek érvénybe, amikor az Anki újraindult.
addons-check-for-updates = Frissítések keresése
addons-checking = Ellenőrzés...
addons-code = Kód:
addons-config = Beállítás
addons-configuration = Konfigurálás
addons-corrupt-addon-file = Sérült kiegészítő fájl.
addons-disabled = (kikapcsolva)
addons-disabled2 = (letiltva)
addons-download-complete-please-restart-anki-to = A letöltés kész. Kérjük, indítsa újra az Ankit a módosítások érvénybe léptetéséhez.
addons-downloaded-fnames = Letöltve { $fname }
addons-downloading-adbd-kb02fkb = Letöltve { $part }/{ $total } ({ $kilobytes }KB)...
addons-error-downloading-ids-errors = Hiba történt a letöltéskor <i>{ $id }</i>: { $error }
addons-error-installing-bases-errors = Hiba történt a telepítéskor <i>{ $base }</i>: { $error }
addons-get-addons = Bővítmények beszerzése...
addons-important-as-addons-are-programs-downloaded = <b>Fontos </b>: Mivel a kiegészítők az internetről letöltött programok, potenciálisan rosszindulatúak is lehetnek.<b> Csak a megbízható kiegészítőket telepítse.</b><br><br>Biztosan folytatni akarja az alábbi Anki-kiegészítő(k) telepítését?<br><br> %(names)s
addons-install-addon = Kiegészítő telepítése
addons-install-addons = Kiegészítő telepítése
addons-install-anki-addon = Kiegészítő telepítése
addons-install-from-file = Telepítés fájlból ...
addons-installation-complete = Telepítés befejezve
addons-installed-names = Telepítve { $name }
addons-installed-successfully = Telepítés sikeres.
addons-invalid-addon-manifest = Érvénytelen kiegészítő.
addons-invalid-code = Érvénytelen kód.
addons-invalid-code-or-addon-not-available = Érvénytelen kód vagy a kiegészítő nem érhető el az Anki verziójában.
addons-invalid-configuration = Érvénytelen konfiguráció:
addons-invalid-configuration-top-level-object-must = Érvénytelen konfiguráció: a felső szintű objektumnak térképnek kell lennie
addons-no-updates-available = Nincs elérhető frissítés.
addons-one-or-more-errors-occurred = Egy vagy több hiba történt:
addons-packaged-anki-addon = Tömörített Anki-kiterjesztés
addons-please-check-your-internet-connection = Ellenőrizze internetkapcsolatát.
addons-please-report-this-to-the-respective = Kérjük, jelentse ezt a megfelelő kiegészítő szerzőnek.
addons-please-restart-anki-to-complete-the = <b>Kérjük, a telepítés befejezéséhez indítsa újra az Ankit.</b>
addons-please-select-a-single-addon-first = Először válasszon ki egy kiegészítőt.
addons-requires = ({ $val } szükséges hozzá)
addons-restored-defaults = Visszaállított alapértékek
addons-the-following-addons-are-incompatible-with = A következő kiegészítők nem kompatibilisek a { $name } névvel és le vannak tiltva: { $found }
addons-the-following-addons-have-updates-available = Az alábbi kiegészítőkhöz frissítések érhetők el. Telepíti most ezeket?
addons-the-following-conflicting-addons-were-disabled = Az alábbi kiegészítőket ütközés miatt letiltottuk:
addons-this-addon-is-not-compatible-with = Ez a kiegészítő nem kompatibilis az Anki verziójával.
addons-to-browse-addons-please-click-the = Bővítmények kereséséhez kattintson a lenti keresés gombra.<br><br>Ha megtalálta a kívánt bővítményt, kérjük, illessze be annak kódját alulra. Több kódot is beilleszthet, szóközökkel elválasztva.
addons-toggle-enabled = Engedélyezés átváltása
addons-unable-to-update-or-delete-addon = Nem sikerült frissíteni vagy törölni a kiegészítőt. Kérjük, indítsa el az Anki-t, miközben lenyomva tartja a Shift billentyűt a kiegészítők ideiglenes letiltásához, majd próbálja újra.  Hibakeresési információ: { $val }
addons-unknown-error = Ismeretlen hiba: { $val }
addons-view-addon-page = Kiegészítő oldal megtekintése
addons-view-files = Fájlok megtekintése
addons-delete-the-numd-selected-addon =
    { $count ->
        [one] Törli a { $count } kiválasztott kiegészítőt?
       *[other] Törli a { $count } kiválasztott kiegészítőket?
    }
addons-choose-update-window-title = Bővítmények frissítése
addons-choose-update-update-all = Összes frissítése
