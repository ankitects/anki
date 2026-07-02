## Shown at the top of the media check screen

media-check-window-title = 檢查媒體檔
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    垃圾桶資料夾：{ $count ->
       *[other] { $count } 個檔案，{ $megs } MB
    }
media-check-missing-count = 遺失檔案數：{ $count }
media-check-unused-count = 未使用檔案數：{ $count }
media-check-renamed-count = 重新命名檔案數：{ $count }
media-check-oversize-count = 大於 100MB 的檔案數：{ $count }
media-check-subfolder-count = 子資料夾數：{ $count }
media-check-extracted-count = 擷取影像數：{ $count }

## Shown at the top of each section

media-check-renamed-header = 部分檔案因相容性問題而被重新命名：
media-check-oversize-header = 大於 100MB 的檔案無法同步到 AnkiWeb。
media-check-subfolder-header = 不支援媒體資料夾中的資料夾。
media-check-missing-header = 在媒體資料夾中找不到以下被卡片使用的檔案：
media-check-unused-header = 媒體資料夾中的這些檔案未被卡片使用：
media-check-template-references-field-header =
    當你在媒體/LaTeX 標籤中加入「{ "{{欄位}}" }」來引用檔案時，Anki 將無法偵測引用的檔案。請將媒體/LaTeX 標籤單獨加入到每一則筆記中。
    
    以下模板存在此問題：

## Shown once for each file

media-check-renamed-file = 重新命名：{ $old } -> { $new }
media-check-oversize-file = 大於 100MB：{ $filename }
media-check-subfolder-file = 資料夾：{ $filename }
media-check-missing-file = 遺失：{ $filename }
media-check-unused-file = 未使用：{ $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }：{ $card_type }（{ $side }）

## Progress

media-check-checked = 已檢查 { $count }...

## Deleting unused media

media-check-delete-unused-confirm = 要刪除未使用的媒體檔嗎？
media-check-files-remaining =
    剩餘 { $count ->
       *[other] { $count } 個檔案。
    }
media-check-delete-unused-complete =
    { $count ->
       *[other] 已移動 { $count } 個檔案
    }到垃圾桶。
media-check-trash-emptied = 已清空垃圾桶資料夾。
media-check-trash-restored = 已回復刪除的檔案到媒體資料夾。

## Rendering LaTeX

media-check-all-latex-rendered = 已轉譯所有 LaTeX。

## Buttons

media-check-delete-unused = 刪除未使用檔案
media-check-render-latex = 轉譯 LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = 清空垃圾桶
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = 回復已刪除檔案
media-check-check-media-action = 檢查媒體檔
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = 媒體檔遺失
# add a tag to notes with missing media
media-check-add-tag = 為遺失媒體檔的筆記加上標籤
