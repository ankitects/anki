## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = Tìm kiếm không hợp lệ: { $reason }
search-misplaced-and = một `và` đã được tìm thấy nhưng nó không kết nối hai cụm từ tìm kiếm. Nếu bạn muốn tự tìm kiếm từ này, hãy đặt nó trong dấu ngoặc kép: `" và "`.
search-misplaced-or = một `hoặc` đã được tìm thấy nhưng nó không kết nối hai cụm từ tìm kiếm. Nếu bạn muốn tìm kiếm chính từ đó, hãy đặt nó trong dấu ngoặc kép: `" hoặc "`.
# Here, the ellipsis "..." may be localised.
search-empty-group = một nhóm `(...)` đã được tìm thấy, nhưng không có gì nằm giữa các dấu ngoặc để tìm kiếm. Nếu bạn muốn tìm kiếm các dấu ngoặc theo nghĩa đen, hãy đặt chúng trong dấu ngoặc kép: `" () "`.
search-unopened-group = một dấu ngoặc đóng `)` đã được tìm thấy, nhưng không có dấu ngoặc mở `(` trước nó. Nếu bạn muốn tìm kiếm một chữ `)`, hãy đặt nó trong dấu ngoặc kép hoặc thêm dấu gạch chéo ngược: `") "` hoặc ` \) '.
search-unclosed-group = một dấu ngoặc mở '(`được tìm thấy, nhưng không có dấu ngoặc đóng') 'theo sau nó. Nếu bạn muốn tìm kiếm một chữ `(`, hãy đặt nó trong dấu ngoặc kép hoặc thêm dấu gạch chéo ngược: `" ("` hoặc `\ (`.
search-empty-quote = một cặp dấu ngoặc kép `" "` đã được tìm thấy, nhưng không có gì ở giữa chúng để tìm kiếm. Nếu bạn muốn tìm kiếm các dấu ngoặc kép theo nghĩa đen, hãy thêm dấu gạch chéo ngược vào trước: `\" \ "`.
search-unclosed-quote = một dấu ngoặc kép mở đầu `" `đã được tìm thấy, nhưng không có dấu ngoặc kép thứ hai để đóng nó. Nếu bạn muốn tìm kiếm một từ` "` theo nghĩa đen, hãy thêm dấu gạch chéo ngược: `\" `.
search-missing-key = dấu hai chấm `:` được tìm thấy, nhưng không có từ khóa nào đứng trước nó. Nếu bạn muốn tìm kiếm một chữ `: ', hãy thêm dấu gạch chéo ngược vào trước:` \:'.
search-unknown-escape = trình tự thoát `{ $val }` không được xác định. Nếu bạn muốn tìm kiếm một dấu gạch chéo ngược theo nghĩa đen `\`, hãy thêm vào trước một dấu khác: `\\`.
search-invalid-argument = `{ $term }`đã được cung cấp một đối số không hợp lệ'`{ $argument }`'.
search-invalid-flag-2 = `cờ:` phải được theo sau bởi một số cờ hợp lệ: `1` (đỏ), `2` (cam), `3` (lục), `4` (lam), `5` (hồng), `6 ` (lam ngọc), `7` (tím) hoặc `0` (không có cờ).
search-invalid-prop-operator = `prop:{ $val }` phải được theo sau bởi một trong các toán tử so sánh sau: `=`, `!=`, `<`, `>`, `<=` hoặc `>=`.
search-invalid-other = vui lòng kiểm tra lỗi đánh máy.

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = dự tính một số trong "`{ $context }`", nhưng đã tìm thấy "`{ $provided }`".
search-invalid-whole-number = dự tính một số nguyên trong "`{ $context }`", nhưng đã tìm thấy "`{ $provided }`".
search-invalid-positive-whole-number = dự tính một số nguyên dương trong "`{ $context }`", nhưng đã tìm thấy "`{ $provided }`".
search-invalid-negative-whole-number = dự tính một số nguyên nhỏ hơn hoặc bằng 0 trong "`{ $context }`", nhưng đã tìm thấy "`{ $provided }`".
search-invalid-answer-button = dự tính một đáp án trong khoảng từ 1-4 trong "`{ $context }`", nhưng lại tìm thấy "`{ $context }`",

## Column labels in browse screen

search-note-modified = Đã sửa
search-card-modified = Đã đổi

##

# Tooltip for search lines outside browser
search-view-in-browser = Xem trong trình duyệt
