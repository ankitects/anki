# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Any, Dict, Optional

import anki
from anki.lang import TR

# whether new cards should be mixed with reviews, or shown first or last
NEW_CARDS_DISTRIBUTE = 0
NEW_CARDS_LAST = 1
NEW_CARDS_FIRST = 2

# new card insertion order
NEW_CARDS_RANDOM = 0
NEW_CARDS_DUE = 1

# Queue types
QUEUE_TYPE_MANUALLY_BURIED = -3
QUEUE_TYPE_SIBLING_BURIED = -2
QUEUE_TYPE_SUSPENDED = -1
QUEUE_TYPE_NEW = 0
QUEUE_TYPE_LRN = 1
QUEUE_TYPE_REV = 2
QUEUE_TYPE_DAY_LEARN_RELEARN = 3
QUEUE_TYPE_PREVIEW = 4

# Card types
CARD_TYPE_NEW = 0
CARD_TYPE_LRN = 1
CARD_TYPE_REV = 2
CARD_TYPE_RELEARNING = 3

# removal types
REM_CARD = 0
REM_NOTE = 1
REM_DECK = 2

# count display
COUNT_ANSWERED = 0
COUNT_REMAINING = 1

# media log
MEDIA_ADD = 0
MEDIA_REM = 1

# Kind of decks
DECK_STD = 0
DECK_DYN = 1

# dynamic deck order
DYN_OLDEST = 0
DYN_RANDOM = 1
DYN_SMALLINT = 2
DYN_BIGINT = 3
DYN_LAPSES = 4
DYN_ADDED = 5
DYN_DUE = 6
DYN_REVADDED = 7
DYN_DUEPRIORITY = 8

DYN_MAX_SIZE = 99999

# model types
MODEL_STD = 0
MODEL_CLOZE = 1

STARTING_FACTOR = 2500

HELP_SITE = "https://docs.ankiweb.net/#/"

# Leech actions
LEECH_SUSPEND = 0
LEECH_TAGONLY = 1

# Buttons
BUTTON_ONE = 1
BUTTON_TWO = 2
BUTTON_THREE = 3
BUTTON_FOUR = 4

# Revlog types
REVLOG_LRN = 0
REVLOG_REV = 1
REVLOG_RELRN = 2
REVLOG_CRAM = 3
REVLOG_RESCHED = 4

# Labels
##########################################################################


def _tr(col: Optional[anki.collection.Collection]) -> Any:
    if col:
        return col.tr
    else:
        print("routine in consts.py should be passed col")
        from anki.lang import tr_legacyglobal

        return tr_legacyglobal


def newCardOrderLabels(col: Optional[anki.collection.Collection]) -> Dict[int, Any]:
    tr = _tr(col)
    return {
        0: tr(TR.SCHEDULING_SHOW_NEW_CARDS_IN_RANDOM_ORDER),
        1: tr(TR.SCHEDULING_SHOW_NEW_CARDS_IN_ORDER_ADDED),
    }


def newCardSchedulingLabels(
    col: Optional[anki.collection.Collection],
) -> Dict[int, Any]:
    tr = _tr(col)
    return {
        0: tr(TR.SCHEDULING_MIX_NEW_CARDS_AND_REVIEWS),
        1: tr(TR.SCHEDULING_SHOW_NEW_CARDS_AFTER_REVIEWS),
        2: tr(TR.SCHEDULING_SHOW_NEW_CARDS_BEFORE_REVIEWS),
    }


def dynOrderLabels(col: Optional[anki.collection.Collection]) -> Dict[int, Any]:
    tr = _tr(col)
    return {
        0: tr(TR.DECKS_OLDEST_SEEN_FIRST),
        1: tr(TR.DECKS_RANDOM),
        2: tr(TR.DECKS_INCREASING_INTERVALS),
        3: tr(TR.DECKS_DECREASING_INTERVALS),
        4: tr(TR.DECKS_MOST_LAPSES),
        5: tr(TR.DECKS_ORDER_ADDED),
        6: tr(TR.DECKS_ORDER_DUE),
        7: tr(TR.DECKS_LATEST_ADDED_FIRST),
        8: tr(TR.DECKS_RELATIVE_OVERDUENESS),
    }
