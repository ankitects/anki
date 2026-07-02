importing-failed-debug-info = インポートに失敗しました。デバッグ情報:
importing-aborted = 中断: { $val }
importing-added-duplicate-with-first-field = 最初のフィールドが重複したノートを追加しました: { $val }
importing-all-supported-formats = サポートされているファイル形式 { $val }
importing-allow-html-in-fields = フィールド内でHTMLを使う
importing-anki-files-are-from-a-very = .ankiファイルはAnkiのとても古いバージョンのものです。それらのファイルは、Ankiのウェブサイトより入手可能なAnki 2.0を使用してインポートすることができます。
importing-anki2-files-are-not-directly-importable = .anki2ファイルを直接インポートすることはできません。代わりに受信した.apkgまたは.zipファイルをインポートしてください。
importing-appeared-twice-in-file = { $val } は二回ファイルに出てきました
importing-by-default-anki-will-detect-the =
    既定では、Ankiはフィールドを区切るタブやカンマなどの文字を識別します。
    もしフィールドを区切る文字をAnkiがうまく識別できない場合は、ここにその区切り文字を入力してください。
    タブ区切りを使用する場合は \t と入力してください。
importing-cannot-merge-notetypes-of-different-kinds =
    穴埋め問題のノートタイプは他のノートタイプに統合できません。
    '{ importing-merge-notetypes }'を無効にすることでファイルをインポートすることはできます。
importing-change = 変更
importing-colon = コロン ( : )
importing-comma = コンマ ( , )
importing-empty-first-field = 最初のフィールドが空白：{ $val }
importing-field-separator = フィールドの区切り
importing-field-separator-guessed = フィールドの区切り
importing-field-mapping = フィールドの割り当て
importing-field-of-file-is = ファイルの<b>{ $val }</b>番目のフィールドは：
importing-fields-separated-by = フィールドの区切り: { $val }
importing-file-must-contain-field-column = ファイルには、ノートのフィールドに割り当てることができる列が少なくとも1つ含まれている必要があります。
importing-file-version-unknown-trying-import-anyway = 不明のファイルバージョンですが、インポートを試みています。
importing-first-field-matched = 最初のフィールドが一致しました: { $val }
importing-identical = 同一
importing-ignore-field = フィールドを無視する
importing-ignore-lines-where-first-field-matches = 最初のフィールドが既存のノートと一致する行は無視する
importing-ignored = <無視する>
importing-import-even-if-existing-note-has = 最初のフィールドが既存のノートと同じであっても読み込む
importing-import-options = インポートのオプション
importing-importing-complete = インポートが完了しました。
importing-invalid-file-please-restore-from-backup = ファイルが壊れています。バックアップから復元してください。
importing-map-to = { $val } に割り当てる
importing-map-to-tags = タグに割り当てる
importing-mapped-to = <b>{ $val }</b> に割り当てる
importing-mapped-to-tags = <b>タグ</b> に割り当てる
# the action of combining two existing note types to create a new one
importing-merge-notetypes = ノートタイプを統合
importing-merge-notetypes-help =
    このオプションがオンの場合、インポート先またはインポート元のノートタイプのスキーマが変更（※）されていれば、Ankiは変更前と変更後のノートタイプをそれぞれ保持するのではなく、一つのノートタイプに統合します。
    
    ※ ノートタイプのスキーマの変更とは、フィールドまたはカードタイプの追加・削除・並べ替え、またはソートフィールドの変更のことです。（これに該当しない変更の例：既存のカードタイプの表面のテンプレートの内容の変更）
    
    注意：ノートタイプの統合を行った場合、次回の同期で一方向同期（一方のデータベースをもう片方に上書きする同期）が必要となります。また、既存のノートが変更済みとしてマークされる可能性があります。
importing-mnemosyne-20-deck-db = Mnemosyne 2.0 デッキ (*.db)
importing-multicharacter-separators-are-not-supported-please = 複数文字のデリミタを使用することができません。１文字のみ入力してください。
importing-new-deck-will-be-created = 新しいデッキを作成します：{ $name }
importing-notes-added-from-file = ファイルから追加したノート: { $val }枚
importing-notes-found-in-file = ファイル内にあるノート: { $val }枚
importing-notes-skipped-as-theyre-already-in = すでにコレクション内に最新版が存在するためスキップされたノート: { $val }枚
importing-notes-skipped-update-due-to-notetype = ノートでの更新を行いませんでした（既存のノートのノートタイプが変更されているため）: { $val }枚
importing-notes-updated-as-file-had-newer = 既存のノートを更新しました（より新しい版がファイル内にあるため）: { $val }個
importing-include-reviews = 学習履歴を含める
importing-also-import-progress = 学習履歴もインポート
importing-with-deck-configs = デッキのプリセットもインポート
importing-updates = 更新
importing-include-reviews-help = オンにすると、このデッキに（デッキ作成者の）以前の学習履歴が含まれている場合、その履歴もインポートします。オフにすると、すべてのカードを新規カードとしてインポートします。
importing-with-deck-configs-help =
    オンにすると、デッキ作成者がデッキに設定した各オプションもすべてインポートします。
    オフにすると、インポートするすべてのデッキにデフォルトのプリセットを適用します。
