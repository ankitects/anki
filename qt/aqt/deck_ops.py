# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Callable, Sequence

from anki.decks import DeckId
from aqt import AnkiQt, QWidget
from aqt.main import PerformOpOptionalSuccessCallback
from aqt.utils import getOnlyText, tooltip, tr


def remove_decks(
    *,
    mw: AnkiQt,
    parent: QWidget,
    deck_ids: Sequence[DeckId],
) -> None:
    mw.perform_op(
        lambda: mw.col.decks.remove(deck_ids),
        success=lambda out: tooltip(
            tr.browsing_cards_deleted(count=out.count), parent=parent
        ),
    )


def reparent_decks(
    *, mw: AnkiQt, parent: QWidget, deck_ids: Sequence[DeckId], new_parent: DeckId
) -> None:
    mw.perform_op(
        lambda: mw.col.decks.reparent(deck_ids=deck_ids, new_parent=new_parent),
        success=lambda out: tooltip(
            tr.browsing_reparented_decks(count=out.count), parent=parent
        ),
    )


def rename_deck(
    *,
    mw: AnkiQt,
    deck_id: DeckId,
    new_name: str,
    after_rename: Callable[[], None] = None,
) -> None:
    mw.perform_op(
        lambda: mw.col.decks.rename(deck_id, new_name), after_hooks=after_rename
    )


def add_deck_dialog(
    *,
    mw: AnkiQt,
    parent: QWidget,
    default_text: str = "",
    success: PerformOpOptionalSuccessCallback = None,
) -> None:
    if name := getOnlyText(
        tr.decks_new_deck_name(), default=default_text, parent=parent
    ).strip():
        add_deck(mw=mw, name=name, success=success)


def add_deck(
    *, mw: AnkiQt, name: str, success: PerformOpOptionalSuccessCallback = None
) -> None:
    mw.perform_op(
        lambda: mw.col.decks.add_normal_deck_with_name(name),
        success=success,
    )
