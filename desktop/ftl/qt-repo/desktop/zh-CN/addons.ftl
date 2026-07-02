addons-possibly-involved = 相关插件：{ $addons }
addons-failed-to-load =
    下列插件加载失败。如果该问题持续出现，请在「工具」>「插件」中禁用或删除此插件。
    
    加载 { $name } 时：
    { $traceback }
addons-failed-to-load2 =
    以下插件加载失败：
    { $addons }
    
    这些插件需要更新至支持本版本的 Anki，请到「工具」>「插件」点击「{ addons-check-for-updates }」按钮来查看是否有可用的更新。
    
    您可以使用「{ about-copy-debug-info }」按钮来复制调试信息，以向插件作者反馈。
    
    对于无可用更新的插件，您可以禁用或删除它们来阻止这条消息的出现。
addons-startup-failed = 插件启动失败
# Shown in the add-on configuration screen (Tools>Add-ons>Config), in the title bar
addons-config-window-title = { $name } 插件设置
addons-config-validation-error = 提供的设置存在问题：{ $problem }，位于路径{ $path }，依据模式{ $schema }。
addons-window-title = 插件
addons-addon-has-no-configuration = 插件无设置界面。
addons-addon-installation-error = 插件安装出错
addons-browse-addons = 插件官网
addons-changes-will-take-effect-when-anki = 更改将在 Anki 重启后生效。
addons-check-for-updates = 检查更新
addons-checking = 正在检查…
addons-code = 代码：
addons-config = 插件设置
addons-configuration = 设置
addons-corrupt-addon-file = 损坏的插件文件。
addons-disabled = （已禁用）
addons-disabled2 = （已禁用）
addons-download-complete-please-restart-anki-to = 下载完成。请重启 Anki 以应用更改。
addons-downloaded-fnames = 已下载 { $fname }
addons-downloading-adbd-kb02fkb = 正在下载 { $part }/{ $total } ({ $kilobytes }KB)...
addons-error-downloading-ids-errors = 下载出错 <i>{ $id }</i>：{ $error }
addons-error-installing-bases-errors = 安装出错 <i>{ $base }</i>：{ $error }
addons-get-addons = 获取插件…
addons-important-as-addons-are-programs-downloaded = <b>重要</b>：互联网上下载的插件可能含有恶意程序<b>您应当仅安装您认为可信的插件。</b><br><br>确定要继续安装以下插件吗？<br><br>%(names)s
addons-install-addon = 安装插件
addons-install-addons = 安装插件
addons-install-anki-addon = 安装 Anki 插件
addons-install-from-file = 本地安装…
addons-installation-complete = 安装完成
addons-installed-names = 已安装 { $name }
addons-installed-successfully = 安装成功。
addons-invalid-addon-manifest = 无效的插件清单。
addons-invalid-code = 无效代码。
addons-invalid-code-or-addon-not-available = 代码无效或该插件无法适用于当前版本的 Anki
addons-invalid-configuration = 设置无效：
addons-invalid-configuration-top-level-object-must = 设置无效：顶层对象必须为 map。
addons-no-updates-available = 无可用更新。
addons-one-or-more-errors-occurred = 出现错误：
addons-packaged-anki-addon = 已打包的 Anki 插件
addons-please-check-your-internet-connection = 请检查网络连接。
addons-please-report-this-to-the-respective = 请将此内容反馈给相应的插件开发者。
addons-please-restart-anki-to-complete-the = <b>请重启 Anki 以完成安装。</b>
addons-please-select-a-single-addon-first = 请先选择一个插件。
addons-requires = （需要{ $val }）
addons-restored-defaults = 已恢复默认
addons-the-following-addons-are-incompatible-with = 以下插件与 { $name } 不兼容，已被禁用：{ $found }
addons-the-following-addons-have-updates-available = 以下插件有可用更新。是否立即安装？
addons-the-following-conflicting-addons-were-disabled = 以下插件发生冲突，已被禁用：
addons-this-addon-is-not-compatible-with = 此插件与当前 Anki 版本不兼容。
addons-to-browse-addons-please-click-the = 浏览插件请点击下方「插件官网」按钮。<br><br>并将您想要安装插件的代码粘贴到下方代码框中。如需输入多个代码，请以空格分隔。
addons-toggle-enabled = 启用/禁用
addons-unable-to-update-or-delete-addon = 无法更新或删除插件。请在打开 Anki 时按住 <kbd>Shift</kbd> 键以临时插件加载，然后重试。 调试信息：{ $val }
addons-unknown-error = 未知错误：{ $val }
addons-view-addon-page = 插件网页
addons-view-files = 查看文件
addons-delete-the-numd-selected-addon =
    { $count ->
       *[other] 确定要删除已选中的 { $count } 个插件吗？
    }
addons-choose-update-window-title = 更新插件
addons-choose-update-update-all = 全部更新
