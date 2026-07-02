### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Maggiori informazioni
card-template-rendering-front-side-problem = Il modello fronte ha un problema:
card-template-rendering-back-side-problem = Il modello retro ha un problema:
card-template-rendering-browser-front-side-problem = Il modello `fronte` specifico per il browser ha un problema:
card-template-rendering-browser-back-side-problem = Il modello `retro` specifico per il browser ha un problema:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = Manca '{ $missing }' in '{ $tag }'
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = Manca '{ $missing }'
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = Trovato '{ $found }', però era previsto '{ $expected }'
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = Trovato '{ $found }', però manca '{ $missing1 }' o '{ $missing2 }'
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = Trovato '{ $found }', ma non c'è un campo chiamato '{ $field }'
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = Il fronte di questa carta è vuoto.
card-template-rendering-missing-cloze =
    Non è stato trovato il cloze { $number } sulla carta.
    Aggiungi una cancellazione cloze o usa lo strumento Carte vuote.
