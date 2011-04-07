# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Open a deck:

    deck = anki.Deck(path)

Get a due card:

    card = deck.sched.getCard()
    if not card:
        # deck is finished

Show the card:

    print card.q(), card.a()

Answer the card:

    deck.sched.answerCard(card, ease)

Refresh after a change:

    deck.reset()

Edit the card:

    fact = card.fact()
    for (name, value) in fact.items():
        fact[name] = value + " new"
    fact.flush()

Save & close:

    deck.close()
"""

version = "1.99"
from anki.storage import Deck
