addons-possibly-involved = Doplňky, kterých se to může týkat: { $addons }
addons-failed-to-load =
    Doplněk, který jste nainstalovali, nelze načíst. Jestliže problém přetrvává, prosím jděte do nabídky Nástroje>Doplňky a zakažte nebo odstraňte daný doplněk.
    
    Při načítání „{ $name }“:
    { $traceback }
addons-failed-to-load2 =
    Následující doplňky se nepodařilo načíst:
    { $addons }
    
    Možná bude potřeba je aktualizovat, aby podporovaly tuto verzi Anki. Klikněte na tlačítko { addons-check-for-updates } a zjistěte, zda jsou k dispozici nějaké aktualizace.
    
    Pomocí tlačítka { about-copy-debug-info } můžete získat informace, které můžete vložit do 
    hlášení pro autora doplňku.
    
    U doplňků, které nemají k dispozici aktualizaci, můžete doplněk zakázat nebo odstranit, aby se 
    tato zpráva již nezobrazovala.
addons-startup-failed = Spouštění doplňku selhalo
# Shown in the add-on configuration screen (Tools>Add-ons>Config), in the title bar
addons-config-window-title = Nastavení „{ $name }“
addons-config-validation-error = Nastal problém s poskytnutým nastavením: { $problem }, v cestě { $path }, proti schématu { $schema }.
addons-window-title = Doplňky
addons-addon-has-no-configuration = Doplněk nemá žádné nastavení.
addons-addon-installation-error = Chyba instalace doplňku
addons-browse-addons = Procházet doplňky
addons-changes-will-take-effect-when-anki = Změny se projeví po restartování Anki.
addons-check-for-updates = Zkontrolovat aktualizace
addons-checking = Kontroluje se...
addons-code = Kód:
addons-config = Nastavení
addons-configuration = Nastavení
addons-corrupt-addon-file = Poškozený doplňkový soubor.
addons-disabled = (zakázáno)
addons-disabled2 = (zakázáno)
addons-download-complete-please-restart-anki-to = Stahování dokončeno. Prosím restartujte Anki, abyste aplikovali změny.
addons-downloaded-fnames = Stažen { $fname }
addons-downloading-adbd-kb02fkb = Stahování { $part }/{ $total } ({ $kilobytes } KB)...
addons-error-downloading-ids-errors = Chyba při stahování <i>{ $id }</i>: { $error }
addons-error-installing-bases-errors = Chyba instalace <i>{ $base }</i>: { $error }
addons-get-addons = Získat doplňky...
addons-important-as-addons-are-programs-downloaded = <b>Důležité</b>: Protože doplňky jsou programy stažené z internetu, jsou potencionálně nebezpečné. <b>Měli byste pouze instalovat doplňky, kterým věříte.</b><br><br>Jste si jistí, že chcete pokračovat v instalaci následujícího Anki doplňku (doplňků)?<br><br>%(names)s
addons-install-addon = Instalace doplňku
addons-install-addons = Instalace doplňku (doplňků)
addons-install-anki-addon = Instalovat doplněk Anki
addons-install-from-file = Instalovat ze souboru...
addons-installation-complete = Instalace dokončena
addons-installed-names = { $name } nainstalován
addons-installed-successfully = Úspěšně nainstalováno.
addons-invalid-addon-manifest = Neplatný manifest doplňku.
addons-invalid-code = Neplatný kód.
addons-invalid-code-or-addon-not-available = Neplatný kód nebo doplněk není dostupný pro vaši verzi Anki.
addons-invalid-configuration = Neplatná konfigurace:
addons-invalid-configuration-top-level-object-must = Neplatná konfigurace: objekt nejvyšší úrovně musí být mapa
addons-no-updates-available = Žádné aktualizace nejsou k dispozici.
addons-one-or-more-errors-occurred = Nastala jedna nebo více chyb:
addons-packaged-anki-addon = Zabalený Anki doplněk
addons-please-check-your-internet-connection = Zkontrolujte prosím své připojení k internetu.
addons-please-report-this-to-the-respective = Prosím nahlašte tuto věc autorovi/autorům tohoto doplňku.
addons-please-restart-anki-to-complete-the = <b>Prosím restartujte Anki, abyste dokončili instalaci.</b>
addons-please-select-a-single-addon-first = Nejdříve prosím vyberte jeden doplněk.
addons-requires = (vyžaduje { $val })
addons-restored-defaults = Původní nastavení obnovena
addons-the-following-addons-are-incompatible-with = Následující doplňky nejsou kompatibilní s { $name } a byly zakázány: { $found }
addons-the-following-addons-have-updates-available = Následující doplňky mají dostupné aktualizace. Instalovat je nyní?
addons-the-following-conflicting-addons-were-disabled = Následující konfliktní doplňky byly zakázány:
addons-this-addon-is-not-compatible-with = Tento doplněk není kompatibilní s vaší verzí Anki.
addons-to-browse-addons-please-click-the = Chcete-li procházet doplňky, prosím klikněte na tlačítko prohlížet níže.<br><br>Pokud jste našli doplněk, který se vám líbí, prosím vložte jeho kód níže. Je možné vložit více kódů oddělených mezerami.
addons-toggle-enabled = Přepnout povolení
addons-unable-to-update-or-delete-addon = Nelze aktualizovat nebo odstranit doplněk. Prosím spusťte Anki, zatímco držíte stisknutou klávesu shift, abyste dočasně zakázali doplňky, poté to zkuste znovu.  Ladicí informace: { $val }
addons-unknown-error = Neznámá chyba: { $val }
addons-view-addon-page = Zobrazit stránku doplňku
addons-view-files = Zobrazit soubory
addons-delete-the-numd-selected-addon =
    { $count ->
        [one] Odstranit { $count } vybraný doplněk?
        [few] Odstranit { $count } vybrané doplňky?
       *[other] Odstranit { $count } vybraných doplňků?
    }
addons-choose-update-window-title = Aktualizovat doplňky
addons-choose-update-update-all = Aktualizovat vše
