## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount }ସେ
scheduling-answer-button-time-minutes = { $amount }ମି
scheduling-answer-button-time-hours = { $amount }ଘ
scheduling-answer-button-time-days = { $amount }ଦି
scheduling-answer-button-time-months = { $amount }ମା
scheduling-answer-button-time-years = { $amount }ବ

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] { $amount } ସେକେଣ୍ଡ
       *[other] { $amount } ସେକେଣ୍ଡ
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] { $amount } ମିନିଟ୍
       *[other] { $amount } ମିନିଟ୍
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } ଘଣ୍ଟା
       *[other] { $amount } ଘଣ୍ଟା
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } ଦିନ
       *[other] { $amount } ଦିନ
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } ମାସ
       *[other] { $amount } ମାସ
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } ବର୍ଷ
       *[other] { $amount } ବର୍ଷ
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    ପରବର୍ତ୍ତୀ ଶିକ୍ଷା ପତ୍ର { $unit ->
        [seconds]
            { $amount ->
                [one] { $amount } ସେକେଣ୍ଡ
               *[other] { $amount } ସେକେଣ୍ଡ
            }
        [minutes]
            { $amount ->
                [one] { $amount } ମିନିଟ
               *[other] { $amount } ମିନିଟ
            }
       *[hours]
            { $amount ->
                [one] { $amount } ଘଣ୍ଟା
               *[other] { $amount } ଘଣ୍ଟା
            }
    }ରେ ପ୍ରସ୍ତୁତ ହେବ।
scheduling-learn-remaining =
    { $remaining ->
        [one] ଆଜି ପାଇଁ ଗୋଟିଏ ଶିକ୍ଷା ପତ୍ର ବାକି ଅଛି।
       *[other] ଆଜି ପାଇଁ { $remaining }ଟି ଶିକ୍ଷା ପତ୍ର ବାକି ଅଛି।
    }
scheduling-congratulations-finished = ଅଭିନନ୍ଦନ! ଆପଣ ବର୍ତ୍ତମାନ ପାଇଁ ଏହି ଡେକ୍ ସମାପ୍ତ କରିଛନ୍ତି।
scheduling-today-review-limit-reached =
    ଆଜିର ସମୀକ୍ଷା ସୀମା ରେ ପହଞ୍ଚିଯାଇଛି, କିନ୍ତୁ ଏବେ ବି
    ପତ୍ର ସମୀକ୍ଷା କରିବାକୁ ଅପେକ୍ଷା କରୁଛି। ସର୍ବୋତ୍ସାହ୍ୟ ସ୍ମୃତି ପାଇଁ,
    ବିକଳ୍ପଗୁଡ଼ିକରେ ଦୈନିକ ସୀମା ବୃଦ୍ଧି କରିବାକୁ ବିଚାର କରନ୍ତୁ।
scheduling-today-new-limit-reached =
    ଅଧିକ ନୂଆ ପତ୍ର ଉପଲବ୍ଧ ଅଛି, କିନ୍ତୁ ଦୈନିକ ସୀମା ପହଞ୍ଚିସାରିଛି।
    ଆପଣ ବିକଳ୍ପଗୁଡ଼ିକରେ ସୀମା ବୃଦ୍ଧି କରିପାରିବେ, କିନ୍ତୁ ଦୟାକରି
    ମନେରଖନ୍ତୁ ଯେ ଆପଣ ଯେତେ ନୂତନ ପତ୍ର ଉପସ୍ଥାପନ କରିବେ,
    ଆପଣଙ୍କ ସ୍ୱଳ୍ପ ମିଆଦି ସମୀକ୍ଷା କାର୍ଯ୍ୟ ଭାର ଅଧିକ ହେବ।
scheduling-buried-cards-found = ଗୋଟିଏ କିମ୍ବା ଅଧିକ ପତ୍ର ସ୍ଥଗିତ ରଖାଗଲା, ଏବଂ ଆସନ୍ତାକାଲି ପ୍ରଦର୍ଶିତ ହେବ। ଯଦି ଆପଣ ସେମାନଙ୍କୁ ତୁରନ୍ତ ଦେଖିବାକୁ ଚାହାଁନ୍ତି ତେବେ ଆପଣ ସେମାନଙ୍କୁ { $unburyThem } କରିପାରିବେ।
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = ସେମାନଙ୍କୁ ଫେରାଇ ଆଣ
scheduling-how-to-custom-study = ଯଦି ଆପଣ ନିୟମିତ କାର୍ଯ୍ୟସୂଚୀ ବାହାରେ ଅଧ୍ୟୟନ କରିବାକୁ ଚାହାଁନ୍ତି, ତେବେ ଆପଣ { $customStudy } ବୈଶିଷ୍ଟ୍ୟ ବ୍ୟବହାର କରିପାରିବେ ।
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = କଷ୍ଟମ୍ ଅଧ୍ୟୟନ

