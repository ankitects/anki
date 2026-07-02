### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = और जानकारी
card-template-rendering-front-side-problem = फ्रंट टेम्प्लेट में समस्या है:
card-template-rendering-back-side-problem = बैक टेम्प्लेट में समस्या है:
card-template-rendering-browser-front-side-problem = ब्राउज़र-विशिष्ट फ्रंट टेम्प्लेट में समस्या है:
card-template-rendering-browser-back-side-problem = ब्राउज़र-विशिष्ट बैक टेम्प्लेट में समस्या है:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = '{ $tag }' में '{ $missing }' गुम है
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = गुम '{ $missing }'
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = पाया '{ $found }', लेकिन अपेक्षित '{ $expected }'
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = पाया '{ $found }', लेकिन लापता '{ $missing1 }' या '{ $missing2 }'
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = '{ $found }' मिला, लेकिन '{ $field }' नामक कोई फ़ील्ड नहीं है
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = इस कार्ड का फ्रंट खाली है।
card-template-rendering-missing-cloze =
    कार्ड पर कोई क्लॉज़ { $number } नहीं मिला।
    कृपया या तो क्लॉज़ हटाने को जोड़ें, या खाली कार्ड उपकरण का उपयोग करें।
