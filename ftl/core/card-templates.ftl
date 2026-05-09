# This word is used by TTS voices instead of the elided part of a cloze.
card-templates-blank = blank
card-templates-changes-will-affect-notes =
    { $count ->
        [one] Changes below will affect the { $count } note that uses this card type.
       *[other] Changes below will affect the { $count } notes that use this card type.
    }
card-templates-card-type = Card Type:
card-templates-front-template = Front Template
card-templates-back-template = Back Template
card-templates-template-styling = Styling
card-templates-front-preview = Front Preview
card-templates-back-preview = Back Preview
card-templates-preview-box = Preview
card-templates-template-box = Template
card-templates-sample-cloze = This is a { "{{c1::" }sample{ "}}" } cloze deletion.
card-templates-fill-empty = Fill Empty Fields
card-templates-night-mode = Night Mode
# Add "mobile" class to card preview, so the card appears like it would
# on a mobile device.
card-templates-add-mobile-class = Add Mobile Class
card-templates-preview-settings = Options
card-templates-invalid-template-number = Card template { $number } in note type '{ $notetype }' has a problem.
card-templates-identical-front = The front side is identical to card template { $number }.
card-templates-no-front-field = Expected to find a field replacement on the front of the card template.
card-templates-missing-cloze = Expected to find '{ "{{" }cloze:Text{ "}}" }' or similar on the front and back of the card template.
card-templates-extraneous-cloze = 'cloze:' can only be used on cloze note types.
card-templates-see-preview = See the preview for more information.
card-templates-field-not-found = Field '{ $field }' not found.
card-templates-changes-saved = Changes saved.
card-templates-discard-changes = Discard changes?
card-templates-add-card-type = Add Card Type...
card-templates-anki-couldnt-find-the-line-between = Anki couldn't find the line between the question and answer. Please adjust the template manually to switch the question and answer.
card-templates-at-least-one-card-type-is = At least one card type is required.
card-templates-browser-appearance = Browser Appearance...
card-templates-card = Card { $val }
card-templates-card-types-for = Card Types for { $val }
card-templates-cloze = Cloze { $val }
card-templates-deck-override = Deck Override...
card-templates-copy-info = Copy Info to Clipboard
card-templates-delete-the-as-card-type-and = Delete the '{ $template }' card type, and its { $cards }?
card-templates-enter-deck-to-place-new = Enter deck to place new { $val } cards in, or leave blank:
card-templates-enter-new-card-position-1 = Enter new card position (1...{ $val }):
card-templates-flip = Flip
card-templates-form = Form
card-templates-off = (off)
card-templates-on = (on)
card-templates-remove-card-type = Remove Card Type...
card-templates-rename-card-type = Rename Card Type...
card-templates-reposition-card-type = Reposition Card Type...
card-templates-card-count =
    { $count ->
        [one] { $count } card
       *[other] { $count } cards
    }
card-templates-this-will-create-card-proceed =
    { $count ->
        [one] This will create { $count } card. Proceed?
       *[other] This will create { $count } cards. Proceed?
    }
card-templates-type-boxes-warning = Only one typing box per card template is supported.
card-templates-restore-to-default = Restore to Default
card-templates-restore-to-default-confirmation = This will reset all fields and templates in this note type to their default values, removing any extra fields/templates and their content, and any custom styling. Do you wish to proceed?
card-templates-restored-to-default = Note type has been restored to its original state.

