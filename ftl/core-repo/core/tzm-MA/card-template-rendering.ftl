### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = ⵉⵏⵖⵎⵉⵙⵏ ⵢⴰⴹⵏⵉⵏ
card-template-rendering-front-side-problem = ⵉⵍⵍⴰ ⴷⴰⵔ ⵓⵏⴰⵡ ⵏ ⴷⴰⵜ ⵢⴰⵜ ⵜⵎⴽⵔⵉⵙⵜ :
card-template-rendering-back-side-problem = ⵉⵍⵍⴰ ⴷⴰⵔ ⵓⵏⴰⵡ ⵏ ⴹⴰⵔⵜ ⵢⴰⵜ ⵜⵎⴽⵔⵉⵙⵜ :
card-template-rendering-browser-front-side-problem = ⵉⵍⵍⴰ ⴷⴰⵔ ⵡⴰⵏⴰⵡ ⵏ ⵡⴰⵣⵢ ⵏ ⴷⴰⵜ ⵏ ⵓⵎⵙⵙⴰⵔⴰⵢ ⵢⴰⵜ ⵜⵎⴽⵔⵉⵙⵜ
card-template-rendering-browser-back-side-problem = ⵉⵍⵍⴰ ⴷⴰⵔ ⵡⴰⵏⴰⵡ ⵏ ⵡⴰⵣⵢ ⵏ ⴹⴰⵔⵜ ⵏ ⵓⵎⵙⵙⴰⵔⴰⵢ ⵢⴰⵜ ⵜⵎⴽⵔⵉⵙⵜ
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = ⵓⵔ ⵉⵍⵍⵉ « { $missing } » ⴳ « { $tag } »
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = ⵓⵔ ⵉⵍⵍⵉ « { $missing } »
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = ⵉⵜⵜⵓⵢⴰⴼⴰ « { $found } » ⵎⴰⵛⴰ ⵜⵉⴷⵎⵉ ⵜⴳⴰ « { $expected } »
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = ⵉⵜⵜⵓⵢⴰⴼⴰ « { $found } »  ⵎⴰⵛⴰ « { $missing1 } » ⵏⵖⴷ « { $missing2 } » ⵓⵔ ⵉⵍⵍⵉ
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = ⵉⵜⵜⵓⵢⴰⴼⴰ « { $found } » ⵎⴰⵛⴰ ⵓⵔ ⵉⵍⵍⵉ ⴽⵔⴰⵏ ⵉⴳⵔ ⴰⵙⵙⴰⵖ ⵏⵏⵙ « { $field } »
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = ⴰⵥⵢ ⵏ ⴷⴰⵜ ⵏ ⵜⴰⴽⴰⵕⴹⴰ ⵉⵅⵡⴰ
card-template-rendering-missing-cloze = ⵓⵔ ⵜⵢⴰⴼⴰⵏⵜ ⴽⵔⴰ ⵏ ⵜⵉⴼⵍⵍⴰⵡⵉⵏ ⵉ { $number } ⴳ ⵜⴽⴰⵕⴹⴰ. ⵜⵖⵉⵎ ⴰⴷ ⵜⵙⵙⵎⵔⵙⴷ "ⴽⵙⵙ ⵜⴰⴽⴰⵕⴹⴰ" ⴰⴼⴰⴷ ⴰⴷ ⵜⵙⵙⵓⴼⵖⴷ ⵜⴰⴽⴰⵕⴹⴰ ⴰⴷ ⵉⵅⵡⴰⵏ.
