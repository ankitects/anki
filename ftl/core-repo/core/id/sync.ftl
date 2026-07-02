### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = Ditambahkan: { $up }↑ { $down }↓
sync-media-removed-count = Dihapus: { $up }↑ { $down }↓
sync-media-checked-count = Diperiksa: { $count }
sync-media-starting = Sinkronisasi media sedang dimulai...
sync-media-complete = Sinkronisasi media telah selesai.
sync-media-failed = Sinkronisasi media telah gagal.
sync-media-aborting = Membatalkan sinkronisasi media...
sync-media-aborted = Sinkronisasi media telah dibatalkan.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = Sinkronisasi media telah dinonaktifkan.
# Title of the screen that shows syncing progress history
sync-media-log-title = Log sinkronisasi media

## Error messages / dialogs

sync-conflict = Anda hanya dapat melakukan sinkronisasi satu kali dalam satu waktu. Tolong tunggu beberapa menit, lalu coba lagi.
sync-server-error = Ankiweb telah mengalami masalah. Tolong coba lagi dalam beberapa menit.
sync-client-too-old = Versi Anki Anda terlalu lama. Harap perbarui ke versi terbaru untuk melanjutkan sinkronisasi.
sync-wrong-pass = Email atau sandi salah; tolong coba lagi.
sync-resync-required = Silakan sinkronkan lagi. Jika pesan ini terus muncul, harap melapor di situs dukungan.
sync-must-wait-for-end = Anki sedang melakukan sinkronisasi. Mohon tunggu hingga sinkronisasi selesai, lalu coba lagi.
sync-confirm-empty-download = Koleksi lokal tidak memiliki kartu. Unduh dari AnkiWeb?
sync-confirm-empty-upload = Koleksi AnkiWeb tidak memiliki kartu. Ganti dengan koleksi lokal?
sync-conflict-explanation =
    Koleksi-koleksi dek Anda di sini dan di AnkiWeb berbeda sehingga tidak dapat digabungkan, jadi perlu untuk menimpa kumpulan dek di satu sisi dengan kumpulan dek dari sisi lainnya.
    
    Jika Anda memilih unduh, Anki akan mengambil koleksi dari AnkiWeb, dan setiap perubahan yang telah Anda buat di perangkat ini sejak sinkronisasi terakhir ke Ankiweb akan hilang.
    
    Jika Anda memilih unggah, Anki akan mengirim data perangkat ini ke AnkiWeb, dan setiap perubahan yang menunggu di AnkiWeb akan hilang. 
    
    Setelah semua perangkat tersinkronisasi, tinjauan dan kartu baru di masa mendatang akan tersedia.…
sync-conflict-explanation2 =
    Terjadi konflik antara koleksi dek di perangkat ini dan AnkiWeb. Anda harus memilih versi mana yang akan disimpan:
    
    - Pilih **{ sync-download-from-ankiweb }** untuk mengganti koleksi dek di sini dengan versi AnkiWeb. Anda akan kehilangan semua perubahan yang Anda buat di perangkat ini semenjak sinkronisasi terakhir Anda.
    
    - Pilih **{ sync-upload-to-ankiweb }** untuk menimpa versi AnkiWeb dengan dek dari perangkat ini dan menghapus semua perubahan di AnkiWeb.
    
    Setelah konflik teratasi, sinkronisasi akan berfungsi seperti biasa.
sync-ankiweb-id-label = Email:
sync-password-label = Sandi:
sync-account-required =
    <h1>Akun Diperlukan</h1>
    Anda membutuhkan akun gratis untuk menjaga koleksi Anda tetap sinkron. Silakan <a href="{ $link }">daftar</a> untuk mendapatkan akun, lalu masukkan detail Anda di bawah ini.
sync-sanity-check-failed = Silakan gunakan fungsi Periksa Basis Data, lalu sinkronkan lagi. Jika masalah tetap berlanjut, silakan paksa sinkronisasi satu arah di pengaturan preferensi.
sync-clock-off = Tidak dapat melakukan sinkronisasi - Jam perangkat anda tidak disetel ke waktu yang benar.
# “details” expands to a string such as “300.14 MB > 300.00 MB”
sync-upload-too-large =
    File koleksi Anda terlalu besar untuk dikirim ke AnkiWeb. Anda dapat mengurangi ukurannya dengan menghapus dek yang tidak diinginkan (opsional, ekspor terlebih dahulu), lalu gunakan fungsi Periksa Basis Data untuk mengecilkan ukuran file.
    
    { $details } (belum dikompres)
sync-sign-in = Masuk
sync-ankihub-dialog-heading = Login ke Ankihub
sync-ankihub-username-label = Username atau Email:
sync-ankihub-login-failed = Tidak dapat masuk ke AnkiHub dengan kredensial yang diberikan.
sync-ankihub-addon-installation = Instalasi Add-on Ankihub

## Buttons

sync-media-log-button = Log media
sync-abort-button = Batalkan
sync-download-from-ankiweb = Unduh dari Ankiweb
sync-upload-to-ankiweb = Unggah ke AnkiWeb
sync-cancel-button = Batalkan

## Normal sync progress

sync-downloading-from-ankiweb = Mengunduh dari Ankiweb...
sync-uploading-to-ankiweb = Mengunggah ke AnkiWeb...
sync-syncing = Menyinkron...
sync-checking = Memeriksa...
sync-connecting = Menghubungkan...
sync-added-updated-count = Ditambahkan/dimodifikasi: { $up }↑ { $down }↓
sync-log-in-button = Login
sync-log-out-button = Logout
sync-collection-complete = Sinkronisasi koleksi telah berhasil.
