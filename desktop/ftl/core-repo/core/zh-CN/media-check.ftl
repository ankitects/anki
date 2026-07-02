## Shown at the top of the media check screen

media-check-window-title = 检查媒体文件
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    回收站：{ $count ->
       *[other] { $count } 个文件， { $megs }MB
    }
media-check-missing-count = 缺失的文件数：{ $count }
media-check-unused-count = 未被使用的文件数：{ $count }
media-check-renamed-count = 重命名文件数：{ $count }
media-check-oversize-count = 超过 100MB 的文件数：{ $count }
media-check-subfolder-count = 子文件夹数：{ $count }
media-check-extracted-count = 提取图像数：{ $count }

## Shown at the top of each section

media-check-renamed-header = 部分文件因兼容性问题被重命名：
media-check-oversize-header = 超过 100MB 的文件无法同步到 AnkiWeb。
media-check-subfolder-header = 媒体文件夹不支持子文件夹。
media-check-missing-header = 以下文件在卡片中被使用但在媒体文件夹里没找到：
media-check-unused-header = 以下文件存在于媒体文件夹中，但未被任何卡片使用：
media-check-template-references-field-header =
    当您在媒体/LaTeX 标签中加入 { "{{Field}}" } 来引用文件时，Anki 将无法检测到引用的文件，请将媒体/LaTeX 标签单独加入到每一个笔记当中。
    
    以下模板存在此问题：

## Shown once for each file

media-check-renamed-file = 重命名：{ $old }->{ $new }
media-check-oversize-file = 大于 100MB：{ $filename }
media-check-subfolder-file = 文件夹：{ $filename }
media-check-missing-file = 缺失：{ $filename }
media-check-unused-file = 未被使用：{ $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }：{ $card_type } （{ $side }）

## Progress

media-check-checked = 已检查{ $count }…

## Deleting unused media

media-check-delete-unused-confirm = 确定删除未使用的媒体文件吗？
media-check-files-remaining =
    剩余
    { $count ->
       *[other] { $count } 个文件。
    }
media-check-delete-unused-complete =
    { $count ->
       *[other] 已移动 { $count } 个文件
    }至回收站。
media-check-trash-emptied = 已清空回收站。
media-check-trash-restored = 已恢复删除的文件到媒体文件夹。

## Rendering LaTeX

media-check-all-latex-rendered = 已渲染所有 LaTeX。

## Buttons

media-check-delete-unused = 删除未使用的文件
media-check-render-latex = 渲染 LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = 清空回收站
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = 恢复已删除
media-check-check-media-action = 检查媒体文件
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = 缺失媒体文件
# add a tag to notes with missing media
media-check-add-tag = 标记缺失
