### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Vairāk informācijas
card-template-rendering-front-side-problem = Priekšpuses veidnē ir nepilnība:
card-template-rendering-back-side-problem = Aizmugures veidnē ir nepilnība:
card-template-rendering-browser-front-side-problem = Pārlūkam paredzētajā priekšpuses veidnē ir nepilnība:
card-template-rendering-browser-back-side-problem = Pārlūkam paredzētajā aizmugures veidnē ir nepilnība:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = „{ $tag }” trūkst „{ $missing }”
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = Trūkst „{ $missing }”
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = Atrasts „{ $found }”, bet tika gaidīts „{ $expected }”
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = Atrasts „{ $found }”, bet trūkst „{ $missing1 }” vai „{ $missing2 }”
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = Atrasts „{ $found }”, bet nav lauka ar nosaukumu „{ $field }”
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = Kartītes priekšpuse ir tukša.
card-template-rendering-missing-cloze =
    Kārtī nav atrasts atstarpju aizpildīšanas (cloze) ({ $number }) elements.
    Lūdzu, vai nu pievienojiet atstarpju aizpildīšanas elementu (cloze deletion), vai izmantojiet Tukšo kāršu rīku
