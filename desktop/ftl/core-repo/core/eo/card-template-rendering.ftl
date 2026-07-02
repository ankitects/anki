### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Pliaj informoj
card-template-rendering-front-side-problem = La fronta ŝablono havas problemon:
card-template-rendering-back-side-problem = La dorsa ŝablono havas problemon:
card-template-rendering-browser-front-side-problem = La fronta ŝablono por specifa foliumilo havas problemen:
card-template-rendering-browser-back-side-problem = La dorsa ŝablono por specifa foliumilo havas problemen:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = '{ $missing }' mankas en '{ $tag }'
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = '{ $missing }' mankas
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = '{ $found }' estis trovita, sed '{ $expected }' estis atendita
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = '{ $found }' estis trovita, sed '{ $missing1 }' aŭ '{ $missing2 }' mankas
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = '{ $found }' estis trovita, sed ne ekzistas kampo kun la nomo '{ $field }'
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = La fronta flanko de ĉi tiu karto estas malplena.
card-template-rendering-missing-cloze =
    Neniu truo { $number }  estis trovita en la karto.
    Bonvolu aldoni trutekston aŭ uzu la ilon por malplenaj kartoj.
