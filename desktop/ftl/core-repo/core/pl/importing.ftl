importing-failed-debug-info = Importowanie nie powiodło się. Informacja diagnostyczna:
importing-aborted = Przerwane: { $val }
importing-added-duplicate-with-first-field = Dodano duplikat z pierwszym polem: { $val }
importing-all-supported-formats = Wszystkie obsługiwane formaty { $val }
importing-allow-html-in-fields = Zezwól na HTML w polach
importing-anki-files-are-from-a-very = Pliki .anki pochodzą z bardzo starej wersji Anki. Możesz zaimportować je używając Anki w wersji 2.0, dostępnej do ściągnięcia ze strony Anki.
importing-anki2-files-are-not-directly-importable = Plików .anki2 nie da się bezpośrednio importować - zaimportuj zamiast tego otrzymany plik .apkg lub .zip.
importing-appeared-twice-in-file = Pojawił się dwa razy w pliku: { $val }
importing-by-default-anki-will-detect-the = Domyślnie Anki wykrywa znak oddzielający pola, jak np. tabulator, dwukropek itp. Jeśli Anki nieprawidłowo wykrywa taki znak, możesz wpisać go tutaj. Użyj \t jako oznaczenie tabulacji.
importing-cannot-merge-notetypes-of-different-kinds =
    Notatki typu Luka nie mogą zostać połączone ze zwykłīmi typami notatek.
    W dalszym ciągu możesz zaimportować plik z wyłączoną opcją  '{ importing-merge-notetypes }'.
importing-change = Zmień
importing-colon = Dwukropek
importing-comma = Przecinek
importing-empty-first-field = Puste pierwsze pole: { $val }
importing-field-separator = Separator pól
importing-field-separator-guessed = Separator pól (wykryty automatycznie)
importing-field-mapping = Odwzorowanie pól
importing-field-of-file-is = Pole <b>{ $val }</b> z pliku jest:
importing-fields-separated-by = Pola oddzielone o: { $val }
importing-file-must-contain-field-column = Plik musi zawierać co najmniej jedną kolumnę, która może zostać przyporządkowana do pola notatki.
importing-file-version-unknown-trying-import-anyway = Podjęto próbę importu mimo nieznanej wersji pliku.
importing-first-field-matched = Pierwsze dopasowane pole: { $val }
importing-identical = Identyczne
importing-ignore-field = Ignoruj pole
importing-ignore-lines-where-first-field-matches = Ignoruj ​​linie, których pierwsze pole pasuje do istniejącej notatki
importing-ignored = <zignorowane>
importing-import-even-if-existing-note-has = Importuj nawet jeśli istniejąca notatka ma takie samo pierwsze pole
importing-import-options = Opcje importowania
importing-importing-complete = Importowanie zakończone.
importing-invalid-file-please-restore-from-backup = Nieprawidłowy plik. Przywróć kopię zapasową.
importing-map-to = Odwzorowanie na { $val }
importing-map-to-tags = Przypisz do tagów
importing-mapped-to = odwzorowane na <b>{ $val }</b>
importing-mapped-to-tags = przypisane do <b>tagów</b>
# the action of combining two existing note types to create a new one
importing-merge-notetypes = Połącz typy notatek
importing-merge-notetypes-help =
    Jeśli zostanie zaznaczone, a schemat typu notatki został zmieniony przez Ciebie lub autora talii, Anki
    połączy obie wersje zamiast zachowywać je oddzielnie.
    
    Zmiana schematu notatki oznacza dodawanie, usuwanie, zmieniane kolejności pól lub szablonów
    lub zmianą pola sortowania.
    Dla kontrprzykładu, zmiana strony przedniej istniejącego szablonu *nie* jest zmianą schematu.
    
    Uwaga: Zmiana ta wymusi synchronizację w jedną stronę i może oznaczyć istniejące notatki jako zmodyfikowane.
