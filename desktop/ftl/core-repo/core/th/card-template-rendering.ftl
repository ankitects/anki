### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = ข้อมูลเพิ่มเติม
card-template-rendering-front-side-problem = แม่แบบด้านหน้ามีปัญหา:
card-template-rendering-back-side-problem = แม่แบบด้านหลังมีปัญหา:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = ขาด '{ $missing }' ใน '{ $tag }'
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = ขาด '{ $missing }'
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = พบ '{ $found }' แต่ขาด '{ $missing1 }' หรือ '{ $missing2 }'
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = พบ '{ $found }' แต่ไม่มีช่องข้อมูลชื่อ '{ $field }'
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = ด้านหน้าของการ์ดนี้ว่างเปล่า