importing-packaged-anki-deckcollection-apkg-colpkg-zip = パッケージ化されたAnkiデッキ／コレクション (*.apkg *.colpkg *.zip)
# the '|' character
importing-pipe = パイプ ( | )
# Warning displayed when the csv import preview table is clipped (some columns were hidden)
# $count is intended to be a large number (1000 and above)
importing-preview-truncated = 最初の{ $count }列のみを表示しています。 これが正しくないと思われる場合は、フィールドの区切り文字を変更してみてください。
importing-rows-had-num1d-fields-expected-num2d = 「{ $row }」には { $found } 個のフィールドがありました。予想では { $expected } 個でした。
importing-selected-file-was-not-in-utf8 = 選択したファイルは UTF-8 形式ではありません。マニュアルのインポート (Importing) に関する項目をご覧ください。
importing-semicolon = セミコロン ( ; )
importing-skipped = スキップ
importing-tab = タブ
importing-tag-modified-notes = 更新されたノートに付けるタグ：
importing-text-separated-by-tabs-or-semicolons = テキスト(タブ区切りまたはセミコロン区切り) (*)
importing-the-first-field-of-the-note = ノートタイプの最初のフィールドは割り当てなくてはなりません。
importing-the-provided-file-is-not-a = 指定したファイルは正当な .apkg ファイルではありません。
importing-this-file-does-not-appear-to = このファイルは正当な .apkgファイルではないようです。このエラーが AnkiWeb からダウンロードしたファイルで発生した場合、ダウンロードが失敗した可能性があります。再度ダウンロードしても この問題が続くようであれば、別のウェブブラウザからもう一度試してみてください。
importing-this-will-delete-your-existing-collection = この処理は、既存のコレクションを削除し、今からインポートするファイルのデータに置き換えます。本当に実行しますか？
importing-unable-to-import-from-a-readonly = 読み取り専用ファイルはインポートできません。
importing-unknown-file-format = ファイルの種類が不明。
importing-update-existing-notes-when-first-field = 最初のフィールドが一致した場合、既存のノートを更新する
importing-updated = 更新
importing-update-if-newer = 既存のものより新しい場合は行う
importing-update-always = 常に行う
importing-update-never = 常に行わない
importing-update-notes = ノートを更新
importing-update-notes-help = どういう場合にコレクション内の既存のノートを、それとマッチしたノートで更新するか、を設定します。デフォルト（`既存のものより新しい場合は行う`）では、マッチしたノートの方を最近変更している場合にのみ、既存のノートを更新します。
importing-update-notetypes = ノートタイプを更新
importing-update-notetypes-help =
    マッチしているコレクション内の既存のノートタイプをどういう場合に更新するか、を設定します。デフォルト（`既存のものより新しい場合は行う`）では、インポートするノートタイプを最近編集している場合にのみ、既存のノートタイプを更新します。
    
    カードタイプのテンプレート内のテキストまたはCSSスタイルの変更は常にインポートされますが、ノートタイプのスキーマ（例. フィールドの数や順序）の変更には、`{ importing-merge-notetypes }` オプションもオンにする必要があります。
importing-note-added =
    { $count ->
       *[other] { $count }個のノートを追加しました。
    }
importing-note-imported =
    { $count ->
       *[other] { $count }個のノートをインポートしました。
    }
importing-note-unchanged =
    { $count ->
       *[other] { $count }個のノートを変更しませんでした
    }
importing-note-updated =
    { $count ->
       *[other] { $count }個のノートを更新しました。
    }
importing-processed-media-file =
    { $count ->
       *[other] { $count }個のメディアファイルをインポートしました
    }
importing-importing-file = ファイルをインポート中...
importing-extracting = データを抽出中...
importing-gathering = データを収集中...
importing-failed-to-import-media-file = メディアファイルのインポートに失敗しました: { $debugInfo }
importing-processed-notes =
    { $count ->
       *[other] { $count }個のノートを追加しています...
    }
importing-processed-cards =
    { $count ->
       *[other] { $count }枚のカードの処理が完了しました...
    }
importing-existing-notes = 既存のノート
# "Existing notes: Duplicate" (verb)
importing-duplicate = 重複を許す
# "Existing notes: Preserve" (verb)
importing-preserve = 維持
# "Existing notes: Update" (verb)
importing-update = 更新
importing-tag-all-notes = すべてのノートにタグ
importing-tag-updated-notes = 更新したノートにタグ
importing-file = ファイル
# "Match scope: notetype / notetype and deck". Controls how duplicates are matched.
importing-match-scope = 重複チェックの範囲
# Used with the 'match scope' option
importing-notetype-and-deck = ノートタイプとデッキ
importing-cards-added =
    { $count ->
        [one] { $count }枚のカードを追加しました。
       *[other] { $count }枚のカードを追加しました。
    }
importing-file-empty = 選択したファイルが空です。
importing-notes-added =
    { $count ->
       *[other] { $count }個のノートを新規ノートとしてインポートしました。
    }
