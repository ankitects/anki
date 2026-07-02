### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = ଅଧିକ ସୂଚନା
card-template-rendering-front-side-problem = ସାମ୍ନା ଟେମ୍ପଲେଟରେ ଏକ ସମସ୍ୟା ଅଛି:
card-template-rendering-back-side-problem = ପଛ ଟେମ୍ପଲେଟରେ ଏକ ସମସ୍ୟା ଅଛି:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = '{ $tag }' ରେ '{ $missing }' ନିଖୋଜ ଅଛି
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = '{ $missing }' ନିଖୋଜ ଅଛି
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = '{ $found }' ମିଳିଲା, କିନ୍ତୁ '{ $expected }' ଆଶା କରାଯାଇଥିଲା
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = '{ $found }' ମିଳିଲା, କିନ୍ତୁ '{ $missing1 }' କିମ୍ବା '{ $missing2 }' ନିଖୋଜ ଅଛି
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = '{ $found }' ମିଳିଲା, କିନ୍ତୁ '{ $field }' ନାମକ କୌଣସି କ୍ଷେତ୍ର ନାହିଁ
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = ଏହି ପତ୍ରର ସାମ୍ନା ଭାଗ ଖାଲି ଅଛି।
card-template-rendering-missing-cloze =
    ପତ୍ର ରେ କୌଣସି କ୍ଲୋଜ୍ { $number } ମିଳିଲା ନାହିଁ।
    ଦୟାକରି ଗୋଟିଏ କ୍ଲୋଜ୍ ବିଲୋପ ଯୋଗ କରନ୍ତୁ, କିମ୍ବା "ଖାଲି ପତ୍ରଗୁଡ଼ିକ" ଉପକରଣ ବ୍ୟବହାର କରନ୍ତୁ।
