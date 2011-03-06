# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

MATURE_THRESHOLD = 21

# whether new cards should be mixed with reviews, or shown first or last
NEW_CARDS_DISTRIBUTE = 0
NEW_CARDS_LAST = 1
NEW_CARDS_FIRST = 2

# new card insertion order
NEW_CARDS_RANDOM = 0
NEW_CARDS_DUE = 1

# sort order for day's new cards
NEW_TODAY_ORD = 0
NEW_TODAY_DUE = 1

# review card sort order
REV_CARDS_OLD_FIRST = 0
REV_CARDS_NEW_FIRST = 1
REV_CARDS_RANDOM = 2

# Labels
##########################################################################

def newCardOrderLabels():
    return {
        0: _("Add new cards in random order"),
        1: _("Add new cards to end of queue"),
        }

def newCardSchedulingLabels():
    return {
        0: _("Spread new cards out through reviews"),
        1: _("Show new cards after all other cards"),
        2: _("Show new cards before reviews"),
        }

# FIXME: order due is not very useful anymore
def revCardOrderLabels():
    return {
        0: _("Review cards from largest interval"),
        1: _("Review cards from smallest interval"),
        2: _("Review cards in order due"),
        3: _("Review cards in random order"),
        }

def failedCardOptionLabels():
    return {
        0: _("Show failed cards soon"),
        1: _("Show failed cards at end"),
        2: _("Show failed cards in 10 minutes"),
        3: _("Show failed cards in 8 hours"),
        4: _("Show failed cards in 3 days"),
        5: _("Custom failed cards handling"),
        }

def alignmentLabels():
    return {
        0: _("Center"),
        1: _("Left"),
        2: _("Right"),
        }
