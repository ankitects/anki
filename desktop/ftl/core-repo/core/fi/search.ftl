## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = Virheellinen haku: { $reason }
search-misplaced-and = `and` löytyi, mutta se ei yhdistä kahta hakutermiä. Jos haluat etsiä itse sanaa, ympäröi se lainausmerkeillä: `"and"`.
search-misplaced-or = `or` löytyi, mutta se ei yhdistä kahta hakutermiä. Jos haluat etsiä itse sanaa, ympäröi se lainausmerkeillä: `"or"`.
# Here, the ellipsis "..." may be localised.
search-empty-group = ryhmä `(...)` löytyi, mutta sulkujen välissä ei ollut mitään etsittävää. Jos haluat kirjaimellisesti etsiä tyhjiä sulkeita, ympäröi ne kaksinkertaisilla lainausmerkkeillä: `"( )"`.
search-unopened-group = sulkeva sulje `)` löytyi, mutta sitä ei edeltänyt avaava sulje `(`. Jos haluat kirjaimellisesti etsiä `)`-merkkiä, ympäröi se kaksinkertaisilla lainausmerkkeillä tai lisää kenoviiva sen edelle: `")"` tai `\)`.
search-unclosed-group = avaava sulje `(` löytyi, mutta sitä ei seurannut sulkeva sulje `)`. Jos haluat kirjaimellisesti etsiä `(`-merkkiä, ympäröi se kaksinkertaisilla lainausmerkkeillä tai lisää kenoviiva sen edelle: `"("` tai `\(`.
search-empty-quote = löytyi lainausmerkkipari `""`, mutta niiden välissä ei ollut mitään etsittävää. Jos haluat etsiä kirjaimellisesti kaksinkertaista lainausmerkkiparia, lisää niiden eteen kenoviivat: `\"\"`.
search-unclosed-quote = löytyi avaava lainausmerkki `"`, mutta ei löytynyt toista (sulkevaa) lainausmerkkiä. Jos haluat etsiä kirjaimellisesti lainausmerkkiä `"`, lisää sen eteen kenoviiva: `\"`.
search-missing-key = kaksoispiste `:` löytyi, mutta sen edeltä ei löytynyt avainsanaa. Jos haluat etsiä kirjaimellisesti `:`-merkkiä, lisää sen eteen kenoviiva: `\:`.
search-unknown-escape = koodinvaihtojaksoa `{ $val }` ei ole määritelty. Jos haluat hakea kirjaimellisesti kenoviivaa `\`, lisää sen eteen toinen: `\\`.
search-invalid-argument = `{ $term }` sai virheellisen argumentin '`{ $argument }`'.
search-invalid-flag-2 = `flag:`-merkinnän jälkeen on tultava kelvollinen lipun numero: `1` (punainen), `2` (oranssi), `3` (vihreä), `4` (sininen), `5` (pinkki), `6` (turkoosi), `7` (violetti) tai `0` (ei lippua).
search-invalid-prop-operator = `prop:{ $val }`-merkinnän jälkeen on tultava yksi seuraavista vertailuoperaattoreista: `=`, `!=`, `<`, `>`, `<=` tai `>=`.
search-invalid-other = tarkista kirjoitusvirheet.

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = odotettiin numeroa kohdassa "`{ $context }`", mutta löytyi "`{ $provided }`".
search-invalid-whole-number = odotettiin kokonaislukua kohdassa "`{ $context }`", mutta löytyi "`{ $provided }`".
search-invalid-positive-whole-number = odotettiin positiivista kokonaislukua kohdassa "`{ $context }`", mutta löytyi "`{ $provided }`".
search-invalid-negative-whole-number = odotettiin kokonaislukua, joka on pienempi tai yhtä suuri kuin 0 kohdassa "`{ $context }`", mutta löytyi "`{ $provided }`".
search-invalid-answer-button = odotettiin vastauspainiketta väliltä 1-4 kohdassa "`{ $context }`", mutta löytyi "`{ $provided }`".

## Column labels in browse screen

search-note-modified = Muistiinpanoa muokattu
search-card-modified = Korttia muokattu

##

# Tooltip for search lines outside browser
search-view-in-browser = Näytä selaimessa
