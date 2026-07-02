## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount }gi
scheduling-answer-button-time-minutes = { $amount }ph
scheduling-answer-button-time-hours = { $amount }g
scheduling-answer-button-time-days = { $amount }ng
scheduling-answer-button-time-months = { $amount }th
scheduling-answer-button-time-years = { $amount }n

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds = { $amount } giây
scheduling-time-span-minutes = { $amount } phút
scheduling-time-span-hours = { $amount } giờ
scheduling-time-span-days = { $amount } ngày
scheduling-time-span-months = { $amount } tháng
scheduling-time-span-years = { $amount } năm

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    Thẻ đang học kế tiếp sẽ sẵn sàng sau { $unit ->
        [seconds]
            { $amount ->
                [one] { $amount } giây
               *[other] { $amount } giây
            }
        [minutes]
            { $amount ->
                [one] { $amount } phút
               *[other] { $amount } phút
            }
       *[hours]
            { $amount ->
                [one] { $amount } giờ
               *[other] { $amount } giờ
            }
    }.
scheduling-learn-remaining =
    { $remaining ->
       *[other] Còn lại { $remaining } thẻ học tập sẽ đến hạn sau ngày hôm nay.
    }
scheduling-congratulations-finished = Xin chúc mừng! Hiện giờ bạn đã học xong bộ thẻ này.
scheduling-today-review-limit-reached =
    Đã đến giới hạn trong ngày hôm nay, nhưng vẫn còn nhiều thẻ
    đang chờ ôn tập. Để giúp trí nhớ hoạt động hiệu quả hơn, bạn
    có thể xem xét tăng giới hạn hàng ngày trong phần tùy chọn.
scheduling-today-new-limit-reached =
    Vẫn còn nhiều thẻ nữa, nhưng đã đến giới hạn hàng ngày.
    Bạn có thể tăng thêm giới hạn trong phần tùy chọn, nhưng
    cần nhớ rằng bạn càng đưa ra nhiều thẻ mới thì gánh nặng ôn
    tập trong thời gian ngắn đối với bạn ngày càng cao hơn.
scheduling-buried-cards-found = Một hoặc nhiều thẻ đã bị tạm hoãn và sẽ được hiển thị vào ngày mai. Bạn có thể { $unburyThem } nếu bạn muốn xem chúng ngay lập tức.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = bỏ tạm hoãn chúng
scheduling-how-to-custom-study = Nếu bạn muốn học ngoài lịch trình thông thường, bạn có thể sử dụng tính năng { $customStudy }
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = Học tùy biến

## Scheduler upgrade

scheduling-update-soon = Anki 2.1 đi kèm với một bộ lập lịch mới, giúp khắc phục một số vấn đề mà các phiên bản Anki trước đó gặp phải. Cập nhật nó được khuyến khích.
scheduling-update-done = Đã cập nhật thành công bộ lập lịch biểu.
scheduling-update-button = Cập nhật
scheduling-update-later-button = Để sau
scheduling-update-more-info-button = Tìm hiểu thêm
scheduling-update-required =
    Bạn cần nâng cấp bộ sưu tập lên bộ lập lịch biểu V2.
    Hãy chọn { scheduling-update-more-info-button } trước khi tiếp tục.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = Luôn bao gồm câu hỏi bên khi phát lại âm thanh
scheduling-at-least-one-step-is-required = Cần ít nhất một bước.
scheduling-automatically-play-audio = Tự động phát âm thanh
scheduling-bury-related-new-cards-until-the = Tạm hoãn các thẻ mới có liên quan cho đến ngày hôm sau
scheduling-bury-related-reviews-until-the-next = Tạm hoãn nội dung ôn tập có liên quan cho đến ngày hôm sau
scheduling-days = ngày
scheduling-description = Mô tả
scheduling-easy-bonus = Phần chênh mức Dễ
scheduling-easy-interval = Khoảng cách mức Dễ
scheduling-end = (cuối)
scheduling-general = Tổng quát
scheduling-graduating-interval = Khoảng cách mức Được
scheduling-hard-interval = Khoảng cách mức Khó
scheduling-ignore-answer-times-longer-than = Bỏ qua mỗi khi thời gian trả lời lâu hơn
scheduling-interval-modifier = Hệ số khoảng cách
scheduling-lapses = Hỏng
scheduling-lapses2 = lần hỏng
scheduling-learning = Đang học
scheduling-leech-action = Hành động với thẻ bám
scheduling-leech-threshold = Ngưỡng thành thẻ bám
scheduling-maximum-interval = Khoảng tối đa
scheduling-maximum-reviewsday = Ôn tập tối đa/ngày
scheduling-minimum-interval = Khoảng tối thiểu
scheduling-mix-new-cards-and-reviews = Trộn lẫn thẻ mới và thẻ ôn tập
scheduling-new-cards = Thẻ Mới
scheduling-new-cardsday = Thẻ mới/ngày
scheduling-new-interval = Khoảng mới
scheduling-new-options-group-name = Tên nhóm tùy chọn mới:
scheduling-options-group = Nhóm tùy chọn:
scheduling-order = Thứ tự
scheduling-parent-limit = (giới hạn ở cấp trên: { $val })
scheduling-reset-counts = Đặt lại số lần lặp lại và bỏ sót
scheduling-restore-position = Khôi phục vị trí ban đầu nếu có thể
scheduling-review = Ôn tập
scheduling-reviews = Ôn tập
scheduling-seconds = giây
scheduling-set-all-decks-below-to = Đặt tất cả bộ thẻ dưới { $val } vào nhóm tùy chọn này?
scheduling-set-for-all-subdecks = Đặt cho tất cả bộ thẻ con
scheduling-show-answer-timer = Hiện đồng hồ bấm giờ trả lời
scheduling-show-new-cards-after-reviews = Hiện thẻ mới sau phần ôn tập
scheduling-show-new-cards-before-reviews = Hiện các thẻ mới trước khi ôn tập
scheduling-show-new-cards-in-order-added = Hiện các thẻ mới theo thứ tự thêm vào
scheduling-show-new-cards-in-random-order = Hiện các thẻ mới theo thứ tự ngẫu nhiên
scheduling-starting-ease = Độ dễ ban đầu
scheduling-steps-in-minutes = Bước (phút)
scheduling-steps-must-be-numbers = Bước phải là số
scheduling-tag-only = Chỉ gắn Nhãn
scheduling-the-default-configuration-cant-be-removed = Không thể loại bỏ cấu hình mặc định.
scheduling-your-changes-will-affect-multiple-decks = Thay đổi của bạn sẽ tác động đến nhiều bộ thẻ. Nếu bạn chỉ muốn thay đổi bộ thẻ hiện tại, vui lòng thêm vào một nhóm tuỳ chọn mới trước.
scheduling-deck-updated =
    { $count ->
       *[other] Đã cập nhật { $count } bộ thẻ.
    }
scheduling-set-due-date-prompt =
    { $cards ->
       *[other] Hiển thị thẻ trong bao nhiêu ngày?
    }
scheduling-set-due-date-prompt-hint =
    0 = hôm nay
    1! = ngày mai + đặt lại khoảng thời gian xem xét
    3-7 = lựa chọn ngẫu nhiên trong khoảng 3-7 ngày
scheduling-set-due-date-done =
    { $cards ->
       *[other] Đặt ngày đến hạn của { $cards } thẻ.
    }
scheduling-graded-cards-done = Đã chấm điểm { $cards } thẻ.
scheduling-forgot-cards =
    { $cards ->
       *[other] Quên { $cards } thẻ.
    }
