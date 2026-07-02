importing-failed-debug-info = 匯入失敗。除錯資訊：
importing-aborted = 已中止：{ $val }
importing-added-duplicate-with-first-field = 已新增第一個欄位重複的項目：{ $val }
importing-all-supported-formats = 所有支援格式 { $val }
importing-allow-html-in-fields = 允許在欄位中使用 HTML 語法
importing-anki-files-are-from-a-very = .anki 檔是非常舊的 Anki 版本所使用的格式。你可以使用附加元件 175027074 或是 Anki 2.0 來匯入，可在 Anki 網站上取得。
importing-anki2-files-are-not-directly-importable = .anki2 檔無法直接匯入 - 請匯入你收到的 .apkg 檔或 .zip 檔。
importing-appeared-twice-in-file = 檔案中出現兩次：{ $val }
importing-by-default-anki-will-detect-the = Anki 預設會自動偵測欄位間的分隔符號，如定位字元 (tab) 和逗號。若偵測錯誤，請在此處手動輸入分隔符號。用「\t」來表示 tab。
importing-cannot-merge-notetypes-of-different-kinds =
    無法合併克漏字和普通筆記類型。
    你可以停用「{ importing-merge-notetypes }」選項來匯入檔案。
importing-change = 更改
importing-colon = 冒號
importing-comma = 逗號
importing-empty-first-field = 第一個欄位是空的：{ $val }
importing-field-separator = 欄位分隔符號
importing-field-separator-guessed = 欄位分隔符號（自動偵測）
importing-field-mapping = 欄位對應
importing-field-of-file-is = 檔案的第 <b>{ $val }</b> 個欄位為：
importing-fields-separated-by = 以 { $val } 分隔欄位
importing-file-must-contain-field-column = 檔案中應包含至少一行內容以對應到筆記欄位。
importing-file-version-unknown-trying-import-anyway = 檔案版本未知，嘗試繼續匯入。
importing-first-field-matched = 第一個欄位符合的：{ $val }
importing-identical = 相同
importing-ignore-field = 忽略欄位
importing-ignore-lines-where-first-field-matches = 忽略那些第一個欄位與現有筆記吻合的行數
importing-ignored = <忽略>
importing-import-even-if-existing-note-has = 即使第一個欄位與現有筆記相同，也要匯入
importing-import-options = 匯入選項
importing-importing-complete = 匯入完成。
importing-invalid-file-please-restore-from-backup = 檔案無效。請回復備份。
importing-map-to = 對應到 { $val }
importing-map-to-tags = 對應到標籤
importing-mapped-to = 對應到<b>{ $val }</b>
importing-mapped-to-tags = 對應到 <b>標籤</b>
# the action of combining two existing note types to create a new one
importing-merge-notetypes = 合併筆記類型
importing-merge-notetypes-help =
    勾選後，若你或牌組作者修改了牌組類型的架構，則 Anki 不會分別保留兩個版本，而會將它們合併。
    
    牌組類型的架構更動包括新增、移除及重新排列欄位或模板，或更改排序欄位。舉反例，更改現有模板的正面**不屬於**更改架構。
    
    警告：這將需要單向同步，且可能會將現有筆記標記為已修改。
