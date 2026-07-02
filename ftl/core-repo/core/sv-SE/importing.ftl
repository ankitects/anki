importing-failed-debug-info = Import misslyckades. Felsökningsinformation:
importing-aborted = Avbröts: { $val }
importing-added-duplicate-with-first-field = Lade till dublett med första fält: { $val }
importing-all-supported-formats = Alla stödda format { $val }
importing-allow-html-in-fields = Tillåt HTML i fälten
importing-anki-files-are-from-a-very = .anki-filer är från en väldigt gammal version av Anki. De kan importeras med tillägg 175027074 eller med Anki 2.0, tillgängligt på Anki-hemsidan.
importing-anki2-files-are-not-directly-importable = .anki2-filer är inte direkt importerbara - var god importera istället .apkg- eller .zip-filen som den hör till.
importing-appeared-twice-in-file = Hittades två gånger i filen: { $val }
importing-by-default-anki-will-detect-the = Som förval identifierar Anki vilket tecken som används för att skilja fält åt, som tabbsteg, komma osv. Om Anki gör ett felaktigt val av tecken, så kan du ange rätt tecken här. Använd \t för tabbsteg.
importing-cannot-merge-notetypes-of-different-kinds =
    Lucktextnoter kan inte sammanfogas med vanliga nottyper.
    Filen kan fortfarande importeras med '{ importing-merge-notetypes }' inaktiverat.
importing-change = Ändra
importing-colon = Kolon
importing-comma = Kommatecken
importing-empty-first-field = Tomt första fält: { $val }
importing-field-separator = Fältseparator
importing-field-separator-guessed = Fältseparator (gissad)
importing-field-mapping = Fälthopparning
importing-field-of-file-is = Fält <b>{ $val }</b> i fil är:
importing-fields-separated-by = Fält separerade av: { $val }
importing-file-must-contain-field-column = Filen måste innehålla åtminstone en kolumn som motsvarar ett notfält.
importing-file-version-unknown-trying-import-anyway = Okänd filversion, försöker ändå att importera.
importing-first-field-matched = Första matchande fält: { $val }
importing-identical = Identisk
importing-ignore-field = Ignorera fält
importing-ignore-lines-where-first-field-matches = Ignorera rader där det första fältet matchar existerande note
importing-ignored = <ignoreras>
importing-import-even-if-existing-note-has = Importera även om redan existerande not har samma förstafält
importing-import-options = Importalternativ
importing-importing-complete = Importering slutförd.
importing-invalid-file-please-restore-from-backup = Fil ogiltig. Var god återställ från säkerhetskopia.
importing-map-to = Mappa till { $val }
importing-map-to-tags = Para ihop med etiketter
importing-mapped-to = parades ihop med <b>{ $val }</b>
importing-mapped-to-tags = parades ihop med <b>etiketter</b>
# the action of combining two existing note types to create a new one
importing-merge-notetypes = Sammanfoga nottyper
importing-merge-notetypes-help =
    Om aktiverad, och användaren eller kortleksförfattaren har ändrat på schemat för en nottyp, kommer
    Anki att sammanfoga de två varianterna istället för att behålla båda.
    
    Att ändra på schemat för en nottyp innebär att lägga till, ta bort eller ändra ordningen på fält eller mallar,
    eller ändra vilket fält som är sorteringsfält.
    Som ett motexempel utgör ändringar på framsidan av en befintlig mall *inte*
    en schemaförändring.
    
    Varning: Detta kommer kräva en envägssynkronisering, och kan etikettera befintliga noter som modifierade.
importing-mnemosyne-20-deck-db = Kortlek för Mnemosyne 2.0 (*.db)
importing-multicharacter-separators-are-not-supported-please = Separatorer med fler än ett tecken stöds inte. Skriv in endast ett tecken.
importing-new-deck-will-be-created = En ny kortlek kommer skapas: { $name }
importing-notes-added-from-file = Noter tillagda från filen: { $val }
importing-notes-found-in-file = Noter hittade i filen: { $val }
importing-notes-skipped-as-theyre-already-in = Noter hoppades över eftersom aktuella kopior redan finns i samlingen: { $val }
importing-notes-skipped-update-due-to-notetype = Noter uppdaterades ej eftersom nottypen har modifierats sedan noterna först importerades: { $val }
importing-notes-updated-as-file-had-newer = Noter uppdaterade, eftersom filen hade en mer aktuell version: { $val }
importing-include-reviews = Inkludera repetitioner
importing-also-import-progress = Importera inlärningsframsteg
importing-with-deck-configs = Importera kortleksförinställningar
importing-updates = Uppdateringar
importing-include-reviews-help =
    Om aktiverad kommer tidigare repetitioner som kortleksförfattaren inkluderat också att importeras.
    Annars kommer alla kort importeras som nya kort, och alla 'leech'- eller 'marked'-etiketter
    tas bort.
