# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Sequence

from anki.collection import OpChanges, OpChangesWithCount
from anki.decks import DeckId
from anki.notes import Note, NoteId
from aqt.operations import CollectionOp
from aqt.qt import QWidget
from aqt.utils import tooltip, tr


def add_note(
    *,
    parent: QWidget,
    note: Note,
    target_deck_id: DeckId,
) -> CollectionOp[OpChanges]:
    return CollectionOp(parent, lambda col: col.add_note(note, target_deck_id))


def update_note(*, parent: QWidget, note: Note) -> CollectionOp[OpChanges]:
    return CollectionOp(parent, lambda col: col.update_note(note))


def remove_notes(
    *,
    parent: QWidget,
    note_ids: Sequence[NoteId],
) -> CollectionOp[OpChangesWithCount]:
    return CollectionOp(parent, lambda col: col.remove_notes(note_ids)).success(
        lambda out: tooltip(tr.browsing_cards_deleted(count=out.count)),
    )
