# The date a card will be ready to review
statistics-due-date = مرور
# The count of cards waiting to be reviewed
statistics-due-count = مرور
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = جدید #{ $number }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } کارت بر دقیقه
statistics-average-answer-time = { $average-seconds } ثانیه ({ statistics-cards-per-min })

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds =
    { $amount ->
       *[other] در { $amount } ثانیه
    }
statistics-in-time-span-minutes =
    { $amount ->
       *[other] در { $amount } دقیقه
    }
statistics-in-time-span-hours =
    { $amount ->
       *[other] در { $amount } ساعت
    }
statistics-in-time-span-days =
    { $amount ->
       *[other] در { $amount } روز
    }
statistics-in-time-span-months =
    { $amount ->
       *[other] در { $amount } ماه
    }
statistics-in-time-span-years =
    { $amount ->
       *[other] در { $amount } سال
    }
statistics-cards =
    { $cards ->
        [one] { $cards } کارت
       *[other] { $cards } کارت
    }
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews =
    { $reviews ->
        [one] { $reviews } مرور
       *[other] { $reviews } مرور
    }
# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { statistics-in-time-span-seconds } part should be pasted in from the English
# version unmodified.
statistics-studied-today =
    امروز { statistics-cards } عدد کارت در{ $unit ->
        [seconds] { statistics-in-time-span-seconds }
        [minutes] { statistics-in-time-span-minutes }
        [hours] { statistics-in-time-span-hours }
        [days] { statistics-in-time-span-days }
        [months] { statistics-in-time-span-months }
       *[years] { statistics-in-time-span-years }
    }مطالعه شده است
    ({ $secs-per-card } ثانیه/کارت)
statistics-today-title = امروز
statistics-today-again-count = شمارش مجدد:
statistics-today-type-counts = یادگیری: { $learnCount }, مرورشده: { $reviewCount }, بازآموزی: { $relearnCount }, فیلترشده: { $filteredCount }
statistics-today-no-cards = هیچ کارتی امروز مطالعه نشده است.
statistics-today-no-mature-cards = هیچ کارت دائمی در مطالعه شده های امروز نبود.
statistics-today-correct-mature = پاسخ های صحیح در کارتهای دائم: { $correct }/{ $total } ({ $percent }%)
statistics-counts-total-cards = تمام کارت‌ها
statistics-counts-new-cards = جدید
statistics-counts-young-cards = موقت
statistics-counts-mature-cards = دائم
statistics-counts-suspended-cards = معلق شده
statistics-counts-buried-cards = دفن شده
statistics-counts-filtered-cards = فیلتر شده
statistics-counts-learning-cards = در حال یادگیری
statistics-counts-relearning-cards = بازآموزی
statistics-counts-title = تعداد کارت
statistics-counts-separate-suspended-buried-cards = کارت‌های معلق/دفن شده را جدا کنید
statistics-range-all-time = عمر دسته
statistics-range-1-year-history = 12 ماه گذشته
statistics-range-all-history = تاریخچه کامل
statistics-range-deck = دسته
statistics-range-collection = مجموعه
statistics-range-search = جست و جو
statistics-card-ease-title = سهولت کارت
statistics-card-difficulty-title = دشواری کارت
statistics-card-stability-title = پایداری کارت
statistics-card-stability-subtitle = تاخیری که در آن قابلیت بازیابی به 90٪ کاهش می یابد.
statistics-average-stability = پایداری متوسط
statistics-card-retrievability-title = قابلیت بازیابی کارت
statistics-card-ease-subtitle = هرچقدر سهولت کارت کمتر باشد، کارت بیشتر نمایش داده می‌شود.
statistics-card-difficulty-subtitle2 = هر چه سختی بیشتر باشد، پایداری کندتر افزایش می یابد.
statistics-retrievability-subtitle = احتمال فراخوانی کارت امروز.
# eg "3 cards with 150-170% ease"
statistics-card-ease-tooltip =
    { $cards ->
       *[other] { $cards } کارت دارای { $percent } سهولت
    }
statistics-card-difficulty-tooltip =
    { $cards ->
        [one] تعداد { $cards } کارت با درصد سختی { $percent }
       *[other] 0
    }
statistics-retrievability-tooltip =
    { $cards ->
        [one] تعداد { $cards } کارت با قابلیت بازیابی { $percent }
       *[other] 0
    }
