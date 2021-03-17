# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Callable, Optional, Sequence

from anki.lang import TR
from anki.notes import Note
from aqt import AnkiQt, QWidget
from aqt.main import PerformOpOptionalSuccessCallback
from aqt.utils import show_invalid_search_error, showInfo, tr


def add_note(
    *,
    mw: AnkiQt,
    note: Note,
    target_deck_id: int,
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
    note_ids: Sequence[int],
    success: PerformOpOptionalSuccessCallback = None,
) -> None:
    mw.perform_op(lambda: mw.col.remove_notes(note_ids), success=success)


def add_tags(*, mw: AnkiQt, note_ids: Sequence[int], space_separated_tags: str) -> None:
    mw.perform_op(lambda: mw.col.tags.bulk_add(note_ids, space_separated_tags))


def remove_tags(
    *, mw: AnkiQt, note_ids: Sequence[int], space_separated_tags: str
) -> None:
    mw.perform_op(lambda: mw.col.tags.bulk_remove(note_ids, space_separated_tags))


def find_and_replace(
    *,
    mw: AnkiQt,
    parent: QWidget,
    note_ids: Sequence[int],
    search: str,
    replacement: str,
    regex: bool,
    field_name: Optional[str],
    match_case: bool,
) -> None:
    mw.perform_op(
        lambda: mw.col.find_and_replace(
            note_ids=note_ids,
            search=search,
            replacement=replacement,
            regex=regex,
            field_name=field_name,
            match_case=match_case,
        ),
        success=lambda out: showInfo(
            tr(TR.FINDREPLACE_NOTES_UPDATED, changed=out.count, total=len(note_ids)),
            parent=parent,
        ),
        failure=lambda exc: show_invalid_search_error(exc, parent=parent),
    )
