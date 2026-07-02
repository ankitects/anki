### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks = Digunakan oleh { $decks } dek
deck-config-default-name = Bawaan
deck-config-title = Opsi Dek

## Daily limits section

deck-config-daily-limits = Batas Harian
deck-config-new-limit-tooltip =
    Jumlah maksimum kartu baru yang dapat diperkenalkan dalam sehari, jika kartu baru tersedia.
    Karena materi baru akan meningkatkan beban ulasan jangka pendek, ini sebaiknya
    setidaknya 10 kali lebih kecil dari batas ulasan Anda.
deck-config-review-limit-tooltip =
    Jumlah maksimum kartu ulasan yang ditampilkan dalam sehari,
    jika kartu sudah siap untuk diulas.
deck-config-limit-deck-v3 =
    Saat mempelajari dek yang memiliki sub-dek di dalamnya, batas yang ditetapkan pada setiap
    sub-dek mengontrol jumlah maksimum kartu yang diambil dari dek tersebut.
    Batas pada dek yang dipilih mengontrol total kartu yang akan ditampilkan.
deck-config-limit-new-bound-by-reviews = Batasan ulasan memengaruhi batasan baru. Misalnya, jika batas ulasan Anda disetel ke 200, dan Anda memiliki 190 ulasan yang menunggu, maksimal 10 kartu baru akan diperkenalkan. Jika batas ulasan Anda telah tercapai, tidak ada kartu baru yang akan ditampilkan.
deck-config-limit-interday-bound-by-reviews = Batas ulasan juga memengaruhi kartu pembelajaran antar hari. aat batas diterapkan, kartu pembelajaran antarhari dikumpulkan terlebih dahulu, kemudian kartu ulasan.
deck-config-tab-description =
    - `Prasetel`: Batas ini berlaku untuk semua dek yang menggunakan prasetel ini.
    - `Dek ini`: Batas ini khusus untuk dek ini.
    - `Hari ini saja`: Membuat perubahan sementara pada batas dek ini.
deck-config-new-cards-ignore-review-limit = Kartu baru mengabaikan batas ulasan.
deck-config-new-cards-ignore-review-limit-tooltip = Secara bawaan, batas ulasan juga berlaku untuk kartu baru, dan kartu baru tidak akan ditampilkan setelah batas ulasan tercapai. Jika opsi ini diaktifkan, kartu baru akan tetap ditampilkan meskipun batas ulasan tercapai.
deck-config-apply-all-parent-limits = Batas dimulai dari atas.
deck-config-apply-all-parent-limits-tooltip = Secara bawaan, batas harian pada dek tingkat atas tidak berlaku jika Anda belajar dari subdek-nya. Jika opsi ini diaktifkan, batas akan diterapkan mulai dari dek tingkat atas, yang dapat berguna jika Anda ingin mempelajari subdek secara terpisah sambil tetap memberlakukan batas total kartu untuk keseluruhan pohon dek.
deck-config-affects-entire-collection = Mempengaruhi seluruh koleksi.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Prasetel
deck-config-deck-only = Dek ini
deck-config-today-only = Hari ini saja

## New Cards section

deck-config-learning-steps = Langkah pembelajaran
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Jeda umumnya dalam hitungan menit (misal, '1m') atau hari (misal, '2d'), tetapi jam (misal, '1h') dan detik (misal, '30s') juga didukung.
deck-config-learning-steps-tooltip = Satu atau lebih jeda, dipisahkan dengan spasi. Jeda pertama digunakan saat Anda menekan tombol `Lagi` pada kartu baru, dan secara bawaan adalah 1 menit. Tombol `Lumayan` akan melanjutkan ke langkah berikutnya, yang secara bawaan adalah 10 menit. Setelah semua langkah dilalui, kartu akan menjadi kartu ulasan, dan akan muncul pada hari yang berbeda. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip = Jumlah hari jeda sebelum kartu ditampilkan kembali, setelah tombol 'Lumayan' ditekan pada langkah pembelajaran terakhir.
deck-config-easy-interval-tooltip = Jumlah hari jeda sebelum kartu ditampilkan kembali, setelah tombol 'Mudah' digunakan untuk langsung mengeluarkan kartu dari pembelajaran.
deck-config-new-insertion-order = Urutan penyisipan
deck-config-new-insertion-order-tooltip =
    Mengatur posisi (berdasarkan nomor tenggat) kartu baru yang ditambahkan.
    Kartu dengan nomor tenggat lebih rendah akan ditampilkan lebih dulu saat belajar. Mengubah opsi ini akan otomatis memperbarui posisi kartu baru yang ada.
