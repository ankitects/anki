importing-failed-debug-info = La importació ha fallat. Informació de depuració:
importing-aborted = Interromput: { $val }
importing-added-duplicate-with-first-field = S'ha afegit duplicada amb el primer camp: { $val }
importing-all-supported-formats = Tots els formats compatibles { $val }
importing-allow-html-in-fields = Permet l'HTML als camps
importing-anki-files-are-from-a-very = Aquests fitxers .anki són d'una versió anterior massa antiga d'Anki. Podeu importar-los amb el complement 175027074 o amb Anki 2.0, que està disponible en el web d'Anki.
importing-anki2-files-are-not-directly-importable = Els fitxers .anki2 no es poden importar directament; en el seu lloc, importeu els fitxers .apkg o .zip que heu rebut.
importing-appeared-twice-in-file = Ha aparegut dues vegades en el fitxer: { $val }
importing-by-default-anki-will-detect-the = Per defecte, Anki detectarà una tabulació, una coma o similar entre camps. Si Anki reconeix malament aquest caràcter, introduïu-lo ací. Per a representar la tabulació, heu de fer servir \t.
importing-cannot-merge-notetypes-of-different-kinds =
    No és possible combinar tipus de nota amb buits amb tipus de nota normals.
    Podeu importar el fitxer amb l’opció «{ importing-merge-notetypes }» desactivada.
importing-change = Canvia
importing-colon = Dos punts
importing-comma = Coma
importing-empty-first-field = Primer camp buit: { $val }
importing-field-separator = Separador de camps
importing-field-separator-guessed = Separador de camps (detectat)
importing-field-mapping = Assignació de camps
importing-field-of-file-is = El camp <b>{ $val }</b> del fitxer és:
importing-fields-separated-by = Camps separats per: { $val }
importing-file-must-contain-field-column = El fitxer ha de contenir almenys una columna que pugui assignar-se a un camp de la nota.
importing-file-version-unknown-trying-import-anyway = La versió del fitxer és desconeguda; de totes maneres, Anki intentarà importar-lo.
importing-first-field-matched = Primer camp coincident: { $val }
importing-identical = Idèntic
importing-ignore-field = Ignora el camp
importing-ignore-lines-where-first-field-matches = Ignora les línies en què el primer camp coincideix amb una nota existent
importing-ignored = <ignorat>
importing-import-even-if-existing-note-has = Importa encara que existeixi una nota amb el mateix primer camp
importing-import-options = Opcions d'importació
importing-importing-complete = Importació completa.
importing-invalid-file-please-restore-from-backup = Fitxer invàlid. Restaureu des d'una còpia de seguretat.
importing-map-to = Associa a { $val }
importing-map-to-tags = Associa a les etiquetes
importing-mapped-to = assignat a <b>{ $val }</b>
importing-mapped-to-tags = assignat a <b>Etiquetes</b>
# the action of combining two existing note types to create a new one
importing-merge-notetypes = Fusiona els tipus de nota
importing-merge-notetypes-help =
    Si activeu aquesta opció i s’ha modificat l’estructura d’un tipus de nota, Anki en fusionarà les dues versions.
    
    S’ha modificat l’estructura d’un tipus de nota si s’ha afegit, eliminat o canviat l’ordre de camps o plantilles o si s’ha canviat el camp d’ordenació.
    Modificar l’anvers d’una plantilla *no* en modifica l’estructura.
    
    Avís: haureu de forçar la sincronització i les notes podrien marcar-se com a modificades.
importing-mnemosyne-20-deck-db = Baralla Mnemosyne 2.0 (*.db)
importing-multicharacter-separators-are-not-supported-please = Els separadors de més d'un caràcter no son vàlids; introduïu un sol caràcter.
importing-new-deck-will-be-created = Es crearà una nova baralla: { $name }
importing-notes-added-from-file = Notes afegides des del fitxer: { $val }
importing-notes-found-in-file = Notes trobades en el fitxer: { $val }
importing-notes-skipped-as-theyre-already-in = S’han omès les notes perquè ja n’hi ha versions actualitzades en la col·lecció: { $val }
importing-notes-skipped-update-due-to-notetype = No s’han actualitzat les notes perquè el tipus de nota s’ha modificat des de la importació original: { $val }
importing-notes-updated-as-file-had-newer = S’han actualitzat les notes perquè el fitxer contenia una versió més recent: { $val }
importing-include-reviews = Inclou els repassos
importing-also-import-progress = Importa també qualsevol procés d’aprenentatge
importing-with-deck-configs = Importa les configuracions de baralla
importing-updates = Actualitzacions
importing-include-reviews-help =
    Si activeu aquesta opció, s’importaran els repassos del creador de la baralla.
    En cas contrari, totes les targetes s’importaran com a noves i s’eliminaran les etiquetes «leech» i «marked».
importing-with-deck-configs-help =
    Si activeu aquesta opció, s’importaran les configuracions de baralla de la persona que l’ha compartida.
    En cas contrari, s’assignarà la configuració per defecte a totes les baralles.