importing-mnemosyne-20-deck-db = Talia Mnemosyne 2.0 (*.db)
importing-multicharacter-separators-are-not-supported-please = Wieloznakowe separatory nie są obsługiwane. Wpisz tylko jeden znak.
importing-new-deck-will-be-created = Zostanie utworzona nowa talia: { $name }
importing-notes-added-from-file = Notatki dodane z pliku: { $val }
importing-notes-found-in-file = Notatki znalezione w pliku: { $val }
importing-notes-skipped-as-theyre-already-in = Notatki pominięte, gdyż są już w kolekcji: { $val }
importing-notes-skipped-update-due-to-notetype = Notatki nie zostały zaktualizowane, ponieważ typ notatki został zmodyfikowany od czasu pierwszego zaimportowania notatek: { $val }
importing-notes-updated-as-file-had-newer = Notatki zaktualizowane nowszą wersją z pliku: { $val }
importing-include-reviews = Dołącz powtórki
importing-also-import-progress = importuj postęp nauki
importing-with-deck-configs = Importuj opcje talii
importing-updates = Aktualizacja
importing-include-reviews-help =
    Jeśli opcja jest włączona, zostaną zaimportowane również wszystkie wcześniejsze powtórki
    dołączone przez udostępniającego talię. W przeciwnym razie wszystkie karty zostaną
    zaimportowane jako nowe, a tagi „leech” — oznaczające kartę często zapominaną
    problematyczną — i „marked” zostaną usunięte.
importing-with-deck-configs-help =
    Jeśli zostanie włączone, jakiekolwiek opcje talii ustawione przez udostępniającego również zostaną zaimportowane.
    W przeciwnym razie wszystkie talie otrzymają domyślne opcje.
importing-packaged-anki-deckcollection-apkg-colpkg-zip = Spakowana kolekcja/talia Anki (*.apkg *.colpkg *.zip)
# the '|' character
importing-pipe = Kreska pionowa
# Warning displayed when the csv import preview table is clipped (some columns were hidden)
# $count is intended to be a large number (1000 and above)
importing-preview-truncated =
    { $count ->
        [one] Pokazywane są tylko pierwsze { $count } kolumny. Jeśli wydaje się, że coś jest nie tak, spróbuj zmienić separator pola.
        [few] Pokazywane są tylko pierwsze { $count } kolumny. Jeśli wydaje się, że coś jest nie tak, spróbuj zmienić separator pola.
       *[many] Pokazywanych jest tylko pierwszych { $count } kolumn. Jeśli wydaje się, że coś jest nie tak, spróbuj zmienić separator pola.
    }
importing-rows-had-num1d-fields-expected-num2d = '{ $row }' ma { $found } pól, oczekiwano { $expected }
importing-selected-file-was-not-in-utf8 = Wybrany plik nie używa kodowania UTF-8. Przeczytaj rozdział o imporcie w instrukcji
importing-semicolon = Średnik
importing-skipped = Pominięte
importing-tab = Tabulator
importing-tag-modified-notes = Nadaj tagi zmodyfikowanym notatkom:
importing-text-separated-by-tabs-or-semicolons = Tekst oddzielony tabulacją lub średnikami (*)
importing-the-first-field-of-the-note = Pierwsze pole typu notatki musi być przypisane.
importing-the-provided-file-is-not-a = Podany plik nie jest poprawnym plikiem .apkg.
importing-this-file-does-not-appear-to = Ten plik nie jest prawidłowym plikiem .apkg. Jeżeli ten błąd dotyczy pliku pobranego z AnkiWeb, to możliwe, że nie został on poprawnie pobrany. Spróbuj ponownie i jeżeli problem będzie się powtarzał, spróbuj użyć innej przeglądarki.
importing-this-will-delete-your-existing-collection = Twoja kolekcja zostanie usunięta i zastąpiona danymi z pliku, który importujesz. Chcesz kontynuować?
importing-unable-to-import-from-a-readonly = Nie można zaimportować z pliku tylko do odczytu.
importing-unknown-file-format = Nieznany format pliku.
importing-update-existing-notes-when-first-field = Aktualizuj istniejące notatki jeżeli zgadzają się pierwsze pola
importing-updated = Zaktualizowane
importing-update-if-newer = Jeśli nowsze
importing-update-always = Zawsze
importing-update-never = Nigdy
importing-update-notes = Aktualizacja notatki
importing-update-notes-help =
    Kiedy aktualizować istniejącą już notatkę w twojej kolekcji. Domyślnie dzieje się to tylko wtedy,
    gdy dopasowana notatka ma nowszą datę modyfikacji.
importing-update-notetypes = Aktualizuj typy notatek
importing-update-notetypes-help =
    Kiedy aktualizować istniejący już typ notatki w twojej kolekcji. Domyślnie dzieje się to tylko wtedy,
    gdy dopasowany zaimportowany typ notatki ma nowszą datę modyfikacji. Zmiany w tekście szablonu
    i w stylach zawsze mogą zostać zaimportowane, jednak w przypadku zmian schematu (np. zmieniona liczba lub kolejność pól) należy włączyć opcję "{ importing-merge-notetypes }".
