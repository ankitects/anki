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

# model types
MODEL_STD = 0
MODEL_CLOZE = 1

# deck schema & syncing vars
SCHEMA_VERSION = 11
SYNC_ZIP_SIZE = int(2.5*1024*1024)
SYNC_URL = os.environ.get("SYNC_URL") or "https://beta.ankiweb.net/sync/"
SYNC_VER = 5

HELP_SITE="http://ankisrs.net/docs/dev/manual.html"

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
        }

def dynExamples():
    # defaults are resched=True, steps=None, deck=True
    return [
        [_("<select preset>"), None],
        None,
        [_("Preview all new cards"), dict(
            search="is:new", resched=False, steps="1", order=5)],
        [_("Preview cards added today"), dict(
            search="added:1", resched=False, steps="1", order=5)],
        None,
        [_("Review today's forgotten cards"), dict(
            search="rated:1:1", order=4)],
        [_("Review ahead by two days"), dict(
            search="prop:due<=2", order=6)],
        [_("Review due cards with tag"), dict(
            search="is:due tag:%s" % _("type_tag_here"), order=6)],
        None,
        [_("Cram all cards"), dict(
            search="", order=0, steps="1 10 20")],
    ]
