## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = Ungültige Suche: { $reason }
search-misplaced-and = Es wurde ein `and` gefunden, das nicht zwischen zwei Suchbegriffen steht. Sie können nach dem Wort an sich suchen, indem Sie es mit Anführungszeichen umgeben: `"and"`.
search-misplaced-or = Es wurde ein `or` gefunden, das nicht zwischen zwei Suchbegriffen steht. Sie können nach dem Wort an sich suchen, indem Sie es mit Anführungszeichen umgeben: `"or"`.
# Here, the ellipsis "..." may be localised.
search-empty-group = Es wurde eine Gruppe `( …)` gefunden, jedoch stand nichts zwischen den Klammern, nach dem hätte gesucht werden können. Sie können nach Klammern suchen, indem Sie sie mit Anführungszeichen umgeben: `"()"`.
search-unopened-group = Es wurde eine schließende Klammer `)`,  zuvor jedoch keine öffnende Klammer `(` gefunden. Um nach dem Symbol `)` zu suchen, können Sie es mit Anführungszeichen umgeben oder einen umgekehrten Schrägstrich voranstellen: `")"` oder `\)`.
search-unclosed-group = Es wurde eine öffnende Klammer `(`,  danach jedoch keine schließende Klammer `)` gefunden. Um nach dem Symbol `(` zu suchen, können Sie es mit Anführungszeichen umgeben oder einen umgekehrten Schrägstrich voranstellen: `"("` oder `\(`.
search-empty-quote = Es wurde ein Paar Anführungszeichen `""` gefunden, jedoch stand nichts zwischen diesen, nach dem hätte gesucht werden können. Sie können nach Anführungszeichen suchen, indem Sie umgekehrte Schrägstriche voranstellen: `\"\"`.
search-unclosed-quote = Es wurde ein öffnendes Anführungszeichen `"` gefunden, jedoch kein zweites, um es zu schließen. Sie können nach dem Symbol `"` suchen, indem Sie einen umgekehrten Schrägstrich voranstellen: `\"`.
search-missing-key = Es wurde ein Doppelpunkt `:` gefunden, davor jedoch kein Schlüsselbegriff. Sie können nach dem Symbol `:` suchen, indem Sie einen umgekehrten Schrägstrich voranstellen: `\:`.
search-unknown-escape = Die Escape-Sequenz `{ $val }` ist nicht definiert. Sie können nach einem umgekehrten Schrägstrich `\` suchen, indem Sie einen weiteren voranstellen: `\\`.
search-invalid-argument = `{ $term }` wurde der ungültige Wert „`{ $argument }`“ übergeben.
search-invalid-flag-2 = Nach `flag:` muss eine gültige Flaggennummer eingetragen werden : `1` (rot), `2` (orange), `3` (grün), `4` (blau), `5` (rosa), `6` (türkis), `7` (violett) or `0` (keine Flagge).
search-invalid-prop-operator = Auf `prop:{ $val }` muss einer der folgenden Vergleichsoperatoren folgen: `=`, `!=`, `<`, `>`, `<=` oder `>=`.
search-invalid-other = Bitte auf Tippfehler prüfen.

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = In „`{ $context }`“ wurde eine Zahl erwartet, aber stattdessen „`{ $provided }`“ gefunden.
search-invalid-whole-number = In „`{ $context }`“ wurde eine ganze Zahl erwartet, aber stattdessen „`{ $provided }`“ gefunden.
search-invalid-positive-whole-number = In „`{ $context }`“ wurde eine positive ganze Zahl erwartet, aber stattdessen „`{ $provided }`“ gefunden.
search-invalid-negative-whole-number = In „`{ $context }`“ wurde eine ganze Zahl gleich oder kleiner 0 erwartet, aber stattdessen „`{ $provided }`“ gefunden.
search-invalid-answer-button = In „`{ $context }`“ wurde eine Antwortknopf zwischen 1 und 4 erwartet, aber stattdessen „`{ $provided }`“ gefunden.

## Column labels in browse screen

search-note-modified = Notiz geändert
search-card-modified = Karte geändert

##

# Tooltip for search lines outside browser
search-view-in-browser = In der Kartenverwaltung anschauen
