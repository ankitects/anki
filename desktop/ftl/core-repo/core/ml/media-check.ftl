## Shown at the top of the media check screen

media-check-window-title = മീഡിയ പരിശോധിക്കുക
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    ട്രാഷ് ഫോൾഡർ:{ $count ->
        [one] { $count } ഫയൽ, { $megs }MB
       *[other] { $count } ഫയലുകൾ, { $megs }MB
    }
media-check-missing-count = നഷ്‌ടമായ ഫയലുകൾ: { $count }
media-check-unused-count = ഉപയോഗിക്കാത്ത ഫയലുകൾ: { $count }
media-check-renamed-count = പേരുമാറ്റിയ ഫയലുകൾ: { $count }
media-check-oversize-count = 100MB- യിൽ കൂടുതൽ: { $count }
media-check-subfolder-count = ഉപഫോൾഡറുകൾ: { $count }

## Shown at the top of each section

media-check-renamed-header = അനുയോജ്യതയ്ക്കായി ചില ഫയലുകളുടെ പേരുമാറ്റി:
media-check-oversize-header = 100MB- യിൽ കൂടുതലുള്ള ഫയലുകൾ AnkiWeb-മായി സമന്വയിപ്പിക്കാൻ കഴിയില്ല.
media-check-subfolder-header = മീഡിയ ഫോൾഡറിനുള്ളിലെ ഫോൾഡറുകൾ പിന്തുണയ്‌ക്കുന്നില്ല.
media-check-missing-header = ഇനിപ്പറയുന്ന ഫയലുകൾ കാർഡുകൾ ഉപയോഗിച്ച് പരാമർശിക്കുന്നു, പക്ഷേ മീഡിയ ഫോൾഡറിൽ കണ്ടെത്തിയില്ല:
media-check-unused-header = ഇനിപ്പറയുന്ന ഫയലുകൾ മീഡിയ ഫോൾഡറിൽ കണ്ടെത്തി, പക്ഷേ ഒരു കാർഡിലും ഉപയോഗിക്കുന്നതായി തോന്നുന്നില്ല:

## Shown once for each file

media-check-renamed-file = പേരുമാറ്റി: { $old } -> { $new }
media-check-oversize-file = 100MB- യിൽ കൂടുതൽ: { $filename }
media-check-subfolder-file = ഫോൾഡർ: { $filename }
media-check-missing-file = കാണുന്നില്ല: { $filename }
media-check-unused-file = ഉപയോഗിക്കാത്തത്: { $filename }

## Progress

media-check-checked = പരിശോധിച്ചത് { $count }...

## Deleting unused media

media-check-delete-unused-confirm = ഉപയോഗിക്കാത്ത മീഡിയ ഇല്ലാതാക്കണോ?
media-check-files-remaining =
    { $count ->
        [one] { $count } ഫയൽ
       *[other] { $count } ഫയലുകൾ
    } ശേഷിക്കുന്നു.
media-check-delete-unused-complete =
    { $count ->
        [one] { $count } ഫയൽ
       *[other] { $count } ഫയലുകൾ
    } ട്രാഷിലേക്ക് നീക്കി.
media-check-trash-emptied = ട്രാഷ് ഫോൾഡർ ഇപ്പോൾ ശൂന്യമാണ്.
media-check-trash-restored = ഇല്ലാതാക്കിയ ഫയലുകൾ മീഡിയ ഫോൾഡറിലേക്ക് പുനഃസ്ഥാപിച്ചു.

## Rendering LaTeX

media-check-all-latex-rendered = എല്ലാ LaTeX-ഉം റെൻഡർ ചെയ്‌തു.

## Buttons

media-check-delete-unused = ഉപയോഗിക്കാത്തവ ഇല്ലാതാക്കുക
media-check-render-latex = LaTeX റെൻഡർ ചെയ്യുക
# button to permanently delete media files from the trash folder
media-check-empty-trash = ട്രാഷ് ശൂന്യമാക്കുക
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = ഇല്ലാതാക്കിയത് പുനഃസ്ഥാപിക്കുക
media-check-check-media-action = മീഡിയ പരിശോധിക്കുക
