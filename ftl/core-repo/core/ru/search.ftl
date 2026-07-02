## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = Ошибка поиска: { $reason }
search-misplaced-and = было найдено `and`, но оно не соединяет два поисковых термина. Если вы хотите найти само слово, заключите его в двойные кавычки: `"and"`.
search-misplaced-or = было найдено `or`, но оно не соединяет два поисковых термина. Если вы хотите найти само слово, заключите его в двойные кавычки: `"or"`.
# Here, the ellipsis "..." may be localised.
search-empty-group = группа `(...)` была найдена, но между скобками не было ничего, что можно было бы найти. Если вы хотите найти сами скобки, заключите их в двойные кавычки: `"( )"`.
search-unopened-group = была найдена закрывающая скобка `)`, но перед ней не было открывающей скобки `(`. Если вы хотите найти саму `)`, заключите её в двойные кавычки или добавьте обратный слэш: `")"` или `\)`.
search-unclosed-group = была найдена открывающаяся скобка `(`), но за ней не было закрывающей скобки `)`. Если вы хотите найти саму `(`, заключите её в двойные кавычки или добавьте обратный слэш: `"("` или `\(` .
search-empty-quote = найдена пара двойных кавычек `""` без содержания. Если вы хотите искать именно двойные кавычки `""`, добавьте обратные слэши: `\"\"`.
search-unclosed-quote = найдена открывающая кавычка `"` без закрывающей. Если вы хотите искать именно кавычку`"`, добавьте обратный слэш: `\"`.
search-missing-key = найдено двоеточие `:`, но перед ним нет ключевого слова. Если вы хотите искать именно двоеточие `:`, добавьте обратный слэш: `\:`.
search-unknown-escape = неизвестная управляющая последовательность `{ $val }`. Если вы хотите найти обратную косую `\`, то поставьте перед ней ещё одну: `\\`.
search-invalid-argument = `{ $term }` был дан неверный аргумент '`{ $argument }`'.
search-invalid-flag-2 = "флажок:" должен иметь соответствующий номер: "1" (красный), "2" (оранжевый), "3" (зеленый), "4" (голубой), "5" (розовый), "6" (бирюзовый), "7" (фиолетовый) или "0" (нет флажка).
search-invalid-prop-operator = `prop:{ $val }` должен сопровождаться одним из следующих операторов сравнения: `=`, `!=`, `<`, `>`, `<=` или `>=`.
search-invalid-other = проверьте на наличие опечаток.

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = ожидаемый результат в "`{ $context }`", но найдено "`{ $provided }`".
search-invalid-whole-number = ожидаемый результат в "`{ $context }`", но найдено "`{ $provided }`".
search-invalid-positive-whole-number = ожидаемый результат в "`{ $context }`", но найдено "`{ $provided }`".
search-invalid-negative-whole-number = ожидаемое количество меньше или равно 0 в "`{ $context }`", но найдено "`{ $provided }`".
search-invalid-answer-button = ожидаемая кнопка ответа между 1-4 в { $context }`", но найдено "`{ $provided }`".

## Column labels in browse screen

search-note-modified = Запись изменена
search-card-modified = Карточка изменена

##

# Tooltip for search lines outside browser
search-view-in-browser = Показать в браузере
