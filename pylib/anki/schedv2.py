# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import pprint
import random
import time
from heapq import *
from typing import (
    TYPE_CHECKING,
    Any,
    Callable,
    Dict,
    List,
    Optional,
    Sequence,
    Tuple,
    Union,
)

import anki  # pylint: disable=unused-import
import anki.backend_pb2 as pb
from anki import hooks
from anki.cards import Card
from anki.consts import *
from anki.decks import Deck, DeckConfig, DeckManager, QueueConfig
from anki.notes import Note
from anki.rsbackend import (
    TR,
    CountsForDeckToday,
    DeckTreeNode,
    FormatTimeSpanContext,
    SchedTimingToday,
    from_json_bytes,
)
from anki.utils import ids2str, intTime

UnburyCurrentDeckMode = pb.UnburyCardsInCurrentDeckIn.Mode  # pylint:disable=no-member
BuryOrSuspendMode = pb.BuryOrSuspendCardsIn.Mode  # pylint:disable=no-member
if TYPE_CHECKING:
    UnburyCurrentDeckModeValue = (
        pb.UnburyCardsInCurrentDeckIn.ModeValue  # pylint:disable=no-member
    )
    BuryOrSuspendModeValue = (
        pb.BuryOrSuspendCardsIn.ModeValue  # pylint:disable=no-member
    )

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

    def __init__(self, col: anki.collection.Collection) -> None:
        self.col = col.weakref()
        self.queueLimit = 50
        self.reportLimit = 1000
        self.dynReportLimit = 99999
        self.reps = 0
        self.today: Optional[int] = None
        self._haveQueues = False
        self._lrnCutoff = 0
        self._updateCutoff()

    def __repr__(self) -> str:
        d = dict(self.__dict__)
        del d["col"]
        return f"{super().__repr__()} {pprint.pformat(d, width=300)}"

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
        self.col.decks.update_active()
        self._updateCutoff()
        self._reset_counts()
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

        card.mod = intTime()
        card.usn = self.col.usn()
        card.flush()

    def _answerCard(self, card: Card, ease: int) -> None:
        if self._previewingCard(card):
            self._answerCardPreview(card, ease)
            return

        card.reps += 1

        new_delta = 0
        review_delta = 0

        if card.queue == QUEUE_TYPE_NEW:
            # came from the new queue, move to learning
            card.queue = QUEUE_TYPE_LRN
            card.type = CARD_TYPE_LRN
            # init reps to graduation
            card.left = self._startingLeft(card)
            new_delta = +1

        if card.queue in (QUEUE_TYPE_LRN, QUEUE_TYPE_DAY_LEARN_RELEARN):
            self._answerLrnCard(card, ease)
        elif card.queue == QUEUE_TYPE_REV:
            self._answerRevCard(card, ease)
            review_delta = +1
        else:
            raise Exception("Invalid queue '%s'" % card)

        self.update_stats(
            card.did,
            new_delta=new_delta,
            review_delta=review_delta,
            milliseconds_delta=+card.timeTaken(),
        )

        # once a card has been answered once, the original due date
        # no longer applies
        if card.odue:
            card.odue = 0

    # note: when adding revlog entries in the future, make sure undo
    # code deletes the entries
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

    def _reset_counts(self):
        tree = self.deck_due_tree(self.col.decks.selected())
        node = self.col.decks.find_deck_in_tree(tree, int(self.col.conf["curDeck"]))
        if not node:
            # current deck points to a missing deck
            self.newCount = 0
            self.revCount = 0
            self._immediate_learn_count = 0
        else:
            self.newCount = node.new_count
            self.revCount = node.review_count
            self._immediate_learn_count = node.learn_count

    def counts(self, card: Optional[Card] = None) -> Tuple[int, int, int]:
        counts = [self.newCount, self.lrnCount, self.revCount]
        if card:
            idx = self.countIdx(card)
            counts[idx] += 1
        new, lrn, rev = counts
        return (new, lrn, rev)

    def _is_finished(self):
        "Don't use this, it is a stop-gap until this code is refactored."
        return not any((self.newCount, self.revCount, self._immediate_learn_count))

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

    def update_stats(
        self, deck_id: int, new_delta=0, review_delta=0, milliseconds_delta=0
    ):
        self.col.backend.update_stats(
            deck_id=deck_id,
            new_delta=new_delta,
            review_delta=review_delta,
            millisecond_delta=milliseconds_delta,
        )

    def counts_for_deck_today(self, deck_id: int) -> CountsForDeckToday:
        return self.col.backend.counts_for_deck_today(deck_id)

    def extendLimits(self, new: int, rev: int) -> None:
        did = self.col.decks.current()["id"]
        self.col.backend.extend_limits(deck_id=did, new_delta=new, review_delta=rev)

    # legacy

    def _updateStats(self, card: Card, type: str, cnt: int = 1) -> None:
        did = card.did
        if type == "new":
            self.update_stats(did, new_delta=cnt)
        elif type == "rev":
            self.update_stats(did, review_delta=cnt)
        elif type == "time":
            self.update_stats(did, milliseconds_delta=cnt)

    # Deck list
    ##########################################################################

    def deckDueTree(self) -> List:
        "List of (base name, did, rev, lrn, new, children)"
        print(
            "deckDueTree() is deprecated; use decks.deck_tree() for a tree without counts, or sched.deck_due_tree()"
        )
        return from_json_bytes(self.col.backend.deck_tree_legacy())[5]

    def deck_due_tree(self, top_deck_id: int = 0) -> DeckTreeNode:
        """Returns a tree of decks with counts.
        If top_deck_id provided, counts are limited to that node."""
        return self.col.backend.deck_tree(top_deck_id=top_deck_id, now=intTime())

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

    def _resetNew(self) -> None:
        self._newDids = self.col.decks.active()[:]
        self._newQueue: List[int] = []
        self._updateNewCardRatio()

    def _fillNew(self, recursing=False) -> bool:
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

        # if we didn't get a card but the count is non-zero,
        # we need to check again for any cards that were
        # removed from the queue but not buried
        if recursing:
            print("bug: fillNew()")
            return False
        self._reset_counts()
        self._resetNew()
        return self._fillNew(recursing=True)

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
        self, did: int, fn: Optional[Callable[[Deck], int]] = None
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

    def _deckNewLimitSingle(self, g: DeckConfig) -> int:
        "Limit for deck without parent limits."
        if g["dyn"]:
            return self.dynReportLimit
        c = self.col.decks.confForDid(g["id"])
        limit = max(0, c["new"]["perDay"] - self.counts_for_deck_today(g["id"]).new)
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

    def _updateRevIvlOnFail(self, card: Card, conf: QueueConfig) -> None:
        card.lastIvl = card.ivl
        card.ivl = self._lapseIvl(card, conf)

    def _moveToFirstStep(self, card: Card, conf: QueueConfig) -> Any:
        card.left = self._startingLeft(card)

        # relearning card?
        if card.type == CARD_TYPE_RELEARNING:
            self._updateRevIvlOnFail(card, conf)

        return self._rescheduleLrnCard(card, conf)

    def _moveToNextStep(self, card: Card, conf: QueueConfig) -> None:
        # decrement real left count and recalculate left today
        left = (card.left % 1000) - 1
        card.left = self._leftToday(conf["delays"], left) * 1000 + left

        self._rescheduleLrnCard(card, conf)

    def _repeatStep(self, card: Card, conf: QueueConfig) -> None:
        delay = self._delayForRepeatingGrade(conf, card.left)
        self._rescheduleLrnCard(card, conf, delay=delay)

    def _rescheduleLrnCard(
        self, card: Card, conf: QueueConfig, delay: Optional[int] = None
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

    def _delayForGrade(self, conf: QueueConfig, left: int) -> int:
        left = left % 1000
        try:
            delay = conf["delays"][-left]
        except IndexError:
            if conf["delays"]:
                delay = conf["delays"][0]
            else:
                # user deleted final step; use dummy value
                delay = 1
        return int(delay * 60)

    def _delayForRepeatingGrade(self, conf: QueueConfig, left: int) -> Any:
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

    def _rescheduleAsRev(self, card: Card, conf: QueueConfig, early: bool) -> None:
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
        self,
        delays: List[int],
        left: int,
        now: Optional[int] = None,
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
        self, card: Card, conf: QueueConfig, early: bool, fuzz: bool = True
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

    def _rescheduleNew(self, card: Card, conf: QueueConfig, early: bool) -> None:
        "Reschedule a new card that's graduated for the first time."
        card.ivl = self._graduatingIvl(card, conf, early)
        card.due = self.today + card.ivl
        card.factor = conf["initialFactor"]
        card.type = card.queue = QUEUE_TYPE_REV

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
        lim = max(0, c["rev"]["perDay"] - self.counts_for_deck_today(d["id"]).review)

        if parentLimit is not None:
            lim = min(parentLimit, lim)
        elif "::" in d["name"]:
            for parent in self.col.decks.parents(d["id"]):
                # pass in dummy parentLimit so we don't do parent lookup again
                lim = min(lim, self._deckRevLimitSingle(parent, parentLimit=lim))
        return hooks.scheduler_review_limit_for_single_deck(lim, d)

    def _revForDeck(
        self, did: int, lim: int, childMap: DeckManager.childMapNode
    ) -> Any:
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

    def _resetRev(self) -> None:
        self._revQueue: List[int] = []

    def _fillRev(self, recursing=False) -> bool:
        "True if a review card can be fetched."
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

        if recursing:
            print("bug: fillRev2()")
            return False
        self._reset_counts()
        self._resetRev()
        return self._fillRev(recursing=True)

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

    def _lapseIvl(self, card: Card, conf: QueueConfig) -> Any:
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
        self, ivl: float, conf: QueueConfig, prev: int, fuzz: bool
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

    # Filtered deck handling
    ##########################################################################

    def rebuild_filtered_deck(self, deck_id: int) -> int:
        return self.col.backend.rebuild_filtered_deck(deck_id)

    def empty_filtered_deck(self, deck_id: int) -> None:
        self.col.backend.empty_filtered_deck(deck_id)

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

    # legacy

    def rebuildDyn(self, did: Optional[int] = None) -> Optional[int]:
        did = did or self.col.decks.selected()
        count = self.rebuild_filtered_deck(did) or None
        if not count:
            return None
        # and change to our new deck
        self.col.decks.select(did)
        return count

    def emptyDyn(self, did: Optional[int], lim: Optional[str] = None) -> None:
        if lim is None:
            self.empty_filtered_deck(did)
            return

        queue = f"""
queue = (case when queue < 0 then queue
              when type in (1,{CARD_TYPE_RELEARNING}) then
  (case when (case when odue then odue else due end) > 1000000000 then 1 else
  {QUEUE_TYPE_DAY_LEARN_RELEARN} end)
else
  type
end)
"""
        self.col.db.execute(
            """
update cards set did = odid, %s,
due = (case when odue>0 then odue else due end), odue = 0, odid = 0, usn = ? where %s"""
            % (queue, lim),
            self.col.usn(),
        )

    def remFromDyn(self, cids: List[int]) -> None:
        self.emptyDyn(None, "id in %s and odid" % ids2str(cids))

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
                card.queue = QUEUE_TYPE_SUSPENDED
            # notify UI
            hooks.card_did_leech(card)
            return True
        return False

    # Tools
    ##########################################################################

    def _cardConf(self, card: Card) -> DeckConfig:
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

    def _revConf(self, card: Card) -> QueueConfig:
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

    def _checkDay(self) -> None:
        # check if the day has rolled over
        if time.time() > self.dayCutoff:
            self.reset()

    def _timing_today(self) -> SchedTimingToday:
        return self.col.backend.sched_timing_today()

    # Deck finished state
    ##########################################################################

    def congratulations_info(self) -> pb.CongratsInfoOut:
        return self.col.backend.congrats_info()

    def finishedMsg(self) -> str:
        print("finishedMsg() is obsolete")
        return ""

    def _nextDueMsg(self) -> str:
        print("_nextDueMsg() is obsolete")
        return ""

    def haveBuriedSiblings(self) -> bool:
        return self.congratulations_info().have_sched_buried

    def haveManuallyBuried(self) -> bool:
        return self.congratulations_info().have_user_buried

    def haveBuried(self) -> bool:
        info = self.congratulations_info()
        return info.have_sched_buried or info.have_user_buried

    # Next time reports
    ##########################################################################

    def nextIvlStr(self, card: Card, ease: int, short: bool = False) -> str:
        "Return the next interval for CARD as a string."
        ivl_secs = self.nextIvl(card, ease)
        if not ivl_secs:
            return self.col.tr(TR.SCHEDULING_END)
        s = self.col.format_timespan(ivl_secs, FormatTimeSpanContext.ANSWER_BUTTONS)
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

    def unsuspend_cards(self, ids: List[int]) -> None:
        self.col.backend.restore_buried_and_suspended_cards(ids)

    def unbury_cards(self, ids: List[int]) -> None:
        self.col.backend.restore_buried_and_suspended_cards(ids)

    def unbury_cards_in_current_deck(
        self,
        mode: UnburyCurrentDeckModeValue = UnburyCurrentDeckMode.ALL,
    ) -> None:
        self.col.backend.unbury_cards_in_current_deck(mode)

    def suspend_cards(self, ids: Sequence[int]) -> None:
        self.col.backend.bury_or_suspend_cards(
            card_ids=ids, mode=BuryOrSuspendMode.SUSPEND
        )

    def bury_cards(self, ids: Sequence[int], manual: bool = True) -> None:
        if manual:
            mode = BuryOrSuspendMode.BURY_USER
        else:
            mode = BuryOrSuspendMode.BURY_SCHED
        self.col.backend.bury_or_suspend_cards(card_ids=ids, mode=mode)

    def bury_note(self, note: Note):
        self.bury_cards(note.card_ids())

    # legacy

    def unburyCards(self) -> None:
        print(
            "please use unbury_cards() or unbury_cards_in_current_deck instead of unburyCards()"
        )
        self.unbury_cards_in_current_deck()

    def buryNote(self, nid: int) -> None:
        note = self.col.getNote(nid)
        self.bury_cards(note.card_ids())

    def unburyCardsForDeck(self, type: str = "all") -> None:
        print(
            "please use unbury_cards_in_current_deck() instead of unburyCardsForDeck()"
        )
        if type == "all":
            mode = UnburyCurrentDeckMode.ALL
        elif type == "manual":
            mode = UnburyCurrentDeckMode.USER_ONLY
        else:  # elif type == "siblings":
            mode = UnburyCurrentDeckMode.SCHED_ONLY
        self.unbury_cards_in_current_deck(mode)

    unsuspendCards = unsuspend_cards
    buryCards = bury_cards
    suspendCards = suspend_cards

    # Sibling spacing
    ##########################################################################

    def _burySiblings(self, card: Card) -> None:
        toBury: List[int] = []
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
                queue_obj = self._revQueue
                if buryRev:
                    toBury.append(cid)
            else:
                queue_obj = self._newQueue
                if buryNew:
                    toBury.append(cid)

            # even if burying disabled, we still discard to give same-day spacing
            try:
                queue_obj.remove(cid)
            except ValueError:
                pass
        # then bury
        if toBury:
            self.bury_cards(toBury, manual=False)

    # Resetting
    ##########################################################################

    def schedule_cards_as_new(self, card_ids: List[int]) -> None:
        "Put cards at the end of the new queue."
        self.col.backend.schedule_cards_as_new(card_ids=card_ids, log=True)

    def schedule_cards_as_reviews(
        self, card_ids: List[int], min_interval: int, max_interval: int
    ) -> None:
        "Make cards review cards, with a new interval randomly selected from range."
        self.col.backend.schedule_cards_as_reviews(
            card_ids=card_ids, min_interval=min_interval, max_interval=max_interval
        )

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
        self.col.backend.schedule_cards_as_new(card_ids=nonNew, log=False)

    # legacy

    forgetCards = schedule_cards_as_new
    reschedCards = schedule_cards_as_reviews

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
        self.col.backend.sort_cards(
            card_ids=cids,
            starting_from=start,
            step_size=step,
            randomize=shuffle,
            shift_existing=shift,
        )

    def randomizeCards(self, did: int) -> None:
        self.col.backend.sort_deck(deck_id=did, randomize=True)

    def orderCards(self, did: int) -> None:
        self.col.backend.sort_deck(deck_id=did, randomize=False)

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
