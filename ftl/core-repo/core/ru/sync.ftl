### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = Добавлено: { $up }↑ { $down }↓
sync-media-removed-count = Удалено: { $up }↑ { $down }↓
sync-media-checked-count = Проверено: { $count }
sync-media-starting = Запускается синхронизация медиафайлов...
sync-media-complete = Синхронизация медиафайлов завершена.
sync-media-failed = Синхронизация медиафайлов не удалась.
sync-media-aborting = Отменяется синхронизация медиафайлов...
sync-media-aborted = Синхронизация медиафайлов отменена.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = Синхронизация медиафайлов отключена.
# Title of the screen that shows syncing progress history
sync-media-log-title = Журнал синхронизации медиафайлов

## Error messages / dialogs

sync-conflict = Только одна программа за раз может синхронизироваться с учётной записью. Подождите немного и попробуйте снова.
sync-server-error = У AnkiWeb проблемы. Попробуйте позже.
sync-client-too-old = Ваша версия Anki устарела. Чтобы синхронизировать данные, обновите Anki.
sync-wrong-pass = Имя и пароль AnkiWeb неверны. Попробуйте ещё раз.
sync-resync-required = Повторите синхронизацию. Если это сообщение появляется снова, обратитесь на сайт поддержки.
sync-must-wait-for-end = Anki сейчас синхронизируется. Подождите немного и попробуйте снова.
sync-confirm-empty-download = В локальной коллекции нет карточек. Загрузить их с AnkiWeb?
sync-confirm-empty-upload = В коллекции AnkiWeb нет карточек. Заменить её локальной коллекцией?
sync-conflict-explanation =
    Ваши колоды на AnkiWeb отличаются от локальной копии и не могут быть объединены. Вы можете перезаписать локальную коллекцию версией с AnkiWeb или наоборот.
    
    Если вы выберете загрузку с AnkiWeb, то Anki скачает колоды с AnkiWeb, и все изменения, произведённые на этом компьютере с момента последней синхронизации, будут утеряны.
    
    Если вы выберете выгрузку на AnkiWeb, то Anki выгрузит колоды на AnkiWeb, и все изменения, произведённые на AnkiWeb или других устройствах с момента последней синхронизации, будут утеряны.
    
    После того как все устройства будут синхронизированы, информация о просмотрах и новые карточки будут обновляться автоматически.
sync-conflict-explanation2 =
    Возник конфликт между колодами на этом устройстве и AnkiWeb. Вы должны выбрать, какую версию оставить:
    
    - Выберите **{ sync-download-from-ankiweb }**, чтобы заменить локальные колоды на колоды из AnkiWeb. Вы потеряете все изменения, сделанные на этом устройстве с момента последней синхронизации.
    - Выберите **{ sync-upload-to-ankiweb }**, чтобы заменить версии AnkiWeb колодами с этого устройства и удалить все изменения на AnkiWeb.
    
    После устранения конфликта синхронизация будет работать как обычно.
sync-ankiweb-id-label = Логин на AnkiWeb:
sync-password-label = Пароль:
sync-account-required =
    <h1>Нужна учётная запись</h1>
    Для синхронизации коллекции необходима учётная запись. <a href="{ $link }">Создайте</a> учётную запись, и добавьте её внизу.
sync-sanity-check-failed = Используйте функцию "Проверить базу данных", затем синхронизируйте снова. Если проблемы останутся, воспользуйтесь полной синхронизацией на экране настроек.
sync-clock-off = Не удалось синхронизировать: время на устройстве не точное.
# “details” expands to a string such as “300.14 MB > 300.00 MB”
sync-upload-too-large =
    Файл с данной подборкой слишком велик для AnkiWeb. Вы можете сократить его
    размер, удалив ненужные колоды (при необходимости, их можно сначала экспортировать), а 
    затем с помощью Проверки базы данных сжать файл. ({ $details })
sync-sign-in = Войти
sync-ankihub-dialog-heading = AnkiHub логин
sync-ankihub-username-label = Имя пользователя или email:
sync-ankihub-login-failed = Не удаётся войти на Ankihub с введёнными логином/паролем.
sync-ankihub-addon-installation = Установка дополнения AnkiHub

## Buttons

sync-media-log-button = Журнал медиафайлов
sync-abort-button = Отменить
sync-download-from-ankiweb = Скачать с AnkiWeb
sync-upload-to-ankiweb = Выгрузить на AnkiWeb
sync-cancel-button = Отменить

## Normal sync progress

sync-downloading-from-ankiweb = Загружается с AnkiWeb...
sync-uploading-to-ankiweb = Выгружается на AnkiWeb...
sync-syncing = Синхронизируется...
sync-checking = Проверяется...
sync-connecting = Подключается...
sync-added-updated-count = Добавлено/изменено: { $up }↑ { $down }↓
sync-log-in-button = Войти
sync-log-out-button = Выйти
sync-collection-complete = Синхронизация коллекции завершена.
