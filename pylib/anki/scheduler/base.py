# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import anki
import anki._backend.backend_pb2 as _pb
from anki.collection import OpChanges, OpChangesWithCount, OpChangesWithID
from anki.config import Config

SchedTimingToday = _pb.SchedTimingTodayOut


from typing import List, Optional, Sequence

from anki.cards import CardID
from anki.consts import CARD_TYPE_NEW, NEW_CARDS_RANDOM, QUEUE_TYPE_NEW, QUEUE_TYPE_REV
from anki.decks import DeckConfigDict, DeckID, DeckTreeNode
from anki.notes import Note
from anki.utils import ids2str, intTime

CongratsInfo = _pb.CongratsInfoOut
UnburyCurrentDeck = _pb.UnburyCardsInCurrentDeckIn
BuryOrSuspend = _pb.BuryOrSuspendCardsIn
FilteredDeckForUpdate = _pb.FilteredDeckForUpdate


class SchedulerBase:
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
    def dayCutoff(self) -> int:
        return self._timing_today().next_day_at

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
        assert self.col.db
        return self.col.db.scalar(
            f"""
select count() from cards where id in (
select id from cards where did in %s and queue = {QUEUE_TYPE_REV} and due <= ? limit 9999)"""
            % self._deckLimit(),
            self.today,
        )

    # fixme: only used by totalRevForCurrentDeck and old deck stats;
    # schedv2 defines separate version
    def _deckLimit(self) -> str:
        self.col.decks.update_active()
        return ids2str(self.col.decks.active())

    # Filtered deck handling
    ##########################################################################

    def rebuild_filtered_deck(self, deck_id: DeckID) -> OpChangesWithCount:
        return self.col._backend.rebuild_filtered_deck(deck_id)

    def empty_filtered_deck(self, deck_id: DeckID) -> OpChanges:
        return self.col._backend.empty_filtered_deck(deck_id)

    def get_or_create_filtered_deck(self, deck_id: DeckID) -> FilteredDeckForUpdate:
        return self.col._backend.get_or_create_filtered_deck(deck_id)

    def add_or_update_filtered_deck(
        self, deck: FilteredDeckForUpdate
    ) -> OpChangesWithID:
        return self.col._backend.add_or_update_filtered_deck(deck)

    # Suspending & burying
    ##########################################################################

    def unsuspend_cards(self, ids: Sequence[CardID]) -> OpChanges:
        return self.col._backend.restore_buried_and_suspended_cards(ids)

    def unbury_cards(self, ids: List[CardID]) -> OpChanges:
        return self.col._backend.restore_buried_and_suspended_cards(ids)

    def unbury_cards_in_current_deck(
        self,
        mode: UnburyCurrentDeck.Mode.V = UnburyCurrentDeck.ALL,
    ) -> None:
        self.col._backend.unbury_cards_in_current_deck(mode)

    def suspend_cards(self, ids: Sequence[CardID]) -> OpChanges:
        return self.col._backend.bury_or_suspend_cards(
            card_ids=ids, mode=BuryOrSuspend.SUSPEND
        )

    def bury_cards(self, ids: Sequence[CardID], manual: bool = True) -> OpChanges:
        if manual:
            mode = BuryOrSuspend.BURY_USER
        else:
            mode = BuryOrSuspend.BURY_SCHED
        return self.col._backend.bury_or_suspend_cards(card_ids=ids, mode=mode)

    def bury_note(self, note: Note) -> None:
        self.bury_cards(note.card_ids())

    # Resetting/rescheduling
    ##########################################################################

    def schedule_cards_as_new(self, card_ids: List[CardID]) -> OpChanges:
        "Put cards at the end of the new queue."
        return self.col._backend.schedule_cards_as_new(card_ids=card_ids, log=True)

    def set_due_date(
        self,
        card_ids: List[CardID],
        days: str,
        config_key: Optional[Config.String.Key.V] = None,
    ) -> OpChanges:
        """Set cards to be due in `days`, turning them into review cards if necessary.
        `days` can be of the form '5' or '5..7'
        If `config_key` is provided, provided days will be remembered in config."""
        key: Optional[Config.String]
        if config_key:
            key = Config.String(key=config_key)
        else:
            key = None
        return self.col._backend.set_due_date(
            card_ids=card_ids,
            days=days,
            # this value is optional; the auto-generated typing is wrong
            config_key=key,  # type: ignore
        )

    def resetCards(self, ids: List[CardID]) -> None:
        "Completely reset cards for export."
        sids = ids2str(ids)
        assert self.col.db
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

    def reposition_new_cards(
        self,
        card_ids: Sequence[CardID],
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

    def randomizeCards(self, did: DeckID) -> None:
        self.col._backend.sort_deck(deck_id=did, randomize=True)

    def orderCards(self, did: DeckID) -> None:
        self.col._backend.sort_deck(deck_id=did, randomize=False)

    def resortConf(self, conf: DeckConfigDict) -> None:
        for did in self.col.decks.didsForConf(conf):
            if conf["new"]["order"] == 0:
                self.randomizeCards(did)
            else:
                self.orderCards(did)

    # for post-import
    def maybeRandomizeDeck(self, did: Optional[DeckID] = None) -> None:
        if not did:
            did = self.col.decks.selected()
        conf = self.col.decks.confForDid(did)
        # in order due?
        if conf["new"]["order"] == NEW_CARDS_RANDOM:
            self.randomizeCards(did)

    # legacy
    def sortCards(
        self,
        cids: List[CardID],
        start: int = 1,
        step: int = 1,
        shuffle: bool = False,
        shift: bool = False,
    ) -> None:
        self.reposition_new_cards(cids, start, step, shuffle, shift)
