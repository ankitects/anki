# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
This file contains experimental scheduler changes:

https://betas.ankiweb.net/2021-scheduler.html

It uses the same DB schema as the V2 scheduler, and 'schedVer' remains
as '2' internally.
"""

from __future__ import annotations

from typing import List, Literal, Sequence, Tuple

import anki._backend.backend_pb2 as _pb
from anki.cards import Card
from anki.collection import OpChanges
from anki.consts import *
from anki.decks import DeckId
from anki.errors import DBError
from anki.scheduler.legacy import SchedulerBaseWithLegacy
from anki.types import assert_exhaustive
from anki.utils import intTime

QueuedCards = _pb.QueuedCards
SchedulingState = _pb.SchedulingState
NextStates = _pb.NextCardStates
CardAnswer = _pb.CardAnswer


class Scheduler(SchedulerBaseWithLegacy):
    version = 3

    # don't rely on this, it will likely be removed in the future
    reps = 0

    # Fetching the next card
    ##########################################################################

    def get_queued_cards(
        self,
        *,
        fetch_limit: int = 1,
        intraday_learning_only: bool = False,
    ) -> QueuedCards:
        "Returns zero or more pending cards, and the remaining counts. Idempotent."
        return self.col._backend.get_queued_cards(
            fetch_limit=fetch_limit, intraday_learning_only=intraday_learning_only
        )

    def describe_next_states(self, next_states: NextStates) -> Sequence[str]:
        "Labels for each of the answer buttons."
        return self.col._backend.describe_next_states(next_states)

    # Answering a card
    ##########################################################################

    def build_answer(
        self, *, card: Card, states: NextStates, rating: CardAnswer.Rating.V
    ) -> CardAnswer:
        "Build input for answer_card()."
        if rating == CardAnswer.AGAIN:
            new_state = states.again
        elif rating == CardAnswer.HARD:
            new_state = states.hard
        elif rating == CardAnswer.GOOD:
            new_state = states.good
        elif rating == CardAnswer.EASY:
            new_state = states.easy
        else:
            assert False, "invalid rating"

        return CardAnswer(
            card_id=card.id,
            current_state=states.current,
            new_state=new_state,
            rating=rating,
            answered_at_millis=intTime(1000),
            milliseconds_taken=card.timeTaken(),
        )

    def answer_card(self, input: CardAnswer) -> OpChanges:
        "Update card to provided state, and remove it from queue."
        self.reps += 1
        return self.col._backend.answer_card(input=input)

    def state_is_leech(self, new_state: SchedulingState) -> bool:
        "True if new state marks the card as a leech."
        return self.col._backend.state_is_leech(new_state)

    # Fetching the next card (legacy API)
    ##########################################################################

    def reset(self) -> None:
        # backend automatically resets queues as operations are performed
        pass

    def getCard(self) -> Optional[Card]:
        """Fetch the next card from the queue. None if finished."""
        try:
            queued_card = self.get_queued_cards().cards[0]
        except IndexError:
            return None

        card = Card(self.col)
        card._load_from_backend_card(queued_card.card)
        card.startTimer()
        return card

    def _is_finished(self) -> bool:
        "Don't use this, it is a stop-gap until this code is refactored."
        return not self.get_queued_cards().cards

    def counts(self, card: Optional[Card] = None) -> Tuple[int, int, int]:
        info = self.get_queued_cards()
        return (info.new_count, info.learning_count, info.review_count)

    @property
    def newCount(self) -> int:
        return self.counts()[0]

    @property
    def lrnCount(self) -> int:
        return self.counts()[1]

    @property
    def reviewCount(self) -> int:
        return self.counts()[2]

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

    # Answering a card (legacy API)
    ##########################################################################

    def answerCard(self, card: Card, ease: Literal[1, 2, 3, 4]) -> OpChanges:
        if ease == BUTTON_ONE:
            rating = CardAnswer.AGAIN
        elif ease == BUTTON_TWO:
            rating = CardAnswer.HARD
        elif ease == BUTTON_THREE:
            rating = CardAnswer.GOOD
        elif ease == BUTTON_FOUR:
            rating = CardAnswer.EASY
        else:
            assert False, "invalid ease"

        states = self.col._backend.get_next_card_states(card.id)
        changes = self.answer_card(
            self.build_answer(card=card, states=states, rating=rating)
        )

        # tests assume card will be mutated, so we need to reload it
        card.load()

        return changes

    # Next times (legacy API)
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

    # Other legacy
    ###################

    # called by col.decks.active(), which add-ons are using
    @property
    def active_decks(self) -> List[DeckId]:
        try:
            return self.col.db.list("select id from active_decks")
        except DBError:
            return []

    # used by custom study; will likely be rolled into a separate routine
    # in the future
    def totalNewForCurrentDeck(self) -> int:
        return self.col.db.scalar(
            f"""
select count() from cards where queue={QUEUE_TYPE_NEW} and did in (select id from active_decks)"""
        )
