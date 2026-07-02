### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = تېخىمۇ كۆپ ئۇچۇر
card-template-rendering-front-side-problem = ئالدى قېلىپتا مەسىلە بار:
card-template-rendering-back-side-problem = كەينى قېلىپتا مەسىلە بار:
card-template-rendering-browser-front-side-problem = بەلگىلەنگەن توركۆرگۈنىڭ ئالدى قېلىپىدا مەسىلە بار:
card-template-rendering-browser-back-side-problem = بەلگىلەنگەن توركۆرگۈنىڭ كەينى قېلىپىدا مەسىلە بار:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = «{ $tag }» دا «{ $missing }» كەم
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = «{ $missing }» كەم
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = «{ $found }» تېپىلدى، ئەمما كۈتۈلگىنى «{ $expected }»
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = «{ $found }» تېپىلدى، ئەمما «{ $missing1 }» ياكى «{ $missing2 }» كەم
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = «{ $found }» تىپىلدى ئەمما «{ $field }» بۆلەك يوق
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = بۇ كارتىنىڭ ئالدى يۈزى بوش.
card-template-rendering-missing-cloze =
    كارتىدىن بوش ئورۇن تولدۇرۇش { $number } بايقالمىدى.
    بىر بوشلۇق قوشۇڭ ياكى قورال › بوش كارتىنى تازىلا ئارقىلىق بۇ كارتىنى چىقىرىۋېتىڭ.
