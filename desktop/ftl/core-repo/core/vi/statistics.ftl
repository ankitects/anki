# The date a card will be ready to review
statistics-due-date = Đến hạn
# The count of cards waiting to be reviewed
statistics-due-count = Đến hạn
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = Mới #{ $number }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } thẻ / phút
statistics-average-answer-time = { $average-seconds }s ({ statistics-cards-per-min })

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds =
    { $amount ->
       *[other] trong { $amount } giây
    }
statistics-in-time-span-minutes =
    { $amount ->
       *[other] trong { $amount } phút
    }
statistics-in-time-span-hours =
    { $amount ->
       *[other] trong { $amount } giờ
    }
statistics-in-time-span-days =
    { $amount ->
       *[other] trong { $amount } ngày
    }
statistics-in-time-span-months =
    { $amount ->
       *[other] trong { $amount } tháng
    }
statistics-in-time-span-years =
    { $amount ->
       *[other] trong { $amount } năm
    }
# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { statistics-in-time-span-seconds } part should be pasted in from the English
# version unmodified.
statistics-studied-today =
    Đã học { statistics-cards }
    { $unit ->
        [seconds] { statistics-in-time-span-seconds }
        [minutes] { statistics-in-time-span-minutes }
        [hours] { statistics-in-time-span-hours }
        [days] { statistics-in-time-span-days }
        [months] { statistics-in-time-span-months }
       *[years] { statistics-in-time-span-years }
    } hôm nay
    ({ $secs-per-card }giây/thẻ)

##

statistics-cards = { $cards } thẻ
statistics-notes = { $notes } ghi chú
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews = { $reviews } thẻ ôn tập
# This fragment of the tooltip in the FSRS simulation
# diagram (Deck options -> FSRS) shows the total number of
# cards that can be recalled or retrieved on a specific date.
statistics-memorized = { $memorized } thẻ đã nhớ
statistics-today-title = Hôm nay
statistics-today-again-count = Học lại:
statistics-today-type-counts = Học: { $learnCount }, Ôn: { $reviewCount }, Học lại: { $relearnCount }, Lọc: { $filteredCount }
statistics-today-no-cards = Không có thẻ nào đã được học hôm nay.
statistics-today-no-mature-cards = Chưa có thẻ trưởng thành học trong hôm nay.
statistics-today-correct-mature = Số trả lời đúng thẻ trưởng thành: { $correct }/{ $total } ({ $percent }%)
statistics-counts-total-cards = Tổng số thẻ
statistics-counts-new-cards = Mới
statistics-counts-young-cards = Trẻ
statistics-counts-mature-cards = Trưởng thành
statistics-counts-suspended-cards = Dừng
statistics-counts-buried-cards = Đã tạm hoãn
statistics-counts-filtered-cards = Đã lọc
statistics-counts-learning-cards = Đang học
statistics-counts-relearning-cards = Đang học lại
statistics-counts-title = Số lượng thẻ
statistics-counts-separate-suspended-buried-cards = Tách riêng thẻ đã ngừng/tạm hoãn

## Retention represents your actual retention from past reviews, in
## comparison to the "desired retention" setting of FSRS, which forecasts
## future retention. Retention is the percentage of all reviewed cards
## that were marked as "Hard," "Good," or "Easy" within a specific time period.
##
## Most of these strings are used as column / row headings in a table.
## (Excluding -title and -subtitle)
## It is important to keep these translations short so that they do not make
## the table too large to display on a single stats card.
##
## N.B. Stats cards may be very small on mobile devices and when the Stats
##      window is certain sizes.

