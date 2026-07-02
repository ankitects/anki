### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = 已新增：{ $up }↑ { $down }↓
sync-media-removed-count = 已移除：{ $up }↑ { $down }↓
sync-media-checked-count = 已檢查：{ $count }
sync-media-starting = 開始同步媒體...
sync-media-complete = 媒體同步完成。
sync-media-failed = 媒體同步失敗。
sync-media-aborting = 正在中止媒體同步...
sync-media-aborted = 已中止媒體同步。
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = 已停用媒體同步。
# Title of the screen that shows syncing progress history
sync-media-log-title = 媒體同步記錄

## Error messages / dialogs

sync-conflict = 一次只能同步一份 Anki 複本到你的帳號。請稍候幾分鐘，然後再試一次。
sync-server-error = AnkiWeb 遇到了一個問題。請過幾分鐘後再試一次。
sync-client-too-old = 你的 Anki 版本過舊。請更新至最新版本後再繼續同步。
sync-wrong-pass = 電子郵件或密碼錯誤；請再試一次。
sync-resync-required = 請再次同步。若此訊息持續出現，請回報至支援網站。
sync-must-wait-for-end = Anki 正在同步中。請等待同步完成，然後再試一次。
sync-confirm-empty-download = 本地集合沒有任何卡片。要從 AnkiWeb 下載嗎？
sync-confirm-empty-upload = AnkiWeb 集合中沒有卡片。要用本地集合取代 AnkiWeb 集合嗎？
sync-conflict-explanation =
    本地牌組與 AnkiWeb 上的牌組之間的差異無法被合併，因此必須覆寫其中一方。
    
    如果你選擇下載，Anki 將擷取 AnkiWeb 上的集合，自上次同步後，你在此裝置所作的更動將全部遺失。
    
    如果你選擇上傳，Anki 將傳送此裝置的資料到 AnkiWeb 上，AnkiWeb 上尚未同步至此裝置的更動將全部遺失。
    
    所有裝置都同步後，未來的複習及新增的卡片即可自動合併。
sync-conflict-explanation2 =
    此裝置上的牌組與 AnkiWeb 有衝突。必須選取要保留的版本：
    
    - **{ sync-download-from-ankiweb }**：使用 AnkiWeb 上的版本來取代這裡的牌組。在此裝置上一次同步過後所做的任何更動都將遺失。
    - **{ sync-upload-to-ankiweb }**：使用此裝置上的牌組來覆寫 AnkiWeb 上的版本，並刪除 AnkiWeb 上的所有更動。
sync-ankiweb-id-label = 電子郵件：
sync-password-label = 密碼：
sync-account-required =
    <h1>需要帳號</h1>
    要同步集合，請先<a href="{ $link }">建立</a>免費帳號，然後在下方輸入詳細資訊。
sync-sanity-check-failed = 請使用「檢查資料庫」功能，然後再次同步。如果問題持續，請到偏好設定內強制單向同步。
sync-clock-off = 無法同步 - 你的時鐘設定時間不正確。
# “details” expands to a string such as “300.14 MB > 300.00 MB”
sync-upload-too-large =
    你的集合檔案過大，無法傳送至 AnkiWeb。你可以移除（並可先匯出）不需要的牌組來減少檔案大小，並使用檢查資料庫來縮減檔案大小。
    （{ $details }）
sync-sign-in = 登入
sync-ankihub-dialog-heading = 登入 AnkiHub
sync-ankihub-username-label = 使用者名稱或電子郵件：
sync-ankihub-login-failed = 無法使用所給憑證來登入 AnkiHub。
sync-ankihub-addon-installation = AnkiHub 附加元件安裝

## Buttons

sync-media-log-button = 媒體記錄
sync-abort-button = 中止
sync-download-from-ankiweb = 從 AnkiWeb 下載
sync-upload-to-ankiweb = 上傳到 AnkiWeb
sync-cancel-button = 取消

## Normal sync progress

sync-downloading-from-ankiweb = 正在從 AnkiWeb 下載...
sync-uploading-to-ankiweb = 正在上傳到 AnkiWeb...
sync-syncing = 同步中...
sync-checking = 檢查中...
sync-connecting = 連線中...
sync-added-updated-count = 已新增/修改：{ $up }↑ { $down }↓
sync-log-in-button = 登入
sync-log-out-button = 登出
sync-collection-complete = 集合同步完成。
