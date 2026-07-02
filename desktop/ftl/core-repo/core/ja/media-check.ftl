## Shown at the top of the media check screen

media-check-window-title = メディアをチェック
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    ごみ箱フォルダ：{ $count ->
       *[other] { $count }ファイル、{ $megs }MB
    }
media-check-missing-count = 欠落しているファイル：{ $count }個
media-check-unused-count = 使用されていないファイル：{ $count }個
media-check-renamed-count = 名前を変更したファイル数：{ $count }
media-check-oversize-count = 100MBを超えているファイル数：{ $count }
media-check-subfolder-count = サブフォルダの数：{ $count }
media-check-extracted-count = 抽出された画像：{ $count }

## Shown at the top of each section

media-check-renamed-header = ▼ 次のファイルは互換性のためにファイル名が変更されました
media-check-oversize-header = 100MBを超えるファイルはAnkiWebと同期することができません。
media-check-subfolder-header = Ankiはメディアフォルダ内のサブフォルダには対応していません。
media-check-missing-header = ▼ 次のファイルを参照しているカードがありますが、該当するファイルがメディアフォルダ内にありません
media-check-unused-header = ▼ 次のファイルはメディアフォルダ内に存在していますが、どのカードにも使用されていません
media-check-template-references-field-header =
    メディアやLaTeXを指定するHTMLタグ内でフィールドを参照している場合
    （例えば  { "{{Front}}" }.jpg  という表現で各カードに表示するファイルを指定している場合）、
    指定している個々のファイル（例えば、apple.jpg ）は、この「メディアをチェック」機能では検出できません。
    メディアやLaTeXのタグは、個々のノートに記載することをおすすめします。
    ▼ フィールドを参照しているタグが記載されているテンプレート

## Shown once for each file

media-check-renamed-file = 名前変更：{ $old } ->{ $new }
media-check-oversize-file = 100MB超：{ $filename }
media-check-subfolder-file = フォルダ：{ $filename }
media-check-missing-file = 実物なし（欠落）：{ $filename }
media-check-unused-file = 使用なし：{ $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = チェック済み: { $count }...

## Deleting unused media

media-check-delete-unused-confirm = どのカードにも使用されていないメディアファイルを削除します。よろしいですか？
media-check-files-remaining = 残り{ $count }個のファイル...
media-check-delete-unused-complete = { $count }個のファイルをごみ箱に移動しました。
media-check-trash-emptied = ごみ箱フォルダを空にしました
media-check-trash-restored = 削除したファイルをメディアフォルダに戻しました。

## Rendering LaTeX

media-check-all-latex-rendered = すべてのLaTeXをレンダリングしました。

## Buttons

media-check-delete-unused = 使用なしﾌｧｲﾙを削除
media-check-render-latex = LaTexをレンダリングする
# button to permanently delete media files from the trash folder
media-check-empty-trash = ごみ箱を空にする
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = 削除したファイルを復元する
media-check-check-media-action = メディアをチェック
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = missing-media
# add a tag to notes with missing media
media-check-add-tag = 欠落ﾌｧｲﾙ名記載ﾉｰﾄにﾀｸﾞ
