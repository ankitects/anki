### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = More information
card-template-rendering-front-side-problem = Front template has a problem:
card-template-rendering-back-side-problem = Back template has a problem:
card-template-rendering-browser-front-side-problem = Browser-specific front template has a problem:
card-template-rendering-browser-back-side-problem = Browser-specific back template has a problem:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = Missing '{ $missing }' in '{ $tag }'
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = Missing '{ $missing }'
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = Found '{ $found }', but expected '{ $expected }'
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = Found '{ $found }', but missing '{ $missing1 }' or '{ $missing2 }'
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = Found '{ $found }', but there is no field called '{ $field }'
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = The front of this card is blank.
card-template-rendering-missing-cloze =
    No cloze { $number } found on card.
    Please either add a cloze deletion, or use the Empty Cards tool.
