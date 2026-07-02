### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Lisätietoja
card-template-rendering-front-side-problem = Etupuolen mallissa on ongelma:
card-template-rendering-back-side-problem = Kääntöpuolen mallissa on ongelma:
card-template-rendering-browser-front-side-problem = Selainkohtaisessa etupuolen mallissa on ongelma:
card-template-rendering-browser-back-side-problem = Selainkohtaisessa kääntöpuolen mallissa on ongelma:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = Kohteesta '{ $tag }' puuttuu '{ $missing }'
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = Puuttuu '{ $missing }'
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = Löydettiin '{ $found }', mutta odotettiin '{ $expected }'
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = Löydettiin '{ $found }', mutta '{ $missing1 }' tai '{ $missing2 }' puuttuu.
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = Löydettiin '{ $found }', mutta kenttää '{ $field }' ei löytynyt.
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = Kortin etupuoli on tyhjä.
card-template-rendering-missing-cloze =
    Aukkotehtävää { $number } ei löytynyt kortista.
    Lisää joko puuttuva aukkotehtävä, tai poista kortti Tyhjät kortit -toiminnolla.
