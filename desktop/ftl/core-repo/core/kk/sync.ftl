### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = Қосылды: { $up }↑ { $down }↓
sync-media-removed-count = Жойылды: { $up }↑ { $down }↓
sync-media-checked-count = Тексерілген: { $count }
sync-media-starting = Медиа үйлестіруді бастау...
sync-media-complete = Медиа үйлестіру аяқталды.
sync-media-failed = Медиа үйлестіру сәтсіз аяқталды.
sync-media-aborting = Медиа үйлестіруді доғару...
sync-media-aborted = Медиа үйлестіру доғарылды.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = Медиа үйлестіру өшірілген.
# Title of the screen that shows syncing progress history
sync-media-log-title = Медиа Үйлестіру Жұрналы

## Error messages / dialogs

sync-conflict = Anki бағдарламасының тек бір данасы ғана тіркелгіңізбен бір уақытта үйлесе алады. Бірнеше минут күтіп, қайтадан көріңіз.
sync-server-error = AnkiWeb қате шығарды. Бірнеше минуттан кейін қайтадан көріңіз.
sync-client-too-old = Anki нұсқасы тым ескі. Үйлесімді жалғастыру үшін соңғы нұсқасына жаңартыңыз.
sync-wrong-pass = AnkiWeb сәйкестендіргіші немесе құпиясөз қате; қайтадан көріңіз.
sync-resync-required = Қайтадан үйлестіріп көріңіз. Егер бұл хабар қайтадан шықса, қолдау сайтына жазыңыз.
sync-must-wait-for-end = Anki үйлестіріліп жатыр. Үйлесіп біткенші күтіп, қатаданан көріңіз.
sync-confirm-empty-download = Жергілікті жинақта карта жоқ. AnkiWeb-тен жүктеп алу?
sync-confirm-empty-upload = AnkiWeb жинағында карта жоқ. Орнына жергілікті жинақты қою?
sync-conflict-explanation =
    Мұндағы колодаларыңыз бен AnkiWeb-тегі колодаларыңыз біріктіруге келмейді, сондықтан бір жақтағы колодаларды екінші жақтағы колодалармен алмастыру қажет.
    
    Егер жүктеп алуды таңдасаңыз, Anki колекцияны AnkiWeb-тен алады, және осы құрылғыда соңғы үйлестіруден бері енгізілген өзгерістер жойылады.
    
    Жүктеп салуды таңдасаңыз, Anki осы құрылғыдағы деректерді AnkiWeb-ке жібереді, және AnkiWeb-те күтіп тұрған өзгерістер жойылады.
    
    Барлық құрылғылар үйлестірілген кейін, болашақ қайталау мен қосылған карталар автоматты түрде біріктіріледі.
sync-conflict-explanation2 =
    Бұл құрылғыдағы колодалар мен AnkiWeb арасындағы қайшылық бар. Қай нұсқаны сақтайтыныңызды таңдау қажет:
    
    { sync-download-from-ankiweb } таңдасаңыз, мұндағы колодаларды AnkiWeb нұсқасымен ауыстырасыз. Соңғы үйлестіруден бері енгізілген өзгерістер жойылады.
    { sync-upload-to-ankiweb } таңдасаңыз, AnkiWeb-тің нұсқаларын осы құрылғыдан алынған колодалармен алмастырасыз, және AnkiWeb-тегі өзгерістер жойылады.
    
    Қайшылық шешілгеннен кейін, үйлестіру әдеттегідей жұмыс істейтін болады.
sync-ankiweb-id-label = AnkiWeb сәйкестендіргіші:
sync-password-label = Құпиясөз:
sync-account-required =
    <h1>Тіркелгі қажет</h1> 
    Жинағыңызды үйлестіріп ұстап тұру үшін тегін тіркелгі қажет. Тіркелгі үшін <a href="{ $link }">тіркеліңіз</a>, сосын төменде деректеріңізді енгізіңіз.
sync-sanity-check-failed = Дерекқорды тексеру функциясын пайдаланыңыз, сосын қайтадан үйлестіру жасаңыз. Егер проблемалар сақталса, параметрлер бейнебетіне біржақты үйлестіру жасауды мәжбүрлеңіз.
sync-clock-off = Үйлестіру мүмкін емес - сағатыңыздың уақыты бұрыс.
# “details” expands to a string such as “300.14 MB > 300.00 MB”
sync-upload-too-large =
    Жинақ файлыңыз AnkiWeb-ке жіберу үшін тым үлкен. Қажет емес 
    колодаларды (еркіңізбен экспорттап) жойып, содан кейін дерекқорды тексеру
    функциясын пайдаланып, файл өлшемін азайта аласыз. ({ $details }
sync-sign-in = Кіру
sync-ankihub-dialog-heading = AnkiHub-қа Кіру
sync-ankihub-username-label = Пайдаланушы аты не Э-пошта:
sync-ankihub-login-failed = Берілген деректермен AnkiHub-қа кіру мүмкін емес.
sync-ankihub-addon-installation = AnkiHub Қондарманы Жүктеу

## Buttons

sync-media-log-button = Медиа Жұрналы
sync-abort-button = Доғару
sync-download-from-ankiweb = AnkiWeb-тен Жүктеп алу
sync-upload-to-ankiweb = AnkiWeb-ке Жүктеу
sync-cancel-button = Болдырмау

## Normal sync progress

sync-downloading-from-ankiweb = AnkiWeb-тен Жүктеу...
sync-uploading-to-ankiweb = AnkiWeb-ке Жүктеу...
sync-syncing = Үйлестіру...
sync-checking = Тексеру...
sync-connecting = Қосылу...
sync-added-updated-count = Қосылды/өңделді: { $up }↑ { $down }↓
sync-log-in-button = Кіру
sync-log-out-button = Шығу
sync-collection-complete = Жинақ үйлестірілді.
