### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    { $decks ->
        [one] { $decks }ଟିଏ ଡେକ୍ ଦ୍ୱାରା ବ୍ୟବହୃତ
       *[other] { $decks }ଟି ଡେକ୍ ଦ୍ୱାରା ବ୍ୟବହୃତ
    }
deck-config-default-name = ଡିଫଲ୍ଟ
deck-config-title = ଡେକ୍ ବିକଳ୍ପଗୁଡ଼ିକ

## Daily limits section

deck-config-daily-limits = ଦୈନିକ ସୀମା
deck-config-review-limit-tooltip =
    ଗୋଟିଏ ଦିନରେ ଦେଖାଇବାକୁ ସର୍ବାଧିକ ସମୀକ୍ଷା ପତ୍ରର ସଂଖ୍ୟା,
    ଯଦି ପତ୍ରଗୁଡ଼ିକ ସମୀକ୍ଷା ପାଇଁ ପ୍ରସ୍ତୁତ ଅଟନ୍ତି।
deck-config-limit-deck-v3 =
    ଏକ ଡେକ୍ ଅଧ୍ୟୟନ କରିବାବେଳେ ଏହାର ଭିତରେ ଉପଡେକ୍ ଥାଏ, ପ୍ରତ୍ୟେକ
    ଉପଡେକ୍ ଉପରେ ନିର୍ମିତ ସୀମା ସେହି ନିର୍ଦ୍ଦିଷ୍ଟ ଡେକ୍ ରୁ ନିଆଯାଇଥିବା ସର୍ବାଧିକ ପତ୍ରସଂଖ୍ୟାକୁ ନିୟନ୍ତ୍ରଣ କରିଥାଏ।
    ମନୋନୀତ ଡେକ୍ ର ସୀମା ଦେଖାଯିବାକୁ ଥିବା ମୋଟ ପତ୍ରସଂଖ୍ୟା ନିୟନ୍ତ୍ରଣ କରେ।
deck-config-affects-entire-collection = ସମଗ୍ର ସଂଗ୍ରହକୁ ପ୍ରଭାଵିତ କରେ।

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-deck-only = ଏହି ତାସଖଣ୍ଡ
deck-config-today-only = କେଵଳ ଆଜି

## New Cards section

deck-config-learning-steps = ଶିଖିବା ପାହୁଣ୍ଡ
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = ବିଳମ୍ବ ସାଧାରଣତଃ ମିନିଟ୍ (ଯଥା `1ମି`) କିମ୍ବା ଦିନ (ଯଥା `2ଦି`), କିନ୍ତୁ ଘଣ୍ଟା (ଯଥା `1ଘ`) ଏବଂ ସେକେଣ୍ଡ (ଯଥା `30ସେ`) ମଧ୍ୟ ସମର୍ଥିତ।
deck-config-new-insertion-order = ସନ୍ନିବେଶ କ୍ରମ
deck-config-new-insertion-order-sequential = କ୍ରମିକ (ପ୍ରଥମେ ପୁରାତନ ପତ୍ର)

## Lapses section

deck-config-leech-threshold-tooltip = ଏକ ଜୋକ (leech) ବୋଲି ଚିହ୍ନିତ ହେବା ପୂର୍ବରୁ ଏକ ସମୀକ୍ଷା ପତ୍ରରେ କେତେଥର `ପୁଣି` ଦବାଇବା ଆବଶ୍ୟକ। ଜୋକ ହେଉଛି ପତ୍ର ଯାହା ଆପଣଙ୍କର ବହୁତ ସମୟ ଖାଇଥାଏ, ଏବଂ ଯେତେବେଳେ ଏକ ପତ୍ର ଏକ ଜୋକ ବୋଲି ଚିହ୍ନିତ ହୁଏ, ଏହାକୁ ପୁନଃଲିଖନ କରିବା, ଏହାକୁ ବିଲୋପ କରିବା, କିମ୍ବା ଏହାକୁ ମନେ ରଖିବା ପାଇଁ ଏକ mnemonic ଭାବିବା ଏକ ଉତ୍ତମ ବିଚାର।
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `କେବଳ ଟ୍ୟାଗ୍ କରନ୍ତୁ`: ନୋଟ୍ ରେ ଏକ "leech" ଟ୍ୟାଗ୍ ଯୋଡ଼ାଯାଇ, ଏକ ପପ୍-ଅପ୍ ପ୍ରଦର୍ଶନ କରାଯିବ।
    
    `ପତ୍ର ନିଲମ୍ବିତ କରନ୍ତୁ`: ନୋଟ୍ ଟ୍ୟାଗ୍ କରିବା ବ୍ୟତୀତ, ପତ୍ରକୁ ହସ୍ତକୃତ ଭାବରେ ଅନିଲମ୍ବିତ ନକରାଯିବା
    ପର୍ଯ୍ୟନ୍ତ ଲୁଚାଯିବ।