importing-packaged-anki-deckcollection-apkg-colpkg-zip = Baralla comprimida d’Anki o col·lecció (*.apkg *.colpkg *.zip)
# the '|' character
importing-pipe = Barra vertical
# Warning displayed when the csv import preview table is clipped (some columns were hidden)
# $count is intended to be a large number (1000 and above)
importing-preview-truncated =
    { $count ->
        [one] Només es mostra la primera columna. Si no és correcte, canvieu el separador de camps.
       *[other] Només es mostren les primeres { $count } columnes. Si no és correcte, canvieu el separador de camps.
    }
importing-rows-had-num1d-fields-expected-num2d = '{ $row }' tenia { $found } camps, se n'esperaven { $expected }
importing-selected-file-was-not-in-utf8 = El fitxer seleccionat no està en format UTF-8; llegiu la secció del manual referent a la importació per a més informació.
importing-semicolon = Punt i coma
importing-skipped = Omès
importing-tab = Tabulació
importing-tag-modified-notes = Etiqueta les notes modificades:
importing-text-separated-by-tabs-or-semicolons = Text separat per tabulacions o punt i coma (*)
importing-the-first-field-of-the-note = El primer camp del tipus de nota ha d'assignar-se a alguna cosa.
importing-the-provided-file-is-not-a = El fitxer proporcionat no és un fitxer .apkg vàlid.
importing-this-file-does-not-appear-to = Sembla que aquest fitxer no és un fitxer .apkg vàlid. Si heu obtingut aquest error amb un fitxer descarregat des d'AnkiWeb, és possible que la descàrrega hagi fallat. Torneu-ho a intentar i, si el problema persisteix, intenteu-ho amb un altre navegador.
importing-this-will-delete-your-existing-collection = S’eliminarà la col·lecció actual i se substituirà amb les dades del fitxer que importeu. Segur que voleu continuar?
importing-unable-to-import-from-a-readonly = No és possible importar des d'un fitxer de només lectura.
importing-unknown-file-format = Format de fitxer desconegut.
importing-update-existing-notes-when-first-field = Actualitza les targetes existents quan el primer camp coincideixi
importing-updated = Actualitzat
importing-update-if-newer = Si són més noves
importing-update-always = Sempre
importing-update-never = Mai
importing-update-notes = Actualitza les notes
importing-update-notes-help = Quan s’actualitzaran les notes de la col·lecció. Per defecte, només s’actualitzaran si les notes importades coincidents s’han modificat més recentment.
importing-update-notetypes = Actualtza els tipus de nota
importing-update-notetypes-help =
    Quan s’actualitzaran els tipus de nota de la col·lecció. Per defecte, només s’actualitzaran si els tipus de nota importats coincidents s’han modificat més recentment.
    Els canvis en el text i l’estil de les plantilles sempre es poden importar. Per a actualitzar els canvis d’estructura (per exemple, si s’ha modificat la quantitat o l’ordre dels camps), activeu l’opció «{ importing-merge-notetypes }».
importing-note-added =
    { $count ->
        [one] S'ha afegit una nota
       *[other] S'han afegit { $count } notes
    }
importing-note-imported =
    { $count ->
        [one] S'ha importat una nota.
       *[other] S'han importat { $count } notes.
    }
importing-note-unchanged =
    { $count ->
        [one] No s’ha modificat una nota
       *[other] No s’han modificat { $count } notes
    }
importing-note-updated =
    { $count ->
        [one] S’ha actualitzat una nota
       *[other] S’han actualitzat { $count } notes
    }
importing-processed-media-file =
    { $count ->
        [one] S'ha processat un fitxer multimèdia
       *[other] S'han processat { $count } fitxers multimèdia
    }
importing-importing-file = S'està important el fitxer…
importing-extracting = Extraient-ne les dades…
importing-gathering = Reunint-ne les dades…
importing-failed-to-import-media-file = No s'ha pogut importar el fitxer multimèdia: { $debugInfo }
importing-processed-notes =
    { $count ->
        [one] S'ha processat una nota…
       *[other] S'han processat { $count } notes…
    }
importing-processed-cards =
    { $count ->
        [one] S'ha processat una targeta…
       *[other] S'han processat { $count } targetes…
    }
importing-existing-notes = Notes existents
# "Existing notes: Duplicate" (verb)
importing-duplicate = Duplica
# "Existing notes: Preserve" (verb)
importing-preserve = Omet
# "Existing notes: Update" (verb)
importing-update = Actualitza
importing-tag-all-notes = Etiqueta totes les notes
importing-tag-updated-notes = Etiqueta les notes actualitzades
importing-file = Fitxer
# "Match scope: notetype / notetype and deck". Controls how duplicates are matched.
importing-match-scope = Abast de correspondència
# Used with the 'match scope' option
importing-notetype-and-deck = Tipus de nota i baralla
importing-cards-added =
    { $count ->
        [one] S’ha afegit una targeta.
       *[other] S’han afegit { $count } targetes.
    }
