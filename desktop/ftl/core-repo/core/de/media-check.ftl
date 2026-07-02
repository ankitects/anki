## Shown at the top of the media check screen

media-check-window-title = Medien überprüfen
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    Papierkorb: { $count ->
        [one] 1 Datei, { $megs } MB
       *[other] { $count } Dateien, { $megs } MB
    }
media-check-missing-count = Fehlende Dateien: { $count }
media-check-unused-count = Unbenutzte Dateien: { $count }
media-check-renamed-count = Umbenannte Dateien: { $count }
media-check-oversize-count = Über 100MB: { $count }
media-check-subfolder-count = Unterordner: { $count }
media-check-extracted-count = Extrahierte Bilder: { $count }

## Shown at the top of each section

media-check-renamed-header = Einige Dateien wurden aus Kompatibilitätsgründen umbenannt:
media-check-oversize-header = Dateien mit einer Größe über 100MB können nicht mit AnkiWeb synchronisiert werden.
media-check-subfolder-header = Ordner innerhalb des Medienordners werden nicht unterstützt.
media-check-missing-header = Die folgenden Dateien werden von Karten referenziert, aber konnten nicht im Medienordner gefunden werden
media-check-unused-header = Die folgenden Dateien wurden im Medienordner gefunden, werden aber anscheinend von keiner Karte verwendet:
media-check-template-references-field-header =
    Anki kann keine benutzten Dateien erkennen, wenn Sie { "{{Field}}" }-Verweise in Medien/LaTeX-Tags verwenden. Die Media/LaTeX-Tags sollten stattdessen auf individuellen Notizen platziert werden.
    
    Vorlagen mit entsprechenden Verweisen:

## Shown once for each file

media-check-renamed-file = Umbenannt: { $old } -> { $new }
media-check-oversize-file = Über 100MB: { $filename }
media-check-subfolder-file = Ordner: { $filename }
media-check-missing-file = Fehlend: { $filename }
media-check-unused-file = Unbenutzt: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = { $count } überprüft …

## Deleting unused media

media-check-delete-unused-confirm = Unbenutzte Medien löschen?
media-check-files-remaining =
    { $count ->
        [one] 1 Datei
       *[other] { $count } Dateien
    } verbleibend.
media-check-delete-unused-complete =
    { $count ->
        [one] 1 Datei
       *[other] { $count } files
    } wurde(n) in den Papierkorb verschoben.
media-check-trash-emptied = Der Papierkorb ist jetzt leer.
media-check-trash-restored = Gelöschte Dateien im Medienordner wiederhergestellt.

## Rendering LaTeX

media-check-all-latex-rendered = Sämtlicher LaTeX-Code wurde gerendert.

## Buttons

media-check-delete-unused = Unbenutzte Dateien löschen
media-check-render-latex = LaTeX-Code rendern
# button to permanently delete media files from the trash folder
media-check-empty-trash = Papierkorb leeren
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Gelöschte Dateien wiederherstellen
media-check-check-media-action = Medien überprüfen
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = Fehlende-Medien
# add a tag to notes with missing media
media-check-add-tag = Schlagwort Fehlend
