database-check-corrupt = Kolekce je poškozena. Nahlédněte prosím do manuálu.
database-check-rebuilt = Databáze zrekonstruována a optimalizována.
database-check-card-properties =
    { $count ->
        [one] Opravena { $count } karta s nesprávnými vlastnostmi.
        [few] Opraveny { $count } karty s nesprávnými vlastnostmi.
        [many] Opraveno { $count } karty s nesprávnými vlastnostmi.
       *[other] Opraveno { $count } karet s nesprávnými vlastnostmi.
    }
database-check-card-last-review-time-empty =
    { $count ->
        [one] Přidán čas posledního opakování k { $count } kartě.
        [few] Přidán čas posledního opakování ke { $count } kartám.
        [many] { "" }
       *[other] Přidán čas posledního opakování k { $count } kartám.
    }
database-check-missing-templates =
    { $count ->
        [one] Odstraněna { $count } karta s chybějící šablonou.
        [few] Odstraněny { $count } karty s chybějící šablonou.
        [many] Odstraněno { $count } karet s chybějící šablonou.
       *[other] Odstraněno { $count } karet s chybějící šablonou.
    }
database-check-field-count =
    { $count ->
        [one] Opravena { $count } poznámka se špatným počtem polí.
        [few] Opraveny { $count } poznámky se špatným počtem polí.
       *[other] Opraveno { $count } poznámek se špatným počtem polí.
    }
database-check-new-card-high-due =
    { $count ->
        [one] Nalezena { $count } nová karta s číslem ke zkoušení >= 1,000,000 - zvažte změnu pořadí na obrazovce Prohlížet.
        [few] Nalezeny { $count } nové karty s číslem ke zkoušení >= 1,000,000 - zvažte změnu pořadí na obrazovce Prohlížet.
       *[other] Nalezeno { $count } nových karet s číslem ke zkoušení >= 1,000,000 - zvažte změnu pořadí na obrazovce Prohlížet.
    }
database-check-card-missing-note =
    { $count ->
        [one] Odstraněna { $count } karta s chybějící poznámkou.
        [few] Odstraněny { $count } karty s chybějící poznámkou.
        [many] Odstraněno { $count } karet s chybějící poznámkou.
       *[other] Odstraněno { $count } karet s chybějící poznámkou.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] Odstraněna { $count } karta s duplicitní šablonou.
        [few] Odstraněny { $count } karty s duplicitní šablonou.
       *[other] Odstraněno { $count } karet s duplicitní šablonou.
    }
database-check-missing-decks =
    { $count ->
        [one] Opraven { $count } chybějící balíček.
        [few] Opraveny { $count } chybějící balíčky.
       *[other] Opraveno { $count } chybějících balíčků.
    }
database-check-revlog-properties =
    { $count ->
        [one] Opraven { $count } zápis opakování s nesprávnými vlastnostmi.
        [few] Opraveny { $count } zápisy opakování s nesprávnými vlastnostmi.
       *[other] Opraveno { $count } zápisů opakování s nesprávnými vlastnostmi.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] Opravena { $count } poznámka s neplatnými znaky utf8.
        [few] Opraveny { $count } poznámky s neplatnými znaky utf8.
       *[other] Opraveno { $count } poznámek s neplatnými znaky utf8.
    }
database-check-fixed-invalid-ids =
    { $count ->
        [one] Opraven { $count } objekt s časovým razítkem v budoucnosti.
        [few] Opraveny { $count } objekty s časovým razítkem v budoucnosti.
       *[other] Opraveno { $count } objektů s časovým razítkem v budoucnosti.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Jeden nebo více typů poznámek chyběly. Poznámky, které je používaly, dostaly nové typy poznámek, které začínají „db-check“, ale názvy polí a design karty se ztratil, takže možná bude lepší obnovit je z automatické zálohy.

## Progress info

database-check-checking-integrity = Kontroluje se kolekce…
database-check-rebuilding = Obnovuje se...
database-check-checking-cards = Kontrolují se karty…
database-check-checking-notes = Kontrolují se poznámky…
database-check-checking-history = Kontroluje se historie…
database-check-title = Zkontrolovat databázi
