### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Més informació
card-template-rendering-front-side-problem = La plantilla de l'anvers té un problema:
card-template-rendering-back-side-problem = La plantilla del revers té un problema:
card-template-rendering-browser-front-side-problem = La plantilla de l'anvers específica del navegador té un problema:
card-template-rendering-browser-back-side-problem = La plantilla del revers específica del navegador té un priblema:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = Falta '{ $missing }' en '{ $tag }'
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = Falta '{ $missing }'
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = S'ha trobat '{ $found }', però s'esperava '{ $expected }'
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = S'ha trobat '{ $found }', però falta '{ $missing1 }' o '{ $missing2 }'
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = S'ha trobat '{ $found }', però no hi ha cap camp anomenat '{ $field }'
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = L’anvers d'aquesta targeta està en blanc.
card-template-rendering-missing-cloze =
    No s'ha trobat cap buit número { $number } en la targeta.
    Podeu afegir-hi un buit o utilitzar la funció «Targetes buides…» per a eliminar aquesta targeta buida.
