### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = أُضيف: { $up }↑ { $down }↓
sync-media-removed-count = حُذِف: { $up }↑ { $down }↓
sync-media-checked-count = فُحِص: { $count }
sync-media-starting = بدء مزامنة الوسائط...
sync-media-complete = تمت مزامنة الوسائط.
sync-media-failed = فشلت مزامنة الوسائط.
sync-media-aborting = إيقاف مزامنة الوسائط...
sync-media-aborted = تم إلغاء مزامنة الوسائط.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = مزامنة الوسائط غير مفعلة.
# Title of the screen that shows syncing progress history
sync-media-log-title = سجل مزامنة الوسائط

## Error messages / dialogs

sync-conflict = يمكن لنسخة واحدة فقط من أنكي المزامنة مع حسابك في الوقت ذاته. الرجاء الانتظار بضع دقائق، ثم المحاولة مجددًا.
sync-server-error = صادف أنكي ويب مشكلة. الرجاء المحاولة مجددًا بعد بضع دقائق.
sync-client-too-old = إصدار أنكي الخاص بك قديم جدًا. الرجاء التحديث إلى آخر إصدار لاستكمال المزامنة.
sync-wrong-pass = البريد الإلكتروني أو كلمة السر غير صحيحة. حاول مجددًا.
sync-resync-required = الرجاء المزامنة مجددًا. إذا استمرت هذه الرسالة بالظهور، انشر منشورًا في موقع الدعم.
sync-must-wait-for-end = أنكي يزامن الآن. الرجاء الانتظار حتى ينتهي، ثم المحاولة مجددًا.
sync-confirm-empty-download = المجموعة المحلية ليس فيها أي بطاقات. هل تريد التنزيل من أنكي ويب؟
sync-confirm-empty-upload = مجموعة AnkiWeb ليس فيها أي بطاقات. هل تريد استبدالها بمجموعتك المحلية؟
sync-conflict-explanation =
    رزمك هنا وفي أنكي ويب تختلف لدرجة تمنع دمجها معًا، لذلك من الضروري استبدال الرزم في جانب بالرزم الأخرى في الجانب الآخر.
    
    إذا اخترت تنزيل، أنكي سينزل المجموعة من أنكي ويب، وستفقد أي تغييرات أجريتها على هذا الجهاز منذ آخر مزامنة.
    
    إذا اخترت رفع، سيرفع أنكي بيانات هذا الجهاز إلى أنكي ويب، وستفقد أي تغييرات معلقة أجريتها في أنكي ويب.
    
    بعد أن تصبح كل الأجهزة متزامنة، المراجعات القادمة والبطاقات المضافة ستُدمج تلقائيًا.
sync-conflict-explanation2 =
    هناك تعارض بين البطاقات في هذا الجهاز وأنكي ويب. عليك اختيار النسخة التي تريد إبقاءها:
    
    - اختر **{ sync-download-from-ankiweb }** لاستبدال رزمك هنا بنسخة أنكي ويب. ستخسر أي تغييرات أجريتها في هذا الجهاز منذ آخر مزامنة.
    - اختر **{ sync-upload-to-ankiweb }** لاستبدال نسخة أنكي ويب بالرزم في هذا الجهاز، وحذف أي تغييرات في أنكي ويب.
    
    ستعمل المزامنة كالمعتاد بعد حل التعارض.
sync-ankiweb-id-label = البريد الإلكتروني:
sync-password-label = كلمة السر:
sync-account-required =
    <h1>يلزم حساب</h1>
    يلزم حساب مجاني لمزامنة مجموعتك. الرجاء <a href="{ $link }">إنشاء</a> حساب، ثم إدخال التفاصيل في الأسفل.
sync-sanity-check-failed = الرجاء استخدام ميزة فحص قاعدة البيانات والمزامنة مجددًا. إذا استمرت المشاكل، افرض مزامنة كاملة من خلال نافذة التفضيلات.
sync-clock-off = تتعذر المزامنة - تختلف ساعتك عن الوقت الصحيح.
sync-upload-too-large =
    حجم ملف مجموعتك كبير جدًا ليتم إرساله إلى أنكي ويب. تستطيع تقليل
    الحجم بحذف الرزم التي لا تريدها (بعد تصديرها أولا إذا أردت)، ثم
    استخدام أداة فحص قاعدة البيانات لتقليص الملف. ({ $details })
sync-sign-in = تسجيل الدخول
sync-ankihub-dialog-heading = تسجيل الدخول إلى AnkiHub
sync-ankihub-username-label = اسم المستخدم أو البريد الإلكتروني:
sync-ankihub-login-failed = تعذر تسجيل الدخول إلى AnkiHub باستخدام المعلومات المزودة.
sync-ankihub-addon-installation = تثبيت إضافة AnkiHub

## Buttons

sync-media-log-button = سِجل الوسائط
sync-abort-button = إلغاء
sync-download-from-ankiweb = نزّل من أنكي ويب
sync-upload-to-ankiweb = ارفع إلى أنكي ويب
sync-cancel-button = إلغاء

## Normal sync progress

sync-downloading-from-ankiweb = يجري التنزيل من أنكي ويب...
sync-uploading-to-ankiweb = يجري الرفع إلى أنكي ويب...
sync-syncing = تجري المزامنة...
sync-checking = يجري الفحص...
sync-connecting = يجري الاتصال...
sync-added-updated-count = أُضيف/عُدِّل: { $up }↑ { $down }↓
sync-log-in-button = تسجيل الدخول
sync-log-out-button = تسجيل الخروج
sync-collection-complete = تمت مزامنة المجموعة.
