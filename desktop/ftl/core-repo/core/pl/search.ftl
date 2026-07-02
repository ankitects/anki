## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = Nieprawidłowe wyszukiwanie: { $reason }
search-misplaced-and = znaleziono `and`, jednak nie łączy ono dwóch wyszukiwanych terminów. Jeśli chcesz znaleźć konkretnie to słowo, umieść je w cudzysłowie: `"and"`.
search-misplaced-or = znaleziono `or`, jednak nie łączy ono dwóch wyszukiwanych terminów. Jeśli chcesz znaleźć konkretnie to słowo, umieść je w cudzysłowie: `"or"`.
# Here, the ellipsis "..." may be localised.
search-empty-group = Znaleziono grupę: `(...)`, jednak w nawiasach nie znajdowało się nic do wyszukania. Jeśli chcesz wyszukać nawiasów, ujmij je w cytat: "( )"`.
search-unopened-group = Znaleziono nawias zamykający - `)`, jednak nie było przed nim nawiasu otwierającego - `(`. Jeśli chcesz wyszukać `)`, ujmij go w cytat lub poprzedź odwrotnym ukośnikiem: `")"` lub `\)`.
search-unclosed-group = Znaleziono nawias otwierający - `(`, jednak nie było po nim nawiasu zamykającego - `)`. Jeśli chcesz wyszukać `(`, ujmij go w cytat lub poprzedź odwrotnym ukośnikiem: `"("` lub `\(`.
search-empty-quote = znaleziono parę podwójnych cudzysłowów `""`, ale nie ma między nimi nic do wyszukania. Jeśli chcesz wyszukać właśnie parę cudzysłowów, dodaj backslash: `\"\"`.
search-unclosed-quote = znaleziono podwójny cudzysłów `", ale nie ma żadnego zamykającego. Jeśli chcesz wyszukać właśnie cudzysłów, dodaj backslash: `\"`.
search-missing-key = pojawił się dwukropek `:` niepoprzedzony słowem kluczowym. Jeśli chcesz wyszukać znak `:`, poprzedź go ukośnikiem: `\:`.
search-unknown-escape = Sekwencja ucieczki `{ $val }` nie jest zdefiniowana. Jeśli chcesz wyszukać ukośnik odwrotny `\`, dodaj przed nim jeszcze jeden ukośnik: `\\`.
search-invalid-argument = `{ $term }` nadano nieprawidłowy  argument '`{ $argument } '`.
search-invalid-flag-2 = Po tekście wyszukiwania `flag:` należy wpisać prawidłowy numer flagi:  "1" (czerwony),  "2" (pomarańczowy), "3" (zielony), "4" (niebieski), "5" (różowy), "6" (turkusowy), "7" (fioletowy) lub "0" (brak flagi).
search-invalid-prop-operator = po `prop:{ $val }` musi następować jeden z operatorów porównania: `=`, `!=`, `<`, `>`, `<=` lub `>=`.
search-invalid-other = Sprawdź, czy nie ma literówek.

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = oczekiwano liczby w "`{ $context }`", lecz znaleziono "`{ $provided }`".
search-invalid-whole-number = oczekiwano liczby całkowitej w "`{ $context } "`, lecz znaleziono "`{ $provided } "`.
search-invalid-positive-whole-number = oczekiwano dodatniej liczby całkowitej w "`{ $context } "`, lecz znaleziono "`{ $provided } "`.
search-invalid-negative-whole-number = oczekiwano liczby całkowitej mniejszej lub równej 0 w "`{ $context } "`, lecz znaleziono "`{ $provided } "`.
search-invalid-answer-button = oczekiwano przycisku odpowiedzi w granicach 1-4 w "`{ $context }`", lecz znaleziono "`{ $provided }`".

## Column labels in browse screen

search-note-modified = Notatka zmodyfikowana
search-card-modified = Karta zmodyfikowana

##

# Tooltip for search lines outside browser
search-view-in-browser = Zobacz w przeglądarce
