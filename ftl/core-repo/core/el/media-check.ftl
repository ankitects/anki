## Shown at the top of the media check screen

media-check-window-title = Έλεγχος πολυμέσων
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    Κάδος ανακύκλωσης: { $count ->
        [one] { $count } αρχείο, { $megs }MB
       *[other] { $count } αρχεία, { $megs }ΜΒ
    }
media-check-missing-count = Αρχεία που λείπουν: { $count }
media-check-unused-count = Αχρησιμοποίητα αρχεία: { $count }
media-check-renamed-count = Μετονομασμένα αρχεία: { $count }
media-check-oversize-count = Πάνω από 100MB: { $count }
media-check-subfolder-count = Υποφάκελοι: { $count }
media-check-extracted-count = Εξαχθείσες εικόνες: { $count }

## Shown at the top of each section

media-check-renamed-header = Μερικά αρχεία έχουν μετονομαστεί για συμβατότητα:
media-check-oversize-header = Αρχεία πάνω από 100MB δεν μπορούν να συγχρονιστούν με το AnkiWeb.
media-check-subfolder-header = Φάκελοι μέσα στον φάκελο των πολυμέσων δεν υποστηρίζονται.
media-check-missing-header = Τα ακόλουθα αρχεία αναφέρονται σε κάρτες αλλά δεν βρέθηκαν στον φάκελο πολυμέσων:
media-check-unused-header = Τα ακόλουθα αρχεία βρέθηκαν στον φάκελο πολυμέσων, αλλά δεν εμφανίζονται να χρησιμοποιούνται από καμία κάρτα:

## Shown once for each file

media-check-renamed-file = Μετονομάστηκαν: { $old } -> { $new }
media-check-oversize-file = Πάνω από 100MB: { $filename }
media-check-subfolder-file = Φάκελος: { $filename }
media-check-missing-file = Λείπουν: { $filename }
media-check-unused-file = Αχρησιμοποίητα: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = Ελέγχθηκαν { $count }...

## Deleting unused media

media-check-delete-unused-confirm = Διαγραφή αχρησιμοποίητων πολυμέσων;
media-check-files-remaining =
    { $count ->
        [one] Απομένει { $count } αρχείο.
       *[other] Απομένουν { $count } αρχεία .
    }
media-check-delete-unused-complete =
    { $count ->
        [one] { $count } αρχείο μεταφέρθηκε
       *[other] { $count } αρχεία μεταφέρθηκαν
    } στον κάδο ανακύκλωσης.
media-check-trash-emptied = O κάδος ανακύκλωσης είναι κενός.
media-check-trash-restored = Επαναφορά διαγραμμένων αρχείων στον φάκελο πολυμέσων.

## Rendering LaTeX


## Buttons

media-check-delete-unused = Διαγραφή αχρησιμοποίητων
media-check-render-latex = Μεταφόρτωση LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = Άδειασμα Κάδου Ανακύκλωσης
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Επαναφορά διαγραμμένων
media-check-check-media-action = Έλεγχος πολυμέσων
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = ελλείποντα-πολυμέσα
# add a tag to notes with missing media
media-check-add-tag = Ετικέτα σε ελλείποντα
