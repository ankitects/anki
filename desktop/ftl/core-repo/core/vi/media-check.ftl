## Shown at the top of the media check screen

media-check-window-title = Kiểm tra Phương tiện
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    Thư mục thùng rác: { $count ->
       *[other] { $count } tệp, { $megs }MB
    }
media-check-missing-count = Thiếu tệp: { $count }
media-check-unused-count = Tệp không sử dụng: { $count }
media-check-renamed-count = Tệp đã được đổi tên: { $count }
media-check-oversize-count = Trên 100MB: { $count }
media-check-subfolder-count = Thư mục con: { $count }
media-check-extracted-count = Hình ảnh đã trích xuất: { $count }

## Shown at the top of each section

media-check-renamed-header = Một số tệp đã được đổi tên để tương thích:
media-check-oversize-header = Không thể đồng bộ hóa các tệp trên 100MB với AnkiWeb.
media-check-subfolder-header = Các thư mục bên trong thư mục media không được hỗ trợ.
media-check-missing-header = Phương tiện được thẻ sử dụng nhưng không có trong thư mục phương tiện:
media-check-unused-header = Các tệp sau đây đã được tìm thấy trong thư mục phương tiện, nhưng dường như không được sử dụng trên bất kỳ thẻ nào:
media-check-template-references-field-header =
    Anki không thể phát hiện tập tin khi bạn sử dụng { "{{Field}}" } trong phương tiện/nhãn LaTeX. Bạn cần để phương tiện/nhãn LaTeX trên từng ghi chú một.
    
    Mẫu tham chiếu:

## Shown once for each file

media-check-renamed-file = Đã đổi tên: { $old } -> { $new }
media-check-oversize-file = Trên 100MB: { $filename }
media-check-subfolder-file = Thư mục: { $filename }
media-check-missing-file = Thiếu: { $filename }
media-check-unused-file = Không sử dụng: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = Đã kiểm tra { $count }...

## Deleting unused media

media-check-delete-unused-confirm = Xoá dữ liệu phương tiện không dùng ?
media-check-files-remaining =
    { $count ->
       *[other] { $count } thư mục
    } còn lại.
media-check-delete-unused-complete =
    { $count ->
       *[other] Đã chuyển { $count } tệp tin
    } vào thùng rác
media-check-trash-emptied = Đã dọn sạch thư mục thùng rác.
media-check-trash-restored = Đã khôi phục các tệp đã xóa vào thư mục phương tiện.

## Rendering LaTeX

media-check-all-latex-rendered = Tất cả LaTeX được kết xuất.

## Buttons

media-check-delete-unused = Xóa Không sử dụng
media-check-render-latex = Kết xuất LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = Dọn sạch Thùng rác
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Khôi phục Đã xóa
media-check-check-media-action = Kiểm tra Phương tiện
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = phương-tiện-thiếu
# add a tag to notes with missing media
media-check-add-tag = Nhãn Thiếu
