### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    { $decks ->
        [one] Χρησιμοποιείται από { $decks } τράπουλα
       *[other] Χρησιμοποιείται από { $decks } τράπουλες
    }
deck-config-default-name = Προεπιλογή
deck-config-title = Επιλογές τράπουλας

## Daily limits section

deck-config-daily-limits = Ημερήσια όρια
deck-config-new-limit-tooltip =
    Ο μέγιστος αριθμός καρτών που εισάγετε σε μια μέρα, αν νέες κάρτες είναι διαθέσιμες.
    Επειδή το νέο υλικό θα αυξήσει τον βραχυπρόθεσμο όγκο εργασίας, θα πρέπει τυπικά να είναι τουλάχιστον 10x μικρότερο από το όριο επαναλήψεων.
deck-config-review-limit-tooltip =
    Ο μέγιστος αριθμός καρτών που εμφανίζονται προς επανάληψη ανά ημέρα, 
    όταν οι κάρτες είναι έτοιμες για επανάληψη.
deck-config-limit-deck-v3 =
    Όταν μελετάτε μια τράπουλα που έχει υποτράπουλες μέσα της, τα όρια που έχουν τεθεί σε κάθε
    υποτράπουλα ελέγχουν τον μέγιστο αριθμό καρτών που συγκεντρώνεται από αυτή τη συγκεκριμένη τράπουλα.
    Τα όρια της επιλεγμένης τράπουλας ελέγχουν το σύνολο των καρτών που θα εμφανιστούν.
deck-config-limit-new-bound-by-reviews = Το όριο των επαναλήψεων επηρεάζει το όριο των νέων καρτών. Για παράδειγμα, αν το όριο επαναλήψεων είναι 200 και έχετε 190 κάρτες προς επανάληψη, τότε έως και 10 νέες κάρτες μπορούν να εισαχθούν. Αν έχετε φτάσει τό όριο επαναλήψεων, καμία νέα κάρτα δεν θα εμφανιστεί.
deck-config-limit-interday-bound-by-reviews = Το όριο επαναλήψεων επηρεάζει επίσης τις ενδοημερήσιες κάρτες προς μελέτη. Αν εφαρμόσετε αυτό το όριο, οι ενδοημερήσιες κάρτες για μελέτη θα ληφθούν πρώτες και μετά οι επαναλήψεις.
deck-config-tab-description =
    - `Προεπιλογή`: Το όριο μοιράζεται μεταξύ των τραπουλών που χρησιμοποιούν αυτή την προεπιλογή.
    - `Αυτή η τράπουλα`: Το όριο είναι ειδικά για αυτή την τράπουλα.
    - `Μόνο σήμερα`: Προσωρινή αλλαγή του ορίου αυτής της τράπουλας.
deck-config-new-cards-ignore-review-limit = Οι νέες κάρτες αγνοούν το όριο επαναλήψεων
deck-config-new-cards-ignore-review-limit-tooltip = Από προεπιλογή, το όριο επαναλήψεων εφαρμόζεται και στις νέες κάρτες και καμία νέα κάρτα δεν θα εμφανιστεί όταν έχετε φτάσει το όριο των επαναλήψεων. Αν η επιλογή είναι ενεργοποιημένη, νέες κάρτες θα εμφανιστούν ανεξάρτητα από το όριο επαναλήψεων.
deck-config-apply-all-parent-limits = Τα όρια ξεκινούν από την κορυφή
deck-config-affects-entire-collection = Επηρεάζει ολόκληρη την συλλογή

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Προεπιλογή
deck-config-deck-only = Αυτή η τράπουλα
deck-config-today-only = Σήμερα μόνο

## New Cards section

deck-config-learning-steps = Βήματα εκμάθησης
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Οι καθυστερήσεις είναι τυπικά σε λεπτά (π.χ. '1m') ή μέρες (π.χ. '2d'), αλλά ώρες (π.χ. '1h') ή δευτερόλεπτα (π.χ. '30s') υποστηρίζονται επίσης.
deck-config-learning-steps-tooltip =
    Μία ή περισσότερες καθυστερήσεις, διαχωρισμένες με κενά. Θα χρησιμοποιηθεί η πρώτη καθυστέρηση
    όταν πατάτε το κουμπί «ξανά» σε μια νέα κάρτα, και είναι 1 λεπτό από προεπιλογή.
    Το κουμπί `Καλό` θα προχωρήσει στο επόμενο βήμα, το οποίο είναι 10 λεπτά από προεπιλογή.
    Μόλις περάσουν όλα τα βήματα, η κάρτα θα γίνει κάρτα για επανάληψη και
    θα εμφανιστεί σε διαφορετική ημέρα. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip =
    Ο αριθμός ημερών πριν επανεμφανιστεί η κάρτα, αφού πατηθεί το κουμπί 'Καλά'
    στο τελευταίο βήμα εκμάθησης.
