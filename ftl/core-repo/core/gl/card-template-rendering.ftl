### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Máis información
card-template-rendering-front-side-problem = O modelo do anverso ten un problema:
card-template-rendering-back-side-problem = O modelo do reverso ten un problema:
card-template-rendering-browser-front-side-problem = O modelo do anverso específico do explorador ten un problema:
card-template-rendering-browser-back-side-problem = O modelo do reverso específico do explorador ten un problema:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = Falta "{ $missing }" en "{ $tag }"
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = Falta "{ $missing }"
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = Encontrouse "{ $found }", mais se esperaba "{ $expected }"
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = Encontrouse "{ $found }", mais falta "{ $missing1 }" ou "{ $missing2 }"
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = Encontrouse "{ $found }", mais non hai ningún campo chamado "{ $field }"
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = O anverso desta tarxeta está baleiro.
card-template-rendering-missing-cloze =
    Non se encontrou ningún oco número { $number } na tarxeta.
    Engade un oco ou usa a ferramenta "Tarxetas baleiras".
