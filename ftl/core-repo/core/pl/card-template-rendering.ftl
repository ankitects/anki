### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Więcej informacji
card-template-rendering-front-side-problem = Problem w przednim szablonie:
card-template-rendering-back-side-problem = Problem w tylnim szablonie:
card-template-rendering-browser-front-side-problem = Jest problem z szablonem przodu specyficznym dla przeglądarki:
card-template-rendering-browser-back-side-problem = Jest problem z szablonem tyłu specyficznym dla przeglądarki:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = Brak "{ $missing }" w "{ $tag }"
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = Brak "{ $missing }"
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = Znaleziono "{ $found }", oczekiwano "{ $expected }"
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = Znaleziono "{ $found }", ale brak "{ $missing1 }" lub "{ $missing2 }"
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = Znaleziono "{ $found }", ale brak pola o nazwie "{ $field }"
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = Przód tej karty jest pusty.
card-template-rendering-missing-cloze =
    Nie znaleziono luki { $number } na karcie.
    Dodaj wypełnianie luki lub użyj narzędzia Puste Karty.
