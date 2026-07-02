database-check-corrupt = Kolekcja jest uszkodzona. Przywróć ją z automatycznej kopii zapasowej.
database-check-rebuilt = Baza danych przebudowana i zoptymalizowana.
database-check-card-properties =
    { $count ->
        [one] Naprawiono { $count } kartę z nieprawidłowymi wartościami.
        [few] Naprawiono { $count } karty z nieprawidłowymi wartościami.
        [many] Naprawiono { $count } kart z nieprawidłowymi wartościami.
       *[other] Nie naprawiono żadnej karty z nieprawidłowymi wartościami.
    }
database-check-card-last-review-time-empty =
    { $count ->
        [one] Dodano ostatni czas powtórki do { $count } karty.
        [few] Dodano ostatni czas powtórki do { $count } kart.
       *[many] Dodano ostatni czas powtórki do { $count } kart.
    }
database-check-missing-templates =
    { $count ->
        [one] Usunięto { $count } kartę z brakującym szablonem.
        [few] Usunięto { $count } karty z brakującym szablonem.
        [many] Usunięto { $count } kart z brakującym szablonem.
       *[other] Nie usunięto żadnej karty z brakującym szablonem.
    }
database-check-field-count =
    { $count ->
        [one] Naprawiono { $count } notatkę ze złą liczbą pól.
        [few] Naprawiono { $count } notatki ze złą liczbą pól.
        [many] Naprawiono { $count } notatek ze złą liczbą pól.
       *[other] Nie naprawiono żadnej notatki ze złą liczbą pól.
    }
database-check-new-card-high-due =
    { $count ->
        [one] Znaleziono { $count } nową kartę przypadającą za 1 000 000 lub więcej dni. Rozważ ponowne ustawienie jej pozycji na ekranie Przeglądu.
        [few] Znaleziono { $count } nowe karty przypadające za 1 000 000 lub więcej dni. Rozważ ponowne ustawienie ich pozycji na ekranie Przeglądu.
        [many] Znaleziono { $count } nowych kart przypadających za 1 000 000 lub więcej dni. Rozważ ponowne ustawienie ich pozycji na ekranie Przeglądu.
       *[other] Nie znaleziono żadnej karty przypadającej za 1 000 000 lub więcej dni. Rozważ ponowne ustawienie pozycji kart na ekranie Przeglądu.
    }
database-check-card-missing-note =
    { $count ->
        [one] Usunięto { $count } kartę bez notatki.
        [few] Usunięto { $count } karty bez notatki.
        [many] Usunięto { $count } kart bez notatki.
       *[other] Nie usunięto żadnej karty bez notatki.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] Usunięto { $count } kartę ze zduplikowanym szablonem.
        [few] Usunięto { $count } karty ze zduplikowanym szablonem.
        [many] Usunięto { $count } kart ze zduplikowanym szablonem.
       *[other] Nie usunięto żadnej karty ze zduplikowanym szablonem.
    }
database-check-missing-decks =
    { $count ->
        [one] Naprawiono { $count } brakującą talię.
        [few] Naprawiono { $count } brakujące talie.
        [many] Naprawiono { $count } brakujących talii.
       *[other] Nie naprawiono żadnej brakujących talii.
    }
database-check-revlog-properties =
    { $count ->
        [one] Naprawiono { $count } wpis powtórki z niewłaściwymi właściwościami.
        [few] Naprawiono { $count } wpisy powtórki z niewłaściwymi właściwościami.
        [many] Naprawiono { $count } wpisów powtórki z niewłaściwymi właściwościami.
       *[other] Nie naprawiono żadnego wpisu powtórek z niewłaściwymi właściwościami.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] Naprawiono { $count } notatkę z nieprawidłowymi znakami utf8.
        [few] Naprawiono { $count } notatki z nieprawidłowymi znakami utf8.
        [many] Naprawiono { $count } notatek z nieprawidłowymi znakami utf8.
       *[other] Nie naprawiono żadnej notatki z nieprawidłowymi znakami utf8.
    }
database-check-fixed-invalid-ids =
    { $count ->
        [one] Naprawiono { $count } obiekt z datą w przyszłości.
        [few] Naprawiono { $count } obiekty z datą w przyszłości.
       *[many] Naprawiono { $count } obiekty z datą w przyszłości.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Brakuje jednego lub więcej typów notatek. Notatkom, które je używały nadano nowe typy notatek, zaczynające się od "db-check", jednak nazwy pól oraz wygląd kart został utracony, więc lepszą opcją może być odtworzenie automatycznej kopii zapasowej.

## Progress info

database-check-checking-integrity = Sprawdzanie kolekcji...
database-check-rebuilding = Przebudowywanie....
database-check-checking-cards = Sprawdzanie kart...
database-check-checking-notes = Sprawdzanie notatek...
database-check-checking-history = Sprawdzanie historii...
database-check-title = Sprawdź bazę danych
