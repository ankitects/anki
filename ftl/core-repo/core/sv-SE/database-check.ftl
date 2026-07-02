database-check-corrupt = Samlingen är korrupt. Var god se manualen.
database-check-rebuilt = Databasen återuppbyggd och optimerad.
database-check-card-properties =
    { $count ->
        [one] Åtgärdade { $count } kort med ogiltiga egenskaper
       *[other] Åtgärdade { $count } kort med ogiltiga egenskaper
    }
database-check-card-last-review-time-empty =
    { $count ->
        [one] Senaste repetitionstid lades till { $count } kort.
       *[other] Senaste repetitionstid lades till { $count } kort.
    }
database-check-missing-templates =
    { $count ->
        [one] Tog bort { $count } kort som saknade mall.
       *[other] Tog bort { $count } kort som saknade mallar.
    }
database-check-field-count =
    { $count ->
        [one] Fixade { $count } not med fel fältantal.
       *[other] Fixade { $count } noter med fel fältantal.
    }
database-check-new-card-high-due =
    { $count ->
        [one] Hittade { $count } nytt kort med ett förfallonummer >= 1 000 000. Överväg att positionera om det i Bläddra-skärmen.
       *[other] Hittade { $count } nya kort med ett förfallonummer >= 1 000 000. Överväg att positionera om dem i Bläddra-skärmen.
    }
database-check-card-missing-note =
    { $count ->
        [one] Tog bort { $count } kort utan not.
       *[other] Tog bort { $count } kort utan noter.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] Tog bort { $count } med dublettmall.
       *[other] Tog bort { $count } med dublettmallar.
    }
database-check-missing-decks =
    { $count ->
        [one] Fixade { $count } saknad kortlek.
       *[other] Fixade { $count } saknad kortlekar.
    }
database-check-revlog-properties =
    { $count ->
        [one] Fixade { $count } repetition med ogiltiga egenskaper.
       *[other] Fixade { $count } repetitioner med ogiltiga egenskaper.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] Fixade { $count } not med ogiltiga utf8-karaktärer.
       *[other] Fixade { $count } noter med ogiltiga utf8-karaktärer.
    }
database-check-fixed-invalid-ids =
    { $count ->
        [one] Fixade { $count } objekt med tidsstämplar i framtiden.
       *[other] Fixade { $count } objekt med tidsstämplar i framtiden.
    }
# "db-check" is always in English
database-check-notetypes-recovered = En eller fler nottyper saknades. Noterna som använde dessa har nu tilldelats nya nottyper börjandes med "db-check", men fältnamnen och kortutseendet har gått förlorade, så det kan vara värt att återställa från en automatisk säkerhetskopia.

## Progress info

database-check-checking-integrity = Kontrollerar samling ...
database-check-rebuilding = Återskapar ...
database-check-checking-cards = Kontrollerar kort ...
database-check-checking-notes = Kontrollerar noter ...
database-check-checking-history = Kontrollerar historik ...
database-check-title = Kontrollera databas