## Burying section

deck-config-bury-title = ସ୍ଥଗିତ କରିବା
deck-config-bury-new-siblings = ସମ୍ପୃକ୍ତ ନୂତନ ପତ୍ରଗୁଡ଼ିକୁ ତା' ବାସିଦିନ ପର୍ଯ୍ୟନ୍ତ ସ୍ଥଗିତ କରନ୍ତୁ
deck-config-bury-review-siblings = ସମ୍ପୃକ୍ତ ସମୀକ୍ଷାଗୁଡ଼ିକୁ ତା' ବାସିଦିନ ପର୍ଯ୍ୟନ୍ତ ସ୍ଥଗିତ କରନ୍ତୁ

## Ordering section

deck-config-ordering-title = ପ୍ରଦର୍ଶନ କ୍ରମ
deck-config-new-gather-priority-deck = ଡେକ୍
deck-config-new-gather-priority-position-lowest-first = ଆରୋହଣ ପୋଜିସନ୍
deck-config-new-gather-priority-position-highest-first = ଅବରୋହଣ ପୋଜିସନ୍
deck-config-new-card-sort-order = ନୂତନ ପତ୍ର ସଜାଇବା କ୍ରମ
deck-config-sort-order-template-then-gather = ପତ୍ର ଟେମ୍ପଲେଟ୍
deck-config-new-review-priority = ନୂତନ/ସମୀକ୍ଷା କ୍ରମ
deck-config-new-review-priority-tooltip = ସମୀକ୍ଷା ପତ୍ର ସମ୍ପର୍କରେ କେବେ ନୂତନ ପତ୍ର ଦେଖାଇବେ।
deck-config-review-mix-mix-with-reviews = ସମୀକ୍ଷାଗୁଡ଼ିକ ସହ ମିଶ୍ରଣ କରନ୍ତୁ
deck-config-review-mix-show-after-reviews = ସମୀକ୍ଷା ପରେ ଦେଖାନ୍ତୁ
deck-config-review-mix-show-before-reviews = ସମୀକ୍ଷା ପୂର୍ବରୁ ଦେଖାନ୍ତୁ
deck-config-sort-order-ascending-intervals = ଆରୋହଣ ଅନ୍ତରାଳ
deck-config-sort-order-descending-intervals = ଅବରୋହଣ ଅନ୍ତରାଳ
deck-config-sort-order-ascending-ease = ଆରୋହଣ ସହଜତା
deck-config-sort-order-descending-ease = ଅବରୋହଣ ସହଜତା
deck-config-sort-order-relative-overdueness = ଆପେକ୍ଷିକ ଅତିଦେୟତା

## Timer section

deck-config-timer-title = ଟାଇମର୍
deck-config-maximum-answer-secs = ସର୍ବାଧିକ ଉତ୍ତର ସେକେଣ୍ଡ
deck-config-show-answer-timer-tooltip =
    ସମୀକ୍ଷା ସ୍କ୍ରିନରେ, ଏକ ଟାଇମର୍ ଦେଖାନ୍ତୁ ଯାହା ପ୍ରତ୍ୟେକ ପତ୍ର ସମୀକ୍ଷା କରିବାକୁ ଆପଣ
    ନେଉଥିବା ସେକେଣ୍ଡର ସଂଖ୍ୟା ଗଣନା କରେ।

## Audio section

deck-config-audio-title = ଅଡ଼ିଓ
deck-config-disable-autoplay = ସ୍ୱୟଂଚାଳିତ ଭାବରେ ଅଡ଼ିଓ ଚଲାନ୍ତୁ ନାହିଁ
deck-config-skip-question-when-replaying = ଉତ୍ତର ପୁନଃଚାଳନ କରିବା ସମୟରେ ପ୍ରଶ୍ନ ସ୍କିପ୍ କରନ୍ତୁ
deck-config-always-include-question-audio-tooltip =
    ଏକ ପତ୍ରର ଉତ୍ତର ପାର୍ଶ୍ୱକୁ ଦେଖିବାବେଳେ ରିପ୍ଲେ କ୍ରିୟା ବ୍ୟବହୃତ ହେଲେ
    ପ୍ରଶ୍ନ ଅଡ଼ିଓ ଅନ୍ତର୍ଭୁକ୍ତ ହେବା ଉଚିତ କି ନୁହେଁ।

## Advanced section

