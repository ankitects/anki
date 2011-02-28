# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from anki.sched import Scheduler

class CramScheduler(Scheduler):

    # Cramming
    ##########################################################################

    def setupCramScheduler(self, active, order):
        self.getCardId = self._getCramCardId
        self.activeCramTags = active
        self.cramOrder = order
        self.rebuildNewCount = self._rebuildCramNewCount
        self.rebuildRevCount = self._rebuildCramCount
        self.rebuildLrnCount = self._rebuildLrnCramCount
        self.fillRevQueue = self._fillCramQueue
        self.fillLrnQueue = self._fillLrnCramQueue
        self.finishScheduler = self.setupStandardScheduler
        self.lrnCramQueue = []
        print "requeue cram"
        self.requeueCard = self._requeueCramCard
        self.cardQueue = self._cramCardQueue
        self.answerCard = self._answerCramCard
        self.spaceCards = self._spaceCramCards
        # reuse review early's code
        self.answerPreSave = self._cramPreSave
        self.cardLimit = self._cramCardLimit
        self.scheduler = "cram"

    def _cramPreSave(self, card, ease):
        # prevent it from appearing in next queue fill
        card.lastInterval = self.cramLastInterval
        card.type = -3

    def _spaceCramCards(self, card):
        self.spacedFacts[card.factId] = time.time() + self.newSpacing

    def _answerCramCard(self, card, ease):
        self.cramLastInterval = card.lastInterval
        self._answerCard(card, ease)
        if ease == 1:
            self.lrnCramQueue.insert(0, [card.id, card.factId])

    def _getCramCardId(self, check=True):
        self.checkDay()
        self.fillQueues()
        if self.lrnCardMax and self.learnCount >= self.lrnCardMax:
            return self.lrnQueue[-1][0]
        # card due for review?
        if self.revNoSpaced():
            return self.revQueue[-1][0]
        if self.lrnQueue:
            return self.lrnQueue[-1][0]
        if check:
            # collapse spaced cards before reverting back to old scheduler
            self.reset()
            return self.getCardId(False)
        # if we're in a custom scheduler, we may need to switch back
        if self.finishScheduler:
            self.finishScheduler()
            self.reset()
            return self.getCardId()

    def _cramCardQueue(self, card):
        if self.revQueue and self.revQueue[-1][0] == card.id:
            return 1
        else:
            return 0

    def _requeueCramCard(self, card, oldSuc):
        if self.cardQueue(card) == 1:
            self.revQueue.pop()
        else:
            self.lrnCramQueue.pop()

    def _rebuildCramNewCount(self):
        self.newAvail = 0
        self.newCount = 0

    def _cramCardLimit(self, active, inactive, sql):
        # inactive is (currently) ignored
        if isinstance(active, list):
            return sql.replace(
                "where", "where +c.id in " + ids2str(active) + " and")
        else:
            yes = parseTags(active)
            if yes:
                yids = tagIds(self.db, yes).values()
                return sql.replace(
                    "where ",
                    "where +c.id in (select cardId from cardTags where "
                    "tagId in %s) and " % ids2str(yids))
            else:
                return sql

    def _fillCramQueue(self):
        if self.revCount and not self.revQueue:
            self.revQueue = self.db.all(self.cardLimit(
                self.activeCramTags, "", """
select id, factId from cards c
where queue between 0 and 2
order by %s
limit %s""" % (self.cramOrder, self.queueLimit)))
            self.revQueue.reverse()

    def _rebuildCramCount(self):
        self.revCount = self.db.scalar(self.cardLimit(
            self.activeCramTags, "",
            "select count(*) from cards c where queue between 0 and 2"))

    def _rebuildLrnCramCount(self):
        self.learnCount = len(self.lrnCramQueue)

    def _fillLrnCramQueue(self):
        self.lrnQueue = self.lrnCramQueue
