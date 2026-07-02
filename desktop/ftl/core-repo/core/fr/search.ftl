## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = Recherche invalide : { $reason }
search-misplaced-and = un `and` a été trouvé mais il ne connecte pas deux termes de recherche. Si vous voulez chercher le mot en lui-même, entourez-le de double guillemets : `"and"`.
search-misplaced-or = un `or` a été trouvé mais il ne connecte pas deux termes de recherche. Si vous voulez chercher le mot en lui-même, entourez-le de double guillemets : `"or"`.
# Here, the ellipsis "..." may be localised.
search-empty-group = un groupe `(...)` a été trouvé mais rien n'a été trouvé entre les parenthèses. Si vous voulez chercher des parenthèses, entourez-les de double guillemets : `"()"`.
search-unopened-group = une parenthèse fermante `)` a été trouvée, mais il n'y avait pas de parenthèse ouvrante `(` avant. Si vous voulez chercher une parenthèse `)`, entourez-la de double guillemets ou ajoutez une barre oblique inversée avant : `")"` or `\)`.
search-unclosed-group = une parenthèse ouvrante `(` a été trouvée, mais il n'y avait pas de parenthèse fermante `)` après. Si vous voulez chercher une parenthèse ouvrante `(`, entourez-la de double guillemets ou ajoutez une barre oblique inversée avant : `"("` or `\(`.
search-empty-quote = une paire de double guillemets `""` a été trouvée, mais rien n'a été trouvé entre les guillemets. Si vous voulez chercher des doubles guillemets, ajoutez des barres obliques inversées avant : `\"\"`.
search-unclosed-quote = un double guillemet `"` a été trouvé, mais aucun autre pour le fermer. Si vous voulez chercher un double guillemet, ajoutez une barre oblique inversée avant : `\"`.
search-missing-key = des deux points `:` ont été trouvés, mais il n'y avait pas de mot-clé avant. Si vous voulez chercher des deux points, ajoutez une barre oblique inversée avant : `\:`.
search-unknown-escape = la séquence d'échappement `{ $val }` n'est pas définie. Si vous voulez chercher une barre oblique inversée `\`, ajoutez une autre barre avant: `\\`.
search-invalid-argument = `{ $term }` a donné un argument invalide '`{ $argument }`'.
search-invalid-flag-2 = `drapeau:` doit être suivi d'un numéro de drapeau valide : `1` (rouge), `2` (orange), `3` (vert), `4` (bleu), `5` (rose), `6` (turquoise), `7` (violet) ou `0` (pas de drapeau).
search-invalid-prop-operator = `prop:{ $val }` doit être suivi par l'un des opérateurs de comparaison suivant : `=`, `!=`, `<`, `>`, `<=` or `>=`.
search-invalid-other = veuillez vérifier les fautes de frappes.

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = attendait un nombre dans "`{ $context }`", mais a trouvé "`{ $provided }`".
search-invalid-whole-number = attendait un nombre entier dans "`{ $context }`", mais a trouvé "`{ $provided }`".
search-invalid-positive-whole-number = attendait un nombre entier positif dans "`{ $context }`", mais a trouvé "`{ $provided }`".
search-invalid-negative-whole-number = attendait un nombre entier inférieur ou égal à 0 dans "`{ $context }`", mais a trouvé "`{ $provided }`".
search-invalid-answer-button = attendait une réponse de bouton entre 1-4 dans "`{ $context }`", mais a trouvé "`{ $provided }`".

## Column labels in browse screen

search-note-modified = Note modifiée
search-card-modified = Carte modifiée

##

# Tooltip for search lines outside browser
search-view-in-browser = Afficher dans le navigateur
