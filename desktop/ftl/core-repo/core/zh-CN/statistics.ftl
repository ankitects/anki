# The date a card will be ready to review
statistics-due-date = 到期
# The count of cards waiting to be reviewed
statistics-due-count = 待复习
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = 新卡片 #{ $number }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } 张卡片/分钟
statistics-average-answer-time = { $average-seconds } 秒（{ statistics-cards-per-min }）

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds = { $amount } 秒内
statistics-in-time-span-minutes = { $amount } 分内
statistics-in-time-span-hours = { $amount } 小时内
statistics-in-time-span-days = { $amount } 天内
statistics-in-time-span-months = { $amount } 个月内
statistics-in-time-span-years = { $amount } 年内
# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { statistics-in-time-span-seconds } part should be pasted in from the English
# version unmodified.
statistics-studied-today =
    今天在 { $unit ->
        [seconds] { statistics-in-time-span-seconds }
        [minutes] { statistics-in-time-span-minutes }
        [hours] { statistics-in-time-span-hours }
        [days] { statistics-in-time-span-days }
        [months] { statistics-in-time-span-months }
       *[years] { statistics-in-time-span-years }
    }学习了 { statistics-cards }（平均每张卡片 { $secs-per-card } 秒）

##

statistics-cards = { $cards } 张卡片
statistics-notes = { $notes } 条笔记
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews = { $reviews } 次复习
# This fragment of the tooltip in the FSRS simulation
# diagram (Deck options -> FSRS) shows the total number of
# cards that can be recalled or retrieved on a specific date.
statistics-memorized = 已记忆 { $memorized } 张卡片
statistics-today-title = 今天
statistics-today-again-count = 「重来」计数：
statistics-today-type-counts = 学习：{ $learnCount }；复习：{ $reviewCount }；重新学习：{ $relearnCount }；已筛选：{ $filteredCount }
statistics-today-no-cards = 今天尚未学习任何卡片。
statistics-today-no-mature-cards = 今天没有学习熟练卡片。
statistics-today-correct-mature = 熟练卡片正确率：{ $correct }/{ $total }（{ $percent }%）
statistics-counts-total-cards = 总计
statistics-counts-new-cards = 未学习
statistics-counts-young-cards = 欠熟练
statistics-counts-mature-cards = 已熟练
statistics-counts-suspended-cards = 已暂停
statistics-counts-buried-cards = 已搁置
statistics-counts-filtered-cards = 已筛选
statistics-counts-learning-cards = 学习中
statistics-counts-relearning-cards = 重学中
statistics-counts-title = 卡片数量
statistics-counts-separate-suspended-buried-cards = 分开统计暂停/搁置的卡片

## Retention represents your actual retention from past reviews, in
## comparison to the "desired retention" setting of FSRS, which forecasts
## future retention. Retention is the percentage of all reviewed cards
## that were marked as "Hard," "Good," or "Easy" within a specific time period.
##
## Most of these strings are used as column / row headings in a table.
## (Excluding -title and -subtitle)
## It is important to keep these translations short so that they do not make
## the table too large to display on a single stats card.
##
## N.B. Stats cards may be very small on mobile devices and when the Stats
##      window is certain sizes.

statistics-true-retention-title = 记忆保留率
statistics-true-retention-subtitle = 间隔大于等于 1 天的卡片的通过率
statistics-true-retention-tooltip = 若使用 FSRS，您的实际记忆保留率预计会接近目标保留率。请注意单日数据存在波动，建议查看月度数据。
statistics-true-retention-range = 范围
statistics-true-retention-pass = 通过
statistics-true-retention-fail = 失败
# This will usually be the same as statistics-counts-total-cards
statistics-true-retention-total = 总计
statistics-true-retention-count = 总数
statistics-true-retention-retention = 记忆保留率
# This will usually be the same as statistics-counts-young-cards
statistics-true-retention-young = 欠熟练
# This will usually be the same as statistics-counts-mature-cards
statistics-true-retention-mature = 已熟练
statistics-true-retention-all = 所有
statistics-true-retention-today = 今天
statistics-true-retention-yesterday = 昨天
statistics-true-retention-week = 上周
statistics-true-retention-month = 上个月
statistics-true-retention-year = 近一年
statistics-true-retention-all-time = 全部时间
# If there are no reviews within a specific time period, the retention
# percentage cannot be calculated and is displayed as "N/A."
statistics-true-retention-not-applicable = -

##

statistics-range-all-time = 全部时间
statistics-range-1-year-history = 近一年
statistics-range-all-history = 全部
statistics-range-deck = 牌组
statistics-range-collection = 集合
statistics-range-search = 搜索
statistics-card-ease-title = 卡片简易度
statistics-card-difficulty-title = 卡片难度
statistics-card-stability-title = 卡片记忆稳定期
statistics-card-stability-subtitle = 记忆可提取性下降至 90% 的时间间隔。
statistics-median-stability = 记忆稳定期中位数
statistics-card-retrievability-title = 卡片记忆可提取性
statistics-card-ease-subtitle = 卡片简易度越低，其出现频率越高。
statistics-card-difficulty-subtitle2 = 卡片难度越高，记忆稳定期提升越慢。
statistics-retrievability-subtitle = 您能够成功回忆卡片内容的概率。
# eg "3 cards with 150-170% ease"
statistics-card-ease-tooltip =
    { $cards ->
       *[other] { $cards } 张简易度为 { $percent } 的卡片
    }
