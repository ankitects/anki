### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Thông tin bổ sung
card-template-rendering-front-side-problem = Mẫu mặt trước gặp vấn đề:
card-template-rendering-back-side-problem = Mẫu mặt sau gặp vấn đề:
card-template-rendering-browser-front-side-problem = Mẫu mặt trước gặp vẫn đề liên quan đến trình duyệt:
card-template-rendering-browser-back-side-problem = Mẫu mặt sau gặp vẫn đề liên quan đến trình duyệt:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = Thiếu '{ $missing }' trong '{ $tag }'
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = Thiếu '{ $missing }'
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = Đã tìm thấy '{ $found }', nhưng xảy ra '{ $expected }'
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = Đã tìm thấy '{ $found }', nhưng thiếu '{ $missing1 }' hoặc '{ $missing2 }'
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = Đã tìm thấy '{ $found }', nhưng không có trường '{ $field }'
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = Mặt trước của thẻ này trống.
card-template-rendering-missing-cloze =
    Không tìm thấy bản sao { $number } trên thẻ.
    Vui lòng hoặc xóa bản sao, hoặc sử dụng công cụ Thẻ trống.
