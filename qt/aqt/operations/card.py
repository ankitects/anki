# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Optional, Sequence

from anki.cards import CardId
from anki.decks import DeckId
from aqt import AnkiQt
from aqt.main import PerformOpOptionalSuccessCallback


def set_card_deck(*, mw: AnkiQt, card_ids: Sequence[CardId], deck_id: DeckId) -> None:
    mw.perform_op(lambda: mw.col.set_deck(card_ids, deck_id))


def set_card_flag(
    *,
    mw: AnkiQt,
    card_ids: Sequence[CardId],
    flag: int,
    handler: Optional[object] = None,
    success: PerformOpOptionalSuccessCallback = None,
) -> None:
    mw.perform_op(
        lambda: mw.col.set_user_flag_for_cards(flag, card_ids),
        handler=handler,
        success=success,
    )
