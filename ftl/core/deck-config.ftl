### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    used by { $decks ->
        [one] { $decks } deck
       *[other] { $decks } decks
    }
deck-config-default-name = Default
deck-config-title = Deck Options

## Daily limits section

deck-config-daily-limits = Daily Limits
deck-config-new-limit-tooltip =
    The maximum number of new cards to introduce in a day, if new cards are available.
    Because new material will increase your short-term review workload, this should typically
    be at least 10x smaller than your review limit.
deck-config-review-limit-tooltip =
    The maximum number of review cards to show in a day,
    if cards are ready for review.
deck-config-limit-deck-v3 =
    When studying a deck that has subdecks inside it, the limits set on each
    subdeck control the maximum number of cards drawn from that particular deck.
    The selected deck's limits control the total cards that will be shown.
deck-config-limit-new-bound-by-reviews =
    The review limit affects the new limit. For example, if your review limit is
    set to 200, and you have 190 reviews waiting, a maximum of 10 new cards will
    be introduced. If your review limit has been reached, no new cards will be
    shown.

## New Cards section

deck-config-learning-steps = Learning steps
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Delays are typically minutes (eg `1m`) or days (eg `2d`), but hours (eg `1h`) and seconds (eg `30s`) are also supported.
deck-config-learning-steps-tooltip =
    One or more delays, separated by spaces. The first delay will be used
    when you press the `Again` button on a new card, and is 1 minute by default.
    The `Good` button will advance to the next step, which is 10 minutes by default.
    Once all steps have been passed, the card will become a review card, and
    will appear on a different day. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip =
    The number of days to wait before showing a card again, after the `Good` button
    is pressed on the final learning step.
deck-config-easy-interval-tooltip =
    The number of days to wait before showing a card again, after the `Easy` button
    is used to immediately remove a card from learning.
