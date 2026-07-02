### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Weitere Informationen
card-template-rendering-front-side-problem = Die Vorlage für die Vorderseite hat ein Problem:
card-template-rendering-back-side-problem = Die Vorlage für die Rückseite hat ein Problem:
card-template-rendering-browser-front-side-problem = Browser-spezifische Vorlage für die Vorderseite hat ein Problem:
card-template-rendering-browser-back-side-problem = Browser-spezifische Vorlage für die Rückseite hat ein Problem:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = '{ $missing }' fehlt in '{ $tag }'
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = '{ $missing }' fehlt
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = '{ $found }' gefunden, aber '{ $expected }' erwartet
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = '{ $found }' gefunden, aber '{ $missing1 }' oder '{ $missing2 }' fehlt
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = '{ $found }' gefunden, aber es gibt kein Feld mit dem Namen '{ $field }'
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = Die Vorderseite dieser Karte ist leer.
card-template-rendering-missing-cloze = Die Lücke Nummer { $number } ist im Lückentext nicht vorhanden. Sie können die Lücke hinzufügen oder diese Karte mit dem „Leere Karten …“-Werkzeug löschen.
