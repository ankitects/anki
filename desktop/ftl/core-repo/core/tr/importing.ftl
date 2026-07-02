importing-failed-debug-info = İçe aktarma gerçekleştirilemedi. Hata ayıklama bilgisi:
importing-aborted = İptal Edildi: { $val }
importing-added-duplicate-with-first-field = İlk alanın aynısı eklendi: { $val }
importing-all-supported-formats = Desteklenen tüm formatlar { $val }
importing-allow-html-in-fields = Alanlarda HTML kodlarına izin ver
importing-anki-files-are-from-a-very = .anki dosyaları, Anki'nin çok eski bir sürümünden kalma. Bu dosyaları 175027074 numaralı eklentiyle veya Anki web sitesinde bulunan Anki 2.0 ile içe aktarabilirsiniz.
importing-anki2-files-are-not-directly-importable = .anki2 dosyaları doğrudan içe aktarılamaz - lütfen bunun yerine size verilen .apkg veya .zip dosyasını içe aktarın.
importing-appeared-twice-in-file = Dosya { $val } içinde çifte giriş var.
importing-by-default-anki-will-detect-the = Varsayılan, Anki karakter arasında boş alan tespit ettiğinde, örneğin tab, virgül vb. Eğerr Anki yanlış karakter tespit ettiyse buraya girebilirsiniz. tab tuşunu \t ile kullanın.
importing-change = Değiştir
importing-colon = İki nokta üst üste
importing-comma = Virgül
importing-empty-first-field = İlk alanı boşalt  :{ $val }
importing-field-separator = Alan ayırıcı
importing-field-separator-guessed = Alan ayırıcı (tahmini)
importing-field-mapping = Alan eşleştirme
importing-field-of-file-is = Dosyanın  <b>{ $val }</b> alanı
importing-fields-separated-by = İle ayrılmış alanlar: { $val }
importing-file-version-unknown-trying-import-anyway = Dosya sürümü bilinmiyor, yine de içe aktarılmaya çalışılıyor.
importing-first-field-matched = İlk alan eşleşti: { $val }
importing-identical = Özdeş
importing-ignore-field = Alanı gözardı et.
importing-ignore-lines-where-first-field-matches = İlk alanı mevcut not ile eşleşen satırları yok say
importing-ignored = <yoksay>
importing-import-even-if-existing-note-has = Mevcut not aynı ilk alana sahip olmasına rağmen içe aktar
importing-import-options = İçe aktarma seçenekleri
importing-importing-complete = İçe aktarma tamamlandı.
importing-invalid-file-please-restore-from-backup = Geçersiz dosya. Lütfen yedekten yükleyin.
importing-map-to = Eşle { $val }
importing-map-to-tags = Etiketleri Eşle
importing-mapped-to = eşlenmiş <b>{ $val }</b>
importing-mapped-to-tags = Eşlenmiş <b>Etiketler</b>
# the action of combining two existing note types to create a new one
importing-merge-notetypes = Not türlerini birleştir
importing-mnemosyne-20-deck-db = Mnemosyne 2.0 Deste (*.db)
importing-multicharacter-separators-are-not-supported-please = Çok karakterli ayırıcılar desteklenmiyor. Lütfen yalnızca bir karakter girin.
importing-new-deck-will-be-created = Yeni bir deste oluşturulacak: { $name }
importing-notes-added-from-file = Dosyadan eklenen notlar: { $val }
importing-notes-found-in-file = Dosyada bulunan notlar: { $val }
importing-notes-updated-as-file-had-newer = Dosya daha yeni bir sürüme sahip olduğu için notlar güncellendi: { $val }
importing-include-reviews = Gözden geçirmeleri dahil et
importing-with-deck-configs = Deste ön ayarlarını içe aktar
importing-updates = Güncellemeler
importing-packaged-anki-deckcollection-apkg-colpkg-zip = Paketlenmiş Anki Destesi/Koleksiyonu (*.apkg *.colpkg *.zip)
# the '|' character
importing-pipe = Dikey çizgi
importing-rows-had-num1d-fields-expected-num2d = '{ $row }' içinde { $found } alan vardı, { $expected } beklendi
importing-selected-file-was-not-in-utf8 = Seçilen dosya UTF-8 biçiminde değildi. Lütfen kılavuzdaki içe aktarma bölümüne bakın.
importing-semicolon = Noktalı Virgül
importing-tab = Sekme
importing-text-separated-by-tabs-or-semicolons = Sekmeler veya noktalı virgüllerle ayırılmış metin (*)
importing-the-first-field-of-the-note = Not türünün ilk alanı eşlenmelidir.
importing-the-provided-file-is-not-a = Girdiğiniz dosya geçerli bir .apkg dosyası değil.
importing-this-file-does-not-appear-to = Bu dosya geçerli bir .apkg dosyası gibi görünmüyor. Bu hatayı AnkiWeb'den indirilen bir dosyadan alıyorsanız, indirme başarısız olmuş olabilir. Lütfen tekrar deneyin ve sorun devam ederse lütfen farklı bir tarayıcıyla yeniden deneyin.
importing-this-will-delete-your-existing-collection = Bu, mevcut koleksiyonunuzu siler ve içe aktardığınız dosyadaki verilerle değiştirir. Emin misiniz?
importing-unable-to-import-from-a-readonly = Salt okunur bir dosyadan içe aktarılamıyor.
importing-unknown-file-format = Bilinmeyen dosya biçimi.
importing-update-always = Her Zaman
importing-update-never = Hiçbir Zaman
importing-update-notes = Notları güncelle
importing-update-notes-help = Koleksiyonunuzdaki mevcut bir notu ne zaman güncellemelisiniz? Varsayılan olarak, bu yalnızca eşleşen içe aktarılan not yakın zamanda değiştirilmişse yapılır.
importing-update-notetypes = Not türlerini güncelle
importing-note-added =
    { $count ->
        [one] { $count } not eklendi
       *[other] { $count } not eklendi
    }
