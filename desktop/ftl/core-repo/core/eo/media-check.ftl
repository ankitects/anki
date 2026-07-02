## Shown at the top of the media check screen

media-check-window-title = Kontroli aŭdvidaĵojn
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    { $count ->
        [one] Rubujo: { $count } dosiero, { $megs } MB
       *[other] Rubujo: { $count } dosieroj, { $megs } MB
    }
media-check-missing-count = Mankaj dosieroj: { $count }
media-check-unused-count = Neuzataj dosieroj: { $count }
media-check-renamed-count = Alinomitaj dosieroj: { $count }
media-check-oversize-count = Dosieroj pli grandaj ol 100 MB: { $count }
media-check-subfolder-count = Subdosierujoj: { $count }
media-check-extracted-count = Elŝiritaj bildoj: { $count }

## Shown at the top of each section

media-check-renamed-header = Alinomis kelkajn dosieroj pro kongrueco:
media-check-oversize-header = Dosieroj pli grandaj ol 100 MB ne povas esti samtempigitaj kun AnkiWeb.
media-check-subfolder-header = Dosieroj ene la aŭdvidaĵa dosierujo ne estas subtenataj.
media-check-missing-header = Dosieroj uzataj de kartoj, sed ne trovitaj en la aŭdvidaĵa dosierujo:
media-check-unused-header = Trovis la jenajn dosierojn en la aŭdvidaĵa dosierujo, kiuj estas uzataj de neniu karto:
media-check-template-references-field-header =
    Anki ne povas eltrovi uzatajn dosierojn, kiam vi uzas en aŭdvidaĵaj/LaTeX etikedoj la referencojn { "{{Field}}" }. La etikedoj aŭdvidaĵaj/LaTeX estu en aparataj notoj.
    
    Rilataj ŝablonoj:

## Shown once for each file

media-check-renamed-file = Alinomis: { $old } → { $new }
media-check-oversize-file = Pli granda ol 100 MB: { $filename }
media-check-subfolder-file = Dosierujo: { $filename }
media-check-missing-file = Mankas: { $filename }
media-check-unused-file = Neuzata: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = Kontrolado de { $count }…

## Deleting unused media

media-check-delete-unused-confirm = Ĉu forigi neuzatajn aŭdvidaĵojn?
media-check-files-remaining =
    { $count ->
        [one] Alinomis { $count } dosieron.
       *[other] Alinomis { $count } dosierojn.
    }
media-check-delete-unused-complete =
    { $count ->
        [one] Movis { $count } dosieron al rubujo.
       *[other] Movis { $count } dosierojn al rubujo.
    }
media-check-trash-emptied = Malplenigis rubujon.
media-check-trash-restored = Restarigis forigitajn dosierojn al la aŭdvidaĵa dosierujo.

## Rendering LaTeX

media-check-all-latex-rendered = Bildigis ĉiujn LaTeX-formulojn.

## Buttons

media-check-delete-unused = Forigi neuzatajn dosierojn
media-check-render-latex = Bildigi LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = Malplenigi rubujon
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Restarigi forigitajn
media-check-check-media-action = Kontroli aŭdovidaĵojn
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = aŭdvidaĵo_mankas
# add a tag to notes with missing media
media-check-add-tag = Etikedi mankajn
