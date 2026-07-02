## Shown at the top of the media check screen

media-check-window-title = فحص الوسائط
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    مجلد المهملات:{ $count ->
        [zero] 0 ملف، 0 ميغابايت
        [one] ملف واحد، { $megs } ميغابايت
        [two] ملفين، { $megs } ميغابايت
        [few] { $count } ملفات، { $megs } ميغابايت
        [many] { $count } ملفًا، { $megs } ميغابايت
       *[other] { $count } ملف، { $megs } ميغابايت
    }
media-check-missing-count = ملفات مفقودة: { $count }
media-check-unused-count = ملفات غير مستخدمة: { $count }
media-check-renamed-count = عدد الملفات التي أعيدت تسميتها: { $count }
media-check-oversize-count = أكبر من 100 ميغابايت: { $count }
media-check-subfolder-count = مجلدات فرعية: { $count }
media-check-extracted-count = الصور المستخرَجة: { $count }

## Shown at the top of each section

media-check-renamed-header = أُعيدت تسمية بعض الملفات من أجل التوافق:
media-check-oversize-header = لا تمكن مزامنة الملفات ذات حجم أكبر من 100 ميغابايت بواسطة AnkiWeb.
media-check-subfolder-header = المجلدات داخل مجلد الوسائط غير مدعومة.
media-check-missing-header = الملفات التالية مشار إليها من قبل البطاقات، لكنها غير موجودة في مجلد الوسائط:
media-check-unused-header = الملفات التالية موجودة في مجلد الوسائط، لكنها غير مستخدمة من قبل أي بطاقة:
media-check-template-references-field-header =
    لا يستطيع أنكي إيجاد الملفات المستخدمة عندما تستخدم مراجع { "{{Field}}" } في وسوم الوسائط/LaTeX. يجب وضع وسوم الوسائط/LaTeX في الملحوظات نفسها بدلًا من ذلك.
    
    القوالب المعنية:

## Shown once for each file

media-check-renamed-file = أُعيدت تسميته: { $old } -> { $new }
media-check-oversize-file = أكبر من 100 ميغابايت: { $filename }
media-check-subfolder-file = مجلد: { $filename }
media-check-missing-file = مفقود: { $filename }
media-check-unused-file = غير مستخدم: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = تم فحص { $count }...

## Deleting unused media

media-check-delete-unused-confirm = هل تريد حذف الوسائط غير المستخدمة؟
media-check-files-remaining =
    تبقى { $count ->
        [zero] 0 ملف
        [one] ملف واحد
        [two] ملفين
        [few] { $count } ملفات
        [many] { $count } ملفًا
       *[other] { $count } ملف
    }
media-check-delete-unused-complete =
    تم نقل { $count ->
        [zero] 0 ملف
        [one] ملف واحد
        [two] ملفين
        [few] { $count } ملفات
        [many] { $count } ملفًا
       *[other] { $count } ملف
    } إلى المهملات.
media-check-trash-emptied = مجلد المهملات فارغ الآن.
media-check-trash-restored = تم استرجاع الملفات المحذوفة إلى مجلد الوسائط.

## Rendering LaTeX

media-check-all-latex-rendered = تمت معالجة كل معادلات LaTeX.

## Buttons

media-check-delete-unused = احذف الملفات غير المستخدمة
media-check-render-latex = معالجة LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = أفرغ مجلد المهملات
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = استرجع الملفات المحذوفة
media-check-check-media-action = فحص الوسائط
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = وسائط-مفقودة
# add a tag to notes with missing media
media-check-add-tag = وسم الملحوظات بوسائط مفقودة
