## Shown at the top of the media check screen

media-check-window-title = I-check ang media
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    Folder ng basura: { $count ->
        [one] { $count } file, { $megs }MB
       *[other] { $count } (na) file, { $megs }MB
    }
media-check-missing-count = Nawawalang mga file: { $count }
media-check-unused-count = Di-gamit na mga file: { $count }
media-check-renamed-count = Na-rename na mga file: { $count }
media-check-oversize-count = Higit sa 100MB: { $count }
media-check-subfolder-count = Mga Subfolder: { $count }

## Shown at the top of each section

media-check-renamed-header = Ang ilang file ay ni-rename para maging mas compatible:
media-check-oversize-header = Ang mga file na higit 100MB ay hindi kayang ma-sync sa AnkiWeb.
media-check-subfolder-header = Hindi supported ang mga folder sa loob ng media folder.
media-check-missing-header = Ang mga sumusunod na file ay na-reference ng mga card, pero hindi mahanap sa media folder:
media-check-unused-header = Ang mga sumusunod na file ay nakita sa media folder, pero parang hindi nagagamit sa kahit aling card:
media-check-template-references-field-header =
    Hindi made-detect ng ANki ang mga gamit na file kapag gagamit ka ng { "{{Field}}" } na references sa media/LaTeX tags. Sa halip, ng mga media/LaTeX tag ay dapat na malagay sa mga individual na note.
    
    Mga template sa pagre-reference:

## Shown once for each file

media-check-renamed-file = Na-rename: { $old } -> { $new }
media-check-oversize-file = Higit sa 100MB: { $filename }
media-check-subfolder-file = Folder: { $filename }
media-check-missing-file = Nawawala: { $filename }
media-check-unused-file = Hindi gamít: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = { $count } ang na-check...

## Deleting unused media

media-check-delete-unused-confirm = I-delete ang hindi gamít na media?
media-check-files-remaining =
    { $count ->
        [one] { $count } file
       *[other] { $count } (na) file
    } ang natitira.
media-check-delete-unused-complete =
    { $count ->
        [one] { $count } file
       *[other] { $count } (na) file
    } ang nalipat sa trash.
media-check-trash-emptied = Walang nang laman ngayon ang trash folder.
media-check-trash-restored = Ni-restore ang mga deleted file sa media folder.

## Rendering LaTeX

media-check-all-latex-rendered = Lahat ng LaTeX ay na-render.

## Buttons

media-check-delete-unused = I-delete ang hindi gamít
media-check-render-latex = I-render ang LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = I-empty ang trash
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = I-restore ang deleted
media-check-check-media-action = I-check ang media
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = nawawalang-media
# add a tag to notes with missing media
media-check-add-tag = I-tag ang nawawala
