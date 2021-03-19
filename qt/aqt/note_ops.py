# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Callable, Sequence

from anki.collection import OpChangesWithCount
from anki.lang import TR
from anki.notes import Note
from aqt import AnkiQt, QWidget
from aqt.main import PerformOpOptionalSuccessCallback
from aqt.utils import showInfo, tooltip, tr


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


def add_tags(
    *,
    mw: AnkiQt,
    note_ids: Sequence[int],
    space_separated_tags: str,
    success: PerformOpOptionalSuccessCallback = None,
) -> None:
    mw.perform_op(
        lambda: mw.col.tags.bulk_add(note_ids, space_separated_tags), success=success
    )


def remove_tags_for_notes(
    *,
    mw: AnkiQt,
    note_ids: Sequence[int],
    space_separated_tags: str,
    success: PerformOpOptionalSuccessCallback = None,
) -> None:
    mw.perform_op(
        lambda: mw.col.tags.bulk_remove(note_ids, space_separated_tags), success=success
    )


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
