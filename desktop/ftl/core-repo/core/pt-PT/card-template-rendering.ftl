### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Mais informação
card-template-rendering-front-side-problem = O modelo da frente tem um problema:
card-template-rendering-back-side-problem = O modelo do verso tem um problema:
card-template-rendering-browser-front-side-problem = O modelo da frente criado para o navegador ("browser") tem um problema:
card-template-rendering-browser-back-side-problem = O modelo do verso criado para o navegador ("browser") tem um problema:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = '{ $missing }' falta(m) em '{ $tag }'
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = '{ $missing }' em falta
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = Encontrado '{ $found }', quando era esperado '{ $expected }'
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = Encontrado '{ $found }', mas tem em falta '{ $missing1 }' ou '{ $missing2 }'
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = Encontrado '{ $found }', mas não existe nenhum campo chamado '{ $field }'
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = A frente desta ficha está vazia.
card-template-rendering-missing-cloze =
    Não foi encontrada a omissão { $number } na ficha.
    Por favor adicione-a, ou utilize a ferramenta "Fichas Vazias".
