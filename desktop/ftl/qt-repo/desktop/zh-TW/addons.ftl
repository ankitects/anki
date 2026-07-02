addons-possibly-involved = 可能與以下附加元件有關：{ $addons }
addons-failed-to-load =
    無法載入你安裝的一個附加元件。如果問題持續出現，請前往「工具」>「附加元件」選單，並停用或刪除此附加元件。
    
    載入「{ $name }」時：
    { $traceback }
addons-failed-to-load2 =
    以下附加元件載入失敗：
    { $addons }
    
    你可能需要更新這些附加元件來支援這個版本的 Anki。按一下「{ addons-check-for-updates }」按鈕來檢查有沒有更新可用。
    
    向附加元件作者回報時，你可以使用「{ about-copy-debug-info }」按鈕，在回報中附上取得的資訊。
    
    若有附加元件沒有更新可用，你可以停用或刪除這些附加元件來避免此訊息出現。
addons-startup-failed = 附加元件啟動失敗
# Shown in the add-on configuration screen (Tools>Add-ons>Config), in the title bar
addons-config-window-title = 設定「{ $name }」
addons-config-validation-error = 提供的設定存在問題：{ $problem }，位於路徑 { $path }，對於結構描述 { $schema }。
addons-window-title = 附加元件
addons-addon-has-no-configuration = 附加元件沒有設定檔。
addons-addon-installation-error = 附加元件安裝出現錯誤
addons-browse-addons = 瀏覽附加元件
addons-changes-will-take-effect-when-anki = 更動將在 Anki 重新啟動後生效。
addons-check-for-updates = 檢查更新
addons-checking = 檢查中...
addons-code = 代碼：
addons-config = 設定
addons-configuration = 設定
addons-corrupt-addon-file = 損毀的附加元件檔案。
addons-disabled = （已停用）
addons-disabled2 = （已停用）
addons-download-complete-please-restart-anki-to = 下載完成。請重新啟動 Anki 以套用更動。
addons-downloaded-fnames = 已下載 { $fname }
addons-downloading-adbd-kb02fkb = 下載中 { $part }/{ $total } ({ $kilobytes }KB)...
addons-error-downloading-ids-errors = 下載 <i>{ $id }</i> 時發生錯誤：{ $error }
addons-error-installing-bases-errors = 安裝 <i>{ $base }</i> 時發生錯誤：{ $error }
addons-get-addons = 取得附加元件...
addons-important-as-addons-are-programs-downloaded = <b>重要提示</b>：附加元件來自網路，因此有可能是惡意程式。<b>請僅安裝可信任的附加元件。</b><br><br>確定要安裝以下 Anki 附加元件嗎？<br><br>%(names)s
addons-install-addon = 安裝附加元件
addons-install-addons = 安裝附加元件
addons-install-anki-addon = 安裝 Anki 附加元件
addons-install-from-file = 從檔案安裝...
addons-installation-complete = 安裝完成
addons-installed-names = 已安裝 { $name }
addons-installed-successfully = 安裝成功。
addons-invalid-addon-manifest = 無效的附加元件資訊清單。
addons-invalid-code = 無效代碼。
addons-invalid-code-or-addon-not-available = 代碼無效，或附加元件與該 Anki 版本不相容。
addons-invalid-configuration = 無效設定：
addons-invalid-configuration-top-level-object-must = 無效設定：頂層物件必須為 map
addons-no-updates-available = 無可用更新。
addons-one-or-more-errors-occurred = 發生了一或多個錯誤：
addons-packaged-anki-addon = 已封裝的 Anki 附加元件
addons-please-check-your-internet-connection = 請檢查你的網際網路連線。
addons-please-report-this-to-the-respective = 請將此內容回報到對應的附加元件作者。
addons-please-restart-anki-to-complete-the = <b>請重新啟動 Anki 來完成安裝。</b>
addons-please-select-a-single-addon-first = 請先選取單個附加元件。
addons-requires = （需要{ $val }）
addons-restored-defaults = 已回復預設
addons-the-following-addons-are-incompatible-with = 由於與 { $name } 不相容，以下附加元件已被停用：{ $found }
addons-the-following-addons-have-updates-available = 以下附加元件有可用的更新。立即安裝？
addons-the-following-conflicting-addons-were-disabled = 由於發生衝突，以下附加元件已被停用：
addons-this-addon-is-not-compatible-with = 此附加元件與你的 Anki 版本不相容。
addons-to-browse-addons-please-click-the = 請按下方按鈕以瀏覽附加元件。<br><br>當你找到想要的附加元件時，請將其代碼在下方貼上。你可以貼上多條代碼，以空格分開。
addons-toggle-enabled = 啟用/停用
addons-unable-to-update-or-delete-addon = 無法更新或刪除附加元件。請在打開 Anki 時按住 Shift 鍵來暫時停用附加元件，然後再試一次。 除錯資訊：{ $val }
addons-unknown-error = 未知錯誤：{ $val }
addons-view-addon-page = 檢視附加元件頁面
addons-view-files = 檢視檔案
addons-delete-the-numd-selected-addon =
    { $count ->
       *[other] 要刪除選取的 { $count } 個附加元件嗎？
    }
addons-choose-update-window-title = 更新附加元件
addons-choose-update-update-all = 全部更新
