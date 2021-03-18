# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Callable, Optional, Sequence

from anki.collection import OpChangesWithCount
from anki.lang import TR
from anki.notes import Note
from aqt import AnkiQt, QWidget
from aqt.main import PerformOpOptionalSuccessCallback
from aqt.utils import show_invalid_search_error, showInfo, tooltip, tr


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


def remove_tags_for_notes(
    *, mw: AnkiQt, note_ids: Sequence[int], space_separated_tags: str
) -> None:
    mw.perform_op(lambda: mw.col.tags.bulk_remove(note_ids, space_separated_tags))


def clear_unused_tags(*, mw: AnkiQt, parent: QWidget) -> None:
    mw.perform_op(
        mw.col.tags.clear_unused_tags,
        success=lambda out: tooltip(
            tr(TR.BROWSING_REMOVED_UNUSED_TAGS_COUNT, count=out.count), parent=parent
        ),
    )


def rename_tag(
    *,
    mw: AnkiQt,
    parent: QWidget,
    current_name: str,
    new_name: str,
    after_rename: Callable[[], None],
) -> None:
    def success(out: OpChangesWithCount) -> None:
        if out.count:
            tooltip(tr(TR.BROWSING_NOTES_UPDATED, count=out.count), parent=parent)
        else:
            showInfo(tr(TR.BROWSING_TAG_RENAME_WARNING_EMPTY), parent=parent)

    mw.perform_op(
        lambda: mw.col.tags.rename(old=current_name, new=new_name),
        success=success,
        after_hooks=after_rename,
    )


def remove_tags_for_all_notes(
    *, mw: AnkiQt, parent: QWidget, space_separated_tags: str
) -> None:
    mw.perform_op(
        lambda: mw.col.tags.remove(space_separated_tags=space_separated_tags),
        success=lambda out: tooltip(
            tr(TR.BROWSING_NOTES_UPDATED, count=out.count), parent=parent
        ),
    )


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
