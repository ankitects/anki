## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = Ogiltig sökning: { $reason }
search-misplaced-and = ett `and` hittades men förbinder inte två söktermer. Om själva ordet önskas sökas, citera det i dubbla citattecken: `"and"`.
search-misplaced-or = ett `or` hittades men förbinder inte två söktermer. Om själva ordet önskas sökas, citera det i dubbla citattecken: `"or"`.
# Here, the ellipsis "..." may be localised.
search-empty-group = en grupp `(...)` hittades, men det fanns inget mellan parenteserna att söka efter. Om bokstavliga parenteser önskas sökas, citera dem i dubbla citattecken: `"( )"`.
search-unopened-group = en stängningsparentes `)` hittades, men ingen föregående öppningsparentes `(`. Om bokstavligen `)` önskas sökas, citera det i dubbla citattecken eller infoga ett omvänt snedstreck före: `")"` eller `\)`.
search-unclosed-group = en öppningsparentes `)` hittades, men ingen följande stängningsparentes `(`. Om bokstavligen `(` önskas sökas, citera det i dubbla citattecken eller infoga ett omvänt snedstreck före: `"("` eller `\(`.
search-empty-quote = ett par dubbla citattecken `""` hittades, men det fanns inget mellan dem att söka efter. Om bokstavligen dubbla citattecken önskas sökas, infoga omvända snedstreck före: `\"\"`.
search-unclosed-quote = ett inledande citattecken `"` hittades, men inte ett andra att stänga det. Om bokstavligen `"` önskas sökas, infoga ett omvänt snedstreck före: `\"`.
search-missing-key = ett kolon `:` hittades, men det fanns inget nyckelord före det. Om bokstavligen `:` önskas sökas, infoga ett omvänt snedstreck före: `\:`.
search-unknown-escape = avbrottssekvensen `{ $val }` är inte definierad. Om bokstavligen ett omvänt snedstreck `\` önskas sökas, infoga ett till före: `\\`.
search-invalid-argument = `{ $term }` gavs ett ogiltigt argument '`{ $argument }`'.
search-invalid-flag-2 = `flag:` måste följas av en giltig flaggsiffra: `1` (röd), `2` (orange), `3` (grön), `4` (blå), `5` (rosa), `6` (turkos), `7` (lila) or `0` (ingen flagga).
search-invalid-prop-operator = `prop:{ $val }` måste följas av en av följande jämförelseoperatorer: `=`, `!=`, `<`, `>`, `<=` or `>=`.
search-invalid-other = var god kolla efter skrivfel.

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = förväntade ett tal i "`{ $context }`", men hittade "`{ $provided }`".
search-invalid-whole-number = förväntade ett heltal i "`{ $context }`", men hittade "`{ $provided }`".
search-invalid-positive-whole-number = förväntade ett positivt heltal i "`{ $context }`", men hittade "`{ $provided }`".
search-invalid-negative-whole-number = förväntade ett heltal mindre än eller lika med 0 i "`{ $context }`", men hittade "`{ $provided }`".
search-invalid-answer-button = förväntade en svarsknapp mellan 1-4 i "`{ $context }`", men hittade "`{ $provided }`".

## Column labels in browse screen

search-note-modified = Redigerade
search-card-modified = Ändrade

##

# Tooltip for search lines outside browser
search-view-in-browser = Visa i bläsaren
