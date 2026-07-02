addons-possibly-involved = 次のアドオンが関係している可能性があります：{ $addons }
addons-failed-to-load =
    インストールしたアドオンの読み込みに失敗しました。問題が続く場合は、メニューバーの[ツール]→[アドオン]で表示されるアドオン管理画面で、該当するアドオンを無効化するか削除してください。
    
    '{ $name }' を読み込んだ際のエラー:
    { $traceback }
addons-failed-to-load2 =
    次のアドオンの読み込みに失敗しました：
    { $addons }
    
    上記のアドオンは、このバージョンのAnkiで使用するためにはアップデートが必要である可能性があります。「{ addons-check-for-updates }」ボタンをクリックして、利用可能なアップデートがないか確認してください。
    
    「{ about-copy-debug-info }」ボタンをクリックすると、 アドオンの作者に問い合わせを行う際に作者にとって参考となる情報を取得できます。
    
    利用可能なアップデートがないアドオンについては、そのアドオンを無効にするか削除することで、このメッセージが表示されないようにすることができます。
addons-startup-failed = アドオンの起動に失敗
# Shown in the add-on configuration screen (Tools>Add-ons>Config), in the title bar
addons-config-window-title = 「{ $name }」を設定
addons-config-validation-error = 与えられた条件の設定に問題がありました：{ $problem }、at path = { $path }、against schema = { $schema }
addons-window-title = アドオン
addons-addon-has-no-configuration = このアドオンには設定項目がありません。
addons-addon-installation-error = アドオンのインストールエラー
addons-browse-addons = アドオン一覧
addons-changes-will-take-effect-when-anki = 変更を反映させるにはAnkiを再起動してください。
addons-check-for-updates = アップデートを確認
addons-checking = 確認中...
addons-code = コード:
addons-config = 設定
addons-configuration = 設定
addons-corrupt-addon-file = 破損したアドオンファイル
addons-disabled = (無効)
addons-disabled2 = (無効)
addons-download-complete-please-restart-anki-to = ダウンロードが完了しました。変更を適用するにはAnkiを再起動してください。
addons-downloaded-fnames = { $fname } をダウンロードしました。
addons-downloading-adbd-kb02fkb = ダウンロード中 { $part }/{ $total } ({ $kilobytes }KB)...
addons-error-downloading-ids-errors = { $id } のダウンロード中にエラーが発生しました: { $error }
addons-error-installing-bases-errors = { $base } のインストール中にエラーが発生しました: { $error }
addons-get-addons = 新たにアドオンを取得...
addons-important-as-addons-are-programs-downloaded = <b>重要</b>：アドオンはインターネットからダウンロードされるプログラムであるため、マルウェアである可能性もあります。<b>信頼できるアドオンだけをインストールしてください。</b><br><br>以下のAnkiのアドオンのインストールを続行してもよろしいですか？<br><br>%(names)s
addons-install-addon = アドオンをインストールする
addons-install-addons = アドオンをインストールする
addons-install-anki-addon = Ankiアドオンをインストール
addons-install-from-file = ファイルからインストール...
addons-installation-complete = インストール完了
addons-installed-names = { $name } をインストールしました
addons-installed-successfully = インストールが完了しました。
addons-invalid-addon-manifest = 無効なアドオンのマニフェスト
addons-invalid-code = コードが無効です。
addons-invalid-code-or-addon-not-available = 無効なコードです。数字が誤っているか、このアドオンがこのバージョンのAnkiに対応していません。
addons-invalid-configuration = 無効な設定:
addons-invalid-configuration-top-level-object-must = 無効な設定：トップレベルのオブジェクトはマップである必要があります
addons-no-updates-available = 利用可能なアップデートはありません。
addons-one-or-more-errors-occurred = １つかそれ以上のエラーが発生しました：
addons-packaged-anki-addon = パッケージ化されたAnkiアドオン
addons-please-check-your-internet-connection = インターネット接続を確認してください
addons-please-report-this-to-the-respective = 当該アドオン作成者に報告してください。
addons-please-restart-anki-to-complete-the = <b>インストールを完了させるためにAnkiを再起動してください。</b>
addons-please-select-a-single-addon-first = はじめにアドオンを選択してください。
addons-requires = ({ $val }が必要)
addons-restored-defaults = 初期設定に戻す
addons-the-following-addons-are-incompatible-with = これらのアドオンは{ $name }と互換性がないため無効化されました：{ $found }
addons-the-following-addons-have-updates-available = 以下のアドオンにアップデートがあります。今すぐインストールしますか？
addons-the-following-conflicting-addons-were-disabled = これらの競合するアドオンを無効化しました：
addons-this-addon-is-not-compatible-with = このアドオンはご使用のAnkiのバージョンとの互換性がありません。
addons-to-browse-addons-please-click-the = 下の「アドオン一覧」ボタンをクリックすると、使用可能なアドオンの一覧が表示されます。<br><br>使用したいアドオンがある場合は、そのアドオンのページの「Download」欄に記載されているコード（8桁の数字）をコピーして下の欄に貼り付けてください。スペースで間隔を空けて複数のコードを入力することも可能です。
addons-toggle-enabled = 有効/無効 の切り替え
addons-unable-to-update-or-delete-addon = アドオンを更新または削除することができません。一時的にアドオンを無効にするため、Shiftキーを押したままの状態でAnkiを起動し、その後再度お試しください。  デバッグ情報：{ $val }
addons-unknown-error = 不明なエラー：{ $val }
addons-view-addon-page = 選択中のアドオンの詳細
addons-view-files = ファイルを見る
addons-delete-the-numd-selected-addon =
    { $count ->
       *[other] 選択した{ $count }個のアドオンを削除しますか？
    }
addons-choose-update-window-title = アドオンをアップデート
addons-choose-update-update-all = すべてをアップデート
