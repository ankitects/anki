# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from collections.abc import Sequence

from anki.collection import OpChanges, OpChangesWithCount, OpChangesWithId
from anki.decks import DeckCollapseScope, DeckDict, DeckId, UpdateDeckConfigs
from aqt.operations import CollectionOp
from aqt.qt import QWidget
from aqt.utils import getOnlyText, tooltip, tr


def remove_decks(
    *,
    parent: QWidget,
    deck_ids: Sequence[DeckId],
    deck_name: str,
) -> CollectionOp[OpChangesWithCount]:
    return CollectionOp(parent, lambda col: col.decks.remove(deck_ids)).success(
        lambda out: tooltip(
            tr.browsing_cards_deleted_with_deckname(
                count=out.count,
                deck_name=deck_name,
            ),
            parent=parent,
        )
    )


def reparent_decks(
    *, parent: QWidget, deck_ids: Sequence[DeckId], new_parent: DeckId
) -> CollectionOp[OpChangesWithCount]:
    return CollectionOp(
        parent, lambda col: col.decks.reparent(deck_ids=deck_ids, new_parent=new_parent)
    ).success(
        lambda out: tooltip(
            tr.browsing_reparented_decks(count=out.count), parent=parent
        )
    )


def rename_deck(
    *,
    parent: QWidget,
    deck_id: DeckId,
    new_name: str,
) -> CollectionOp[OpChanges]:
    return CollectionOp(
        parent,
        lambda col: col.decks.rename(deck_id, new_name),
    )


def add_deck_dialog(
    *,
    parent: QWidget,
    default_text: str = "",
) -> CollectionOp[OpChangesWithId] | None:
    if name := getOnlyText(
        tr.decks_new_deck_name(), default=default_text, parent=parent
    ).strip():
        return add_deck(parent=parent, name=name)
    else:
        return None


def add_deck(*, parent: QWidget, name: str) -> CollectionOp[OpChangesWithId]:
    return CollectionOp(parent, lambda col: col.decks.add_normal_deck_with_name(name))


def set_deck_collapsed(
    *,
    parent: QWidget,
    deck_id: DeckId,
    collapsed: bool,
    scope: DeckCollapseScope.V,
) -> CollectionOp[OpChanges]:
    return CollectionOp(
        parent,
        lambda col: col.decks.set_collapsed(
            deck_id=deck_id, collapsed=collapsed, scope=scope
        ),
    )


def set_current_deck(*, parent: QWidget, deck_id: DeckId) -> CollectionOp[OpChanges]:
    return CollectionOp(parent, lambda col: col.decks.set_current(deck_id))


def update_deck_configs(
    *, parent: QWidget, input: UpdateDeckConfigs
) -> CollectionOp[OpChanges]:
    return CollectionOp(parent, lambda col: col.decks.update_deck_configs(input))


def update_deck_dict(*, parent: QWidget, deck: DeckDict) -> CollectionOp[OpChanges]:
    return CollectionOp(parent, lambda col: col.decks.update_dict(deck))
