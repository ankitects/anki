### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Больше информации
card-template-rendering-front-side-problem = Проблема с шаблоном лица:
card-template-rendering-back-side-problem = Проблема с шаблоном оборота:
card-template-rendering-browser-front-side-problem = Проблема в шаблоне лица при просмотре:
card-template-rendering-browser-back-side-problem = Проблема в шаблоне оборота при просмотре:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = В «{ $tag }» не хватает «{ $missing }»
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = Не хватает «{ $missing }»
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = Использован «{ $found }», но должен быть «{ $expected }»
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = Использован «{ $found }», но не хватает «{ $missing1 }» или «{ $missing2 }»
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = Использовано «{ $found }», но поля с именем «{ $field }» нет
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = Лицо этой карточки пустое.
card-template-rendering-missing-cloze =
    Задание с пропусками { $number } не найдено в карточке.
    Вы можете использовать инструмент "Пустые карточки" для удаления этой пустой карточки.