## Scheduler upgrade

scheduling-update-soon = Anki 2.1 ଏକ ନୂତନ ଅନୁସୂଚକ ସହିତ ଆସିଥାଏ, ଯାହା ପୂର୍ବ Anki ସଂସ୍କରଣରେ ଥିବା ଅନେକ ସମସ୍ୟାକୁ ସମାଧାନ କରେ। ଏହାକୁ ଅଦ୍ୟତନ କରିବା ସୁପାରିଶ କରାଯାଇଛି।
scheduling-update-done = ଅନୁସୂଚକ ସଫଳତାର ସହିତ ଅଦ୍ୟତନ ହେଲା।
scheduling-update-button = ଅଦ୍ୟତନ କରନ୍ତୁ
scheduling-update-later-button = ପରେ
scheduling-update-more-info-button = ଅଧିକ ଜାଣନ୍ତୁ

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = ଅଡ଼ିଓ ପୁନଃଚାଳନ କରିବା ସମୟରେ ସର୍ବଦା ପ୍ରଶ୍ନ ପାର୍ଶ୍ୱ ଅନ୍ତର୍ଭୁକ୍ତ କରନ୍ତୁ
scheduling-at-least-one-step-is-required = ଅତି କମରେ ୧ଟିଏ ପାହୁଣ୍ଡ ଆବଶ୍ୟକ
scheduling-automatically-play-audio = ସ୍ୱୟଂଚାଳିତ ଭାବରେ ଅଡ଼ିଓ ଚଲାନ୍ତୁ
scheduling-bury-related-new-cards-until-the = ସମ୍ପୃକ୍ତ ନୂତନ ପତ୍ରଗୁଡ଼ିକୁ ତା' ବାସିଦିନ ପର୍ଯ୍ୟନ୍ତ ସ୍ଥଗିତ କରନ୍ତୁ
scheduling-bury-related-reviews-until-the-next = ସମ୍ପୃକ୍ତ ସମୀକ୍ଷାଗୁଡ଼ିକୁ ତା' ବାସିଦିନ ପର୍ଯ୍ୟନ୍ତ ସ୍ଥଗିତ କରନ୍ତୁ
scheduling-description = ବର୍ଣ୍ଣନା
scheduling-easy-interval = ସହଜ ଅନ୍ତରାଳ
scheduling-end = (ଶେଷ)
scheduling-general = ସାଧାରଣ
scheduling-graduating-interval = ସ୍ନାତକୋତ୍ତର ଅନ୍ତରାଳ
scheduling-hard-interval = କଠିନ ଅନ୍ତରାଳ
scheduling-interval-modifier = ଅନ୍ତରାଳ ସଂଶୋଧକ
scheduling-lapses = ଲାପ୍ସ ସଂଖ୍ୟା
scheduling-lapses2 = ଲାପ୍ସ ସଂଖ୍ୟା
scheduling-leech-action = ଜୋକ (leech) କାର୍ଯ୍ୟ
scheduling-leech-threshold = ଜୋକ (leech) ଦୁଆରସୀମା
scheduling-maximum-interval = ସର୍ବାଧିକ ଅନ୍ତରାଳ
scheduling-maximum-reviewsday = ସର୍ବାଧିକ ସମୀକ୍ଷା/ଦିନ
scheduling-minimum-interval = ସର୍ବନିମ୍ନ ଅନ୍ତରାଳ
scheduling-mix-new-cards-and-reviews = ନୂଆ ପତ୍ର ଏବଂ ସମୀକ୍ଷାସବୁ ର ମିଶ୍ରଣ
scheduling-new-cards = ନୂଆ ପତ୍ର
scheduling-new-cardsday = ନୂତନ ପତ୍ର/ଦିନ
scheduling-new-interval = ନୂତନ ଅନ୍ତରାଳ
scheduling-new-options-group-name = ନୂତନ ବିକଳ୍ପ ଗୋଷ୍ଠୀ ନାମ:
scheduling-options-group = ବିକଳ୍ପ ଗୋଷ୍ଠୀ:
scheduling-order = କ୍ରମ
scheduling-parent-limit = (ସର୍ବାଧିକ ସୀମା: { $val })
scheduling-restore-position = ଯେଉଁଠାରେ ସମ୍ଭଵ ମୂଳ ଅଵସ୍ଥିତି ପୁନରୁଦ୍ଧାର କରନ୍ତୁ
scheduling-review = ସମୀକ୍ଷା
scheduling-reviews = ସମୀକ୍ଷା
scheduling-seconds = ସେକେଣ୍ଡ
scheduling-set-all-decks-below-to = { $val } ତଳେ ଥିବା ସମସ୍ତ ଡେକଗୁଡ଼ିକୁ ଏହି ବିକଳ୍ପ ଗୋଷ୍ଠୀକୁ ସେଟ୍ କରିବେ କି?
scheduling-show-answer-timer = ଉତ୍ତର ଟାଇମର୍ ଦେଖାନ୍ତୁ
scheduling-show-new-cards-after-reviews = ସମୀକ୍ଷା ପରେ ନୂତନ ପତ୍ରଗୁଡ଼ିକ ଦେଖାନ୍ତୁ
scheduling-show-new-cards-before-reviews = ସମୀକ୍ଷା ପୂର୍ବରୁ ନୂତନ ପତ୍ରଗୁଡ଼ିକ ଦେଖାନ୍ତୁ
scheduling-show-new-cards-in-order-added = ଯୋଡ଼ାଯାଇଥିବା କ୍ରମରେ ନୂତନ ପତ୍ରଗୁଡ଼ିକ ଦେଖାନ୍ତୁ
scheduling-show-new-cards-in-random-order = ଅନିୟମିତ କ୍ରମରେ ନୂତନ ପତ୍ରଗୁଡ଼ିକ ଦେଖାନ୍ତୁ
scheduling-starting-ease = ପ୍ରାରମ୍ଭିକ ସହଜତା
scheduling-steps-in-minutes = ପାହୁଣ୍ଡ (ମିନିଟ୍ ରେ)
scheduling-steps-must-be-numbers = ପାହୁଣ୍ଡଟି ସଂଖ୍ୟା ହେବା ଜରୁରୀ।
scheduling-tag-only = କେବଳ ଟ୍ୟାଗ୍ କରନ୍ତୁ
scheduling-the-default-configuration-cant-be-removed = ଡିଫଲ୍ଟ ବିନ୍ୟାସୀକରଣ ଅପସାରଣ ହୋଇପାରିବ ନାହିଁ।
scheduling-your-changes-will-affect-multiple-decks = ଆପଣଙ୍କ ପରିବର୍ତ୍ତନଗୁଡ଼ିକ ଏକାଧିକ ତାସଖଣ୍ଡକୁ ପ୍ରଭାବିତ କରିବ। ଯଦି ଆପଣ କେବଳ ଚଳିତ ତାସଖଣ୍ଡକୁ ପରିବର୍ତ୍ତନ କରିବାକୁ ଚାହୁଁଛନ୍ତି, ଦୟାକରି ପ୍ରଥମେ ଗୋଟେ ନୂଆ ବିକଳ୍ପ ଗୋଷ୍ଠୀ ଯୋଡ଼ନ୍ତୁ।
scheduling-deck-updated =
    { $count ->
        [one] { $count }ଟିଏ ଡେକ୍ ଅଦ୍ୟତନ ହୋଇଛି।
       *[other] { $count }ଟି ଡେକ୍ ଅଦ୍ୟତନ ହୋଇଛି।
    }
