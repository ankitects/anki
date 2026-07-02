## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = Неправильний пошук: { $reason }
search-misplaced-and = знайдено `і`, але воно не з'єднує два шукані терміни. Якщо Ви хочете знайти таке слово, візьміть його в подвійні лапки: `"і"`.
search-misplaced-or = знайдено `або`, але воно не з'єднує два шукані терміни. Якщо Ви хочете знайти таке слово, візьміть його в подвійні лапки: `"або"`.
# Here, the ellipsis "..." may be localised.
search-empty-group = знайдено групу `(...)`, але в дужках порожньо. Якщо Ви хочете шукати саме дужки, візьміть їх у подвійні лапки: `"( )"`.
search-unopened-group = знайдено праву дужку `)`, але перед нею нема лівої дужки. Якщо Ви хочете шукати ліву дужку як літерал ")", візьміть її в подвійні лапки або додайте зворотну похилу риску: `")"` або `\ )`.
search-unclosed-group = знайдено ліву дужку `(`, але після неї нема правої дужки `)`. Якщо Ви хочете шукати літерал правої дужки `(`, візьміть її в подвійні лапки або додайте зворотну похилу риску: `"("` або `\(` .
search-empty-quote = знайдено пару подвійних лапок `""`, але всередині порожньо. Якщо Ви хочете шукати подвійні лапки як літерал, додайте перед ними зворотні зворотні похилі риски: `\"\"`.
search-unclosed-quote = знайдено ліву подвійну лапку `"`, але немає другої, яка її закриває. Якщо Ви хочете шукати літерал `"`, додайте перед нею зворотну похилу риску: `\"`.
search-missing-key = знайдено двокрапку `:`, але перед нею немає ключового слова. Якщо Ви хочете шукати двокрапку як літерал, додайте перед нею зворотну похилу риску: `\:`.
search-unknown-escape = не визначено керівну послідовність `{ $val }`. Якщо Ви хочете шукати зворотну похилу риску `\` як літерал, додайте перед нею ще одну: `\\`.
search-invalid-argument = "{ $term }" отримав неправильний аргумент "{ $argument }".
search-invalid-flag-2 = Після `flag:` має бути номер прапорця: `1` (червоний), `2` (помаранчевий), `3` (зелений), `4` (синій), `5` (рожевий), `6 ` (бірюзовий), `7` (фіолетовий) або `0` (без прапорця).
search-invalid-prop-operator = Після `prop:{ $val }` повинен бути одним із операторів порівняння: `=`, `!=`, `<`,`>`, `>=` або `<=`.
search-invalid-other = перевірте помилки в наборі.

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = очікуване число в "`{ $context }`", але знайдено "`{ $provided }`".
search-invalid-whole-number = очікуване ціле число в "`{ $context }`", але знайдено "`{ $provided }`".
search-invalid-positive-whole-number = очікуване додатне ціле число в "`{ $context }`", але знайдено "`{ $provided }`".
search-invalid-negative-whole-number = очікуване ціле число, менше або рівне за 0 у "`{ $context }`", але знайдено "`{ $provided }`".
search-invalid-answer-button = очікувана кнопка відповіді між 1-4 у "`{ $context }`", але знайдено "`{ $provided }`".

## Column labels in browse screen

search-note-modified = Відредаговано
search-card-modified = Змінено

##

# Tooltip for search lines outside browser
search-view-in-browser = Переглянути у навігаторі
