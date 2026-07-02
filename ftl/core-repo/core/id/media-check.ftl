## Shown at the top of the media check screen

media-check-window-title = Periksa Media
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count = Folder sampah: { $count } file, { $megs }MB
media-check-missing-count = File yang hilang: { $count }
media-check-unused-count = File yang tidak terpakai: { $count }
media-check-renamed-count = File yang diubah namanya: { $count }
media-check-oversize-count = Lebih dari 100MB: { $count }
media-check-subfolder-count = Subfolders: { $count }
media-check-extracted-count = Gambar diekstrak: { $count }

## Shown at the top of each section

media-check-renamed-header = Beberapa file telah diubah namanya untuk kompabilitas:
media-check-oversize-header = File yang ukurannya melebihi 100MB tidak dapat disinkronisasikan dengan AnkiWeb.
media-check-subfolder-header = Folder di dalam folder media tidak didukung.
media-check-missing-header = File-file berikut direferensikan oleh kartu, tetapi tidak ditemukan di folder media:
media-check-unused-header = File-file berikut ditemukan di folder media, tetapi tampaknya tidak digunakan pada kartu mana pun:
media-check-template-references-field-header =
    Anki tidak dapat mendeteksi file yang digunakan saat Anda menggunakan referensi { "{{Field}}" } dalam tag media/LaTeX. Tag media/LaTeX sebaiknya ditempatkan pada catatan individual.
    
    Referensi templat:

## Shown once for each file

media-check-renamed-file = Nama diubah: { $old } -> { $new }
media-check-oversize-file = Lebih dari 100MB: { $filename }
media-check-subfolder-file = Folder: { $filename }
media-check-missing-file = Tidak dapat ditemukan: { $filename }
media-check-unused-file = Tidak digunakan: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = Diperiksa: { $count }...

## Deleting unused media

media-check-delete-unused-confirm = Hapus media tidak terpakai?
media-check-files-remaining = { $count } file tersisa.
media-check-delete-unused-complete = { $count } file dipindahkan ke tempat sampah.
media-check-trash-emptied = Folder sampah saat ini kosong.
media-check-trash-restored = File yang dihapus telah dipulihkan kembali ke folder media.

## Rendering LaTeX

media-check-all-latex-rendered = Seluruh LaTeX telah dirender.

## Buttons

media-check-delete-unused = Hapus yang tidak digunakan
media-check-render-latex = Render LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = Kosongkan Sampah
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Pulihkan kembali file yang telah dihapus
media-check-check-media-action = Periksa Media
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = Media-hilang
# add a tag to notes with missing media
media-check-add-tag = Tag hilang
