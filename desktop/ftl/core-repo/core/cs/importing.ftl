importing-failed-debug-info = Import selhal. Ladicí informace:
importing-aborted = Zrušeno: { $val }
importing-added-duplicate-with-first-field = Přidána duplicita s prvním polem: { $val }
importing-all-supported-formats = Všechny podporované formáty { $val }
importing-allow-html-in-fields = Povolit  HTML v polích
importing-anki-files-are-from-a-very = .anki soubory pocházejí z velmi staré verze Anki. Můžete je importovat pomocí Anki 2.0, který je dostupný na webových stránkách Anki.
importing-anki2-files-are-not-directly-importable = .anki2 soubory nelze importovat přímo - prosím importujte místo toho .apkg nebo .zip soubor, který máte.
importing-appeared-twice-in-file = V souboru se vyskytuje dvakrát: { $val }
importing-by-default-anki-will-detect-the = Anki implicitně detekuje znak oddělující pole, jako je tabulátor či čárka. Pokud ho Anki detekuje špatně, můžete ho vložit sem. \t představuje tabulátor.
importing-cannot-merge-notetypes-of-different-kinds =
    Tap poznámky doplňovačka nemůže být sloučen s jinými typy poznámek.
    Stále můžete importovat soubor, když je '{ importing-merge-notetypes }' zakázáno.
importing-change = Změnit
importing-colon = Dvojtečka
importing-comma = Čárka
importing-empty-first-field = Prázdné první pole: { $val }
importing-field-separator = Oddělovač polí
importing-field-separator-guessed = Oddělovač polí (odhadnutý)
importing-field-mapping = Přiřazení polí
importing-field-of-file-is = Pole <b>{ $val }</b> souboru je:
importing-fields-separated-by = Pole rozděleny pomocí: { $val }
importing-file-must-contain-field-column = Soubor musí obsahovat alespoň jeden sloupec, který se může přiřadit k poli poznámky.
importing-file-version-unknown-trying-import-anyway = Verze souboru neznámá, zkouším importovat.
importing-first-field-matched = První pole se shodovalo: { $val }
importing-identical = Totožný
importing-ignore-field = Ignorovat pole
importing-ignore-lines-where-first-field-matches = Ignorovat řádky, kde první pole odpovídá existující poznámce
importing-ignored = <ignorováno>
importing-import-even-if-existing-note-has = Importovat, i když existující poznámka má stejné první pole
importing-import-options = Importovat nastavení
importing-importing-complete = Import kompletní.
importing-invalid-file-please-restore-from-backup = Vadný soubor. Prosím obnovte ze zálohy.
importing-map-to = Přiřadit na { $val }
importing-map-to-tags = Přiřadit ke štítkům
importing-mapped-to = přiřazeno na <b>{ $val }</b>
importing-mapped-to-tags = přiřazeno na <b>Štítky</b>
# the action of combining two existing note types to create a new one
importing-merge-notetypes = Sloučit typy poznámek
importing-merge-notetypes-help =
    Je-li zaškrtnuto a vy nebo autor balíčku jste změnili schéma typu poznámky, Anki
    sloučí tyto dvě verze místo toho, aby ponechalo obě.
    
    Změna schématu typu poznámky znamená přidání, odstranění nebo změna pořadí polí nebo šablon,
    nebo zněna řazení polí.
    Naopak, změna přední strany existující šablony *nepředstavuje*
    změnu schématu.
    
    Varování: Tato akce vyžaduje synchronizaci v jednom směru a může označit stávající poznámky jako modifikované.
