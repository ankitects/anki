database-check-corrupt = Die Sammlung ist beschädigt. Bitte führen Sie eine Wiederherstellung aus einer Sicherungskopie durch.
database-check-rebuilt = Datenbank neu generiert und optimiert.
database-check-card-properties =
    { $count ->
        [one] Ungültige Eigenschaften bei { $count } Karte korrigiert.
       *[other] Ungültige Eigenschaften bei { $count } Karten korrigiert.
    }
database-check-card-last-review-time-empty =
    { $count ->
        [one] Das Datum der letzten Wiederholung wurde bei { $count } Karte für die direkte Abfrage verfügbar gemacht.
       *[other] Das Datum der letzten Wiederholung wurde bei { $count } Karten für die direkte Abfrage verfügbar gemacht.
    }
database-check-missing-templates =
    { $count ->
        [one] { $count } Karte ohne Vorlage wurde gelöscht.
       *[other] { $count } Karten ohne Vorlage wurden gelöscht.
    }
database-check-field-count =
    { $count ->
        [one] { $count } Notiz mit falscher Anzahl von Feldern wurde gelöscht.
       *[other] { $count } Notizen mit falscher Anzahl von Feldern wurden gelöscht.
    }
database-check-new-card-high-due =
    { $count ->
        [one] Es wurde { $count } neue Karte mit einer Positionsnummer >= 1.000.000 gefunden. Bitte ziehen Sie eine Änderung der Positionsnummer dieser Karte in der Kartenverwaltung in Betracht.
       *[other] Es wurden { $count } neue Karten mit einer Positionsnummer >= 1.000.000 gefunden. Bitte ziehen Sie eine Änderung der Positionsnummern dieser Karten in der Kartenverwaltung in Betracht.
    }
database-check-card-missing-note =
    { $count ->
        [one] { $count } Karte ohne zugehörige Notiz wurde gelöscht.
       *[other] { $count } Karten ohne zugehörige Notiz wurden gelöscht.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] { $count } Karte mit doppelt vorhandener Vorlage wurde gelöscht.
       *[other] { $count } Karten mit doppelt vorhandener Vorlage wurden gelöscht.
    }
database-check-missing-decks =
    { $count ->
        [one] { $count } fehlender Stapel wurde repariert.
       *[other] { $count } fehlende Stapel wurden repariert.
    }
database-check-revlog-properties =
    { $count ->
        [one] { $count } Wiederholungseintrag mit ungültigen Eigenschaften wurde repariert.
       *[other] { $count } Wiederholungseinträge mit ungültigen Eigenschaften wurden repariert.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] { $count } Notiz mit ungültigen UTF-8-Zeichen wurden repariert.
       *[other] { $count } Notizen mit ungültigen UTF-8-Zeichen wurden repariert.
    }
database-check-fixed-invalid-ids =
    { $count ->
        [one] { $count } Objekt mit Zeitstempel in der Zukunft wurde repariert.
       *[other] { $count } Objekten mit Zeitstempel in der Zukunft wurden repariert.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Eine oder mehrere Notiztypen fehlten. Den Notizen, die diese genutzt haben, wurden neue Notizentypen beginnend mit „db-check“ zugewiesen, aber Feldnamen und Kartendesign sind verloren gegangen. Es könnte besser sein, eine Sicherungskopie zu nutzen.

## Progress info

database-check-checking-integrity = Sammlung wird überprüft …
database-check-rebuilding = Es wird neu aufgebaut …
database-check-checking-cards = Karten werden überprüft …
database-check-checking-notes = Notizen werden überprüft …
database-check-checking-history = Verlauf wird überprüft …
database-check-title = Datenbank überprüfen
