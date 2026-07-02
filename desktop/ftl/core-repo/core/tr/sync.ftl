### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = Eklendi: { $up }↑ { $down }↓
sync-media-removed-count = Kaldırıldı: { $up }↑ { $down }↓
sync-media-checked-count = Kontrol Edildi: { $count }
sync-media-starting = Medya senkronizasyonu başlıyor...
sync-media-complete = Medya senkronizasyonu tamamlandı.
sync-media-failed = Medya senkronizasyonu başarısız.
sync-media-aborting = Medya senkronizasyonu iptal ediliyor...
sync-media-aborted = Medya senkronizasyonu iptal edildi.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = Medya senkronizasyonu devre dışı.
# Title of the screen that shows syncing progress history
sync-media-log-title = Medya Senkronizasyon Günlüğü

## Error messages / dialogs

sync-conflict = Hesabınıza Anki'nin sadece bir kaydı aynı anda senkronize edilebilir. Lütfen birkaç dakika bekleyin, sonra yeniden deneyin.
sync-server-error = AnkiWeb bir sorunla karşılaştı. Lütfen birkaç dakika sonra yeniden deneyin.
sync-client-too-old = Anki versiyonun çok eski. Senkronize etmeye devam etmek için lütfen en son versiyona güncelleyin.
sync-wrong-pass = AnkiWeb kullanıcı adı ya da parolası hatalıydı, lütfen tekrar deneyin.
sync-resync-required = Lütfen yeniden senkronize edin. Eğer bu mesaj hâlâ devam ediyorsa, destek sitesine paylaşın.
sync-must-wait-for-end = Anki şu an senkronize ediyor. Lütfen senkronizenin tamamlanmasını bekleyin, sonra yeniden deneyin.
sync-confirm-empty-download = Yerel koleksiyonun kartları yok. AnkiWeb'den indirilsin mi?
sync-confirm-empty-upload = AnkiWeb'deki koleksiyonun kartları yok. Yerel koleksiyonla değiştirelim mi?
sync-conflict-explanation =
    Buradaki ve AnkiWeb'deki desteleriniz, birleştirilemeyecek kadar farklı olduğundan, bir taraftaki destelerin diğer taraftaki destelerle değiştirilmesi gerekiyor.
    
    İndirmeyi seçerseniz, Anki koleksiyonu AnkiWeb'den alır ve son senkronizasyondan beri bu cihazda yaptığınız tüm değişiklikler kaybolur.
    
    Yüklemeyi seçerseniz, Anki bu cihazın verilerini AnkiWeb'e gönderir ve AnkiWeb'de bekleyen tüm değişiklikler kaybolur.
    
    Tüm cihazlar senkronize olduktan sonra, gelecekteki gözden geçirmeler ve eklenen kartlar otomatikman birleştirilebilir.
sync-conflict-explanation2 =
    Bu cihazdaki ve AnkiWeb’deki desteler arasında bir çakışma var. Hangi sürümü koruyacağınızı seçmelisiniz:
    
    - Buradaki desteleri AnkiWeb sürümüyle değiştirmek için **{ sync-download-from-ankiweb }** ögesini seçin. Son senkronizasyonunuzdan beri bu cihazda yaptığınız tüm değişiklikleri kaybedeceksiniz.
    - Bu cihazdaki desteleri AnkiWeb sürümlerinin üzerine yazmak ve AnkiWeb'deki tüm değişiklikleri silmek için **{ sync-upload-to-ankiweb }** ögesini seçin.
    
     Çakışma çözüldükten sonra senkronizasyon her zamanki gibi çalışacaktır.
sync-ankiweb-id-label = Kullanıcı Adı:
sync-password-label = Parola:
sync-account-required =
    <h1>Hesap Gerekli</h1>
    Koleksiyonunuzun eşitlenmesi için ücretsiz hesap açmanız gerekiyor.  Lütfen  <a href="{ $link }">hesap açın</a> ve bilgilerinizi yazın.
sync-sanity-check-failed = Lütfen "Veritabanını Kontrol Et" işlevini kullanın ve ardından tekrar senkronize edin. Sorun devam ederse, lütfen tercihler ekranından tek yönlü senkronizasyonu zorlayın.
sync-clock-off = Senkronize edilemedi - sistem saatin doğru saate ayarlanmamış.
sync-sign-in = Oturum aç
sync-ankihub-dialog-heading = AnkiHub Girişi
sync-ankihub-username-label = Kullanıcı adı veya E-posta
sync-ankihub-login-failed = Sağlanan kimlik bilgileriyle AnkiHub'a giriş yapılamıyor.
sync-ankihub-addon-installation = AnkiHub Eklenti Kurulumu

## Buttons

sync-media-log-button = Medya Günlüğü
sync-abort-button = İptal Et
sync-download-from-ankiweb = AnkiWeb'den İndir
sync-upload-to-ankiweb = AnkiWeb'e Yükle
sync-cancel-button = İptal

## Normal sync progress

sync-downloading-from-ankiweb = AnkiWeb'den indiriliyor...
sync-uploading-to-ankiweb = AnkiWeb'e yükleniyor...
sync-syncing = Eşitleniyor...
sync-checking = Kontrol ediliyor...
sync-connecting = Bağlantı kuruluyor...
sync-added-updated-count = Eklendi/düzenlendi: { $up }↑ { $down }↓
sync-log-in-button = Giriş Yap
sync-log-out-button = Çıkış Yap
sync-collection-complete = Koleksiyon senkronizesi tamamlandı.
