# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import anki
import anki.collection
from anki import decks_pb2, scheduler_pb2
from anki._legacy import DeprecatedNamesMixin
from anki.cards import Card
from anki.collection import OpChanges, OpChangesWithCount, OpChangesWithId
from anki.config import Config

SchedTimingToday = scheduler_pb2.SchedTimingTodayResponse
CongratsInfo = scheduler_pb2.CongratsInfoResponse
UnburyDeck = scheduler_pb2.UnburyDeckRequest
BuryOrSuspend = scheduler_pb2.BuryOrSuspendCardsRequest
CustomStudyRequest = scheduler_pb2.CustomStudyRequest
CustomStudyDefaults = scheduler_pb2.CustomStudyDefaultsResponse
ScheduleCardsAsNew = scheduler_pb2.ScheduleCardsAsNewRequest
ScheduleCardsAsNewDefaults = scheduler_pb2.ScheduleCardsAsNewDefaultsResponse
FilteredDeckForUpdate = decks_pb2.FilteredDeckForUpdate
RepositionDefaults = scheduler_pb2.RepositionDefaultsResponse

from collections.abc import Sequence
from typing import overload

from anki import config_pb2
from anki.cards import CardId
from anki.consts import (
    CARD_TYPE_NEW,
    NEW_CARDS_RANDOM,
    QUEUE_TYPE_DAY_LEARN_RELEARN,
    QUEUE_TYPE_LRN,
    QUEUE_TYPE_NEW,
    QUEUE_TYPE_PREVIEW,
)
from anki.decks import DeckConfigDict, DeckId, DeckTreeNode
from anki.notes import NoteId
from anki.utils import ids2str, int_time


