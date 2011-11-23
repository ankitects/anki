# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

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

    note = card.note()
    for (name, value) in note.items():
        note[name] = value + " new"
    note.flush()

Save & close:

    deck.close()
"""

import sys
if sys.version_info[0] > 2:
    raise Exception("Anki should be run with python2.x.")
elif sys.version_info[1] < 5:
    raise Exception("Anki requires Python 2.5+")
if sys.getfilesystemencoding().lower() in ("ascii", "ansi_x3.4-1968"):
    raise Exception("Anki requires a UTF-8 locale.")

import os
if not os.path.exists(os.path.expanduser("~/.no-warranty")):
    raise Exception("Don't use this without reading the forum thread")

version = "1.99"
from anki.storage import Deck