deck-config-advanced-title = ବିକଶିତ
deck-config-starting-ease-tooltip =
    ସେହି ଗୁଣକ ଯାହା ସହିତ ନୂତନ ପତ୍ର ଆରମ୍ଭ ହୁଏ। ଡିଫଲ୍ଟ ଭାବରେ, ଏକ ନୂଆରେ ଶିଖିଥିବା ପତ୍ରରେ `ଭଲ` ବଟନ୍
    ପରବର୍ତ୍ତୀ ସମୀକ୍ଷାକୁ ପୂର୍ବ ବିଳମ୍ବର 2.5 ଗୁଣ ବିଳମ୍ବ କରିବ।
deck-config-easy-bonus-tooltip =
    ଏକ ଅତିରିକ୍ତ ଗୁଣକ ଯାହା ଏକ ସମୀକ୍ଷା ପତ୍ରର ଅନ୍ତରାଳରେ ପ୍ରୟୋଗ କରାଯାଏ ଯେତେବେଳେ ଆପଣ ଏହାକୁ
    `ସହଜ` ବୋଲି ମୂଲ୍ୟାଙ୍କନ କରନ୍ତି।
deck-config-interval-modifier-tooltip =
    ଏହି ଗୁଣକ ସମସ୍ତ ସମୀକ୍ଷାରେ ପ୍ରୟୋଗ କରାଯାଏ, ଏବଂ Ankiକୁ ନିଜର ନିର୍ଦ୍ଧାରଣରେ ଅଧିକ ରକ୍ଷଣଶୀଳ କିମ୍ବା ଆକ୍ରମଣାତ୍ମକ କରିବା ପାଇଁ
    ସାମାନ୍ୟ ଆଡଜଷ୍ଟମେଣ୍ଟ ବ୍ୟବହାର କରାଯାଇପାରିବ। ଏହି ବିକଳ୍ପ ପରିବର୍ତ୍ତନ କରିବା ପୂର୍ବରୁ ଦୟାକରି
    ମାନୁଆଲ୍ ଦେଖନ୍ତୁ।
deck-config-hard-interval-tooltip = `କଠିନ` ଉତ୍ତର ଦେବା ମାତ୍ରେ ଏକ ସମୀକ୍ଷା ଅନ୍ତରାଳରେ ପ୍ରୟୋଗ କରାଯାଉଥିବା ଗୁଣକ।
deck-config-new-interval-tooltip = `ପୁଣି` ଉତ୍ତର ଦେବା ମାତ୍ରେ ଏକ ସମୀକ୍ଷା ଅନ୍ତରାଳରେ ପ୍ରୟୋଗ କରାଯାଉଥିବା ଗୁଣକ।
deck-config-custom-scheduling-tooltip = ସମଗ୍ର ସଂଗ୍ରହକୁ ପ୍ରଭାବିତ କରେ। ଆପଣଙ୍କ ନିଜ ଦାୟିତ୍ୱରେ ବ୍ୟବହାର କରନ୍ତୁ!

## Adding/renaming

deck-config-add-group = ଗୋଷ୍ଠୀ ଯୋଡ଼ନ୍ତୁ
deck-config-name-prompt = ନାମ:

## Removing

deck-config-remove-group = ଗୋଷ୍ଠୀ ଅପସାରଣ କରନ୍ତୁ
deck-config-confirm-remove-name = { $name } କୁ ଅପସାରଣ କରିବେ କି?

## Other Buttons


## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Anki 2.1.41+ ନିୟନ୍ତ୍ରଣ
deck-config-description-new-handling-hint =
    ଇନପୁଟ୍ କୁ Markdown ଭାବରେ ବିବେଚନା କରେ, ଏବଂ HTML ଇନପୁଟ୍ ସଫା କରେ। ସକ୍ଷମ ହୋଇଥିବା ବେଳେ,
    ଅଭିନନ୍ଦନ ସ୍କ୍ରିନରେ ବର୍ଣ୍ଣନା ମଧ୍ୟ ଦେଖାଯିବ।
    Markdown Anki 2.1.40 ଏବଂ ତା’ଠାରୁ କମ୍ ରେ ପାଠ୍ୟ ଭାବରେ ଦୃଶ୍ୟମାନ ହେବ।

## Warnings shown to the user


## Selecting a deck

deck-config-which-deck = ଆପଣ କେଉଁ ଡେକ୍ ପସନ୍ଦ କରିବେ?

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-bury-tooltip =
    ସମାନ ନୋଟର ଅନ୍ୟ ପତ୍ରଗୁଡ଼ିକ (ଯଥା ଓଲଟା ପତ୍ର,
    ସଂଲଗ୍ନ କ୍ଲୋଜ୍ ବିଲୋପ) ପରଦିନ ପର୍ଯ୍ୟନ୍ତ ବିଳମ୍ବ ହେବ କି ନାହିଁ।
