### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Ytterligare information
card-template-rendering-front-side-problem = Framsidesmall har ett problem:
card-template-rendering-back-side-problem = Baksidesmall har ett problem:
card-template-rendering-browser-front-side-problem = Bläddrarspecifik framsidesmall har ett problem:
card-template-rendering-browser-back-side-problem = Bläddrarspecifik baksidesmall har ett problem:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = Saknar "{ $missing }" i "{ $tag }"
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = Saknar "{ $missing }"
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = Stötte på "{ $found }", men förväntade "{ $expected }"
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = Stötte på "{ $found }", men saknar "{ $missing1 }" eller "{ $missing2 }"
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = Stötte på "{ $found }", men det finns inget fält som kallas "{ $field }"
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = Detta korts framsida är tom.
card-template-rendering-missing-cloze =
    Ingen lucktext { $number } hittades på kort.
    Var god lägg till en textlucka, eller använd verktyget Töm kort.
