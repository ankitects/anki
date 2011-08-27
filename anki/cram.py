# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from anki.utils import ids2str, intTime
from anki.sched import Scheduler

# fixme: set log type for cram

class CramScheduler(Scheduler):
    name = "cram"

    def __init__(self, deck, order, min=0, max=None):
        Scheduler.__init__(self, deck)
        # should be the opposite order of what you want
        self.order = order
        # days to limit cram to, where tomorrow=0. Max is inclusive.
        self.min = min
        self.max = max
        self.reset()

    def counts(self):
        return (self.newCount, self.lrnCount, 0)

    def reset(self):
        self._updateCutoff()
        self._resetLrnCount()
        self._resetLrn()
        self._resetNew()
        self._resetRev()

    def answerCard(self, card, ease):
        if card.queue == 2:
            card.queue = 1
            card.edue = card.due
        if card.queue == 1:
            self._answerLrnCard(card, ease)
        else:
            raise Exception("Invalid queue")
        if ease == 1:
            conf = self._lrnConf(card)
            if conf['reset']:
                # reset interval
                card.ivl = max(1, int(card.ivl * conf['mult']))
                # mark card as due today so that it doesn't get rescheduled
                card.due = card.edue = self.today
        card.mod = intTime()
        card.flushSched()

    def countIdx(self, card):
        if card.queue == 2:
            return 0
        else:
            return 1

    def answerButtons(self, card):
        return 3

    # Fetching
    ##########################################################################

    def _resetNew(self):
        "All review cards that are not due yet."
        if self.max is not None:
            maxlim = "and due <= %d" % (self.today+1+self.max)
        else:
            maxlim = ""
        self.newQueue = self.deck.db.list("""
select id from cards where queue = 2 and due >= %d
%s
%s order by %s limit %d""" % (self.today+1+self.min,
                              maxlim,
                              self._groupLimit(),
                              self.order,
                              self.reportLimit))
        self.newCount = len(self.newQueue)

    def _resetRev(self):
        self.revQueue = []
        self.revCount = 0

    def _timeForNewCard(self):
        return True

    def _getNewCard(self):
        if self.newQueue:
            id = self.newQueue.pop()
            self.newCount -= 1
            return id

    # Answering
    ##########################################################################

    def _rescheduleAsRev(self, card, conf, early):
        Scheduler._rescheduleAsRev(self, card, conf, early)
        ivl = self._graduatingIvl(card, conf, early)
        card.due = self.today + ivl
        # temporarily suspend it
        self.deck.setDirty()
        card.queue = -3

    def _graduatingIvl(self, card, conf, early):
        if conf['resched']:
            # shift card by the time it was delayed
            return card.ivl - card.edue - self.today
        else:
            return card.ivl

    def _lrnConf(self, card):
        return self._cardConf(card)['cram']

    # Next time reports
    ##########################################################################

    def nextIvl(self, card, ease):
        "Return the next interval for CARD, in seconds."
        return self._nextLrnIvl(card, ease)