scheduling-set-due-date-prompt =
    { $cards ->
        [one] କେତେ ଦିନ ମଧ୍ୟରେ ପତ୍ର ଦେଖାନ୍ତୁ?
       *[other] କେତେ ଦିନ ମଧ୍ୟରେ ପତ୍ରଗୁଡ଼ିକୁ ଦେଖାନ୍ତୁ?
    }
scheduling-set-due-date-prompt-hint =
    0 = ଆଜି
    1! = ଆସନ୍ତାକାଲି+ସମୀକ୍ଷା ଅନ୍ତରାଳ ପୁନଃସେଟ୍ କର
    3-7 = 3-7 ଦିନ ଭିତରୁ ଅନିୟମିତ ପସନ୍ଦ
scheduling-set-due-date-done =
    { $cards ->
        [one] { $cards }ଟିଏ ପତ୍ରର ଧାର୍ଯ୍ୟ ତାରିଖ ସେଟ୍ କରନ୍ତୁ।
       *[other] { $cards }ଟି ପତ୍ରର ଧାର୍ଯ୍ୟ ତାରିଖ ସେଟ୍ କରନ୍ତୁ।
    }
scheduling-forgot-cards =
    { $cards ->
        [one] { $cards }ଟିଏ ପତ୍ର ଭୁଲିଯାଇଛି।
       *[other] { $cards }ଟି ପତ୍ର ଭୁଲିଯାଇଛି।
    }
