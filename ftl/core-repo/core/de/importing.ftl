importing-failed-debug-info = Import fehlgeschlagen. Die Fehlermeldung lautet:
importing-aborted = Abgebrochen: { $val }
importing-added-duplicate-with-first-field = Duplikat mit folgendem ersten Feld hinzugefügt: { $val }
importing-all-supported-formats = Alle unterstützten Formate { $val }
importing-allow-html-in-fields = HTML in Feldern zulassen
importing-anki-files-are-from-a-very = .anki-Dateien stammen noch von einer sehr alten Version von Anki. Sie können diese mithilfe des Add-ons 175027074 oder in Anki 2.0 importieren. Anki 2.0 steht Ihnen auf der Webseite von Anki zur Verfügung.
importing-anki2-files-are-not-directly-importable = .anki2-Dateien können nicht direkt importiert werden - bitte importieren Sie die .apkg- oder .zip-Datei, die Sie stattdessen erhalten haben.
importing-appeared-twice-in-file = Doppelt vorhanden in Datei: { $val }
importing-by-default-anki-will-detect-the = Für gewöhnlich erkennt Anki das Trennzeichen zwischen zwei Feldern (z.B. ein Komma, Tab oder Ähnliches) automatisch. Sollte Anki das Trennzeichen nicht korrekt erkennen, gaben Sie es hier ein. Anstelle von Tab verwenden Sie bitte: \t.
importing-cannot-merge-notetypes-of-different-kinds =
    Lückentext-Notiztypen können nicht mit normalen Notiztypen zusammengeführt werden.
    Sie können die Datei trotzdem importieren, wenn Sie die Einstellung "{ importing-merge-notetypes }" deaktivieren.
importing-change = Ändern
importing-colon = Doppelpunkt
importing-comma = Komma
importing-empty-first-field = Erstes Feld ist leer: { $val }
importing-field-separator = Feldtrennzeichen
importing-field-separator-guessed = Feldtrennzeichen (automatisch erkannt)
importing-field-mapping = Feldzuordnung
importing-field-of-file-is = Feld <b>{ $val }</b> der Datei ist:
importing-fields-separated-by = Feldtrennzeichen: { $val }
importing-file-must-contain-field-column = Die Datei muss mindestens eine Spalte beinhalten, die einem Notizfeld zugeordnet werden kann.
importing-file-version-unknown-trying-import-anyway = Die Dateiversion ist unbekannt. Es wird trotzdem versucht, den Import durchzuführen.
importing-first-field-matched = Erstes Feld stimmt überein mit: { $val }
importing-identical = Identisch
importing-ignore-field = Feld ignorieren
importing-ignore-lines-where-first-field-matches = Zeilen ignorieren, wenn das erste Feld mit einer bereits vorhandenen Notiz übereinstimmt
importing-ignored = <ignoriert>
importing-import-even-if-existing-note-has = Auch dann importieren, wenn es eine vorhandene Notiz mit demselben ersten Feld gibt
importing-import-options = Einstellungen für den Import
importing-importing-complete = Importierung abgeschlossen.
importing-invalid-file-please-restore-from-backup = Ungültige Datei. Bitte eine Sicherungskopie öffnen.
importing-map-to = { $val } zuordnen
importing-map-to-tags = Schlagwörter zuordnen
importing-mapped-to = abgebildet auf <b>{ $val }</b>
importing-mapped-to-tags = abgebildet auf <b>Schlagwörter</b>
# the action of combining two existing note types to create a new one
importing-merge-notetypes = Notiztypen zusammenführen
importing-merge-notetypes-help =
    Bestimmt, wie Anki vorgeht, wenn Sie einen Notiztyp (wie z.B. "Einfach" oder "Lückentext") in Ihre Sammlung importieren, dessen Schema sich geändert hat (Erklärung siehe unten). Falls aktiviert, wird Anki beide Versionen in einer kombinieren, anstatt (wie bisher) beide separat anzulegen. Default-Wert für diese Einstellung: Deaktiviert
    
    Wann hat sich das Schema eines Notiztyps geändert? Wenn bei dem Notiztyp a) Felder oder b) Kartenvorlagen hinzugefügt oder entfernt oder deren Reihenfolge geändert wurde. Gegenbeispiel: Wenn hingegen nur das Styling oder der Inhalt einer Kartenvorlage geändert wurde, stellt dies keine Schema-Änderung dar, sodass diese Einstellung nicht relevant ist. Dann kommt es stattdessen auf die Einstellung "Notizen aktualisieren" an.
    
    Hinweis: Wenn Sie diese Einstellung aktivieren, kann dies ggf. eine Einweg-Synchronisierung erforderlich machen. Zudem werden betroffene Notizen möglicherweise geändert gekennzeichnet werden.
