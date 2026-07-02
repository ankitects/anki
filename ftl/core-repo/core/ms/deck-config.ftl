### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    diguna oleh { $decks ->
       *[other] { $decks } dek
    }
deck-config-default-name = Lalai
deck-config-title = Tetapan Dek

## Daily limits section

deck-config-daily-limits = Had Harian
deck-config-new-limit-tooltip =
    Bilangan maksimum kad baru untuk tunjukkan dalam satu hari, jika terdapat kad baru.
    Disebabkan bahan baru akan tingkatkan beban semakan jangka masa pendek anda, ini
    selalunya 10x lebih kecil dari had semakan anda.
deck-config-review-limit-tooltip =
    Bilangan maksimum kad semakan untuk tunjukkan dalam satu hari,
    jika kad sedia untuk disemak.
deck-config-limit-deck-v3 =
    Apabila ulang kaji dek yang mempunyai subdek dalamnya, had-had pada
    setiap subdek kawal bilangan maksimum kad diambil daripada dek tersebut.
    Had dek terpilih kawal jumlah kad yang akan ditunjuk.
deck-config-limit-new-bound-by-reviews =
    Had semakan mempengaruhi had baru. Contohnya, jika had semakan tetap
    kepada 200, dan anda mempunyai 190 semakan tunggak, maksimum sebanyak
    10 kad baru akan ditunjukkan. Jika had semakan anda tercapai, tiada kad baru akan
    ditunjuk.
deck-config-limit-interday-bound-by-reviews =
    Had semakan juga mempengaruhi kad belajar berhari. Apabila menggunakan had,
    kad belajar berhari akan diperoleh dahulu, diikuti semakan.
deck-config-tab-description =
    - `Pratetap`: Had dikongsi dengan semua dek menggunakan pratetap ini.
    - `This deck`: Had khusus kepada dek ini.
    - `Hari ini`: Perubahan sementara kepada had dek ini.
deck-config-new-cards-ignore-review-limit = Kad baru abaikan had semakan
deck-config-new-cards-ignore-review-limit-tooltip =
    Secara lalai, had semakan juga digunakan untuk kad baru, dan tiada kad baru akan
    ditunjuk apabila had semakan tercapai. Jika tetapan ini didayakan, kad baru akan
    ditunjukkan tanpa mengira had semakan.
deck-config-affects-entire-collection = Mempengaruhi seluruh koleksi.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Pratetap
deck-config-deck-only = Dek ini
deck-config-today-only = Hari ini

## New Cards section

deck-config-learning-steps = Langkah belajar
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Tundaan selalunya dalam minit (cth `1m`) atau hari (cth `2d`), tetapi jam (cth `1h`) dan saat (`30s`) juga disokong.
deck-config-learning-steps-tooltip =
    Satu atau lebih tundaan, diasingkan dengan ruang kosong. Tangguhan pertama
    akan digunakan apabila anda tekan butang `Ulang` pada kad baru, serta adalah 1
    minit secara lalai. Butang `Baik` akan lanjut ke langkah seterusnya, iaitu 10 minit
    secara lalai. Apabila sudah lepasi semua langkah, kad akan menjadi kad semakan,
    dan akan ditunjukkan pada hari lain. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip =
    Bilangan hari untuk tunggu sebelum tunjuk kad lagi, selepas butang `Baik`
    ditekan pada langkah belajar terakhir.
deck-config-easy-interval-tooltip =
    Bilangan hari untuk tunggu sebelum tunjuk kad lagi, selepas butang `Senang`
    ditekan untuk keluarkan kad dari belajar serta merta.
