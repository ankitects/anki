### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks = { $decks }個のデッキで使用中
deck-config-default-name = デフォルト
deck-config-title = デッキオプション

## Daily limits section

deck-config-daily-limits = 一日の上限
deck-config-new-limit-tooltip =
    一日に導入（＝学習を開始）する新規カードの最大枚数。
    
    導入する新規カードの枚数が増加するほど、それらのカードの初期の復習期日が短期間に密集しがちになり、過度な学習負荷となるおそれがあります。そのため、一日の新規カード導入枚数の上限は、一日の復習枚数の上限の１０分の１以下とすることをお勧めします。
deck-config-review-limit-tooltip = 復習する期日に達しているカードの、一日に出題する最大枚数。
deck-config-limit-deck-v3 = サブデッキを持つデッキを選択して学習する場合、例えばサブデッキＡから準備されるカードの最大枚数は、そのサブデッキＡ自体のオプションで設定されている最大枚数です。その上で、選択したデッキのオプションで設定されている最大枚数に従って（場合によってはサブデッキＡからのカードの枚数は減らされ）、選択したデッキ全体での表示枚数が決まります。
deck-config-limit-new-bound-by-reviews = 一日の復習枚数の上限は、一日の新規カード導入枚数も制限します。例えば、一日の復習枚数の上限を200に設定し、期日を迎えたカードが190枚ある場合、その日に導入される新規カードは最大で10枚となります。また例えば、期日を迎えたカードの枚数が、一日の復習枚数の上限に達している場合、その日に新規カードは導入されません。
deck-config-limit-interday-bound-by-reviews = この枚数には、文字通りの復習カードだけでなく、その時点のステップが日をまたいでいる習得中カード（または再習得中カード）も含まれます。この枚数のカードが準備されるときは、習得中または再習得中であるそれらのカードが優先的に集められ、その後に復習カードが集められます。
deck-config-tab-description =
    - `プリセット`: この上限は、このプリセットを使用するすべてのデッキで共有されます。
    - `このデッキ`: この上限は、このデッキにのみ適用されます。
    - `今日だけ`: このデッキの上限を一時的に変更します。
deck-config-new-cards-ignore-review-limit = 復習枚数の上限に関係なく新規カードを導入する
deck-config-new-cards-ignore-review-limit-tooltip =
    このオプションがオフ（デフォルト）の場合、復習枚数の上限は、新規カード導入枚数も制限します。例えば、復習期日を迎えたカードの枚数が、復習枚数の上限に達していたり、さらに多い場合、その日に新規カードは導入されません。
    
    このオプションがオンの場合、新規カード導入枚数は、復習枚数の上限によって制限されないようになります。つまり、期日を迎えたカードの枚数が、復習枚数の上限に達していたり、さらに多い場合でも、それはそれとして、新規カードが導入されます。
deck-config-apply-all-parent-limits = 上限をメインデッキから適用
deck-config-apply-all-parent-limits-tooltip =
    このオプションがオフ（デフォルト）の場合、各枚数の上限には、学習を開始する際に選択したデッキの設定がまず適用され、その中のデッキ（サブデッキ）の設定も、選択したデッキの設定の範囲内で適用されます。さらに下位のサブデッキがある場合も同様に、上位のデッキの設定の範囲内で、下位のデッキの設定が適用されます。一方、選択したデッキより上位のデッキの設定は無視されます。
    
    このオプションがオンの場合、各枚数の上限には、学習を開始する際に選択したデッキについての最上位のデッキ（メインデッキ）の設定がまず適用され、その中のデッキ（サブデッキ）の設定も、メインデッキの設定の範囲内で適用されます。さらに下位のサブデッキがある場合も同様に、上位のデッキの設定の範囲内で、下位のデッキの設定が適用されます。「個々のサブデッキを選択して学習したいが、メインデッキで設定した枚数制限を守りたい」という場合に適した設定です。
deck-config-affects-entire-collection = この設定はコレクション全体に一括で適用されます。

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = プリセット
deck-config-deck-only = このデッキ
deck-config-today-only = 今日だけ

## New Cards section