importing-note-imported =
    { $count ->
        [one] { $count } not içe aktarıldı.
       *[other] { $count } not içe aktarıldı.
    }
importing-note-unchanged =
    { $count ->
        [one] { $count } not değişmedi
       *[other] { $count } notlar değişmedi
    }
importing-note-updated =
    { $count ->
        [one] { $count } not güncellendi.
       *[other] { $count } not güncellendi.
    }
importing-processed-media-file =
    { $count ->
        [one] { $count } ortam dosyası içe aktarıldı.
       *[other] { $count } ortam dosyası içe aktarıldı.
    }
importing-importing-file = Dosya içe aktarılıyor…
importing-extracting = Veriler ayıklanıyor…
importing-gathering = Veri toplanıyor…
importing-failed-to-import-media-file = Medya dosyası içe aktarılamadı: { $debugInfo }
importing-processed-notes =
    { $count ->
        [one] { $count } not işlendi…
       *[other] { $count } not işlendi…
    }
importing-processed-cards =
    { $count ->
        [one] { $count } kart işlendi…
       *[other] { $count } kart işlendi…
    }
importing-existing-notes = Mevcut notlar
# "Existing notes: Duplicate" (verb)
importing-duplicate = Kopya oluştur
# "Existing notes: Preserve" (verb)
importing-preserve = Koru
# "Existing notes: Update" (verb)
importing-update = Güncelle
importing-tag-all-notes = Tüm notları etiketle
importing-tag-updated-notes = Güncellenen notları etiketle
importing-file = Dosya
# "Match scope: notetype / notetype and deck". Controls how duplicates are matched.
importing-match-scope = Eşleşme kapsamı
# Used with the 'match scope' option
importing-notetype-and-deck = Not türü ve deste
importing-cards-added =
    { $count ->
        [one] { $count } kart eklendi.
       *[other] { $count } kart eklendi.
    }
importing-file-empty = Seçtiğiniz dosya boş.
importing-notes-added =
    { $count ->
        [one] { $count } yeni not içe aktarıldı.
       *[other] { $count } yeni not içe aktarıldı.
    }
importing-notes-updated =
    { $count ->
        [one] Mevcut notları güncellemek için { $count } adet not kullanıldı.
       *[other] Mevcut notları güncellemek için { $count } adet not kullanıldı.
    }
importing-existing-notes-skipped =
    { $count ->
        [one] { $count } not koleksiyonunuzda zaten mevcut.
       *[other] { $count } not koleksiyonunuzda zaten mevcut.
    }
importing-notes-failed =
    { $count ->
        [one] { $count } not içe aktarılamadı.
       *[other] { $count } not içe aktarılamadı.
    }
importing-conflicting-notes-skipped =
    { $count ->
        [one] { $count } adet not, türü değiştiği için içe aktarılamadı.
       *[other] { $count } adet not, türleri değiştiği için içe aktarılamadı.
    }
importing-import-log = İçeri Aktarma Günlüğü
importing-no-notes-in-file = Dosyada not bulunamadı.
importing-notes-found-in-file2 =
    { $notes ->
        [one] Dosyada { $notes } not bulundu. Bunlar:
       *[other] Dosyada { $notes } not bulundu. Bunlar:
    }
importing-show = Göster
importing-details = Ayrıntılar
importing-status = Durum
importing-duplicate-note-added = Yinelenen not eklendi
importing-added-new-note = Yeni not eklendi
importing-existing-note-skipped = Güncel bir kopya zaten koleksiyonunuzda bulunduğundan not atlandı
importing-note-updated-as-file-had-newer = Dosya daha yeni bir sürüme sahip olduğu için not güncellendi
importing-note-skipped-due-to-empty-first-field = İlk alanı boş olduğu için not atlandı
importing-deck-help = İçe aktarılan kartlar bu desteye yerleştirilecek.
importing-existing-notes-help =
    İçe aktarılan bir not mevcut bir notla çakışırsa ne yapılmalı?
    
    - `{ importing-update }`: Mevcut notu güncelle.
    - `{ importing-preserve }`: Hiçbir şey yapma.
    - `{ importing-duplicate }`: Yeni bir not oluştur.
importing-tag-all-notes-help = Bu etiketler hem yeni içe aktarılan hem de güncellenen notlara eklenecektir.
importing-tag-updated-notes-help = Bu etiketler güncellenen notlara eklenecektir.
importing-overview = Genel Bakış

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

importing-importing-collection = Koleksiyon içeri aktarılıyor...
importing-unable-to-import-filename = { $filename } içe aktarılamadı: Dosya türü desteklenmiyor
importing-added = Eklendi
importing-pauker-18-lesson-paugz = Pauker 1.8 Dersi (*.pau.gz)
importing-supermemo-xml-export-xml = Supermemo XML dışa aktarma (*.xml)
