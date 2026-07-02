database-check-corrupt = 集合文件已损坏。请从自动备份中恢复。
database-check-rebuilt = 已优化重建数据库。
database-check-card-properties = 已修复 { $count } 条无效的卡片属性。
database-check-card-last-review-time-empty = 已为 { $count } 张卡片添加最后复习时间。
database-check-missing-templates = 已删除 { $count } 张缺失内容模板的卡片。
database-check-field-count =
    { $count ->
       *[other] 已修复 { $count } 条字段数量有误的笔记。
    }
database-check-new-card-high-due = 已找到 { $count } 张到期数字大于等于一百万的新卡片，建议在「浏览」界面重排其位置。
database-check-card-missing-note = 已删除 { $count } 张缺失笔记的卡片。
database-check-duplicate-card-ords = 已删除 { $count } 张内容模板重复的卡片。
database-check-missing-decks = 已修复 { $count } 个缺失的牌组。
database-check-revlog-properties = 已修复 { $count } 张属性无效的复习卡片。
database-check-notes-with-invalid-utf8 =
    { $count ->
       *[other] 已修复 { $count } 条有无效 UTF-8 字符的笔记。
    }
database-check-fixed-invalid-ids =
    { $count ->
       *[other] 已修复 { $count } 个时间戳为未来的对象。
    }
# "db-check" is always in English
database-check-notetypes-recovered = 至少一个笔记模板已丢失。已使用「db-check」开头的笔记模板进行替代，但由于字段名称和卡片设计均已丢失，仍建议您从自动备份中恢复。

## Progress info

database-check-checking-integrity = 正在检查集合…
database-check-rebuilding = 正在重建…
database-check-checking-cards = 正在检查卡片…
database-check-checking-notes = 正在检查笔记…
database-check-checking-history = 正在检查历史…
database-check-title = 检查数据库