deck-config-new-insertion-order = Susunan masuk
deck-config-new-insertion-order-tooltip =
    Kawal posisi (# tunggak) kad baru yang ditetapkan apabila anda tambah kad baru.
    Kad dengan nombor tunggak rendah akan ditunjukkan dahulu apabila ulang kaji.
    Mengubah tetapan ini akan kemas kini posisi sedia kad baru secara automatik.
deck-config-new-insertion-order-sequential = Berurutan (kad tertua dahulu)
deck-config-new-insertion-order-random = Rawak
deck-config-new-insertion-order-random-with-v3 =
    Dengan penjadual V3, lebih baik untuk biarkan ini tetap pada berurutan, dan
    sebaliknya ubah susunan pungutan kad baru.

## Lapses section

deck-config-relearning-steps = Langkah belajar semula
deck-config-relearning-steps-tooltip =
    Kosong atau lebih tundaan, diasingkan oleh ruang kosong. Secara lalai, tekan butang
    `Ulang` pada kad semakan akan tunjukkannya lagi 10 minit kemudian. Jika tiada
    tundaan diberikan, selang kad tersebut akan diubah, tanpa belajar semula.
    { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip =
    Bilangan kali `Ulang`  perlu ditekan pada kad semakan sebelum ditanda lintah.
    Lintah adalah kad yang memakan banyak masa anda, dan apabila suatu kad
    ditanda lintah, digalakkan untuk sunting semula, padam atau cipta mnemonik
    bagi membantu anda mengingatinya.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `Tag Sahaja`: Tanda "leech" pada nota, dan paparkan pop timbul.
    
    `Bekukan Kad`: Selain daripada tanda nota, bekukan kad sehingga ia
    diaktifkan secara manual.

## Burying section

deck-config-bury-title = Sorok
deck-config-bury-new-siblings = Sorok adik beradik baru
deck-config-bury-review-siblings = Sork adik beradik semakan
deck-config-bury-interday-learning-siblings = Sork adik beradik belajar berhari
deck-config-bury-new-tooltip =
    Sama ada kad `baru` lain nota yang sama (cth kad terbalik, penghapusan kloz bersebelahan)
    akan ditunda sehingga hari esok.
deck-config-bury-review-tooltip = Sama ada kad `semakan`lain nota yang sama akan ditunda sehingga hari esok.
deck-config-bury-interday-learning-tooltip =
    Sama ada kad `belajar` lain nota yang sama dengan selang > 1 hari
    akan ditunda sehingga hari esok.
deck-config-bury-priority-tooltip =
    Apabila Anki kumpul kad, mula-mula kumpul kad belajar sehari, kemudian
    kad belajar berhari, kemudian semakan dan akhirnya kad baru. Ini mempengaruhi
    kaedah sorok:
    
    - Jika anda dayakan semua tetapan sorok, adik beradik paling awal dalam senarai
     akan ditunjuk. Contohnya, kad semakan akan diutamakan berbanding kad baru.
    - Adik beradik kemudian dalam senarai tidak boleh sorok jenis kad lebih awal.
     Contohnya, jika anda lumpuhkan sorok kad baru, dan ulang kaji suatu kad baru,
     kad belajar berhari atau kad semakan tidak akan disorokkan, dan mungkin
     terdapat adik beradik semakan dan adik beradik baru dalam sesi yang sama.

## Ordering section

deck-config-ordering-title = Susunan Tunjuk
deck-config-new-gather-priority = Susunan kumpul kad baru
deck-config-new-gather-priority-tooltip-2 =
    `Dek`: kumpul kad dari setiap dek ikut susunan, mula dari atas. Kad dari setiap dek
    dikumpulkan dalam posisi menaik. Jika had harian dek pilihan tercapai, mungkin
    berhenti kumpul sebelum semua dek diperiksa. Susunan ini paling pantas dalam koleksi
    besar bagi anda untuk utamakan subdek dekat ke atas.
    
    `Posisi menaik`: kumpul kad mengikut posisi menaik (# tunggak), biasanya
     tertua ditambah dahulu.
    
    `Posisi menurun`: kumpul kad mengikut posisi menurun (# tunggak), biasanya
     terbaru ditambah dahulu.
    
    `Nota rawak`: kumpul kad dari nota pilihan rawak. Apabila sorok adik beradik lumpuh,
    maka semua kad suatu nota ditunjuk dalam suatu sesi (e.g. kedua-dua kad front->back dan back->front)
    
    `Kad rawak`: kumpul kad secara rawak sepenuhnya.
deck-config-new-gather-priority-deck = Dek
deck-config-new-gather-priority-deck-then-random-notes = Dek kemudian nota rawak
deck-config-new-gather-priority-position-lowest-first = Posisi menaik
deck-config-new-gather-priority-position-highest-first = Posisi menurun
deck-config-new-gather-priority-random-notes = Nota rawak
deck-config-new-gather-priority-random-cards = Kad rawak
deck-config-new-card-sort-order = Susunan kad baru
deck-config-new-card-sort-order-tooltip-2 =
    `Jenis kad`: Tunjuk kad dalam susunan nombor jenis kad. Jika anda lumpuhkan adik beradik
    tersorok, ini akan pastikan semua kad front→back ditunjuk sebelum mana-mana kad back→front.
    Berguna bagi memastikan semua kad nota sama ditunjuk dalam sesi sama, tetapi tidak terlalu
    rapat.
    
    `Susunan pungutan`: Tunjuk kad ikut susunan dikumpulkan. Jika anda lumpuhkan adik beradik
    tersorok, ini akan pastikan semua kad suatu nota ditunjuk satu demi satu.
    
    `Jenis kad, kemudian rawak`: Seperti `Jenis kad`, tetapi kocok kad-kad setiap nombor jenis
    kad. Jika anda tetapkan `Susunan menaik` untuk pungut kad tertua, anda boleh guna tetapan
    ini untuk tunjuk kad tersebut dalam susunan rawak, tetapi pastikan semua kad nota sama tidak
    terlalu rapat.
    
    `Nota rawak, kemudian jenis kad`: Pilih nota secara rawak, kemudian tunjuk semua adik-beradik
    ikut susunan.
    
    `Rawak`: Kocok kad pungutan sepenuhnya
deck-config-sort-order-card-template-then-random = Jenis kad, kemudian rawak
deck-config-sort-order-random-note-then-template = Nota rawak, kemudian jenis kad
deck-config-sort-order-random = Rawak
deck-config-sort-order-template-then-gather = Jenis kad
deck-config-sort-order-gather = Susunan pungutan
deck-config-new-review-priority = Susunan baru/semakan
deck-config-new-review-priority-tooltip = Bila untuk tunjuk kad baru berbanding kad semakan
deck-config-interday-step-priority = Susunan belajar berhari/semakan
deck-config-interday-step-priority-tooltip =
    Bila tunjuk kad belajar (semula) lebihi sehari.
    
    Had semakan masih digunakan dahulu kepada kad belajar berhari, kemudian kad semakan.
    Tetapan ini akan kawal susunan kad pungutan yang akan ditunjuk, tetapi kad belajar
    berhari akan sentiasa dipungut terlebih dahlulu.
deck-config-review-mix-mix-with-reviews = Campur bersama semakan
deck-config-review-mix-show-after-reviews = Tunjuk selepas semakan
deck-config-review-mix-show-before-reviews = Tunjuk sebelum semakan
deck-config-review-sort-order = Susunan semakan
deck-config-review-sort-order-tooltip =
    Susunan lalai utamakan kad paling lama tunggu, maka jika anda ada semakan
    tertangguh, kad paling lama ditangguh akan ditunjuk dahulu. Jika kad tertangguh
    anda besar yang akan mengambil beberapa hari untuk selesaikan, atau mahu
    tunjuk kad dalam susunan subdek, susunan lain mungkin lebih baik.
deck-config-sort-order-due-date-then-random = Tarikh tunggakan, kemudian rawak
deck-config-sort-order-due-date-then-deck = Tarikh tunggakan, kemudian dek
deck-config-sort-order-deck-then-due-date = Dek, kemudian tarikh tunggakan
deck-config-sort-order-ascending-intervals = Selang menaik
deck-config-sort-order-descending-intervals = Selang menurun
deck-config-sort-order-ascending-ease = Longgaran menaik
deck-config-sort-order-descending-ease = Longgaran menurun
deck-config-sort-order-ascending-difficulty = Kesukaran menaik
deck-config-sort-order-descending-difficulty = Kesukaran menurun
deck-config-sort-order-relative-overdueness = Terlebih tunggak secara relatif
deck-config-display-order-will-use-current-deck =
    Anki akan menggunakan susunan tunjuk daripada
    dek anda ulang kaji, dan bukan subdeknya.

## Timer section

deck-config-timer-title = Pemasa
deck-config-maximum-answer-secs = Maksimum saat menjawab
deck-config-maximum-answer-secs-tooltip =
    Bilangan saat maksimum untuk rekod bagi suatu semakan. Jika jawapan lebih masa ini
    (contohnya, anda lakukan kerja lain), masa diambil akan dihadkan ikut
    tetapan anda.
deck-config-show-answer-timer-tooltip =
    Dalam skrin semakan, tunjuk pemasa yang kira bilangan saat diambil untuk
    semak setiap kad.

## Audio section

deck-config-audio-title = Audio
deck-config-disable-autoplay = Jangan mainkan audio secara automatik
deck-config-disable-autoplay-tooltip =
    Jika didayakan, Anki tidak akan mainkan audio secara automatik.
    Audio boleh dimainkan dengan menekan ikon audio, atau menggunakan tindakan main semula audio.
deck-config-skip-question-when-replaying = Langkau soalan apabila main semula jawapan
deck-config-always-include-question-audio-tooltip =
    Sama ada audio soalan sertai apabila tindakan main semula digunakan
    semasa melihat sisi jawapan kad.
deck-config-stop-timer-on-answer = Berhenti pemasa apabila menjawab
deck-config-stop-timer-on-answer-tooltip =
    Sama ada masa dihentikan apabila jawapan ditunjukkan.
    Ini tidak mengubah masa diambil.

## Advanced section

deck-config-advanced-title = Lanjutan
deck-config-maximum-interval-tooltip =
    Bilangan maksimum hari suatu kad semakan boleh ditangguh. Apabila
    semakan mencapai had, `Susah`, `Baik` dan `Senang` akan beri tundaan
    yang sama. Semakin singkat tetapan ini, semakin berat beban anda.
deck-config-starting-ease-tooltip =
    Pekali longgaran kad baru mulakan. Secara lalai, butang `Baik` pada kad baru
    tamat belajar akan tunda semakan seterusnya 2.5x tundaan sebelumnya.
deck-config-easy-bonus-tooltip = Pekali lebihan kepada selang kad semakan apabila anda jawab `Senang`
deck-config-interval-modifier-tooltip =
    Pekali ini digunakan dalam semua semakan, dan suaian kecil boleh
    digunakan agar Anki lebih agresif atau tidak dalam penjadualannya. Sila
    lihat manual sebelum mengubah tetapan ini.
deck-config-hard-interval-tooltip = Pekali kepada selang semakan apabila menjawab `Susah`.
deck-config-new-interval-tooltip = Pekali kepada selang semakan apabila menjawab `Ulang`.
deck-config-minimum-interval-tooltip = Selang minimum diberikan kepada kad semakan apabila menjawab `ulang`.

## Adding/renaming


## Removing


## Other Buttons


## These strings are shown via the Description button at the bottom of the
## overview screen.


## Warnings shown to the user


## Selecting a deck


## Messages related to the FSRS scheduler


## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

