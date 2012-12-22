# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from anki.utils import  maxID

# Base importer
##########################################################################

class Importer(object):

    needMapper = False
    needDelimiter = False

    def __init__(self, col, file):
        self.file = file
        self.log = []
        self.col = col
        self.total = 0

    def run(self):
        pass

    # Timestamps
    ######################################################################
    # It's too inefficient to check for existing ids on every object,
    # and a previous import may have created timestamps in the future, so we
    # need to make sure our starting point is safe.

    def _prepareTS(self):
        self._ts = maxID(self.dst.db)

    def ts(self):
        self._ts += 1
        return self._ts
