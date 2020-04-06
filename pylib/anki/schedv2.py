# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import itertools
import random
import time
from heapq import *
from operator import itemgetter

# from anki.collection import _Collection
from typing import Any, Callable, Dict, List, Optional, Sequence, Set, Tuple, Union

import anki  # pylint: disable=unused-import
from anki import hooks
from anki.cards import Card
from anki.consts import *
from anki.decks import DeckManager
from anki.lang import _
from anki.rsbackend import FormatTimeSpanContext, SchedTimingToday
from anki.utils import ids2str, intTime

# card types: 0=new, 1=lrn, 2=rev, 3=relrn
# queue types: 0=new, 1=(re)lrn, 2=rev, 3=day (re)lrn,
#   4=preview, -1=suspended, -2=sibling buried, -3=manually buried

# revlog types: 0=lrn, 1=rev, 2=relrn, 3=early review
# positive revlog intervals are in days (rev), negative in seconds (lrn)
# odue/odid store original due/did when cards moved to filtered deck


class Scheduler:
    name = "std2"
    haveCustomStudy = True
    _burySiblingsOnAnswer = True
    revCount: int

    def __init__(self, col: anki.storage._Collection) -> None:
        self.col = col.weakref()
        self.queueLimit = 50
        self.reportLimit = 1000
        self.dynReportLimit = 99999
        self.reps = 0
        self.today: Optional[int] = None
        self._haveQueues = False
        self._lrnCutoff = 0
        self._updateCutoff()

    def getCard(self) -> Optional[Card]:
        """Pop the next card from the queue. None if finished."""
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
        assert 0 <= card.queue <= 4
        self.col.markReview(card)
        if self._burySiblingsOnAnswer:
            self._burySiblings(card)

        self._answerCard(card, ease)

        self._updateStats(card, "time", card.timeTaken())
        card.mod = intTime()
        card.usn = self.col.usn()
        card.flush()

    def _answerCard(self, card: Card, ease: int) -> None:
        if self._previewingCard(card):
            self._answerCardPreview(card, ease)
            return

        card.reps += 1

        if card.queue == QUEUE_TYPE_NEW:
            # came from the new queue, move to learning
            card.queue = QUEUE_TYPE_LRN
            card.type = CARD_TYPE_LRN
            # init reps to graduation
            card.left = self._startingLeft(card)
            # update daily limit
            self._updateStats(card, "new")

        if card.queue in (QUEUE_TYPE_LRN, QUEUE_TYPE_DAY_LEARN_RELEARN):
            self._answerLrnCard(card, ease)
        elif card.queue == QUEUE_TYPE_REV:
            self._answerRevCard(card, ease)
            # update daily limit
            self._updateStats(card, "rev")
        else:
            assert 0

        # once a card has been answered once, the original due date
        # no longer applies
        if card.odue:
            card.odue = 0

    def _answerCardPreview(self, card: Card, ease: int) -> None:
        assert 1 <= ease <= 2

        if ease == BUTTON_ONE:
            # repeat after delay
            card.queue = QUEUE_TYPE_PREVIEW
            card.due = intTime() + self._previewDelay(card)
            self.lrnCount += 1
        else:
            # BUTTON_TWO
            # restore original card state and remove from filtered deck
            self._restorePreviewCard(card)
            self._removeFromFiltered(card)

    def counts(self, card: Optional[Card] = None) -> Tuple[int, int, int]:
        counts = [self.newCount, self.lrnCount, self.revCount]
        if card:
            idx = self.countIdx(card)
            counts[idx] += 1
        new, lrn, rev = counts
        return (new, lrn, rev)

    def dueForecast(self, days: int = 7) -> List[Any]:
        "Return counts over next DAYS. Includes today."
        daysd: Dict[int, int] = dict(
            self.col.db.all(  # type: ignore
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
        if card.queue in (QUEUE_TYPE_DAY_LEARN_RELEARN, QUEUE_TYPE_PREVIEW):
            return QUEUE_TYPE_LRN
        return card.queue

    def answerButtons(self, card: Card) -> int:
        conf = self._cardConf(card)
        if card.odid and not conf["resched"]:
            return 2
        return 4

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

        childMap = self.col.decks.childMap()
        for deck in decks:
            p = DeckManager.immediate_parent(deck["name"])
            # new
            nlim = self._deckNewLimitSingle(deck)
            if p is not None:
                nlim = min(nlim, lims[p][0])
            new = self._newForDeck(deck["id"], nlim)
            # learning
            lrn = self._lrnForDeck(deck["id"])
            # reviews
            if p:
                plim = lims[p][1]
            else:
                plim = None
            rlim = self._deckRevLimitSingle(deck, parentLimit=plim)
            rev = self._revForDeck(deck["id"], rlim, childMap)
            # save to list
            data.append([deck["name"], deck["id"], rev, lrn, new])
            # add deck as a parent
            lims[deck["name"]] = [nlim, rlim]
        return data

    def deckDueTree(self) -> Any:
        self.col.decks._enable_dconf_cache()
        try:
            return self._groupChildren(self.deckDueList())
        finally:
            self.col.decks._disable_dconf_cache()

    def _groupChildren(self, grps: List[List[Any]]) -> Any:
        # first, split the group names into components
        for g in grps:
            g[0] = DeckManager.path(g[0])
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
            children: Any = []
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
                lrn += ch[3]
                new += ch[4]
            # limit the counts to the deck's limits
            conf = self.col.decks.confForDid(did)
            deck = self.col.decks.get(did)
            if not conf["dyn"]:
                new = max(0, min(new, self._deckNewLimitSingle(deck)))
            tree.append((head, did, rev, lrn, new, children))
        return tuple(tree)

    # Getting the next card
    ##########################################################################

    def _getCard(self) -> Optional[Card]:
        """Return the next due card, or None."""
        # learning card due?
        c = self._getLrnCard()
        if c:
            return c

        # new first, or time for one?
        if self._timeForNewCard():
            c = self._getNewCard()
            if c:
                return c

        # day learning first and card due?
        dayLearnFirst = self.col.conf.get("dayLearnFirst", False)
        if dayLearnFirst:
            c = self._getLrnDayCard()
            if c:
                return c

        # card due for review?
        c = self._getRevCard()
        if c:
            return c

        # day learning card due?
        if not dayLearnFirst:
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
        self._newQueue: List[int] = []
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
        else:
            # shouldn't reach
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
            return self.dynReportLimit
        c = self.col.decks.confForDid(g["id"])
        limit = max(0, c["new"]["perDay"] - g["newToday"][1])
        return hooks.scheduler_new_limit_for_single_deck(limit, g)

    def totalNewForCurrentDeck(self) -> int:
        return self.col.db.scalar(
            f"""
select count() from cards where id in (
select id from cards where did in %s and queue = {QUEUE_TYPE_NEW} limit ?)"""
            % self._deckLimit(),
            self.reportLimit,
        )

    # Learning queues
    ##########################################################################

    # scan for any newly due learning cards every minute
    def _updateLrnCutoff(self, force: bool) -> bool:
        nextCutoff = intTime() + self.col.conf["collapseTime"]
        if nextCutoff - self._lrnCutoff > 60 or force:
            self._lrnCutoff = nextCutoff
            return True
        return False

    def _maybeResetLrn(self, force: bool) -> None:
        if self._updateLrnCutoff(force):
            self._resetLrn()

    def _resetLrnCount(self) -> None:
        # sub-day
        self.lrnCount = (
            self.col.db.scalar(
                f"""
select count() from cards where did in %s and queue = {QUEUE_TYPE_LRN}
and due < ?"""
                % (self._deckLimit()),
                self._lrnCutoff,
            )
            or 0
        )
        # day
        self.lrnCount += self.col.db.scalar(
            f"""
select count() from cards where did in %s and queue = {QUEUE_TYPE_DAY_LEARN_RELEARN}
and due <= ?"""
            % (self._deckLimit()),
            self.today,
        )
        # previews
        self.lrnCount += self.col.db.scalar(
            f"""
select count() from cards where did in %s and queue = {QUEUE_TYPE_PREVIEW}
"""
            % (self._deckLimit())
        )

    def _resetLrn(self) -> None:
        self._updateLrnCutoff(force=True)
        self._resetLrnCount()
        self._lrnQueue: List[Tuple[int, int]] = []
        self._lrnDayQueue: List[int] = []
        self._lrnDids = self.col.decks.active()[:]

    # sub-day learning
    def _fillLrn(self) -> Union[bool, List[Any]]:
        if not self.lrnCount:
            return False
        if self._lrnQueue:
            return True
        cutoff = intTime() + self.col.conf["collapseTime"]
        self._lrnQueue = self.col.db.all(  # type: ignore
            f"""
select due, id from cards where
did in %s and queue in ({QUEUE_TYPE_LRN},{QUEUE_TYPE_PREVIEW}) and due < ?
limit %d"""
            % (self._deckLimit(), self.reportLimit),
            cutoff,
        )
        for i in range(len(self._lrnQueue)):
            self._lrnQueue[i] = (self._lrnQueue[i][0], self._lrnQueue[i][1])
        # as it arrives sorted by did first, we need to sort it
        self._lrnQueue.sort()
        return self._lrnQueue

    def _getLrnCard(self, collapse: bool = False) -> Optional[Card]:
        self._maybeResetLrn(force=collapse and self.lrnCount == 0)
        if self._fillLrn():
            cutoff = time.time()
            if collapse:
                cutoff += self.col.conf["collapseTime"]
            if self._lrnQueue[0][0] < cutoff:
                id = heappop(self._lrnQueue)[1]
                card = self.col.getCard(id)
                self.lrnCount -= 1
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
        # shouldn't reach here
        return False

    def _getLrnDayCard(self) -> Optional[Card]:
        if self._fillLrnDay():
            self.lrnCount -= 1
            return self.col.getCard(self._lrnDayQueue.pop())
        return None

    def _answerLrnCard(self, card: Card, ease: int) -> None:
        conf = self._lrnConf(card)
        if card.type in (CARD_TYPE_REV, CARD_TYPE_RELEARNING):
            type = REVLOG_RELRN
        else:
            type = REVLOG_LRN
        # lrnCount was decremented once when card was fetched
        lastLeft = card.left

        leaving = False

        # immediate graduate?
        if ease == BUTTON_FOUR:
            self._rescheduleAsRev(card, conf, True)
            leaving = True
        # next step?
        elif ease == BUTTON_THREE:
            # graduation time?
            if (card.left % 1000) - 1 <= 0:
                self._rescheduleAsRev(card, conf, False)
                leaving = True
            else:
                self._moveToNextStep(card, conf)
        elif ease == BUTTON_TWO:
            self._repeatStep(card, conf)
        else:
            # back to first step
            self._moveToFirstStep(card, conf)

        self._logLrn(card, ease, conf, leaving, type, lastLeft)

    def _updateRevIvlOnFail(self, card: Card, conf: Dict[str, Any]) -> None:
        card.lastIvl = card.ivl
        card.ivl = self._lapseIvl(card, conf)

    def _moveToFirstStep(self, card: Card, conf: Dict[str, Any]) -> Any:
        card.left = self._startingLeft(card)

        # relearning card?
        if card.type == CARD_TYPE_RELEARNING:
            self._updateRevIvlOnFail(card, conf)

        return self._rescheduleLrnCard(card, conf)

    def _moveToNextStep(self, card: Card, conf: Dict[str, Any]) -> None:
        # decrement real left count and recalculate left today
        left = (card.left % 1000) - 1
        card.left = self._leftToday(conf["delays"], left) * 1000 + left

        self._rescheduleLrnCard(card, conf)

    def _repeatStep(self, card: Card, conf: Dict[str, Any]) -> None:
        delay = self._delayForRepeatingGrade(conf, card.left)
        self._rescheduleLrnCard(card, conf, delay=delay)

    def _rescheduleLrnCard(
        self, card: Card, conf: Dict[str, Any], delay: Optional[int] = None
    ) -> Any:
        # normal delay for the current step?
        if delay is None:
            delay = self._delayForGrade(conf, card.left)

        card.due = int(time.time() + delay)
        # due today?
        if card.due < self.dayCutoff:
            # add some randomness, up to 5 minutes or 25%
            maxExtra = min(300, int(delay * 0.25))
            fuzz = random.randrange(0, max(1, maxExtra))
            card.due = min(self.dayCutoff - 1, card.due + fuzz)
            card.queue = QUEUE_TYPE_LRN
            if card.due < (intTime() + self.col.conf["collapseTime"]):
                self.lrnCount += 1
                # if the queue is not empty and there's nothing else to do, make
                # sure we don't put it at the head of the queue and end up showing
                # it twice in a row
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
        return delay

    def _delayForGrade(self, conf: Dict[str, Any], left: int) -> int:
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

    def _delayForRepeatingGrade(self, conf: Dict[str, Any], left: int) -> Any:
        # halfway between last and next
        delay1 = self._delayForGrade(conf, left)
        if len(conf["delays"]) > 1:
            delay2 = self._delayForGrade(conf, left - 1)
        else:
            delay2 = delay1 * 2
        avg = (delay1 + max(delay1, delay2)) // 2
        return avg

    def _lrnConf(self, card: Card) -> Any:
        if card.type in (CARD_TYPE_REV, CARD_TYPE_RELEARNING):
            return self._lapseConf(card)
        else:
            return self._newConf(card)

    def _rescheduleAsRev(self, card: Card, conf: Dict[str, Any], early: bool) -> None:
        lapse = card.type in (CARD_TYPE_REV, CARD_TYPE_RELEARNING)

        if lapse:
            self._rescheduleGraduatingLapse(card, early)
        else:
            self._rescheduleNew(card, conf, early)

        # if we were dynamic, graduating means moving back to the old deck
        if card.odid:
            self._removeFromFiltered(card)

    def _rescheduleGraduatingLapse(self, card: Card, early: bool = False) -> None:
        if early:
            card.ivl += 1
        card.due = self.today + card.ivl
        card.queue = QUEUE_TYPE_REV
        card.type = CARD_TYPE_REV

    def _startingLeft(self, card: Card) -> int:
        if card.type == CARD_TYPE_RELEARNING:
            conf = self._lapseConf(card)
        else:
            conf = self._lrnConf(card)
        tot = len(conf["delays"])
        tod = self._leftToday(conf["delays"], tot)
        return tot + tod * 1000

    def _leftToday(
        self, delays: List[int], left: int, now: Optional[int] = None,
    ) -> int:
        "The number of steps that can be completed by the day cutoff."
        if not now:
            now = intTime()
        delays = delays[-left:]
        ok = 0
        for i in range(len(delays)):
            now += int(delays[i] * 60)
            if now > self.dayCutoff:
                break
            ok = i
        return ok + 1

    def _graduatingIvl(
        self, card: Card, conf: Dict[str, Any], early: bool, fuzz: bool = True
    ) -> Any:
        if card.type in (CARD_TYPE_REV, CARD_TYPE_RELEARNING):
            bonus = early and 1 or 0
            return card.ivl + bonus
        if not early:
            # graduate
            ideal = conf["ints"][0]
        else:
            # early remove
            ideal = conf["ints"][1]
        if fuzz:
            ideal = self._fuzzedIvl(ideal)
        return ideal

    def _rescheduleNew(self, card: Card, conf: Dict[str, Any], early: bool) -> None:
        "Reschedule a new card that's graduated for the first time."
        card.ivl = self._graduatingIvl(card, conf, early)
        card.due = self.today + card.ivl
        card.factor = conf["initialFactor"]
        card.type = card.queue = QUEUE_TYPE_REV

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
        if leaving:
            ivl = card.ivl
        else:
            if ease == BUTTON_TWO:
                ivl = -self._delayForRepeatingGrade(conf, card.left)
            else:
                ivl = -self._delayForGrade(conf, card.left)

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

    def _lrnForDeck(self, did: int) -> int:
        cnt = (
            self.col.db.scalar(
                f"""
select count() from
(select null from cards where did = ? and queue = {QUEUE_TYPE_LRN} and due < ? limit ?)""",
                did,
                intTime() + self.col.conf["collapseTime"],
                self.reportLimit,
            )
            or 0
        )
        return cnt + self.col.db.scalar(
            f"""
select count() from
(select null from cards where did = ? and queue = {QUEUE_TYPE_DAY_LEARN_RELEARN}
and due <= ? limit ?)""",
            did,
            self.today,
            self.reportLimit,
        )

    # Reviews
    ##########################################################################

    def _currentRevLimit(self) -> int:
        d = self.col.decks.get(self.col.decks.selected(), default=False)
        return self._deckRevLimitSingle(d)

    def _deckRevLimitSingle(
        self, d: Dict[str, Any], parentLimit: Optional[int] = None
    ) -> int:
        # invalid deck selected?
        if not d:
            return 0

        if d["dyn"]:
            return self.dynReportLimit

        c = self.col.decks.confForDid(d["id"])
        lim = max(0, c["rev"]["perDay"] - d["revToday"][1])

        if parentLimit is not None:
            lim = min(parentLimit, lim)
        elif "::" in d["name"]:
            for parent in self.col.decks.parents(d["id"]):
                # pass in dummy parentLimit so we don't do parent lookup again
                lim = min(lim, self._deckRevLimitSingle(parent, parentLimit=lim))
        return hooks.scheduler_review_limit_for_single_deck(lim, d)

    def _revForDeck(self, did: int, lim: int, childMap: Dict[int, Any]) -> Any:
        dids = [did] + self.col.decks.childDids(did, childMap)
        lim = min(lim, self.reportLimit)
        return self.col.db.scalar(
            f"""
select count() from
(select 1 from cards where did in %s and queue = {QUEUE_TYPE_REV}
and due <= ? limit ?)"""
            % ids2str(dids),
            self.today,
            lim,
        )

    def _resetRevCount(self) -> None:
        lim = self._currentRevLimit()
        self.revCount = self.col.db.scalar(
            f"""
select count() from (select id from cards where
did in %s and queue = {QUEUE_TYPE_REV} and due <= ? limit ?)"""
            % self._deckLimit(),
            self.today,
            lim,
        )

    def _resetRev(self) -> None:
        self._resetRevCount()
        self._revQueue: List[int] = []

    def _fillRev(self) -> Any:
        if self._revQueue:
            return True
        if not self.revCount:
            return False

        lim = min(self.queueLimit, self._currentRevLimit())
        if lim:
            self._revQueue = self.col.db.list(
                f"""
select id from cards where
did in %s and queue = {QUEUE_TYPE_REV} and due <= ?
order by due, random()
limit ?"""
                % self._deckLimit(),
                self.today,
                lim,
            )

            if self._revQueue:
                # preserve order
                self._revQueue.reverse()
                return True

        if self.revCount:
            # if we didn't get a card but the count is non-zero,
            # we need to check again for any cards that were
            # removed from the queue but not buried
            self._resetRev()
            return self._fillRev()

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
            % self._deckLimit(),
            self.today,
            self.reportLimit,
        )

    # Answering a review card
    ##########################################################################

    def _answerRevCard(self, card: Card, ease: int) -> None:
        delay = 0
        early = bool(card.odid and (card.odue > self.today))
        type = early and REVLOG_CRAM or REVLOG_REV

        if ease == BUTTON_ONE:
            delay = self._rescheduleLapse(card)
        else:
            self._rescheduleRev(card, ease, early)

        hooks.schedv2_did_answer_review_card(card, ease, early)
        self._logRev(card, ease, delay, type)

    def _rescheduleLapse(self, card: Card) -> Any:
        conf = self._lapseConf(card)

        card.lapses += 1
        card.factor = max(1300, card.factor - 200)

        suspended = self._checkLeech(card, conf) and card.queue == QUEUE_TYPE_SUSPENDED

        if conf["delays"] and not suspended:
            card.type = CARD_TYPE_RELEARNING
            delay = self._moveToFirstStep(card, conf)
        else:
            # no relearning steps
            self._updateRevIvlOnFail(card, conf)
            self._rescheduleAsRev(card, conf, early=False)
            # need to reset the queue after rescheduling
            if suspended:
                card.queue = QUEUE_TYPE_SUSPENDED
            delay = 0

        return delay

    def _lapseIvl(self, card: Card, conf: Dict[str, Any]) -> Any:
        ivl = max(1, conf["minInt"], int(card.ivl * conf["mult"]))
        return ivl

    def _rescheduleRev(self, card: Card, ease: int, early: bool) -> None:
        # update interval
        card.lastIvl = card.ivl
        if early:
            self._updateEarlyRevIvl(card, ease)
        else:
            self._updateRevIvl(card, ease)

        # then the rest
        card.factor = max(1300, card.factor + [-150, 0, 150][ease - 2])
        card.due = self.today + card.ivl

        # card leaves filtered deck
        self._removeFromFiltered(card)

    def _logRev(self, card: Card, ease: int, delay: int, type: int) -> None:
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
                type,
            )

        try:
            log()
        except:
            # duplicate pk; retry in 10ms
            time.sleep(0.01)
            log()

    # Interval management
    ##########################################################################

    def _nextRevIvl(self, card: Card, ease: int, fuzz: bool) -> int:
        "Next review interval for CARD, given EASE."
        delay = self._daysLate(card)
        conf = self._revConf(card)
        fct = card.factor / 1000
        hardFactor = conf.get("hardFactor", 1.2)
        if hardFactor > 1:
            hardMin = card.ivl
        else:
            hardMin = 0
        ivl2 = self._constrainedIvl(card.ivl * hardFactor, conf, hardMin, fuzz)
        if ease == BUTTON_TWO:
            return ivl2

        ivl3 = self._constrainedIvl((card.ivl + delay // 2) * fct, conf, ivl2, fuzz)
        if ease == BUTTON_THREE:
            return ivl3

        ivl4 = self._constrainedIvl(
            (card.ivl + delay) * fct * conf["ease4"], conf, ivl3, fuzz
        )
        return ivl4

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

    def _constrainedIvl(
        self, ivl: float, conf: Dict[str, Any], prev: int, fuzz: bool
    ) -> int:
        ivl = int(ivl * conf.get("ivlFct", 1))
        if fuzz:
            ivl = self._fuzzedIvl(ivl)
        ivl = max(ivl, prev + 1, 1)
        ivl = min(ivl, conf["maxIvl"])
        return int(ivl)

    def _daysLate(self, card: Card) -> int:
        "Number of days later than scheduled."
        due = card.odue if card.odid else card.due
        return max(0, self.today - due)

    def _updateRevIvl(self, card: Card, ease: int) -> None:
        card.ivl = self._nextRevIvl(card, ease, fuzz=True)

    def _updateEarlyRevIvl(self, card: Card, ease: int) -> None:
        card.ivl = self._earlyReviewIvl(card, ease)

    # next interval for card when answered early+correctly
    def _earlyReviewIvl(self, card: Card, ease: int) -> int:
        assert card.odid and card.type == CARD_TYPE_REV
        assert card.factor
        assert ease > 1

        elapsed = card.ivl - (card.odue - self.today)

        conf = self._revConf(card)

        easyBonus = 1
        # early 3/4 reviews shouldn't decrease previous interval
        minNewIvl = 1

        if ease == BUTTON_TWO:
            factor = conf.get("hardFactor", 1.2)
            # hard cards shouldn't have their interval decreased by more than 50%
            # of the normal factor
            minNewIvl = factor / 2
        elif ease == BUTTON_THREE:
            factor = card.factor / 1000
        else:  # ease == BUTTON_FOUR:
            factor = card.factor / 1000
            ease4 = conf["ease4"]
            # 1.3 -> 1.15
            easyBonus = ease4 - (ease4 - 1) / 2

        ivl = max(elapsed * factor, 1)

        # cap interval decreases
        ivl = max(card.ivl * minNewIvl, ivl) * easyBonus

        ivl = self._constrainedIvl(ivl, conf, prev=0, fuzz=False)

        return ivl

    # Dynamic deck handling
    ##########################################################################

    _restoreQueueWhenEmptyingSnippet = f"""
queue = (case when queue < 0 then queue
              when type in (1,{CARD_TYPE_RELEARNING}) then
  (case when (case when odue then odue else due end) > 1000000000 then 1 else
  {QUEUE_TYPE_DAY_LEARN_RELEARN} end)
else
  type
end)
"""

    def rebuildDyn(self, did: Optional[int] = None) -> Optional[int]:
        "Rebuild a dynamic deck."
        did = did or self.col.decks.selected()
        deck = self.col.decks.get(did)
        assert deck["dyn"]
        # move any existing cards back first, then fill
        self.emptyDyn(did)
        cnt = self._fillDyn(deck)
        if not cnt:
            return None
        # and change to our new deck
        self.col.decks.select(did)
        return cnt

    def _fillDyn(self, deck: Dict[str, Any]) -> int:
        start = -100000
        total = 0
        for search, limit, order in deck["terms"]:
            orderlimit = self._dynOrder(order, limit)
            if search.strip():
                search = "(%s)" % search
            search = "%s -is:suspended -is:buried -deck:filtered" % search
            try:
                ids = self.col.findCards(search, order=orderlimit)
            except:
                return total
            # move the cards over
            self.col.log(deck["id"], ids)
            self._moveToDyn(deck["id"], ids, start=start + total)
            total += len(ids)
        return total

    def emptyDyn(self, did: Optional[int], lim: Optional[str] = None) -> None:
        if not lim:
            lim = "did = %s" % did
        self.col.log(self.col.db.list("select id from cards where %s" % lim))

        self.col.db.execute(
            """
update cards set did = odid, %s,
due = (case when odue>0 then odue else due end), odue = 0, odid = 0, usn = ? where %s"""
            % (self._restoreQueueWhenEmptyingSnippet, lim),
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
        elif o == DYN_DUEPRIORITY:
            t = (
                f"(case when queue={QUEUE_TYPE_REV} and due <= %d then (ivl / cast(%d-due+0.001 as real)) else 100000+due end)"
                % (self.today, self.today)
            )
        else:  # DYN_DUE or unknown
            t = "c.due, c.ord"
        return t + " limit %d" % l

    def _moveToDyn(self, did: int, ids: Sequence[int], start: int = -100000) -> None:
        deck = self.col.decks.get(did)
        data = []
        u = self.col.usn()
        due = start
        for id in ids:
            data.append((did, due, u, id))
            due += 1

        queue = ""
        if not deck["resched"]:
            queue = f",queue={QUEUE_TYPE_REV}"

        query = (
            """
update cards set
odid = did, odue = due,
did = ?,
due = (case when due <= 0 then due else ? end),
usn = ?
%s
where id = ?
"""
            % queue
        )
        self.col.db.executemany(query, data)

    def _removeFromFiltered(self, card: Card) -> None:
        if card.odid:
            card.did = card.odid
            card.odue = 0
            card.odid = 0

    def _restorePreviewCard(self, card: Card) -> None:
        assert card.odid

        card.due = card.odue

        # learning and relearning cards may be seconds-based or day-based;
        # other types map directly to queues
        if card.type in (CARD_TYPE_LRN, CARD_TYPE_RELEARNING):
            if card.odue > 1000000000:
                card.queue = QUEUE_TYPE_LRN
            else:
                card.queue = QUEUE_TYPE_DAY_LEARN_RELEARN
        else:
            card.queue = card.type

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
                card.queue = QUEUE_TYPE_SUSPENDED
            # notify UI
            hooks.card_did_leech(card)
            return True
        return False

    # Tools
    ##########################################################################

    def _cardConf(self, card: Card) -> Dict[str, Any]:
        return self.col.decks.confForDid(card.did)

    def _newConf(self, card: Card) -> Any:
        conf = self._cardConf(card)
        # normal deck
        if not card.odid:
            return conf["new"]
        # dynamic deck; override some attributes, use original deck for others
        oconf = self.col.decks.confForDid(card.odid)
        return dict(
            # original deck
            ints=oconf["new"]["ints"],
            initialFactor=oconf["new"]["initialFactor"],
            bury=oconf["new"].get("bury", True),
            delays=oconf["new"]["delays"],
            # overrides
            order=NEW_CARDS_DUE,
            perDay=self.reportLimit,
        )

    def _lapseConf(self, card: Card) -> Any:
        conf = self._cardConf(card)
        # normal deck
        if not card.odid:
            return conf["lapse"]
        # dynamic deck; override some attributes, use original deck for others
        oconf = self.col.decks.confForDid(card.odid)
        return dict(
            # original deck
            minInt=oconf["lapse"]["minInt"],
            leechFails=oconf["lapse"]["leechFails"],
            leechAction=oconf["lapse"]["leechAction"],
            mult=oconf["lapse"]["mult"],
            delays=oconf["lapse"]["delays"],
            # overrides
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

    def _previewingCard(self, card: Card) -> Any:
        conf = self._cardConf(card)
        return conf["dyn"] and not conf["resched"]

    def _previewDelay(self, card: Card) -> Any:
        return self._cardConf(card).get("previewDelay", 10) * 60

    # Daily cutoff
    ##########################################################################

    def _updateCutoff(self) -> None:
        oldToday = self.today
        timing = self._timing_today()
        self.today = timing.days_elapsed
        self.dayCutoff = timing.next_day_at

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
            self.col.conf["lastUnburied"] = self.today

    def _checkDay(self) -> None:
        # check if the day has rolled over
        if time.time() > self.dayCutoff:
            self.reset()

    def _rolloverHour(self) -> int:
        return self.col.conf.get("rollover", 4)

    def _timing_today(self) -> SchedTimingToday:
        roll: Optional[int] = None
        if self.col.schedVer() > 1:
            roll = self._rolloverHour()
        return self.col.backend.sched_timing_today(
            self.col.crt,
            self._creation_timezone_offset(),
            self._current_timezone_offset(),
            roll,
        )

    def _current_timezone_offset(self) -> Optional[int]:
        if self.col.server:
            mins = self.col.server.minutes_west
            if mins is not None:
                return mins
            # older Anki versions stored the local offset in
            # the config
            return self.col.conf.get("localOffset", 0)
        else:
            return None

    def _creation_timezone_offset(self) -> Optional[int]:
        return self.col.conf.get("creationOffset", None)

    # New timezone handling - GUI helpers
    ##########################################################################

    def new_timezone_enabled(self) -> bool:
        return self.col.conf.get("creationOffset") is not None

    def set_creation_offset(self):
        """Save the UTC west offset at the time of creation into the DB.

        Once stored, this activates the new timezone handling code.
        """
        mins_west = self.col.backend.local_minutes_west(self.col.crt)
        self.col.conf["creationOffset"] = mins_west
        self.col.setMod()

    def clear_creation_offset(self):
        if "creationOffset" in self.col.conf:
            del self.col.conf["creationOffset"]
            self.col.setMod()

    # Deck finished state
    ##########################################################################

    def finishedMsg(self) -> str:
        return (
            "<b>"
            + _("Congratulations! You have finished this deck for now.")
            + "</b><br><br>"
            + self._nextDueMsg()
        )

    def next_learn_msg(self) -> str:
        dids = self._deckLimit()
        (next, remaining) = self.col.db.first(
            f"""
select min(due), count(*)
from cards where did in {dids} and queue = {QUEUE_TYPE_LRN}
"""
        )
        next = next or 0
        remaining = remaining or 0
        if next and next < self.dayCutoff:
            next -= intTime() - self.col.conf["collapseTime"]
            return self.col.backend.learning_congrats_msg(abs(next), remaining)
        else:
            return ""

    def _nextDueMsg(self) -> str:
        line = []

        learn_msg = self.next_learn_msg()
        if learn_msg:
            line.append(learn_msg)

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

    def haveBuriedSiblings(self) -> bool:
        cnt = self.col.db.scalar(
            f"select 1 from cards where queue = {QUEUE_TYPE_SIBLING_BURIED} and did in %s limit 1"
            % self._deckLimit()
        )
        return not not cnt

    def haveManuallyBuried(self) -> bool:
        cnt = self.col.db.scalar(
            f"select 1 from cards where queue = {QUEUE_TYPE_MANUALLY_BURIED} and did in %s limit 1"
            % self._deckLimit()
        )
        return not not cnt

    def haveBuried(self) -> bool:
        return self.haveManuallyBuried() or self.haveBuriedSiblings()

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

    def nextIvl(self, card: Card, ease: int) -> Any:
        "Return the next interval for CARD, in seconds."
        # preview mode?
        if self._previewingCard(card):
            if ease == BUTTON_ONE:
                return self._previewDelay(card)
            return 0

        # (re)learning?
        if card.queue in (QUEUE_TYPE_NEW, QUEUE_TYPE_LRN, QUEUE_TYPE_DAY_LEARN_RELEARN):
            return self._nextLrnIvl(card, ease)
        elif ease == BUTTON_ONE:
            # lapse
            conf = self._lapseConf(card)
            if conf["delays"]:
                return conf["delays"][0] * 60
            return self._lapseIvl(card, conf) * 86400
        else:
            # review
            early = card.odid and (card.odue > self.today)
            if early:
                return self._earlyReviewIvl(card, ease) * 86400
            else:
                return self._nextRevIvl(card, ease, fuzz=False) * 86400

    # this isn't easily extracted from the learn code
    def _nextLrnIvl(self, card: Card, ease: int) -> Any:
        if card.queue == QUEUE_TYPE_NEW:
            card.left = self._startingLeft(card)
        conf = self._lrnConf(card)
        if ease == BUTTON_ONE:
            # fail
            return self._delayForGrade(conf, len(conf["delays"]))
        elif ease == BUTTON_TWO:
            return self._delayForRepeatingGrade(conf, card.left)
        elif ease == BUTTON_FOUR:
            return self._graduatingIvl(card, conf, True, fuzz=False) * 86400
        else:  # ease == BUTTON_THREE
            left = card.left % 1000 - 1
            if left <= 0:
                # graduate
                return self._graduatingIvl(card, conf, False, fuzz=False) * 86400
            else:
                return self._delayForGrade(conf, left)

    # Suspending & burying
    ##########################################################################

    # learning and relearning cards may be seconds-based or day-based;
    # other types map directly to queues
    _restoreQueueSnippet = f"""
queue = (case when type in ({CARD_TYPE_LRN},{CARD_TYPE_RELEARNING}) then
  (case when (case when odue then odue else due end) > 1000000000 then 1 else
  {QUEUE_TYPE_DAY_LEARN_RELEARN} end)
else
  type
end)
"""

    def suspendCards(self, ids: List[int]) -> None:
        "Suspend cards."
        self.col.log(ids)
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
            (
                f"update cards set %s,mod=?,usn=? where queue = {QUEUE_TYPE_SUSPENDED} and id in %s"
            )
            % (self._restoreQueueSnippet, ids2str(ids)),
            intTime(),
            self.col.usn(),
        )

    def buryCards(self, cids: List[int], manual: bool = True) -> None:
        queue = manual and QUEUE_TYPE_MANUALLY_BURIED or QUEUE_TYPE_SIBLING_BURIED
        self.col.log(cids)
        self.col.db.execute(
            """
update cards set queue=?,mod=?,usn=? where id in """
            + ids2str(cids),
            queue,
            intTime(),
            self.col.usn(),
        )

    def buryNote(self, nid: int) -> None:
        "Bury all cards for note until next session."
        cids = self.col.db.list(
            f"select id from cards where nid = ? and queue >= {QUEUE_TYPE_NEW}", nid
        )
        self.buryCards(cids)

    def unburyCards(self) -> None:
        "Unbury all buried cards in all decks."
        self.col.log(
            self.col.db.list(
                f"select id from cards where queue in ({QUEUE_TYPE_SIBLING_BURIED}, {QUEUE_TYPE_MANUALLY_BURIED})"
            )
        )
        self.col.db.execute(
            f"update cards set %s where queue in ({QUEUE_TYPE_SIBLING_BURIED}, {QUEUE_TYPE_MANUALLY_BURIED})"
            % self._restoreQueueSnippet
        )

    def unburyCardsForDeck(self, type: str = "all") -> None:
        if type == "all":
            queue = (
                f"queue in ({QUEUE_TYPE_SIBLING_BURIED}, {QUEUE_TYPE_MANUALLY_BURIED})"
            )
        elif type == "manual":
            queue = f"queue = {QUEUE_TYPE_MANUALLY_BURIED}"
        elif type == "siblings":
            queue = f"queue = {QUEUE_TYPE_SIBLING_BURIED}"
        else:
            raise Exception("unknown type")

        self.col.log(
            self.col.db.list(
                "select id from cards where %s and did in %s"
                % (queue, self._deckLimit())
            )
        )
        self.col.db.execute(
            "update cards set mod=?,usn=?,%s where %s and did in %s"
            % (self._restoreQueueSnippet, queue, self._deckLimit()),
            intTime(),
            self.col.usn(),
        )

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
            self.buryCards(toBury, manual=False)

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
            d.append((max(1, r), r + t, self.col.usn(), mod, STARTING_FACTOR, id,))
        self.remFromDyn(ids)
        self.col.db.executemany(
            f"""
update cards set type={CARD_TYPE_REV},queue={QUEUE_TYPE_REV},ivl=?,due=?,odue=0,
usn=?,mod=?,factor=? where id=?""",
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
            d.append((due[nid], now, self.col.usn(), id))
        self.col.db.executemany("update cards set due=?,mod=?,usn=? where id = ?", d)

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

    # Changing scheduler versions
    ##########################################################################

    def _emptyAllFiltered(self) -> None:
        self.col.db.execute(
            f"""
update cards set did = odid, queue = (case
when type = {CARD_TYPE_LRN} then {QUEUE_TYPE_NEW}
when type = {CARD_TYPE_RELEARNING} then {QUEUE_TYPE_REV}
else type end), type = (case
when type = {CARD_TYPE_LRN} then {CARD_TYPE_NEW}
when type = {CARD_TYPE_RELEARNING} then {CARD_TYPE_REV}
else type end),
due = odue, odue = 0, odid = 0, usn = ? where odid != 0""",
            self.col.usn(),
        )

    def _removeAllFromLearning(self, schedVer: int = 2) -> None:
        # remove review cards from relearning
        if schedVer == 1:
            self.col.db.execute(
                f"""
    update cards set
    due = odue, queue = {QUEUE_TYPE_REV}, type = {CARD_TYPE_REV}, mod = %d, usn = %d, odue = 0
    where queue in ({QUEUE_TYPE_LRN},{QUEUE_TYPE_DAY_LEARN_RELEARN}) and type in ({CARD_TYPE_REV}, {CARD_TYPE_RELEARNING})
    """
                % (intTime(), self.col.usn())
            )
        else:
            self.col.db.execute(
                f"""
    update cards set
    due = %d+ivl, queue = {QUEUE_TYPE_REV}, type = {CARD_TYPE_REV}, mod = %d, usn = %d, odue = 0
    where queue in ({QUEUE_TYPE_LRN},{QUEUE_TYPE_DAY_LEARN_RELEARN}) and type in ({CARD_TYPE_REV}, {CARD_TYPE_RELEARNING})
    """
                % (self.today, intTime(), self.col.usn())
            )
        # remove new cards from learning
        self.forgetCards(
            self.col.db.list(
                f"select id from cards where queue in ({QUEUE_TYPE_LRN},{QUEUE_TYPE_DAY_LEARN_RELEARN})"
            )
        )

    # v1 doesn't support buried/suspended (re)learning cards
    def _resetSuspendedLearning(self) -> None:
        self.col.db.execute(
            f"""
update cards set type = (case
when type = {CARD_TYPE_LRN} then {CARD_TYPE_NEW}
when type in ({CARD_TYPE_REV}, {CARD_TYPE_RELEARNING}) then {CARD_TYPE_REV}
else type end),
due = (case when odue then odue else due end),
odue = 0,
mod = %d, usn = %d
where queue < {QUEUE_TYPE_NEW}"""
            % (intTime(), self.col.usn())
        )

    # no 'manually buried' queue in v1
    def _moveManuallyBuried(self) -> None:
        self.col.db.execute(
            f"update cards set queue={QUEUE_TYPE_SIBLING_BURIED},mod=%d where queue={QUEUE_TYPE_MANUALLY_BURIED}"
            % intTime()
        )

    # adding 'hard' in v2 scheduler means old ease entries need shifting
    # up or down
    def _remapLearningAnswers(self, sql: str) -> None:
        self.col.db.execute(
            f"update revlog set %s and type in ({CARD_TYPE_NEW},{CARD_TYPE_REV})" % sql
        )

    def moveToV1(self) -> None:
        self._emptyAllFiltered()
        self._removeAllFromLearning()

        self._moveManuallyBuried()
        self._resetSuspendedLearning()
        self._remapLearningAnswers("ease=ease-1 where ease in (3,4)")

    def moveToV2(self) -> None:
        self._emptyAllFiltered()
        self._removeAllFromLearning(schedVer=1)
        self._remapLearningAnswers("ease=ease+1 where ease in (2,3)")