deck-config-learning-steps = 習得ステップ
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = ステップの間隔は、分（例：`1m`）や日数（例：`2d`）で指定するのが一般的ですが、時間（例：`1h`）や秒（例：`30s`）で指定することも可能です。
deck-config-learning-steps-tooltip =
    １個以上のステップ（※）を、各ステップの間に半角スペースをはさんで入力します。
    
    ※ステップ：前の学習から一定の間隔をあけた習得学習（または再習得学習）のスケジュール。例えば「`10m`」は、「前の学習から10分（minutes）経過してから表示する」という意味。
    
    新規カードがあらかじめ最初のステップにある状態から習得学習を開始します。この最初のステップの間隔は、新規カードまたは習得中カードの学習で「`もう一度`」ボタンを押した場合に使用されます。デフォルトでは1分となっています。
    
    最初のステップで「`正解`」ボタンを押すと、2番目のステップに進みます。デフォルトではこのステップの間隔は10分となっており、10分経過後にカードが再び表示されるようスケジュールが組まれます。
    
    すべてのステップで`正解`すると、そのカードは`復習カード`となり、別の日に復習のため表示されるようスケジュールが組まれます。
    
    { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip = 最後の習得ステップで「`正解`」ボタンが押された場合に、そのカードが再び表示されるまでの日数。
deck-config-easy-interval-tooltip = 「`簡単`」ボタンが押され、習得中カードから復習カードへと直ちに切り替わった場合に、そのカードが再び表示されるまでの日数。
deck-config-new-insertion-order = 配置順序
deck-config-new-insertion-order-tooltip = 新規カードを追加したときにそのカードに割り当てる位置（新規カード番号）の決め方を選択できます。新規カード番号の数字が小さい順にカードは習得学習で表示されます。このオプションを変更すると、ただちに既存の新規カードの位置が更新されます。
deck-config-new-insertion-order-sequential = 追加順
deck-config-new-insertion-order-random = ランダム
deck-config-new-insertion-order-random-with-v3 = 現在ご利用中のV3スケジューラーでは、この設定を「追加順」のままにして、代わりに、「表示順序」カテゴリーの「新規カードを集める順序」オプションで順序を設定することをお勧めします。

## Lapses section

deck-config-relearning-steps = 再習得ステップ
deck-config-relearning-steps-tooltip =
    通常、１個または複数個のステップ（※）を、各ステップの間に半角スペースをはさんで入力します。
    
    ※ステップ：前の学習から一定の間隔をあけた習得学習（または再習得学習）のスケジュール。例えば「`10m`」は、「前の学習から10分（minutes）経過してから表示する」という意味。
    
    デフォルト（「`10m`」）では、復習カードで「`もう一度`」ボタンを押すと、そのカードは再習得中カードとして10分後に再び表示されます。ステップが入力されていない場合は、そのカードは再習得ステップに入らずに復習間隔が変更されます。
    
    { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip =
    復習時に何回間違えれば（＝復習カードに何回「`もう一度`」ボタンを押せば）「leech」（苦戦、忘却多発）の状態だと判定して「leech」のタグを付けるか、を設定します。
    
    苦戦（忘却多発）の状態のカードは、あなたの時間をたくさん消費しています。内容を書き直す、削除する、印象に残る覚え方を工夫するなど、何らかの対策を行うことをおすすめします。
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `タグのみ`：そのノートに「leech」というタグを付け、注意を喚起するためにポップアップを表示します。
    
    `カードを休止`：上記の操作に加えて、カードの表示を無期限で休止します。手動で休止を解除するまではカードを学習画面に表示しません。

## Burying section

deck-config-bury-title = 兄弟カードの非表示
deck-config-bury-new-siblings = 兄弟関係の新規カードを同じ日に表示しない
deck-config-bury-review-siblings = 兄弟関係の復習カードを同じ日に表示しない
deck-config-bury-interday-learning-siblings = 兄弟関係の習得中カードで、ステップが日をまたいでいる場合は、同じ日に表示しない
deck-config-bury-new-tooltip =
    学習したカードと兄弟関係のカード（※）があり、そのカードが`新規`カードである場合、そのカードを表示する順番がきたとしても同じ日には表示を行わず、翌日から表示を再開します。
    
    ※ 兄弟関係のカード：同じノートから作られたカード。例えば、裏表反転カード。
deck-config-bury-review-tooltip =
    学習したカードと兄弟関係のカード（※）があり、そのカードが`復習`カードである場合、そのカードを表示する期日に達していても同じ日には表示を行わず、翌日から表示を再開します。
    
    ※ 兄弟関係のカード：同じノートから作られたカード。例えば、裏表反転カード。
deck-config-bury-interday-learning-tooltip =
    学習したカードと兄弟関係のカード（※）があり、そのカードが`習得中`カードである場合、そのカードの現時点のステップの間隔が日をまたいでいる場合（つまり、ステップの間隔が比較的大きいので、学習タイミングを遅らせる影響が比較的小さい場合）は、同じ日には表示を行わず、翌日から表示を再開します。
    
    ※ 兄弟関係のカード：同じノートから作られたカード。例えば、裏表反転カード。
deck-config-bury-priority-tooltip =
    Ankiは学習画面に表示するカードを準備する際、準備リストにまず「現時点のステップが日をまたいでいない（＝ステップの間隔が比較的短く、当日中に再び学習する必要性が高い）習得中/再習得中カード」を並べ、次に「現時点のステップが日をまたいでいる習得中/再習得中カード」、その次に復習カード、最後に新規カードを並べます。この並び方をもとに、どのカードを表示し、どのカードを当日は非表示にするかが次のように決まります：
    
    - すべての非表示オプションが有効になっている場合、リスト内の兄弟どうしのうち、最も先に並んでいるカードのみが表示されます。例えば、リスト内のある復習カードとある新規カードが兄弟どうしの場合、復習カードの方が先に並んでいるので、復習カードが表示され、新規カードは当日は非表示となります。
    - リスト内の兄弟どうしのうちで後ろに並んでいるカードの非表示を解除したとしても、先に並んでいる別の種類のカードが代わりに非表示になるということはありません。例えばリスト内の、ある復習カードとある新規カードが兄弟どうしの場合、たとえ新規カードの非表示を解除して学習したとしても、代わりに復習カードが非表示になってしまうことはありません。つまりこの場合は、兄弟どうしである復習カードと新規カードの両方が同じ日に表示されることになります。

## Gather order and sort order of cards

deck-config-ordering-title = 表示順序
deck-config-new-gather-priority = 新規カードを集める順序
deck-config-new-gather-priority-tooltip-2 =
    `デッキの並び順（上から）`: 選択したデッキとその中のサブデッキのうち、デッキリストで上に並んでいるデッキからカードを集めていきます。つまり、最初は選択したデッキ自体のカードを集めて、次に、一番上のサブデッキのカードを集めて、という順序です。各デッキ内のカードを集める順序は、`位置（新規カード番号）の昇順`（次項参照）となります。集めている途中で一日の上限枚数に達した場合は、そのデッキより下に並んでいるデッキのカードは集めません。この順序を選択すると、大規模なコレクションでも比較的早くカードを集めることができます。なお、優先したいサブデッキがある場合は、例えば各サブデッキの名前の先頭に数字を付けるなどして、そのサブデッキを上の方に並べ変えるとよいでしょう。
    
    `位置（新規カード番号）の昇順`: 追加時に各カードに割り当てられた位置（＝新規カード番号）の数字が小さい順にカードを集めます。通常は、各カード中で最初に追加したカード（最古のカード）から集められていきます。
    
    `位置（新規カード番号）の降順`: 追加時に各カードに割り当てられた位置（＝新規カード番号）の数字が大きい順にカードを集めます。通常は、各カード中で最後に追加したカード（最新のカード）から集められていきます。
    
    `ランダム（ノート単位）`: ランダムに選んだノートからカードを集めます。「兄弟関係のカードの非表示」オプションがオフになっている場合は、同じノートから作成されたカード（例えば、《表面→裏面》カードと《裏面→表面》カード）がすべて同じセッションで表示されます。
    
    `ランダム（カード単位）`: 完全にランダムにカードを集めます。
deck-config-new-card-sort-order = 集めた新規カードを並べる順序
deck-config-new-card-sort-order-tooltip-2 =
    `カードタイプ順`: 「カード 1」「カード 2」といったカードタイプごとにグループ分けをし、カードタイプの数字順に、グループごとにカードを表示していきます。同じカードタイプどうしのカードは、集めたときの順序で表示されます。「兄弟関係のカードの非表示」カテゴリの各オプションを無効にしている場合は、例えば、カードタイプが「カード 1」である《表面→裏面》カードをすべて表示してから、カードタイプが「カード 2」である《裏面→表面》カードを表示していきます。兄弟関係のカード（＝同じノートから作られたカード）どうしを同じセッションで表示したいが、近づけすぎないようにもしたい、という場合に適した設定です。
    
    `集めたときの順序`: カードを集めたときのままの順序で表示します。「兄弟関係のカードの非表示」カテゴリの各オプションを無効にしている場合、通常、兄弟関係のカードどうしは連続して表示されます。
    
    `カードタイプ順→ランダム`: 「カードタイプ順」と同じくカードタイプの数字順にグループ分けをしますが、各カードタイプのグループ内のカードをランダムな並びで表示します。カードを集めるときに「位置（新規カード番号）の昇順」で古いカードを優先的に集めた上で、この設定にすることによって、それらのカードをランダムな並びで表示し、兄弟関係のカードどうしを近づけすぎずに表示することができます。
    
    `ランダム（ノート単位）→カードタイプ順`: ノートをランダムに並べ、ノートがカードを複数持つ場合（つまり、兄弟関係のカードがある場合）はそれらのカードをカードタイプ順に表示します。
    
    `ランダム（カード単位）`: 集めたカードを完全にランダムな並びで表示します。
deck-config-new-review-priority = 新規カード表示のタイミング
deck-config-new-review-priority-tooltip = 新規カードを、復習カードとの関連でいつ表示するのか選択できます。
deck-config-interday-step-priority = 日をまたいだステップの習得中（再習得中）カード表示のタイミング
deck-config-interday-step-priority-tooltip =
    「現時点のステップが日をまたいでいる習得中（または再習得中）カード」をどのタイミングで表示するかを選択できます。
    
    「一日の復習枚数の上限」オプションの枚数に従って表示するカードを選び出す際には、この「現時点のステップが日をまたいでいる習得中（または再習得中）カード」が復習カードよりも常に優先されます。それに対してこのオプションでは、その後の「優先的に選び出したそれらのカードと復習カードをどのような順序で表示するか」を設定します。
deck-config-review-sort-order = 復習カードを並べる順序
deck-config-review-sort-order-tooltip =
    デフォルトでは、最も長い期間待機しているカードが最初に表示されるよう、待機日数が大きい順にカードを表示します。
    復習カードが蓄積してすべてのカードをこなすのに数日以上を要する場合や、あるいはサブデッキの順番でカード学習をしたい場合など、状況や好みに応じて別のソート方法を選択することができます。
deck-config-display-order-will-use-current-deck = 表示順序の設定は、学習を開始する時に選択したデッキのオプションでの設定が用いられます。つまり、選択したデッキのサブデッキのカードを表示する際にも、そのサブデッキのオプションではなく、選択したデッキのオプションでの設定が適用されます。

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = デッキの並び順（上から）
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = デッキの並び順（上から）→ランダム（ノート単位）
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = 位置（新規カード番号）の昇順
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = 位置（新規カード番号）の降順
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = ランダム（ノート単位）
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = ランダム（カード単位）
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = カードタイプ順→ランダム
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = ランダム（ノート単位）→カードタイプ順
# Sort the cards randomly.
deck-config-sort-order-random = ランダム（カード単位）
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = カードタイプ順
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = 集めたときの順序
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = 復習と混ぜる
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = 復習の後に表示
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = 復習の前に表示
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = 期日超過日数が多い順→同日数はランダム
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = 期日超過日数が多い順→同日数はデッキの並び順
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = デッキ並び順→デッキ内は期日超過日数多い順
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = 復習間隔が短い順
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = 復習間隔が長い順
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = 易しさが低い順
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = 易しさが高い順
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = 難度が低い順
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = 難度が高い順
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = 正答可能性が低い順
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = 正答可能性が高い順

## Timer section

deck-config-timer-title = タイマー
deck-config-maximum-answer-secs = 最長回答秒数
deck-config-maximum-answer-secs-tooltip =
    カード学習に要した時間として記録する１件の回答の最長秒数。
    
    この秒数を超えた場合（例えば、カード学習の途中でスクリーンから離れた場合など）、ここで設定した最長秒数がそのカードの回答に要した時間として記録されます。
    
    「回答タイマーを表示する」オプションがオンの場合、この秒数に達するとタイマーのカウントアップが停止します。
deck-config-show-answer-timer-tooltip = カード学習画面に、表示中のカードの回答秒数をカウントするタイマーを表示します。
deck-config-stop-timer-on-answer = 解答を表示したときに、タイマーのカウントを停止する
deck-config-stop-timer-on-answer-tooltip =
    解答側を表示した時点でタイマーのカウントアップを停止するか、それとも続行するかを設定します。
    この設定はあくまでタイマーのカウント表示についてのものであり、カードへの回答秒数についての統計には影響しません。つまり、タイマーのカウントアップを停止した場合でも、その後、回答ボタンを押すまでの秒数は引き続き計測されます。

## Auto Advance section

deck-config-seconds-to-show-question = 質問表示時間（秒）
deck-config-seconds-to-show-question-tooltip-3 =
    「カードの自動送り」機能が有効になっているとき、カードの質問側が表示されてから、自動アクションが実行されるまでの待機秒数。
    
    この自動アクションを無効にするには、値を0に設定してください。
deck-config-seconds-to-show-answer = 解答表示時間（秒）
deck-config-seconds-to-show-answer-tooltip-2 =
    「カードの自動送り」機能が有効になっているとき、カードの解答側が表示されてから、自動アクションを適用するまでの待機秒数。
    
    自動アクションを無効にするには、値を0に設定してください。
deck-config-question-action-show-answer = 解答を表示
deck-config-question-action-show-reminder = リマインダーを表示
deck-config-question-action = 質問表示時間経過後の自動アクション
deck-config-question-action-tool-tip = カードの質問側が表示されてから、質問表示時間が経過した後に実行するアクション。
deck-config-answer-action = 解答表示時間経過後の自動アクション
deck-config-answer-action-tooltip-2 = カードの解答側が表示されてから、解答表示時間が経過した後に実行するアクション。
deck-config-wait-for-audio-tooltip-2 = `質問表示時間経過後の自動アクション`を実行する前、および `解答表示時間経過後の自動アクション`を実行する前に、音声の再生が終了するのを待ちます。

## Audio section

deck-config-audio-title = 音声
deck-config-disable-autoplay = 音声を自動再生しない
deck-config-disable-autoplay-tooltip = このオプションがオンの場合、カードの質問側・解答側それぞれを表示したタイミングでの音声の自動再生を行いません。この場合でも、メニューボタンで「音声を再生」アクションを選択すれば、自由なタイミングで音声を再生することができます。
deck-config-skip-question-when-replaying = 解答側を表示後の「音声を再生」アクションでは、質問側を再生しない
deck-config-always-include-question-audio-tooltip = カードの解答側（裏面）をすでに表示した後に「音声を再生」アクションを選択した場合に、質問側（表面）の音声ファイルも再生するのかどうかを設定します。

## Advanced section

deck-config-advanced-title = 高度なオプション
deck-config-maximum-interval-tooltip =
    復習カードが再び表示されるまでの日数の最大値。
    
    設定した上限に達した場合、そのカードの「`難しい`」「`正解`」「`簡単`」のいずれのボタンでも同じ日数が表示されることがあります。
    
    ここでの設定日数を短くするほど、学習負荷がかかります。
deck-config-starting-ease-tooltip =
    習得ステップを修了した際にカードに設定される、復習間隔の広がり方を定める乗数。「易しさ」の初期値。
    
    デフォルトの値2.50の場合、習得ステップ修了後の最初の復習で「正解」と回答すると、そのカードの次の復習までの間隔は、その時の復習までの間隔の2.5倍となります。
deck-config-easy-bonus-tooltip = 復習カードに「簡単」と回答した場合に、そのカードの復習間隔に追加して適用される乗数。
deck-config-interval-modifier-tooltip = ここで設定する乗数はすべての復習に適用され、Ankiのスケジューリングの間隔をお好みに合わせて微調整することができます。この設定を変更する際にはマニュアルをご確認ください。
deck-config-hard-interval-tooltip = 復習カードに「難しい」と回答した場合に、復習間隔に適用される乗数。
deck-config-new-interval-tooltip = 復習カードに「もう一度」と回答した場合に、復習間隔に対して適用される乗数。
deck-config-minimum-interval-tooltip = 復習カードに`もう一度`と回答し、再学習ステップを完了した後にそのカードが再び表示されるまでの最短日数。
deck-config-custom-scheduling = カスタムスケジューリング
deck-config-custom-scheduling-tooltip = この設定はコレクション全体に一括で適用されます。ご自身の責任でご使用ください！

## Easy Days section.

deck-config-easy-days-title = 負担軽減日
deck-config-easy-days-monday = 月曜日
deck-config-easy-days-tuesday = 火曜日
deck-config-easy-days-wednesday = 水曜日
deck-config-easy-days-thursday = 木曜日
deck-config-easy-days-friday = 金曜日
deck-config-easy-days-saturday = 土曜日
deck-config-easy-days-sunday = 日曜日
deck-config-easy-days-normal = 標準
deck-config-easy-days-reduced = 少なめ
deck-config-easy-days-minimum = 最小限
deck-config-easy-days-no-normal-days = 少なくともどれか一つの曜日で「{ deck-config-easy-days-normal }」を選択してください。（そうしないと、相対的に他の曜日の負担を軽くすることができません。）
deck-config-easy-days-change = FSRSについてのオプション「{ deck-config-reschedule-cards-on-change }」がオンになっていない場合、各カードの、すでに予定済みの期日は変更（再スケジュール）されません。

## Adding/renaming

deck-config-add-group = プリセットを新規作成
deck-config-name-prompt = 名前
deck-config-rename-group = このプリセットの名前を変更
deck-config-clone-group = このプリセットを複製

## Removing

deck-config-remove-group = このプリセットを削除
deck-config-will-require-full-sync = この変更を行うと、次回の同期の際に一方向同期（一方のデータベースをもう片方に上書きする同期）が必要となります。他のデバイスで何らかの変更（カード学習など）を行い、このデバイスとまだ同期していない場合は、この操作を続行する前にそちらの同期を済ませてください。
deck-config-confirm-remove-name = { $name } を削除しますか？

## Other Buttons

deck-config-save-button = 保存
deck-config-save-to-all-subdecks = 保存してサブデッキにも適用
deck-config-save-and-optimize = すべてのﾌﾟﾘｾｯﾄで最適化して保存
deck-config-revert-button-tooltip = この設定をデフォルトに戻す

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Markdown記法を有効にする (Anki 2.1.41以降で有効)
deck-config-description-new-handling-hint =
    Markdown形式の文字列として扱い、HTML形式の入力は無視します。このオプションをオンにすると、「おめでとうございます」の画面でもこの説明文が表示されます。
    
    なお、古いバージョンのAnki（バージョン 2.1.40まで）では、Markdown形式の文字列はプレーンテキストとして表示されます。

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    親デッキの上限が{ $cards ->
       *[other] { $cards }枚
    }に設定されているため、そこでの上限がここにも適用されます。
deck-config-reviews-too-low =
    新規カードを一日に{ $cards ->
       *[other] { $cards }枚導入し、各カードの学習を継続していくには、
    }一日の復習枚数の上限を、{ $expected }枚か、それより多い枚数とするのが妥当です。
deck-config-learning-step-above-graduating-interval = 習得ステップ修了後の最初の復習間隔は、最後の習得ステップの間隔より長くする（少なくとも同じにする）ことをお勧めします。
deck-config-good-above-easy = 「簡単」と回答後の最初の復習間隔は、習得ステップ修了後の最初の復習間隔より長くする（少なくとも同じにする）ことをお勧めします。
deck-config-relearning-steps-above-minimum-interval = 復習再開時の復習間隔の下限は、最後の再習得ステップの間隔より長くする（少なくとも同じにする）ことをお勧めします。
deck-config-maximum-answer-secs-above-recommended = 回答時間を短く保つほうが、効率的な学習スケジュール作成に役立ちます。
deck-config-too-short-maximum-interval = 復習間隔の上限は、6か月（＝180日）以上の値にすることをおすすめします。
deck-config-ignore-before-info = FSRSパラメータの最適化のために全{ $totalCards }枚中のおよそ{ $included }枚のカードを参照します。

## Selecting a deck

deck-config-which-deck = どのデッキを選択しますか？

## Messages related to the FSRS scheduler

deck-config-updating-cards = カードを更新中: { $current_cards_count }/{ $total_cards_count }...
deck-config-invalid-parameters = 入力されているFSRSパラメータが無効です。 デフォルトのパラメータを使用するには、空欄のままにしてください。
deck-config-not-enough-history = この操作を行うために十分な数の復習履歴がありません。
deck-config-must-have-400-reviews = 復習履歴が{ $count }件しか見つかりません。この操作を実行するには400件以上の復習履歴が必要です。
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = FSRSパラメータ
deck-config-compute-optimal-weights = FSRSパラメータ値を最適化
deck-config-optimize-button = このﾌﾟﾘｾｯﾄで最適化
# Indicates that a given function or label, provided via the "text" variable, operates slowly.
deck-config-slow-suffix = { $text }（低速）
deck-config-compute-button = 推定
deck-config-ignore-before = 指定日より前の復習を無視
deck-config-time-to-optimize = しばらく最適化が行われていません。「すべてのプリセットで最適化して保存」ボタンを使用することをおすすめします。
deck-config-evaluate-button = 評価
deck-config-desired-retention = 目標正答率
deck-config-historical-retention = 履歴欠落期間の推定正答率
deck-config-smaller-is-better = 各数値が小さいほど、復習履歴とよく適合していることを意味します。
deck-config-steps-too-large-for-fsrs = FSRSオプションがオンの場合、1日以上の間隔のステップは推奨されません。
deck-config-get-params = パラメータを取得
deck-config-complete = { $num }% 完了
deck-config-iterations = 反復: { $count }...
deck-config-reschedule-cards-on-change = パラメータ変更の際に既存のスケジュールも変更
deck-config-fsrs-tooltip =
    FSRS（Free Spaced Repetition Scheduler、フリーの間隔反復スケジューラー）は、Anki の従来のスケジューラーである SM2（SuperMemo 2 ）スケジューラーの代わりに使用できるスケジューラーです。 FSRS は、あなたが学習内容を忘れてしまいそうなタイミングをより正確に予測し、従来より効率よく学習内容を覚えていられるような学習スケジュールを組み立ててくれる可能性があります。
    
    このオプションのオン・オフの設定は、すべてのデッキのプリセットで共有されます。
    
    「カスタムスケジューリング」オプションを事前に使用していた場合は、このFSRSオプションをオンにする前に、必ずカスタムスケジューリングの入力欄の内容を消去してください。
deck-config-desired-retention-tooltip =
    デフォルトの値（90%）では、あなたがカードを復習する際に答えを忘れずに覚えている確率が90%となるタイミングを予測してスケジュールを組み立てます。
    
    この値を上げると、Ankiはあなたが答えを忘れずに覚えていやすくなるよう、より頻繁に、つまり、より短めの間隔でカードを表示します。
    
    この値を下げると、より少ない頻度で、つまり、より長めの間隔でカードを表示し、結果として、あなたは答えを比較的忘れやすくなると予想されます。
    
    この値の調整は、控えめに、慎重に行ってください。値を上げすぎると、学習量が大幅に増えて重荷になってしまいますし、値を下げすぎても、答えを忘れてしまうカードが多くなって、かえって学習意欲が下がってしまうかもしれません。
deck-config-desired-retention-tooltip2 = この値の調整中に表示される学習負荷についての比較情報（例：「変更後の学習負荷は、変更前(90%)の場合のおよそ0.87倍となります」）の数値は比較的おおまかな概算によるものです。より精度の高い情報を確認したい場合は、シミュレータを使用してください。
deck-config-historical-retention-tooltip =
    復習履歴の一部が欠落している場合、FSRSはその部分を仮のデータで補足する必要があります。デフォルトでは、その期間の復習の正答率は90％だったと仮定します。もし、実際の正答率は90%とはかなり異なっていた（もっと高かった、またはもっと低かった）と思う場合は、このオプションの値を調整することで、その欠落部分をより実際に近いデータで補足することができます。
    
    復習履歴の一部が欠落する理由としては、次の２つが考えられます。
    
    (1) 「`指定日より前の復習を無視`」オプションによって復習履歴の一部を無視している
    
    (2) デバイスの空き容量を確保するために復習履歴を削除したか、別のSRSプログラムからデータをインポートしたことがある
    
    (2) は非常にまれなケースなので、(1) が該当していない場合は、おそらくこのオプションの値を調整する必要はないでしょう。
deck-config-weights-tooltip2 =
    カードの復習間隔を調整する（目標正答率以外の）18種類の要因についての変数。
    
    デフォルトでは、ユーザー全般に対して一般的におすすめできる値を使用しています。
    
    「最適化」機能を使うと、このプリセットを使用しているデッキにおけるこれまでの回答成績をもとに、あなた個人とそのデッキに最適化された値を使用することができます。
deck-config-reschedule-cards-on-change-tooltip =
    このオプションは、FSRSの使用を開始するとき、またはFSRSパラメータ値を最適化した後に、各カードの、すでに予定済みの期日を変更するかどうかを設定します。
    
    このオプションがオフ（デフォルト）の場合、各カードの、すでに予定済みの期日を変更（再スケジュール）しません。今後の復習では新しいスケジュールを使用しますが、予定済みの期日は維持されるため、学習負荷が急に変化することはありません。
    
    このオプションがオンの場合、各カードの、すでに予定済みの期日を変更（再スケジュール）します。
    
    このオプションの設定はすべてのプリセットで共有され、一括で適用された後、オフ（デフォルト）になります。
deck-config-reschedule-cards-warning =
    目標正答率によっては、各カードの復習間隔が大幅に短縮され、大量のカードが復習期日に到達済みになる（＝現在の課題が急増する）可能性があるため、初めてSM2スケジューラーからFSRSに切り替える際にこのオプションをオンにすることはおすすめしません。
    
    また、このオプションを使用するたびに、カード情報に変更履歴が追加され、コレクションのサイズが増加するため、頻繁な使用もおすすめしません。
deck-config-ignore-before-tooltip-2 =
    このオプションがオンの場合、FSRSパラメータ値を最適化する際、指定日より前に復習したことのあるカードを無視します。
    
    この設定は、インポートしたデッキに他の人の復習履歴が含まれている場合や、あなたが回答ボタンを選ぶ際の方針が以前と今とでは異なるという場合に適しています。
deck-config-compute-optimal-weights-tooltip2 =
    具体的には、「最適化」ボタンを押すと、それまでの復習履歴が分析され、パラメータの値が置き換わります。その値は、あなたが学習内容（答え）を忘れてしまう前のタイミングで復習できるよう、あなたのそれまでの回答にもとづいて調整された値です。
    
    簡単なデッキと難しいデッキとでは適切なパラメータの値が異なるため、各デッキの難度が大きく異なる場合は、それぞれのデッキに別々のプリセットを使用し、それぞれのプリセットで「最適化」を行うことをおすすめします。
    
    「最適化」を頻繁に行う必要はありません。数か月に一度で十分です。
    
    デフォルトでは、「最適化」によるパラメータの値は、そのプリセットを使用しているデッキ内のすべてのカードの復習履歴にもとづいて計算されます。計算対象となるカードの条件を変更したい場合は、その条件をFSRSパラメータ欄の一つ下の欄に入力してから「最適化」ボタンを押してください。
deck-config-please-save-your-changes-first = 変更を先に保存してください
deck-config-workload-factor-change =
    変更後の学習負荷は、変更前（{ $previousDR }%）の場合の
    およそ{ $factor }倍となります
deck-config-workload-factor-unchanged = この値が高いほど、復習間隔の伸び方がゆるやかになり、より頻繁にカードが表示されます。
deck-config-desired-retention-too-low = 目標正答率が非常に低くなっています。復習間隔が極端に長くなってしまう可能性があります。
deck-config-desired-retention-too-high = 目標正答率が非常に高くなっています。復習間隔が極端に短くなってしまう可能性があります。
deck-config-percent-of-reviews =
    { $reviews ->
       *[other] 復習履歴{ $reviews }件の{ $pct }%を読み込みました...
    }
deck-config-percent-input = { $pct }%
# This message appears during FSRS parameter optimization.
deck-config-checking-for-improvement = 最適化に関するチェックを行っています...
deck-config-optimizing-preset = { $total_count }個中{ $current_count }個目のプリセットを最適化しています...
deck-config-fsrs-must-be-enabled = 先にFSRSを有効にする必要があります。
deck-config-fsrs-params-optimal = FSRSパラメータは、今のところ、このままの値が最適であると思われます。
deck-config-fsrs-params-no-reviews = 該当する復習が見つかりません。今回FSRSパラメータを最適化したいデッキ（サブデッキを含む）すべてがこのプリセットを使用していることを確認してから、操作をやり直してください。
deck-config-wait-for-audio = 音声再生終了を待つ
deck-config-show-reminder = リマインダーを表示
deck-config-answer-again = 回答 (もう一度)
deck-config-answer-hard = 回答 (難しい)
deck-config-answer-good = 回答 (正解)
deck-config-days-to-simulate = 学習予定期間（日）
deck-config-desired-retention-below-optimal = 現在、この値よりも低い値が「目標正答率」として設定されています。「目標正答率」をこの値以上に変更することをおすすめします。
# Description of the y axis in the FSRS simulation
# diagram (Deck options -> FSRS) showing the total number of
# cards that can be recalled or retrieved on a specific date.
deck-config-fsrs-simulator-experimental = FSRSシミュレータ（実験的機能）
deck-config-fsrs-simulate-desired-retention-experimental = 目標正答率ｺｽﾊﾟ比較ｼﾐｭﾚｰﾀ（実験的機能）
deck-config-fsrs-simulate-save-preset = 「最適化」オプション（または手入力）によってFSRSパラメータが変更されています。このオプションを使用する前に、このプリセットを保存してください。
deck-config-fsrs-desired-retention-help-me-decide-experimental = 正答率ｺｽﾊﾟ比較ｼﾐｭﾚｰﾀ（実験的機能）
deck-config-additional-new-cards-to-simulate = 新規カード追加枚数
deck-config-simulate = ｼﾐｭﾚｰﾄ
deck-config-clear-last-simulate = 直近のｼﾐｭﾚｰﾄ結果を消去
deck-config-fsrs-simulator-radio-count = 学習回数
deck-config-advanced-settings = 高度な設定
deck-config-smooth-graph = グラフをスムーズにする
deck-config-suspend-leeches = 「leech」（苦戦、習得困難）タグの付いたカードを休止する
deck-config-save-options-to-preset = 変更をﾌﾟﾘｾｯﾄに反映
deck-config-save-options-to-preset-confirm = 現在このシミュレーターの各オプションで指定している内容と同じになるよう、元のプリセットの各オプションの既存の設定内容を変更します。よろしいですか？
# Radio button in the FSRS simulation diagram (Deck options -> FSRS) selecting
# to show the total number of cards that can be recalled or retrieved on a
# specific date.
deck-config-fsrs-simulator-radio-memorized = 記憶維持
deck-config-fsrs-simulator-radio-ratio = 1枚あたりの記憶維持コスト
# $time here is pre-formatted e.g. "10 Seconds" 
deck-config-fsrs-simulator-ratio-tooltip = 1枚あたり { $time } のコスト（学習時間）で、指定期間の間、記憶を維持

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.

# Checkbox
deck-config-health-check = 最適化を実行する際にFSRSパラメータの信頼性もチェック
# Message box showing the result of the health check
deck-config-fsrs-bad-fit-warning =
    FSRSは今のところ、あなた個人の記憶の今後の推移について、信頼性の高い予測を行うことが困難です。これは、あなた個人の記憶のこれまでの推移について、信頼性の高いデータが蓄積されていると判断できないためです。
    
    【推奨事項】
    ・カードの復習を始める前に、そのカードの内容を理解（習得）する。（カードの内容を理解（習得）していないうちは、そのカードの復習を始めない。）
    ・回答ボタンを選択する際は、一貫した基準によって選択する。
    ・回答「難しい」は、「正しい答えを思い出せたが、思い出すのが難しかった」場合に選択する。正しい答えを思い出せなかった場合には「難しい」ではなく「もう一度」を選択する。
    ・「leech」（苦戦、習得困難）タグの付いたカードは、より記憶しやすい内容に改善するか、休止する
    
    これらの推奨事項を行うと、通常は数か月後には、より信頼性の高いFSRSパラメータを利用できるようになります。
# Message box showing the result of the health check
deck-config-fsrs-good-fit = 現在のFSRSパラメータは、あなた個人の記憶の推移に合わせて適切に調整されていると思われます。これは、あなた個人の記憶のこれまでの推移について、信頼性の高いデータが蓄積されていると判断されるためです。

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-unable-to-determine-desired-retention = 有益な目標正答率の下限を推定できませんでした
deck-config-predicted-minimum-recommended-retention = 有益な目標正答率の下限: { $num }
deck-config-compute-minimum-recommended-retention = 有益な目標正答率の下限
deck-config-compute-optimal-retention-tooltip4 =
    このツールは、最も学習効率の高い（＝最高水準の学習成果を、できるだけ少ない学習時間・学習回数で達成する）`目標正答率`を推定します。
    
    この値は、あなたが`目標正答率`の値を調整する際の参考値とすることができます。
    
    「学習時間や学習回数が増えてもかまわないから、実際の正答率をさらに高くしたい」という場合は、この値よりも高い値を`目標正答率`として設定するのもよいでしょう。
    
    この値よりも低い値を`目標正答率`として設定するのはおすすめしません。復習間隔が大きくなりすぎて正答を思い出しにくくなり、かえって学習の負担が高くなると予想されるためです。
deck-config-plotted-on-x-axis = NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.
deck-config-a-100-day-interval =
    { $days ->
        [one] 【参考】 たとえば、デフォルトの目標正答率だと復習間隔が100日になる場合、この目標正答率だと、その復習間隔は{ $days }日になります。
       *[other] 【参考】 たとえば、デフォルトの目標正答率だと復習間隔が100日になる場合、この目標正答率だと、その復習間隔は{ $days }日になります。
    }
deck-config-fsrs-simulator-y-axis-title-time = 各日の学習時間
deck-config-fsrs-simulator-y-axis-title-count = 各日の学習回数
deck-config-fsrs-simulator-y-axis-title-memorized = 記憶を維持できているカード数
deck-config-bury-siblings = Bury siblings
deck-config-do-not-bury = Do not bury siblings
deck-config-bury-if-new = Bury if new
deck-config-bury-if-new-or-review = Bury if new or review
deck-config-bury-if-new-review-or-interday = Bury if new, review, or interday learning
deck-config-bury-tooltip =
    Siblings are other cards from the same note (eg forward/reverse cards, or
    other cloze deletions from the same text).
    
    When this option is off, multiple cards from the same note may be seen on the same
    day. When enabled, Anki will automatically *bury* siblings, hiding them until the next
    day. This option allows you to choose which kinds of cards may be buried when you answer
    one of their siblings.
    
    When using the V3 scheduler, interday learning cards can also be buried. Interday
    learning cards are cards with a current learning step of one or more days.
deck-config-seconds-to-show-question-tooltip =
    「カードの自動送り」機能が有効になっているとき、カードの解答側が表示されてから、自動アクションが実行されるまでの待機秒数。
    
    この自動アクションを無効にするには、値を0に設定してください。
deck-config-answer-action-tooltip = ユーザーが回答などの操作を手動で行わず、自動的に次のカードに進む前に、現在のカードに対して実行するアクション。
deck-config-wait-for-audio-tooltip = 解答を自動的に表示する前、または自動回答アクションを行う前に、音声の再生が終了するのを待ちます。
deck-config-ignore-before-tooltip =
    このオプションがオンの場合、FSRSパラメータ値を最適化・推測する際、指定日より前の復習履歴を無視します。
    
    この設定は、インポートしたデッキに他の人の復習履歴が含まれている場合や、あなたが回答ボタンを選ぶ際の方針が以前と今とでは異なるという場合に適しています。
deck-config-compute-optimal-retention-tooltip =
    このツールは、あなたがまだどのカードも学習していない状態で学習を開始すると仮定して、指定された学習プラン（枚数、日数、１日あたりの学習時間）に基づく正答率を推定します。
    
    推定される正答率は各項目に入力する値によって大きく変化します。推定値と0.9との差が著しく大きい場合は、予定の学習枚数に対して、予定の学習時間が少なすぎるか、または多すぎる可能性があります。
    
    この値は、あくまで学習プランの調整などのための参考値であり、`目標正答率`の欄でそのまま使用するための推奨値ではありません。
deck-config-health-check-tooltip1 = NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.
deck-config-health-check-tooltip2 = NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.
deck-config-compute-optimal-retention = 有益な目標正答率の下限を推定
deck-config-predicted-optimal-retention = 有益な目標正答率の下限: { $num }
deck-config-weights-tooltip =
    カードの復習スケジュールを調整する因子となる複数の値。
    
    FSRSを開始する際、パラメータには、デフォルトの値があらかじめ適用されています。
deck-config-compute-optimal-weights-tooltip =
    このパラメータの値は、そのプリセットを使用しているデッキでの復習履歴が1,000件以上に達してから「最適化」ボタンを押すと、別の値に置き換わります。その値は、あなたが学習内容（答え）を忘れてしまう前のタイミングで復習できるよう、あなたのそれまでの回答にもとづいて自動的に調整された値です。
    
    簡単なデッキと難しいデッキとでは、適したパラメータの値が異なるため、各デッキの難度が大きく異なる場合は、それぞれのデッキに別々のプリセットを使用し、それぞれで「最適化」を行うことをおすすめします。
    
    「最適化」をこまめに行う必要はありません。数か月に一度で十分です。
    
    デフォルトでは、「最適化」によるパラメータの値は、現在のプリセットを使用しているすべてのデッキのカードの復習履歴から計算されます。計算に使用するカードを抽出する条件を変更したい場合は、「最適化」ボタンを押す前に、パラメータ欄の下にある欄にその条件を入力してください。
deck-config-compute-optimal-retention-tooltip2 =
    このツールは、あなたがまだどのカードも学習していない状態で学習を開始すると想定して、最少の学習時間（学習回数）で最大の学習成果につながる`目標正答率`を推定します。
    
    この値は、あなたが`目標正答率`の値を調整する際の参考値となります。
    
    「学習時間（学習回数）が増えてもかまわないから、実際の正答率をもっと高くしたい」という場合は、この値よりも高い値を`目標正答率`として設定するのもよいでしょう。
    
    この値よりも低い値を`目標正答率`として設定するのはおすすめしません。成果があまり見込めない学習を繰り返すという結果になると予想されるためです。
deck-config-compute-optimal-retention-tooltip3 =
    このツールは、あなたがまだどのカードも学習していない状態で学習を開始すると想定して、最少の学習時間（学習回数）で最大の学習成果につながる`目標正答率`を推定します。あなたの学習プロセスを高い精度でシミュレートするために、この機能を使用するには400件以上の復習履歴が必要です。
    
    この値は、あなたが`目標正答率`の値を調整する際の参考値となります。
    
    「学習時間（学習回数）が増えてもかまわないから、実際の正答率をもっと高くしたい」という場合は、この値よりも高い値を`目標正答率`として設定するのもよいでしょう。
    
    この値よりも低い値を`目標正答率`として設定するのはおすすめしません。成果があまり見込めない学習を繰り返すという結果になると予想されるためです。
deck-config-seconds-to-show-question-tooltip-2 =
    「カードの自動送り」機能が有効になっているとき、カードの質問側が表示されてから、解答側を表示するまでの待機秒数。
    
    この自動表示を無効にするには、値を0に設定してください。
deck-config-invalid-weights = パラメータの欄には、デフォルト値を使用するために何も入力しないままにするか、コンマ (", ") で区切られた17個の数字を入力する必要があります。
deck-config-fsrs-on-all-clients = コレクションを他の端末のAnkiと同期している場合は、 それらのAnkiのバージョンがいずれも Anki(Mobile) 23.10 以降または AnkiDroid 2.17 以降であることを確認してください。 FSRSは、それらのいずれかが古いバージョンである場合は正しく動作しません。
deck-config-optimize-all-tip = （最適化を、このプリセットだけでなく、すべてのプリセットに対して一度に行いたい場合は、画面上部の保存ボタン右側の「∨」ボタン→「すべてのプリセットで最適化して保存」によって実行できます。）
