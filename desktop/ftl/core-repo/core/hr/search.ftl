## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = Neispravno pretraživanje: { $reason }
search-misplaced-and = pronađen je `and`, ali ne povezuje dva izraza za pretraživanje. Ako želite potražiti samu riječ, omotajte je s dvostrukim navodnicima: `"and"`.
search-misplaced-or = pronađen je `or`, ali ne povezuje dva izraza za pretraživanje. Ako želite potražiti samu riječ, omotajte je s dvostrukim navodnicima: `"or"`.
# Here, the ellipsis "..." may be localised.
search-empty-group = pronađena je grupa `(...)`, ali nije bilo ničega između zagrada da bi se pretražilo. Ako želite potražiti doslovne zagrade, omatajte ih dvostrukim navodnicima: `"( )"`.
search-unopened-group = pronađena je zatvorena zagrada `)`, ali nije bilo `(` prije nje. Ako želite potražiti doslovnu `)`, omotajte je dvostrukim navodnicima ili dodajte obrnutu kosu crtu: `")"` ili `\)`.
search-unclosed-group = pronađena je otvorena zagrada `(`, ali nije slijedila `)` nakon nje. Ako želite potražiti doslovno `(`, omotajte je dvostrukim navodnicima ili dodajte obrnutu kosu crtu: `"("` ili `\(`.
search-empty-quote = pronađeni su dvostruki navodnici `""`, no između njih nije nađeno ništa što bi se moglo pretražiti. Ako želite potražiti doslovne duple navodnike, dodajte obrnute kose crte, ovako: `\"\"`.
search-unclosed-quote = pronađen je prvi dvostruki navodnik `"`, ali nije slijedio drugi da ga zatvori. Ako želite potražiti doslovno `"`, dodajte obrnutu kosu crtu: `\"`.
search-missing-key = pronađena je dvotočka `:`, no ispred nje se nije nalazila nijedna ključna riječ. Ako želite potražiti doslovnu `:`, dodajte obrnutu kosu crtu: `\:`.
search-unknown-escape = kontrolna sekvenca `{ $val }` nije definirana. Ako želite potražiti doslovnu `\`, dodajte još jednu: `\\`.
search-invalid-argument = `{ $term }` je predan neispravni argument '`{ $argument }`'.
search-invalid-flag-2 = nakon `flag:` mora slijediti valjani broj zastavice: `1` (crvena), `2` (narančasta), `3` (zelena), `4` (plava), `5` (ružičasta), `6` (tirkizna), `7` (ljubičasta) ili `0` (bez zastavice).
search-invalid-prop-operator = nakon `prop:{ $val }` mora slijediti jedan od sljedećih operatora usporedbe: `=`, `!=`, `<`, `>`, `<=` ili `>=`.
search-invalid-other = provjerite da niste nešto pogrešno napisali.

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = očekivao se broj u "`{ $context }`", no pronađeno je "`{ $provided }`".
search-invalid-whole-number = očekivao se cijeli broj u "`{ $context }`", no pronađeno je "`{ $provided }`".
search-invalid-positive-whole-number = očekivao se pozitivni cijeli broj u "`{ $context }`", no pronađeno je "`{ $provided }`".
search-invalid-negative-whole-number = očekivao se cijeli broj manji ili jednak nuli u "`{ $context }`", no pronađeno je "`{ $provided }`".
search-invalid-answer-button = očekivao se gumb za odgovor između 1 i 4 u "`{ $context }`", no pronađeno je "`{ $provided }`".

## Column labels in browse screen

search-note-modified = Bilješka promijenjena
search-card-modified = Kartica promijenjena

##

# Tooltip for search lines outside browser
search-view-in-browser = Pogledaj u pregledniku
