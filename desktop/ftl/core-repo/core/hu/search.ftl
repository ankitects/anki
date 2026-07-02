## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = Érvénytelen keresés: { $reason }
search-misplaced-and = A keresésben `and` szó szerepel, de nem két keresőkifejezés között. Ha a szóra szeretnél keresni, tedd idézőjelek közé: `"and"`.
search-misplaced-or = A keresésben `or` szó szerepel, de nem két keresőkifejezés között. Ha a szóra szeretnél keresni, tedd idézőjelek közé: `"or"`.
# Here, the ellipsis "..." may be localised.
search-empty-group = A keresésben egy üres csoport `()` szerepel. Ha zárójelekre szeretnél keresni, tedd őket idézőjelek közé: `"()"`.
search-unopened-group = A keresésben `)` zárójel szerepel, de nincs hozzá tartozó `(`. Ha a zárójelre szeretnél keresni, tedd idézőjelek közé: `")"` vagy használj visszaper jelet: `\)`.
search-unclosed-group = A keresésben `(` zárójel szerepel, de nincs hozzá tartozó `(`. Ha a zárójelre szeretnél keresni, tedd idézőjelek közé: `"("` vagy használj visszaper jelet: `\(`.
search-empty-quote = A keresésben idézőjelek (`""`) szerepelnek, de nincs közöttük szöveg. Ha idézőjelekre szeretnél keresni, használj visszaper jelet: `\"\"`.
search-unclosed-quote = A keresésben páratlan `"` idézőjel szerepel. Ha az idézőjelre szeretnél keresni, használj visszaper jelet: `\"`.
search-missing-key = A keresésben `:` kettőspont szerepel, de nincs előtte kucsszó. Ha a kettőspontra szeretnél keresni, használj visszaper jelet: `\:`.
search-unknown-escape = A `{ $val }` kifejezés nincs definiálva. Ha visszaper jelre szeretnél keresni, használj két visszaper jelet: `\\`.
search-invalid-argument = `{ $term }` érvénytelen paramétert kapott: `{ $argument }`.
search-invalid-flag-2 = A `flag` szót egy jelölőhöz tartozó számnak kell követnie: `1` (piros), `2` (narancssárga), `3` (zöld), `4` (kék), `5` (rózsaszín), `6` (türkiz), `7` (lila) vagy `0` (nincs jelölő).
search-invalid-prop-operator = `prop:{ $val }` után `=`, `!=`, `<`, `>`, `<=` vagy `>=` műveletnek kell következnie.
search-invalid-other = Ellenőrizd, hogy elgépeltél-e valamit!

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = "`{ $context }`" részben "`{ $provided }`" helyett egy számnak kell szerepelnie.
search-invalid-whole-number = "`{ $context }`" részben "`{ $provided }`" helyett egész számnak kell szerepelnie.
search-invalid-positive-whole-number = "`{ $context }`" részben "`{ $provided }`" helyett pozitív egész számnak kell szerepelnie.
search-invalid-negative-whole-number = "`{ $context }`" részben "`{ $provided }`" helyett negatív egész számnak vagy nullának kell szerepelnie.
search-invalid-answer-button = "`{ $context }`" részben "`{ $provided }`" helyett egy és négy közötti egész számnak kell szerepelnie.

## Column labels in browse screen

search-note-modified = Szerkesztve
search-card-modified = Módosítva

##

# Tooltip for search lines outside browser
search-view-in-browser = Megtekintés a böngészőben
