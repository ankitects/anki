# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Optional, Sequence

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


def find_and_replace(
    *,
    parent: QWidget,
    note_ids: Sequence[NoteId],
    search: str,
    replacement: str,
    regex: bool,
    field_name: Optional[str],
    match_case: bool,
) -> CollectionOp[OpChangesWithCount]:
    return CollectionOp(
        parent,
        lambda col: col.find_and_replace(
            note_ids=note_ids,
            search=search,
            replacement=replacement,
            regex=regex,
            field_name=field_name,
            match_case=match_case,
        ),
    ).success(
        lambda out: tooltip(
            tr.findreplace_notes_updated(changed=out.count, total=len(note_ids)),
            parent=parent,
        )
    )