statistics-true-retention-title = Đã nhớ
statistics-true-retention-subtitle = Độ nhớ của thẻ với khoảng thời gian ≥ 1 ngày
statistics-true-retention-tooltip = Nếu bạn đang dùng FSRS, độ nhớ của bạn sẽ gần với thời gian lưu trữ mong muốn. Xin lưu ý rằng dữ liệu cho một ngày khá nhiễu, bạn nên nhìn dữ liệu của cả tháng.
statistics-true-retention-range = Phạm vi
statistics-true-retention-pass = Đậu
statistics-true-retention-fail = Rớt
# This will usually be the same as statistics-counts-total-cards
statistics-true-retention-total = Tổng số thẻ
statistics-true-retention-count = Số lượng
statistics-true-retention-retention = Lưu trữ
# This will usually be the same as statistics-counts-young-cards
statistics-true-retention-young = Trẻ
# This will usually be the same as statistics-counts-mature-cards
statistics-true-retention-mature = Trưởng thành
statistics-true-retention-all = Tất cả
statistics-true-retention-today = Hôm nay
statistics-true-retention-yesterday = Hôm qua
statistics-true-retention-week = Tuần trước
statistics-true-retention-month = Tháng trước
statistics-true-retention-year = Năm trước
statistics-true-retention-all-time = Tổng thời gian
# If there are no reviews within a specific time period, the retention
# percentage cannot be calculated and is displayed as "N/A."
statistics-true-retention-not-applicable = Không xác định

##

statistics-range-all-time = dòng đời bộ thẻ
statistics-range-1-year-history = 12 tháng trước
statistics-range-all-history = tất cả lịch sử
statistics-range-deck = bộ thẻ
statistics-range-collection = bộ sưu tập
statistics-range-search = Tìm
statistics-card-ease-title = Thẻ Dễ
statistics-card-difficulty-title = Độ khó của thẻ
statistics-card-stability-title = Độ ổn định của thẻ
statistics-card-stability-subtitle = Thời gian giãn cách khi khả năng truy xuất xuống dưới 90%.
statistics-median-stability = Độ ổn định trung vị
statistics-card-retrievability-title = Khả năng truy xuất của thẻ
statistics-card-ease-subtitle = Mức độ dễ càng thấp, thẻ sẽ xuất hiện càng thường xuyên.
statistics-card-difficulty-subtitle2 = Độ khó càng cao, độ ổn định tăng càng chậm.
statistics-retrievability-subtitle = Xác suất nhớ lại thẻ hôm nay.
# eg "3 cards with 150-170% ease"
statistics-card-ease-tooltip =
    { $cards ->
       *[other] { $cards } thẻ với { $percent } dễ
    }
statistics-card-difficulty-tooltip = { $cards } thẻ với độ khó { $percent }
statistics-retrievability-tooltip = { $cards } thẻ với khả năng truy xuất { $percent }
statistics-future-due-title = Dự báo
statistics-future-due-subtitle = Số thẻ ôn tập đến hạn trong tương lai.
statistics-added-title = Đã thêm
statistics-added-subtitle = Số thẻ mới bạn đã thêm.
statistics-reviews-count-subtitle = Số câu hỏi đã trả lời.
statistics-reviews-time-subtitle = Thời gian trả lời câu hỏi.
statistics-answer-buttons-title = Nút Trả lời
# eg Button: 4
statistics-answer-buttons-button-number = Nút
# eg Times pressed: 123
statistics-answer-buttons-button-pressed = Số lần nhấn
statistics-answer-buttons-subtitle = Số lần nhấn mỗi nút.
statistics-reviews-title = Ôn tập
statistics-reviews-time-checkbox = Thời gian
statistics-in-days-single =
    { $days ->
        [0] Hôm nay
        [1] Ngày mai
       *[other] { $days } ngày nữa
    }
statistics-in-days-range = Trong khoảng { $daysStart }-{ $daysEnd } ngày nữa
statistics-days-ago-single =
    { $days ->
        [1] Hôm qua
       *[other] { $days } ngày trước
    }
statistics-days-ago-range = { $daysStart }-{ $daysEnd } ngày trước
statistics-running-total = Tổng thời gian học
statistics-cards-due =
    { $cards ->
       *[other] { $cards } thẻ đến hạn
    }