importing-note-added =
    { $count ->
        [one] dodano { $count } notatkę
        [few] dodano { $count } notatki
        [other] dodano { $count } notatek
       *[many] dodano { $count } notatek
    }
importing-note-imported =
    { $count ->
        [one] zaimportowano { $count } notatkę.
        [few] zaimportowano { $count } notatki.
        [other] zaimportowano { $count } notatek.
       *[many] zaimportowano { $count } notatek.
    }
importing-note-unchanged =
    { $count ->
        [one] nie zmieniono { $count } notatki
        [few] nie zmieniono { $count } notatek
        [other] nie zmieniono żadnej notatki
       *[many] nie zmieniono { $count } notatek
    }
importing-note-updated =
    { $count ->
        [one] zaktualizowano { $count } notatkę
        [few] zaktualizowano { $count } notatki
        [other] zaktualizowano { $count } notatek
       *[many] zaktualizowano { $count } notatek
    }
importing-processed-media-file =
    { $count ->
        [one] Przetworzono { $count } plik
        [few] Przetworzono { $count } pliki
        [other] Przetworzono { $count } plików
       *[many] Przetworzono { $count } plików
    }
importing-importing-file = Import pliku...
importing-extracting = Znajdowanie danych...
importing-gathering = Zbieranie danych...
importing-failed-to-import-media-file = Nie udało się zaimportować pliku: { $debugInfo }
importing-processed-notes =
    { $count ->
        [one] Przeprocesowano { $count } notatkę...
        [few] Przeprocesowano { $count } notatki...
       *[many] Przeprocesowano { $count } notatek...
    }
importing-processed-cards =
    { $count ->
        [one] Przeprocesowano { $count } kartę...
        [few] Przeprocesowano { $count } karty...
       *[many] Przeprocesowano { $count } kart...
    }
importing-existing-notes = Istniejące notatki
# "Existing notes: Duplicate" (verb)
importing-duplicate = Duplikuj
# "Existing notes: Preserve" (verb)
importing-preserve = Nie zmieniaj
# "Existing notes: Update" (verb)
importing-update = Uaktualnij
importing-tag-all-notes = Nadaj tag wszystkim notatkom
importing-tag-updated-notes = Nadaj tag uaktualnionym notatkom
importing-file = Plik
# "Match scope: notetype / notetype and deck". Controls how duplicates are matched.
importing-match-scope = Zakres dopasowania
# Used with the 'match scope' option
importing-notetype-and-deck = Typ notatki i talia
importing-cards-added =
    { $count ->
        [one] { $count } karta dodana.
        [few] { $count } karty dodane.
       *[many] { $count } kart dodanych.
    }
importing-file-empty = Wybrany plik jest pusty.
importing-notes-added =
    { $count ->
        [one] Zaimportowano { $count } nową notatkę.
        [few] Zaimportowano { $count } nowe notatki.
       *[many] Zaimportowano { $count } nowych notatek.
    }
importing-notes-updated =
    { $count ->
        [one] { $count } notatka została użyta do zaktualizowania już istniejących.
        [few] { $count } notatki zostały użyte do zaktualizowania już istniejących.
       *[many] { $count } notatek zostało użytych do zaktualizowania już istniejących.
    }
importing-existing-notes-skipped =
    { $count ->
        [one] { $count } notatka już znajdowała się w twojej kolekcji.
        [few] { $count } notatki już znajdowały się w twojej kolekcji.
       *[many] { $count } notatek już znajdowało sie w twojej kolekcji.
    }
importing-notes-failed =
    { $count ->
        [one] { $count } notatka nie mogła zostać zaimportowana.
        [few] { $count } notatki nie mogły zostać zaimportowane.
       *[many] { $count } notatek nie mogło zostać zaimportowanych.
    }
importing-conflicting-notes-skipped =
    { $count ->
        [one] { $count } notatka nie została zaimportowana, ponieważ zmienił się jej typ notatki.
        [few] { $count } notatki nie zostały zaimportowane, ponieważ zmienił się ich typ notatki.
       *[many] { $count } notatek nie zostało zaimportowanych, ponieważ zmienił się ich typ notatki.
    }
