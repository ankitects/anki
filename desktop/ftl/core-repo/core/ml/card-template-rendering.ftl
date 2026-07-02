### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = കൂടുതൽ വിവരങ്ങൾ
card-template-rendering-front-side-problem = ഫ്രണ്ട് ടെംപ്ലേറ്റിന് ഒരു പ്രശ്നമുണ്ട്:
card-template-rendering-back-side-problem = ബാക്ക് ടെംപ്ലേറ്റിന് ഒരു പ്രശ്നമുണ്ട്:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = '{ $missing }' '{ $tag }'-ൽ കാണുന്നില്ല
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = '{ $missing }' കാണുന്നില്ല
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = '{ $found }' കണ്ടെത്തി, പക്ഷേ പ്രതീക്ഷിച്ചത് '{ $expected }'
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = '{ $found }' കണ്ടെത്തി, പക്ഷേ '{ $missing1 }' 'അല്ലെങ്കിൽ '{ $missing2 }' കാണുന്നില്ല
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = '{ $found }' കണ്ടെത്തി, പക്ഷേ '{ $field }' എന്ന് വിളിക്കുന്ന ഒരു മണ്ഡലവും ഇല്ല.
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = ഈ കാർഡിന്റെ മുൻഭാഗം ശൂന്യമാണ്.
card-template-rendering-missing-cloze =
    കാർഡിൽ ക്ലോസ് { $number } കണ്ടെത്തിയിട്ടില്ല.
    ഒന്നുകിൽ ഒരു 'ക്ലോസ് ഇല്ലാതാക്കൽ' ചേർക്കുക, അല്ലെങ്കിൽ 'കാർഡുകൾ ശൂന്യമാക്കുക' എന്ന ഉപകരണം ഉപയോഗിക്കുക.
