# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import time, datetime, simplejson, random
from operator import itemgetter
from heapq import *
#from anki.cards import Card
from anki.utils import parseTags, ids2str
from anki.lang import _
from anki.consts import *

# the standard Anki scheduler
class Scheduler(object):
    def __init__(self, deck):
        self.deck = deck
        self.db = deck.db
        self.name = "main"
        self.queueLimit = 200
        self.learnLimit = 1000
        self.updateCutoff()

    def getCard(self):
        "Pop the next card from the queue. None if finished."
        self.checkDay()
        id = self.getCardId()
        if id:
            c = self.deck.getCard(id)
            c.startTimer()
            return c

    def reset(self):
        self.resetConf()
        t = time.time()
        self.resetLearn()
        #print "lrn %0.2fms" % ((time.time() - t)*1000); t = time.time()
        self.resetReview()
        #print "rev %0.2fms" % ((time.time() - t)*1000); t = time.time()
        self.resetNew()
        #print "new %0.2fms" % ((time.time() - t)*1000); t = time.time()

    def answerCard(self, card, ease):
        if card.queue == 0:
            self.answerLearnCard(card, ease)
        elif card.queue == 1:
            self.revCount -= 1
            self.answerRevCard(card, ease)
        elif card.queue == 2:
            # put it in the learn queue
            card.queue = 0
            self.newCount -= 1
            self.answerLearnCard(card, ease)
        else:
            raise Exception("Invalid queue")
        card.flushSched()

    def counts(self):
        # FIXME: should learn count include new cards due today, or be separate?
        return (self.learnCount, self.revCount)

    def cardQueue(self, card):
        return card.queue

    # Getting the next card
    ##########################################################################

    def getCardId(self):
        "Return the next due card id, or None."
        # learning card due?
        id = self.getLearnCard()
        if id:
            return id
        # new first, or time for one?
        if self.timeForNewCard():
            return self.getNewCard()
        # card due for review?
        id = self.getReviewCard()
        if id:
            return id
        # new cards left?
        id = self.getNewCard()
        if id:
            return id
        # collapse or finish
        return self.getLearnCard(collapse=True)

    # New cards
    ##########################################################################

    # need to keep track of reps for timebox and new card introduction

    def resetNew(self):
        l = self.deck.qconf
        if l['newToday'][0] != self.today:
            # it's a new day; reset counts
            l['newToday'] = [self.today, 0]
        lim = min(self.queueLimit, l['newPerDay'] - l['newToday'][1])
        if lim <= 0:
            self.newQueue = []
            self.newCount = 0
        else:
            self.newQueue = self.db.all("""
select id, due from cards where
queue = 2 %s order by due, id limit %d""" % (self.groupLimit('new'),
                                         lim))
            self.newQueue.reverse()
            self.newCount = len(self.newQueue)
        self.updateNewCardRatio()

    def getNewCard(self):
        if self.newQueue:
            (id, due) = self.newQueue.pop()
            # move any siblings to the end?
            if self.deck.qconf['newTodayOrder'] == NEW_TODAY_ORD:
                while self.newQueue and self.newQueue[-1][1] == due:
                    self.newQueue.insert(0, self.newQueue.pop())
            return id

    def updateNewCardRatio(self):
        if self.deck.qconf['newCardSpacing'] == NEW_CARDS_DISTRIBUTE:
            if self.newCount:
                self.newCardModulus = (
                    (self.newCount + self.revCount) / self.newCount)
                # if there are cards to review, ensure modulo >= 2
                if self.revCount:
                    self.newCardModulus = max(2, self.newCardModulus)
                return
        self.newCardModulus = 0

    def timeForNewCard(self):
        "True if it's time to display a new card when distributing."
        if not self.newCount:
            return False
        if self.deck.qconf['newCardSpacing'] == NEW_CARDS_LAST:
            return False
        elif self.deck.qconf['newCardSpacing'] == NEW_CARDS_FIRST:
            return True
        elif self.newCardModulus:
            return self.deck.reps and self.deck.reps % self.newCardModulus == 0

    # Learning queue
    ##########################################################################

    def resetLearn(self):
        self.learnQueue = self.db.all("""
select due, id from cards where
queue = 0 and due < :lim order by due
limit %d""" % self.learnLimit, lim=self.dayCutoff)
        self.learnCount = len(self.learnQueue)

    def getLearnCard(self, collapse=False):
        if self.learnQueue:
            cutoff = time.time()
            if collapse:
                cutoff -= self.deck.collapseTime
            if self.learnQueue[0][0] < cutoff:
                return heappop(self.learnQueue)[1]

    def answerLearnCard(self, card, ease):
        # ease 1=no, 2=yes, 3=remove
        conf = self.learnConf(card)
        leaving = False
        if ease == 3:
            self.rescheduleAsReview(card, conf, True)
            leaving = True
        elif ease == 2 and card.grade+1 >= len(conf['delays']):
            self.rescheduleAsReview(card, conf, False)
            leaving = True
        else:
            card.cycles += 1
            if ease == 2:
                card.grade += 1
            else:
                card.grade = 0
            card.due = time.time() + self.delayForGrade(conf, card.grade)
        try:
            self.logLearn(card, ease, conf, leaving)
        except:
            time.sleep(0.01)
            self.logLearn(card, ease, conf, leaving)

    def delayForGrade(self, conf, grade):
        return conf['delays'][grade]*60

    def learnConf(self, card):
        conf = self.confForCard(card)
        if card.type == 2:
            return conf['new']
        else:
            return conf['lapse']

    def rescheduleAsReview(self, card, conf, early):
        card.queue = 1
        card.type = 1
        if card.type == 1:
            # failed, nothing else to do
            pass
        else:
            self.rescheduleNew(card, conf, early)

    def rescheduleNew(self, card, conf, early):
        if not early:
            # graduate
            int_ = conf['ints'][0]
        elif card.cycles:
            # remove
            int_ = conf['ints'][2]
        else:
            # first time bonus
            int_ = conf['ints'][1]
        card.interval = int_
        card.factor = conf['initialFactor']

    def logLearn(self, card, ease, conf, leaving):
        self.deck.db.execute(
            "insert into revlog values (?,?,?,?,?,?,?,?,?)",
            int(time.time()*1000), card.id, ease, card.cycles,
            self.delayForGrade(conf, card.grade),
            self.delayForGrade(conf, max(0, card.grade-1)),
            leaving, card.timeTaken(), 0)

    # Reviews
    ##########################################################################

    def resetReview(self):
        self.revQueue = self.db.all("""
select id from cards where
queue = 1 %s and due < :lim order by %s limit %d""" % (
    self.groupLimit("rev"), self.revOrder(), self.queueLimit),
                                    lim=self.dayCutoff)
        if self.deck.qconf['revCardOrder'] == REV_CARDS_RANDOM:
            random.shuffle(self.revQueue)
        else:
            self.revQueue.reverse()
        self.revCount = len(self.revQueue)

    def getReviewCard(self):
        if self.haveRevCards():
            return self.revQueue.pop()

    def haveRevCards(self):
        if self.revCount:
            if not self.revQueue:
                self.fillRevQueue()
            return self.revQueue

    def revOrder(self):
        return ("ivl desc",
                "ivl",
                "due")[self.deck.qconf['revCardOrder']]

    # FIXME: rewrite
    def showFailedLast(self):
        return self.collapseTime or not self.delay0

    # Answering a card
    ##########################################################################

    def _answerCard(self, card, ease):
        undoName = _("Answer Card")
        self.setUndoStart(undoName)
        now = time.time()
        # old state
        oldState = self.cardState(card)
        oldQueue = self.cardQueue(card)
        lastDelaySecs = time.time() - card.due
        lastDelay = lastDelaySecs / 86400.0
        oldSuc = card.successive
        # update card details
        last = card.interval
        card.ivl = self.nextInterval(card, ease)
        if card.reps:
            # only update if card was not new
            card.lastDue = card.due
        card.due = self.nextDue(card, ease, oldState)
        if not self.finishScheduler:
            # don't update factor in custom schedulers
            self.updateFactor(card, ease)
        # spacing
        self.spaceCards(card)
        # adjust counts for current card
        if ease == 1:
            if card.due < self.dayCutoff:
                self.learnCount += 1
        if oldQueue == 0:
            self.learnCount -= 1
        elif oldQueue == 1:
            self.revCount -= 1
        else:
            self.newAvail -= 1
        # card stats
        self.updateCardStats(card, ease, oldState)
        # update type & ensure past cutoff
        card.type = self.cardType(card)
        card.queue = card.type
        if ease != 1:
            card.due = max(card.due, self.dayCutoff+1)
        # allow custom schedulers to munge the card
        if self.answerPreSave:
            self.answerPreSave(card, ease)
        # save
        card.due = card.due
        card.saveSched()
        # review history
        print "make sure flags is set correctly when reviewing early"
        logReview(self.db, card, ease, 0)
        self.modified = now
        # leech handling - we need to do this after the queue, as it may cause
        # a reset()
        isLeech = self.isLeech(card)
        if isLeech:
            self.handleLeech(card)
        runHook("cardAnswered", card.id, isLeech)
        self.setUndoEnd(undoName)

    def updateCardStats(self, card, ease, state):
        card.reps += 1
        if ease == 1:
            card.successive = 0
            card.lapses += 1
        else:
            card.successive += 1
        # if not card.firstAnswered:
        #     card.firstAnswered = time.time()

    def spaceCards(self, card):
        new = time.time() + self.newSpacing
        self.db.execute("""
update cards set
due = (case
when queue = 1 then due + 86400 * (case
  when interval*:rev < 1 then 0
  else interval*:rev
  end)
when queue = 2 then :new
end),
modified = :now
where id != :id and fid = :fid
and due < :cut
and queue between 1 and 2""",
                         id=card.id, now=time.time(), fid=card.fid,
                         cut=self.dayCutoff, new=new, rev=self.revSpacing)
        # update local cache of seen facts
        self.spacedFacts[card.fid] = new

