## Shown at the top of the media check screen

media-check-window-title = Ortamı Kontrol Et
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    { $count ->
        [one] Çöp kutusu: { $count } dosya, { $megs } MB
       *[other] Çöp kutusu: { $count } dosya, { $megs } MB
    }
media-check-missing-count = Kayıp dosyalar: { $count }
media-check-unused-count = Kullanılmayan dosyalar: { $count }
media-check-renamed-count = Yeniden adlandırılan dosyalar: { $count }
media-check-oversize-count = 100 MB'den fazla: { $count }
media-check-subfolder-count = Alt klasörler: { $count }

## Shown at the top of each section

media-check-renamed-header = Bazı dosyalar uyumluluk için yeniden adlandırıldı.
media-check-oversize-header = 100 MB'ın üzerindeki dosyalar AnkiWeb ile senkronize edilemez.
media-check-subfolder-header = Ortam klasörünün içindeki klasörler desteklenmiyor.
media-check-unused-header = Aşağıdaki dosyalar medya klasöründe bulundu, ancak hiçbir kartta kullanılmıyor gibi görünüyor:

## Shown once for each file

media-check-renamed-file = Yeniden adlandırıldı: { $old } -> { $new }
media-check-oversize-file = 100 MB'den fazla: { $filename }
media-check-subfolder-file = Klasör: { $filename }
media-check-missing-file = Kayıp: { $filename }
media-check-unused-file = Kullanılmayan: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = { $count } Adet Kontrol Edildi...

## Deleting unused media

media-check-delete-unused-confirm = Kullanılmayan ortamlar silinsin mi?
media-check-files-remaining =
    { $count ->
        [one] { $count } dosya kaldı.
       *[other] { $count } dosya kaldı.
    }
media-check-delete-unused-complete =
    { $count ->
        [one] { $count } dosya çöp kutusuna taşındı.
       *[other] { $count } dosya çöp kutusuna taşındı.
    }
media-check-trash-emptied = Çöp kutusu boş.
media-check-trash-restored = Silinen dosyalar medya klasörüne geri yüklendi.

## Rendering LaTeX

media-check-all-latex-rendered = Tüm LaTeX oluşturuldu.

## Buttons

media-check-delete-unused = Kullanılmayanları Sil
media-check-render-latex = LaTeX'i Oluştur
# button to permanently delete media files from the trash folder
media-check-empty-trash = Çöp Kutusunu Boşalt
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Silinenleri Geri Yükle
media-check-check-media-action = Ortamı Kontrol Et
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = kayıp-medya