importing-mnemosyne-20-deck-db = Mnemosyne 2.0 balíček (*.db)
importing-multicharacter-separators-are-not-supported-please = Víceznakové oddělovače nejsou podporovány. Prosím, vložte pouze jeden znak.
importing-new-deck-will-be-created = Vytvoří se nový balíček: { $name }
importing-notes-added-from-file = Poznámky přidané ze souboru: { $val }
importing-notes-found-in-file = Poznámky nalezené v souboru: { $val }
importing-notes-skipped-as-theyre-already-in = Přeskočené poznámky, které jsou již v kolekci: { $val }
importing-notes-skipped-update-due-to-notetype = Neaktualizované poznámky, typ poznámky se od prvního importu změnil: { $val }
importing-notes-updated-as-file-had-newer = Poznámky aktualizovány, protože soubor měl novější verzi: { $val }
importing-include-reviews = Zahrnout opakování
importing-also-import-progress = Importovat veškerý pokrok v učení
importing-with-deck-configs = Importovat všechny předvolby balíčku
importing-updates = Aktualizace
importing-include-reviews-help =
    Je-li povoleno, všechna dřívější opakování, která jsou v balíčku zahrnuta, budou také importována. 
    V opačném případě budou všechny karty importovány jako nové karty.
importing-with-deck-configs-help = Je-li povoleno, veškeré nastavení balíčku, které osoba sdílející balíček zahrnula, bude také importováno. V opačném případě budou všechny balíčky přiřazeny k výchozí předvolbě.
importing-packaged-anki-deckcollection-apkg-colpkg-zip = Zabalený Anki balíček/kolekce (*.apkg *.colpkg *.zip)
# the '|' character
importing-pipe = Svislá čára
# Warning displayed when the csv import preview table is clipped (some columns were hidden)
# $count is intended to be a large number (1000 and above)
importing-preview-truncated =
    { $count ->
        [one] Zobrazuje se pouze první sloupec. Pokud se zdá, že to není v pořádku, zkuste změnit oddělovač polí.
        [few] Zobrazují se pouze první { $count } sloupce. Pokud se zdá, že to není v pořádku, zkuste změnit oddělovač polí.
        [many] { "" }
       *[other] Zobrazuje se pouze prvních { $count } sloupců. Pokud se zdá, že to není v pořádku, zkuste změnit oddělovač polí.
    }
importing-rows-had-num1d-fields-expected-num2d = „{ $row }“ mělo { $found } polí, namísto očekávaných { $expected }
importing-selected-file-was-not-in-utf8 = Vybraný soubor není ve formátu UTF-8. Blíže viz manuál kapitola Import.
importing-semicolon = Středník
importing-skipped = Přeskočeno
importing-tab = Tabulátor
importing-tag-modified-notes = Štítek modifikovaných poznámek:
importing-text-separated-by-tabs-or-semicolons = Text oddělený tabulátory nebo středníky (*)
importing-the-first-field-of-the-note = První pole typu poznámky musí být přiřazeno.
importing-the-provided-file-is-not-a = Toto není validní soubor .apkg.
importing-this-file-does-not-appear-to = Zdá se, že nejde o validní soubor .apkg. Pokud se vám tato chyba zobrazuje u souboru staženého z AnkiWeb, je možné, že nebyl stažen správně.
importing-this-will-delete-your-existing-collection = Tímto se odstraní vaše současná kolekce a nahradí se daty ze souboru, který importujete. Jste si jistý?
importing-unable-to-import-from-a-readonly = Nelze importovat ze souboru s právy jen pro čtení.
importing-unknown-file-format = Neznámý formát souboru.
importing-update-existing-notes-when-first-field = Aktualizovat existující poznámku, když je první pole stejné
importing-updated = Aktualizováno
importing-update-if-newer = Jsou-li novější
importing-update-always = Vždy
importing-update-never = Nikdy
importing-update-notes = Aktualizovat poznámky
importing-update-notes-help =
    Když se aktualizuje stávající poznámka ve vaší kolekci. Ve výchozím nastavení se toto 
    stane pouze, pokud odpovídající importovaná poznámka byla nedávno upravena.
importing-update-notetypes = Aktualizovat typy poznámek
importing-update-notetypes-help =
    Když se aktualizuje stávající typ poznámky ve vaší kolekci. Ve výchozím nastavení se toto stane 
    pouze, pokud odpovídající importovaný typ poznámky byl nedávno upraven. Změny v textu šablony 
    a formátování může být vždy importováno, ale pro změny schématu (např. se změnil počet nebo pořadí 
    polí), musí být také povolena volba '{ importing-merge-notetypes }'.
importing-note-added =
    { $count ->
        [one] { $count } poznámka přidána
        [few] { $count } poznámky přidány
       *[other] { $count } poznámek přidáno
    }
