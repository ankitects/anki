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
    subdeck control the maximum number of cards gathered from that particular deck.
    The selected deck's limits control the total cards that will be shown.
deck-config-limit-new-bound-by-reviews =
    The review limit affects the new limit. For example, if your review limit is
    set to 200, and you have 190 reviews waiting, a maximum of 10 new cards will
    be introduced. If your review limit has been reached, no new cards will be
    shown.
deck-config-limit-interday-bound-by-reviews =
    The review limit also affects interday learning cards. When applying the limit,
    interday learning cards are gathered first, then review cards.
deck-config-tab-description =
    - `Preset`: The limit applies to all decks using this preset.
    - `This deck`: The limit is specific to this deck.
    - `Today only`: Make a temporary change to this deck's limit.
deck-config-new-cards-ignore-review-limit = New cards ignore review limit
deck-config-new-cards-ignore-review-limit-tooltip =
    By default, the review limit also applies to new cards, and no new cards will be
    shown when the review limit has been reached. If this option is enabled, new cards
    will be shown regardless of the review limit.
deck-config-same-day-reviews-ignore-review-limit = Same-day reviews ignore review limit
deck-config-same-day-reviews-ignore-review-limit-tooltip =
    Cards already answered during the current scheduler day can be shown again
    after the maximum reviews/day limit is reached. Repeated answers do not
    reduce the number of other review cards available that day. Intraday
    learning and relearning cards already behave this way.
deck-config-apply-all-parent-limits = Limits start from top
deck-config-apply-all-parent-limits-tooltip =
    By default, the daily limits of a higher-level deck do not apply if you're studying from its subdeck.
    If this option is enabled, the limits will
    start from the top-level deck instead, which can be useful if you wish to study individual
    subdecks, while enforcing a total limit on cards for the deck tree.
deck-config-affects-entire-collection = Affects the entire collection.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Preset
deck-config-deck-only = This deck
deck-config-today-only = Today only

## New Cards section

deck-config-learning-steps = Learning steps
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Delays are typically minutes (e.g. `1m`) or days (e.g. `2d`), but hours (e.g. `1h`) and seconds (e.g. `30s`) are also supported.
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
deck-config-new-insertion-order-random-with-v3 =
    With the v3 scheduler, it is better to leave this set to sequential, and
    adjust the new card gather order instead.

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
    `Tag Only`: Add a 'leech' tag to the note, and display a pop-up.
    
    `Suspend Card`: In addition to tagging the note, hide the card until it is
    manually unsuspended.
deck-config-leech-only-if-young = Only mark if young again
deck-config-leech-only-if-young-tooltip =
    If enabled, cards are only marked as leeches at the configured threshold
    when pressing `Again` makes the card young again:
    stability under 21 days with FSRS, or interval under 21 days without FSRS.

## Burying section

deck-config-bury-title = Burying
deck-config-bury-new-siblings = Bury new siblings
deck-config-bury-review-siblings = Bury review siblings
deck-config-bury-interday-learning-siblings = Bury interday learning siblings
deck-config-bury-new-tooltip =
    Whether other `new` cards of the same note (e.g. reverse cards, adjacent cloze deletions)
    will be delayed until the next day.
deck-config-bury-review-tooltip = Whether other `review` cards of the same note will be delayed until the next day.
deck-config-bury-interday-learning-tooltip =
    Whether other `learning` cards of the same note with intervals > 1 day
    will be delayed until the next day.
deck-config-bury-priority-tooltip =
    When Anki gathers cards, it first gathers intraday learning cards, then
    interday learning cards, then review cards, and finally new cards. This affects
    how burying works:
    
    - If you have all burying options enabled, the sibling that comes earliest in
    that list will be shown. For example, a review card will be shown in preference
    to a new card.
    - Siblings later in the list can not bury earlier card types. For example, if you
    disable burying of new cards, and study a new card, it will not bury any interday
    learning or review cards, and you may see both a review sibling and new sibling in the
    same session.

## Gather order and sort order of cards

