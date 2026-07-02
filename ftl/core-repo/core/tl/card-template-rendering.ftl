### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Higit na impormasyon
card-template-rendering-front-side-problem = May problema ang front template:
card-template-rendering-back-side-problem = May problema ang back template:
card-template-rendering-browser-front-side-problem = May problema ang front template na browser-specific:
card-template-rendering-browser-back-side-problem = May problema ang back template na browser-specific:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = Nawawala ang '{ $missing }' sa '{ $tag }'
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = Nawawala ang '{ $missing }'
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = Nahanap ang '{ $found }', pero ang '{ $expected }' ang inasahan.
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = Nahanap ang '{ $found }', pero nawawala ang '{ $missing1 }' o ang '{ $missing2 }'
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = Nahanap ang '{ $found }', pero walang field na '{ $field }' ang pangalan.
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = Blangko ang harap ng card na ito.
card-template-rendering-missing-cloze =
    Walang cloze { $number } na nahanap sa card.
    Mag-add ng cloze deletion, o gamitin ang Empty Cards tool.
