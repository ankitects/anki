## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = Cerca invàlida: { $reason }
search-misplaced-and = s'ha trobat un `and` que no connecta dos termes de cerca. Si voleu cercar aquesta mateixa paraula, escriviu-la entre cometes dobles: `"and"`.
search-misplaced-or = s'ha trobat un `or` que no connecta dos termes de cerca. Si voleu cercar aquesta mateixa paraula, escriviu-la entre cometes dobles: `"or"`.
# Here, the ellipsis "..." may be localised.
search-empty-group = s'ha trobat un grup `(...)`, però no hi ha res dins dels parèntesis. Si voleu cercar literalment els parèntesis, escriviu-los entre cometes dobles: `"()"`.
search-unopened-group = s'ha trobat un parèntesi de tancament `)`, però no hi ha cap parèntesi d'obertura `(` que el precedeixi. Si voleu cercar un parèntesi de tancament de manera literal, escriviu-lo entre cometes dobles o afegiu-hi una barra invertida: `")"` o `\)`.
search-unclosed-group = s'ha trobat un claudàtor d'obertura `(` sense cap claudàtor de tancament `)` que el succeixi. Si voleu cercar un claudàtor d'obertura de manera literal, escriviu-lo entre cometes dobles o afegiu-hi una barra invertida al davant: `"("` o `\(`.
search-empty-quote = s'ha trobat un parell de cometes dobles `""`, però no hi ha res dins de les cometes. Si voleu cercar les cometes dobles de manera literal, afegiu-hi unes barres invertides al davant: `\"\"`.
search-unclosed-quote = s'han trobat unes cometes dobles `"`d'obertura, però no n'hi ha de tancament. Si voleu cercar les cometes dobles de manera literal, afegiu-hi una barra invertida al davant: `\"`.
search-missing-key = s'han trobat dos punts `:`, però no hi ha cap paraula que els precedeixi. Si voleu cercar els dos punts de manera literal, afegiu-hi una barra invertida al davant: `\:`.
search-unknown-escape = la seqüència d'escapament `{ $val }` no està definida. Si voleu cercar una barra invertida `\` de manera literal, afegiu-n'hi una altra al davant: `\\`.
search-invalid-argument = `{ $term }` ha rebut un paràmetre invàlid '`{ $argument }`'.
search-invalid-flag-2 = `flag:` ha d'anar succeït per un número de marcador vàlid: `1` (roig), `2` (taronja), `3` (verd), `4` (blau), `5` (rosa), `6 ` (turquesa), `7` (morat) o `0` (cap senyal).
search-invalid-prop-operator = `prop:{ $val }` ha d'anar succeït per un dels operadors de comparació següents: `=`, `!=`, `<`, `>`, `<=` o `>=`.
search-invalid-other = comproveu si hi ha cap error d’escriptura.

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = S'esperava un número en "`{ $context }`", però s'ha trobat "`{ $provided }`".
search-invalid-whole-number = S'esperava un número sencer en "`{ $context }`", però s'ha trobat "`{ $provided }`".
search-invalid-positive-whole-number = S'esperava un número sencer positiu en "`{ $context }`", però s'ha trobat "`{ $provided }`".
search-invalid-negative-whole-number = S'esperava un número sencer menor o igual a 0 en "`{ $context }`", però s'ha trobat "`{ $provided }`".
search-invalid-answer-button = S'esperava un botó de resposta entre 1 i 4 en "`{ $context }`", però s'ha trobat "`{ $provided }`".

## Column labels in browse screen

search-note-modified = Nota modificada
search-card-modified = Targeta modificada

##

# Tooltip for search lines outside browser
search-view-in-browser = Mostra en el navegador
