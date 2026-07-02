### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Tuilleadh eolais
card-template-rendering-front-side-problem = Teimpléad tosaigh lochtach:
card-template-rendering-back-side-problem = Teimpléad cúil lochtach:
card-template-rendering-browser-front-side-problem = Teimpléad tosaigh an bhrabhsálaithe seo lochtach:
card-template-rendering-browser-back-side-problem = Teimpléad cúil an bhrabhsálaithe seo lochtach:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = '{ $missing }'  ar iarraidh i '{ $tag }'
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = '{ $missing }'  ar iarraidh
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = Aimsíodh '{ $found }' agus súil le '{ $expected }'
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = Aimsíodh '{ $found }', ach tá '{ $missing1 }' nó '{ $missing2 }'  ar iarraidh
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = Aimsíodh '{ $found }', ach níl a leithéid de réimse agus '{ $field }' ann
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = Tá tosach an chárta seo bán.
card-template-rendering-missing-cloze =
    Níl iomlánú { $number } ar an gcárta seo.
    
    Cuir iomlánú nua leis, nó oibrigh an uirlis 'Cártaí Folmha' chun é a scriosadh.
