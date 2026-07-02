database-check-corrupt = Колекція пошкоджена. Відновіть її з автоматичної резервної копії.
database-check-rebuilt = Перебудовано та оптимізовано базу даних.
database-check-card-properties =
    { $count ->
        [one] Виправлено { $count } картку з недійсними властивостями.
        [few] Виправлено { $count } картки з недійсними властивостями.
        [many] Виправлено { $count } карток з недійсними властивостями.
       *[other] Виправлено { $count } карток з недійсними властивостями.
    }
database-check-card-last-review-time-empty =
    { $count ->
        [one] Попередній час пригадування додано до { $count } картки.
        [few] Попередній час пригадування додано до { $count } карток.
       *[many] Попередній час пригадування додано до { $count } карток.
    }
database-check-missing-templates =
    { $count ->
        [one] Вилучено { $count } картку з відсутнім шаблоном.
        [few] Вилучено { $count } картки з відсутнім шаблоном.
        [many] Вилучено { $count } карток з відсутнім шаблоном.
       *[other] Вилучено { $count } карток з відсутнім шаблоном.
    }
database-check-field-count =
    { $count ->
        [one] Виправлено { $count } нотатку з невірною кількістю полів.
        [few] Виправлено { $count } нотатки з невірною кількістю полів.
       *[other] Виправлено { $count } нотаток з невірною кількістю полів.
    }
database-check-new-card-high-due =
    { $count ->
        [one] Знайдено { $count } нову картку з числом наступного перегляду ≥ 1'000'000 — варто змінити на екрані перегляду.
        [few] Знайдено { $count } нові картки з числом наступного перегляду ≥ 1'000'000 — варто змінити на екрані перегляду.
       *[other] Знайдено { $count } нових карток з числом наступного перегляду ≥ 1'000'000 — варто змінити на екрані перегляду.
    }
database-check-card-missing-note =
    { $count ->
        [one] Вилучено { $count } картка з відсутньою нотаткою.
        [few] Вилучено { $count } картки з відсутньою нотаткою.
        [many] Вилучено { $count } карток з відсутньою нотаткою.
       *[other] Вилучено { $count } карток з відсутньою нотаткою.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] Видалена { $count } картка з дубльованим шаблоном.
        [few] Видалені { $count } картки з дубльованим шаблоном.
       *[other] Видалено { $count } карток з дубльованим шаблоном.
    }
database-check-missing-decks =
    { $count ->
        [one] Виправлено { $count } відсутню колоду.
        [few] Виправлено { $count } відсутні колоди.
       *[other] Виправлено { $count } відсутніх колод.
    }
database-check-revlog-properties =
    { $count ->
        [one] Виправлено { $count } елемент перегляду з неправильними властивостями.
        [few] Виправлено { $count } елементи перегляду з неправильними властивостями.
       *[other] Виправлено { $count } елементів перегляду з неправильними властивостями.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] Виправлено символи UTF-8 в { $count } нотатці.
       *[other] Виправлено символи UTF-8 в { $count } нотатках.
    }
database-check-fixed-invalid-ids =
    { $count ->
        [one] Виправлено { $count } об'єкт з майбутніми часовими мітками.
        [few] Виправлено { $count } об'єкти з майбутніми часовими мітками.
       *[other] Виправлено { $count } об'єктів з майбутніми часовими мітками.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Бракує одного або більше типів нотаток. Нотатки, які їх використовували тепер мають новий тип нотаток, який починається з «db-check», але назви полів та вигляд карток втрачено, можливо, краще відновити з автоматичної копії.

## Progress info

database-check-checking-integrity = Перевірка колекції…
database-check-rebuilding = Перебудова…
database-check-checking-cards = Перевірка карток…
database-check-checking-notes = Перевірка нотаток…
database-check-checking-history = Перевірка історії…
database-check-title = Перевірити базу даних
