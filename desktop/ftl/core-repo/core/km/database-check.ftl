database-check-corrupt = បណ្ដុំឯកសារខូចហើយ។​ សូមស្តារពីឯកសារបម្រុងទុកដោយស្វ័យប្រវត្តិ។
database-check-rebuilt = ឃ្លាំងទិន្នន័យត្រូវបានបង្កើតវិញ និងបានធ្វើឱ្យប្រសើរឡើង។
database-check-card-properties =
    { $count ->
        [one] បានជួសជុលលក្ខណៈកាតដែលមិនត្រឹមត្រូវចំនួន { $count }។
       *[other] បានជួសជុលលក្ខណៈកាតដែលមិនត្រឹមត្រូវចំនួន { $count }។
    }
database-check-missing-templates =
    { $count ->
        [one] បានលុបកាតដែលគ្មានគំរូចំនួន { $count }។
       *[other] បានលុបកាតដែលគ្មានគំរូចំនួន { $count }។
    }
database-check-field-count =
    { $count ->
        [one] បានជួសជុលកំណត់ត្រា { $count } ដែលមានចំនួនប្រអប់សំណួរ-ចម្លើយខុស។
       *[other] បានជួសជុលកំណត់ត្រា { $count } ដែលមានចំនួនប្រអប់សំណួរ-ចម្លើយខុស។
    }
database-check-new-card-high-due =
    { $count ->
        [one] បានរកឃើញកាតថ្មីចំនួន { $count } ដែលមានលេខផុតកំណត់ >= 1,000,000 - ពិចារណាប្ដូរទីតាំងវាក្នុងអេក្រង់រុករក។
       *[other] បានរកឃើញកាតថ្មីចំនួន { $count } ដែលមានលេខផុតកំណត់ >= 1,000,000 - ពិចារណាប្ដូរទីតាំងវាក្នុងអេក្រង់រុករក។
    }
database-check-card-missing-note =
    { $count ->
        [one] បានលុបកាតចំនួន { $count } ដែលគ្មានកំណត់ត្រា។
       *[other] បានលុបកាតចំនួន { $count } ដែលគ្មានកំណត់ត្រា។
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] បានលុបកាតចំនួន { $count } ដែលមានគំរូស្ទួន។
       *[other] បានលុបកាតចំនួន { $count } ដែលមានគំរូស្ទួន។
    }
database-check-missing-decks =
    { $count ->
        [one] បានជួសជុលហ៊ូដែលបាត់ចំនួន { $count }។
       *[other] បានជួសជុលហ៊ូដែលបាត់ចំនួន { $count }។
    }
database-check-revlog-properties =
    { $count ->
        [one] បានជួលជុលការចូលរំឭកចំនួន { $count } ដែលមានលក្ខណៈមិនត្រឹមត្រូវ។
       *[other] បានជួលជុលការចូលរំឭកចំនួន { $count } ដែលមានលក្ខណៈមិនត្រឹមត្រូវ។
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] បានជួសជុលកំណត់ត្រាចំនួន { $count } ដែលមានតួអក្សរ utf8 មិនត្រឹមត្រូវ។
       *[other] បានជួសជុលកំណត់ត្រាចំនួន { $count } ដែលមានតួអក្សរ utf8 មិនត្រឹមត្រូវ។
    }
database-check-fixed-invalid-ids =
    { $count ->
        [one] បានជួសជុលវត្ថុចំនួន { $count } ដែលមានត្រាពេលវេលាក្នុងអនាគត។
       *[other] បានជួសជុលវត្ថុចំនួន { $count } ដែលមានត្រាពេលវេលាក្នុងអនាគត។
    }
# "db-check" is always in English
database-check-notetypes-recovered = ប្រភេទកំណត់ត្រាមួយ ឬច្រើនត្រូវបានបាត់។ កំណត់ត្រាដែលបានប្រើប្រភេទទាំងនោះត្រូវបានផ្តល់ជូននូវប្រភេទថ្មីដែលផ្តើមដោយ "db-check" តែឈ្មោះប្រអប់សំណួរ-ចម្លើយ និងការរចនាកាតត្រូវបានបាត់បង់ ដូច្នេះអ្នកគួរស្ដារពីការបម្រុងទុកដោយស្វ័យប្រវត្តិវិញ។

## Progress info

database-check-checking-integrity = កំពុងពិនិត្យសំណុំ...
database-check-rebuilding = កំពុងបង្កើតឡើងវិញ...
database-check-checking-cards = កំពុង​ពិនិត្យ​កាត...
database-check-checking-notes = កំពុង​ពិនិត្យ​កំណត់ត្រា...
database-check-checking-history = កំពុងពិនិត្យប្រវត្តិ...
database-check-title = ពិនិត្យឃ្លាំងទិន្នន័យ