importing-mnemosyne-20-deck-db = Mnemosyne 2.0 的牌組 (*.db)
importing-multicharacter-separators-are-not-supported-please = 無法使用多字符的分隔，請只輸入一個字符。
importing-new-deck-will-be-created = 將建立新牌組「{ $name }」
importing-notes-added-from-file = 從檔案中加入的筆記：{ $val }
importing-notes-found-in-file = 從檔案中找到的筆記：{ $val }
importing-notes-skipped-as-theyre-already-in = 由於最新的副本已在集合中，因此略過了筆記：{ $val }
importing-notes-skipped-update-due-to-notetype = 由於首次匯入筆記後修改過筆記類型，因此筆記未被更新：{ $val }
importing-notes-updated-as-file-had-newer = 因檔案有新版本而更新的筆記：{ $val }
importing-include-reviews = 包含複習
importing-also-import-progress = 匯入學習進度（如果存在）
importing-with-deck-configs = 匯入牌組預設組（如果存在）
importing-updates = 更新
importing-include-reviews-help = 啟用後，牌組分享者包含的複習歷程也會被匯入。如未啟用，則所有卡片將匯入為新卡片，並將移除「leech」及「marked」標籤。
importing-with-deck-configs-help = 啟用後，牌組分享者包含的任何牌組選項也會被匯入。如未啟用，則所有牌組都將使用預設選項。
importing-packaged-anki-deckcollection-apkg-colpkg-zip = 已封裝的 Anki 牌組/集合 (*.apkg *.colpkg *.zip)
# the '|' character
importing-pipe = 直立線符號 (|)
# Warning displayed when the csv import preview table is clipped (some columns were hidden)
# $count is intended to be a large number (1000 and above)
importing-preview-truncated = 僅顯示前 { $count } 個欄位。如果出現問題，請嘗試更改欄位分隔符號。
importing-rows-had-num1d-fields-expected-num2d = 「{ $row }」行有 { $found } 個欄位，預期 { $expected }
importing-selected-file-was-not-in-utf8 = 所選檔案不是 UTF-8 格式，請參閱使用手冊的匯入 (Importing) 章節。
importing-semicolon = 分號
importing-skipped = 已略過
importing-tab = 分頁
importing-tag-modified-notes = 為更動的筆記加上標籤：
importing-text-separated-by-tabs-or-semicolons = Tab 字元或分號所分隔的文字檔 (*)
importing-the-first-field-of-the-note = 筆記類型的第一個欄位必須相符。
importing-the-provided-file-is-not-a = 此檔案並非有效的 .apkg 檔
importing-this-file-does-not-appear-to = 此檔案不是有效的 .apkg 檔，如果你是從 AnkiWeb 下載檔案後收到此錯誤訊息，那有可能是下載失敗。請再試一次，如果問題持續，請換另一個網頁瀏覽器再試一次。
importing-this-will-delete-your-existing-collection = 這將刪除現有的集合，並被匯入的檔案取代。確定嗎？
importing-unable-to-import-from-a-readonly = 無法匯入唯讀檔案。
importing-unknown-file-format = 未知檔案格式。
importing-update-existing-notes-when-first-field = 第一個欄位相符時，更新現有筆記
importing-updated = 已更新
importing-update-if-newer = 版本較新
importing-update-always = 總是
importing-update-never = 永不
importing-update-notes = 更新筆記
importing-update-notes-help = 選擇集合中現有的筆記要在何時更新。根據預設，符合匯入的筆記只有在近期被修改過時才會被更新。
importing-update-notetypes = 更新筆記類型
importing-update-notetypes-help = 選擇集合中現有的筆記類型要在何時更新。根據預設，符合匯入的筆記類型只有在近期被修改過時才會被更新。模板文字和樣式更動總是會被匯入，但你需要啟用「{ importing-merge-notetypes }」選項來匯入架構更動（如欄位的數量或順序更動）。
importing-note-added =
    { $count ->
       *[other] 已加入 { $count } 則筆記
    }
importing-note-imported =
    { $count ->
       *[other] 已匯入 { $count } 則筆記。
    }
importing-note-unchanged =
    { $count ->
       *[other] { $count } 則筆記保持不變
    }
importing-note-updated =
    { $count ->
       *[other] 已更新 { $count } 則筆記
    }
importing-processed-media-file =
    { $count ->
       *[other] 己匯入 { $count } 個媒體檔
    }
importing-importing-file = 匯入檔案中...
importing-extracting = 擷取資料中...
importing-gathering = 蒐集資料中...
importing-failed-to-import-media-file = 匯入媒體檔案失敗：{ $debugInfo }
importing-processed-notes =
    { $count ->
       *[other] 已處理 { $count } 則筆記...
    }
importing-processed-cards =
    { $count ->
       *[other] 已處理 { $count } 張卡片...
    }
importing-existing-notes = 現有筆記
# "Existing notes: Duplicate" (verb)
importing-duplicate = 複製
# "Existing notes: Preserve" (verb)
importing-preserve = 不更改
# "Existing notes: Update" (verb)
importing-update = 更新
importing-tag-all-notes = 為全部筆記加上標籤
importing-tag-updated-notes = 為更新的筆記加上標籤
importing-file = 檔案
# "Match scope: notetype / notetype and deck". Controls how duplicates are matched.
importing-match-scope = 符合條件範圍
# Used with the 'match scope' option
importing-notetype-and-deck = 筆記類型和牌組
importing-cards-added =
    { $count ->
       *[other] 已加入 { $count } 張卡片。
    }
