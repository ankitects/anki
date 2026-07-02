### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = Đã thêm: { $up }↑ { $down }↓
sync-media-removed-count = Đã xóa: { $up }↑ { $down }↓
sync-media-checked-count = Đã kiểm tra: { $count }
sync-media-starting = Đang khởi chạy đồng bộ hóa phương tiện...
sync-media-complete = Đồng bộ hóa phương tiện đã hoàn tất.
sync-media-failed = Đồng bộ hóa phương tiện không thành công.
sync-media-aborting = Đang hủy đồng bộ hóa phương tiện...
sync-media-aborted = Đã hủy đồng bộ hóa phương tiện.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = Đã tắt đồng bộ hóa phương tiện.
# Title of the screen that shows syncing progress history
sync-media-log-title = Nhật ký đồng bộ hóa phương tiện

## Error messages / dialogs

sync-conflict = Chỉ một bản sao của Anki có thể đồng bộ hóa với tài khoản của bạn trong cùng một lúc. Vui lòng đợi một vài phút, sau đó thử lại.
sync-server-error = AnkiWeb đã gặp sự cố. Vui lòng thử lại trong vài phút.
sync-client-too-old = Phiên bản Anki của bạn quá cũ. Vui lòng cập nhật lên phiên bản mới nhất để tiếp tục đồng bộ hóa.
sync-wrong-pass = ID hoặc mật khẩu Anki Web không chính xác; xin vui lòng thử lại.
sync-resync-required = Vui lòng đồng bộ lại. Nếu thông báo này tiếp tục xuất hiện, vui lòng đăng trên trang web hỗ trợ.
sync-must-wait-for-end = Anki hiện đang đồng bộ hóa. Vui lòng đợi quá trình đồng bộ hóa hoàn tất, sau đó thử lại.
sync-confirm-empty-download = Bộ sưu tập gốc không có thẻ. Tải xuống từ AnkiWeb?
sync-confirm-empty-upload = Bộ sưu tập AnkiWeb không có thẻ. Thay thế với bộ sưu tập gốc?
sync-conflict-explanation =
    Bộ thẻ của bạn ở đây và trên AnkiWeb khác nhau đến nỗi không thể kết hợp với nhau được, vì vậy cần phải ghi đè một bộ lên bộ còn lại.
    
    Nếu chọn tải xuống, Anki sẽ tải xuống bộ sưu tập trên AnkiWeb, và mọi thay đổi trên máy tính từ lần đồng bộ trước sẽ bị mất.
    
    Nếu chọn tải lên, Anki sẽ tải bộ sưu tập của bạn lên AnkiWeb, và mọi thay đổi trên AnkiWeb hoặc các thiết bị khác từ lần đồng bộ trước sẽ bị mất.
    
    Sau khi mọi thiết bị đã được đồng bộ, những lần ôn tập và thêm thẻ mới trong tương lai sẽ được tự động kết hợp với nhau.
sync-conflict-explanation2 =
    Có mâu thuẫn giữa các bộ thẻ trên thiết bị này và AnkiWeb. Bạn phải chọn phiên bản để giữ lại:
    
    - Chọn **{ sync-download-from-ankiweb }** để thay thế các bộ thẻ trên thiết bị này bằng phiên bản của AnkiWeb. Bạn sẽ mất mọi thay đổi đã thực hiện trên thiết bị này từ lần đồng bộ cuối cùng.
    - Chọn **{ sync-upload-to-ankiweb }** để ghi đè phiên bản của AnkiWeb bằng bộ thẻ từ thiết bị này và xóa mọi thay đổi trên AnkiWeb.
    
    Sau khi vấn đề được giải quyết, quá trình đồng bộ sẽ tiếp tục hoạt động bình thường.
sync-ankiweb-id-label = ID AnkiWeb:
sync-password-label = Mật khẩu:
sync-account-required =
    <h1>Yêu cầu tài khoản</h1>
    Bạn cần có tài khoản (miễn phí) để đồng bộ bộ sưu tập. Xin vui lòng <a href="{ $link }">đăng ký</a> tài khoản rồi nhập thông tin chi tiết vào phía dưới.
sync-sanity-check-failed = Vui lòng sử dụng chức năng Kiểm tra Cơ sở dữ liệu, sau đó đồng bộ hóa lại. Nếu sự cố vẫn tiếp diễn, vui lòng buộc đồng bộ hóa hoàn toàn trong màn hình tùy chọn.
sync-clock-off = Không thể đồng bộ hóa - đồng hồ của bạn không được đặt đúng giờ.
# “details” expands to a string such as “300.14 MB > 300.00 MB”
sync-upload-too-large =
    Tệp bộ sưu tập của bạn quá lớn để gửi đến AnkiWeb. Bạn có thể giảm kích thước
    bằng cách loại bỏ bất kỳ bộ bài không mong muốn nào (tùy chọn xuất chúng trước), và 
    sau đó sử dụng Kiểm tra Cơ sở dữ liệu để thu nhỏ kích thước tệp xuống. ({ $details })
sync-sign-in = Đăng nhập
sync-ankihub-dialog-heading = Đăng nhập AnkiHub
sync-ankihub-username-label = Tên người dùng hoặc email:
sync-ankihub-login-failed = Không thể đăng nhập lên AnkiHub với thông tin đã cung cấp.
sync-ankihub-addon-installation = Tải tiện ích AnkiHub

## Buttons

sync-media-log-button = Nhật ký Phương tiện
sync-abort-button = Hủy bỏ
sync-download-from-ankiweb = Tải xuống từ AnkiWeb
sync-upload-to-ankiweb = Tải lên AnkiWeb
sync-cancel-button = Hủy

## Normal sync progress

sync-downloading-from-ankiweb = Đang tải xuống từ AnkiWeb...
sync-uploading-to-ankiweb = Đang tải lên AnkiWeb...
sync-syncing = Đang đồng bộ...
sync-checking = Đang kiểm tra...
sync-connecting = Đang kết nối...
sync-added-updated-count = Đã thêm/sửa đổi: { $up }↑ { $down }↓
sync-log-in-button = Đăng nhập
sync-log-out-button = Đăng xuất
sync-collection-complete = Đã hoàn thành đồng bộ hóa bộ sưu tập.
