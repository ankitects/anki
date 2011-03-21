# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import time, datetime, simplejson, random
from operator import itemgetter
from heapq import *
#from anki.cards import Card
from anki.utils import parseTags, ids2str, intTime, fmtTimeSpan
from anki.lang import _, ngettext
from anki.consts import *
from anki.hooks import runHook

# the standard Anki scheduler
class Scheduler(object):
    name = "std"
    def __init__(self, deck):
        self.deck = deck
        self.db = deck.db
        self.queueLimit = 200
        self.reportLimit = 1000

    def getCard(self):
        "Pop the next card from the queue. None if finished."
        self._checkDay()
        id = self._getCardId()
        if id:
            c = self.deck.getCard(id)
            c.startTimer()
            return c

    def reset(self):
        self._updateCutoff()
        self._resetConf()
        t = time.time()
        self._resetLrn()
        #print "lrn %0.2fms" % ((time.time() - t)*1000); t = time.time()
        self._resetRev()
        #print "rev %0.2fms" % ((time.time() - t)*1000); t = time.time()
        self._resetNew()
        #print "new %0.2fms" % ((time.time() - t)*1000); t = time.time()

    def answerCard(self, card, ease):
        if card.queue == 0:
            # put it in the learn queue
            card.queue = 1
        if card.queue == 1:
            self._answerLrnCard(card, ease)
        elif card.queue == 2:
            self._answerRevCard(card, ease)
        else:
            raise Exception("Invalid queue")
        card.mod = intTime()
        card.flushSched()

    def counts(self):
        "Does not include fetched but unanswered."
        return (self.newCount, self.lrnCount, self.revCount)

    def dueForecast(self, days=7):
        "Return counts over next DAYS. Includes today."
        return self.db.list("""
select count() from cards
where queue = 2 %s
and due between ? and ?
group by due
order by due""" % self._groupLimit("rev"),
                            self.today,
                            self.today+days-1)

    def countIdx(self, card):
        return card.queue

    def onClose(self):
        "Unbury and remove temporary suspends on close."
        self.db.execute(
            "update cards set queue = type where queue between -3 and -2")

    def _resetSchedBuried(self):
        "Put temporarily suspended cards back into play."
        self.db.execute(
            "update cards set queue = type where queue = -3")

    # Getting the next card
    ##########################################################################

    def _getCardId(self):
        "Return the next due card id, or None."
        # learning card due?
        id = self._getLrnCard()
        if id:
            return id
        # new first, or time for one?
        if self._timeForNewCard():
            return self._getNewCard()
        # card due for review?
        id = self._getRevCard()
        if id:
            return id
        # new cards left?
        id = self._getNewCard()
        if id:
            return id
        # collapse or finish
        return self._getLrnCard(collapse=True)

    # New cards
    ##########################################################################

    # need to keep track of reps for timebox and new card introduction

    def _resetNewCount(self):
        l = self.deck.qconf
        if l['newToday'][0] != self.today:
            # it's a new day; reset counts
            l['newToday'] = [self.today, 0]
        lim = min(self.reportLimit, l['newPerDay'] - l['newToday'][1])
        if lim <= 0:
            self.newCount = 0
        else:
            self.newCount = self.db.scalar("""
select count() from (select id from cards where
queue = 0 %s limit %d)""" % (self._groupLimit('new'), lim))

    def _resetNew(self):
        self._resetNewCount()
        lim = min(self.queueLimit, self.newCount)
        self.newQueue = self.db.all("""
select id, due from cards where
queue = 0 %s order by due limit %d""" % (self._groupLimit('new'),
                                         lim))
        self.newQueue.reverse()
        self._updateNewCardRatio()

    def _getNewCard(self):
        if self.newQueue:
            (id, due) = self.newQueue.pop()
            # move any siblings to the end?
            if self.deck.qconf['newTodayOrder'] == NEW_TODAY_ORD:
                n = len(self.newQueue)
                while self.newQueue and self.newQueue[-1][1] == due:
                    self.newQueue.insert(0, self.newQueue.pop())
                    n -= 1
                    if not n:
                        # we only have one fact in the queue; stop rotating
                        break
            self.newCount -= 1
            return id

    def _updateNewCardRatio(self):
        if self.deck.qconf['newCardSpacing'] == NEW_CARDS_DISTRIBUTE:
            if self.newCount:
                self.newCardModulus = (
                    (self.newCount + self.revCount) / self.newCount)
                # if there are cards to review, ensure modulo >= 2
                if self.revCount:
                    self.newCardModulus = max(2, self.newCardModulus)
                return
        self.newCardModulus = 0

    def _timeForNewCard(self):
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

    def _resetLrnCount(self):
        self.lrnCount = self.db.scalar(
            "select count() from cards where queue = 1 and due < ?",
            intTime() + self.deck.qconf['collapseTime'])

    def _resetLrn(self):
        self._resetLrnCount()
        self.lrnQueue = self.db.all("""
select due, id from cards where
queue = 1 and due < :lim order by due
limit %d""" % self.reportLimit, lim=self.dayCutoff)

    def _getLrnCard(self, collapse=False):
        if self.lrnQueue:
            cutoff = time.time()
            if collapse:
                cutoff -= self.deck.collapseTime
            if self.lrnQueue[0][0] < cutoff:
                id = heappop(self.lrnQueue)[1]
                self.lrnCount -= 1
                return id

    def _answerLrnCard(self, card, ease):
        # ease 1=no, 2=yes, 3=remove
        conf = self._lrnConf(card)
        leaving = False
        if ease == 3:
            self._rescheduleAsRev(card, conf, True)
            leaving = True
        elif ease == 2 and card.grade+1 >= len(conf['delays']):
            self._rescheduleAsRev(card, conf, False)
            leaving = True
        else:
            card.cycles += 1
            if ease == 2:
                card.grade += 1
            else:
                card.grade = 0
            card.due = time.time() + self._delayForGrade(conf, card.grade)
        self._logLrn(card, ease, conf, leaving)

    def _delayForGrade(self, conf, grade):
        return conf['delays'][grade]*60

    def _lrnConf(self, card):
        conf = self._cardConf(card)
        if card.type == 2:
            return conf['lapse']
        else:
            return conf['new']

    def _rescheduleAsRev(self, card, conf, early):
        if card.type == 2:
            # failed; put back entry due
            card.due = card.edue
        else:
            self._rescheduleNew(card, conf, early)
        card.queue = 2
        card.type = 2

    def _graduatingIvl(self, card, conf, early):
        if not early:
            # graduate
            ideal =  conf['ints'][0]
        elif card.cycles:
            # remove
            ideal = conf['ints'][2]
        else:
            # first time bonus
            ideal = conf['ints'][1]
        return self._adjRevIvl(card, ideal)

    def _rescheduleNew(self, card, conf, early):
        card.ivl = self._graduatingIvl(card, conf, early)
        card.due = self.today+card.ivl
        card.factor = conf['initialFactor']

    def _logLrn(self, card, ease, conf, leaving):
        # limit time taken to global setting
        taken = min(card.timeTaken(), self._cardConf(card)['maxTaken']*1000)
        def log():
            self.deck.db.execute(
                "insert into revlog values (?,?,?,?,?,?,?,?,?)",
                int(time.time()*1000), card.id, ease, card.cycles,
                # interval
                self._delayForGrade(conf, card.grade),
                # last interval
                self._delayForGrade(conf, max(0, card.grade-1)),
                leaving, taken, 0)
        try:
            log()
        except:
            # duplicate pk; retry in 10ms
            time.sleep(0.01)
            log()

    def removeFailed(self):
        "Remove failed cards from the learning queue."
        self.deck.db.execute("""
update cards set
due = edue, queue = 2
where queue = 1 and type = 2
""")

    # Reviews
    ##########################################################################

    def _resetRevCount(self):
        self.revCount = self.db.scalar("""
select count() from (select id from cards where
queue = 2 %s and due <= :lim limit %d)""" % (
            self._groupLimit("rev"), self.reportLimit),
                                       lim=self.today)

    def _resetRev(self):
        self._resetRevCount()
        self.revQueue = self.db.list("""
select id from cards where
queue = 2 %s and due <= :lim order by %s limit %d""" % (
            self._groupLimit("rev"), self._revOrder(), self.queueLimit),
                                    lim=self.today)
        if self.deck.qconf['revCardOrder'] == REV_CARDS_RANDOM:
            random.shuffle(self.revQueue)
        else:
            self.revQueue.reverse()

    def _getRevCard(self):
        if self._haveRevCards():
            return self.revQueue.pop()

    def _haveRevCards(self):
        if self.revCount:
            if not self.revQueue:
                self._resetRev()
            return self.revQueue

    def _revOrder(self):
        return ("ivl desc",
                "ivl",
                "due")[self.deck.qconf['revCardOrder']]

    # Answering a review card
    ##########################################################################

    def _answerRevCard(self, card, ease):
        self.revCount -= 1
        card.reps += 1
        if ease == 1:
            self._rescheduleLapse(card)
        else:
            self._rescheduleRev(card, ease)
        self._logRev(card, ease)

    def _rescheduleLapse(self, card):
        conf = self._cardConf(card)['lapse']
        card.streak = 0
        card.lapses += 1
        card.lastIvl = card.ivl
        card.ivl = self._nextLapseIvl(card, conf)
        card.factor = max(1300, card.factor-200)
        card.due = card.edue = self.today + card.ivl
        # put back in the learn queue?
        if conf['relearn']:
            card.queue = 1
            self.lrnCount += 1
        # leech?
        self._checkLeech(card, conf)

    def _nextLapseIvl(self, card, conf):
        return int(card.ivl*conf['mult']) + 1

    def _rescheduleRev(self, card, ease):
        card.streak += 1
        # update interval
        card.lastIvl = card.ivl
        self._updateRevIvl(card, ease)
        # then the rest
        card.factor = max(1300, card.factor+[-150, 0, 150][ease-2])
        card.due = self.today + card.ivl

    def _logRev(self, card, ease):
        taken = min(card.timeTaken(), self._cardConf(card)['maxTaken']*1000)
        def log():
            self.deck.db.execute(
                "insert into revlog values (?,?,?,?,?,?,?,?,?)",
                int(time.time()*1000), card.id, ease, card.reps,
                card.ivl, card.lastIvl, card.factor, taken,
                1)
        try:
            log()
        except:
            # duplicate pk; retry in 10ms
            time.sleep(0.01)
            log()

    # Interval management
    ##########################################################################

    def _nextRevIvl(self, card, ease):
        "Ideal next interval for CARD, given EASE."
        delay = self._daysLate(card)
        conf = self._cardConf(card)
        fct = card.factor / 1000.0
        if ease == 2:
            interval = (card.ivl + delay/4) * 1.2
        elif ease == 3:
            interval = (card.ivl + delay/2) * fct
        elif ease == 4:
            interval = (card.ivl + delay) * fct * conf['rev']['ease4']
        # must be at least one day greater than previous interval
        return max(card.ivl+1, int(interval))

    def _daysLate(self, card):
        "Number of days later than scheduled."
        return max(0, self.today - card.due)

    def _updateRevIvl(self, card, ease):
        "Update CARD's interval, trying to avoid siblings."
        idealIvl = self._nextRevIvl(card, ease)
        card.ivl = self._adjRevIvl(card, idealIvl)

    def _adjRevIvl(self, card, idealIvl):
        "Given IDEALIVL, return an IVL away from siblings."
        idealDue = self.today + idealIvl
        conf = self._cardConf(card)['rev']
        # find sibling positions
        dues = self.db.list(
            "select due from cards where fid = ? and queue = 2"
            " and id != ?", card.fid, card.id)
        if not dues or idealDue not in dues:
            return idealIvl
        else:
            leeway = max(conf['minSpace'], int(idealIvl * conf['fuzz']))
            fudge = 0
            # do we have any room to adjust the interval?
            if leeway:
                # loop through possible due dates for an empty one
                for diff in range(1, leeway+1):
                    # ensure we're due at least tomorrow
                    if idealDue - diff >= 1 and (idealDue - diff) not in dues:
                        fudge = -diff
                        break
                    elif (idealDue + diff) not in dues:
                        fudge = diff
                        break
            return idealIvl + fudge

    # Leeches
    ##########################################################################

    def _checkLeech(self, card, conf):
        "Leech handler. True if card was a leech."
        lf = conf['leechFails']
        if not lf:
            return
        # if over threshold or every half threshold reps after that
        if (lf >= card.lapses and
            (card.lapses-lf) % (max(lf/2, 1)) == 0):
            # add a leech tag
            f = card.fact()
            f.tags.append("leech")
            f.flush()
            # handle
            if conf['leechAction'][0] == "suspend":
                self.suspendCards([card.id])
                card.queue = -1
            # notify UI
            runHook("leech", card)

    # Tools
    ##########################################################################

    def _resetConf(self):
        "Update group conf cache."
        self.groupConfs = dict(self.db.all("select id, gcid from groups"))
        self.confCache = {}

    def _cardConf(self, card):
        id = self.groupConfs[card.gid]
        if id not in self.confCache:
            self.confCache[id] = self.deck.groupConf(id)
        return self.confCache[id]

    def _groupLimit(self, type):
        l = self.deck.activeGroups(type)
        if not l:
            # everything
            return ""
        return " and gid in %s" % ids2str(l)

    # Daily cutoff
    ##########################################################################

    def _updateCutoff(self):
        # days since deck created
        self.today = int((time.time() - self.deck.crt) / 86400)
        # end of day cutoff
        self.dayCutoff = self.deck.crt + (self.today+1)*86400

    def _checkDay(self):
        # check if the day has rolled over
        if time.time() > self.dayCutoff:
            self.updateCutoff()
            self.reset()

    # Deck finished state
    ##########################################################################

    def finishedMsg(self):
        return (
            "<h1>"+_("Congratulations!")+"</h1>"+
            self._finishedSubtitle()+
            "<br><br>"+
            self._nextDueMsg())

    def _finishedSubtitle(self):
        if self.deck.activeGroups("rev") or self.deck.activeGroups("new"):
            return _("You have finished the selected groups for now.")
        else:
            return _("You have finished the deck for now.")

    def _nextDueMsg(self):
        line = []
        rev = self.revTomorrow() + self.lrnTomorrow()
        if rev:
            line.append(
                ngettext("There will be <b>%s review</b>.",
                         "There will be <b>%s reviews</b>.", rev) % rev)
        new = self.newTomorrow()
        if new:
            line.append(
                ngettext("There will be <b>%d new</b> card.",
                         "There will be <b>%d new</b> cards.", new) % new)
        if line:
            line.insert(0, _("At this time tomorrow:"))
            buf = "<br>".join(line)
        else:
            buf = _("No cards are due tomorrow.")
        buf = '<style>b { color: #00f; }</style>' + buf
        return buf

    def lrnTomorrow(self):
        "Number of cards in the learning queue due tomorrow."
        return self.db.scalar(
            "select count() from cards where queue = 1 and due < ?",
            self.dayCutoff+86400)

    def revTomorrow(self):
        "Number of reviews due tomorrow."
        return self.db.scalar(
            "select count() from cards where queue = 2 and due = ?"+
            self._groupLimit("rev"),
            self.today+1)

    def newTomorrow(self):
        "Number of new cards tomorrow."
        lim = self.deck.qconf['newPerDay']
        return self.db.scalar(
            "select count() from (select id from cards where "
            "queue = 0 limit %d)" % lim)

    # Next time reports
    ##########################################################################

    def nextIvlStr(self, card, ease, short=False):
        "Return the next interval for CARD as a string."
        return fmtTimeSpan(
            self.nextIvl(card, ease), short=short)

    def nextIvl(self, card, ease):
        "Return the next interval for CARD, in seconds."
        if card.queue in (0,1):
            # in learning
            return self._nextLrnIvl(card, ease)
        elif ease == 1:
            # lapsed
            conf = self._cardConf(card)['lapse']
            return self._nextLapseIvl(card, conf)*86400
        else:
            # review
            return self._nextRevIvl(card, ease)*86400

    # this isn't easily extracted from the learn code
    def _nextLrnIvl(self, card, ease):
        conf = self._lrnConf(card)
        if ease == 1:
            # grade 0
            return self._delayForGrade(conf, 0)
        elif ease == 3:
            # early removal
            return self._graduatingIvl(card, conf, True) * 86400
        else:
            grade = card.grade + 1
            if grade >= len(conf['delays']):
                # graduate
                return self._graduatingIvl(card, conf, False) * 86400
            else:
                # next level
                return self._delayForGrade(conf, grade)

    # Suspending
    ##########################################################################

    def suspendCards(self, ids):
        "Suspend cards."
        self.db.execute(
            "update cards set queue = -1, mod = ? where id in "+
            ids2str(ids), intTime())

    def unsuspendCards(self, ids):
        "Unsuspend cards."
        self.db.execute(
            "update cards set queue = type, mod = ? "
            "where queue = -1 and id in "+ ids2str(ids),
            intTime())

    def buryFact(self, fid):
        "Bury all cards for fact until next session."
        self.db.execute("update cards set queue = -2 where fid = ?", fid)

    # Counts
    ##########################################################################

    def timeToday(self):
        "Time spent learning today, in seconds."
        return self.deck.db.scalar(
            "select sum(taken/1000.0) from revlog where time > ?*1000",
            self.dayCutoff-86400) or 0

    def repsToday(self):
        "Number of cards answered today."
        return self.deck.db.scalar(
            "select count() from revlog where time > ?*1000",
            self.dayCutoff-86400)

    # Dynamic indices
    ##########################################################################

    def updateDynamicIndices(self):
        # determine required columns
        required = []
        if self.deck.qconf['revCardOrder'] in (
            REV_CARDS_OLD_FIRST, REV_CARDS_NEW_FIRST):
            required.append("interval")
        cols = ["queue", "due", "gid"] + required
        # update if changed
        if self.deck.db.scalar(
            "select 1 from sqlite_master where name = 'ix_cards_multi'"):
            rows = self.deck.db.all("pragma index_info('ix_cards_multi')")
        else:
            rows = None
        if not (rows and cols == [r[2] for r in rows]):
            self.db.execute("drop index if exists ix_cards_multi")
            self.db.execute("create index ix_cards_multi on cards (%s)" %
                              ", ".join(cols))
            self.db.execute("analyze")

