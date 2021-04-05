# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Optional, Sequence

from anki.decks import DeckId
from anki.notes import Note, NoteId
from aqt import AnkiQt
from aqt.main import PerformOpOptionalSuccessCallback
from aqt.operations import OpMeta


def add_note(
    *,
    mw: AnkiQt,
    note: Note,
    target_deck_id: DeckId,
    success: PerformOpOptionalSuccessCallback = None,
) -> None:
    mw.perform_op(lambda: mw.col.add_note(note, target_deck_id), success=success)


def update_note(*, mw: AnkiQt, note: Note, handler: Optional[object]) -> None:
    mw.perform_op(
        lambda: mw.col.update_note(note),
        meta=OpMeta(handler=handler),
    )


def remove_notes(
    *,
    mw: AnkiQt,
    note_ids: Sequence[NoteId],
    success: PerformOpOptionalSuccessCallback = None,
) -> None:
    mw.perform_op(lambda: mw.col.remove_notes(note_ids), success=success)
