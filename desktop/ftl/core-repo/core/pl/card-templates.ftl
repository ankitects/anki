# This word is used by TTS voices instead of the elided part of a cloze.
card-templates-blank = puste
card-templates-changes-will-affect-notes =
    { $count ->
        [one] Poniższe zmiany będą miały wpływ na { $count } notatkę, która używa tego typu karty.
        [few] Poniższe zmiany będą miały wpływ na { $count } notatki, które używają tego typu karty.
        [many] Poniższe zmiany będą miały wpływ na { $count } notatatek, które używają tego typu karty.
       *[other] Poniższe zmiany nie będą miały wpływu na żadną notatkę, ponieważ żadna nie używa tego typu karty.
    }
card-templates-card-type = Typ karty:
card-templates-front-template = Szablon przodu
card-templates-back-template = Szablon tyłu
card-templates-template-styling = Styl
card-templates-front-preview = Podgląd przodu
card-templates-back-preview = Podgląd tyłu
card-templates-preview-box = Podgląd
card-templates-template-box = Szablon
card-templates-sample-cloze = To jest { "{{c1::" }przykładowe{ "}}" } wypełnianie luki.
card-templates-fill-empty = Wypełnij puste pola
card-templates-night-mode = Tryb nocny
# Add "mobile" class to card preview, so the card appears like it would
# on a mobile device.
card-templates-add-mobile-class = Dodaj typ "Mobile"
card-templates-preview-settings = Opcje
card-templates-invalid-template-number = Problem w { $number } szablonie.
card-templates-identical-front = Przednia strona jest identyczna jak szablon karty { $number }.
card-templates-no-front-field = Oczekiwane jest pole do podmienienia na przedniej stronie szablonu karty.
card-templates-missing-cloze = Spodziewane jest użycie '{ "{{" }cloze:Text{ "}}" }' lub czegoś podobnego z przodu i tyłu szablonu karty.
card-templates-extraneous-cloze = "luka": może być użyta tylko w typach notatek z luką
card-templates-see-preview = Wejdź na podgląd, by zobaczyć więcej.
card-templates-field-not-found = Nie znaleziono "{ $field }".
card-templates-changes-saved = Zmiany zapisane.
card-templates-discard-changes = Porzucić zmiany?
card-templates-add-card-type = Dodaj typ karty...
card-templates-anki-couldnt-find-the-line-between = Anki nie było w stanie odnaleźć linijki pomiędzy pytaniem a odpowiedzią. Dopasuj ręcznie szablon, by zamienić pytanie z odpowiedzią.
card-templates-at-least-one-card-type-is = Wymagany jest przynajmniej jeden typ karty.
card-templates-browser-appearance = Wygląd w przeglądarce...
card-templates-card = Karta { $val }
card-templates-card-types-for = Typy kart dla { $val }
card-templates-cloze = Luka { $val }
card-templates-deck-override = Nadpisz talię...
card-templates-copy-info = Skopiuj informacje do schowka
card-templates-delete-the-as-card-type-and = Usunąć typ kart '{ $template }' i jego { $cards }?
card-templates-enter-deck-to-place-new = Wprowadź talię do wprowadzenia nowych kart { $val } lub pozostaw puste pole:
card-templates-enter-new-card-position-1 = Wprowadź nową pozycję karty (1...{ $val }):
card-templates-flip = Odwróć
card-templates-form = Formularz
card-templates-off = (wył)
card-templates-on = (wł)
card-templates-remove-card-type = Usuń typ karty...
card-templates-rename-card-type = Zmień nazwę typu karty...
card-templates-reposition-card-type = Zmień pozycję typu karty...
card-templates-card-count =
    { $count ->
        [one] { $count } karta
        [few] { $count } karty
        [many] { $count } kart
       *[other] { $count } kart
    }
card-templates-this-will-create-card-proceed =
    { $count ->
        [one] Zostanie stworzona { $count } karta. Kontynuować?
        [few] Zostaną stworzone { $count } karty. Kontynuować?
        [many] Zostanie stworzonych { $count } kart. Kontynuować?
       *[other] Nie zostanie stworzona żadna karta. Kontynuować?
    }
card-templates-type-boxes-warning = Szablon karty może zawierać tylko jedno pole wprowadzania.
card-templates-restore-to-default = Przywróć domyślne
card-templates-restore-to-default-confirmation =
    Pola i szablony tego typu notatki zostaną przywrócone do domyślnego
    stanu, usuwając wszelkie dodatkowe pola/szablony i ich zawartość, jak też ustawione style. Kontynuować?
card-templates-restored-to-default = Typ notatki został cofnięty do oryginalnego stanu.
