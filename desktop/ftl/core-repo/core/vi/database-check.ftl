database-check-corrupt = Bộ sưu tập bị hỏng. Xin vui lòng tham khảo tài liệu hướng dẫn.
database-check-rebuilt = Đã tái dựng và tối ưu hóa cơ sở dữ liệu.
database-check-card-properties = Đã sửa { $count } thẻ có thuộc tính không hợp lệ.
database-check-card-last-review-time-empty = Đã thêm { $count } thẻ vào lần ôn trước.
database-check-missing-templates = Đã xóa { $count } thẻ thiếu kiểu mẫu.
database-check-field-count =
    { $count ->
       *[other] Đã sửa { $count } phiếu có số trường sai.
    }
database-check-new-card-high-due =
    { $count ->
       *[other] Đã thấy { $count } thẻ mới có số đến hạn >= 1,000,000 - xem xét định vị lại chúng trong màn hình Duyệt.
    }
database-check-card-missing-note = Đã xóa { $count } thẻ thiếu ghi chú.
database-check-duplicate-card-ords =
    { $count ->
       *[other] Đã xóa { $count } thẻ có mẫu trùng lặp.
    }
database-check-missing-decks =
    { $count ->
       *[other] Đã sửa { $count } bộ thẻ bị thiếu.
    }
database-check-revlog-properties =
    { $count ->
       *[other] Đã sửa { $count } thẻ ôn tập được nhập có thuộc tính không hợp lệ.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
       *[other] Đã sửa { $count } phiếu có kí tự utf8 không hợp lệ.
    }
database-check-fixed-invalid-ids = Đã sửa { $count } đối tượng với thời gian trong tương lai.
# "db-check" is always in English
database-check-notetypes-recovered = Thiếu một hoặc nhiều loại phiếu. Các phiếu sử dụng chúng đã được phân vào loại phiếu mới bắt đầu bằng "db-check", nhưng tên trường và thiết kế thẻ đã bị mất, vì vậy tốt hơn hết bạn nên khôi phục từ bản sao lưu tự động.

## Progress info

database-check-checking-integrity = Đang kiểm tra bộ sưu tập...
database-check-rebuilding = Đang dựng lại...
database-check-checking-cards = Đang kiểm tra thẻ...
database-check-checking-notes = Đang kiểm tra phiếu...
database-check-checking-history = Đang kiểm tra lịch sử...
database-check-title = Kiểm tra Cơ sở dữ liệu