class SchedulerBase(DeprecatedNamesMixin):
    "Actions shared between schedulers."
    version = 0

    def __init__(self, col: anki.collection.Collection) -> None:
        self.col = col.weakref()

    def _timing_today(self) -> SchedTimingToday:
        return self.col._backend.sched_timing_today()

    @property
    def today(self) -> int:
        return self._timing_today().days_elapsed

    @property
    def day_cutoff(self) -> int:
        return self._timing_today().next_day_at

    def countIdx(self, card: Card) -> int:
        if card.queue in (QUEUE_TYPE_DAY_LEARN_RELEARN, QUEUE_TYPE_PREVIEW):
            return QUEUE_TYPE_LRN
        return card.queue

    # Deck list
    ##########################################################################

    @overload
    def deck_due_tree(self, top_deck_id: None = None) -> DeckTreeNode: ...

    @overload
    def deck_due_tree(self, top_deck_id: DeckId) -> DeckTreeNode | None: ...

    def deck_due_tree(self, top_deck_id: DeckId | None = None) -> DeckTreeNode | None:
        """Returns a tree of decks with counts.
        If top_deck_id provided, only the according subtree is returned."""
        tree = self.col._backend.deck_tree(now=int_time())
        if top_deck_id:
            return self.col.decks.find_deck_in_tree(tree, top_deck_id)
        return tree

    # Deck finished state & custom study
    ##########################################################################

    def congratulations_info(self) -> CongratsInfo:
        return self.col._backend.congrats_info()

    def have_buried_siblings(self) -> bool:
        return self.congratulations_info().have_sched_buried

    def have_manually_buried(self) -> bool:
        return self.congratulations_info().have_user_buried

    def have_buried(self) -> bool:
        info = self.congratulations_info()
        return info.have_sched_buried or info.have_user_buried

    def custom_study(self, request: CustomStudyRequest) -> OpChanges:
        return self.col._backend.custom_study(request)

    def custom_study_defaults(self, deck_id: DeckId) -> CustomStudyDefaults:
        return self.col._backend.custom_study_defaults(deck_id=deck_id)

    def extend_limits(self, new: int, rev: int) -> None:
        did = self.col.decks.current()["id"]
        self.col._backend.extend_limits(deck_id=did, new_delta=new, review_delta=rev)

    # fixme: only used by total_rev_for_current_deck and old deck stats;
    # schedv2 defines separate version
    def _deck_limit(self) -> str:
        return ids2str(
            self.col.decks.deck_and_child_ids(self.col.decks.get_current_id())
        )

    # Filtered deck handling
    ##########################################################################

    def rebuild_filtered_deck(self, deck_id: DeckId) -> OpChangesWithCount:
        return self.col._backend.rebuild_filtered_deck(deck_id)

    def empty_filtered_deck(self, deck_id: DeckId) -> OpChanges:
        return self.col._backend.empty_filtered_deck(deck_id)

    def get_or_create_filtered_deck(self, deck_id: DeckId) -> FilteredDeckForUpdate:
        return self.col._backend.get_or_create_filtered_deck(deck_id)

    def add_or_update_filtered_deck(
        self, deck: FilteredDeckForUpdate
    ) -> OpChangesWithId:
        return self.col._backend.add_or_update_filtered_deck(deck)

    def filtered_deck_order_labels(self) -> Sequence[str]:
        return self.col._backend.filtered_deck_order_labels()

    # Suspending & burying
    ##########################################################################

    def unsuspend_cards(self, ids: Sequence[CardId]) -> OpChanges:
        return self.col._backend.restore_buried_and_suspended_cards(ids)

    def unbury_cards(self, ids: Sequence[CardId]) -> OpChanges:
        return self.col._backend.restore_buried_and_suspended_cards(ids)

    def unbury_deck(
        self,
        deck_id: DeckId,
        mode: UnburyDeck.Mode.V = UnburyDeck.ALL,
    ) -> OpChanges:
        return self.col._backend.unbury_deck(deck_id=deck_id, mode=mode)

    def suspend_cards(self, ids: Sequence[CardId]) -> OpChangesWithCount:
        return self.col._backend.bury_or_suspend_cards(
            card_ids=ids, note_ids=[], mode=BuryOrSuspend.SUSPEND
        )

    def suspend_notes(self, ids: Sequence[NoteId]) -> OpChangesWithCount:
        return self.col._backend.bury_or_suspend_cards(
            card_ids=[], note_ids=ids, mode=BuryOrSuspend.SUSPEND
        )

    def bury_cards(
        self, ids: Sequence[CardId], manual: bool = True
    ) -> OpChangesWithCount:
        if manual:
            mode = BuryOrSuspend.BURY_USER
        else:
            mode = BuryOrSuspend.BURY_SCHED
        return self.col._backend.bury_or_suspend_cards(
            card_ids=ids, note_ids=[], mode=mode
        )

    def bury_notes(self, note_ids: Sequence[NoteId]) -> OpChangesWithCount:
        return self.col._backend.bury_or_suspend_cards(
            card_ids=[], note_ids=note_ids, mode=BuryOrSuspend.BURY_USER
        )

    # Resetting/rescheduling
    ##########################################################################

    def schedule_cards_as_new(
        self,
        card_ids: Sequence[CardId],
        *,
        restore_position: bool = False,
        reset_counts: bool = False,
        context: ScheduleCardsAsNew.Context.V | None = None,
    ) -> OpChanges:
        "Place cards back into the new queue."
        request = ScheduleCardsAsNew(
            card_ids=card_ids,
            log=True,
            restore_position=restore_position,
            reset_counts=reset_counts,
            context=context,
        )
        return self.col._backend.schedule_cards_as_new(request)

    def schedule_cards_as_new_defaults(
        self, context: ScheduleCardsAsNew.Context.V
    ) -> ScheduleCardsAsNewDefaults:
        return self.col._backend.schedule_cards_as_new_defaults(context)

    def set_due_date(
        self,
        card_ids: Sequence[CardId],
        days: str,
        config_key: Config.String.V | None = None,
    ) -> OpChanges:
        """Set cards to be due in `days`, turning them into review cards if necessary.
        `days` can be of the form '5' or '5-7'
        If `config_key` is provided, provided days will be remembered in config."""
        key: config_pb2.OptionalStringConfigKey | None
        if config_key is not None:
            key = config_pb2.OptionalStringConfigKey(key=config_key)
        else:
            key = None
        return self.col._backend.set_due_date(
            card_ids=card_ids,
            days=days,
            # this value is optional; the auto-generated typing is wrong
            config_key=key,  # type: ignore
        )

    def reset_cards(self, ids: list[CardId]) -> None:
        "Completely reset cards for export."
        sids = ids2str(ids)
        assert self.col.db
        # we want to avoid resetting due number of existing new cards on export
        non_new = self.col.db.list(
            f"select id from cards where id in %s and (queue != {QUEUE_TYPE_NEW} or type != {CARD_TYPE_NEW})"
            % sids
        )
        # reset all cards
        self.col.db.execute(
            f"update cards set reps=0,lapses=0,odid=0,odue=0,queue={QUEUE_TYPE_NEW}"
            " where id in %s" % sids
        )
        # and forget any non-new cards, changing their due numbers
        request = ScheduleCardsAsNew(card_ids=non_new, log=False, restore_position=True)
        self.col._backend.schedule_cards_as_new(request)

    # Repositioning new cards
    ##########################################################################

    def reposition_new_cards(
        self,
        card_ids: Sequence[CardId],
        starting_from: int,
        step_size: int,
        randomize: bool,
        shift_existing: bool,
    ) -> OpChangesWithCount:
        return self.col._backend.sort_cards(
            card_ids=card_ids,
            starting_from=starting_from,
            step_size=step_size,
            randomize=randomize,
            shift_existing=shift_existing,
        )

    def reposition_defaults(self) -> RepositionDefaults:
        return self.col._backend.reposition_defaults()

    def randomize_cards(self, did: DeckId) -> None:
        self.col._backend.sort_deck(deck_id=did, randomize=True)

    def order_cards(self, did: DeckId) -> None:
        self.col._backend.sort_deck(deck_id=did, randomize=False)

    def resort_conf(self, conf: DeckConfigDict) -> None:
        for did in self.col.decks.decks_using_config(conf):
            if conf["new"]["order"] == 0:
                self.randomize_cards(did)
            else:
                self.order_cards(did)

    # for post-import
    def maybe_randomize_deck(self, did: DeckId | None = None) -> None:
        if not did:
            did = self.col.decks.selected()
        conf = self.col.decks.config_dict_for_deck_id(did)
        # in order due?
        if conf["new"]["order"] == NEW_CARDS_RANDOM:
            self.randomize_cards(did)

    def _legacy_sort_cards(
        self,
        cids: list[CardId],
        start: int = 1,
        step: int = 1,
        shuffle: bool = False,
        shift: bool = False,
    ) -> None:
        self.reposition_new_cards(cids, start, step, shuffle, shift)


SchedulerBase.register_deprecated_attributes(
    sortCards=(SchedulerBase._legacy_sort_cards, SchedulerBase.reposition_new_cards)
)
