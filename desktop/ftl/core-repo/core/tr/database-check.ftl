database-check-corrupt = Koleksiyon dosyası bozuk. Lütfen otomatik bir yedeklemeden geri yükleyin.
database-check-rebuilt = Veritabanı yeniden oluşturuldu ve optimize edildi.
database-check-card-properties =
    { $count ->
        [one] { $count } hatalı kart özelliği düzeltildi.
       *[other] { $count } hatalı kart özelliği düzeltildi.
    }
database-check-card-last-review-time-empty =
    { $count ->
        [one] { $count } karta son gözden geçirme tarihi eklendi.
       *[other] { $count } karta son gözden geçirme tarihi eklendi.
    }
database-check-missing-templates =
    { $count ->
        [one] Şablonu olmayan { $count } kart silindi.
       *[other] Şablonu olmayan { $count } kart silindi.
    }
database-check-field-count =
    { $count ->
        [one] Yanlış alan sayısına sahip { $count } not düzeltildi.
       *[other] Yanlış alan sayısına sahip { $count } not düzeltildi.
    }
database-check-new-card-high-due =
    { $count ->
        [one] Vadesi >= 1,000,000 olan { $count } kart bulundu. - bu kartı Göz At sekmesinde yeniden konumlandırın.
       *[other] Vadesi >= 1,000,000 olan { $count } kart bulundu. - bu kartları Göz At sekmesinde yeniden konumlandırın.
    }
database-check-card-missing-note =
    { $count ->
        [one] Notu olmayan { $count } kart silindi.
       *[other] Notu olmayan { $count } kart silindi.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] Tekrarlayan şablonu olan { $count } kart silindi.
       *[other] Tekrarlayan şablonu olan { $count } kart silindi.
    }
database-check-missing-decks =
    { $count ->
        [one] { $count } eksik deste düzeltildi.
       *[other] { $count } eksik deste düzeltildi.
    }
database-check-revlog-properties =
    { $count ->
        [one] Hatalı özelliği bulunan { $count } gözden geçirme girişi düzeltildi.
       *[other] Hatalı özelliği bulunan { $count } gözden geçirme girişi düzeltildi.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] Hatalı utf8 karakterleri içeren { $count } not düzeltildi.
       *[other] Hatalı utf8 karakterleri içeren { $count } not düzeltildi.
    }
database-check-fixed-invalid-ids =
    { $count ->
        [one] Zaman damgası gelecekte bulunan { $count } obje düzeltildi.
       *[other] Zaman damgası gelecekte bulunan { $count } obje düzeltildi.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Bir veya daha fazla not türü eksik. Bunu kullanan notlara "db-check" ile başlayan yeni not türleri atandı, ancak alan adları ve kart tasarımı kaybolduğu için otomatik bir yedeklemeden geri yüklemeniz daha iyi olabilir.

## Progress info

database-check-checking-integrity = Koleksiyon kontrol ediliyor...
database-check-rebuilding = Yeniden oluşturuyor...
database-check-checking-cards = Kartlar kontrol ediliyor...
database-check-checking-notes = Notlar kontrol ediliyor...
database-check-checking-history = Geçmiş kontrol ediliyor...
database-check-title = Veritabanını Kontrol Et
