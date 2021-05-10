# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
This file contains experimental scheduler changes, and is not currently
used by Anki.

It uses the same DB schema as the V2 scheduler, and 'schedVer' remains
as '2' internally.
"""

from __future__ import annotations

from typing import Tuple, Union

import anki._backend.backend_pb2 as _pb
from anki.cards import Card
from anki.consts import *
from anki.scheduler.base import CongratsInfo
from anki.scheduler.legacy import SchedulerBaseWithLegacy
from anki.types import assert_exhaustive
from anki.utils import intTime

QueuedCards = _pb.GetQueuedCardsOut.QueuedCards
SchedulingState = _pb.SchedulingState


class Scheduler(SchedulerBaseWithLegacy):
    version = 3

    # don't rely on this, it will likely be removed in the future
    reps = 0

    # Fetching the next card
    ##########################################################################

    def reset(self) -> None:
        # backend automatically resets queues as operations are performed
        pass

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

        self._answerCard(card, ease)

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

    def state_is_leech(self, new_state: SchedulingState) -> bool:
        "True if new state marks the card as a leech."
        return self.col._backend.state_is_leech(new_state)

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
