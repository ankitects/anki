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
