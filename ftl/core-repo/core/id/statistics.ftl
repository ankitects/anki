# The date a card will be ready to review
statistics-due-date = Jatuh tempo
# The count of cards waiting to be reviewed
statistics-due-count = Jatuh tempo
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = Baru #{ $number }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } kartu/menit
statistics-average-answer-time = { $average-seconds }dtk ({ statistics-cards-per-min })

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds = dalam { $amount } detik
statistics-in-time-span-minutes = dalam { $amount } menit
statistics-in-time-span-hours = dalam { $amount } jam
statistics-in-time-span-days = dalam { $amount } hari
statistics-in-time-span-months = dalam { $amount } bulan
statistics-in-time-span-years = dalam { $amount } tahun
# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { statistics-in-time-span-seconds } part should be pasted in from the English
# version unmodified.
statistics-studied-today =
    { $unit ->
        [seconds]
            Hari ini, sebanyak { statistics-cards } telah dipelajari
            { statistics-in-time-span-seconds }
            ({ $secs-per-card }dtk/kartu)
        [minutes]
            Hari ini, sebanyak { statistics-cards } telah dipelajari
            { statistics-in-time-span-minutes }
            ({ $secs-per-card }dtk/kartu)
        [hours]
            Hari ini, sebanyak { statistics-cards } telah dipelajari
            { statistics-in-time-span-hours }
            ({ $secs-per-card }dtk/kartu)
        [days]
            Hari ini, sebanyak { statistics-cards } telah dipelajari
            { statistics-in-time-span-days }
            ({ $secs-per-card }dtk/kartu)
        [months]
            Hari ini, sebanyak { statistics-cards } telah dipelajari
            { statistics-in-time-span-months }
            ({ $secs-per-card }dtk/kartu)
       *[years]
            Hari ini, sebanyak { statistics-cards } telah dipelajari
            { statistics-in-time-span-years }
            ({ $secs-per-card }dtk/kartu)
    }

##

statistics-cards = { $cards } kartu
statistics-notes = { $notes } catatan
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews = { $reviews } ulasan
# This fragment of the tooltip in the FSRS simulation
# diagram (Deck options -> FSRS) shows the total number of
# cards that can be recalled or retrieved on a specific date.
statistics-memorized = sebanyak { $memorized } kartu dapat diingat
statistics-today-title = Hari ini
statistics-today-again-count = Mengulang lagi:
statistics-today-type-counts = Pelajari: { $learnCount }, Ulasan: { $reviewCount }, Pelajari Ulang: { $relearnCount }, Difilter: { $filteredCount }
statistics-today-no-cards = Tidak ada kartu yang dipelajari hari ini.
statistics-today-no-mature-cards = Tidak ada kartu yang sudah matang yang dipelajari hari ini.
statistics-today-correct-mature = Jawaban benar pada kartu yang sudah matang: { $correct }/{ $total } ({ $percent }%)
statistics-counts-total-cards = Total
statistics-counts-new-cards = Baru
statistics-counts-young-cards = Belum matang
statistics-counts-mature-cards = Matang
statistics-counts-suspended-cards = Ditangguhkan
statistics-counts-buried-cards = Terkubur
statistics-counts-filtered-cards = Ter-filter
statistics-counts-learning-cards = Sedang dipelajari
statistics-counts-relearning-cards = Dipelajari ulang
statistics-counts-title = Jumlah kartu
statistics-counts-separate-suspended-buried-cards = Pisahkan kartu yang ditangguhkan/dikubur

## Retention represents your actual retention from past reviews, in
## comparison to the "desired retention" setting of FSRS, which forecasts
## future retention. Retention is the percentage of all reviewed cards
## that were marked as "Hard," "Good," or "Easy" within a specific time period.
##
## Most of these strings are used as column / row headings in a table.
## (Excluding -title and -subtitle)
## It is important to keep these translations short so that they do not make
## the table too large to display on a single stats card.
##
## N.B. Stats cards may be very small on mobile devices and when the Stats
##      window is certain sizes.