importing-file-empty = El fitxer que heu seleccionat està buit.
importing-notes-added =
    { $count ->
        [one] S’ha afegit una nota nova.
       *[other] S’han afegit { $count } notes noves.
    }
importing-notes-updated =
    { $count ->
        [one] S’ha utilitzat una nota per a actualitzar les notes existents.
       *[other] S’han utilitzat { $count } notes per a actualitzar les notes existents.
    }
importing-existing-notes-skipped =
    { $count ->
        [one] Ja hi ha una nota en la vostra col·lecció.
       *[other] Ja hi ha { $count } notes en la vostra col·lecció.
    }
importing-notes-failed =
    { $count ->
        [one] No s’ha pogut importar una nota.
       *[other] No s’han pogut importar { $count } notes.
    }
importing-conflicting-notes-skipped =
    { $count ->
        [one] No s’ha importat una nota perquè el tipus ha canviat.
       *[other] No s’han importat { $count } notes perquè el tipus ha canviat.
    }
importing-conflicting-notes-skipped2 =
    { $count ->
        [one] No s’ha importat una nota perquè el tipus ha canviat i ‘{ importing-merge-notetypes }’ no està activat.
       *[other] No s’han importat { $count } notes perquè el tipus ha canviat i ‘{ importing-merge-notetypes }’ no està activat.
    }
importing-import-log = Registre d’importació
importing-no-notes-in-file = No s’ha trobat cap nota en el fitxer.
importing-notes-found-in-file2 =
    { $notes ->
        [one] S'ha trobat una nota
       *[other] S’han trobat { $notes } notes
    } en el fitxer, de les quals:
importing-show = Mostra
importing-details = Detalls
importing-status = Estat
importing-duplicate-note-added = S’ha afegit una nota duplicada.
importing-added-new-note = S’ha afegit una nota nova.
importing-existing-note-skipped = S’ha ignorat la nota perquè ja en teniu una còpia actualitzada
importing-note-skipped-update-due-to-notetype = No s’ha actualitzat la nota perquè el tipus de nota s’ha modificat des de la importació original: { $val }
importing-note-skipped-update-due-to-notetype2 = No s’ha actualitzat la nota perquè el tipus de nota s’ha modificat des que la vau importar i no heu activat «{ importing-merge-notetypes }».
importing-note-updated-as-file-had-newer = Nota actualitzada perquè el fitxer contenia una versió més recent: { $val }
importing-note-skipped-due-to-missing-notetype = S’ha ignorat la nota perquè no té tipus
importing-note-skipped-due-to-missing-deck = S’ha ignorat la nota perquè no té baralla
importing-note-skipped-due-to-empty-first-field = S’ha ignorat la nota perquè el primer camp està buit
importing-field-separator-help =
    Caràcter amb què se separaran els camps del fitxer de text. Podeu fer servir la previsualització per a comprovar que els camps estiguin ben separats.
    
    Tingueu en compte que, si aquest caràcter apareix en un camp, aquest haurà de seguir l’estàndard CSV. Els programes de càlcul, com LibreOffice, ho fan automàticament.
    
    Aquest caràcter no es pot canviar si el fitxer de text obliga a utilitzar un separador mitjançant una capçalera. Si no hi ha capçalera, Anki intentarà detectar el separador.
importing-allow-html-in-fields-help = Activeu aquesta opció si el fitxer conté text en format HTML. Així, per exemple, la cadena ‘&lt;br&gt;’ apareixerà com a salt de línia en la targeta. Si desactiveu aquesta opció, es mostraran els caràcters ‘&lt;br&gt;’.
importing-notetype-help =
    Les notes recentment importades tindran aquest tipus de nota, i només s’actualitzaran les notes existents que tinguin aquest tipus de nota.
    
    Podeu triar quins camps del fitxer corresponen a quins camps de tipus nota amb l’eina de mapatge.
importing-deck-help = Les targetes importades s’afegiran a aquesta baralla.
importing-existing-notes-help =
    Quan una nota importada coincideixi amb una altra:
    
    - `{ importing-update }`: Actualitza la nota existent.¶
    - `{ importing-preserve }`: Omet.¶
    - `{ importing-duplicate }`: Crea una nota nova.
importing-match-scope-help = No es comprovarà si hi ha duplicats entre les notes existents del mateix tipus. Podeu restringir aquesta opció a les notes amb targetes en la mateixa baralla.
importing-tag-all-notes-help = S’afegiran aquestes etiquetes a les notes importades i actualitzades.
importing-tag-updated-notes-help = Aquestes etiquetes s’afegiran a totes les notes que actualitzeu.
importing-overview = Resum

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

importing-importing-collection = S'està important la col·lecció…
importing-unable-to-import-filename = No s'ha pogut importar el fitxer { $filename }. Tipus de fitxer no compatible.
importing-notes-that-could-not-be-imported = Notes que no s'han pogut importar a causa d'un canvi de tipus de nota: { $val }
importing-added = S'ha afegit
importing-pauker-18-lesson-paugz = Lliçó de Pauker 1.8 (*.pau.gz)
importing-supermemo-xml-export-xml = XML exportat de Supermemo (*.xml)
