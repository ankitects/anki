database-check-corrupt = مجموعه خراب است. لطفاً مجموعه را از یک فایل پشتیبان خودکار بازیابی کنید.
database-check-rebuilt = پایگاه داده بازسازی و بهینه‌سازی شد.
database-check-card-properties =
    { $count ->
       *[other] تعداد { $count } مشخصات کارت خراب تعمیر شد.
    }
database-check-card-last-review-time-empty =
    { $count ->
        [one] زمان آخرین مرور به { $count } کارت اضافه شد.
       *[other] زمان آخرین مرور به { $count } کارت اضافه شد.
    }
database-check-missing-templates =
    { $count ->
        [one] تعداد { $count } کارت فاقد قالب حذف شد.
       *[other] تعداد { $count } کارت فاقد قالب حذف شد.
    }
database-check-field-count =
    { $count ->
       *[other] تعداد { $count } یادداشت با تعداد فیلد اشتباه حذف شد.
    }
database-check-new-card-high-due =
    { $count ->
       *[other] تعداد { $count } با سرامد بیشتر از 1,000,000 یافت شد - لطفاً موقعیت کارت‌ها در در جستجو تغییر دهید.
    }
database-check-card-missing-note =
    { $count ->
        [one] تعداد { $count } کارت فاقد یادداشت حذف شد.
       *[other] تعداد { $count } کارت فاقد یادداشت حذف شد.
    }
database-check-duplicate-card-ords =
    { $count ->
       *[other] تعداد { $count } کارت دارای قالب تکراری حذف شد.
    }
database-check-missing-decks =
    { $count ->
       *[other] تعداد { $count } دستۀ مفقود تعمیر شد.
    }
database-check-revlog-properties =
    { $count ->
       *[other] تعداد { $count } ورودی مرور دارای مشخصات اشتباه تعمیر شد.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
       *[other] تعداد { $count } یادداشت با کاراکترهای اشتباه utf8 اصلاح شدند.
    }
database-check-fixed-invalid-ids =
    { $count ->
        [one] تعداد { $count } آبجکت با timestamps در  آینده ثابت شد.
       *[other] تعداد { $count } آبجکت  با timestamps در آینده ثابت شد.
    }
# "db-check" is always in English
database-check-notetypes-recovered = یک یا چند نوع یادداشت یافت نشد. یادداشت‌هایی که از آنها استفاده می‌کردند نام دیگری که با "db-check" شروع می شوند داده شد، ولی نام فیلدها و قالب کارت از بین رفته است. پس شاید بهتر باشد که از یک بک آپ خودکار استفاده کنید.

## Progress info

database-check-checking-integrity = در حال بررسی مجموعه...
database-check-rebuilding = در حال بازسازی...
database-check-checking-cards = در حال بررسی کارت‌ها...
database-check-checking-notes = در حال بررسی یادداشت‌ها...
database-check-checking-history = در حال بررسی تاریخچه...
database-check-title = بررسی دیتابیس
