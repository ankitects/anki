# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from typing import Any, Dict

from anki.lang import _

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

# deck schema & syncing vars
SCHEMA_VERSION = 11
SYNC_ZIP_SIZE = int(2.5 * 1024 * 1024)
SYNC_ZIP_COUNT = 25
SYNC_BASE = "https://sync%s.ankiweb.net/"
SYNC_VER = 10

HELP_SITE = "https://apps.ankiweb.net/docs/manual.html"

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

# Labels
##########################################################################


def newCardOrderLabels() -> Dict[int, Any]:
    return {
        0: _("Show new cards in random order"),
        1: _("Show new cards in order added"),
    }


def newCardSchedulingLabels() -> Dict[int, Any]:
    return {
        0: _("Mix new cards and reviews"),
        1: _("Show new cards after reviews"),
        2: _("Show new cards before reviews"),
    }


def alignmentLabels() -> Dict[int, Any]:
    return {
        0: _("Center"),
        1: _("Left"),
        2: _("Right"),
    }


def dynOrderLabels() -> Dict[int, Any]:
    return {
        0: _("Oldest seen first"),
        1: _("Random"),
        2: _("Increasing intervals"),
        3: _("Decreasing intervals"),
        4: _("Most lapses"),
        5: _("Order added"),
        6: _("Order due"),
        7: _("Latest added first"),
        8: _("Relative overdueness"),
    }
