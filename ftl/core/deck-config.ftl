### Text shown on the "Deck Options" screen

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

## New Cards section

deck-config-learning-steps = Learning steps
# Please don't translate '5m' or '2d'
-deck-config-delay-hint = Delays can be in minutes (eg "5m"), or days (eg "2d").
deck-config-learning-steps-tooltip =
    One or more delays, separated by spaces. The first delay will be used
    when you press the Again button on a new card, and is 1 minute by default.
    The Good button will advance to the next step, which is 10 minutes by default.
    Once all steps have been passed, the card will become a review card, and
    will appear on a different day. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip =
    The number of days to wait before showing a card again, after the Good button
    is pressed on the final learning step.
deck-config-easy-interval-tooltip =
    The number of days to wait before showing a card again, after the Easy button
    is used to immediately remove a card from learning.

## Lapses section

deck-config-relearning-steps = Relearning steps
deck-config-relearning-steps-tooltip =
    Zero or more delays, separated by spaces. By default, pressing the Again
    button on a review card will show it again 10 minutes later. If no delays
    are provided, the card will have its interval changed, without entering
    relearning. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip =
    The number of times Again needs to be pressed on a review card before it is
    marked as a leech. Leeches are cards that consume a lot of your time, and
    when a card is marked as a leech, it's a good idea to rewrite it, delete it, or
    think of a mnemonic to help you remember it.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    <b>Tag Only</b>: Add a "leech" tag to the note, and display a pop-up.<br>
    <b>Suspend Card</b>: In addition to tagging the note, hide the card until it is
    manually unsuspended.

## Burying section

deck-config-burying-title = Burying
deck-config-bury-tooltip =
    Whether other cards of the same note (eg reverse cards, adjacent
    cloze deletions) will be delayed until the next day.

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
    reached the limit, Hard, Good and Easy will all give the same delay.
    The shorter you set this, the greater your workload will be.
deck-config-starting-ease-tooltip =
    The ease multiplier new cards start with. By default, the Good button on a
    newly-learnt card will delay the next review by 2.5x the previous delay.
deck-config-easy-bonus-tooltip =
    An extra multiplier that is applied to a review card's interval when you rate
    it Easy.
deck-config-interval-modifier-tooltip =
    This multiplier is applied to all reviews, and minor adjustments can be used
    to make Anki more conservative or aggressive in its scheduling. Please see
    the manual before changing this option.
deck-config-hard-interval-tooltip = The multiplier applied to a review interval when answering Hard.
deck-config-new-interval-tooltip = The multiplier applied to a review interval when answering Again.
deck-config-minimum-interval-tooltip = The minimum interval given to a review card after answering Again.

## Adding/renaming

deck-config-add-group = Add Group
deck-config-name-prompt = Name:
deck-config-rename-group = Rename Group

## Removing

deck-config-remove-group = Remove Group
deck-config-confirm-normal = Remove { $name }?
-deck-config-will-require-full-sync = This will require a one-way sync.
# You don't need to translate this
deck-config-confirm-full =
    { deck-config-confirm-normal }
    { -deck-config-will-require-full-sync }

## Other Buttons

deck-config-save-button = Save
deck-config-save-to-all-children = Save to All Children
deck-config-revert-button-tooltip = Restore this setting to its default value.

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-markdown = Enable markdown+clean HTML
deck-config-description-markdown-hint = Will appear as text on Anki 2.1.40 and below.

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
