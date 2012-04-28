# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import time, datetime, simplejson, random, itertools, math
from operator import itemgetter
from heapq import *
#from anki.cards import Card
from anki.utils import ids2str, intTime, fmtTimeSpan
from anki.lang import _, ngettext
from anki.consts import *
from anki.hooks import runHook

# revlog types: 0=lrn, 1=rev, 2=relrn, 3=cram
# queue types: 0=new/cram, 1=lrn, 2=rev, -1=suspended, -2=buried
# positive intervals are in days (rev), negative intervals in seconds (lrn)

# fixme:
# - should log cram reps as cramming

class Scheduler(object):
    name = "std"
    def __init__(self, col):
        self.col = col
        self.queueLimit = 50
        self.reportLimit = 1000
        self.reps = 0
        self._haveQueues = False
        self._clearOverdue = True
        self._updateCutoff()

    def getCard(self):
        "Pop the next card from the queue. None if finished."
        self._checkDay()
        if not self._haveQueues:
            self.reset()
        card = self._getCard()
        if card:
            card.startTimer()
            return card

    def reset(self):
        deck = self.col.decks.current()
        self._updateCutoff()
        if self._clearOverdue:
            self.removeFailed(expiredOnly=True)
        self._resetLrn()
        self._resetRev()
        self._resetNew()
        self._haveQueues = True

    def answerCard(self, card, ease):
        assert ease >= 1 and ease <= 4
        self.col.markReview(card)
        self.reps += 1
        card.reps += 1
        wasNew = card.queue == 0
        if wasNew:
            # came from the new queue, move to learning
            card.queue = 1
            # if it was a new card, it's now a learning card
            if card.type == 0:
                card.type = 1
            # init reps to graduation
            card.left = self._startingLeft(card)
            # dynamic?
            if card.odid and card.type == 2:
                # reviews get their ivl boosted on first sight
                card.ivl = self._dynIvlBoost(card)
                card.odue = self.today + card.ivl
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
        card.usn = self.col.usn()
        card.flushSched()

    def counts(self, card=None):
        counts = [self.newCount, self.lrnCount, self.revCount]
        if card:
            idx = self.countIdx(card)
            if idx == 1:
                counts[1] += card.left
            else:
                counts[idx] += 1
        return tuple(counts)

    def dueForecast(self, days=7):
        "Return counts over next DAYS. Includes today."
        daysd = dict(self.col.db.all("""
select due, count() from cards
where did in %s and queue = 2
and due between ? and ?
group by due
order by due""" % self._deckLimit(),
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
        if not card.odid and card.odue:
            conf = self._lapseConf(card)
            if len(conf['delays']) > 1:
                return 3
            return 2
        elif card.queue == 2:
            return 4
        else:
            return 3

    def onClose(self):
        "Unbury cards when closing."
        self.col.db.execute(
            "update cards set queue = type where queue = -2")

    # Rev/lrn/time daily stats
    ##########################################################################

    def _updateStats(self, card, type, cnt=1):
        key = type+"Today"
        for g in ([self.col.decks.get(card.did)] +
                  self.col.decks.parents(card.did)):
            # add
            g[key][1] += cnt
            self.col.decks.save(g)

    def _walkingCount(self, limFn=None, cntFn=None):
        tot = 0
        pcounts = {}
        # for each of the active decks
        for did in self.col.decks.active():
            # early alphas were setting the active ids as a str
            did = int(did)
            # get the individual deck's limit
            lim = limFn(self.col.decks.get(did))
            if not lim:
                continue
            # check the parents
            parents = self.col.decks.parents(did)
            for p in parents:
                # add if missing
                if p['id'] not in pcounts:
                    pcounts[p['id']] = limFn(p)
                # take minimum of child and parent
                lim = min(pcounts[p['id']], lim)
            # see how many cards we actually have
            cnt = cntFn(did, lim)
            # if non-zero, decrement from parent counts
            for p in parents:
                pcounts[p['id']] -= cnt
            # we may also be a parent
            pcounts[did] = lim - cnt
            # and add to running total
            tot += cnt
        return tot

    # Deck list
    ##########################################################################

    def deckDueList(self):
        "Returns [deckname, did, due, new]"
        self._checkDay()
        if self._clearOverdue:
            self.removeFailed(expiredOnly=True)
        decks = self.col.decks.all()
        decks.sort(key=itemgetter('name'))
        lims = {}
        data = []
        def parent(name):
            parts = name.split("::")
            if len(parts) < 2:
                return None
            parts = parts[:-1]
            return "::".join(parts)
        for deck in decks:
            p = parent(deck['name'])
            # new
            nlim = self._deckNewLimitSingle(deck)
            if p:
                nlim = min(nlim, lims[p][0])
            new = self._newForDeck(deck['id'], nlim)
            # learning
            lrn = self._lrnForDeck(deck['id'])
            # reviews
            rlim = self._deckRevLimitSingle(deck)
            if p:
                rlim = min(rlim, lims[p][1])
            rev = self._revForDeck(deck['id'], rlim)
            # save to list
            data.append([deck['name'], deck['id'], lrn+rev, new])
            # add deck as a parent
            lims[deck['name']] = [nlim, rlim]
        return data

    def deckDueTree(self):
        return self._groupChildren(self.deckDueList())

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
            did = None
            rev = 0
            new = 0
            children = []
            for c in tail:
                if len(c[0]) == 1:
                    # current node
                    did = c[1]
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
            # limit the counts to the deck's limits
            conf = self.col.decks.confForDid(did)
            if not conf['dyn']:
                rev = min(rev, conf['rev']['perDay'])
                new = min(new, conf['new']['perDay'])
            tree.append((head, did, rev, new, children))
        return tuple(tree)

    # Getting the next card
    ##########################################################################

    def _getCard(self):
        "Return the next due card id, or None."
        # learning card due?
        c = self._getLrnCard()
        if c:
            return c
        # new first, or time for one?
        if self._timeForNewCard():
            return self._getNewCard()
        # card due for review?
        c = self._getRevCard()
        if c:
            return c
        # new cards left?
        c = self._getNewCard()
        if c:
            return c
        # collapse or finish
        return self._getLrnCard(collapse=True)

    # New cards
    ##########################################################################

    def _resetNewCount(self):
        cntFn = lambda did, lim: self.col.db.scalar("""
select count() from (select 1 from cards where
did = ? and queue = 0 limit ?)""", did, lim)
        self.newCount = self._walkingCount(self._deckNewLimitSingle, cntFn)

    def _resetNew(self):
        self._resetNewCount()
        self._newDids = self.col.decks.active()[:]
        self._newQueue = []
        self._updateNewCardRatio()

    def _fillNew(self):
        if self._newQueue:
            return True
        if not self.newCount:
            return False
        while self._newDids:
            did = self._newDids[0]
            lim = min(self.queueLimit, self._deckNewLimit(did))
            if lim:
                # fill the queue with the current did
                self._newQueue = self.col.db.all("""
select id, due from cards where did = ? and queue = 0 limit ?""", did, lim)
                if self._newQueue:
                    self._newQueue.reverse()
                    return True
            # nothing left in the deck; move to next
            self._newDids.pop(0)

    def _getNewCard(self):
        if not self._fillNew():
            return
        (id, due) = self._newQueue.pop()
        # move any siblings to the end?
        conf = self.col.decks.confForDid(self._newDids[0])
        if conf['dyn'] or conf['new']['separate']:
            n = len(self._newQueue)
            while self._newQueue and self._newQueue[-1][1] == due:
                self._newQueue.insert(0, self._newQueue.pop())
                n -= 1
                if not n:
                    # we only have one note in the queue; stop rotating
                    break
        self.newCount -= 1
        return self.col.getCard(id)

    def _updateNewCardRatio(self):
        if self.col.conf['newSpread'] == NEW_CARDS_DISTRIBUTE:
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
        if self.col.conf['newSpread'] == NEW_CARDS_LAST:
            return False
        elif self.col.conf['newSpread'] == NEW_CARDS_FIRST:
            return True
        elif self.newCardModulus:
            return self.reps and self.reps % self.newCardModulus == 0

    def _deckNewLimit(self, did, fn=None):
        if not fn:
            fn = self._deckNewLimitSingle
        sel = self.col.decks.get(did)
        lim = -1
        # for the deck and each of its parents
        for g in [sel] + self.col.decks.parents(did):
            rem = fn(g)
            if lim == -1:
                lim = rem
            else:
                lim = min(rem, lim)
        return lim

    def _newForDeck(self, did, lim):
        "New count for a single deck."
        if not lim:
            return 0
        lim = min(lim, self.reportLimit)
        return self.col.db.scalar("""
select count() from
(select 1 from cards where did = ? and queue = 0 limit ?)""", did, lim)

    def _deckNewLimitSingle(self, g):
        "Limit for deck without parent limits."
        if g['dyn']:
            return self.reportLimit
        c = self.col.decks.confForDid(g['id'])
        return max(0, c['new']['perDay'] - g['newToday'][1])

    # Learning queue
    ##########################################################################

    def _resetLrnCount(self):
        self.lrnCount = self.col.db.scalar("""
select sum(left) from (select left from cards where
did in %s and queue = 1 and due < ? limit %d)""" % (
            self._deckLimit(), self.reportLimit),
            self.dayCutoff) or 0

    def _resetLrn(self):
        self._resetLrnCount()
        self._lrnQueue = []

    def _fillLrn(self):
        if not self.lrnCount:
            return False
        if self._lrnQueue:
            return True
        self._lrnQueue = self.col.db.all("""
select due, id from cards where
did in %s and queue = 1 and due < :lim
limit %d""" % (self._deckLimit(), self.reportLimit), lim=self.dayCutoff)
        # as it arrives sorted by did first, we need to sort it
        self._lrnQueue.sort()
        return self._lrnQueue

    def _getLrnCard(self, collapse=False):
        if self._fillLrn():
            cutoff = time.time()
            if collapse:
                cutoff += self.col.conf['collapseTime']
            if self._lrnQueue[0][0] < cutoff:
                id = heappop(self._lrnQueue)[1]
                card = self.col.getCard(id)
                self.lrnCount -= card.left
                return card

    def _answerLrnCard(self, card, ease):
        # ease 1=no, 2=yes, 3=remove
        conf = self._lrnConf(card)
        if card.odid:
            type = 3
        elif card.type == 2:
            type = 2
        else:
            type = 0
        leaving = False
        # lrnCount was decremented once when card was fetched
        lastLeft = card.left
        # immediate graduate?
        if ease == 3:
            self._rescheduleAsRev(card, conf, True)
            leaving = True
        # graduation time?
        elif ease == 2 and card.left-1 <= 0:
            self._rescheduleAsRev(card, conf, False)
            leaving = True
        else:
            # one step towards graduation
            if ease == 2:
                card.left -= 1
            # failed
            else:
                card.left = self._startingLeft(card)
                if card.odid:
                    if 'mult' in conf:
                        # review that's lapsed
                        card.ivl = max(1, card.ivl*conf['mult'])
                    else:
                        # new card; no ivl adjustment
                        pass
                    card.odue = self.today + 1
            self.lrnCount += card.left
            delay = self._delayForGrade(conf, card.left)
            if card.due < time.time():
                # not collapsed; add some randomness
                delay *= random.uniform(1, 1.25)
            card.due = int(time.time() + delay)
            # if the queue is not empty and there's nothing else to do, make
            # sure we don't put it at the head of the queue and end up showing
            # it twice in a row
            if self._lrnQueue and not self.revCount and not self.newCount:
                smallestDue = self._lrnQueue[0][0]
                card.due = max(card.due, smallestDue+1)
            heappush(self._lrnQueue, (card.due, card.id))
        self._logLrn(card, ease, conf, leaving, type, lastLeft)

    def _delayForGrade(self, conf, left):
        try:
            delay = conf['delays'][-left]
        except IndexError:
            delay = conf['delays'][0]
        return delay*60

    def _lrnConf(self, card):
        if card.type == 2:
            return self._lapseConf(card)
        else:
            return self._newConf(card)

    def _rescheduleAsRev(self, card, conf, early):
        if card.type == 2:
            card.due = max(self.today+1, card.odue)
            card.odue = 0
        else:
            self._rescheduleNew(card, conf, early)
        card.queue = 2
        card.type = 2
        # if we were dynamic, graduating means moving back to the old deck
        if card.odid:
            card.did = card.odid
            card.odue = 0
            card.odid = 0

    def _startingLeft(self, card):
        conf = self._lrnConf(card)
        return len(conf['delays'])

    def _graduatingIvl(self, card, conf, early, adj=True):
        if card.type == 2:
            # lapsed card being relearnt
            if card.odid:
                return self._dynIvlBoost(card)
            return card.ivl
        if not early:
            # graduate
            ideal =  conf['ints'][0]
        else:
            # early remove
            ideal = conf['ints'][1]
        if adj:
            return self._adjRevIvl(card, ideal)
        else:
            return ideal

    def _rescheduleNew(self, card, conf, early):
        "Reschedule a new card that's graduated for the first time."
        card.ivl = self._graduatingIvl(card, conf, early)
        card.due = self.today+card.ivl
        card.factor = conf['initialFactor']

    def _logLrn(self, card, ease, conf, leaving, type, lastLeft):
        lastIvl = -(self._delayForGrade(conf, lastLeft))
        ivl = card.ivl if leaving else -(self._delayForGrade(conf, card.left))
        def log():
            self.col.db.execute(
                "insert into revlog values (?,?,?,?,?,?,?,?,?)",
                int(time.time()*1000), card.id, self.col.usn(), ease,
                ivl, lastIvl, card.factor, card.timeTaken(), type)
        try:
            log()
        except:
            # duplicate pk; retry in 10ms
            time.sleep(0.01)
            log()

    def removeFailed(self, ids=None, expiredOnly=False):
        "Remove failed cards from the learning queue."
        if ids:
            extra = " and id in "+ids2str(ids)
        else:
            # benchmarks indicate it's about 10x faster to search all decks
            # with the index than scan the table
            extra = " and did in "+ids2str(self.col.decks.allIds())
        if expiredOnly:
            extra += " and odue <= %d" % self.today
        self.col.db.execute("""
update cards set
due = odue, queue = 2, mod = %d, usn = %d
where queue = 1 and type = 2
%s
""" % (intTime(), self.col.usn(), extra))

    def _lrnForDeck(self, did):
        return self.col.db.scalar(
            """
select sum(left) from
(select left from cards where did = ? and queue = 1 and due < ? limit ?)""",
            did, intTime() + self.col.conf['collapseTime'], self.reportLimit) or 0

    # Reviews
    ##########################################################################

    def _deckRevLimit(self, did):
        return self._deckNewLimit(did, self._deckRevLimitSingle)

    def _deckRevLimitSingle(self, d):
        if d['dyn']:
            return self.reportLimit
        c = self.col.decks.confForDid(d['id'])
        return max(0, c['rev']['perDay'] - d['revToday'][1])

    def _revForDeck(self, did, lim):
        lim = min(lim, self.reportLimit)
        return self.col.db.scalar(
            """
select count() from
(select 1 from cards where did = ? and queue = 2
and due <= ? limit ?)""",
            did, self.today, lim)

    def _resetRevCount(self):
        def cntFn(did, lim):
            return self.col.db.scalar("""
select count() from (select id from cards where
did = ? and queue = 2 and due <= ? limit %d)""" % lim,
                                      did, self.today)
        self.revCount = self._walkingCount(
            self._deckRevLimitSingle, cntFn)

    def _resetRev(self):
        self._resetRevCount()
        self._revQueue = []
        self._revDids = self.col.decks.active()[:]

    def _fillRev(self):
        if self._revQueue:
            return True
        if not self.revCount:
            return False
        while self._revDids:
            did = self._revDids[0]
            lim = min(self.queueLimit, self._deckRevLimit(did))
            if lim:
                # fill the queue with the current did
                self._revQueue = self.col.db.list("""
select id from cards where
did = ? and queue = 2 and due <= ? limit ?""",
                                                  did, self.today, lim)
                if self._revQueue:
                    if self.col.decks.get(did)['dyn']:
                        # dynamic decks need due order preserved
                        self._revQueue.reverse()
                    else:
                        # random order for regular reviews
                        r = random.Random()
                        r.seed(self.today)
                        r.shuffle(self._revQueue)
                    return True
            # nothing left in the deck; move to next
            self._revDids.pop(0)

    def _getRevCard(self):
        if self._fillRev():
            self.revCount -= 1
            return self.col.getCard(self._revQueue.pop())

    # Answering a review card
    ##########################################################################

    def _answerRevCard(self, card, ease):
        if ease == 1:
            self._rescheduleLapse(card)
        else:
            self._rescheduleRev(card, ease)
        self._logRev(card, ease)

    def _rescheduleLapse(self, card):
        conf = self._lapseConf(card)
        card.lapses += 1
        card.lastIvl = card.ivl
        card.ivl = self._nextLapseIvl(card, conf)
        card.factor = max(1300, card.factor-200)
        card.due = self.today + card.ivl
        # put back in the learn queue?
        if conf['delays']:
            card.odue = card.due
            card.due = int(self._delayForGrade(conf, 0) + time.time())
            card.left = len(conf['delays'])
            card.queue = 1
            self.lrnCount += card.left
        # leech?
        if not self._checkLeech(card, conf) and conf['delays']:
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
        if card.odid:
            card.did = card.odid
            card.odid = 0
            card.odue = 0

    def _logRev(self, card, ease):
        def log():
            self.col.db.execute(
                "insert into revlog values (?,?,?,?,?,?,?,?,?)",
                int(time.time()*1000), card.id, self.col.usn(), ease,
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
        conf = self._revConf(card)
        fct = card.factor / 1000.0
        if ease == 2:
            interval = (card.ivl + delay/4) * 1.2
        elif ease == 3:
            interval = (card.ivl + delay/2) * fct
        elif ease == 4:
            interval = (card.ivl + delay) * fct * conf['ease4']
        # apply forgetting index transform
        interval = self._ivlForFI(conf, interval)
        # must be at least one day greater than previous interval; two if easy
        return max(card.ivl + (2 if ease==4 else 1), int(interval))

    def _ivlForFI(self, conf, ivl):
        new, old = conf['fi']
        return ivl * math.log(1-new/100.0) / math.log(1-old/100.0)

    def _daysLate(self, card):
        "Number of days later than scheduled."
        due = card.odue if card.odid else card.due
        return max(0, self.today - due)

    def _updateRevIvl(self, card, ease):
        "Update CARD's interval, trying to avoid siblings."
        idealIvl = self._nextRevIvl(card, ease)
        card.ivl = self._adjRevIvl(card, idealIvl)

    def _adjRevIvl(self, card, idealIvl):
        "Given IDEALIVL, return an IVL away from siblings."
        idealDue = self.today + idealIvl
        conf = self._revConf(card)
        # find sibling positions
        dues = self.col.db.list(
            "select due from cards where nid = ? and type = 2"
            " and id != ?", card.nid, card.id)
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
                    if idealIvl - diff >= 1 and (idealDue - diff) not in dues:
                        fudge = -diff
                        break
                    elif (idealDue + diff) not in dues:
                        fudge = diff
                        break
            return idealIvl + fudge

    # Dynamic deck handling
    ##########################################################################

    def rebuildDyn(self, did=None):
        "Rebuild a dynamic deck."
        did = did or self.col.decks.selected()
        deck = self.col.decks.get(did)
        assert deck['dyn']
        # move any existing cards back first
        self.remDyn(did)
        # gather card ids and sort
        order = self._dynOrder(deck)
        limit = " limit %d" % deck['limit']
        search = deck['search'] + " -is:suspended"
        try:
            ids = self.col.findCards(search, order=order+limit)
        except:
            ids = []
        # move the cards over
        self._moveToDyn(did, ids)
        # and change to our new deck
        self.col.decks.select(did)

    def remDyn(self, did, lim=None):
        if not lim:
            lim = "did = %s" % did
        self.col.db.execute("""
update cards set did = odid, queue = type, due = odue, odue = 0, odid = 0,
usn = ?, mod = ? where %s""" % lim, self.col.usn(), intTime())

    def remFromDyn(self, cids):
        self.remDyn(None, "id in %s and odid" % ids2str(cids))

    def _dynOrder(self, deck):
        o = deck['order']
        if o == DYN_OLDEST:
            return "order by c.mod"
        elif o == DYN_RANDOM:
            return "order by random()"
        elif o == DYN_SMALLINT:
            return "order by ivl"
        elif o == DYN_BIGINT:
            return "order by ivl desc"
        elif o == DYN_LAPSES:
            return "order by lapses desc"
        elif o == DYN_FAILED:
            return """
and c.id in (select cid from revlog where ease = 1 and time > %d)
order by c.mod""" % ((self.dayCutoff-86400)*1000)
        elif o == DYN_ADDED:
            return "order by n.id"
        elif o == DYN_DUE:
            return "order by c.due"

    def _moveToDyn(self, did, ids):
        deck = self.col.decks.get(did)
        data = []
        t = intTime(); u = self.col.usn()
        for c, id in enumerate(ids):
            # start at -1000 so that reviews are all due
            data.append((did, -1000+c, t, u, id))
        if deck['cramRev']:
            # everything in the new queue
            queue = "0"
        else:
            # due reviews stay in the review queue
            queue = "(case when type=2 and (odue or due) <= %d then 2 else 0 end)"
            queue %= self.today
        self.col.db.executemany("""
update cards set
odid = (case when odid then odid else did end),
odue = (case when odue then odue else due end),
did = ?, queue = %s, due = ?, mod = ?, usn = ? where id = ?""" % queue, data)

    def _dynIvlBoost(self, card):
        assert card.odid and card.type == 2
        assert card.factor
        elapsed = card.ivl - (card.odue - self.today)
        factor = ((card.factor/1000.0)+1.2)/2.0
        return int(max(card.ivl, elapsed * factor, 1))

    # Leeches
    ##########################################################################

    def _checkLeech(self, card, conf):
        "Leech handler. True if card was a leech."
        lf = conf['leechFails']
        if not lf:
            return
        # if over threshold or every half threshold reps after that
        if (card.lapses >= lf and
            (card.lapses-lf) % (max(lf/2, 1)) == 0):
            # add a leech tag
            f = card.note()
            f.addTag("leech")
            f.flush()
            # handle
            a = conf['leechAction']
            if a == 0:
                self.suspendCards([card.id])
                card.load()
            # notify UI
            runHook("leech", card)
            return True

    # Tools
    ##########################################################################

    def _cardConf(self, card):
        return self.col.decks.confForDid(card.did)

    def _newConf(self, card):
        conf = self._cardConf(card)
        # normal deck
        if not card.odid:
            return conf['new']
        # dynamic deck; override some attributes, use original deck for others
        oconf = self.col.decks.confForDid(card.odid)
        return dict(
            # original deck
            ints=oconf['new']['ints'],
            initialFactor=oconf['new']['initialFactor'],
            # overrides
            delays=conf['delays'],
            separate=conf['separate'],
            order=NEW_CARDS_DUE,
            perDay=self.reportLimit
        )

    def _lapseConf(self, card):
        conf = self._cardConf(card)
        # normal deck
        if not card.odid:
            return conf['lapse']
        # dynamic deck; override some attributes, use original deck for others
        oconf = self.col.decks.confForDid(card.odid)
        return dict(
            # original deck
            minInt=oconf['lapse']['minInt'],
            leechFails=oconf['lapse']['leechFails'],
            leechAction=oconf['lapse']['leechAction'],
            # overrides
            delays=conf['delays'],
            mult=conf['fmult'],
        )

    def _revConf(self, card):
        conf = self._cardConf(card)
        # normal deck
        if not card.odid:
            return conf['rev']
        # dynamic deck; override some attributes, use original deck for others
        oconf = self.col.decks.confForDid(card.odid)
        return dict(
            # original deck
            ease4=oconf['rev']['ease4'],
            fi=oconf['rev']['fi'],
            minSpace=oconf['rev']['minSpace'],
            fuzz=oconf['rev']['fuzz']
        )

    def _deckLimit(self):
        return ids2str(self.col.decks.active())

    # Daily cutoff
    ##########################################################################

    def _updateCutoff(self):
        # days since col created
        self.today = int((time.time() - self.col.crt) / 86400)
        # end of day cutoff
        self.dayCutoff = self.col.crt + (self.today+1)*86400
        # update all daily counts, but don't save decks to prevent needless
        # conflicts. we'll save on card answer instead
        def update(g):
            for t in "new", "rev", "lrn", "time":
                key = t+"Today"
                if g[key][0] != self.today:
                    g[key] = [self.today, 0]
        for deck in self.col.decks.all():
            update(deck)

    def _checkDay(self):
        # check if the day has rolled over
        if time.time() > self.dayCutoff:
            self.reset()

    # Deck finished state
    ##########################################################################

    def finishedMsg(self):
        return ("<b>"+_(
            "Congratulations! You have finished this deck for now.")+
            "</b><br><br>" + self._nextDueMsg())

    def _nextDueMsg(self):
        line = []
        if self.revDue():
            line.append(_("""\
Today's review limit has been reached, but there are still cards
waiting to be reviewed. For optimum memory, consider increasing
the daily limit in the options."""))
        if self.newDue():
            line.append(_("""\
There are more new cards available, but the daily limit has been
reached. You can increase the limit in the options, but please
bear in mind that the more new cards you introduce, the higher
your short-term review workload will become."""))
        return "<br>".join(line)

    def revDue(self):
        "True if there are any rev cards due."
        return self.col.db.scalar(
            ("select 1 from cards where did in %s and queue = 2 "
             "and due <= ? limit 1") % self._deckLimit(),
            self.today)

    def newDue(self):
        "True if there are any new cards due."
        return self.col.db.scalar(
            ("select 1 from cards where did in %s and queue = 0 "
             "limit 1") % self._deckLimit())

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
            conf = self._lapseConf(card)
            if conf['delays']:
                return conf['delays'][0]*60
            return self._nextLapseIvl(card, conf)*86400
        else:
            # review
            return self._nextRevIvl(card, ease)*86400

    # this isn't easily extracted from the learn code
    def _nextLrnIvl(self, card, ease):
        if card.queue == 0:
            card.left = self._startingLeft(card)
        conf = self._lrnConf(card)
        if ease == 1:
            # fail
            return self._delayForGrade(conf, len(conf['delays']))
        elif ease == 3:
            # early removal
            return self._graduatingIvl(card, conf, True, adj=False) * 86400
        else:
            left = card.left - 1
            if left <= 0:
                # graduate
                return self._graduatingIvl(card, conf, False, adj=False) * 86400
            else:
                return self._delayForGrade(conf, left)

    # Suspending
    ##########################################################################

    def suspendCards(self, ids):
        "Suspend cards."
        self.remFromDyn(ids)
        self.removeFailed(ids)
        self.col.db.execute(
            "update cards set queue=-1,mod=?,usn=? where id in "+
            ids2str(ids), intTime(), self.col.usn())

    def unsuspendCards(self, ids):
        "Unsuspend cards."
        self.col.db.execute(
            "update cards set queue=type,mod=?,usn=? "
            "where queue = -1 and id in "+ ids2str(ids),
            intTime(), self.col.usn())

    def buryNote(self, nid):
        "Bury all cards for note until next session."
        self.col.setDirty()
        cids = self.col.db.list("select id from cards where nid = ?", nid)
        self.remFromDyn(cids)
        self.removeFailed(cids)
        self.col.db.execute("update cards set queue = -2 where nid = ?", nid)

    # Resetting
    ##########################################################################

    def forgetCards(self, ids):
        "Put cards at the end of the new queue."
        self.col.db.execute(
            "update cards set type=0,queue=0,ivl=0 where id in "+ids2str(ids))
        pmax = self.col.db.scalar(
            "select max(due) from cards where type=0") or 0
        # takes care of mod + usn
        self.sortCards(ids, start=pmax+1)

    def reschedCards(self, ids, imin, imax):
        "Put cards in review queue with a new interval in days (min, max)."
        d = []
        t = self.today
        mod = intTime()
        for id in ids:
            r = random.randint(imin, imax)
            d.append(dict(id=id, due=r+t, ivl=max(1, r), mod=mod,
                          usn=self.col.usn(), fact=2500))
        self.col.db.executemany("""
update cards set type=2,queue=2,ivl=:ivl,due=:due,
usn=:usn, mod=:mod, factor=:fact where id=:id and odid=0""",
                                d)

    # Repositioning new cards
    ##########################################################################

    def sortCards(self, cids, start=1, step=1, shuffle=False, shift=False):
        scids = ids2str(cids)
        now = intTime()
        nids = self.col.db.list(
            ("select distinct nid from cards where type = 0 and id in %s "
             "order by nid") % scids)
        if not nids:
            # no new cards
            return
        # determine nid ordering
        due = {}
        if shuffle:
            random.shuffle(nids)
        for c, nid in enumerate(nids):
            due[nid] = start+c*step
        high = start+c*step
        # shift?
        if shift:
            low = self.col.db.scalar(
                "select min(due) from cards where due >= ? and type = 0 "
                "and id not in %s" % scids,
                start)
            if low is not None:
                shiftby = high - low + 1
                self.col.db.execute("""
update cards set mod=?, usn=?, due=due+? where id not in %s
and due >= ? and queue = 0""" % scids, now, self.col.usn(), shiftby, low)
        # reorder cards
        d = []
        for id, nid in self.col.db.execute(
            "select id, nid from cards where type = 0 and id in "+scids):
            d.append(dict(now=now, due=due[nid], usn=self.col.usn(), cid=id))
        self.col.db.executemany(
            "update cards set due=:due,mod=:now,usn=:usn where id = :cid""", d)

    def randomizeCards(self, did):
        cids = self.col.db.list("select id from cards where did = ?", did)
        self.sortCards(cids, shuffle=True)

    def orderCards(self, did):
        cids = self.col.db.list("select id from cards where did = ?", did)
        self.sortCards(cids)

    def resortConf(self, conf):
        for did in self.col.decks.didsForConf(conf):
            if conf['new']['order'] == 0:
                self.randomizeCards(did)
            else:
                self.orderCards(did)