importing-conflicting-notes-skipped2 =
    { $count ->
        [one] { $count } notatka nie została zaimportowana, ponieważ zmienił się jej typ notatki oraz nie została włączona opcja "{ importing-merge-notetypes }".
        [few] { $count } notatki nie zostały zaimportowane, ponieważ zmienił się ich typ notatki oraz nie została włączona opcja "{ importing-merge-notetypes }".
       *[many] { $count } notatek nie zostało zaimportowanych, ponieważ zmienił się ich typ notatki oraz nie została włączona opcja "{ importing-merge-notetypes }".
    }
importing-import-log = Rejestr importu
importing-no-notes-in-file = W pliku nie znaleziono notatek.
importing-notes-found-in-file2 =
    { $notes ->
        [one] { $notes } notatka znaleziona w pliku. Z tych:
        [few] { $notes } notatki znalezione w pliku. Z tych:
       *[many] { $notes } notatek znaleziono w pliku. Z tych:
    }
importing-show = Pokaż
importing-details = Szczegóły
importing-status = Status
importing-duplicate-note-added = Dodano duplikat notatki
importing-added-new-note = Dodano nową notatkę
importing-existing-note-skipped = Pominięto notatkę, ponieważ aktualna kopia już jest w kolekcji
importing-note-skipped-update-due-to-notetype = Notatka nie została zaktualizowana, ponieważ typ notatki został zmodyfikowany odkąd notatka była pierwszy raz importowana.
importing-note-skipped-update-due-to-notetype2 = Notatka nie została zaktualizowana, ponieważ typ notatki został zmodyfikowany od czasu kiedy pierwszy raz zaimportowałeś tę notatkę oraz opcja "{ importing-merge-notetypes }" nie była włączona.
importing-note-updated-as-file-had-newer = Zaktualizowano notatkę, ponieważ plik miał nową wersję
importing-note-skipped-due-to-missing-notetype = Pominięto notatkę, ponieważ brakowało typu notatki
importing-note-skipped-due-to-missing-deck = Pominięto notatkę, ponieważ brakowało talii
importing-note-skipped-due-to-empty-first-field = Pominięto notatkę, ponieważ pierwsze pole jest puste
importing-field-separator-help =
    Znak służący do rozdzielana pól w pliku tekstowym. Możesz użyć podglądu, aby sprawdzić czy pola są rozdzielone poprawnie 
    
    Pamiętaj, że jeśli ten znak pojawi się jakimkolwiek polu, pole to musi zawierać cudzysłów na początku i końcu z zgodnie ze standardem CSV. Programy do arkuszy kalkulacyjnych takie jak LibreOffice wykonają tę czynność automatycznie.
importing-allow-html-in-fields-help =
    Włącz tę opcję, jeśli plik zawiera formatowanie HTML np. jeśli plik zawiera ciąg
    '&lt;br&gt;', na karcie tekst przejdzie do nowej linii. Jeśli natomiast ta opcja będzie wyłączona, na karcie pojawi się dosłowny tekst '&lt;br&gt;'.
importing-notetype-help =
    Nowo zaimportowane notatki będą miały ten typ notatki oraz tylko istniejące notatki z tym typem notatki zostaną zaktualizowane.
    Możesz wybrać, które pola w pliku odpowiadają któremu polu w typie notatki używając narzędzia do mapowania.
importing-deck-help = Importowane karty będą umieszczone w tej talii.
importing-existing-notes-help =
    Co robić, gdy zaimportowana notatka jest już w kolekcji.
    
    - `{ importing-update }`: Aktualizuj istniejącą.¶
    - `{ importing-preserve }`: Pomiń.¶
    - `{ importing-duplicate }`: Stwórz nową.
importing-match-scope-help = Tylko istniejące notatki z takim samym typem notatek będą sprawdzane pod względem duplikatów. Można to dodatkowo ograniczyć do notatek z kartami znajdującymi się w tej samej talii.
importing-tag-all-notes-help = Te etykiety zostaną dodane zarówno do nowo zaimportowanych jak i zaktualizowanych notatek.
importing-tag-updated-notes-help = Te tagi zostaną dodane do każdej zaktualizowanej notatki.
importing-overview = Podsumowanie

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

importing-importing-collection = Import kolekcji...
importing-unable-to-import-filename = Nie udało się zaimportować { $filename }: ten typ pliku nie jest obsługiwany
importing-notes-that-could-not-be-imported = Notatki niezaimportowane, gdyż zmienił się typ notatki: { $val }
importing-added = Dodano
importing-pauker-18-lesson-paugz = Lekcja Pauker 1.8 (*.pau.gz)
importing-supermemo-xml-export-xml = Eksport XML Supermemo (*.xml)
