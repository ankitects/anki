# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Sequence

from anki.cards import CardId
from anki.collection import OpChanges
from anki.decks import DeckId
from aqt.operations import CollectionOp
from aqt.qt import QWidget


def set_card_deck(
    *, parent: QWidget, card_ids: Sequence[CardId], deck_id: DeckId
) -> CollectionOp[OpChanges]:
    return CollectionOp(parent, lambda col: col.set_deck(card_ids, deck_id))


def set_card_flag(
    *,
    parent: QWidget,
    card_ids: Sequence[CardId],
    flag: int,
) -> CollectionOp[OpChanges]:
    return CollectionOp(parent, lambda col: col.set_user_flag_for_cards(flag, card_ids))
