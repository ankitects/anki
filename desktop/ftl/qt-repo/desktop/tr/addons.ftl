addons-possibly-involved = Dâhil olmuş olabilen eklentiler: { $addons }
addons-failed-to-load =
    Yüklediğiniz bir eklenti yüklenemedi. Sorunlar devam ederse, lütfen Araçlar>Eklentiler menüsüne gidin ve eklentiyi devre dışı bırakın veya silin.
    
    '{ $name }' yüklendiğinde:
    { $traceback }
addons-failed-to-load2 =
    Aşağıdaki eklentiler yüklenemedi:
    { $addons }
    
    Anki'nin bu sürümünü desteklemek için bunları güncellemeniz gerekebilir.
    Bir güncelleştirmenin mevcut olup olmadığına bakmak için { addons-check-for-updates } düğmesine tıklayın.
    
    Eklenti yazarına bir rapora yapıştırabileceğiniz bilgileri almak için 
    { about-copy-debug-info } düğmesini kullanabilirsiniz.
    
    Eklenti için bir güncelleştirme mevcut değilse, 
    bu mesajın görünmesini önlemek için eklentiyi devre dışı bırakabilir veya silebilirsiniz.
addons-startup-failed = Eklenti Başlaması Başarısız Oldu
# Shown in the add-on configuration screen (Tools>Add-ons>Config), in the title bar
addons-config-window-title = '{ $name }' yapılandır
addons-config-validation-error = Sağlanan yapılandırmayla ilgili bir sorun vardı: { $problem }, { $path } konumunda, { $schema } şemasına karşı.
addons-window-title = Eklentiler
addons-addon-has-no-configuration = Eklentinin yapılandırması yok.
addons-addon-installation-error = Eklenti yükleme hatası
addons-browse-addons = Eklentilere Göz At
addons-changes-will-take-effect-when-anki = Değişiklikler Anki yeniden başlatıldığında etkili olacaktır.
addons-check-for-updates = Güncellemeleri kontrol et
addons-checking = Kontrol ediliyor...
addons-code = Kod:
addons-config = Yapılandırma
addons-configuration = Yapılandırma
addons-corrupt-addon-file = Bozuk eklenti dosyası.
addons-disabled = (devre dışı bırakıldı)
addons-disabled2 = (devre dışı bırakıldı)
addons-download-complete-please-restart-anki-to = İndirme tamamlandı. Değişiklikleri uygulamak için lütfen Anki'yi yeniden başlatınız.
addons-downloaded-fnames = { $fname } indirildi
addons-downloading-adbd-kb02fkb = İndiriliyor { $part }/{ $total } ({ $kilobytes }KB)...
addons-error-downloading-ids-errors = <i>{ $id }</i> indirilirken hata oluştu: { $error }
addons-error-installing-bases-errors = <i>{ $base }</i> yüklenirken hata oluştu: { $error }
addons-get-addons = Eklenti Alın...
addons-important-as-addons-are-programs-downloaded = <b>Önemli</b>: Eklentiler internetten indirilen programlar olduğu için kötü niyetli olabilir.<b>Sadece güvendiğiniz eklentileri yüklemelisiniz.</b><br><br>Aşağıdaki Anki eklentilerini yüklemeye devam etmek istediğinizden emin misiniz?<br><br>%(names)s
addons-install-addon = Eklentiyi Yükleyin
addons-install-addons = Eklenti(leri) Yükleyin
addons-install-anki-addon = Anki eklentisini yükleyin
addons-install-from-file = Dosyadan yükle...
addons-installation-complete = Yükleme tamamlandı
addons-installed-names = { $name } yüklendi
addons-installed-successfully = Başarıyla yüklendi.
addons-invalid-addon-manifest = Geçersiz eklenti manifestosu.
addons-invalid-code = Geçersiz kod.
addons-invalid-code-or-addon-not-available = Geçersiz kod, veya eklenti sizin Anki sürümünüz için mevcut değil.
addons-invalid-configuration = Geçersiz yapılandırma:
addons-invalid-configuration-top-level-object-must = Geçersiz yapılandırma: üst seviye nesne bir harita olmalıdır
addons-no-updates-available = Güncelleme mevcut değil.
addons-one-or-more-errors-occurred = Bir veya daha fazla hata oluştu:
addons-packaged-anki-addon = Paketlenmiş Anki Eklentisi
addons-please-check-your-internet-connection = Lütfen internet bağlantınızı kontrol edin.
addons-please-report-this-to-the-respective = Lütfen bunu eklentinin ilgili yazar(lar)ına bildirin.
addons-please-restart-anki-to-complete-the = <b>Yüklemeyi tamamlamak için lütfen Anki'yi yeniden başlatın.</b>
addons-please-select-a-single-addon-first = Lütfen önce tek bir eklenti seçin.
addons-requires = ({ $val } gereklidir)
addons-restored-defaults = Varsayılanlar geri yüklendi
addons-the-following-addons-are-incompatible-with = Aşağıdaki eklentiler, { $name } ile uyumsuzdur ve devre dışı bırakılmıştır: { $found }
addons-the-following-addons-have-updates-available = Aşağıdaki eklentilerin güncellemeleri mevcuttur. Şimdi yüklensinler mi?
addons-the-following-conflicting-addons-were-disabled = Aşağıdaki çakışan eklentiler devre dışı bırakıldı:
addons-this-addon-is-not-compatible-with = Bu eklenti, Anki sürümünüz ile uyumlu değildir.
addons-to-browse-addons-please-click-the = Eklentilere göz atmak için lütfen aşağıdaki göz at düğmesine tıklayın.<br><br>İstediğin bir eklentiyi bulunca lütfen kodunu aşağıya yapıştırın. Boşluklarla ayrılmış birden fazla kod yapıştırabilirsin.
addons-toggle-enabled = Etkin Durumunu Aç/Kapat
addons-unable-to-update-or-delete-addon = Eklenti güncellenemedi veya silinemedi. Eklentileri geçici olarak devre dışı bırakmak için lütfen shift tuşunu basılı tutarken Anki'yi başlatın, sonra tekrar deneyin. Hata ayıklama bilgisi:  { $val }
addons-unknown-error = Bilinmeyen hata: { $val }
addons-view-addon-page = Eklenti sayfasını görüntüleyin
addons-view-files = Dosyaları Görüntüle
addons-delete-the-numd-selected-addon =
    { $count ->
        [one] Seçilen { $count } eklenti silinsin mi?
       *[other] Seçilen { $count } eklenti silinsin mi?
    }
addons-choose-update-window-title = Eklentileri güncelle
addons-choose-update-update-all = Tümünü güncelle
