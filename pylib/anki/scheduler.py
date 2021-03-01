# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
This file contains experimental scheduler changes, and is not currently
used by Anki.
"""

from __future__ import annotations

from heapq import *
from typing import Any, List, Optional, Sequence, Tuple, Union

import anki  # pylint: disable=unused-import
import anki._backend.backend_pb2 as _pb
from anki import hooks
from anki.cards import Card
from anki.consts import *
from anki.decks import DeckConfig, DeckTreeNode, QueueConfig
from anki.notes import Note
from anki.types import assert_exhaustive
from anki.utils import from_json_bytes, ids2str, intTime

QueuedCards = _pb.GetQueuedCardsOut.QueuedCards
CongratsInfo = _pb.CongratsInfoOut
SchedTimingToday = _pb.SchedTimingTodayOut
UnburyCurrentDeck = _pb.UnburyCardsInCurrentDeckIn
BuryOrSuspend = _pb.BuryOrSuspendCardsIn

# fixme: reviewer.cardQueue/editCurrent/undo handling/retaining current card


class Scheduler:
    is_2021 = True

    def __init__(self, col: anki.collection.Collection) -> None:
        self.col = col.weakref()
        # don't rely on this, it will likely be removed out in the future
        self.reps = 0

    # Timing
    ##########################################################################

    def timing_today(self) -> SchedTimingToday:
        return self.col._backend.sched_timing_today()

    @property
    def today(self) -> int:
        return self.timing_today().days_elapsed

    @property
    def dayCutoff(self) -> int:
        return self.timing_today().next_day_at

    # Fetching the next card
    ##########################################################################

    def reset(self) -> None:
        self.col._backend.clear_card_queues()

    def get_queued_cards(
        self,
        *,
        fetch_limit: int = 1,
        intraday_learning_only: bool = False,
    ) -> Union[QueuedCards, CongratsInfo]:
        info = self.col._backend.get_queued_cards(
            fetch_limit=fetch_limit, intraday_learning_only=intraday_learning_only
        )
        kind = info.WhichOneof("value")
        if kind == "queued_cards":
            return info.queued_cards
        elif kind == "congrats_info":
            return info.congrats_info
        else:
            assert_exhaustive(kind)
            assert False

    def getCard(self) -> Optional[Card]:
        """Fetch the next card from the queue. None if finished."""
        response = self.get_queued_cards()
        if isinstance(response, QueuedCards):
            backend_card = response.cards[0].card
            card = Card(self.col)
            card._load_from_backend_card(backend_card)
            card.startTimer()
            return card
        else:
            return None

    def _is_finished(self) -> bool:
        "Don't use this, it is a stop-gap until this code is refactored."
        info = self.get_queued_cards()
        return isinstance(info, CongratsInfo)

    def counts(self, card: Optional[Card] = None) -> Tuple[int, int, int]:
        info = self.get_queued_cards()
        if isinstance(info, CongratsInfo):
            counts = [0, 0, 0]
        else:
            counts = [info.new_count, info.learning_count, info.review_count]

        return tuple(counts)  # type: ignore

    @property
    def newCount(self) -> int:
        return self.counts()[0]

    @property
    def lrnCount(self) -> int:
        return self.counts()[1]

    @property
    def reviewCount(self) -> int:
        return self.counts()[2]

    # Answering a card
    ##########################################################################

    def answerCard(self, card: Card, ease: int) -> None:
        assert 1 <= ease <= 4
        assert 0 <= card.queue <= 4

        self.col.markReview(card)

        new_state = self._answerCard(card, ease)

        self._handle_leech(card, new_state)

        self.reps += 1

    def _answerCard(self, card: Card, ease: int) -> _pb.SchedulingState:
        states = self.col._backend.get_next_card_states(card.id)
        if ease == BUTTON_ONE:
            new_state = states.again
            rating = _pb.AnswerCardIn.AGAIN
        elif ease == BUTTON_TWO:
            new_state = states.hard
            rating = _pb.AnswerCardIn.HARD
        elif ease == BUTTON_THREE:
            new_state = states.good
            rating = _pb.AnswerCardIn.GOOD
        elif ease == BUTTON_FOUR:
            new_state = states.easy
            rating = _pb.AnswerCardIn.EASY
        else:
            assert False, "invalid ease"

        self.col._backend.answer_card(
            card_id=card.id,
            current_state=states.current,
            new_state=new_state,
            rating=rating,
            answered_at_millis=intTime(1000),
            milliseconds_taken=card.timeTaken(),
        )

        # fixme: tests assume card will be mutated, so we need to reload it
        card.load()

        return new_state

    def _handle_leech(self, card: Card, new_state: _pb.SchedulingState) -> bool:
        "True if was leech."
        if self.col._backend.state_is_leech(new_state):
            if hooks.card_did_leech.count() > 0:
                hooks.card_did_leech(card)
                # leech hooks assumed that card mutations would be saved for them
                card.mod = intTime()
                card.usn = self.col.usn()
                card.flush()

            return True
        else:
            return False

    # Next times
    ##########################################################################
    # fixme: move these into tests_schedv2 in the future

    def _interval_for_state(self, state: _pb.SchedulingState) -> int:
        kind = state.WhichOneof("value")
        if kind == "normal":
            return self._interval_for_normal_state(state.normal)
        elif kind == "filtered":
            return self._interval_for_filtered_state(state.filtered)
        else:
            assert_exhaustive(kind)
            return 0  # unreachable

    def _interval_for_normal_state(self, normal: _pb.SchedulingState.Normal) -> int:
        kind = normal.WhichOneof("value")
        if kind == "new":
            return 0
        elif kind == "review":
            return normal.review.scheduled_days * 86400
        elif kind == "learning":
            return normal.learning.scheduled_secs
        elif kind == "relearning":
            return normal.relearning.learning.scheduled_secs
        else:
            assert_exhaustive(kind)
            return 0  # unreachable

    def _interval_for_filtered_state(
        self, filtered: _pb.SchedulingState.Filtered
    ) -> int:
        kind = filtered.WhichOneof("value")
        if kind == "preview":
            return filtered.preview.scheduled_secs
        elif kind == "rescheduling":
            return self._interval_for_normal_state(filtered.rescheduling.original_state)
        else:
            assert_exhaustive(kind)
            return 0  # unreachable

    def nextIvl(self, card: Card, ease: int) -> Any:
        "Don't use this - it is only required by tests, and will be moved in the future."
        states = self.col._backend.get_next_card_states(card.id)
        if ease == BUTTON_ONE:
            new_state = states.again
        elif ease == BUTTON_TWO:
            new_state = states.hard
        elif ease == BUTTON_THREE:
            new_state = states.good
        elif ease == BUTTON_FOUR:
            new_state = states.easy
        else:
            assert False, "invalid ease"

        return self._interval_for_state(new_state)

    # Review-related UI helpers
    ##########################################################################

    def countIdx(self, card: Card) -> int:
        if card.queue in (QUEUE_TYPE_DAY_LEARN_RELEARN, QUEUE_TYPE_PREVIEW):
            return QUEUE_TYPE_LRN
        return card.queue

    def answerButtons(self, card: Card) -> int:
        return 4

    def nextIvlStr(self, card: Card, ease: int, short: bool = False) -> str:
        "Return the next interval for CARD as a string."
        states = self.col._backend.get_next_card_states(card.id)
        return self.col._backend.describe_next_states(states)[ease - 1]

    # Deck list
    ##########################################################################

    def deck_due_tree(self, top_deck_id: int = 0) -> DeckTreeNode:
        """Returns a tree of decks with counts.
        If top_deck_id provided, counts are limited to that node."""
        return self.col._backend.deck_tree(top_deck_id=top_deck_id, now=intTime())

    # Deck finished state & custom study
    ##########################################################################

    def congratulations_info(self) -> CongratsInfo:
        return self.col._backend.congrats_info()

    def haveBuriedSiblings(self) -> bool:
        return self.congratulations_info().have_sched_buried

    def haveManuallyBuried(self) -> bool:
        return self.congratulations_info().have_user_buried

    def haveBuried(self) -> bool:
        info = self.congratulations_info()
        return info.have_sched_buried or info.have_user_buried

    def extendLimits(self, new: int, rev: int) -> None:
        did = self.col.decks.current()["id"]
        self.col._backend.extend_limits(deck_id=did, new_delta=new, review_delta=rev)

    # fixme: used by custom study
    def totalRevForCurrentDeck(self) -> int:
        return self.col.db.scalar(
            f"""
