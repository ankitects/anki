database-check-corrupt = Corrupt itong collection file. Paki-restore mula sa isang automatic backup.
database-check-rebuilt = Napabuti't na-rebuild ang Database.
database-check-card-properties =
    { $count ->
        [one] Naayos ang { $count } invalid card property.
       *[other] Naayos ang { $count } (na) invalid card property.
    }
database-check-missing-templates =
    { $count ->
        [one] Na-delete ang { $count } card na may nawawalang template.
       *[other] Na-delete ang { $count } (na) card may nawawalang template.
    }
database-check-field-count =
    { $count ->
        [one] Naayos ang { $count } note na merong maling field count.
       *[other] Naayos ang { $count } (na) ntoe na merong maling field count.
    }
database-check-new-card-high-due =
    { $count ->
        [one] Nahanap ang { $count } bagong card na may due number na >= 1,000,000 - ikonsidera na ilipat ito sa Browse screen.
       *[other] Nahanap ang { $count } (na) bagong card na may due number na >= 1,000,000 - ikonsidera na ilipat ito sa Browse screen.
    }
database-check-card-missing-note =
    { $count ->
        [one] Na-delete ang { $count } card na may nawawalang note.
       *[other] Na-delete ang { $count } (na) card na may nawawalang note.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] Na-delete ang { $count } card na may duplicate template.
       *[other] Na-delete ang { $count } (na) card na may dupliate template.
    }
database-check-missing-decks =
    { $count ->
        [one] Naayos ang { $count } nawawalang deck.
       *[other] Naayos ang { $count } na nawawalang deck.
    }
database-check-revlog-properties =
    { $count ->
        [one] Naayos ang { $count } review entry na may invalid properties.
       *[other] Naayos ang { $count } (na) review entry na may invalid properties.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] Naayos ang { $count } note na may invalid na utf8 characters.
       *[other] Naayos ang { $count } (na) note na may invalid na utf8 characters.
    }
database-check-fixed-invalid-ids =
    { $count ->
        [one] Naayos ang { $count } object na may mga timestamp sa future.
       *[other] Naayos ang { $count } (na) object na may mga timestamp sa future.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Isa o higit na mga notetype ang nawawala. Ang mga note na gumamit nito ay nabigyan ng mga bagong notetype na nagsisimula sa "db-check", pero ang mga field name at card design ay nawala, kaya't mas mabuti na mag-restore na lang mula sa automatic backup.

## Progress info

database-check-checking-integrity = Tsine-check ang collection...
database-check-rebuilding = Nire-rebuild...
database-check-checking-cards = Sinusuri ang mga card...
database-check-checking-notes = Sinusuri ang mga note...
database-check-checking-history = Sinusuri ang kasaysayan...
database-check-title = Suriin ang database
