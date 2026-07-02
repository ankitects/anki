## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = Malĝusta serĉpeto: { $reason }
search-misplaced-and = trovis vorton `and`, sed ĝi ne ligas du serĉatajn vortojn. Se vi volas serĉi tiun ĉi vorton, enigu ĝin inter simplaj citiloj: `"and"`.
search-misplaced-or = trovis vorton `or`, sed ĝi ne ligas du serĉatajn vortojn. Se vi volas serĉi tiun ĉi vorton, enigu ĝin inter simplaj citiloj: `"or"`.
# Here, the ellipsis "..." may be localised.
search-empty-group = trovis grupon `(…)`, sed estis nenio inter krampoj por serĉi. Se vi volas serĉi krampojn, enigu ilin inter simplaj citiloj: `"( )"`.
search-unopened-group = trovis finan krampon `)`, sed ne trovis komencan krampon `(` antaŭ ĝi. Se vi volas serĉi `)`, enigu ĝin inter simplaj citiloj aŭ antaŭiĝu ĝin per malsuprenstreko: `")"` aŭ `\)`.
search-unclosed-group = trovis komencan krampon `(`, sed ne trovis finan krampon `)` post ĝi. Se vi volas serĉi `(`, enigu ĝin inter simplaj citiloj aŭ antaŭigu ĝin per malsuprenstreko: `"("` aŭ `\(`.
search-empty-quote = trovis paron da simplaj citiloj `""`, sed neniun tekston inter ili por serĉi. Se vi volas serĉi paron da simplaj citiloj, antaŭigu ilin per malsuprenstrekoj: `\"\"`.
search-unclosed-quote = trovis simplan citilon `"`, sed ne trovis alian finan simplan citilon. Se vi volas serĉi `"`, antaŭigu ĝin per malsuprenstreko: `\"`.
search-missing-key = trovis dupunkton `:`, sed ne trovis serĉvorton antaŭ ĝi. Se vi volas serĉi `:`, anstaŭigu ĝin per malsuprenstreko: `\:`.
search-unknown-escape = la kodŝanĝa tekstĉeno `{ $val }` ne estas difinita. Se vi volas serĉi malsuprenstrekon `\`, antaŭigu ĝin per unu alia malsuprenstreko: `\\`.
search-invalid-argument = `{ $term }` ricevis eraran argumenton “`{ $argument }`”.
search-invalid-flag-2 = post `flag:` estu ĝusta numero de flago: `1` (ruĝa), `2` (oranĝa), `3` (verda), `4` (blua), `5` (rozkolora), `6` (turkisa), `7` (violkolora) aŭ `0` (neniu flago).
search-invalid-prop-operator = post `prop:{ $val }` estu unu el la jenaj kompar-operaciiloj: `=`, !=`, `<`, `>`, `<=` aŭ `>=`.
search-invalid-other = kontrolu pri skriberaroj.

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = atendis nombron en “`{ $context }`”, sed trovis “`{ $provided }`”.
search-invalid-whole-number = atendis plennombron en “`{ $context }`”, sed trovis “`{ $provided }`”.
search-invalid-positive-whole-number = atendis pozitivan plennombron en “`{ $context }`”, sed trovis “`{ $provided }`”.
search-invalid-negative-whole-number = atendis plennombron malpli grandan aŭ egalan al 0 en “`{ $context }`”, sed trovis “`{ $provided }`”.
search-invalid-answer-button = atendis respondobutonon inter 1–4 en “`{ $context }`”, sed trovis “`{ $provided }`”.

## Column labels in browse screen

search-note-modified = Modifis noton
search-card-modified = Modifis karton

##

# Tooltip for search lines outside browser
search-view-in-browser = Montri en foliumilo
