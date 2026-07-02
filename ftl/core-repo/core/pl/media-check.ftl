## Shown at the top of the media check screen

media-check-window-title = Sprawdź pliki
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    Kosz: { $count ->
        [one] 1 plik, { $megs }MB
        [few] { $count } pliki, { $megs }MB
        [many] { $count } plików, { $megs }MB
       *[other] { $count } plików, { $megs }MB
    }
media-check-missing-count = Brakujące pliki:
media-check-unused-count = Nieużywane pliki:
media-check-renamed-count = Zmieniono nazwę: { $count }
media-check-oversize-count = Ponad 100MB: { $count }
media-check-subfolder-count = Podfoldery: { $count }
media-check-extracted-count = Znalezione grafiki: { $count }

## Shown at the top of each section

media-check-renamed-header = Nazwy niektórych plików zostały zmienione, aby zapewnić kompatybilność:
media-check-oversize-header = Plików ważących ponad 100MB nie można zsynchronizować z AnkiWeb.
media-check-subfolder-header = Foldery w folderze plików nie są wspierane.
media-check-missing-header = Użyte w kartach, ale brakujące w folderze z plikami:
media-check-unused-header = Znaleziono następujące pliki w folderze plików, które nie są używane w żadnych kartach:
media-check-template-references-field-header =
    Anki nie może wykryć używanych plików, jeśli w tagach media/LaTeX zostały zastosowane
    odwołania w formacie { "{{Field}}" }. Tagów media/LaTeX należy używać dla pojedynczych
    notatek.
    
    Szablony referencyjne:

## Shown once for each file

media-check-renamed-file = Zmieniono nazwę: { $old } -> { $new }
media-check-oversize-file = Ponad 100MB: { $filename }
media-check-subfolder-file = Folder: { $filename }
media-check-missing-file = Brakuje: { $filename }
media-check-unused-file = Nieużywany: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = Sprawdzono { $count }...

## Deleting unused media

media-check-delete-unused-confirm = Usunąć nieużywane pliki?
media-check-files-remaining =
    { $count ->
        [one] Pozostał { $count } plik.
        [few] Pozostały { $count } pliki.
        [many] Pozostało { $count } plików.
       *[other] Pozostało { $count } plików.
    }
media-check-delete-unused-complete =
    { $count ->
        [one] { $count } plik został przeniesiony do kosza.
        [few] { $count } pliki zostały przeniesione do kosza.
        [many] { $count } plików zostało przeniesionych do kosza.
       *[other] Żaden plik nie został przeniesiony do kosza.
    }
media-check-trash-emptied = Kosz opróżniony
media-check-trash-restored = Przywrócono usunięte pliki do folderu plików.

## Rendering LaTeX

media-check-all-latex-rendered = Wszystkie wyrenderowane w LaTeX.

## Buttons

media-check-delete-unused = Usuń nieużywane pliki
media-check-render-latex = Renderuj LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = Opróżnij kosz
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Przywróć usunięte
media-check-check-media-action = Sprawdź pliki
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = brak-pliku
# add a tag to notes with missing media
media-check-add-tag = Brakujący tag