importing-with-deck-configs-help =
    Om aktiverad kommer eventuella kortleksinställningar som kortlekens skapare inkluderat också att importeras.
    Annars kommer alla kortlekar att tilldelas den förvalda förinställningen.
importing-packaged-anki-deckcollection-apkg-colpkg-zip = Paketerad Ankikortlek/-samling (*.apkg *.colpkg *.zip)
# the '|' character
importing-pipe = Pipa
# Warning displayed when the csv import preview table is clipped (some columns were hidden)
# $count is intended to be a large number (1000 and above)
importing-preview-truncated =
    { $count ->
        [one] Endast den första kolumnen visas.
       *[other] Endast de första { $count } kolumnerna visas. Ifall detta förefaller vara felaktigt, pröva att ändra fältseparatorn.
    }
importing-rows-had-num1d-fields-expected-num2d = '{ $row }' hade { $found } fält, förväntat antal är { $expected }
importing-selected-file-was-not-in-utf8 = Den valda filen var inte i UTF-8-format. Se avsnittet i manualen om att importera.
importing-semicolon = Semikolon
importing-skipped = Hoppades över
importing-tab = Tabb
importing-tag-modified-notes = Etikettera ändrade noter:
importing-text-separated-by-tabs-or-semicolons = Text separerad med tabbar eller semikolon (*)
importing-the-first-field-of-the-note = Det första fältet i nottypen måste paras ihop.
importing-the-provided-file-is-not-a = Den angivna filen är inte en giltig .apkg-fil.
importing-this-file-does-not-appear-to = Denna fil verkar inte vara en giltig .apkg-fil. Om du får detta fel med en fil du laddat ned från AnkiWeb, har nedladdningen troligtvis misslyckats. Försök igen, och om problemet kvarstår, försök igen med en annan webbläsare.
importing-this-will-delete-your-existing-collection = Detta kommer att ta bort din nuvarande samling och ersätta den med data från filen du importerar. Är du säker?
importing-unable-to-import-from-a-readonly = Kan inte importera från en skrivskyddad fil.
importing-unknown-file-format = Okänt filformat.
importing-update-existing-notes-when-first-field = Uppdatera existerande noter när det första fältet matchar
importing-updated = Uppdaterat
importing-update-if-newer = Om nyare
importing-update-always = Alltid
importing-update-never = Aldrig
importing-update-notes = Uppdatera noter
importing-update-notes-help =
    När en befintlig not bör uppdateras. Som förval utförs detta endast
    om den matchande importerade noten var mer nyligen ändrad.
importing-update-notetypes = Uppdatera nottyper
importing-update-notetypes-help =
    När en befintlig not bör uppdateras i samlingen. Som förval utförs detta endast
    om den importerade noten var mer nyligen ändrad. Ändringar till kortmallens innehåll
    och stilmall kan alltid importeras, men för schemaförändringar (t.ex. att fältens ordning eller antal har ändrats), måste alternativet '{ importing-merge-notetypes }' också vara aktiverat.
importing-note-added =
    { $count ->
        [one] { $count } not tillagd
       *[other] { $count } noter tillagda
    }
importing-note-imported =
    { $count ->
        [one] { $count } not importerad
       *[other] { $count } noter importerade
    }
importing-note-unchanged =
    { $count ->
        [one] { $count } not oförändrad
       *[other] { $count } noter oförändrade
    }
importing-note-updated =
    { $count ->
        [one] { $count } not uppdaterad
       *[other] { $count } noter uppdaterade
    }
importing-processed-media-file =
    { $count ->
        [one] Bearbetade { $count } mediafil
       *[other] Bearbetade { $count } mediafiler
    }
importing-importing-file = Importerar fil ...
importing-extracting = Extraherar data ...
importing-gathering = Samlar data ...
importing-failed-to-import-media-file = Det gick inte att importera mediafilen: { $debugInfo }
importing-processed-notes =
    { $count ->
        [one] { $count } not behandlad ...
       *[other] { $count } noter behandlade ...
    }
importing-processed-cards =
    { $count ->
        [one] { $count } kort behandlat ...
       *[other] { $count } kort behandlade ...
    }
importing-existing-notes = Existerande noter
# "Existing notes: Duplicate" (verb)
importing-duplicate = Duplicera
# "Existing notes: Preserve" (verb)
importing-preserve = Bevara
# "Existing notes: Update" (verb)
importing-update = Uppdatera
importing-tag-all-notes = Etikettera alla noter
importing-tag-updated-notes = Etikettera uppdaterade noter
importing-file = Fil
# "Match scope: notetype / notetype and deck". Controls how duplicates are matched.
importing-match-scope = Matchningskontext
# Used with the 'match scope' option
importing-notetype-and-deck = Nottyp och kortlek
importing-cards-added =
    { $count ->
        [one] { $count } kort tillagt.
       *[other] { $count } kort tillagda.
    }
