### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Plus d’informations
card-template-rendering-front-side-problem = Le modèle du recto a un problème :
card-template-rendering-back-side-problem = Le modèle du verso a un problème :
card-template-rendering-browser-front-side-problem = Le modèle du recto spécifique au navigateur a un problème :
card-template-rendering-browser-back-side-problem = Le modèle du verso spécifique au navigateur a un problème :
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = Il manque « { $missing } » dans « { $tag } »
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = Il manque « { $missing } »
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = « { $found } » a été trouvé mais c’est « { $expected } » qui est attendu
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = « { $found } » a été trouvé mais « { $missing1 } » ou « { $missing2 } » est manquant
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = « { $found } » a été trouvé, mais il n’existe pas de champ nommé « { $field } »
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = Le recto de cette carte est vide.
card-template-rendering-missing-cloze = Aucun résultat n'a été trouvé pour { $number } sur la carte. Vous pouvez utiliser "supprimer la carte" pour enlever cette carte vide.
