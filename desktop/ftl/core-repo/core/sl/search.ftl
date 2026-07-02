## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = Neveljavno iskanje: { $reason }
search-misplaced-and = operator 'and' je bil najden, vendar ne povezuje dveh iskalnih pojmov. Če želite iskati po tej besedi, jo vpišite v dvojne narekovaje: "and".
search-misplaced-or = operator 'or' je bil najden, vendar ne povezuje dveh iskalnih pojmov. Če želite iskati po tej besedi, jo vpišite v dvojne narekovaje: "or".
# Here, the ellipsis "..." may be localised.
search-empty-group = najdena je bila skupina '(...)' ampak v kolepajih ni bilo ničesar za iskanje. Če želite iskati dejanske oklepaje, jih obdajte z dvojnimi narekovaji: "( )".
search-unopened-group = zaklepaj ')' je bil najden, vendar ni bilo oklepaja '(' pred njim. Če želite zaklepaj uporabiti kot iskalni pojem, ga obdajte z dvojnimi narekovaji ")" ali pred njega postavite levo poševnico '\)'.
search-unclosed-group = oklepaj '(' je bil najden, vendar ni bilo zaklepaja ')' za njim. Če želite oklepaj uporabiti kot iskalni pojem, ga obdajte z dvojnimi narekovaji "(" ali pred njega postavite levo poševnico '\('.
search-empty-quote = najden je bil par dvojnih narekovajev ' "" ', ampak med njima ni bilo iskalnega pojma. Če želite iskati dejansko dva narekovaja, pred njiju dodajte levi poševnici ' \"\" '.
search-unclosed-quote = najden je bil začetni narekovaj ' " ', vendar ni bilo končnega. Če želite narekovaj uporabiti kot iskalni pojem, pred njega postavite levo poševnico ' \" '.
search-missing-key = najdeno je bilo dvopičje ' : ' brez ključne besede pred njim. Če želite dvpišje uporabiti kot iskalni pojem, pred njega postavitev levo poševnico ' \: '.
search-unknown-escape = izhodno zaporedje ' { $val } ' ni definirano. Če želite levo poševnico uporabiti kot iskalni pojem, pred njo postavite še eno ' \\ '.
search-invalid-argument = v ' { $term } ' je bil dan napačen argument '{ $argument }'.
search-invalid-flag-2 = 'flag:' morate vpisati z ustrezno številko: '1' rdeča, '2' oranžna, '3' zelena, '4' modra, '5' roza, '6' turkizna, '7' vijolična ali '0' brez zastavice.
search-invalid-prop-operator = 'prop:{ $val }' morate vpisati z enim od primerjalnih operatorjev: '=`, `!=`, `<`, `>`, `<=` ali `>=`.
search-invalid-other = prosimo, preverite za pravopisne napake.

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = pričakovana je bila številka v "'{ $context }'", našli pa smo "'{ $provided }'".
search-invalid-whole-number = pričakovano je bilo celo število v "'{ $context }'", našli pa smo "'{ $provided }'".
search-invalid-positive-whole-number = pričakovano je bilo pozitivno celo število v "'{ $context }'", našli pa smo "'{ $provided }'".
search-invalid-negative-whole-number = pričakovano je bilo celo število (večje ali enako 0) v "'{ $context }'", našli pa smo "'{ $provided }'".
search-invalid-answer-button = pričakovan je bil gumb za odgovor 1-4 v "'{ $context }'", našli pa smo "'{ $provided }'".

## Column labels in browse screen

search-note-modified = Urejeno
search-card-modified = Spremenjeno

##

# Tooltip for search lines outside browser
search-view-in-browser = Poglej v brskalniku