importing-note-imported =
    { $count ->
        [one] { $count } poznámka importována.
        [few] { $count } poznámky importovány.
       *[other] { $count } poznámek importováno.
    }
importing-note-unchanged =
    { $count ->
        [one] { $count } poznámka nezměněna
        [few] { $count } poznámky nezměněny
       *[other] { $count } poznámek nezměněno
    }
importing-note-updated =
    { $count ->
        [one] { $count } poznámka aktualizována
        [few] { $count } poznámky aktualizovány
       *[other] { $count } poznámek aktualizováno
    }
importing-processed-media-file =
    { $count ->
        [one] Importován { $count } multimediální soubor
        [few] Importovány { $count } multimediální soubory
       *[other] Importováno { $count } multimediálních souborů
    }
importing-importing-file = Importuje se soubor...
importing-extracting = Rozbalují se data...
importing-gathering = Shromažďují se data...
importing-failed-to-import-media-file = Import multimediálního souboru selhal: { $debugInfo }
importing-processed-notes =
    { $count ->
        [one] Zpracována { $count } poznámka...
        [few] Zpracovány { $count } poznámky...
       *[other] Zpracováno { $count } poznámek...
    }
importing-processed-cards =
    { $count ->
        [one] Zpracována { $count } karta...
        [few] Zpracovány { $count } karty...
       *[other] Zpracováno { $count } karet...
    }
importing-existing-notes = Stávající poznámky
# "Existing notes: Duplicate" (verb)
importing-duplicate = Duplikovat
# "Existing notes: Preserve" (verb)
importing-preserve = Zachovat
# "Existing notes: Update" (verb)
importing-update = Aktualizovat
importing-tag-all-notes = Označit všechny poznámky štítkem
importing-tag-updated-notes = Označit aktualizované poznámky štítkem
importing-file = Soubor
# "Match scope: notetype / notetype and deck". Controls how duplicates are matched.
importing-match-scope = Rozsah přiřazení
# Used with the 'match scope' option
importing-notetype-and-deck = Typ poznámky a balíček
importing-cards-added =
    { $count ->
        [one] { $count } karta přidána.
        [few] { $count } karty přidány.
       *[other] { $count } karet přidáno.
    }
importing-file-empty = Vybraný soubor je prázdný.
importing-notes-added =
    { $count ->
        [one] { $count } nová poznámka importována.
        [few] { $count } nové poznámky importovány.
       *[other] { $count } nových poznámek importováno.
    }
importing-notes-updated =
    { $count ->
        [one] { $count } poznámka se použila k aktualizaci existující poznámky.
        [few] { $count } poznámky se použily k aktualizaci existujících poznámek.
       *[other] { $count } poznámek se použilo k aktualizaci existujících poznámek.
    }
importing-existing-notes-skipped =
    { $count ->
        [one] { $count } poznámka již existuje ve vaší kolekci.
        [few] { $count } poznámky již existují ve vaší kolekci.
       *[other] { $count } poznámek již existuje ve vaší kolekci.
    }
importing-notes-failed =
    { $count ->
        [one] { $count } poznámka nemohla být importovana.
        [few] { $count } poznámky nemohly být importovány.
        [many] { $count } poznámky nemohlo být importovano.
       *[other] { $count } poznámek nemohlo být importováno.
    }
importing-conflicting-notes-skipped =
    { $count ->
        [one] { $count } poznámka se neimportovala, protože se změnil její typ poznámky.
        [few] { $count } poznámky se neimportovaly, protože se změnil jejich typ poznámky.
       *[other] { $count } poznámek se neimportovalo, protože se změnil jejich typ poznámky.
    }
importing-conflicting-notes-skipped2 =
    { $count ->
        [one] { $count } poznámka nebyla importována, protože se změnil její typ poznámky a '{ importing-merge-notetypes }' nebylo povoleno.
        [few] { $count } poznámky nebyly importovány, protože se změnil jejich typ poznámky a '{ importing-merge-notetypes }' nebylo povoleno.
        [many] { $count } poznámky nebyla importována, protože se změnil její typ poznámky a '{ importing-merge-notetypes }' nebylo povoleno.
       *[other] { $count } poznámek nebylo importováno, protože se změnil jejich typ poznámky a '{ importing-merge-notetypes }' nebylo povoleno.
    }