importing-file-empty = 你選取的檔案是空的。
importing-notes-added =
    { $count ->
       *[other] 已匯入 { $count } 則新筆記。
    }
importing-notes-updated =
    { $count ->
       *[other] 已更新 { $count } 則現有筆記。
    }
importing-existing-notes-skipped =
    { $count ->
       *[other] 有 { $count } 則筆記已在集合中。
    }
importing-notes-failed = 無法匯入 { $count } 則筆記。
importing-conflicting-notes-skipped =
    { $count ->
       *[other] { $count } 則筆記未被匯入，因為更改了筆記類型。
    }
importing-conflicting-notes-skipped2 =
    { $count ->
       *[other] 未匯入 { $count } 則筆記，因為更改了筆記類型，且未啟用「{ importing-merge-notetypes }」。
    }
importing-import-log = 匯入記錄
importing-no-notes-in-file = 在檔案中找不到筆記。
importing-notes-found-in-file2 =
    { $notes ->
       *[other] 在檔案中找到了 { $notes } 則筆記。
    }其中：
importing-show = 顯示
importing-details = 詳細資訊
importing-status = 狀態
importing-duplicate-note-added = 已加入重複的筆記
importing-added-new-note = 已加入新筆記
importing-existing-note-skipped = 已略過集合中已有最新副本的筆記
importing-note-skipped-update-due-to-notetype = 由於首次匯入筆記後修改過筆記類型，因此筆記未被更新
importing-note-skipped-update-due-to-notetype2 = 由於首次匯入筆記後修改過筆記類型，且未啟用「{ importing-merge-notetypes }」，因此筆記未被更新
importing-note-updated-as-file-had-newer = 已更新檔案中版本較新的筆記
importing-note-skipped-due-to-missing-notetype = 已略過缺少筆記類型的筆記
importing-note-skipped-due-to-missing-deck = 已略過缺少牌組的筆記
importing-note-skipped-due-to-empty-first-field = 已略過缺少第一個欄位的筆記
importing-field-separator-help =
    文字檔中分隔欄位的字元。你可以使用預覽來檢查欄位分隔是否正確。
    
    請注意，若有欄位內容包含分隔符號本身，則欄位應依 CSV 標準加上引號。LibreOffice 等試算表程式會自動加入引號。
importing-allow-html-in-fields-help = 若檔案包含 HTML 格式，啟用此選項。例如，若檔案中包含字串「&lt;br&gt;」則會在卡片中顯示為斷行。停用此選項時，則會被逐字轉譯為字元「&lt;br&gt;」。
importing-notetype-help =
    新匯入的筆記將使用這個筆記類型，且只有使用這個筆記類型的現有筆記會被更新。
    
    你可以使用對應工具來選擇與檔案欄位對應的筆記類型欄位。
importing-deck-help = 匯入的卡片將被放入這個牌組中。
importing-existing-notes-help =
    當匯入的筆記符合現有筆記時要執行的動作。
    
    - `{ importing-update }`：更新現有筆記。
    - `{ importing-preserve }`：不執行任何動作。
    - `{ importing-duplicate }`：建立一則新筆記。
importing-match-scope-help = 只會在筆記類型相同的現有筆記中檢查重複項目。可額外限制到卡片在相同牌組中的筆記。
importing-tag-all-notes-help = 新匯入和更新的筆記都將被加上這些標籤。
importing-tag-updated-notes-help = 任何更新的筆記都將被加上這些標籤。
importing-overview = 概覽

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

importing-importing-collection = 匯入集合中...
importing-unable-to-import-filename = 無法匯入 { $filename }：不支援此檔案類型
importing-notes-that-could-not-be-imported = 因更動筆記類型而無法匯入的筆記：{ $val }
importing-added = 已新增
importing-pauker-18-lesson-paugz = Pauker 1.8 課程 (*.pau.gz)
importing-supermemo-xml-export-xml = Supermemo XML 匯出檔 (*.xml)
