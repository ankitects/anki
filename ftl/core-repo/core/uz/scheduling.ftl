## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount } s
scheduling-answer-button-time-minutes = { $amount } min
scheduling-answer-button-time-hours = { $amount } soat
scheduling-answer-button-time-days = { $amount } kun
scheduling-answer-button-time-months = { $amount } oy
scheduling-answer-button-time-years = { $amount } yil

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] { $amount } soniya
       *[other] { $amount } soniya
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] { $amount } minut
       *[other] { $amount } minut
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } soat
       *[other] { $amount } soat
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } kun
       *[other] { $amount } kun
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } oy
       *[other] { $amount } oy
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } yil
       *[other] { $amount } yil
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    { $unit ->
        [seconds]
            { $amount ->
                [one] Keyingi oʻrganish kartasi { $amount } soniyadan soʻng tayyor boʻladi.
               *[other] Keyingi oʻrganish kartasi { $amount } soniyadan soʻng tayyor boʻladi.
            }
        [minutes]
            { $amount ->
                [one] Keyingi oʻrganish kartasi { $amount } daqiqadan soʻng tayyor boʻladi.
               *[other] Keyingi oʻrganish kartasi { $amount } daqiqadan soʻng tayyor boʻladi.
            }
       *[hours]
            { $amount ->
                [one] Keyingi oʻrganish kartasi { $amount } soatdan soʻng tayyor boʻladi.
               *[other] Keyingi oʻrganish kartasi { $amount } soatdan soʻng tayyor boʻladi.
            }
    }
scheduling-learn-remaining =
    { $remaining ->
        [one] Bugunga bitta oʻrganish kartasi qoldi.
       *[other] Bugunga { $remaining } ta oʻrganish kartasi qoldi.
    }
scheduling-congratulations-finished = Tabriklaymiz! Hozircha bu dastani tugatdingiz.
scheduling-today-review-limit-reached = Bugungi takrorlash limitidan oshib ketdingiz, lekin hali takrorlanishi kutilayotgan kartalar bor. Optimal xotira uchun kunlik limitni parametrlarda oshirish haqida oʻylab koʻring.
scheduling-today-new-limit-reached = Hali yangi kartalar mavjud, ammo kunlik limitga erishildi. Parametrlarda limitni oshirishingiz mumkin, lekin shuni yodda tutingki, qancha koʻp yangi kartalar qoʻshsangiz, qisqa muddatli takrorlash yuklamangiz shunchalik yuqori boʻladi.
scheduling-buried-cards-found = Bir yoki bir nechta kartalar koʻmilgan va ertaga koʻrsatiladi. Agar ularni darhol koʻrmoqchi boʻlsangiz, { $unburyThem }ingiz mumkin.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = koʻmishni bekor qilish
scheduling-how-to-custom-study = Odatdagi kun tartibingizdan tashqari oʻrganmoqchi boʻlsangiz, { $customStudy } funksiyasini ishlatishingiz mumkin.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = maxsus oʻrganish

## Scheduler upgrade

scheduling-update-soon = Anki 2.1 dagi yangi rejalashtiruvchi oldingi Anki versiyalarida mavjud boʻlgan bir qator muammolarni hal qiladi. Yangilash tavsiya etiladi.
scheduling-update-done = Rejalashtiruvchi muvaffaqiyatli yangilandi.
scheduling-update-button = Yangilash
scheduling-update-later-button = Keyinroq
scheduling-update-more-info-button = Batafsil
scheduling-update-required =
    Kolleksiyangiz V2 rejalashtiruvchisiga yangilanishi kerak.
    Davom etishdan oldin { scheduling-update-more-info-button } ni tanlang.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = Audioni qayta tinglashda har doim savol tomoni bilan tinglash
