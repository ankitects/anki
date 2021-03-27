# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import sys
from typing import Any, Dict, NewType, Optional

import anki

# whether new cards should be mixed with reviews, or shown first or last
NewCardsReviewOrder = NewType("NewCardsReviewOrder", int)
NEW_CARDS_DISTRIBUTE = NewCardsReviewOrder(0)
NEW_CARDS_LAST = NewCardsReviewOrder(1)
NEW_CARDS_FIRST = NewCardsReviewOrder(2)

# new card insertion order
NewCardsInsertionOrder = NewType("NewCardsInsertionOrder", int)
NEW_CARDS_RANDOM = NewCardsInsertionOrder(0)
NEW_CARDS_DUE = NewCardsInsertionOrder(1)

# Queue types
CardQueue = NewType("CardQueue", int)
QUEUE_TYPE_MANUALLY_BURIED = CardQueue(-3)
QUEUE_TYPE_SIBLING_BURIED = CardQueue(-2)
QUEUE_TYPE_SUSPENDED = CardQueue(-1)
QUEUE_TYPE_NEW = CardQueue(0)
QUEUE_TYPE_LRN = CardQueue(1)
QUEUE_TYPE_REV = CardQueue(2)
QUEUE_TYPE_DAY_LEARN_RELEARN = CardQueue(3)
QUEUE_TYPE_PREVIEW = CardQueue(4)

# Card types
CardType = NewType("CardType", int)
CARD_TYPE_NEW = CardType(0)
CARD_TYPE_LRN = CardType(1)
CARD_TYPE_REV = CardType(2)
CARD_TYPE_RELEARNING = CardType(3)

# removal types
RemovalType = NewType("RemovalType", int)
REM_CARD = RemovalType(0)
REM_NOTE = RemovalType(1)
REM_DECK = RemovalType(2)

# count display
CountDisplay = NewType("CountDisplay", int)
COUNT_ANSWERED = CountDisplay(0)
COUNT_REMAINING = CountDisplay(1)

# media log
MediaLog = NewType("MediaLog", int)
MEDIA_ADD = MediaLog(0)
MEDIA_REM = MediaLog(1)

# Kind of decks
DeckKind = NewType("DeckKind", int)
DECK_STD = DeckKind(0)
DECK_DYN = DeckKind(1)

# dynamic deck order
DynDeckOrder = NewType("DynDeckOrder", int)
DYN_OLDEST = DynDeckOrder(0)
DYN_RANDOM = DynDeckOrder(1)
DYN_SMALLINT = DynDeckOrder(2)
DYN_BIGINT = DynDeckOrder(3)
DYN_LAPSES = DynDeckOrder(4)
DYN_ADDED = DynDeckOrder(5)
DYN_DUE = DynDeckOrder(6)
DYN_REVADDED = DynDeckOrder(7)
DYN_DUEPRIORITY = DynDeckOrder(8)

DYN_MAX_SIZE = 99999

# model types
ModelType = NewType("ModelType", int)
MODEL_STD = ModelType(0)
MODEL_CLOZE = ModelType(1)

STARTING_FACTOR = 2500

HELP_SITE = "https://docs.ankiweb.net/#/"

# Leech actions
LeechAction = NewType("LeechAction", int)
LEECH_SUSPEND = LeechAction(0)
LEECH_TAGONLY = LeechAction(1)

# Buttons
ButtonNumber = NewType("ButtonNumber", int)
BUTTON_ONE = ButtonNumber(1)
BUTTON_TWO = ButtonNumber(2)
BUTTON_THREE = ButtonNumber(3)
BUTTON_FOUR = ButtonNumber(4)

# Revlog types
RevlogType = NewType("RevlogType", int)
REVLOG_LRN = RevlogType(0)
REVLOG_REV = RevlogType(1)
REVLOG_RELRN = RevlogType(2)
REVLOG_CRAM = RevlogType(3)
REVLOG_RESCHED = RevlogType(4)

# Labels
##########################################################################


def _tr(col: Optional[anki.collection.Collection]) -> Any:
    if col:
        return col.tr
    else:
        print("routine in consts.py should be passed col")
        import traceback

        traceback.print_stack(file=sys.stdout)
        from anki.lang import tr_legacyglobal

        return tr_legacyglobal


def newCardOrderLabels(col: Optional[anki.collection.Collection]) -> Dict[int, Any]:
    tr = _tr(col)
    return {
        0: tr.scheduling_show_new_cards_in_random_order(),
        1: tr.scheduling_show_new_cards_in_order_added(),
    }


def newCardSchedulingLabels(
    col: Optional[anki.collection.Collection],
) -> Dict[int, Any]:
    tr = _tr(col)
    return {
        0: tr.scheduling_mix_new_cards_and_reviews(),
        1: tr.scheduling_show_new_cards_after_reviews(),
        2: tr.scheduling_show_new_cards_before_reviews(),
    }


def dynOrderLabels(col: Optional[anki.collection.Collection]) -> Dict[int, Any]:
    tr = _tr(col)
    return {
        0: tr.decks_oldest_seen_first(),
        1: tr.decks_random(),
        2: tr.decks_increasing_intervals(),
        3: tr.decks_decreasing_intervals(),
        4: tr.decks_most_lapses(),
        5: tr.decks_order_added(),
        6: tr.decks_order_due(),
        7: tr.decks_latest_added_first(),
        8: tr.decks_relative_overdueness(),
    }
