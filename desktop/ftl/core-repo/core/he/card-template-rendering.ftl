### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = מידע נוסף
card-template-rendering-front-side-problem = יש בעיה בתבנית הקדמית:
card-template-rendering-back-side-problem = יש בעיה בתבנית האחורית:
card-template-rendering-browser-front-side-problem = דפדוף- נמצאה בעיה בצד הקדמי של הכרטיס.
card-template-rendering-browser-back-side-problem = דפדוף- נמצאה בעיה בצד האחורי של הכרטיס.
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = חסר '{ $missing }' ב- '{ $tag }'
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = חסר '{ $missing }'
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = נמצא '{ $found }', במקום '{ $expected }'
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = '{ $found }' נמצא, אך חסר '{ $missing1 }' או '{ $missing2 }'
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = נמצא '{ $found }', אך אין שדה שנקרא '{ $field }'
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = התבנית הקדמית בכרטיס זה - ריקה
card-template-rendering-missing-cloze =
    לא נמצאו { $number } השלמות על הכרטיס.
    נא הוסף שדה השלם את החסר, או השתמש בכלי כרטיסים ריקים.