scheduling-at-least-one-step-is-required = Kamida bitta bosqich kerak.
scheduling-automatically-play-audio = Audio avtomatik tarzda ijro etilsin
scheduling-bury-related-new-cards-until-the = Aloqador yangi kartalarni keyingi kungacha koʻmish
scheduling-bury-related-reviews-until-the-next = Aloqador takrorlash kartalarini keyingi kungacha koʻmish
scheduling-days = kun
scheduling-description = Tavsif
scheduling-easy-bonus = Osonlik bonusi
scheduling-easy-interval = Oson interval
scheduling-end = (oxiri)
scheduling-general = Umumiy
scheduling-graduating-interval = Bitiriuv intervali
scheduling-hard-interval = Qiyin interval
scheduling-ignore-answer-times-longer-than = Shu vaqtdan oshib ketdan javoblar inobatga olinmasin
scheduling-interval-modifier = Interval oʻzgartiruvchisi
scheduling-lapses = Unutilishlar
scheduling-lapses2 = unutishlar
scheduling-learning = Oʻrganilmoqda
scheduling-leech-action = Yopishqoq karta amali
scheduling-leech-threshold = Yopishqoq karta chegarasi
scheduling-maximum-interval = Maksimal interval
scheduling-maximum-reviewsday = Kuniga eng koʻp takrorlashlar
scheduling-minimum-interval = Minimal interval
scheduling-mix-new-cards-and-reviews = Yangi kartalarni takrorlashlanadiganlar bilan aralashtirish
scheduling-new-cards = Yangi kartalar
scheduling-new-cardsday = Yangi kartalar/kun
scheduling-new-interval = Yangi interval
scheduling-new-options-group-name = Yangi parametrlar guruhi nomi:
scheduling-options-group = Parametrlar guruhi:
scheduling-order = Tartib
scheduling-parent-limit = (Ona dasta limiti: { $val })
scheduling-reset-counts = Takrorlashlar va unutilishlar sonini tiklash
scheduling-restore-position = Iloji boʻlsa, original oʻrnini tiklash
scheduling-review = Takrorlash
scheduling-reviews = Takrorlashlar
scheduling-seconds = soniya
scheduling-set-all-decks-below-to = { $val } ostidagi barcha dastalar shu parametrlar guruhiga sozlansinmi?
scheduling-set-for-all-subdecks = Barcha ichki dastalar uchun sozlash
scheduling-show-answer-timer = Javob taymerini koʻrsatish
scheduling-show-new-cards-after-reviews = Yangi kartalarni takrorlashdan keyin koʻrsatish
scheduling-show-new-cards-before-reviews = Yangi kartalarni takrorlashdan oldin koʻrsatish
scheduling-show-new-cards-in-order-added = Yangi kartalarni qoʻshilgan tartibi boʻyicha koʻrsatish
scheduling-show-new-cards-in-random-order = Yangi kartalarni tasodifiy tartibda koʻrsatish
scheduling-starting-ease = Boshlangʻich osonlik
scheduling-steps-in-minutes = Bosqichlar (daqiqalarda)
scheduling-steps-must-be-numbers = Bosqichlar son boʻlishi kerak.
scheduling-tag-only = Faqat teg qoʻyish
scheduling-the-default-configuration-cant-be-removed = Birlamchi konfiguratsiyani oʻchiribi boʻlmaydi.
scheduling-your-changes-will-affect-multiple-decks = Oʻzgartirishlaringiz bir nechta dastaga taʼsir qiladi. Agar faqat joriy dastani oʻzgartirmoqchi boʻlsangiz, avval yangi parametrlar guruhini qoʻshing.
scheduling-deck-updated =
    { $count ->
        [one] { $count } ta dasta yangilandi
       *[other] { $count } ta dasta yangilandi
    }
scheduling-set-due-date-prompt =
    { $cards ->
        [one] Karta nechi kundan keyin koʻrsatilsin?
       *[other] Kartalar nechi kundan keyin koʻrsatilsin?
    }
scheduling-set-due-date-prompt-hint =
    0 = bugun
    1! = ertaga + intervalni 1 ga oʻzgartirish
    3-7 = 3-7 kun orasidagi tasodifiy kun
scheduling-set-due-date-done =
    { $cards ->
        [one] { $cards } ta karta muddati belgilandi.
       *[other] { $cards } ta karta muddati belgilandi.
    }
scheduling-graded-cards-done =
    { $cards ->
        [one] { $cards } ta karta baholandi.
       *[other] { $cards } ta karta baholandi.
    }
scheduling-forgot-cards =
    { $cards ->
        [one] { $cards } ta karta qayta tiklandi.
       *[other] { $cards } ta karta qayta tiklandi.
    }