select count() from cards where id in (
select id from cards where did in %s and queue = {QUEUE_TYPE_REV} and due <= ? limit 9999)"""
            % self._deckLimit(),
            self.today,
        )

    # Filtered deck handling
    ##########################################################################

    def rebuild_filtered_deck(self, deck_id: int) -> int:
        return self.col._backend.rebuild_filtered_deck(deck_id)

    def empty_filtered_deck(self, deck_id: int) -> None:
        self.col._backend.empty_filtered_deck(deck_id)

    # Suspending & burying
    ##########################################################################

    def unsuspend_cards(self, ids: List[int]) -> None:
        self.col._backend.restore_buried_and_suspended_cards(ids)

    def unbury_cards(self, ids: List[int]) -> None:
        self.col._backend.restore_buried_and_suspended_cards(ids)

    def unbury_cards_in_current_deck(
        self,
        mode: UnburyCurrentDeck.Mode.V = UnburyCurrentDeck.ALL,
    ) -> None:
        self.col._backend.unbury_cards_in_current_deck(mode)

    def suspend_cards(self, ids: Sequence[int]) -> None:
        self.col._backend.bury_or_suspend_cards(
            card_ids=ids, mode=BuryOrSuspend.SUSPEND
        )

    def bury_cards(self, ids: Sequence[int], manual: bool = True) -> None:
        if manual:
            mode = BuryOrSuspend.BURY_USER
        else:
            mode = BuryOrSuspend.BURY_SCHED
        self.col._backend.bury_or_suspend_cards(card_ids=ids, mode=mode)

    def bury_note(self, note: Note) -> None:
        self.bury_cards(note.card_ids())

    # Resetting/rescheduling
    ##########################################################################

    def schedule_cards_as_new(self, card_ids: List[int]) -> None:
        "Put cards at the end of the new queue."
        self.col._backend.schedule_cards_as_new(card_ids=card_ids, log=True)

    def set_due_date(self, card_ids: List[int], days: str) -> None:
        """Set cards to be due in `days`, turning them into review cards if necessary.
        `days` can be of the form '5' or '5..7'"""
        self.col._backend.set_due_date(card_ids=card_ids, days=days)

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
        self.col._backend.schedule_cards_as_new(card_ids=nonNew, log=False)

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
        self.col._backend.sort_cards(
            card_ids=cids,
            starting_from=start,
            step_size=step,
            randomize=shuffle,
            shift_existing=shift,
        )

    def randomizeCards(self, did: int) -> None:
        self.col._backend.sort_deck(deck_id=did, randomize=True)

    def orderCards(self, did: int) -> None:
        self.col._backend.sort_deck(deck_id=did, randomize=False)

    def resortConf(self, conf: DeckConfig) -> None:
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

    ##########################################################################

    # unit tests
    def _fuzzIvlRange(self, ivl: int) -> Tuple[int, int]:
        return (ivl, ivl)

    # Legacy aliases and helpers
    ##########################################################################

    # fixme: only used by totalRevForCurrentDeck and old deck stats
    def _deckLimit(self) -> str:
        self.col.decks.update_active()
        return ids2str(self.col.decks.active())

    def reschedCards(
        self, card_ids: List[int], min_interval: int, max_interval: int
    ) -> None:
        self.set_due_date(card_ids, f"{min_interval}-{max_interval}!")

    def buryNote(self, nid: int) -> None:
        note = self.col.getNote(nid)
        self.bury_cards(note.card_ids())

    def unburyCards(self) -> None:
        print(
            "please use unbury_cards() or unbury_cards_in_current_deck instead of unburyCards()"
        )
        self.unbury_cards_in_current_deck()

    def unburyCardsForDeck(self, type: str = "all") -> None:
        print(
            "please use unbury_cards_in_current_deck() instead of unburyCardsForDeck()"
        )
        if type == "all":
            mode = UnburyCurrentDeck.ALL
        elif type == "manual":
            mode = UnburyCurrentDeck.USER_ONLY
        else:  # elif type == "siblings":
            mode = UnburyCurrentDeck.SCHED_ONLY
        self.unbury_cards_in_current_deck(mode)

    def finishedMsg(self) -> str:
        print("finishedMsg() is obsolete")
        return ""

    def _nextDueMsg(self) -> str:
        print("_nextDueMsg() is obsolete")
        return ""

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
        self.emptyDyn(None, f"id in {ids2str(cids)} and odid")

    def update_stats(
        self,
        deck_id: int,
        new_delta: int = 0,
        review_delta: int = 0,
        milliseconds_delta: int = 0,
    ) -> None:
        self.col._backend.update_stats(
            deck_id=deck_id,
            new_delta=new_delta,
            review_delta=review_delta,
            millisecond_delta=milliseconds_delta,
        )

    def _updateStats(self, card: Card, type: str, cnt: int = 1) -> None:
        did = card.did
        if type == "new":
            self.update_stats(did, new_delta=cnt)
        elif type == "rev":
            self.update_stats(did, review_delta=cnt)
        elif type == "time":
            self.update_stats(did, milliseconds_delta=cnt)

    def deckDueTree(self) -> List:
        "List of (base name, did, rev, lrn, new, children)"
        print(
            "deckDueTree() is deprecated; use decks.deck_tree() for a tree without counts, or sched.deck_due_tree()"
        )
        return from_json_bytes(self.col._backend.deck_tree_legacy())[5]

    def _cardConf(self, card: Card) -> DeckConfig:
        return self.col.decks.confForDid(card.did)

    def _home_config(self, card: Card) -> DeckConfig:
        return self.col.decks.confForDid(card.odid or card.did)

    def _newConf(self, card: Card) -> QueueConfig:
        return self._home_config(card)["new"]

    def _lapseConf(self, card: Card) -> QueueConfig:
        return self._home_config(card)["lapse"]

    def _revConf(self, card: Card) -> QueueConfig:
        return self._home_config(card)["rev"]

    def _lrnConf(self, card: Card) -> QueueConfig:
        if card.type in (CARD_TYPE_REV, CARD_TYPE_RELEARNING):
            return self._lapseConf(card)
        else:
            return self._newConf(card)

    unsuspendCards = unsuspend_cards
    buryCards = bury_cards
    suspendCards = suspend_cards
    forgetCards = schedule_cards_as_new
