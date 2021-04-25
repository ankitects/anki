# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    used by { $decks ->
        [one] { $decks } deck
       *[other] { $decks } decks
    }
deck-config-default-name = Default
deck-config-description-markdown = Enable markdown+clean HTML
deck-config-description-markdown-hint = Will appear as text on Anki 2.1.40 and below.
deck-config-title = Deck Options

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
