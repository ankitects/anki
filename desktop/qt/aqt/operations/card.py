# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from collections.abc import Sequence

from anki.cards import CardId
from anki.collection import OpChangesWithCount
from anki.decks import DeckId
from aqt.operations import CollectionOp
from aqt.qt import QWidget
from aqt.utils import tooltip, tr


def set_card_deck(
    *, parent: QWidget, card_ids: Sequence[CardId], deck_id: DeckId
) -> CollectionOp[OpChangesWithCount]:
    return CollectionOp(parent, lambda col: col.set_deck(card_ids, deck_id)).success(
        lambda out: tooltip(tr.browsing_cards_updated(count=out.count), parent=parent)
    )


def set_card_flag(
    *,
    parent: QWidget,
    card_ids: Sequence[CardId],
    flag: int,
) -> CollectionOp[OpChangesWithCount]:
    return CollectionOp(
        parent, lambda col: col.set_user_flag_for_cards(flag, card_ids)
    ).success(
        lambda out: tooltip(tr.browsing_cards_updated(count=out.count), parent=parent)
    )