statistics-backlog-checkbox = Tồn đọng
statistics-intervals-title = Khoảng cách
statistics-intervals-subtitle = Thời gian giãn cách đến khi hiện thẻ ôn tập lần nữa
statistics-intervals-day-range =
    { $cards ->
       *[other] { $cards } thẻ có khoảng từ { $daysStart }~{ $daysEnd } ngày
    }
statistics-intervals-day-single =
    { $cards ->
       *[other] { $cards } thẻ trong khoảng { $day } ngày
    }
statistics-stability-day-range = { $cards } thẻ với độ ổn định { $daysStart }~{ $daysEnd } ngày
statistics-stability-day-single = { $cards } thẻ với độ ổn định { $day } ngày
# hour range, eg "From 14:00-15:00"
statistics-hours-range = Từ { $hourStart }:00~{ $hourEnd }:00
statistics-hours-correct = { $correct }/{ $total } đúng ({ $percent }%)
statistics-hours-correct-info = → (ngoại trừ 'Lặp lại')
# the emoji depicts the graph displaying this number
statistics-hours-reviews = 📊 { $reviews } bài ôn tập
# the emoji depicts the graph displaying this number
statistics-hours-correct-reviews = 📈 { $percent }% đúng ({ $reviews })
statistics-hours-title = Chia nhỏ Theo giờ
statistics-hours-subtitle = Tỷ lệ ôn tập thành công mỗi giờ trong ngày
# shown when graph is empty
statistics-no-data = KHÔNG CÓ DỮ LIỆU
statistics-calendar-title = Lịch

## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.

statistics-elapsed-time-seconds = { $amount }s
statistics-elapsed-time-minutes = { $amount }m
statistics-elapsed-time-hours = { $amount }h
statistics-elapsed-time-days = { $amount }d
statistics-elapsed-time-months = { $amount }mo
statistics-elapsed-time-years = { $amount }y

##

statistics-average-for-days-studied = Số ngày trung bình đã học
# This term is used in a variety of contexts to refers to the total amount of
# items (e.g., cards, mature cards, etc) for a given period, rather than the
# total of all existing items.
statistics-total = Tổng
statistics-days-studied = Số ngày đã học
statistics-average-answer-time-label = Thời gian trả lời trung bình
statistics-average = Trung bình
statistics-median-interval = Khoảng cánh trung vị
statistics-due-tomorrow = Đến hạn ngày mai
# This string, ‘Daily load,’ appears in the ‘Future due’ table and represents a
# forecasted estimate of the number of cards expected to be reviewed daily in 
# the future. Unlike the other strings in the table that display actual data 
# derived from the current scheduling (e.g., ‘Average’, ‘Due tomorrow’),
# ‘Daily load’ is a projection based on the given data.
statistics-daily-load = Số lượng trong ngày
# eg 5 of 15 (33.3%)
statistics-amount-of-total-with-percentage = { $amount } trong { $total } ({ $percent }%)
statistics-average-over-period = Nếu học hàng ngày thì
statistics-reviews-per-day =
    { $count ->
       *[other] { $count } thẻ ôn tập/ngày
    }
statistics-minutes-per-day =
    { $count ->
       *[other] { $count } phút/ngày
    }
statistics-cards-per-day =
    { $count ->
       *[other] { $count } thẻ/ngày
    }
statistics-median-ease = Độ dễ trung vị
statistics-median-difficulty = Độ khó trung vị
statistics-average-retrievability = Khả năng truy xuất trung bình
statistics-estimated-total-knowledge = Ước tính tổng kiến thức
statistics-save-pdf = Lưu PDF
statistics-saved = Đã lưu.
statistics-stats = thống kê
statistics-title = Thống kê

## These strings are no longer used - you do not need to translate them if they
## are not already translated.

statistics-average-stability = Độ ổn định trung bình
statistics-average-interval = Khoảng cách trung bình
statistics-average-ease = Độ dễ trung bình
statistics-average-difficulty = Độ khó trung bình
