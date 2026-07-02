database-check-corrupt = Коллекция файлы бузулган. Сураныч, автоматтык камдык көчүрмөдөн калыбына келтириңиз.
database-check-rebuilt = Маалыматтар базасы кайра түзүлүп, оптималдаштырылды.
database-check-card-properties =
    { $count ->
        [one] { $count } жараксыз карта касиети оңдолду.
       *[other] { $count } жараксыз карта касиеттери оңдолду.
    }
database-check-card-last-review-time-empty =
    { $count ->
        [one] { $count } картага акыркы кайталоо убактысы кошулду.
       *[other] { $count } карталарга акыркы кайталоо убактысы кошулду.
    }
database-check-missing-templates =
    { $count ->
        [one] Калыбы жок { $count } карта өчүрүлдү.
       *[other] Калыбы жок { $count } карталар өчүрүлдү.
    }
database-check-field-count =
    { $count ->
        [one] Талааларынын саны туура эмес { $count } эскертме оңдолду.
       *[other] Талааларынын саны туура эмес { $count } эскертмелер оңдолду.
    }
database-check-new-card-high-due =
    { $count ->
        [one] Мөөнөт номери 1,000,000 же андан жогору болгон { $count } жаңы карта табылды - алардын ордун "Карап чыгуу" экранынан өзгөртүүнү карап көрүңүз.
       *[other] Мөөнөт номери 1,000,000 же андан жогору болгон { $count } жаңы карталар табылды - алардын ордун "Карап чыгуу" экранынан өзгөртүүнү карап көрүңүз.
    }
database-check-card-missing-note =
    { $count ->
        [one] Эскертмеси жок { $count } карта өчүрүлдү.
       *[other] Эскертмеси жок { $count } карталар өчүрүлдү.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] Кайталанган калыбы бар { $count } карта өчүрүлдү.
       *[other] Кайталанган калыбы бар { $count } карталар өчүрүлдү.
    }
database-check-missing-decks =
    { $count ->
        [one] { $count } жок топтом оңдолду.
       *[other] { $count } жок топтомдор оңдолду.
    }
database-check-revlog-properties =
    { $count ->
        [one] { $count } жараксыз касиеттери бар кайталоо жазуу оңдолду.
       *[other] { $count } жараксыз касиеттери бар кайталоо жазуулары оңдолду.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] Жараксыз utf8 символдору бар { $count } эскертме оңдолду.
       *[other] Жараксыз utf8 символдору бар { $count } эскертмелер оңдолду.
    }
database-check-fixed-invalid-ids =
    { $count ->
        [one] Убакыт белгиси келечекте болгон { $count } объект оңдолду.
       *[other] Убакыт белгиси келечекте болгон { $count } объекттер оңдолду.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Бир же бир нече эскертме түрү жок болуп кеткен. Аларды колдонгон эскертмелерге "db-check" менен башталган жаңы эскертме түрлөрү берилди, бирок талаа аталыштары жана карта дизайны жоголду, андыктан автоматтык камдык көчүрмөдөн калыбына келтиргениңиз оң.

## Progress info

database-check-checking-integrity = Коллекция текшерилүүдө...
database-check-rebuilding = Кайра курулууда...
database-check-checking-cards = Карталар текшерилүүдө...
database-check-checking-notes = Эскертмелер текшерилүүдө...
database-check-checking-history = Тарых текшерилүүдө...
database-check-title = Маалыматтар базасын текшерүү