deck-config-new-insertion-order = Insertion order
deck-config-new-insertion-order-tooltip =
    Controls the position (due #) new cards are assigned when you add new cards.
    Cards with a lower due number will be shown first when studying. Changing
    this option will automatically update the existing position of new cards.
deck-config-new-insertion-order-sequential = Sequential (oldest cards first)
deck-config-new-insertion-order-random = Random

## Lapses section

deck-config-relearning-steps = Relearning steps
deck-config-relearning-steps-tooltip =
    Zero or more delays, separated by spaces. By default, pressing the `Again`
    button on a review card will show it again 10 minutes later. If no delays
    are provided, the card will have its interval changed, without entering
    relearning. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip =
    The number of times `Again` needs to be pressed on a review card before it is
    marked as a leech. Leeches are cards that consume a lot of your time, and
    when a card is marked as a leech, it's a good idea to rewrite it, delete it, or
    think of a mnemonic to help you remember it.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `Tag Only`: Add a "leech" tag to the note, and display a pop-up.
    
    `Suspend Card`: In addition to tagging the note, hide the card until it is
    manually unsuspended.

## Burying section

deck-config-bury-title = Burying
deck-config-bury-new-siblings = Bury new siblings until the next day
deck-config-bury-review-siblings = Bury review siblings until the next day
deck-config-bury-tooltip =
    Whether other cards of the same note (eg reverse cards, adjacent
    cloze deletions) will be delayed until the next day.

## Ordering section

deck-config-ordering-title = Display Order
deck-config-new-gather-priority = New card gather priority
deck-config-new-gather-priority-tooltip =
    `Deck`: gathers cards from each subdeck in order, and stops when the
    limit of the selected deck has been exceeded. This is faster, and allows you
    to prioritize subdecks that are closer to the top.
    
    `Position`: gathers cards from all decks before they are sorted. This
    ensures cards appear in strict position (due #) order, even if the parent limit is
    not high enough to see cards from all decks.
deck-config-new-gather-priority-deck = Deck
deck-config-new-gather-priority-position-lowest-first = Ascending position
deck-config-new-gather-priority-position-highest-first = Descending position
deck-config-new-card-sort-order = New card sort order
deck-config-new-card-sort-order-tooltip =
    How cards are sorted after they have been gathered. By default, Anki sorts
    by card template first, to avoid multiple cards of the same note from being
    shown in succession.
deck-config-sort-order-card-template-then-lowest-position = Card template, then ascending position
deck-config-sort-order-card-template-then-highest-position = Card template, then descending position
deck-config-sort-order-card-template-then-random = Card template, then random
deck-config-sort-order-lowest-position = Ascending position
deck-config-sort-order-highest-position = Descending position
deck-config-sort-order-random = Random
deck-config-new-review-priority = New/review priority
deck-config-new-review-priority-tooltip = When to show new cards in relation to review cards.
deck-config-interday-step-priority = Interday learning/review priority
deck-config-interday-step-priority-tooltip = When to show (re)learning cards that cross a day boundary.
deck-config-review-mix-mix-with-reviews = Mix with reviews
deck-config-review-mix-show-after-reviews = Show after reviews
deck-config-review-mix-show-before-reviews = Show before reviews
deck-config-review-sort-order = Review sort order
deck-config-review-sort-order-tooltip =
    The default order prioritizes cards that have been waiting longest, so that
    if you have a backlog of reviews, the longest-waiting ones will appear
    first. If you have a large backlog that will take more than a few days to
    clear, or wish to see cards in subdeck order, you may find the alternate
    sort orders preferable.
deck-config-sort-order-due-date-then-random = Due date, then random
deck-config-sort-order-due-date-then-deck = Due date, then deck
deck-config-sort-order-deck-then-due-date = Deck, then due date
deck-config-sort-order-ascending-intervals = Ascending intervals
deck-config-sort-order-descending-intervals = Descending intervals
deck-config-display-order-will-use-current-deck =
    Anki will use the display order from the deck you 
    select to study, and not any subdecks it may have.

## Timer section

deck-config-timer-title = Timer
deck-config-maximum-answer-secs = Maximum answer seconds
deck-config-maximum-answer-secs-tooltip =
    The maximum number of seconds to record for a single review. If an answer
    exceeds this time (because you stepped away from the screen for example),
    the time taken will be recorded as the limit you have set.
deck-config-show-answer-timer-tooltip =
    In the review screen, show a timer that counts the number of seconds you're
    taking to review each card.

## Audio section

deck-config-audio-title = Audio
deck-config-disable-autoplay = Don't play audio automatically
deck-config-always-include-question-audio-tooltip =
    Whether the question audio should be included when the Replay action is
    used while looking at the answer side of a card.

## Advanced section

deck-config-advanced-title = Advanced
deck-config-maximum-interval-tooltip =
    The maximum number of days a review card will wait. When reviews have
    reached the limit, `Hard`, `Good` and `Easy` will all give the same delay.
    The shorter you set this, the greater your workload will be.
deck-config-starting-ease-tooltip =
    The ease multiplier new cards start with. By default, the `Good` button on a
    newly-learned card will delay the next review by 2.5x the previous delay.
deck-config-easy-bonus-tooltip =
    An extra multiplier that is applied to a review card's interval when you rate
    it `Easy`.
deck-config-interval-modifier-tooltip =
    This multiplier is applied to all reviews, and minor adjustments can be used
    to make Anki more conservative or aggressive in its scheduling. Please see
    the manual before changing this option.
deck-config-hard-interval-tooltip = The multiplier applied to a review interval when answering `Hard`.
deck-config-new-interval-tooltip = The multiplier applied to a review interval when answering `Again`.
deck-config-minimum-interval-tooltip = The minimum interval given to a review card after answering `Again`.
deck-config-custom-scheduling = Custom scheduling
deck-config-custom-scheduling-tooltip = Affects the entire collection. Use at your own risk!

## Adding/renaming

deck-config-add-group = Add Preset
deck-config-name-prompt = Name
deck-config-rename-group = Rename Preset
deck-config-clone-group = Clone Preset

## Removing

deck-config-remove-group = Remove Preset
deck-config-confirm-normal = Remove { $name }?
deck-config-will-require-full-sync =
    The requested change will require a one-way sync. If you have made changes
    on another device, and not synced them to this device yet, please do so before
    you proceed.
deck-config-confirm-remove-name = Remove { $name }?

## Other Buttons

deck-config-save-button = Save
deck-config-save-to-all-subdecks = Save to All Subdecks
deck-config-revert-button-tooltip = Restore this setting to its default value.

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Anki 2.1.41+ handling
deck-config-description-new-handling-hint =
    Treats input as markdown, and cleans HTML input. When enabled, the
    description will also be shown on the congratulations screen.
    Markdown will appear as text on Anki 2.1.40 and below.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    A parent deck has a limit of { $cards ->
        [one] { $cards } card
       *[other] { $cards } cards
    }, which will override this limit.
deck-config-reviews-too-low =
    If adding { $cards ->
        [one] { $cards } new card each day
       *[other] { $cards } new cards each day
    }, your review limit should be at least { $expected }.
deck-config-learning-step-above-graduating-interval = The graduating interval should be at least as long as your final learning step.
deck-config-good-above-easy = The easy interval should be at least as long as the graduating interval.
deck-config-relearning-steps-above-minimum-interval = The minimum lapse interval should be at least as long as your final relearning step.

## Selecting a deck

deck-config-which-deck = Which deck would you like?
