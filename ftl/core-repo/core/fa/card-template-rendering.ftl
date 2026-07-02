### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = اطلاعات بیشتر
card-template-rendering-front-side-problem = قالب جلوی کارت معیوب است:
card-template-rendering-back-side-problem = قالب پشت کار معیوب است:
card-template-rendering-browser-front-side-problem = قالب جلوی مختص به مرورگر معیوب است:
card-template-rendering-browser-back-side-problem = قالب پشت مختص به مرورگر معیوب است:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = '{ $missing }' در '{ $tag }' یافت نشد
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = '{ $missing }' یافت نشد
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = '{ $found }' یافت شد، در حالی که '{ $expected }' انتظار می‌رفت
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = '{ $found }' یافت شد، ولی '{ $missing1 }' یا '{ $missing2 }' یافت نشد
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = '{ $found }' یافت شد، ولی هیچ فیلدی به نام '{ $field }' وجود ندارد
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = جلوی این کارت خالی است.
card-template-rendering-missing-cloze =
    کارت شمارۀ { $number } جاخالی یافت نشد.
    شما می‌توانید از ابزار کارت‌های خالی برای حذف این کارت خالی استفاده کنید.
