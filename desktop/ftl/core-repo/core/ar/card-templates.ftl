# This word is used by TTS voices instead of the elided part of a cloze.
card-templates-blank = فارغ
card-templates-changes-will-affect-notes =
    { $count ->
        [zero] لن تؤثر التغييرات في الأسفل على أي بطاقة.
        [one] ستؤثر التغييرات في الأسفل على الملحوظة الوحيدة التي تستخدم نوع البطاقة هذا.
        [two] ستؤثر التغييرات في الأسفل على الملحوظتين اللتين تستخدمان نوع البطاقة هذا.
        [few] ستؤثر التغييرات في الأسفل على الـ{ $count } بطاقة التي تستخدم نوع البطاقة هذا.
        [many] ستؤثر التغييرات في الأسفل على الـ{ $count } بطاقة التي تستخدم نوع البطاقة هذا.
       *[other] ستؤثر التغييرات في الأسفل على الـ{ $count } بطاقة التي تستخدم نوع البطاقة هذا.
    }
card-templates-card-type = نوع البطاقة:
card-templates-front-template = القالب الأمامي
card-templates-back-template = القالب الخلفي
card-templates-template-styling = التنسيق
card-templates-front-preview = معاينة أمامية
card-templates-back-preview = معاينة خلفية
card-templates-preview-box = معاينة
card-templates-template-box = القالب
card-templates-sample-cloze = هذا { "{{c1::" }مثال{ "}}" } عن عبارات ملء الفراغات.
card-templates-fill-empty = ملء الحقول الفارغة
card-templates-night-mode = الوضع الليلي
# Add "mobile" class to card preview, so the card appears like it would
# on a mobile device.
card-templates-add-mobile-class = إضافة صنف الهاتف المحمول
card-templates-preview-settings = خيارات
card-templates-invalid-template-number = هناك مشكلة في قالب البطاقة رقم { $number } من نوع الملحوظة "{ $notetype }".
card-templates-identical-front = الجانب الأمامي مطابق لقالب البطاقة { $number }.
card-templates-no-front-field = كان من المتوقع إيجاد عبارة استبدال حقل في الجانب الأمامي لقالب البطاقة.
card-templates-missing-cloze = كان من المتوقع إيجاد '{ "{{" }cloze:Text{ "}}" }' أو ما شابه في الجانبين الأمامي والخلفي لقالب البطاقة.
card-templates-extraneous-cloze = يمكن استخدام 'cloze:' في أنواع ملحوظات ملء الفراغات فقط.
card-templates-see-preview = انظر المعاينة لمزيد من المعلومات.
card-templates-field-not-found = الحقل '{ $field }' غير موجود.
card-templates-changes-saved = حُفِظت التغييرات.
card-templates-discard-changes = هل تريد تجاهل التغييرات؟
card-templates-add-card-type = إضافة نوع بطاقة...
card-templates-anki-couldnt-find-the-line-between = أنكي لا يستطيع إيجاد الخط الفاصل بين جانبي السؤال والجواب. الرجاء ضبط القالب يدويًا لتبديل مكان السؤال والجواب.
card-templates-at-least-one-card-type-is = يلزم نوع بطاقة واحد على الأقل.
card-templates-browser-appearance = مظهر المتصفّح...
card-templates-card = البطاقة { $val }
card-templates-card-types-for = أنواع البطاقات لـ { $val }
card-templates-cloze = ملء فراغات { $val }
card-templates-deck-override = رزمة مهيمنة...
card-templates-copy-info = نسخ المعلومات إلى الحافظة
card-templates-delete-the-as-card-type-and = هل تريد حذف نوع البطاقة '{ $template }'، و { $cards }؟
card-templates-enter-deck-to-place-new = أدخل اسم الرزمة التي تريد وضع { $val } بطاقة جديدة فيها، أو اترك الحقل فارغًا:
card-templates-enter-new-card-position-1 = أدخل موضع البطاقة الجديد (1...{ $val }):
card-templates-flip = قلب
card-templates-form = نموذج
card-templates-off = (غير مفعل)
card-templates-on = (مفعل)
card-templates-remove-card-type = حذف نوع البطاقة...
card-templates-rename-card-type = تغيير اسم نوع البطاقة...
card-templates-reposition-card-type = تغيير موضع نوع البطاقة...
card-templates-card-count =
    { $count ->
        [zero] { $count } بطاقة
        [one] { $count } بطاقة
        [two] { $count } بطاقة
        [few] { $count } بطاقات
        [many] { $count } بطاقة
       *[other] { $count } بطاقة
    }
card-templates-this-will-create-card-proceed =
    { $count ->
        [zero] سينشئ هذا { $count } بطاقة. هل تريد الاستمرار؟
        [one] سينشئ هذا { $count } بطاقة. هل تريد الاستمرار؟
        [two] سينشئ هذا { $count } بطاقة. هل تريد الاستمرار؟
        [few] سينشئ هذا { $count } بطاقات. هل تريد الاستمرار؟
        [many] سينشئ هذا { $count } بطاقة. هل تريد الاستمرار؟
       *[other] سينشئ هذا { $count } بطاقة. هل تريد الاستمرار؟
    }
card-templates-type-boxes-warning = فقط حقل إدخال واحد في قالب البطاقات مدعوم
card-templates-restore-to-default = استعادة الافتراضي
card-templates-restore-to-default-confirmation =
    سيعيد هذا كل الحقول والقوالب في نوع الملحوظة هذا إلى قيمها الافتراضية،
    ما يؤدي لمسح أي حقول أو قوالب إضافية ومحتواها والتنسيقات المخصصة. هل تريد الاستمرار؟
card-templates-restored-to-default = أعيد نوع الملحوظة إلى حالته الأصلية.
