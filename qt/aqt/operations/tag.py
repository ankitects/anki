# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Callable, Optional, Sequence

from anki.collection import OpChangesWithCount
from anki.notes import NoteId
from aqt import AnkiQt, QWidget
from aqt.main import PerformOpOptionalSuccessCallback
from aqt.utils import showInfo, tooltip, tr


def add_tags_to_notes(
    *,
    mw: AnkiQt,
    note_ids: Sequence[NoteId],
    space_separated_tags: str,
    success: PerformOpOptionalSuccessCallback = None,
    handler: Optional[object] = None,
) -> None:
    mw.perform_op(
        lambda: mw.col.tags.bulk_add(note_ids, space_separated_tags),
        success=success,
        handler=handler,
    )


def remove_tags_from_notes(
    *,
    mw: AnkiQt,
    note_ids: Sequence[NoteId],
    space_separated_tags: str,
    success: PerformOpOptionalSuccessCallback = None,
    handler: Optional[object] = None,
) -> None:
    mw.perform_op(
        lambda: mw.col.tags.bulk_remove(note_ids, space_separated_tags),
        success=success,
        handler=handler,
    )


def clear_unused_tags(*, mw: AnkiQt, parent: QWidget) -> None:
    mw.perform_op(
        mw.col.tags.clear_unused_tags,
        success=lambda out: tooltip(
            tr.browsing_removed_unused_tags_count(count=out.count), parent=parent
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
            tooltip(tr.browsing_notes_updated(count=out.count), parent=parent)
        else:
            showInfo(tr.browsing_tag_rename_warning_empty(), parent=parent)

    mw.perform_op(
        lambda: mw.col.tags.rename(old=current_name, new=new_name),
        success=success,
        after_hooks=after_rename,
    )


def remove_tags_from_all_notes(
    *, mw: AnkiQt, parent: QWidget, space_separated_tags: str
) -> None:
    mw.perform_op(
        lambda: mw.col.tags.remove(space_separated_tags=space_separated_tags),
        success=lambda out: tooltip(
            tr.browsing_notes_updated(count=out.count), parent=parent
        ),
    )


def reparent_tags(
    *, mw: AnkiQt, parent: QWidget, tags: Sequence[str], new_parent: str
) -> None:
    mw.perform_op(
        lambda: mw.col.tags.reparent(tags=tags, new_parent=new_parent),
        success=lambda out: tooltip(
            tr.browsing_notes_updated(count=out.count), parent=parent
        ),
    )


def set_tag_collapsed(*, mw: AnkiQt, tag: str, collapsed: bool) -> None:
    mw.perform_op(lambda: mw.col.tags.set_collapsed(tag=tag, collapsed=collapsed))
