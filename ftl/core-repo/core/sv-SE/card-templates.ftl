# This word is used by TTS voices instead of the elided part of a cloze.
card-templates-blank = blank
card-templates-changes-will-affect-notes =
    { $count ->
        [one] Ändringar nedan kommer att påverka den { $count } not som använder denna korttyp.
       *[other] Ändringar nedan kommer att påverka de { $count } noter som använder denna korttyp.
    }
card-templates-card-type = Korttyp:
card-templates-front-template = Mall för framsida
card-templates-back-template = Mall för baksida
card-templates-template-styling = Stilmall
card-templates-front-preview = Förhandsvisning av framsida
card-templates-back-preview = Förhandsvisning av baksida
card-templates-preview-box = Förhandsvisning
card-templates-template-box = Mall
card-templates-sample-cloze = Det här är ett { "{{c1::" }exempel{ "}}" } på en lucktext.
card-templates-fill-empty = Fyll tomma fält
card-templates-night-mode = Nattläge
# Add "mobile" class to card preview, so the card appears like it would
# on a mobile device.
card-templates-add-mobile-class = Lägg till mobil-klass
card-templates-preview-settings = Alternativ
card-templates-invalid-template-number = Kortmall { $number } i nottyp "{ $notetype }" har ett problem.
card-templates-identical-front = Framsidan är identisk med kortmall { $number }.
card-templates-no-front-field = Förväntade att hitta en fältersättning på kortmallens framsida.
card-templates-missing-cloze = Förväntade att hitta "{ "{{" }cloze:Text{ "}}" }" eller dylikt på fram- och baksidan av kortmallen.
card-templates-extraneous-cloze = "cloze:" kan endast användas på lucktextsnottyper.
card-templates-see-preview = Se förhandsvisningen för mer information.
card-templates-field-not-found = Fältet '{ $field }' ej hittat
card-templates-changes-saved = Ändringar sparade.
card-templates-discard-changes = Kasta ändringar?
card-templates-add-card-type = Lägg till korttyp
card-templates-anki-couldnt-find-the-line-between = Anki kunde inte hitta linjen mellan frågan och svaret. Anpassa mallen manuellt för att byta plats på frågan och svaret.
card-templates-at-least-one-card-type-is = Åtminstone en korttyp krävs.
card-templates-browser-appearance = Bläddrarutseende
card-templates-card = Kort { $val }
card-templates-card-types-for = Korttyper för { $val }
card-templates-cloze = Lucktext
card-templates-deck-override = Kortlek för detta kort
card-templates-copy-info = Kopiera info till urklipp
card-templates-delete-the-as-card-type-and = Radera korttypen ”{ $template }” och dess { $cards }?
card-templates-enter-deck-to-place-new = Skriv in kortlek att lägga nya { $val }-kort i, eller lämna blankt:
card-templates-enter-new-card-position-1 = Skriv in ny kortposition (1...{ $val }):
card-templates-flip = Vänd
card-templates-form = Formulär
card-templates-off = (av)
card-templates-on = (på)
card-templates-remove-card-type = Ta bort korttyp
card-templates-rename-card-type = Döp om korttyp
card-templates-reposition-card-type = Positionera om korttyp
card-templates-card-count =
    { $count ->
        [one] { $count } kort
       *[other] { $count } kort
    }
card-templates-this-will-create-card-proceed =
    { $count ->
        [one] Detta kommer att skapa { $count } kort. Fortsätt?
       *[other] Detta kommer att skapa { $count } kort. Fortsätt?
    }
card-templates-type-boxes-warning = Endast ett skrivfält per kortmall stöds.
card-templates-restore-to-default = Återställ till förvalt värde
card-templates-restore-to-default-confirmation =
    Detta kommer återställa alla fält och mallar i denna nottyp till deras
    förvalda värden, vilket kommer ta bort alla extra fält/mallar och deras innehåll, samt alla anpassade stilmallar. Önskar du att fortsätta?
card-templates-restored-to-default = Nottyp har återställts till sitt originaltillstånd.
