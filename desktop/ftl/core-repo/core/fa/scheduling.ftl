## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount } ثانیه
scheduling-answer-button-time-minutes = { $amount } دقیقه
scheduling-answer-button-time-hours = { $amount } ساعت
scheduling-answer-button-time-days = { $amount } روز
scheduling-answer-button-time-months = { $amount } ماه
scheduling-answer-button-time-years = { $amount } سال

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] { $amount } ثانیه
       *[other] { $amount } ثانیه
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] { $amount } دقیقه
       *[other] { $amount } دقیقه
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } ساعت
       *[other] { $amount } ساعت
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } روز
       *[other] { $amount } روز
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } ماه
       *[other] { $amount } ماه
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } سال
       *[other] { $amount } سال
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    کارت بعدی در مرحلۀ آموزش بعد از { $unit ->
        [seconds]
            { $amount ->
                [one] { $amount } ثانیه
               *[other] { $amount } ثانیه
            }
        [minutes]
            { $amount ->
                [one] { $amount } دقیقه
               *[other] { $amount } دقیقه
            }
       *[hours]
            { $amount ->
                [one] { $amount } ساعت
               *[other] { $amount } ساعت
            }
    }.
scheduling-learn-remaining =
    { $remaining ->
       *[other] { $remaining } عدد کارت در مرحلۀ یادگیری برای امروز وجود دارند.
    }
scheduling-congratulations-finished = تبریک! شما فعلاً این دسته را تمام کردید.
scheduling-today-review-limit-reached =
    محدوده مرور امروز سر رسید شده است، اما هنوز کارتهایی وجود دارد
    که منتظر برای مرور هستند. برای بهینه کردن حافظه،افزایش محدوده روزانه در اختیارات
    را ملاحظه کنید.
scheduling-today-new-limit-reached =
    کارت‌های جدیدی بیشتر در دسته وجود دارد، ولی تعداد کارت‌های جدید محدود شده است
    شما می‌توانید از طریق اختیارات، حداکثر تعداد کارت‌های جدید مطالعه شده در یک روز را افزایش دهید.
    ولی به یاد داشته باشید، هرچه تعداد کارت‌های جدید بیشتری مطالعه کنید، تعداد کارت‌هایی که باید در کوتاه‌مدت مطالعه کنید بیشتر می‌شوند.
scheduling-buried-cards-found = یک یا چند کارت به حالت مخفی درآمده‌اند و فردا نمایش داده خواهند شد. اگر بخواهید همین حالا آن‌ها را ببینید، می‌توانید { $unburyThem }.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = آن‌ها را از حالت مخفی خارج کنید
scheduling-how-to-custom-study = اگر می خواهید خارج از برنامه عادی مطالعه کنید، می توانید از ویژگی { $customStudy } استفاده کنید.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = مطالعه سفارشی

## Scheduler upgrade

