## Shown at the top of the media check screen

media-check-window-title = Check Media
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    Trash folder: { $count ->
        [one] { $count } file, { $megs }MB
       *[other] { $count } files, { $megs }MB
    }
media-check-missing-count = Missing files: { $count }
media-check-unused-count = Unused files: { $count }
media-check-renamed-count = Renamed files: { $count }
media-check-oversize-count = Over 100MB: { $count }
media-check-subfolder-count = Subfolders: { $count }
media-check-extracted-count = Extracted images: { $count }

## Shown at the top of each section

media-check-renamed-header = Some files have been renamed for compatibility:
media-check-oversize-header = Files over 100MB can not be synced with AnkiWeb.
media-check-subfolder-header = Folders inside the media folder are not supported.
media-check-missing-header = The following files are referenced by cards, but were not found in the media folder:
media-check-unused-header = The following files were found in the media folder, but do not appear to be used on any cards:
media-check-template-references-field-header =
    Anki can not detect used files when you use { "{{Field}}" } references in media/LaTeX tags. The media/LaTeX tags should be placed on individual notes instead.
    
    Referencing templates:

## Shown once for each file

media-check-renamed-file = Renamed: { $old } -> { $new }
media-check-oversize-file = Over 100MB: { $filename }
media-check-subfolder-file = Folder: { $filename }
media-check-missing-file = Missing: { $filename }
media-check-unused-file = Unused: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = Checked { $count }...

## Deleting unused media

media-check-delete-unused-confirm = Delete unused media?
media-check-files-remaining =
    { $count ->
        [one] { $count } file
       *[other] { $count } files
    } remaining.
media-check-delete-unused-complete =
    { $count ->
        [one] { $count } file
       *[other] { $count } files
    } moved to the trash.
media-check-trash-emptied = The trash folder is now empty.
media-check-trash-restored = Restored deleted files to the media folder.

## Rendering LaTeX

media-check-all-latex-rendered = All LaTeX rendered.

## Buttons

media-check-delete-unused = Delete Unused
media-check-render-latex = Render LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = Empty Trash
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Restore Deleted
media-check-check-media-action = Check Media
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = missing-media
# add a tag to notes with missing media
media-check-add-tag = Tag Missing
