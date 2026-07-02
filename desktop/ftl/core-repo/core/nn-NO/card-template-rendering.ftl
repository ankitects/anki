### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Fleire opplysingar
card-template-rendering-front-side-problem = Framsidemal har eit problem:
card-template-rendering-back-side-problem = Baksidemal har eit problem:
card-template-rendering-browser-front-side-problem = Nettlesarspesifisk framsidemal har eit problem:
card-template-rendering-browser-back-side-problem = Nettlesarspesifisk baksidemal har eit problem:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = Manglar «{ $missing }» i «{ $tag }»
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = Manglar «{ $missing }»
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = Fann «{ $found }» men venta «{ $expected }»
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = Fann «{ $found }», men manglar «{ $missing1 }» eller «{ $missing2 }»
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = Fann «{ $found }», men det er inkje felt som heiter «{ $field }»
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = Framsida til dette kortet er tom.
card-template-rendering-missing-cloze =
    Inga luke { $number } fann på kort.
    Venlegast legg til ei lukesletting eller bruka Tomme kort-verktyet.
