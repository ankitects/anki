# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from anki.utils import intTime

# Base importer
##########################################################################

class Importer(object):

    needMapper = False

    def __init__(self, deck, file):
        self.file = file
        self.log = []
        self.deck = deck
        self.total = 0

    def run(self):
        pass

    # Timestamps
    ######################################################################
    # It's too inefficient to check for existing ids on every object,
    # and a previous import may have created timestamps in the future, so we
    # need to make sure our starting point is safe.

    def _prepareTS(self):
        now = intTime(1000)
        for tbl in "cards", "facts":
            now = max(now, self.dst.db.scalar(
                "select max(id) from %s" % tbl))
        self._ts = now

    def ts(self):
        self._ts += 1
        return self._ts
