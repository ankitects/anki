### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = 已新增：{ $up }↑ { $down }↓
sync-media-removed-count = 已删除：{ $up }↑ { $down }↓
sync-media-checked-count = 已检查：{ $count }
sync-media-starting = 媒体同步开始…
sync-media-complete = 媒体同步完成。
sync-media-failed = 媒体同步失败。
sync-media-aborting = 正在中止媒体同步…
sync-media-aborted = 媒体同步已中止。
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = 媒体同步已禁用。
# Title of the screen that shows syncing progress history
sync-media-log-title = 媒体同步日志

## Error messages / dialogs

sync-conflict = 仅能同时同步一个设备的数据，请稍后重试。
sync-server-error = AnkiWeb 服务出现错误，请稍后重试。
sync-client-too-old = 当前 Anki 软件版本过低，请更新至最新版本以继续同步。
sync-wrong-pass = 用户名或密码错误，请重试。
sync-resync-required = 请尝试再次同步。如此信息反复出现，请在支持网站上反馈。
sync-must-wait-for-end = 当前正在同步，请在同步完成后重试。
sync-confirm-empty-download = 本地集合为空，是否立即从 AnkiWeb 下载？
sync-confirm-empty-upload = AnkiWeb 的集合没有卡片。是否要用本地集合替换 AnkiWeb 的集合？
sync-conflict-explanation =
    本地牌组与 AnkiWeb 上的牌组存在不能合并的差异，所以必须覆盖其中之一。
    
    如选择下载，将使用 AnkiWeb 上的数据覆盖本地数据，本设备上未同步的更改将全部丢失。
    
    如选择上传，将使用本地数据覆盖 AnkiWeb 上的数据，AnkiWeb 上的更改将全部丢失。
    
    当所有设备同步完成后，之后的复习和新增卡片都能自动合并。
sync-conflict-explanation2 =
    此设备上的牌组与 AnkiWeb 有冲突。您必须选择保留其中一个版本：
    
    - **{ sync-download-from-ankiweb }**：使用 AnkiWeb 上的版本替换此设备的牌组，您会失去自上次同步以来在此设备作出的任何更改。
    - **{ sync-upload-to-ankiweb }**：使用此设备的牌组覆盖 AnkiWeb 上的版本，并删除 AnkiWeb 上的任何更改。
    
    冲突解决后，同步将照常进行。
sync-ankiweb-id-label = AnkiWeb 账号：
sync-password-label = 密码：
sync-account-required =
    <h1>需要账号</h1>
    需要一个免费帐号以同步您的集合，请先<a href="{ $link }">注册</a>帐号，并在下方登录。
sync-sanity-check-failed = 请使用「检查数据库」功能，然后再次同步。若问题仍然存在，请在设置界面中进行强制单向同步。
sync-clock-off = 无法同步 - 本地时间设置错误。
sync-upload-too-large =
    集合文件过大，无法上传至 AnkiWeb。请删除不需要的牌组以减小文件大小（可先导出牌组），并使用「检查数据库」功能以缩小文件大小。
    
    { $details }（未压缩）
sync-sign-in = 登录
sync-ankihub-dialog-heading = AnkiHub 登录
sync-ankihub-username-label = 用户名或邮箱：
sync-ankihub-login-failed = 无法使用提供的凭据登录 AnkiHub。
sync-ankihub-addon-installation = AnkiHub 插件安装

## Buttons

sync-media-log-button = 媒体日志
sync-abort-button = 中止
sync-download-from-ankiweb = 从 AnkiWeb 下载
sync-upload-to-ankiweb = 上传到 AnkiWeb
sync-cancel-button = 取消

## Normal sync progress

sync-downloading-from-ankiweb = 正在从 AnkiWeb 下载…
sync-uploading-to-ankiweb = 正在上传到 AnkiWeb…
sync-syncing = 正在同步…
sync-checking = 正在检查…
sync-connecting = 正在连接…
sync-added-updated-count = 已新增/修改：{ $up }↑ { $down }↓
sync-log-in-button = 登录
sync-log-out-button = 退出登录
sync-collection-complete = 集合同步完成
