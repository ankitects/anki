importing-failed-debug-info = 导入失败。调试信息：
importing-aborted = 已中止：{ $val }
importing-added-duplicate-with-first-field = 已添加首字段的副本：{ $val }
importing-all-supported-formats = 所有支持的格式 { $val }
importing-allow-html-in-fields = 允许在字段中使用 HTML
importing-anki-files-are-from-a-very = .anki 文件来自 Anki 较为早期的版本。您可以使用「175027074」附加组件或在 Anki 网站上找到 Anki 2.0 版本来导入它们。
importing-anki2-files-are-not-directly-importable = .anki2 文件无法直接导入 - 请导入您接收到的 .apkg 或 .zip 文件。
importing-appeared-twice-in-file = 文件中出现两次：{ $val }
importing-by-default-anki-will-detect-the = 默认情况下，Anki 将自动检测字段间的分隔字符（如制表符，逗号等）。如未能正确检测分隔字符，请在这里输入。用「\t」代表制表符。
importing-cannot-merge-notetypes-of-different-kinds =
    填空题模板不能与常规模板合并。
    您仍然可以在禁用「{ importing-merge-notetypes }」的情况下导入文件。
importing-change = 修改
importing-colon = 冒号
importing-comma = 逗号
importing-empty-first-field = 首字段为空：{ $val }
importing-field-separator = 字段分隔符
importing-field-separator-guessed = 字段分隔符（自动检测）
importing-field-mapping = 字段匹配
importing-field-of-file-is = 文件中的第 <b>{ $val }</b> 个字段为：
importing-fields-separated-by = 字段分隔字符：{ $val }
importing-file-must-contain-field-column = 文件应至少包含一个可以对应到笔记字段的列。
importing-file-version-unknown-trying-import-anyway = 文件版本未知，尝试继续导入。
importing-first-field-matched = 首字段匹配的：{ $val }
importing-identical = 完全相同
importing-ignore-field = 忽略字段
importing-ignore-lines-where-first-field-matches = 当导入行的首字段与现有笔记相同时，忽略该行
importing-ignored = <忽略>
importing-import-even-if-existing-note-has = 当导入行的首字段与现有笔记相同时，仍然导入
importing-import-options = 导入选项
importing-importing-complete = 导入成功。
importing-invalid-file-please-restore-from-backup = 无效文件。请从备份恢复。
importing-map-to = 对应到 { $val }
importing-map-to-tags = 对应到标签
importing-mapped-to = 对应到 <b>{ $val }</b>
importing-mapped-to-tags = 对应到<b>标签</b>
# the action of combining two existing note types to create a new one
importing-merge-notetypes = 合并笔记模板
importing-merge-notetypes-help =
    勾选后，若您或牌组作者更改了笔记模板的架构，Anki 将不会保留两个笔记模板，而会将它们合并。
    
    更改笔记模板的架构包括对「字段」或「模板」进行「增加」「移除」或「重排位置」操作。
    反例：对正面内容模板进行修改**不属于**更改笔记模板架构。
    
    警告：这将需要进行强制单向同步，同时可能将现有笔记视为已被修改。
importing-mnemosyne-20-deck-db = Mnemosyne 2.0 牌组（*.db）
importing-multicharacter-separators-are-not-supported-please = 不支持多分隔字符，请输入单个分隔字符。
importing-new-deck-will-be-created = 一个新的牌组将会被创建：{ $name }
importing-notes-added-from-file = 从文件中添加的笔记：{ $val }
importing-notes-found-in-file = 文件中找到的笔记：{ $val }
importing-notes-skipped-as-theyre-already-in = 因现有集合中已存在而跳过的笔记：{ $val }
importing-notes-skipped-update-due-to-notetype = 由于在笔记首次导入后修改过笔记模板，笔记未能更新：{ $val }
importing-notes-updated-as-file-had-newer = 因文件有新版本而更新的笔记：{ $val }
importing-include-reviews = 包含复习情况
importing-also-import-progress = 导入所有学习进度信息
importing-with-deck-configs = 导入所有牌组预设配置
importing-updates = 更新
importing-include-reviews-help =
    启用后，牌组分享者的复习情况也会被导入。
    若未启用，所有卡片将被视为新卡片导入，同时将会移除所有记忆难点「leech」及「marked」标签。
