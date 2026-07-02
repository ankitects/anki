### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Mai multe informații
card-template-rendering-front-side-problem = Șablonul față are o problemă:
card-template-rendering-back-side-problem = Șablonul verso are o problemă:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = Lipsă '{ $missing }' în '{ $tag }'
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = Lipsă '{ $missing }'
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = Găsite '{ $found }', dar așteptarea era pentru '{ $expected }'
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = Găsite '{ $found }', dar lipsesc '{ $missing1 }' sau '{ $missing2 }'
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = Găsite'{ $found }', dar  nu există niciun câmp denumit '{ $field }'
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = Fața acestui card este goală.
card-template-rendering-missing-cloze = Nu s-a găsit nicio selecție de tip ”cloze” pe acest card la numărul { $number }. Te rugăm ori să adaugi o selecție de tip ”cloze”, ori să folosești instrumentul  ”Carduri goale”.
