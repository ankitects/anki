### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Více informací
card-template-rendering-front-side-problem = Šablona líce má problém:
card-template-rendering-back-side-problem = Šablona rubu má problém:
card-template-rendering-browser-front-side-problem = Šablona líce prohlížeče má problém:
card-template-rendering-browser-back-side-problem = Šablona rubu prohlížeče má problém:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = Chybí „{ $missing }“ v „{ $tag }“
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = Chybí „{ $missing }“
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = Nalezeno „{ $found }“, ale očekáváno „{ $expected }“
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = Nalezeno „{ $found }“, ale chybí „{ $missing1 }“ nebo „{ $missing2 }“
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = Nalezeno „{ $found }“, ale neexistuje žádné pole s názvem „{ $field }“
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = Líc této karty je prázdný.
card-template-rendering-missing-cloze =
    Na kartě nebyla nenalezena doplňovačka { $number }.
    Prosím buď přidejte doplňovačku, nebo použijte nástroj Prázdné karty.