statistics-future-due-title = پیش‌بینی
statistics-future-due-subtitle = تعداد مرورهایی که درآینده باید انجام دهید.
statistics-added-title = اضافه‌شده
statistics-added-subtitle = تعداد کارت‌های جدید که اضافه کردید.
statistics-reviews-count-subtitle = تعداد سؤالاتی که شما پاسخ دادید.
statistics-reviews-time-subtitle = زمانی که برای پاسخ به سؤالات صرف شده است.
statistics-answer-buttons-title = دکمه‌های پاسخ
# eg Button: 4
statistics-answer-buttons-button-number = دکمه
# eg Times pressed: 123
statistics-answer-buttons-button-pressed = دفعۀ فشرده شده
statistics-answer-buttons-subtitle = تعداد دفعاتی که شما هر دکمه را فشرده اید.
statistics-reviews-title = مرورها
statistics-reviews-time-checkbox = زمان
statistics-in-days-single =
    { $days ->
        [0] امروز
        [1] فردا
       *[other] { $days } روز دیگر
    }
statistics-in-days-range = در طول { $daysStart }-{ $daysEnd } روز
statistics-days-ago-single =
    { $days ->
        [1] دیروز
       *[other] { $days } روز گذشته
    }
statistics-days-ago-range = { $daysStart }-{ $daysEnd } روز گذشته
statistics-running-total = تعداد کل
statistics-cards-due =
    { $cards ->
       *[other] { $cards } کارت مرور
    }
statistics-backlog-checkbox = بک‌لاگ
statistics-intervals-title = بازه های زمانی
statistics-intervals-subtitle = تاوقتی که مرورها دوباره نشان داده شوند به تاخیر انداخته شود.
statistics-intervals-day-range =
    { $cards ->
       *[other] { $cards } کارت دارای موعد مرور { $daysStart }~{ $daysEnd } روز
    }
statistics-intervals-day-single =
    { $cards ->
       *[other] { $cards } دارای موعد مرور { $day } روز
    }
statistics-stability-day-range =
    { $cards ->
        [one] تعداد { $cards } با پایداری { $daysStart } تا { $daysEnd }
       *[other] 0
    }
statistics-stability-day-single =
    { $cards ->
        [one] تعداد { $cards } کارت با { $day } روز پایداری
       *[other] 0
    }
# hour range, eg "From 14:00-15:00"
statistics-hours-range = از { $hourStart }:00~{ $hourEnd }:00
statistics-hours-correct = { $correct }/{ $total } درست ({ $percent }%)
# the emoji depicts the graph displaying this number
statistics-hours-reviews = مرورها { $reviews }
# the emoji depicts the graph displaying this number
statistics-hours-correct-reviews = { $percent } درصد صحیح ({ $reviews })
statistics-hours-title = تفکیک ساعت به ساعت
statistics-hours-subtitle = میزان موفقیت مرور در هر ساعت از روز
# shown when graph is empty
statistics-no-data = فاقد داده
statistics-calendar-title = تقویم

## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.

statistics-elapsed-time-seconds = { $amount } ثانیه
statistics-elapsed-time-minutes = { $amount } دقیقه
statistics-elapsed-time-hours = { $amount } ساعت
statistics-elapsed-time-days = { $amount } روز
statistics-elapsed-time-months = { $amount } ماه
statistics-elapsed-time-years = { $amount } سال

##

statistics-average-for-days-studied = میانگین برای روزهای مطالعه شده
statistics-total = کل
statistics-days-studied = روزهای مطالعه شده
statistics-average-answer-time-label = میانگین زمان پاسخگویی
statistics-average = میانگین
statistics-average-interval = میانگین بازه زمانی
statistics-due-tomorrow = موعد مرور فردا
# eg 5 of 15 (33.3%)
statistics-amount-of-total-with-percentage = { $amount } از { $total } ({ $percent }%)
statistics-average-over-period = اگر شما هر روز مطالعه نموده‌اید
statistics-reviews-per-day =
    { $count ->
       *[other] { $count } مرور/روز
    }
statistics-minutes-per-day =
    { $count ->
       *[other] { $count } دقیقه/روز
    }
statistics-cards-per-day =
    { $count ->
       *[other] { $count } کارت/روز
    }
statistics-average-ease = میانگین آسانی
statistics-average-difficulty = سختی متوسط
statistics-average-retrievability = میانگین قابلیت بازیابی
statistics-save-pdf = ذخیره PDF
statistics-saved = ذخیره شد.
statistics-stats = آمار
statistics-title = آمارها
statistics-true-retention-total = تمام کارت‌ها
statistics-true-retention-young = موقت
statistics-true-retention-mature = دائم