deck-config-new-insertion-order-sequential = Berurutan (kartu paling lama terlebih dulu)
deck-config-new-insertion-order-random = Acak
deck-config-new-insertion-order-random-with-v3 = Dengan scheduler v3, sebaiknya opsi ini dibiarkan pada pengaturan berurutan, dan sebagai gantinya menyesuaikan urutan pengambilan kartu baru.

## Lapses section

deck-config-relearning-steps = Langkah pembelajaran ulang
deck-config-relearning-steps-tooltip = Nol atau lebih jeda, dipisahkan dengan spasi. Secara bawaan, menekan tombol 'Lagi' pada kartu ulasan akan menampilkannya kembali 10 menit kemudian. Jika tidak ada jeda yang diberikan, interval kartu akan diubah, tanpa memasuki pembelajaran ulang. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip = Jumlah kali tombol 'Lagi' harus ditekan pada kartu ulasan sebelum kartu tersebut ditandai sebagai bandel. Kartu bandel adalah kartu yang memakan banyak waktu Anda, dan ketika kartu ditandai sebagai bandel, sebaiknya kartu tersebut ditulis ulang, dihapus, atau diberi mnemonik agar lebih mudah diingat.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    'Hanya Label': Menambahkan label bandel pada catatan, dan menampilkan pop-up.
    
    'Cabut Kartu': Selain menambahkan label pada catatan, kartu akan disembunyikan hingga diaktifkan kembali secara manual.

## Burying section

deck-config-bury-title = Pendam
deck-config-bury-new-siblings = Pendam kartu terkait
deck-config-bury-review-siblings = Pendam kartu ulasan terkait
deck-config-bury-interday-learning-siblings = Pendam kartu pembelajaran antar hari terkait
deck-config-bury-new-tooltip = Apakah kartu `baru` lainnya dengan catatan yang sama (misalnya, kartu terbalik, perumpangan yag berdekatan) akan ditunda hingga hari berikutnya.
deck-config-bury-review-tooltip = Apakah kartu ulasan lainnya dengan catatan yang sama akan ditunda hingga hari berikutnya.
deck-config-bury-interday-learning-tooltip = Apakah kartu 'belajar'  lain dari catatan yang sama dengan interval > 1 hari akan ditunda hingga hari berikutnya.
deck-config-bury-priority-tooltip =
    Saat Anki mengumpulkan kartu, urutannya adalah kartu belajar harian, lalu
    kartu belajar antar hari, kartu ulasan, dan kartu baru. Ini memengaruhi 
    cara kartu dipendam:
    - Jika semua opsi pendam diaktifkan, kartu terkait yang muncul paling awal dalam urutan tersebut yang akan ditampilkan. Misalnya, kartu ulasan akan ditampilkan lebih dulu 
    dibandingkan kartu baru.
    - Kartu terkait yang berada lebih akhir dalam urutan tidak dapat menyembunyikan tipe kartu yang lebih awal. Sebagai contoh, jika Anda 
    menonaktifkan pendam pada kartu baru lalu mempelajari sebuah kartu baru, kartu tersebut tidak akan memendam kartu belajar antar hari 
    atau kartu ulasan, sehingga Anda dapat melihat kartu ulasan terkait dan kartu baru terkait 
    dalam sesi yang sama.

## Gather order and sort order of cards

