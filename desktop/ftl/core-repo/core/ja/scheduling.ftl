## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount }秒
scheduling-answer-button-time-minutes = { $amount }分
scheduling-answer-button-time-hours = { $amount }時間
scheduling-answer-button-time-days = { $amount }日
scheduling-answer-button-time-months = { $amount }か月
scheduling-answer-button-time-years = { $amount }年

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds = { $amount }秒
scheduling-time-span-minutes = { $amount }分
scheduling-time-span-hours = { $amount }時間
scheduling-time-span-days = { $amount }日
scheduling-time-span-months = { $amount }か月
scheduling-time-span-years = { $amount }年

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    { $unit ->
        [seconds] { $amount }秒後に再び課題となる習得中カードがあります。
        [minutes] { $amount }分後に再び課題となる習得中カードがあります。
       *[hours] { $amount }時間後に再び課題となる習得中カードがあります。
    }
scheduling-learn-remaining =
    { $remaining ->
       *[other] 今日中に再び課題となる習得中カードは、今のところ全部で{ $remaining }枚です。
    }
scheduling-congratulations-finished = おめでとうございます！ このデッキの現在の課題をすべて達成しました！
scheduling-today-review-limit-reached =
    期日に達しているカードがまだ残っていますが、今日の復習枚数が一日の上限に達しています。
    適切なタイミングでの復習によって記憶を維持するため、デッキのオプションで「一日の復習枚数の上限」を上げることを検討してください。
scheduling-today-new-limit-reached = 未導入の新規カードがまだ残っていますが、今日の新規カード導入枚数がすでに一日の上限に達しています。デッキのオプションで「一日の新規カード導入枚数の上限」を上げることも可能ですが、一日の導入枚数を増やすほど、その後、短めの間隔の課題が急増しやすくなることに注意してください。
scheduling-buried-cards-found = 手動操作またはオプションによる自動操作によって、今日は非表示となっているカードがあります。それらのカードをすぐに学習したい場合は、{ $unburyThem }してください。
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = 非表示を解除
scheduling-how-to-custom-study = 通常のスケジュール外で学習したい場合、{ $customStudy }機能を活用することもできます。
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = カスタム学習

## Scheduler upgrade

scheduling-update-soon = Anki 2.1 では新しいスケジューラーが使用されています。古いバージョンで発生した問題の多くが解決されているため、アップデートすることをおすすめします。
scheduling-update-done = スケジューラーをアップデートしました。
scheduling-update-button = アップデート
scheduling-update-later-button = 後で
scheduling-update-more-info-button = 詳細
scheduling-update-required =
    コレクションのスケジューラーを、V2スケジューラーにアップデートする必要があります。
    続行する前に「{ scheduling-update-more-info-button }」を選択してください。

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = 音声を再生する際、質問側の音声も必ず含める
scheduling-at-least-one-step-is-required = 最低でも一つのステップが必要です。
scheduling-automatically-play-audio = 音声を自動再生する
scheduling-bury-related-new-cards-until-the = 関連する新規カードを今日は非表示にする
scheduling-bury-related-reviews-until-the-next = 関連する復習カードを今日は非表示にする
scheduling-days = 日
scheduling-description = デッキ説明文
scheduling-easy-bonus = 復習で「簡単」と回答した場合のボーナス〔「正解」比〕（倍）
scheduling-easy-interval = 「簡単」と回答後の最初の復習間隔（日）
scheduling-end = (終了)
scheduling-general = 一般
scheduling-graduating-interval = 習得ステップ修了後の最初の復習間隔（日）
scheduling-hard-interval = 復習で「難しい」と回答した場合の間隔〔前回比〕（倍）
scheduling-ignore-answer-times-longer-than = 統計に利用する解答時間の最大値
scheduling-interval-modifier = 復習間隔の一括調整（倍）
scheduling-lapses = 復習での忘却
scheduling-lapses2 = 回
scheduling-learning = 習得中または再習得中
scheduling-leech-action = 苦戦カードへの処置
scheduling-leech-threshold = 苦戦カードと判定する、復習での忘却回数
scheduling-maximum-interval = 復習間隔の上限（日）
scheduling-maximum-reviewsday = 一日の復習枚数の上限
scheduling-minimum-interval = 復習再開時の復習間隔の下限（日）
scheduling-mix-new-cards-and-reviews = 新規カードと学習カードを混ぜる
scheduling-new-cards = 新規カード
scheduling-new-cardsday = 一日の新規カード導入枚数の上限
scheduling-new-interval = 復習再開後の復習間隔〔前回復習比〕（倍）
scheduling-new-options-group-name = 新しいオプショングループ名:
scheduling-options-group = オプショングループ
scheduling-order = 順序
scheduling-parent-limit = (元の最大出題数は { $val })
scheduling-reset-counts = 学習回数と復習での忘却回数をリセット
scheduling-restore-position = 可能な限り元の位置に復元
scheduling-review = 復習
scheduling-reviews = 学習回数
scheduling-seconds = 秒
scheduling-set-all-decks-below-to = このオプショングループをこのデッキ「{ $val }」内のすべてのサブデッキにも適用しますか？
scheduling-set-for-all-subdecks = すべてのサブデッキに適用する
scheduling-show-answer-timer = 回答タイマーを表示する
scheduling-show-new-cards-after-reviews = 復習カードの後に新規カードを学習する
scheduling-show-new-cards-before-reviews = 復習カードの前に新規カードを学習する
scheduling-show-new-cards-in-order-added = 新規カードを追加順に表示する
scheduling-show-new-cards-in-random-order = 新規カードをランダムに選んで表示する
scheduling-starting-ease = 復習開始時の「易しさ」〔復習間隔の前回比〕（倍）
scheduling-steps-in-minutes = 習得ステップ（分）
scheduling-steps-must-be-numbers = 習得ステップは数字で指定してください。
scheduling-tag-only = タグを付けるだけ
scheduling-the-default-configuration-cant-be-removed = これはデフォルトのプリセットなので削除できません。
scheduling-your-changes-will-affect-multiple-decks = この変更は複数のデッキに影響が及びます。現在のデッキのみに変更を加えたい時には、まず最初にオプショングループを新規追加してください。
scheduling-deck-updated =
    { $count ->
       *[other] { $count } 個のデッキを更新しました。
    }
scheduling-set-due-date-prompt =
    { $cards ->
       *[other]
            何日後にカードを表示しますか？
            　
    }
scheduling-set-due-date-prompt-hint =
    入力例）
    　0    = 今日
    　1!   = 明日。かつ、復習間隔を1日に変更
    　3-7 = 3日後～7日後からランダムに選択
scheduling-set-due-date-done =
    { $cards ->
       *[other] { $cards }枚のカードの期日を設定しました。
    }
scheduling-graded-cards-done = { $cards }枚のカードの評価を回答しました。
scheduling-forgot-cards =
    { $cards ->
       *[other] { $cards }枚のカードを新規カードに戻しました。
    }
