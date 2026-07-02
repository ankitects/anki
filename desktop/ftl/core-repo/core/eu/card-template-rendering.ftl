### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Informazio gehiago
card-template-rendering-front-side-problem = Aurrealdearen txantiloiak arazo bat du:
card-template-rendering-back-side-problem = Atzealdearen txantiloiak arazo bat du:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = '{ $missing }' falta da hemen: '{ $tag }'
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = '{ $missing }' falta da
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = '{ $found }' aurkitu da, baina '{ $expected }' espero zen
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = '{ $found }' aurkitu da, baina '{ $missing1 }' edo '{ $missing2 }' falta da
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = '{ $found }' aurkitu da, baina ez dago '{ $field }' izeneko eremurik
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = Txartel honen aurrealdea hutsik dago.
card-template-rendering-missing-cloze =
    Ez da { $number }. hutsunerik aurkitu txartelean.
    Gehitu hutsune bat edo erabili "Txartel hutsak" tresna.
