## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = Памылковы пошук: { $reason }
search-misplaced-and = знойдзена `and`, але яно не злучае два пошукавых выраза. Калі вы хочаце шукаць само слова, абгарніце яго ў двукоссі: `"and"`.
search-misplaced-or = знойдзена `or`, але яно не злучае два пошукавых выраза. Калі вы хочаце шукаць само слова, абгарніце яго ў двукоссі: `"or"`.
# Here, the ellipsis "..." may be localised.
search-empty-group = знойдзена група `(...)`, але паміж дужак няма нічога для пошуку. Калі вы хочаце шукаць самі дужкі, абгарніце іх у двукоссі: `"()"`.
search-unopened-group = знойдзена закрываючая дужка `)`, але перад ёй не было адкрываючай `(`. Калі вы хочаце шукаць сам літарал `)`, абгарніце яго ў двукоссі або дадайце адваротную косую рысу: `")"` або `\)`.
search-unclosed-group = знойдзена адкрываючая дужка `(`, але перад ёй не было закрываючай `)`. Калі вы хочаце шукаць сам літарал `(`, абгарніце яго ў двукоссі або дадайце адваротную косую рысу: `"("` або `\(`.
search-empty-quote = знойдзена пара двукоссяў `""`, але паміж дужак няма нічога для пошуку. Калі вы хочаце шукаць самі двукоссі, дадайце адваротныя косыя рысы: `\"\"`.
search-unclosed-quote = знойдзена адкрываючае двукоссе `"`, але няма другога, якое бы яго закрыла. Калі вы хочаце шукаць сам літарал `"`, дадайце адваротную косую рысу: `\"`.
search-missing-key = знойдзена двукроп'е `:`, але перад ім няма ключавога слова. Калі вы хочаце шукаць сам літарал `:`, дадайце адваротную косую рысу: `\:`.
search-unknown-escape = экранаваная паслядоўнасць `{ $val }` не вызначана. Калі вы хочаце шукаць саму адваротную косую рысу `\`, дадайце перад ёй яшчэ адну: `\\`.
search-invalid-argument = `{ $term }` мае памылковы аргумент «`{ $argument }`».
search-invalid-flag-2 = пасля выразу `flag:` павінен ісці правільны нумар сцяжка: `1` (чырвоны), `2` (аранжавы), `3` (зялёны), `4` (сіні), `5` (ружовы), `6` (бірузовы), `7` (фіялетавы), `0` (без сцяжку).
search-invalid-prop-operator = услед за `prop:{ $val }` павінен ісці адзін з наступных аператараў параўнання: `=`, `!=`, `<`, `>`, `<=` або `>=`.
search-invalid-other = праверце наяўнасць памылак друку.

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = чакаўся лік у «`{ $context }`», але знойдзена «`{ $provided }`».
search-invalid-whole-number = чакаўся цэлы лік у «`{ $context }`», але знойдзена «`{ $provided }`».
search-invalid-positive-whole-number = чакаўся цэлы дадатны лік у «`{ $context }`», але знойдзена «`{ $provided }`».
search-invalid-negative-whole-number = чакаўся цэлы лік меншы або роўны 0 у «`{ $context }`», але знойдзена «`{ $provided }`».
search-invalid-answer-button = чакалася кнопка адказу паміж 1-4 у «`{ $context }`», але знойдзена «`{ $provided }`».

## Column labels in browse screen

search-note-modified = Нататка зменена
search-card-modified = Картка зменена

##

# Tooltip for search lines outside browser
search-view-in-browser = Праглядзець у браўзеры