# Flags: 0=standard review, 1=reschedule due to cram, drill, etc
# Rep: Repetition number. The same number may appear twice if a card has been
# manually rescheduled or answered on multiple sites before a sync.
#
# We store the times in integer milliseconds to avoid an extra index on the
# primary key.

    def logReview(db, card, ease, flags=0):
        db.execute("""
insert into revlog values (
:created, :cardId, :ease, :rep, :lastInterval, :interval, :factor,
:userTime, :flags)""",
                 created=int(time.time()*1000), cardId=card.id, ease=ease, rep=card.reps,
                 lastInterval=card.lastInterval, interval=card.interval,
                 factor=card.factor, userTime=int(card.userTime()*1000),
                 flags=flags)

    # Interval management
    ##########################################################################

    def nextInterval(self, card, ease):
        "Return the next interval for CARD given EASE."
        delay = self.adjustedDelay(card, ease)
        return self._nextInterval(card, delay, ease)

    def _nextInterval(self, card, delay, ease):
        interval = card.interval
        factor = card.factor
        # if cramming / reviewing early
        if delay < 0:
            interval = max(card.lastInterval, card.interval + delay)
            if interval < self.midIntervalMin:
                interval = 0
            delay = 0
        # if interval is less than mid interval, use presets
        if ease == 1:
            interval *= self.delay2
            if interval < self.hardIntervalMin:
                interval = 0
        elif interval == 0:
            if ease == 2:
                interval = random.uniform(self.hardIntervalMin,
                                          self.hardIntervalMax)
            elif ease == 3:
                interval = random.uniform(self.midIntervalMin,
                                          self.midIntervalMax)
            elif ease == 4:
                interval = random.uniform(self.easyIntervalMin,
                                          self.easyIntervalMax)
        else:
            # if not cramming, boost initial 2
            if (interval < self.hardIntervalMax and
                interval > 0.166):
                mid = (self.midIntervalMin + self.midIntervalMax) / 2.0
                interval = mid / factor
            # multiply last interval by factor
            if ease == 2:
                interval = (interval + delay/4) * 1.2
            elif ease == 3:
                interval = (interval + delay/2) * factor
            elif ease == 4:
                interval = (interval + delay) * factor * self.factorFour
            fuzz = random.uniform(0.95, 1.05)
            interval *= fuzz
        return interval

    def nextIntervalStr(self, card, ease, short=False):
        "Return the next interval for CARD given EASE as a string."
        int = self.nextInterval(card, ease)
        return anki.utils.fmtTimeSpan(int*86400, short=short)

    def nextDue(self, card, ease, oldState):
        "Return time when CARD will expire given EASE."
        if ease == 1:
            # 600 is a magic value which means no bonus, and is used to ease
            # upgrades
            cram = self.scheduler == "cram"
            if (not cram and oldState == "mature"
                and self.delay1 and self.delay1 != 600):
                # user wants a bonus of 1+ days. put the failed cards at the
                # start of the future day, so that failures that day will come
                # after the waiting cards
                return self.dayCutoff + (self.delay1 - 1)*86400
            else:
                due = 0
        else:
            due = card.interval * 86400.0
        return due + time.time()

    def updateFactor(self, card, ease):
        "Update CARD's factor based on EASE."
        print "update cardIsBeingLearnt()"
        if not card.reps:
            # card is new, inherit beginning factor
            card.factor = self.averageFactor
        if card.successive and not self.cardIsBeingLearnt(card):
            if ease == 1:
                card.factor -= 0.20
            elif ease == 2:
                card.factor -= 0.15
        if ease == 4:
            card.factor += 0.10
        card.factor = max(1.3, card.factor)

    def adjustedDelay(self, card, ease):
        "Return an adjusted delay value for CARD based on EASE."
        if self.cardIsNew(card):
            return 0
        if card.due <= self.dayCutoff:
            return (self.dayCutoff - card.due) / 86400.0
        else:
            return (self.dayCutoff - card.due) / 86400.0

    # Leeches
    ##########################################################################

    def isLeech(self, card):
        no = card.lapses
        fmax = self.getInt('leechFails')
        if not fmax:
            return
        return (
            # failed
            not card.successive and
            # greater than fail threshold
            no >= fmax and
            # at least threshold/2 reps since last time
            (fmax - no) % (max(fmax/2, 1)) == 0)

    def handleLeech(self, card):
        scard = self.cardFromId(card.id, True)
        tags = scard.fact.tags
        tags = addTags("Leech", tags)
        scard.fact.tags = canonifyTags(tags)
        scard.fact.setModified(textChanged=True, deck=self)
        self.updateFactTags([scard.fact.id])
        self.db.expunge(scard)
        if self.getBool('suspendLeeches'):
            self.suspendCards([card.id])
        self.reset()

    # Tools
    ##########################################################################

    def resetConf(self):
        "Update group conf cache."
        self.groupConfs = dict(self.db.all("select id, gcid from groups"))
        self.confCache = {}

    def confForCard(self, card):
        id = self.groupConfs[card.gid]
        if id not in self.confCache:
            self.confCache[id] = simplejson.loads(
                self.db.scalar("select conf from gconf where id = :id",
                               id=id))
        return self.confCache[id]

    def resetSchedBuried(self):
        "Put temporarily suspended cards back into play."
        self.db.execute(
            "update cards set queue = type where queue = -3")

    def groupLimit(self, type):
        l = self.deck.qconf[type+"Groups"]
        if not l:
            # everything
            return ""
        return " and gid in %s" % ids2str(l)

    # Daily cutoff
    ##########################################################################

    def updateCutoff(self):
        d = datetime.datetime.utcfromtimestamp(
            time.time() - self.deck.utcOffset) + datetime.timedelta(days=1)
        d = datetime.datetime(d.year, d.month, d.day)
        newday = self.deck.utcOffset - time.timezone
        d += datetime.timedelta(seconds=newday)
        cutoff = time.mktime(d.timetuple())
        # cutoff must not be in the past
        while cutoff < time.time():
            cutoff += 86400
        # cutoff must not be more than 24 hours in the future
        cutoff = min(time.time() + 86400, cutoff)
        self.dayCutoff = cutoff
        self.today = int(cutoff/86400 - self.deck.crt/86400)

    def checkDay(self):
        # check if the day has rolled over
        if time.time() > self.dayCutoff:
            self.updateCutoff()
            self.reset()

    # Review early
    ##########################################################################

    def setupReviewEarlyScheduler(self):
        self.fillRevQueue = self._fillRevEarlyQueue
        self.rebuildRevCount = self._rebuildRevEarlyCount
        self.finishScheduler = self.setupStandardScheduler
        self.answerPreSave = self._reviewEarlyPreSave
        self.scheduler = "reviewEarly"

    def _reviewEarlyPreSave(self, card, ease):
        if ease > 1:
            # prevent it from appearing in next queue fill
            card.queue = -3

    def _rebuildRevEarlyCount(self):
        # in the future it would be nice to skip the first x days of due cards
        self.revCount = self.db.scalar(
            self.cardLimit(
            "revActive", "revInactive", """
select count() from cards c where queue = 1 and due > :now
"""), now=self.dayCutoff)

    def _fillRevEarlyQueue(self):
        if self.revCount and not self.revQueue:
            self.revQueue = self.db.all(
                self.cardLimit(
                "revActive", "revInactive", """
select id, fid from cards c where queue = 1 and due > :lim
order by due limit %d""" % self.queueLimit), lim=self.dayCutoff)
            self.revQueue.reverse()

    # Learn more
    ##########################################################################

    def setupLearnMoreScheduler(self):
        self.rebuildNewCount = self._rebuildLearnMoreCount
        self.updateNewCountToday = self._updateLearnMoreCountToday
        self.finishScheduler = self.setupStandardScheduler
        self.scheduler = "learnMore"

    def _rebuildLearnMoreCount(self):
        self.newAvail = self.db.scalar(
            self.cardLimit(
            "newActive", "newInactive",
            "select count(*) from cards c where queue = 2 "
            "and due < :lim"), lim=self.dayCutoff)
        self.spacedCards = []

    def _updateLearnMoreCountToday(self):
        self.newCount = self.newAvail