statistics-true-retention-title = Retensi
statistics-true-retention-subtitle = Tingkat kelulusan kartu dengan interval ≥ 1 hari.
statistics-true-retention-tooltip = Jika Anda mengaktifkan FSRS, tingkat retensi Anda diharapkan dapat mendekati tingkat retensi yang Anda inginkan. Harap diingat bahwa data untuk satu hari cenderung tidak akurat, jadi lebih baik melihat data bulanan.
statistics-true-retention-range = Jangkauan
statistics-true-retention-pass = Lulus
statistics-true-retention-fail = Gagal
# This will usually be the same as statistics-counts-total-cards
statistics-true-retention-total = Total
statistics-true-retention-count = Jumlah
statistics-true-retention-retention = Retensi
# This will usually be the same as statistics-counts-young-cards
statistics-true-retention-young = Belum matang
# This will usually be the same as statistics-counts-mature-cards
statistics-true-retention-mature = Matang
statistics-true-retention-all = Semua
statistics-true-retention-today = Hari ini
statistics-true-retention-yesterday = Kemarin
statistics-true-retention-week = Minggu lalu
statistics-true-retention-month = Bulan lalu
statistics-true-retention-year = Tahun lalu
statistics-true-retention-all-time = Sepanjang masa
# If there are no reviews within a specific time period, the retention
# percentage cannot be calculated and is displayed as "N/A."
statistics-true-retention-not-applicable = N/A

##

statistics-range-all-time = semua
statistics-range-1-year-history = 12 bulan terakhir
statistics-range-all-history = Sepanjang waktu
statistics-range-deck = dek
statistics-range-collection = koleksi
statistics-range-search = Cari
statistics-card-ease-title = Tingkat kemudahan kartu
statistics-card-difficulty-title = Tingkat kesulitan kartu
statistics-card-stability-title = Stabilitas kartu
statistics-card-stability-subtitle = Penundaan di mana kemampuan meingat turun hingga 90%.
statistics-median-stability = Stabilitas median
statistics-card-retrievability-title = Tingkat kemudahan mengingat kartu
statistics-card-ease-subtitle = Semakin rendah tingkat kemudahannya, semakin sering kartu tersebut akan muncul.
statistics-card-difficulty-subtitle2 = Semakin tinggi tingkat kesulitannya, semakin lambat stabilitas akan meningkat.
statistics-retrievability-subtitle = Kemungkinan kemampuan mengingat kartu hari ini.
# eg "3 cards with 150-170% ease"
statistics-card-ease-tooltip = { $cards } kartu dengan tingkat kemudahan { $percent }
statistics-card-difficulty-tooltip = { $cards } kartu dengan tingkat kesulitan { $percent }
statistics-retrievability-tooltip = { $cards } kartu dengan tingkat kemampuan mengingat { $percent }
statistics-future-due-title = Akan jatuh tempo
statistics-future-due-subtitle = Jumlah kartu yang akan ditinjau di masa mendatang.
statistics-added-title = Ditambahkan
statistics-added-subtitle = Jumlah kartu baru yang telah Anda tambahkan.
statistics-reviews-count-subtitle = Jumlah pertanyaan yang telah Anda jawab.
statistics-reviews-time-subtitle = Waktu yang dibutuhkan untuk menjawab pertanyaan.
statistics-answer-buttons-title = Tombol jawaban
# eg Button: 4
statistics-answer-buttons-button-number = Tombol
# eg Times pressed: 123
statistics-answer-buttons-button-pressed = Berapa kali ditekan
statistics-answer-buttons-subtitle = Jumlah berapa kali Anda menekan setiap tombol.
statistics-reviews-title = Ulasan
statistics-reviews-time-checkbox = Waktu
statistics-in-days-single =
    { $days ->
        [1] Besok
        [0] Hari ini
       *[other] Dalam { $days } hari
    }
statistics-in-days-range = Dalam { $daysStart }-{ $daysEnd } hari
statistics-days-ago-single =
    { $days ->
        [1] Kemarin
       *[other] { $days } hari yang lalu
    }
