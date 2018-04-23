# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
from anki.lang import _

# whether new cards should be mixed with reviews, or shown first or last
NEW_CARDS_DISTRIBUTE = 0
NEW_CARDS_LAST = 1
NEW_CARDS_FIRST = 2

# new card insertion order
NEW_CARDS_RANDOM = 0
NEW_CARDS_DUE = 1

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
SYNC_ZIP_SIZE = int(2.5*1024*1024)
SYNC_ZIP_COUNT = 25
SYNC_BASE = "https://sync.ankiweb.net/"
SYNC_MEDIA_BASE = "https://sync.ankiweb.net/msync/"
SYNC_VER = 9

HELP_SITE="http://ankisrs.net/docs/manual.html"

# from card.py
# Card and Queue types
# Card Types
# Type: 0=new, 1=learning, 2=due
# Queue: same as above, and:
#        -1=suspended, -2=user buried, -3=sched buried
# Due is used differently for different queues.
# - new queue: note id or random int
# - rev queue: integer day
# - lrn queue: integer timestamp

# from sched.py
# queue types: 0=new/cram, 1=lrn, 2=rev, 3=day lrn, -1=suspended, -2=buried
# revlog types: 0=lrn, 1=rev, 2=relrn, 3=cram

CARD_TYPE_NEW=0
CARD_TYPE_LEARNING=1
CARD_TYPE_DUE=2

QUEUE_TYPE_SUSPENDED=-1
QUEUE_TYPE_USER_BURIED=-2
QUEUE_TYPE_SCHED_BURIED=-3
QUEUE_TYPE_NEW=CARD_TYPE_NEW
QUEUE_TYPE_LEARNING=CARD_TYPE_LEARNING
QUEUE_TYPE_DUE=CARD_TYPE_DUE
QUEUE_TYPE_DAY_LEARN=3
QUEUE_TYPE_PREVIEW=4


# Labels
##########################################################################

def newCardOrderLabels():
    return {
        0: _("Show new cards in random order"),
        1: _("Show new cards in order added")
        }

def newCardSchedulingLabels():
    return {
        0: _("Mix new cards and reviews"),
        1: _("Show new cards after reviews"),
        2: _("Show new cards before reviews"),
        }

def alignmentLabels():
    return {
        0: _("Center"),
        1: _("Left"),
        2: _("Right"),
        }

def dynOrderLabels():
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