importing-notes-updated =
    { $count ->
       *[other] { $count }個のノートは、既存のノートを更新するために使用しました。
    }
importing-existing-notes-skipped =
    { $count ->
       *[other] { $count }個のノートは同じノートがすでにコレクション内に存在します。
    }
importing-notes-failed =
    { $count ->
        [one] { $count }個のノートはインポートできませんでした。
       *[other] { $count }個のノートはインポートできませんでした。
    }
importing-conflicting-notes-skipped =
    { $count ->
       *[other] { $count }個のノートはインポートしませんでした。（ノートタイプが変更されたため）
    }
importing-conflicting-notes-skipped2 =
    { $count ->
       *[other] { $count }個のノートはインポートしませんでした。（ノートタイプが変更されており、かつ、「{ importing-merge-notetypes }」のオプションがオフになっているため）
    }
importing-import-log = インポート ログ
importing-no-notes-in-file = ノートがファイル内で見つかりませんでした。
importing-notes-found-in-file2 =
    { $notes ->
       *[other] { $notes }個のノートがファイル内で見つかりました。処理の内訳は下記の通りです。
    }
importing-show = 表示
importing-details = 詳細
importing-status = 状態
importing-duplicate-note-added = 重複しているノートを追加しました
importing-added-new-note = 新規ノートとして追加しました
importing-existing-note-skipped = このノートのインポートをスキップしました（このノートの最新版がコレクション内にすでに存在するため）
importing-note-skipped-update-due-to-notetype = このノートでの更新を行いませんでした（既存のノートのノートタイプが変更されているため）
importing-note-skipped-update-due-to-notetype2 = このノートでの更新を行いませんでした（ノートタイプが変更されており、かつ、「{ importing-merge-notetypes }」がオフになっているため）
importing-note-updated-as-file-had-newer = 既存のノートを更新しました（より新しい版がファイル内にあるため）
importing-note-skipped-due-to-missing-notetype = このノートのインポートをスキップしました（ノートタイプが不明なため）
importing-note-skipped-due-to-missing-deck = このノートのインポートをスキップしました（デッキが不明なため）
importing-note-skipped-due-to-empty-first-field = このノートのインポートをスキップしました（最初のフィールドが空のため）
importing-field-separator-help =
    テキストファイル内で各フィールドを区切っている文字。フィールドが正しく区切られているかどうかは、このオプションの下方に表示されるプレビューで確認できます。
    
    この文字自体をフィールド内に表示したい場合、そのフィールドをCSVの一般的な書式に従って引用符で囲む必要があることに注意してください。LibreOfficeのような表計算ソフトは自動的にこれを行います。
    
    テキストファイルのファイルヘッダーによって特定の区切り文字の使用が強制されている場合は、変更できません。
    一方、テキストファイルにファイルヘッダーがない場合は、Ankiはそのテキストの内容から区切り文字を推測し、選択します。プレビューを確認して、その選択が誤っていると思われる場合は、適切な別の区切り文字を選択してください。
importing-allow-html-in-fields-help =
    ファイルにHTMLの書式が含まれている場合は、このオプションをオンにしてください。
    
    例えば、ファイルに '&lt;br&gt;' という文字列が含まれている場合、このオプションをオンにすると、カード上ではその箇所を改行して表示します。オフにすると、その文字列 '&lt;br&gt;' をそのまま表示します。
importing-notetype-help =
    インポートして新たに追加するノートのノートタイプを設定します。また、既存のノートで更新の対象となるのは、このノートタイプのノートに限られます。
    
    ファイル内の各フィールドがノートタイプのどのフィールドに対応するかは、次のカテゴリ「フィールドの割り当て」で選択できます。
importing-deck-help = インポートして新たに追加するノートの追加先となるデッキを設定します。
importing-existing-notes-help =
    インポートしたノートが既存のノートと一致した場合の処置を設定します。
    
    - `{ importing-update }`: 既存のノートを更新します。
    - `{ importing-preserve }`: 何も行いません。既存のノートが維持されます。
    - `{ importing-duplicate }`: 既存のノートを維持し、インポートしたノートを新規ノートとして追加します。
importing-match-scope-help = デフォルトでは、既存のノートの重複チェックは、同じノートタイプを持つノートを対象としています。このオプションにより、同じデッキのカードを持つノートという制限を追加することができます。
importing-tag-all-notes-help = 指定したタグを、新たにインポートしたノートと更新したノートの両方に付けます。
importing-tag-updated-notes-help = 指定したタグを、更新したノートに付けます。
importing-overview = 概要

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

importing-importing-collection = コレクションをインポート中...
importing-unable-to-import-filename = { $filename }をインポートできません：このファイルのタイプはサポートされていません。
importing-notes-that-could-not-be-imported = ノートタイプが変更されたためインポートできなかったノート：{ $val }
importing-added = 追加済
importing-pauker-18-lesson-paugz = Pauker 1.8 レッスン (*.pau.gz)
importing-supermemo-xml-export-xml = Supermemo 用の XML 形式 (*.xml)
