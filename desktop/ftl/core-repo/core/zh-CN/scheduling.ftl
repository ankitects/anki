## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount } 秒
scheduling-answer-button-time-minutes = { $amount } 分
scheduling-answer-button-time-hours = { $amount } 小时
scheduling-answer-button-time-days = { $amount } 天
scheduling-answer-button-time-months = { $amount } 个月
scheduling-answer-button-time-years = { $amount } 年

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds = { $amount } 秒
scheduling-time-span-minutes = { $amount } 分钟
scheduling-time-span-hours = { $amount } 小时
scheduling-time-span-days = { $amount } 天
scheduling-time-span-months = { $amount } 个月
scheduling-time-span-years = { $amount } 年

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    下一张学习中的卡片将于{ $unit ->
        [seconds] { $amount } 秒
        [minutes] { $amount } 分
       *[hours] { $amount } 小时
    }内准备就绪。
scheduling-learn-remaining =
    { $remaining ->
       *[other] 今天将有 { $remaining } 张学习中的卡片到期。
    }
scheduling-congratulations-finished = 恭喜！您已完成当前牌组的学习！
scheduling-today-review-limit-reached =
    已达到今日复习上限，但仍有卡片尚待复习。
    为达最佳记忆效果， 可考虑在设置中提升每日上限。
scheduling-today-new-limit-reached = 尚有卡片可供学习，但已达今日学习上限，您可以在设置中提升学习上限。但需注意：学习的新卡片越多，短期内复习量就会越大。
scheduling-buried-cards-found = 搁置的卡片将在明天展示。如想立即查看，请{ $unburyThem }。
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = 取消搁置
scheduling-how-to-custom-study = 如想在常规计划外进行学习，请使用{ $customStudy }功能。
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = 自定义学习

## Scheduler upgrade

scheduling-update-soon = Anki 2.1 采用了新版的排程算法，解决了旧版本 Anki 中存在的一些问题。推荐更新。
scheduling-update-done = 已成功更新排程算法。
scheduling-update-button = 更新
scheduling-update-later-button = 稍后
scheduling-update-more-info-button = 更多信息
scheduling-update-required =
    集合需要升级到 V2 排程算法。
    请选择{ scheduling-update-more-info-button }后再继续。

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = 重播音频时总是包含问题面
scheduling-at-least-one-step-is-required = 请至少选择一个难易度
scheduling-automatically-play-audio = 自动播放音频
scheduling-bury-related-new-cards-until-the = 搁置相关新卡片到隔日
scheduling-bury-related-reviews-until-the-next = 搁置相关复习到隔日
scheduling-days = 天
scheduling-description = 描述
scheduling-easy-bonus = 「简单」复习间隔乘数
scheduling-easy-interval = 「简单」卡片再现间隔
scheduling-end = （结束）
scheduling-general = 总体
scheduling-graduating-interval = 「毕业」卡片再现间隔
scheduling-hard-interval = 「困难」复习间隔乘数
scheduling-ignore-answer-times-longer-than = 忽略回答时间上限：超过
scheduling-interval-modifier = 全局间隔乘数
scheduling-lapses = 遗忘
scheduling-lapses2 = 遗忘次数
scheduling-learning = 学习中
scheduling-leech-action = 记忆难点（leech）处理
scheduling-leech-threshold = 记忆难点（leech）阈值
scheduling-maximum-interval = 复习卡片的最大间隔（天）
scheduling-maximum-reviewsday = 每日复习的上限（次/天）
scheduling-minimum-interval = 最小间隔
scheduling-mix-new-cards-and-reviews = 混合新卡片和复习卡
scheduling-new-cards = 新卡片
scheduling-new-cardsday = 每日新卡片的上限（张/天）
scheduling-new-interval = 「重来」复习间隔乘数
scheduling-new-options-group-name = 新设置组名称：
scheduling-options-group = 设置组：
scheduling-order = 顺序
scheduling-parent-limit = （父牌组上限：{ $val }）
scheduling-reset-counts = 复习和遗忘次数归零
scheduling-restore-position = 如可能，恢复初始位置。
scheduling-review = 复习
scheduling-reviews = 复习
scheduling-seconds = 秒
scheduling-set-all-decks-below-to = 将此组预设配置应用于「{ $val }」下的所有牌组吗？
scheduling-set-for-all-subdecks = 应用于所有子牌组
scheduling-show-answer-timer = 显示屏幕计时器
scheduling-show-new-cards-after-reviews = 先复习，后学新
scheduling-show-new-cards-before-reviews = 先学新，后复习
scheduling-show-new-cards-in-order-added = 按添加顺序学习新卡片
scheduling-show-new-cards-in-random-order = 按随机顺序学习新卡片
scheduling-starting-ease = 初始简易度
scheduling-steps-in-minutes = 步幅 （分钟）
scheduling-steps-must-be-numbers = 步幅必须是数字。
scheduling-tag-only = 仅加标签
scheduling-the-default-configuration-cant-be-removed = 不能删除预置牌组。
scheduling-your-changes-will-affect-multiple-decks = 此变更将会影响很多牌组。如仅想更改当前牌组，请先新增一组预设配置。
scheduling-deck-updated =
    { $count ->
       *[other] 已更新 { $count } 个牌组。
    }
scheduling-set-due-date-prompt =
    { $cards ->
       *[other] 要在多少天后显示卡片？
    }
scheduling-set-due-date-prompt-hint =
    0 = 今天
    1! = 明天 + 将间隔更改为 1 天
    3-7 = 随机选择 3-7 天
scheduling-set-due-date-done =
    { $cards ->
       *[other] 已为 { $cards } 张卡片设置到期日。
    }
scheduling-graded-cards-done = 已评分 { $cards } 张卡片
scheduling-forgot-cards =
    { $cards ->
       *[other] 已重置 { $cards } 张卡片。
    }
