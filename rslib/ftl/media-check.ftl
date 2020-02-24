## Shown at the top of the media check screen

media-check-missing-count = Missing files: {$count}
media-check-unused-count = Unused files: {$count}
media-check-renamed-count = Renamed files: {$count}
media-check-oversize-count = Over 100MB: {$count}
media-check-subfolder-count = Subfolders: {$count}

## Shown at the top of each section

media-check-renamed-header = Some files have been renamed for compatibility:
media-check-oversize-header = Files over 100MB can not be synced with AnkiWeb.
media-check-subfolder-header = Folders inside the media folder are not supported.
media-check-missing-header =
  The following files are referenced by cards, but were not found in the media folder:
media-check-unused-header =
  The following files were found in the media folder, but do not appear to be used on any cards:

## Shown once for each file

media-check-renamed-file = Renamed: {$old} -> {$new}
media-check-oversize-file = Over 100MB: {$filename}
media-check-subfolder-file = Folder: {$filename}
media-check-missing-file = Missing: {$filename}
media-check-unused-file = Unused: {$filename}

## Progress

media-check-checked = Checked {$count}...

## Deleting unused media

media-check-delete-unused-confirm = Delete unused media?
media-check-files-remaining = {$count ->
    [one] 1 file
    *[other] {$count} files
  } remaining.
media-check-delete-unused-complete = {$count ->
    [one] 1 file
    *[other] {$count} files
  } moved to the trash.

## Rendering LaTeX

media-check-all-latex-rendered = All LaTeX rendered.

## Buttons

media-check-delete-unused = Delete Unused
media-check-render-latex = Render LaTeX
