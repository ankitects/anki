# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
used-by-decks = used by { $decks ->
   [one]   1 deck
  *[other] {$decks} decks
  }

