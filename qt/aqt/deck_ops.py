# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Callable, Sequence

from anki.decks import DeckID
from anki.lang import TR
from aqt import AnkiQt, QWidget
from aqt.utils import tooltip, tr


def remove_decks(
    *,
    mw: AnkiQt,
    parent: QWidget,
    deck_ids: Sequence[DeckID],
) -> None:
    mw.perform_op(
        lambda: mw.col.decks.remove(deck_ids),
        success=lambda out: tooltip(
            tr(TR.BROWSING_CARDS_DELETED, count=out.count), parent=parent
        ),
    )


def reparent_decks(
    *, mw: AnkiQt, parent: QWidget, deck_ids: Sequence[DeckID], new_parent: DeckID
) -> None:
    mw.perform_op(
        lambda: mw.col.decks.reparent(deck_ids=deck_ids, new_parent=new_parent),
        success=lambda out: tooltip(
            tr(TR.BROWSING_REPARENTED_DECKS, count=out.count), parent=parent
        ),
    )


def rename_deck(
    *,
    mw: AnkiQt,
    deck_id: DeckID,
    new_name: str,
    after_rename: Callable[[], None] = None,
) -> None:
    mw.perform_op(
        lambda: mw.col.decks.rename(deck_id, new_name), after_hooks=after_rename
    )
