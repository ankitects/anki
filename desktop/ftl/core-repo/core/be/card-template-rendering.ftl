### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Больш інфармацыі
card-template-rendering-front-side-problem = Праблема з шаблонам пярэдняга боку:
card-template-rendering-back-side-problem = Праблема з шаблонам адваротнага боку:
card-template-rendering-browser-front-side-problem = Браўзерная праблема з шаблонам пярэдняга боку карткі:
card-template-rendering-browser-back-side-problem = Браўзерная праблема з шаблонам задняга боку карткі:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = У «{ $tag }» адсутнічае «{ $missing }»
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = Адсутнічае «{ $missing }»
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = Знойдзена «{ $found }», але чакалася «{ $expected }»
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = Знойдзена «{ $found }», але адсутнічае «{ $missing1 }» або «{ $missing2 }»
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = Знойдзена «{ $found }», але няма поля з назвай «{ $field }»
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = Пярэдні бок гэтай карткі пусты.
card-template-rendering-missing-cloze =
    Прабел { $number } не знойдзены на картцы.
    Дадайце запаўненне прабела або скарыстайце інструмент «Пустыя карткі».
