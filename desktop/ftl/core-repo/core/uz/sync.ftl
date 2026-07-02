### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = Qoʻshildi: { $up }↑ { $down }↓
sync-media-removed-count = Oʻchirildi: { $up }↑ { $down }↓
sync-media-checked-count = Tekshirildi: { $count }
sync-media-starting = Media fayllarni sinxronlash boshlanmoqda...
sync-media-complete = Media fayllar sinxronlandi.
sync-media-failed = Media fayllarni sinxronlab boʻlmadi.
sync-media-aborting = Media fayllarni sinxronlash toʻxtatilmoqda...
sync-media-aborted = Media fayllarni sinxronlash toʻxtatildi.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = Media fayllarni sinxronolash oʻchirilgan.
# Title of the screen that shows syncing progress history
sync-media-log-title = Media fayllar sinxronlash jurnali

## Error messages / dialogs

sync-conflict = Ankiʼning faqat bitta nusxasi bir vaqtning oʻzida akkauntingizga sinxronlanishi mumkin. Bir necha daqiqa kutib turing, keyin qayta urinib koʻring.
sync-server-error = AnkiWeb muammoga duch keldi. Bir necha daqiqadan soʻng qayta urinib koʻring.
sync-client-too-old = Anki versiyangiz juda eski. Sinxronlashni davom ettirish uchun soʻnggi versiyaga yangilang.
sync-wrong-pass = E-pochta manzili yoki parol notoʻgʻri; qayta urinib koʻring.
sync-resync-required = Qayta sinxronlab koʻring. Agar bu xabar qayta paydo boʻladigan boʻlsa, qoʻllab-quvvatlash saytiga yuklang.
sync-must-wait-for-end = Anki hozirda sinxronlashmoqda. Sinxronlash tugashini kuting, keyin qayta urinib koʻring.
sync-confirm-empty-download = Qurilmadagi kolleksiyada kartalar yoʻq. AnkiWebʼdan yuklab olinsinmi?
sync-confirm-empty-upload = AnkiWeb kolleksiyasida kartalar yoʻq. Qurilmadagi kolleksiya bilan almashtirilsinmi?
sync-conflict-explanation =
    Qurilmangizdagi va AnkiWebʼdagi dastalaringiz bir-biridan shunday farq qiladiki, ularni birlashtirib boʻlmaydi, shuning uchun bir joydagi dastalarni boshqa joydagi dastalar bilan almashtirish kerak.
    
    Yuklab olishni tanlasangiz, Anki kolleksiyani AnkiWebʼdan oladi va ushbu qurilmadagi oxirgi sinxronlashdan keyingi barcha oʻzgarishlaringiz yoʻqoladi.
    
    Yuklashni tanlasangiz, Anki ushbu qurilmadagi maʼlumotlarini AnkiWebʼga yuboradi va AnkiWebʼda kutilayotgan har qanday oʻzgarishlar yoʻqoladi.
    
    Barcha qurilmalar sinxronlanganidan soʻng, kelajakdagi takrorlashlar va qoʻshilgan kartalar avtomatik ravishda birlashtirilishi mumkin.
sync-conflict-explanation2 =
    Ushbu qurilmadagi va AnkiWebʼdagi dastalar oʻrtasida ixtilof mavjud. Nusxalardan qaysi biri saqlanishini tanlashingiz kerak:
    
    - AnkiWebʼdagi dastalar bilan almashtirish uchun **{ sync-download-from-ankiweb }** ni tanlang. Bu qurilmadagi oxirgi sinxronizatsiyadan keyingi barcha oʻzgarishlaringizni yoʻqotasiz.
    - **{ sync-upload-to-ankiweb }** ni tanlasangiz, AnkiWebʼdagi dastalar ushbu qurilmadagi dastalar bilan almashtiriladi va AnkiWebʼdagi har qanday oʼzgarishlarni yoʻqoladi.
    
    Ixtilof bartaraf etilgach, sinxronlash odatdagidek ishlaydi.
sync-ankiweb-id-label = E‑pochta:
sync-password-label = Parol:
sync-account-required =
    <h1>Akkaunt zarur</h1>
    Kolleksiyangizni sinxronlash uchun bepul akkaunt boʻlishi shart. Iltimos, akkaunt ochish uchun <a href="{ $link }">roʻyxatdan oʻting</a>, soʻngra maʼlumotlaringizni pastga kiriting.
sync-sanity-check-failed = Maʼlumotlar bazasini tekshirish funksiyasini ishlating, soʻng qayta sinxronlang. Muammo davom etsa, sozlamalar ekranida bir tomonlama sinxronlashni majburlang.
sync-clock-off = Sinxronlab boʻlmadi - soatingiz toʻgʻri sozlanmagan.
# “details” expands to a string such as “300.14 MB > 300.00 MB”
sync-upload-too-large =
    Kolleksiya faylingizni AnkiWebʼga yuborish uchun juda katta. Uni oʻlchamini har qanday keraksiz dastalarni olib tashlagandan keyin (istasangiz avval ularni eksport qilib) maʼlumotlar bazasini tekshirish funksiyasidan foydalanish orqali fayl hajmini kamaytirishingiz mumkin.
    
    ({ $details })
sync-sign-in = Tizimga kirish
sync-ankihub-dialog-heading = AnkiHub tizimiga kirish
sync-ankihub-username-label = Foydalanuvchi nomi yoki e-pochta:
sync-ankihub-login-failed = Berilgan hisob maʼlumotlari bilan AnkiHub tizimiga kirib boʻlmadi.
sync-ankihub-addon-installation = AnkiHub kengaytmasini oʻrnatish

## Buttons

sync-media-log-button = Media fayllar jurnali
sync-abort-button = Toʻxtatish
sync-download-from-ankiweb = AnkiWebʼdan yuklab olish
sync-upload-to-ankiweb = AnkiWebʼga yuklash
sync-cancel-button = Bekor qilish

## Normal sync progress

sync-downloading-from-ankiweb = AnkiWebʼdan yuklab olinmoqda...
sync-uploading-to-ankiweb = AnkiWebʼga yuklanmoqda...
sync-syncing = Sinxronlanmoqda...
sync-checking = Tekshirilmoqda...
sync-connecting = Ulanilmoqda...
sync-added-updated-count = Qoʻshildi/oʻzgartirildi: { $up }↑ { $down }↓
sync-log-in-button = Tizimga kirish
sync-log-out-button = Tizimdan chiqish
sync-collection-complete = Kolleksiya sinxronizatsiyasi tugadi.
