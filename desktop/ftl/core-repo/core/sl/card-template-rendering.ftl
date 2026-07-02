### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Več informacij
card-template-rendering-front-side-problem = Predloga prednje strani kartice ima težavo:
card-template-rendering-back-side-problem = Predloga zadnje strani kartice ima težavo:
card-template-rendering-browser-front-side-problem = Predloga prednje strani kartice ima težavo:
card-template-rendering-browser-back-side-problem = Predloga zadnje strani kartice ima težavo.
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = Manjka '{ $missing }' v '{ $tag }'
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = Manjka '{ $missing }'
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = Najdeno je '{ $found }', pričakovani pa '{ $expected }'
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = Najdeno je '{ $found }', vendar manjka '{ $missing1 }' ali '{ $missing2 }'
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = Najdeno '{ $found }', vendar ni polja z imenom '{ $field }'
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = Prednji del kartice je prazen.
card-template-rendering-missing-cloze =
    Na kartici nismo našli zapore podatka { $number }.
    Prosimo, uporabite orodje za zaporo dela podatka ali uporabite orodje za prazne kartice.
