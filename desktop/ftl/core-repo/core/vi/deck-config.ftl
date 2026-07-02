### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    đang được sử dụng bởi { $decks ->
       *[other] { $decks } bộ thẻ
    }
deck-config-default-name = Mặc định
deck-config-title = Tùy chỉnh Bộ bài

## Daily limits section

deck-config-daily-limits = Giới hạn hàng ngày
deck-config-new-limit-tooltip =
    Số lượng thẻ mới tối đa xuất ra trong một ngày, nếu có thẻ mới.
    Bởi vì tài liệu mới sẽ làm tăng khối lượng công việc ôn tập ngắn hạn của bạn, điều này thường nên ít nhất 10 lần ít hơn so với giới hạn ôn tập của bạn.
deck-config-review-limit-tooltip =
    Số lượng thẻ ôn tập tối đa để hiển thị trong một ngày,
    nếu thẻ đã sẵn sàng để ôn tập.
deck-config-limit-deck-v3 =
    Khi học một bộ bài có Tập con chứa bên trong nó, giới hạn đặt trên mỗi
    Tập con điều chỉnh số Thẻ tối đa được rút ra từ bộ bài cụ thể đó.
    Giới hạn của bộ bài được chọn điều chỉnh tổng số Thẻ sẽ xuất hiện.
deck-config-limit-new-bound-by-reviews =
    Giới hạn ôn tập ảnh hưởng đến giới hạn mới. Ví dụ, nếu giới hạn xem xét của bạn là
    đặt thành 200 và bạn có 190 thẻ ôn tập đang chờ, tối đa 10 thẻ mới sẽ
    được giới thiệu. Nếu bạn đã đạt đến giới hạn ôn tập, sẽ không có thẻ mới
    được xem.
deck-config-limit-interday-bound-by-reviews = Giới hạn ôn tập cũng sẽ áp dụng cho những thẻ cần học trong ngày. Khi áp dụng giới hạn, hệ thống sẽ hiển thị thẻ trong ngày trước, sau đó ôn lại, và cuối cùng là những thẻ mới.
deck-config-tab-description =
    - `Cài đặt sắn`: Giới hạn áp dụng cho tất cả các bộ thẻ sử dụng cài đặt sẵn này.
    - `Bộ thẻ này`: Giới hạn áp dụng cho bộ thẻ này.
    - `Chỉ hôm nay`: Tạm thời thay đổi giới hạn cho bộ thẻ này.
deck-config-new-cards-ignore-review-limit = Thẻ mới bỏ qua giới hạn ôn tập
deck-config-new-cards-ignore-review-limit-tooltip =
    Theo mặc định, giới hạn ôn tập được sự dụng trên thẻ mới, và các thẻ mới sẽ
    không hiển diện khi đến giới hạn ôn tập. Nếu tùy chọn này được bật, các thẻ mới
    sẽ hiển diện bất kể giới hạn ôn tập.
deck-config-apply-all-parent-limits = Giới hạn bắt đầu từ trên cùng
deck-config-apply-all-parent-limits-tooltip =
    Theo mặc đinh, giới hạn hàng ngày của một bộ thẻ bậc cao không được sử dụng khi bạn học từ tập con
    Nếu tùy chọn này được bất, giới hạn sẽ
    bắt đầu từ bộ thẻ bậc cao. Điều này có hữu ích nếu bạn muốn học từng tập con một trong khi tuân theo một giới hạn tổng trên tất cả các thẻ trông bộ.
deck-config-affects-entire-collection = Ảnh hưởng đến toàn bộ sưu tập.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Nhóm trước
deck-config-deck-only = Bộ thẻ này
deck-config-today-only = Hôm nay thôi

## New Cards section

deck-config-learning-steps = Bước học
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Độ trễ thường là phút (vd `1m`) hoặc ngày (vd: `2d`), nhưng giờ (vd `1h`) và giây (vd`30s`) cũng được hỗ trợ.
deck-config-learning-steps-tooltip =
    Một hoặc nhiều lần trì hoãn, được phân tách bằng dấu cách. Thời gian trễ đầu tiên sẽ được sử dụng
    khi bạn nhấn nút `Lại` trên thẻ mới, và theo mặc định là 1 phút.
    Nút `Tốt` sẽ chuyển sang bước tiếp theo, theo mặc định là 10 phút.
    Khi tất cả các bước đã được thông qua, thẻ này sẽ trở thành một thẻ ôn tập, và
    sẽ xuất hiện vào một ngày khác. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip =
    Số ngày phải chờ trước khi hiển thị lại thẻ, sau khi nút `Tốt`
    được nhấn vào bước học cuối cùng.
deck-config-easy-interval-tooltip =
    Số ngày phải chờ trước khi hiển thị lại thẻ, sau khi nút `Dễ`
    được sử dụng để xóa ngay một thẻ khỏi quá trình học.
