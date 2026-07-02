database-check-corrupt = A gyűjtemény sérült. Állítsd vissza egy automatikus biztonsági mentésből!
database-check-rebuilt = Adatbázis újraépítve és optimalizálva.
database-check-card-properties =
    { $count ->
        [one] { $count } érvénytelen tulajdonságú kártya javítva.
       *[other] { $count } érvénytelen tulajdonságú kártya javítva.
    }
database-check-card-last-review-time-empty =
    { $count ->
       *[other] Utolsó ismétlés hozzáadva { $count } kártyához
    }
database-check-missing-templates =
    { $count ->
       *[other] { $count } sablon nélküli kártya törölve.
    }
database-check-field-count =
    { $count ->
       *[other] { $count } hibás mezőszámú jegyzet kijavítva.
    }
database-check-new-card-high-due =
    { $count ->
        [one] { $count } új kártya esedékes sorszáma nagyobb mint 1.000.000 — érdemes lehet átsorolni a kártyát a Böngészés képernyőn.
       *[other] { $count } új kártya esedékes sorszáma nagyobb mint 1.000.000 — érdemes lehet átsorolni a kártyákat a Böngészés képernyőn.
    }
database-check-card-missing-note =
    { $count ->
       *[other] { $count } jegyzet nélküli kártya törölve
    }
database-check-duplicate-card-ords =
    { $count ->
       *[other] { $count } duplikált sablonú kártya törölve.
    }
database-check-missing-decks =
    { $count ->
       *[other] { $count } hiányzó pakli kijavítva.
    }
database-check-revlog-properties =
    { $count ->
       *[other] { $count } érvénytelen tulajdonságokkal rendelkező áttekintési bejegyzés javítva.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
       *[other] { $count } érvénytelen utf-8 karaktert tartalmazó jegyzet javítva.
    }
database-check-fixed-invalid-ids =
    { $count ->
       *[other] { $count } jövőbeni időbélyegű objektum javítva.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Egy vagy több jegyzettípus hiányzott. Az ezeket használó jegyzetek új, "db-check" kezdetű jegyzettípusokat kaptak, de a mezőnevek és a kártyakialakítás elveszett. Érdemes lehet inkább automatikus biztonsági mentésből visszaállítani.

## Progress info

database-check-checking-integrity = Gyűjtemény ellenőrzése...
database-check-rebuilding = Újjáépítés...
database-check-checking-cards = Kártyák ellenőrzése...
database-check-checking-notes = Jegyzetek ellenőrzése...
database-check-checking-history = Előzmények ellenőrzése...
database-check-title = Adatbázis ellenőrzése
