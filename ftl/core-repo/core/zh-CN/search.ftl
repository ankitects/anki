## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = 无效的搜索：{ $reason }
search-misplaced-and = 搜索条件中包含一个「and」，但未用来连接搜索项。若想搜索「and」这个单词本身，请将其置于双引号中：「"and"」。
search-misplaced-or = 搜索条件中包含一个「or」，但未用来连接搜索项。若想搜索「or」这个单词本身，请将其置于双引号中：「"or"」。
# Here, the ellipsis "..." may be localised.
search-empty-group = 搜索条件中包含一组「(...)」，但其括号内并无可搜索的内容。若想搜索括号本身，请将其置于双引号中：「"( )"」。
search-unopened-group = 搜索条件中包含一个右括号「)」，但在其前未找到与其对应的左括号「(」。若想搜索右括号本身，请将其置于双引号中或在其前加上反斜杠：「")"」或「\)」。
search-unclosed-group = 搜索条件中包含一个左括号「(」，但在其后未找到与其对应的右括号「)」。若想搜索左括号本身，请将其置于双引号中或在其前加上反斜杠：「"("」或「\(」。
search-empty-quote = 搜索条件中包含一对双引号「""」，但其引号内并无可没有搜索内容。若想搜索双引号本身，请在其前加上反斜杠：「\"\"」。
search-unclosed-quote = 搜索条件中包含一个双引号「"」，但未找到第二个双引号来结束。若想搜索双引号本身，请在其前加上反斜杠：「\"」。
search-missing-key = 搜索条件中包含一个冒号「:」，但其之前未找到关键字。若想搜索冒号本身，请在其前加一个反斜杠：「\:」。
search-unknown-escape = 转义字符「{ $val }」未定义。若想搜索反斜杠本身「\」，请在其前再加一个反斜杠：「\\」。
search-invalid-argument = `{ $term }` 接收到了无效的参数 `{ $argument }`。
search-invalid-flag-2 = `flag:` 后必须跟上有效旗标序号：`1`（红色）、`2`（橙色）、`3`（绿色）、`4`（蓝色）、`5`（粉色）、`6`（青色）、`7`（紫色）或 `0`（无旗标）。
search-invalid-prop-operator = `prop:{ $val }` 后面必须是下列比较运算符之一：`=`、`!=`、`<`、`>`、`<=`或`>=`。
search-invalid-other = 请检查输入是否有误。

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = 「`{ $context }`」中应为数字，但实际却是「`{ $provided }`」。
search-invalid-whole-number = 「`{ $context }`」中应为整数，但实际却是「`{ $provided }`」 。
search-invalid-positive-whole-number = 「`{ $context }`」中应为正整数，但实际却是「`{ $provided }`」 。
search-invalid-negative-whole-number = 「`{ $context }`」中应为负整数或零，但实际却是「`{ $provided }`」。
search-invalid-answer-button = 「`{ $context }`」中应为 1-4 的回答按钮，但实际却是「`{ $provided }`」。

## Column labels in browse screen

search-note-modified = 笔记修改日期
search-card-modified = 卡片修改日期

##

# Tooltip for search lines outside browser
search-view-in-browser = 在浏览器中查看