importing-mnemosyne-20-deck-db = Mnemosyne 2.0-Stapel (*.db)
importing-multicharacter-separators-are-not-supported-please = Das Feldtrennzeichen kann nur aus einem einzigen Zeichen bestehen. Ein aus mehreren Zeichen bestehendes Feldtrennzeichen wird nicht unterstützt.
importing-new-deck-will-be-created = Ein neuer Stapel namens „{ $name }“ wird erstellt.
importing-notes-added-from-file = Notizen hinzugefügt aus Datei: { $val }
importing-notes-found-in-file = Notizen gefunden in Datei: { $val }
importing-notes-skipped-as-theyre-already-in = Notizen übersprungen, da sich diese bereits in Ihrer Sammlung befinden: { $val }
importing-notes-skipped-update-due-to-notetype = Notizen nicht aktualisiert, da ihr Notiztyp geändert wurde: { $val }
importing-notes-updated-as-file-had-newer = Notizen aktualisiert, da der importierte Stapel eine neuere Version enthielt: { $val }
importing-include-reviews = Wiederholungsverlauf (falls enthalten) ebenfalls importieren
importing-also-import-progress = Lernfortschritt (falls enthalten) ebenfalls importieren
importing-with-deck-configs = Stapelprofile (falls enthalten) ebenfalls importieren
importing-updates = Vorgehen bei Aktualisierungen
importing-include-reviews-help =
    Falls aktiviert, werden auch der Wiederholungsverlauf und die im Stapel gespeicherten Stapelprofile mitimportiert (vorausgesetzt, diese sind im Stapel vorhanden, weil der Stapelersteller sie mitexportiert hat).
    
    Falls deaktiviert (oder falls kein Lernfortschritt im Stapel vorhanden ist), werden alle Karten unabhängig von dem enthaltenen Lernstatus als neue Karten importiert und das Standard-Stapelprofil Ihrer Sammlung verwendet.
importing-with-deck-configs-help = Wenn aktiviert, werden ggf. auch die vom Autor bereitgestellten Stapelprofile importiert. Anderenfalls wird allen Stapeln das Standard-Stapelprofil zugewiesen.
importing-packaged-anki-deckcollection-apkg-colpkg-zip = Komprimierte Anki-Stapeldatei/Sammlung (*.apkg *.colpkg *.zip)
# the '|' character
importing-pipe = Pipe
# Warning displayed when the csv import preview table is clipped (some columns were hidden)
# $count is intended to be a large number (1000 and above)
importing-preview-truncated =
    { $count ->
        [one] Es wird nur die erste Spalte angezeigt. Sollte dies nicht stimmen, versuchen Sie, das Feldtrennzeichen zu verändern.
       *[other] Es werden nur die ersten { $count } Spalten angezeigt. Sollte dies nicht stimmen, versuchen Sie, das Feldtrennzeichen zu verändern.
    }
importing-rows-had-num1d-fields-expected-num2d = '{ $row }' hat { $found } Felder, erwartet wurden { $expected }
importing-selected-file-was-not-in-utf8 = Die ausgewählte Datei befand sich nicht im UTF-8-Format. Weitere Hinweise dazu finden Sie in der Bedienungsanleitung im Abschnitt »Import«.
importing-semicolon = Semikolon
importing-skipped = Übersprungen
importing-tab = Tab
importing-tag-modified-notes = Geänderten Notizen folgende Schlagwörter hinzufügen:
importing-text-separated-by-tabs-or-semicolons = Durch Tabs oder Semikolons getrennter Text (*)
importing-the-first-field-of-the-note = Das erste Feld des Notiztyps muss zugeordnet werden.
importing-the-provided-file-is-not-a = Die ausgewählte Datei ist keine gültige .apkg-Datei.
importing-this-file-does-not-appear-to = Diese Datei ist wahrscheinlich keine gültige .apkg-Datei. Wenn dieser Fehler bei einer Datei auftritt, die von AnkiWeb heruntergeladen wurde, ist der Download höchstwahrscheinlich fehlgeschlagen. Bitte versuchen Sie es noch einmal. Wenn das Problem weiterhin besteht, versuchen Sie es bitte mit einem anderen Browser.
importing-this-will-delete-your-existing-collection = Hierdurch wird die gesamte derzeitige Sammlung gelöscht und durch die importierte Datei ersetzt. Trotzdem fortfahren?
importing-unable-to-import-from-a-readonly = Import nicht möglich: Die ausgewählte Datei ist schreibgeschützt.
importing-unknown-file-format = Unbekannter Dateityp.
importing-update-existing-notes-when-first-field = Notizen mit übereinstimmendem erstem Feld aktualisieren
importing-updated = Aktualisiert
importing-update-if-newer = Falls neuer
importing-update-always = Immer
importing-update-never = Nie
importing-update-notes = Notizinhalt aktualisieren:
importing-update-notes-help =
    Beeinflusst, wann einzelne in Ihrer Sammlung vorhandene Notizen aktualisiert werden.
    
    Default: "Falls neuer", also wenn die in dem zu importierenden Stapel gefundene Version der Notiz neuer ist als die Version in Ihrer Sammlung.