deck-config-ordering-title = Display Order
deck-config-new-gather-priority = New card gather order
deck-config-new-gather-priority-tooltip-3 =
    `Deck`: Gathers cards from each subdeck in order, starting from the top. Cards from each subdeck are
    gathered in ascending position. If the daily limit of the selected deck is reached, gathering
    can stop before all subdecks have been checked. This order is fastest in large collections, and
    allows you to prioritize subdecks that are closer to the top.
    
    `Deck, then random notes`: Like `Random notes`, but goes through subdecks in order,
    as `Deck` does, picking notes at random within each subdeck and gathering all of their cards.
    
    `Ascending position`: Gathers cards by ascending position (due #), which is typically
    the oldest-added first.
    
    `Descending position`: Gathers cards by descending position (due #), which is typically
    the latest-added first.

    `Ascending/descending retrievability (RWKV)`: Gathers cards by RWKV retrievability when
    RWKV queue scores are available. Cards without an RWKV score fall back to ascending position.
    These are desktop-only RWKV gather modes.
    
    `Random notes`: Picks notes at random, then gathers all of its cards.
    
    `Random cards`: Gathers cards in a random order.
deck-config-new-card-sort-order = New card sort order
deck-config-new-card-sort-order-tooltip-2 =
    `Card type, then order gathered`: Shows cards in order of card type number.
    Cards of each card type number are shown in the order they were gathered. 
    If you have sibling burying disabled, this will ensure all front→back cards are seen before any back→front cards.
    This is useful to have all cards of the same note shown in the same session, but not
    too close to one another.
    
    `Order gathered`: Shows cards exactly as they were gathered. If sibling burying is disabled,
    this will typically result in all cards of a note being seen one after the other.
    
    `Card type, then random`: Shows cards in order of card type number. Cards of each card
    type number are shown in a random order. This order is useful if you don't want sibling cards
    to appear too close to each other, but still want the cards to appear in a random order.
    
    `Random note, then card type`: Picks notes at random, then shows all of its cards
    in order.
    
    `Random`: Shows cards in a random order.
deck-config-new-review-priority = New/review order
deck-config-new-review-priority-tooltip = When to show new cards in relation to review cards.
deck-config-reviews-per-new-card = Reviews per new card
deck-config-reviews-per-new-card-tooltip =
    When new cards are mixed with reviews, show one new card after this many reviews.
    0 spreads today's new cards evenly over the reviews.
deck-config-interday-step-priority = Interday learning/review order
deck-config-interday-step-priority-tooltip =
    When to show (re)learning cards that cross a day boundary.
    
    With FSRS retrievability or Relative Overdueness sorting, Before and After
    place interday learning cards before or after the other due cards, keeping
    the selected sort order within each group. Mix sorts all due cards together.
    This option does not change the order of intraday learning cards.

    Review limits still determine which cards are available. With other review
    sort orders, interday learning cards are gathered before review cards.
deck-config-intraday-step-priority = Intraday learning/review order
deck-config-intraday-step-priority-tooltip =
    With FSRS retrievability or Relative Overdueness sorting, show due intraday
    learning and relearning cards before or after the other cards, keeping the
    selected sort within each group. Mix keeps them in the shared sorted order.
    Future learning steps still wait until their due time. With other review
    sort orders, due intraday learning cards already have priority.
deck-config-review-sort-order = Review sort order
deck-config-review-sort-order-tooltip =
    The default order prioritizes cards that have been waiting longest, so that
    if you have a backlog of reviews, the longest-waiting ones will appear
    first. If you have a large backlog that will take more than a few days to
    clear, or wish to see cards in subdeck order, you may find the alternate
    sort orders preferable.

deck-config-display-order-will-use-current-deck =
    Anki will use the display order from the deck you 
    select to study, and not any subdecks it may have.

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = Deck
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = Deck, then random notes
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = Ascending position
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = Descending position
# Gather new cards ordered by RWKV retrievability percentage, ascending (0% to 100%, least retrievable to most easily retrievable).
deck-config-new-gather-priority-ascending-retrievability = Ascending retrievability (RWKV)
# Gather new cards ordered by RWKV retrievability percentage, descending (100% to 0%, most easily retrievable to least retrievable).
deck-config-new-gather-priority-descending-retrievability = Descending retrievability (RWKV)
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = Random notes
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = Random cards
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = Card type, then random
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = Random note, then card type
# Sort the cards randomly.
deck-config-sort-order-random = Random
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = Card type, then order gathered
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = Order gathered
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = Mix with reviews
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = Show after reviews
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = Show before reviews
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = Due date, then random
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = Due date, then deck
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = Deck, then due date
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = Ascending intervals
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = Descending intervals
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = Ascending ease
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = Descending ease
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = Easy cards first
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = Difficult cards first
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = Ascending retrievability
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = Descending retrievability

## Timer section

deck-config-timer-title = Timers
deck-config-maximum-answer-secs = Maximum answer seconds
deck-config-maximum-answer-secs-tooltip =
    The maximum number of seconds to record for a single review. If an answer
    exceeds this time (because you stepped away from the screen for example),
    the time taken will be recorded as the limit you have set.
deck-config-show-answer-timer-tooltip =
    On the Study screen, show a timer that counts the time you're
    taking to study each card.
deck-config-stop-timer-on-answer = Stop on-screen timer on answer
deck-config-stop-timer-on-answer-tooltip =
    Whether to stop the on-screen timer when the answer is revealed.
    This doesn't affect statistics.

## Auto Advance section

deck-config-seconds-to-show-question = Seconds to show question for
deck-config-seconds-to-show-question-tooltip-3 = When auto advance is activated, the number of seconds to wait before applying the question action. Set to 0 to disable.
deck-config-seconds-to-show-answer = Seconds to show answer for
deck-config-seconds-to-show-answer-tooltip-2 = When auto advance is activated, the number of seconds to wait before applying the answer action. Set to 0 to disable.
deck-config-question-action-show-answer = Show Answer
deck-config-question-action-show-reminder = Show Reminder
deck-config-question-action = Question action 
deck-config-question-action-tool-tip = The action to perform after the question is shown, and time has elapsed.
deck-config-answer-action = Answer action
deck-config-answer-action-tooltip-2 = The action to perform after the answer is shown, and time has elapsed.
deck-config-wait-for-audio-tooltip-2 = Wait for audio to finish before automatically applying the question action or answer action.

## Audio section

deck-config-audio-title = Audio
deck-config-disable-autoplay = Don't play audio automatically
deck-config-disable-autoplay-tooltip =
    When enabled, Anki will not play audio automatically.
    It can be played manually by clicking/tapping on an audio icon, or by using the Replay action.
deck-config-skip-question-when-replaying = Skip question when replaying answer
deck-config-always-include-question-audio-tooltip =
    Whether the question audio should be included when the Replay action is
    used while looking at the answer side of a card.
## Advanced section

deck-config-advanced-title = Advanced
deck-config-maximum-interval-tooltip =
    The maximum number of days a review card will wait. When reviews have
    reached the limit, `Hard`, `Good` and `Easy` will all give the same delay.
    The shorter you set this, the greater your workload will be. You can enter
    values like 1d, 1w, 1m, or 1y.
deck-config-fsrs-minimum-interval-tooltip =
    The minimum interval for FSRS same-day learning and relearning cards, such as
    1s, 1m, 1h, or 1d.
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

## RWKV model and recurrent state precision. Keep f32, int8 and int4 cards + int8 notes unchanged.

deck-config-rwkv-model-and-storage = Model and storage
deck-config-rwkv-profile-settings-scope = Applies to all decks in this profile on this computer. These settings do not sync. Saving a different model prompts you to rebuild RWKV state when needed.
deck-config-rwkv-precision = Storage precision
deck-config-rwkv-model = Prediction model
deck-config-rwkv-model-sm40 = SM40 — smaller model
deck-config-rwkv-model-sm36 = SM36 Muon
deck-config-rwkv-model-sm55 = SM55 — previous smaller model
deck-config-rwkv-model-sm56 = Previous smaller model (SM56)
deck-config-rwkv-model-sm72 = Previous smaller model (SM72, cool-down from epoch 15)
deck-config-rwkv-model-sm72e20 = Previous smaller model (SM72, cool-down from epoch 20)
deck-config-rwkv-model-sm92 = Show-time retrain of the release (SM92)
deck-config-rwkv-model-big = Big
deck-config-rwkv-model-small = Small
deck-config-rwkv-model-tiny = Tiny
deck-config-rwkv-model-size-heading = Model size
deck-config-rwkv-model-estimate-summary = Estimated model state for { $cards } cards and { $notes } notes.
deck-config-rwkv-model-estimate-no-counts = Estimated model state
deck-config-rwkv-model-per-card-note = { $size } per card + note
deck-config-rwkv-model-state-ram-disk = Estimated state RAM / disk
deck-config-rwkv-model-state-unavailable = Estimate unavailable
deck-config-rwkv-model-both-separate = Both schedulers · separate models
deck-config-rwkv-model-both-shared = Both schedulers · one shared model
deck-config-rwkv-model-curve-only = Curve only
deck-config-rwkv-model-instant-only = Instant only
deck-config-rwkv-model-logloss = LogLoss
deck-config-rwkv-model-curve-label = Curve
deck-config-rwkv-model-instant-label = Instant
deck-config-rwkv-model-baseline = Baseline
deck-config-rwkv-model-not-used = Not used
deck-config-rwkv-model-rebuild-baseline = Model replay time: baseline
deck-config-rwkv-model-rebuild-saving = About { $percent }% less model replay time
deck-config-rwkv-model-loss-legend = LogLoss is relative to Big with the same scheduler settings; lower is better.
deck-config-rwkv-model-estimates-help = About these estimates
deck-config-rwkv-model-estimates-explanation = State estimates use this profile's card and note counts and the selected storage precision. They include model state only, not model weights, shared state, cache overhead, or other Anki memory and disk use. Actual usage can be lower when some cards have no state.
deck-config-rwkv-model-estimates-loss = Factory LogLoss comes from 722 held-out users; the figures can vary across collections.
deck-config-rwkv-model-estimates-rebuild = Replay-time comparisons use a synthetic benchmark at the default state precision. They exclude history loading and cache writes; your results depend on your computer and review history.
deck-config-rwkv-model-sm92-description = Kept from an earlier version: one model for both schedulers. Pick a size above to switch.
deck-config-rwkv-single-model = Use one model for RWKV
deck-config-rwkv-single-model-explanation = When your presets use both RWKV-Instant and RWKV-Curve (including as Instant's fallback), each normally uses its own model of the chosen size. Turn this on to use the size's Curve model for both: it uses less memory, but Instant predictions are slightly less accurate.
deck-config-rwkv-single-model-instant-only = If you use Instant without Curve, the size's Instant model always runs alone. FSRS supplies due dates and memory states, and RWKV workload simulation is unavailable.
deck-config-rwkv-workload-needs-curve = RWKV workload simulation needs RWKV-Curve. Turn on RWKV-Curve in a preset (as its scheduler or as RWKV-Instant's fallback) and save to simulate RWKV workload.
deck-config-rwkv-model-size-migration = Older small models saved by earlier versions switch to Small when you save.
# Preserve the links and URL variables.
deck-config-rwkv-model-results = Results: <a href="{ $factory }">Model Factory results</a> · <a href="{ $backup }">Matched backup comparison</a>
deck-config-rwkv-f32 = f32 — full precision fallback
deck-config-rwkv-int8 = int8 — cards and notes
deck-config-rwkv-int4-cards-int8-notes = int4 cards + int8 notes — default
deck-config-rwkv-precision-explanation = Applies to card and note state in memory and on disk for this profile. Computation remains at full precision. int4 cards + int8 notes saves more memory but can change predictions; note state stays at int8.

deck-config-rwkv-description =
    RWKV is a neural network designed to improve predictions of retrievability:
    the chance of remembering a card. It has two variants: Curve sets answer
    intervals, while Instant selects reviews. Instant is recommended, but it
    adapts to your reviews on the fly, so the due count can fluctuate considerably
    during a session. Setting a daily review limit is recommended.
deck-config-rwkv-read-more = Read more about RWKV
deck-config-rwkv-recommended = Recommended
deck-config-rwkv-instant-subtitle = Review queue
deck-config-rwkv-curve-subtitle = Answer intervals
deck-config-rwkv-instant-description =
    Instant replaces FSRS and Curve for review selection; scheduled due dates
    are ignored.
deck-config-rwkv-curve-description =
    Curve replaces FSRS due dates with RWKV dates, useful for your next synced
    mobile session. Instant ignores these dates.
deck-config-rwkv-review-enabled = Use RWKV-Curve for answer intervals
deck-config-rwkv-review-enabled-tooltip =
    On this computer, use RWKV-Curve to calculate the next intervals shown for
    Again, Hard, Good, and Easy. This does not enable RWKV-Instant review queue
    ordering; that is controlled separately. Other devices
    continue to use FSRS or SM-2.
deck-config-rwkv-review-enforce-grade-order = Enforce Again ≤ Hard ≤ Good ≤ Easy intervals
deck-config-rwkv-review-enforce-grade-order-tooltip =
    Keep Again, Hard, Good, and Easy in a sensible interval order. When RWKV's
    four predictions disagree, Anki gently balances the conflicting predictions
    before choosing the intervals. Turn this off to use the raw RWKV-Curve
    results.
deck-config-rwkv-review-instant-order = Use RWKV-Instant to choose review cards
deck-config-rwkv-review-instant-order-recommended = Recommended: Use Ascending Retrievability or Random
deck-config-rwkv-review-instant-order-tooltip =
    RWKV decides which review cards are ready, and your selected review sort
    order determines how those cards are shown. A card may appear before its
    normal due date, or wait longer, depending on whether your estimated chance
    of remembering it has fallen to your Desired Retention. This setting is
    independent of RWKV-Curve: turn Curve off to keep FSRS intervals, or leave
    this off to use RWKV-Curve intervals without RWKV-Instant queue selection.
deck-config-rwkv-review-minimum-reviews-per-day = Minimum reviews per day
deck-config-rwkv-review-minimum-reviews-per-day-tooltip =
    If RWKV-Instant finds fewer reviews than this daily target, pull forward the
    lowest-retrievability review cards from this deck and its subdecks until the
    target is met. Reviews already completed today count toward the target. The
    normal maximum review limits and same-day repeat safeguards still apply.
deck-config-rwkv-review-respect-subdeck-minimums = Also honor subdeck minimums
deck-config-rwkv-apply-respect-subdeck-minimums-to-children = Apply this setting to all subdecks on save
deck-config-rwkv-review-respect-subdeck-minimums-tooltip =
    When studying this deck, also meet the minimum reviews set on its direct
    subdecks. A subdeck can also honor its own subdecks' minimums by enabling
    this option. Reviews selected for subdeck minimums count toward this deck's
    minimum, but may add reviews beyond it. Turn this off to use this deck's
    minimum across its whole tree. This option belongs to the deck, not its
    preset; you can apply it to all subdecks when saving.
deck-config-rwkv-review-candidate-refresh = Use faster, approximate queue updates
deck-config-rwkv-review-candidate-refresh-tooltip =
    After the first full update, RWKV rechecks only the cards most likely to
    appear next. This makes queue updates faster for large decks, but other
    cards keep an older estimate until a later update. Anki automatically bounds
    the number rechecked at once. This setting applies when precomputation is on.
deck-config-rwkv-review-precompute-next-card = Precompute next card before this card answer
deck-config-rwkv-review-precompute-next-card-tooltip =
    Use the queue scores computed before your answer to choose the next card,
    then update the scores in the background. This can show the next card
    faster, but it may not have the lowest retrievability after your answer.
    When disabled with retrievability ordering, Anki updates the full queue after
    every answer before showing the next card. Anki also waits when the queue
    must be rebuilt.
deck-config-rwkv-review-refresh-interval = Update the RWKV queue every
deck-config-rwkv-review-refresh-interval-tooltip =
    How many answers to wait between RWKV queue updates. 1 updates the queue
    after every answer. Higher values reduce the work RWKV does while you
    review, but the queue may use older estimates between updates. With
    precomputation off and retrievability ordering, Anki updates after every answer.
deck-config-rwkv-review-refresh-on-exit = Update the RWKV queue after reviewing
deck-config-rwkv-review-refresh-on-exit-tooltip =
    If you answered at least one card, update the RWKV estimates in the
    background when you leave the reviewer. This helps prepare an up-to-date
    queue for your next review session.
deck-config-scheduler-review = Review scheduler
deck-config-scheduler-enable-fsrs = Machine Learning Based scheduling
deck-config-scheduler-disabled = Machine learning scheduling is disabled for this collection. Selecting a model enables it; RWKV settings remain independent.
deck-config-scheduler-due-dates = Fallback
deck-config-scheduler-due-dates-description = Writes due dates for mobile sync and statistics. Instant selects reviews independently of these dates.
deck-config-scheduler-recommended = Recommended: { $scheduler }
deck-config-apply-schedule-to-all-presets = Apply Schedule to All Presets
deck-config-apply-schedule-description = Choose what to copy from the current preset. Click Save in Deck Options to save the changes.
deck-config-copy-scheduler = Scheduler
deck-config-copy-scheduler-description = The selected review scheduler.
deck-config-copy-fallback = Fallback
deck-config-copy-fallback-description = The due-date calculator for RWKV-Instant presets. Other review schedulers calculate their own due dates.
deck-config-copy-settings = Settings
deck-config-copy-settings-description = FSRS and RWKV preset model settings, including parameters, interval limits, optimization options, Dynamic DR, and repeat spacing.
deck-config-copy-retention = Desired Retention
deck-config-copy-retention-description = The preset's desired retention. Deck-specific retention overrides remain unchanged.
deck-config-apply-schedule = Apply
deck-config-fsrs-description =
    FSRS uses your review history to predict retrievability and schedule reviews
    at your desired retention. FSRS-7 provides more accurate predictions than
    FSRS-6 on average.
deck-config-fsrs-read-more = Read more about FSRS
deck-config-rwkv-review-allow-same-day-review = Allow a card to repeat on the same day
deck-config-rwkv-review-allow-same-day-review-tooltip =
    When a card's estimated chance of recall is at or below your Desired
    Retention, RWKV may show it again later the same day. When this is off, a
    review card you answer will not appear again until the next day.
# The variables become editable number fields that include their units.
deck-config-rwkv-repeat-spacing = Minimum { $reviews-field } and { $seconds-field } between repeats
deck-config-rwkv-repeat-spacing-title = Minimum spacing between repeats
deck-config-rwkv-repeat-spacing-tooltip =
    Before RWKV repeats a card, both minimums must be met: the number of other
    reviews in the selected deck tree and the time since the card's last review.
    Set either value to 0 to remove that minimum.
deck-config-rwkv-repeat-reviews-unit =
    { $count ->
        [one] review
       *[other] reviews
    }
deck-config-rwkv-repeat-seconds-unit =
    { $count ->
        [one] second
       *[other] seconds
    }
deck-config-rwkv-review-min-intervening-reviews = Minimum other reviews before a repeat
deck-config-rwkv-review-min-intervening-reviews-tooltip =
    How many other cards you must review before RWKV may repeat the same card. 0
    means no minimum. If you also set a minimum time, both limits must be met.
deck-config-rwkv-review-min-elapsed-secs = Minimum seconds before a repeat
deck-config-rwkv-review-min-elapsed-secs-tooltip =
    How long RWKV must wait before repeating the same card. 0 means no minimum.
    If you also set a minimum number of other reviews, both limits must be met.
deck-config-rwkv-review-first-review-elapsed-from-card-creation = Predict R for new cards based on creation time
deck-config-rwkv-review-first-review-elapsed-from-card-creation-tooltip =
    Use the time since a new card was created when predicting R before its first
    learning review. The first answer is recorded with elapsed time unknown, so
    creation time does not affect later predictions. This is always enabled.
deck-config-rwkv-review-dynamic-preset-replay = Dynamic Preset Addon Support
deck-config-rwkv-review-dynamic-preset-replay-tooltip =
    RWKV always resolves each card's current add-on preset once when rebuilding
    review history. Turn this on only if the add-on can move a card between
    presets over time; RWKV will then reapply its rules at every historical
    review. Leaving this off reuses the initially resolved preset for the card.
deck-config-custom-scheduling = Custom scheduling
deck-config-custom-scheduling-tooltip = Affects the entire collection. Use at your own risk!

## Easy Days section.

deck-config-easy-days-title = Easy Days
deck-config-easy-days-monday = Mon
deck-config-easy-days-tuesday = Tue
deck-config-easy-days-wednesday = Wed
deck-config-easy-days-thursday = Thu
deck-config-easy-days-friday = Fri
deck-config-easy-days-saturday = Sat
deck-config-easy-days-sunday = Sun
deck-config-easy-days-normal = Normal
deck-config-easy-days-reduced = Reduced
deck-config-easy-days-minimum = Minimum
deck-config-easy-days-no-normal-days = At least one day should be set to '{ deck-config-easy-days-normal }'.
deck-config-easy-days-change = Existing reviews will not be rescheduled unless '{ deck-config-reschedule-cards-on-change }' is enabled in the FSRS options.
deck-config-load-balancer-enabled = Enable load balancer
deck-config-review-fuzz-enabled = Enable review fuzz
deck-config-load-balancer-enabled-tooltip = Redistributes day-based review intervals within the available fuzz range to smooth out workload. Affects the entire collection.
deck-config-review-fuzz-enabled-tooltip = Enables the review fuzz window for this preset. When disabled, day-based review intervals are scheduled without fuzz.
deck-config-review-fuzz-title = Review fuzz
deck-config-review-fuzz-base = Base fuzz (days)
deck-config-review-fuzz-factor-short = Fuzz factor (2.5-7 days)
deck-config-review-fuzz-factor-mid = Fuzz factor (7-20 days)
deck-config-review-fuzz-factor-long = Fuzz factor (20+ days)
deck-config-review-fuzz-preview = Review fuzz preview
deck-config-default-fuzz = Default fuzz
deck-config-current-fuzz = Current fuzz
deck-config-selected-fuzz = Selected fuzz

## Adding/renaming

deck-config-add-group = Add Preset
deck-config-name-prompt = Name
deck-config-rename-group = Rename Preset
deck-config-clone-group = Clone Preset

## Removing

deck-config-remove-group = Remove Preset
deck-config-will-require-full-sync =
    The requested change will require a one-way sync. If you have made changes
    on another device, and not synced them to this device yet, please do so before
    you proceed.
deck-config-confirm-remove-name = Remove { $name }?

## Other Buttons

deck-config-save-button = Save
deck-config-save-and-close = Save and Close
deck-config-save-to-all-subdecks = Save to All Subdecks
deck-config-save-and-optimize = Optimize All Presets
deck-config-revert-button-tooltip = Restore this setting to its default value?

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling2 = Interpret as Markdown
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
deck-config-maximum-answer-secs-above-recommended = Anki can schedule your reviews more efficiently when you keep each question short.
deck-config-too-short-maximum-interval = A maximum interval less than 6 months is not recommended.
deck-config-ignore-before-info = (Approximately) { $included }/{ $totalCards } cards will be used to optimize the FSRS parameters.

## Selecting a deck

deck-config-which-deck = Which deck would you like to display options for?

## Messages related to the FSRS scheduler

deck-config-updating-cards = Updating cards: { $current_cards_count }/{ $total_cards_count }...
deck-config-invalid-parameters = The provided FSRS parameters are invalid. Leave them blank to use the default values.
deck-config-placeholder-parameters =
    Default parameters
    (Press "{deck-config-optimize-button}" periodically to allow FSRS to better adjust to your memory)
deck-config-manual-parameter-edit-warning = The parameters should only be modified using the optimize button. Manually editing them is heavily advised against.
deck-config-not-enough-history = Insufficient review history to perform this operation.
deck-config-must-have-400-reviews =
    { $count ->
        [one] Only { $count } review was found.
       *[other] Only { $count } reviews were found.
    } You must have at least 400 reviews for this operation.
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = FSRS parameters
deck-config-fsrs-version = FSRS version
deck-config-compute-optimal-weights = Optimize FSRS parameters
deck-config-optimize-button = Optimize Current Preset
# Indicates that a given function or label, provided via the "text" variable, operates slowly.
deck-config-slow-suffix = { $text } (slow)
deck-config-compute-button = Compute
deck-config-ignore-before = Ignore cards reviewed before
deck-config-time-to-optimize = It's been a while - using the Optimize All Presets button is recommended.
deck-config-evaluate-button = Evaluate
deck-config-desired-retention = Desired retention
deck-config-new-card-intervals = New card intervals at graduation
deck-config-current-dr = Current DR
deck-config-selected-dr = Selected DR
deck-config-again-then-good = Again -> Good
deck-config-again-then-again = Again -> Again
deck-config-good-then-again = Good -> Again
deck-config-good-then-good = Good -> Good
deck-config-historical-retention = Historical retention
deck-config-smaller-is-better = Smaller numbers indicate a better fit to your review history.
deck-config-steps-too-large-for-fsrs = When FSRS is enabled, steps of 1 day or more are not recommended.
deck-config-get-params = Get Params
deck-config-complete = { $num }% complete.
deck-config-iterations = Iteration: { $count }...
deck-config-saving-optimized-presets = Saving optimized presets: { $current_count }/{ $total_count }...
deck-config-rescheduling-cards = Rescheduling cards: { $current_cards_count }/{ $total_cards_count }...
deck-config-reschedule-cards-on-change = Reschedule cards on change
deck-config-fsrs-tooltip =
    Affects the entire collection.

    Machine learning scheduling uses predictions of how likely you are to remember
    a card to guide reviews, as an alternative to Anki's legacy SuperMemo 2 (SM-2)
    algorithm. This switch is shared by all presets.

deck-config-desired-retention-tooltip =
    By default, Anki schedules cards so that you have a 90% chance of remembering them when
    they come up for review again. If you increase this value, Anki will show cards more frequently
    to increase the chances of you remembering them. If you decrease the value, Anki will show cards
    less frequently, and you will forget more of them. Be conservative when adjusting this - higher
    values will greatly increase your workload, and lower values can be demoralizing when you forget
    a lot of material.
deck-config-desired-retention-tooltip2 = 
    The workload values provided by the info box are a rough approximation. For a greater level of accuracy, use the simulator.
deck-config-historical-retention-tooltip =
    When some of your review history is missing, FSRS needs to fill in the gaps. By default, it will
    assume that when you did those old reviews, you remembered 90% of the material. If your old retention
    was appreciably higher or lower than 90%, adjusting this option will allow FSRS to better approximate
    the missing reviews.

    Your review history may be incomplete for two reasons:
    1. Because you're using the 'ignore cards reviewed before' option.
    2. Because you previously deleted review logs to free up space, or imported material from a different
    SRS program.

    The latter is quite rare, so unless you're using the former option, you probably don't need to adjust
    this option.
deck-config-weights-tooltip2 =
    FSRS parameters affect how cards are scheduled. Anki will start with default parameters. You can use 
    the option below to optimize the parameters to best match your performance in decks using this preset.
deck-config-reschedule-cards-on-change-tooltip =
    Affects the entire collection, and is not saved.

    This option controls whether the due dates of cards will be changed when you enable FSRS, or optimize
    the parameters. The default is not to reschedule cards: future reviews will use the new scheduling, but
    there will be no immediate change to your workload. If rescheduling is enabled, the due dates of cards
    will be changed.
deck-config-reschedule-cards-warning =
    Depending on your desired retention, this can result in a large number of cards becoming
    due, so is not recommended when first switching from SM-2.

    Use this option sparingly, as it will add a review entry to each of your cards, and
    increase the size of your collection.
deck-config-ignore-before-tooltip-2 = 
    If set, cards reviewed before the provided date will be ignored when optimizing FSRS parameters.
    This can be useful if you imported someone else's scheduling data, or have changed the way you use the answer buttons.
deck-config-compute-optimal-weights-tooltip2 =
    When you click the Optimize button, FSRS will analyze your review history, and generate parameters that are 
    optimal for your memory and the content you're studying. If your decks vary wildly in subjective difficulty, it 
    is recommended to assign them separate presets, as the parameters for easy decks and hard decks will be different. 
    You don't need to optimize your parameters frequently - once every few months is sufficient.
    
    By default, parameters will be calculated from the review history of all decks using the current preset. You can
    optionally adjust the search before calculating the parameters, if you'd like to alter which cards are used for
    optimizing the parameters.

deck-config-please-save-your-changes-first = Please save your changes first.
deck-config-workload-factor-change = Approximate workload: {$factor}x (vs initial DR: {$previousDR}%).
deck-config-workload-factor-note = FSRS interval-based workload only.
deck-config-workload-factor-unchanged = The higher your desired retention, the more frequently cards will be shown to you.
deck-config-desired-retention-too-low = Your desired retention is very low, which can lead to very long intervals.
deck-config-desired-retention-too-high = Your desired retention is very high, which can lead to very short intervals.

deck-config-percent-of-reviews =  
    { $reviews ->
        [one] { $pct }% of { $reviews } review
       *[other] { $pct }% of { $reviews } reviews
    }
deck-config-percent-input = { $pct }%
# This message appears during FSRS parameter optimization.
deck-config-checking-for-improvement = Checking for improvement...
deck-config-optimizing-preset = Optimizing preset { $current_count }/{ $total_count }...
deck-config-fsrs-must-be-enabled = FSRS must be enabled first.
deck-config-fsrs-params-optimal = The FSRS parameters are currently optimal. This is expected once parameters are well-fitted to your review history. You can try optimizing again after doing more reviews.
deck-config-fsrs-confirm-save-and-optimize = This will save any other changes you have made. Are you sure?

deck-config-fsrs-params-no-reviews = No reviews found. Make sure this preset is assigned to all decks (including subdecks) that you want to optimize, and try again.

deck-config-wait-for-audio = Wait for audio
deck-config-show-reminder = Show Reminder
deck-config-answer-again = Answer Again
deck-config-answer-hard = Answer Hard
deck-config-answer-good = Answer Good
deck-config-days-to-simulate = Days to simulate
deck-config-desired-retention-below-optimal = Your desired retention is below optimal. Increasing it is recommended.
# Description of the y axis in the FSRS simulation
# diagram (Deck options -> FSRS) showing the total number of
# cards that can be recalled or retrieved on a specific date.
deck-config-fsrs-simulator-experimental = FSRS Simulator (Experimental)
deck-config-fsrs-simulate-desired-retention-experimental = FSRS Desired Retention Simulator (Experimental)
deck-config-fsrs-simulate-save-preset = After optimizing, please save your deck preset before running the simulator.
deck-config-fsrs-desired-retention-help-me-decide-experimental = Desired Retention: Help Me Decide
deck-config-additional-new-cards-to-simulate = Additional new cards to simulate
deck-config-simulate = Simulate
deck-config-clear-last-simulate = Clear Last Simulation
deck-config-fsrs-simulator-radio-count = Reviews
deck-config-advanced-settings = Advanced Settings
deck-config-smooth-graph = Smooth graph
deck-config-suspend-leeches = Suspend leeches
deck-config-save-options-to-preset = Save Changes to Preset
deck-config-save-options-to-preset-confirm = Overwrite the options in your current preset with the options that are currently set in the simulator?
# Radio button in the FSRS simulation diagram (Deck options -> FSRS) selecting
# to show the total number of cards that can be recalled or retrieved on a
# specific date.
deck-config-fsrs-simulator-radio-memorized = Memorized
deck-config-fsrs-simulator-radio-efficiency = Net efficiency
deck-config-fsrs-simulator-radio-weighted-memorized = R*f(S)
deck-config-fsrs-simulator-radio-weighted-efficiency = Net R*f(S)/t
deck-config-fsrs-simulator-split-by-preset = Split by active preset
deck-config-fsrs-simulator-ratio-tooltip2 = { $time } net memorized cards per hour
deck-config-fsrs-simulator-weighted-memorized-tooltip = { $memorized } R*f(S)
deck-config-fsrs-simulator-weighted-ratio-tooltip = { $time } net R*f(S) per hour

## Experimental FSRS/RWKV shared-learner comparison.

deck-config-rwkv-comparison-learner = Simulated learner
deck-config-rwkv-comparison-learner-fsrs = FSRS-based learner
deck-config-rwkv-comparison-learner-rwkv = RWKV-based learner
deck-config-rwkv-comparison-scorer = Estimate memorized with
deck-config-rwkv-comparison-scenario = Performance scenario
deck-config-rwkv-comparison-steady = Steady
deck-config-rwkv-comparison-temporary-drop = Temporary drop: −15 points in the middle third
deck-config-rwkv-comparison-persistent-drop = Persistent drop: −15 points after the first third
deck-config-rwkv-comparison-temporary-improvement = Temporary improvement: +10 points in the middle third
deck-config-rwkv-comparison-steady-short = steady
deck-config-rwkv-comparison-temporary-drop-short = temporary drop
deck-config-rwkv-comparison-persistent-drop-short = persistent drop
deck-config-rwkv-comparison-temporary-improvement-short = temporary improvement
deck-config-rwkv-comparison-sample = Sampled cards (0 for all)
deck-config-rwkv-comparison-step = Retention step (percentage points)
deck-config-rwkv-comparison-runs = Paired simulation runs
deck-config-rwkv-comparison-both-learners = Compare both answer models
deck-config-rwkv-comparison-graph-learner = Answer model for graph
deck-config-rwkv-comparison-both-run-learner = both answer models
deck-config-rwkv-comparison-graph-answer-note = The graph shows the selected answer model for paired runs. Single-model runs keep their original answer model.
deck-config-rwkv-comparison-agreement-min-retention = Minimum retention shown in agreement (%)
deck-config-rwkv-comparison-agreement-title = Scheduler agreement on net efficiency
deck-config-rwkv-comparison-agreement-help =
    Each cell compares RWKV scheduling with FSRS scheduling at the same requested retention.
    The two lines use FSRS and RWKV to predict answers; each line shows the percentage change using FSRS and RWKV to estimate memory, in that order.
    A winner requires all four comparisons to agree. Differences shown as 0.0% count as ties. A percentage is unavailable if review time is zero or FSRS efficiency is not positive.
    These are unsmoothed simulated averages, not proof of a real-world advantage; small differences may be noise. Smoothing the graph does not change this table.
    To compare horizons, change Days to simulate and run again. Each row keeps its own settings and card sample.
deck-config-rwkv-comparison-agreement-run = Run { $run }: { $preset }, { $days } days
deck-config-rwkv-comparison-agreement-retention = Requested retention
deck-config-rwkv-comparison-agreement-fsrs = FSRS scheduler higher
deck-config-rwkv-comparison-agreement-rwkv = RWKV scheduler higher
deck-config-rwkv-comparison-agreement-mixed = Mixed / tied
deck-config-rwkv-comparison-agreement-unavailable = Unavailable
deck-config-rwkv-comparison-agreement-fsrs-answers = FSRS answers
deck-config-rwkv-comparison-agreement-rwkv-answers = RWKV answers
deck-config-rwkv-comparison-agreement-memory-order = Memory estimates: FSRS / RWKV
deck-config-rwkv-comparison-agreement-empty = No tested retentions at or above the minimum.
deck-config-rwkv-comparison-agreement-both-help = Running both answer models takes longer; each uses the same cards, starting state, scenario, and background reviews.
deck-config-rwkv-comparison-assumptions =
    Both schedulers use the same cards, starting due dates, simulated learner and average time per grade. Both models estimate memory from each scheduler's simulated answers; change the scoring model without rerunning.
    The last year of reviews on other cards is replayed as fixed background activity for RWKV, including in the no-review baseline. The learner and background remain assumptions, and sampled results are estimates. One paired run can be noisy.
    More cards, finer steps, background reviews and more runs take longer. Review ordering is approximated.
    The day-level comparison excludes simulated learning and relearning steps, easy days, dynamic retention, preset switching and the RWKV-Instant queue.
deck-config-rwkv-comparison-run-details = Run { $run }, { $preset }, { $learner }, { $scenario }: { $sampled } of { $total } cards simulated; { $estimated } sampled cards used estimated FSRS states; { $background } reviews in the one-year background pattern; { $days } days, { $runs } paired runs, { $step }-point retention step.
deck-config-rwkv-comparison-matched-memory-help =
    The chart compares the same requested retention, which can produce different simulated memory.
    Below, each FSRS point is matched with the cheapest RWKV point on the tested grid with at least as much simulated memory.
    Negative savings mean RWKV took more time; unmatched targets are excluded. Selecting the cheapest point can make savings look optimistic when runs are noisy.
deck-config-rwkv-comparison-no-memory-match = No tested points matched in simulated memory.

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.

# Checkbox
deck-config-health-check = Check health when optimizing
# Button
deck-config-health-check-button = Check Health
# Message box showing the result of the health check
deck-config-fsrs-bad-fit-warning = Health Check:
    Your memory is difficult for FSRS to predict. Recommendations:

    - Suspend or reformulate any cards you constantly forget.
    - Use the answer buttons consistently. Keep in mind that "Hard" is a passing grade, not a failing grade.
    - Understand before you memorize.

    If you follow these suggestions, performance will usually improve over the next few months.
# Message box showing the result of the health check
deck-config-fsrs-good-fit = Health Check:
    FSRS can adapt to your memory well.

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-description-new-handling = Anki 2.1.41+ handling
deck-config-unable-to-determine-desired-retention =
    Unable to determine a minimum recommended retention.
deck-config-predicted-minimum-recommended-retention = Minimum recommended retention: { $num }
deck-config-compute-minimum-recommended-retention = Minimum recommended retention
deck-config-compute-optimal-retention-tooltip4 =
    This tool will attempt to find the desired retention value
    that will lead to the most material learnt, in the least amount of time. The calculated number can serve as a reference
    when deciding what to set your desired retention to. You may wish to choose a higher desired retention if you’re
    willing to invest more study time to achieve it. Setting your desired retention lower than the minimum
    is not recommended, as it will lead to a higher workload, because of the high forgetting rate.
deck-config-fsrs-simulator-radio-ratio = Time / Memorized Ratio
# $time here is pre-formatted e.g. "10 Seconds"
deck-config-fsrs-simulator-ratio-tooltip = { $time } per memorized card
deck-config-plotted-on-x-axis = (Plotted on the X-axis)
deck-config-a-100-day-interval = 
    { $days ->
        [one] A 100 day interval will become { $days } day.
       *[other] A 100 day interval will become { $days } days.
    }

deck-config-fsrs-simulator-y-axis-title-time = Review Time/Day
deck-config-fsrs-simulator-y-axis-title-count = Review Count/Day
deck-config-fsrs-simulator-y-axis-title-memorized = Memorized Total
deck-config-bury-siblings = Bury siblings
deck-config-do-not-bury = Do not bury siblings
deck-config-bury-if-new = Bury if new
deck-config-bury-if-new-or-review = Bury if new or review
deck-config-bury-if-new-review-or-interday = Bury if new, review, or interday learning
deck-config-bury-tooltip =
    Siblings are other cards from the same note (eg forward/reverse cards, or
    other cloze deletions from the same text).
    
    When this option is off, multiple cards from the same note may be seen on the same
    day. When enabled, Anki will automatically *bury* siblings, hiding them until the next
    day. This option allows you to choose which kinds of cards may be buried when you answer
    one of their siblings.
    
    When using the V3 scheduler, interday learning cards can also be buried. Interday
    learning cards are cards with a current learning step of one or more days.
deck-config-seconds-to-show-question-tooltip = When auto advance is activated, the number of seconds to wait before revealing the answer. Set to 0 to disable.
deck-config-answer-action-tooltip = The action to perform on the current card before automatically advancing to the next one.
deck-config-wait-for-audio-tooltip = Wait for audio to finish before automatically revealing answer or next question.
deck-config-ignore-before-tooltip = 
    If set, reviews before the provided date will be ignored when optimizing & evaluating FSRS parameters.
    This can be useful if you imported someone else's scheduling data, or have changed the way you use the answer buttons.
deck-config-compute-optimal-retention-tooltip =
    This tool assumes you're starting with 0 cards, and will attempt to calculate the amount of material you'll
    be able to retain in the given time frame. The estimated retention will greatly depend on your inputs, and
    if it significantly differs from 0.9, it's a sign that the time you've allocated each day is either too low
    or too high for the amount of cards you're trying to learn. This number can be useful as a reference, but it
    is not recommended to copy it into the desired retention field.
deck-config-health-check-tooltip1 = This will show a warning if FSRS struggles to adapt to your memory.
deck-config-health-check-tooltip2 = Health check is performed only when using Optimize Current Preset.

deck-config-compute-optimal-retention = Compute minimum recommended retention
deck-config-predicted-optimal-retention = Minimum recommended retention: { $num }
deck-config-weights-tooltip =
    FSRS parameters affect how cards are scheduled. Anki will start with default parameters. Once
    you've accumulated 1000+ reviews, you can use the option below to optimize the parameters to best
    match your performance in decks using this preset.
deck-config-compute-optimal-weights-tooltip =
    Once you've done 1000+ reviews in Anki, you can use the Optimize button to analyze your review history,
    and automatically generate parameters that are optimal for your memory and the content you're studying.
    If you have decks that vary wildly in difficulty, it is recommended to assign them separate presets, as
    the parameters for easy decks and hard decks will be different. There is no need to optimize your parameters
    frequently - once every few months is sufficient.
    
    By default, parameters will be calculated from the review history of all decks using the current preset. You can
    optionally adjust the search before calculating the parameters, if you'd like to alter which cards are used for
    optimizing the parameters.
deck-config-compute-optimal-retention-tooltip2 =
    This tool assumes that you’re starting with 0 learned cards, and will attempt to find the desired retention
    value that will lead to the most material learnt, in the least amount of time. This number can be used as a
    reference when deciding what to set your desired retention to. You may wish to choose a higher desired retention,
    if you’re willing to trade more study time for a greater recall rate. Setting your desired retention lower than
    the minimum is not recommended, as it will lead to more work without benefit.
deck-config-compute-optimal-retention-tooltip3 =
    This tool assumes that you’re starting with 0 learned cards, and will attempt to find the desired retention value 
    that will lead to the most material learnt, in the least amount of time. To accurately simulate your learning process, 
    this feature requires a minimum of 400+ reviews. The calculated number can serve as a reference when deciding what to 
    set your desired retention to. You may wish to choose a higher desired retention, if you’re willing to trade more study 
    time for a greater recall rate. Setting your desired retention lower than the minimum is not recommended, as it will 
    lead to a higher workload, because of the high forgetting rate.
deck-config-seconds-to-show-question-tooltip-2 = When auto advance is activated, the number of seconds to wait before revealing the answer. Set to 0 to disable.
deck-config-invalid-weights = Parameters must be either left blank to use the defaults, or must be 17 comma-separated numbers.
deck-config-fsrs-on-all-clients =
    Please ensure all of your Anki clients are Anki(Mobile) 23.10+ or AnkiDroid 2.17+. FSRS will
    not work correctly if one of your clients is older.
deck-config-optimize-all-tip = You can optimize all presets at once by using the dropdown button next to "Save".
