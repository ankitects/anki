## Shown at the top of the media check screen

media-check-window-title = Zkontrolovat multimédia
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    Koš: { $count ->
        [one] 1 soubor, { $megs } MB
        [few] { $count } soubory, { $megs } MB
       *[other] { $count } souborů, { $megs } MB
    }
media-check-missing-count = Chybějící soubory: { $count }
media-check-unused-count = Nepoužívané soubory: { $count }
media-check-renamed-count = Přejmenované soubory: { $count }
media-check-oversize-count = Přes 100 MB: { $count }
media-check-subfolder-count = Podadresářů: { $count }
media-check-extracted-count = Extrahované obrázky: { $count }

## Shown at the top of each section

media-check-renamed-header = Některé soubory byly přejmenovány kvůli kompatibilitě:
media-check-oversize-header = Soubory větší jak 100 MB nelze synchronizovat s AnkiWeb.
media-check-subfolder-header = Složky uvnitř složky s multimédii nejsou podporovány.
media-check-missing-header = Následující soubory jsou použity na kartách, ale nebyly nalezeny ve složce s multimédii:
media-check-unused-header = Následující soubory byly nalezeny ve složce s multimédii, ale nezdá se, že jsou použity na kartách:
media-check-template-references-field-header =
    Anki nemůže detekovat použité soubory, když používáte { "{{Field}}" } odkazy ve značkách media/LaTeX. Značky media/LaTeX by se místo toho měly umístit na jednotlivé poznámky.
    
    Šablony s odkazy:

## Shown once for each file

media-check-renamed-file = Přejmenováno: { $old } -> { $new }
media-check-oversize-file = Přes 100 MB: { $filename }
media-check-subfolder-file = Složka: { $filename }
media-check-missing-file = Chybějící: { $filename }
media-check-unused-file = Nepoužívané: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = Zkontrolováno { $count }...

## Deleting unused media

media-check-delete-unused-confirm = Odstranit nepoužívaná multimédia?
media-check-files-remaining =
    { $count ->
        [one] 1 soubor zbývá.
        [few] { $count } soubory zbývají.
       *[other] { $count } souborů zbývá.
    }
media-check-delete-unused-complete =
    { $count ->
        [one] 1 soubor přesunut
        [few] { $count } soubory přesunuty
       *[other] { $count } souborů přesunuto
    } do koše.
media-check-trash-emptied = Koš je nyní prázdný.
media-check-trash-restored = Odstraněné soubory byly obnoveny do složky s multimédii.

## Rendering LaTeX

media-check-all-latex-rendered = Všechen LaTeX vyrenderován.

## Buttons

media-check-delete-unused = Odstranit nepoužívané
media-check-render-latex = Renderovat LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = Vysypat koš
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Obnovit odstraněné
media-check-check-media-action = Zkontrolovat multimédia
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = chybějící-multimédia
# add a tag to notes with missing media
media-check-add-tag = Označit chybějící
