database-check-corrupt = Skedar i dëmtuar. Ju lutem rivendosni nga një kopje rezervë automatike.
database-check-rebuilt = Baza e të dhënave u rindërtua dhe u optimizua.
database-check-card-properties =
    { $count ->
        [one] U ndreq { $count } parametër i gabuar.
       *[other] U ndreqën { $count } parametra të gabuar.
    }
database-check-card-last-review-time-empty =
    { $count ->
        [one] U shtua koha e fundit e rishikimit në { $count } kartë.
       *[other] U shtua koha e fundit e rishikimit në { $count } karta.
    }
database-check-missing-templates =
    { $count ->
        [one] U fshi { $count } kartë pa shabllon.
       *[other] u fshinë { $count } karta pa shabllonë.
    }
database-check-field-count =
    { $count ->
        [one] U ndreq { $count } shënim me numrin e gabuar të fushave.
       *[other] U ndreqën { $count } shënime me numrin e gabuar të fushave.
    }
database-check-new-card-high-due =
    { $count ->
        [one] U gjet { $count } kartë e re me një numër >= 1,000,000 - konsidero ti zhvendosësh ato në faqen e shfletimit.
       *[other] U gjetën { $count } karta të reja me një numër >= 1,000,000 - konsidero ti zhvendosësh ato në faqen e shfletimit.
    }
database-check-card-missing-note =
    { $count ->
        [one] U fshi { $count } kartë pa shënim.
       *[other] U fshinë { $count } karta pa shënim.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] u fshi { $count } kartë me shabllonë të dublikuar.
       *[other] u fshinë { $count } karta me shabllonë të dublikuar.
    }
database-check-missing-decks =
    { $count ->
        [one] U ndreq { $count } pako që mungon.
       *[other] U ndreqën { $count } pako që mungojnë.
    }
database-check-revlog-properties =
    { $count ->
        [one] U ndreq { $count } përsëritje me parametra të gabuar.
       *[other] U ndreqën { $count } përsëritje me parametra të gabuar.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] U ndreq { $count } shënim me shkronja të gabuar në formatin utf8.
       *[other] U ndreqën { $count } shënime me shkronja të gabuar në formatin utf8.
    }
database-check-fixed-invalid-ids =
    { $count ->
        [one] U ndreq { $count } objekt me kohën e shënuar në të ardhmën.
       *[other] U ndreqën { $count } objekte me kohën e shënuar në të ardhmën.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Më shumë se një shënim po mungonin. Shënimet që i përdornin ato u janë dhënë lloj të ri që fillon me "db-check", por emrat e fushave dhe dizajni i kartave është humbur, kështu që më së miri rivendosni nga një kopje rezervë automatike.

## Progress info

database-check-checking-integrity = Duke kontrolluar koleksionin...
database-check-rebuilding = Duke rindërtuar...
database-check-checking-cards = Duke kontrolluar kartat...
database-check-checking-notes = Duke kontrolluar shënimet...
database-check-checking-history = Duke kontrolluar historinë
database-check-title = Kontrollo bazën e të dhënave