scheduling-update-soon = آنکی 2.1 دارای سیستم زمانبندی جدید است که تعدادی از مشکلات نسخه‌های قبلی آنکی را حل می‌کند. به‌روزرسانی آن توصیه می‌شود.
scheduling-update-done = سیستم زمانبندی با موفقیت به‌روزرسانی شد.
scheduling-update-button = به‌روزرسانی
scheduling-update-later-button = بعداً
scheduling-update-more-info-button = اطلاعات بیشتر
scheduling-update-required =
    مجموعه شما باید به زمانبندی V2 ارتقا یابد.
    لطفاً قبل از ادامه، { scheduling-update-more-info-button } را انتخاب کنید

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = در هنگام پخش صدا، قسمت سوال همیشه پخش شود
scheduling-at-least-one-step-is-required = حداقل یک مرحله لازم است.
scheduling-automatically-play-audio = پخش خودکار صدا
scheduling-bury-related-new-cards-until-the = دفن کردن کارت‌های جدید مرتبط تا روز بعد
scheduling-bury-related-reviews-until-the-next = دفن کردن مرورهای مرتبط تا روز بعد
scheduling-days = روز
scheduling-description = توضیحات
scheduling-easy-bonus = جایزۀ آسانی
scheduling-easy-interval = مدت آسانی
scheduling-end = (پایان)
scheduling-general = عمومی
scheduling-graduating-interval = بازه زمانی فارغ شدن کارت
scheduling-hard-interval = بازۀ زمانی سخت
scheduling-ignore-answer-times-longer-than = نادیده گرفتن زمان‌های پاسخ بیشتر از
scheduling-interval-modifier = اصلاح بازه‌های زمانی
scheduling-lapses = تعداد اشتباه
scheduling-lapses2 = اشتباهات
scheduling-learning = در حال یادگیری
scheduling-leech-action = عمل در کارت‌های سخت
scheduling-leech-threshold = آستانه علامتگذاری به عنوان خیلی سخت
scheduling-maximum-interval = بیشترین وقفه
scheduling-maximum-reviewsday = حداکثر مرور/روز
scheduling-minimum-interval = کمترین وقفه
scheduling-mix-new-cards-and-reviews = ادغام کارت‌های جدید و مرورها
scheduling-new-cards = کارت‌های جدید
scheduling-new-cardsday = کارت‌های جدید/روز
scheduling-new-interval = وقفۀ جدید
scheduling-new-options-group-name = نام گروه اختیارات جدید:
scheduling-options-group = گروه اختیارات:
scheduling-order = چیدمان
scheduling-parent-limit = (حد دستۀ والد: { $val })
scheduling-reset-counts = بازنشانی تعداد دفعات تکرار و فاصله
scheduling-restore-position = در صورت امکان موقعیت اصلی را بازیابی کنید
scheduling-review = مرور
scheduling-reviews = مرورها
scheduling-seconds = ثانیه
scheduling-set-all-decks-below-to = قرار دادن { $val } به‌عنوان تنظیمات برای همۀ دسته‌های زیر؟
scheduling-set-for-all-subdecks = تنظیم برای همۀ زیردسته‌ها
scheduling-show-answer-timer = نمایش زمان سنج پاسخ
scheduling-show-new-cards-after-reviews = کارت‌های جدید بعد از مرورها نشان داده شوند
scheduling-show-new-cards-before-reviews = کارت های جدید را قبل از مرور ها نشان بده
scheduling-show-new-cards-in-order-added = کارت ها را به ترتیب اضافه شدن، نشان بده
scheduling-show-new-cards-in-random-order = کارت ها را بدون ترتیب نشان بده
scheduling-starting-ease = سهولت شروع
scheduling-steps-in-minutes = مراحل (به دقیقه)
scheduling-steps-must-be-numbers = مراحل باید عدد باشند.
scheduling-tag-only = فقط برچسب
scheduling-the-default-configuration-cant-be-removed = تنظیمات پیش فرض قابل حذف نیست.
scheduling-your-changes-will-affect-multiple-decks = تغییرات شما بر روی چندین دسته تأثیر خواهد گذاشت. اگر می‌خواهید تغییرات فقط بر روی دستۀ فعلی تأثیر بگذارد، لطفا ابتدا یک گروه اختیارات جدید اضافه کنید.
scheduling-deck-updated =
    { $count ->
       *[other] { $count } دسته بروز شد.
    }
scheduling-set-due-date-prompt =
    { $cards ->
       *[other] نمایش کارت بعد از چند روز؟
    }
scheduling-set-due-date-prompt-hint =
    0 = امروز
    1! = فردا + بازنشانی بازه‌های مرور
    3-7 = در 3 تا 7 روز آینده
scheduling-set-due-date-done =
    { $cards ->
       *[other] تنظیم تاریخ مرور { $cards } عدد کارت.
    }
scheduling-forgot-cards =
    { $cards ->
       *[other] تعداد { $cards } کارت فراموش شده بود.
    }
