## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount }dtk
scheduling-answer-button-time-minutes = { $amount }mnt
scheduling-answer-button-time-hours = { $amount }jm
scheduling-answer-button-time-days = { $amount }hr
scheduling-answer-button-time-months = { $amount }bln
scheduling-answer-button-time-years = { $amount }thn

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds = { $amount } detik
scheduling-time-span-minutes = { $amount } menit
scheduling-time-span-hours = { $amount } jam
scheduling-time-span-days = { $amount } hari
scheduling-time-span-months = { $amount } bulan
scheduling-time-span-years = { $amount } tahun

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    { $unit ->
        [seconds] Kartu yang sedang dipelajari selanjutnya akan siap dalam { $amount } detik..
        [minutes] Kartu yang sedang dipelajari selanjutnya akan siap dalam { $amount } menit.
       *[hours] Kartu yang sedang dipelajari selanjutnya akan siap dalam { $amount } jam.
    }
scheduling-learn-remaining =
    { $remaining ->
        [one] Masih ada satu kartu yang sedang dipelajari dan harus diselesaikan nanti hari ini.
       *[other] Masih ada { $remaining } kartu yang sedang dipelajari dan harus diselesaikan nanti hari ini.
    }
scheduling-congratulations-finished = Selamat! Anda telah menyelesaikan dek ini untuk saat ini.
scheduling-today-review-limit-reached =
    Batas peninjauan hari ini telah tercapai, tetapi masih ada kartu
    yang menunggu untuk ditinjau. Untuk mengingat informasi dengan lebih optimal, pertimbangkan
    untuk meningkatkan batas harian di opsi.
scheduling-today-new-limit-reached =
    Masih ada lebih banyak kartu baru yang tersedia, tetapi batas harian telah tercapai.
    Anda dapat meningkatkan batas kartu baru di opsi, 
    tetapi harap diingat bahwa semakin banyak kartu baru yang Anda ketahui,
    semakin tinggi beban kerja peninjauan Anda dalam jangka pendek.
scheduling-buried-cards-found = Satu atau lebih kartu terkubur, dan akan ditampilkan besok. Anda dapat { $unburyThem } jika ingin melihatnya segera.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = membangkitkan kembali kartu-kartu tersebut
scheduling-how-to-custom-study = Jika Anda ingin belajar di luar jadwal biasanya, Anda dapat menggunakan fitur { $customStudy }.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = studi khusus

## Scheduler upgrade

scheduling-update-soon = Anki 2.1 hadir dengan penjadwal baru, yang mana telah memperbaiki sejumlah masalah yang ada di versi Anki sebelumnya. Pembaruan ke versi ini sangat direkomendasikan.
scheduling-update-done = Pembaruan penjadwal berhasil.
scheduling-update-button = Perbarui
scheduling-update-later-button = Nanti
scheduling-update-more-info-button = Pelajari lebih lanjut
scheduling-update-required =
    Koleksi Anda perlu ditingkatkan untuk menggunakan penjadwal V2.
    Silakan pilih { scheduling-update-more-info-button } sebelum melanjutkan.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = Selalu sertakan sisi pertanyaan saat memutar ulang audio
scheduling-at-least-one-step-is-required = Setidaknya satu langkah diperlukan.
scheduling-automatically-play-audio = Putar audio secara otomatis
scheduling-bury-related-new-cards-until-the = Kubur kartu baru yang terkait hingga keesokan harinya
scheduling-bury-related-reviews-until-the-next = Kubur peninjauan yang terkait hingga keesokan harinya.
scheduling-days = hari
scheduling-description = Deskripsi
scheduling-easy-bonus = Bonus mudah
scheduling-easy-interval = Interval mudah
scheduling-end = (akhiri)
scheduling-general = Umum
scheduling-graduating-interval = Interval kelulusan
scheduling-hard-interval = Interval sulit
scheduling-ignore-answer-times-longer-than = Abaikan waktu respons yang lebih lama dari
scheduling-interval-modifier = Pengubah interval
scheduling-lapses = Selang
scheduling-lapses2 = selang
scheduling-learning = Sedang dipelajari
scheduling-leech-action = Aksi pemanfaatan
scheduling-leech-threshold = Ambang batas pemanfaatan
scheduling-maximum-interval = Interval maksimal
scheduling-maximum-reviewsday = peninjauan/hari maksimal
scheduling-minimum-interval = Interval minimal
scheduling-mix-new-cards-and-reviews = Campur kartu baru dan tinjauan
scheduling-new-cards = Kartu baru
scheduling-new-cardsday = Kartu baru/hari
scheduling-new-interval = Interval baru
scheduling-new-options-group-name = Nama grup opsi baru:
scheduling-options-group = Grup opsi:
scheduling-order = Urutan
scheduling-parent-limit = (batas induk: { $val })
scheduling-reset-counts = Atur ulang jumlah pengulangan dan jeda.
scheduling-restore-position = Kembalikan ke posisi semula jika memungkinkan
scheduling-review = Tinjau
scheduling-reviews = Peninjauan
scheduling-seconds = detik
scheduling-set-all-decks-below-to = Atur semua dek di bawah { $val } ke grup opsi ini?
scheduling-set-for-all-subdecks = Atur untuk semua sub-dek
scheduling-show-answer-timer = Tampilkan timer waktu di layar
scheduling-show-new-cards-after-reviews = Tampilkan kartu baru setelah peninjauan
scheduling-show-new-cards-before-reviews = Tampilkan kartu baru sebelum peninjauan
scheduling-show-new-cards-in-order-added = Tampilkan kartu baru sesuai urutan penambahannya
scheduling-show-new-cards-in-random-order = Tampilkan kartu baru secara acak.
scheduling-starting-ease = Kemudahan di awal
scheduling-steps-in-minutes = Langkah-langkah (dalam menit)
scheduling-steps-must-be-numbers = Langkah-langkah harus dalam satuan angka.
scheduling-tag-only = Hanya Tag
scheduling-the-default-configuration-cant-be-removed = Konfigurasi bawaan tidak dapat dihapus.
scheduling-your-changes-will-affect-multiple-decks = Perubahan Anda akan memengaruhi beberapa dek. Jika Anda hanya ingin mengubah dek saat ini, harap tambahkan grup opsi baru terlebih dahulu.
scheduling-deck-updated = { $count } dek diperbaharui.
scheduling-set-due-date-prompt = Tunjukkan kartu dalam berapa hari?
scheduling-set-due-date-prompt-hint =
    0 = hari ini
    1! = besok + dan mengubah interval menjadi 1
    3-7 = pilihan acak 3-7 hari
scheduling-set-due-date-done = Tetapkan tanggal jatuh tempo untuk kartu { $cards }.
scheduling-graded-cards-done = Kartu yang telah dinilai: { $cards }.
scheduling-forgot-cards = Atur ulang kartu { $cards }.
