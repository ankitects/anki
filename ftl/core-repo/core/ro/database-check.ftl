database-check-corrupt = Colecția este coruptă. Restaurează dintr-o copie de rezervă automată.
database-check-rebuilt = Bază de date reconstruită și optimizată.
database-check-card-properties =
    { $count ->
        [one] Am remediat { $count } card cu proprietăți nevalide.
        [few] Am remediat { $count } carduri cu proprietăți nevalide.
       *[other] Am remediat { $count } carduri cu proprietăți nevalide.
    }
database-check-missing-templates =
    { $count ->
        [one] A fost șters { $count } card cu șablonul lipsă.
        [few] Au fost șterse { $count } carduri cu șablonul lipsă.
       *[other] Au fost șterse { $count } carduri cu șablonul lipsă.
    }
database-check-field-count =
    { $count ->
        [one] Am remediat { $count } notiţă cu numerotarea gresită a câmpurilor.
        [few] Am remediat { $count } notiţe cu numerotarea gresită a câmpurilor.
       *[other] Am remediat { $count } notiţe cu numerotarea gresită a câmpurilor.
    }
database-check-new-card-high-due =
    { $count ->
        [one] S-a găsit { $count } card nou cu un număr scadent >= 1.000.000 - luați în considerare repoziționarea acestuia în ecranul Răsfoire.
        [few] S-au găsit { $count } carduri noi cu un număr scadent >= 1.000.000 - luați în considerare repoziționarea acestora în ecranul Răsfoire.
       *[other] S-au găsit { $count } carduri noi cu un număr scadent >= 1.000.000 - luați în considerare repoziționarea acestora în ecranul Răsfoire.
    }
database-check-card-missing-note =
    { $count ->
        [one] A fost șters { $count } card cu notiţa lipsă.
        [few] Au fost șterse { $count } carduri cu notiţe lipsă.
       *[other] Au fost șterse { $count } carduri cu notiţe lipsă.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] S-a șters un card cu șablon duplicat.
        [few] S-au șters { $count } carduri cu șablon duplicat.
       *[other] S-au șters { $count } carduri cu șablon duplicat.
    }
database-check-missing-decks =
    { $count ->
        [one] S-a rezolvat lipsa unui pachet.
        [few] S-a remediat lipsa a { $count } pachete.
       *[other] S-a remediat lipsa a { $count } pachete.
    }
database-check-revlog-properties =
    { $count ->
        [one] S-a remediat { $count } intrare de repetat cu proprietăți nevalide.
        [few] S-au remediat { $count } intrări de repetat cu proprietăți nevalide.
       *[other] S-au remediat { $count } intrări de repetat cu proprietăți nevalide.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] S-a remediat { $count } notiţă cu caractere utf8 nevalide.
        [few] S-au remediat { $count } notiţe cu caractere utf8 nevalide.
       *[other] S-au remediat { $count } notiţe cu caractere utf8 nevalide.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Unul sau mai multe tipuri de notiţe lipseau. Notiţele care le-au folosit au primit noi tipuri de notiţe, începând cu „db-check”, dar numele câmpurilor și designul cardului s-au pierdut, așa că ar fi mai bine să restaurezi dintr-o copie de rezervă automată.

## Progress info

database-check-checking-integrity = Verificare colecţie...
database-check-rebuilding = Reconstruire...
database-check-checking-cards = Verificare carduri...
database-check-checking-notes = Verificare notiţe...
database-check-checking-history = Verificare istoric...
database-check-title = Verificare bază de date
