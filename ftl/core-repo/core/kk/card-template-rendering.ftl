### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Толығырақ
card-template-rendering-front-side-problem = Алдыңғы үлгі мәселесі:
card-template-rendering-back-side-problem = Артқы үлгі мәселесі:
card-template-rendering-browser-front-side-problem = Шолғыштағы алдыңғы үлгі мәселесі:
card-template-rendering-browser-back-side-problem = Шолғыштағы артқы үлгі мәселесі:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = { $tag } ішінде { $missing } жоқ
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = { $missing } жетіспейді
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = { $expected } орнына { $found } табылды
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = "{ $found }' табылды, бірақ '{ $missing1 }' немесе '{ $missing2 }' жоқ
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = { $found } табылды, бірақ { $field } деген өріс жоқ
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = Картаның алды бос.
card-template-rendering-missing-cloze =
    Картада { $number } "саңылау толтыру " табылмады.
    Саңылау жасаңыз немесе Бос Карта құралын қолданыңыз.
