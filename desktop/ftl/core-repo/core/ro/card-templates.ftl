# This word is used by TTS voices instead of the elided part of a cloze.
card-templates-blank = gol
card-templates-changes-will-affect-notes =
    { $count ->
        [one] Modificările de mai jos vor afecta o notiţă care utilizează acest tip de card.
        [few] Modificările de mai jos vor afecta { $count } notiţe  care utilizează acest tip de card.
       *[other] Modificările de mai jos vor afecta { $count } notiţe  care utilizează acest tip de card.
    }
card-templates-card-type = Tipul cardului:
card-templates-front-template = Șablon față
card-templates-back-template = Șablon verso
card-templates-template-styling = Stilizare
card-templates-front-preview = Previzualizare față
card-templates-back-preview = Previzualizare verso
card-templates-preview-box = Previzualizare
card-templates-template-box = Șablon
card-templates-sample-cloze = Acesta este un { "{{c1::" }eșantion{ "}}" } de ascundere tip cloze.
card-templates-fill-empty = Completează câmpuri goale
card-templates-night-mode = Mod ”noapte”
# Add "mobile" class to card preview, so the card appears like it would
# on a mobile device.
card-templates-add-mobile-class = Adaugă clasă ”mobil”
card-templates-preview-settings = Opțiuni
card-templates-invalid-template-number = Șablonul cardului { $number } în tip-notiță '{ $notetype }' are o problemă.
card-templates-identical-front = Fața este identică cu șablonul cardului { $number }.
card-templates-no-front-field = Ar fi trebuit să se găsească un înlocuitor de câmp pe fața șablonului de card.
card-templates-missing-cloze = Ar fi trebuit să se găsească '{ "{{" }cloze:Text{ "}}" }' sau similare pe fața sau verso șablonului de card.
card-templates-extraneous-cloze = 'cloze:' poate fi folosit numai în note tip ”cloze”.
card-templates-see-preview = Vezi previzualizarea pentru mai multe informații.
card-templates-changes-saved = Modificări salvate.
card-templates-discard-changes = Anulezi modificările?
card-templates-add-card-type = Adaugă tip de card...
card-templates-anki-couldnt-find-the-line-between = Anki nu a putut găsi linia dintre întrebare și răspuns. Te rog să ajustezi manual șablonul pentru a comuta între întrebare și răspuns.
card-templates-at-least-one-card-type-is = Este nevoie de cel puțin un tip de card.
card-templates-browser-appearance = Aspectul browserului...
card-templates-card = Card { $val }
card-templates-card-types-for = Tipuri de card pentru { $val }
card-templates-cloze = Test cu cuvinte lipsă (”cloze”)
card-templates-deck-override = Suprascriere pachet...
card-templates-delete-the-as-card-type-and = Ștergi tipul de card '{ $template }' împreună cu { $cards } lui?
card-templates-enter-deck-to-place-new = Introdu pachetul pentru a plasa cele { $val } carduri noi sau lasă-l necompletat:
card-templates-enter-new-card-position-1 = Introdu noua poziție a cardului (1...{ $val }):
card-templates-flip = Inversează
card-templates-form = Formular
card-templates-off = (oprit)
card-templates-on = (pornit)
card-templates-remove-card-type = Elimină tipul de card...
card-templates-rename-card-type = Redenumește tipul de card...
card-templates-reposition-card-type = Repoziționează tipul de card...
card-templates-card-count =
    { $count ->
        [one] { $count } card
        [few] { $count } carduri
       *[other] { $count } carduri
    }
card-templates-this-will-create-card-proceed =
    { $count ->
        [one] Aceasta va crea un card. Continui?
        [few] Aceasta va crea { $count } carduri. Continui?
       *[other] Aceasta va crea { $count } carduri. Continui?
    }
