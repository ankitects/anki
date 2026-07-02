## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = Căutare nevalidă: { $reason }
search-misplaced-and = a fost găsit un `și` dar nu conectează doi termeni de căutare. Dacă dorești să cauți cuvântul în sine, încadrează-l între ghilimele duble: `"și"`.
search-misplaced-or = a fost găsit un `sau` dar nu conectează doi termeni de căutare. Dacă dorești s cauți cuvântul în sine, încadrează-l între ghilimele duble: `"sau"`.
# Here, the ellipsis "..." may be localised.
search-empty-group = a fost găsit un grup `(...)`, dar nu era nimic de căutat între paranteze. Dacă dorești să cauți paranteze literale, include-le între ghilimele duble: `"( )"`.
search-unopened-group = a fost găsită o paranteză de închidere `)`, dar nu a existat nicio paranteză de deschidere `(` înaintea acesteia. Dacă doriți să căutați un literal `)`, includeți-l între ghilimele duble sau adăugați o bară oblică inversă: `")"` sau ` \)`.
search-unclosed-group = a fost găsită o paranteză de deschidere `(`, dar nu a existat nicio paranteză de închidere `)` după ea. Dacă doriți să căutați un literal `(`, includeți-l între ghilimele duble sau adăugați o bară oblică inversă: `"("` sau `\(`).
search-empty-quote = au fost găsite o pereche de ghilimele duble `""`, dar nu era nimic de căutat între ele. Dacă doriți să căutați ghilimele duble, adăugați înainte barele oblice inverse: `\"\"`.
search-unclosed-quote = a fost găsită ghilimele duble `"`, dar nu a existat un al doilea care să-l închidă. Dacă doriți să căutați un literal `"`, adăugați o bară oblică inversă: `\"`.
search-missing-key = a fost găsit două puncte `:`, dar nu a existat niciun cuvânt cheie înainte de acesta. Dacă doriți să căutați un literal `:`, adăugați o bară oblică inversă: `\:`.
search-unknown-escape = secvența de evacuare `{ $val }` nu este definită. Dacă doriți să căutați o bară oblică inversă `\`, adăugați alta: `\\`.
search-invalid-argument = `{ $term }` a primit un argument nevalid '`{ $argument }`'.
search-invalid-flag-2 = `steagul:` trebuie urmat de un număr de steag valid: `1` (roșu), `2` (portocaliu), `3` (verde), `4` (albastru), `5` (roz), `6 ` (turcoaz), `7` (violet) sau `0` (fără steag).
search-invalid-prop-operator = `prop:{ $val }` trebuie să fie urmat de unul dintre următorii operatori de comparație: `=`, `!=`, `<`, `>`, `<=` sau `>=`.
search-invalid-other = vă rugăm să verificați dacă există greșeli de tastare.

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = se aștepta un număr în „`{ $context }`”, dar s-a găsit „`{ $provided }`”.
search-invalid-whole-number = se aștepta un număr întreg în „`{ $context }`”, dar s-a găsit „`{ $provided }`”.
search-invalid-positive-whole-number = se aștepta un număr întreg pozitiv în „`{ $context }`”, dar s-a găsit „`{ $provided }`”.
search-invalid-negative-whole-number = se aștepta un număr întreg mai mic sau egal cu 0 în „`{ $context }`”, dar s-a găsit „`{ $provided }`”.
search-invalid-answer-button = se aștepta un buton de răspuns între 1-4 în „`{ $context }`”, dar s-a găsit „`{ $provided }`”.

## Column labels in browse screen

search-note-modified = Notiță modificată
search-card-modified = Schimbat

##

# Tooltip for search lines outside browser
search-view-in-browser = Vezi în browser