deck-config-ordering-title = Urutan tampilan
deck-config-new-gather-priority = urutan pengambilan kartu baru
deck-config-new-gather-priority-tooltip-2 =
    `Dek`: Mengumpulkan kartu dari setiap subdek secara berurutan, dimulai dari bagian teratas.
    Kartu dari setiap subdek 
    dikumpulkan berdasarkan posisi menaik.
    Jika batas harian dek yang dipilih tercapai, proses pengambilan 
    dapat berhenti sebelum semua subdeck diperiksa.
    Urutan ini paling cepat untuk koleksi besar, dan 
    memungkinkan Anda memprioritaskan subdek yang berada lebih dekat ke atas.
    
    `Posisi menaik`: Mengumpulkan kartu berdasarkan posisi naik (due #), yang biasanya 
    adalah kartu yang pertama kali ditambahkan.
    
    `Posisi menurun: Mengumpulkan kartu berdasarkan posisi turun (due #), yang biasanya 
    adalah kartu yang terbaru ditambahkan.
    
    `Catatan acak`: Memilih catatan secara acak, lalu mengumpulkan semua kartunya.
    
    `Kartu acak`: Mengumpulkan kartu secara acak.
deck-config-new-card-sort-order = Urutan penyortiran kartu baru
deck-config-new-card-sort-order-tooltip-2 =
    `Jenis kartu, lalu urutan pengambilan`: Menampilkan kartu berdasarkan nomor tipe kartu.
    Kartu dari setiap tipe kartu ditampilkan sesuai urutan saat kartu tersebut dikumpulkan.
    Jika kartu pendam yang terkait dinonaktifkan, pengaturan ini memastikan semua kartu depan→belakang ditampilkan sebelum kartu belakang→depan.
    Opsi ini berguna untuk menampilkan semua kartu dari catatan yang sama dalam satu sesi, tetapi tidak terlalu berdekatan.
    
    `Urutan pengambilan`: Tampilkan kartu persis seperti saat dikumpulkan. Jika kartu pendam yang terkait dinonaktifkan, umumnya membuat semua kartu dari satu catatan terlihat urut.
    
    `Jenis kartu, lalu acak`: Menampilkan kartu berdasarkan nomor jenis kartu.
    Kartu dari setiap jenis kartu ditampilkan dalam urutan acak.
    Urutan ini berguna jika Anda tidak ingin kartu yang terkait 
    muncul terlalu berdekatan, tetapi tetap ingin urutan acak.
    
    `Catatan acak, lalu jenis kartu`: Memilih catatan secara acak, lalu 
    menampilkan semua kartunya secara berurutan.
    
    `Acak`: Menampilkan kartu secara acak.
deck-config-new-review-priority = Urutan baru/ulasan
deck-config-new-review-priority-tooltip = Menentukan kapan kartu baru ditampilkan dibandingkan dengan kartu ulasan.
deck-config-interday-step-priority = Urutan pelajaran antar hari/ulasan
deck-config-interday-step-priority-tooltip =
    Menentukan kapan kartu pembelajaran (ulang) yang melewati batas hari akan ditampilkan.
    
    Batas ulasan selalu diterapkan terlebih dahulu pada kartu belajar antar hari, 
    kemudian pada kartu ulasan. Opsi ini mengatur urutan tampilan kartu yang telah dikumpulkan,
    tetapi kartu belajar antar hari akan selalu dikumpulkan lebih dulu.
deck-config-review-sort-order = Urutan penyortiran ulasan
deck-config-review-sort-order-tooltip =
    Secara bawaan, kartu yang telah menunggu paling lama akan diprioritaskan, sehingga 
    kartu ulasan yang paling lama tertunda akan muncul lebih dulu. 
    Jika penumpukan ulasan Anda sangat besar atau Anda ingin kartu ditampilkan 
    berdasarkan urutan sub dek, 
    opsi pengurutan lain mungkin lebih sesuai.
deck-config-display-order-will-use-current-deck =
    Anki akan menggunakan urutan tampilan dari dek yang Anda 
    pilih untuk dipelajari, bukan dari subdeck yang dimilikinya.

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = Dek
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = Dek, lalu catatan acak
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = Posisi urut naik
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = Posisi urut turun
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = Catatan acak
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = Kartu acak
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = Tipe kartu, lalu acak
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = Catatan acak, lalu tipe kartu
# Sort the cards randomly.
deck-config-sort-order-random = Acak
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = Jenis kartu, lalu urutan pengambilan
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = Urutan pengambilan
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = Campur dengan ulasan
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = Tampilkan setelah ulasan
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = Tampilkan sebelum ulasan
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = Tenggat waktu, lalu acak
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = Tenggat waktu, lalu dek
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = Dek, lalu tenggat waktu
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = Interval menaik
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = Interval menurun
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = Kemudahan menaik
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = Kemudahan menurun
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = Kartu termudah pertama
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = Kartu tersulit pertama
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = Keterambilan menaik
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = Keterambilan menurun

## Timer section

deck-config-timer-title = Pengatur Waktu
deck-config-maximum-answer-secs = Detik maksimal jawaban
deck-config-maximum-answer-secs-tooltip =
    Jumlah detik maksimal yang akan dicatat untuk satu kali ulasan. Jika waktu menjawab 
    melebihi batas ini (misalnya karena Anda meninggalkan layar), 
    waktu yang tercatat akan dibatasi pada nilai yang Anda tetapkan.
deck-config-show-answer-timer-tooltip = Pada layar Belajar, tampilkan pengatur waktu yang menghitung lama waktu Anda mempelajari setiap kartu.
deck-config-stop-timer-on-answer = Hentikan pengatur waktu saat jawaban ditampilkan.
deck-config-stop-timer-on-answer-tooltip =
    Hentikan pengatur waktu saat jawaban ditampilkan.
    Ini tidak mempengaruhi statistik

## Auto Advance section

deck-config-seconds-to-show-question = Durasi tampilan pertanyaan (detik)
deck-config-seconds-to-show-question-tooltip-3 = Saat lanjut otomatis diaktifkan, jumlah detik yang ditunggu sebelum menerapkan aksi pertanyaan. Atur ke 0 untuk menonaktifkan.
deck-config-seconds-to-show-answer = Durasi tampilan jawaban (detik)
deck-config-seconds-to-show-answer-tooltip-2 = Saat lanjut otomatis diaktifkan, jumlah detik yang ditunggu sebelum menerapkan aksi pertanyaan. Atur ke 0 untuk menonaktifkan.
deck-config-question-action-show-answer = Tampilkan jawaban
deck-config-question-action-show-reminder = Tampilkan pengingat
deck-config-question-action = Aksi pertanyaan
deck-config-question-action-tool-tip = Tindakan yang harus dilakukan setelah pertanyaan ditampilkan, dan waktu telah berlalu.
deck-config-answer-action = Tindakan jawaban
deck-config-answer-action-tooltip-2 = Tindakan yang harus dilakukan setelah jawaban ditampilkan, dan waktu telah berlalu.
deck-config-wait-for-audio-tooltip-2 = Tunggu hingga  pemutaran audio selesai sebelum menerapkan tindakan pertanyaan atau tindakan jawaban secara otomatis.

## Audio section

deck-config-audio-title = Audio
deck-config-disable-autoplay = Jangan putar audio secara otomatis
deck-config-disable-autoplay-tooltip =
    Saat diaktifkan, Anki tidak akan memutar audio secara otomatis.
    Audio dapat diputar secara manual dengan mengklik/mengetuk ikon audio, atau dengan menggunakan tindakan Putar Ulang.
deck-config-skip-question-when-replaying = Lewati pertanyaan saat memutar ulang jawaban
deck-config-always-include-question-audio-tooltip = Apakah audio pertanyaan harus disertakan ketika tindakan Putar Ulang digunakan saat melihat sisi jawaban kartu.

## Advanced section

deck-config-advanced-title = Opsi Tingkat lanjut
deck-config-maximum-interval-tooltip =
    Jumlah hari maksimum yang akan ditunggu oleh kartu ulasan. Ketika ulasan telah mencapai batas, `Sulit`, `Baik`, dan `Mudah` semuanya akan memberikan penundaan yang sama.
    
    Semakin pendek Anda menetapkan ini, semakin besar beban studi Anda.
deck-config-starting-ease-tooltip = Pengali kemudahan yang akan digunakan kartu baru saat pertama kali dipelajari. Secara default, tombol `Baik` pada kartu yang baru dipelajari akan menunda ulasan berikutnya sebanyak 2,5 kali penundaan sebelumnya.
deck-config-easy-bonus-tooltip = Pengali tambahan yang diterapkan pada jeda kartu ulasan saat Anda memberi peringkat `Mudah`.
deck-config-interval-modifier-tooltip =
    Pengali ini diterapkan pada semua ulasan, dan penyesuaian kecil dapat digunakan
    untuk membuat Anki lebih konservatif atau agresif dalam penjadwalannya. Silakan lihat
    panduan manual sebelum mengubah opsi ini.
deck-config-hard-interval-tooltip = Pengali yang diterapkan pada interval peninjauan saat menjawab `Sulit`.
deck-config-new-interval-tooltip = Pengali yang diterapkan pada interval peninjauan saat menjawab `Lagi`.
deck-config-minimum-interval-tooltip = Pengali yang diterapkan pada interval peninjauan saat menjawab `Lagi`.
deck-config-custom-scheduling = Penjadwalan khusus
deck-config-custom-scheduling-tooltip = Ini mempengaruhi seluruh koleksi dek Anda. Harap gunakan dengan bijak!

## Easy Days section.

deck-config-easy-days-title = Hari-hari Santai
deck-config-easy-days-monday = Senin
deck-config-easy-days-tuesday = Selasa
deck-config-easy-days-wednesday = Rabu
deck-config-easy-days-thursday = Kamis
deck-config-easy-days-friday = Jumat
deck-config-easy-days-saturday = Sabtu
deck-config-easy-days-sunday = Minggu
deck-config-easy-days-normal = Normal
deck-config-easy-days-reduced = Diringankan
deck-config-easy-days-minimum = Minimal
deck-config-easy-days-no-normal-days = Setidaknya satu hari harus diatur ke '{ deck-config-easy-days-normal }'.
deck-config-easy-days-change = Tinjauan yang sudah ada tidak akan dijadwalkan ulang kecuali '{ deck-config-reschedule-cards-on-change }' diaktifkan dalam opsi FSRS.

## Adding/renaming

deck-config-add-group = Tambahkan Preset
deck-config-name-prompt = Nama
deck-config-rename-group = Ubah nama Preset
deck-config-clone-group = Salin Preset

## Removing

deck-config-remove-group = Hapus Preset
deck-config-will-require-full-sync =
    Perubahan yang diminta akan memerlukan sinkronisasi satu arah. Jika Anda telah melakukan perubahan pada perangkat lain, dan belum menyinkronkannya ke perangkat ini,
    harap lakukan terlebih dahulu sebelum Anda melanjutkan.
deck-config-confirm-remove-name = Hapus { $name }?

## Other Buttons

deck-config-save-button = Simpan
deck-config-save-to-all-subdecks = Simpan ke semua Subdek
deck-config-save-and-optimize = Optimalkan Semua Preset
deck-config-revert-button-tooltip = Kembalikan pengaturan ini ke opsi defaultnya?

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Penanganan khusus untuk Anki 2.1.41+
deck-config-description-new-handling-hint =
    Memperlakukan input sebagai markdown, dan membersihkan input HTML. Saat diaktifkan,
    deskripsi juga akan ditampilkan di layar ucapan selamat.
    Markdown akan muncul sebagai teks pada Anki 2.1.40 dan di bawahnya.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped = Dek induk memiliki batasan sebanyak { $cards } kartu, yang akan mengabaikan batasan ini.
deck-config-reviews-too-low = Jika menambahkan { $cards } kartu baru setiap hari, batas peninjauan Anda setidaknya harus { $expected }.
deck-config-learning-step-above-graduating-interval = Interval kelulusan kartu baru setidaknya harus sama panjangnya dengan langkah pembelajaran terakhir Anda.
deck-config-good-above-easy = Interval mudah setidaknya harus sama panjangnya dengan interval kelulusan kartu baru.
deck-config-relearning-steps-above-minimum-interval = Interval jeda minimum setidaknya harus sama panjangnya dengan langkah kartu yang dipelajari ulang terakhir Anda.
deck-config-maximum-answer-secs-above-recommended = Anki dapat menjadwalkan sesi peninjauan Anda dengan lebih efisien jika setiap pertanyaan dibuat singkat.
deck-config-too-short-maximum-interval = Interval maksimal kurang dari 6 bulan tidak disarankan.
deck-config-ignore-before-info = (Kurang lebih) { $included }/{ $totalCards } kartu akan digunakan untuk mengoptimalkan parameter FSRS.

## Selecting a deck

deck-config-which-deck = Dek mana yang ingin Anda tampilkan opsinya?

## Messages related to the FSRS scheduler

deck-config-updating-cards = Memperbarui kartu: { $current_cards_count }/{ $total_cards_count }...
deck-config-invalid-parameters = Parameter FSRS yang diberikan tidak valid. Biarkan kosong untuk menggunakan parameter default.
deck-config-not-enough-history = Riwayat peninjauan tidak mencukupi untuk melakukan operasi ini.
deck-config-must-have-400-reviews = Hanya ditemukan { $count } ulasan. Anda harus memiliki setidaknya 400 tinjauan agar operasi ini berhasil.
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = Parameter FSRS
deck-config-compute-optimal-weights = Optimalkan parameter FSRS
deck-config-optimize-button = Optimalkan Preset Saat Ini
# Indicates that a given function or label, provided via the "text" variable, operates slowly.
deck-config-slow-suffix = { $text } (lambat)
deck-config-compute-button = Kalkulasikan
deck-config-ignore-before = Abaikan kartu yang telah ditinjau sebelumnya
deck-config-time-to-optimize = Sudah lama sekali - disarankan untuk menggunakan tombol Optimalkan Semua Preset.
deck-config-evaluate-button = Evaluasikan
deck-config-desired-retention = Retensi yang diinginkan
deck-config-historical-retention = Retensi historis
deck-config-smaller-is-better = Angka yang lebih kecil menunjukkan kesesuaian yang lebih baik dengan riwayat ulasan Anda.
deck-config-steps-too-large-for-fsrs = Saat FSRS diaktifkan, langkah waktu 1 hari atau lebih tidak disarankan.
deck-config-get-params = Dapatkan Parameter
deck-config-complete = { $num }% selesai.
deck-config-iterations = Iterasi: { $count }...
deck-config-reschedule-cards-on-change = Jadwalkan ulang kartu jika terjadi perubahan
deck-config-fsrs-tooltip =
    Ini memengaruhi seluruh koleksi Anda.
    
    Free Spaced Repetition Scheduler (FSRS) adalah pengganti untuk algoritma SuperMemo 2 (SM-2) Anki yang sudah jadul.
    
    Dengan menentukan secara lebih akurat seberapa besar kemungkinan Anda melupakan sebuah kartu, ini dapat membantu Anda mengingat lebih banyak materi dalam waktu yang sama. Pengaturan ini berlaku untuk semua preset.
deck-config-desired-retention-tooltip =
    Secara default, Anki menjadwalkan kartu sehingga Anda memiliki peluang 90% untuk mengingatnya ketika kartu tersebut muncul untuk ditinjau kembali. Jika Anda meningkatkan nilai ini, Anki akan menampilkan kartu lebih sering untuk meningkatkan peluang Anda mengingatnya.
    Jika Anda menurunkan nilainya, Anki akan menampilkan kartu lebih jarang, dan Anda akan melupakan lebih banyak kartu.
    Berhati-hatilah saat menyesuaikan ini - nilai yang lebih tinggi akan sangat meningkatkan beban studi Anda, dan nilai yang lebih rendah dapat menurunkan semangat Anda ketika Anda melupakan banyak materi.
deck-config-desired-retention-tooltip2 = Nilai beban studi yang diberikan oleh kotak informasi hanyalah perkiraan kasar. Untuk tingkat akurasi yang lebih tinggi, gunakan simulator.
deck-config-historical-retention-tooltip =
    Ketika sebagian riwayat ulasan Anda hilang, FSRS perlu mengisi kekosongan tersebut. Secara default, FSRS akan menganggap bahwa ketika Anda melakukan ulasan lama tersebut, Anda mengingat sebanyak 90% dari materi itu.
    Jika tingkat retensi lama Anda jauh lebih tinggi atau lebih rendah dari 90%, menyesuaikan opsi ini akan memungkinkan FSRS untuk memperkirakan ulasan yang hilang dengan lebih baik.
    
    Riwayat ulasan Anda mungkin tidak lengkap karena dua alasan:
    1. Karena Anda menggunakan opsi 'Abaikan kartu yang telah ditinjau sebelumnya'.
    2. Karena Anda sebelumnya menghapus log ulasan untuk mengosongkan ruang, atau mengimpor materi dari program SRS yang berbeda.
    
    Masih ada lagi, tapi sisa-sisa itu cukup jarang terjadi, jadi kecuali Anda menggunakan opsi pertama, Anda mungkin tidak perlu menyesuaikan opsi ini.
deck-config-weights-tooltip2 = Parameter FSRS memengaruhi bagaimaan cara kartu-kartu dijadwalkan. Anki akan dimulai dengan parameter default. Anda dapat menggunakan opsi di bawah ini untuk mengoptimalkan parameter agar sesuai dengan performa Anda di dek yang menggunakan preset ini.
deck-config-reschedule-cards-on-change-tooltip =
    Ini memengaruhi seluruh koleksi, dan tidak disimpan.
    
    Opsi ini mengontrol apakah tanggal jatuh tempo kartu akan diubah saat Anda mengaktifkan FSRS, atau mengoptimalkan parameter.
    Secara default, kartu tidak dijadwal ulang: tinjauan di masa mendatang akan menggunakan penjadwalan baru, tetapi tidak akan ada perubahan langsung pada beban studi Anda.
    Jika penjadwalan ulang diaktifkan, tanggal jatuh tempo kartu akan diubah.
deck-config-reschedule-cards-warning =
    Tergantung pada tingkat retensi yang Anda inginkan, ini dapat mengakibatkan sejumlah besar kartu menjadi jatuh tempo, sehingga tidak disarankan saat pertama kali beralih dari SM-2.
    
    Gunakan opsi ini dengan bijak, karena akan menambahkan entri ulasan ke setiap kartu Anda, dan meningkatkan ukuran koleksi Anda.
deck-config-ignore-before-tooltip-2 = Jika diatur, kartu yang ditinjau sebelum tanggal yang diberikan akan diabaikan saat mengoptimalkan parameter FSRS. Ini dapat berguna jika Anda mengimpor data penjadwalan orang lain, atau telah mengubah cara Anda menggunakan tombol jawaban.
deck-config-compute-optimal-weights-tooltip2 =
    Saat Anda mengklik tombol Optimalkan, FSRS akan menganalisis riwayat seluruh ulasan Anda, dan menghasilkan parameter yang optimal untuk daya ingat Anda dan konten yang sedang Anda pelajari. Jika tingkat kesulitan dek Anda sangat bervariasi, disarankan untuk menetapkan preset terpisah untuk setiap dek, karena parameter untuk dek mudah dan dek sulit akan berbeda.
    
    Anda tidak perlu mengoptimalkan parameter Anda secara terus-menerus; setiap beberapa bulan saja sudah cukup.
    
    Secara default, parameter akan dihitung dari riwayat ulasan semua dek yang menggunakan preset saat ini. Anda dapat menyesuaikan pencarian sebelum menghitung parameter jika Anda ingin mengubah kartu mana yang digunakan untuk mengoptimalkan parameter.
deck-config-please-save-your-changes-first = Harap simpan perubahan Anda terlebih dahulu.
deck-config-workload-factor-change =
    Perkiraan beban studi: { $factor }x
    (dibandingkan dengan { $previousDR }% retensi sebelumnya)
deck-config-workload-factor-unchanged = Semakin tinggi nilai ini, semakin sering kartu akan ditampilkan kepada Anda.
deck-config-desired-retention-too-low = Tingkat retensi yang Anda inginkan sangat rendah, yang dapat menyebabkan interval yang sangat panjang.
deck-config-desired-retention-too-high = Tingkat retensi yang Anda inginkan sangat tinggi, yang dapat menyebabkan interval yang sangat singkat.
deck-config-percent-of-reviews = { $pct }% dari { $reviews } ulasan
deck-config-percent-input = { $pct }
# This message appears during FSRS parameter optimization.
deck-config-checking-for-improvement = Memeriksa apakah ada yang bisa diimprovisasi...
deck-config-optimizing-preset = Mengoptimalkan preset { $current_count }/{ $total_count }...
deck-config-fsrs-must-be-enabled = FSRS harus diaktifkan terlebih dahulu.
deck-config-fsrs-params-optimal = Parameter FSRS saat ini tampaknya sudah optimal.
deck-config-fsrs-params-no-reviews = Tidak ada ulasan yang ditemukan. Pastikan preset ini diterapkan ke semua deck (termasuk subdeck) yang ingin Anda optimalkan, lalu coba lagi.
deck-config-wait-for-audio = Tunggu Audio
deck-config-show-reminder = Tampilkan Pengingat
deck-config-answer-again = Jawab Lagi
deck-config-answer-hard = Jawab Sulit
deck-config-answer-good = Jawab Baik
deck-config-days-to-simulate = Berapa hari untuk simulasi
deck-config-desired-retention-below-optimal = Tingkat retensi yang Anda inginkan saat ini masih di bawah optimal. Disarankan untuk meningkatkannya.
# Description of the y axis in the FSRS simulation
# diagram (Deck options -> FSRS) showing the total number of
# cards that can be recalled or retrieved on a specific date.
deck-config-fsrs-simulator-experimental = Simulasi FSRS (Eksperimental)
deck-config-fsrs-simulate-desired-retention-experimental = Tingkat retensi yang Diinginkan untuk simulasi FSRS (Eksperimental)
deck-config-fsrs-simulate-save-preset = Setelah melakukan optimasi, harap simpan preset dek Anda sebelum menjalankan simulator.
deck-config-fsrs-desired-retention-help-me-decide-experimental = Bantu Aku Memutuskan (Eksperimental)
deck-config-additional-new-cards-to-simulate = Kartu baru tambahan untuk simulasi
deck-config-simulate = Simulasikan
deck-config-clear-last-simulate = Hapus Simulasi Terakhir
deck-config-fsrs-simulator-radio-count = Ulasan
deck-config-advanced-settings = Pengaturan Tingkat Lanjut
deck-config-smooth-graph = Grafik halus
deck-config-suspend-leeches = Tangguhkan kartu-kartu bandel
deck-config-save-options-to-preset = Simpan Perubahan pada Preset
deck-config-save-options-to-preset-confirm = Apakah Anda ingin menimpa opsi di preset Anda saat ini dengan opsi yang saat ini diatur di simulasi?
# Radio button in the FSRS simulation diagram (Deck options -> FSRS) selecting
# to show the total number of cards that can be recalled or retrieved on a
# specific date.
deck-config-fsrs-simulator-radio-memorized = Diingat
deck-config-fsrs-simulator-radio-ratio = Rasio Waktu / Ingatan
# $time here is pre-formatted e.g. "10 Seconds" 
deck-config-fsrs-simulator-ratio-tooltip = { $time } per kartu yang diingat

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.

# Checkbox
deck-config-health-check = Periksa kondisi saat melakukan optimasi
# Message box showing the result of the health check
deck-config-fsrs-bad-fit-warning =
    Pemeriksaan Kesehatan:
    Kemampuan ingatan Anda sulit diprediksi oleh FSRS. Rekomendasi:
    
    - Tangguhkan atau formulasikan ulang kartu apa pun yang terus-menerus Anda lupakan.
    - Gunakan tombol jawaban secara konsisten. Ingatlah bahwa "Sulit" adalah nilai lulus, bukan nilai gagal.
    - Pahami sebelum mengingat.
    
    Jika Anda mengikuti saran-saran ini, kinerja biasanya akan meningkat dalam beberapa bulan ke depan.
# Message box showing the result of the health check
deck-config-fsrs-good-fit =
    Pemeriksaan Kesehatan:
    FSRS dapat beradaptasi dengan memori Anda dengan baik.

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-unable-to-determine-desired-retention = Tidak dapat menentukan retensi minimum yang direkomendasikan.
deck-config-predicted-minimum-recommended-retention = Retensi minimum yang disarankan: { $num }
