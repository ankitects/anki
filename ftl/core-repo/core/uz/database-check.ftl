database-check-corrupt = Kolleksiya fayli buzilgan. Avtomatik zaxiradan qayta tiklang.
database-check-rebuilt = Maʼlumotlar bazasi qayta qurildi va optimallashtirildi.
database-check-card-properties =
    { $count ->
        [one] { $count } ta yaroqsiz karta xususiyatlari tuzatildi.
       *[other] { $count } ta yaroqsiz karta xususiyatlari tuzatildi.
    }
database-check-card-last-review-time-empty =
    { $count ->
        [one] { $count } ta kartaga oxirgi takrorlash vaqti  qoʻshildi.
       *[other] { $count } ta kartaga oxirgi takrorlash vaqti  qoʻshildi.
    }
database-check-missing-templates =
    { $count ->
        [one] Shabloni yoʻq { $count } ta karta oʻchirildi.
       *[other] Shabloni yoʻq { $count } ta karta oʻchirildi.
    }
database-check-field-count =
    { $count ->
        [one] Notoʻgʻri maydonlar soniga ega { $count } ta qayd tuzatildi.
       *[other] Notoʻgʻri maydonlar soniga ega { $count } ta qayd tuzatildi.
    }
database-check-new-card-high-due =
    { $count ->
        [one] Muddati >= 1,000,000 boʻlgan { $count } ta yangi karta topildi. Ularni oʻrnilarini koʻrib chiqish oynasida oʻzgartirganiz maʼqul.
       *[other] { "" }
    }
database-check-card-missing-note =
    { $count ->
        [one] { $count } ta qaydsiz karta oʻchirildi.
       *[other] { $count } ta qaydsiz karta oʻchirildi.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] Shabloni takrorlangan { $count } ta karta oʻchirildi.
       *[other] Shabloni takrorlangan { $count } ta karta oʻchirildi.
    }
database-check-missing-decks =
    { $count ->
        [one] { $count } ta yoʻq dasta tuzatildi.
       *[other] { $count } ta yoʻq dasta tuzatildi.
    }
database-check-revlog-properties =
    { $count ->
        [one] Xususiyatli yaroqsiz { $count } ta takrorlash yozuvlari tuzatildi.
       *[other] Xususiyatli yaroqsiz { $count } ta takrorlash yozuvlari tuzatildi.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] Yaroqsiz utf8 belgilari bor { $count } ta qayd tuzatildi.
       *[other] Yaroqsiz utf8 belgilari bor { $count } ta qayd tuzatildi.
    }
database-check-fixed-invalid-ids =
    { $count ->
        [one] Vaqti belgisi kelajakda boʻlgan  { $count } ta obyekt tuzatildi.
       *[other] Vaqti belgisi kelajakda boʻlgan  { $count } ta obyekt tuzatildi.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Bir yoki bir nechta qayd turlari yoʻq. Ulardan foydalangan qaydlarga "db-check" bilan boshlangan yangi qayd turlari berildi, ammo maydon nomlari va kartalar tuzilishi yoʻqolgan, shuning uchun avtomatik zaxiradan qayta tiklaganingiz maʼqul.

## Progress info

database-check-checking-integrity = Kolleksiya tekshirilmoqda...
database-check-rebuilding = Qayta qurilmoqda...
database-check-checking-cards = Kartalar tekshirilmoqda...
database-check-checking-notes = Qaydlar tekshirilmoqda...
database-check-checking-history = Tarix tekshirilmoqda...
database-check-title = Maʼlumotlar bazasini tekshirish
