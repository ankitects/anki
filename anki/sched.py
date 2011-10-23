# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import time, datetime, simplejson, random, itertools
from operator import itemgetter
from heapq import *
#from anki.cards import Card
from anki.utils import ids2str, intTime, fmtTimeSpan
from anki.lang import _, ngettext
from anki.consts import *
from anki.hooks import runHook

# revlog:
# types: 0=lrn, 1=rev, 2=relrn, 3=cram
# positive intervals are in days (rev), negative intervals in seconds (lrn)

# the standard Anki scheduler
class Scheduler(object):
    name = "std"
    def __init__(self, deck):
        self.deck = deck
        self.queueLimit = 50
        self.reportLimit = 1000
        # fixme: replace reps with group based counts
        self.reps = 0
        self._updateCutoff()

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
        self._resetLrn()
        self._resetRev()
        self._resetNew()

    def answerCard(self, card, ease):
        assert ease >= 1 and ease <= 4
        self.deck.markReview(card)
        self.reps += 1
        card.reps += 1
        wasNew = (card.queue == 0) and card.type != 2
        if wasNew:
            # put it in the learn queue
            card.queue = 1
            card.type = 1
            card.left = self._startingLeft(card)
            self.lrnRepCount += card.left
            self._updateStats(card, 'new')
        if card.queue == 1:
            self._answerLrnCard(card, ease)
            if not wasNew:
                self._updateStats(card, 'lrn')
        elif card.queue == 2:
            self._answerRevCard(card, ease)
            self._updateStats(card, 'rev')
        else:
            raise Exception("Invalid queue")
        self._updateStats(card, 'time', card.timeTaken())
        card.mod = intTime()
        card.usn = self.deck.usn()
        card.flushSched()

    def repCounts(self):
        return (self.newCount, self.lrnRepCount, self.revCount)

    def cardCounts(self):
        return (self.newCount, self.lrnCount, self.revCount)

    def dueForecast(self, days=7):
        "Return counts over next DAYS. Includes today."
        daysd = dict(self.deck.db.all("""
select due, count() from cards
where gid in %s and queue = 2
and due between ? and ?
group by due
order by due""" % self._groupLimit(),
                            self.today,
                            self.today+days-1))
        for d in range(days):
            d = self.today+d
            if d not in daysd:
                daysd[d] = 0
        # return in sorted order
        ret = [x[1] for x in sorted(daysd.items())]
        return ret

    def countIdx(self, card):
        return card.queue

    def answerButtons(self, card):
        if card.queue == 2:
            return 4
        else:
            return 3

    def onClose(self):
        "Unbury and remove temporary suspends on close."
        self.deck.db.execute(
            "update cards set queue = type where queue between -3 and -2")

    # Rev/lrn/time daily stats
    ##########################################################################

    def _updateStats(self, card, type, cnt=1):
        key = type+"Today"
        for g in ([self.deck.groups.get(card.gid)] +
                  self.deck.groups.parents(card.gid)):
            # ensure we're on the correct day
            if g[key][0] != self.today:
                g[key] = [self.today, 0]
            # add
            g[key][1] += cnt
            self.deck.groups.save(g)

    # Group counts
    ##########################################################################

    def groupCounts(self):
        "Returns [groupname, hasDue, hasNew]"
        # find groups with 1 or more due cards
        gids = {}
        for g in self.deck.groups.all():
            hasDue = self._groupHasLrn(g['id']) or self._groupHasRev(g['id'])
            hasNew = self._groupHasNew(g['id'])
            gids[g['id']] = [hasDue or 0, hasNew or 0]
        return [[grp['name'], int(gid)]+gids[int(gid)] #.get(int(gid))
                for (gid, grp) in self.deck.groups.groups.items()]

    def groupCountTree(self):
        return self._groupChildren(self.groupCounts())

    def groupTree(self):
        "Like the count tree without the counts. Faster."
        return self._groupChildren(
            [[grp['name'], int(gid), 0, 0, 0]
             for (gid, grp) in self.deck.groups.groups.items()])

    def _groupChildren(self, grps):
        # first, split the group names into components
        for g in grps:
            g[0] = g[0].split("::")
        # and sort based on those components
        grps.sort(key=itemgetter(0))
        # then run main function
        return self._groupChildrenMain(grps)

    def _groupChildrenMain(self, grps):
        tree = []
        # group and recurse
        def key(grp):
            return grp[0][0]
        for (head, tail) in itertools.groupby(grps, key=key):
            tail = list(tail)
            gid = None
            rev = 0
            new = 0
            children = []
            for c in tail:
                if len(c[0]) == 1:
                    # current node
                    gid = c[1]
                    rev += c[2]
                    new += c[3]
                else:
                    # set new string to tail
                    c[0] = c[0][1:]
                    children.append(c)
            children = self._groupChildrenMain(children)
            # tally up children counts
            for ch in children:
                rev += ch[2]
                new += ch[3]
            tree.append((head, gid, rev, new, children))
        return tuple(tree)

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

    def _resetNewCount(self):
        self.newCount = 0
        pcounts = {}
        # for each of the active groups
        for gid in self.deck.groups.active():
            # get the individual group's limit
            lim = self._groupNewLimitSingle(self.deck.groups.get(gid))
            if not lim:
                continue
            # check the parents
            parents = self.deck.groups.parents(gid)
            for p in parents:
                # add if missing
                if p['id'] not in pcounts:
                    pcounts[p['id']] = self._groupNewLimitSingle(p)
                # take minimum of child and parent
                lim = min(pcounts[p['id']], lim)
            # see how many cards we actually have
            cnt = self.deck.db.scalar("""
select count() from (select 1 from cards where
gid = ? and queue = 0 limit ?)""", gid, lim)
            # if non-zero, decrement from parent counts
            for p in parents:
                pcounts[p['id']] -= cnt
            # we may also be a parent
            pcounts[gid] = lim - cnt
            # and add to running total
            self.newCount += cnt

    def _resetNew(self):
        self._resetNewCount()
        self.newGids = self.deck.groups.active()
        self._newQueue = []
        self._updateNewCardRatio()

    def _fillNew(self):
        if self._newQueue:
            return True
        if not self.newCount:
            return False
        while self.newGids:
            gid = self.newGids[0]
            lim = min(self.queueLimit, self._groupNewLimit(gid))
            if lim:
                # fill the queue with the current gid
                self._newQueue = self.deck.db.all("""
select id, due from cards where gid = ? and queue = 0 limit ?""", gid, lim)
                if self._newQueue:
                    self._newQueue.reverse()
                    return True
            # nothing left in the group; move to next
            self.newGids.pop(0)

    def _getNewCard(self):
        if not self._fillNew():
            return
        (id, due) = self._newQueue.pop()
        # move any siblings to the end?
        conf = self.deck.groups.conf(self.newGids[0])
        if conf['new']['order'] == NEW_TODAY_ORD:
            n = len(self._newQueue)
            while self._newQueue and self._newQueue[-1][1] == due:
                self._newQueue.insert(0, self._newQueue.pop())
                n -= 1
                if not n:
                    # we only have one fact in the queue; stop rotating
                    break
        self.newCount -= 1
        return id

    def _updateNewCardRatio(self):
        if self.deck.groups.top()['newSpread'] == NEW_CARDS_DISTRIBUTE:
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
        if self.deck.groups.top()['newSpread'] == NEW_CARDS_LAST:
            return False
        elif self.deck.groups.top()['newSpread'] == NEW_CARDS_FIRST:
            return True
        elif self.newCardModulus:
            return self.reps and self.reps % self.newCardModulus == 0

    def _groupHasNew(self, gid):
        if not self._groupNewLimit(gid):
            return False
        return self.deck.db.scalar(
            "select 1 from cards where gid = ? and queue = 0 limit 1", gid)

    def _groupNewLimit(self, gid):
        sel = self.deck.groups.get(gid)
        lim = -1
        # for the group and each of its parents
        for g in [sel] + self.deck.groups.parents(gid):
            rem = self._groupNewLimitSingle(g)
            if lim == -1:
                lim = rem
            else:
                lim = min(rem, lim)
        return lim

    def _groupNewLimitSingle(self, g):
        # update day if necessary
        if g['newToday'][0] != self.today:
            g['newToday'] = [self.today, 0]
        c = self.deck.groups.conf(g['id'])
        return max(0, c['new']['perDay'] - g['newToday'][1])

    # Learning queue
    ##########################################################################

    def _resetLrnCount(self):
        (self.lrnCount, self.lrnRepCount) = self.deck.db.first("""
select count(), sum(left) from (select left from cards where
gid in %s and queue = 1 and due < ? limit %d)""" % (
            self._groupLimit(), self.reportLimit),
            self.dayCutoff)
        self.lrnRepCount = self.lrnRepCount or 0

    def _resetLrn(self):
        self._resetLrnCount()
        self._lrnQueue = []

    def _fillLrn(self):
        if not self.lrnCount:
            return False
        if self._lrnQueue:
            return True
        self._lrnQueue = self.deck.db.all("""
select due, id from cards where
gid in %s and queue = 1 and due < :lim
limit %d""" % (self._groupLimit(), self.reportLimit), lim=self.dayCutoff)
        # as it arrives sorted by gid first, we need to sort it
        self._lrnQueue.sort()
        return self._lrnQueue

    def _getLrnCard(self, collapse=False):
        if self._fillLrn():
            cutoff = time.time()
            if collapse:
                cutoff += self.deck.groups.top()['collapseTime']
            if self._lrnQueue[0][0] < cutoff:
                id = heappop(self._lrnQueue)[1]
                self.lrnCount -= 1
                return id

    def _answerLrnCard(self, card, ease):
        # ease 1=no, 2=yes, 3=remove
        conf = self._lrnConf(card)
        if card.type == 2:
            type = 2
        else:
            type = 0
        leaving = False
        lastLeft = card.left
        if ease == 3:
            self._rescheduleAsRev(card, conf, True)
            self.lrnRepCount -= lastLeft
            leaving = True
        elif ease == 2 and card.left-1 <= 0:
            self._rescheduleAsRev(card, conf, False)
            self.lrnRepCount -= 1
            leaving = True
        else:
            if ease == 2:
                card.left -= 1
                self.lrnRepCount -= 1
            else:
                card.left = self._startingLeft(card)
                self.lrnRepCount += card.left - lastLeft
            delay = self._delayForGrade(conf, card.left)
            if card.due < time.time():
                # not collapsed; add some randomness
                delay *= random.uniform(1, 1.25)
            card.due = int(time.time() + delay)
            heappush(self._lrnQueue, (card.due, card.id))
            self.lrnCount += 1
        self._logLrn(card, ease, conf, leaving, type, lastLeft)

    def _delayForGrade(self, conf, left):
        try:
            delay = conf['delays'][-left]
        except IndexError:
            delay = conf['delays'][0]
        return delay*60

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

    def _startingLeft(self, card):
        return len(self._cardConf(card)['new']['delays'])

    def _graduatingIvl(self, card, conf, early):
        if card.type == 2:
            # lapsed card being relearnt
            return card.ivl
        if not early:
            # graduate
            ideal =  conf['ints'][0]
        else:
            # early remove
            ideal = conf['ints'][1]
        return self._adjRevIvl(card, ideal)

    def _rescheduleNew(self, card, conf, early):
        card.ivl = self._graduatingIvl(card, conf, early)
        card.due = self.today+card.ivl
        card.factor = conf['initialFactor']

    def _logLrn(self, card, ease, conf, leaving, type, lastLeft):
        lastIvl = -(self._delayForGrade(conf, lastLeft))
        ivl = card.ivl if leaving else -(self._delayForGrade(conf, card.left))
        def log():
            self.deck.db.execute(
                "insert into revlog values (?,?,?,?,?,?,?,?,?)",
                int(time.time()*1000), card.id, self.deck.usn(), ease,
                ivl, lastIvl, card.factor, card.timeTaken(), type)
        try:
            log()
        except:
            # duplicate pk; retry in 10ms
            time.sleep(0.01)
            log()

    def removeFailed(self, ids=None):
        "Remove failed cards from the learning queue."
        extra = ""
        if ids:
            extra = " and id in "+ids2str(ids)
        self.deck.db.execute("""
update cards set
due = edue, queue = 2, mod = %d, usn = %d
where queue = 1 and type = 2
%s
""" % (intTime(), self.deck.usn(), extra))

    def _groupHasLrn(self, gid):
        return self.deck.db.scalar(
            "select 1 from cards where gid = ? and queue = 1 "
            "and due < ? limit 1",
            gid, intTime() + self.deck.groups.top()['collapseTime'])

    # Reviews
    ##########################################################################

    def _groupHasRev(self, gid):
        return self.deck.db.scalar(
            "select 1 from cards where gid = ? and queue = 2 "
            "and due <= ? limit 1",
            gid, self.today)

    def _resetRevCount(self):
        self.revCount = self.deck.db.scalar("""
select count() from (select id from cards where
gid in %s and queue = 2 and due <= :lim limit %d)""" % (
            self._groupLimit(), self.reportLimit),
                                       lim=self.today)

    def _resetRev(self):
        self._resetRevCount()
        self._revQueue = []

    def _fillRev(self):
        if not self.revCount:
            return False
        if self._revQueue:
            return True
        self._revQueue = self.deck.db.list("""
select id from cards where
gid in %s and queue = 2 and due <= :lim %s limit %d""" % (
            self._groupLimit(), self._revOrder(), self.queueLimit),
                                          lim=self.today)
        if not self.deck.conf['revOrder']:
            r = random.Random()
            r.seed(self.today)
            r.shuffle(self._revQueue)
        return True

    def _getRevCard(self):
        if self._fillRev():
            self.revCount -= 1
            return self._revQueue.pop()

    def _revOrder(self):
        if self.deck.conf['revOrder']:
            return "order by %s" % ("ivl desc", "ivl")[self.deck.conf['revOrder']-1]
        return ""

    # Answering a review card
    ##########################################################################

    def _answerRevCard(self, card, ease):
        if ease == 1:
            self._rescheduleLapse(card)
        else:
            self._rescheduleRev(card, ease)
        self._logRev(card, ease)

    def _rescheduleLapse(self, card):
        conf = self._cardConf(card)['lapse']
        card.lapses += 1
        card.lastIvl = card.ivl
        card.ivl = self._nextLapseIvl(card, conf)
        card.factor = max(1300, card.factor-200)
        card.due = self.today + card.ivl
        # put back in the learn queue?
        if conf['relearn']:
            card.edue = card.due
            card.due = int(self._delayForGrade(conf, 0) + time.time())
            card.left = len(conf['delays'])
            card.queue = 1
            self.lrnCount += 1
            self.lrnRepCount += card.left
        # leech?
        if not self._checkLeech(card, conf) and conf['relearn']:
            heappush(self._lrnQueue, (card.due, card.id))

    def _nextLapseIvl(self, card, conf):
        return int(card.ivl*conf['mult']) + 1

    def _rescheduleRev(self, card, ease):
        # update interval
        card.lastIvl = card.ivl
        self._updateRevIvl(card, ease)
        # then the rest
        card.factor = max(1300, card.factor+[-150, 0, 150][ease-2])
        card.due = self.today + card.ivl

    def _logRev(self, card, ease):
        def log():
            self.deck.db.execute(
                "insert into revlog values (?,?,?,?,?,?,?,?,?)",
                int(time.time()*1000), card.id, self.deck.usn(), ease,
                card.ivl, card.lastIvl, card.factor, card.timeTaken(),
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
        # must be at least one day greater than previous interval; two if easy
        return max(card.ivl + (2 if ease==4 else 1), int(interval))

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
        dues = self.deck.db.list(
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
            f.addTag("leech")
            f.flush()
            # handle
            a = conf['leechAction']
            if a == 0:
                self.suspendCards([card.id])
                card.queue = -1
            # notify UI
            runHook("leech", card)
            return True

    # Tools
    ##########################################################################

    def _cardConf(self, card):
        return self.deck.groups.conf(card.gid)

    def _groupLimit(self):
        return ids2str(self.deck.groups.active())

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
            self._updateCutoff()
            self.reset()

    # Deck finished state
    ##########################################################################

    def finishedMsg(self):
        return (
            "<h1>"+_("Congratulations!")+"</h1>"+
            _("You have finished the selected groups for now.") +
            "<br><br>"+
            self._nextDueMsg())

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
        return self.deck.db.scalar(
            "select count() from cards where queue = 1 and due < ?",
            self.dayCutoff+86400)

    def revTomorrow(self):
        "Number of reviews due tomorrow."
        return self.deck.db.scalar(
            "select count() from cards where gid in %s and queue = 2 and due = ?"%
            self._groupLimit(), self.today+1)

    def newTomorrow(self):
        "Number of new cards tomorrow."
        print "fixme: rethink newTomorrow() etc"
        return 1
        lim = self.deck.groups.top()['newPerDay']
        return self.deck.db.scalar(
            "select count() from (select id from cards where "
            "gid in %s and queue = 0 limit %d)" % (self._groupLimit(), lim))

    # Next time reports
    ##########################################################################

    def nextIvlStr(self, card, ease, short=False):
        "Return the next interval for CARD as a string."
        return fmtTimeSpan(
            self.nextIvl(card, ease), short=short)

    def nextIvl(self, card, ease):
        "Return the next interval for CARD, in seconds."
        if card.queue in (0,1):
            return self._nextLrnIvl(card, ease)
        elif ease == 1:
            # lapsed
            conf = self._cardConf(card)['lapse']
            if conf['relearn']:
                return conf['delays'][0]*60
            return self._nextLapseIvl(card, conf)*86400
        else:
            # review
            return self._nextRevIvl(card, ease)*86400

    # this isn't easily extracted from the learn code
    def _nextLrnIvl(self, card, ease):
        conf = self._lrnConf(card)
        if ease == 1:
            # fail
            return self._delayForGrade(conf, len(conf['delays']))
        elif ease == 3:
            # early removal
            return self._graduatingIvl(card, conf, True) * 86400
        else:
            if card.queue == 0:
                left = self._startingLeft(card) - 1
            else:
                left = card.left - 1
            if left <= 0:
                # graduate
                return self._graduatingIvl(card, conf, False) * 86400
            else:
                return self._delayForGrade(conf, left)

    # Suspending
    ##########################################################################

    def suspendCards(self, ids):
        "Suspend cards."
        self.removeFailed(ids)
        self.deck.db.execute(
            "update cards set queue=-1,mod=?,usn=? where id in "+
            ids2str(ids), intTime(), self.deck.usn())

    def unsuspendCards(self, ids):
        "Unsuspend cards."
        self.deck.db.execute(
            "update cards set queue=type,mod=?,usn=? "
            "where queue = -1 and id in "+ ids2str(ids),
            intTime(), self.deck.usn())

    def buryFact(self, fid):
        "Bury all cards for fact until next session."
        self.deck.setDirty()
        self.removeFailed(
            self.deck.db.list("select id from cards where fid = ?", fid))
        self.deck.db.execute("update cards set queue = -2 where fid = ?", fid)

    # Resetting
    ##########################################################################

    def forgetCards(self, ids):
        "Put cards at the end of the new queue."
        self.deck.db.execute(
            "update cards set type=0,queue=0,ivl=0 where id in "+ids2str(ids))
        pmax = self.deck.db.scalar("select max(due) from cards where type=0")
        # takes care of mod + usn
        self.sortCards(ids, start=pmax+1, shuffle=self.deck.models.randomNew())

    def reschedCards(self, ids, imin, imax):
        "Put cards in review queue with a new interval in days (min, max)."
        d = []
        t = self.today
        mod = intTime()
        for id in ids:
            r = random.randint(imin, imax)
            d.append(dict(id=id, due=r+t, ivl=max(1, r), mod=mod))
        self.deck.db.executemany(
            "update cards set type=2,queue=2,ivl=:ivl,due=:due where id=:id",
            d)

    # Repositioning new cards
    ##########################################################################

    def sortCards(self, cids, start=1, step=1, shuffle=False, shift=False):
        scids = ids2str(cids)
        now = intTime()
        fids = self.deck.db.list(
            ("select distinct fid from cards where type = 0 and id in %s "
             "order by fid") % scids)
        if not fids:
            # no new cards
            return
        # determine fid ordering
        due = {}
        if shuffle:
            random.shuffle(fids)
        for c, fid in enumerate(fids):
            due[fid] = start+c*step
        high = start+c*step
        # shift?
        if shift:
            low = self.deck.db.scalar(
                "select min(due) from cards where due >= ? and type = 0 "
                "and id not in %s" % scids,
                start)
            if low is not None:
                shiftby = high - low + 1
                self.deck.db.execute("""
update cards set mod=?, usn=?, due=due+? where id not in %s
and due >= ?""" % scids, now, self.deck.usn(), shiftby, low)
        # reorder cards
        d = []
        for id, fid in self.deck.db.execute(
            "select id, fid from cards where type = 0 and id in "+scids):
            d.append(dict(now=now, due=due[fid], usn=self.deck.usn(), cid=id))
        self.deck.db.executemany(
            "update cards set due=:due,mod=:now,usn=:usn where id = :cid""", d)

    # fixme: because it's a model property now, these should be done on a
    # per-model basis
    def randomizeCards(self):
        self.sortCards(self.deck.db.list("select id from cards"), shuffle=True)

    def orderCards(self):
        self.sortCards(self.deck.db.list("select id from cards"))