statistics-card-difficulty-tooltip =
    { $cards ->
       *[other] { $cards } 张难度为 { $percent } 的卡片
    }
statistics-retrievability-tooltip =
    { $cards ->
       *[other] { $cards } 张记忆可提取性为 { $percent } 的卡片
    }
statistics-future-due-title = 预测
statistics-future-due-subtitle = 将来到期的复习的数目
statistics-added-title = 新增
statistics-added-subtitle = 新增的卡片数量。
statistics-reviews-count-subtitle = 已经回答的问题的数量。
statistics-reviews-time-subtitle = 答题用时
statistics-answer-buttons-title = 回答按钮
# eg Button: 4
statistics-answer-buttons-button-number = 按钮
# eg Times pressed: 123
statistics-answer-buttons-button-pressed = 按键次数
statistics-answer-buttons-subtitle = 按下每个按钮的次数。
statistics-reviews-title = 复习
statistics-reviews-time-checkbox = 用时
statistics-in-days-single =
    { $days ->
        [0] 今天
        [1] 明天
       *[other] { $days } 天内
    }
statistics-in-days-range = { $daysStart }－{ $daysEnd } 天内
statistics-days-ago-single =
    { $days ->
        [1] 昨天
        [2] 前天
       *[other] { $days } 天前
    }
statistics-days-ago-range = { $daysStart }－{ $daysEnd } 天前
statistics-running-total = 累计
statistics-cards-due =
    { $cards ->
       *[other] { $cards } 张卡片到期
    }
statistics-backlog-checkbox = 积压
statistics-intervals-title = 复习间隔
statistics-intervals-subtitle = 复习卡片再次出现前的间隔。
statistics-intervals-day-range =
    { $cards ->
       *[other] { $cards } 张间隔为 { $daysStart }~{ $daysEnd } 天的卡片
    }
statistics-intervals-day-single =
    { $cards ->
       *[other] { $cards } 张间隔为 { $day } 天的卡片
    }
statistics-stability-day-range =
    { $cards ->
       *[other] { $cards } 张记忆稳定期为 { $daysStart }~{ $daysEnd } 天的卡片
    }
statistics-stability-day-single =
    { $cards ->
       *[other] { $cards } 张记忆稳定期为 { $day } 天的卡片
    }
# hour range, eg "From 14:00-15:00"
statistics-hours-range = { $hourStart } 时~{ $hourEnd } 时
statistics-hours-correct = { $correct }/{ $total } 正确（{ $percent }%）
statistics-hours-correct-info = →（非「重来」）
# the emoji depicts the graph displaying this number
statistics-hours-reviews = 📊复习了 { $reviews } 次
# the emoji depicts the graph displaying this number
statistics-hours-correct-reviews = 📈正确率 { $percent }%（{ $reviews } 次）
statistics-hours-title = 逐小时分析
statistics-hours-subtitle = 当天逐小时的复习成功率。
# shown when graph is empty
statistics-no-data = 无数据
statistics-calendar-title = 日程表

## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.

statistics-elapsed-time-seconds = { $amount } 秒
statistics-elapsed-time-minutes = { $amount } 分钟
statistics-elapsed-time-hours = { $amount } 小时
statistics-elapsed-time-days = { $amount } 天
statistics-elapsed-time-months = { $amount } 个月
statistics-elapsed-time-years = { $amount } 年

##

statistics-average-for-days-studied = 平均值（只计实际学习天数）
# This term is used in a variety of contexts to refers to the total amount of
# items (e.g., cards, mature cards, etc) for a given period, rather than the
# total of all existing items.
statistics-total = 总计
statistics-days-studied = 学习天数
statistics-average-answer-time-label = 平均回答用时
statistics-average = 平均
statistics-median-interval = 间隔中位数
statistics-due-tomorrow = 明天到期
# This string, ‘Daily load,’ appears in the ‘Future due’ table and represents a
# forecasted estimate of the number of cards expected to be reviewed daily in 
# the future. Unlike the other strings in the table that display actual data 
# derived from the current scheduling (e.g., ‘Average’, ‘Due tomorrow’),
# ‘Daily load’ is a projection based on the given data.
statistics-daily-load = 每日工作量
# eg 5 of 15 (33.3%)
statistics-amount-of-total-with-percentage = { $amount }/{ $total } ({ $percent }%)
statistics-average-over-period = 平均值（包含未学习天数）
statistics-reviews-per-day =
    { $count ->
       *[other] { $count } 次复习/天
    }
statistics-minutes-per-day =
    { $count ->
       *[other] { $count } 分钟/天
    }
statistics-cards-per-day =
    { $count ->
       *[other] { $count } 张/天
    }
statistics-median-ease = 简易度中位数
statistics-median-difficulty = 难度中位数
statistics-average-retrievability = 平均记忆可提取性
statistics-estimated-total-knowledge = 预估习得总数
statistics-save-pdf = 保存为 PDF
statistics-saved = 已保存。
statistics-stats = 统计
statistics-title = 统计数据

## These strings are no longer used - you do not need to translate them if they
## are not already translated.

statistics-average-stability = 平均稳定期
statistics-average-interval = 平均间隔
statistics-average-ease = 平均简易度
statistics-average-difficulty = 平均难度
