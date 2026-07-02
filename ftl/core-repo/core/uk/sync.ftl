### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = Додано: { $up }↑ { $down }↓
sync-media-removed-count = Прибрано: { $up }↑ { $down }↓
sync-media-checked-count = Перевірено: { $count }
sync-media-starting = Почалась синхронізація медіа…
sync-media-complete = Синхронізація медіа виконана.
sync-media-failed = Не вдалось синхронізувати медіа.
sync-media-aborting = Скасування синхронізації медіа…
sync-media-aborted = Синхронізацію медіа скасовано.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = Синхронізація медіа вимкнено.
# Title of the screen that shows syncing progress history
sync-media-log-title = Журнал синхронізації медіа

## Error messages / dialogs

sync-conflict = Одночасно може синхронізуватися тільки одна копія Anki з Вашим аккаунтом. Будь ласка, зачекайте декілька хвилин і спробуйте повторно.
sync-server-error = У AnkiWeb проблеми. Спробуйте ще раз через декілька хвилин.
sync-client-too-old = Ваша версія Anki застаріла. Будь ласка, оновіть до останньої версії щоб продовжити синхронізацію.
sync-wrong-pass = Невірні логін AnkiWeb або пароль; повторіть спробу.
sync-resync-required = Будь ласка, синхронізуйте повторно. Якщо це повідомлення повторно з'являється, то, будь ласка, напишіть на сайті підтримки.
sync-must-wait-for-end = Anki синхронізується. Будь ласка, зачекайте завершення синхронізації і спробуйте повторно.
sync-confirm-empty-download = В колекції на пристрої немає карток. Завантажити з AnkiWeb?
sync-confirm-empty-upload = Колекція на AnkiWeb не має карток. Замінити її локальною колекцією?
sync-conflict-explanation =
    Ваші колоди тут та на сервері AnkiWeb відрізняются настільки, що не можуть бути об'єднані автоматично, тому необхідно замінити копію колоди на даному пристрою тією, що на сервері, або навпаки. 
    
    Якщо ви оберете варіант завантаження із сервера, Anki завнтажить колекцію колод із сервера AnkiWeb, при цьому будь-які зміни, зроблені в колодах на цьому пристрої після останньої синхронізації, буде втрачено. 
    
    Якщо ви оберете відправку на сервер, Anki відправить вашу колекцію колод на сервер AnkiWeb, при цьому будь-які зміни, зроблені в колодах на сервері AnkiWeb або інших пристроях після останньої синхронізації, буде втрачено. 
    
    Після синхронізації усіх пристроїв, статистика наступних переглядів карток та додані картки будуть об'єднані автоматично.
sync-conflict-explanation2 =
    Колоди на цьому пристрої та AnkiWeb конфліктують. Оберіть, яку версію залишити:
    
    - Оберіть **{ sync-download-from-ankiweb }**, щоб замінити колоди на пристрої версією з AnkiWeb. Ви втратите всі зміни, внесені на цьому пристрої після останньої синхронізації.
    - Оберіть **{ sync-upload-to-ankiweb }**, щоб замінити колоди на AnkiWeb колодами з цього пристрою та видалити зміни в AnkiWeb.
    
    Щойно конфлікт буде усунено, синхронізація працюватиме як зазвичай.
sync-ankiweb-id-label = Обліковий запис на AnkiWeb:
sync-password-label = Пароль
sync-account-required =
    <h1>Необхідно увійти в обліковий запис</h1>
    Для підтримки синхронізації вашої колекції колод потрібен обліковий запис, це безкоштовно. <a href="{ $link }">Зареєструйтеся</a> для отримання облікового запису, а потім внизу введіть Ваш логін та пароль.
sync-sanity-check-failed = Будь ласка, скористайтесь перевіркою бази даних, потім синхронізуйте знову. Якщо проблема залишиться, скористайтесь повною синхронізацією в налаштуваннях, будь ласка.
sync-clock-off = Неможливо синхронізувати – на Вашому годиннику невірно встановлено час.
sync-upload-too-large =
    Розмір вашої колекції завеликий, щоб надіслати на AnkiWeb. Ви можете зменшити її
    розмір, видаливши небажані колоди (за бажанням, спочатку експортувавши їх), і
    тоді використайте «Перевірити базу даних», щоб зменшити її. ({ $details })
sync-sign-in = Увійти
sync-ankihub-dialog-heading = Вхід до AnkiHub
sync-ankihub-username-label = Ім'я користувача або е-пошта:
sync-ankihub-login-failed = Неможливо увійти до AnkiHub з цими обліковими даними.
sync-ankihub-addon-installation = Встановлення додатку AnkiHub

## Buttons

sync-media-log-button = Журнал медіа
sync-abort-button = Перервати
sync-download-from-ankiweb = Завантажити з AnkiWeb
sync-upload-to-ankiweb = Відправити на AnkiWeb
sync-cancel-button = Скасувати

## Normal sync progress

sync-downloading-from-ankiweb = Завантаження з  AnkiWeb...
sync-uploading-to-ankiweb = Відправляю на  AnkiWeb...
sync-syncing = Синхронізую...
sync-checking = Перевірка…
sync-connecting = Підключення...
sync-added-updated-count = Додано/оновлено: { $up }↑ { $down }↓
sync-log-in-button = Увійти
sync-log-out-button = Вийти
sync-collection-complete = Завершено синхронізацію колекції.
