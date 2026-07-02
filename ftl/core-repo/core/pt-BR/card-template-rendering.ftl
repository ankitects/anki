### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Mais informações
card-template-rendering-front-side-problem = O modelo da frente tem um problema:
card-template-rendering-back-side-problem = O modelo do Verso tem um problema:
card-template-rendering-browser-front-side-problem = O modelo frontal específico do navegador tem um problema:
card-template-rendering-browser-back-side-problem = O modelo do Verso específico do navegador tem um problema:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = Faltando '{ $missing }' em '{ $tag }'
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = Faltando '{ $missing }'
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = Encontrado '{ $found }', mas se esperava '{ $expected }'
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = Encontrado '{ $found }', mas faltando '{ $missing1 }' ou '{ $missing2 }'
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = Encontrado '{ $found }', mas não existe o campo chamado '{ $field }'
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = A frente deste cartão está em branco.
card-template-rendering-missing-cloze =
    Nenhuma omissão { $number } encontrada no cartão.
    Adicione uma omissão de palavras ou use a ferramenta 'Cartas Vazias'.