#     def resetCards(self, ids=None):
#         "Reset progress on cards in IDS."
#         print "position in resetCards()"
#         sql = """
# update cards set mod=:now, position=0, type=2, queue=2, lastInterval=0,
# interval=0, due=created, factor=2.5, reps=0, successive=0, lapses=0, flags=0"""
#         sql2 = "delete from revlog"
#         if ids is None:
#             lim = ""
#         else:
#             sids = ids2str(ids)
#             sql += " where id in "+sids
#             sql2 += "  where cardId in "+sids
#         self.db.execute(sql, now=time.time())
#         self.db.execute(sql2)
#         if self.qconf['newCardOrder'] == NEW_CARDS_RANDOM:
#             # we need to re-randomize now
#             self.randomizeNewCards(ids)

#     def randomizeNewCards(self, cardIds=None):
#         "Randomize 'due' on all new cards."
#         now = time.time()
#         query = "select distinct fid from cards where reps = 0"
#         if cardIds:
#             query += " and id in %s" % ids2str(cardIds)
#         fids = self.db.list(query)
#         data = [{'fid': fid,
#                  'rand': random.uniform(0, now),
#                  'now': now} for fid in fids]
#         self.db.executemany("""
# update cards
# set due = :rand + ord,
# mod = :now
# where fid = :fid
# and type = 2""", data)

#     def orderNewCards(self):
#         "Set 'due' to card creation time."
#         self.db.execute("""
# update cards set
# due = created,
# mod = :now
# where type = 2""", now=time.time())

#     def rescheduleCards(self, ids, min, max):
#         "Reset cards and schedule with new interval in days (min, max)."
#         self.resetCards(ids)
#         vals = []
#         for id in ids:
#             r = random.uniform(min*86400, max*86400)
#             vals.append({
#                 'id': id,
#                 'due': r + time.time(),
#                 'int': r / 86400.0,
#                 't': time.time(),
#                 })
#         self.db.executemany("""
# update cards set
# interval = :int,
# due = :due,
# reps = 1,
# successive = 1,
# yesCount = 1,
# firstAnswered = :t,
# queue = 1,
# type = 1,
# where id = :id""", vals)
