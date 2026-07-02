### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = المزيد من المعلومات
card-template-rendering-front-side-problem = هناك مشكلة في القالب الأمامي:
card-template-rendering-back-side-problem = هناك مشكلة في القالب الخلفي:
card-template-rendering-browser-front-side-problem = القالب الأمامي الخاص بالمتصفح فيه مشكلة:
card-template-rendering-browser-back-side-problem = القالب الخلفي الخاص بالمتصفح فيه مشكلة:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = ينقص '{ $missing }' في '{ $tag }'
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = ينقص '{ $missing }'
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = وُجد '{ $found }'، لكن تُوقع '{ $expected }'
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = وُجد '{ $found }'، لكن ينقص '{ $missing1 }' أو '{ $missing2 }'
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = وُجد '{ $found }'، لكن ليس هناك حقل يسمى '{ $field }'
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = مقدمة هذه البطاقة فارغة.
card-template-rendering-missing-cloze =
    عبارة ملء الفراغات رقم { $number } غير موجودة في البطاقة.
    الرجاء إضافة عبارة، أو استخدام أداة البطاقات الفارغة.