importing-update-notetypes = Notiztypen aktualisieren:
importing-update-notetypes-help =
    Beeinflusst, wann ein bereits in Ihrer Sammlung vorhandener Notiztyp (wie z.B. "Einfach" oder "Lückentext") aktualisiert wird.
    
    Default: "Falls neuer", also wenn die Version des Notiztyps in dem zu importierenden Stapel neuer ist als die Version in Ihrer Sammlung.
    
    Wichtig: Diese Einstellung ist nur relevant, wenn Sie einen Notiztyp importieren, der bereits in Ihrer Sammlung vorhanden ist, und dessen Schema sich NICHT geändert hat. Eine Schema-Änderung liegt vor, wenn bei der Version in Ihrer Sammlung die Anzahl oder Reihenfolge von Feldern oder Kartentypen anders ist. Wenn das der Fall sein sollte, können Sie hingegen über die Einstellung '{ importing-merge-notetypes }' festlegen, wie Anki beim Import vorgehen soll.
importing-note-added =
    { $count ->
        [one] { $count } Notiz wurde hinzugefügt.
       *[other] { $count } Notizen wurden hinzugefügt.
    }
importing-note-imported =
    { $count ->
        [one] { $count } Notiz wurde importiert.
       *[other] { $count } Notizen wurden importiert.
    }
importing-note-unchanged =
    { $count ->
        [one] { $count } Notiz ist unverändert geblieben.
       *[other] { $count } Notizen sind unverändert geblieben.
    }
importing-note-updated =
    { $count ->
        [one] { $count } Notiz wurde aktualisiert.
       *[other] { $count } Notizen wurden aktualisiert.
    }
importing-processed-media-file =
    { $count ->
        [one] { $count } Mediendatei wurde importiert.
       *[other] { $count } Mediendateien wurden importiert.
    }
importing-importing-file = Datei wird importiert …
importing-extracting = Daten werden extrahiert …
importing-gathering = Daten werden zusammengetragen …
importing-failed-to-import-media-file = Mediendatei konnte nicht importiert werden: { $debugInfo }
importing-processed-notes =
    { $count ->
        [one] { $count } Notiz verarbeitet …
       *[other] { $count } Notizen verarbeitet …
    }
importing-processed-cards =
    { $count ->
        [one] { $count } Karte verarbeitet …
       *[other] { $count } Karten verarbeitet …
    }
importing-existing-notes = Vorhandene Notizen
# "Existing notes: Duplicate" (verb)
importing-duplicate = Duplizieren
# "Existing notes: Preserve" (verb)
importing-preserve = Behalten
# "Existing notes: Update" (verb)
importing-update = Aktualisieren
importing-tag-all-notes = Alle Notizen verschlagworten
importing-tag-updated-notes = Aktualisierten Notizen Schlagwörter hinzufügen
importing-file = Datei
# "Match scope: notetype / notetype and deck". Controls how duplicates are matched.
importing-match-scope = Trefferumfang
# Used with the 'match scope' option
importing-notetype-and-deck = Notiztyp und Stapel
importing-cards-added =
    { $count ->
        [one] { $count } Karte wurde hinzugefügt.
       *[other] { $count } Karten wurden hinzugefügt.
    }
importing-file-empty = Die Datei, die Sie ausgewählt haben, ist leer.
importing-notes-added =
    { $count ->
        [one] { $count } neue Notiz importiert.
       *[other] { $count } neue Notizen importiert.
    }
importing-notes-updated =
    { $count ->
        [one] { $count } Notiz aktualisiert.
       *[other] { $count } Notizen aktualisiert.
    }
importing-existing-notes-skipped =
    { $count ->
        [one] { $count } Notiz war bereits in Ihrer Sammlung vorhanden.
       *[other] { $count } Notizen waren bereits in Ihrer Sammlung vorhanden.
    }
importing-notes-failed =
    { $count ->
        [one] { $count } Notiz konnte nicht importiert werden.
       *[other] { $count } Notizen konnten nicht importiert werden.
    }
importing-conflicting-notes-skipped =
    { $count ->
        [one] { $count } Notiz nicht importiert, da ihr Notiztyp geändert wurde.
       *[other] { $count } Notizen nicht importiert, da ihr Notiztyp geändert wurde.
    }
