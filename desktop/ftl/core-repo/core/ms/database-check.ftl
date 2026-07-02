database-check-corrupt = Fail koleksi rosak. Sila pulih dari sandaran automatik.
database-check-rebuilt = Pangkalan data dibina semula dan dioptimumkan.
database-check-card-properties =
    { $count ->
       *[other] Telah baiki { $count } ciri-ciri kad tidak sah.
    }
database-check-missing-templates =
    { $count ->
       *[other] Telah buang { $count } kad tiada templat.
    }
database-check-field-count =
    { $count ->
       *[other] Telah baiki { $count } nota dengan bilangan medan salah.
    }
database-check-new-card-high-due =
    { $count ->
       *[other] Jumpa { $count } kad baru dengan nombor tunggak >= 1,000,000 - pertimbangkan posisi semula kad tersebut dalam skrin Pencari.
    }
database-check-card-missing-note =
    { $count ->
       *[other] Telah buang { $count } kad tiada nota.
    }
database-check-duplicate-card-ords =
    { $count ->
       *[other] Telah buang { $count } kad dengan templat sama.
    }
database-check-missing-decks =
    { $count ->
       *[other] Telah baiki { $count } dek hilang.
    }
database-check-revlog-properties =
    { $count ->
       *[other] Telah baiki { $count } entri semakan dengan ciri-ciri tidak sah.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
       *[other] Telah baiki { $count } nota dengan aksara utf8 tidak sah.
    }
database-check-fixed-invalid-ids =
    { $count ->
       *[other] Telah baiki { $count } benda dengan cap masa dalam masa hadapan.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Satu atau lebih jenis nota tiada. Nota yang sebelum ini gunanya telah diberikan jenis nota baru bermula dengan "db-check", tetapi nama medan dan penggayaan kad hilang, jadi anda mungkin lebih baik pulih dari sandaran automatik.

## Progress info

database-check-checking-integrity = Periksa koleksi...
database-check-rebuilding = Bina semula...
database-check-checking-cards = Periksa kad...
database-check-checking-notes = Periksa nota...
database-check-checking-history = Periksa sejarah...
database-check-title = Periksa Pangkalan Data
