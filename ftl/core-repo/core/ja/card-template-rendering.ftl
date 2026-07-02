### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = 詳細
card-template-rendering-front-side-problem = 表面のテンプレートに不備があります：
card-template-rendering-back-side-problem = 裏面のテンプレートに不備があります：
card-template-rendering-browser-front-side-problem = ブラウザのテーブル用の質問テンプレートに不備があります:
card-template-rendering-browser-back-side-problem = ブラウザのテーブル用の解答テンプレートに不備があります:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = 「{ $tag }」に「{ $missing }」が欠けています
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = 「{ $missing }」が’欠けています
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = 「{ $found }」は「{ $expected }」ではないですか？
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = 「{ $found }」に対応する「{ $missing1 }」または「{ $missing2 }」が欠けています
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = 「{ $found }」と入力されていますが、このノートタイプには「{ $field }」というフィールドが存在しません
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = カードの表面が空白になっています。
card-template-rendering-missing-cloze = カード上に{ $number }番目の穴埋め問題がみつかりません。穴埋め問題を追加するか、[ツール] から [白紙カードをチェック] を実行してください。