importing-conflicting-notes-skipped2 =
    { $count ->
        [one] { $count } Notiz nicht importiert, da ihr Notiztyp geändert wurde und die Einstellung '{ importing-merge-notetypes }' nicht aktiviert war.
       *[other] { $count } Notizen nicht importiert, da ihr Notiztyp geändert wurde und die Einstellung '{ importing-merge-notetypes }' nicht aktiviert war.
    }
importing-import-log = Import Log
importing-no-notes-in-file = Keine Notizen in der Datei gefunden.
importing-notes-found-in-file2 =
    { $notes ->
        [one] { $notes } Notiz in der Datei gefunden. Davon:
       *[other] { $notes } Notizen in der Datei gefunden. Davon:
    }
importing-show = Anzeigen
importing-details = Details
importing-status = Status
importing-duplicate-note-added = Doppelt hinzugefügt
importing-added-new-note = Neu hinzugefügt
importing-existing-note-skipped = Übersprungen, da die Notiz in der aktuellen Version bereits in der Sammlung vorhanden ist
importing-note-skipped-update-due-to-notetype = Notiz nicht aktualisiert, da ihr Notiztyp geändert wurde
importing-note-skipped-update-due-to-notetype2 = Notiz nicht aktualisiert, da ihr Notiztyp nach dem letzten Import geändert wurde und die Einstellung "{ importing-merge-notetypes }" deaktiviert ist.
importing-note-updated-as-file-had-newer = Notiz aktualisiert, da der importierte Stapel eine neuere Version enthielt
importing-note-skipped-due-to-missing-notetype = Notiz übersprungen, Notiztyp fehlt
importing-note-skipped-due-to-missing-deck = Notiz übersprungen, Stapel fehlt
importing-note-skipped-due-to-empty-first-field = Notiz übersprungen, erstes Feld ist leer
importing-field-separator-help =
    Das Zeichen, das die verschiedenen Felder in der Textdatei trennt. In der Vorschau können Sie prüfen, ob die Felder korrekt getrennt werden.
    
    Falls das Feldtrennzeichen selbst auch innerhalb eines Feldes vorkommt, muss dieses Feld dem CSV-Standard gemäß in Anführungszeichen gesetzt werden. Tabellenkalkulationen wie LibreOffice erledigen dies automatisch.
    
    Das Feldtrennzeichen kann nicht geändert werden, wenn die Textdatei in einer Kopfzeile die Verwendung eines bestimmten Feldtrennzeichens vorschreibt. Ist keine Kopfzeile vorhanden, versucht Anki, das Trennzeichen automatisch zu erkennen.
importing-allow-html-in-fields-help = Aktivieren Sie diese Einstellung, falls die Datei HTML-Formatierungen enthält wie z.B. "&lt;br&gt;". Falls deaktiviert, werden diese Zeichen in Ihren Karten nicht als Zeilenumbruch angezeigt, sondern buchstäblich als "&lt;br&gt;".
importing-notetype-help =
    Neu importierte Notizen wird dieser Notiztyp zugewiesen und nur bestehende Notizen mit diesem Notiztyp werden aktualisiert.
    
    Sie können auswählen, welche Felder in der Datei welchen Feldern in dem Notiztyp entsprechen, indem Sie das Mapping-Tool verwenden.
importing-deck-help = Importierte Karten werden in diesem Stapel gespeichert.
importing-existing-notes-help =
    Was soll passieren, wenn eine zu importierende Notiz identisch zu einer bereits vorhandenen ist?
    
    - `{ importing-update }`: Die vorhandene Notiz aktualisieren
    - `{ importing-preserve }`: Nichts tun
    - `{ importing-duplicate }`: Neue (zusätzliche) Notiz erstellen
importing-match-scope-help = Nur Notizen mit dem gleichen Notiztyp werden daraufhin überprüft, ob sie bereits in der Sammlung vorhanden sind. Zusätzlich kann die Prüfung darauf beschränkt werden, ob im selben Stapel eine identische Notiz vorhanden ist.
importing-tag-all-notes-help = Diese Schlagwörter werden sowohl den neu importierten als auch den aktualisierten Notizen hinzugefügt.
importing-tag-updated-notes-help = Diese Schlagwörter werden nur den aktualisierten Notizen hinzugefügt.
importing-overview = Überblick

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

importing-importing-collection = Sammlung wird importiert …
importing-unable-to-import-filename = { $filename } kann nicht importiert werden: Dateityp nicht unterstützt
importing-notes-that-could-not-be-imported = Notizen, die nicht importiert werden konnten, weil sich der Notiztyp geändert hat: { $val }
importing-added = Hinzugefügt
importing-pauker-18-lesson-paugz = Pauker 1.8 Lektion (*.pau.gz)
importing-supermemo-xml-export-xml = Supermemo XML-Export (*.xml)
