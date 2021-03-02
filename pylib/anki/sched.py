# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import random
import time
from heapq import *
from typing import Any, List, Optional, Tuple, Union

import anki
from anki import hooks
from anki.cards import Card
from anki.consts import *
from anki.decks import QueueConfig
from anki.schedv2 import Scheduler as V2
from anki.utils import ids2str, intTime

# queue types: 0=new/cram, 1=lrn, 2=rev, 3=day lrn, -1=suspended, -2=buried
# revlog types: 0=lrn, 1=rev, 2=relrn, 3=cram
# positive revlog intervals are in days (rev), negative in seconds (lrn)


class Scheduler(V2):
    name = "std"
    haveCustomStudy = True
    _spreadRev = True
    _burySiblingsOnAnswer = True

    def __init__(  # pylint: disable=super-init-not-called
        self, col: anki.collection.Collection
    ) -> None:
        self.col = col.weakref()
        self.queueLimit = 50
        self.reportLimit = 1000
        self.dynReportLimit = 99999
        self.reps = 0
        self.lrnCount = 0
        self.revCount = 0
        self.newCount = 0
        self.today: Optional[int] = None
        self._haveQueues = False
        self._updateCutoff()

    def answerCard(self, card: Card, ease: int) -> None:
        self.col.log()
        assert 1 <= ease <= 4
        self.col.markReview(card)
        if self._burySiblingsOnAnswer:
            self._burySiblings(card)
        card.reps += 1
        self.reps += 1
        # former is for logging new cards, latter also covers filt. decks
        card.wasNew = card.type == CARD_TYPE_NEW  # type: ignore
        wasNewQ = card.queue == QUEUE_TYPE_NEW

        new_delta = 0
        review_delta = 0

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
            new_delta = +1
        if card.queue in (QUEUE_TYPE_LRN, QUEUE_TYPE_DAY_LEARN_RELEARN):
            self._answerLrnCard(card, ease)
        elif card.queue == QUEUE_TYPE_REV:
            self._answerRevCard(card, ease)
            review_delta = +1
        else:
            raise Exception(f"Invalid queue '{card}'")

        self.update_stats(
            card.did,
            new_delta=new_delta,
            review_delta=review_delta,
            milliseconds_delta=+card.timeTaken(),
        )

        card.mod = intTime()
        card.usn = self.col.usn()
        card.flush()

    def counts(self, card: Optional[Card] = None) -> Tuple[int, int, int]:
        counts = [self.newCount, self.lrnCount, self.revCount]
        if card:
            idx = self.countIdx(card)
            if idx == QUEUE_TYPE_LRN:
                counts[QUEUE_TYPE_LRN] += card.left // 1000
            else:
                counts[idx] += 1

        new, lrn, rev = counts
        return (new, lrn, rev)

    def countIdx(self, card: Card) -> int:
        if card.queue == QUEUE_TYPE_DAY_LEARN_RELEARN:
            return QUEUE_TYPE_LRN
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
did in %s and queue = {QUEUE_TYPE_LRN} and due < ?
limit %d"""
            % (self._deckLimit(), self.reportLimit),
            self.dayCutoff,
        )
        for i in range(len(self._lrnQueue)):
            self._lrnQueue[i] = (self._lrnQueue[i][0], self._lrnQueue[i][1])
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
                    card.ivl = max(1, conf["minInt"], int(card.ivl * conf["mult"]))
                else:
                    # new card; no ivl adjustment
                    pass
                if resched and card.odid:
                    card.odue = self.today + 1
            delay = self._delayForGrade(conf, card.left)
            if card.due < time.time():
                # not collapsed; add some randomness
                delay *= int(random.uniform(1, 1.25))
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

    def _lrnConf(self, card: Card) -> QueueConfig:
        if card.type == CARD_TYPE_REV:
            return self._lapseConf(card)
        else:
            return self._newConf(card)

    def _rescheduleAsRev(self, card: Card, conf: QueueConfig, early: bool) -> None:
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

    def _graduatingIvl(
        self, card: Card, conf: QueueConfig, early: bool, adj: bool = True
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

    def _rescheduleNew(self, card: Card, conf: QueueConfig, early: bool) -> None:
        "Reschedule a new card that's graduated for the first time."
        card.ivl = self._graduatingIvl(card, conf, early)
        card.due = self.today + card.ivl
        card.factor = conf["initialFactor"]

    def _logLrn(
        self,
        card: Card,
        ease: int,
        conf: QueueConfig,
        leaving: bool,
        type: int,
        lastLeft: int,
    ) -> None:
        lastIvl = -(self._delayForGrade(conf, lastLeft))
        ivl = card.ivl if leaving else -(self._delayForGrade(conf, card.left))

        def log() -> None:
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
            extra = f" and id in {ids2str(ids)}"
        else:
            # benchmarks indicate it's about 10x faster to search all decks
            # with the index than scan the table
            extra = " and did in " + ids2str(
                d.id for d in self.col.decks.all_names_and_ids()
            )
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

    def _resetRev(self) -> None:
        self._revQueue: List[Any] = []
        self._revDids = self.col.decks.active()[:]

    def _fillRev(self, recursing: bool = False) -> bool:
        "True if a review card can be fetched."
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

        # if we didn't get a card but the count is non-zero,
        # we need to check again for any cards that were
        # removed from the queue but not buried
        if recursing:
            print("bug: fillRev()")
            return False
        self._reset_counts()
        self._resetRev()
        return self._fillRev(recursing=True)

    # Answering a review card
    ##########################################################################

    def _answerRevCard(self, card: Card, ease: int) -> None:
        delay: int = 0
        if ease == BUTTON_ONE:
            delay = self._rescheduleLapse(card)
        else:
            self._rescheduleRev(card, ease)
        self._logRev(card, ease, delay, REVLOG_REV)

    def _rescheduleLapse(self, card: Card) -> int:
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
        delay: int = 0
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

    def _nextLapseIvl(self, card: Card, conf: QueueConfig) -> int:
        return max(conf["minInt"], int(card.ivl * conf["mult"]))

    def _rescheduleRev(self, card: Card, ease: int) -> None:  # type: ignore[override]
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

    # Interval management
    ##########################################################################

    def _nextRevIvl(self, card: Card, ease: int) -> int:  # type: ignore[override]
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

    def _constrainedIvl(self, ivl: float, conf: QueueConfig, prev: int) -> int:  # type: ignore[override]
        "Integer interval after interval factor and prev+1 constraints applied."
        new = ivl * conf.get("ivlFct", 1)
        return int(max(new, prev + 1))

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

    # Filtered deck handling
    ##########################################################################

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

    def _checkLeech(self, card: Card, conf: QueueConfig) -> bool:
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

    def _newConf(self, card: Card) -> QueueConfig:
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
            order=NEW_CARDS_DUE,
            perDay=self.reportLimit,
        )

    def _lapseConf(self, card: Card) -> QueueConfig:
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

    def _resched(self, card: Card) -> bool:
        conf = self._cardConf(card)
        if not conf["dyn"]:
            return True
        return conf["resched"]

    # Deck finished state
    ##########################################################################

    def haveBuried(self) -> bool:
        sdids = self._deckLimit()
        cnt = self.col.db.scalar(
            f"select 1 from cards where queue = {QUEUE_TYPE_SIBLING_BURIED} and did in %s limit 1"
            % sdids
        )
        return not not cnt

    # Next time reports
    ##########################################################################

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