deck-config-easy-interval-tooltip = Ο αριθμός ημερών μέχρι την επανεμφάνιση της κάρτας, αφού πατηθεί το κουμπί 'Εύκολο' που αφαιρεί αμέσως την κάρτα από το στάδιο της εκμάθησης.
deck-config-new-insertion-order = Σειρά εισαγωγής
deck-config-new-insertion-order-tooltip =
    Ελέγχει την θέση (due #) που παίρνουν νέες κάρτες όταν τις προσθέτετε.
    Κάρτες με χαμηλότερο αριθμό due θα εμφανιστούν πρώτες στην μελέτη. Αλλάζοντας την επιλογή θα ανανεώσει αυτόματα την υπάρχουσα θέση νέων καρτών.
deck-config-new-insertion-order-sequential = Διαδοχικά (παλαιότερες κάρτες πρώτα)
deck-config-new-insertion-order-random = Τυχαία
deck-config-new-insertion-order-random-with-v3 = Με τον προγραμματιστή V3, είναι καλύτερο να αφήσετε αυτή την προεπιλογή ως διαδοχική και να προσαρμόσετε την σειρά συγκέντρωσης νέων καρτών.

## Lapses section

deck-config-relearning-steps = Βήματα επανεκμάθησης
deck-config-relearning-steps-tooltip =
    Καμία ή περισσότερες καθυστερήσεις, διαχωρισμένες με κενά. Από προεπιλογή, το πάτημα του πλήκτρου `Ξανά`
    σε μια κάρτα προς επανάληψη θα την εμφανίσει ξανά 10 λεπτά αργότερα. Εάν δεν δίνονται καθυστερήσεις, η κάρτα θα αλλάξει το διάστημά της, χωρίς να εισαχθεί σε στάδιο επανεκμάθησης. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip = Οι φορές που πρέπει να πατηθεί το "Ξανά" σε μια κάρτα προς επανάληψη πριν επισημανθεί ως leech. Οι κάρτες leech είναι κάρτες που καταναλώνουν πολύ από τον χρόνο σας. Όταν μια κάρτα γίνεται leech, καλό θα ήταν να την ξαναγράψετε, διαγράψετε ή να σκεφτείτε ένα μνημονικό που θα σας βοηθήσει να την θυμάστε.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `Ετικέτα μόνο`: Προσθέτει την ετικέτα "leech" σε μία σημείωση και εμφανίζει ένα αναδύομενο παράθυρο.
    `Αναστολή κάρτας`: Εκτός από ετικέτα, κρύβει επίσης την κάρτα μέχρι να γίνει χειροκίνητα αναίρεση της αναστολής.

## Burying section

deck-config-bury-title = Αναβολή
deck-config-bury-new-siblings = Αναβολή νέων ομοειδών
deck-config-bury-review-siblings = Αναβολή ομοειδών επαναλήψεων
deck-config-bury-new-tooltip =
    Αν άλλες "νέες" κάρτες της ίδιας σημείωσης (πχ αντίστροφες κάρτες, κάρτες συμπλήρωσης κενού)
    θα καθυστερήσουν έως την επόμενη μέρα.
deck-config-bury-review-tooltip = Αν άλλες κάρτες προς επανάληψη της ίδια σημείωσης θα καθυστερήσουν έως την επόμενη μέρα.
deck-config-bury-interday-learning-tooltip =
    Αν άλλες κάρτες "προς εκμάθηση" της ίδιας σημείωσης με διαστήματα > 1 μέρα
    θα καθυστερήσουν έως την επόμενη ημέρα.

## Gather order and sort order of cards

deck-config-ordering-title = Σειρά εμφάνισης
deck-config-new-gather-priority = Σειρά συγκέντρωσης νέων καρτών
deck-config-new-card-sort-order = Σειρά ταξινόμησης νέων καρτών
deck-config-new-review-priority = Σειρά νέων/επαναλήψεων
deck-config-new-review-priority-tooltip = Πότε θα εμφανίζονται οι νέες κάρτες σε σχέση με τις επαναλήψεις.
deck-config-interday-step-priority = Ενδοημερήσια σειρά εκμάθησης/επανάληψης
deck-config-review-sort-order = Σειρά ταξινόμησης επαναλήψεων
deck-config-review-sort-order-tooltip = Η προεπιλεγμένη σειρά δίνει προτεραιότητα στις κάρτες που περιμένουν περισσότερο, έτσι ώστε αν έχετε πολλές επαναλήψεις, οι κάρτες που περιμένουν περισσότερο θα εμφανιστούν πρώτα. Αν έχετε μεγάλο όγκο αναμονής που θα χρειαστεί περισσότερες από μερικές ημέρες για να καθαρίσει ή αν θέλετε να βλέπετε τις κάρτες σε σειρά subdecks, ίσως θεωρήσετε προτιμότερες τις εναλλακτικές σειρές ταξινόμησης.
deck-config-display-order-will-use-current-deck = Το Anki θα χρησιμοποιήσει την σειρά προβολής από την τράπουλα που επιλέγετε για διάβασμα και όχι από τις υπο-τράπουλες που μπορεί να έχει.

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = Τράπουλα
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = Τράπουλα, μετά τυχαίες σημειώσεις
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = Αύξουσα θέση
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = Φθίνουσα θέση
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = Τυχαίες σημειώσεις
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = Τυχαίες κάρτες
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = Τύπος κάρτας, μετά τυχαία
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = Τυχαία σημείωση, μετά τύπος κάρτας
# Sort the cards randomly.
deck-config-sort-order-random = Τυχαία
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = Τύπος κάρτας
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = Σειρά συγκέντρωσης
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = Ανάμιξη με επαναλήψεις
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = Εμφάνιση μετά από επαναλήψεις
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = Εμφάνιση πριν από επαναλήψεις
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = Προθεσμία, μετά τυχαία
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = Προθεσμία, μετά τράπουλα
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = Τράπουλα, μετά προθεσμία
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = Αύξοντα διαστήματα
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = Φθίνοντα διαστήματα
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = Αύξουσα ευκολία
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = Φθίνουσα ευκολία
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = Αύξουσα δυσκολία
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = Φθίνουσα δυσκολία
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = Αύξουσα ανακτησιμότητα
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = Φθίνουσα ανακτησιμότητα

## Timer section

deck-config-timer-title = Χρονόμετρο
deck-config-maximum-answer-secs = Μέγιστος χρόνος απάντησης σε δευτερόλεπτα
deck-config-maximum-answer-secs-tooltip =
    Ο μέγιστος αριθμός δευτερολέπτων που καταγράφονται σε μια επανάληψη. Αν η απάντηση υπερβαίνει αυτόν τον χρόνο (επειδή φύγατε για παράδειγμα από την οθόνη),
    ο χρόνος που θα καταγραφεί θα είναι το όριο που θέσατε.
deck-config-show-answer-timer-tooltip =
    Εμφάνιση στην οθόνη επαναλήψεων ενός χρονομετρητή που μετρά τα δευτερόλεπτα που 
    χρειάζεστε για να κάνετε επανάληψη κάθε κάρτας.
deck-config-stop-timer-on-answer = Παύση χρονόμετρου κατά την απάντηση
deck-config-stop-timer-on-answer-tooltip =
    Αν ο χρόνος σταματάει όταν αποκαλύπτεται η απάντηση.
    Δεν επηρεάζει τα στατιστικά.

## Auto Advance section

deck-config-seconds-to-show-question = Δευτερόλεπτα για εμφάνιση ερώτησης
deck-config-seconds-to-show-question-tooltip-3 = Ο αριθμός των δευτερολέπτων πριν την εφαρμογή της ενέργειας ερώτησης, όταν το auto advance είναι ενεργοποιημένο. Θέσετε σε 0 για απενεργοποίηση.
deck-config-seconds-to-show-answer = Δευτερόλεπτα για εμφάνιση απάντησης
deck-config-seconds-to-show-answer-tooltip-2 = Ο αριθμός των δευτερολέπτων πριν την εφαρμογή της ενέργειας απάντησης, όταν το auto advance είναι ενεργοποιημένο. Θέσετε σε 0 για απενεργοποίηση.
deck-config-question-action-show-answer = Προβολή Απάντησης
deck-config-question-action-show-reminder = Εμφάνιση Υπενθύμισης
deck-config-question-action = Ενέργεια κατά την ερώτηση
deck-config-question-action-tool-tip = Η ενέργεια που εκτελείται αφού εμφανιστεί η ερώτηση και παρέλθει ο χρόνος.
deck-config-answer-action = Ενέργεια απάντησης
deck-config-answer-action-tooltip-2 = Η ενέργεια που εκτελείται αφού εμφανιστεί η απάντηση και παρέλθει ο χρόνος.
deck-config-wait-for-audio-tooltip-2 = Αναμονή ολοκλήρωσης του ήχου πριν την αυτόματη εφαρμογή της ενέργειας ερώτησης ή απάντησης.

## Audio section

deck-config-audio-title = Ήχος
deck-config-disable-autoplay = Μη αυτόματη αναπαραγωγή ήχου
deck-config-disable-autoplay-tooltip =
    Όταν ενεργοποιηθεί, το Anki δεν θα παίζει τον ήχο αυτόματα.
    Μπορεί να παίζει χειροκίνητα με το πάτημα ενός εικονιδίου ήχου ή χρησιμοποιώντας την ενέργεια επανέναρξης.
deck-config-skip-question-when-replaying = Παράλειψη της ερώτησης κατά την επανεμφάνιση της απάντησης
deck-config-always-include-question-audio-tooltip = Εάν ο ήχος της ερώτησης θα πρέπει να συμπεριλαμβάνεται όταν η ενέργεια Αναπαραγωγή χρησιμοποιείται ενώ κοιτάτε την πλευρά της απάντησης μιας κάρτας.

## Advanced section

deck-config-advanced-title = Για προχωρημένους
deck-config-maximum-interval-tooltip =
    Ο μέγιστος αριθμός ημερών που θα περιμένει μια κάρτα για επανάληψη. Όταν οι επαναλήψεις έχουν φτάσει το όριο, τα "Δύσκολο", "Καλώς" και "Εύκολο" θα δίνουν ίδια διαστήματα.
    Όσο μικρότερος είναι, τόσο μεγαλύτερος θα είναι ο φόρτος εργασίας.
deck-config-starting-ease-tooltip = Ο πολλαπλασιαστής ευκολίας με τον οποίο ξεκινάνε οι νέες κάρτες. Ως προεπιλογή, το κουμπί "Καλά" σε μία νέα κάρτα που έχετε μελετήσει, θα καθυστερήσει την επόμενη επανάληψη κατά 2.5x σχετικά με το προηγούμενο διάστημα.
deck-config-easy-bonus-tooltip = Ένας πρόσθετος πολλαπλασιαστής πουεφαρμόζεται στο διάστημα επανάληψης μιας κάρτας όταν την αξιολογείτε ως 'Εύκολη'
deck-config-interval-modifier-tooltip = Ο πολλαπλασιαστής εφαρμόζεται σε όλες τις επαναλήψεις και μικρές αλλαγές του μπορούν να χρησιμοποιηθούν για να κάνουν το Anki πιο συντηρητικό ή επιθετικό στον προγραμματισμό του. Παρακαλώ δείτε το εγχειρίδιο πριν αλλάξετε αυτή την ρύθμιση.
deck-config-hard-interval-tooltip = Ο πολλαπλασιαστής που εφαρμόζεται στο διάστημα επανάληψης μετά από απάντηση 'Δύσκολο'.
deck-config-new-interval-tooltip = Ο πολλαπλασιαστής που εφαρμόζεται στο διάστημα επανάληψης μετά από απάντηση 'Ξανά'.
deck-config-minimum-interval-tooltip = Το ελάχιστο διάστημα που δίνεται σε μια κάρτα προς επανάληψη όταν απαντάτε με 'Ξανά'.
deck-config-custom-scheduling = Προσαρμοσμένος προγραμματισμός
deck-config-custom-scheduling-tooltip = Επηρεάζει ολόκληρη την συλλογή. Χρησιμοποιήστε με δική σας ευθύνη!

## Easy Days section.

deck-config-easy-days-title = Εύκολες Ημέρες
deck-config-easy-days-monday = Δευτέρα
deck-config-easy-days-tuesday = Τρίτη
deck-config-easy-days-wednesday = Τετάρτη
deck-config-easy-days-thursday = Πέμπτη
deck-config-easy-days-friday = Παρασκευή
deck-config-easy-days-saturday = Σάββατο
deck-config-easy-days-sunday = Κυριακή
deck-config-easy-days-normal = Κανονικό
deck-config-easy-days-reduced = Μειωμένο
deck-config-easy-days-minimum = Ελάχιστο
deck-config-easy-days-no-normal-days = Τουλάχιστον μία ημέρα θα πρέπει να έχει οριστεί σε '{ deck-config-easy-days-normal }'.
deck-config-easy-days-change = Οι υπάρχουσες επαναλήψεις δεν θα επαναπρογραμματιστούν, εκτός εάν το '{ deck-config-reschedule-cards-on-change }' είναι ενεργοποιημένο στις επιλογές FSRS.

## Adding/renaming

deck-config-add-group = Προσθήκη προεπιλογής
deck-config-name-prompt = Όνομα
deck-config-rename-group = Μετονομασία προεπιλογής
deck-config-clone-group = Κλωνοποίηση προεπιλογής

## Removing

deck-config-remove-group = Αφαίρεση προεπιλογής
deck-config-will-require-full-sync = Η ζητούμενη αλλαγή απαιτεί συγχρονισμό προς μια κατεύθυνση. Αν έχετε κάνει αλλαγές σε άλλη συσκευή και δεν τις έχετε συγχρονίσει ακόμα, παρακαλώ κάντε το πριν συνεχίσετε.
deck-config-confirm-remove-name = Αφαίρεση { $name };

## Other Buttons

deck-config-save-button = Αποθήκευση
deck-config-save-to-all-subdecks = Αποθήκευση σε όλες τις υπο-τράπουλες
deck-config-save-and-optimize = Βελτιστοποίηση όλων των προεπιλογών
deck-config-revert-button-tooltip = Επαναφορά αυτής της ρύθμισης στην προεπιλεγμένη της τιμή.

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Χρήση Anki 2.1.41+
deck-config-description-new-handling-hint =
    Αντιμετωπίζει το input ως markdown και καθαρίζει το HTML input. Όταν είναι ενεργοποιημένο, η
    περιγραφή θα εμφανίζεται επίσης στην οθόνη συγχαρητηρίων.
    Το markdown θα εμφανίζεται ως κείμενο στο Anki 2.1.40 και νεότερες εκδόσεις.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    Η κύρια συλλογή έχει όριο { $cards ->
        [one] { $cards } κάρτας
       *[other] { $cards } καρτών
    }, που θα αντικαταστήσει αυτό το όριο.
deck-config-reviews-too-low =
    Αν προσθέτετε{ $cards ->
        [one] { $cards } νέα κάρτα κάθε μέρα
       *[other] { $cards } νέες κάρτες κάθε μέρα
    }, το όριο επαναλήψεων σας πρέπει να είναι τουλάχιστον { $expected }.
deck-config-good-above-easy = Το εύκολο διάστημα θα πρέπει να είναι τουλάχιστον όσο και το διάστημα αποφοίτησης.
deck-config-relearning-steps-above-minimum-interval = Το ελάχιστο διάστημα παρέλευσης πρέπει να είναι τουλάχιστον όσο και το τελικό βήμα επανεκμάθησης.
deck-config-maximum-answer-secs-above-recommended = Το Anki μπορεί να προγραμματίσει τις επαναλήψεις πιο αποτελεσματικά όταν κρατάτε την κάθε ερώτηση σύντομη.
deck-config-too-short-maximum-interval = Δεν συνιστάται μέγιστο διάστημα μικρότερο των 6 μηνών.
deck-config-ignore-before-info = (Περίπου) { $included }/{ $totalCards } κάρτες θα χρησιμοποιηθούν για τη βελτιστοποίηση των παραμέτρων FSRS.

## Selecting a deck

deck-config-which-deck = Τις ρυθμίσεις ποιας τράπουλας θα θέλατε να εμφανίσετε;

## Messages related to the FSRS scheduler

deck-config-updating-cards = Ενημέρωση καρτών: { $current_cards_count }/{ $total_cards_count }
deck-config-invalid-parameters = Οι παρεχόμενες παράμετροι FSRS δεν είναι έγκυρες. Αφήστε τις κενές για να χρησιμοποιήσετε τις προεπιλεγμένες παραμέτρους.
deck-config-not-enough-history = Το ιστορικό των επαναλήψεων δεν είναι αρκετό για τη διενέργεια αυτής της λειτουργίας.
deck-config-must-have-400-reviews =
    { $count ->
        [one] Μόνο { $count } επανάληψη βρέθηκε. Πρέπει να έχετε τουλάχιστον 400 επαναλήψεις για αυτή την ενέργεια.
       *[other] Μόνο { $count } επαναλήψεις βρέθηκαν. Πρέπει να έχετε τουλάχιστον 400 επαναλήψεις για αυτή την ενέργεια.
    }
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = Παράμετροι FSRS
deck-config-compute-optimal-weights = Βελτιστοποίηση παραμέτρων FSRS
deck-config-optimize-button = Βελτιστοποίηση
# Indicates that a given function or label, provided via the "text" variable, operates slowly.
deck-config-slow-suffix = { $text } (αργό)
deck-config-compute-button = Υπολογισμός
deck-config-ignore-before = Παράλειψη επαναλήψεων πριν
deck-config-time-to-optimize = Έχει περάσει καιρός - συνιστάται η χρήση του κουμπιού Βελτιστοποίηση Όλων.
deck-config-evaluate-button = Εκτίμηση
deck-config-desired-retention = Επιθυμητή ανάκληση
deck-config-historical-retention = Ιστορικό ανάκλησης
deck-config-smaller-is-better = Μικρότεροι αριθμοί υποδεικνύουν καλύτερη ταύτιση με το ιστορικό των επαναλήψεων σας.
deck-config-steps-too-large-for-fsrs = Όταν το FSRS είναι ενεργοποιημένο, βήματα εκμάθησης μίας ή περισσότερων ημερών δεν προτείνονται.
deck-config-get-params = Λήψη παραμέτρων
deck-config-complete = { $num }% ολοκληρώθηκε.
deck-config-iterations = Επανάληψη: { $count }...
deck-config-reschedule-cards-on-change = Επαναπρογραμματισμός καρτών σε αλλαγή
deck-config-fsrs-tooltip =
    Ο προγραμματιστής Free Spaced Repetition Scheduler (FSRS) είναι εναλλακτικός του SuperMemo 2.
    Προσδιορίζει με μεγαλύτερη ακρίβεια τι είναι πιθανότερο να ξεχάσετε και σας βοηθάει να θυμάστε περισσότερο υλικό στην ίδια χρονική περίοδο. Αυτή η ρύθμιση μοιράζεται από όλα τα προκαθορισμένα τραπουλών.
    Αν προηγουμένως χρησιμοποιούσατε την έκδοση 'custom scheduling' του FSRS, παρακαλώ βεβαιωθείτε ότι διαγράψατε την ενότητα του 'custom scheduling' πριν ενεργοποιήσετε αυτή την επιλογή.
deck-config-desired-retention-tooltip =
    Η προεπιλεγμένη τιμή 0.9 θα προγραμματίσει τις κάρτες ώστε να έχετε 90% πιθανότητα ανάκλησης όταν αυτές εμφανίζονται προς επανάληψη. Αν αυξήσετε αυτή την τιμή, το Anki θα εμφανίζει τις κάρτες συχνότερα ώστε να αυξήσει την πιθανότητα να τις θυμηθείτε.
    Αν μειώσετε την τιμή, το Anki θα δείξει τις κάρτες λιγότερο συχνά και θα ξεχάσετε περισσότερες από αυτές. Να είστε συντηρητικοί όταν ρυθμίζετε αυτή την παράμετρο. Υψηλότερες τιμές θα αυξήσουν κατά πολύ τον φόρτο εργασίας σας και μικρότερες τιμές μπορούν να σας αποθαρρύνουν όταν ξεχνάτε πολύ υλικό.
deck-config-desired-retention-tooltip2 = Οι τιμές φόρτου εργασίας που παρέχονται από το εργαλείο συμβουλών είναι μια κατά προσέγγιση εκτίμηση. Για μεγαλύτερη ακρίβεια, χρησιμοποιήστε τον προσομοιωτή.
deck-config-historical-retention-tooltip = Όταν λείπει κάποιο από το ιστορικό των επαναλήψεών σας, το FSRS πρέπει να συμπληρώσει τα κενά. Από προεπιλογή, υποθέτει ότι όταν κάνατε αυτές τις παλιές επαναλήψεις, θυμόσασταν το 90% του υλικού. Εάν η παλιά σας ανάκληση ήταν αισθητά υψηλότερη ή χαμηλότερη από το 90%, η προσαρμογή αυτής της επιλογής θα επιτρέψει στο FSRS να προσεγγίσει καλύτερα τις επαναλήψεις που λείπουν. Το ιστορικό των επαναλήψεών σας μπορεί να είναι ελλιπές για δύο λόγους: 1. Επειδή έχετε χρησιμοποιήσει την επιλογή "αγνόηση επαναλήψεων στο παρελθόν". 2. Επειδή διαγράψατε προηγουμένως τα αρχεία καταγραφής επαναλήψεων για να ελευθερώσετε χώρο ή εισάγετε υλικό από διαφορετικό πρόγραμμα SRS. Το τελευταίο είναι αρκετά σπάνιο, οπότε αν δεν έχετε χρησιμοποιήσει την πρώτη επιλογή, πιθανώς δεν χρειάζεται να προσαρμόσετε αυτή τη ρύθμιση.
deck-config-weights-tooltip2 =
    Οι παράμετροι FSRS επηρεάζουν τον τρόπο προγραμματισμού των καρτών. Το Anki θα ξεκινήσει με προεπιλεγμένες παραμέτρους. Μπορείτε να χρησιμοποιήσετε 
    την παρακάτω επιλογή για να βελτιστοποιήσετε τις παραμέτρους ώστε να ταιριάζουν καλύτερα με την απόδοσή σας στις τράπουλες που χρησιμοποιούν αυτή την προεπιλογή.
deck-config-reschedule-cards-on-change-tooltip =
    Αυτή η επιλογή ελέγχει πόσο συχνά θα αλλάζουν οι ημερομηνίες προγραμματισμού των καρτών αν ενεργοποιήσετε το FSRS ή βελτιστοποιήσετε τις παραμέτρους.
    Η προεπιλογή είναι να μην επαναπρογραμματίζονται οι κάρτες: μελλοντικές επαναλήψεις θα χρησιμοποιούν τον νέο προγραμματισμό, αλλά δεν θα υπάρχει άμεση αλλαγή στον φόρτο εργασίας σας. Αν ο επαναπρογραμματισμός είναι ενεργοποιημένος οι ημερομηνίες θα αλλάξουν.
deck-config-reschedule-cards-warning = Ανάλογα με την επιθυμητή ανάκληση, μπορεί να οδηγήσει σε μεγάλο αριθμό καρτών που θα γίνουν due, οπότε δεν συνιστάται κατά την πρώτη μετάβαση από το SM2. Χρησιμοποιήστε αυτή την επιλογή με φειδώ, καθώς θα προσθέσει μια εγγραφή επανάληψης σε κάθε κάρτα σας και θα αυξήσει το μέγεθος της συλλογής σας.
deck-config-ignore-before-tooltip-2 =
    Εάν οριστεί, οι κάρτες που έχουν γίνει επανάληψη πριν από την προβλεπόμενη ημερομηνία θα αγνοηθούν κατά τη βελτιστοποίηση των παραμέτρων FSRS.
    Αυτό μπορεί να είναι χρήσιμο εάν έχετε εισάγει δεδομένα χρονοπρογραμματισμού κάποιου άλλου ή έχετε αλλάξει τον τρόπο με τον οποίο χρησιμοποιείτε τα κουμπιά απαντήσεων.
deck-config-compute-optimal-weights-tooltip2 =
    Όταν κάνετε κλικ στο κουμπί Βελτιστοποίηση, το FSRS θα αναλύσει το ιστορικό των επαναλήψεών σας και θα δημιουργήσει παραμέτρους που είναι 
    βέλτιστες για τη μνήμη σας και το περιεχόμενο που μελετάτε. Εάν οι τράπουλες σας διαφέρουν αρκετά ως προς την υποκειμενική δυσκολία,  
    συνιστάται να θέσετε ξεχωριστές προεπιλογές, καθώς οι παράμετροι για τις εύκολες  και δύσκολες τράπουλες  θα είναι διαφορετικές. 
    Δεν χρειάζεται να βελτιστοποιείτε συχνά τις παραμέτρους σας - αρκεί μία φορά κάθε μερικούς μήνες.
    
    Από προεπιλογή, οι παράμετροι θα υπολογίζονται από το ιστορικό επαναλήψεων όλων των τραπουλών που χρησιμοποιούν την τρέχουσα προεπιλογή. Μπορείτε να
    προαιρετικά ρυθμίσετε την αναζήτηση πριν από τον υπολογισμό των παραμέτρων, αν θέλετε να αλλάξετε ποιες κάρτες χρησιμοποιούνται για
    βελτιστοποίηση των παραμέτρων.
deck-config-please-save-your-changes-first = Παρακαλώ αποθηκεύσετε πρώτα τις αλλαγές σας.
deck-config-workload-factor-change =
    Κατά προσέγγιση φόρτος εργασίας: { $factor }x
    (σε σύγκριση με { $previousDR }% επιθυμητή ανάκληση)
deck-config-workload-factor-unchanged = Όσο υψηλότερη είναι αυτή η τιμή, τόσο πιο συχνά θα σας εμφανίζονται κάρτες.
deck-config-desired-retention-too-low = Η επιθυμητή ανάκληση είναι πολύ χαμηλή, γεγονός που μπορεί να οδηγήσει σε πολύ μεγάλα διαστήματα.
deck-config-desired-retention-too-high = Η επιθυμητή ανάκληση είναι πολύ υψηλή, γεγονός που μπορεί να οδηγήσει σε πολύ μικρά διαστήματα.
deck-config-percent-of-reviews =
    { $reviews ->
        [one] { $pct }% από { $reviews } επανάληψη
       *[other] { $pct }% από { $reviews } επαναλήψεις
    }
deck-config-percent-input = { $pct }%
# This message appears during FSRS parameter optimization.
deck-config-checking-for-improvement = Έλεγχος για βελτίωση...
deck-config-optimizing-preset = Βελτιστοποίηση προεπιλογής { $current_count }/{ $total_count }...
deck-config-fsrs-must-be-enabled = Το FSRS θα πρέπει να είναι πρώτα ενεργοποιημένο.
deck-config-fsrs-params-optimal = Οι τρέχουσες ρυθμίσεις FSRS είναι βέλτιστες.
deck-config-fsrs-params-no-reviews = Δεν βρέθηκαν επαναλήψεις. Παρακαλούμε ελέγξτε ότι αυτή η προεπιλογή έχει εκχωρηθεί σε όλες τις τράπουλες που θέλετε να βελτιστοποιήσετε (συμπεριλαμβανομένων των υποενοτήτων) και δοκιμάστε ξανά.
deck-config-wait-for-audio = Αναμονή για ήχο
deck-config-show-reminder = Εμφάνιση υπενθύμισης
deck-config-answer-again = Απαντήστε "Ξανά"
deck-config-answer-hard = Απαντήστε "Δύσκολο"
deck-config-answer-good = Απαντήστε "Καλά"
deck-config-days-to-simulate = Ημέρες για προσομοίωση
deck-config-desired-retention-below-optimal = Η επιθυμητή ανάκληση είναι χαμηλότερη της βέλτιστης. Συνιστάται η αύξησή της.
# Description of the y axis in the FSRS simulation
# diagram (Deck options -> FSRS) showing the total number of
# cards that can be recalled or retrieved on a specific date.
deck-config-fsrs-simulator-experimental = Προσομοιωτής FSRS (πειραματικό)
deck-config-fsrs-simulate-desired-retention-experimental = FSRS Προσομοιωτής επιθυμητής ανάκλησης (πειραματικός)
deck-config-fsrs-simulate-save-preset = Μετά την βελτιστοποίηση, αποθηκεύστε τις προεπιλογές της τράπουλάς σας πριν εκτελέσετε τον προσομοιωτή.
deck-config-fsrs-desired-retention-help-me-decide-experimental = Βοήθησέ με να αποφασίσω (Πειραματικό)
deck-config-additional-new-cards-to-simulate = Επιπρόσθετες νέες κάρτες για προσομοίωση
deck-config-simulate = Προσομοίωση
deck-config-clear-last-simulate = Εκκαθάριση προηγούμενης προσομοίωσης
deck-config-fsrs-simulator-radio-count = Επαναλήψεις
deck-config-advanced-settings = Ρυθμίσεις για προχωρημένους
deck-config-smooth-graph = Ομαλό γράφημα
deck-config-save-options-to-preset = Αποθήκευση αλλαγών στην προεπιλογή
deck-config-save-options-to-preset-confirm = Αντικατάσταση των επιλογών της τρέχουσας προεπιλογής με τις επιλογές που έχουν οριστεί στον προσομοιωτή;
# Radio button in the FSRS simulation diagram (Deck options -> FSRS) selecting
# to show the total number of cards that can be recalled or retrieved on a
# specific date.
deck-config-fsrs-simulator-radio-memorized = Απομνημονευμένα
deck-config-fsrs-simulator-radio-ratio = Χρόνος / Αναλογία απομνημόνευσης
# $time here is pre-formatted e.g. "10 Seconds" 
deck-config-fsrs-simulator-ratio-tooltip = { $time } ανά απομνημονευμένη κάρτα

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.

# Checkbox
deck-config-health-check = Έλεγχος υγείας κατά τη βελτιστοποίηση
# Message box showing the result of the health check
deck-config-fsrs-good-fit = Το FSRS είναι καλά προσαρμοσμένο στη μνήμη σας.

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-unable-to-determine-desired-retention = Αδύνατη η εκτίμηση της βέλτιστης ανάκλησης.
deck-config-predicted-minimum-recommended-retention = Ελάχιστη προτεινόμενη ανάκληση: { $num }
deck-config-compute-minimum-recommended-retention = Ελάχιστη προτεινόμενη ανάκληση
deck-config-compute-optimal-retention-tooltip4 =
    Αυτό το εργαλείο θα προσπαθήσει να βρει την επιθυμητή τιμή διατήρησης 
    που θα οδηγήσει στην εκμάθηση της μεγαλύτερης ύλης, στο λιγότερο δυνατό χρονικό διάστημα. Ο υπολογισμένος αριθμός μπορεί να χρησιμεύσει ως σημείο αναφοράς
    όταν αποφασίζετε σε ποια τιμή θα θέσετε την επιθυμητή τιμή διατήρησης. Μπορεί να επιθυμείτε να επιλέξετε μια υψηλότερη επιθυμητή τιμή διατήρησης, εάν είστε 
    πρόθυμοι να επενδύσετε περισσότερο χρόνο μελέτης για να το επιτύχετε. Θέτοντας την επιθυμητή συγκράτησή σας χαμηλότερα από την ελάχιστη
    δεν συνιστάται, καθώς θα οδηγήσει σε υψηλότερο φόρτο εργασίας, λόγω του υψηλού ποσοστού λήθης.
deck-config-plotted-on-x-axis = (Απεικονίζεται στον άξονα Χ)
deck-config-a-100-day-interval =
    { $days ->
        [one] Διάστημα 100 ημερών θα γίνει { $days } ημέρας.
       *[other] Διάστημα 100 ημερών θα γίνει { $days } ημερών.
    }
deck-config-fsrs-simulator-y-axis-title-time = Χρόνος επαναλήψεων/μέρα
deck-config-fsrs-simulator-y-axis-title-count = Αριθμός επαναλήψεων/μέρα
deck-config-fsrs-simulator-y-axis-title-memorized = Σύνολο απομνημονευμένων
deck-config-bury-siblings = Αναβολή ομοειδών
deck-config-seconds-to-show-question-tooltip = Ο αριθμός των δευτερολέπτων αναμονής πριν από την αποκάλυψη της απάντησης, όταν είναι ενεργοποιημένη η αυτόματη προώθηση. Ορίστε το 0 για απενεργοποίηση.
deck-config-answer-action-tooltip = Η ενέργεια που θα εκτελεστεί στην τρέχουσα κάρτα πριν την αυτόματη εμφάνιση της επόμενης.
deck-config-wait-for-audio-tooltip = Αναμονή για την ολοκλήρωση του ήχου πριν την αυτόματη αποκάλυψη της απάντησης ή την επόμενη ερώτηση
deck-config-ignore-before-tooltip =
    Αν ενεργοποιημένο, επαναλήψεις πριν από τη δεδομένη ημερομηνία θα παραλείπονται όταν γίνεται βελτιστοποίηση των παραμέτρων FSRS. 
    Αυτό μπορεί να είναι χρήσιμο όταν έχετε εισάγει δεδομένα κάποιου τρίτου ή έχετε αλλάξει τον τρόπο που χρησιμοποιείτε τα κουμπιά των απαντήσεων.
deck-config-compute-optimal-retention-tooltip =
    Αυτό το εργαλείο υποθέτει ότι ξεκινάτε με 0 κάρτες και επιχειρεί να υπολογίσει την ποσότητα υλικού που θα είστε
    ικανοί να απομνημονεύσετε σε δεδομένο χρονικό διάστημα. Η εκτιμούμενη ανάκληση εξαρτάται από τις εισαγωγές σας και αν διαφέρει κατά πολύ από 0.9 είναι ένα σημάδι ότι ο χρόνος που αφιερώνετε κάθε μέρα είναι είτε λίγος
    ή υψηλός για τον αριθμό καρτών που προσπαθείτε να μάθετε. Αυτός ο αριθμός είναι χρήσιμος σαν reference, αλλά δεν συνίσταται να τον αντιγράφετε στο πεδίο της επιθυμητής ανάκλησης.
deck-config-health-check-tooltip1 = Αυτό θα εμφανίσει μια προειδοποίηση εάν το FSRS δυσκολεύεται να προσαρμοστεί στη μνήμη σας.
deck-config-health-check-tooltip2 = Ο έλεγχος υγείας εκτελείται μόνο όταν χρησιμοποιείτε τη λειτουργία Βελτιστοποίηση τρέχουσας προεπιλογής.
deck-config-compute-optimal-retention = Υπολογισμός βέλτιστης ανάκλησης.
deck-config-predicted-optimal-retention = Εκτιμώμενη βέλτιστη απομνημόνευση: { $num }
deck-config-weights-tooltip =
    Οι παράμετροι FSRS θα επηρεάσουν πως προγραμματίζονται οι κάρτες σας. Το Anki θα ξεκινήσει με τις προεπιλεγμένες παραμέτρους.
    Όταν συγκεντρωθούν 1000+ επαναλήψεις, μπορείτε να χρησιμοποιήσετε την παρακάτω επιλογή για την βελτιστοποίηση των παραμέτρων ώστε να ταιριάζουν καλύτερα στην απόδοσή σας, στις τράπουλες που έχουν αυτό το preset.
deck-config-compute-optimal-weights-tooltip =
    Όταν κάνετε πάνω από 1000 επαναλήψεις στο Anki, μπορείτε να χρησιμοποιήσετε το κουμπί Βελτιστοποίηση για να αναλύσετε το ιστορικό των επαναλήψεων,
    και αυτόματα να δημιουργήσετε τις παραμέτρους που είναι βέλτιστες για την μνήμη σας και το περιεχόμενο που διαβάζετε.
    Αν έχετε τράπουλες που διαφέρουν πολύ σε δυσκολία, είναι προτεινόμενο να αναθέσετε ένα διαφορετικό προκαθορισμένο για καθεμία, καθώς οι παράμετροι για εύκολες τράπουλες και για δύσκολες τράπουλες είναι διαφορετικοί. Δεν υπάρχει ανάγκη συχνής βελτιστοποίησης των παραμέτρων - μια φορά κάθε λίγους μήνες είναι αρκετή.
deck-config-compute-optimal-retention-tooltip2 = Αυτό το εργαλείο υποθέτει ότι ξεκινάτε με 0 κάρτες  και θα προσπαθήσει να βρει την επιθυμητή τιμή ανάκλησης που θα οδηγήσει στην εκμάθηση περισσότερου υλικού σε λιγότερο χρόνο. Αυτός ο αριθμός μπορεί να χρησιμοποιηθεί ως σημείο αναφοράς όταν αποφασίζετε σε ποια τιμή θα θέσετε την επιθυμητή ανάκληση. Μπορεί να θέλετε να επιλέξετε μια υψηλότερη επιθυμητή τιμή ανάκλησης, αν είστε πρόθυμοι να ανταλλάξετε περισσότερο χρόνο μελέτης με μεγαλύτερο ποσοστό ανάκλησης. Ο καθορισμός της επιθυμητής ανάκλησης χαμηλότερα από το βέλτιστο δεν συνιστάται, καθώς θα οδηγήσει σε περισσότερη εργασία χωρίς όφελος.
deck-config-compute-optimal-retention-tooltip3 =
    Αυτό το εργαλείο θεωρεί ότι ξεκινάτε με 0 κάρτες που έχουν μελετηθεί και θα προσπαθήσει να βρει την επιθυμητή τιμή ανάκλησης
    που θα οδηγήσει στην εκμάθηση περισσότερου υλικού, στο λιγότερο δυνατό χρονικό διάστημα. Για να προσομοιώσει με ακρίβεια τη διαδικασία εκμάθησής σας, 
    αυτή η λειτουργία απαιτεί τουλάχιστον 400+ επαναλήψεις. Ο υπολογισμένος αριθμός μπορεί να χρησιμεύσει ως σημείο αναφοράς όταν αποφασίζετε τι να 
    να ορίσετε την επιθυμητή τιμή ανάκλησης. Μπορεί να επιθυμείτε να επιλέξετε μια υψηλότερη επιθυμητή ανάκληση, αν είστε διατεθειμένοι να ανταλλάξετε περισσότερο  
    χρόνο μελέτης για ένα μεγαλύτερο ποσοστό ανάκλησης. Δεν συνιστάται ο καθορισμός της επιθυμητής ανάκλησης χαμηλότερα από το ελάχιστο όριο, καθώς θα 
    οδηγήσει σε μεγαλύτερο φόρτο εργασίας, λόγω του υψηλού ποσοστού λήθης.
deck-config-seconds-to-show-question-tooltip-2 = Ο αριθμός των δευτερολέπτων αναμονής πριν από την αποκάλυψη της απάντησης, όταν είναι ενεργοποιημένη η αυτόματη προώθηση. Ορίστε το 0 για απενεργοποίηση.
deck-config-invalid-weights = Οι παράμετροι πρέπει να είναι κενοί για χρήση των προεπιλογών ή θα πρέπει να είναι 17 αριθμοί διαχωρισμένοι με κόμμα.
deck-config-fsrs-on-all-clients =
    Παρακαλώ βεβαιωθείτε ότι η εκδόση Anki είναι 23.10+ και αυτή του AnkiDroid 2.17+.
    Το FSRS δεν λειτουργεί σωστά εάν οι εκδόσεις είναι παλαιότερες.
deck-config-optimize-all-tip = Μπορείτε να βελτιστοποιήσετε όλες τις προεπιλογές ταυτόχρονα πατώντας το κουμπί πάνω.
