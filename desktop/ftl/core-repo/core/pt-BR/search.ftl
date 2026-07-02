## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = Pesquisa inválida: { $reason }
search-misplaced-and = um `and` foi encontrado, mas não está conectando dois termos de pesquisa. Se você deseja pesquisar a própria palavra, coloque-a entre aspas duplas: `"and"`.
search-misplaced-or = um `or` foi encontrado, mas não está conectando dois termos de pesquisa. Se você deseja pesquisar a própria palavra, coloque-a entre aspas duplas: `"or"`.
# Here, the ellipsis "..." may be localised.
search-empty-group = m grupo `(...)` foi encontrado, mas não havia nada entre os parênteses para pesquisar. Se você quiser pesquisar parênteses literais, coloque-os entre aspas duplas: `"()"`.
search-unopened-group = um parênteses de fechamento `)` foi encontrado, mas não havia nenhum parênteses de abertura `(` precedendo-o. Se você deseja pesquisar um literal `)`, coloque-o entre aspas duplas ou coloque uma barra invertida antes: `") "` ou ` \) `
search-unclosed-group = um parênteses de abertura `(` foi encontrado, mas não havia nenhum parênteses de fechamento `)` seguindo-o. Se você quiser pesquisar por um `(` literalmente, coloque-o entre aspas duplas ou coloque uma barra invertida no início: `" ("` ou `\ (`.
search-empty-quote = um par de aspas duplas `""` foi encontrado, mas não havia nada entre elas para pesquisar. Se você deseja pesquisar por aspas duplas literais, acrescente barras invertidas: `\"\"`.
search-unclosed-quote = uma aspa dupla de abertura `" `foi encontrada, mas não havia nenhuma segunda para fechá-la. Se você deseja pesquisar por uma `"` literal, coloque uma barra invertida no início: `\"`.
search-missing-key = dois pontos `:` foram encontrados, mas não havia nenhuma palavra-chave precedendo-o. Se você quiser pesquisar por um `:` literal, coloque uma barra invertida: `\:`.
search-unknown-escape = a sequência de escape `{ $val }` não está definida. Se você deseja pesquisar por uma barra invertida literal `\`, acrescente outra: `\\`.
search-invalid-argument = `{ $term }` recebeu um argumento inválido '`{ $argument }`'.
search-invalid-flag-2 = `flag:` deve ser seguido por um número de bandeira válido: `1` (vermelho),` 2` (laranja), `3` (verde),` 4` (azul), `5` (rosa),` 6 `(turquesa),` 7` (roxo) ou `0` (sem bandeira).
search-invalid-prop-operator = `prop:{ $val }` deve ser seguido por um dos seguintes operadores de comparação: `=`, `! =`, `<`, `>`, `<=` ou `> =`.
search-invalid-other = Por favor, verifique se existem erros de digitação.

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = número em "`{ $context }`" esperado, mas "`{ $provided }`" encontrado.
search-invalid-whole-number = número inteiro em "`{ $context }`" esperado, mas "`{ $provided }`" encontrado.
search-invalid-positive-whole-number = número inteiro positivo em "`{ $context }`" esperado, mas "`{ $provided }`" encontrado.
search-invalid-negative-whole-number = número inteiro menor ou igual a 0 em "`{ $context }`" esperado, mas "`{ $provided }`" encontrado.
search-invalid-answer-button = botão de resposta entre 1-4 em "`{ $context }`" esperado, mas "`{ $provided }`" encontrado.

## Column labels in browse screen

search-note-modified = Nota Modificada
search-card-modified = Cartão Modificado

##

# Tooltip for search lines outside browser
search-view-in-browser = Visualizar no painel
