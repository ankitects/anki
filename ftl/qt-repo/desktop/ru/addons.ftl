addons-possibly-involved = Причиной могли послужить: { $addons }
addons-failed-to-load =
    Установленное дополнение не запустилось. Если проблема сохраняется, выберите в меню «Инструменты» — «Дополнения» и отключите или удалите это дополнение.
    
    При загрузке '{ $name }':
    { $traceback }
addons-failed-to-load2 =
    Не удалось установить следующие дополнения:
    { $addons }
    
    Возможно, их необходимо обновить для поддержки этой версии Anki. Нажмите кнопку { addons-check-for-updates }, чтобы узнать, доступны ли какие-либо обновления.
    
    Вы можете нажать кнопку { about-copy-debug-info }, чтобы получить информацию, которую необходимо вставить в отчет автору дополнения.
    
    Чтобы это сообщение не появлялось, вам необходимо отключить или удалить дополнения, у которых нет доступных обновлений.
addons-startup-failed = Дополнение не запустилось
# Shown in the add-on configuration screen (Tools>Add-ons>Config), in the title bar
addons-config-window-title = Настроить '{ $name }'
addons-config-validation-error = Возникла проблема с данной конфигурацией: { $problem }, по адресу { $path }, в отношении схемы { $schema }.
addons-window-title = Дополнения
addons-addon-has-no-configuration = У дополнения нет настроек.
addons-addon-installation-error = Ошибка при установке дополнения
addons-browse-addons = Обзор дополнений
addons-changes-will-take-effect-when-anki = Перезапустите Anki, чтобы применить изменения.
addons-check-for-updates = Проверить обновления
addons-checking = Проверяется...
addons-code = Код:
addons-config = Конфигурация
addons-configuration = Конфигурация
addons-corrupt-addon-file = Файл дополнения повреждён.
addons-disabled = (отключено)
addons-disabled2 = (отключено)
addons-download-complete-please-restart-anki-to = Загрузка завершена. Перезапустите Anki, чтобы применить изменения.
addons-downloaded-fnames = Загружено { $fname }
addons-downloading-adbd-kb02fkb = Загружается { $part }/{ $total } ({ $kilobytes } кБ)...
addons-error-downloading-ids-errors = Ошибка при загрузке <i>{ $id }</i>: { $error }
addons-error-installing-bases-errors = Ошибка при установке <i>{ $base }</i>: { $error }
addons-get-addons = Установить дополнения...
addons-important-as-addons-are-programs-downloaded = <b>Внимание</b>: Дополнения скачиваются из интернета и могут быть вредоносны. <b>Устанавливайте только проверенные дополнения.</b><br><br>Установить выбранные дополнения Anki?<br><br>%(names)s
addons-install-addon = Установить дополнение
addons-install-addons = Установить дополнения
addons-install-anki-addon = Установить дополнение Anki
addons-install-from-file = Установить из файла...
addons-installation-complete = Установка завершена
addons-installed-names = Установлено { $name }
addons-installed-successfully = Успешно установлено
addons-invalid-addon-manifest = Неправильный манифест дополнения.
addons-invalid-code = Недопустимый код.
addons-invalid-code-or-addon-not-available = Неверный код или дополнение недоступное для этой версии Anki.
addons-invalid-configuration = Недопустимая конфигурация:
addons-invalid-configuration-top-level-object-must = Недопустимая конфигурация: объект верхнего уровня должен быть отображением
addons-no-updates-available = Обновлений нет
addons-one-or-more-errors-occurred = Произошла(и) ошибка(и):
addons-packaged-anki-addon = Упакованное дополнение Anki
addons-please-check-your-internet-connection = Проверьте соединение с интернетом.
addons-please-report-this-to-the-respective = Сообщите об этом автору дополнения.
addons-please-restart-anki-to-complete-the = <b>Перезапустите Anki, чтобы завершить установку.</b>
addons-please-select-a-single-addon-first = Сначала выберите одно дополнение.
addons-requires = (требует { $val })
addons-restored-defaults = Настройки сброшены
addons-the-following-addons-are-incompatible-with = Дополнения, несовместимые с %(name)s, были отключены: %(found)s
addons-the-following-addons-have-updates-available = Доступны обновления для этих дополнений. Установить их?
addons-the-following-conflicting-addons-were-disabled = Отключены данные несовместимые дополнения:
addons-this-addon-is-not-compatible-with = Это дополнение не совместимо с этой версией Anki.
addons-to-browse-addons-please-click-the = Для поиска дополнений нажмите кнопку "Обзор дополнений". <br><br>Когда вы найдёте нужное дополнение, введите его код внизу. Вы можете ввести несколько кодов, разделяя их пробелами.
addons-toggle-enabled = Включить — отключить
addons-unable-to-update-or-delete-addon = Не получается обновить или удалить дополнение. Запустите Anki зажав Shift, чтобы временно отключить дополнения. Потом попробуйте снова.  Отладочная информация: { $val }
addons-unknown-error = Неизвестная ошибка: { $val }
addons-view-addon-page = Открыть страницу дополнения
addons-view-files = Просмотреть файлы
addons-delete-the-numd-selected-addon =
    { $count ->
        [one] Удалить { $count } выбранное дополнение?
        [few] Удалить { $count } выбранных дополнения?
        [many] Удалить { $count } выбранных дополнений?
       *[other] Удалить { $count } выбранных дополнений?
    }
addons-choose-update-window-title = Обновить дополнения
addons-choose-update-update-all = Обновить всё