importing-with-deck-configs-help =
    如果启用该选项，所有牌组分享者的牌组预设配置也会被导入。
    若未启用该选项，所有牌组会使用默认预设。
importing-packaged-anki-deckcollection-apkg-colpkg-zip = 打包的 Anki 牌组/集合（*.apkg *.colpkg *.zip）
# the '|' character
importing-pipe = 字符（|）
# Warning displayed when the csv import preview table is clipped (some columns were hidden)
# $count is intended to be a large number (1000 and above)
importing-preview-truncated = 仅显示前 { $count } 列。若出现问题，请尝试更改字段分隔符。
importing-rows-had-num1d-fields-expected-num2d = 「{ $row }」行有 { $found } 个字段，应有 { $expected } 个
importing-selected-file-was-not-in-utf8 = 选择的文件不是 UTF-8 格式的。请查看帮助文档的导入章节。
importing-semicolon = 分号
importing-skipped = 已跳过
importing-tab = 制表符
importing-tag-modified-notes = 已修改标签的笔记：
importing-text-separated-by-tabs-or-semicolons = 以制表符或分号分隔的文本 (*)
importing-the-first-field-of-the-note = 笔记模板的首字段必须匹配。
importing-the-provided-file-is-not-a = 此文件不是有效的 .apkg 文件。
importing-this-file-does-not-appear-to = 此文件不是有效的 .apkg 文件。如果您是从 AnkiWeb 下载的，请尝试重新下载。如问题仍然存在，请尝试使用其他浏览器。
importing-this-will-delete-your-existing-collection = 将删除现有集合，并以导入文件中的集合替换，是否确认？
importing-unable-to-import-from-a-readonly = 无法从只读文件中导入。
importing-unknown-file-format = 未知文件格式。
importing-update-existing-notes-when-first-field = 当导入行的首字段与现有笔记相同时，更新笔记
importing-updated = 已更新
importing-update-if-newer = 仅在笔记较现有新时
importing-update-always = 始终
importing-update-never = 从不
importing-update-notes = 更新笔记
importing-update-notes-help = 选择在哪种情况下更新集合中现有的笔记。默认情况下，仅在导入的相应笔记的更改较新时才会进行该操作。
importing-update-notetypes = 更新笔记模板
importing-update-notetypes-help = 选择在哪种情况下更新集合中现有的笔记模板。默认情况下，仅在导入的相应笔记模板的更改较新时才会进行该操作。对模板内容和样式的修改始终会被导入，但对笔记模板架构的更改（如字段的数量或顺序被更改），则需要额外启用「{ importing-merge-notetypes }」选项。
importing-note-added =
    { $count ->
       *[other] 已添加 { $count } 条笔记
    }
importing-note-imported =
    { $count ->
       *[other] 已导入 { $count } 条笔记
    }
importing-note-unchanged =
    { $count ->
       *[other] { $count } 条笔记未变更
    }
importing-note-updated =
    { $count ->
       *[other] 已更新 { $count } 条笔记
    }
importing-processed-media-file =
    { $count ->
       *[other] 已导入 { $count } 个媒体文件
    }
importing-importing-file = 正在导入文件…
importing-extracting = 数据提取中…
importing-gathering = 数据收集中…
importing-failed-to-import-media-file = 媒体文件导入失败：{ $debugInfo }
importing-processed-notes =
    { $count ->
       *[other] 已处理 { $count } 条笔记…
    }
importing-processed-cards =
    { $count ->
       *[other] 已处理 { $count } 张卡片…
    }
importing-existing-notes = 现有笔记
# "Existing notes: Duplicate" (verb)
importing-duplicate = 复制
# "Existing notes: Preserve" (verb)
importing-preserve = 保留
# "Existing notes: Update" (verb)
importing-update = 更新
importing-tag-all-notes = 标记所有笔记
importing-tag-updated-notes = 标记已更新的笔记
importing-file = 文件
# "Match scope: notetype / notetype and deck". Controls how duplicates are matched.
importing-match-scope = 匹配范围
# Used with the 'match scope' option
importing-notetype-and-deck = 笔记模板和牌组
importing-cards-added =
    { $count ->
       *[other] 已添加 { $count } 条笔记
    }