statistics-days-ago-range = { $daysStart }-{ $daysEnd } hari yang lalu
statistics-running-total = Jumlah kumulatif
statistics-cards-due = { $cards } kartu jatuh tempo
statistics-backlog-checkbox = Hutang
statistics-intervals-title = Interval Ulasan
statistics-intervals-subtitle = Penundaan hingga kartu ulasan akan ditampilkan kembali.
statistics-intervals-day-range = { $cards } kartu dengan jeda { $daysStart }-{ $daysEnd } hari
statistics-intervals-day-single = { $cards } kartu dengan jeda { $day } hari
statistics-stability-day-range = { $cards } kartu dengan tingkat stabilitas { $daysStart }-{ $daysEnd } hari
statistics-stability-day-single = { $cards } kartu dengan tingkat stabilitas { $day } hari
# hour range, eg "From 14:00-15:00"
statistics-hours-range = Dari { $hourStart }:00~{ $hourEnd }:00
statistics-hours-correct = { $correct }/{ $total } benar ({ $percent }%)
statistics-hours-correct-info = → (bukan 'Lagi')
# the emoji depicts the graph displaying this number
statistics-hours-reviews = 📊 { $reviews } ulasan
# the emoji depicts the graph displaying this number
statistics-hours-correct-reviews = 📈 { $percent }% benar ({ $reviews })
statistics-hours-title = Rincian Per Jam
statistics-hours-subtitle = Tinjau tingkat keberhasilan untuk setiap jam dalam sehari.
# shown when graph is empty
statistics-no-data = TIDAK ADA DATA
statistics-calendar-title = Kalender

## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.

statistics-elapsed-time-seconds = { $amount }dtk
statistics-elapsed-time-minutes = { $amount }mnt
statistics-elapsed-time-hours = { $amount }jam
statistics-elapsed-time-days = { $amount }hr
statistics-elapsed-time-months = { $amount }bln
statistics-elapsed-time-years = { $amount }thn

##

statistics-average-for-days-studied = Rata-rata jumlah berapa hari Anda belajar
# This term is used in a variety of contexts to refers to the total amount of
# items (e.g., cards, mature cards, etc) for a given period, rather than the
# total of all existing items.
statistics-total = Total
statistics-days-studied = Berapa hari Anda belajar
statistics-average-answer-time-label = Waktu respons rata-rata
statistics-average = Rata-rata
statistics-median-interval = Interval median
statistics-due-tomorrow = Jatuh tempo besok
# This string, ‘Daily load,’ appears in the ‘Future due’ table and represents a
# forecasted estimate of the number of cards expected to be reviewed daily in 
# the future. Unlike the other strings in the table that display actual data 
# derived from the current scheduling (e.g., ‘Average’, ‘Due tomorrow’),
# ‘Daily load’ is a projection based on the given data.
statistics-daily-load = Beban harian
# eg 5 of 15 (33.3%)
statistics-amount-of-total-with-percentage = { $amount } dari { $total } ({ $percent }%)
statistics-average-over-period = Rata-rata selama periode tersebut
statistics-reviews-per-day = { $count } ulasan/hari
statistics-minutes-per-day = { $count } menit/hari
statistics-cards-per-day = { $count } kartu/hari
statistics-median-ease = Median tingkat kemudahan
statistics-median-difficulty = Median tingkat kesulitan
statistics-average-retrievability = Rata-rata kemampuan mengingat
statistics-estimated-total-knowledge = Estimasi total pengetahuan
statistics-save-pdf = Simpan sebagai PDF
statistics-saved = Berhasil disimpan.
statistics-stats = statistik
statistics-title = Statistik

## These strings are no longer used - you do not need to translate them if they
## are not already translated.

statistics-average-stability = Rata-rata stabilitas
statistics-average-interval = Rata-rata interval
statistics-average-ease = Rata-rata tingkat kemudahan
statistics-average-difficulty = Rata-rata tingkat kesulitan
