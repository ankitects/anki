## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = Neplatné vyhledávání: { $reason }
search-misplaced-and = bylo nalezeno `and`, ale nespojuje dva vyhledávací výrazy. Chcete-li hledat toto slovo, dejte ho do uvozovek `"and"`.
search-misplaced-or = bylo nalezeno `or`, ale nespojuje dva vyhledávací výrazy. Chcete-li hledat toto slovo, dejte ho do uvozovek `"or"`.
# Here, the ellipsis "..." may be localised.
search-empty-group = byla nalezena skupina `(...)`, ale v závorkách nebylo nic, co by se dalo hledat. Chcete-li hledat závorky, dejte je do uvozovek `"( )"`.
search-unopened-group = byla nalezena uzavírací závorka `)`, ale nebyla před ní žádná otevírací závorka `(`. Chcete-li hledat `)`, dejte ji do uvozovek nebo přidejte zpětné lomítko: `")"` nebo `\)`.
search-unclosed-group = byla nalezena otevírací závorka `(`, ale nebyla za ní žádná uzavírací závorka `)`. Chcete-li hledat `(`, dejte ji do uvozovek nebo přidejte zpětné lomítko: `"("` nebo `\(`.
search-empty-quote = byla nalezena dvojice uvozovek `""`, ale nebylo v nich nic, co by se dalo hledat. Chcete-li hledat uvozovky, přidejte zpětné lomítko `\"\"`.
search-unclosed-quote = byla nalezena uvozovka `"`, ale nebyla zde druhá, která by ji uzavřela. Chcete-li hledat `"`, přidejte zpětné lomítko: `\"`.
search-missing-key = byla nalezena dvojtečka `:`, ale nepředcházelo jí žádné klíčové slovo. Chcete-li hledat `:`, přidejte zpětné lomítko: `\:`.
search-unknown-escape = úniková sekvence `{ $val }` není definována. Chcete-li hledat zpětné lomítko `\`, přidejte ještě jedno: `\\`.
search-invalid-argument = `{ $term }` má neplatný argument „`{ $argument }`“.
search-invalid-flag-2 = Za `flag:` musí být platné číslo příznaku: `1` (červený), `2` (oranžový), `3` (zelený), `4` (modrý), `5` (růžový), `6` (tyrkysový), `7` (purpurový) nebo `0` (žádný příznak).
search-invalid-prop-operator = za `prop:{ $val }` musí být jeden z následujících operátorů porovnání: `=`, `!=`, `<`, `>`, `<=` nebo `>=`.
search-invalid-other = Prosím zkontrolujte překlepy.

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = očekáváno číslo v „`{ $context }`“, ale nalezeno „`{ $provided }`“.
search-invalid-whole-number = očekáváno celé číslo v „`{ $context }`“, ale nalezeno „`{ $provided }`“.
search-invalid-positive-whole-number = očekáváno kladné celé číslo v „`{ $context }`“, ale nalezeno „`{ $provided }`“.
search-invalid-negative-whole-number = očekáváno celé číslo menší než 0 včetně v „`{ $context }`“, ale nalezeno „`{ $provided }`“.
search-invalid-answer-button = očekáváno tlačítko odpovědi mezi 1-4 v „`{ $context }`“, ale nalezeno „`{ $provided }`“.

## Column labels in browse screen

search-note-modified = Poznámka upravena
search-card-modified = Karta upravena

##

# Tooltip for search lines outside browser
search-view-in-browser = Zobrazit v prohlížeči
