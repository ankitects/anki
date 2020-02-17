missing-count = Missing files: {$count}
unused-count = Unused files: {$count}
renamed-count = Renamed files: {$count}
oversize-count = Over 100MB: {$count}
subfolder-count = Subfolders: {$count}

renamed-header = Some files have been renamed for compatibility:
oversize-header = Files over 100MB can not be synced with AnkiWeb.
subfolder-header = Folders inside the media folder are not supported.
missing-header =
  The following files are referenced by cards, but were not found in the media folder:
unused-header =
  The following files were found in the media folder, but do not appear to be used on any cards:

renamed-file = Renamed: {$old} -> {$new}
oversize-file = Over 100MB: {$filename}
subfolder-file = Folder: {$filename}
missing-file = Missing: {$filename}
unused-file = Unused: {$filename}

checked = Checked {$count}...

delete-unused = Delete Unused
delete-unused-confirm = Delete unused media?
files-remaining = {$count ->
    [one] 1 file
    *[other] {$count} files
  } remaining.
delete-unused-complete = {$count ->
    [one] 1 file
    *[other] {$count} files
  } moved to the trash.

render-latex = Render LaTeX
all-latex-rendered = All LaTeX rendered.
