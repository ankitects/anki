## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount } 秒
scheduling-answer-button-time-minutes = { $amount } 分
scheduling-answer-button-time-hours = { $amount } 小時
scheduling-answer-button-time-days = { $amount } 天
scheduling-answer-button-time-months = { $amount } 個月
scheduling-answer-button-time-years = { $amount } 年

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds = { $amount } 秒
scheduling-time-span-minutes = { $amount } 分鐘
scheduling-time-span-hours = { $amount } 小時
scheduling-time-span-days = { $amount } 天
scheduling-time-span-months = { $amount } 個月
scheduling-time-span-years = { $amount } 年

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    下一張學習中的卡片將在 { $unit ->
        [seconds]
            { $amount ->
                [one] { $amount } 秒
               *[other] { $amount } 秒
            }
        [minutes]
            { $amount ->
                [one] { $amount } 分鐘
               *[other] { $amount } 分鐘
            }
       *[hours]
            { $amount ->
                [one] { $amount } 小時
               *[other] { $amount } 小時
            }
    }後準備就緒。
scheduling-learn-remaining =
    { $remaining ->
       *[other] { $remaining } 張學習中的卡片將於今天到期。
    }
scheduling-congratulations-finished = 恭喜！你已學完此牌組當前的內容。
scheduling-today-review-limit-reached = 已經達到今天的複習上限，但還有卡片尚待複習。若要保持最佳記憶效果，請在選項中增加每日上限。
scheduling-today-new-limit-reached = 還有尚未學習的新卡片，但今日學習數量已達上限。你可以在選項中增加每日上限，但請注意，學習更多新卡片將增加近期複習量。
scheduling-buried-cards-found = 有卡片被推遲到明天顯示。如果你想要立即學習這些卡片，可以{ $unburyThem }。
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = 取消推遲
scheduling-how-to-custom-study = 要在定時排程外學習，請使用{ $customStudy }功能。
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = 自訂學習

## Scheduler upgrade

scheduling-update-soon = Anki 2.1 升級了排程器，解決了舊版本 Anki 的一些問題。建議更新排程器。
scheduling-update-done = 已成功更新排程器。
scheduling-update-button = 更新
scheduling-update-later-button = 稍後
scheduling-update-more-info-button = 進一步了解
scheduling-update-required =
    你的集合需要升級至 V2 排程器。
    請選取{ scheduling-update-more-info-button }後再繼續。

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = 重播音訊時總是包含問題面
scheduling-at-least-one-step-is-required = 至少要有一個學習階段。
scheduling-automatically-play-audio = 自動播放音訊
scheduling-bury-related-new-cards-until-the = 明天前推遲關聯的新卡片
scheduling-bury-related-reviews-until-the-next = 明天前推遲關聯的複習卡
scheduling-days = 天
scheduling-description = 描述
scheduling-easy-bonus = 「簡單」倍率
scheduling-easy-interval = 「簡單」間隔
scheduling-end = （結束）
scheduling-general = 一般
scheduling-graduating-interval = 畢業間隔
scheduling-hard-interval = 「困難」間隔
scheduling-ignore-answer-times-longer-than = 忽略回答的時間上限：超過
scheduling-interval-modifier = 間隔調節器
scheduling-lapses = 遺忘
scheduling-lapses2 = 遺忘次數
scheduling-learning = 學習中
scheduling-leech-action = 低效卡動作
scheduling-leech-threshold = 低效卡臨界值
scheduling-maximum-interval = 最長間隔
scheduling-maximum-reviewsday = 每日複習上限
scheduling-minimum-interval = 最短間隔
scheduling-mix-new-cards-and-reviews = 新卡片與複習卡混合
scheduling-new-cards = 新卡片
scheduling-new-cardsday = 每日新卡片張數
scheduling-new-interval = 新間隔
scheduling-new-options-group-name = 新選項群組名稱：
scheduling-options-group = 選項群組：
scheduling-order = 順序
scheduling-parent-limit = （母牌組上限：{ $val }）
scheduling-reset-counts = 重設重複和遺忘次數
scheduling-restore-position = 如可能，回復原始順序
scheduling-review = 複習
scheduling-reviews = 複習次數
scheduling-seconds = 秒
scheduling-set-all-decks-below-to = 要將「{ $val }」下的所有牌組都設為此選項群組嗎？
scheduling-set-for-all-subdecks = 設定所有子牌組
scheduling-show-answer-timer = 顯示計時器
scheduling-show-new-cards-after-reviews = 先顯示複習卡，後顯示新卡片
scheduling-show-new-cards-before-reviews = 先顯示新卡片，後顯示複習卡
scheduling-show-new-cards-in-order-added = 依建立順序學習新卡片
scheduling-show-new-cards-in-random-order = 依隨機順序學習新卡片
scheduling-starting-ease = 起始輕鬆度
scheduling-steps-in-minutes = 學習階段（分鐘）
scheduling-steps-must-be-numbers = 學習階段值必須為數字。
scheduling-tag-only = 僅加上標籤
scheduling-the-default-configuration-cant-be-removed = 無法刪除預設的設定。
scheduling-your-changes-will-affect-multiple-decks = 此更動將會影響多個牌組，若你只想更改當前牌組，請先新增一個選項群組。
scheduling-deck-updated =
    { $count ->
       *[other] 已更新 { $count } 個牌組。
    }
scheduling-set-due-date-prompt =
    { $cards ->
       *[other] 要在多少天后顯示卡片？
    }
scheduling-set-due-date-prompt-hint =
    0 = 今天
    1! = 明天+更改複習間隔為 1
    3-7 = 隨機選擇 3-7 天
scheduling-set-due-date-done =
    { $cards ->
       *[other] 已為 { $cards } 張卡片設定到期日。
    }
scheduling-graded-cards-done = 已對 { $cards } 張卡片評等。
scheduling-forgot-cards =
    { $cards ->
       *[other] 已重置 { $cards } 張卡片。
    }