deck-config-new-insertion-order = Lệnh chèn
deck-config-new-insertion-order-tooltip =
    Kiểm soát vị trí (do #) thẻ mới được chỉ định khi bạn thêm thẻ mới.
    Các thẻ có số đến hạn thấp hơn sẽ được hiển thị đầu tiên khi học. Thay đổi
    tùy chọn này sẽ tự động cập nhật vị trí hiện có của các thẻ mới.
deck-config-new-insertion-order-sequential = Tuần tự (thẻ cũ nhất trước)
deck-config-new-insertion-order-random = Ngẫu nhiên
deck-config-new-insertion-order-random-with-v3 =
    Với bộ lập lịch biểu v3, bạn nên giữ cài đặt tuần tự và
    thay đổi thứ tự tập hợp thẻ.

## Lapses section

deck-config-relearning-steps = Bước học lại
deck-config-relearning-steps-tooltip =
    Độ trễ bằng 0 hoặc nhiều hơn, được phân tách bằng dấu cách. Theo mặc định, nhấn nút `Lại`
    trên thẻ ôn tập sẽ hiển thị lại sau 10 phút. Nếu không có sự chậm trễ
    xen vào, thẻ sẽ thay đổi khoảng thời gian, mà không cần nhập
    phân chia lại. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip =
    Số lần `Lại` cần được nhấn vào thẻ ôn tập trước khi nó được
    được đánh dấu là một con đỉa. Đỉa là loại thẻ ngốn rất nhiều thời gian của bạn, và
    khi một thẻ được đánh dấu là một con đỉa, bạn nên viết lại nó, xóa nó, hoặc
    nghĩ ra một phương pháp ghi nhớ để giúp bạn nhớ nó.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `Chỉ Nhãn`: Thêm nhãn "đỉa" vào phiếu và hiển thị cửa sổ bật lên.
    
    `Ngừng Thẻ`: Ngoài việc gắn nhãn cho phiếu, hãy ẩn thẻ cho đến khi nó
    không bị tạm dừng theo cách thủ công.

## Burying section

deck-config-bury-title = Đang tạm hoãn
deck-config-bury-new-siblings = Hoãn các thẻ anh em của thẻ mới cho tới ngày tiếp theo
deck-config-bury-review-siblings = Hoãn các thẻ anh em của thẻ ôn tập cho tới ngày tiếp theo
deck-config-bury-interday-learning-siblings = Tạm hoãn thẻ anh em trong ngày
deck-config-bury-new-tooltip = Các thẻ mới cùng loại ghi chú (v.d. thẻ ngược, liền kề với khoảng điền) có bị trì hoãn đến ngày hôm sau hay không.
deck-config-bury-review-tooltip = Các thẻ ôn tạp cùng loại ghi chú có bị trì hoãn đến ngày hôm sau hay không.
deck-config-bury-interday-learning-tooltip =
    Các thẻ đang học cùng loại ghi chú với khoảng thời gian > 1 ngày
    có bị trì hoãn đến ngày hôm sau hay không.
deck-config-bury-priority-tooltip =
    Anki bắt đầu tập hợp các thẻ bằng các thẻ đang học trong ngày, sau đó 
    các thẻ đang học ngày khác, rồi các thẻ ôn tập, và cuối cùng là các thẻ mới. Việc này
    có liên quan đến phương pháp tạm hoãn:
    - Nếu bạn bật tất cả các tùy chọn tạm hoãn, thẻ anh em đến sớm nhất
    trong danh sách sẽ hiển thị. Ví dụ, một thẻ ôn tập sẽ hiện trước một thẻ mới.
    - Thẻ anh em đến sau trong danh sách không thể tạm hoãn những loại thẻ khác. Ví dụ,
    nếu bạn tắt việc tạm hoãn thẻ mới, và học một thẻ mới, các thẻ đang học trong ngày sẽ không
    bị tạm hoãn và bạn có thể thấy cả thẻ anh em ôn tập, cả thẻ anh em mới trong mọt buổi học.

## Gather order and sort order of cards

deck-config-ordering-title = Thứ tự hiển thị
deck-config-new-gather-priority = Ưu tiên nhóm thẻ mới
deck-config-new-gather-priority-tooltip-2 =
    `Bộ thẻ`: Thu thập các thẻ từ mỗi tập con theo thứ tự, bắt đầu từ trên cùng. Các thẻ từ mỗi tập con được
    thu theo thứ tự tăng dần. Nếu bộ thẻ đã chọn đạt đến giới hạn trong ngày, việc thu thập có thể dừng lại 
    trước khi kiểm tra tất cả các tập con. Thứ tự này nhanh nhất trong các bộ thẻ lớn và
    cho phép bạn ưu tiên các tập con gần đầu hơn.
    
    `Vị trí tăng dần`: Thu thập các thẻ theo thứ tự tăng dần (số #), thường là thẻ được thêm vào trước.
    
    `Vị trí giảm dần`: Thu thập các thẻ theo thứ tự giảm dần (số #), thường là thẻ được thêm vào sau cùng.
    
    `Ghi chú ngẫu nhiên`: Chọn các ghi chú ngẫu nhiên, sau đó thu thập tất cả các thẻ liên quan.
    
    `Thẻ ngẫu nhiên`: Thu thập các thẻ theo thứ tự ngẫu nhiên.
deck-config-new-card-sort-order = Thứ tự sắp xếp thẻ mới
deck-config-new-card-sort-order-tooltip-2 =
    `Loại thẻ, sau đó theo thứ tự`: Hiển thị các thẻ theo thứ tự của loại thẻ.
    Các thẻ của mỗi loại được hiển thị theo thứ tự được sắp xếp.
    Nếu bạn đã tắt tùy chọn tạm hoãn thẻ anh em, tùy chọn này sẽ đảm bảo tất cả các thẻ 
    trước → sau được hiển thị trước thẻ sau → trước.
    Tùy chọn này hữu ích nễu bạn muốn hiển thị tất cả các thẻ cùng một ghi chú trong cùng một buổi học, 
    nhưng không hiển thị chúng quá gần nhau.
    
    `Thứ tự được sắp xếp`: Hiển thị các thẻ như chúng được sắp xếp. Nếu tùy tạm hoãn thẻ anh em bị tắt,
    điều này thường dẫn đến việc tất cả các thẻ của một ghi chú được hiển thị lần lượt.
    
    `Loại thẻ, sau đó ngẫu nhiên`: Hiển thị các thẻ theo thứ tự số loại thẻ. Các thẻ của mỗi loại thẻ
    số được hiển thị theo thứ tự ngẫu nhiên. Thứ tự này hữu ích nếu bạn không muốn các thẻ anh em
    xuất hiện quá gần nhau, nhưng vẫn muốn các thẻ xuất hiện theo thứ tự ngẫu nhiên.
    
    `Ghi chú ngẫu nhiên, sau đó theo loại thẻ`: Chọn các ghi chú ngẫu nhiên, sau đó hiển thị tất cả các thẻ liên kết
    theo thứ tự.
    
    `Ngẫu nhiên`: Hiển thị các thẻ theo thứ tự ngẫu nhiên.
deck-config-new-review-priority = Ưu tiên thẻ mới/ôn tập
deck-config-new-review-priority-tooltip = Thời điểm hiển thị thẻ mới liên quan đến thẻ ôn tập.
deck-config-interday-step-priority = Ưu tiên học/ôn tập trong ngày
deck-config-interday-step-priority-tooltip = Khi nào hiển thị (lại) các thẻ học tập vượt qua ranh giới trong ngày.
deck-config-review-sort-order = Xem lại thứ tự sắp xếp
deck-config-review-sort-order-tooltip =
    Thứ tự mặc định ưu tiên các thẻ bị xếp vào hàng đợi lâu nhất, do đó
    Nếu bạn có bị tồn đọng thẻ ôn tập, những hàng đợi lâu nhất sẽ xuất hiện
    đầu tiên. Nếu bạn có một lượng lớn tồn đọng sẽ mất hơn vài ngày để giải quyết
    sạch sẽ, hoặc nếu muốn xem các thẻ theo thứ tự bộ thẻ con, bạn có thể tìm thẻ thay thế
    sắp xếp thứ tự thích hợp.
deck-config-display-order-will-use-current-deck =
    Anki sẽ sử dụng thứ tự hiển thị từ bộ thẻ mà bạn
    chọn để học, chứ không phải bất kỳ bộ thẻ con có thể có nào khác.

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = Bộ thẻ
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = Bộ thẻ, sau đó phiếu ngẫu nhiên
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = Vị trí tăng dần
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = Vị trí giảm dần
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = Phiếu ngẫu nhiên
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = Thẻ ngẫu nhiên
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = Loại thẻ, sau đó là ngẫu nhiên
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = Phiếu ngẫu nhiên, sau đó theo loại thẻ
# Sort the cards randomly.
deck-config-sort-order-random = Ngẫu nhiên
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = Theo mẫu thẻ, rồi đến thứ tự tập hợp thẻ
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = Theo thứ tự tập hợp thẻ
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = Trộn các thẻ ôn tập
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = Xem sau các thẻ ôn tập
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = Xem trước các thẻ ôn tập
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = Theo ngày đến hạn, sau đó ngẫu nhiên
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = Theo ngày đến hạn, sau đó theo bộ thẻ
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = Theo bộ thẻ, sau đó theo ngày đến hạn
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = Theo khoảng cách tăng dần
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = Theo khoảng cách giảm dần
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = Bội số tăng dần
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = Bội số giảm dần
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = Thẻ dễ trước
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = Thẻ khó trước
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = Khả năng truy xuất đi lên
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = Khả năng truy xuất đi xuống

## Timer section

deck-config-timer-title = Bộ hẹn giờ
deck-config-maximum-answer-secs = Số giây trả lời tối đa
deck-config-maximum-answer-secs-tooltip =
    Số giây tối đa được ghi lại cho một thẻ ôn tập đơn. Nếu một câu trả lời
    vượt quá thời gian này (vì bạn đã thoát ra khỏi màn hình chẳng hạn), 
    thời gian thực hiện sẽ được ghi lại dưới dạng giới hạn bạn đã đặt.
deck-config-show-answer-timer-tooltip =
    Trong màn hình xem lại, hiển thị bộ đếm số giây bạn
    ôn tập từng thẻ.
deck-config-stop-timer-on-answer = Dừng bộ hẹn giờ trên màn hình sau khi trả lời
deck-config-stop-timer-on-answer-tooltip =
    Việc dừng bộ hẹn giờ trên màn hình sau khi trả lời hay không.
    Điều này không ảnh hưởng đến thống kê.

## Auto Advance section

deck-config-seconds-to-show-question = Hiển thị câu hỏi trong vòng bao nhiêu giây
deck-config-seconds-to-show-question-tooltip-3 = Khi tính năng tự động đi tiếp được kích hoạt, phải chờ bao nhiêu giây trước khi áp dụng hành động khi hỏi. Đặt số 0 để tắt.
deck-config-seconds-to-show-answer = Hiển thị câu trả lời trong vòng bao nhiêu giây
deck-config-seconds-to-show-answer-tooltip-2 = Khi tính năng tự động đi tiếp được kích hoạt, phải chờ bao nhiêu giây trước khi áp dụng hành động khi trả lời. Đặt số 0 để tắt.
deck-config-question-action-show-answer = Hiển thị Câu trả lời
deck-config-question-action-show-reminder = Hiển thị Lời nhắc
deck-config-question-action = Hành động khi hỏi
deck-config-question-action-tool-tip = Hành động sau khi hiển thị câu hỏi và thời gian đã qua.
deck-config-answer-action = Hành động khi trả lời
deck-config-answer-action-tooltip-2 = Hành động sau khi hiển thị câu trả lời và thời gian đã qua.
deck-config-wait-for-audio-tooltip-2 = Đợi âm thanh phát xong trước khi tự động áp dụng hành động khi hỏi hoặc hành động khi trả lời.

## Audio section

deck-config-audio-title = Âm thanh
deck-config-disable-autoplay = Không tự động phát âm thanh
deck-config-disable-autoplay-tooltip =
    Khi được bật, Anki sẽ không tự động phát âm thanh.
    Bạn có thể phát thủ công bằng cách nhấp/chạm vào biểu tượng âm thanh hoặc sử dụng thao tác Phát lại.
deck-config-skip-question-when-replaying = Bỏ qua câu hỏi khi phát lại câu trả lời
deck-config-always-include-question-audio-tooltip =
    Liệu câu hỏi dạng âm thanh có nên được đưa vào khi tác vụ Phát lại
    được sử dụng hay không trong lúc nhìn vào mặt trả lời của thẻ.

## Advanced section

deck-config-advanced-title = Nâng cao
deck-config-maximum-interval-tooltip =
    Số ngày tối đa mà một thẻ ôn tập sẽ đợi. Khi các thẻ ôn tập
    đạt đến giới hạn, `Khó`, `Tốt` và `Dễ` đều sẽ có cùng độ trễ .
    Bạn đặt càng ngắn, khối lượng công việc của bạn sẽ càng lớn.
deck-config-starting-ease-tooltip =
    Thẻ mới bắt đầu với bội số dễ. Theo mặc định, nút `Tốt` trên một
    thẻ mới đã học sẽ hoãn lần ôn tập tiếp theo gấp 2,5 lần độ trễ trước đó.
deck-config-easy-bonus-tooltip =
    Một bội số bổ sung được áp dụng cho khoảng thời gian của thẻ ôn tập khi bạn xếp 
    chúng vào `Dễ`.
deck-config-interval-modifier-tooltip =
    Bội số này được áp dụng cho tất cả các thẻ ôn tập và các điều chỉnh thứ cấp có thể được sử dụng 
    để làm cho Anki thận trọng hơn hoặc tích cực hơn trong việc tự lập lịch trình. Vui lòng xem
    hướng dẫn sử dụng trước khi thay đổi tùy chọn này.
deck-config-hard-interval-tooltip = Cấp số được áp dụng cho một khoảng thời gian ôn tập khi trả lời `Khó`.
deck-config-new-interval-tooltip = Cấp số được áp dụng cho một khoảng thời gian ôn tập khi trả lời `Lại`.
deck-config-minimum-interval-tooltip = Khoảng thời gian tối thiểu được cung cấp cho thẻ ôn tập sau khi trả lời `Lại '.
deck-config-custom-scheduling = Tùy chỉnh lên lịch
deck-config-custom-scheduling-tooltip = Ảnh hưởng đến toàn bộ bộ sưu tập. Sử dụng bạn sẽ có nguy cơ gặp rủi ro!

## Easy Days section.

deck-config-easy-days-title = Ngày dễ
deck-config-easy-days-monday = Thứ 2
deck-config-easy-days-tuesday = Thứ 3
deck-config-easy-days-wednesday = Thứ 4
deck-config-easy-days-thursday = Thứ 5
deck-config-easy-days-friday = Thứ 6
deck-config-easy-days-saturday = Thứ 7
deck-config-easy-days-sunday = CN
deck-config-easy-days-normal = Bình thường
deck-config-easy-days-reduced = Giảm
deck-config-easy-days-minimum = Tối thiểu
deck-config-easy-days-no-normal-days = Ít nhất một ngày cần phải cài sang '{ deck-config-easy-days-normal }'.
deck-config-easy-days-change = Các bài ôn tập hiện tại sẽ không được lập lịch lại trừ khi '{ deck-config-reschedule-cards-on-change }' được bật trong tùy chọn FSRS.

## Adding/renaming

deck-config-add-group = Thêm nhóm trước
deck-config-name-prompt = Tên
deck-config-rename-group = Đổi tên nhóm trước
deck-config-clone-group = Nhân đôi nhóm trước

## Removing

deck-config-remove-group = Xóa nhóm trước
deck-config-will-require-full-sync = Thao tác thay đổi này yêu cầu tải lên toàn bộ cơ sở dữ liệu trong lần đồng bộ bộ sưu tập kế tiếp. Nếu bạn có phần ôn tập hoặc thay đổi khác trên thiết bị khác chưa được đồng bộ thì chúng sẽ bị mất.
deck-config-confirm-remove-name = Xóa { $name }?

## Other Buttons

deck-config-save-button = Lưu
deck-config-save-to-all-subdecks = Lưu vào mọi Tập con
deck-config-save-and-optimize = Tối ưu hóa Tất cả các Nhóm trước
deck-config-revert-button-tooltip = Khôi phục cài đặt gốc.

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Anki 2.1.41+ Chuyển hướng
deck-config-description-new-handling-hint =
    Xử lý đầu vào như đánh dấu và làm sạch đầu vào HTML. Khi được bật, các
    mô tả cũng sẽ được hiển thị trên màn hình chào mừng.
    Đánh dấu sẽ xuất hiện dưới dạng văn bản trên bản Anki 2.1.40 trở xuống.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    Bộ bài mẹ có giới hạn là { $cards ->
       *[other] { $cards } thẻ
    }sẽ ghi đè lên giới hạn này.
deck-config-reviews-too-low =
    Nếu thêm{ $cards ->
       *[other] thẻ mới mỗi ngày
    }, giới hạn ôn tập nên đặt ít nhất
deck-config-learning-step-above-graduating-interval = Khoảng thời gian hoàn thành ít nhất phải dài bằng bước học cuối cùng của bạn.
deck-config-good-above-easy = Khoảng thời gian dễ ít nhất phải dài bằng khoảng thời gian hoàn thành.
deck-config-relearning-steps-above-minimum-interval = Khoảng thời gian trôi đi tối thiểu ít nhất phải dài bằng bước học lại cuối cùng của bạn.
deck-config-maximum-answer-secs-above-recommended = Anki có thể lên lịch ôn tập hiệu quả hơn khi bạn đặt câu hỏi ngắn gọn.
deck-config-too-short-maximum-interval = Không nên cài đặt thời gian tối đa dưới 6 tháng.
deck-config-ignore-before-info = (Khoảng) { $included }/{ $totalCards } thẻ sẽ được sử dụng để tối ưu hóa cài đặt FSRS.

## Selecting a deck

deck-config-which-deck = Bạn muốn bộ thẻ nào?

## Messages related to the FSRS scheduler

deck-config-updating-cards = Đang cập nhật thẻ: { $current_cards_count }/{ $total_cards_count }...
deck-config-invalid-parameters = Cài đặt FSRS được cung cấp không hợp lệ. Vùi lòng để trường trống để sử dụng cài đặt mặc định.
deck-config-not-enough-history = Chưa đủ lịch sử ôn tập để thực hiện thao tác này.
deck-config-must-have-400-reviews = Chỉ tìm được { $count } bài ôn tập. Bạn cần có ít nhất 400 bài để thực hiện thao tác này.
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = Cài đặt FSRS
deck-config-compute-optimal-weights = Tối ưu hóa cài đặt FSRS
deck-config-optimize-button = Tối ưu hóa Cài đặt Hiện tại
# Indicates that a given function or label, provided via the "text" variable, operates slowly.
deck-config-slow-suffix = { $text } (chậm)
deck-config-compute-button = Tính
deck-config-ignore-before = Bỏ qua thẻ đã ôn rồi
deck-config-time-to-optimize = Nhiều thời gian đã trôi qua - bạn nên sử dụng nút Tối ưu hóa Cài đặt Hiện tại.
deck-config-evaluate-button = Đánh giá
deck-config-desired-retention = Thời gian lưu trữ mong muốn
deck-config-historical-retention = Lịch sử Thời gian lưu trữ
deck-config-smaller-is-better = Số nhỏ hơn hợp với lịch sử ôn tập của bạn hơn.
deck-config-steps-too-large-for-fsrs = Khi FSRS được bật, các bước 1 ngày hoặc hơn không được khuyến khích.
deck-config-get-params = Lấy Cài đặt
deck-config-complete = Xong { $num }%
deck-config-iterations = Lần lặp lại: { $count }...
deck-config-reschedule-cards-on-change = Lên lịch thẻ lại khi thay đổi
deck-config-fsrs-tooltip =
    Ảnh hưởng đến toàn bộ bộ sưu tập.
    
    Bộ lập lịch lặp lại khoảng cách tự do (FSRS) là một giải pháp thay thế cho thuật toán SuperMemo 2 (SM-2) cũ của Anki.
    Bằng cách xác định chính xác hơn khả năng bạn quên một thẻ, Anki có thể giúp bạn nhớ
    nhiều nội dung hơn trong cùng một khoảng thời gian. Cài đặt này được áp dụng cho tất cả các nhóm trước.
deck-config-desired-retention-tooltip =
    Theo mặc định, Anki lên lịch các thẻ để cho bạn có 90% khả năng nhớ khi ôn tập lại. 
    Nếu bạn tăng số này, Anki sẽ hiển thị thẻ thường xuyên hơn để tăng khả năng nhớ của bạn. 
    Nếu bạn giảm số này, Anki sẽ hiển thị thẻ ít hơn và bạn sẽ quên nhiều hơn. 
    Hãy thận trọng khi điều chỉnh số này - số cao hơn sẽ làm tăng đáng kể lượng công việc của bạn, 
    và số thấp hơn có thể gây nản lòng khi bạn quên nhiều tài liệu.
deck-config-desired-retention-tooltip2 = Chỉ số lượng công việc được cung cấp trong hộp thông tin chỉ là số ước tính. Để có độ chính xác cao hơn, hãy sử dụng trình mô phỏng.
deck-config-historical-retention-tooltip =
    Khi lịch sử ôn tập của bạn bị thiếu, FSRS cần điền vào các khoảng trống. Theo mặc định, 
    hệ thống sẽ giả định rằng khi bạn ôn tập trước đó, bạn đã nhớ 90% nội dung. Nếu tỷ lệ nhớ cũ 
    của bạn cao hơn hoặc thấp hơn 90% một cách đáng kể, việc điều chỉnh tùy chọn này sẽ cho phép 
    FSRS ước tính tốt hơn các buổi ôn tập bị thiếu.
    
    Lịch sử ôn tập của bạn có thể không đầy đủ vì hai lý do:
    1. Vì bạn đang sử dụng tùy chọn 'bỏ qua các thẻ đã được ôn trước đó'.
    2. Vì trước đó bạn đã xóa nhật ký ôn tập để giải phóng dung lượng hoặc đã nhập tài liệu từ một chương trình SRS khác.
    
    Trường hợp thứ hai khá hiếm, vì vậy trừ khi bạn đang sử dụng tùy chọn đầu tiên, bạn có thể không cần điều chỉnh tùy chọn này.
deck-config-weights-tooltip2 =
    Cài đặt FSRS ảnh hưởng đến cách lên lịch cho các thẻ. Anki sẽ bắt đầu với cài đặt mặc định. 
    Bạn có thể sử dụng tùy chọn bên dưới để tối ưu hóa các cài đặt sao cho phù hợp nhất với hiệu suất của bạn khi ôn các bộ thẻ này.
deck-config-reschedule-cards-on-change-tooltip =
    Ảnh hưởng đến toàn bộ bộ sưu tập và không được lưu lại.
    
    Tùy chọn này kiểm soát việc ngày đến hạn của thẻ sẽ được thay đổi khi bạn bật FSRS hoặc khi 
    bạn tối ưu hóa các cài đặt. Theo mặc định, các thẻ sẽ không lên lịch lại: các bài ôn tập trong tương lai 
    sẽ sử dụng lịch mới, nhưng sẽ không có thay đổi ngay lập tức đối với khối lượng công việc của bạn. 
    Nếu tính năng lên lịch lại được bật, ngày đến hạn của thẻ sẽ được thay đổi.
deck-config-reschedule-cards-warning =
    Tùy thuộc vào nhu cầu nhớ của bạn, điều này có thể dẫn đến việc một số lượng lớn thẻ đến hạn, 
    do đó khi bạn mới chuyển từ SM-2 thì việc sử dụng không được khuyến khích.
    
    Hãy sử dụng tùy chọn này một cách tiết kiệm, vì nó sẽ thêm một mục ôn tập vào mỗi thẻ của bạn 
    và làm tăng kích cỡ bộ sưu tập của bạn.
deck-config-ignore-before-tooltip-2 =
    Nếu được thiết lập, các thẻ đã ôn trước ngày được cung cấp sẽ bị bỏ qua khi tối ưu hóa các cài đặt FSRS.
    Điều này có thể hữu ích nếu bạn đã nhập dữ liệu lịch trình của người khác hoặc đã thay đổi cách sử dụng nút trả lời.
deck-config-compute-optimal-weights-tooltip2 =
    Khi bạn ấn nút Tối ưu hóa, FSRS sẽ phân tích lịch sử ôn tập của bạn và tạo ra các cài đặt tối ưu 
    cho trí nhớ và nội dung bạn đang học. Nếu các bộ bài của bạn có độ khó khác nhau, bạn nên gắn 
    cho chúng các cài đặt trước riêng biệt, vì các tham số cho bộ thẻ dễ và bộ thẻ khó sẽ khác nhau.
    Bạn không cần phải tối ưu hóa các tham số thường xuyên - vài tháng một lần là đủ.
    
    Theo mặc định, các cài đặt sẽ được tính toán từ lịch sử ôn tập của tất cả các bộ thẻ sử dụng 
    cài đặt trước hiện tại. Bạn cũng có thể điều chỉnh tìm kiếm trước khi tính toán các tham số, 
    nếu bạn muốn thay đổi số thẻ được sử dụng để tối ưu hóa các cài đặt.
deck-config-please-save-your-changes-first = Vui lòng lưu lại những thay đổi của bạn trước.
deck-config-workload-factor-change =
    Khối lượng công việc ước tính: { $factor }x
    (so với { $previousDR }% tỷ lệ nhớ mong muốn)
deck-config-workload-factor-unchanged = Số này càng to thì tần suất hiển thị thẻ cho bạn càng thường xuyên hơn.
deck-config-desired-retention-too-low = Tỷ lệ nhớ mong muốn của bạn rất thấp, điều này có thể dẫn đến khoảng thời gian dài hơn.
deck-config-desired-retention-too-high = Tỷ lệ nhớ mong muốn của bạn rất cao, điều này có thể dẫn đến khoảng thời gian ngắn hơn.
deck-config-percent-of-reviews = { $pct }% của { $reviews } bài ôn tập
deck-config-percent-input = { $pct }%
# This message appears during FSRS parameter optimization.
deck-config-checking-for-improvement = Đang kiểm tra để cải thiện...
deck-config-optimizing-preset = Đang tối ưu hóa cái đặt trước { $current_count }/{ $total_count }...
deck-config-fsrs-must-be-enabled = Bạn cần bật FSRS trước.
deck-config-fsrs-params-optimal = Các cài đặt FSRS có vẻ đã tối ưu rồi.
deck-config-fsrs-params-no-reviews = Không tìm thấy bài ôn nào. Hãy đảm bảo cài đặt trước này được gắn cho tất cả các bộ bài (bao gồm cả tập con) mà bạn muốn tối ưu hóa và thử lại.
deck-config-wait-for-audio = Chờ Âm thanh
deck-config-show-reminder = Hiển thị Lời nhắc
deck-config-answer-again = Trả lời lần nữa
deck-config-answer-hard = Trả lời Khó
deck-config-answer-good = Trả lời Tốt
deck-config-days-to-simulate = Ngày để mô phỏng
deck-config-desired-retention-below-optimal = Thời gian nhớ mong muốn của bạn chưa đạt mức tối ưu. Bạn nên tăng thời gian này.
# Description of the y axis in the FSRS simulation
# diagram (Deck options -> FSRS) showing the total number of
# cards that can be recalled or retrieved on a specific date.
deck-config-fsrs-simulator-experimental = Mô phỏng FSRS (thí nghiệm)
deck-config-fsrs-simulate-desired-retention-experimental = Mô phỏng FSRS cho Thời gian nhớ Mong Muốn (thí nghiệm)
deck-config-fsrs-simulate-save-preset = Sau khi tối ưu hóa, vui lòng lưu cài đặt trước của bộ thẻ trước khi bắt đầu mô phỏng.
deck-config-fsrs-desired-retention-help-me-decide-experimental = Giúp Tôi Chọn (thí nghiệm)
deck-config-additional-new-cards-to-simulate = Thẻ mới để mô phỏng
deck-config-simulate = Mô phỏng
deck-config-clear-last-simulate = Xóa Mô phỏng Trước
deck-config-fsrs-simulator-radio-count = Ôn tập
deck-config-advanced-settings = Cài đặt Nâng cao
deck-config-smooth-graph = Đồ thị cong
deck-config-suspend-leeches = Dừng thẻ bám
deck-config-save-options-to-preset = Lưu Thay đổi trên Cài đặt trước
deck-config-save-options-to-preset-confirm = Ghi đè các tùy chọn trong cài đặt trước hiện tại của bạn bằng các tùy chọn được cài đặt trong trình mô phỏng?
# Radio button in the FSRS simulation diagram (Deck options -> FSRS) selecting
# to show the total number of cards that can be recalled or retrieved on a
# specific date.
deck-config-fsrs-simulator-radio-memorized = Đã nhớ
deck-config-fsrs-simulator-radio-ratio = Tỷ lệ Thời gian / Đã nhớ
# $time here is pre-formatted e.g. "10 Seconds" 
deck-config-fsrs-simulator-ratio-tooltip = { $time } cho một thẻ đã nhớ

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.

# Checkbox
deck-config-health-check = Kiểm tra sức khỏe khi tối ưu hóa
# Message box showing the result of the health check
deck-config-fsrs-bad-fit-warning =
    Kiểm tra sức khỏe:
    FSRS không thể dự đoán trí nhớ của bạn một cách dễ dàng. Khuyến nghị:
    
    - Tạm hoãn hoặc sắp xếp lại bất kỳ thẻ nào bạn quên thường xuyên.
    - Sử dụng các nút trả lời một cách nhất quán. Lưu ý rằng "Khó" là điểm đạt, không phải điểm trượt.
    - Hiểu rõ trước khi ghi nhớ.
    
    Nếu bạn theo những gợi ý này, hiệu suất thường sẽ được cải thiện trong vài tháng tới.
# Message box showing the result of the health check
deck-config-fsrs-good-fit =
    Kiểm tra sức khỏe:
    FSRS có thể thích ứng tốt với trí nhớ của bạn.

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-unable-to-determine-desired-retention = Không thể xác định độ nhớ tối thiểu để khuyến nghị.
deck-config-predicted-minimum-recommended-retention = Độ nhớ tối thiểu khuyến nghị: { $num }
deck-config-compute-minimum-recommended-retention = Độ nhớ tối thiểu khuyến nghị
deck-config-compute-optimal-retention-tooltip4 =
    Công cụ này sẽ cố gắng tìm ra độ ghi nhớ mong muốn 
    giúp bạn tiếp thu được nhiều kiến ​​thức nhất trong thời gian ngắn nhất. Con số được tính 
    có thể dùng làm chỉ sổ tham chiếu khi quyết định mức ghi nhớ mong muốn của bạn.
    Bạn có thể chọn mức ghi nhớ mong muốn cao hơn nếu bạn sẵn sàng đầu tư nhiều thời gian học hơn. 
    Không nên đặt mức ghi nhớ mong muốn thấp hơn mức tối thiểu vì điều này sẽ dẫn đến 
    khối lượng công việc cao hơn do tỷ lệ quên cao hơn.
deck-config-plotted-on-x-axis = (Được vẽ trên trục X)
deck-config-a-100-day-interval = Khoảng thời gian 100 ngày sẽ trở thành { $days } ngày.
deck-config-fsrs-simulator-y-axis-title-time = <MARKED AS NOT NEEDED>
deck-config-fsrs-simulator-y-axis-title-count = <MARKED AS NOT NEEDED>
deck-config-fsrs-simulator-y-axis-title-memorized = <MARKED AS NOT NEEDED>
deck-config-bury-siblings = <MARKED AS NOT NEEDED>
deck-config-do-not-bury = <MARKED AS NOT NEEDED>
deck-config-bury-if-new = <MARKED AS NOT NEEDED>
deck-config-bury-if-new-or-review = <MARKED AS NOT NEEDED>
deck-config-bury-if-new-review-or-interday = <MARKED AS NOT NEEDED>
deck-config-bury-tooltip =
    Cho dù các thẻ khác cùng phiếu (vd các thẻ đảo, liền kề
    với khoảng điền) sẽ bị trì hoãn cho đến ngày hôm sau.
deck-config-seconds-to-show-question-tooltip = <MARKED AS NOT NEEDED>
deck-config-answer-action-tooltip = <MARKED AS NOT NEEDED>
deck-config-wait-for-audio-tooltip = <MARKED AS NOT NEEDED>
deck-config-ignore-before-tooltip = <MARKED AS NOT NEEDED>
deck-config-compute-optimal-retention-tooltip = <MARKED AS NOT NEEDED>
deck-config-health-check-tooltip1 = <MARKED AS NOT NEEDED>
deck-config-health-check-tooltip2 = <MARKED AS NOT NEEDED>
deck-config-compute-optimal-retention = <MARKED AS NOT NEEDED>
deck-config-predicted-optimal-retention = <MARKED AS NOT NEEDED>
deck-config-weights-tooltip = <MARKED AS NOT NEEDED>
deck-config-compute-optimal-weights-tooltip = <MARKED AS NOT NEEDED>
deck-config-compute-optimal-retention-tooltip2 = <MARKED AS NOT NEEDED>
deck-config-compute-optimal-retention-tooltip3 = <MARKED AS NOT NEEDED>
deck-config-seconds-to-show-question-tooltip-2 = <MARKED AS NOT NEEDED>
deck-config-invalid-weights = <MARKED AS NOT NEEDED>
deck-config-fsrs-on-all-clients = <MARKED AS NOT NEEDED>
deck-config-optimize-all-tip = <MARKED AS NOT NEEDED>
