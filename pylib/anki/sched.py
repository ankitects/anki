# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import itertools
import random
import time
from heapq import *
from operator import itemgetter
from typing import Any, Callable, Dict, List, Optional, Set, Tuple, Union

import anki
from anki import hooks
from anki.cards import Card
from anki.consts import *
from anki.lang import _
from anki.rsbackend import FormatTimeSpanContext
from anki.utils import ids2str, intTime

# queue types: 0=new/cram, 1=lrn, 2=rev, 3=day lrn, -1=suspended, -2=buried
# revlog types: 0=lrn, 1=rev, 2=relrn, 3=cram
# positive revlog intervals are in days (rev), negative in seconds (lrn)


class Scheduler:
    name = "std"
    haveCustomStudy = True
    _spreadRev = True
    _burySiblingsOnAnswer = True

    def __init__(self, col: anki.storage._Collection) -> None:
        self.col = col
        self.queueLimit = 50
        self.reportLimit = 1000
        self.reps = 0
        self.lrnCount = 0
        self.revCount = 0
        self.newCount = 0
        self.today: Optional[int] = None
        self._haveQueues = False
        self._updateCutoff()

    def getCard(self) -> Optional[Card]:
        "Pop the next card from the queue. None if finished."
        self._checkDay()
        if not self._haveQueues:
            self.reset()
        card = self._getCard()
        if card:
            self.col.log(card)
            if not self._burySiblingsOnAnswer:
                self._burySiblings(card)
            self.reps += 1
            card.startTimer()
            return card
        return None

    def reset(self) -> None:
        self._updateCutoff()
        self._resetLrn()
        self._resetRev()
        self._resetNew()
        self._haveQueues = True

    def answerCard(self, card: Card, ease: int) -> None:
        self.col.log()
        assert 1 <= ease <= 4
        self.col.markReview(card)
        if self._burySiblingsOnAnswer:
            self._burySiblings(card)
        card.reps += 1
        # former is for logging new cards, latter also covers filt. decks
        card.wasNew = card.type == CARD_TYPE_NEW  # type: ignore
        wasNewQ = card.queue == QUEUE_TYPE_NEW
        if wasNewQ:
            # came from the new queue, move to learning
            card.queue = QUEUE_TYPE_LRN
            # if it was a new card, it's now a learning card
            if card.type == CARD_TYPE_NEW:
                card.type = CARD_TYPE_LRN
            # init reps to graduation
            card.left = self._startingLeft(card)
            # dynamic?
            if card.odid and card.type == CARD_TYPE_REV:
                if self._resched(card):
                    # reviews get their ivl boosted on first sight
                    card.ivl = self._dynIvlBoost(card)
                    card.odue = self.today + card.ivl
            self._updateStats(card, "new")
        if card.queue in (QUEUE_TYPE_LRN, QUEUE_TYPE_DAY_LEARN_RELEARN):
            self._answerLrnCard(card, ease)
            if not wasNewQ:
                self._updateStats(card, "lrn")
        elif card.queue == QUEUE_TYPE_REV:
            self._answerRevCard(card, ease)
            self._updateStats(card, "rev")
        else:
            raise Exception("Invalid queue")
        self._updateStats(card, "time", card.timeTaken())
        card.mod = intTime()
        card.usn = self.col.usn()
        card.flushSched()

    def counts(self, card: Optional[Card] = None) -> Tuple[int, int, int]:
        counts = [self.newCount, self.lrnCount, self.revCount]
        if card:
            idx = self.countIdx(card)
            if idx == 1:
                counts[1] += card.left // 1000
            else:
                counts[idx] += 1

        new, lrn, rev = counts
        return (new, lrn, rev)

    def dueForecast(self, days: int = 7) -> List[Any]:
        "Return counts over next DAYS. Includes today."
        daysd = dict(
            self.col.db.all(
                f"""
select due, count() from cards
where did in %s and queue = {QUEUE_TYPE_REV}
and due between ? and ?
group by due
order by due"""
                % self._deckLimit(),
                self.today,
                self.today + days - 1,
            )
        )
        for d in range(days):
            d = self.today + d
            if d not in daysd:
                daysd[d] = 0
        # return in sorted order
        ret = [x[1] for x in sorted(daysd.items())]
        return ret

    def countIdx(self, card: Card) -> int:
        if card.queue == QUEUE_TYPE_DAY_LEARN_RELEARN:
            return 1
        return card.queue

    def answerButtons(self, card: Card) -> int:
        if card.odue:
            # normal review in dyn deck?
            if card.odid and card.queue == QUEUE_TYPE_REV:
                return 4
            conf = self._lrnConf(card)
            if card.type in (CARD_TYPE_NEW, CARD_TYPE_LRN) or len(conf["delays"]) > 1:
                return 3
            return 2
        elif card.queue == QUEUE_TYPE_REV:
            return 4
        else:
            return 3

    def unburyCards(self) -> None:
        "Unbury cards."
        self.col.conf["lastUnburied"] = self.today
        self.col.log(
            self.col.db.list(
                f"select id from cards where queue = {QUEUE_TYPE_SIBLING_BURIED}"
            )
        )
        self.col.db.execute(
            f"update cards set queue=type where queue = {QUEUE_TYPE_SIBLING_BURIED}"
        )

    def unburyCardsForDeck(self) -> None:
        sids = ids2str(self.col.decks.active())
        self.col.log(
            self.col.db.list(
                f"select id from cards where queue = {QUEUE_TYPE_SIBLING_BURIED} and did in %s"
                % sids
            )
        )
        self.col.db.execute(
            f"update cards set mod=?,usn=?,queue=type where queue = {QUEUE_TYPE_SIBLING_BURIED} and did in %s"
            % sids,
            intTime(),
            self.col.usn(),
        )

    # Rev/lrn/time daily stats
    ##########################################################################

    def _updateStats(self, card: Card, type: str, cnt: int = 1) -> None:
        key = type + "Today"
        for g in [self.col.decks.get(card.did)] + self.col.decks.parents(card.did):
            # add
            g[key][1] += cnt
            self.col.decks.save(g)

    def extendLimits(self, new: int, rev: int) -> None:
        cur = self.col.decks.current()
        parents = self.col.decks.parents(cur["id"])
        children = [
            self.col.decks.get(did)
            for (name, did) in self.col.decks.children(cur["id"])
        ]
        for g in [cur] + parents + children:
            # add
            g["newToday"][1] -= new
            g["revToday"][1] -= rev
            self.col.decks.save(g)

    def _walkingCount(
        self,
        limFn: Optional[Callable[[Any], Optional[int]]] = None,
        cntFn: Optional[Callable[[int, int], int]] = None,
    ) -> int:
        tot = 0
        pcounts: Dict[int, int] = {}
        # for each of the active decks
        nameMap = self.col.decks.nameMap()
        for did in self.col.decks.active():
            # early alphas were setting the active ids as a str
            did = int(did)
            # get the individual deck's limit
            lim = limFn(self.col.decks.get(did))
            if not lim:
                continue
            # check the parents
            parents = self.col.decks.parents(did, nameMap)
            for p in parents:
                # add if missing
                if p["id"] not in pcounts:
                    pcounts[p["id"]] = limFn(p)
                # take minimum of child and parent
                lim = min(pcounts[p["id"]], lim)
            # see how many cards we actually have
            cnt = cntFn(did, lim)
            # if non-zero, decrement from parent counts
            for p in parents:
                pcounts[p["id"]] -= cnt
            # we may also be a parent
            pcounts[did] = lim - cnt
            # and add to running total
            tot += cnt
        return tot

    # Deck list
    ##########################################################################

    def deckDueList(self) -> List[List[Any]]:
        "Returns [deckname, did, rev, lrn, new]"
        self._checkDay()
        self.col.decks.checkIntegrity()
        decks = self.col.decks.all()
        decks.sort(key=itemgetter("name"))
        lims: Dict[str, List[int]] = {}
        data = []

        def parent(name):
            parts = name.split("::")
            if len(parts) < 2:
                return None
            parts = parts[:-1]
            return "::".join(parts)

        for deck in decks:
            p = parent(deck["name"])
            # new
            nlim = self._deckNewLimitSingle(deck)
            if p:
                nlim = min(nlim, lims[p][0])
            new = self._newForDeck(deck["id"], nlim)
            # learning
            lrn = self._lrnForDeck(deck["id"])
            # reviews
            rlim = self._deckRevLimitSingle(deck)
            if p:
                rlim = min(rlim, lims[p][1])
            rev = self._revForDeck(deck["id"], rlim)
            # save to list
            data.append([deck["name"], deck["id"], rev, lrn, new])
            # add deck as a parent
            lims[deck["name"]] = [nlim, rlim]
        return data

    def deckDueTree(self) -> Any:
        return self._groupChildren(self.deckDueList())

    def _groupChildren(self, grps: List[List[Any]]) -> Any:
        # first, split the group names into components
        for g in grps:
            g[0] = g[0].split("::")
        # and sort based on those components
        grps.sort(key=itemgetter(0))
        # then run main function
        return self._groupChildrenMain(grps)

    def _groupChildrenMain(self, grps: List[List[Any]]) -> Any:
        tree = []
        # group and recurse
        def key(grp):
            return grp[0][0]

        for (head, tail) in itertools.groupby(grps, key=key):
            tail = list(tail)  # type: ignore
            did = None
            rev = 0
            new = 0
            lrn = 0
            children = []
            for c in tail:
                if len(c[0]) == 1:
                    # current node
                    did = c[1]
                    rev += c[2]
                    lrn += c[3]
                    new += c[4]
                else:
                    # set new string to tail
                    c[0] = c[0][1:]
                    children.append(c)
            children = self._groupChildrenMain(children)
            # tally up children counts
            for ch in children:
                rev += ch[2]
                lrn += ch[3]
                new += ch[4]
            # limit the counts to the deck's limits
            conf = self.col.decks.confForDid(did)
            deck = self.col.decks.get(did)
            if not conf["dyn"]:
                rev = max(0, min(rev, conf["rev"]["perDay"] - deck["revToday"][1]))
                new = max(0, min(new, self._deckNewLimitSingle(deck)))
            tree.append((head, did, rev, lrn, new, children))
        return tuple(tree)

    # Getting the next card
    ##########################################################################

    def _getCard(self) -> Optional[Card]:
        "Return the next due card id, or None."
        # learning card due?
        c = self._getLrnCard()
        if c:
            return c
        # new first, or time for one?
        if self._timeForNewCard():
            c = self._getNewCard()
            if c:
                return c
        # card due for review?
        c = self._getRevCard()
        if c:
            return c
        # day learning card due?
        c = self._getLrnDayCard()
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

    def _resetNewCount(self) -> None:
        cntFn = lambda did, lim: self.col.db.scalar(
            f"""
select count() from (select 1 from cards where
did = ? and queue = {QUEUE_TYPE_NEW} limit ?)""",
            did,
            lim,
        )
        self.newCount = self._walkingCount(self._deckNewLimitSingle, cntFn)

    def _resetNew(self) -> None:
        self._resetNewCount()
        self._newDids = self.col.decks.active()[:]
        self._newQueue: List[Any] = []
        self._updateNewCardRatio()

    def _fillNew(self) -> Optional[bool]:
        if self._newQueue:
            return True
        if not self.newCount:
            return False
        while self._newDids:
            did = self._newDids[0]
            lim = min(self.queueLimit, self._deckNewLimit(did))
            if lim:
                # fill the queue with the current did
                self._newQueue = self.col.db.list(
                    f"""
                select id from cards where did = ? and queue = {QUEUE_TYPE_NEW} order by due,ord limit ?""",
                    did,
                    lim,
                )
                if self._newQueue:
                    self._newQueue.reverse()
                    return True
            # nothing left in the deck; move to next
            self._newDids.pop(0)
        if self.newCount:
            # if we didn't get a card but the count is non-zero,
            # we need to check again for any cards that were
            # removed from the queue but not buried
            self._resetNew()
            return self._fillNew()
        return None

    def _getNewCard(self) -> Optional[Card]:
        if self._fillNew():
            self.newCount -= 1
            return self.col.getCard(self._newQueue.pop())
        return None

    def _updateNewCardRatio(self) -> None:
        if self.col.conf["newSpread"] == NEW_CARDS_DISTRIBUTE:
            if self.newCount:
                self.newCardModulus = (self.newCount + self.revCount) // self.newCount
                # if there are cards to review, ensure modulo >= 2
                if self.revCount:
                    self.newCardModulus = max(2, self.newCardModulus)
                return
        self.newCardModulus = 0

    def _timeForNewCard(self) -> Optional[bool]:
        "True if it's time to display a new card when distributing."
        if not self.newCount:
            return False
        if self.col.conf["newSpread"] == NEW_CARDS_LAST:
            return False
        elif self.col.conf["newSpread"] == NEW_CARDS_FIRST:
            return True
        elif self.newCardModulus:
            return self.reps != 0 and self.reps % self.newCardModulus == 0
        return None

    def _deckNewLimit(
        self, did: int, fn: Optional[Callable[[Dict[str, Any]], int]] = None
    ) -> int:
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

    def _newForDeck(self, did: int, lim: int) -> int:
        "New count for a single deck."
        if not lim:
            return 0
        lim = min(lim, self.reportLimit)
        return self.col.db.scalar(
            f"""
select count() from
(select 1 from cards where did = ? and queue = {QUEUE_TYPE_NEW} limit ?)""",
            did,
            lim,
        )

    def _deckNewLimitSingle(self, g: Dict[str, Any]) -> int:
        "Limit for deck without parent limits."
        if g["dyn"]:
            return self.reportLimit
        c = self.col.decks.confForDid(g["id"])
        return max(0, c["new"]["perDay"] - g["newToday"][1])

    def totalNewForCurrentDeck(self) -> int:
        return self.col.db.scalar(
            f"""
select count() from cards where id in (
select id from cards where did in %s and queue = {QUEUE_TYPE_NEW} limit ?)"""
            % ids2str(self.col.decks.active()),
            self.reportLimit,
        )

    # Learning queues
    ##########################################################################

    def _resetLrnCount(self) -> None:
        # sub-day
        self.lrnCount = (
            self.col.db.scalar(
                f"""
select sum(left/1000) from (select left from cards where
did in %s and queue = {QUEUE_TYPE_LRN} and due < ? limit %d)"""
                % (self._deckLimit(), self.reportLimit),
                self.dayCutoff,
            )
            or 0
        )
        # day
        self.lrnCount += self.col.db.scalar(
            f"""
select count() from cards where did in %s and queue = {QUEUE_TYPE_DAY_LEARN_RELEARN}
and due <= ? limit %d"""
            % (self._deckLimit(), self.reportLimit),
            self.today,
        )

    def _resetLrn(self) -> None:
        self._resetLrnCount()
        self._lrnQueue: List[Any] = []
        self._lrnDayQueue: List[Any] = []
        self._lrnDids = self.col.decks.active()[:]

    # sub-day learning
    def _fillLrn(self) -> Union[bool, List[Any]]:
        if not self.lrnCount:
            return False
        if self._lrnQueue:
            return True
        self._lrnQueue = self.col.db.all(
            f"""
select due, id from cards where
did in %s and queue = {QUEUE_TYPE_LRN} and due < :lim
limit %d"""
            % (self._deckLimit(), self.reportLimit),
            lim=self.dayCutoff,
        )
        # as it arrives sorted by did first, we need to sort it
        self._lrnQueue.sort()
        return self._lrnQueue

    def _getLrnCard(self, collapse: bool = False) -> Optional[Card]:
        if self._fillLrn():
            cutoff = time.time()
            if collapse:
                cutoff += self.col.conf["collapseTime"]
            if self._lrnQueue[0][0] < cutoff:
                id = heappop(self._lrnQueue)[1]
                card = self.col.getCard(id)
                self.lrnCount -= card.left // 1000
                return card
        return None

    # daily learning
    def _fillLrnDay(self) -> Optional[bool]:
        if not self.lrnCount:
            return False
        if self._lrnDayQueue:
            return True
        while self._lrnDids:
            did = self._lrnDids[0]
            # fill the queue with the current did
            self._lrnDayQueue = self.col.db.list(
                f"""
select id from cards where
did = ? and queue = {QUEUE_TYPE_DAY_LEARN_RELEARN} and due <= ? limit ?""",
                did,
                self.today,
                self.queueLimit,
            )
            if self._lrnDayQueue:
                # order
                r = random.Random()
                r.seed(self.today)
                r.shuffle(self._lrnDayQueue)
                # is the current did empty?
                if len(self._lrnDayQueue) < self.queueLimit:
                    self._lrnDids.pop(0)
                return True
            # nothing left in the deck; move to next
            self._lrnDids.pop(0)
        return None

    def _getLrnDayCard(self) -> Optional[Card]:
        if self._fillLrnDay():
            self.lrnCount -= 1
            return self.col.getCard(self._lrnDayQueue.pop())
        return None

    def _answerLrnCard(self, card: Card, ease: int) -> None:
        # ease 1=no, 2=yes, 3=remove
        conf = self._lrnConf(card)
        if card.odid and not card.wasNew:  # type: ignore
            type = REVLOG_CRAM
        elif card.type == CARD_TYPE_REV:
            type = REVLOG_RELRN
        else:
            type = REVLOG_LRN
        leaving = False
        # lrnCount was decremented once when card was fetched
        lastLeft = card.left
        # immediate graduate?
        if ease == BUTTON_THREE:
            self._rescheduleAsRev(card, conf, True)
            leaving = True
        # graduation time?
        elif ease == BUTTON_TWO and (card.left % 1000) - 1 <= 0:
            self._rescheduleAsRev(card, conf, False)
            leaving = True
        else:
            # one step towards graduation
            if ease == BUTTON_TWO:
                # decrement real left count and recalculate left today
                left = (card.left % 1000) - 1
                card.left = self._leftToday(conf["delays"], left) * 1000 + left
            # failed
            else:
                card.left = self._startingLeft(card)
                resched = self._resched(card)
                if "mult" in conf and resched:
                    # review that's lapsed
                    card.ivl = max(1, conf["minInt"], card.ivl * conf["mult"])
                else:
                    # new card; no ivl adjustment
                    pass
                if resched and card.odid:
                    card.odue = self.today + 1
            delay = self._delayForGrade(conf, card.left)
            if card.due < time.time():
                # not collapsed; add some randomness
                delay *= random.uniform(1, 1.25)
            card.due = int(time.time() + delay)
            # due today?
            if card.due < self.dayCutoff:
                self.lrnCount += card.left // 1000
                # if the queue is not empty and there's nothing else to do, make
                # sure we don't put it at the head of the queue and end up showing
                # it twice in a row
                card.queue = QUEUE_TYPE_LRN
                if self._lrnQueue and not self.revCount and not self.newCount:
                    smallestDue = self._lrnQueue[0][0]
                    card.due = max(card.due, smallestDue + 1)
                heappush(self._lrnQueue, (card.due, card.id))
            else:
                # the card is due in one or more days, so we need to use the
                # day learn queue
                ahead = ((card.due - self.dayCutoff) // 86400) + 1
                card.due = self.today + ahead
                card.queue = QUEUE_TYPE_DAY_LEARN_RELEARN
        self._logLrn(card, ease, conf, leaving, type, lastLeft)

    def _delayForGrade(self, conf: Dict[str, Any], left: int) -> float:
        left = left % 1000
        try:
            delay = conf["delays"][-left]
        except IndexError:
            if conf["delays"]:
                delay = conf["delays"][0]
            else:
                # user deleted final step; use dummy value
                delay = 1
        return delay * 60

    def _lrnConf(self, card: Card) -> Dict[str, Any]:
        if card.type == CARD_TYPE_REV:
            return self._lapseConf(card)
        else:
            return self._newConf(card)

    def _rescheduleAsRev(self, card: Card, conf: Dict[str, Any], early: bool) -> None:
        lapse = card.type == CARD_TYPE_REV
        if lapse:
            if self._resched(card):
                card.due = max(self.today + 1, card.odue)
            else:
                card.due = card.odue
            card.odue = 0
        else:
            self._rescheduleNew(card, conf, early)
        card.queue = QUEUE_TYPE_REV
        card.type = CARD_TYPE_REV
        # if we were dynamic, graduating means moving back to the old deck
        resched = self._resched(card)
        if card.odid:
            card.did = card.odid
            card.odue = 0
            card.odid = 0
            # if rescheduling is off, it needs to be set back to a new card
            if not resched and not lapse:
                card.queue = card.type = CARD_TYPE_NEW
                card.due = self.col.nextID("pos")

    def _startingLeft(self, card: Card) -> int:
        if card.type == CARD_TYPE_REV:
            conf = self._lapseConf(card)
        else:
            conf = self._lrnConf(card)
        tot = len(conf["delays"])
        tod = self._leftToday(conf["delays"], tot)
        return tot + tod * 1000

    def _leftToday(
        self, delays: List[int], left: int, now: Optional[int] = None
    ) -> int:
        "The number of steps that can be completed by the day cutoff."
        if not now:
            now = intTime()
        delays = delays[-left:]
        ok = 0
        for i in range(len(delays)):
            now += delays[i] * 60
            if now > self.dayCutoff:
                break
            ok = i
        return ok + 1

    def _graduatingIvl(
        self, card: Card, conf: Dict[str, Any], early: bool, adj: bool = True
    ) -> int:
        if card.type == CARD_TYPE_REV:
            # lapsed card being relearnt
            if card.odid:
                if conf["resched"]:
                    return self._dynIvlBoost(card)
            return card.ivl
        if not early:
            # graduate
            ideal = conf["ints"][0]
        else:
            # early remove
            ideal = conf["ints"][1]
        if adj:
            return self._adjRevIvl(card, ideal)
        else:
            return ideal

    def _rescheduleNew(self, card: Card, conf: Dict[str, Any], early: bool) -> None:
        "Reschedule a new card that's graduated for the first time."
        card.ivl = self._graduatingIvl(card, conf, early)
        card.due = self.today + card.ivl
        card.factor = conf["initialFactor"]

    def _logLrn(
        self,
        card: Card,
        ease: int,
        conf: Dict[str, Any],
        leaving: bool,
        type: int,
        lastLeft: int,
    ) -> None:
        lastIvl = -(self._delayForGrade(conf, lastLeft))
        ivl = card.ivl if leaving else -(self._delayForGrade(conf, card.left))

        def log():
            self.col.db.execute(
                "insert into revlog values (?,?,?,?,?,?,?,?,?)",
                int(time.time() * 1000),
                card.id,
                self.col.usn(),
                ease,
                ivl,
                lastIvl,
                card.factor,
                card.timeTaken(),
                type,
            )

        try:
            log()
        except:
            # duplicate pk; retry in 10ms
            time.sleep(0.01)
            log()

    def removeLrn(self, ids: Optional[List[int]] = None) -> None:
        "Remove cards from the learning queues."
        if ids:
            extra = " and id in " + ids2str(ids)
        else:
            # benchmarks indicate it's about 10x faster to search all decks
            # with the index than scan the table
            extra = " and did in " + ids2str(self.col.decks.allIds())
        # review cards in relearning
        self.col.db.execute(
            f"""
update cards set
due = odue, queue = {QUEUE_TYPE_REV}, mod = %d, usn = %d, odue = 0
where queue in ({QUEUE_TYPE_LRN},{QUEUE_TYPE_DAY_LEARN_RELEARN}) and type = {CARD_TYPE_REV}
%s
"""
            % (intTime(), self.col.usn(), extra)
        )
        # new cards in learning
        self.forgetCards(
            self.col.db.list(
                f"select id from cards where queue in ({QUEUE_TYPE_LRN},{QUEUE_TYPE_DAY_LEARN_RELEARN}) %s"
                % extra
            )
        )

    def _lrnForDeck(self, did: int) -> int:
        cnt = (
            self.col.db.scalar(
                f"""
select sum(left/1000) from
(select left from cards where did = ? and queue = {QUEUE_TYPE_LRN} and due < ? limit ?)""",
                did,
                intTime() + self.col.conf["collapseTime"],
                self.reportLimit,
            )
            or 0
        )
        return cnt + self.col.db.scalar(
            f"""
select count() from
(select 1 from cards where did = ? and queue = {QUEUE_TYPE_DAY_LEARN_RELEARN}
and due <= ? limit ?)""",
            did,
            self.today,
            self.reportLimit,
        )

    # Reviews
    ##########################################################################

    def _deckRevLimit(self, did: int) -> int:
        return self._deckNewLimit(did, self._deckRevLimitSingle)

    def _deckRevLimitSingle(self, d: Dict[str, Any]) -> int:
        if d["dyn"]:
            return self.reportLimit
        c = self.col.decks.confForDid(d["id"])
        return max(0, c["rev"]["perDay"] - d["revToday"][1])

    def _revForDeck(self, did: int, lim: int) -> int:
        lim = min(lim, self.reportLimit)
        return self.col.db.scalar(
            f"""
select count() from
(select 1 from cards where did = ? and queue = {QUEUE_TYPE_REV}
and due <= ? limit ?)""",
            did,
            self.today,
            lim,
        )

    def _resetRevCount(self) -> None:
        def cntFn(did, lim):
            return self.col.db.scalar(
                f"""
select count() from (select id from cards where
did = ? and queue = {QUEUE_TYPE_REV} and due <= ? limit %d)"""
                % lim,
                did,
                self.today,
            )

        self.revCount = self._walkingCount(self._deckRevLimitSingle, cntFn)

    def _resetRev(self) -> None:
        self._resetRevCount()
        self._revQueue: List[Any] = []
        self._revDids = self.col.decks.active()[:]

    def _fillRev(self) -> Optional[bool]:
        if self._revQueue:
            return True
        if not self.revCount:
            return False
        while self._revDids:
            did = self._revDids[0]
            lim = min(self.queueLimit, self._deckRevLimit(did))
            if lim:
                # fill the queue with the current did
                self._revQueue = self.col.db.list(
                    f"""
select id from cards where
did = ? and queue = {QUEUE_TYPE_REV} and due <= ? limit ?""",
                    did,
                    self.today,
                    lim,
                )
                if self._revQueue:
                    # ordering
                    if self.col.decks.get(did)["dyn"]:
                        # dynamic decks need due order preserved
                        self._revQueue.reverse()
                    else:
                        # random order for regular reviews
                        r = random.Random()
                        r.seed(self.today)
                        r.shuffle(self._revQueue)
                    # is the current did empty?
                    if len(self._revQueue) < lim:
                        self._revDids.pop(0)
                    return True
            # nothing left in the deck; move to next
            self._revDids.pop(0)
        if self.revCount:
            # if we didn't get a card but the count is non-zero,
            # we need to check again for any cards that were
            # removed from the queue but not buried
            self._resetRev()
            return self._fillRev()

        return None

    def _getRevCard(self) -> Optional[Card]:
        if self._fillRev():
            self.revCount -= 1
            return self.col.getCard(self._revQueue.pop())
        return None

    def totalRevForCurrentDeck(self) -> int:
        return self.col.db.scalar(
            f"""
select count() from cards where id in (
select id from cards where did in %s and queue = {QUEUE_TYPE_REV} and due <= ? limit ?)"""
            % ids2str(self.col.decks.active()),
            self.today,
            self.reportLimit,
        )

    # Answering a review card
    ##########################################################################

    def _answerRevCard(self, card: Card, ease: int) -> None:
        delay: float = 0
        if ease == BUTTON_ONE:
            delay = self._rescheduleLapse(card)
        else:
            self._rescheduleRev(card, ease)
        self._logRev(card, ease, delay)

    def _rescheduleLapse(self, card: Card) -> float:
        conf = self._lapseConf(card)
        card.lastIvl = card.ivl
        if self._resched(card):
            card.lapses += 1
            card.ivl = self._nextLapseIvl(card, conf)
            card.factor = max(1300, card.factor - 200)
            card.due = self.today + card.ivl
            # if it's a filtered deck, update odue as well
            if card.odid:
                card.odue = card.due
        # if suspended as a leech, nothing to do
        delay: float = 0
        if self._checkLeech(card, conf) and card.queue == QUEUE_TYPE_SUSPENDED:
            return delay
        # if no relearning steps, nothing to do
        if not conf["delays"]:
            return delay
        # record rev due date for later
        if not card.odue:
            card.odue = card.due
        delay = self._delayForGrade(conf, 0)
        card.due = int(delay + time.time())
        card.left = self._startingLeft(card)
        # queue 1
        if card.due < self.dayCutoff:
            self.lrnCount += card.left // 1000
            card.queue = QUEUE_TYPE_LRN
            heappush(self._lrnQueue, (card.due, card.id))
        else:
            # day learn queue
            ahead = ((card.due - self.dayCutoff) // 86400) + 1
            card.due = self.today + ahead
            card.queue = QUEUE_TYPE_DAY_LEARN_RELEARN
        return delay

    def _nextLapseIvl(self, card: Card, conf: Dict[str, Any]) -> int:
        return max(conf["minInt"], int(card.ivl * conf["mult"]))

    def _rescheduleRev(self, card: Card, ease: int) -> None:
        # update interval
        card.lastIvl = card.ivl
        if self._resched(card):
            self._updateRevIvl(card, ease)
            # then the rest
            card.factor = max(1300, card.factor + [-150, 0, 150][ease - 2])
            card.due = self.today + card.ivl
        else:
            card.due = card.odue
        if card.odid:
            card.did = card.odid
            card.odid = 0
            card.odue = 0

    def _logRev(self, card: Card, ease: int, delay: float) -> None:
        def log():
            self.col.db.execute(
                "insert into revlog values (?,?,?,?,?,?,?,?,?)",
                int(time.time() * 1000),
                card.id,
                self.col.usn(),
                ease,
                -delay or card.ivl,
                card.lastIvl,
                card.factor,
                card.timeTaken(),
                REVLOG_REV,
            )

        try:
            log()
        except:
            # duplicate pk; retry in 10ms
            time.sleep(0.01)
            log()

    # Interval management
    ##########################################################################

    def _nextRevIvl(self, card: Card, ease: int) -> int:
        "Ideal next interval for CARD, given EASE."
        delay = self._daysLate(card)
        conf = self._revConf(card)
        fct = card.factor / 1000
        ivl2 = self._constrainedIvl((card.ivl + delay // 4) * 1.2, conf, card.ivl)
        ivl3 = self._constrainedIvl((card.ivl + delay // 2) * fct, conf, ivl2)
        ivl4 = self._constrainedIvl(
            (card.ivl + delay) * fct * conf["ease4"], conf, ivl3
        )
        if ease == BUTTON_TWO:
            interval = ivl2
        elif ease == BUTTON_THREE:
            interval = ivl3
        elif ease == BUTTON_FOUR:
            interval = ivl4
        # interval capped?
        return min(interval, conf["maxIvl"])

    def _fuzzedIvl(self, ivl: int) -> int:
        min, max = self._fuzzIvlRange(ivl)
        return random.randint(min, max)

    def _fuzzIvlRange(self, ivl: int) -> List[int]:
        if ivl < 2:
            return [1, 1]
        elif ivl == 2:
            return [2, 3]
        elif ivl < 7:
            fuzz = int(ivl * 0.25)
        elif ivl < 30:
            fuzz = max(2, int(ivl * 0.15))
        else:
            fuzz = max(4, int(ivl * 0.05))
        # fuzz at least a day
        fuzz = max(fuzz, 1)
        return [ivl - fuzz, ivl + fuzz]

    def _constrainedIvl(self, ivl: float, conf: Dict[str, Any], prev: int) -> int:
        "Integer interval after interval factor and prev+1 constraints applied."
        new = ivl * conf.get("ivlFct", 1)
        return int(max(new, prev + 1))

    def _daysLate(self, card: Card) -> int:
        "Number of days later than scheduled."
        due = card.odue if card.odid else card.due
        return max(0, self.today - due)

    def _updateRevIvl(self, card: Card, ease: int) -> None:
        idealIvl = self._nextRevIvl(card, ease)
        card.ivl = min(
            max(self._adjRevIvl(card, idealIvl), card.ivl + 1),
            self._revConf(card)["maxIvl"],
        )

    def _adjRevIvl(self, card: Card, idealIvl: int) -> int:
        if self._spreadRev:
            idealIvl = self._fuzzedIvl(idealIvl)
        return idealIvl

    # Dynamic deck handling
    ##########################################################################

    def rebuildDyn(self, did: Optional[int] = None) -> Optional[List[int]]:
        "Rebuild a dynamic deck."
        did = did or self.col.decks.selected()
        deck = self.col.decks.get(did)
        assert deck["dyn"]
        # move any existing cards back first, then fill
        self.emptyDyn(did)
        ids = self._fillDyn(deck)
        if not ids:
            return None
        # and change to our new deck
        self.col.decks.select(did)
        return ids

    def _fillDyn(self, deck: Dict[str, Any]) -> List[int]:
        search, limit, order = deck["terms"][0]
        orderlimit = self._dynOrder(order, limit)
        if search.strip():
            search = "(%s)" % search
        search = "%s -is:suspended -is:buried -deck:filtered -is:learn" % search
        try:
            ids = self.col.findCards(search, order=orderlimit)
        except:
            ids = []
            return ids
        # move the cards over
        self.col.log(deck["id"], ids)
        self._moveToDyn(deck["id"], ids)
        return ids

    def emptyDyn(self, did: Optional[int], lim: Optional[str] = None) -> None:
        if not lim:
            lim = "did = %s" % did
        self.col.log(self.col.db.list("select id from cards where %s" % lim))
        # move out of cram queue
        self.col.db.execute(
            f"""
update cards set did = odid, queue = (case when type = {CARD_TYPE_LRN} then {QUEUE_TYPE_NEW}
else type end), type = (case when type = {CARD_TYPE_LRN} then {CARD_TYPE_NEW} else type end),
due = odue, odue = 0, odid = 0, usn = ? where %s"""
            % lim,
            self.col.usn(),
        )

    def remFromDyn(self, cids: List[int]) -> None:
        self.emptyDyn(None, "id in %s and odid" % ids2str(cids))

    def _dynOrder(self, o: int, l: int) -> str:
        if o == DYN_OLDEST:
            t = "(select max(id) from revlog where cid=c.id)"
        elif o == DYN_RANDOM:
            t = "random()"
        elif o == DYN_SMALLINT:
            t = "ivl"
        elif o == DYN_BIGINT:
            t = "ivl desc"
        elif o == DYN_LAPSES:
            t = "lapses desc"
        elif o == DYN_ADDED:
            t = "n.id"
        elif o == DYN_REVADDED:
            t = "n.id desc"
        elif o == DYN_DUE:
            t = "c.due"
        elif o == DYN_DUEPRIORITY:
            t = (
                f"(case when queue={QUEUE_TYPE_REV} and due <= %d then (ivl / cast(%d-due+0.001 as real)) else 100000+due end)"
                % (self.today, self.today)
            )
        else:
            # if we don't understand the term, default to due order
            t = "c.due"
        return t + " limit %d" % l

    def _moveToDyn(self, did: int, ids: List[int]) -> None:
        deck = self.col.decks.get(did)
        data = []
        t = intTime()
        u = self.col.usn()
        for c, id in enumerate(ids):
            # start at -100000 so that reviews are all due
            data.append((did, -100000 + c, u, id))
        # due reviews stay in the review queue. careful: can't use
        # "odid or did", as sqlite converts to boolean
        queue = f"""
(case when type={CARD_TYPE_REV} and (case when odue then odue <= %d else due <= %d end)
 then {QUEUE_TYPE_REV} else {QUEUE_TYPE_NEW} end)"""
        queue %= (self.today, self.today)
        self.col.db.executemany(
            """
update cards set
odid = (case when odid then odid else did end),
odue = (case when odue then odue else due end),
did = ?, queue = %s, due = ?, usn = ? where id = ?"""
            % queue,
            data,
        )

    def _dynIvlBoost(self, card: Card) -> int:
        assert card.odid and card.type == CARD_TYPE_REV
        assert card.factor
        elapsed = card.ivl - (card.odue - self.today)
        factor = ((card.factor / 1000) + 1.2) / 2
        ivl = int(max(card.ivl, elapsed * factor, 1))
        conf = self._revConf(card)
        return min(conf["maxIvl"], ivl)

    # Leeches
    ##########################################################################

    def _checkLeech(self, card: Card, conf: Dict[str, Any]) -> bool:
        "Leech handler. True if card was a leech."
        lf = conf["leechFails"]
        if not lf:
            return False
        # if over threshold or every half threshold reps after that
        if card.lapses >= lf and (card.lapses - lf) % (max(lf // 2, 1)) == 0:
            # add a leech tag
            f = card.note()
            f.addTag("leech")
            f.flush()
            # handle
            a = conf["leechAction"]
            if a == LEECH_SUSPEND:
                # if it has an old due, remove it from cram/relearning
                if card.odue:
                    card.due = card.odue
                if card.odid:
                    card.did = card.odid
                card.odue = card.odid = 0
                card.queue = QUEUE_TYPE_SUSPENDED
            # notify UI
            hooks.card_did_leech(card)
            return True
        else:
            return False

    # Tools
    ##########################################################################

    def _cardConf(self, card: Card) -> Dict[str, Any]:
        return self.col.decks.confForDid(card.did)

    def _newConf(self, card: Card) -> Dict[str, Any]:
        conf = self._cardConf(card)
        # normal deck
        if not card.odid:
            return conf["new"]
        # dynamic deck; override some attributes, use original deck for others
        oconf = self.col.decks.confForDid(card.odid)
        delays = conf["delays"] or oconf["new"]["delays"]
        return dict(
            # original deck
            ints=oconf["new"]["ints"],
            initialFactor=oconf["new"]["initialFactor"],
            bury=oconf["new"].get("bury", True),
            # overrides
            delays=delays,
            separate=conf["separate"],
            order=NEW_CARDS_DUE,
            perDay=self.reportLimit,
        )

    def _lapseConf(self, card: Card) -> Dict[str, Any]:
        conf = self._cardConf(card)
        # normal deck
        if not card.odid:
            return conf["lapse"]
        # dynamic deck; override some attributes, use original deck for others
        oconf = self.col.decks.confForDid(card.odid)
        delays = conf["delays"] or oconf["lapse"]["delays"]
        return dict(
            # original deck
            minInt=oconf["lapse"]["minInt"],
            leechFails=oconf["lapse"]["leechFails"],
            leechAction=oconf["lapse"]["leechAction"],
            mult=oconf["lapse"]["mult"],
            # overrides
            delays=delays,
            resched=conf["resched"],
        )

    def _revConf(self, card: Card) -> Dict[str, Any]:
        conf = self._cardConf(card)
        # normal deck
        if not card.odid:
            return conf["rev"]
        # dynamic deck
        return self.col.decks.confForDid(card.odid)["rev"]

    def _deckLimit(self) -> str:
        return ids2str(self.col.decks.active())

    def _resched(self, card: Card) -> bool:
        conf = self._cardConf(card)
        if not conf["dyn"]:
            return True
        return conf["resched"]

    # Daily cutoff
    ##########################################################################

    def _updateCutoff(self) -> None:
        oldToday = self.today
        # days since col created
        self.today = int((time.time() - self.col.crt) // 86400)
        # end of day cutoff
        self.dayCutoff = self.col.crt + (self.today + 1) * 86400
        if oldToday != self.today:
            self.col.log(self.today, self.dayCutoff)
        # update all daily counts, but don't save decks to prevent needless
        # conflicts. we'll save on card answer instead
        def update(g):
            for t in "new", "rev", "lrn", "time":
                key = t + "Today"
                if g[key][0] != self.today:
                    g[key] = [self.today, 0]

        for deck in self.col.decks.all():
            update(deck)
        # unbury if the day has rolled over
        unburied = self.col.conf.get("lastUnburied", 0)
        if unburied < self.today:
            self.unburyCards()

    def _checkDay(self) -> None:
        # check if the day has rolled over
        if time.time() > self.dayCutoff:
            self.reset()

    # Deck finished state
    ##########################################################################

    def finishedMsg(self) -> str:
        return (
            "<b>"
            + _("Congratulations! You have finished this deck for now.")
            + "</b><br><br>"
            + self._nextDueMsg()
        )

    def _nextDueMsg(self) -> str:
        line = []
        # the new line replacements are so we don't break translations
        # in a point release
        if self.revDue():
            line.append(
                _(
                    """\
Today's review limit has been reached, but there are still cards
waiting to be reviewed. For optimum memory, consider increasing
the daily limit in the options."""
                ).replace("\n", " ")
            )
        if self.newDue():
            line.append(
                _(
                    """\
There are more new cards available, but the daily limit has been
reached. You can increase the limit in the options, but please
bear in mind that the more new cards you introduce, the higher
your short-term review workload will become."""
                ).replace("\n", " ")
            )
        if self.haveBuried():
            if self.haveCustomStudy:
                now = " " + _("To see them now, click the Unbury button below.")
            else:
                now = ""
            line.append(
                _(
                    """\
Some related or buried cards were delayed until a later session."""
                )
                + now
            )
        if self.haveCustomStudy and not self.col.decks.current()["dyn"]:
            line.append(
                _(
                    """\
To study outside of the normal schedule, click the Custom Study button below."""
                )
            )
        return "<p>".join(line)

    def revDue(self) -> Optional[int]:
        "True if there are any rev cards due."
        return self.col.db.scalar(
            (
                f"select 1 from cards where did in %s and queue = {QUEUE_TYPE_REV} "
                "and due <= ? limit 1"
            )
            % self._deckLimit(),
            self.today,
        )

    def newDue(self) -> Optional[int]:
        "True if there are any new cards due."
        return self.col.db.scalar(
            (
                f"select 1 from cards where did in %s and queue = {QUEUE_TYPE_NEW} "
                "limit 1"
            )
            % self._deckLimit()
        )

    def haveBuried(self) -> bool:
        sdids = ids2str(self.col.decks.active())
        cnt = self.col.db.scalar(
            f"select 1 from cards where queue = {QUEUE_TYPE_SIBLING_BURIED} and did in %s limit 1"
            % sdids
        )
        return not not cnt

    # Next time reports
    ##########################################################################

    def nextIvlStr(self, card: Card, ease: int, short: bool = False) -> str:
        "Return the next interval for CARD as a string."
        ivl_secs = self.nextIvl(card, ease)
        if not ivl_secs:
            return _("(end)")
        s = self.col.backend.format_time_span(
            ivl_secs, FormatTimeSpanContext.ANSWER_BUTTONS
        )
        if ivl_secs < self.col.conf["collapseTime"]:
            s = "<" + s
        return s

    def nextIvl(self, card: Card, ease: int) -> float:
        "Return the next interval for CARD, in seconds."
        if card.queue in (QUEUE_TYPE_NEW, QUEUE_TYPE_LRN, QUEUE_TYPE_DAY_LEARN_RELEARN):
            return self._nextLrnIvl(card, ease)
        elif ease == BUTTON_ONE:
            # lapsed
            conf = self._lapseConf(card)
            if conf["delays"]:
                return conf["delays"][0] * 60
            return self._nextLapseIvl(card, conf) * 86400
        else:
            # review
            return self._nextRevIvl(card, ease) * 86400

    # this isn't easily extracted from the learn code
    def _nextLrnIvl(self, card: Card, ease: int) -> float:
        if card.queue == 0:
            card.left = self._startingLeft(card)
        conf = self._lrnConf(card)
        if ease == BUTTON_ONE:
            # fail
            return self._delayForGrade(conf, len(conf["delays"]))
        elif ease == BUTTON_THREE:
            # early removal
            if not self._resched(card):
                return 0
            return self._graduatingIvl(card, conf, True, adj=False) * 86400
        else:
            left = card.left % 1000 - 1
            if left <= 0:
                # graduate
                if not self._resched(card):
                    return 0
                return self._graduatingIvl(card, conf, False, adj=False) * 86400
            else:
                return self._delayForGrade(conf, left)

    # Suspending
    ##########################################################################

    def suspendCards(self, ids: List[int]) -> None:
        "Suspend cards."
        self.col.log(ids)
        self.remFromDyn(ids)
        self.removeLrn(ids)
        self.col.db.execute(
            f"update cards set queue={QUEUE_TYPE_SUSPENDED},mod=?,usn=? where id in "
            + ids2str(ids),
            intTime(),
            self.col.usn(),
        )

    def unsuspendCards(self, ids: List[int]) -> None:
        "Unsuspend cards."
        self.col.log(ids)
        self.col.db.execute(
            "update cards set queue=type,mod=?,usn=? "
            f"where queue = {QUEUE_TYPE_SUSPENDED} and id in " + ids2str(ids),
            intTime(),
            self.col.usn(),
        )

    def buryCards(self, cids: List[int]) -> None:
        self.col.log(cids)
        self.remFromDyn(cids)
        self.removeLrn(cids)
        self.col.db.execute(
            f"""
update cards set queue={QUEUE_TYPE_SIBLING_BURIED},mod=?,usn=? where id in """
            + ids2str(cids),
            intTime(),
            self.col.usn(),
        )

    def buryNote(self, nid: int) -> None:
        "Bury all cards for note until next session."
        cids = self.col.db.list(
            "select id from cards where nid = ? and queue >= 0", nid
        )
        self.buryCards(cids)

    # Sibling spacing
    ##########################################################################

    def _burySiblings(self, card: Card) -> None:
        toBury = []
        nconf = self._newConf(card)
        buryNew = nconf.get("bury", True)
        rconf = self._revConf(card)
        buryRev = rconf.get("bury", True)
        # loop through and remove from queues
        for cid, queue in self.col.db.execute(
            f"""
select id, queue from cards where nid=? and id!=?
and (queue={QUEUE_TYPE_NEW} or (queue={QUEUE_TYPE_REV} and due<=?))""",
            card.nid,
            card.id,
            self.today,
        ):
            if queue == QUEUE_TYPE_REV:
                if buryRev:
                    toBury.append(cid)
                # if bury disabled, we still discard to give same-day spacing
                try:
                    self._revQueue.remove(cid)
                except ValueError:
                    pass
            else:
                # if bury disabled, we still discard to give same-day spacing
                if buryNew:
                    toBury.append(cid)
                try:
                    self._newQueue.remove(cid)
                except ValueError:
                    pass
        # then bury
        if toBury:
            self.col.db.execute(
                f"update cards set queue={QUEUE_TYPE_SIBLING_BURIED},mod=?,usn=? where id in "
                + ids2str(toBury),
                intTime(),
                self.col.usn(),
            )
            self.col.log(toBury)

    # Resetting
    ##########################################################################

    def forgetCards(self, ids: List[int]) -> None:
        "Put cards at the end of the new queue."
        self.remFromDyn(ids)
        self.col.db.execute(
            f"update cards set type={CARD_TYPE_NEW},queue={QUEUE_TYPE_NEW},ivl=0,due=0,odue=0,factor=?"
            " where id in " + ids2str(ids),
            STARTING_FACTOR,
        )
        pmax = (
            self.col.db.scalar(f"select max(due) from cards where type={CARD_TYPE_NEW}")
            or 0
        )
        # takes care of mod + usn
        self.sortCards(ids, start=pmax + 1)
        self.col.log(ids)

    def reschedCards(self, ids: List[int], imin: int, imax: int) -> None:
        "Put cards in review queue with a new interval in days (min, max)."
        d = []
        t = self.today
        mod = intTime()
        for id in ids:
            r = random.randint(imin, imax)
            d.append(
                dict(
                    id=id,
                    due=r + t,
                    ivl=max(1, r),
                    mod=mod,
                    usn=self.col.usn(),
                    fact=STARTING_FACTOR,
                )
            )
        self.remFromDyn(ids)
        self.col.db.executemany(
            f"""
update cards set type={CARD_TYPE_REV},queue={QUEUE_TYPE_REV},ivl=:ivl,due=:due,odue=0,
usn=:usn,mod=:mod,factor=:fact where id=:id""",
            d,
        )
        self.col.log(ids)

    def resetCards(self, ids: List[int]) -> None:
        "Completely reset cards for export."
        sids = ids2str(ids)
        # we want to avoid resetting due number of existing new cards on export
        nonNew = self.col.db.list(
            f"select id from cards where id in %s and (queue != {QUEUE_TYPE_NEW} or type != {CARD_TYPE_NEW})"
            % sids
        )
        # reset all cards
        self.col.db.execute(
            f"update cards set reps=0,lapses=0,odid=0,odue=0,queue={QUEUE_TYPE_NEW}"
            " where id in %s" % sids
        )
        # and forget any non-new cards, changing their due numbers
        self.forgetCards(nonNew)
        self.col.log(ids)

    # Repositioning new cards
    ##########################################################################

    def sortCards(
        self,
        cids: List[int],
        start: int = 1,
        step: int = 1,
        shuffle: bool = False,
        shift: bool = False,
    ) -> None:
        scids = ids2str(cids)
        now = intTime()
        nids = []
        nidsSet: Set[int] = set()
        for id in cids:
            nid = self.col.db.scalar("select nid from cards where id = ?", id)
            if nid not in nidsSet:
                nids.append(nid)
                nidsSet.add(nid)
        if not nids:
            # no new cards
            return
        # determine nid ordering
        due = {}
        if shuffle:
            random.shuffle(nids)
        for c, nid in enumerate(nids):
            due[nid] = start + c * step
        # pylint: disable=undefined-loop-variable
        high = start + c * step
        # shift?
        if shift:
            low = self.col.db.scalar(
                f"select min(due) from cards where due >= ? and type = {CARD_TYPE_NEW} "
                "and id not in %s" % scids,
                start,
            )
            if low is not None:
                shiftby = high - low + 1
                self.col.db.execute(
                    f"""
update cards set mod=?, usn=?, due=due+? where id not in %s
and due >= ? and queue = {QUEUE_TYPE_NEW}"""
                    % scids,
                    now,
                    self.col.usn(),
                    shiftby,
                    low,
                )
        # reorder cards
        d = []
        for id, nid in self.col.db.execute(
            f"select id, nid from cards where type = {CARD_TYPE_NEW} and id in " + scids
        ):
            d.append(dict(now=now, due=due[nid], usn=self.col.usn(), cid=id))
        self.col.db.executemany(
            "update cards set due=:due,mod=:now,usn=:usn where id = :cid", d
        )

    def randomizeCards(self, did: int) -> None:
        cids = self.col.db.list("select id from cards where did = ?", did)
        self.sortCards(cids, shuffle=True)

    def orderCards(self, did: int) -> None:
        cids = self.col.db.list("select id from cards where did = ? order by nid", did)
        self.sortCards(cids)

    def resortConf(self, conf) -> None:
        for did in self.col.decks.didsForConf(conf):
            if conf["new"]["order"] == 0:
                self.randomizeCards(did)
            else:
                self.orderCards(did)

    # for post-import
    def maybeRandomizeDeck(self, did: Optional[int] = None) -> None:
        if not did:
            did = self.col.decks.selected()
        conf = self.col.decks.confForDid(did)
        # in order due?
        if conf["new"]["order"] == NEW_CARDS_RANDOM:
            self.randomizeCards(did)
