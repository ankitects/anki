# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Callable, Sequence

from anki.decks import DeckID
from anki.notes import Note, NoteID
from aqt import AnkiQt
from aqt.main import PerformOpOptionalSuccessCallback


def add_note(
    *,
    mw: AnkiQt,
    note: Note,
    target_deck_id: DeckID,
    success: PerformOpOptionalSuccessCallback = None,
) -> None:
    mw.perform_op(lambda: mw.col.add_note(note, target_deck_id), success=success)


def update_note(*, mw: AnkiQt, note: Note, after_hooks: Callable[[], None]) -> None:
    mw.perform_op(
        lambda: mw.col.update_note(note),
        after_hooks=after_hooks,
    )


def remove_notes(
    *,
    mw: AnkiQt,
    note_ids: Sequence[NoteID],
    success: PerformOpOptionalSuccessCallback = None,
) -> None:
    mw.perform_op(lambda: mw.col.remove_notes(note_ids), success=success)
