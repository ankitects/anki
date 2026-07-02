# This word is used by TTS voices instead of the elided part of a cloze.
card-templates-blank = blangko
card-templates-changes-will-affect-notes =
    { $count ->
        [one] Maaapektuhan ng mga sumusunod na pagbabago ang { $count } note na gumagamit ng card type na ito.
       *[other] Maaapektuhan ng mga sumusunod na pagbabago ang { $count } mga note na gumagamit ng card type na ito.
    }
card-templates-card-type = Card Type:
card-templates-front-template = Template sa harap
card-templates-back-template = Back Template
card-templates-template-styling = Styling
card-templates-front-preview = Front Preview
card-templates-back-preview = Back Preview
card-templates-preview-box = Preview
card-templates-template-box = Template
card-templates-sample-cloze = Ito ay isang { "{{c1::" }sample{ "}}" } ng cloze deletion.
card-templates-fill-empty = I-fill lahat ng empty field
card-templates-night-mode = Night Mode
# Add "mobile" class to card preview, so the card appears like it would
# on a mobile device.
card-templates-add-mobile-class = Magdagdag ng Mobile Class
card-templates-preview-settings = Mga Option
card-templates-invalid-template-number = May problema ang card template { $number } sa notetype '{ $notetype }'.
card-templates-identical-front = Ang front side ay identical sa card template { $number }.
card-templates-no-front-field = Inaasahang makahanap ng field replacement sa harap ng card template.
card-templates-missing-cloze = Inaasahang mahanap ang '{ "{{" }cloze:Text{ "}}" }' o katulad sa harap at likod ng card template.
card-templates-extraneous-cloze = Magagamit lang ang 'cloze:' sa mga cloze notetype.
card-templates-see-preview = Tingnan ang preview para sa higit na impormation.
card-templates-field-not-found = Hindi mahanap ang '{ $field }' na field.
card-templates-changes-saved = Na-save ang mga pagbabago.
card-templates-discard-changes = I-discard ang mga pagbabago?
card-templates-add-card-type = Magdagdag ng Card Type...
card-templates-anki-couldnt-find-the-line-between = Hindi mahanap ng Anki ang linya sa pagitan ng question and answer. Paki-adjust nang mano-mano ang template para ma-switch ang mga ito.
card-templates-at-least-one-card-type-is = Atleast isang card type ang required.
card-templates-browser-appearance = Itsura ng Browser...
card-templates-card = Card { $val }
card-templates-card-types-for = Mga Card Type para sa { $val }
card-templates-cloze = Cloze { $val }
card-templates-deck-override = Deck Override...
card-templates-delete-the-as-card-type-and = I-delete ang '{ $template }' card type, at mga { $cards } nito?
card-templates-enter-deck-to-place-new = Mag-enter ng deck para maglagay ng bagong { $val } na mga card, o iwanang blangko:
card-templates-enter-new-card-position-1 = Mag-enter ng bagong card position  (1...{ $val }):
card-templates-flip = Baliktarin
card-templates-form = Form
card-templates-off = (naka-off)
card-templates-on = (naka-on)
card-templates-remove-card-type = Tanggalin ang Card Type...
card-templates-rename-card-type = I-rename ang Card Type...
card-templates-reposition-card-type = Baguhin ng puwesto ang Card Type...
card-templates-card-count =
    { $count ->
        [one] card
       *[other] (na) card
    }
card-templates-this-will-create-card-proceed =
    { $count ->
        [one] Gagawa ito ng { $count } card. Tuloy ba?
       *[other] Gagawa ito ng { $count } (na) card. Tuloy ba?
    }
card-templates-type-boxes-warning = Isang typing box lang ang sinu-support bawat card template.
card-templates-restore-to-default = I-restore sa default
card-templates-restore-to-default-confirmation =
    Iri-reset nito sa default value ang lahat ng field at template sa notetype na ito,
    at matatanggal ang anumang extra field o template at nilalaman ng mga ito, at kahit anong custom styling. Nais mo bang magpatuloy?
card-templates-restored-to-default = Na-restore na ang Notetype original state nito.
