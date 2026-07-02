## Shown at the top of the media check screen

media-check-window-title = Controleer media
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    { $count ->
        [one] Prullenbak: { $count } bestand, { $megs }MB
       *[other] Prullenbak: { $count } bestanden, { $megs }MB
    }
media-check-missing-count = Missende bestanden: { $count }
media-check-oversize-count = Over 100MB
media-check-subfolder-count = Subfolders: { $count }

## Shown at the top of each section

media-check-renamed-header = Sommige bestanden werden hernoemd voor compatibiliteit
media-check-oversize-header = Bestanden over 100MB kunnen niet gesynct worden met AnkiWeb
media-check-subfolder-header = Folders in de media folder worden niet ondersteund
media-check-missing-header = Gebruikt in kaarten maar niet gevonden in media-map:

## Shown once for each file


##


## Progress


## Deleting unused media

media-check-delete-unused-confirm = Ongebruikte media verwijderen?
media-check-files-remaining =
    { $count ->
        [one] { $count } bestand resterend
       *[other] { $count } bestanden resterend
    }
media-check-delete-unused-complete =
    { $count ->
        [one] { $count } bestand naar prullenback verplaatst
       *[other] { $count } bestanden naar prullenbak verplaatst
    }
media-check-trash-emptied = De prullenbak is nu leeg.

## Rendering LaTeX

media-check-all-latex-rendered = Alle LaTeX gerenderd

## Buttons

media-check-delete-unused = Ongebruikte bestanden verwijderen
media-check-render-latex = LaTeX renderen
# button to permanently delete media files from the trash folder
media-check-empty-trash = Prullenbak leegmaken
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Verwijderde ophalen
media-check-check-media-action = Controleer media
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = ontbrekende-media
# add a tag to notes with missing media
media-check-add-tag = Label ontbreekt
