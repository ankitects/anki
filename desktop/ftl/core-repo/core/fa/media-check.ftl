## Shown at the top of the media check screen

media-check-window-title = بررسی رسانه
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    سطح زباله:{ $count ->
       *[other] { $count } عدد فایل، { $megs } مگابایت
    }
media-check-missing-count = فایل‌های مفقود: { $count }
media-check-unused-count = فایل‌های استفاده نشده: { $count }
media-check-renamed-count = فایل‌هایی که نامشان تغییر داده شده است: { $count }
media-check-oversize-count = بیشتر از 100 مگابایت: { $count }
media-check-subfolder-count = زیرپوشه‌ها: { $count }
media-check-extracted-count = تصاویر استخراج شده: { $count }

## Shown at the top of each section

media-check-renamed-header = نام بعضی از فایل‌ها برای سازگار شدن تغییر داده شد:
media-check-oversize-header = فایل‌هایی که حجمشان بیشتر از 100 مگابایت است نمی‌توانند با AnkiWeb همگام‌سازی شوند.
media-check-subfolder-header = پوشه‌های داخل پوشۀ رسانه پشتیبانی نمی‌شوند.
media-check-missing-header = فایل‌های زیر در کارت‌های استفاده شده‌اند، ولی در پوشۀ رسانه یافت نشدند:
media-check-unused-header = فایل‌های زیر در پوشۀ رسانه یافت شدند، اما در هیچ کارتی استفاده نشده‌اند:
media-check-template-references-field-header =
    وقتی از ارجاعات { "{{Field}}" } در برچسب‌های رسانه/لاتک استفاده می‌کنید، آنکی نمی‌تواند فایل‌های استفاده شده را شناسایی کند. برچسب‌های رسانه/لاتک باید به جای آن روی یادداشت‌های جداگانه قرار گیرند.
    
    الگوهای ارجاع:

## Shown once for each file

media-check-renamed-file = نام از { $old } به { $new } تغییر داده شد
media-check-oversize-file = حجم بیشتر از 100 مگابایت: { $filename }
media-check-subfolder-file = پوشه: { $filename }
media-check-missing-file = مفقود: { $filename }
media-check-unused-file = استفاده نشده: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = بررسی { $count }...

## Deleting unused media

media-check-delete-unused-confirm = حذف فایل‌های رسانه استفاده نشده؟
media-check-files-remaining =
    { $count ->
       *[other] { $count } عدد فایل
    }باقی مانده است.
media-check-delete-unused-complete =
    { $count ->
       *[other] { $count } عدد فایل
    }به سطل زباله انتقال داده شد.
media-check-trash-emptied = سطل زباله اکنون خالی است.
media-check-trash-restored = فایل‌های حذف شده به پوشۀ رسانه بازگردانده شدند.

## Rendering LaTeX

media-check-all-latex-rendered = همۀ دارای LaTeX.

## Buttons

media-check-delete-unused = حذف استفاده نشده
media-check-render-latex = ایجاد LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = خالی کردن سطح زباله
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = بازگردانی حذف شده‌ها
media-check-check-media-action = بررسی و رسانه
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = رسانه از دست رفته
# add a tag to notes with missing media
media-check-add-tag = تگ گم شده
