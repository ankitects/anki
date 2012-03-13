# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os

# whether new cards should be mixed with reviews, or shown first or last
NEW_CARDS_DISTRIBUTE = 0
NEW_CARDS_LAST = 1
NEW_CARDS_FIRST = 2

# new card insertion order
NEW_CARDS_RANDOM = 0
NEW_CARDS_DUE = 1

# review card sort order
REV_CARDS_RANDOM = 0
REV_CARDS_OLD_FIRST = 1
REV_CARDS_NEW_FIRST = 2

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

# deck schema & syncing vars
SCHEMA_VERSION = 3
SYNC_ZIP_SIZE = int(2.5*1024*1024)
SYNC_URL = os.environ.get("SYNC_URL") or "https://beta.ankiweb.net/sync/"
SYNC_VER = 1

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

def revCardOrderLabels():
    return {
        0: _("Sort by due date"),
        1: _("Sort by decreasing interval (slower)"),
        2: _("Sort by increasing interval (slower)"),
        }

def alignmentLabels():
    return {
        0: _("Center"),
        1: _("Left"),
        2: _("Right"),
        }

# todo: expand
def dynOrderLabels():
    return {
        0: _("Oldest seen first"),
        1: _("Random"),
        }
