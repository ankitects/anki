# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from anki.utils import ids2str
from anki.sched import Scheduler

class CramScheduler(Scheduler):
    name = "cram"

    def __init__(self, deck, gids, order):
        Scheduler.__init__(self, deck)
        self.gids = gids
        self.order = order
        self.reset()

    def reset(self):
        pass

    def getCard(self):
        pass

    def answerCard(self):
        pass
