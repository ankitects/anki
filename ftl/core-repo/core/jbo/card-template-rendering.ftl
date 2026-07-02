### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = djunoi
card-template-rendering-front-side-problem = .i la'e di'e pe lo te morna be fi lo crane cu nabmi
card-template-rendering-back-side-problem = .i la'e di'e pe lo te morna be fi lo trixe cu nabmi
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = .i sarcu fa lo du'u zoi zoi. { $tag } .zoi ckini zoi zoi. { $missing } .zoi
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = .i sarcu fa tu'a zoi zoi. { $missing } .zoi
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = .i pu facki tu'a zoi zoi. { $found } .zoi .i ku'i sarcu fa tu'a zoi zoi. { $expected } .zoi
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = .i pu facki tu'a zoi zoi. { $found } .zoi .i ku'i na ckini zoi zoi. { $missing1 } .zoi ja zoi zoi. { $missing2 } .zoi
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = .i pu facki tu'a zoi zoi. { $found } .zoi .i ku'i zoi zoi. { $field } .zoi cmene no datnyvau
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = .i pa crane be lo karda cu kunti
