### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = 更多資訊
card-template-rendering-front-side-problem = 正面模板存在一個問題：
card-template-rendering-back-side-problem = 背面模板存在一個問題：
card-template-rendering-browser-front-side-problem = 瀏覽器特定的正面模板存在一個問題：
card-template-rendering-browser-back-side-problem = 瀏覽器特定的背面模板存在一個問題：
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = 「{ $tag }」中缺少「{ $missing }」
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = 缺少「{ $missing }」
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = 已找到「{ $found }」，但需要「{ $expected }」。
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = 已找到「{ $found }」，但缺少「{ $missing1 }」或「{ $missing2 }」。
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = 已找到「{ $found }」，但沒有名為「{ $field }」的欄位。
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = 這張卡片的正面沒有內容。
card-template-rendering-missing-cloze = 卡片上找不到克漏字 { $number }。請新增一個空格，或使用「空白卡片」工具。
