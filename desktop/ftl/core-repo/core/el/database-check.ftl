database-check-corrupt = Η συλλογή είναι κατεστραμμένη. Παρακαλώ αντικαταστήστε την από ένα αυτόματο αντίγραφο ασφαλείας.
database-check-rebuilt = Η βάση δεδομένων ξαναδημιουργήθηκε και βελτιώθηκε.
database-check-card-properties =
    { $count ->
        [one] Διορθώθηκε { $count } μη έγκυρη ιδιότητα κάρτας.
       *[other] Διορθώθηκαν { $count } μη έγκυρες ιδιότητες κάρτας.
    }
database-check-card-last-review-time-empty =
    { $count ->
        [one] Προστέθηκε η τελευταία ώρα αξιολόγησης σε { $count } κάρτα .
       *[other] Προστέθηκε η τελευταία ώρα αξιολόγησης σε { $count } κάρτες.
    }
database-check-missing-templates =
    { $count ->
        [one] Διαγράφηκε { $count } κάρτα με ελλείπον πρότυπο.
       *[other] Διαγράφηκαν { $count } κάρτες με ελλείπον πρότυπο.
    }
database-check-field-count =
    { $count ->
        [one] Διορθώθηκε μια σημείωση με λανθασμένο αριθμό πεδίων.
       *[other] Διορθώθηκαν { $count } σημείωσεις με λανθασμένο αριθμό πεδίων.
    }
database-check-new-card-high-due =
    { $count ->
        [one] Βρέθηκε { $count } νέα κάρτα με αριθμό προθεσμίας  >= 1,000,000 - θεωρήστε την επανατοποθέτησή της στην οθόνη Περιήγησης.
       *[other] Βρέθηκαν { $count } νέες κάρτες με αριθμό προθεσμίας  >= 1,000,000 - θεωρήστε την επανατοποθέτησή τους στην οθόνη Περιήγησης.
    }
database-check-card-missing-note =
    { $count ->
        [one] Διαγράφηκε { $count } κάρτα με ελλείπουσα σημείωση.
       *[other] Διαγράφηκαν { $count } κάρτες με ελλείπουσα σημείωση.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] Διαγράφηκε { $count } κάρτα με διπλό πρότυπο.
       *[other] Διαγράφηκαν { $count } κάρτες με διπλό πρότυπο.
    }
database-check-missing-decks =
    { $count ->
        [one] Διορθώθηκε { $count } ελλείπουσα τράπουλα.
       *[other] Διορθώθηκαν { $count } ελλείπουσες τράπουλες.
    }
database-check-revlog-properties =
    { $count ->
        [one] Διορθώθηκε { $count } καταχώρηση επανάληψης με μη έγκυρες ιδιότητες.
       *[other] Διορθώθηκαν { $count } καταχώρησεις επανάληψεων με μη έγκυρες ιδιότητες.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] Διορθώθηκε μια σημείωση με μη έγκυρους utf8 χαρακτήρες.
       *[other] Διορθώθηκαν { $count } σημειώσεις με μη έγκυρους utf8 χαρακτήρες.
    }
database-check-fixed-invalid-ids =
    { $count ->
        [one] Διορθώθηκε { $count } αντικείμενο με χρονικά σημεία στο μέλλον.
       *[other] Διορθώθηκαν { $count } αντικείμενα με χρονικά σημεία στο μέλλον.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Ένας ή περισσότεροι τύποι σημειώσεων λείπουν. Οι σημειώσεις που τους χρησιμοποιούσαν έχουν λάβει νέους τύπους που ξεκινούν με "db-check", αλλά τα ονόματα των πεδίων και ο σχεδιασμός των καρτών έχει χαθεί, οπότε είναι προτιμότερη η αποκατάσταση από ένα αυτόματο αντίγραφο ασφαλείας.

## Progress info

database-check-checking-integrity = Έλεγχος συλλογής...
database-check-rebuilding = Ανακατασκευή...
database-check-checking-cards = Έλεγχος καρτών...
database-check-checking-notes = Έλεγχος σημειώσεων...
database-check-checking-history = Έλεγχος ιστορικού...
database-check-title = Έλεγχος βάσης δεδομένων