importing-import-log = Záznam importu
importing-no-notes-in-file = V souboru se nenalezly žádné poznámky.
importing-notes-found-in-file2 =
    { $notes ->
        [one] { $notes } poznámka nalezena
        [few] { $notes } poznámky nalezeny
       *[other] { $notes } poznámek nalezeno
    } v souboru. Z nich:
importing-show = Zobrazit
importing-details = Podrobnosti
importing-status = Stav
importing-duplicate-note-added = Přidána duplicitní poznámka
importing-added-new-note = Přidána nová poznámka
importing-existing-note-skipped = Poznámka přeskočena, protože aktuální kopie poznámky je již ve vaší kolekci
importing-note-skipped-update-due-to-notetype = Poznámka se neaktualizovala, protože typ poznámky se změnil od té doby, kdy jste poprvé importovali tuto poznámku
importing-note-skipped-update-due-to-notetype2 = Poznámka nebyla aktualizována, protože typ poznámky se od prvního importu poznamky změnil a '{ importing-merge-notetypes }' nebylo povoleno
importing-note-updated-as-file-had-newer = Poznámka aktualizována, protože v souboru byla novější verze
importing-note-skipped-due-to-missing-notetype = Poznámka přeskočena, protože chyběl její typ poznámky
importing-note-skipped-due-to-missing-deck = Poznámka přeskočena, protože chyběl její balík
importing-note-skipped-due-to-empty-first-field = Poznámka přeskočena, protože její první pole je prázdné
importing-field-separator-help =
    Znak oddělující pole v textovém souboru. Můžete použít náhled, abyste zkontrolovali, 
    jestli jsou pole oddělena správně.
    
    Upozorňujeme, že jestliže se tento znak objeví v jakémkoli poli, musí být toto pole
    odpovídajícím způsobem uvozeno podle standardu CSV. Tabulkové procesory jako 
    LibreOffice toto dělají automaticky.
importing-allow-html-in-fields-help =
    Povolte, jestliže soubor obsahuje formátování HTML. Např. jestliže soubor obsahuje 
    řetězec '&lt;br&gt;', zobrazí se na vaší kartě jako konec řádku. Na druhou stranu, 
    jestliže je tato možnost zakázána, budou vykresleny doslova znaky '&lt;br&gt;'.
importing-notetype-help =
    Nově importované poznámky budou mít tento typ poznámky a pouze stávající 
    poznámky s tímto typem poznámky se aktualizují.
    
    Pomocí přiřazovacího nástroje si můžete vybrat, která pole v souboru odpovídají 
    kterým polím typu poznámky.
importing-deck-help = Importované karty se umístí do tohoto balíčku.
importing-existing-notes-help =
    Co dělat, jestliže se importovaná poznámka shoduje se stávající poznámkou.
    
    - `{ importing-update }`: Aktualizovat stávající poznámku.
    - `{ importing-preserve }`: Nedělat nic.
    - `{ importing-duplicate }`: Vytvořit novou poznámku.
importing-match-scope-help =
    Jestli existují duplikáty se bude kontrolovat pouze u stávajících poznámek se stejným 
    typem poznámky. To může být navíc omezeno na poznámky s kartami ve stejném balíčku.
importing-tag-all-notes-help = Tyto štítky se přidají do nově importovaných i aktualizovaných poznámek.
importing-tag-updated-notes-help = Tyto štítky se přidají do všech aktualizovaných poznámek.
importing-overview = Přehled

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

importing-importing-collection = Importuje se kolekce…
importing-unable-to-import-filename = Nelze importovat { $filename }: typ souboru není podporován
importing-notes-that-could-not-be-imported = Poznámky, které nemohly být importovány, protože se změnil typ poznámky: { $val }
importing-added = Přidáno
importing-pauker-18-lesson-paugz = Pauker 1.8 lekce (*.pau.gz)
importing-supermemo-xml-export-xml = Supermemo XML export (*.xml)
