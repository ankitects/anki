## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = Búsqueda no válida: { $reason }
search-misplaced-and = se encontró un `and`, pero no está conectando dos términos de búsqueda. Si desea buscar la palabra en sí, enciérrela entre comillas dobles: `"and"`.
search-misplaced-or = se encontró un `or`, pero no está conectando dos términos de búsqueda. Si desea buscar la palabra en sí, enciérrela entre comillas dobles: `"or"`.
# Here, the ellipsis "..." may be localised.
search-empty-group = se encontró un grupo `(...)`, pero no había nada entre paréntesis que buscar. Si desea buscar paréntesis literales, enciérrelos entre comillas dobles: `"( )"`.
search-unopened-group = se encontró un paréntesis de cierre `)`, pero no había ningún paréntesis de apertura `(` precediéndolo. Si desea buscar un paréntesis  `)` literal, envuélvalo entre comillas dobles o anteponga una barra invertida: `")"` o ` \)`.
search-unclosed-group = se encontró un paréntesis de apertura`)`, pero no había ningún paréntesis de cierre`(` precediéndolo. Si desea buscar un paréntesis `(` literal, envuélvalo entre comillas dobles o anteponga una barra invertida: `"("` o ` \(` .
search-empty-quote = se encontró un par de comillas dobles `""`, pero no había nada entre ellas para buscar. Si desea buscar comillas dobles literales, anteponga barras invertidas: `\"\"` .
search-unclosed-quote = se encontró comillas de apertura `"`, pero no se encontró comillas de cierre. Si solo desea buscar por `"` literal, anteponga una barra invertida: `\"`.
search-missing-key = Se encontraron dos puntos `:`, pero no había ninguna palabra clave que lo precediera. Si desea buscar `:` literal, coloque una barra invertida: `\:`.
search-unknown-escape = la secuencia de escape `{ $val }` no está definida. Si desea buscar una barra invertida literal `\`, anteponga otra: `\\`.
search-invalid-argument = `{ $term }` recibió un parámetro inválido '`{ $argument }`' .
search-invalid-flag-2 = `flag:` debe ir seguido de un número de marcador válido: `1` (rojo), `2` (naranja), `3` (verde), `4` (azul), `5` (rosa), `6 ` (turquesa), `7` (morado) o `0` (no marcada).
search-invalid-prop-operator = `prop:{ $val }` debe ir seguido de uno de los siguientes operadores de comparación: `=`, `!=`, `<`, `>`, `<=` o `>=`.
search-invalid-other = Por favor, compruebe si hay errores tipográficos.

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = Era esperado un número en "`{ $context }`", pero se encontró  "`{ $provided }`".
search-invalid-whole-number = Era esperado un número entero en "`{ $context }`", pero se encontró "`{ $provided }`".
search-invalid-positive-whole-number = Era esperado un número entero positivo en "`{ $context }`", pero se encontró "`{ $provided }`".
search-invalid-negative-whole-number = Era esperado un número menor o igual a 0 en "`{ $context }`", pero se encontró "`{ $provided }`".
search-invalid-answer-button = Se esperaba un botón de respuesta entre 1-4 en "`{ $context }`",  pero se encontró "`{ $provided }`".

## Column labels in browse screen

search-note-modified = Editado
search-card-modified = Modificada

##

# Tooltip for search lines outside browser
search-view-in-browser = Ver en el navegador