importing-file-empty = 您选择的是空文件。
importing-notes-added =
    { $count ->
       *[other] 已导入 { $count } 条新笔记。
    }
importing-notes-updated =
    { $count ->
       *[other] 已更新 { $count } 条现有笔记。
    }
importing-existing-notes-skipped =
    { $count ->
       *[other] 有 { $count } 条笔记已在您的集合中。
    }
importing-notes-failed = 有 { $count } 张笔记无法导入。
importing-conflicting-notes-skipped =
    { $count ->
       *[other] { $count } 条笔记未被导入，因为其笔记模板已被更改。
    }
importing-conflicting-notes-skipped2 =
    { $count ->
       *[other] { $count } 条笔记未被导入，因为其笔记模板已被更改，且选项「{ importing-merge-notetypes }」未被启用。
    }
importing-import-log = 导入日志
importing-no-notes-in-file = 未在文件中找到笔记。
importing-notes-found-in-file2 =
    { $notes ->
       *[other] 在文件中找到 { $notes } 条笔记。
    }其中：
importing-show = 展示
importing-details = 详细情况
importing-status = 状态
importing-duplicate-note-added = 重复笔记已添加
importing-added-new-note = 新笔记已添加
importing-existing-note-skipped = 笔记被跳过，因为您集合中已存在最新的副本。
importing-note-skipped-update-due-to-notetype = 笔记未更新，因为自您首次导入此笔记以来笔记模板已被修改
importing-note-skipped-update-due-to-notetype2 = 由于笔记模板在首次导入笔记后已被修改，同时「{ importing-merge-notetypes }」未被启用，因此笔记未能被更新。
importing-note-updated-as-file-had-newer = 笔记已更新，因为文件中有较新的版本
importing-note-skipped-due-to-missing-notetype = 笔记被跳过，因为缺少其笔记模板
importing-note-skipped-due-to-missing-deck = 笔记被跳过，因为缺少其牌组
importing-note-skipped-due-to-empty-first-field = 笔记被跳过，因为其首字段为空
importing-field-separator-help =
    文本文件中用以分隔字段的字符。您可以使用预览来检查字段分隔是否正确。
    请注意，若字段内容包含该字符本身，则该字段内容需依照 CSV 标准加上引号。
    LibreOffice 等工作表程序会自动进行该操作。
    若文本文件通过文件头强制指定了特定分隔符，则无法修改此设置。若不存在文件头，Anki 将自动检测分隔符。
importing-allow-html-in-fields-help =
    如果文件中包含 HTML 格式，请启用此选项。例如，若文件包含字符串「&lt;br&gt;」，则会在您的卡片中显示为换行。
    如果您未启用此选项，则会原样显示「&lt;br&gt;」。
importing-notetype-help =
    新导入的笔记将会使用该笔记模板，且仅使用该笔记模板的现有笔记会被更新。
    
    您可以使用下面的「字段匹配」工具选择文件中字段与笔记模板字段的对应关系。
importing-deck-help = 卡片将被导入该牌组中。
importing-existing-notes-help =
    当导入的笔记与现有笔记匹配时执行的操作。
    
    - `{ importing-update }`：更新现有笔记。
    - `{ importing-preserve }`：不执行任何操作。
    - `{ importing-duplicate }`：创建一个新的笔记。
importing-match-scope-help = 只会在笔记模板相同的现有笔记中检查重复项目。可额外限制为卡片在相同牌组的笔记。
importing-tag-all-notes-help = 这些标签将添加到新导入和更新的笔记。
importing-tag-updated-notes-help = 这些标签将添加到任何更新的笔记。
importing-overview = 概览

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

importing-importing-collection = 正在导入集合…
importing-unable-to-import-filename = 未能导入 { $filename }：不支持的文件类型
importing-notes-that-could-not-be-imported = 因笔记模板已变更而无法导入的笔记：{ $val }
importing-added = 已添加
importing-pauker-18-lesson-paugz = Pauker 1.8 课程（*.pau.gz）
importing-supermemo-xml-export-xml = Supermemo XML 导出（*.xml）
