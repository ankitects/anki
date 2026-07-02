studying-again = Снова
studying-all-buried-cards = Все отложенные карточки
studying-audio-5s = Аудио -5 с
studying-audio-and5s = Аудио +5 с
studying-buried-siblings = Отложенные связанные
studying-bury = Отложить
studying-bury-card = Отложить карточку
studying-bury-note = Отложить запись
studying-card-suspended = Карточка исключена.
studying-card-was-a-leech = Карточка была приставучей.
studying-cards-buried =
    { $count ->
        [one] Отложена { $count } карточка
        [few] Отложены { $count } карточки
        [many] Отложено { $count } карточек
       *[other] Отложено { $count } карточек
    }
studying-cards-will-be-automatically-returned-to = Карточки автоматически вернутся в свои колоды после повторения.
studying-continue = Продолжить
studying-counts-differ = Количество отличается от списка колод потому что включено откладывание. Некоторые карточки были исключены, и другие могли занять их место.
studying-delete-note = Удалить запись
studying-deleting-this-deck-from-the-deck = Удаление этой колоды из списка вернёт все оставшиеся карточки в их колоды.
studying-easy = Легко
studying-edit = Редактировать
studying-empty = Очистить
studying-finish = Завершить
studying-flag-card = Поставить флажок
studying-good = Хорошо
studying-hard = Трудно
studying-it-has-been-suspended = Исключена.
studying-manually-buried-cards = Отложенные вручную
studying-mark-note = Отметить запись
studying-more = Ещё
studying-no-cards-are-due-yet = Нет карточек на сегодня.
studying-note-suspended = Запись исключена.
studying-pause-audio = Аудио на паузу
studying-please-run-toolsempty-cards = Выберите в меню «Инструменты»—«Пустые карточки»
studying-record-own-voice = Записать свой голос
studying-replay-own-voice = Воспроизвести свой голос
studying-show-answer = Показать ответ
studying-space = Пробел
studying-study-now = Учить
studying-suspend = Исключить
studying-suspend-note = Исключить запись
studying-this-is-a-special-deck-for = Это специальная колода для обучения вне обычного расписания.
studying-to-review = Повторяемые
studying-type-answer-unknown-field = Напишите ответ: неизвестное поле { $val }
studying-unbury = Вернуть
studying-what-would-you-like-to-unbury = Какие вы хотите вернуть?
studying-you-havent-recorded-your-voice-yet = Вы ещё не записали своего голоса.
studying-card-studied-in-minute =
    { $cards ->
        [one]
            { $minutes ->
                [one] { $cards } карта изучена за { $minutes } минуту.
                [few] { $cards } карта изучена за { $minutes } минуты.
               *[many] { $cards } карта изучена за { $minutes } минут.
            }
        [few]
            { $minutes ->
                [one] { $cards } карты изучены за { $minutes } минуту.
                [few] { $cards } карты изучены за { $minutes } минуты.
               *[many] { $cards } карты изучены за { $minutes } минут.
            }
       *[many]
            { $minutes ->
                [one] { $cards } карт изучено за { $minutes } минуту.
                [few] { $cards } карт изучено за { $minutes } минуты.
               *[many] { $cards } карт изучено за { $minutes } минут.
            }
    }
studying-question-time-elapsed = Время вопроса истекло
studying-answer-time-elapsed = Время ответа истекло

## OBSOLETE; you do not need to translate this

studying-card-studied-in =
    { $count ->
        [one] { $count } карточка изучена за
        [few] { $count } карточки изучены за
        [many] { $count } карточек изучены за
       *[other] { $count } карточек изучены за
    }
studying-minute =
    { $count ->
        [one] { $count } минуту.
        [few] { $count } минуты.
        [many] { $count } минут.
       *[other] { $count } минут.
    }
