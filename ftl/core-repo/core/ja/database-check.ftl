database-check-corrupt = コレクションが壊れています。マニュアルをご覧ください。
database-check-rebuilt = データベースを再構築し最適化しました。
database-check-card-properties = 無効なプロパティを持っている{ $count }枚のカードを修正しました。
database-check-card-last-review-time-empty = 以前学習した{ $count }枚のカードの情報を整理・補完（直近の学習日時の情報を、学習履歴を参照してカードに直接記録）しました。
database-check-missing-templates = テンプレートがない{ $count }枚のカードを削除しました。
database-check-field-count =
    { $count ->
       *[other] 正しくないフィールド数をもつ{ $count }個のノートを修正しました。
    }
database-check-new-card-high-due =
    { $count ->
       *[other] 位置（新規カード番号）が1,000,000以上の新規カードが{ $count }枚見つかりました。ブラウザ画面のメニューで該当カードの位置を変更することをおすすめします。
    }
database-check-card-missing-note = ノートのないカードを{ $count }枚削除しました。
database-check-duplicate-card-ords =
    { $count ->
       *[other] テンプレートのないカードを{ $count }枚削除しました。
    }
database-check-missing-decks =
    { $count ->
       *[other] 見つからないデッキを{ $count }個修正しました。
    }
database-check-revlog-properties =
    { $count ->
       *[other] 無効なプロパティをもつ{ $count }個のエントリーを修正しました。
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
       *[other] 無効なutf8の文字が含まれる{ $count }個のノートを修正しました。
    }
database-check-fixed-invalid-ids =
    { $count ->
       *[other] 未来の日時のタイムスタンプを持つ{ $count }個のオブジェクトを修正しました。
    }
# "db-check" is always in English
database-check-notetypes-recovered = ひとつかそれ以上のノートタイプがみつかりません。そのノートタイプを使用したノートには、"db-check"で始まる新しいノートタイプが与えられましたが、フィールド名やカードデザインの情報は失われています。そのため、自動バックアップから復元することをおすすめします。

## Progress info

database-check-checking-integrity = コレクションをチェック中...
database-check-rebuilding = 再構築中...
database-check-checking-cards = カードをチェック中...
database-check-checking-notes = ノートをチェック中...
database-check-checking-history = 履歴をチェック中...
database-check-title = データベースをチェック
