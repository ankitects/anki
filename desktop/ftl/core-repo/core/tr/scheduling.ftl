## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount }sn
scheduling-answer-button-time-minutes = { $amount }dk
scheduling-answer-button-time-hours = { $amount }sa
scheduling-answer-button-time-days = { $amount }g
scheduling-answer-button-time-months = { $amount }ay
scheduling-answer-button-time-years = { $amount }y

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] { $amount } saniye
       *[other] { $amount } saniye
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] { $amount } dakika
       *[other] { $amount } dakika
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } saat
       *[other] { $amount } saat
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } gün
       *[other] { $amount } gün
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } ay
       *[other] { $amount } ay
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } yıl
       *[other] { $amount } yıl
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    { $unit ->
        [seconds]
            { $amount ->
                [one] Bir sonraki öğrenme kartı { $amount } saniye içinde hazır olacak.
               *[other] Bir sonraki öğrenme kartı { $amount } saniye içinde hazır olacak.
            }
        [minutes]
            { $amount ->
                [one] Bir sonraki öğrenme kartı { $amount } dakika içinde hazır olacak.
               *[other] Bir sonraki öğrenme kartı { $amount } dakika içinde hazır olacak.
            }
       *[hours]
            { $amount ->
                [one] Bir sonraki öğrenme kartı { $amount } saat içinde hazır olacak.
               *[other] Bir sonraki öğrenme kartı { $amount } saat içinde hazır olacak.
            }
    }
scheduling-learn-remaining =
    { $remaining ->
        [one] Bugün daha sonra sırası gelecek bir kart var.
       *[other] Bugün daha sonra sırası gelecek { $remaining } kart var.
    }
scheduling-congratulations-finished = Tebrikler! Bu desteyi şimdilik tamamladınız.
scheduling-today-review-limit-reached =
    Bugünün gözden geçirme sınırına ulaşıldı, ancak hâlâ gözden
    geçirilmeyi bekleyen kartlar var. Optimum hafıza için,
    seçeneklerdeki günlük sınırı arttırmayı düşünün.
scheduling-today-new-limit-reached =
    Daha fazla yeni kartlar mevcut, ama günlük sınıra ulaşıldı. 
    Sınırı seçeneklerde arttırabilirsiniz, ama lütfen aklında tutun ki 
    daha fazla yeni kart tanıtıldıkça, kısa süreli gözden geçirme 
    iş yükünüz artacak.
scheduling-buried-cards-found = Bir veya daha fazla kart gömüldü, yarın da gösterilecek. Onları hemen görmek istiyorsanız, { $unburyThem }abilirsiniz.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = gömmeyi geri al
scheduling-how-to-custom-study = Olağan planın dışında çalışmak istiyorsanız, { $customStudy } özelliğini kullanabilirsiniz.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = özel çalışma

## Scheduler upgrade

scheduling-update-soon = Anki 2.1, önceki Anki sürümlerinde bulunan bazı sorunları gideren yeni bir zamanlayıcı içeriyor. Güncellemeniz önerilir.
scheduling-update-done = Planlayıcı başarıyla güncellendi.
scheduling-update-button = Güncelleştir
scheduling-update-later-button = Daha sonra
scheduling-update-more-info-button = Daha Fazlasını Öğren

## Other scheduling strings

scheduling-at-least-one-step-is-required = En az bir adım gereklidir.
scheduling-automatically-play-audio = Sesi otomatik olarak çal
scheduling-bury-related-new-cards-until-the = İlgili yeni kartları ertesi güne kadar göm
scheduling-bury-related-reviews-until-the-next = İlgili gözden geçirmeleri ertesi güne kadar göm
scheduling-days = günler
scheduling-description = Açıklama
scheduling-easy-bonus = Kolay ikramiye
scheduling-easy-interval = Kolay aralık
scheduling-end = (son)
scheduling-general = Genel
scheduling-graduating-interval = Terfi sonrası aralık
scheduling-hard-interval = Zor aralık
scheduling-ignore-answer-times-longer-than = Cevap süresinden uzun olanları yok say
scheduling-interval-modifier = Aralık ayarlayıcı
scheduling-lapses = Hatalar
scheduling-lapses2 = hatalar
scheduling-learning = Öğrenme
scheduling-leech-action = Sömürü hareketi
scheduling-leech-threshold = Sömürü eşiği
scheduling-maximum-interval = Maksimum aralık
scheduling-maximum-reviewsday = En fazla gözden geçirme/gün
scheduling-minimum-interval = Minimum aralık
scheduling-mix-new-cards-and-reviews = Yeni kartları ve gözden geçirmeleri karıştır
scheduling-new-cards = Yeni Kartlar
scheduling-new-cardsday = Yeni kartlar/gün
scheduling-new-interval = Yeni aralık
scheduling-new-options-group-name = Yeni seçenekler grubu adı:
scheduling-options-group = Seçenekler grubu:
scheduling-order = Sıralama
scheduling-parent-limit = (üst sınır: { $val })
scheduling-review = Gözden Geçir
scheduling-reviews = Gözden Geçirmeler
scheduling-seconds = saniye
scheduling-set-for-all-subdecks = Tüm alt destler için uygula
scheduling-show-answer-timer = Cevap zamanını göster
scheduling-show-new-cards-after-reviews = Yeni kartları gözden geçirmelerden sonra göster
scheduling-show-new-cards-before-reviews = Yeni kartları gözden geçirmelerden önce göster
scheduling-show-new-cards-in-order-added = Yeni kartları eklendiği sırada göster
scheduling-show-new-cards-in-random-order = Yeni kartları rastgele sırada göster
scheduling-starting-ease = Başlangıç kolaylığı
scheduling-steps-in-minutes = Adımlar (dakikada)
scheduling-steps-must-be-numbers = Adımlar sayı olmalıdır.
scheduling-tag-only = Sadece Etiket
scheduling-the-default-configuration-cant-be-removed = Varsayılan yapılandırma kaldırılamaz.
scheduling-deck-updated =
    { $count ->
        [one] { $count } deste güncellendi.
       *[other] { $count } deste güncellendi.
    }
scheduling-forgot-cards =
    { $cards ->
        [one] { $cards } kart unutuldu.
       *[other] { $cards } kart unutuldu.
    }
