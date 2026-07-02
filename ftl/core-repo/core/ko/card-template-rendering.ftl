### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = 더 많은 정보
card-template-rendering-front-side-problem = 앞면 탬플릿에 오류가 있습니다:
card-template-rendering-back-side-problem = 뒷면 탬플릿에 오류가 있습니다:
card-template-rendering-browser-front-side-problem = 특정 브라우저용 앞면 템플릿에 오류가 있습니다:
card-template-rendering-browser-back-side-problem = 특정 브라우저용 뒷면 템플릿에 오류가 있습니다:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = '{ $tag }'에서 '{ $missing }'이 빠졌습니다
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = '{ $missing }'이 빠졌습니다
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = '{ $found }'를 찾았지만 '{ $expected }'가 필요합니다
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = '{ $found }'를 찾았지만 '{ $missing1 }' 또는 '{ $missing2 }'이 빠졌습니다
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = '{ $found }'를 찾았지만 '{ $field }'라는 필드는 없습니다
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = 카드의 앞면이 빈칸입니다.
card-template-rendering-missing-cloze =
    { $number }번 빈칸은 카드에 없습니다.
    빈칸을 추가하거나, 빈 카드 도구를 사용하세요.