importing-file-empty = Den fil du valde är tom.
importing-notes-added =
    { $count ->
        [one] { $count } ny not importerad.
       *[other] { $count } nya noter importerade.
    }
importing-notes-updated =
    { $count ->
        [one] { $count } not användes för att uppdatera befintliga noter.
       *[other] { $count } noter användes för att uppdatera befintliga noter.
    }
importing-existing-notes-skipped =
    { $count ->
        [one] { $count } not finns redan i samlingen.
       *[other] { $count } noter finns redan i samlingen.
    }
importing-notes-failed =
    { $count ->
        [one] { $count } not kunde inte importeras.
       *[other] { $count } noter kunde inte importeras.
    }
importing-conflicting-notes-skipped =
    { $count ->
        [one] { $count } not importerades ej eftersom dess nottyp har förändrats.
       *[other] { $count } noter importerades ej eftersom deras nottyper har förändrats.
    }
importing-conflicting-notes-skipped2 =
    { $count ->
        [one] { $count } not importerades ej eftersom dess nottyp har ändrats samt att '{ importing-merge-notetypes }' inte har aktiverats.
       *[other] { $count } noter importerades ej eftersom deras nottyper har ändrats samt att '{ importing-merge-notetypes }' inte har aktiverats.
    }
importing-import-log = Importlogg
importing-no-notes-in-file = Inga noter hittades i filen.
importing-notes-found-in-file2 =
    { $notes ->
        [one] { $notes } not hittad i filen. Av dessa:
       *[other] { $notes } noter hittade i filen. Av dessa:
    }
importing-show = Visa
importing-details = Detaljer
importing-status = Status
importing-duplicate-note-added = Dubblettnot tillagd
importing-added-new-note = Ny not tillagd
importing-existing-note-skipped = Not hoppades över eftersom en aktuell kopia redan finns i samlingen
importing-note-skipped-update-due-to-notetype = Not uppdaterades ej, eftersom nottypen har ändrats sedan noten först importerades
importing-note-skipped-update-due-to-notetype2 = Not uppdaterades ej, eftersom nottypen har ändrats sedan noten först importerades, och '{ importing-merge-notetypes }' ej var aktiverat
importing-note-updated-as-file-had-newer = Not uppdaterades eftersom filen hade en nyare version
importing-note-skipped-due-to-missing-notetype = Not hoppades över eftersom dess nottyp saknades
importing-note-skipped-due-to-missing-deck = Not hoppades över eftersom dess kortlek saknades
importing-note-skipped-due-to-empty-first-field = Not hoppades över eftersom dess första fält var tomt
importing-field-separator-help =
    Tecknet som separerar fält i textfilen. Förhandsgranskningen kan användas
    för att kontrollera att fälten separeras korrekt.
    
    Var god notera att om detta tecken förekommer i något fält måste fältet
    citeras enligt CSV-standarden. Kalkylprogram som LibreOffice
    gör detta automatiskt.
importing-allow-html-in-fields-help =
    Aktivera detta om filen innehåller HTML-formattering. Om filen t.ex. innehåller strängen
    '&lt;br&gt;' kommer den visas som en radbrytning på kortet. Däremot, om detta
    alternativ är inaktiverat kommer bokstavligen tecknen '&lt;br&gt;' att visas.
importing-notetype-help =
    Nyligen importerade noter kommer ha denna nottyp, och endast befintliga noter med
    denna nottyp kommer uppdateras.
    
    Mappningsverktyget kan användas för att välja vilka fält som motsvarar vilka nottypsfält.
importing-deck-help = Importerade kort kommer placeras i denna kortlek.
importing-existing-notes-help =
    Vad som bör göras om en importerad not matchar en befintlig not.
    
    - `{ importing-update }`: Uppdatera den befintliga noten.
    - `{ importing-preserve }`: Gör inget.
    - `{ importing-duplicate }`: Skapa en ny not.
importing-match-scope-help =
    Endast befintliga noter med samma nottyp kommer kontrolleras för dubletter. Detta kan
    dessutom begränsas till noter med kort i samma kortlek.
importing-tag-all-notes-help = Dessa etiketter kommer läggas till både nyligen importerade och uppdaterade noter.
importing-tag-updated-notes-help = Dessa etiketter kommer läggas till eventuella uppdaterade noter.
importing-overview = Översikt

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

importing-importing-collection = Importerar samling ...
importing-unable-to-import-filename = Kunde inte importera { $filename }: filtyp stöds ej
importing-notes-that-could-not-be-imported = Noter som inte kunde importeras som nottyp har ändrats: { $val }
importing-added = Tillagda
importing-pauker-18-lesson-paugz = Pauker 1.8-lektion (*.pau.gz)
importing-supermemo-xml-export-xml = XML-export för Supermemo (*.xml)
