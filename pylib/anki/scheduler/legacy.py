# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# pylint: disable=invalid-name

from typing import List, Optional, Tuple

from anki.cards import Card, CardId
from anki.consts import CARD_TYPE_RELEARNING, QUEUE_TYPE_DAY_LEARN_RELEARN
from anki.decks import DeckConfigDict, DeckId
from anki.notes import NoteId
from anki.scheduler.base import SchedulerBase, UnburyDeck
from anki.utils import from_json_bytes, ids2str


class SchedulerBaseWithLegacy(SchedulerBase):
    "Legacy aliases and helpers. These will go away in the future."

    def reschedCards(
        self, card_ids: List[CardId], min_interval: int, max_interval: int
    ) -> None:
        self.set_due_date(card_ids, f"{min_interval}-{max_interval}!")

    def buryNote(self, nid: NoteId) -> None:
        note = self.col.get_note(nid)
        self.bury_cards(note.card_ids())

    def unburyCards(self) -> None:
        print("please use unbury_cards() or unbury_deck() instead of unburyCards()")
        self.unbury_deck(self.col.decks.get_current_id())

    def unburyCardsForDeck(self, type: str = "all") -> None:
        print("please use unbury_deck() instead of unburyCardsForDeck()")
        if type == "all":
            mode = UnburyDeck.ALL
        elif type == "manual":
            mode = UnburyDeck.USER_ONLY
        else:  # elif type == "siblings":
            mode = UnburyDeck.SCHED_ONLY
        self.unbury_deck(self.col.decks.get_current_id(), mode)

    def finishedMsg(self) -> str:
        print("finishedMsg() is obsolete")
        return ""

    def _nextDueMsg(self) -> str:
        print("_nextDueMsg() is obsolete")
        return ""

    def rebuildDyn(self, did: Optional[DeckId] = None) -> Optional[int]:
        did = did or self.col.decks.selected()
        count = self.rebuild_filtered_deck(did).count or None
        if not count:
            return None
        # and change to our new deck
        self.col.decks.select(did)
        return count

    def emptyDyn(self, did: Optional[DeckId], lim: Optional[str] = None) -> None:
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

    def remFromDyn(self, cids: List[CardId]) -> None:
        self.emptyDyn(None, f"id in {ids2str(cids)} and odid")

    # used by v2 scheduler and some add-ons
    def update_stats(
        self,
        deck_id: DeckId,
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

    # legacy in v3 but used by unit tests; redefined in v2/v1

    def _cardConf(self, card: Card) -> DeckConfigDict:
        return self.col.decks.config_dict_for_deck_id(card.did)

    def _fuzzIvlRange(self, ivl: int) -> Tuple[int, int]:
        return (ivl, ivl)

    # simple aliases
    unsuspendCards = SchedulerBase.unsuspend_cards
    buryCards = SchedulerBase.bury_cards
    suspendCards = SchedulerBase.suspend_cards
    forgetCards = SchedulerBase.schedule_cards_as_new
