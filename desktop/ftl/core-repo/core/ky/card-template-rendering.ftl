### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Көбүрөөк маалымат
card-template-rendering-front-side-problem = Алдыңкы калыпта көйгөй бар:
card-template-rendering-back-side-problem = Арткы калыпта көйгөй бар:
card-template-rendering-browser-front-side-problem = Браузерге тиешелүү алдыңкы калыпта көйгөй бар:
card-template-rendering-browser-back-side-problem = Браузерге тиешелүү арткы калыпта көйгөй бар:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = '{ $tag }' ичинде '{ $missing }' жок
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = '{ $missing }' жок
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = '{ $found }' табылды, бирок '{ $expected }' күтүлгөн
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = '{ $found }' табылды, бирок '{ $missing1 }' же '{ $missing2 }' жок
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = '{ $found }' табылды, бирок '{ $field }' аттуу талаа жок
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = Бул картанын алдыңкы бети бош.
card-template-rendering-missing-cloze =
    Картада { $number } номериндеги жашыруун сөз табылган жок.
    Сураныч, жашыруун сөз белгисин кошуңуз же "Бош карталар" куралын колдонуңуз.
