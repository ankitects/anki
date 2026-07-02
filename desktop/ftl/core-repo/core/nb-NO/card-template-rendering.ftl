### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Mer informasjon
card-template-rendering-front-side-problem = Det er et problem med malen til forsiden:
card-template-rendering-back-side-problem = Det er et problem med malen til baksiden:
card-template-rendering-browser-front-side-problem = Den utforsker-spesifikke malen til forsiden har et problem:
card-template-rendering-browser-back-side-problem = Den utforsker-spesifikke malen til baksiden har et problem:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = Manglende '{ $missing }' i '{ $tag }'
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = Manglende '{ $missing }'
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = Fant '{ $found }', men forventet '{ $expected }'
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = Fant '{ $found }', men mangler '{ $missing1 }' eller '{ $missing2 }'
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = Fant '{ $found }', men det er ikke noe felt som heter '{ $field }'
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = Dette kortets forside er tomt.
