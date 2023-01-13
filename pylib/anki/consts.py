# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import sys
from typing import TYPE_CHECKING, Any, NewType

from anki._legacy import DeprecatedNamesMixinForModule

# whether new cards should be mixed with reviews, or shown first or last
NEW_CARDS_DISTRIBUTE = 0
NEW_CARDS_LAST = 1
NEW_CARDS_FIRST = 2

# new card insertion order
NEW_CARDS_RANDOM = 0
NEW_CARDS_DUE = 1

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
STARTING_FACTOR_FRACTION = STARTING_FACTOR / 1000

HELP_SITE = "https://docs.ankiweb.net/"

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

import anki.collection


def _tr(col: anki.collection.Collection | None) -> Any:
    if col:
        return col.tr
    else:
        print("routine in consts.py should be passed col")
        import traceback

        traceback.print_stack(file=sys.stdout)
        from anki.lang import tr_legacyglobal

        return tr_legacyglobal


def new_card_order_labels(col: anki.collection.Collection | None) -> dict[int, Any]:
    tr = _tr(col)
    return {
        0: tr.scheduling_show_new_cards_in_random_order(),
        1: tr.scheduling_show_new_cards_in_order_added(),
    }


def new_card_scheduling_labels(
    col: anki.collection.Collection | None,
) -> dict[int, Any]:
    tr = _tr(col)
    return {
        0: tr.scheduling_mix_new_cards_and_reviews(),
        1: tr.scheduling_show_new_cards_after_reviews(),
        2: tr.scheduling_show_new_cards_before_reviews(),
    }


def new_gather_priority_choices(
    col: anki.collection.Collection | None,
) -> dict[int, Any]:
    tr = _tr(col)
    return {
        0: tr.deck_config_new_gather_priority_deck(),
        1: tr.deck_config_new_gather_priority_position_lowest_first(),
        2: tr.deck_config_new_gather_priority_position_highest_first(),
        3: tr.deck_config_new_gather_priority_random_notes(),
        4: tr.deck_config_new_gather_priority_random_cards(),
    }


def new_sort_order_choices(
    col: anki.collection.Collection | None,
) -> dict[int, Any]:
    tr = _tr(col)
    return {
        0: tr.deck_config_sort_order_template_then_gather(),
        1: tr.deck_config_sort_order_gather(),
        2: tr.deck_config_sort_order_card_template_then_random(),
        3: tr.deck_config_sort_order_random_note_then_template(),
        4: tr.deck_config_sort_order_random(),
    }


def review_mix_choices(
    col: anki.collection.Collection | None,
) -> dict[int, Any]:
    tr = _tr(col)
    return {
        0: tr.deck_config_review_mix_mix_with_reviews(),
        1: tr.deck_config_review_mix_show_after_reviews(),
        2: tr.deck_config_review_mix_show_before_reviews(),
    }


def review_order_choices(
    col: anki.collection.Collection | None,
) -> dict[int, Any]:
    tr = _tr(col)
    return {
        0: tr.deck_config_sort_order_due_date_then_random(),
        1: tr.deck_config_sort_order_due_date_then_deck(),
        2: tr.deck_config_sort_order_deck_then_due_date(),
        3: tr.deck_config_sort_order_ascending_intervals(),
        4: tr.deck_config_sort_order_descending_intervals(),
        5: tr.deck_config_sort_order_ascending_ease(),
        6: tr.deck_config_sort_order_descending_ease(),
        7: tr.deck_config_sort_order_relative_overdueness(),
    }


_deprecated_names = DeprecatedNamesMixinForModule(globals())


if not TYPE_CHECKING:

    def __getattr__(name: str) -> Any:
        return _deprecated_names.__getattr__(name)
