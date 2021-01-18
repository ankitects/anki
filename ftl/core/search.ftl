## Errors shown when invalid search input is encountered.
## Text wrapped in `backticks` is literal search input, and should generally not to be altered.

search-invalid-search = Invalid search: { $reason }
search-misplaced-and = an `and` was found but it is not connecting two search terms. If you want to search for the word itself, wrap it in double quotes: `"and"`.
search-misplaced-or = an `or` was found but it is not connecting two search terms. If you want to search for the word itself, wrap it in double quotes: `"or"`.
# Here, the ellipsis "..." may be localised.
search-empty-group = a group `(...)` was found, but there was nothing between the brackets to search for. If you want to search for literal brackets, wrap them in double quotes: `"( )"`.
search-unopened-group = a closing bracket `)` was found, but there was no opening bracket `(` preceding it. If you want to search for the literal `)`, wrap it in double quotes or prepend a backslash: `")"` or `\)`.
search-unclosed-group = an opening bracket `(` was found, but there was no closing bracket `)` following it. If you want to search for the literal `(`, wrap it in double quotes or prepend a backslash: `"("` or `\(` .
search-empty-quote = a pair of double quotes `""` was found but there was nothing between them to search for. If you want to search for literal double quotes, prepend backslashes: `\"\"`.
search-unclosed-quote = an opening double quote `"` was found but there was no second one to close it. If you want to search for the literal `"`, prepend a backslash: `\"`.
search-missing-key = a colon `:` was found but there was no keyword preceding it. If you want to search for the literal `:`, prepend a backslash: `\:`.
search-unknown-escape = the escape sequence `{ $val }` is not defined. If you want to search for the literal backslash `\`, prepend another one: `\\`.
search-invalid-id-list = note or card id lists must be comma-separated numbers.
search-invalid-argument = `{ $term }` was given an invalid argument '`{ $argument }`'.
search-invalid-flag = `flag:` must be followed by a valid flag number: `1` (red), `2` (orange), `3` (green), `4` (blue) or `0` (no flag).
search-invalid-followed-by-positive-days = `{ $term }` must be followed by a positive number of days.
search-invalid-rated-days = `rated:` must be followed by a positive number of days.
search-invalid-rated-ease = `{ $val }:` must be followed by `1` (again), `2` (hard), `3` (good) or `4` (easy).
search-invalid-prop-operator = `prop:{ $val }` must be followed by one of the comparison operators: `=`, `!=`, `<`, `>`, `<=` or `>=`.
search-invalid-prop-float = `prop:{ $val }` must be followed by a decimal number.
search-invalid-prop-integer = `prop:{ $val }` must be followed by a whole number.
search-invalid-prop-unsigned = `prop:{ $val }` must be followed by a non-negative whole number.
search-invalid-number = `{ $val }`: invalid number provided
search-invalid-did = `did:` must be followed by a valid deck id.
search-invalid-mid = `mid:` must be followed by a note type id.
search-invalid-other = please check for typing mistakes.

## Column labels in browse screen

search-note-modified = Note Modified
search-card-modified = Card Modified

##

