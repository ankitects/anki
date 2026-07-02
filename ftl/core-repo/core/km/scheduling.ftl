## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount }វិនាទី
scheduling-answer-button-time-minutes = { $amount }នាទី
scheduling-answer-button-time-hours = { $amount }ម៉ោង
scheduling-answer-button-time-days = { $amount }ថ្ងៃ
scheduling-answer-button-time-months = { $amount }ខែ
scheduling-answer-button-time-years = { $amount }ឆ្នាំ

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] { $amount } វិនាទី
       *[other] { $amount } វិនាទី
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] { $amount } នាទី
       *[other] { $amount } នាទី
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } ម៉ោង
       *[other] { $amount } ម៉ោង
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } ថ្ងៃ
       *[other] { $amount } ថ្ងៃ
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } ខែ
       *[other] { $amount } ខែ
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } ឆ្នាំ
       *[other] { $amount } ឆ្នាំ
    }

## Shown in the "Congratulations!" message after study finishes.

# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = មិនកប់វា
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = សិក្សាតាមបំណង

## Scheduler upgrade

scheduling-update-button = ធ្វើបច្ចុប្បន្នភាព
scheduling-update-more-info-button = ស្វែងយល់បន្ថែម

## Other scheduling strings

scheduling-automatically-play-audio = ចាក់អូឌីយ៉ូដោយស្វ័យប្រវត្តិ
scheduling-days = ថ្ងៃ
scheduling-description = ការពិពណ៌នា
scheduling-end = (ចប់)
scheduling-general = ទូទៅ
scheduling-learning = ការរៀន
scheduling-new-cards = កាតថ្មី
scheduling-new-cardsday = កាតថ្មី/ថ្ងៃ
scheduling-order = លំដាប់
scheduling-review = រំឭក
scheduling-reviews = ការរំឭក
scheduling-seconds = វិនាទី
scheduling-show-new-cards-after-reviews = បង្ហាញកាតថ្មីក្រោយការរំឭក
scheduling-show-new-cards-before-reviews = បង្ហាញកាតថ្មីមុនការរំឭក
scheduling-show-new-cards-in-order-added = បង្ហាញកាតថ្មីតាមលំដាប់ដែលបានបន្ថែម
scheduling-show-new-cards-in-random-order = បង្ហាញកាតថ្មីតាមលំដាប់ចៃដន្យ
scheduling-tag-only = ស្លាកប៉ុណ្ណោះ
scheduling-deck-updated =
    { $count ->
        [one] { $count } ហ៊ូត្រូវបានធ្វើបច្ចុប្បន្នភាព។
       *[other] { $count } ហ៊ូត្រូវបានធ្វើបច្ចុប្បន្នភាព។
    }
scheduling-set-due-date-prompt =
    { $cards ->
        [one] បង្ហាញកាតក្នុងរយៈពេលប៉ុន្មានថ្ងៃ?
       *[other] បង្ហាញកាតក្នុងរយៈពេលប៉ុន្មានថ្ងៃ?
    }
scheduling-forgot-cards =
    { $cards ->
        [one] កំណត់ឡើងវិញនូវកាតចំនួន { $cards }។
       *[other] កំណត់ឡើងវិញនូវកាតចំនួន { $cards }។
    }
